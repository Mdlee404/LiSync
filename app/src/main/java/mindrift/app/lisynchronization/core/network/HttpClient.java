package mindrift.app.lisynchronization.core.network;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mindrift.app.lisynchronization.utils.Logger;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClient {
    private final OkHttpClient client;
    private static final int LOG_BODY_LIMIT = 2000;

    public HttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Logger.debug("[HTTP] " + message));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        client = new OkHttpClient.Builder()
                .connectTimeout(NetworkConfig.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(NetworkConfig.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(NetworkConfig.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .addInterceptor(logging)
                .build();
    }

    public interface NetworkCallback {
        void onSuccess(int code, String body, Map<String, String> headers);
        void onFailure(Exception e);
    }

    public static class ResponseData {
        public final int code;
        public final String body;
        public final Map<String, String> headers;

        public ResponseData(int code, String body, Map<String, String> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }
    }

    public void request(String url, Map<String, Object> options, NetworkCallback callback) {
        requestWithCall(url, options, callback);
    }

    public Call requestWithCall(String url, Map<String, Object> options, NetworkCallback callback) {
        try {
            String method = String.valueOf(options.getOrDefault("method", "GET"));
            Map<String, String> headers = HttpUtils.coerceHeaders(options.get("headers"));
            String body = options.get("body") instanceof String ? (String) options.get("body") : null;
            Map<String, Object> form = HttpUtils.castMap(options.get("form"));
            Map<String, Object> formData = HttpUtils.castMap(options.get("formData"));
            Integer timeoutMs = HttpUtils.readTimeout(options.get("timeout"));

            logRequest(method, url, headers, body, form, formData);

            Request.Builder requestBuilder = new Request.Builder().url(url);

            if (method.equalsIgnoreCase("GET")) {
                requestBuilder.get();
            } else {
                RequestBody requestBody = buildRequestBody(body, form, formData);
                if (method.equalsIgnoreCase("POST")) requestBuilder.post(requestBody);
                else if (method.equalsIgnoreCase("PUT")) requestBuilder.put(requestBody);
                else if (method.equalsIgnoreCase("DELETE")) requestBuilder.delete(requestBody);
                else requestBuilder.method(method.toUpperCase(), requestBody);
            }

            HttpUtils.ensureUserAgent(headers);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            OkHttpClient requestClient = HttpUtils.applyTimeouts(client, timeoutMs);
            Call call = requestClient.newCall(requestBuilder.build());
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Logger.error("HTTP <- failed " + method + " " + url + ": " + e.getMessage(), e);
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        String responseBody = r.body() != null ? r.body().string() : "";
                        Map<String, String> responseHeaders = HttpUtils.readHeaders(r);
                        logResponse(method, url, r.code(), responseHeaders, responseBody);
                        callback.onSuccess(r.code(), responseBody, responseHeaders);
                    }
                }
            });
            return call;
        } catch (Exception e) {
            callback.onFailure(e);
            return null;
        }
    }

    public ResponseData requestSync(String url, Map<String, Object> options) throws Exception {
        String method = String.valueOf(options.getOrDefault("method", "GET"));
        Map<String, String> headers = HttpUtils.coerceHeaders(options.get("headers"));
        String body = options.get("body") instanceof String ? (String) options.get("body") : null;
        Map<String, Object> form = HttpUtils.castMap(options.get("form"));
        Map<String, Object> formData = HttpUtils.castMap(options.get("formData"));
        Integer timeoutMs = HttpUtils.readTimeout(options.get("timeout"));

        logRequest(method, url, headers, body, form, formData);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (method.equalsIgnoreCase("GET")) {
            requestBuilder.get();
        } else {
            RequestBody requestBody = buildRequestBody(body, form, formData);
            if (method.equalsIgnoreCase("POST")) requestBuilder.post(requestBody);
            else if (method.equalsIgnoreCase("PUT")) requestBuilder.put(requestBody);
            else if (method.equalsIgnoreCase("DELETE")) requestBuilder.delete(requestBody);
            else requestBuilder.method(method.toUpperCase(), requestBody);
        }

        HttpUtils.ensureUserAgent(headers);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        OkHttpClient requestClient = HttpUtils.applyTimeouts(client, timeoutMs);
        try (Response response = requestClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            Map<String, String> responseHeaders = HttpUtils.readHeaders(response);
            logResponse(method, url, response.code(), responseHeaders, responseBody);
            return new ResponseData(response.code(), responseBody, responseHeaders);
        }
    }

    private RequestBody buildRequestBody(String body, Map<String, Object> form, Map<String, Object> formData) {
        if (body != null) {
            return RequestBody.create(body, MediaType.parse("application/json"));
        } else if (form != null) {
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, Object> entry : form.entrySet()) {
                builder.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return builder.build();
        } else if (formData != null) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                builder.addFormDataPart(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return builder.build();
        }
        return RequestBody.create("", null);
    }

    private void logRequest(String method, String url, Map<String, String> headers, String body, Map<String, Object> form, Map<String, Object> formData) {
        Logger.info("HTTP -> " + method + " " + url);
        Logger.info("HTTP -> headers: " + (headers == null ? "{}" : headers));
        Logger.info("HTTP -> payload: " + summarizePayload(body, form, formData));
    }

    private void logResponse(String method, String url, int code, Map<String, String> headers, String body) {
        int length = body == null ? 0 : body.length();
        Logger.info("HTTP <- " + code + " " + method + " " + url + " bytes=" + length);
        Logger.info("HTTP <- headers: " + (headers == null ? "{}" : headers));
        Logger.info("HTTP <- body(" + length + "): " + truncate(body));
    }

    private String summarizePayload(String body, Map<String, Object> form, Map<String, Object> formData) {
        if (body != null) {
            return "body(" + body.length() + "): " + truncate(body);
        }
        if (form != null) {
            return "form: " + truncate(String.valueOf(form));
        }
        if (formData != null) {
            return "formData: " + truncate(String.valueOf(formData));
        }
        return "body: <empty>";
    }

    private String truncate(String value) {
        if (value == null) return "null";
        if (value.length() <= LOG_BODY_LIMIT) return value;
        return value.substring(0, LOG_BODY_LIMIT) + "...(+" + (value.length() - LOG_BODY_LIMIT) + " chars)";
    }
}






