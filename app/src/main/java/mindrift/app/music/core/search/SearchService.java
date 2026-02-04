package mindrift.app.music.core.search;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mindrift.app.music.core.network.HttpClient;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.PlatformUtils;

public class SearchService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHE_SIZE = 100;
    private final HttpClient httpClient = new HttpClient();
    private final Gson gson = new Gson();
    private final Map<String, CacheEntry<SearchResult>> cache = new LinkedHashMap<String, CacheEntry<SearchResult>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<SearchResult>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public SearchResult search(String platform, String keyword, int page, Integer pageSize) {
        String normalizedPlatform = PlatformUtils.normalize(platform);
        String kw = normalizeKeyword(keyword);
        if (kw.isEmpty()) {
            return new SearchResult(normalizedPlatform, page, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize, 0, new ArrayList<>());
        }
        int safePage = Math.max(1, page);
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;

        List<String> platforms = buildPlatformOrder(normalizedPlatform);
        for (String source : platforms) {
            String cacheKey = "search_" + source + "_" + kw + "_p" + safePage + "_s" + size;
            SearchResult cached = getCached(cacheKey);
            if (cached != null) return cached;

            SearchResult result = searchByPlatform(source, kw, safePage, size);
            if (result != null && !result.results.isEmpty()) {
                cache.put(cacheKey, new CacheEntry<>(result));
                return result;
            }
        }
        return new SearchResult(normalizedPlatform, safePage, size, 0, new ArrayList<>());
    }

    private List<String> buildPlatformOrder(String platform) {
        Set<String> ordered = new LinkedHashSet<>();
        if (platform != null && !platform.trim().isEmpty()) {
            ordered.add(platform.trim());
        }
        ordered.add("tx");
        ordered.add("wy");
        ordered.add("kg");
        return new ArrayList<>(ordered);
    }

    private SearchResult getCached(String key) {
        CacheEntry<SearchResult> entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    private SearchResult searchByPlatform(String platform, String keyword, int page, int pageSize) {
        if ("tx".equalsIgnoreCase(platform)) {
            return searchTencent(keyword, page, pageSize);
        }
        if ("wy".equalsIgnoreCase(platform)) {
            return searchNetease(keyword, page, pageSize);
        }
        if ("kg".equalsIgnoreCase(platform)) {
            return searchKugou(keyword, page, pageSize);
        }
        return null;
    }

    private SearchResult searchTencent(String keyword, int page, int pageSize) {
        try {
            String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?aggr=1&cr=1&p="
                    + page + "&n=" + pageSize + "&w=" + urlEncode(keyword) + "&format=json";
            Map<String, Object> options = new LinkedHashMap<>();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Referer", "https://y.qq.com/");
            options.put("headers", headers);
            HttpClient.ResponseData resp = httpClient.requestSync(url, options);
            JsonObject root = parseJson(resp.body);
            JsonObject data = getObject(root, "data");
            JsonObject song = data != null ? getObject(data, "song") : getObject(root, "song");
            JsonArray list = song != null ? song.getAsJsonArray("list") : null;
            List<SearchItem> results = new ArrayList<>();
            if (list != null) {
                for (JsonElement el : list) {
                    JsonObject item = el.getAsJsonObject();
                    String id = getString(item, "songmid");
                    String title = getString(item, "songname");
                    String artist = buildArtist(item.getAsJsonArray("singer"));
                    String album = getString(item, "albumname");
                    Integer duration = getIntObj(item, "interval");
                    if (id != null && !id.isEmpty()) {
                        results.add(new SearchItem("tx", id, title, artist, album, duration, null));
                    }
                }
            }
            int total = song != null ? getInt(song, "totalnum", getInt(song, "total", 0)) : 0;
            return new SearchResult("tx", page, pageSize, total, results);
        } catch (Exception e) {
            Logger.warn("Search tx failed: " + e.getMessage());
            return null;
        }
    }

    private SearchResult searchNetease(String keyword, int page, int pageSize) {
        try {
            int offset = (Math.max(1, page) - 1) * pageSize;
            String url = "http://iwenwiki.com:3000/search?keywords="
                    + urlEncode(keyword) + "&limit=" + pageSize + "&offset=" + offset;
            HttpClient.ResponseData resp = httpClient.requestSync(url, new LinkedHashMap<>());
            JsonObject root = parseJson(resp.body);
            JsonObject result = getObject(root, "result");
            JsonArray list = result != null ? result.getAsJsonArray("songs") : null;
            List<SearchItem> results = new ArrayList<>();
            if (list != null) {
                for (JsonElement el : list) {
                    JsonObject item = el.getAsJsonObject();
                    String id = getString(item, "id");
                    String title = getString(item, "name");
                    String artist = buildArtist(item.getAsJsonArray("artists"));
                    String album = null;
                    Integer duration = null;
                    JsonObject albumObj = getObject(item, "album");
                    if (albumObj != null) {
                        album = getString(albumObj, "name");
                        duration = getIntObj(item, "duration");
                    } else {
                        duration = getIntObj(item, "duration");
                    }
                    String picUrl = albumObj != null ? getString(albumObj, "picUrl") : null;
                    if (duration != null && duration > 1000) {
                        duration = duration / 1000;
                    }
                    if (id != null && !id.isEmpty()) {
                        results.add(new SearchItem("wy", id, title, artist, album, duration, picUrl));
                    }
                }
            }
            int total = result != null ? getInt(result, "songCount", 0) : 0;
            return new SearchResult("wy", page, pageSize, total, results);
        } catch (Exception e) {
            Logger.warn("Search wy failed: " + e.getMessage());
            return null;
        }
    }

    private SearchResult searchKugou(String keyword, int page, int pageSize) {
        try {
            String url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                    + urlEncode(keyword) + "&page=" + Math.max(1, page) + "&pagesize=" + pageSize;
            Map<String, Object> options = new LinkedHashMap<>();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.put("Referer", "http://kugou.com/");
            options.put("headers", headers);
            HttpClient.ResponseData resp = httpClient.requestSync(url, options);
            JsonObject root = parseJson(resp.body);
            JsonObject data = getObject(root, "data");
            JsonArray list = data != null ? data.getAsJsonArray("info") : null;
            List<SearchItem> results = new ArrayList<>();
            if (list != null) {
                for (JsonElement el : list) {
                    JsonObject item = el.getAsJsonObject();
                    String id = getString(item, "hash");
                    String title = getString(item, "songname");
                    String artist = getString(item, "singername");
                    String album = getString(item, "album_name");
                    if (album == null) album = getString(item, "albumname");
                    Integer duration = getIntObj(item, "duration");
                    String picUrl = getString(item, "imgurl");
                    if (picUrl != null) {
                        picUrl = picUrl.replace("{size}", "400");
                    }
                    if (id != null && !id.isEmpty()) {
                        results.add(new SearchItem("kg", id, title, artist, album, duration, picUrl));
                    }
                }
            }
            int total = data != null ? getInt(data, "total", 0) : 0;
            return new SearchResult("kg", page, pageSize, total, results);
        } catch (Exception e) {
            Logger.warn("Search kg failed: " + e.getMessage());
            return null;
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        return keyword.trim().replaceAll("\\s+", " ");
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private JsonObject parseJson(String raw) {
        if (raw == null) return new JsonObject();
        String payload = raw.trim();
        if (payload.startsWith("MusicJsonCallback(")) {
            payload = payload.substring("MusicJsonCallback(".length());
            if (payload.endsWith(")")) payload = payload.substring(0, payload.length() - 1);
        } else if (payload.startsWith("callback(")) {
            payload = payload.substring("callback(".length());
            if (payload.endsWith(")")) payload = payload.substring(0, payload.length() - 1);
        }
        return gson.fromJson(payload, JsonObject.class);
    }

    private JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) return null;
            return el.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer getIntObj(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) return null;
            return el.getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildArtist(JsonArray array) {
        if (array == null || array.size() == 0) return "";
        List<String> names = new ArrayList<>();
        for (JsonElement el : array) {
            JsonObject item = el.getAsJsonObject();
            String name = getString(item, "name");
            if (name != null && !name.isEmpty()) names.add(name);
        }
        return String.join(" / ", names);
    }

    private static class CacheEntry<T> {
        final long timestamp = System.currentTimeMillis();
        final T value;

        CacheEntry(T value) {
            this.value = value;
        }
    }

    public void shutdown() {
        cache.clear();
        httpClient.shutdown();
    }

    public static class SearchItem {
        public final String source;
        public final String id;
        public final String title;
        public final String artist;
        public final String album;
        public final Integer duration;
        public final String picUrl;

        public SearchItem(String source, String id, String title, String artist, String album, Integer duration, String picUrl) {
            this.source = source;
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.picUrl = picUrl;
        }
    }

    public static class SearchResult {
        public final String platform;
        public final int page;
        public final int pageSize;
        public final int total;
        public final List<SearchItem> results;

        public SearchResult(String platform, int page, int pageSize, int total, List<SearchItem> results) {
            this.platform = platform;
            this.page = page;
            this.pageSize = pageSize;
            this.total = total;
            this.results = results == null ? new ArrayList<>() : results;
        }
    }
}


