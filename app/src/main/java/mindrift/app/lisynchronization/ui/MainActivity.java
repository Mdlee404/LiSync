package mindrift.app.lisynchronization.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.lisynchronization.App;
import mindrift.app.lisynchronization.R;
import mindrift.app.lisynchronization.core.cache.CacheEntry;
import mindrift.app.lisynchronization.core.cache.CacheManager;
import mindrift.app.lisynchronization.core.proxy.RequestProxy;
import mindrift.app.lisynchronization.core.script.ScriptManager;
import mindrift.app.lisynchronization.model.ResolveRequest;
import mindrift.app.lisynchronization.utils.AppLogBuffer;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private TextView scriptCountText;
    private TextView cacheCountText;
    private TextView lastUpdatedText;
    private TextView testResultText;
    private TextView logOutputText;
    private AutoCompleteTextView scriptDropdown;
    private AutoCompleteTextView platformDropdown;
    private AutoCompleteTextView actionDropdown;
    private TextInputEditText songIdInput;
    private TextInputEditText qualityInput;
    private final java.util.List<String> scriptOptions = new java.util.ArrayList<>();
    private final java.util.List<ActionItem> actionOptions = new java.util.ArrayList<>();
    private final java.util.List<PlatformItem> platformOptions = new java.util.ArrayList<>();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private final com.google.gson.Gson prettyGson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private String lastResolvedUrl;
    private ActivityResultLauncher<String[]> importLauncher;
    private final AppLogBuffer.LogListener logListener = newLine -> runOnUiThread(this::refreshLogView);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        App app = (App) getApplication();
        cacheManager = app.getCacheManager();
        scriptManager = app.getScriptManager();
        requestProxy = app.getRequestProxy();

        scriptCountText = findViewById(R.id.text_script_count);
        cacheCountText = findViewById(R.id.text_cache_count);
        lastUpdatedText = findViewById(R.id.text_last_updated);
        testResultText = findViewById(R.id.text_test_result);
        logOutputText = findViewById(R.id.text_log_output);
        scriptDropdown = findViewById(R.id.dropdown_script);
        platformDropdown = findViewById(R.id.dropdown_platform);
        actionDropdown = findViewById(R.id.dropdown_action);
        songIdInput = findViewById(R.id.input_song_id);
        qualityInput = findViewById(R.id.input_quality);

        MaterialButton refreshButton = findViewById(R.id.button_refresh);
        MaterialButton clearCacheButton = findViewById(R.id.button_clear_cache);
        MaterialButton importFileButton = findViewById(R.id.button_import_file);
        MaterialButton importUrlButton = findViewById(R.id.button_import_url);
        MaterialButton reloadScriptsButton = findViewById(R.id.button_reload_scripts);
        MaterialButton testButton = findViewById(R.id.button_test_request);
        TextView copyLogsButton = findViewById(R.id.button_copy_logs);
        TextView clearLogsButton = findViewById(R.id.button_clear_logs);

        refreshButton.setOnClickListener(v -> refreshData());
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
        importUrlButton.setOnClickListener(v -> showImportUrlDialog());
        testButton.setOnClickListener(v -> runTestRequest());
        copyLogsButton.setOnClickListener(v -> copyLogs());
        clearLogsButton.setOnClickListener(v -> {
            AppLogBuffer.clear();
            refreshLogView();
        });
        testResultText.setOnClickListener(v -> openResolvedUrl());

        setupDropdowns();
        AppLogBuffer.addListener(logListener);
        refreshLogView();
        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogBuffer.removeListener(logListener);
        executor.shutdown();
    }

    private void refreshData() {
        executor.execute(() -> {
            List<CacheEntry> entries = cacheManager.list();
            List<String> loadedIds = scriptManager.getLoadedScriptIds();
            String lastUpdated = getString(R.string.updated_at, DateFormat.getDateTimeInstance().format(new Date()));
            runOnUiThread(() -> {
                scriptCountText.setText(String.valueOf(loadedIds.size()));
                cacheCountText.setText(String.valueOf(entries.size()));
                lastUpdatedText.setText(lastUpdated);
                updateScriptDropdown(loadedIds);
            });
        });
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

    private void showImportUrlDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0); // Add padding
        
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

    private void setupDropdowns() {
        actionOptions.clear();
        actionOptions.add(new ActionItem(getString(R.string.action_music_url), "musicUrl"));
        actionOptions.add(new ActionItem(getString(R.string.action_lyric), "lyric"));
        actionOptions.add(new ActionItem(getString(R.string.action_pic), "pic"));

        platformOptions.clear();
        platformOptions.add(new PlatformItem(getString(R.string.platform_tx), "tx"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_wy), "wy"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_kg), "kg"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_kw), "kw"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_mg), "mg"));

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapActionLabels());
        actionDropdown.setAdapter(actionAdapter);
        if (!actionOptions.isEmpty()) {
            actionDropdown.setText(actionOptions.get(0).label, false);
        }

        ArrayAdapter<String> platformAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapPlatformLabels());
        platformDropdown.setAdapter(platformAdapter);
        if (!platformOptions.isEmpty()) {
            platformDropdown.setText(platformOptions.get(0).label, false);
        }

        updateScriptDropdown(scriptManager.getLoadedScriptIds());
    }

    private void updateScriptDropdown(List<String> scriptIds) {
        scriptOptions.clear();
        if (scriptIds != null) {
            scriptOptions.addAll(scriptIds);
        }
        if (scriptOptions.isEmpty()) {
            scriptOptions.add(getString(R.string.no_scripts));
        }
        ArrayAdapter<String> scriptAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                scriptOptions);
        scriptDropdown.setAdapter(scriptAdapter);
        scriptDropdown.setText(scriptOptions.get(0), false);
    }

    private void runTestRequest() {
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        String platformLabel = platformDropdown.getText() == null ? "" : platformDropdown.getText().toString();
        String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
        String songId = songIdInput.getText() == null ? "" : songIdInput.getText().toString().trim();
        String quality = qualityInput.getText() == null ? "" : qualityInput.getText().toString().trim();

        if (getString(R.string.no_scripts).equals(scriptLabel) || scriptLabel.isEmpty()) {
            testResultText.setText(getString(R.string.prompt_import_first));
            return;
        }
        if (songId.isEmpty()) {
            testResultText.setText(getString(R.string.prompt_input_song_id));
            return;
        }
        String scriptId = scriptLabel;
        String platform = resolvePlatformValue(platformLabel);
        String action = resolveActionValue(actionLabel);

        testResultText.setText(getString(R.string.request_in_progress));
        executor.execute(() -> {
            try {
                ResolveRequest request = new ResolveRequest();
                request.setSource(platform);
                request.setAction(action);
                request.setQuality(quality.isEmpty() ? "128k" : quality);
                request.setTargetScriptId(scriptId);
                ResolveRequest.MusicInfo musicInfo = new ResolveRequest.MusicInfo();
                musicInfo.songmid = songId;
                musicInfo.hash = songId;
                request.setMusicInfo(musicInfo);
                String response = requestProxy.resolveSync(request);
                String url = extractUrl(response);
                runOnUiThread(() -> {
                    lastResolvedUrl = url;
                    testResultText.setText(url == null ? getString(R.string.result_empty) : url);
                });
            } catch (Exception e) {
                runOnUiThread(() -> testResultText.setText(getString(R.string.request_failed, e.getMessage())));
            }
        });
    }

    private void refreshLogView() {
        if (logOutputText == null) return;
        String snapshot = AppLogBuffer.getSnapshot();
        logOutputText.setText(snapshot.isEmpty() ? getString(R.string.no_logs) : snapshot);
    }

    private void copyLogs() {
        String snapshot = AppLogBuffer.getSnapshot();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), snapshot));
        }
    }

    private String extractUrl(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(response);
            if (!element.isJsonObject()) {
                return extractPlainUrl(response);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
                return obj.get("url").getAsString();
            }
            if (obj.has("data")) {
                JsonElement data = obj.get("data");
                if (data != null && data.isJsonObject()) {
                    JsonObject dataObj = data.getAsJsonObject();
                    if (dataObj.has("url") && dataObj.get("url").isJsonPrimitive()) {
                        return dataObj.get("url").getAsString();
                    }
                }
                if (data != null && data.isJsonPrimitive()) {
                    return extractPlainUrl(data.getAsString());
                }
            }
            return null;
        } catch (Exception e) {
            return extractPlainUrl(response);
        }
    }

    private String extractPlainUrl(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return null;
    }

    private void openResolvedUrl() {
        if (lastResolvedUrl == null || lastResolvedUrl.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(lastResolvedUrl));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private String resolvePlatformValue(String label) {
        for (PlatformItem item : platformOptions) {
            if (item.label.equals(label)) {
                return item.value;
            }
        }
        return label;
    }

    private String resolveActionValue(String label) {
        for (ActionItem item : actionOptions) {
            if (item.label.equals(label)) {
                return item.value;
            }
        }
        return label;
    }

    private String[] mapActionLabels() {
        String[] labels = new String[actionOptions.size()];
        for (int i = 0; i < actionOptions.size(); i++) {
            labels[i] = actionOptions.get(i).label;
        }
        return labels;
    }

    private String[] mapPlatformLabels() {
        String[] labels = new String[platformOptions.size()];
        for (int i = 0; i < platformOptions.size(); i++) {
            labels[i] = platformOptions.get(i).label;
        }
        return labels;
    }

    private static class ActionItem {
        final String label;
        final String value;

        ActionItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private static class PlatformItem {
        final String label;
        final String value;

        PlatformItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}






