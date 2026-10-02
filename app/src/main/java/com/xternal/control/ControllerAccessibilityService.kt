package com.xternal.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ControllerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ControllerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            InteractionBridge.sendForegroundPackageChanged(pkg)
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    fun performBackAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHomeAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecentsAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun captureDisplayScreenshot(displayId: Int, onComplete: (android.graphics.Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    displayId,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                val copy = bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                onComplete(copy)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onComplete(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            onComplete(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        } else {
            onComplete(null)
        }
    }

    fun dispatchClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchDoubleClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 40)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 120, 40)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke1)
            addStroke(stroke2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchLongClick(displayId: Int, x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 800)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchScroll(displayId: Int, startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 100)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dispatchZoom(displayId: Int, centerX: Float, centerY: Float, isZoomIn: Boolean): Boolean {
        val path1 = Path()
        val path2 = Path()

        val stroke1: GestureDescription.StrokeDescription
        val stroke2: GestureDescription.StrokeDescription

        if (isZoomIn) {
            val startRadius = 20f
            val endRadius = 220f
            // Pinch-open: Fingers move outwards from center
            path1.moveTo(centerX - startRadius, centerY)
            path1.lineTo(centerX - endRadius, centerY)

            path2.moveTo(centerX + startRadius, centerY)
            path2.lineTo(centerX + endRadius, centerY)

            stroke1 = GestureDescription.StrokeDescription(path1, 0, 250)
            stroke2 = GestureDescription.StrokeDescription(path2, 0, 250)
        } else {
            // Pinch-close: Fingers move inwards towards center (slow and larger range for Gallery, slow to prevent Maps momentum zoom out)
            val startRadius = 130f
            val endRadius = 30f
            path1.moveTo(centerX - startRadius, centerY)
            path1.lineTo(centerX - endRadius, centerY)

            path2.moveTo(centerX + startRadius, centerY)
            path2.lineTo(centerX + endRadius, centerY)

            stroke1 = GestureDescription.StrokeDescription(path1, 0, 450)
            stroke2 = GestureDescription.StrokeDescription(path2, 0, 450)
        }

        val builder = GestureDescription.Builder().apply {
            addStroke(stroke1)
            addStroke(stroke2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun performBackOnDisplay(displayId: Int) {
        // Shift focus safely to the target display at (0, 0) without clicking interactive views or video players
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 20)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        val dispatched = try {
            dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
            performGlobalAction(GLOBAL_ACTION_BACK)
            true
        }
        if (!dispatched) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    fun performBackOnDisplay(displayId: Int, x: Float, y: Float) {
        performBackOnDisplay(displayId)
    }

    fun performRecentsOnDisplay(displayId: Int, x: Float, y: Float) {
        // Shift focus first using a tiny 1-pixel gesture
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        try {
            dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    enum class MediaAction {
        PLAY_PAUSE,
        FAST_FORWARD,
        REWIND
    }

    fun dispatchHorizontalSwipe(displayId: Int, startX: Float, endX: Float, y: Float, durationMs: Long = 180): Boolean {
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && displayId > 0) {
                setDisplayId(displayId)
            }
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun performMediaAction(
        action: MediaAction,
        displayId: Int,
        displayWidth: Int,
        displayHeight: Int,
        packageName: String = ""
    ): Boolean {
        // Step 1: Search for media action accessibility nodes in active windows
        val matchedNode = findMediaActionNode(action)
        if (matchedNode != null) {
            val clicked = matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        // Step 2: Fallback to non-destructive gesture injection on target display
        val dm = resources.displayMetrics
        val targetWidth = if (displayWidth > 0) displayWidth.toFloat() else dm.widthPixels.toFloat()
        val targetHeight = if (displayHeight > 0) displayHeight.toFloat() else dm.heightPixels.toFloat()
        val targetDisplay = if (displayId > 0) displayId else Display.DEFAULT_DISPLAY

        val pkgLower = packageName.lowercase()
        val isGallery = pkgLower.contains("gallery") || pkgLower.contains("photos") || pkgLower.contains("video")
        val isYouTube = pkgLower.contains("youtube")

        return when (action) {
            MediaAction.PLAY_PAUSE -> {
                // Tapping center of display toggles playback or reveals/triggers controls in video player
                dispatchClick(targetDisplay, targetWidth * 0.5f, targetHeight * 0.5f)
            }
            MediaAction.FAST_FORWARD -> {
                if (isYouTube) {
                    // Double-tap on right half (+10s seek) inside YouTube video player
                    dispatchDoubleClick(targetDisplay, targetWidth * 0.75f, targetHeight * 0.5f)
                } else if (isGallery) {
                    // Smooth horizontal swipe rightwards scrubs video forward WITHOUT triggering zoom
                    dispatchHorizontalSwipe(targetDisplay, targetWidth * 0.40f, targetWidth * 0.75f, targetHeight * 0.5f)
                } else {
                    // General player: try double-tap right first, fallback to horizontal scrub
                    val done = dispatchDoubleClick(targetDisplay, targetWidth * 0.75f, targetHeight * 0.5f)
                    if (!done) {
                        dispatchHorizontalSwipe(targetDisplay, targetWidth * 0.40f, targetWidth * 0.75f, targetHeight * 0.5f)
                    } else true
                }
            }
            MediaAction.REWIND -> {
                if (isYouTube) {
                    // Double-tap on left half (-10s seek) inside YouTube video player
                    dispatchDoubleClick(targetDisplay, targetWidth * 0.25f, targetHeight * 0.5f)
                } else if (isGallery) {
                    // Smooth horizontal swipe leftwards scrubs video backward WITHOUT triggering zoom
                    dispatchHorizontalSwipe(targetDisplay, targetWidth * 0.60f, targetWidth * 0.25f, targetHeight * 0.5f)
                } else {
                    val done = dispatchDoubleClick(targetDisplay, targetWidth * 0.25f, targetHeight * 0.5f)
                    if (!done) {
                        dispatchHorizontalSwipe(targetDisplay, targetWidth * 0.60f, targetWidth * 0.25f, targetHeight * 0.5f)
                    } else true
                }
            }
        }
    }

    data class MediaTimelineInfo(
        val currentTime: String,
        val totalTime: String,
        val progressPercent: Int
    )

    fun queryActiveMediaTimeline(): MediaTimelineInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                windows?.forEach { win ->
                    win.root?.let { roots.add(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootInActiveWindow?.let { if (!roots.contains(it)) roots.add(it) }

        val timeRegex = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""")
        for (root in roots) {
            val textList = mutableListOf<String>()
            collectAllTexts(root, textList)
            val matchedTimes = textList.flatMap { text ->
                timeRegex.findAll(text).map { it.groupValues[1] }.toList()
            }
            if (matchedTimes.size >= 2) {
                val cur = matchedTimes[0]
                val tot = matchedTimes[1]
                val curSecs = parseTimeToSeconds(cur)
                val totSecs = parseTimeToSeconds(tot)
                val pct = if (totSecs > 0) ((curSecs.toFloat() / totSecs) * 100).toInt().coerceIn(0, 100) else 0
                return MediaTimelineInfo(cur, tot, pct)
            }
        }
        return null
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) list.add(t)
        val cd = node.contentDescription?.toString()
        if (!cd.isNullOrBlank()) list.add(cd)
        for (i in 0 until node.childCount) {
            collectAllTexts(node.getChild(i), list)
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":").map { it.toInt() }
            if (parts.size == 2) parts[0] * 60 + parts[1]
            else if (parts.size == 3) parts[0] * 3600 + parts[1] * 60 + parts[2]
            else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun findMediaActionNode(action: MediaAction): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                windows?.forEach { win ->
                    win.root?.let { roots.add(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootInActiveWindow?.let { if (!roots.contains(it)) roots.add(it) }

        val searchKeywords: List<String> = when (action) {
            MediaAction.PLAY_PAUSE -> listOf(
                "play", "pause", "play_pause", "btn_play", "btn_pause",
                "exo_play", "exo_pause", "play_btn", "pause_btn",
                "video_play", "video_pause", "btn_play_pause", "iv_play_pause",
                "action_play", "action_pause", "播放", "暂停"
            )
            MediaAction.FAST_FORWARD -> listOf(
                "forward", "fast-forward", "fast forward", "seek forward",
                "skip forward", "ffwd", "exo_ffwd", "forward 10", "10 seconds forward",
                "快进", "前进"
            )
            MediaAction.REWIND -> listOf(
                "rewind", "seek back", "skip back", "rew", "exo_rew",
                "rewind 10", "10 seconds backward", "seek backward",
                "快退", "后退"
            )
        }

        for (root in roots) {
            val node = searchNodeByKeywords(root, searchKeywords)
            if (node != null) return node
        }
        return null
    }

    fun queryVideoPlayingState(): Boolean? {
        val pauseNode = findMediaActionNode(MediaAction.PLAY_PAUSE)
        if (pauseNode != null) {
            val desc = pauseNode.contentDescription?.toString()?.lowercase() ?: ""
            val text = pauseNode.text?.toString()?.lowercase() ?: ""
            val viewId = pauseNode.viewIdResourceName?.lowercase() ?: ""
            val combined = "$desc $text $viewId"
            if (combined.contains("pause") || combined.contains("暂停")) {
                return true
            }
            if (combined.contains("play") || combined.contains("播放")) {
                return false
            }
        }
        return null
    }

    private fun searchNodeByKeywords(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isMatch = keywords.any { kw ->
            desc.contains(kw) || text.contains(kw) || viewId.contains(kw)
        }
        if (isMatch && node.isClickable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = searchNodeByKeywords(child, keywords)
            if (res != null) return res
        }
        return null
    }
}
