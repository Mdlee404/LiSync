package mindrift.app.music.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private static final String PREFS_NAME = "lisync_prefs";
    private static final String KEY_FORCED_SCRIPT = "forced_script_id";
    private static final String KEY_FORCE_POLLING = "force_polling";
    private static final String KEY_CLOUD_REPO_URL = "cloud_repo_url";
    private static final String DEFAULT_CLOUD_REPO_URL = "https://music.scriptlibrary.mindrift.cn/library.json";

    private SettingsStore() {}

    public static String getForcedScriptId(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FORCED_SCRIPT, "");
    }

    public static void setForcedScriptId(Context context, String scriptId) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = scriptId == null ? "" : scriptId.trim();
        prefs.edit().putString(KEY_FORCED_SCRIPT, value).apply();
    }

    public static boolean isForcePolling(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FORCE_POLLING, true);
    }

    public static void setForcePolling(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FORCE_POLLING, enabled).apply();
    }

    public static String getCloudRepoUrl(Context context) {
        if (context == null) return DEFAULT_CLOUD_REPO_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_CLOUD_REPO_URL, DEFAULT_CLOUD_REPO_URL);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_CLOUD_REPO_URL;
        }
        return value.trim();
    }

    public static void setCloudRepoUrl(Context context, String url) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) {
            value = DEFAULT_CLOUD_REPO_URL;
        }
        prefs.edit().putString(KEY_CLOUD_REPO_URL, value).apply();
    }
}
