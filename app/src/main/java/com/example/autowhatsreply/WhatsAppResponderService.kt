package com.example.autowhatsreply

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.RemoteInput

class WhatsAppResponderService : NotificationListenerService() {

    companion object {
        private const val AUTO_REPLY_MESSAGE = "Hola, estoy ocupado ahora mismo. Te responderé más tarde."
        private const val COOLDOWN_TIME_MS = 5 * 60 * 1000L // 5 minutos de espera por contacto
        private val lastRepliedMap = HashMap<String, Long>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return

        if (text.contains(AUTO_REPLY_MESSAGE) || text.contains("Responder") || text.contains("Respondiendo")) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastRepliedMap[title]
        if (lastTime != null && (currentTime - lastTime) < COOLDOWN_TIME_MS) {
            return
        }

        val success = extractAndSendReply(notification)
        if (success) {
            lastRepliedMap[title] = currentTime
        }
    }

    private fun extractAndSendReply(notification: Notification): Boolean {
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, AUTO_REPLY_MESSAGE)

                    val remoteInputArray = arrayOf(remoteInput)
                    android.app.RemoteInput.addResultsToIntent(remoteInputArray, intent, bundle)

                    try {
                        action.actionIntent.send(this, 0, intent)
                        return true
                    } catch (e: PendingIntent.CanceledException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return false
    }
}
