package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import mindrift.app.music.R;

public class SettingsActivity extends AppCompatActivity {
    private ActivityResultLauncher<String[]> uploadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialButton logsButton = findViewById(R.id.button_settings_open_logs);
        MaterialButton diagnosticsButton = findViewById(R.id.button_settings_open_diagnostics);
        MaterialButton cacheButton = findViewById(R.id.button_settings_open_cache);
        MaterialButton searchPlayButton = findViewById(R.id.button_settings_open_search_play);
        MaterialButton uploadButton = findViewById(R.id.button_settings_upload_music);
        MaterialButton notificationButton = findViewById(R.id.button_settings_notification);

        logsButton.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));
        diagnosticsButton.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        cacheButton.setOnClickListener(v -> startActivity(new Intent(this, CacheActivity.class)));
        searchPlayButton.setOnClickListener(v -> startActivity(new Intent(this, SearchPlayActivity.class)));
        notificationButton.setOnClickListener(v -> openNotificationSettings());

        uploadLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("uploadUri", uri.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        uploadButton.setOnClickListener(v -> uploadLauncher.launch(new String[]{"audio/*"}));
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}
