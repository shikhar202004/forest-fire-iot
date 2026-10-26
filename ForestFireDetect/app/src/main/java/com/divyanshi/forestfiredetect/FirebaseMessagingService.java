package com.divyanshi.forestfiredetect;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class FirebaseMessagingService
        extends com.google.firebase.messaging.FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Save new token to Firestore whenever it refreshes
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("fcm_tokens")
                    .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .set(data);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        String title = "🔥 Fire Alert";
        String body  = "Fire risk detected!";

        if (msg.getNotification() != null) {
            title = msg.getNotification().getTitle();
            body  = msg.getNotification().getBody();
        } else if (!msg.getData().isEmpty()) {
            // Handle data messages
            title = msg.getData().getOrDefault("title", title);
            body  = msg.getData().getOrDefault("body", body);
        }

        sendNotification(title, body);
    }

    private void sendNotification(String title, String body) {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Create channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    "fire_alerts",
                    "Fire Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Forest fire detection alerts");
            ch.enableVibration(true);
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        }

        // Tap notification → open app
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "fire_alerts")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setVibrate(new long[]{0, 500, 200, 500});

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }
}