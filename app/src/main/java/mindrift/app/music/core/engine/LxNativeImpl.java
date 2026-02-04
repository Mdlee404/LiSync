package mindrift.app.music.core.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import mindrift.app.music.core.network.HttpClient;
import mindrift.app.music.core.script.ScriptContext;
import mindrift.app.music.utils.CryptoUtils;
import mindrift.app.music.utils.Logger;
import okhttp3.Call;
import java.net.URLDecoder;

public class LxNativeImpl implements LxNativeInterface {
    private final ScriptContext scriptContext;
    private final String scriptId;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = new HttpClient();
    private final ScriptEventListener eventListener;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Call> pendingRequests = new ConcurrentHashMap<>();
    private static final int LOG_LIMIT = 2000;

    public interface ScriptEventListener {
        void onInited(String scriptId, String dataJson);
        void onUpdateAlert(String scriptId, String dataJson);
    }

    public LxNativeImpl(ScriptContext scriptContext, String scriptId, ScriptEventListener eventListener) {
        this.scriptContext = scriptContext;
        this.scriptId = scriptId;
        this.eventListener = eventListener;
    }

    @JavascriptInterface
    public String nativeCall(String key, String action, String dataJson) {
        if (key == null || !key.equals(scriptContext.getNativeKey())) {
            return "Invalid key";
        }
        if (action == null) return "Invalid action";
        if ("response".equals(action) || "request".equals(action) || "init".equals(action)) {
            int size = dataJson == null ? 0 : dataJson.length();
            Logger.info("[NativeCall] action=" + action + " size=" + size);
            if ("request".equals(action) || "response".equals(action)) {
                Logger.info("[NativeCall] payload=" + trimLog(dataJson));
            }
        }
        switch (action) {
            case "init":
                handleInitEvent(dataJson);
                break;
            case "showUpdateAlert":
                if (eventListener != null) {
                    eventListener.onUpdateAlert(scriptId, dataJson);
                }
                break;
            case "request":
                handleScriptRequest(dataJson);
                break;
            case "cancelRequest":
                handleCancelRequest(dataJson);
                break;
            case "response":
                handleScriptResponse(dataJson);
                break;
            case "__set_timeout__":
                break;
            default:
                Logger.warn("Unknown native action: " + action);
                break;
        }
        return null;
    }

    @JavascriptInterface
    public void setTimeout(double id, double timeoutMs) {
        int timeout = (int) Math.max(0, Math.round(timeoutMs));
        String payload = gson.toJson((int) Math.round(id));
        timeoutHandler.postDelayed(() -> sendNativeEvent("__set_timeout__", payload), timeout);
    }

    @JavascriptInterface
    public void on(String eventName, String handlerJson) {
        if ("request".equals(eventName)) {
            Logger.info("Script " + scriptId + " registered 'request' handler.");
        }
    }

    @JavascriptInterface
    public void send(String eventName, String dataJson) {
        if ("inited".equals(eventName)) {
            Logger.info("Script " + scriptId + " inited: " + dataJson);
            if (eventListener != null) {
                eventListener.onInited(scriptId, dataJson);
            }
        } else if ("updateAlert".equals(eventName)) {
            Logger.info("[Alert] " + scriptId + ": " + dataJson);
            if (eventListener != null) {
                eventListener.onUpdateAlert(scriptId, dataJson);
            }
        }
    }

    @JavascriptInterface
    public void request(String url, String optionsJson, String callbackId) {
        Logger.info("HTTP request start: url=" + url + " callbackId=" + callbackId);
        Map<String, Object> options = parseOptions(optionsJson);
        Logger.info("HTTP request options: " + optionsJson);

        httpClient.request(url, options, new HttpClient.NetworkCallback() {
            @Override
            public void onSuccess(int code, String body, Map<String, String> headers) {
                Logger.info("HTTP response: code=" + code + " bytes=" + (body == null ? 0 : body.length()));
                Map<String, Object> response = new HashMap<>();
                response.put("statusCode", code);
                response.put("statusMessage", "");
                response.put("headers", headers != null ? headers : new HashMap<>());
                response.put("bytes", body != null ? body.getBytes(StandardCharsets.UTF_8).length : 0);
                response.put("body", parseBody(body));
                invokeJsCallback(callbackId, null, gson.toJson(response));
            }

            @Override
            public void onFailure(Exception e) {
                Logger.error("HTTP request failed: " + e.getMessage(), e);
                Map<String, Object> error = new HashMap<>();
                error.put("message", e.getMessage() != null ? e.getMessage() : "Request failed");
                invokeJsCallback(callbackId, gson.toJson(error), null);
            }
        });
    }

