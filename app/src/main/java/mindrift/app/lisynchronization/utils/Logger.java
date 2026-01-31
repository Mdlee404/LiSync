package mindrift.app.lisynchronization.utils;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Logger {
    private static final String TAG = "LiSync";
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private Logger() {}

    public static void init() {
        AppLogBuffer.clear();
    }

    public static void debug(String message) {
        log("D", message, null);
    }

    public static void info(String message) {
        log("I", message, null);
    }

    public static void warn(String message) {
        log("W", message, null);
    }

    public static void error(String message) {
        log("E", message, null);
    }

    public static void error(String message, Throwable t) {
        log("E", message, t);
    }

    private static void log(String level, String message, Throwable t) {
        String timestamp = FORMAT.format(new Date());
        String line = "[" + timestamp + "] " + level + " " + message;
        switch (level) {
            case "D":
                Log.d(TAG, message, t);
                break;
            case "W":
                Log.w(TAG, message, t);
                break;
            case "E":
                Log.e(TAG, message, t);
                break;
            default:
                Log.i(TAG, message, t);
                break;
        }
        AppLogBuffer.append(line);
    }
}






