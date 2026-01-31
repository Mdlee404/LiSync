package mindrift.app.lisynchronization.core.script;

import java.util.HashMap;
import java.util.Map;

public class ScriptInfo {
    private Map<String, SourceInfo> sources = new HashMap<>();

    public Map<String, SourceInfo> getSources() {
        return sources;
    }

    public void setSources(Map<String, SourceInfo> sources) {
        this.sources = sources;
    }
}






