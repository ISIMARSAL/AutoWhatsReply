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
        private val lastProcessedMessages = HashMap<String, Long>()

        /**
         * Nuevo Algoritmo Fuzzy Match:
         * Calcula el porcentaje de similitud real permitiendo variaciones de 1 o 2 letras,
         * errores tipográficos y diferencia de orden de palabras.
         */
        fun calculateMatchPercentage(incoming: String, target: String): Int {
            val cleanIn = cleanString(incoming)
            val cleanTar = cleanString(target)

            if (cleanIn.isEmpty() || cleanTar.isEmpty()) return 0
            if (cleanIn == cleanTar) return 100

            // 1. Si la frase o palabra objetivo está dentro del mensaje recibido
            if (cleanIn.contains(cleanTar) || cleanTar.contains(cleanIn)) {
                val ratio = minOf(cleanIn.length, cleanTar.length).toDouble() / maxOf(cleanIn.length, cleanTar.length).toDouble()
                val score = (ratio * 100).toInt()
                if (score >= 50) return maxOf(score, 85)
            }

            // 2. Distancia Levenshtein directa sobre la cadena completa
            val maxLen = maxOf(cleanIn.length, cleanTar.length)
            val dist = levenshteinDistance(cleanIn, cleanTar)
            val directScore = (((maxLen - dist).toDouble() / maxLen.toDouble()) * 100).toInt()

            // Si la diferencia es solo de 1 o 2 letras (ej. quitar/cambiar una letra)
            if (dist <= 2) {
                val boostedScore = maxOf(directScore, 80)
                return boostedScore
            }

            // 3. Comparación parcial por subsecuencia / palabras clave
            val inWords = cleanIn.split(" ").filter { it.isNotEmpty() }
            val tarWords = cleanTar.split(" ").filter { it.isNotEmpty() }

            var wordMatches = 0.0
            for (tWord in tarWords) {
                var bestWordScore = 0.0
                for (iWord in inWords) {
                    if (tWord == iWord) {
                        bestWordScore = 1.0
                        break
                    }
                    val wMax = maxOf(tWord.length, iWord.length)
                    val wDist = levenshteinDistance(tWord, iWord)
                    val wScore = (wMax - wDist).toDouble() / wMax.toDouble()
                    if (wScore > bestWordScore) {
                        bestWordScore = wScore
                    }
                }
                wordMatches += bestWordScore
            }

            val tokenScore = ((wordMatches / tarWords.size.toDouble()) * 100).toInt()

            return maxOf(directScore, tokenScore).coerceIn(0, 100)
        }

        private fun cleanString(str: String): String {
            return str.lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("ñ", "n")
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
        }

        private fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[j][0] = j

            for (i 1..s1.length) {
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

        // Anti-duplicados por 10 segundos
        val messageKey = "$title:$text"
        val currentTime = System.currentTimeMillis()
        val lastTime = lastProcessedMessages[messageKey] ?: 0L

        if (currentTime - lastTime < 10000) {
            return
        }

        // Filtro de grupos
        if (prefs.getBoolean("ignore_groups", true)) {
            if (title.contains(":") || title.contains("@g.us")) {
                return
            }
        }

        // Horarios
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
        val requiredPercent = prefs.getInt("similarity_percent", 50)

        if (replyMessage.isEmpty()) return

        // Evitar responder a sí mismo
        if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
            return
        }

        if (text.contains("WhatsApp") || text.contains("mensajes nuevos") || text.contains("Respondiendo")) {
            return
        }

        // Evaluación de similitud con el nuevo motor
        if (triggerMessage.isNotEmpty()) {
            val score = calculateMatchPercentage(text, triggerMessage)
            if (score < requiredPercent) {
                return
            }
        }

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
