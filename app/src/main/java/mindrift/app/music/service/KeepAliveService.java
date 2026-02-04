package mindrift.app.music.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import android.content.pm.ServiceInfo;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.NotificationHelper;

public class KeepAliveService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (NotificationHelper.canPost(this)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NotificationHelper.NOTIFY_ID_STATUS,
                            NotificationHelper.buildOngoing(this),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NotificationHelper.NOTIFY_ID_STATUS, NotificationHelper.buildOngoing(this));
                }
            } catch (Exception e) {
                Logger.warn("Start foreground service failed: " + e.getMessage());
                stopSelf();
            }
        } else {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
