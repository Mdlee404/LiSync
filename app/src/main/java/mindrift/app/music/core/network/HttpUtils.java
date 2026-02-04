package mindrift.app.music.core.network;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Response;

final class HttpUtils {
    private HttpUtils() {}

    static Map<String, Object> castMap(Object raw) {
        if (!(raw instanceof Map)) return null;
        return (Map<String, Object>) raw;
    }

    static Map<String, String> coerceHeaders(Object raw) {
        if (!(raw instanceof Map)) return new HashMap<>();
        Map<?, ?> source = (Map<?, ?>) raw;
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) continue;
            out.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return out;
    }

    static void ensureUserAgent(Map<String, String> headers) {
        if (headers == null) return;
        boolean hasUa = false;
        for (String key : headers.keySet()) {
            if (key != null && "user-agent".equalsIgnoreCase(key)) {
                hasUa = true;
                break;
            }
        }
        if (!hasUa) {
            headers.put("User-Agent", NetworkConfig.DEFAULT_USER_AGENT);
        }
    }

    static Integer readTimeout(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    static OkHttpClient applyTimeouts(OkHttpClient base, Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) return base;
        return base.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    static Map<String, String> readHeaders(Response response) {
        Map<String, String> headers = new HashMap<>();
        Headers all = response.headers();
        for (String name : all.names()) {
            headers.put(name, all.get(name));
        }
        return headers;
    }
}








