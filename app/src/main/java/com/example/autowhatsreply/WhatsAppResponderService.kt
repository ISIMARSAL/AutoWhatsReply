package com.example.autowhatsreply

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

class WhatsAppResponderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        // 1. Verificación en tiempo real del Switch
        if (!prefs.getBoolean("is_enabled", false)) return

        // 2. Filtro de Horario (si está activado)
        if (prefs.getBoolean("schedule_enabled", false)) {
            val start = prefs.getString("start_hour", "")?.toIntOrNull()
            val end = prefs.getString("end_hour", "")?.toIntOrNull()
            if (start != null && end != null) {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (!isHourInInterval(currentHour, start, end)) {
                    return
                }
            }
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extraer texto completo del mensaje
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return
        val replyMessage = prefs.getString("reply_message", "")?.trim() ?: return
        val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
        val similarityPercent = prefs.getInt("similarity_percent", 65)

        if (replyMessage.isEmpty()) return

        // Evitar auto-responder a notificaciones propias
        if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
            return
        }

        if (text.contains("WhatsApp") || text.contains("mensajes nuevos") || text.contains("Respondiendo")) {
            return
        }

        // 3. Comparación precisa por Porcentaje de Similitud
        if (triggerMessage.isNotEmpty()) {
            if (!checkMatchWithPercent(incoming = text, target = triggerMessage, requiredPercent = similarityPercent)) {
                return
            }
        }

        extractAndSendReply(notification, replyMessage)
    }

    private fun checkMatchWithPercent(incoming: String, target: String, requiredPercent: Int): Boolean {
        val cleanIn = cleanString(incoming)
        val cleanTar = cleanString(target)

        // Si exige 100%, debe coincidir exactamente
        if (requiredPercent >= 100) {
            return cleanIn == cleanTar
        }

        if (cleanIn.contains(cleanTar) || cleanTar.contains(cleanIn)) {
            return true
        }

        // Cálculo por palabras clave e intersección
        val targetWords = cleanTar.split(" ").filter { it.isNotEmpty() }
        if (targetWords.isEmpty()) return false

        var matched = 0
        for (word in targetWords) {
            if (cleanIn.contains(word)) matched++
        }

        val matchRatio = (matched.toDouble() / targetWords.size.toDouble()) * 100.0
        return matchRatio >= requiredPercent
    }

    private fun cleanString(str: String): String {
        return str.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
    }

    private fun isHourInInterval(current: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            current in start until end
        } else {
            current >= start || current < end
        }
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
