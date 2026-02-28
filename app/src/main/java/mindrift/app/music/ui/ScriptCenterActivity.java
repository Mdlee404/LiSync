package mindrift.app.music.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.core.proxy.RequestProxy;
import mindrift.app.music.core.search.SearchService;
import mindrift.app.music.core.script.ScriptInfo;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.core.script.SourceInfo;
import mindrift.app.music.model.ResolveRequest;

public class ScriptCenterActivity extends AppCompatActivity {
    private static final String CLOUD_LIBRARY_URL = "https://music.scriptlibrary.mindrift.cn/library.json";
    private static final String CLOUD_SOURCE_BASE_URL = "https://music.scriptlibrary.mindrift.cn/sources/";
    private static final String DEFAULT_TEST_KEYWORD = "周杰伦";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SearchService searchService = new SearchService();
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private TextView scriptCountText;
    private AutoCompleteTextView scriptDropdown;
    private final java.util.List<ScriptOption> scriptOptions = new java.util.ArrayList<>();
    private ActivityResultLauncher<String[]> importLauncher;
    private String lastSelectedScriptId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_center);

        App app = (App) getApplication();
        scriptManager = app.getScriptManager();
        requestProxy = app.getRequestProxy();

        scriptCountText = findViewById(R.id.text_script_count);
        scriptDropdown = findViewById(R.id.dropdown_script);
        scriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                lastSelectedScriptId = scriptOptions.get(position).scriptId;
            }
        });

        MaterialButton importFileButton = findViewById(R.id.button_import_file);
        MaterialButton importUrlButton = findViewById(R.id.button_import_url);
        MaterialButton importCloudButton = findViewById(R.id.button_import_cloud);
        MaterialButton reloadScriptsButton = findViewById(R.id.button_reload_scripts);
        MaterialButton editScriptButton = findViewById(R.id.button_script_edit);
        MaterialButton renameScriptButton = findViewById(R.id.button_script_rename);
        MaterialButton deleteScriptButton = findViewById(R.id.button_script_delete);
        MaterialButton testScriptButton = findViewById(R.id.button_script_test);

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImportFile);
        importFileButton.setOnClickListener(v -> importLauncher.launch(new String[] {
                "application/javascript",
                "text/javascript",
                "application/x-javascript",
                "text/plain"
        }));
        importUrlButton.setOnClickListener(v -> showImportUrlDialog());
        importCloudButton.setOnClickListener(v -> showCloudRepoDialog());
        reloadScriptsButton.setOnClickListener(v -> {
            scriptManager.loadScripts();
            refreshData();
        });
        editScriptButton.setOnClickListener(v -> showEditScriptDialog());
        renameScriptButton.setOnClickListener(v -> showRenameScriptDialog());
        deleteScriptButton.setOnClickListener(v -> confirmDeleteScript());
        testScriptButton.setOnClickListener(v -> quickTestCurrentScript());

        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        searchService.shutdown();
    }

    private void refreshData() {
        executor.execute(() -> {
            List<ScriptManager.ScriptEntry> loadedScripts = scriptManager.getLoadedScripts();
            runOnUiThread(() -> {
                scriptCountText.setText(String.valueOf(loadedScripts.size()));
                updateScriptDropdown(loadedScripts, null);
            });
        });
    }

    private void handleImportFile(Uri uri) {
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

    private void showCloudRepoDialog() {
        loadCloudRepo();
    }

    private void loadCloudRepo() {
        Toast.makeText(this, getString(R.string.cloud_repo_loading), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                ScriptManager.CloudRepoIndex index = scriptManager.fetchCloudRepoIndex(CLOUD_LIBRARY_URL);
                List<ScriptManager.CloudScriptEntry> scripts = index == null ? null : index.getScripts();
                if (scripts == null || scripts.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.cloud_repo_empty), Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> showCloudRepoPicker(index));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.cloud_repo_load_failed, e.getMessage() == null ? "unknown" : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showCloudRepoPicker(ScriptManager.CloudRepoIndex index) {
        List<ScriptManager.CloudScriptEntry> scripts = index.getScripts();
        if (scripts == null || scripts.isEmpty()) return;
        String[] labels = new String[scripts.size()];
        for (int i = 0; i < scripts.size(); i++) {
            ScriptManager.CloudScriptEntry entry = scripts.get(i);
            String name = entry == null ? "" : entry.getName();
            String desc = entry == null ? "" : entry.getDescription();
            labels[i] = desc == null || desc.isEmpty() ? name : (name + " - " + desc);
        }
        String repoName = index.getRepoName();
        String title = (repoName == null || repoName.isEmpty())
                ? getString(R.string.cloud_repo_picker_title)
                : getString(R.string.cloud_repo_picker_title_with_name, repoName);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(labels, (dialog, which) -> importCloudScript(scripts.get(which)))
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void importCloudScript(ScriptManager.CloudScriptEntry entry) {
        if (entry == null) return;
        Toast.makeText(this, getString(R.string.cloud_repo_importing), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            String importUrl = buildCloudImportUrl(entry);
            if (importUrl == null || importUrl.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.cloud_repo_load_failed, "invalid script url"),
                        Toast.LENGTH_LONG).show());
                return;
            }
            String importedScriptId = null;
            try {
                java.io.File imported = scriptManager.importFromUrlSync(importUrl);
                if (imported != null) {
                    importedScriptId = imported.getName();
                }
            } catch (Exception e) {
                final String error = e.getMessage() == null ? "unknown" : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.cloud_repo_load_failed, error),
                        Toast.LENGTH_LONG).show());
                return;
            }

            scriptManager.loadScripts();
            String testedScriptId = importedScriptId;
            runOnUiThread(() -> {
                refreshData();
                Toast.makeText(this, getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
            });
            if (testedScriptId != null && !testedScriptId.trim().isEmpty()) {
                runCloudInitTests(java.util.Collections.singletonList(testedScriptId));
            }
        });
    }

    private void quickTestCurrentScript() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null || scriptId.trim().isEmpty()) return;
        if (requestProxy == null) {
            Toast.makeText(this, getString(R.string.script_test_failed, "RequestProxy unavailable"), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.script_test_running), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            long start = SystemClock.elapsedRealtime();
            try {
                ScriptInfo info = scriptManager.getScriptInfo(scriptId);
                String source = pickQuickTestSource(info);
                if (source == null) {
                    runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                            getString(R.string.script_test_unsupported_platform)));
                    return;
                }
                SearchService.SearchResult searchResult = searchService.search(source, DEFAULT_TEST_KEYWORD, 1, 1);
                if (searchResult == null || searchResult.results == null || searchResult.results.isEmpty()) {
                    runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                            getString(R.string.script_test_failed, "search seed song failed")));
                    return;
                }
                String songId = searchResult.results.get(0).id;
                ResolveRequest request = new ResolveRequest();
                request.setSource(source);
                request.setAction("musicUrl");
                request.setQuality("128k");
                request.setNocache(true);
                request.setTargetScriptId(scriptId);
                ResolveRequest.MusicInfo musicInfo = new ResolveRequest.MusicInfo();
                musicInfo.songmid = songId;
                musicInfo.hash = songId;
                request.setMusicInfo(musicInfo);
                String response = requestProxy.resolveSync(request);
                String url = extractResolvedUrl(response);
                long costMs = SystemClock.elapsedRealtime() - start;
                if (url == null || url.isEmpty()) {
                    runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                            getString(R.string.script_test_failed, "no playable url")));
                    return;
                }
                String message = getString(R.string.script_test_success, source, costMs) + "\n" + url;
                runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title), message));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "unknown" : e.getMessage();
                runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                        getString(R.string.script_test_failed, message)));
            }
        });
    }

    private String buildCloudImportUrl(ScriptManager.CloudScriptEntry entry) {
        if (entry == null) return null;
        String directUrl = entry.getUrl();
        if (directUrl != null) {
            String trimmed = directUrl.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        String name = entry.getName();
        if (name == null || name.trim().isEmpty()) return null;
        String normalized = name.trim();
        if (normalized.endsWith(".js")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.isEmpty()) return null;
        return CLOUD_SOURCE_BASE_URL + normalized + ".js";
    }

    private void runCloudInitTests(List<String> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) return;
        int available = 0;
        int unavailable = 0;
        for (String scriptId : scriptIds) {
            if (scriptId == null || scriptId.trim().isEmpty()) {
                unavailable++;
                continue;
            }
            boolean ok = testScriptAvailability(scriptId.trim());
            if (ok) {
                available++;
            } else {
                unavailable++;
            }
        }
        int finalAvailable = available;
        int finalUnavailable = unavailable;
        runOnUiThread(() -> showTestDialog(
                getString(R.string.script_test_result_title),
                getString(R.string.cloud_test_finished, finalAvailable, finalUnavailable)
        ));
    }

    private boolean testScriptAvailability(String scriptId) {
        if (requestProxy == null || scriptId == null || scriptId.isEmpty()) return false;
        try {
            ScriptInfo info = scriptManager.getScriptInfo(scriptId);
            String source = pickQuickTestSource(info);
            if (source == null) return false;
            SearchService.SearchResult searchResult = searchService.search(source, DEFAULT_TEST_KEYWORD, 1, 1);
            if (searchResult == null || searchResult.results == null || searchResult.results.isEmpty()) {
                return false;
            }
            String songId = searchResult.results.get(0).id;
            if (songId == null || songId.trim().isEmpty()) {
                return false;
            }
            ResolveRequest request = new ResolveRequest();
            request.setSource(source);
            request.setAction("musicUrl");
            request.setQuality("128k");
            request.setNocache(true);
            request.setTargetScriptId(scriptId);
            ResolveRequest.MusicInfo musicInfo = new ResolveRequest.MusicInfo();
            musicInfo.songmid = songId;
            musicInfo.hash = songId;
            request.setMusicInfo(musicInfo);
            String response = requestProxy.resolveSync(request);
            String url = extractResolvedUrl(response);
            return url != null && !url.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String pickQuickTestSource(ScriptInfo info) {
        if (info == null || info.getSources() == null || info.getSources().isEmpty()) {
            return null;
        }
        String[] preferred = new String[] {"tx", "wy", "kg"};
        for (String source : preferred) {
            SourceInfo sourceInfo = info.getSources().get(source);
            if (sourceInfo == null) continue;
            if (sourceInfo.getType() != null && !"music".equalsIgnoreCase(sourceInfo.getType())) continue;
            List<String> actions = sourceInfo.getActions();
            if (actions == null || actions.isEmpty() || actions.contains("musicUrl")) {
                return source;
            }
        }
        return null;
    }

    private String extractResolvedUrl(String response) {
        if (response == null || response.trim().isEmpty()) return null;
        try {
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(response);
            if (!element.isJsonObject()) return null;
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
                return obj.get("url").getAsString();
            }
            if (obj.has("data")) {
                com.google.gson.JsonElement data = obj.get("data");
                if (data != null && data.isJsonPrimitive()) {
                    String value = data.getAsString();
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        return value;
                    }
                } else if (data != null && data.isJsonObject()) {
                    com.google.gson.JsonObject dataObj = data.getAsJsonObject();
                    if (dataObj.has("url") && dataObj.get("url").isJsonPrimitive()) {
                        return dataObj.get("url").getAsString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void showTestDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.action_confirm), null)
                .show();
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
                            runOnUiThread(ScriptCenterActivity.this::refreshData);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            runOnUiThread(ScriptCenterActivity.this::refreshData);
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
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
                                refreshData();
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
                                refreshData();
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
                                refreshData();
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
