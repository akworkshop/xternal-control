package com.xternal.control

import android.os.Handler
import android.os.Looper

object InteractionBridge {
    private val handler = Handler(Looper.getMainLooper())

    var cursorMoveListener: ((Float, Float) -> Unit)? = null
    var clickListener: (() -> Unit)? = null
    var longClickListener: (() -> Unit)? = null
    var rightClickListener: (() -> Unit)? = null
    var scrollListener: ((Float) -> Unit)? = null
    var textInputListener: ((String) -> Unit)? = null
    var appLaunchListener: ((String) -> Unit)? = null
    var zoomListener: ((Boolean) -> Unit)? = null
    var pipModeListener: ((Boolean) -> Unit)? = null
    var pipStateChangedListener: ((Boolean) -> Unit)? = null
    var screenshotRequestListener: (() -> Unit)? = null
    var homeRequestListener: (() -> Unit)? = null
    var backRequestListener: (() -> Unit)? = null
    var appLaunchedFromExternalListener: ((String) -> Unit)? = null
    var foregroundPackageChangedListener: ((String) -> Unit)? = null
    var isKeyboardActive: Boolean = false

    fun sendScreenshotRequest() {
        runOnMain { screenshotRequestListener?.invoke() }
    }

    fun sendHomeRequest() {
        runOnMain { homeRequestListener?.invoke() }
    }

    fun sendBackRequest() {
        runOnMain { backRequestListener?.invoke() }
    }

    fun sendCursorMove(dx: Float, dy: Float) {
        runOnMain { cursorMoveListener?.invoke(dx, dy) }
    }

    fun sendClick() {
        runOnMain { clickListener?.invoke() }
    }

    fun sendLongClick() {
        runOnMain { longClickListener?.invoke() }
    }

    fun sendRightClick() {
        runOnMain { rightClickListener?.invoke() }
    }

    fun sendScroll(scrollDy: Float) {
        runOnMain { scrollListener?.invoke(scrollDy) }
    }

    fun sendTextInput(text: String) {
        runOnMain { textInputListener?.invoke(text) }
    }

    fun sendAppLaunch(packageName: String) {
        runOnMain { appLaunchListener?.invoke(packageName) }
    }

    fun sendZoom(isZoomIn: Boolean) {
        runOnMain { zoomListener?.invoke(isZoomIn) }
    }

    fun sendPipMode(enabled: Boolean) {
        runOnMain { pipModeListener?.invoke(enabled) }
    }

    fun sendPipStateChanged(active: Boolean) {
        runOnMain { pipStateChangedListener?.invoke(active) }
    }

    fun sendAppLaunchedFromExternal(packageName: String) {
        runOnMain { appLaunchedFromExternalListener?.invoke(packageName) }
    }

    fun sendForegroundPackageChanged(packageName: String) {
        runOnMain { foregroundPackageChangedListener?.invoke(packageName) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
}
