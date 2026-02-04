package mindrift.app.music.utils;

import java.util.Locale;

public final class PlatformUtils {
    private PlatformUtils() {}

    public static String normalize(String platform) {
        if (platform == null) return null;
        String value = platform.trim();
        if (value.isEmpty()) return value;
        String lower = value.toLowerCase(Locale.US);
        switch (lower) {
            case "tx":
                return "tx";
            case "wy":
                return "wy";
            case "kg":
                return "kg";
        }
        if ("QQ音乐".equalsIgnoreCase(value) || "QQ".equalsIgnoreCase(value) || "腾讯".equals(value) || "腾讯音乐".equals(value)) {
            return "tx";
        }
        if ("网易云音乐".equals(value) || "网易云".equals(value) || "网易".equals(value)) {
            return "wy";
        }
        if ("酷狗音乐".equals(value) || "酷狗".equals(value)) {
            return "kg";
        }
        return value;
    }

    public static String displayName(String platform) {
        if (platform == null) return null;
        String code = normalize(platform);
        if ("tx".equalsIgnoreCase(code)) {
            return "QQ音乐";
        }
        if ("wy".equalsIgnoreCase(code)) {
            return "网易云音乐";
        }
        if ("kg".equalsIgnoreCase(code)) {
            return "酷狗音乐";
        }
        if ("kw".equalsIgnoreCase(code)) {
            return "酷我 (KW)";
        }
        if ("mg".equalsIgnoreCase(code)) {
            return "咪咕 (MG)";
        }
        if ("local".equalsIgnoreCase(code)) {
            return "本地";
        }
        return platform;
    }
}
