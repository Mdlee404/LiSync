package mindrift.app.musiclite.core.proxy;

import mindrift.app.musiclite.core.script.ScriptContext;
import mindrift.app.musiclite.core.script.SourceInfo;

public class ScriptHandler {
    private final String scriptId;
    private final ScriptContext context;
    private final SourceInfo sourceInfo;

    public ScriptHandler(String scriptId, ScriptContext context, SourceInfo sourceInfo) {
        this.scriptId = scriptId;
        this.context = context;
        this.sourceInfo = sourceInfo;
    }

    public String getScriptId() {
        return scriptId;
    }

    public ScriptContext getContext() {
        return context;
    }

    public boolean supportsAction(String action) {
        if (action == null || sourceInfo == null || sourceInfo.getActions() == null) return true;
        return sourceInfo.getActions().isEmpty() || sourceInfo.getActions().contains(action);
    }

    public boolean supportsQuality(String quality) {
        if (quality == null || sourceInfo == null || sourceInfo.getQualitys() == null) return true;
        return sourceInfo.getQualitys().isEmpty() || sourceInfo.getQualitys().contains(quality);
    }
}







