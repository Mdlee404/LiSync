package mindrift.app.music.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.core.proxy.RequestProxy;
import mindrift.app.music.core.search.SearchService;
import mindrift.app.music.model.ResolveRequest;
import mindrift.app.music.utils.PlatformUtils;

public class SearchPlayActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SearchService searchService = new SearchService();
    private RequestProxy requestProxy;
    private AutoCompleteTextView platformDropdown;
    private TextInputEditText keywordInput;
    private TextInputEditText pageInput;
    private MaterialButton searchButton;
    private TextView statusText;
    private TextView emptyText;
    private LinearLayout resultsLayout;
    private final List<PlatformItem> platformOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_play);

        App app = (App) getApplication();
        requestProxy = app.getRequestProxy();

        platformDropdown = findViewById(R.id.dropdown_search_platform);
        keywordInput = findViewById(R.id.input_search_keyword);
        pageInput = findViewById(R.id.input_search_page);
        searchButton = findViewById(R.id.button_search_play);
        statusText = findViewById(R.id.text_search_status);
        emptyText = findViewById(R.id.text_search_empty);
        resultsLayout = findViewById(R.id.layout_search_results);

        setupPlatformOptions();
        searchButton.setOnClickListener(v -> runSearch());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        searchService.shutdown();
    }

    private void setupPlatformOptions() {
        platformOptions.clear();
        platformOptions.add(new PlatformItem(getString(R.string.search_play_platform_all), ""));
        platformOptions.add(new PlatformItem(getString(R.string.platform_tx), "tx"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_wy), "wy"));
        platformOptions.add(new PlatformItem(getString(R.string.platform_kg), "kg"));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mapPlatformLabels());
        platformDropdown.setAdapter(adapter);
        platformDropdown.setText(platformOptions.get(0).label, false);
    }

    private String[] mapPlatformLabels() {
        String[] labels = new String[platformOptions.size()];
        for (int i = 0; i < platformOptions.size(); i++) {
            labels[i] = platformOptions.get(i).label;
        }
        return labels;
    }

    private void runSearch() {
        String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, getString(R.string.prompt_input_keyword), Toast.LENGTH_SHORT).show();
            return;
        }
        String platformLabel = platformDropdown.getText() == null ? "" : platformDropdown.getText().toString();
        String platform = resolvePlatformValue(platformLabel);
        int page = parseInt(pageInput.getText() == null ? "" : pageInput.getText().toString().trim(), 1);

        searchButton.setEnabled(false);
        statusText.setText(getString(R.string.search_play_status_searching));
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setText(getString(R.string.search_play_empty));
        resultsLayout.removeAllViews();

        executor.execute(() -> {
            SearchService.SearchResult result = searchService.search(platform, keyword, page, 20);
            runOnUiThread(() -> {
                searchButton.setEnabled(true);
                updateResults(result);
            });
        });
    }

    private void updateResults(SearchService.SearchResult result) {
        resultsLayout.removeAllViews();
        if (result == null || result.results == null || result.results.isEmpty()) {
            statusText.setText(getString(R.string.search_play_status_empty));
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        statusText.setText(getString(R.string.search_play_status_result, result.results.size()));
        emptyText.setVisibility(View.GONE);
        for (SearchService.SearchItem item : result.results) {
            View card = buildResultView(item);
            resultsLayout.addView(card);
        }
    }

    private View buildResultView(SearchService.SearchItem item) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_search_play_result, resultsLayout, false);
        TextView title = view.findViewById(R.id.text_result_title);
        TextView subtitle = view.findViewById(R.id.text_result_subtitle);
        TextView meta = view.findViewById(R.id.text_result_meta);
        title.setText(item.title == null ? "" : item.title);
        subtitle.setText(buildSubtitle(item));
        meta.setText(buildMeta(item));
        view.setOnClickListener(v -> resolveAndPlay(item, view));
        return view;
    }

    private void resolveAndPlay(SearchService.SearchItem item, View cardView) {
        if (item == null || item.id == null || item.id.isEmpty()) return;
        cardView.setEnabled(false);
        statusText.setText(getString(R.string.search_play_status_resolving, safe(item.title)));
        executor.execute(() -> {
            String url = null;
            String error = null;
            try {
                ResolveRequest request = new ResolveRequest();
                request.setSource(item.source);
                request.setAction("musicUrl");
                request.setQuality("128k");
                request.setSongId(item.id);
                ResolveRequest.MusicInfo musicInfo = new ResolveRequest.MusicInfo();
                musicInfo.songmid = item.id;
                musicInfo.hash = item.id;
                request.setMusicInfo(musicInfo);
                String response = requestProxy.resolveSync(request);
                url = extractUrl(response);
                if (url == null || url.trim().isEmpty()) {
                    error = getString(R.string.result_empty);
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            String resolvedUrl = url;
            String resolvedError = error;
            runOnUiThread(() -> {
                cardView.setEnabled(true);
                if (resolvedUrl != null && !resolvedUrl.trim().isEmpty()) {
                    statusText.setText(getString(R.string.search_play_status_opened));
                    openPlayer(resolvedUrl);
                } else {
                    String message = resolvedError == null || resolvedError.trim().isEmpty()
                            ? getString(R.string.result_empty)
                            : resolvedError.trim();
                    statusText.setText(getString(R.string.search_play_status_failed, message));
                    Toast.makeText(this, getString(R.string.search_play_status_failed, message), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void openPlayer(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), "audio/*");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.search_play_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private String buildSubtitle(SearchService.SearchItem item) {
        String artist = safe(item.artist);
        String album = safe(item.album);
        if (!artist.isEmpty() && !album.isEmpty()) {
            return artist + " · " + album;
        }
        if (!artist.isEmpty()) return artist;
        if (!album.isEmpty()) return album;
        return "";
    }

    private String buildMeta(SearchService.SearchItem item) {
        String platform = PlatformUtils.displayName(item.source);
        String duration = formatDuration(item.duration);
        if (!duration.isEmpty()) {
            return platform + " · " + duration;
        }
        return platform;
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return "";
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs);
    }

    private String resolvePlatformValue(String label) {
        for (PlatformItem item : platformOptions) {
            if (item.label.equals(label)) {
                return item.value;
            }
        }
        return label;
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String extractUrl(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(response);
            if (!element.isJsonObject()) {
                return extractPlainUrl(response);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
                return obj.get("url").getAsString();
            }
            if (obj.has("data")) {
                JsonElement data = obj.get("data");
                if (data != null && data.isJsonObject()) {
                    JsonObject dataObj = data.getAsJsonObject();
                    if (dataObj.has("url") && dataObj.get("url").isJsonPrimitive()) {
                        return dataObj.get("url").getAsString();
                    }
                }
                if (data != null && data.isJsonPrimitive()) {
                    return extractPlainUrl(data.getAsString());
                }
            }
            return null;
        } catch (Exception e) {
            return extractPlainUrl(response);
        }
    }

    private String extractPlainUrl(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class PlatformItem {
        final String label;
        final String value;

        PlatformItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}
