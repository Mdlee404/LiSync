package mindrift.app.music.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import mindrift.app.music.R;
import mindrift.app.music.utils.AppLogBuffer;

public class LogsActivity extends AppCompatActivity {
    private TextView logOutputText;
    private final AppLogBuffer.LogListener logListener = newLine -> runOnUiThread(this::refreshLogView);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        logOutputText = findViewById(R.id.text_log_output);
        TextView copyLogsButton = findViewById(R.id.button_copy_logs);
        TextView clearLogsButton = findViewById(R.id.button_clear_logs);

        copyLogsButton.setOnClickListener(v -> copyLogs());
        clearLogsButton.setOnClickListener(v -> {
            AppLogBuffer.clear();
            refreshLogView();
        });

        AppLogBuffer.addListener(logListener);
        refreshLogView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogBuffer.removeListener(logListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogView();
    }

    private void refreshLogView() {
        if (logOutputText == null) return;
        String snapshot = AppLogBuffer.getSnapshot();
        logOutputText.setText(trimLogSnapshot(snapshot));
    }

    private void copyLogs() {
        String snapshot = AppLogBuffer.getSnapshot();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), snapshot));
        }
    }

    private String trimLogSnapshot(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return getString(R.string.no_logs);
        }
        String[] lines = snapshot.split("\\n");
        int maxLines = 150;
        int start = Math.max(0, lines.length - maxLines);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() > 400) {
                line = line.substring(0, 400) + "...";
            }
            builder.append(line);
            if (i < lines.length - 1) builder.append('\n');
        }
        return builder.toString();
    }
}
