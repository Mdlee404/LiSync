package mindrift.app.music.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.core.cache.CacheEntry;
import mindrift.app.music.core.cache.CacheManager;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.utils.NotificationHelper;
import mindrift.app.music.wearable.XiaomiWearableManager;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_NOTIFICATIONS = 1101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private XiaomiWearableManager wearableManager;
    private TextView scriptCountText;
    private TextView cacheCountText;
    private TextView lastUpdatedText;
    private TextView deviceStatusText;
    private ActivityResultLauncher<String[]> uploadLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable deviceStatusRefresh = this::updateDeviceStatus;
    private final Runnable deviceStatusRefreshDelayed = this::updateDeviceStatus;
    private final Runnable notificationCheck = this::checkNotificationStatus;
    private boolean serviceDialogShown = false;
    private boolean deviceDialogShown = false;
    private boolean notificationDialogShown = false;
    private long lastNotificationPromptAt = 0L;
    private static final long NOTIFICATION_CHECK_INTERVAL_MS = 5_000L;
    private static final String EXTRA_UPLOAD_URI = "uploadUri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();

        App app = (App) getApplication();
        cacheManager = app.getCacheManager();
        scriptManager = app.getScriptManager();
        wearableManager = app.getWearableManager();

        scriptCountText = findViewById(R.id.text_script_count);
        cacheCountText = findViewById(R.id.text_cache_count);
        lastUpdatedText = findViewById(R.id.text_last_updated);
        deviceStatusText = findViewById(R.id.text_device_status);

        MaterialButton refreshButton = findViewById(R.id.button_refresh);
        MaterialButton clearCacheButton = findViewById(R.id.button_clear_cache);
        MaterialButton openThemeTransferButton = findViewById(R.id.button_open_theme_transfer_home);
        MaterialButton openScriptCenterButton = findViewById(R.id.button_open_script_center);
        MaterialButton openToolsButton = findViewById(R.id.button_open_tools);

        refreshButton.setOnClickListener(v -> {
            requestWearableRefresh();
            refreshData();
        });
        clearCacheButton.setOnClickListener(v -> {
            cacheManager.clear();
            refreshData();
        });
        uploadLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleUploadFile);
        openThemeTransferButton.setOnClickListener(v -> startActivity(new Intent(this, ThemeTransferActivity.class)));
        openScriptCenterButton.setOnClickListener(v -> startActivity(new Intent(this, ScriptCenterActivity.class)));
        openToolsButton.setOnClickListener(v -> startActivity(new Intent(this, ToolsActivity.class)));

        refreshData();
        handleUploadFromIntent(getIntent());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                App app = (App) getApplication();
                app.ensureKeepAliveService();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(deviceStatusRefresh);
        mainHandler.removeCallbacks(deviceStatusRefreshDelayed);
        executor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App app = (App) getApplication();
        app.ensureKeepAliveService();
        scheduleNotificationCheck(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleUploadFromIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(notificationCheck);
    }

    private void refreshData() {
        refreshData(null);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            App app = (App) getApplication();
            app.ensureKeepAliveService();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private void scheduleNotificationCheck(boolean immediate) {
        mainHandler.removeCallbacks(notificationCheck);
        if (immediate) {
            mainHandler.post(notificationCheck);
        } else {
            mainHandler.postDelayed(notificationCheck, NOTIFICATION_CHECK_INTERVAL_MS);
        }
    }

    private void checkNotificationStatus() {
        if (isFinishing() || isDestroyed()) return;
        boolean permissionOk = NotificationHelper.canPost(this);
        boolean appEnabled = NotificationHelper.areNotificationsEnabled(this);
        boolean channelOk = NotificationHelper.isStatusChannelEnabled(this);
        if (!permissionOk) {
            showNotificationPrompt(getString(R.string.notification_prompt_title),
                    getString(R.string.notification_prompt_permission));
            scheduleNotificationCheck(false);
            return;
        }
        if (!appEnabled) {
            showNotificationPrompt(getString(R.string.notification_prompt_title),
                    getString(R.string.notification_prompt_disabled));
            scheduleNotificationCheck(false);
            return;
        }
        if (!channelOk) {
            showNotificationPrompt(getString(R.string.notification_prompt_title),
                    getString(R.string.notification_prompt_channel));
            scheduleNotificationCheck(false);
            return;
        }
        App app = (App) getApplication();
        app.ensureKeepAliveService();
        if (!NotificationHelper.isStatusNotificationActive(this)) {
            NotificationHelper.showOngoing(this);
        }
        scheduleNotificationCheck(false);
    }

    private void showNotificationPrompt(String title, String message) {
        long now = System.currentTimeMillis();
        if (notificationDialogShown && (now - lastNotificationPromptAt) < NOTIFICATION_CHECK_INTERVAL_MS) return;
        notificationDialogShown = true;
        lastNotificationPromptAt = now;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.notification_prompt_settings), (dialog, which) -> openNotificationSettings())
                .setNegativeButton(getString(R.string.notification_prompt_ignore), (dialog, which) -> {
                    notificationDialogShown = false;
                })
                .setOnDismissListener(dialog -> notificationDialogShown = false)
                .show();
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void refreshData(String preferredScriptId) {
        executor.execute(() -> {
            List<CacheEntry> entries = cacheManager.list();
            List<ScriptManager.ScriptEntry> loadedScripts = scriptManager.getLoadedScripts();
            String lastUpdated = getString(R.string.updated_at, DateFormat.getDateTimeInstance().format(new Date()));
            runOnUiThread(() -> {
                scriptCountText.setText(String.valueOf(loadedScripts.size()));
                cacheCountText.setText(String.valueOf(entries.size()));
                lastUpdatedText.setText(lastUpdated);
                updateDeviceStatus();
            });
        });
    }

    private void requestWearableRefresh() {
        if (wearableManager == null) return;
        wearableManager.refreshNodes();
        scheduleDeviceStatusRefresh();
    }

    private void updateDeviceStatus() {
        if (wearableManager == null || deviceStatusText == null) return;
        boolean wearableInstalled = wearableManager.isWearableAppInstalledForUi();
        boolean serviceConnected = wearableManager.isServiceConnected();
        String nodeId = wearableManager.getCurrentNodeId();
        String nodeName = wearableManager.getCurrentNodeName();
        Boolean connected = wearableManager.getConnectedStatus();
        String statusText;
        if (!wearableInstalled) {
            statusText = getString(R.string.device_status_no_app);
        } else if (!serviceConnected) {
            statusText = getString(R.string.device_status_service_disconnected);
        } else if (nodeId == null || nodeId.isEmpty()) {
            statusText = getString(R.string.device_status_no_device);
        } else {
            String name = (nodeName == null || nodeName.trim().isEmpty()) ? nodeId : nodeName;
            if (connected == null) {
                statusText = getString(R.string.device_status_connected, name);
            } else {
                statusText = getString(connected ? R.string.device_status_connected : R.string.device_status_disconnected, name);
            }
        }
        deviceStatusText.setText(statusText);
        maybeShowWearableDialog(wearableInstalled, serviceConnected, nodeId);
    }

    private void scheduleDeviceStatusRefresh() {
        mainHandler.removeCallbacks(deviceStatusRefresh);
        mainHandler.removeCallbacks(deviceStatusRefreshDelayed);
        mainHandler.postDelayed(deviceStatusRefresh, 800);
        mainHandler.postDelayed(deviceStatusRefreshDelayed, 2000);
    }

    private void maybeShowWearableDialog(boolean wearableInstalled, boolean serviceConnected, String nodeId) {
        if (!wearableInstalled) return;
        if (!serviceConnected) {
            if (!serviceDialogShown) {
                serviceDialogShown = true;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_service_title))
                        .setMessage(getString(R.string.dialog_service_message))
                        .setPositiveButton(getString(R.string.dialog_retry), (dialog, which) -> requestWearableRefresh())
                        .setNegativeButton(getString(R.string.dialog_ignore), null)
                        .show();
            }
            return;
        }
        serviceDialogShown = false;
        if (nodeId == null || nodeId.isEmpty()) {
            if (!deviceDialogShown) {
                deviceDialogShown = true;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_device_title))
                        .setMessage(getString(R.string.dialog_device_message))
                        .setPositiveButton(getString(R.string.dialog_retry), (dialog, which) -> requestWearableRefresh())
                        .setNegativeButton(getString(R.string.dialog_ignore), null)
                        .show();
            }
        } else {
            deviceDialogShown = false;
        }
    }

    private void handleUploadFile(Uri uri) {
        if (uri == null) return;
        if (wearableManager == null || !wearableManager.isServiceConnected() || wearableManager.getCurrentNodeId() == null) {
            Toast.makeText(this, getString(R.string.upload_no_device), Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentFile doc = DocumentFile.fromSingleUri(this, uri);
        String name = doc != null && doc.getName() != null ? doc.getName() : "music_" + System.currentTimeMillis();
        Toast.makeText(this, getString(R.string.upload_starting, name), Toast.LENGTH_SHORT).show();
        wearableManager.uploadMusic(uri, new XiaomiWearableManager.UploadCallback() {
            @Override
            public void onProgress(int percent) {
                if (percent % 25 == 0) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "上传进度: " + percent + "%", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onSuccess(String fileId, String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        getString(R.string.upload_finished, message == null ? fileId : message),
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        getString(R.string.upload_failed, message),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void handleUploadFromIntent(Intent intent) {
        if (intent == null) return;
        String uriValue = intent.getStringExtra(EXTRA_UPLOAD_URI);
        if (uriValue == null || uriValue.trim().isEmpty()) return;
        intent.removeExtra(EXTRA_UPLOAD_URI);
        try {
            handleUploadFile(Uri.parse(uriValue));
        } catch (Exception ignored) {
        }
    }

}