    @JavascriptInterface
    public String requestSync(String url, String optionsJson) {
        Logger.info("HTTP request sync start: url=" + url);
        Map<String, Object> options = parseOptions(optionsJson);
        Logger.info("HTTP request sync options: " + optionsJson);
        Map<String, Object> wrapper = new HashMap<>();
        try {
            HttpClient.ResponseData responseData = httpClient.requestSync(url, options);
            Logger.info("HTTP sync response: code=" + responseData.code + " bytes=" + (responseData.body == null ? 0 : responseData.body.length()));
            Map<String, Object> response = new HashMap<>();
            response.put("statusCode", responseData.code);
            response.put("statusMessage", "");
            response.put("headers", responseData.headers != null ? responseData.headers : new HashMap<>());
            response.put("bytes", responseData.body != null ? responseData.body.getBytes(StandardCharsets.UTF_8).length : 0);
            response.put("body", parseBody(responseData.body));
            wrapper.put("resp", response);
        } catch (Exception e) {
            Logger.error("HTTP sync request failed: " + e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage() != null ? e.getMessage() : "Request failed");
            wrapper.put("err", error);
        }
        return gson.toJson(wrapper);
    }

    @JavascriptInterface
    public void asyncResult(String asyncId, String payloadJson) {
        if (asyncId == null || asyncId.trim().isEmpty()) return;
        scriptContext.completeAsyncResult(asyncId, payloadJson);
    }

