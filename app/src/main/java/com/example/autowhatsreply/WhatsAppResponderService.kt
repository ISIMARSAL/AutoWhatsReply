package com.example.autowhatsreply

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppResponderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        // Filtrar solo WhatsApp y WhatsApp Business
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return

        // Cargar configuración guardada en la App
        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)
        val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
        val replyMessage = prefs.getString("reply_message", "Hola, estoy ocupado ahora mismo.")?.trim() ?: ""

        // 1. PREVENCIÓN DE BUCLE: Ignorar la notificación si el texto recibido es la propia respuesta enviada por la app
        if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
            return
        }

        // Ignorar textos de sistema de WhatsApp
        if (text.contains("Responder") || text.contains("Respondiendo") || text.contains("WhatsApp")) {
            return
        }

        // 2. FILTRO DE COINCIDENCIA: Si se especificó un disparador, verificar que el mensaje recibido lo contenga
        if (triggerMessage.isNotEmpty() && !text.contains(triggerMessage, ignoreCase = true)) {
            return
        }

        // 3. Enviar la respuesta personalizada
        extractAndSendReply(notification, replyMessage)
    }

    private fun extractAndSendReply(notification: Notification, replyMessage: String): Boolean {
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyMessage)

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
