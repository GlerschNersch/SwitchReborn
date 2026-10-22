package dev.switchreborn

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Drives the "new" dot on the News button. Only the count of dismissable notifications is
 * used; nothing about their content is read or kept.
 */
class NewsListener : NotificationListenerService() {
    override fun onListenerConnected() { connected = true; publish() }
    override fun onListenerDisconnected() { connected = false; hasNews = false; onChange?.invoke() }
    override fun onNotificationPosted(sbn: StatusBarNotification) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish()

    private fun publish() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        hasNews = list.any { it.isClearable && !it.isOngoing && it.packageName != packageName }
        onChange?.invoke()
    }

    companion object {
        @Volatile var hasNews = false
            private set
        @Volatile private var connected = false
        /** Set by the activity while it's alive; called on the listener's thread. */
        @Volatile var onChange: (() -> Unit)? = null

        /**
         * Some OEM builds (ColorOS here) don't bind a listener right after access is granted;
         * asking for a rebind when HOME comes back brings it up.
         */
        fun ensureBound(context: Context) {
            if (!connected && isAllowed(context)) requestRebind(ComponentName(context, NewsListener::class.java))
        }

        fun isAllowed(context: Context): Boolean {
            val me = ComponentName(context, NewsListener::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { it == me }
        }
    }
}
