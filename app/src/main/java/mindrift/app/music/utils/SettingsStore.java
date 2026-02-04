package mindrift.app.music.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private static final String PREFS_NAME = "lisync_prefs";
    private static final String KEY_FORCED_SCRIPT = "forced_script_id";
    private static final String KEY_FORCE_POLLING = "force_polling";

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
        return prefs.getBoolean(KEY_FORCE_POLLING, false);
    }

    public static void setForcePolling(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FORCE_POLLING, enabled).apply();
    }
}
