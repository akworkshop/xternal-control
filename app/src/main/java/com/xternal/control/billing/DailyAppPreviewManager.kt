package com.xternal.control.billing

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages daily preview usage for non-whitelisted apps when the user is on the free tier
 * or their trial has expired.
 *
 * Anti-Tamper & Background Robust:
 * 1. Tracks elapsed CPU time via SystemClock.elapsedRealtime() so clock rollback cannot give free time.
 * 2. Persists consumed seconds per calendar day (keyed by YYYY-MM-DD).
 * 3. Daily allowance is configurable (e.g. 120s for test builds, 600s for release builds).
 */
object DailyAppPreviewManager {

    private const val PREFS_NAME = "XternalControlDailyUsagePrefs"
    private const val KEY_LAST_DAY = "daily_preview_day"
    private const val KEY_CONSUMED_SECONDS = "daily_preview_consumed_seconds"
    private const val KEY_LAST_REALTIME = "daily_preview_last_realtime"
    private const val KEY_LAST_WALL_TIME = "daily_preview_last_wall_time"

    // Production daily preview limit: 6 minutes (360 seconds)
    const val FORCE_TEST_EXPIRED_TRIAL = false

    const val DAILY_LIMIT_SECONDS = 360

    // Standard Whitelisted packages that are ALWAYS 100% free with unlimited time
    val WHITELISTED_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.android.chrome",
        "com.google.android.apps.maps",
        "org.mozilla.firefox",
        "com.google.android.googlequicksearchbox",
        "com.android.settings",
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.android.gallery3d",
        "com.coloros.gallery3d",
        "com.vivo.gallery",
        "com.miui.gallery",
        "mock.browser",
        "mock.notes",
        "mock.map"
    )

    // Critical Android System & Keyboard packages that MUST NEVER be restricted
    // (Prevents Google Play purchase screen, notification shade, and soft keyboards from being locked)
    val SYSTEM_PACKAGES = setOf(
        "com.android.vending",
        "com.google.android.feedback",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.baidu.input"
    )

    // Set of all restricted packages that were launched or active on the external display this session
    val activeExternalRestrictedPackages = mutableSetOf<String>()

    var activeRestrictedPackage: String? = null
        private set
    private var lastTrackingRealtimeMs: Long = 0L

    @Synchronized
    fun registerExternalApp(packageName: String) {
        if (isRestrictedApp(packageName)) {
            activeExternalRestrictedPackages.add(packageName)
        }
    }

    @Synchronized
    fun isPackageInExternalRestrictedSet(packageName: String): Boolean {
        return activeExternalRestrictedPackages.contains(packageName)
    }

    @Synchronized
    fun clearExternalSession() {
        activeExternalRestrictedPackages.clear()
        activeRestrictedPackage = null
        lastTrackingRealtimeMs = 0L
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    @Synchronized
    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getConsumedSecondsToday(context: Context): Int {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val recordedDay = prefs.getString(KEY_LAST_DAY, "")

        if (recordedDay != today) {
            val lastRealtime = prefs.getLong(KEY_LAST_REALTIME, 0L)
            val nowRealtime = SystemClock.elapsedRealtime()
            val lastWallTime = prefs.getLong(KEY_LAST_WALL_TIME, 0L)
            val nowWallTime = System.currentTimeMillis()

            // Anti-cheat: If recordedDay was set, verify that CPU uptime makes sense for a day roll
            if (!recordedDay.isNullOrEmpty()) {
                val realtimeDeltaMs = nowRealtime - lastRealtime
                // If user jumped wall time forward without device running, or rolled clock backward:
                if (nowWallTime < lastWallTime || (realtimeDeltaMs in 0..(2 * 60 * 60 * 1000L) && (nowWallTime - lastWallTime) > 12 * 60 * 60 * 1000L)) {
                    // Refuse to reset counter
                    return prefs.getInt(KEY_CONSUMED_SECONDS, DAILY_LIMIT_SECONDS)
                }
            }

            // Valid new day: reset daily counter
            prefs.edit()
                .putString(KEY_LAST_DAY, today)
                .putInt(KEY_CONSUMED_SECONDS, 0)
                .putLong(KEY_LAST_REALTIME, SystemClock.elapsedRealtime())
                .putLong(KEY_LAST_WALL_TIME, System.currentTimeMillis())
                .apply()
            return 0
        }

        return prefs.getInt(KEY_CONSUMED_SECONDS, 0)
    }

    @Synchronized
    fun getRemainingSecondsToday(context: Context): Int {
        val consumed = getConsumedSecondsToday(context)
        val remaining = DAILY_LIMIT_SECONDS - consumed
        return if (remaining > 0) remaining else 0
    }

    private var cachedImePackages: Set<String> = emptySet()
    private var lastImeRefreshMs: Long = 0L

    @Synchronized
    fun refreshImePackages(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastImeRefreshMs < 30_000L && cachedImePackages.isNotEmpty()) return
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val imeList = imm?.enabledInputMethodList
            if (imeList != null) {
                cachedImePackages = imeList.mapNotNull { it.packageName }.toSet()
                lastImeRefreshMs = now
            }
        } catch (_: Exception) {
        }
    }

    private fun isSystemOrImePackageInternal(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == "android" || packageName.startsWith("com.android.systemui") || packageName.contains("systemui")) return true
        if (packageName.contains("launcher") || packageName.contains("home")) return true
        if (SYSTEM_PACKAGES.contains(packageName)) return true
        if (cachedImePackages.contains(packageName)) return true
        return false
    }

    @Synchronized
    fun isSystemOrImePackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return true
        refreshImePackages(context)
        return isSystemOrImePackageInternal(packageName)
    }

    @Synchronized
    fun isRestrictedApp(context: Context, packageName: String): Boolean {
        refreshImePackages(context)
        return isRestrictedApp(packageName)
    }

    @Synchronized
    fun isRestrictedApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        // Xternal Control itself is ALWAYS whitelisted so it can never lock itself
        if (packageName == "com.xternal.control" || packageName.startsWith("com.xternal.control.")) return false
        if (isSystemOrImePackageInternal(packageName)) return false
        return !WHITELISTED_PACKAGES.contains(packageName)
    }

    @Synchronized
    fun updateAppLockStates(
        context: Context,
        apps: List<com.xternal.control.AppInfo>,
        isProActive: Boolean
    ) {
        val shouldLockRestricted = (com.xternal.control.BuildConfig.FLAVOR == "playstore") &&
            !isProActive &&
            isDailyPreviewExhausted(context)
        if (!shouldLockRestricted) {
            for (app in apps) {
                app.isLocked = false
            }
            return
        }
        refreshImePackages(context)
        for (app in apps) {
            app.isLocked = isRestrictedApp(app.packageName)
        }
    }

    @Synchronized
    fun isDailyPreviewExhausted(context: Context): Boolean {
        return getRemainingSecondsToday(context) <= 0
    }

    /**
     * Called when an app is launched or foregrounded on the external display.
     */
    @Synchronized
    fun onAppForegrounded(context: Context, packageName: String) {
        // First flush any time elapsed for the previously active package
        flushElapsedTime(context)

        // Ignore self package so Xternal Control itself never gets counted
        if (packageName == context.packageName) {
            activeRestrictedPackage = null
            lastTrackingRealtimeMs = 0L
            return
        }

        if (isRestrictedApp(packageName)) {
            activeRestrictedPackage = packageName
            activeExternalRestrictedPackages.add(packageName)
            lastTrackingRealtimeMs = SystemClock.elapsedRealtime()
        } else {
            activeRestrictedPackage = null
            lastTrackingRealtimeMs = 0L
        }
    }

    /**
     * Called when external desktop returns to foreground or cable disconnected.
     */
    @Synchronized
    fun onAppBackgrounded(context: Context) {
        flushElapsedTime(context)
        activeRestrictedPackage = null
        lastTrackingRealtimeMs = 0L
    }

    /**
     * Periodic tick (called every 1s from the background monitor service/handler).
     * Returns true if time limit has just been reached or was reached.
     */
    @Synchronized
    fun onTick(context: Context): Boolean {
        if (activeRestrictedPackage == null) {
            return isDailyPreviewExhausted(context)
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val deltaMs = nowRealtime - lastTrackingRealtimeMs
        if (deltaMs >= 1000L) {
            val deltaSeconds = (deltaMs / 1000L).toInt()
            lastTrackingRealtimeMs += deltaSeconds * 1000L
            recordConsumedSeconds(context, deltaSeconds)
        }

        return isDailyPreviewExhausted(context)
    }

    @Synchronized
    private fun flushElapsedTime(context: Context) {
        if (activeRestrictedPackage != null && lastTrackingRealtimeMs > 0L) {
            val nowRealtime = SystemClock.elapsedRealtime()
            val deltaMs = nowRealtime - lastTrackingRealtimeMs
            if (deltaMs >= 1000L) {
                val seconds = (deltaMs / 1000L).toInt()
                recordConsumedSeconds(context, seconds)
            }
        }
        lastTrackingRealtimeMs = 0L
    }

    @Synchronized
    private fun recordConsumedSeconds(context: Context, secondsToAdd: Int) {
        if (secondsToAdd <= 0) return
        val current = getConsumedSecondsToday(context)
        val newTotal = (current + secondsToAdd).coerceAtMost(DAILY_LIMIT_SECONDS)

        getPrefs(context).edit()
            .putString(KEY_LAST_DAY, getTodayDateString())
            .putInt(KEY_CONSUMED_SECONDS, newTotal)
            .putLong(KEY_LAST_REALTIME, SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_WALL_TIME, System.currentTimeMillis())
            .apply()
    }
}
