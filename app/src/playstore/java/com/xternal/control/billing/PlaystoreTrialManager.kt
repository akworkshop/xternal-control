package com.xternal.control.billing

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PlaystoreTrialManager(private val context: Context) {

    private val tag = "PlaystoreTrialManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences(BillingManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var trialStartTime: Long = 0L
    private var lastRecordedTime: Long = 0L
    private var isTampered: Boolean = false

    companion object {
        const val TRIAL_DURATION_MS = 2 * 24 * 60 * 60 * 1000L // 48 Hours

        private const val KEY_TRIAL_START = "play_trial_start_time"
        private const val KEY_TRIAL_LAST_SEEN = "play_trial_last_seen_time"
        private const val KEY_TRIAL_EXPIRED_SHOWN = "play_trial_expired_dialog_shown"
        private const val KEY_TRIAL_TAMPERED = "play_trial_is_tampered"

        // Public persistent filenames (valid in MediaStore - NO leading dots!)
        private const val FILE_NAME_DOWNLOAD = "xt_license_v1.bin"
        private const val FILE_NAME_DOCUMENTS = "xt_device_state.bin"

        private const val SECRET_SALT = "xternal_control_secure_trial_salt_2026_xrt"
    }

    init {
        loadOrInitializeTrial()
        checkNetworkTimeAsync()
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "default_device"
        } catch (e: Exception) {
            "default_device"
        }
    }

    private fun generateHmac(data: String): String {
        return try {
            val key = (getDeviceId() + SECRET_SALT).toByteArray(StandardCharsets.UTF_8)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest((data + SECRET_SALT).toByteArray(StandardCharsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        }
    }

    @Synchronized
    private fun loadOrInitializeTrial() {
        if (prefs.getBoolean(KEY_TRIAL_TAMPERED, false)) {
            isTampered = true
            return
        }

        val now = System.currentTimeMillis()

        // 1. Read from SharedPreferences
        val localStart = prefs.getLong(KEY_TRIAL_START, 0L)
        val localLastSeen = prefs.getLong(KEY_TRIAL_LAST_SEEN, 0L)

        // 2. Read from persistent storages (MediaStore & Public Shared Folders)
        val persistentTokens = readAllPersistentTrialTokens()

        var finalStart = 0L
        var finalLastSeen = 0L

        val allStarts = mutableListOf<Long>()
        val allLastSeens = mutableListOf<Long>()

        if (localStart > 0L) {
            allStarts.add(localStart)
            allLastSeens.add(localLastSeen)
        }

        for (token in persistentTokens) {
            allStarts.add(token.first)
            allLastSeens.add(token.second)
        }

        if (allStarts.isNotEmpty()) {
            // The earliest start time ever recorded across any storage layer wins
            finalStart = allStarts.minOrNull() ?: now
            finalLastSeen = allLastSeens.maxOrNull() ?: now
            Log.d(tag, "Recovered trial state: start=$finalStart, lastSeen=$finalLastSeen (from ${allStarts.size} records)")
        } else {
            // First install ever
            finalStart = now
            finalLastSeen = now
            Log.d(tag, "First run: initializing 48h trial at $finalStart")
        }

        // 3. Anti-Clock-Tampering Check
        if (now < finalLastSeen - 60_000L) {
            Log.w(tag, "Clock rollback detected! now: $now, lastSeen: $finalLastSeen. Expiring trial.")
            isTampered = true
            prefs.edit().putBoolean(KEY_TRIAL_TAMPERED, true).apply()
            return
        }

        finalLastSeen = maxOf(finalLastSeen, now)
        trialStartTime = finalStart
        lastRecordedTime = finalLastSeen

        // Save back to SharedPreferences
        prefs.edit()
            .putLong(KEY_TRIAL_START, trialStartTime)
            .putLong(KEY_TRIAL_LAST_SEEN, lastRecordedTime)
            .apply()

        // Persist to all storage locations
        writeAllPersistentTrialTokens(trialStartTime, lastRecordedTime)
    }

    fun isTrialActive(): Boolean {
        if (isTampered) return false
        val now = System.currentTimeMillis()
        if (trialStartTime <= 0L) return false

        // Quick rollback check
        if (now < lastRecordedTime - 60_000L) {
            isTampered = true
            prefs.edit().putBoolean(KEY_TRIAL_TAMPERED, true).apply()
            return false
        }

        // Update last seen periodically
        if (now > lastRecordedTime + 30_000L) {
            lastRecordedTime = now
            prefs.edit().putLong(KEY_TRIAL_LAST_SEEN, now).apply()
            writeAllPersistentTrialTokens(trialStartTime, now)
        }

        val elapsed = now - trialStartTime
        return elapsed in 0 until TRIAL_DURATION_MS
    }

    fun isTrialExpired(): Boolean {
        if (isTampered) return true
        if (trialStartTime <= 0L) return false
        val now = System.currentTimeMillis()
        return (now - trialStartTime) >= TRIAL_DURATION_MS || now < lastRecordedTime - 60_000L
    }

    fun getTrialHoursRemaining(): Int {
        if (isTampered || !isTrialActive()) return 0
        val now = System.currentTimeMillis()
        val remainingMs = TRIAL_DURATION_MS - (now - trialStartTime)
        val hours = (remainingMs / (1000 * 60 * 60)).toInt()
        return maxOf(0, minOf(48, hours))
    }

    fun shouldShowTrialExpiredDialog(): Boolean {
        if (!isTrialExpired()) return false
        val alreadyShown = prefs.getBoolean(KEY_TRIAL_EXPIRED_SHOWN, false)
        return !alreadyShown
    }

    fun markTrialExpiredDialogShown() {
        prefs.edit().putBoolean(KEY_TRIAL_EXPIRED_SHOWN, true).apply()
    }

    // ---------------------------------------------------------
    // Persistent Multi-Layer Storage (Survives "Clear Data")
    // ---------------------------------------------------------

    private fun readAllPersistentTrialTokens(): List<Pair<Long, Long>> {
        val results = mutableListOf<Pair<Long, Long>>()

        // Location 1: MediaStore Downloads
        readMediaStoreToken(MediaStore.Downloads.EXTERNAL_CONTENT_URI, FILE_NAME_DOWNLOAD)?.let {
            results.add(it)
        }

        // Location 2: MediaStore Files / Documents
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readMediaStoreToken(MediaStore.Files.getContentUri("external"), FILE_NAME_DOCUMENTS)?.let {
                results.add(it)
            }
        }

        // Location 3: Direct File in Downloads
        readDirectFileToken(Environment.DIRECTORY_DOWNLOADS, FILE_NAME_DOWNLOAD)?.let {
            results.add(it)
        }

        // Location 4: Direct File in Documents
        readDirectFileToken(Environment.DIRECTORY_DOCUMENTS, FILE_NAME_DOCUMENTS)?.let {
            results.add(it)
        }

        // Location 5: Root sdcard path fallbacks
        readDirectPathToken("/sdcard/Download/$FILE_NAME_DOWNLOAD")?.let { results.add(it) }
        readDirectPathToken("/sdcard/Documents/$FILE_NAME_DOCUMENTS")?.let { results.add(it) }

        return results
    }

    private fun writeAllPersistentTrialTokens(startTime: Long, lastSeen: Long) {
        val payload = "$startTime:$lastSeen:${getDeviceId()}"
        val signature = generateHmac(payload)
        val content = "$payload:$signature"

        // Location 1: MediaStore Downloads
        writeMediaStoreToken(
            baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            fileName = FILE_NAME_DOWNLOAD,
            relativePath = "Download/",
            content = content
        )

        // Location 2: MediaStore Files in Documents
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStoreToken(
                baseUri = MediaStore.Files.getContentUri("external"),
                fileName = FILE_NAME_DOCUMENTS,
                relativePath = "Documents/",
                content = content
            )
        }

        // Location 3: Direct File in Downloads
        writeDirectFileToken(Environment.DIRECTORY_DOWNLOADS, FILE_NAME_DOWNLOAD, content)

        // Location 4: Direct File in Documents
        writeDirectFileToken(Environment.DIRECTORY_DOCUMENTS, FILE_NAME_DOCUMENTS, content)
    }

    // ---------------------------------------------------------
    // MediaStore Helper
    // ---------------------------------------------------------

    private fun readMediaStoreToken(baseUri: Uri, fileName: String): Pair<Long, Long>? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            val resolver = context.contentResolver
            resolver.query(baseUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val fileUri = ContentUris.withAppendedId(baseUri, id)
                    resolver.openInputStream(fileUri)?.use { inputStream ->
                        parseToken(inputStream)
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeMediaStoreToken(baseUri: Uri, fileName: String, relativePath: String, content: String) {
        try {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            var targetUri: Uri? = null

            resolver.query(baseUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    targetUri = ContentUris.withAppendedId(baseUri, id)
                }
            }

            if (targetUri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    }
                }
                targetUri = resolver.insert(baseUri, values)
            }

            targetUri?.let { uri ->
                resolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "MediaStore write failed for $fileName: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // Direct File System Fallback
    // ---------------------------------------------------------

    private fun readDirectFileToken(directoryType: String, fileName: String): Pair<Long, Long>? {
        return try {
            val publicDir = Environment.getExternalStoragePublicDirectory(directoryType)
            if (publicDir.exists()) {
                val file = File(publicDir, fileName)
                if (file.exists() && file.canRead()) {
                    FileInputStream(file).use { parseToken(it) }
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readDirectPathToken(absolutePath: String): Pair<Long, Long>? {
        return try {
            val file = File(absolutePath)
            if (file.exists() && file.canRead()) {
                FileInputStream(file).use { parseToken(it) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun writeDirectFileToken(directoryType: String, fileName: String, content: String) {
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(directoryType)
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            if (publicDir.exists() && publicDir.canWrite()) {
                val file = File(publicDir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(content.toByteArray(StandardCharsets.UTF_8))
                    fos.flush()
                }
            }
        } catch (e: Exception) {
            // Expected on scoped storage if not permitted, safely ignored as MediaStore handles it
        }
    }

    private fun parseToken(inputStream: InputStream): Pair<Long, Long>? {
        return try {
            val byteBuffer = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
            }
            val raw = String(byteBuffer.toByteArray(), StandardCharsets.UTF_8).trim()
            val parts = raw.split(":")
            if (parts.size == 4) {
                val start = parts[0].toLongOrNull() ?: return null
                val lastSeen = parts[1].toLongOrNull() ?: return null
                val deviceId = parts[2]
                val expectedSignature = parts[3]

                // Verify device ID matches this physical device
                if (deviceId != getDeviceId()) {
                    Log.w(tag, "Trial token device ID mismatch!")
                    return null
                }

                // Verify HMAC signature
                val computedSig = generateHmac("$start:$lastSeen:$deviceId")
                if (computedSig != expectedSignature) {
                    Log.w(tag, "Trial token signature invalid!")
                    return null
                }

                Pair(start, lastSeen)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------
    // Background Network Time Verification
    // ---------------------------------------------------------

    private fun checkNetworkTimeAsync() {
        Thread {
            try {
                val url = URL("https://www.google.com")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.connect()
                val netTime = conn.date
                conn.disconnect()

                if (netTime > 0L) {
                    mainHandler.post {
                        if (trialStartTime > 0L) {
                            if (netTime < trialStartTime - 60_000L) {
                                Log.w(tag, "Network time is behind start time! Clock rollback flagged.")
                                isTampered = true
                                prefs.edit().putBoolean(KEY_TRIAL_TAMPERED, true).apply()
                            } else if (netTime - trialStartTime >= TRIAL_DURATION_MS) {
                                Log.d(tag, "Network time confirms trial has expired.")
                                prefs.edit().putLong(KEY_TRIAL_LAST_SEEN, netTime).apply()
                                writeAllPersistentTrialTokens(trialStartTime, netTime)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline or timeout, safely ignored
            }
        }.start()
    }
}
