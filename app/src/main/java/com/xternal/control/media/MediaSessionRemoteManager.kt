package com.xternal.control.media

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import com.xternal.control.service.MediaNotificationListenerService

data class MediaSessionState(
    val hasActiveSession: Boolean = false,
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = true,
    val speed: Float = 1.0f
)

class MediaSessionRemoteManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        var instance: MediaSessionRemoteManager? = null
            private set

        fun init(context: Context): MediaSessionRemoteManager {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionRemoteManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeController: MediaController? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var isListenerRegistered = false

    var foregroundPackage: String = ""
        set(value) {
            field = value
            refreshActiveSessions()
        }

    var onStateChangedListener: ((MediaSessionState) -> Unit)? = null

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                notifyStateChanged()
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                notifyStateChanged()
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    mainHandler.removeCallbacks(tickerRunnable)
                    mainHandler.post(tickerRunnable)
                } else {
                    mainHandler.removeCallbacks(tickerRunnable)
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                notifyStateChanged()
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                activeController = null
                refreshActiveSessions()
            }
        }
    }

    init {
        setupSessionListenerIfAuthorized()
    }

    fun isNotificationAccessGranted(): Boolean {
        return MediaNotificationListenerService.isAccessGranted(context)
    }

    fun setupSessionListenerIfAuthorized() {
        if (!isNotificationAccessGranted() || mediaSessionManager == null || isListenerRegistered) {
            return
        }

        try {
            val componentName = MediaNotificationListenerService.getComponentName(context)
            sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                mainHandler.post {
                    selectBestController(controllers)
                }
            }
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener!!, componentName, mainHandler)
            isListenerRegistered = true
            refreshActiveSessions()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshActiveSessions() {
        if (!isNotificationAccessGranted() || mediaSessionManager == null) {
            if (activeController != null) {
                activeController?.unregisterCallback(controllerCallback)
                activeController = null
            }
            notifyStateChanged()
            return
        }

        try {
            val componentName = MediaNotificationListenerService.getComponentName(context)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            selectBestController(controllers)
        } catch (e: SecurityException) {
            e.printStackTrace()
            activeController = null
            notifyStateChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isPackageMatch(controllerPkg: String, foregroundPkg: String): Boolean {
        if (controllerPkg.isBlank() || foregroundPkg.isBlank()) return false
        val c = controllerPkg.lowercase()
        val f = foregroundPkg.lowercase()
        if (c == f || c.contains(f) || f.contains(c)) return true

        // Chromium browser & WebAPK association:
        // WebAPKs generated by Chrome/Chromium run via host browser (e.g. com.android.chrome)
        val isChromium = c.contains("chrome") || c.contains("chromium") || c.contains("browser")
        val isWebApk = f.contains("webapk") || f.startsWith("org.chromium")
        if (isChromium && isWebApk) return true
        if (c.contains("webapk") && (f.contains("chrome") || f.contains("chromium") || f.contains("browser"))) return true

        return false
    }

    private fun selectBestController(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = null
            notifyStateChanged()
            return
        }

        // Priority 1: A controller matching the active foreground package on screen AND actively playing
        val foregroundPlayingController = if (foregroundPackage.isNotEmpty()) {
            controllers.firstOrNull {
                isPackageMatch(it.packageName ?: "", foregroundPackage) &&
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } else null

        // Priority 2: Any controller matching the active foreground package on screen
        val foregroundController = if (foregroundPackage.isNotEmpty()) {
            controllers.firstOrNull { isPackageMatch(it.packageName ?: "", foregroundPackage) }
        } else null

        // Priority 3: A controller currently actively playing
        val playingController = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        // Priority 4: Keep existing controller if still in list
        val currentController = if (activeController != null && controllers.any { it.sessionToken == activeController?.sessionToken }) {
            activeController
        } else null

        // Priority 5: First available controller
        val selected = foregroundPlayingController ?: foregroundController ?: playingController ?: currentController ?: controllers.first()

        if (selected.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = selected
            activeController?.registerCallback(controllerCallback, mainHandler)
        }

        notifyStateChanged()
        if (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            mainHandler.removeCallbacks(tickerRunnable)
            mainHandler.post(tickerRunnable)
        }
    }

    fun onNotificationListenerConnected(service: MediaNotificationListenerService) {
        mainHandler.post {
            setupSessionListenerIfAuthorized()
            refreshActiveSessions()
        }
    }

    fun onNotificationListenerDisconnected() {
        mainHandler.post {
            if (isListenerRegistered && sessionListener != null && mediaSessionManager != null) {
                try {
                    mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isListenerRegistered = false
            }
            activeController?.unregisterCallback(controllerCallback)
            activeController = null
            notifyStateChanged()
        }
    }

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        mainHandler.post {
            refreshActiveSessions()
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification?) {
        mainHandler.post {
            refreshActiveSessions()
        }
    }

    fun getLivePosition(): Long {
        val state = activeController?.playbackState ?: return 0L
        var pos = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            val delta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
            pos += (delta * speed).toLong()
        }
        return pos.coerceAtLeast(0L)
    }

    fun getCurrentState(): MediaSessionState {
        val controller = activeController ?: return MediaSessionState(hasActiveSession = false)
        val playbackState = controller.playbackState
        val metadata = controller.metadata

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val isPaused = playbackState?.state == PlaybackState.STATE_PAUSED
        val isBuffering = playbackState?.state == PlaybackState.STATE_BUFFERING

        // If playbackState is null or stopped/none and no title, treat as inactive
        if (playbackState == null || (!isPlaying && !isPaused && !isBuffering)) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            if (title.isEmpty()) {
                return MediaSessionState(hasActiveSession = false)
            }
        }

        val pkg = controller.packageName ?: ""
        val appName = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            "Media Player"
        }

        val trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: appName

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val currentPos = getLivePosition()

        val actions = playbackState?.actions ?: 0L
        val canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L || duration > 0

        return MediaSessionState(
            hasActiveSession = true,
            packageName = pkg,
            appName = appName,
            title = trackTitle,
            artist = artist,
            isPlaying = isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            canSeek = canSeek,
            speed = playbackState?.playbackSpeed ?: 1.0f
        )
    }

    private fun notifyStateChanged() {
        val state = getCurrentState()
        onStateChangedListener?.invoke(state)
    }

    // --- Media Controls & Dual-Dispatch ---

    private fun isMediaKeyPreferredApp(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.contains("amazon") ||
                pkg.contains("avod") ||
                pkg.contains("apple") ||
                pkg.contains("atve")
    }

    private fun sendMediaKeyEvent(controller: MediaController?, keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0)
        var handled = false

        if (controller != null) {
            try {
                val d1 = controller.dispatchMediaButtonEvent(down)
                val d2 = controller.dispatchMediaButtonEvent(up)
                handled = d1 || d2
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            audioManager?.dispatchMediaKeyEvent(down)
            audioManager?.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return handled
    }

    fun togglePlayPause(): Boolean {
        val controller = activeController ?: return sendMediaKeyEvent(null, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val state = controller.playbackState?.state
        val isPlaying = state == PlaybackState.STATE_PLAYING
        val pkg = controller.packageName ?: ""
        val actions = controller.playbackState?.actions ?: 0L

        // Known apps that ignore transportControls or only declare ACTION_PLAY_PAUSE
        val isKeyPreferred = isMediaKeyPreferredApp(pkg) ||
                (actions != 0L && (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) == 0L)

        if (isKeyPreferred) {
            // Priority 1: Direct media button event for Amazon Prime Video / Apple TV
            sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            // Priority 2: Supplementary transportControls attempt
            try {
                if (isPlaying) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Standard media apps (Netflix, Disney+, YouTube, Spotify)
            try {
                if (isPlaying) {
                    controller.transportControls.pause()
                    // Idempotent pause fallback
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PAUSE)
                } else {
                    controller.transportControls.play()
                    // Idempotent play fallback
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
        return true
    }

    fun play(): Boolean {
        val controller = activeController ?: return sendMediaKeyEvent(null, KeyEvent.KEYCODE_MEDIA_PLAY)
        try {
            controller.transportControls.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY)
        return true
    }

    fun pause(): Boolean {
        val controller = activeController ?: return sendMediaKeyEvent(null, KeyEvent.KEYCODE_MEDIA_PAUSE)
        try {
            controller.transportControls.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    fun seekRelative(deltaMs: Long): Boolean {
        val controller = activeController ?: return false
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val currentPos = getLivePosition()
        var targetPos = currentPos + deltaMs

        if (duration > 0) {
            targetPos = targetPos.coerceIn(0L, duration)
        } else {
            targetPos = targetPos.coerceAtLeast(0L)
        }

        try {
            controller.transportControls.seekTo(targetPos)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val pkg = controller.packageName ?: ""
        val actions = controller.playbackState?.actions ?: 0L
        val canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L

        // If app relies on media keys (Amazon/Apple) or does not advertise ACTION_SEEK_TO, fallback to seek key events
        if (isMediaKeyPreferredApp(pkg) || !canSeek) {
            val keyCode = if (deltaMs > 0) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            } else {
                KeyEvent.KEYCODE_MEDIA_REWIND
            }
            sendMediaKeyEvent(controller, keyCode)
        }

        notifyStateChanged()
        return true
    }

    fun seekTo(positionMs: Long): Boolean {
        val controller = activeController ?: return false
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val clamped = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        try {
            controller.transportControls.seekTo(clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notifyStateChanged()
        return true
    }

    fun skipToNext(): Boolean {
        val controller = activeController ?: return sendMediaKeyEvent(null, KeyEvent.KEYCODE_MEDIA_NEXT)
        try {
            controller.transportControls.skipToNext()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_NEXT)
        return true
    }

    fun skipToPrevious(): Boolean {
        val controller = activeController ?: return sendMediaKeyEvent(null, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        try {
            controller.transportControls.skipToPrevious()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return true
    }
}
