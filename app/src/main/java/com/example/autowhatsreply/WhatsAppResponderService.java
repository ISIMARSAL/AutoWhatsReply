package com.example/autowhatsreply;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.RemoteInput;

public class WhatsAppResponderService extends NotificationListenerService {

    private static final String AUTO_REPLY_MESSAGE = "Hola, estoy ocupado ahora mismo. Te responderé más tarde.";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Filtrar solo las notificaciones de WhatsApp o WhatsApp Business
        if ("com.whatsapp".equals(packageName) || "com.whatsapp.w4b".equals(packageName)) {
            Notification notification = sbn.getNotification();
            if (notification == null) return;

            // Ignorar notificaciones que sean de grupos o de llamadas/sistema si es necesario
            Bundle extras = notification.extras;
            if (extras != null) {
                CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

                if (title != null && text != null) {
                    // Evitar responder a las propias notificaciones del sistema o mensajes enviados
                    extractAndSendReply(notification);
                }
            }
        }
    }

    private void extractAndSendReply(Notification notification) {
        if (notification.actions == null) return;

        for (Notification.Action action : notification.actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                for (android.app.RemoteInput remoteInput : action.getRemoteInputs()) {
                    if (remoteInput.getResultKey() != null) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();

                        // Insertar la respuesta dentro del objeto RemoteInput de la notificación
                        bundle.putCharSequence(remoteInput.getResultKey(), AUTO_REPLY_MESSAGE);
                        android.app.RemoteInput.addResultsToIntent(action.getRemoteInputs(), intent, bundle);

                        try {
                            // Enviar la respuesta automática
                            action.actionIntent.send(this, 0, intent);
                        } catch (PendingIntent.CanceledException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                }
            }
        }
    }
}
