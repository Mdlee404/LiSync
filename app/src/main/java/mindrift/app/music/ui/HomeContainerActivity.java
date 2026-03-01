package mindrift.app.music.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import mindrift.app.music.R;

public class HomeContainerActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "home_tab";

    private ViewPager2 viewPager;
    private View tabHome;
    private View tabSource;
    private View tabSettings;
    private View tabTheme;

    public static Intent newIntent(Context context, int tab) {
        Intent intent = new Intent(context, HomeContainerActivity.class);
        intent.putExtra(EXTRA_TAB, tab);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_container);

        viewPager = findViewById(R.id.home_pager);
        tabHome = findViewById(R.id.dock_tab_home);
        tabSource = findViewById(R.id.dock_tab_source);
        tabSettings = findViewById(R.id.dock_tab_settings);
        tabTheme = findViewById(R.id.dock_tab_theme);

        viewPager.setAdapter(new HomePagerAdapter(this));
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDockState(position);
            }
        });

        tabHome.setOnClickListener(v -> switchTab(DockBarHelper.TAB_HOME));
        tabSource.setOnClickListener(v -> switchTab(DockBarHelper.TAB_SOURCE));
        tabSettings.setOnClickListener(v -> switchTab(DockBarHelper.TAB_SETTINGS));
        tabTheme.setOnClickListener(v -> switchTab(DockBarHelper.TAB_THEME));

        int initialTab = resolveTabFromIntent(getIntent());
        switchTab(initialTab, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int targetTab = resolveTabFromIntent(intent);
        switchTab(targetTab, true);
    }

    public void switchTab(int tab) {
        switchTab(tab, true);
    }

    private void switchTab(int tab, boolean smoothScroll) {
        int safeTab = normalizeTab(tab);
        if (viewPager.getCurrentItem() != safeTab) {
            viewPager.setCurrentItem(safeTab, smoothScroll);
        } else {
            updateDockState(safeTab);
        }
    }

    private int resolveTabFromIntent(Intent intent) {
        if (intent == null) return DockBarHelper.TAB_HOME;
        return normalizeTab(intent.getIntExtra(EXTRA_TAB, DockBarHelper.TAB_HOME));
    }

    private int normalizeTab(int tab) {
        if (tab < DockBarHelper.TAB_HOME || tab > DockBarHelper.TAB_THEME) {
            return DockBarHelper.TAB_HOME;
        }
        return tab;
    }

    private void updateDockState(int activeTab) {
        applyTabState(tabHome, activeTab == DockBarHelper.TAB_HOME);
        applyTabState(tabSource, activeTab == DockBarHelper.TAB_SOURCE);
        applyTabState(tabSettings, activeTab == DockBarHelper.TAB_SETTINGS);
        applyTabState(tabTheme, activeTab == DockBarHelper.TAB_THEME);
    }

    private void applyTabState(View tab, boolean selected) {
        if (tab == null) return;
        tab.setSelected(selected);
        tab.animate()
                .alpha(selected ? 1f : 0.84f)
                .scaleX(selected ? 1f : 0.95f)
                .scaleY(selected ? 1f : 0.95f)
                .setDuration(220L)
                .start();
        ImageView icon = null;
        TextView text = null;
        if (tab instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) tab;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (icon == null && child instanceof ImageView) {
                    icon = (ImageView) child;
                } else if (text == null && child instanceof TextView) {
                    text = (TextView) child;
                }
            }
        }
        if (icon != null) icon.setSelected(selected);
        if (text != null) text.setSelected(selected);
    }

    private static class HomePagerAdapter extends FragmentStateAdapter {
        HomePagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case DockBarHelper.TAB_SOURCE:
                    return new SourceFragment();
                case DockBarHelper.TAB_SETTINGS:
                    return new SettingsFragment();
                case DockBarHelper.TAB_THEME:
                    return new ThemeFragment();
                case DockBarHelper.TAB_HOME:
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
