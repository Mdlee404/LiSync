package mindrift.app.music.core.script;

import android.content.Context;
import android.content.res.AssetManager;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import mindrift.app.music.core.engine.LxNativeImpl;
import mindrift.app.music.core.network.HttpClient;
import mindrift.app.music.core.proxy.ScriptHandler;
import mindrift.app.music.utils.Logger;

public class ScriptManager implements LxNativeImpl.ScriptEventListener {
    private final File scriptsDir;
    private final String preloadScript;
    private final Gson gson = new Gson();
    private final Map<String, ScriptContext> scripts = new ConcurrentHashMap<>();
    private final Map<String, ScriptInfo> scriptInfos = new ConcurrentHashMap<>();
    private final Map<String, List<ScriptHandler>> sourceMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> sourceIndex = new ConcurrentHashMap<>();
    private final HttpClient httpClient = new HttpClient();
    private final List<ScriptChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private static final int LOG_LIMIT = 2000;

    public interface ImportCallback {
        void onSuccess(File file);
        void onFailure(Exception e);
    }

    public interface ScriptChangeListener {
        void onScriptsChanged();
    }

    public ScriptManager(Context context) {
        this(new File(context.getFilesDir(), "scripts"), loadPreloadScript(context));
    }

    public ScriptManager(File scriptsDir, String preloadScript) {
        this.scriptsDir = scriptsDir;
        this.preloadScript = preloadScript == null ? "" : preloadScript;
        if (!scriptsDir.exists() && !scriptsDir.mkdirs()) {
            Logger.warn("Failed to create scripts directory: " + scriptsDir.getAbsolutePath());
        }
        loadScripts();
    }

    public void loadScripts() {
        scripts.clear();
        scriptInfos.clear();
        sourceMap.clear();
        sourceIndex.clear();
        List<File> files = listScriptFilesInternal();
        for (File file : files) {
            loadScript(file);
        }
        notifyScriptsChanged();
    }

    public List<String> getLoadedScriptIds() {
        return new ArrayList<>(scripts.keySet());
    }

    public ScriptInfo getScriptInfo(String scriptId) {
        if (scriptId == null) return null;
        return scriptInfos.get(scriptId);
    }

