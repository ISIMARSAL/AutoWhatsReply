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

        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        // 1. Verificar si la auto-respuesta está ACTIVADA
        val isEnabled = prefs.getBoolean("is_enabled", true)
        if (!isEnabled) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return

        val triggerMessage = prefs.getString("trigger_message", "")?.trim() ?: ""
        val replyMessage = prefs.getString("reply_message", "Hola, bienvenido al evento.")?.trim() ?: ""

        // Prevent Loop: Ignorar si el mensaje entrante coincide con nuestra propia respuesta
        if (text.equals(replyMessage, ignoreCase = true) || text.contains(replyMessage, ignoreCase = true)) {
            return
        }

        if (text.contains("Responder") || text.contains("Respondiendo") || text.contains("WhatsApp")) {
            return
        }

        // 2. Comprobar la SIMILITUD del mensaje recibido
        if (triggerMessage.isNotEmpty()) {
            if (!isSimilarMatch(incomingText = text, targetTrigger = triggerMessage)) {
                return
            }
        }

        extractAndSendReply(notification, replyMessage)
    }

    /**
     * Evalúa si un mensaje recibido es lo suficientemente similar a la frase objetivo.
     */
    private fun isSimilarMatch(incomingText: String, targetTrigger: String): Boolean {
        val cleanIncoming = incomingText.lowercase().replace(Regex("[^a-z0-9áéíóúñ ]"), "")
        val cleanTarget = targetTrigger.lowercase().replace(Regex("[^a-z0-9áéíóúñ ]"), "")

        // a) Coincidencia directa o parcial
        if (cleanIncoming.contains(cleanTarget) || cleanTarget.contains(cleanIncoming)) {
            return true
        }

        // b) Coincidencia por palabras clave (ej: "hola", "evento")
        val targetWords = cleanTarget.split(" ").filter { it.length > 3 }
        var matchedWords = 0
        for (word in targetWords) {
            if (cleanIncoming.contains(word)) {
                matchedWords++
            }
        }
        if (targetWords.isNotEmpty() && (matchedWords.toDouble() / targetWords.size.toDouble()) >= 0.5) {
            return true
        }

        // c) Algoritmo de distancia de Levenshtein (tolerancia a errores tipográficos)
        val similarity = calculateSimilarity(cleanIncoming, cleanTarget)
        return similarity >= 0.65 // 65% de similitud suficiente
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 1.0
        val distance = levenshteinDistance(s1, s2)
        return (maxLength - distance).toDouble() / maxLength.toDouble()
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
