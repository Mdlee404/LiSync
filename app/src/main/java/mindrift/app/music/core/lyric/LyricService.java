package mindrift.app.music.core.lyric;

import android.util.Base64;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import mindrift.app.music.core.network.HttpClient;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.PlatformUtils;

public class LyricService {
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHE_SIZE = 100;
    private final HttpClient httpClient = new HttpClient();
    private final Gson gson = new Gson();
    // 使用 ConcurrentHashMap + LRU 访问顺序追踪
    private final ConcurrentHashMap<String, CacheEntry<LyricResult>> cache = new ConcurrentHashMap<>();
    private final List<String> accessOrder = new ArrayList<>();
    private final ReentrantLock cacheLock = new ReentrantLock();

    public LyricResult getLyric(String platform, String id) {
        String normalizedPlatform = PlatformUtils.normalize(platform);
        if (normalizedPlatform == null || normalizedPlatform.trim().isEmpty() || id == null || id.trim().isEmpty()) {
            return empty();
        }
        String key = "lyric_" + normalizedPlatform + "_" + id;
        LyricResult cached = getCached(key);
        if (cached != null) return cached;

        LyricResult result;
        if ("tx".equalsIgnoreCase(normalizedPlatform)) {
            result = fetchTencent(id);
        } else if ("wy".equalsIgnoreCase(normalizedPlatform)) {
            result = fetchNetease(id);
        } else if ("kg".equalsIgnoreCase(normalizedPlatform)) {
            result = fetchKugou(id);
        } else {
            result = empty();
        }
        putCached(key, new CacheEntry<>(result));
        return result;
    }

    private LyricResult fetchTencent(String id) {
        try {
            String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                    + urlEncode(id) + "&format=json&nobase64=1";
            Map<String, Object> options = new java.util.LinkedHashMap<>();
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("Referer", "https://y.qq.com/portal/player.html");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            options.put("headers", headers);
            HttpClient.ResponseData resp = httpClient.requestSync(url, options);
            JsonObject root = parseJson(resp.body);
            String lyric = getString(root, "lyric");
            if (lyric == null || lyric.isEmpty()) return empty();
            lyric = decodeHtmlEntities(lyric);
            return new LyricResult(lyric, null, null, null);
        } catch (Exception e) {
            Logger.warn("Lyric tx failed: " + e.getMessage());
            return empty();
        }
    }

    private LyricResult fetchNetease(String id) {
        try {
            // 使用 HTTPS 替代不安全的 HTTP
            String url = "https://iwenwiki.com:3000/lyric?id=" + urlEncode(id);
            HttpClient.ResponseData resp = httpClient.requestSync(url, new java.util.LinkedHashMap<>());
            JsonObject root = parseJson(resp.body);
            JsonObject lrc = getObject(root, "lrc");
            JsonObject tlrc = getObject(root, "tlyric");
            String lyric = lrc != null ? getString(lrc, "lyric") : null;
            String tlyric = tlrc != null ? getString(tlrc, "lyric") : null;
            if (lyric == null || lyric.isEmpty()) return empty();
            return new LyricResult(lyric, tlyric, null, null);
        } catch (Exception e) {
            Logger.warn("Lyric wy failed: " + e.getMessage());
            return empty();
        }
    }

    private LyricResult fetchKugou(String id) {
        try {
            // 使用 HTTPS 替代不安全的 HTTP
            String searchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash="
                    + urlEncode(id) + "&album_audio_id=";
            HttpClient.ResponseData searchResp = httpClient.requestSync(searchUrl, new java.util.LinkedHashMap<>());
            JsonObject searchRoot = parseJson(searchResp.body);
            JsonArray candidates = searchRoot.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return empty();
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            String lyricId = getString(candidate, "id");
            String accessKey = getString(candidate, "accesskey");
            if (lyricId == null || accessKey == null) return empty();

            String downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id="
                    + urlEncode(lyricId) + "&accesskey=" + urlEncode(accessKey) + "&fmt=lrc&charset=utf8";
            HttpClient.ResponseData downloadResp = httpClient.requestSync(downloadUrl, new java.util.LinkedHashMap<>());
            JsonObject downloadRoot = parseJson(downloadResp.body);
            String content = getString(downloadRoot, "content");
            if (content == null || content.isEmpty()) return empty();
            String lyric = new String(Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8);
            return new LyricResult(lyric, null, null, null);
        } catch (Exception e) {
            Logger.warn("Lyric kg failed: " + e.getMessage());
            return empty();
        }
    }

    private LyricResult empty() {
        return new LyricResult("", null, null, null);
    }

    private LyricResult getCached(String key) {
        CacheEntry<LyricResult> entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            cache.remove(key);
            removeAccessOrder(key);
            return null;
        }
        // 更新访问顺序
        updateAccessOrder(key);
        return entry.value;
    }

    private void putCached(String key, CacheEntry<LyricResult> entry) {
        cache.put(key, entry);
        cacheLock.lock();
        try {
            accessOrder.remove(key);
            accessOrder.add(key);
            // LRU 淘汰：严格限制缓存大小
            while (accessOrder.size() > MAX_CACHE_SIZE) {
                String oldest = accessOrder.remove(0);
                cache.remove(oldest);
            }
        } finally {
            cacheLock.unlock();
        }
    }

    private void updateAccessOrder(String key) {
        cacheLock.lock();
        try {
            accessOrder.remove(key);
            accessOrder.add(key);
        } finally {
            cacheLock.unlock();
        }
    }

    private void removeAccessOrder(String key) {
        cacheLock.lock();
        try {
            accessOrder.remove(key);
        } finally {
            cacheLock.unlock();
        }
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

    private String decodeHtmlEntities(String input) {
        if (input == null || input.isEmpty()) return input;
        String decoded = input.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'");
        Pattern pattern = Pattern.compile("&#(\\d+);");
        Matcher matcher = pattern.matcher(decoded);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement;
            try {
                int code = Integer.parseInt(matcher.group(1));
                replacement = String.valueOf((char) code);
            } catch (Exception e) {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
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

    public static class LyricResult {
        public final String lyric;
        public final String tlyric;
        public final String rlyric;
        public final String lxlyric;

        public LyricResult(String lyric, String tlyric, String rlyric, String lxlyric) {
            this.lyric = lyric;
            this.tlyric = tlyric;
            this.rlyric = rlyric;
            this.lxlyric = lxlyric;
        }
    }
}


