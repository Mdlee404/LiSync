package mindrift.app.lisynchronization.core.script;

import app.cash.quickjs.QuickJs;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import mindrift.app.lisynchronization.core.engine.LxNativeImpl;
import mindrift.app.lisynchronization.core.engine.LxNativeInterface;

public class ScriptContext {
    private final String scriptId;
    private final String nativeKey;
    private final ExecutorService executor;
    private final CountDownLatch initLatch;
    private QuickJs quickJs;
    private final ConcurrentHashMap<String, ArrayBlockingQueue<String>> asyncResults = new ConcurrentHashMap<>();

    // 存储脚本解析出的音源信息
    private volatile Object scriptInfo;

    public ScriptContext(String scriptId, String nativeKey) {
        this.scriptId = scriptId;
        this.nativeKey = nativeKey;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "v8-script-" + scriptId));
        this.initLatch = new CountDownLatch(1);
    }

    public String getScriptId() {
        return scriptId;
    }

    public String getNativeKey() {
        return nativeKey;
    }

    public void initialize(String wrapperScript, String scriptContent, LxNativeImpl nativeImpl) throws Exception {
        Future<?> future = executor.submit(() -> {
            try {
                quickJs = QuickJs.create();
                quickJs.set("LxNative", LxNativeBridge.class, new LxNativeBridgeImpl(nativeImpl));
                quickJs.evaluate(wrapperScript);
                quickJs.evaluate(scriptContent + "\n;void 0;");
            } finally {
                initLatch.countDown();
            }
        });
        future.get();
    }

    public void evaluateAsync(String script) {
        executor.submit(() -> {
            awaitInit();
            quickJs.evaluate(script);
        });
    }

    public <T> T evaluate(String script, Class<T> type) throws Exception {
        Future<T> future = executor.submit(() -> {
            awaitInit();
            Object result = quickJs.evaluate(script);
            if (type == String.class) {
                return type.cast(result == null ? null : String.valueOf(result));
            }
            return type.cast(result);
        });
        return future.get();
    }

    public Object getScriptInfo() {
        return scriptInfo;
    }

    public void setScriptInfo(Object scriptInfo) {
        this.scriptInfo = scriptInfo;
    }

    public void close() {
        executor.submit(() -> {
            awaitInit();
            if (quickJs != null) {
                quickJs.close();
            }
        });
        executor.shutdown();
    }

    public void completeAsyncResult(String asyncId, String payloadJson) {
        if (asyncId == null) return;
        ArrayBlockingQueue<String> queue = asyncResults.computeIfAbsent(asyncId, k -> new ArrayBlockingQueue<>(1));
        queue.offer(payloadJson == null ? "" : payloadJson);
    }

    public String awaitAsyncResult(String asyncId, long timeoutMs) throws Exception {
        if (asyncId == null) {
            throw new Exception("Async id missing");
        }
        ArrayBlockingQueue<String> queue = asyncResults.computeIfAbsent(asyncId, k -> new ArrayBlockingQueue<>(1));
        try {
            String payload = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (payload == null) {
                long seconds = Math.max(1, timeoutMs / 1000);
                throw new Exception("Request timeout (" + seconds + "s)");
            }
            return payload;
        } finally {
            asyncResults.remove(asyncId);
        }
    }
    private interface LxNativeBridge {
        String nativeCall(String key, String action, String dataJson);
        void setTimeout(double id, double timeoutMs);
        void on(String eventName, String handlerJson);
        void send(String eventName, String dataJson);
        void request(String url, String optionsJson, String callbackId);
        String requestSync(String url, String optionsJson);
        void asyncResult(String asyncId, String payloadJson);
        String utilsStr2b64(String input);
        String utilsB642buf(String input);
        String utilsStr2md5(String input);
        String utilsAesEncrypt(String data, String key, String iv, String mode);
        String utilsRsaEncrypt(String data, String key, String padding);
        String md5(String input);
        String aesEncrypt(String input, String mode, String key, String iv);
        String rsaEncrypt(String input, String key);
        String randomBytes(double size);
        String bufferFrom(String data, String encoding);
        String bufferToString(String buffer, String encoding);
        void log(String level, String messageJson);
        String zlibInflate(String input);
        String zlibDeflate(String input);
    }

    private static class LxNativeBridgeImpl implements LxNativeBridge {
        private final LxNativeInterface nativeImpl;

        private LxNativeBridgeImpl(LxNativeInterface nativeImpl) {
            this.nativeImpl = nativeImpl;
        }

        @Override
        public String nativeCall(String key, String action, String dataJson) {
            return nativeImpl.nativeCall(key, action, dataJson);
        }

        @Override
        public void setTimeout(double id, double timeoutMs) {
            nativeImpl.setTimeout(id, timeoutMs);
        }

        @Override
        public void on(String eventName, String handlerJson) {
            nativeImpl.on(eventName, handlerJson);
        }

        @Override
        public void send(String eventName, String dataJson) {
            nativeImpl.send(eventName, dataJson);
        }

        @Override
        public void request(String url, String optionsJson, String callbackId) {
            nativeImpl.request(url, optionsJson, callbackId);
        }

        @Override
        public String requestSync(String url, String optionsJson) {
            return nativeImpl.requestSync(url, optionsJson);
        }

        @Override
        public void asyncResult(String asyncId, String payloadJson) {
            nativeImpl.asyncResult(asyncId, payloadJson);
        }

        @Override
        public String utilsStr2b64(String input) {
            return nativeImpl.utilsStr2b64(input);
        }

        @Override
        public String utilsB642buf(String input) {
            return nativeImpl.utilsB642buf(input);
        }

        @Override
        public String utilsStr2md5(String input) {
            return nativeImpl.utilsStr2md5(input);
        }

        @Override
        public String utilsAesEncrypt(String data, String key, String iv, String mode) {
            return nativeImpl.utilsAesEncrypt(data, key, iv, mode);
        }

        @Override
        public String utilsRsaEncrypt(String data, String key, String padding) {
            return nativeImpl.utilsRsaEncrypt(data, key, padding);
        }

        @Override
        public String md5(String input) {
            return nativeImpl.md5(input);
        }

        @Override
        public String aesEncrypt(String input, String mode, String key, String iv) {
            return nativeImpl.aesEncrypt(input, mode, key, iv);
        }

        @Override
        public String rsaEncrypt(String input, String key) {
            return nativeImpl.rsaEncrypt(input, key);
        }

        @Override
        public String randomBytes(double size) {
            int intSize = (int) Math.max(0, Math.round(size));
            return nativeImpl.randomBytes(intSize);
        }

        @Override
        public String bufferFrom(String data, String encoding) {
            return nativeImpl.bufferFrom(data, encoding);
        }

        @Override
        public String bufferToString(String buffer, String encoding) {
            return nativeImpl.bufferToString(buffer, encoding);
        }

        @Override
        public void log(String level, String messageJson) {
            nativeImpl.log(level, messageJson);
        }

        @Override
        public String zlibInflate(String input) {
            return nativeImpl.zlibInflate(input);
        }

        @Override
        public String zlibDeflate(String input) {
            return nativeImpl.zlibDeflate(input);
        }
    }

    private void awaitInit() {
        try {
            initLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
