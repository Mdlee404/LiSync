package mindrift.app.music.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import mindrift.app.music.R;
import mindrift.app.music.ui.MainActivity;

public final class NotificationHelper {
    public static final String CHANNEL_STATUS = "lisync_status";
    public static final String CHANNEL_ERROR = "lisync_error";
    public static final int NOTIFY_ID_STATUS = 1001;
    public static final int NOTIFY_ID_ERROR = 2001;
    private static final long ERROR_THROTTLE_MS = 8000L;
    private static volatile long lastErrorAt = 0L;
    private static volatile String lastErrorKey = null;

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel statusChannel = new NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW
        );
        statusChannel.setDescription(context.getString(R.string.notification_channel_status_desc));
        statusChannel.setShowBadge(false);

        NotificationChannel errorChannel = new NotificationChannel(
                CHANNEL_ERROR,
                context.getString(R.string.notification_channel_error),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        errorChannel.setDescription(context.getString(R.string.notification_channel_error_desc));

        manager.createNotificationChannel(statusChannel);
        manager.createNotificationChannel(errorChannel);
    }

    public static boolean canPost(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static Notification buildOngoing(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_status_title))
                .setContentText(context.getString(R.string.notification_status_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build();
    }

    public static void showOngoing(Context context) {
        if (context == null || !canPost(context)) return;
        ensureChannels(context);
        Notification notification = buildOngoing(context);
        NotificationManagerCompat.from(context).notify(NOTIFY_ID_STATUS, notification);
    }

    public static void notifyError(Context context, String title, String message) {
        if (context == null || !canPost(context)) return;
        String safeTitle = title == null || title.trim().isEmpty()
                ? context.getString(R.string.notification_error_title)
                : title.trim();
        String safeMessage = message == null ? "" : message.trim();
        String key = safeTitle + "|" + safeMessage;
        long now = System.currentTimeMillis();
        if (key.equals(lastErrorKey) && (now - lastErrorAt) < ERROR_THROTTLE_MS) {
            return;
        }
        lastErrorKey = key;
        lastErrorAt = now;
        ensureChannels(context);
        String shortMessage = safeMessage.length() > 160 ? safeMessage.substring(0, 160) + "..." : safeMessage;
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ERROR)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safeTitle)
                .setContentText(shortMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeMessage))
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(context).notify(NOTIFY_ID_ERROR, notification);
    }

    public static boolean isStatusNotificationActive(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            StatusBarNotification[] list = manager.getActiveNotifications();
            if (list == null) return false;
            for (StatusBarNotification sbn : list) {
                if (sbn != null && sbn.getId() == NOTIFY_ID_STATUS) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isStatusChannelEnabled(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_STATUS);
            if (channel == null) return true;
            return channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        } catch (Exception ignored) {
        }
        return true;
    }
}
