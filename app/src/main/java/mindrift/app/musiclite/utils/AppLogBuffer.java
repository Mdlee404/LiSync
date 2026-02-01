package mindrift.app.musiclite.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AppLogBuffer {
    public interface LogListener {
        void onLogAdded(String line);
    }

    private static final int MAX_LINES = 500;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final List<LogListener> LISTENERS = new CopyOnWriteArrayList<>();

    private AppLogBuffer() {}

    public static void append(String line) {
        synchronized (LINES) {
            LINES.addLast(line);
            while (LINES.size() > MAX_LINES) {
                LINES.removeFirst();
            }
        }
        for (LogListener listener : LISTENERS) {
            listener.onLogAdded(line);
        }
    }

    public static String getSnapshot() {
        StringBuilder builder = new StringBuilder();
        synchronized (LINES) {
            for (String line : LINES) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }

    public static void addListener(LogListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(LogListener listener) {
        LISTENERS.remove(listener);
    }
}







