package mindrift.app.music.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import mindrift.app.music.R;

public class ToolsActivity extends AppCompatActivity {
    private ActivityResultLauncher<String[]> uploadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);

        MaterialButton openDiagnosticsButton = findViewById(R.id.button_open_diagnostics);
        MaterialButton openCacheButton = findViewById(R.id.button_open_cache);
        MaterialButton openLogsButton = findViewById(R.id.button_open_logs);
        MaterialButton openSearchPlayButton = findViewById(R.id.button_open_search_play);
        MaterialButton uploadMusicButton = findViewById(R.id.button_upload_music);
        MaterialButton openSettingsButton = findViewById(R.id.button_open_settings);

        openDiagnosticsButton.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        openCacheButton.setOnClickListener(v -> startActivity(new Intent(this, CacheActivity.class)));
        openLogsButton.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));
        openSearchPlayButton.setOnClickListener(v -> startActivity(new Intent(this, SearchPlayActivity.class)));
        openSettingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        uploadLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("uploadUri", uri.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        uploadMusicButton.setOnClickListener(v -> uploadLauncher.launch(new String[] {"audio/*"}));
    }
}