    @JavascriptInterface
    public String utilsStr2b64(String input) {
        if (input == null) input = "";
        return Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    @JavascriptInterface
    public String utilsB642buf(String input) {
        if (input == null) return "[]";
        try {
            byte[] bytes = Base64.decode(input, Base64.DEFAULT);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(bytes[i] & 0xFF);
            }
            sb.append(']');
            return sb.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String utilsStr2md5(String input) {
        if (input == null) return "";
        try {
            String decoded = URLDecoder.decode(input, StandardCharsets.UTF_8.name());
            return CryptoUtils.md5(decoded);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public String utilsAesEncrypt(String data, String key, String iv, String mode) {
        try {
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance(mode);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            if (iv != null && !iv.isEmpty()) {
                byte[] ivBytes = Base64.decode(iv, Base64.DEFAULT);
                byte[] finalIvs = new byte[16];
                int len = Math.min(ivBytes.length, 16);
                System.arraycopy(ivBytes, 0, finalIvs, 0, len);
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(finalIvs));
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            }
            byte[] encrypted = cipher.doFinal(dataBytes);
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public String utilsRsaEncrypt(String data, String key, String padding) {
        try {
            byte[] keyBytes = Base64.decode(key.trim().getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            java.security.PublicKey publicKey = keyFactory.generatePublic(spec);
            Cipher cipher = Cipher.getInstance(padding);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public String md5(String str) {
        return CryptoUtils.md5(str == null ? "" : str);
    }

    @JavascriptInterface
    public String aesEncrypt(String dataBase64, String mode, String keyBase64, String ivBase64) {
        byte[] data = decodeBase64(dataBase64);
        byte[] key = decodeBase64(keyBase64);
        byte[] iv = ivBase64 == null || ivBase64.isEmpty() ? null : decodeBase64(ivBase64);
        byte[] encrypted = CryptoUtils.aesEncrypt(data, mode, key, iv);
        return encodeBase64(encrypted);
    }

    @JavascriptInterface
    public String rsaEncrypt(String dataBase64, String publicKey) {
        byte[] buffer = decodeBase64(dataBase64);
        buffer = leftPad(buffer, 128);
        byte[] encrypted = CryptoUtils.rsaEncrypt(buffer, publicKey);
        return encodeBase64(encrypted);
    }

    @JavascriptInterface
    public String randomBytes(double size) {
        int intSize = (int) Math.max(0, Math.round(size));
        return encodeBase64(CryptoUtils.randomBytes(intSize));
    }

    @JavascriptInterface
    public String bufferFrom(String data, String encoding) {
        byte[] bytes = decodeBytes(data, encoding);
        return encodeBase64(bytes);
    }

    @JavascriptInterface
    public String bufferToString(String base64, String format) {
        byte[] bytes = decodeBase64(base64);
        if (format == null || format.isEmpty()) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        String normalized = format.toLowerCase();
        if ("hex".equals(normalized)) {
            return toHex(bytes);
        }
        if ("base64".equals(normalized)) {
            return encodeBase64(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @JavascriptInterface
    public void log(String level, String messageJson) {
        String message = messageJson;
        try {
            List<Object> items = gson.fromJson(messageJson, List.class);
            if (items != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) builder.append(' ');
                    builder.append(String.valueOf(items.get(i)));
                }
                message = builder.toString();
            }
        } catch (Exception ignored) {
        }

        if ("error".equalsIgnoreCase(level)) {
            Logger.error("[JS] " + message);
        } else if ("warn".equalsIgnoreCase(level)) {
            Logger.warn("[JS] " + message);
        } else if ("debug".equalsIgnoreCase(level)) {
            Logger.debug("[JS] " + message);
        } else {
            Logger.info("[JS] " + message);
        }
    }

    @JavascriptInterface
    public String zlibInflate(String base64) {
        byte[] input = decodeBase64(base64);
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(input);
            byte[] buffer = new byte[1024];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, count);
            }
            inflater.end();
            return encodeBase64(out.toByteArray());
        } catch (Exception e) {
            Logger.error("zlib inflate error", e);
            return "";
        }
    }

    @JavascriptInterface
    public String zlibDeflate(String base64) {
        byte[] input = decodeBase64(base64);
        try {
            Deflater deflater = new Deflater();
            deflater.setInput(input);
            deflater.finish();
            byte[] buffer = new byte[1024];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            deflater.end();
            return encodeBase64(out.toByteArray());
        } catch (Exception e) {
            Logger.error("zlib deflate error", e);
            return "";
        }
    }

    private Map<String, Object> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = gson.fromJson(optionsJson, Map.class);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            Logger.warn("Invalid request options JSON, using defaults.");
            return new HashMap<>();
        }
    }

    private Object parseBody(String body) {
        if (body == null) return "";
        try {
            JsonElement element = JsonParser.parseString(body);
            return gson.fromJson(element, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }

    private void invokeJsCallback(String callbackId, String errorJson, String responseJson) {
        StringBuilder js = new StringBuilder();
        js.append("typeof __lx_invokeCallback === 'function' && __lx_invokeCallback(")
                .append(toJsStringLiteral(callbackId))
                .append(", ")
                .append(toJsNullableStringLiteral(errorJson))
                .append(", ")
                .append(toJsNullableStringLiteral(responseJson))
                .append(");");
        scriptContext.evaluateAsync(js.toString());
    }

    private void sendNativeEvent(String action, String payloadJson) {
        StringBuilder js = new StringBuilder();
        js.append("__lx_native__(")
                .append(toJsStringLiteral(scriptContext.getNativeKey()))
                .append(", ")
                .append(toJsStringLiteral(action))
                .append(", ");
        if (payloadJson == null) {
            js.append("null");
        } else {
            js.append(toJsStringLiteral(payloadJson));
        }
        js.append(");");
        scriptContext.evaluateAsync(js.toString());
    }

    private void handleInitEvent(String dataJson) {
        if (eventListener == null) return;
        try {
            Map<String, Object> payload = gson.fromJson(dataJson, Map.class);
            if (payload == null) return;
            Object statusObj = payload.get("status");
            boolean status = statusObj instanceof Boolean && (Boolean) statusObj;
            Object info = payload.get("info");
            if (status && info != null) {
                eventListener.onInited(scriptId, gson.toJson(info));
            } else if (!status) {
                Logger.warn("Script init failed: " + payload.get("errorMessage"));
            }
        } catch (Exception e) {
            Logger.warn("Script init parse error: " + e.getMessage());
        }
    }

    private void handleScriptResponse(String dataJson) {
        try {
            Map<String, Object> payload = gson.fromJson(dataJson, Map.class);
            if (payload == null) return;
            Object requestKeyObj = payload.get("requestKey");
            if (requestKeyObj == null) return;
            String requestKey = String.valueOf(requestKeyObj);
            Logger.info("[NativeCall] response requestKey=" + requestKey);
            scriptContext.completeAsyncResult(requestKey, dataJson);
        } catch (Exception e) {
            Logger.warn("Script response parse error: " + e.getMessage());
        }
    }

    private void handleCancelRequest(String dataJson) {
        try {
            Object keyObj = gson.fromJson(dataJson, Object.class);
            if (keyObj == null) return;
            String requestKey = String.valueOf(keyObj);
            Call call = pendingRequests.remove(requestKey);
            if (call != null) {
                call.cancel();
            }
        } catch (Exception e) {
            Logger.warn("Cancel request parse error: " + e.getMessage());
        }
    }

    private void handleScriptRequest(String dataJson) {
        try {
            Map<String, Object> payload = gson.fromJson(dataJson, Map.class);
            if (payload == null) return;
            String requestKey = String.valueOf(payload.get("requestKey"));
            String url = String.valueOf(payload.get("url"));
            Map<String, Object> options = new HashMap<>();
            Object optionsObj = payload.get("options");
            if (optionsObj instanceof Map) {
                options.putAll((Map<String, Object>) optionsObj);
            }
            maybeInjectSourceForCompat(url, options);
            Logger.info("Script request start: key=" + requestKey + " url=" + url + " options=" + trimLog(gson.toJson(options)));
            Call call = httpClient.requestWithCall(url, options, new HttpClient.NetworkCallback() {
                @Override
                public void onSuccess(int code, String body, Map<String, String> headers) {
                    pendingRequests.remove(requestKey);
                    Logger.info("Script request success: key=" + requestKey + " code=" + code + " bytes=" + (body == null ? 0 : body.length()));
                    Map<String, Object> response = new HashMap<>();
                    response.put("statusCode", code);
                    response.put("statusMessage", "");
                    response.put("headers", headers != null ? headers : new HashMap<>());
                    response.put("body", parseBody(body));
                    Map<String, Object> wrapper = new HashMap<>();
                    wrapper.put("requestKey", requestKey);
                    wrapper.put("error", null);
                    wrapper.put("response", response);
                    sendNativeEvent("response", gson.toJson(wrapper));
                }

                @Override
                public void onFailure(Exception e) {
                    pendingRequests.remove(requestKey);
                    Logger.warn("Script request failed: key=" + requestKey + " message=" + e.getMessage());
                    Map<String, Object> wrapper = new HashMap<>();
                    wrapper.put("requestKey", requestKey);
                    wrapper.put("error", e.getMessage() != null ? e.getMessage() : "Request failed");
                    wrapper.put("response", null);
                    sendNativeEvent("response", gson.toJson(wrapper));
                }
            });
            if (call != null) {
                pendingRequests.put(requestKey, call);
            }
        } catch (Exception e) {
            Logger.warn("Script request parse error: " + e.getMessage());
        }
    }

    private void maybeInjectSourceForCompat(String url, Map<String, Object> options) {
        if (url == null || options == null) return;
        String lowerUrl = url.toLowerCase();
        if (!lowerUrl.contains("api.music.lerd.dpdns.org")) return;
        Object bodyObj = options.get("body");
        if (!(bodyObj instanceof String)) return;
        String body = (String) bodyObj;
        if (body.trim().isEmpty()) return;
        try {
            Map<String, Object> json = gson.fromJson(body, Map.class);
            if (json == null) return;
            String source = scriptContext.getLastRequestSource();
            String quality = scriptContext.getLastRequestQuality();
            String songId = scriptContext.getLastRequestSongId();
            boolean patched = false;
            if (source != null && !source.trim().isEmpty()) {
                if (!json.containsKey("source")) {
                    json.put("source", source);
                    patched = true;
                }
                if (!json.containsKey("platform")) {
                    json.put("platform", source);
                    patched = true;
                }
            }
            if (quality != null && !quality.trim().isEmpty()) {
                if (!json.containsKey("quality")) {
                    json.put("quality", quality);
                    patched = true;
                }
                if (!json.containsKey("type")) {
                    json.put("type", quality);
                    patched = true;
                }
            }
            if (songId != null && !songId.trim().isEmpty()) {
                if (!json.containsKey("songid")) {
                    json.put("songid", songId);
                    patched = true;
                }
                if (!json.containsKey("id")) {
                    json.put("id", songId);
                    patched = true;
                }
            }
            if (!patched) return;
            String patchedJson = gson.toJson(json);
            options.put("body", patchedJson);
            Logger.info("Compat patch: injected fields into request body");
        } catch (Exception ignored) {
        }
    }

    private String toJsNullableStringLiteral(String value) {
        if (value == null) {
            return "null";
        }
        return toJsStringLiteral(value);
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

    private byte[] decodeBytes(String data, String encoding) {
        if (data == null) {
            return new byte[0];
        }
        String normalized = encoding == null ? "" : encoding.toLowerCase();
        if ("base64".equals(normalized)) {
            return decodeBase64(data);
        }
        if ("hex".equals(normalized)) {
            return fromHex(data);
        }
        return data.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeBase64(String base64) {
        if (base64 == null) return new byte[0];
        try {
            return Base64.decode(base64, Base64.DEFAULT);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private String encodeBase64(byte[] bytes) {
        return Base64.encodeToString(bytes == null ? new byte[0] : bytes, Base64.NO_WRAP);
    }

    private byte[] leftPad(byte[] input, int length) {
        if (input == null) return new byte[length];
        if (input.length >= length) return input;
        byte[] padded = new byte[length];
        int start = length - input.length;
        System.arraycopy(input, 0, padded, start, input.length);
        return padded;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] fromHex(String hex) {
        if (hex == null) return new byte[0];
        int len = hex.length();
        if (len % 2 != 0) return new byte[0];
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}








