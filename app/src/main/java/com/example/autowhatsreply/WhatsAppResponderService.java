package com.example.autowhatsreply;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;

public class WhatsAppResponderService extends NotificationListenerService {

    private static final String AUTO_REPLY_MESSAGE = "Hola, estoy ocupado ahora mismo. Te responderé más tarde.";
    // Tiempo de espera entre respuestas al mismo contacto (5 minutos en milisegundos)
    private static final long COOLDOWN_TIME_MS = 5 * 60 * 1000;
    
    // Guardar el historial de último envío por contacto
    private static final Map<String, Long> lastRepliedMap = new HashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // 1. Filtrar solo WhatsApp o WhatsApp Business
        if (!"com.whatsapp".equals(packageName) && !"com.whatsapp.w4b".equals(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        // 2. Ignorar notificaciones de resumen del sistema o mensajes salientes propios
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (title == null || text == null) return;

        String sender = title.toString().trim();
        String messageText = text.toString().trim();

        // Ignorar si el texto coincide con la propia respuesta para evitar bucle interno
        if (messageText.contains(AUTO_REPLY_MESSAGE) || messageText.contains("Responder") || messageText.contains("Respondiendo")) {
            return;
        }

        // 3. Control de Cooldown: Evitar responder al mismo remitente en menos de 5 minutos
        long currentTime = System.currentTimeMillis();
        if (lastRepliedMap.containsKey(sender)) {
            long lastTime = lastRepliedMap.get(sender);
            if (currentTime - lastTime < COOLDOWN_TIME_MS) {
                // Ya se le respondió recientemente, ignorar
                return;
            }
        }

        // 4. Intentar enviar la respuesta
        boolean success = extractAndSendReply(notification);
        if (success) {
            // Actualizar la hora en la que se le respondió por última vez
            lastRepliedMap.put(sender, currentTime);
        }
    }

    private boolean extractAndSendReply(Notification notification) {
        if (notification.actions == null) return false;

        for (Notification.Action action : notification.actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                for (android.app.RemoteInput remoteInput : action.getRemoteInputs()) {
                    if (remoteInput.getResultKey() != null) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();

                        bundle.putCharSequence(remoteInput.getResultKey(), AUTO_REPLY_MESSAGE);
                        android.app.RemoteInput.addResultsToIntent(action.getRemoteInputs(), intent, bundle);

                        try {
                            action.actionIntent.send(this, 0, intent);
                            return true;
                        } catch (PendingIntent.CanceledException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return false;
    }
}
