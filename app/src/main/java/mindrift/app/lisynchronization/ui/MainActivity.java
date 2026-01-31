package mindrift.app.lisynchronization.ui;

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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.lisynchronization.App;
import mindrift.app.lisynchronization.R;
import mindrift.app.lisynchronization.core.cache.CacheEntry;
import mindrift.app.lisynchronization.core.cache.CacheManager;
import mindrift.app.lisynchronization.core.script.ScriptManager;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private TextView scriptCountText;
    private TextView cacheCountText;
    private TextView lastUpdatedText;
    private AutoCompleteTextView scriptDropdown;
    private final java.util.List<String> scriptOptions = new java.util.ArrayList<>();
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        App app = (App) getApplication();
        cacheManager = app.getCacheManager();
        scriptManager = app.getScriptManager();

        scriptCountText = findViewById(R.id.text_script_count);
        cacheCountText = findViewById(R.id.text_cache_count);
        lastUpdatedText = findViewById(R.id.text_last_updated);
        scriptDropdown = findViewById(R.id.dropdown_script_home);

        MaterialButton refreshButton = findViewById(R.id.button_refresh);
        MaterialButton clearCacheButton = findViewById(R.id.button_clear_cache);
        MaterialButton importFileButton = findViewById(R.id.button_import_file);
        MaterialButton importUrlButton = findViewById(R.id.button_import_url);
        MaterialButton reloadScriptsButton = findViewById(R.id.button_reload_scripts);
        MaterialButton editScriptButton = findViewById(R.id.button_script_edit_home);
        MaterialButton renameScriptButton = findViewById(R.id.button_script_rename_home);
        MaterialButton deleteScriptButton = findViewById(R.id.button_script_delete_home);
        MaterialButton openSettingsButton = findViewById(R.id.button_open_settings);

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
        editScriptButton.setOnClickListener(v -> showEditScriptDialog());
        renameScriptButton.setOnClickListener(v -> showRenameScriptDialog());
        deleteScriptButton.setOnClickListener(v -> confirmDeleteScript());
        openSettingsButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, SettingsActivity.class)));

        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

    private String getSelectedScriptId() {
        String scriptLabel = scriptDropdown.getText() == null ? "" : scriptDropdown.getText().toString();
        if (scriptLabel.isEmpty() || getString(R.string.no_scripts).equals(scriptLabel)) {
            Toast.makeText(this, getString(R.string.prompt_import_first), Toast.LENGTH_SHORT).show();
            return null;
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
                                refreshData();
                                scriptDropdown.setText(renamed, false);
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
}
