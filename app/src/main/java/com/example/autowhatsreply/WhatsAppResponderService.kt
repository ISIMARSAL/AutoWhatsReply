package com.example.autowhatsreply

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class WhatsAppResponderService : NotificationListenerService() {

    companion object {
        // Pool de hilos para procesar mensajes en cola sin bloquear notificaciones entrantes
        private val executor = Executors.newSingleThreadExecutor()
        // Registro de los últimos 200 mensajes procesados para evitar responder dos veces al MISMO evento exacto
        private val processedMessageHashes = ConcurrentHashMap.newKeySet<String>()

        fun calculateMatchPercentage(incoming: String, target: String): Int {
            val cleanIn = cleanString(incoming)
            val cleanTar = cleanString(target)

            if (cleanIn.isEmpty() || cleanTar.isEmpty()) return 0
            if (cleanIn == cleanTar) return 100

            if (cleanIn.contains(cleanTar) || cleanTar.contains(cleanIn)) {
                val ratio = minOf(cleanIn.length, cleanTar.length).toDouble() / maxOf(cleanIn.length, cleanTar.length).toDouble()
                val score = (ratio * 100).toInt()
                if (score >= 50) return maxOf(score, 85)
            }

            val maxLen = maxOf(cleanIn.length, cleanTar.length)
            val dist = levenshteinDistance(cleanIn, cleanTar)
            val directScore = (((maxLen - dist).toDouble() / maxLen.toDouble()) * 100).toInt()

            if (dist <= 2) {
                return maxOf(directScore, 80)
            }

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

        // Procesar en segundo plano para no perder notificaciones cuando entran de varios teléfonos a la vez
        executor.execute {
            processNotification(notification, extras, sbn.postTime, prefs)
        }
    }

    private fun processNotification(notification: Notification, extras: Bundle, postTime: Long, prefs: android.content.SharedPreferences) {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""

        // Filtro de grupos
        if (prefs.getBoolean("ignore_groups", true)) {
            if (title.contains(":") || title.contains("@g.us")) {
                return
            }
        }

        val replyMessage = prefs.getString("reply_message", "")?.trim() ?: return
        val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
        val requiredPercent = prefs.getInt("similarity_percent", 50)

        if (replyMessage.isEmpty()) return

        // Extraer mensaje exacto
        val incomingMessage = extractLatestIncomingText(extras) ?: return

        // Ignorar si el mensaje extraído es idéntico a la respuesta que enviamos
        if (incomingMessage.equals(replyMessage, ignoreCase = true)) {
            return
        }

        // Crear una clave única usando título + mensaje + timestamp del sistema
        val uniqueMessageId = "$title|$incomingMessage|$postTime"
        if (processedMessageHashes.contains(uniqueMessageId)) {
            return
        }

        // Evaluar similitud con el disparador
        if (triggerMessage.isNotEmpty()) {
            val score = calculateMatchPercentage(incomingMessage, triggerMessage)
            if (score < requiredPercent) {
                return
            }
        }

        // Responder
        if (extractAndSendReply(notification, replyMessage)) {
            processedMessageHashes.add(uniqueMessageId)
            // Mantener el tamaño de la memoria hash controlado
            if (processedMessageHashes.size > 200) {
                processedMessageHashes.clear()
            }
        }
    }

    private fun extractLatestIncomingText(extras: Bundle): String? {
        // 1. Prioridad: Notificaciones de tipo MessagingStyle (recorre los mensajes reales)
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            for (i in messages.indices.reversed()) {
                val b = messages[i] as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    return text
                }
            }
        }

        // 2. Extraer de las líneas de texto acumuladas (EXTRA_TEXT_LINES)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (textLines != null && textLines.isNotEmpty()) {
            val lastLine = textLines.last()?.toString()?.trim()
            if (!lastLine.isNullOrEmpty()) {
                return if (lastLine.contains(": ")) {
                    lastLine.substringAfter(": ").trim()
                } else {
                    lastLine
                }
            }
        }

        // 3. Texto estándar de la notificación
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!text.isNullOrEmpty() && !text.contains("mensajes nuevos") && !text.contains("Respondiendo a")) {
            return text
        }

        return null
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
