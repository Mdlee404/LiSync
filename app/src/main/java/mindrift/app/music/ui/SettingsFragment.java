package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import mindrift.app.music.R;

public class SettingsFragment extends Fragment {
    private ActivityResultLauncher<String[]> uploadLauncher;

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
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.putExtra("uploadUri", uri.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
