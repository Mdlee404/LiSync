package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.core.lyric.LyricService;
import mindrift.app.music.core.proxy.RequestProxy;
import mindrift.app.music.core.search.SearchService;
import mindrift.app.music.core.script.ScriptInfo;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.core.script.SourceInfo;
import mindrift.app.music.model.ResolveRequest;
import mindrift.app.music.utils.PlatformUtils;
import mindrift.app.music.utils.SettingsStore;

public class DiagnosticsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private TextView testResultText;
    private TextView scriptCapabilitiesText;
    private AutoCompleteTextView scriptDropdown;
    private AutoCompleteTextView forcedScriptDropdown;
    private SwitchMaterial forcePollingSwitch;
    private AutoCompleteTextView platformDropdown;
    private AutoCompleteTextView actionDropdown;
    private AutoCompleteTextView qualityDropdown;
    private TextInputEditText songIdInput;
    private TextInputEditText keywordInput;
    private TextInputEditText pageInput;
    private TextInputEditText pageSizeInput;
    private final java.util.List<ScriptOption> scriptOptions = new java.util.ArrayList<>();
    private final java.util.List<ScriptOption> forcedScriptOptions = new java.util.ArrayList<>();
    private final java.util.List<ActionItem> actionOptions = new java.util.ArrayList<>();
    private final java.util.List<PlatformItem> platformOptions = new java.util.ArrayList<>();
    private final java.util.List<QualityItem> qualityOptions = new java.util.ArrayList<>();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private final SearchService searchService = new SearchService();
    private final LyricService lyricService = new LyricService();
    private String lastResolvedUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        App app = (App) getApplication();
        scriptManager = app.getScriptManager();
        requestProxy = app.getRequestProxy();

        testResultText = findViewById(R.id.text_test_result);
        scriptCapabilitiesText = findViewById(R.id.text_script_capabilities);
        scriptDropdown = findViewById(R.id.dropdown_script);
        forcedScriptDropdown = findViewById(R.id.dropdown_forced_script);
        forcePollingSwitch = findViewById(R.id.switch_force_polling);
        platformDropdown = findViewById(R.id.dropdown_platform);
        actionDropdown = findViewById(R.id.dropdown_action);
        qualityDropdown = findViewById(R.id.dropdown_quality);
        songIdInput = findViewById(R.id.input_song_id);
        keywordInput = findViewById(R.id.input_keyword);
        pageInput = findViewById(R.id.input_page);
        pageSizeInput = findViewById(R.id.input_page_size);

        findViewById(R.id.button_test_request).setOnClickListener(v -> runTestRequest());
        testResultText.setOnClickListener(v -> openResolvedUrl());
        scriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                updateCapabilities(scriptOptions.get(position).scriptId);
            }
        });
        actionDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
            onActionChanged(actionLabel);
        });
        platformDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
            String scriptId = resolveScriptId(scriptLabel);
            ScriptInfo info = scriptManager.getScriptInfo(scriptId);
            String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
            updateQualityOptions(info, resolveActionValue(actionLabel));
        });
        forcedScriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < forcedScriptOptions.size()) {
                SettingsStore.setForcedScriptId(this, forcedScriptOptions.get(position).scriptId);
            }
        });
        forcePollingSwitch.setChecked(SettingsStore.isForcePolling(this));
        forcePollingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> SettingsStore.setForcePolling(this, isChecked));

        setupDropdowns();
        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        searchService.shutdown();
        lyricService.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        executor.execute(() -> {
            List<ScriptManager.ScriptEntry> loadedScripts = scriptManager.getLoadedScripts();
            runOnUiThread(() -> {
                updateScriptDropdown(loadedScripts);
                updateForcedScriptDropdown(loadedScripts);
            });
        });
    }

    private void setupDropdowns() {
        actionOptions.clear();
        actionOptions.add(new ActionItem(getString(R.string.action_music_url), "musicUrl"));
        actionOptions.add(new ActionItem(getString(R.string.action_search), "search"));
        actionOptions.add(new ActionItem(getString(R.string.action_lyric), "lyric"));

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapActionLabels());
        actionDropdown.setAdapter(actionAdapter);
        if (!actionOptions.isEmpty()) {
            actionDropdown.setText(actionOptions.get(0).label, false);
        }

        updateScriptDropdown(scriptManager.getLoadedScripts());
    }

    private void updateScriptDropdown(List<ScriptManager.ScriptEntry> scripts) {
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
            applyDuplicateLabels(scriptOptions);
        }
        ArrayAdapter<String> scriptAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapScriptLabels(scriptOptions));
        scriptDropdown.setAdapter(scriptAdapter);
        scriptDropdown.setText(scriptOptions.get(0).label, false);
        updateCapabilities(scriptOptions.get(0).scriptId);
    }

    private void updateForcedScriptDropdown(List<ScriptManager.ScriptEntry> scripts) {
        forcedScriptOptions.clear();
        forcedScriptOptions.add(new ScriptOption("", getString(R.string.force_script_none)));
        if (scripts != null) {
            for (ScriptManager.ScriptEntry entry : scripts) {
                if (entry == null) continue;
                forcedScriptOptions.add(new ScriptOption(entry.getScriptId(), entry.getDisplayName()));
            }
        }
        applyDuplicateLabels(forcedScriptOptions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapScriptLabels(forcedScriptOptions));
        forcedScriptDropdown.setAdapter(adapter);
        String savedId = SettingsStore.getForcedScriptId(this);
        String label = resolveScriptLabel(savedId, forcedScriptOptions);
        if (label == null) {
            SettingsStore.setForcedScriptId(this, "");
            label = forcedScriptOptions.get(0).label;
        }
        forcedScriptDropdown.setText(label, false);
    }

    private void runTestRequest() {
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        String platformLabel = platformDropdown.getText() == null ? "" : platformDropdown.getText().toString();
        String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
        String songId = songIdInput.getText() == null ? "" : songIdInput.getText().toString().trim();
        String qualityLabel = qualityDropdown.getText() == null ? "" : qualityDropdown.getText().toString().trim();
        String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
        String pageRaw = pageInput.getText() == null ? "" : pageInput.getText().toString().trim();
        String pageSizeRaw = pageSizeInput.getText() == null ? "" : pageSizeInput.getText().toString().trim();

        String scriptId = resolveScriptId(scriptLabel);
        String platform = resolvePlatformValue(platformLabel);
        String action = resolveActionValue(actionLabel);
        String quality = resolveQualityValue(qualityLabel);

        testResultText.setText(getString(R.string.request_in_progress));
        executor.execute(() -> {
            try {
                if ("search".equalsIgnoreCase(action)) {
                    if (keyword.isEmpty()) {
                        runOnUiThread(() -> testResultText.setText(getString(R.string.request_failed, "关键词不能为空")));
                        return;
                    }
                    int page = parseIntOrDefault(pageRaw, 1);
                    int pageSize = parseIntOrDefault(pageSizeRaw, 20);
                    SearchService.SearchResult result = searchService.search(platform, keyword, page, pageSize);
                    String payload = gson.toJson(result);
                    runOnUiThread(() -> {
                        lastResolvedUrl = null;
                        testResultText.setText(payload);
                    });
                    return;
                }
                if ("lyric".equalsIgnoreCase(action)) {
                    if (songId.isEmpty()) {
                        runOnUiThread(() -> testResultText.setText(getString(R.string.prompt_input_song_id)));
                        return;
                    }
                    LyricService.LyricResult result = lyricService.getLyric(platform, songId);
                    String payload = gson.toJson(result);
                    runOnUiThread(() -> {
                        lastResolvedUrl = null;
                        testResultText.setText(payload);
                    });
                    return;
                }
                if (scriptId == null || scriptId.isEmpty()) {
                    runOnUiThread(() -> testResultText.setText(getString(R.string.prompt_import_first)));
                    return;
                }
                if (songId.isEmpty()) {
                    runOnUiThread(() -> testResultText.setText(getString(R.string.prompt_input_song_id)));
                    return;
                }
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

    private void updateCapabilities(String scriptId) {
        if (scriptCapabilitiesText == null) return;
        if (scriptId == null || getString(R.string.no_scripts).equals(scriptId)) {
            scriptCapabilitiesText.setText(getString(R.string.script_capabilities_placeholder));
            String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
            updatePlatformOptions(null, resolveActionValue(actionLabel));
            updateQualityOptions(null, resolveActionValue(actionLabel));
            return;
        }
        ScriptInfo info = scriptManager.getScriptInfo(scriptId);
        if (info == null || info.getSources() == null || info.getSources().isEmpty()) {
            scriptCapabilitiesText.setText(getString(R.string.script_capabilities_empty));
            String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
            updatePlatformOptions(null, resolveActionValue(actionLabel));
            updateQualityOptions(null, resolveActionValue(actionLabel));
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, SourceInfo> entry : info.getSources().entrySet()) {
            String source = entry.getKey();
            SourceInfo sourceInfo = entry.getValue();
            if (sourceInfo == null) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(formatPlatformLabel(source, sourceInfo)).append(": ");
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
        String actionLabel = actionDropdown.getText() == null ? "" : actionDropdown.getText().toString();
        updatePlatformOptions(info, resolveActionValue(actionLabel));
    }

    private void updatePlatformOptions(ScriptInfo info, String action) {
        platformOptions.clear();
        if ("search".equalsIgnoreCase(action) || "lyric".equalsIgnoreCase(action)) {
            addDefaultPlatforms();
        } else if (info != null && info.getSources() != null) {
            for (Map.Entry<String, SourceInfo> entry : info.getSources().entrySet()) {
                String source = entry.getKey();
                SourceInfo sourceInfo = entry.getValue();
                if (sourceInfo == null) continue;
                if (sourceInfo.getType() != null && !"music".equalsIgnoreCase(sourceInfo.getType())) {
                    continue;
                }
                platformOptions.add(new PlatformItem(formatPlatformLabel(source, sourceInfo), source));
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
        updateQualityOptions(info, action);
    }

    private void updateQualityOptions(ScriptInfo info, String action) {
        qualityOptions.clear();
        if (!"musicUrl".equalsIgnoreCase(action)) {
            qualityOptions.add(new QualityItem(getString(R.string.script_capabilities_any_quality), ""));
        } else {
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
        }
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapQualityLabels());
        qualityDropdown.setAdapter(qualityAdapter);
        qualityDropdown.setText(qualityOptions.get(0).label, false);
    }

    private String formatPlatformLabel(String source, SourceInfo sourceInfo) {
        if (sourceInfo != null) {
            String name = sourceInfo.getName();
            if (name != null && !name.trim().isEmpty()) {
                return PlatformUtils.displayName(name.trim());
            }
        }
        return formatPlatformLabel(source);
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

    private String[] mapScriptLabels(List<ScriptOption> options) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
        }
        return labels;
    }

    private void applyDuplicateLabels(List<ScriptOption> options) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (ScriptOption option : options) {
            counts.put(option.label, counts.getOrDefault(option.label, 0) + 1);
        }
        for (ScriptOption option : options) {
            if (counts.getOrDefault(option.label, 0) > 1 && option.scriptId != null && !option.scriptId.isEmpty()) {
                option.label = option.label + " (" + option.scriptId + ")";
            }
        }
    }

    private String resolveScriptId(String label) {
        if (label == null || label.trim().isEmpty() || getString(R.string.no_scripts).equals(label)) {
            return null;
        }
        for (ScriptOption option : scriptOptions) {
            if (option.label.equals(label)) {
                return option.scriptId;
            }
        }
        return label;
    }

    private String resolveScriptLabel(String scriptId, List<ScriptOption> options) {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            return options.isEmpty() ? null : options.get(0).label;
        }
        for (ScriptOption option : options) {
            if (scriptId.equals(option.scriptId)) {
                return option.label;
            }
        }
        return null;
    }

    private void onActionChanged(String actionLabel) {
        String action = resolveActionValue(actionLabel);
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        String scriptId = resolveScriptId(scriptLabel);
        ScriptInfo info = scriptManager.getScriptInfo(scriptId);
        updatePlatformOptions(info, action);
    }

    private void addDefaultPlatforms() {
        platformOptions.add(new PlatformItem(getString(R.string.platform_tx), "tx"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_wy), "wy"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_kg), "kg"));
    }

    private int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static class ScriptOption {
        final String scriptId;
        String label;

        ScriptOption(String scriptId, String label) {
            this.scriptId = scriptId;
            this.label = label == null ? "" : label;
        }
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
}
