package mindrift.app.lisynchronization.core.script;

import java.util.ArrayList;
import java.util.List;

public class SourceInfo {
    private String type;
    private List<String> actions = new ArrayList<>();
    private List<String> qualitys = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public List<String> getQualitys() {
        return qualitys;
    }

    public void setQualitys(List<String> qualitys) {
        this.qualitys = qualitys;
    }
}






