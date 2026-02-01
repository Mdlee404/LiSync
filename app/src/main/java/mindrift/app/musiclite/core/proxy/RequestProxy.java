package mindrift.app.musiclite.core.proxy;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.musiclite.core.cache.CacheEntry;
import mindrift.app.musiclite.core.cache.CacheManager;
import mindrift.app.musiclite.core.script.ScriptManager;
import mindrift.app.musiclite.model.ResolveRequest;
import mindrift.app.musiclite.utils.Logger;

public class RequestProxy {
    public interface ResolveCallback {
        void onSuccess(String responseJson);
        void onFailure(Exception e);
    }

    private final ScriptManager scriptManager;
    private final CacheManager cacheManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private static final long REQUEST_TIMEOUT_MS = 4000;
    private static final int LOG_LIMIT = 2000;

    public RequestProxy(ScriptManager scriptManager, CacheManager cacheManager) {
        this.scriptManager = scriptManager;
        this.cacheManager = cacheManager;
    }

    public void resolve(ResolveRequest request, ResolveCallback callback) {
        executor.execute(() -> {
            try {
                String response = resolveSync(request);
                callback.onSuccess(response);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public String resolveSync(ResolveRequest request) throws Exception {
        if (request == null) throw new Exception("Request is null");
        String source = request.getSource();
        String action = request.getAction();
        String songId = request.resolveSongId();
        String quality = request.getQuality();
        boolean nocache = request.isNocache();

        if (source == null || source.isEmpty()) throw new Exception("Source missing");
        if (songId == null || songId.isEmpty()) throw new Exception("SongId missing");
        if (action == null || action.isEmpty()) action = "musicUrl";
        if (quality == null || quality.isEmpty()) quality = "128k";

        String cacheKey = source + ":" + songId + ":" + action + ":" + quality;
        Logger.info("Resolve " + action + " @ " + source + " - " + songId);

        if (!nocache) {
            CacheEntry cached = cacheManager.get(cacheKey);
            if (cached != null) {
                Logger.info("Cache hit: " + cacheKey + " via " + cached.getProvider());
                return buildResponse(request, action, quality, songId, cached.getData(), cached.getProvider());
            }
        } else {
            Logger.info("Cache skipped (nocache=true)");
        }

        if (request.getTargetScriptId() != null && !request.getTargetScriptId().isEmpty()) {
            ScriptHandler handler = scriptManager.getHandlerById(source, request.getTargetScriptId(), action);
            if (handler == null) {
                throw new Exception("No provider found for source: " + source);
            }
            return executeWithHandler(request, handler, cacheKey, action, quality, songId);
        }

        List<ScriptHandler> handlers = scriptManager.getOrderedHandlers(source, action);
        if (handlers.isEmpty()) {
            throw new Exception("No provider found for source: " + source);
        }

        Exception lastError = null;
        for (ScriptHandler handler : handlers) {
            try {
                return executeWithHandler(request, handler, cacheKey, action, quality, songId);
            } catch (Exception e) {
                lastError = e;
                Logger.warn("Provider failed: " + handler.getScriptId() + " - " + e.getMessage());
            }
        }

        throw new Exception(lastError == null ? "All providers failed" : lastError.getMessage());
    }

    private String buildResponse(ResolveRequest request, String action, String quality, String songId, Object data, String provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", 0);
        payload.put("message", "ok");
        payload.put("data", data);
        payload.put("provider", provider);
        if ("musicUrl".equals(action) && data instanceof String) {
            payload.put("url", data);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("platform", request == null ? null : request.getSource());
        info.put("action", action);
        info.put("quality", quality);
        info.put("songId", songId);
        info.put("provider", provider);
        payload.put("info", info);
        return gson.toJson(payload);
    }

    private String executeWithHandler(ResolveRequest request, ScriptHandler handler, String cacheKey, String action, String quality, String songId) throws Exception {
        String targetQuality = quality;
        if (!handler.supportsQuality(targetQuality)) {
            Logger.warn("Quality not supported by handler, fallback to 128k");
            targetQuality = "128k";
        }
        Map<String, Object> requestPayload = request.buildScriptRequest(targetQuality, action);
        Logger.info("Dispatch to handler: " + handler.getScriptId() + " payload=" + trimLog(gson.toJson(requestPayload)));
        String responseJson = scriptManager.dispatchRequest(handler.getScriptId(), gson.toJson(requestPayload), REQUEST_TIMEOUT_MS);
        Logger.info("Handler response: " + handler.getScriptId() + " payload=" + trimLog(responseJson));
        Map<String, Object> response = gson.fromJson(responseJson, Map.class);
        if (response != null && response.get("error") != null) {
            throw new Exception(String.valueOf(response.get("error")));
        }

        Object data = response;
        if (response != null && response.containsKey("url")) {
            data = response.get("url");
        }
        cacheManager.put(cacheKey, data, handler.getScriptId());
        return buildResponse(request, action, targetQuality, songId, data, handler.getScriptId());
    }

    private String trimLog(String value) {
        if (value == null) return "null";
        if (value.length() <= LOG_LIMIT) return value;
        return value.substring(0, LOG_LIMIT) + "...(+" + (value.length() - LOG_LIMIT) + " chars)";
    }
}







