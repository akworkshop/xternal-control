package com.xternal.control.billing

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.io.ByteArrayOutputStream
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

        private const val PERSISTENT_FILE_NAME = ".xt_trial_v1.dat"
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
        // 1. Check if already marked tampered
        if (prefs.getBoolean(KEY_TRIAL_TAMPERED, false)) {
            isTampered = true
            return
        }

        val now = System.currentTimeMillis()

        // 2. Read from local SharedPreferences
        val localStart = prefs.getLong(KEY_TRIAL_START, 0L)
        val localLastSeen = prefs.getLong(KEY_TRIAL_LAST_SEEN, 0L)

        // 3. Read from persistent external MediaStore (survives "Clear Data")
        val persistentData = readPersistentTrialToken()

        // Earliest known start time wins to prevent resetting
        var finalStart = 0L
        var finalLastSeen = 0L

        if (localStart > 0L && persistentData != null) {
            finalStart = minOf(localStart, persistentData.first)
            finalLastSeen = maxOf(localLastSeen, persistentData.second)
        } else if (localStart > 0L) {
            finalStart = localStart
            finalLastSeen = localLastSeen
        } else if (persistentData != null) {
            finalStart = persistentData.first
            finalLastSeen = persistentData.second
            Log.d(tag, "Recovered trial start time from persistent storage: $finalStart")
        }

        // 4. If no previous record found anywhere, this is the very first install!
        if (finalStart == 0L) {
            finalStart = now
            finalLastSeen = now
            Log.d(tag, "First run: initializing 2-day trial at $finalStart")
        }

        // 5. Anti-Clock-Tampering Check:
        // If current time is significantly before the last recorded time, the user rolled back their clock.
        if (now < finalLastSeen - 60_000L) {
            Log.w(tag, "Clock rollback detected! now: $now, lastSeen: $finalLastSeen. Expiring trial.")
            isTampered = true
            prefs.edit().putBoolean(KEY_TRIAL_TAMPERED, true).apply()
            return
        }

        finalLastSeen = maxOf(finalLastSeen, now)
        trialStartTime = finalStart
        lastRecordedTime = finalLastSeen

        // Save back to both SharedPreferences and Persistent storage
        prefs.edit()
            .putLong(KEY_TRIAL_START, trialStartTime)
            .putLong(KEY_TRIAL_LAST_SEEN, lastRecordedTime)
            .apply()

        writePersistentTrialToken(trialStartTime, lastRecordedTime)
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
            writePersistentTrialToken(trialStartTime, now)
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
    // Persistent MediaStore Storage (Survives "Clear Data")
    // ---------------------------------------------------------

    private fun readPersistentTrialToken(): Pair<Long, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return try {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(PERSISTENT_FILE_NAME)

            val resolver = context.contentResolver
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val fileUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    resolver.openInputStream(fileUri)?.use { inputStream ->
                        parseToken(inputStream)
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to read persistent trial token: ${e.message}")
            null
        }
    }

    private fun writePersistentTrialToken(startTime: Long, lastSeen: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        try {
            val resolver = context.contentResolver
            val deviceId = getDeviceId()
            val payload = "$startTime:$lastSeen:$deviceId"
            val signature = generateHmac(payload)
            val content = "$payload:$signature"

            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(PERSISTENT_FILE_NAME)

            var targetUri: Uri? = null

            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    targetUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                }
            }

            if (targetUri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, PERSISTENT_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/.xt_sys/")
                }
                targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }

            targetUri?.let { uri ->
                resolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to write persistent trial token: ${e.message}")
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

                // Verify device ID matches this physical phone
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
