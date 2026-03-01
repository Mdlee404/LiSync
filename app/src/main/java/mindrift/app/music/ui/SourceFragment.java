package mindrift.app.music.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
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

public class SourceFragment extends Fragment {
    private static final String CLOUD_LIBRARY_URL = "https://music.scriptlibrary.mindrift.cn/sources.json";
    private static final String CLOUD_SOURCE_BASE_URL = "https://music.scriptlibrary.mindrift.cn/sources/";
    private static final String DEFAULT_TEST_KEYWORD = "周杰伦";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SearchService searchService = new SearchService();
    private final java.util.List<ScriptOption> scriptOptions = new java.util.ArrayList<>();

    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private TextView scriptCountText;
    private AutoCompleteTextView scriptDropdown;
    private String lastSelectedScriptId;
    private ActivityResultLauncher<String[]> importLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_source, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImportFile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        App app = (App) requireActivity().getApplication();
        scriptManager = app.getScriptManager();
        requestProxy = app.getRequestProxy();

        scriptCountText = view.findViewById(R.id.text_script_count);
        scriptDropdown = view.findViewById(R.id.dropdown_script);
        scriptDropdown.setOnItemClickListener((parent, itemView, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                lastSelectedScriptId = scriptOptions.get(position).scriptId;
            }
        });

        view.findViewById(R.id.button_import_file).setOnClickListener(v -> importLauncher.launch(new String[]{
                "application/javascript",
                "text/javascript",
                "application/x-javascript",
                "text/plain"
        }));
        view.findViewById(R.id.button_import_url).setOnClickListener(v -> showImportUrlDialog());
        view.findViewById(R.id.button_import_cloud).setOnClickListener(v -> loadCloudRepo());
        view.findViewById(R.id.button_reload_scripts).setOnClickListener(v -> {
            scriptManager.loadScripts();
            refreshData();
        });
        view.findViewById(R.id.button_script_edit).setOnClickListener(v -> showEditScriptDialog());
        view.findViewById(R.id.button_script_rename).setOnClickListener(v -> showRenameScriptDialog());
        view.findViewById(R.id.button_script_delete).setOnClickListener(v -> confirmDeleteScript());
        view.findViewById(R.id.button_script_test).setOnClickListener(v -> quickTestCurrentScript());

        refreshData();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        scriptCountText = null;
        scriptDropdown = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        searchService.shutdown();
    }

    private void refreshData() {
        if (!isAdded() || scriptManager == null) return;
        executor.execute(() -> {
            List<ScriptManager.ScriptEntry> loadedScripts = scriptManager.getLoadedScripts();
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (scriptCountText != null) {
                    scriptCountText.setText(String.valueOf(loadedScripts.size()));
                }
                updateScriptDropdown(loadedScripts, null);
            });
        });
    }

    private void handleImportFile(Uri uri) {
        if (uri == null || !isAdded()) return;
        executor.execute(() -> {
            try (InputStream input = requireActivity().getContentResolver().openInputStream(uri)) {
                if (input == null) return;
                DocumentFile doc = DocumentFile.fromSingleUri(requireContext(), uri);
                String name = doc != null ? doc.getName() : null;
                if (name == null || name.trim().isEmpty()) {
                    name = "import_" + System.currentTimeMillis() + ".js";
                }
                scriptManager.importFromStream(name, input);
                scriptManager.loadScripts();
            } catch (Exception ignored) {
            }
            requireActivity().runOnUiThread(this::refreshData);
        });
    }

    private String getSelectedScriptId() {
        if (!isAdded() || scriptDropdown == null) return null;
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        if (scriptLabel.isEmpty() || getString(R.string.no_scripts).equals(scriptLabel)) {
            Toast.makeText(requireContext(), getString(R.string.prompt_import_first), Toast.LENGTH_SHORT).show();
            return null;
        }
        for (ScriptOption option : scriptOptions) {
            if (option.label.equals(scriptLabel)) {
                return option.scriptId;
            }
        }
        return scriptLabel;
    }

    private void showImportUrlDialog() {
        if (!isAdded()) return;
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        editText.setHint(getString(R.string.import_script_url_placeholder));
        inputLayout.addView(editText);
        inputLayout.setHint(getString(R.string.import_script_hint));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.import_script_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.import_button), (dialog, which) -> {
                    String url = editText.getText() == null ? "" : editText.getText().toString().trim();
                    if (url.isEmpty()) return;
                    scriptManager.importFromUrl(url, new ScriptManager.ImportCallback() {
                        @Override
                        public void onSuccess(java.io.File file) {
                            scriptManager.loadScripts();
                            if (isAdded()) {
                                requireActivity().runOnUiThread(SourceFragment.this::refreshData);
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(SourceFragment.this::refreshData);
                            }
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void loadCloudRepo() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), getString(R.string.cloud_repo_loading), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                ScriptManager.CloudRepoIndex index = scriptManager.fetchCloudRepoIndex(CLOUD_LIBRARY_URL);
                List<ScriptManager.CloudScriptEntry> scripts = index == null ? null : index.getScripts();
                if (scripts == null || scripts.isEmpty()) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), getString(R.string.cloud_repo_empty), Toast.LENGTH_SHORT).show());
                    return;
                }
                requireActivity().runOnUiThread(() -> showCloudRepoPicker(index));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.cloud_repo_load_failed, e.getMessage() == null ? "unknown" : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showCloudRepoPicker(ScriptManager.CloudRepoIndex index) {
        if (!isAdded()) return;
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

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(labels, (dialog, which) -> importCloudScript(scripts.get(which)))
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void importCloudScript(ScriptManager.CloudScriptEntry entry) {
        if (entry == null || !isAdded()) return;
        Toast.makeText(requireContext(), getString(R.string.cloud_repo_importing), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            String importUrl = buildCloudImportUrl(entry);
            if (importUrl == null || importUrl.isEmpty()) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
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
                String error = e.getMessage() == null ? "unknown" : e.getMessage();
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        getString(R.string.cloud_repo_load_failed, error),
                        Toast.LENGTH_LONG).show());
                return;
            }
            scriptManager.loadScripts();
            String testedScriptId = importedScriptId;
            requireActivity().runOnUiThread(() -> {
                refreshData();
                Toast.makeText(requireContext(), getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), getString(R.string.script_test_failed, "RequestProxy unavailable"), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), getString(R.string.script_test_running), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            long start = SystemClock.elapsedRealtime();
            try {
                ScriptInfo info = scriptManager.getScriptInfo(scriptId);
                String source = pickQuickTestSource(info);
                if (source == null) {
                    requireActivity().runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                            getString(R.string.script_test_unsupported_platform)));
                    return;
                }
                SearchService.SearchResult searchResult = searchService.search(source, DEFAULT_TEST_KEYWORD, 1, 1);
                if (searchResult == null || searchResult.results == null || searchResult.results.isEmpty()) {
                    requireActivity().runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
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
                    requireActivity().runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
                            getString(R.string.script_test_failed, "no playable url")));
                    return;
                }
                String message = getString(R.string.script_test_success, source, costMs) + "\n" + url;
                requireActivity().runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title), message));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "unknown" : e.getMessage();
                requireActivity().runOnUiThread(() -> showTestDialog(getString(R.string.script_test_result_title),
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
            if (!trimmed.isEmpty()) {
                String normalized = trimmed;
                if (normalized.startsWith("/")) {
                    normalized = normalized.substring(1);
                }
                if (normalized.startsWith("sources/")) {
                    normalized = normalized.substring("sources/".length());
                }
                if (normalized.endsWith(".js")) {
                    normalized = normalized.substring(0, normalized.length() - 3);
                }
                if (!normalized.isEmpty()) {
                    return CLOUD_SOURCE_BASE_URL + normalized + ".js";
                }
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
            if (testScriptAvailability(scriptId.trim())) {
                available++;
            } else {
                unavailable++;
            }
        }
        int finalAvailable = available;
        int finalUnavailable = unavailable;
        requireActivity().runOnUiThread(() -> showTestDialog(
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
        String[] preferred = new String[]{"tx", "wy", "kg"};
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
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.action_confirm), null)
                .show();
    }

    private void showRenameScriptDialog() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null || !isAdded()) return;
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setHint(getString(R.string.script_name_hint));
        editText.setText(scriptId);
        inputLayout.addView(editText);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.script_rename_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                    String newName = editText.getText() == null ? "" : editText.getText().toString().trim();
                    executor.execute(() -> {
                        String renamed = scriptManager.renameScript(scriptId, newName);
                        requireActivity().runOnUiThread(() -> {
                            if (renamed != null) {
                                scriptManager.loadScripts();
                                refreshData();
                                Toast.makeText(requireContext(), getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void showEditScriptDialog() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null || !isAdded()) return;
        executor.execute(() -> {
            String content = scriptManager.readScriptContent(scriptId);
            requireActivity().runOnUiThread(() -> openEditDialog(scriptId, content));
        });
    }

    private void openEditDialog(String scriptId, String content) {
        if (!isAdded()) return;
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(32, 16, 32, 0);

        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(10);
        editText.setHint(getString(R.string.script_content_hint));
        editText.setText(content == null ? "" : content);
        inputLayout.addView(editText);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.script_edit_title))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                    String newContent = editText.getText() == null ? "" : editText.getText().toString();
                    executor.execute(() -> {
                        boolean ok = scriptManager.updateScriptContent(scriptId, newContent);
                        requireActivity().runOnUiThread(() -> {
                            if (ok) {
                                scriptManager.loadScripts();
                                refreshData();
                                Toast.makeText(requireContext(), getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void confirmDeleteScript() {
        String scriptId = getSelectedScriptId();
        if (scriptId == null || !isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.action_delete_script))
                .setMessage(getString(R.string.script_delete_confirm, scriptId))
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> {
                    executor.execute(() -> {
                        boolean ok = scriptManager.deleteScript(scriptId);
                        requireActivity().runOnUiThread(() -> {
                            if (ok) {
                                scriptManager.loadScripts();
                                refreshData();
                                Toast.makeText(requireContext(), getString(R.string.script_op_success), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.script_op_failed), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void updateScriptDropdown(List<ScriptManager.ScriptEntry> scripts, String preferredScriptId) {
        if (!isAdded() || scriptDropdown == null) return;
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
        ArrayAdapter<String> scriptAdapter = new ArrayAdapter<>(requireContext(),
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
