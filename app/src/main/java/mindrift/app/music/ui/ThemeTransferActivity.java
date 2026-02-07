package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.wearable.XiaomiWearableManager;

public class ThemeTransferActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private XiaomiWearableManager wearableManager;
    private ActivityResultLauncher<String[]> pickLauncher;
    private TextView pathText;
    private TextView nameText;
    private TextView summaryText;
    private TextView statusText;
    private TextView progressText;
    private LinearProgressIndicator progressIndicator;
    private TextInputEditText themeIdInput;
    private SwitchMaterial openSwitch;
    private SwitchMaterial cleanSwitch;
    private MaterialButton pickButton;
    private MaterialButton startButton;
    private MaterialButton cancelButton;
    private XiaomiWearableManager.ThemeInfo currentThemeInfo;
    private File extractedBaseDir;
    private String currentZipLabel;
    private boolean transferInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_transfer);

        App app = (App) getApplication();
        wearableManager = app.getWearableManager();

        pathText = findViewById(R.id.text_theme_path);
        nameText = findViewById(R.id.text_theme_name);
        summaryText = findViewById(R.id.text_theme_summary);
        statusText = findViewById(R.id.text_theme_status);
        progressText = findViewById(R.id.text_theme_progress);
        progressIndicator = findViewById(R.id.progress_theme_transfer);
        themeIdInput = findViewById(R.id.input_theme_id);
        openSwitch = findViewById(R.id.switch_theme_open);
        cleanSwitch = findViewById(R.id.switch_theme_clean);
        pickButton = findViewById(R.id.button_pick_theme);
        startButton = findViewById(R.id.button_theme_start);
        cancelButton = findViewById(R.id.button_theme_cancel);

        pickLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handlePickResult);
        pickButton.setOnClickListener(v -> pickLauncher.launch(new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/java-archive",
                "application/octet-stream"
        }));
        startButton.setOnClickListener(v -> startTransfer());
        cancelButton.setOnClickListener(v -> cancelTransfer());

        updateTransferState(false);
        updateStatus(getString(R.string.theme_transfer_status_idle));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (!transferInProgress) {
            cleanupExtractedDir();
        }
    }

    private void handlePickResult(Uri uri) {
        if (uri == null) return;
        cleanupExtractedDir();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String label = uri.toString();
        try {
            androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri);
            if (doc != null && doc.getName() != null) {
                label = doc.getName();
            }
        } catch (Exception ignored) {
        }
        currentZipLabel = label;
        pathText.setText(label);
        nameText.setText(getString(R.string.theme_transfer_name_placeholder));
        summaryText.setText(getString(R.string.theme_transfer_summary_placeholder));
        updateStatus(getString(R.string.theme_transfer_status_extracting));
        progressIndicator.setProgressCompat(0, false);
        progressText.setText(getString(R.string.theme_transfer_progress_format, 0, 0, 0));
        currentThemeInfo = null;
        updateTransferState(false);

        executor.execute(() -> {
            try {
                File rootDir = extractZipToPrivate(uri);
                XiaomiWearableManager.ThemeInfo info = wearableManager.inspectTheme(rootDir);
                runOnUiThread(() -> {
                    if (rootDir != null) {
                        pathText.setText(currentZipLabel == null ? rootDir.getName() : currentZipLabel);
                    }
                    applyThemeInfo(info);
                });
            } catch (XiaomiWearableManager.ThemeTransferException e) {
                runOnUiThread(() -> showError(getString(R.string.theme_transfer_status_failed, e.getMessage())));
            } catch (Exception e) {
                runOnUiThread(() -> showError(getString(R.string.theme_transfer_status_failed, e.getMessage())));
            }
        });
    }

    private void applyThemeInfo(XiaomiWearableManager.ThemeInfo info) {
        currentThemeInfo = info;
        String themeId = info == null ? "" : info.getThemeId();
        if (themeId != null) {
            themeId = themeId.trim().toLowerCase(Locale.US);
        }
        themeIdInput.setText(themeId == null ? "" : themeId);
        String name = info == null ? "" : info.getThemeName();
        if (name == null || name.trim().isEmpty()) {
            nameText.setText(getString(R.string.theme_transfer_name_placeholder));
        } else {
            nameText.setText(getString(R.string.theme_transfer_name_format, name));
        }
        if (info != null) {
            summaryText.setText(getString(R.string.theme_transfer_summary_format,
                    info.getFileCount(), formatBytes(info.getTotalBytes()), info.getTotalChunks()));
            progressText.setText(getString(R.string.theme_transfer_progress_format, 0, 0, info.getFileCount()));
        } else {
            summaryText.setText(getString(R.string.theme_transfer_summary_placeholder));
        }
        progressIndicator.setProgressCompat(0, false);
        updateStatus(getString(R.string.theme_transfer_status_ready));
        updateTransferState(false);
    }

    private void startTransfer() {
        if (transferInProgress) return;
        if (currentThemeInfo == null) {
            Toast.makeText(this, getString(R.string.theme_transfer_summary_placeholder), Toast.LENGTH_SHORT).show();
            return;
        }
        String themeId = themeIdInput.getText() == null ? "" : themeIdInput.getText().toString().trim();
        if (!themeId.isEmpty()) {
            themeId = themeId.toLowerCase(Locale.US);
            themeIdInput.setText(themeId);
        }
        if (!XiaomiWearableManager.isValidThemeId(themeId)) {
            showError(getString(R.string.theme_transfer_invalid_id, themeId));
            return;
        }
        if (wearableManager == null || !wearableManager.isServiceConnected() || wearableManager.getCurrentNodeId() == null) {
            Toast.makeText(this, getString(R.string.theme_transfer_no_device), Toast.LENGTH_SHORT).show();
            return;
        }
        transferInProgress = true;
        updateTransferState(true);
        updateStatus(getString(R.string.theme_transfer_status_sending));
        progressIndicator.setProgressCompat(0, true);
        progressText.setText(getString(R.string.theme_transfer_progress_format, 0, 0, currentThemeInfo.getFileCount()));

        XiaomiWearableManager.ThemeTransferOptions options = new XiaomiWearableManager.ThemeTransferOptions();
        options.openPage = openSwitch.isChecked();
        options.clean = cleanSwitch.isChecked();
        options.themeIdOverride = themeId;

        wearableManager.transferTheme(currentThemeInfo, options, new XiaomiWearableManager.ThemeTransferCallback() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> updateStatus(message));
            }

            @Override
            public void onProgress(int percent, int filesSent, int totalFiles) {
                runOnUiThread(() -> {
                    progressIndicator.setProgressCompat(percent, true);
                    progressText.setText(getString(R.string.theme_transfer_progress_format, percent, filesSent, totalFiles));
                });
            }

            @Override
            public void onSuccess(String themeId) {
                runOnUiThread(() -> {
                    transferInProgress = false;
                    progressIndicator.setProgressCompat(100, true);
                    updateStatus(getString(R.string.theme_transfer_status_finished));
                    updateTransferState(false);
                    cleanupExtractedDir();
                    Toast.makeText(ThemeTransferActivity.this, getString(R.string.theme_transfer_status_finished), Toast.LENGTH_SHORT).show();
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ThemeTransferActivity.this)
                            .setTitle(getString(R.string.dialog_common_title))
                            .setMessage(getString(R.string.theme_transfer_restart_prompt))
                            .setPositiveButton(getString(R.string.action_confirm), null)
                            .setCancelable(false)
                            .show();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    transferInProgress = false;
                    String reason = message == null ? "" : message;
                    if (reason.contains("取消")) {
                        updateStatus(getString(R.string.theme_transfer_status_cancelled));
                    } else {
                        updateStatus(getString(R.string.theme_transfer_status_failed, reason));
                    }
                    updateTransferState(false);
                    cleanupExtractedDir();
                    Toast.makeText(ThemeTransferActivity.this,
                            reason.contains("取消")
                                    ? getString(R.string.theme_transfer_status_cancelled)
                                    : getString(R.string.theme_transfer_status_failed, reason),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void cancelTransfer() {
        if (!transferInProgress) return;
        updateStatus(getString(R.string.theme_transfer_status_cancelled));
        wearableManager.cancelThemeTransfer(cleanSwitch.isChecked());
    }

    private void updateStatus(String message) {
        statusText.setText(message == null ? "" : message);
    }

    private void updateTransferState(boolean inProgress) {
        pickButton.setEnabled(!inProgress);
        startButton.setEnabled(!inProgress && currentThemeInfo != null);
        cancelButton.setEnabled(inProgress);
    }

    private void showError(String message) {
        String text = message == null ? getString(R.string.theme_transfer_status_failed, "") : message;
        transferInProgress = false;
        updateStatus(text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        updateTransferState(false);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        double value = bytes;
        String[] units = new String[]{"B", "KB", "MB"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024.0;
            index++;
        }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[index]);
    }

    private File extractZipToPrivate(Uri uri) throws Exception {
        if (uri == null) {
            throw new Exception(getString(R.string.theme_transfer_zip_missing));
        }
        File baseDir = new File(getFilesDir(), "theme_imports");
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new Exception(getString(R.string.theme_transfer_extract_failed, "create dir failed"));
        }
        File workDir = new File(baseDir, "theme_" + System.currentTimeMillis());
        if (!workDir.mkdirs()) {
            throw new Exception(getString(R.string.theme_transfer_extract_failed, "create dir failed"));
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new Exception(getString(R.string.theme_transfer_extract_failed, "open zip failed"));
            }
            try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(input))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zipInput.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    String normalized = normalizeZipEntry(name);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    if (isInvalidZipPath(normalized)) {
                        throw new Exception(getString(R.string.theme_transfer_extract_failed, "invalid path: " + normalized));
                    }
                    File target = new File(workDir, normalized);
                    String canonicalTarget = target.getCanonicalPath();
                    String canonicalBase = workDir.getCanonicalPath() + File.separator;
                    if (!canonicalTarget.startsWith(canonicalBase)) {
                        throw new Exception(getString(R.string.theme_transfer_extract_failed, "invalid path: " + normalized));
                    }
                    if (entry.isDirectory()) {
                        if (!target.exists() && !target.mkdirs()) {
                            throw new Exception(getString(R.string.theme_transfer_extract_failed, "create dir failed"));
                        }
                        continue;
                    }
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new Exception(getString(R.string.theme_transfer_extract_failed, "create dir failed"));
                    }
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (Exception e) {
            deleteRecursively(workDir);
            throw new Exception(getString(R.string.theme_transfer_extract_failed, e.getMessage()));
        }
        File themeRoot = resolveThemeRoot(workDir);
        if (themeRoot == null) {
            deleteRecursively(workDir);
            throw new Exception(getString(R.string.theme_transfer_extract_failed, "theme.json missing"));
        }
        extractedBaseDir = workDir;
        return themeRoot;
    }

    private File resolveThemeRoot(File baseDir) {
        if (baseDir == null || !baseDir.isDirectory()) return null;
        File directTheme = new File(baseDir, "theme.json");
        if (directTheme.exists() && directTheme.isFile()) {
            return baseDir;
        }
        File[] children = baseDir.listFiles();
        if (children == null) return null;
        File candidate = null;
        for (File child : children) {
            if (child != null && child.isDirectory()) {
                File themeJson = new File(child, "theme.json");
                if (themeJson.exists() && themeJson.isFile()) {
                    if (candidate != null) {
                        return null;
                    }
                    candidate = child;
                }
            }
        }
        return candidate;
    }

    private void cleanupExtractedDir() {
        if (extractedBaseDir == null) return;
        deleteRecursively(extractedBaseDir);
        extractedBaseDir = null;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private String normalizeZipEntry(String name) {
        String normalized = name.replace("\\", "/");
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private boolean isInvalidZipPath(String path) {
        if (path == null || path.trim().isEmpty()) return true;
        String value = path.trim();
        if (value.startsWith("/") || value.startsWith("\\") || value.startsWith("internal://")) return true;
        return value.contains("..");
    }
}
