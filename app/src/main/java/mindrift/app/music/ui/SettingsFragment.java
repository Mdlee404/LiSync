package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.wearable.XiaomiWearableManager;

public class SettingsFragment extends Fragment {
    private ActivityResultLauncher<String[]> uploadLauncher;
    private XiaomiWearableManager wearableManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uploadLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            uploadMusic(uri);
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        App app = (App) requireActivity().getApplication();
        wearableManager = app.getWearableManager();
        view.findViewById(R.id.settings_button_logs)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), LogsActivity.class)));
        view.findViewById(R.id.settings_button_diagnostics)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), DiagnosticsActivity.class)));
        view.findViewById(R.id.settings_button_cache)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), CacheActivity.class)));
        view.findViewById(R.id.settings_button_search_play)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), SearchPlayActivity.class)));
        view.findViewById(R.id.settings_button_upload)
                .setOnClickListener(v -> uploadLauncher.launch(new String[]{"audio/*"}));
        view.findViewById(R.id.settings_button_notification)
                .setOnClickListener(v -> openNotificationSettings());
    }

    private void uploadMusic(Uri uri) {
        if (!isAdded() || uri == null) return;
        if (wearableManager == null || !wearableManager.isServiceConnected() || wearableManager.getCurrentNodeId() == null) {
            Toast.makeText(requireContext(), getString(R.string.upload_no_device), Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentFile doc = DocumentFile.fromSingleUri(requireContext(), uri);
        String name = doc != null && doc.getName() != null ? doc.getName() : "music_" + System.currentTimeMillis();
        Toast.makeText(requireContext(), getString(R.string.upload_starting, name), Toast.LENGTH_SHORT).show();
        wearableManager.uploadMusic(uri, new XiaomiWearableManager.UploadCallback() {
            @Override
            public void onProgress(int percent) {
                if (!isAdded() || percent % 25 != 0) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "上传进度: " + percent + "%", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onSuccess(String fileId, String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(
                        requireContext(),
                        getString(R.string.upload_finished, message == null ? fileId : message),
                        Toast.LENGTH_LONG
                ).show());
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(
                        requireContext(),
                        getString(R.string.upload_failed, message),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        }
    }
}
