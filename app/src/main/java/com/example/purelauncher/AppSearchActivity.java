package com.example.purelauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSearchActivity extends AppCompatActivity {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int COLOR_ACTIVE   = Color.WHITE;
    private static final int COLOR_HAS_APPS = Color.parseColor("#CCCCCC");
    private static final int COLOR_EMPTY    = Color.parseColor("#444444");

    private final List<AppEntry> allApps = new ArrayList<>();
    private AppSearchAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private TextView letterBubble;
    private TextView[] letterViews;
    private boolean[] letterHasApps;
    private int lastSelectedIndex = -1;
    private boolean isSearchActive = false;

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_app_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.rvApps);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new AppSearchAdapter(this, new ArrayList<>());
        adapter.setOnAppLongClickListener(this::showAppOptions);
        recyclerView.setAdapter(adapter);

        letterBubble = findViewById(R.id.tvLetterBubble);

        // Search input
        EditText queryInput = findViewById(R.id.etSearchApps);
        queryInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString();
                isSearchActive = !q.trim().isEmpty();
                filterApps(q);
            }
        });

        // Load and display apps
        allApps.clear();
        allApps.addAll(loadLaunchableAppsExcludingVaulted());
        adapter.updateItems(allApps);

        // Compute which letters actually have apps — used to colour the sidebar
        letterHasApps = new boolean[LETTERS.length()];
        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            for (AppEntry app : allApps) {
                if (!app.label.trim().isEmpty() &&
                        Character.toUpperCase(app.label.charAt(0)) == letter) {
                    letterHasApps[i] = true;
                    break;
                }
            }
        }

        buildLetterSidebar();
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(0, 0);
    }

    // ─── A-Z Sidebar ───────────────────────────────────────────────────────────

    private void buildLetterSidebar() {
        LinearLayout sidebar = findViewById(R.id.letterSidebar);
        sidebar.removeAllViews();
        letterViews = new TextView[LETTERS.length()];

        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(letter));
            tv.setTextSize(11f);
            tv.setGravity(Gravity.CENTER);
            tv.setTag(letter);
            tv.setTextColor(letterHasApps[i] ? COLOR_HAS_APPS : COLOR_EMPTY);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            tv.setLayoutParams(lp);
            sidebar.addView(tv);
            letterViews[i] = tv;
        }

        sidebar.setOnTouchListener((v, event) -> {
            if (isSearchActive) return false;

            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float y = Math.max(0, Math.min(event.getY(), v.getHeight() - 1));
                int index = (int) ((y / v.getHeight()) * LETTERS.length());
                index = Math.max(0, Math.min(LETTERS.length() - 1, index));

                if (index != lastSelectedIndex) {
                    // Animate the previous selection back out
                    if (lastSelectedIndex >= 0) {
                        animateLetterOut(lastSelectedIndex);
                    }
                    lastSelectedIndex = index;
                    animateLetterIn(index);
                }

                scrollToLetter(LETTERS.charAt(index));
                showLetterBubble(LETTERS.charAt(index));
                return true;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (lastSelectedIndex >= 0) {
                    animateLetterOut(lastSelectedIndex);
                    lastSelectedIndex = -1;
                }
                hideLetterBubble();
                return true;
            }
            return false;
        });
    }

    /** Scale up + add circular highlight ring around the touched letter. */
    private void animateLetterIn(int index) {
        if (index < 0 || index >= letterViews.length) return;
        TextView tv = letterViews[index];
        tv.setTextColor(COLOR_ACTIVE);
        tv.setBackground(makeCircleBackground());
        tv.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(80)
                .start();
    }

    /** Scale back to normal, remove circle, restore colour. */
    private void animateLetterOut(int index) {
        if (index < 0 || index >= letterViews.length) return;
        TextView tv = letterViews[index];
        tv.setTextColor(letterHasApps[index] ? COLOR_HAS_APPS : COLOR_EMPTY);
        tv.setBackground(null);
        tv.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(80)
                .start();
    }

    private GradientDrawable makeCircleBackground() {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#555555"));
        return circle;
    }

    private void scrollToLetter(char letter) {
        int pos = adapter.getPositionForLetter(letter);
        if (pos >= 0) {
            layoutManager.scrollToPositionWithOffset(pos, 0);
        }
    }

    private void showLetterBubble(char letter) {
        letterBubble.setText(String.valueOf(letter));
        letterBubble.setVisibility(View.VISIBLE);
    }

    private void hideLetterBubble() {
        letterBubble.setVisibility(View.GONE);
    }

    // ─── App Loading ───────────────────────────────────────────────────────────

    private List<AppEntry> loadLaunchableAppsExcludingVaulted() {
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launchable = getPackageManager().queryIntentActivities(launchIntent, 0);
        List<AppEntry> items = new ArrayList<>();
        String selfPackage = getPackageName();
        Set<String> vaultedPackages = new HashSet<>(VaultPrefs.getVaultedPackages(this));

        for (ResolveInfo info : launchable) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (selfPackage.equals(packageName) || vaultedPackages.contains(packageName)) continue;
            CharSequence labelSeq = info.loadLabel(getPackageManager());
            String label = labelSeq == null ? packageName : labelSeq.toString();
            String category = categoryLabel(info.activityInfo.applicationInfo.category);
            items.add(new AppEntry(label, category, packageName));
        }

        items.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return items;
    }

    private void filterApps(String query) {
        String normalized = query.toLowerCase(Locale.getDefault()).trim();
        if (normalized.isEmpty()) {
            adapter.updateItems(allApps);
            return;
        }
        List<AppEntry> filtered = new ArrayList<>();
        for (AppEntry item : allApps) {
            if (item.label.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filtered.add(item);
            }
        }
        adapter.updateItems(filtered);
    }

    private String categoryLabel(int category) {
        if (category == ApplicationInfo.CATEGORY_PRODUCTIVITY) return "Productivity";
        if (category == ApplicationInfo.CATEGORY_SOCIAL) return "Social";
        if (category == ApplicationInfo.CATEGORY_NEWS) return "News";
        if (category == ApplicationInfo.CATEGORY_VIDEO) return "Video";
        if (category == ApplicationInfo.CATEGORY_AUDIO) return "Audio";
        if (category == ApplicationInfo.CATEGORY_GAME) return "Games";
        return "Utility";
    }

    // ─── Long-press context menu ────────────────────────────────────────────────

    private void showAppOptions(AppEntry app) {
        boolean vaulted = VaultPrefs.getVaultedPackages(this).contains(app.packageName);
        String vaultLabel = vaulted ? "Remove from Vault" : "Add to Vault";
        String[] options = {vaultLabel, "App info", "Uninstall"};

        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Vault toggle
                            if (vaulted) VaultPrefs.removeVaultedPackage(this, app.packageName);
                            else         VaultPrefs.addVaultedPackage(this, app.packageName);
                            // Refresh list so vaulted apps disappear
                            allApps.clear();
                            allApps.addAll(loadLaunchableAppsExcludingVaulted());
                            adapter.updateItems(allApps);
                            break;
                        case 1: // App info
                            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + app.packageName)));
                            break;
                        case 2: // Uninstall
                            startActivity(new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + app.packageName)));
                            break;
                    }
                })
                .show();
    }

    // ─── AppEntry ──────────────────────────────────────────────────────────────

    static final class AppEntry {
        final String label;
        final String category;
        final String packageName;

        AppEntry(String label, String category, String packageName) {
            this.label = label;
            this.category = category;
            this.packageName = packageName;
        }
    }
}
