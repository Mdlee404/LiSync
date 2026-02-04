package mindrift.app.music.core.proxy;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.music.core.cache.CacheEntry;
import mindrift.app.music.core.cache.CacheManager;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.model.ResolveRequest;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.PlatformUtils;

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

    public RequestProxy(ScriptManager scriptManager, CacheManager cacheManager) {
        this.scriptManager = scriptManager;
        this.cacheManager = cacheManager;
    }

    public void resolve(ResolveRequest request, ResolveCallback callback) {
        if (executor.isShutdown()) {
            callback.onFailure(new IllegalStateException("RequestProxy is shutdown"));
            return;
        }
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
        String source = PlatformUtils.normalize(request.getSource());
        String action = request.getAction();
        String songId = request.resolveSongId();
        String quality = request.getQuality();
        boolean nocache = request.isNocache();

        if (source == null || source.isEmpty()) throw new Exception("Source missing");
        if (request.getSource() == null || !request.getSource().equals(source)) {
            request.setSource(source);
        }
        String displaySource = PlatformUtils.displayName(source);
        if (songId == null || songId.isEmpty()) throw new Exception("SongId missing");
        if (action == null || action.isEmpty()) action = "musicUrl";
        if (quality == null || quality.isEmpty()) quality = "128k";

        String cacheKey = source + ":" + songId + ":" + action + ":" + quality;
        Logger.info("Resolve " + action + " @ " + source + " - " + songId);

        if (!nocache) {
            CacheEntry cached = cacheManager.get(cacheKey);
            if (cached != null) {
                Logger.info("Cache hit: " + cacheKey + " via " + cached.getProvider());
                return buildResponse(request, action, quality, songId, cached.getData(), cached.getProvider(), displaySource);
            }
        } else {
            Logger.info("Cache skipped (nocache=true)");
        }

        if (request.getTargetScriptId() != null && !request.getTargetScriptId().isEmpty()) {
            ScriptHandler handler = scriptManager.getHandlerById(source, request.getTargetScriptId(), action);
            if (handler == null) {
                throw new Exception("No provider found for source: " + source);
            }
            return executeWithHandler(request, handler, cacheKey, action, quality, songId, displaySource);
        }

        List<ScriptHandler> handlers = scriptManager.getOrderedHandlers(source, action);
        if (handlers.isEmpty()) {
            throw new Exception("No provider found for source: " + source);
        }

        Exception lastError = null;
        for (ScriptHandler handler : handlers) {
            try {
                return executeWithHandler(request, handler, cacheKey, action, quality, songId, displaySource);
            } catch (Exception e) {
                lastError = e;
                Logger.warn("Provider failed: " + handler.getScriptId() + " - " + e.getMessage());
            }
        }

        throw new Exception(lastError == null ? "All providers failed" : lastError.getMessage());
    }

    private String buildResponse(ResolveRequest request, String action, String quality, String songId, Object data, String provider, String displayPlatform) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", 0);
        payload.put("message", "ok");
        payload.put("data", data);
        payload.put("provider", provider);
        if ("musicUrl".equals(action) && data instanceof String) {
            payload.put("url", data);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("platform", displayPlatform != null ? displayPlatform : (request == null ? null : request.getSource()));
        info.put("action", action);
        info.put("quality", quality);
        info.put("songId", songId);
        info.put("provider", provider);
        payload.put("info", info);
        return gson.toJson(payload);
    }

    private String executeWithHandler(ResolveRequest request, ScriptHandler handler, String cacheKey, String action, String quality, String songId, String displaySource) throws Exception {
        String targetQuality = resolveQuality(handler, quality);
        Map<String, Object> requestPayload = request.buildScriptRequest(targetQuality, action);
        Logger.info("Dispatch to handler: " + handler.getScriptId() + " action=" + action + " quality=" + targetQuality);
        String responseJson = scriptManager.dispatchRequest(handler.getScriptId(), gson.toJson(requestPayload), REQUEST_TIMEOUT_MS);
        Logger.info("Handler response: " + handler.getScriptId() + " bytes=" + (responseJson == null ? 0 : responseJson.length()));
        Map<String, Object> response = gson.fromJson(responseJson, Map.class);
        if (response != null && response.get("error") != null) {
            throw new Exception(String.valueOf(response.get("error")));
        }

        Object data = response;
        if (response != null && response.containsKey("url")) {
            data = response.get("url");
        }
        cacheManager.put(cacheKey, data, handler.getScriptId());
        return buildResponse(request, action, targetQuality, songId, data, handler.getScriptId(), displaySource);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private String resolveQuality(ScriptHandler handler, String requested) {
        if (handler == null) return requested;
        if (requested == null || requested.isEmpty()) return requested;
        List<String> supported = handler.getQualitys();
        if (supported == null || supported.isEmpty()) return requested;
        if (supported.contains(requested)) return requested;
        String fallback = pickMinQuality(supported);
        if (fallback == null || fallback.isEmpty()) return requested;
        Logger.warn("Quality not supported by handler, fallback to " + fallback);
        return fallback;
    }

    private String pickMinQuality(List<String> supported) {
        if (supported == null || supported.isEmpty()) return null;
        String[] order = new String[]{"128k", "320k", "flac", "flac24bit"};
        for (String q : order) {
            if (supported.contains(q)) return q;
        }
        for (String q : supported) {
            if (q != null && !q.trim().isEmpty()) return q;
        }
        return null;
    }
}








