package mindrift.app.music.ui;

import android.net.Uri;
import android.os.Bundle;
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
import mindrift.app.music.core.script.ScriptManager;

public class ScriptCenterActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScriptManager scriptManager;
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

        scriptCountText = findViewById(R.id.text_script_count);
        scriptDropdown = findViewById(R.id.dropdown_script);
        scriptDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < scriptOptions.size()) {
                lastSelectedScriptId = scriptOptions.get(position).scriptId;
            }
        });

        MaterialButton importFileButton = findViewById(R.id.button_import_file);
        MaterialButton importUrlButton = findViewById(R.id.button_import_url);
        MaterialButton reloadScriptsButton = findViewById(R.id.button_reload_scripts);
        MaterialButton editScriptButton = findViewById(R.id.button_script_edit);
        MaterialButton renameScriptButton = findViewById(R.id.button_script_rename);
        MaterialButton deleteScriptButton = findViewById(R.id.button_script_delete);

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImportFile);
        importFileButton.setOnClickListener(v -> importLauncher.launch(new String[] {
                "application/javascript",
                "text/javascript",
                "application/x-javascript",
                "text/plain"
        }));
        importUrlButton.setOnClickListener(v -> showImportUrlDialog());
        reloadScriptsButton.setOnClickListener(v -> {
            scriptManager.loadScripts();
            refreshData();
        });
        editScriptButton.setOnClickListener(v -> showEditScriptDialog());
        renameScriptButton.setOnClickListener(v -> showRenameScriptDialog());
        deleteScriptButton.setOnClickListener(v -> confirmDeleteScript());

        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
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