    public void addChangeListener(ScriptChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(ScriptChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    public Map<String, Object> getCapabilitiesSummary() {
        Map<String, Set<String>> qualities = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptInfo> entry : scriptInfos.entrySet()) {
            ScriptInfo info = entry.getValue();
            if (info == null || info.getSources() == null) continue;
            for (Map.Entry<String, SourceInfo> sourceEntry : info.getSources().entrySet()) {
                String source = sourceEntry.getKey();
                SourceInfo sourceInfo = sourceEntry.getValue();
                if (source == null || sourceInfo == null) continue;
                if (sourceInfo.getType() != null && !"music".equalsIgnoreCase(sourceInfo.getType())) continue;
                List<String> actions = sourceInfo.getActions();
                if (actions != null && !actions.isEmpty() && !actions.contains("musicUrl")) continue;
                Set<String> list = qualities.computeIfAbsent(source, k -> new LinkedHashSet<>());
                List<String> qs = sourceInfo.getQualitys();
                if (qs != null && !qs.isEmpty()) {
                    list.addAll(qs);
                }
            }
        }
        List<String> orderedSources = new ArrayList<>();
        String[] preferred = new String[]{"tx", "wy", "kg", "kw", "mg", "local"};
        for (String key : preferred) {
            if (qualities.containsKey(key)) orderedSources.add(key);
        }
        for (String key : qualities.keySet()) {
            if (!orderedSources.contains(key)) orderedSources.add(key);
        }
        Map<String, List<String>> qualityMap = new LinkedHashMap<>();
        for (String source : orderedSources) {
            Set<String> list = qualities.get(source);
            qualityMap.put(source, list == null ? new ArrayList<>() : new ArrayList<>(list));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("platforms", orderedSources);
        summary.put("qualities", qualityMap);
        summary.put("actions", Collections.singletonList("musicUrl"));
        return summary;
    }

    public List<File> listScriptFiles() {
        return listScriptFilesInternal();
    }

    public String readScriptContent(String scriptId) {
        if (scriptId == null || scriptId.trim().isEmpty()) return null;
        File file = new File(scriptsDir, scriptId);
        if (!file.exists()) return null;
        return readFile(file);
    }

    public boolean updateScriptContent(String scriptId, String content) {
        if (scriptId == null || scriptId.trim().isEmpty()) return false;
        File file = new File(scriptsDir, scriptId);
        if (!file.exists()) return false;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            byte[] data = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            output.write(data);
            return true;
        } catch (Exception e) {
            Logger.error("Failed to write script: " + scriptId, e);
            return false;
        }
    }

    public String renameScript(String scriptId, String newName) {
        if (scriptId == null || scriptId.trim().isEmpty()) return null;
        if (newName == null || newName.trim().isEmpty()) return null;
        String safeName = sanitizeFileName(newName);
        if (!safeName.toLowerCase().endsWith(".js")) {
            safeName = safeName + ".js";
        }
        if (safeName.equals(scriptId)) return scriptId;
        File source = new File(scriptsDir, scriptId);
        File target = new File(scriptsDir, safeName);
        if (!source.exists() || target.exists()) {
            return null;
        }
        if (source.renameTo(target)) {
            return safeName;
        }
        return null;
    }

    public boolean deleteScript(String scriptId) {
        ScriptContext context = scripts.remove(scriptId);
        if (context != null) {
            context.close();
        }
        scriptInfos.remove(scriptId);
        File target = new File(scriptsDir, scriptId);
        return target.exists() && target.delete();
    }

    public File getScriptsDir() {
        return scriptsDir;
    }

    public File importFromStream(String fileName, InputStream inputStream) throws IOException {
        String safeName = sanitizeFileName(fileName);
        if (!safeName.toLowerCase().endsWith(".js")) {
            safeName = safeName + ".js";
        }
        File target = new File(scriptsDir, safeName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return target;
    }

    public void importFromUrl(String url, ImportCallback callback) {
        Map<String, Object> options = new HashMap<>();
        options.put("method", "GET");
        httpClient.request(url, options, new HttpClient.NetworkCallback() {
            @Override
            public void onSuccess(int code, String body, Map<String, String> headers) {
                if (code < 200 || code >= 300) {
                    callback.onFailure(new IOException("HTTP " + code));
                    return;
                }
                String fileName = deriveFileName(url, headers);
                try (InputStream input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))) {
                    File file = importFromStream(fileName, input);
                    callback.onSuccess(file);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public ScriptHandler getHandlerById(String source, String scriptId, String action) {
        if (source == null || scriptId == null) return null;
        List<ScriptHandler> handlers = sourceMap.get(source);
        if (handlers == null) return null;
        for (ScriptHandler handler : handlers) {
            if (scriptId.equals(handler.getScriptId()) && handler.supportsAction(action)) {
                return handler;
            }
        }
        return null;
    }

    public ScriptHandler getNextHandler(String source, String action) {
        List<ScriptHandler> handlers = sourceMap.get(source);
        if (handlers == null || handlers.isEmpty()) return null;
        int count = handlers.size();
        int index = sourceIndex.getOrDefault(source, 0);
        sourceIndex.put(source, (index + 1) % count);
        for (int i = 0; i < count; i++) {
            ScriptHandler handler = handlers.get((index + i) % count);
            if (handler.supportsAction(action)) {
                return handler;
            }
        }
        return null;
    }

    public List<ScriptHandler> getOrderedHandlers(String source, String action) {
        List<ScriptHandler> handlers = sourceMap.get(source);
        if (handlers == null || handlers.isEmpty()) return Collections.emptyList();
        int count = handlers.size();
        int index = sourceIndex.getOrDefault(source, 0);
        sourceIndex.put(source, (index + 1) % count);
        List<ScriptHandler> ordered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ScriptHandler handler = handlers.get((index + i) % count);
            if (handler.supportsAction(action)) {
                ordered.add(handler);
            }
        }
        return ordered;
    }

    public String dispatchRequest(String scriptId, String requestJson) {
        return dispatchRequest(scriptId, requestJson, 20000);
    }

    public String dispatchRequest(String scriptId, String requestJson, long timeoutMs) {
        ScriptContext context = scripts.get(scriptId);
        if (context == null) {
            return errorJson("Script not found: " + scriptId);
        }
        try {
            Map<String, Object> requestMeta = gson.fromJson(requestJson, Map.class);
            if (requestMeta != null) {
                Object sourceObj = requestMeta.get("source");
                Object actionObj = requestMeta.get("action");
                Map<String, Object> infoObj = requestMeta.get("info") instanceof Map ? (Map<String, Object>) requestMeta.get("info") : null;
                Object qualityObj = infoObj != null ? infoObj.get("type") : null;
                String songId = null;
                if (infoObj != null && infoObj.get("musicInfo") instanceof Map) {
                    Map<String, Object> musicInfo = (Map<String, Object>) infoObj.get("musicInfo");
                    Object mid = musicInfo.get("songmid");
                    Object hash = musicInfo.get("hash");
                    if (mid != null && !String.valueOf(mid).isEmpty()) songId = String.valueOf(mid);
                    else if (hash != null && !String.valueOf(hash).isEmpty()) songId = String.valueOf(hash);
                }
                context.setLastRequestMeta(
                        sourceObj == null ? null : String.valueOf(sourceObj),
                        actionObj == null ? null : String.valueOf(actionObj),
                        qualityObj == null ? null : String.valueOf(qualityObj)
                );
                if (songId != null) {
                    context.setLastRequestSongId(songId);
                }
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> request = gson.fromJson(requestJson, Map.class);
        if (request == null) request = new HashMap<>();
        String requestKey = "request__" + System.currentTimeMillis() + "_" + Math.abs(new java.util.Random().nextInt());
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestKey", requestKey);
        payload.put("data", request);

        String js = "__lx_native__("
                + toJsStringLiteral(context.getNativeKey())
                + ", "
                + toJsStringLiteral("request")
                + ", "
                + toJsStringLiteral(gson.toJson(payload))
                + ");";
        Logger.info("Dispatch request to script: " + scriptId + " key=" + requestKey);
        Logger.info("Dispatch payload: " + trimLog(requestJson));
        context.prepareAsyncResult(requestKey);
        context.evaluateAsync(js);

        try {
            String responseJson = context.awaitAsyncResult(requestKey, timeoutMs);
            Logger.info("Dispatch result: " + (responseJson == null ? "null" : responseJson));
            if (responseJson == null || responseJson.isEmpty()) {
                return errorJson("Empty response");
            }
            Map<String, Object> response = gson.fromJson(responseJson, Map.class);
            if (response == null) {
                return responseJson;
            }
            Object statusObj = response.get("status");
            boolean status = statusObj instanceof Boolean && (Boolean) statusObj;
            if (status) {
                Object result = response.get("result");
                if (result instanceof Map) {
                    Object data = ((Map<?, ?>) result).get("data");
                    return gson.toJson(data != null ? data : result);
                }
                return gson.toJson(result);
            } else {
                String message = response.get("errorMessage") == null ? "Request failed" : String.valueOf(response.get("errorMessage"));
                return errorJson(message);
            }
        } catch (Exception e) {
            Logger.error("Dispatch error: " + e.getMessage(), e);
            return errorJson(e.getMessage() == null ? "Unknown error" : e.getMessage());
        }
    }

    @Override
    public void onInited(String scriptId, String dataJson) {
        ScriptContext context = scripts.get(scriptId);
        if (context == null) return;
        try {
            Logger.info("Script init data: " + trimLog(dataJson));
            ScriptInfo info = gson.fromJson(dataJson, ScriptInfo.class);
            context.setScriptInfo(info);
            scriptInfos.put(scriptId, info);
            registerSources(scriptId, context, info);
            Logger.info("Script sources registered: " + scriptId + " -> " + (info == null ? "{}" : String.valueOf(info.getSources())));
        } catch (Exception e) {
            Logger.warn("Failed to parse script init data for " + scriptId + ": " + e.getMessage());
        }
    }

    @Override
    public void onUpdateAlert(String scriptId, String dataJson) {
        Logger.info("[Alert] " + scriptId + ": " + dataJson);
    }

    private void registerSources(String scriptId, ScriptContext context, ScriptInfo info) {
        if (info == null || info.getSources() == null) return;
        for (Map.Entry<String, SourceInfo> entry : info.getSources().entrySet()) {
            String source = entry.getKey();
            SourceInfo sourceInfo = entry.getValue();
            if (sourceInfo == null || !"music".equalsIgnoreCase(sourceInfo.getType())) continue;
            List<ScriptHandler> handlers = sourceMap.computeIfAbsent(source, k -> new ArrayList<>());
            handlers.add(new ScriptHandler(scriptId, context, sourceInfo));
        }
    }

    private void loadScript(File file) {
        String scriptId = file.getName();
        String scriptContent = readFile(file);
        if (scriptContent == null) return;

        String nativeKey = "key_" + System.currentTimeMillis() + "_" + Math.abs(new java.util.Random().nextInt());
        ScriptContext context = new ScriptContext(scriptId, nativeKey);
        LxNativeImpl nativeImpl = new LxNativeImpl(context, scriptId, this);

        try {
            ScriptMeta meta = parseMeta(scriptContent, file.getName());
            scripts.put(scriptId, context);
            context.initialize(meta, preloadScript, scriptContent, nativeImpl);
            Logger.info("Loaded script: " + scriptId);
        } catch (Exception e) {
            Logger.error("Failed to load script: " + scriptId, e);
            scripts.remove(scriptId);
            context.close();
        }
    }

    private List<File> listScriptFilesInternal() {
        if (!scriptsDir.exists()) return Collections.emptyList();
        File[] files = scriptsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".js"));
        if (files == null) return Collections.emptyList();
        List<File> result = new ArrayList<>();
        Collections.addAll(result, files);
        return result;
    }

    private String readFile(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = inputStream.read(data);
            if (read <= 0) return "";
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Logger.error("Failed to read script: " + file.getName(), e);
            return null;
        }
    }

    private static String loadPreloadScript(Context context) {
        try {
            AssetManager assets = context.getAssets();
            try (InputStream input = assets.open("script/user-api-preload.js")) {
                byte[] buffer = new byte[input.available()];
                int read = input.read(buffer);
                if (read <= 0) return "";
                return new String(buffer, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Logger.error("Failed to load preload script", e);
            return "";
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "script_" + System.currentTimeMillis();
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String deriveFileName(String url, Map<String, String> headers) {
        String name = null;
        if (headers != null) {
            String disposition = headers.get("Content-Disposition");
            if (disposition != null) {
                String[] parts = disposition.split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("filename=")) {
                        name = trimmed.substring("filename=".length()).replace("\"", "");
                        break;
                    }
                }
            }
        }
        if (name == null) {
            int idx = url.lastIndexOf('/');
            name = idx >= 0 ? url.substring(idx + 1) : "script_" + System.currentTimeMillis();
        }
        return sanitizeFileName(name);
    }

    private void notifyScriptsChanged() {
        for (ScriptChangeListener listener : changeListeners) {
            try {
                listener.onScriptsChanged();
            } catch (Exception e) {
                Logger.warn("Script change listener failed: " + e.getMessage());
            }
        }
    }


    private String errorJson(String message) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("message", message);
        error.put("error", detail);
        return gson.toJson(error);
    }

    private String toJsStringLiteral(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        return "\"" + escaped + "\"";
    }

    private String trimLog(String value) {
        if (value == null) return "null";
        if (value.length() <= LOG_LIMIT) return value;
        return value.substring(0, LOG_LIMIT) + "...(+" + (value.length() - LOG_LIMIT) + " chars)";
    }

    private ScriptMeta parseMeta(String scriptContent, String fallbackName) {
        ScriptMeta meta = new ScriptMeta();
        meta.setId(fallbackName);
        meta.setName(fallbackName);
        String[] lines = scriptContent.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                break;
            }
            String content = trimmed.replace("*", "").replace("//", "").trim();
            if (content.startsWith("@name")) meta.setName(content.substring(5).trim());
            if (content.startsWith("@description")) meta.setDescription(content.substring(12).trim());
            if (content.startsWith("@version")) meta.setVersion(content.substring(8).trim());
            if (content.startsWith("@author")) meta.setAuthor(content.substring(7).trim());
            if (content.startsWith("@homepage")) meta.setHomepage(content.substring(9).trim());
        }
        if (meta.getName() == null || meta.getName().isEmpty()) meta.setName(fallbackName);
        if (meta.getId() == null || meta.getId().isEmpty()) meta.setId(fallbackName);
        if (meta.getDescription() == null) meta.setDescription("");
        if (meta.getVersion() == null) meta.setVersion("");
        if (meta.getAuthor() == null) meta.setAuthor("");
        if (meta.getHomepage() == null) meta.setHomepage("");
        return meta;
    }
}








