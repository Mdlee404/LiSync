package mindrift.app.music;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import mindrift.app.music.core.cache.CacheManager;
import mindrift.app.music.core.proxy.RequestProxy;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.service.KeepAliveService;
import mindrift.app.music.ui.AgreementActivity;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.NotificationHelper;
import mindrift.app.music.wearable.XiaomiWearableManager;
import androidx.core.content.ContextCompat;
import android.os.Build;

public class App extends Application {
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private XiaomiWearableManager wearableManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Deque<ScriptManager.UpdateAlert> pendingAlerts = new ArrayDeque<>();
    private final Object alertLock = new Object();
    private WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private boolean alertShowing = false;
    private volatile boolean shuttingDown = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init();
        cacheManager = new CacheManager(this);
        scriptManager = new ScriptManager(this);
        requestProxy = new RequestProxy(this, scriptManager, cacheManager);
        wearableManager = new XiaomiWearableManager(this, requestProxy, scriptManager);
        scriptManager.addChangeListener(() -> wearableManager.notifyCapabilitiesChanged());
        scriptManager.addUpdateAlertListener(this::handleUpdateAlert);
        registerActivityLifecycleCallbacks(activityCallbacks);
        wearableManager.start();
        startKeepAliveService();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        shutdown();
    }

    private final ActivityLifecycleCallbacks activityCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityResumed(Activity activity) {
            currentActivity = new WeakReference<>(activity);
            maybeShowPendingAlerts();
        }

        @Override
        public void onActivityPaused(Activity activity) {
            Activity current = currentActivity.get();
            if (current == activity) {
                currentActivity = new WeakReference<>(null);
            }
        }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    };

    private void handleUpdateAlert(ScriptManager.UpdateAlert alert) {
        if (alert == null) return;
        mainHandler.post(() -> {
            synchronized (alertLock) {
                pendingAlerts.add(alert);
            }
            maybeShowPendingAlerts();
        });
    }

    private void maybeShowPendingAlerts() {
        Activity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activity instanceof AgreementActivity) {
            return;
        }
        ScriptManager.UpdateAlert alert;
        synchronized (alertLock) {
            if (alertShowing) return;
            alert = pendingAlerts.pollFirst();
            if (alert == null) return;
            alertShowing = true;
        }
        showUpdateAlertDialog(activity, alert);
    }

    private void showUpdateAlertDialog(Activity activity, ScriptManager.UpdateAlert alert) {
        String title = (alert.name == null || alert.name.trim().isEmpty())
                ? "脚本更新提示"
                : "源更新：" + alert.name.trim();
        String message = alert.log == null ? "" : alert.log;
        if (message.trim().isEmpty()) {
            message = "脚本提示有更新内容，请查看更新信息。";
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setOnDismissListener(dialog -> {
                    synchronized (alertLock) {
                        alertShowing = false;
                    }
                    maybeShowPendingAlerts();
                });
        if (alert.updateUrl != null && alert.updateUrl.startsWith("http")) {
            builder.setPositiveButton("去更新", (dialog, which) -> openUpdateUrl(activity, alert.updateUrl))
                    .setNegativeButton("关闭", null);
        } else {
            builder.setPositiveButton("知道了", null);
        }
        builder.show();
    }

    private void openUpdateUrl(Activity activity, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        } catch (Exception e) {
            Logger.warn("Open update url failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        try {
            unregisterActivityLifecycleCallbacks(activityCallbacks);
        } catch (Exception ignored) {
        }
        mainHandler.removeCallbacksAndMessages(null);
        synchronized (alertLock) {
            pendingAlerts.clear();
            alertShowing = false;
        }
        stopKeepAliveService();
        if (wearableManager != null) {
            wearableManager.stop();
        }
        if (requestProxy != null) {
            requestProxy.shutdown();
        }
        if (scriptManager != null) {
            scriptManager.shutdown();
        }
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
    }

    private void startKeepAliveService() {
        if (!NotificationHelper.canPost(this)) {
            Logger.warn("Notification permission missing, keep-alive disabled");
            return;
        }
        NotificationHelper.ensureChannels(this);
        Intent intent = new Intent(this, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Logger.warn("Start keep-alive service failed: " + e.getMessage());
        }
    }

    private void stopKeepAliveService() {
        try {
            stopService(new Intent(this, KeepAliveService.class));
        } catch (Exception ignored) {
        }
    }

    public void ensureKeepAliveService() {
        startKeepAliveService();
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public RequestProxy getRequestProxy() {
        return requestProxy;
    }

    public XiaomiWearableManager getWearableManager() {
        return wearableManager;
    }
}








