package com.pushwave.sdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Renders an incoming data message.
 *
 * Messages are data-only on purpose: rendering here is what lets us attach the
 * big picture, carry the app's deep-link extras through the tap, and report the
 * notification as received.
 */
final class PushWaveNotification {

    private static final String META_ICON = "com.google.firebase.messaging.default_notification_icon";
    private static final String META_COLOR = "com.google.firebase.messaging.default_notification_color";

    private PushWaveNotification() {
    }

    static void show(Context context, Map<String, String> data) {
        String title = value(data, "title");
        String message = value(data, "message");
        String bigImage = value(data, "big_image");
        String link = value(data, "link");
        String postId = value(data, "post_id");
        String uniqueId = value(data, "unique_id");
        String messageId = value(data, "pw_message_id");
        String launchUrl = value(data, "launch_url");

        Intent intent = new Intent(context, PushWaveOpenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(PushWave.EXTRA_ID, messageId);
        intent.putExtra(PushWave.EXTRA_TITLE, title);
        intent.putExtra(PushWave.EXTRA_MESSAGE, message);
        intent.putExtra(PushWave.EXTRA_IMAGE, bigImage);
        intent.putExtra(PushWave.EXTRA_LAUNCH_URL, launchUrl);
        intent.putExtra(PushWave.EXTRA_UNIQUE_ID, uniqueId);
        intent.putExtra(PushWave.EXTRA_POST_ID, postId);
        intent.putExtra(PushWave.EXTRA_LINK, link);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        // Android 12+ rejects notification trampolines through a receiver or a
        // service, so the tap has to land on an Activity directly.
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) System.currentTimeMillis(), intent, flags);

        String channelId = data.containsKey("android_channel_id")
                ? data.get("android_channel_id")
                : appName(context);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, appName(context), NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(smallIcon(context))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        int color = notificationColor(context);
        if (color != 0) builder.setColor(ContextCompat.getColor(context, color));

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(sound).setVibrate(new long[]{100, 200, 300, 400});

        if (!bigImage.isEmpty()) {
            Bitmap image = fetchBitmap(bigImage);
            if (image != null) {
                builder.setLargeIcon(image)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(image)
                                .bigLargeIcon((Bitmap) null));
            }
        }

        manager.notify(notificationId(postId, messageId), builder.build());
    }

    /** Same post replaces its own notification; unrelated ones stack. */
    private static int notificationId(String postId, String messageId) {
        try {
            if (!postId.isEmpty() && !"0".equals(postId)) return Integer.parseInt(postId);
        } catch (NumberFormatException ignored) {
            // Not numeric: fall through to the hash below.
        }
        if (!messageId.isEmpty()) return messageId.hashCode();
        return (int) System.currentTimeMillis();
    }

    private static String value(Map<String, String> data, String key) {
        String result = data.get(key);
        return result == null ? "" : result;
    }

    /** Reuses the icon the host app already declares for Firebase messaging. */
    private static int smallIcon(Context context) {
        Bundle metaData = metaData(context);
        if (metaData != null) {
            int icon = metaData.getInt(META_ICON, 0);
            if (icon != 0) return icon;
        }
        return context.getApplicationInfo().icon;
    }

    private static int notificationColor(Context context) {
        Bundle metaData = metaData(context);
        return metaData == null ? 0 : metaData.getInt(META_COLOR, 0);
    }

    private static Bundle metaData(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData;
        } catch (Exception error) {
            return null;
        }
    }

    private static String appName(Context context) {
        CharSequence label = context.getApplicationInfo().loadLabel(context.getPackageManager());
        return label == null ? "Notifications" : label.toString();
    }

    private static Bitmap fetchBitmap(String source) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setDoInput(true);
            connection.connect();
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception error) {
            Log.w(PushWave.TAG, "could not download the notification image: " + error.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
