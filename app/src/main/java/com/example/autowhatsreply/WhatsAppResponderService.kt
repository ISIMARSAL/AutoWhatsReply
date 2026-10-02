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

    companion object {
        // Guardar timestamp del último mensaje respondido para evitar responder 2 veces a la misma notificación
        private val lastProcessedMessages = HashMap<String, Long>()

        /**
         * Retorna el porcentaje exacto (0 - 100) de similitud entre dos textos.
         */
        fun calculateMatchPercentage(incoming: String, target: String): Int {
            val s1 = cleanString(incoming)
            val s2 = cleanString(target)

            if (s1.isEmpty() || s2.isEmpty()) return 0
            if (s1 == s2) return 100

            // 1. Similitud global por Levenshtein
            val maxLen = maxOf(s1.length, s2.length)
            val distance = levenshteinDistance(s1, s2)
            val levScore = ((maxLen - distance).toDouble() / maxLen.toDouble()) * 100.0

            // 2. Coincidencia por palabras clave
            val words1 = s1.split(" ").filter { it.isNotEmpty() }
            val words2 = s2.split(" ").filter { it.isNotEmpty() }

            var matchedWords = 0
            for (w2 in words2) {
                if (words1.contains(w2)) {
                    matchedWords++
                } else {
                    // Tolerancia de 1 letra en palabras de más de 3 letras
                    for (w1 in words1) {
                        if (w2.length > 3 && levenshteinDistance(w1, w2) <= 1) {
                            matchedWords++
                            break
                        }
                    }
                }
            }

            val wordScore = (matchedWords.toDouble() / words2.size.toDouble()) * 100.0

            // Promedio ponderado entre distancia global y palabras
            val finalScore = (levScore * 0.5) + (wordScore * 0.5)
            return finalScore.toInt().coerceIn(0, 100)
        }

        private fun cleanString(str: String): String {
            return str.lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
        }

        private fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[j][0] = j

            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[s1.length][s2.length]
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("is_enabled", false)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return

        // 1. Evitar procesamiento doble del mismo mensaje (Cooldown de 10 segundos por remitente y texto)
        val messageKey = "$title:$text"
        val currentTime = System.currentTimeMillis()
        val lastTime = lastProcessedMessages[messageKey] ?: 0L

        if (currentTime - lastTime < 10000) {
            return // Ignorar duplicado
        }

        // 2. Filtro de chats grupales
        if (prefs.getBoolean("ignore_groups", true)) {
            if (title.contains(":") || title.contains("@g.us")) {
                return
            }
        }

        // 3. Filtro de horario
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

        val replyMessage = prefs.getString("reply_message", "")?.trim() ?: return
        val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
        val requiredPercent = prefs.getInt("similarity_percent", 70)

        if (replyMessage.isEmpty()) return

        // Evitar bucles
        if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
            return
        }

        if (text.contains("WhatsApp") || text.contains("mensajes nuevos") || text.contains("Respondiendo")) {
            return
        }

        // 4. Comparación de similitud
        if (triggerMessage.isNotEmpty()) {
            val score = calculateMatchPercentage(text, triggerMessage)
            if (score < requiredPercent) {
                return
            }
        }

        // Enviar respuesta y guardar en caché
        if (extractAndSendReply(notification, replyMessage)) {
            lastProcessedMessages[messageKey] = currentTime
        }
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
