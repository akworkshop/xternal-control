package com.xternal.control.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.xternal.control.media.MediaSessionRemoteManager

class MediaNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, MediaNotificationListenerService::class.java)
        }

        fun isAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        MediaSessionRemoteManager.instance?.onNotificationListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        isConnected = false
        MediaSessionRemoteManager.instance?.onNotificationListenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        MediaSessionRemoteManager.instance?.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        MediaSessionRemoteManager.instance?.onNotificationRemoved(sbn)
    }
}
