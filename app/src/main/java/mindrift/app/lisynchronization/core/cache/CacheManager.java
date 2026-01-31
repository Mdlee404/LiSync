package mindrift.app.lisynchronization.core.cache;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import mindrift.app.lisynchronization.utils.Logger;

public class CacheManager {
    private static final long CACHE_DURATION = 4 * 60 * 60 * 1000L;
    private final Gson gson = new Gson();
    private final File cacheFile;
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CacheManager(Context context) {
        this.cacheFile = new File(context.getFilesDir(), "cache.json");
        load();
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 10, 10, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::save, 5, 5, TimeUnit.MINUTES);
    }

    public synchronized CacheEntry get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.getExpireAt() <= System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    public synchronized void put(String key, Object data, String provider) {
        long expireAt = System.currentTimeMillis() + CACHE_DURATION;
        CacheEntry entry = new CacheEntry(key, data, provider, expireAt);
        cache.put(key, entry);
        save();
    }

    public synchronized void clear() {
        cache.clear();
        if (cacheFile.exists()) {
            if (!cacheFile.delete()) {
                Logger.warn("Cache file delete failed");
            }
        }
    }

    public synchronized List<CacheEntry> list() {
        return new ArrayList<>(cache.values());
    }

    private synchronized void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        List<String> keys = new ArrayList<>(cache.keySet());
        for (String key : keys) {
            CacheEntry entry = cache.get(key);
            if (entry != null && entry.getExpireAt() <= now) {
                cache.remove(key);
                removed++;
            }
        }
        if (removed > 0) {
            Logger.info("Cache cleaned: " + removed);
        }
    }

    private synchronized void load() {
        if (!cacheFile.exists()) return;
        try (FileInputStream input = new FileInputStream(cacheFile)) {
            byte[] data = new byte[(int) cacheFile.length()];
            int read = input.read(data);
            if (read <= 0) return;
            String json = new String(data, 0, read, StandardCharsets.UTF_8);
            Type type = new TypeToken<List<CacheEntry>>() {}.getType();
            List<CacheEntry> list = gson.fromJson(json, type);
            if (list == null) return;
            long now = System.currentTimeMillis();
            for (CacheEntry entry : list) {
                if (entry.getExpireAt() > now) {
                    cache.put(entry.getKey(), entry);
                }
            }
            Logger.info("Cache loaded: " + cache.size());
        } catch (Exception e) {
            Logger.error("Cache load failed: " + e.getMessage(), e);
        }
    }

    private synchronized void save() {
        try {
            List<CacheEntry> list = new ArrayList<>(cache.values());
            String json = gson.toJson(list);
            try (FileOutputStream output = new FileOutputStream(cacheFile, false)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Logger.error("Cache save failed: " + e.getMessage(), e);
        }
    }
}






