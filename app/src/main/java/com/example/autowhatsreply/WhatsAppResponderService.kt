package com.example.autowhatsreply

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppResponderService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val packageName = sbn.packageName

        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("is_enabled", false)
            if (!isEnabled) return

            val extras = sbn.notification.extras
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val triggerPhrase = prefs.getString("trigger_phrase", "") ?: ""
            val replyMessage = prefs.getString("reply_message", "") ?: ""

            if (triggerPhrase.isNotEmpty() && text.contains(triggerPhrase, ignoreCase = true)) {
                sendAutoReply(sbn.notification, replyMessage)
            }
        }
    }

    private fun sendAutoReply(notification: Notification, message: String) {
        val actions = notification.actions ?: return
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (action.title.toString().contains("Reply", ignoreCase = true) ||
                    action.title.toString().contains("Responder", ignoreCase = true)) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, message)
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                    try {
                        action.actionIntent.send(this, 0, intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    break
                }
            }
        }
    }
}
