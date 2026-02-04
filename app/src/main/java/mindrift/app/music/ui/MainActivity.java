package mindrift.app.music.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.InputStream;
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
    private AutoCompleteTextView scriptDropdown;
    private final java.util.List<ScriptOption> scriptOptions = new java.util.ArrayList<>();
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String[]> uploadLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable deviceStatusRefresh = this::updateDeviceStatus;
    private final Runnable deviceStatusRefreshDelayed = this::updateDeviceStatus;
    private final Runnable notificationCheck = this::checkNotificationStatus;
    private boolean serviceDialogShown = false;
    private boolean deviceDialogShown = false;
    private String lastSelectedScriptId;
    private boolean notificationDialogShown = false;
    private long lastNotificationPromptAt = 0L;
    private static final long NOTIFICATION_CHECK_INTERVAL_MS = 20_000L;

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
        scriptDropdown = findViewById(R.id.dropdown_script_home);
        scriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                lastSelectedScriptId = scriptOptions.get(position).scriptId;
            }
        });

        MaterialButton refreshButton = findViewById(R.id.button_refresh);
        MaterialButton clearCacheButton = findViewById(R.id.button_clear_cache);
        MaterialButton importFileButton = findViewById(R.id.button_import_file);
        MaterialButton importUrlButton = findViewById(R.id.button_import_url);
        MaterialButton uploadMusicButton = findViewById(R.id.button_upload_music);
        MaterialButton reloadScriptsButton = findViewById(R.id.button_reload_scripts);
        MaterialButton editScriptButton = findViewById(R.id.button_script_edit_home);
        MaterialButton renameScriptButton = findViewById(R.id.button_script_rename_home);
        MaterialButton deleteScriptButton = findViewById(R.id.button_script_delete_home);
        MaterialButton openSettingsButton = findViewById(R.id.button_open_settings);

        refreshButton.setOnClickListener(v -> {
            requestWearableRefresh();
            refreshData();
        });
        clearCacheButton.setOnClickListener(v -> {
            cacheManager.clear();
            refreshData();
        });
        reloadScriptsButton.setOnClickListener(v -> {
            scriptManager.loadScripts();
            refreshData();
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImportFile);
        importFileButton.setOnClickListener(v -> importLauncher.launch(new String[] {
                "application/javascript",
                "text/javascript",
                "application/x-javascript",
                "text/plain"
        }));
        uploadLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleUploadFile);
        uploadMusicButton.setOnClickListener(v -> uploadLauncher.launch(new String[] {"audio/*"}));
        importUrlButton.setOnClickListener(v -> showImportUrlDialog());
        editScriptButton.setOnClickListener(v -> showEditScriptDialog());
        renameScriptButton.setOnClickListener(v -> showRenameScriptDialog());
        deleteScriptButton.setOnClickListener(v -> confirmDeleteScript());
        openSettingsButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, SettingsActivity.class)));

        refreshData();
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
        boolean channelOk = NotificationHelper.isStatusChannelEnabled(this);
        if (!permissionOk || !channelOk) {
            showNotificationPrompt(getString(R.string.notification_prompt_title),
                    getString(R.string.notification_prompt_permission));
            scheduleNotificationCheck(false);
            return;
        }
        App app = (App) getApplication();
        app.ensureKeepAliveService();
        if (!NotificationHelper.isStatusNotificationActive(this)) {
            NotificationHelper.showOngoing(this);
            showNotificationPrompt(getString(R.string.notification_prompt_title),
                    getString(R.string.notification_prompt_missing));
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
                updateScriptDropdown(loadedScripts, preferredScriptId);
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

    private void handleImportFile(android.net.Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    return;
                }
                DocumentFile doc = DocumentFile.fromSingleUri(this, uri);
                String name = doc != null ? doc.getName() : null;
                if (name == null || name.trim().isEmpty()) {
                    name = "import_" + System.currentTimeMillis() + ".js";
                }
                scriptManager.importFromStream(name, input);
                scriptManager.loadScripts();
            } catch (Exception ignored) {
            }
            runOnUiThread(this::refreshData);
        });
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

    private void showImportUrlDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        editText.setHint(getString(R.string.import_script_url_placeholder));
        inputLayout.addView(editText);
        inputLayout.setHint(getString(R.string.import_script_hint));

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.import_script_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.import_button), (dialog, which) -> {
                    String url = editText.getText() == null ? "" : editText.getText().toString().trim();
                    if (url.isEmpty()) return;
                    scriptManager.importFromUrl(url, new ScriptManager.ImportCallback() {
                        @Override
                        public void onSuccess(java.io.File file) {
                            scriptManager.loadScripts();
                            runOnUiThread(MainActivity.this::refreshData);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            runOnUiThread(MainActivity.this::refreshData);
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void updateScriptDropdown(List<ScriptManager.ScriptEntry> scripts, String preferredScriptId) {
        scriptOptions.clear();
        if (scripts != null) {
            for (ScriptManager.ScriptEntry entry : scripts) {
                if (entry == null) continue;
                scriptOptions.add(new ScriptOption(entry.getScriptId(), entry.getDisplayName()));
            }
        }
        if (scriptOptions.isEmpty()) {
            scriptOptions.add(new ScriptOption(null, getString(R.string.no_scripts)));
        } else {
            applyDuplicateLabels();
        }
        ArrayAdapter<String> scriptAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapScriptLabels());
        scriptDropdown.setAdapter(scriptAdapter);
        ScriptOption selected = resolvePreferredScript(preferredScriptId);
        scriptDropdown.setText(selected.label, false);
        lastSelectedScriptId = selected.scriptId;
    }

    private String getSelectedScriptId() {
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        if (scriptLabel.isEmpty() || getString(R.string.no_scripts).equals(scriptLabel)) {
            Toast.makeText(this, getString(R.string.prompt_import_first), Toast.LENGTH_SHORT).show();
            return null;
        }
        for (ScriptOption option : scriptOptions) {
            if (option.label.equals(scriptLabel)) {
                return option.scriptId;
            }
        }
        return scriptLabel;
    }

    private void showRenameScriptDialog() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null) return;
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setHint(getString(R.string.script_name_hint));
        editText.setText(scriptId);
        inputLayout.addView(editText);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.script_rename_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                    String newName = editText.getText() == null ? "" : editText.getText().toString().trim();
                    executor.execute(() -> {
                        String renamed = scriptManager.renameScript(scriptId, newName);
                        if (renamed != null) {
                            scriptManager.loadScripts();
                            runOnUiThread(() -> {
                                refreshData(renamed);
                                Toast.makeText(this, getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void showEditScriptDialog() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null) return;
        executor.execute(() -> {
            String content = scriptManager.readScriptContent(scriptId);
            runOnUiThread(() -> openEditDialog(scriptId, content));
        });
    }

    private void openEditDialog(String scriptId, String content) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(10);
        editText.setHint(getString(R.string.script_content_hint));
        editText.setText(content == null ? "" : content);
        inputLayout.addView(editText);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.script_edit_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                    String newContent = editText.getText() == null ? "" : editText.getText().toString();
                    executor.execute(() -> {
                        boolean ok = scriptManager.updateScriptContent(scriptId, newContent);
                        if (ok) {
                            scriptManager.loadScripts();
                            runOnUiThread(() -> {
                                refreshData(scriptId);
                                Toast.makeText(this, getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void confirmDeleteScript() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.action_delete_script))
                .setMessage(getString(R.string.script_delete_confirm, scriptId))
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> {
                    executor.execute(() -> {
                        boolean ok = scriptManager.deleteScript(scriptId);
                        if (ok) {
                            scriptManager.loadScripts();
                            runOnUiThread(() -> {
                                refreshData(null);
                                Toast.makeText(this, getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void applyDuplicateLabels() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (ScriptOption option : scriptOptions) {
            counts.put(option.label, counts.getOrDefault(option.label, 0) + 1);
        }
        for (ScriptOption option : scriptOptions) {
            if (counts.getOrDefault(option.label, 0) > 1 && option.scriptId != null) {
                option.label = option.label + " (" + option.scriptId + ")";
            }
        }
    }

    private ScriptOption resolvePreferredScript(String preferredScriptId) {
        String targetId = preferredScriptId == null ? lastSelectedScriptId : preferredScriptId;
        if (targetId != null) {
            for (ScriptOption option : scriptOptions) {
                if (targetId.equals(option.scriptId)) {
                    return option;
                }
            }
        }
        return scriptOptions.get(0);
    }

    private String[] mapScriptLabels() {
        String[] labels = new String[scriptOptions.size()];
        for (int i = 0; i < scriptOptions.size(); i++) {
            labels[i] = scriptOptions.get(i).label;
        }
        return labels;
    }

    private static class ScriptOption {
        final String scriptId;
        String label;

        ScriptOption(String scriptId, String label) {
            this.scriptId = scriptId;
            this.label = label == null ? "" : label;
        }
    }
}


