package mindrift.app.lisynchronization.core.engine;

public interface LxNativeInterface {
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






