package mindrift.app.music.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import mindrift.app.music.R;

public final class DockBarHelper {
    public static final int TAB_HOME = 0;
    public static final int TAB_SOURCE = 1;
    public static final int TAB_SETTINGS = 2;
    public static final int TAB_THEME = 3;

    private DockBarHelper() {
    }

    public static void bind(@NonNull AppCompatActivity activity, int activeTab) {
        View home = activity.findViewById(R.id.dock_tab_home);
        View source = activity.findViewById(R.id.dock_tab_source);
        View settings = activity.findViewById(R.id.dock_tab_settings);
        View theme = activity.findViewById(R.id.dock_tab_theme);

        if (home == null || source == null || settings == null || theme == null) {
            return;
        }

        applyState(home, activeTab == TAB_HOME);
        applyState(source, activeTab == TAB_SOURCE);
        applyState(settings, activeTab == TAB_SETTINGS);
        applyState(theme, activeTab == TAB_THEME);

        home.setOnClickListener(v -> switchTo(activity, MainActivity.class, activeTab, TAB_HOME));
        source.setOnClickListener(v -> switchTo(activity, ScriptCenterActivity.class, activeTab, TAB_SOURCE));
        settings.setOnClickListener(v -> switchTo(activity, SettingsActivity.class, activeTab, TAB_SETTINGS));
        theme.setOnClickListener(v -> switchTo(activity, ThemeTransferActivity.class, activeTab, TAB_THEME));
    }

    private static void switchTo(Activity activity, Class<?> target, int fromTab, int toTab) {
        if (activity instanceof HomeContainerActivity && fromTab == toTab) {
            return;
        }
        Intent intent = HomeContainerActivity.newIntent(activity, toTab);
        activity.startActivity(intent);
        if (toTab >= fromTab) {
            activity.overridePendingTransition(R.anim.dock_enter_from_right, R.anim.dock_exit_to_left);
        } else {
            activity.overridePendingTransition(R.anim.dock_enter_from_left, R.anim.dock_exit_to_right);
        }
    }

    private static void applyState(View tabView, boolean selected) {
        tabView.setSelected(selected);
        tabView.animate()
                .alpha(selected ? 1f : 0.82f)
                .scaleX(selected ? 1f : 0.96f)
                .scaleY(selected ? 1f : 0.96f)
                .setDuration(220L)
                .start();

        ImageView icon = null;
        TextView label = null;
        if (tabView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) tabView;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (icon == null && child instanceof ImageView) {
                    icon = (ImageView) child;
                } else if (label == null && child instanceof TextView) {
                    label = (TextView) child;
                }
            }
        }
        if (icon != null) {
            icon.setSelected(selected);
        }
        if (label != null) {
            label.setSelected(selected);
        }
    }
}
