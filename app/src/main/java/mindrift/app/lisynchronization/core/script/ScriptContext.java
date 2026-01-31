package mindrift.app.lisynchronization.core.script;

import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mindrift.app.lisynchronization.core.engine.LxNativeImpl;
import mindrift.app.lisynchronization.utils.Logger;

public class ScriptContext {
    private final String scriptId;
    private final String nativeKey;
    private final ExecutorService executor;
    private final CountDownLatch initLatch;
    private QuickJSContext jsContext;
    private final ConcurrentHashMap<String, ArrayBlockingQueue<String>> asyncResults = new ConcurrentHashMap<>();

    // ????????????
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

    public void initialize(ScriptMeta meta, String preloadScript, String scriptContent, LxNativeImpl nativeImpl) throws Exception {
        Future<?> future = executor.submit(() -> {
            try {
                QuickJSLoader.init();
                if (jsContext != null) {
                    jsContext.destroy();
                }
                jsContext = QuickJSContext.create();
                createEnvObj(jsContext, nativeImpl);
                if (preloadScript != null && !preloadScript.isEmpty()) {
                    jsContext.evaluate(preloadScript);
                }
                callSetup(meta, scriptContent);
                jsContext.evaluate(scriptContent + "\n;void 0;");
            } finally {
                initLatch.countDown();
            }
        });
        future.get();
    }

    public void evaluateAsync(String script) {
        executor.submit(() -> {
            awaitInit();
            try {
                if (jsContext != null) {
                    jsContext.evaluate(script);
                }
            } catch (Exception e) {
                Logger.error("Script execution error: " + e.getMessage(), e);
            }
        });
    }

    public <T> T evaluate(String script, Class<T> type) throws Exception {
        Future<T> future = executor.submit(() -> {
            awaitInit();
            Object result = jsContext == null ? null : jsContext.evaluate(script);
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
            if (jsContext != null) {
                jsContext.destroy();
                jsContext = null;
            }
        });
        asyncResults.clear();
        executor.shutdown();
    }

    public void completeAsyncResult(String asyncId, String payloadJson) {
        if (asyncId == null) return;
        ArrayBlockingQueue<String> queue = asyncResults.get(asyncId);
        if (queue == null) {
            Logger.warn("Async result ignored (not pending): " + asyncId);
            return;
        }
        boolean offered = queue.offer(payloadJson == null ? "" : payloadJson);
        if (!offered) {
            Logger.warn("Async result ignored (already completed): " + asyncId);
        }
    }

    public void prepareAsyncResult(String asyncId) {
        if (asyncId == null) return;
        asyncResults.putIfAbsent(asyncId, new ArrayBlockingQueue<>(1));
    }

    public String awaitAsyncResult(String asyncId, long timeoutMs) throws Exception {
        if (asyncId == null) {
            throw new Exception("Async id missing");
        }
        ArrayBlockingQueue<String> queue = asyncResults.get(asyncId);
        if (queue == null) {
            throw new Exception("Async task not prepared");
        }
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
    private void createEnvObj(QuickJSContext context, LxNativeImpl nativeImpl) {
        context.getGlobalObject().setProperty("__lx_native_call__", args -> {
            if (args == null || args.length < 3) return null;
            return nativeImpl.nativeCall(String.valueOf(args[0]), String.valueOf(args[1]), args[2] == null ? null : String.valueOf(args[2]));
        });
        context.getGlobalObject().setProperty("__lx_native_call__utils_str2b64", args -> {
            return nativeImpl.utilsStr2b64(args == null || args.length == 0 ? null : String.valueOf(args[0]));
        });
        context.getGlobalObject().setProperty("__lx_native_call__utils_b642buf", args -> {
            return nativeImpl.utilsB642buf(args == null || args.length == 0 ? null : String.valueOf(args[0]));
        });
        context.getGlobalObject().setProperty("__lx_native_call__utils_str2md5", args -> {
            return nativeImpl.utilsStr2md5(args == null || args.length == 0 ? null : String.valueOf(args[0]));
        });
        context.getGlobalObject().setProperty("__lx_native_call__utils_aes_encrypt", args -> {
            String data = args == null || args.length < 1 ? null : String.valueOf(args[0]);
            String key = args == null || args.length < 2 ? null : String.valueOf(args[1]);
            String iv = args == null || args.length < 3 ? null : String.valueOf(args[2]);
            String mode = args == null || args.length < 4 ? null : String.valueOf(args[3]);
            return nativeImpl.utilsAesEncrypt(data, key, iv, mode);
        });
        context.getGlobalObject().setProperty("__lx_native_call__utils_rsa_encrypt", args -> {
            String data = args == null || args.length < 1 ? null : String.valueOf(args[0]);
            String key = args == null || args.length < 2 ? null : String.valueOf(args[1]);
            String padding = args == null || args.length < 3 ? null : String.valueOf(args[2]);
            return nativeImpl.utilsRsaEncrypt(data, key, padding);
        });
        context.getGlobalObject().setProperty("__lx_native_call__set_timeout", args -> {
            if (args == null || args.length < 2) return null;
            double id = args[0] instanceof Number ? ((Number) args[0]).doubleValue() : Double.parseDouble(String.valueOf(args[0]));
            double timeout = args[1] instanceof Number ? ((Number) args[1]).doubleValue() : Double.parseDouble(String.valueOf(args[1]));
            nativeImpl.setTimeout(id, timeout);
            return null;
        });
        context.getGlobalObject().setProperty("__lx_native_log__", args -> {
            String level = args == null || args.length < 1 ? "info" : String.valueOf(args[0]);
            String message = args == null || args.length < 2 ? "" : String.valueOf(args[1]);
            nativeImpl.log(level, message);
            return null;
        });
        context.evaluate("globalThis.console = {"
                + "log: function(){ return __lx_native_log__('info', JSON.stringify(Array.prototype.slice.call(arguments))); },"
                + "error: function(){ return __lx_native_log__('error', JSON.stringify(Array.prototype.slice.call(arguments))); },"
                + "warn: function(){ return __lx_native_log__('warn', JSON.stringify(Array.prototype.slice.call(arguments))); },"
                + "info: function(){ return __lx_native_log__('info', JSON.stringify(Array.prototype.slice.call(arguments))); },"
                + "debug: function(){ return __lx_native_log__('debug', JSON.stringify(Array.prototype.slice.call(arguments))); },"
                + "group: function(){}, groupEnd: function(){}"
                + "};");
    }

    private void callSetup(ScriptMeta meta, String rawScript) {
        if (jsContext == null) return;
        String id = meta == null || meta.getId() == null ? "" : meta.getId();
        String name = meta == null || meta.getName() == null ? "" : meta.getName();
        String description = meta == null || meta.getDescription() == null ? "" : meta.getDescription();
        String version = meta == null || meta.getVersion() == null ? "" : meta.getVersion();
        String author = meta == null || meta.getAuthor() == null ? "" : meta.getAuthor();
        String homepage = meta == null || meta.getHomepage() == null ? "" : meta.getHomepage();
        jsContext.getGlobalObject().getJSFunction("lx_setup").call(nativeKey, id, name, description, version, author, homepage, rawScript);
    }

    private void awaitInit() {
        try {
            initLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}






