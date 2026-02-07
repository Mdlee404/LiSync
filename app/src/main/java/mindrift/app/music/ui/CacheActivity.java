package mindrift.app.music.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.core.cache.CacheEntry;
import mindrift.app.music.core.cache.CacheManager;

public class CacheActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CacheManager cacheManager;
    private TextView cacheSummaryText;
    private TextView cacheListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache);

        App app = (App) getApplication();
        cacheManager = app.getCacheManager();

        cacheSummaryText = findViewById(R.id.text_cache_summary);
        cacheListText = findViewById(R.id.text_cache_list);
        MaterialButton cacheRefreshButton = findViewById(R.id.button_cache_refresh);
        MaterialButton cacheClearButton = findViewById(R.id.button_cache_clear);

        cacheRefreshButton.setOnClickListener(v -> refreshData());
        cacheClearButton.setOnClickListener(v -> {
            cacheManager.clear();
            refreshData();
        });

        refreshData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        executor.execute(() -> {
            List<CacheEntry> entries = cacheManager.list();
            String summary = getString(R.string.cache_summary, entries.size());
            String cacheText = formatCacheList(entries);
            runOnUiThread(() -> {
                cacheSummaryText.setText(summary);
                cacheListText.setText(cacheText);
            });
        });
    }

    private String formatCacheList(List<CacheEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return getString(R.string.cache_empty);
        }
        long now = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (CacheEntry entry : entries) {
            long ttlSeconds = Math.max(0, (entry.getExpireAt() - now) / 1000);
            builder.append('[').append(index++).append("] ").append(entry.getKey()).append('\n');
            builder.append(getString(R.string.cache_provider_format, safe(entry.getProvider()))).append('\n');
            builder.append(getString(R.string.cache_ttl_format, formatTtl(ttlSeconds))).append('\n');
            String dataJson = new com.google.gson.Gson().toJson(entry.getData());
            builder.append(getString(R.string.cache_data_format, trim(dataJson))).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String formatTtl(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs);
    }

    private String trim(String value) {
        if (value == null) return "";
        int limit = 400;
        if (value.length() <= limit) return value;
        return value.substring(0, limit) + "...(共" + value.length() + "字)";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
