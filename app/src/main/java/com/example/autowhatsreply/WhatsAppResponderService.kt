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
        /**
         * Algoritmo de similitud inteligente con soporte para faltas de 1 letra y % progresivo.
         */
        fun evaluateSimilarity(incoming: String, target: String, requiredPercent: Int): Boolean {
            val cleanIn = cleanString(incoming)
            val cleanTar = cleanString(target)

            if (cleanIn.isEmpty() || cleanTar.isEmpty()) return false

            // 1. Coincidencia exacta
            if (cleanIn == cleanTar) return true

            // 2. Distancia de Levenshtein pura
            val maxLen = maxOf(cleanIn.length, cleanTar.length)
            val distance = levenshteinDistance(cleanIn, cleanTar)
            val distanceScore = ((maxLen - distance).toDouble() / maxLen.toDouble()) * 100.0

            // Si la diferencia es solo de 1 o 2 caracteres en frases medianas/largas, aceptarlo directamente
            if (distance <= 2 && cleanTar.length >= 8 && requiredPercent <= 85) {
                return true
            }

            if (distanceScore >= requiredPercent) {
                return true
            }

            // 3. Análisis por palabras clave
            val targetWords = cleanTar.split(" ").filter { it.isNotEmpty() }
            val incomingWords = cleanIn.split(" ").filter { it.isNotEmpty() }

            if (targetWords.isEmpty()) return false

            var matchedWords = 0
            for (tWord in targetWords) {
                for (iWord in incomingWords) {
                    val wordDist = levenshteinDistance(tWord, iWord)
                    // Si la palabra varía solo por 1 letra
                    if (wordDist <= 1 && tWord.length > 2) {
                        matchedWords++
                        break
                    }
                }
            }

            val wordScore = (matchedWords.toDouble() / targetWords.size.toDouble()) * 100.0
            return wordScore >= requiredPercent
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
        processAllNotifications()
    }

    /**
     * Recorre TODAS las notificaciones activas para asegurarse de procesar
     * múltiples mensajes pendientes y no dejar ninguno sin revisar.
     */
    private fun processAllNotifications() {
        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("is_enabled", false)) return

        val activeNotifications = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return

        for (sbn in activeNotifications) {
            val packageName = sbn.packageName
            if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
                continue
            }

            val notification = sbn.notification ?: continue
            val extras = notification.extras ?: continue

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: continue

            // Ignorar grupos si está activado
            if (prefs.getBoolean("ignore_groups", true)) {
                if (title.contains(":") || title.contains("@g.us")) {
                    continue
                }
            }

            // Horario
            if (prefs.getBoolean("schedule_enabled", false)) {
                val start = prefs.getString("start_hour", "")?.toIntOrNull()
                val end = prefs.getString("end_hour", "")?.toIntOrNull()
                if (start != null && end != null) {
                    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    if (!isHourInInterval(currentHour, start, end)) {
                        continue
                    }
                }
            }

            val replyMessage = prefs.getString("reply_message", "")?.trim() ?: continue
            val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
            val similarityPercent = prefs.getInt("similarity_percent", 70)

            if (replyMessage.isEmpty()) continue

            // Evitar auto-responder a nuestros propios mensajes o resúmenes
            if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
                continue
            }

            if (text.contains("WhatsApp") || text.contains("mensajes nuevos") || text.contains("Respondiendo")) {
                continue
            }

            // Comprobación de similitud
            if (triggerMessage.isNotEmpty()) {
                if (!evaluateSimilarity(text, triggerMessage, similarityPercent)) {
                    continue
                }
            }

            extractAndSendReply(notification, replyMessage)
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
