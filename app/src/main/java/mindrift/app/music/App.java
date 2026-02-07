package mindrift.app.music;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mindrift.app.music.core.cache.CacheManager;
import mindrift.app.music.core.network.HttpClient;
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
    private final Object appUpdateLock = new Object();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final HttpClient updateHttpClient = new HttpClient();
    private final Gson gson = new Gson();
    private AppUpdateInfo pendingAppUpdate;
    private boolean appUpdateShowing = false;
    private boolean appUpdateChecked = false;
    private volatile boolean shuttingDown = false;
    private static final String UPDATE_MANIFEST_URL =
            "https://version.mindrift.cn/HeMusicVersion.json";
    private static final String UPDATE_PREFS = "app_update";
    private static final String KEY_LAST_SHOWN_VERSION = "last_shown_version";
    private static final String KEY_LAST_SHOWN_LOCAL_VERSION = "last_shown_local_version";
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");

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
            maybeCheckAppUpdate();
            maybeShowAppUpdate();
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
                    maybeShowAppUpdate();
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
        try {
            updateExecutor.shutdown();
        } catch (Exception ignored) {
        }
        if (updateHttpClient != null) {
            updateHttpClient.shutdown();
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

    private void maybeCheckAppUpdate() {
        if (appUpdateChecked) return;
        appUpdateChecked = true;
        updateExecutor.execute(this::checkAppUpdateInternal);
    }

    private void checkAppUpdateInternal() {
        try {
            HashMap<String, Object> options = new HashMap<>();
            options.put("method", "GET");
            HttpClient.ResponseData response = updateHttpClient.requestSync(
                    UPDATE_MANIFEST_URL,
                    options
            );
            if (response == null || response.code != 200) {
                Logger.warn("Update check failed: http=" + (response == null ? "null" : response.code));
                return;
            }
            AppUpdateInfo info = parseUpdateInfo(response.body);
            if (info == null) {
                return;
            }
            String localVersion = getLocalVersionName();
            boolean showCurrentVersionNotes = shouldShowCurrentVersionNotes(localVersion);
            boolean androidUpdate = hasAndroidUpdate(info);
            if (!showCurrentVersionNotes && !androidUpdate) {
                return;
            }
            if (!showCurrentVersionNotes) {
                String showKey = buildUpdateKey(info);
                String lastShown = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                        .getString(KEY_LAST_SHOWN_VERSION, "");
                if (showKey.equals(lastShown)) {
                    return;
                }
            }
            info.showCurrentVersionNotes = showCurrentVersionNotes;
            info.androidUpdateAvailable = androidUpdate;
            info.forceUpdateRequired = androidUpdate;
            info.localVersionAtCheck = localVersion;
            mainHandler.post(() -> {
                synchronized (appUpdateLock) {
                    pendingAppUpdate = info;
                }
                maybeShowAppUpdate();
            });
        } catch (Exception e) {
            Logger.warn("Update check failed: " + e.getMessage());
        }
    }

    private AppUpdateInfo parseUpdateInfo(String body) {
        try {
            UpdatePayload payload = gson.fromJson(body, UpdatePayload.class);
            if (payload == null) return null;
            AppUpdateInfo info = new AppUpdateInfo();
            if (payload.phone != null) {
                info.androidVersion = payload.phone.version;
                info.phoneUpdateNotes = sanitizeNotes(payload.phone.updateNotes);
                info.phoneDownloadUrl = payload.phone.downloadUrl;
            }
            if (payload.watch != null) {
                info.watchVersion = payload.watch.version;
                info.watchUpdateNotes = sanitizeNotes(payload.watch.updateNotes);
            }
            return info;
        } catch (Exception e) {
            Logger.warn("Update payload parse failed: " + e.getMessage());
            return null;
        }
    }

    private void maybeShowAppUpdate() {
        Activity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activity instanceof AgreementActivity) {
            return;
        }
        synchronized (alertLock) {
            if (alertShowing) {
                return;
            }
        }
        AppUpdateInfo info;
        synchronized (appUpdateLock) {
            if (appUpdateShowing) return;
            info = pendingAppUpdate;
            if (info == null) return;
            appUpdateShowing = true;
            pendingAppUpdate = null;
        }
        showAppUpdateDialog(activity, info);
    }

    private void showAppUpdateDialog(Activity activity, AppUpdateInfo info) {
        boolean forceUpdateRequired = info != null && info.forceUpdateRequired;
        String title = forceUpdateRequired
                ? "发现新版本（必须更新）"
                : (info != null && info.showCurrentVersionNotes ? "版本更新内容" : "应用更新提示");
        String message = buildAppUpdateMessage(info);
        boolean canOpenDownload = info != null
                && info.androidUpdateAvailable
                && info.phoneDownloadUrl != null
                && info.phoneDownloadUrl.startsWith("http");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(!forceUpdateRequired)
                .setOnDismissListener(dialog -> {
                    synchronized (appUpdateLock) {
                        appUpdateShowing = false;
                    }
                    android.content.SharedPreferences.Editor editor = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                            .edit();
                    if (info != null && info.androidUpdateAvailable && !info.forceUpdateRequired) {
                        editor.putString(KEY_LAST_SHOWN_VERSION, buildUpdateKey(info));
                    }
                    if (info != null && info.showCurrentVersionNotes) {
                        String localVersion = info.localVersionAtCheck == null ? "" : info.localVersionAtCheck.trim();
                        editor.putString(KEY_LAST_SHOWN_LOCAL_VERSION, localVersion);
                    }
                    editor.apply();
                    maybeShowPendingAlerts();
                });
        if (forceUpdateRequired) {
            if (canOpenDownload) {
                builder.setPositiveButton("去更新", (dialog, which) -> {
                    openUpdateUrl(activity, info.phoneDownloadUrl);
                    forceCloseApp(activity);
                });
            } else {
                builder.setPositiveButton("退出应用", (dialog, which) -> forceCloseApp(activity));
            }
        } else if (canOpenDownload) {
            builder.setPositiveButton("去更新", (dialog, which) -> openUpdateUrl(activity, info.phoneDownloadUrl))
                    .setNegativeButton("知道了", null);
        } else {
            builder.setPositiveButton("知道了", null);
        }
        builder.show();
    }

    private String buildUpdateKey(AppUpdateInfo info) {
        if (info == null) return "";
        String android = info.androidVersion == null ? "" : info.androidVersion.trim();
        String watch = info.watchVersion == null ? "" : info.watchVersion.trim();
        return android + "|" + watch;
    }

    private String buildAppUpdateMessage(AppUpdateInfo info) {
        StringBuilder builder = new StringBuilder();
        String localVersion = info != null && info.localVersionAtCheck != null
                ? info.localVersionAtCheck
                : getLocalVersionName();
        if (info.androidVersion != null && !info.androidVersion.trim().isEmpty()) {
            if (!info.showCurrentVersionNotes && info.androidUpdateAvailable) {
                builder.append("发现手机新版本 ").append(info.androidVersion.trim());
            } else {
                builder.append("手机端版本：").append(info.androidVersion.trim());
            }
            if (!localVersion.trim().isEmpty()) {
                builder.append("（当前 ").append(localVersion.trim()).append("）");
            }
        }
        if (info.phoneUpdateNotes != null && !info.phoneUpdateNotes.isEmpty()) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append("手机端更新内容：");
            appendUpdateNotes(builder, info.phoneUpdateNotes);
        }
        if (info.watchVersion != null && !info.watchVersion.trim().isEmpty()) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append("手表端版本：").append(info.watchVersion.trim());
        }
        if (info.watchUpdateNotes != null && !info.watchUpdateNotes.isEmpty()) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append("手表端更新内容：");
            appendUpdateNotes(builder, info.watchUpdateNotes);
        }
        if (builder.length() == 0) {
            builder.append("有新版本可用。");
        }
        if (info != null && info.forceUpdateRequired) {
            builder.append("\n\n该版本为强制更新，不更新无法继续使用。\n点击“去更新”后将退出应用。");
        }
        return builder.toString();
    }

    private void forceCloseApp(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            activity.finishAffinity();
        } catch (Exception ignored) {
        }
    }

    private int compareVersion(String remote, String local) {
        List<Integer> remoteParts = extractVersionNumbers(remote);
        List<Integer> localParts = extractVersionNumbers(local);
        int max = Math.max(remoteParts.size(), localParts.size());
        for (int i = 0; i < max; i++) {
            int r = i < remoteParts.size() ? remoteParts.get(i) : 0;
            int l = i < localParts.size() ? localParts.get(i) : 0;
            if (r != l) {
                return r - l;
            }
        }
        return 0;
    }

    private List<Integer> extractVersionNumbers(String version) {
        if (version == null) return Collections.emptyList();
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(version);
        List<Integer> parts = new ArrayList<>();
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }

    private String getLocalVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info == null || info.versionName == null ? "" : info.versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasAndroidUpdate(AppUpdateInfo info) {
        if (info == null || info.androidVersion == null || info.androidVersion.trim().isEmpty()) {
            return false;
        }
        String localVersion = getLocalVersionName();
        return compareVersion(info.androidVersion, localVersion) > 0;
    }

    private boolean hasWatchUpdate(AppUpdateInfo info) {
        return info != null && info.watchVersion != null && !info.watchVersion.trim().isEmpty();
    }

    private boolean shouldShowCurrentVersionNotes(String localVersion) {
        String current = localVersion == null ? "" : localVersion.trim();
        if (current.isEmpty()) {
            return false;
        }
        String lastShown = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_SHOWN_LOCAL_VERSION, "");
        return !current.equals(lastShown);
    }

    private List<String> sanitizeNotes(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String note : notes) {
            if (note == null) continue;
            String trimmed = note.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private void appendUpdateNotes(StringBuilder builder, List<String> notes) {
        if (builder == null || notes == null || notes.isEmpty()) {
            return;
        }
        for (String note : notes) {
            builder.append("\n- ").append(note);
        }
    }

    private static class UpdatePayload {
        @SerializedName("watch")
        UpdateItem watch;
        @SerializedName("phone")
        PhoneUpdateItem phone;
    }

    private static class UpdateItem {
        @SerializedName("version")
        String version;
        @SerializedName("update_notes")
        List<String> updateNotes;
    }

    private static class PhoneUpdateItem extends UpdateItem {
        @SerializedName("download_url")
        String downloadUrl;
    }

    private static class AppUpdateInfo {
        String watchVersion;
        String androidVersion;
        List<String> watchUpdateNotes;
        List<String> phoneUpdateNotes;
        String phoneDownloadUrl;
        boolean showCurrentVersionNotes;
        boolean androidUpdateAvailable;
        boolean forceUpdateRequired;
        String localVersionAtCheck;
    }
}








