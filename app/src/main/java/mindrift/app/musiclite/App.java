package mindrift.app.musiclite;

import android.app.Application;
import mindrift.app.musiclite.core.cache.CacheManager;
import mindrift.app.musiclite.core.proxy.RequestProxy;
import mindrift.app.musiclite.core.script.ScriptManager;
import mindrift.app.musiclite.utils.Logger;
import mindrift.app.musiclite.wearable.XiaomiWearableManager;

public class App extends Application {
    private CacheManager cacheManager;
    private ScriptManager scriptManager;
    private RequestProxy requestProxy;
    private XiaomiWearableManager wearableManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init();
        cacheManager = new CacheManager(this);
        scriptManager = new ScriptManager(this);
        requestProxy = new RequestProxy(scriptManager, cacheManager);
        wearableManager = new XiaomiWearableManager(this, requestProxy, scriptManager);
        scriptManager.addChangeListener(() -> wearableManager.notifyCapabilitiesChanged());
        wearableManager.start();
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public RequestProxy getRequestProxy() {
        return requestProxy;
    }

    public XiaomiWearableManager getWearableManager() {
        return wearableManager;
    }
}







