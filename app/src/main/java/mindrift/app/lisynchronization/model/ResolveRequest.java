package mindrift.app.lisynchronization.model;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

public class ResolveRequest {
    @SerializedName(value = "source", alternate = {"platform"})
    private String source;
    private String action;
    private String quality;
    private boolean nocache;
    @SerializedName("targetScriptId")
    private String targetScriptId;
    private MusicInfo musicInfo;
    @SerializedName(value = "songid", alternate = {"id", "songId", "songID"})
    private String songId;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public boolean isNocache() {
        return nocache;
    }

    public void setNocache(boolean nocache) {
        this.nocache = nocache;
    }

    public String getTargetScriptId() {
        return targetScriptId;
    }

    public void setTargetScriptId(String targetScriptId) {
        this.targetScriptId = targetScriptId;
    }

    public MusicInfo getMusicInfo() {
        return musicInfo;
    }

    public void setMusicInfo(MusicInfo musicInfo) {
        this.musicInfo = musicInfo;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String resolveSongId() {
        if (musicInfo != null) {
            if (musicInfo.songmid != null && !musicInfo.songmid.isEmpty()) return musicInfo.songmid;
            if (musicInfo.hash != null && !musicInfo.hash.isEmpty()) return musicInfo.hash;
        }
        return songId;
    }

    public Map<String, Object> buildScriptRequest(String quality, String action) {
        Map<String, Object> request = new HashMap<>();
        request.put("action", action);
        request.put("source", source);
        Map<String, Object> info = new HashMap<>();
        MusicInfo mi = musicInfo != null ? musicInfo : new MusicInfo();
        if (mi.songmid == null) mi.songmid = songId;
        if (mi.hash == null) mi.hash = songId;
        info.put("musicInfo", mi.toMap());
        if ("musicUrl".equals(action)) {
            info.put("type", quality);
        }
        request.put("info", info);
        return request;
    }

    public static class MusicInfo {
        public String songmid;
        public String hash;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (songmid != null) map.put("songmid", songmid);
            if (hash != null) map.put("hash", hash);
            return map;
        }
    }
}






