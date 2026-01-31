package mindrift.app.lisynchronization.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.lisynchronization.App;
import mindrift.app.lisynchronization.R;
import mindrift.app.lisynchronization.core.cache.CacheEntry;
import mindrift.app.lisynchronization.core.cache.CacheManager;
import mindrift.app.lisynchronization.core.proxy.RequestProxy;
import mindrift.app.lisynchronization.core.script.ScriptManager;
import mindrift.app.lisynchronization.core.script.ScriptInfo;
import mindrift.app.lisynchronization.core.script.SourceInfo;
import mindrift.app.lisynchronization.model.ResolveRequest;
import mindrift.app.lisynchronization.utils.AppLogBuffer;

public class SettingsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private TextView cacheSummaryText;
    private TextView cacheListText;
    private TextView testResultText;
    private TextView logOutputText;
    private TextView scriptCapabilitiesText;
    private AutoCompleteTextView scriptDropdown;
    private AutoCompleteTextView platformDropdown;
    private AutoCompleteTextView actionDropdown;
    private AutoCompleteTextView qualityDropdown;
    private TextInputEditText songIdInput;
    private final java.util.List<String> scriptOptions = new java.util.ArrayList<>();
    private final java.util.List<ActionItem> actionOptions = new java.util.ArrayList<>();
    private final java.util.List<PlatformItem> platformOptions = new java.util.ArrayList<>();
    private final java.util.List<QualityItem> qualityOptions = new java.util.ArrayList<>();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private String lastResolvedUrl;
    private final AppLogBuffer.LogListener logListener = newLine -> runOnUiThread(this::refreshLogView);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        App app = (App) getApplication();
        cacheManager = app.getCacheManager();
        scriptManager = app.getScriptManager();
        requestProxy = app.getRequestProxy();

        cacheSummaryText = findViewById(R.id.text_cache_summary);
        cacheListText = findViewById(R.id.text_cache_list);
        testResultText = findViewById(R.id.text_test_result);
        logOutputText = findViewById(R.id.text_log_output);
        scriptCapabilitiesText = findViewById(R.id.text_script_capabilities);
        scriptDropdown = findViewById(R.id.dropdown_script);
        platformDropdown = findViewById(R.id.dropdown_platform);
        actionDropdown = findViewById(R.id.dropdown_action);
        qualityDropdown = findViewById(R.id.dropdown_quality);
        songIdInput = findViewById(R.id.input_song_id);

        MaterialButton cacheRefreshButton = findViewById(R.id.button_cache_refresh);
        MaterialButton cacheClearButton = findViewById(R.id.button_cache_clear);
        MaterialButton testButton = findViewById(R.id.button_test_request);
        TextView copyLogsButton = findViewById(R.id.button_copy_logs);
        TextView clearLogsButton = findViewById(R.id.button_clear_logs);

        cacheRefreshButton.setOnClickListener(v -> refreshData());
        cacheClearButton.setOnClickListener(v -> {
            cacheManager.clear();
            refreshData();
        });
        testButton.setOnClickListener(v -> runTestRequest());
        copyLogsButton.setOnClickListener(v -> copyLogs());
        clearLogsButton.setOnClickListener(v -> {
            AppLogBuffer.clear();
            refreshLogView();
        });
        testResultText.setOnClickListener(v -> openResolvedUrl());
        scriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                updateCapabilities(scriptOptions.get(position));
            }
        });
        platformDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String scriptId = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
            ScriptInfo info = scriptManager.getScriptInfo(scriptId);
            updateQualityOptions(info);
        });

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

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogView();
        refreshData();
    }

    private void refreshData() {
        executor.execute(() -> {
            List<CacheEntry> entries = cacheManager.list();
            List<String> loadedIds = scriptManager.getLoadedScriptIds();
            String summary = getString(R.string.cache_summary, entries.size());
            String cacheText = formatCacheList(entries);
            runOnUiThread(() -> {
                cacheSummaryText.setText(summary);
                cacheListText.setText(cacheText);
                updateScriptDropdown(loadedIds);
            });
        });
    }

    private String formatCacheList(List<CacheEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return getString(R.string.cache_empty);
        }
        long now = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (CacheEntry entry : entries) {
            long ttlSeconds = Math.max(0, (entry.getExpireAt() - now) / 1000);
            builder.append('[').append(index++).append("] ").append(entry.getKey()).append('\n');
            builder.append(getString(R.string.cache_provider_format, safe(entry.getProvider()))).append('\n');
            builder.append(getString(R.string.cache_ttl_format, formatTtl(ttlSeconds))).append('\n');
            String dataJson = gson.toJson(entry.getData());
            builder.append(getString(R.string.cache_data_format, trim(dataJson))).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String formatTtl(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs);
    }

    private String trim(String value) {
        if (value == null) return "";
        int limit = 400;
        if (value.length() <= limit) return value;
        return value.substring(0, limit) + "...(共" + value.length() + "字)";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void setupDropdowns() {
        actionOptions.clear();
        actionOptions.add(new ActionItem(getString(R.string.action_music_url), "musicUrl"));

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapActionLabels());
        actionDropdown.setAdapter(actionAdapter);
        if (!actionOptions.isEmpty()) {
            actionDropdown.setText(actionOptions.get(0).label, false);
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
        updateCapabilities(scriptOptions.get(0));
    }

    private void runTestRequest() {
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        String platformLabel = platformDropdown.getText() == null ? "" : platformDropdown.getText().toString();
        String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
        String songId = songIdInput.getText() == null ? "" : songIdInput.getText().toString().trim();
        String qualityLabel = qualityDropdown.getText() == null ? "" : qualityDropdown.getText().toString().trim();

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
        String quality = resolveQualityValue(qualityLabel);

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
        logOutputText.setText(trimLogSnapshot(snapshot));
    }

    private void copyLogs() {
        String snapshot = AppLogBuffer.getSnapshot();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), snapshot));
        }
    }

    private void updateCapabilities(String scriptId) {
        if (scriptCapabilitiesText == null) return;
        if (scriptId == null || getString(R.string.no_scripts).equals(scriptId)) {
            scriptCapabilitiesText.setText(getString(R.string.script_capabilities_placeholder));
            updatePlatformOptions(null);
            updateQualityOptions(null);
            return;
        }
        ScriptInfo info = scriptManager.getScriptInfo(scriptId);
        if (info == null || info.getSources() == null || info.getSources().isEmpty()) {
            scriptCapabilitiesText.setText(getString(R.string.script_capabilities_empty));
            updatePlatformOptions(null);
            updateQualityOptions(null);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, SourceInfo> entry : info.getSources().entrySet()) {
            String source = entry.getKey();
            SourceInfo sourceInfo = entry.getValue();
            if (sourceInfo == null) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(formatPlatformLabel(source)).append(": ");
            List<String> qualitys = sourceInfo.getQualitys();
            if (qualitys == null || qualitys.isEmpty()) {
                builder.append(getString(R.string.script_capabilities_any_quality));
            } else {
                builder.append(joinList(qualitys));
            }
        }
        scriptCapabilitiesText.setText(builder.length() == 0
                ? getString(R.string.script_capabilities_empty)
                : builder.toString());
        updatePlatformOptions(info);
    }

    private void updatePlatformOptions(ScriptInfo info) {
        platformOptions.clear();
        if (info != null && info.getSources() != null) {
            for (Map.Entry<String, SourceInfo> entry : info.getSources().entrySet()) {
                String source = entry.getKey();
                SourceInfo sourceInfo = entry.getValue();
                if (sourceInfo == null) continue;
                if (sourceInfo.getType() != null && !"music".equalsIgnoreCase(sourceInfo.getType())) {
                    continue;
                }
                platformOptions.add(new PlatformItem(formatPlatformLabel(source), source));
            }
        }
        if (platformOptions.isEmpty()) {
            platformOptions.add(new PlatformItem(getString(R.string.no_scripts), ""));
        }
        ArrayAdapter<String> platformAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapPlatformLabels());
        platformDropdown.setAdapter(platformAdapter);
        platformDropdown.setText(platformOptions.get(0).label, false);
        updateQualityOptions(info);
    }

    private void updateQualityOptions(ScriptInfo info) {
        qualityOptions.clear();
        String platform = resolvePlatformValue(platformDropdown.getText() == null ? "" : platformDropdown.getText().toString());
        List<String> qualitys = null;
        if (info != null && info.getSources() != null) {
            SourceInfo sourceInfo = info.getSources().get(platform);
            if (sourceInfo != null) {
                qualitys = sourceInfo.getQualitys();
            }
        }
        if (qualitys == null || qualitys.isEmpty()) {
            qualityOptions.add(new QualityItem(getString(R.string.script_capabilities_any_quality), ""));
        } else {
            for (String q : qualitys) {
                qualityOptions.add(new QualityItem(q, q));
            }
        }
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapQualityLabels());
        qualityDropdown.setAdapter(qualityAdapter);
        qualityDropdown.setText(qualityOptions.get(0).label, false);
    }

    private String formatPlatformLabel(String source) {
        if (source == null) return "";
        switch (source) {
            case "tx":
                return getString(R.string.platform_tx);
            case "wy":
                return getString(R.string.platform_wy);
            case "kg":
                return getString(R.string.platform_kg);
            case "kw":
                return getString(R.string.platform_kw);
            case "mg":
                return getString(R.string.platform_mg);
            case "local":
                return "本地";
            default:
                return source.toUpperCase(Locale.US);
        }
    }

    private String joinList(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) builder.append(" / ");
            builder.append(items.get(i));
        }
        return builder.toString();
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

    private String resolveQualityValue(String label) {
        for (QualityItem item : qualityOptions) {
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

    private String[] mapQualityLabels() {
        String[] labels = new String[qualityOptions.size()];
        for (int i = 0; i < qualityOptions.size(); i++) {
            labels[i] = qualityOptions.get(i).label;
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

    private static class QualityItem {
        final String label;
        final String value;

        QualityItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private String trimLogSnapshot(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return getString(R.string.no_logs);
        }
        String[] lines = snapshot.split("\\n");
        int maxLines = 150;
        int start = Math.max(0, lines.length - maxLines);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() > 400) {
                line = line.substring(0, 400) + "...";
            }
            builder.append(line);
            if (i < lines.length - 1) builder.append('\n');
        }
        return builder.toString();
    }
}
