
package com.example.purelauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.purelauncher.ui.views.BarChartView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ActivityParentDashboardActivity extends AppCompatActivity {

    private static final int PAGE_HOME = 0;
    private static final int PAGE_VAULT = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final String COLLECTION_SYNC_REQUESTS = "sync_requests";
    private static final String PREFS_SYNC = "parent_sync_prefs";
    private static final String KEY_LAST_RELOAD_SYNC_REQUEST_AT = "last_reload_sync_request_at";
    private static final String KEY_LAST_UPDATE_SYNC_REQUEST_AT = "last_update_sync_request_at";
    private static final long SYNC_COOLDOWN_MS = 10L * 1000L;

    private enum SyncAction {
        RELOAD,
        UPDATE
    }

    private View pageHome;
    private View pageVault;
    private View pageSettings;
    private ImageView navHome;
    private ImageView navVault;
    private ImageView navSettings;
    private boolean navExpanded = false;
    private int navIconPadding = 0;

    private View btnUpdate;
    private boolean pendingChanges = false;
    private String linkedChildUid;
    private TextView homeScreenTime;
    private BarChartView homeChart;
    private long latestScreenTimeMinutes = 0L;
    private long[] latestDailyUsageMinutes = new long[0];
    private int selectedHomeBarIndex = -1;

    private View vaultEmptyState;
    private View vaultListContainer;

    private RecyclerView vaultRecycler;
    private ParentAppTextAdapter vaultAdapter;
    private final List<ParentVaultEntry> vaultAllApps = new ArrayList<>();
    private final Set<String> vaultedPackages = new HashSet<>();
    private EditText vaultSearch;

    private View parentDrawerSheet;
    private RecyclerView drawerRecycler;
    private ParentAppTextAdapter drawerAdapter;
    private EditText drawerSearch;
    private float screenH;
    private boolean drawerOpen = false;
    private final List<ParentAppEntry> drawerApps = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final UserProfileStore profileStore = new UserProfileStore();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ListenerRegistration metricsListener;
    private ListenerRegistration appsListener;
    private ListenerRegistration vaultListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parent_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        pageHome = findViewById(R.id.pageHome);
        pageVault = findViewById(R.id.pageVault);
        pageSettings = findViewById(R.id.pageSettings);
        navHome = findViewById(R.id.navHome);
        navVault = findViewById(R.id.navVault);
        navSettings = findViewById(R.id.navSettings);
        parentDrawerSheet = findViewById(R.id.parentAppDrawerSheet);
        btnUpdate = findViewById(R.id.btnParentUpdate);

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenH = dm.heightPixels;
        if (parentDrawerSheet != null) {
            parentDrawerSheet.post(() -> parentDrawerSheet.setTranslationY(screenH));
        }

        setupHomeCard();
        setupVaultPage();
        setupSettingsPage();
        setupBottomNav();
        setupDrawer();
        setupUpdateButton();

        showPage(PAGE_HOME);
        startChildSync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (metricsListener != null) {
            metricsListener.remove();
        }
        if (appsListener != null) {
            appsListener.remove();
        }
        if (vaultListener != null) {
            vaultListener.remove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUpdateButtonState();
    }

    private void setupHomeCard() {
        View stats = findViewById(R.id.cardStats);
        if (stats != null) {
            stats.setOnClickListener(v -> startActivity(new Intent(this, ParentScreenTimeActivity.class)));
        }

        View reload = findViewById(R.id.btnReloadStats);
        if (reload != null) {
            reload.setOnClickListener(v -> {
                requestChildSyncFromUi(SyncAction.RELOAD, false);
                v.animate().rotationBy(360f).setDuration(350).start();
            });
        }

        TextView monitoring = findViewById(R.id.tvMonitoring);
        homeScreenTime = findViewById(R.id.tvScreenTime);
        TextView unlocks = findViewById(R.id.tvUnlockCount);
        TextView friction = findViewById(R.id.tvFrictionCount);
        TextView vaulted = findViewById(R.id.tvVaultedCount);
        homeChart = findViewById(R.id.barChart);

        if (monitoring != null) {
            monitoring.setVisibility(View.GONE);
        }
        renderHomeScreenTime();
        if (unlocks != null) {
            unlocks.setText("0");
        }
        if (friction != null) {
            friction.setText("0");
        }
        if (vaulted != null) {
            vaulted.setText("0");
        }
        if (homeChart != null) {
            homeChart.setSamples(new float[] { 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f });
            homeChart.setOnBarTouchListener(new BarChartView.OnBarTouchListener() {
                @Override
                public void onBarTouch(int index, float value) {
                    selectedHomeBarIndex = index;
                    renderHomeScreenTime();
                }

                @Override
                public void onBarRelease() {
                    selectedHomeBarIndex = -1;
                    renderHomeScreenTime();
                }
            });
        }
    }

    private void setupVaultPage() {
        vaultRecycler = findViewById(R.id.rvParentVaultApps);
        vaultSearch = null;
        vaultEmptyState = findViewById(R.id.vaultEmptyState);
        vaultListContainer = findViewById(R.id.vaultListContainer);
        if (vaultRecycler == null) {
            return;
        }

        vaultRecycler.setLayoutManager(new LinearLayoutManager(this));
        vaultAdapter = new ParentAppTextAdapter();
        vaultAdapter.setOnItemLongClickListener(item -> {
            if (item instanceof ParentVaultEntry) {
                showVaultOptions((ParentVaultEntry) item);
            }
        });
        vaultRecycler.setAdapter(vaultAdapter);

        View add = findViewById(R.id.btnAddVault);
        if (add != null) {
            add.setOnClickListener(v -> openDrawer());
        }
    }
//setting page
    private void setupSettingsPage() {
        updateSettingsSummaries();

        View rowTheme = findViewById(R.id.rowTheme);
        if (rowTheme != null) {
            rowTheme.setOnClickListener(v -> showThemePicker());
        }

        View rowFontSize = findViewById(R.id.rowFontSize);
        if (rowFontSize != null) {
            rowFontSize.setOnClickListener(v -> showFontSizePicker());
        }

        View remove = findViewById(R.id.rowRemoveLink);
        if (remove != null) {
            remove.setOnClickListener(v -> showRemoveLinkDialog());
        }

        View rowLogout = findViewById(R.id.rowLogout);
        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> showLogoutDialog());
        }

        LauncherUiPrefs.applyTypography(findViewById(R.id.main), this);
    }

    private void setupBottomNav() {
        if (navIconPadding == 0 && navHome != null) {
            navIconPadding = navHome.getPaddingLeft();
        }
        if (navHome != null) {
            navHome.setOnClickListener(v -> navClick(PAGE_HOME));
        }
        if (navVault != null) {
            navVault.setOnClickListener(v -> navClick(PAGE_VAULT));
        }
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> navClick(PAGE_SETTINGS));
        }
    }

    private void setupUpdateButton() {
        if (btnUpdate == null) {
            return;
        }
        btnUpdate.setOnClickListener(v -> requestChildSyncFromUi(SyncAction.UPDATE, true));
        updateUpdateButtonState();
    }

    private void setPendingChanges(boolean pending) {
        pendingChanges = pending;
        updateUpdateButtonState();
    }

    private void updateUpdateButtonState() {
        if (btnUpdate == null) {
            return;
        }
        boolean hasVaultedApps = !vaultAllApps.isEmpty();
        boolean canSync = canRequestSync(SyncAction.UPDATE);
        boolean enabled = pendingChanges && hasVaultedApps && canSync;
        btnUpdate.setVisibility(hasVaultedApps ? View.VISIBLE : View.GONE);
        btnUpdate.setEnabled(enabled);
        btnUpdate.setAlpha(enabled ? 1f : 0.4f);
    }

    private void navClick(int page) {
        if (!navExpanded) {
            navExpanded = true;
            updateNavVis();
        } else {
            showPage(page);
            navExpanded = false;
            updateNavVis();
        }
    }

    private void updateNavVis() {
        if (navHome != null) {
            navHome.setVisibility(navExpanded || isPageVisible(PAGE_HOME) ? View.VISIBLE : View.GONE);
        }
        if (navVault != null) {
            navVault.setVisibility(navExpanded || isPageVisible(PAGE_VAULT) ? View.VISIBLE : View.GONE);
        }
        if (navSettings != null) {
            navSettings.setVisibility(navExpanded || isPageVisible(PAGE_SETTINGS) ? View.VISIBLE : View.GONE);
        }
        alignNavIconMargins();
    }

    private void alignNavIconMargins() {
        boolean seenVisible = false;
        ImageView[] icons = new ImageView[] { navHome, navVault, navSettings };
        for (ImageView icon : icons) {
            if (icon == null) {
                continue;
            }
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
            lp.setMarginStart(seenVisible ? dpToPx(16) : 0);
            icon.setLayoutParams(lp);
            if (icon.getVisibility() == View.VISIBLE) {
                seenVisible = true;
            }
        }
    }

    private void showPage(int page) {
        closeDrawer();
        if (pageHome != null) {
            pageHome.setVisibility(page == PAGE_HOME ? View.VISIBLE : View.GONE);
        }
        if (pageVault != null) {
            pageVault.setVisibility(page == PAGE_VAULT ? View.VISIBLE : View.GONE);
        }
        if (pageSettings != null) {
            pageSettings.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        }
        applyNavState(navHome, page == PAGE_HOME);
        applyNavState(navVault, page == PAGE_VAULT);
        applyNavState(navSettings, page == PAGE_SETTINGS);
        updateNavVis();
    }

    private boolean isPageVisible(int page) {
        if (page == PAGE_HOME) {
            return pageHome != null && pageHome.getVisibility() == View.VISIBLE;
        }
        if (page == PAGE_VAULT) {
            return pageVault != null && pageVault.getVisibility() == View.VISIBLE;
        }
        return pageSettings != null && pageSettings.getVisibility() == View.VISIBLE;
    }

    private void applyNavState(ImageView nav, boolean active) {
        if (nav == null) {
            return;
        }
        int padding = navIconPadding > 0 ? navIconPadding : dpToPx(12);
        nav.setBackground(active ? getDrawable(R.drawable.bg_nav_active) : null);
        nav.setPadding(padding, padding, padding, padding);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (!isPageVisible(PAGE_HOME)) {
            showPage(PAGE_HOME);
            return;
        }
        super.onBackPressed();
    }

    private void setupDrawer() {
        if (parentDrawerSheet == null) {
            return;
        }
        drawerRecycler = parentDrawerSheet.findViewById(R.id.rvParentDrawerApps);
        drawerSearch = parentDrawerSheet.findViewById(R.id.etParentDrawerSearch);
        View close = parentDrawerSheet.findViewById(R.id.ivParentDrawerClose);
        View dragHandle = parentDrawerSheet.findViewById(R.id.drawerHandle);

        if (drawerRecycler != null) {
            drawerRecycler.setLayoutManager(new LinearLayoutManager(this));
            drawerAdapter = new ParentAppTextAdapter();
            drawerAdapter.setOnItemLongClickListener(this::showAddToVaultMenu);
            drawerRecycler.setAdapter(drawerAdapter);
        }

        if (drawerSearch != null) {
            drawerSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterDrawerApps(s == null ? "" : s.toString());
                }
            });
        }

        if (close != null) {
            close.setOnClickListener(v -> closeDrawer());
        }

        if (dragHandle != null) {
            final float dragThreshold = ViewConfiguration.get(this).getScaledTouchSlop() * 6f;
            final float[] startY = { -1f };
            dragHandle.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY[0] = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (startY[0] >= 0f && event.getRawY() - startY[0] > dragThreshold) {
                            startY[0] = -1f;
                            closeDrawer();
                            return true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        startY[0] = -1f;
                        return true;
                    default:
                        return false;
                }
            });
        }
    }

    private void openDrawer() {
        if (parentDrawerSheet == null || drawerOpen) {
            return;
        }
        drawerOpen = true;
        parentDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        parentDrawerSheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .withEndAction(() -> parentDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null))
                .start();
    }

    private void closeDrawer() {
        if (parentDrawerSheet == null || !drawerOpen) {
            return;
        }
        drawerOpen = false;
        parentDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        parentDrawerSheet.animate()
                .translationY(screenH)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    parentDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (drawerSearch != null) {
                        drawerSearch.setText("");
                    }
                })
                .start();
    }

    private void filterDrawerApps(String query) {
        if (drawerAdapter == null) {
            return;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<ParentAppEntry> filtered = new ArrayList<>();
        for (ParentAppEntry app : drawerApps) {
            if (vaultedPackages.contains(app.packageName)) {
                continue;
            }
            if (q.isEmpty() || app.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(app);
            }
        }
        drawerAdapter.setItems(filtered);
    }

    private void updateVaultVisibility() {
        boolean hasVaultedApps = !vaultAllApps.isEmpty();
        if (vaultEmptyState != null) {
            vaultEmptyState.setVisibility(hasVaultedApps ? View.GONE : View.VISIBLE);
        }
        if (vaultListContainer != null) {
            vaultListContainer.setVisibility(hasVaultedApps ? View.VISIBLE : View.GONE);
        }
    }

    private void updateChildApps(List<ParentAppEntry> apps) {
        drawerApps.clear();
        drawerApps.addAll(apps);
        filterDrawerApps(drawerSearch == null ? "" : drawerSearch.getText().toString());
    }

    private void updateVaultedApps(List<ParentVaultEntry> apps) {
        vaultAllApps.clear();
        vaultAllApps.addAll(apps);
        vaultedPackages.clear();
        for (ParentVaultEntry entry : vaultAllApps) {
            vaultedPackages.add(entry.packageName);
        }
        filterVaultApps(vaultSearch == null ? "" : vaultSearch.getText().toString());
        updateVaultVisibility();
        filterDrawerApps(drawerSearch == null ? "" : drawerSearch.getText().toString());
        updateUpdateButtonState();
    }

    private void startChildSync() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        profileStore.getLinkedChildUid(user).addOnSuccessListener(childUid -> {
            if (childUid == null || childUid.trim().isEmpty()) {
                linkedChildUid = null;
                updateVaultVisibility();
                updateUpdateButtonState();
                return;
            }
            linkedChildUid = childUid.trim();
            attachMetricsListener(linkedChildUid);
            attachChildAppsListener(linkedChildUid);
            attachVaultListener(linkedChildUid);
            requestChildSync(linkedChildUid, false, false, null);
        });
    }

    private void requestChildSyncFromUi(SyncAction action, boolean clearPendingOnSuccess) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        profileStore.getLinkedChildUid(user).addOnSuccessListener(childUid -> {
            if (childUid == null || childUid.trim().isEmpty()) {
                return;
            }
            requestChildSync(childUid.trim(), true, clearPendingOnSuccess, action);
        });
    }

    private void requestChildSync(String childUid, boolean userInitiated, boolean clearPendingOnSuccess,
            SyncAction action) {
        if (userInitiated && action != null && !canRequestSync(action)) {
            if (userInitiated) {
                long remainingMs = getSyncCooldownRemainingMs(action);
                long remainingSec = Math.max(1L, remainingMs / 1000L);
                Toast.makeText(this, "Sync cooldown: " + remainingSec + " s", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        String requestId = String.valueOf(System.currentTimeMillis());
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("types", "full");
        data.put("requestedAt", FieldValue.serverTimestamp());
        data.put("requestedBy", user.getUid());

        firestore.collection(COLLECTION_SYNC_REQUESTS)
                .document(childUid)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    if (userInitiated && action != null) {
                        markSyncRequested(action);
                    }
                    if (clearPendingOnSuccess) {
                        setPendingChanges(false);
                    }
                    if (userInitiated) {
                        Toast.makeText(this, "Sync requested", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String keyForAction(SyncAction action) {
        return action == SyncAction.UPDATE
                ? KEY_LAST_UPDATE_SYNC_REQUEST_AT
                : KEY_LAST_RELOAD_SYNC_REQUEST_AT;
    }

    private boolean canRequestSync(SyncAction action) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SYNC, MODE_PRIVATE);
        long last = prefs.getLong(keyForAction(action), 0L);
        return System.currentTimeMillis() - last >= SYNC_COOLDOWN_MS;
    }

    private long getSyncCooldownRemainingMs(SyncAction action) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SYNC, MODE_PRIVATE);
        long last = prefs.getLong(keyForAction(action), 0L);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, SYNC_COOLDOWN_MS - elapsed);
    }

    private void markSyncRequested(SyncAction action) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SYNC, MODE_PRIVATE);
        prefs.edit().putLong(keyForAction(action), System.currentTimeMillis()).apply();
        updateUpdateButtonState();
    }

    private void attachMetricsListener(String childUid) {
        if (metricsListener != null) {
            metricsListener.remove();
        }
        metricsListener = firestore.collection("child_metrics")
                .document(childUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    applyMetricsSnapshot(snapshot);
                });
    }

    private void applyMetricsSnapshot(DocumentSnapshot snapshot) {
        TextView screenTime = homeScreenTime != null ? homeScreenTime : findViewById(R.id.tvScreenTime);
        TextView unlocks = findViewById(R.id.tvUnlockCount);
        TextView friction = findViewById(R.id.tvFrictionCount);
        TextView vaulted = findViewById(R.id.tvVaultedCount);
        BarChartView chart = homeChart != null ? homeChart : findViewById(R.id.barChart);

        Long screenMinutes = snapshot.getLong("screenTimeMinutes");
        Long unlockCount = snapshot.getLong("unlockCount");
        Long frictionCount = snapshot.getLong("frictionCount");
        Long vaultedCount = snapshot.getLong("vaultedCount");
        @SuppressWarnings("unchecked")
        List<Long> daily = (List<Long>) snapshot.get("dailyUsageMinutes");

        if (screenMinutes != null) {
            latestScreenTimeMinutes = screenMinutes;
        }
        if (daily != null && !daily.isEmpty()) {
            latestDailyUsageMinutes = toLongArray(daily);
            if (chart != null) {
                chart.setSamples(normalize(daily));
                if (selectedHomeBarIndex < 0 || selectedHomeBarIndex >= latestDailyUsageMinutes.length) {
                    chart.setHighlightedBar(getTodayHomeBarIndex());
                }
            }
        }
        if (unlocks != null && unlockCount != null) {
            unlocks.setText(String.valueOf(unlockCount));
        }
        if (friction != null && frictionCount != null) {
            friction.setText(String.valueOf(frictionCount));
        }
        if (vaulted != null && vaultedCount != null) {
            vaulted.setText(String.valueOf(vaultedCount));
        }

        renderHomeScreenTime();
    }

    private void renderHomeScreenTime() {
        if (homeScreenTime == null) {
            return;
        }
        if (selectedHomeBarIndex >= 0 && selectedHomeBarIndex < latestDailyUsageMinutes.length) {
            homeScreenTime.setText(formatMinutes(latestDailyUsageMinutes[selectedHomeBarIndex]));
            if (homeChart != null) {
                homeChart.setHighlightedBar(selectedHomeBarIndex);
            }
            return;
        }
        homeScreenTime.setText(formatMinutes(latestScreenTimeMinutes));
        if (homeChart != null) {
            homeChart.setHighlightedBar(getTodayHomeBarIndex());
        }
    }

    private int getTodayHomeBarIndex() {
        return latestDailyUsageMinutes.length == 0 ? -1 : latestDailyUsageMinutes.length - 1;
    }

    private long[] toLongArray(List<Long> values) {
        long[] out = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Long value = values.get(i);
            out[i] = value == null ? 0L : value;
        }
        return out;
    }

    private void attachChildAppsListener(String childUid) {
        if (appsListener != null) {
            appsListener.remove();
        }
        appsListener = firestore.collection("child_apps")
                .document(childUid)
                .collection("apps")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    List<ParentAppEntry> apps = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) {
                            name = doc.getId();
                        }
                        String pkg = doc.getString("packageName");
                        if (pkg == null || pkg.trim().isEmpty()) {
                            pkg = doc.getId();
                        }
                        apps.add(new ParentAppEntry(name, pkg));
                    }
                    Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    updateChildApps(apps);
                });
    }

    private void attachVaultListener(String childUid) {
        if (vaultListener != null) {
            vaultListener.remove();
        }
        vaultListener = firestore.collection("child_vault")
                .document(childUid)
                .collection("apps")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    List<ParentVaultEntry> apps = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) {
                            name = doc.getId();
                        }
                        String pkg = doc.getString("packageName");
                        if (pkg == null || pkg.trim().isEmpty()) {
                            pkg = doc.getId();
                        }
                        int friction = doc.getLong("friction") == null
                                ? VaultPrefs.FRICTION_PLUS_ONE
                                : doc.getLong("friction").intValue();
                        int limitMinutes = doc.getLong("dailyLimitMinutes") == null
                                ? 0
                                : doc.getLong("dailyLimitMinutes").intValue();
                        String limitChangedAt = doc.getString("limitChangedAt");
                        apps.add(new ParentVaultEntry(name, pkg, friction, limitMinutes, limitChangedAt));
                    }
                    Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    updateVaultedApps(apps);
                });
    }

    private void filterVaultApps(String query) {
        if (vaultAdapter == null) {
            return;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            vaultAdapter.setItems(vaultAllApps);
            return;
        }
        List<ParentVaultEntry> filtered = new ArrayList<>();
        for (ParentVaultEntry app : vaultAllApps) {
            if (app.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(app);
            }
        }
        vaultAdapter.setItems(filtered);
    }

    private void showAddToVaultMenu(ParentAppEntry app) {
        if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
            return;
        }
        View menuView = getLayoutInflater().inflate(R.layout.dialog_app_context_menu, null);
        TextView title = menuView.findViewById(R.id.tvMenuTitle);
        TextView action = menuView.findViewById(R.id.tvVaultAction);
        if (title != null) {
            title.setText(app.name);
        }
        if (action != null) {
            action.setText("Add to Vault");
        }
        menuView.findViewById(R.id.divVaultTop).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.divLimitTop).setVisibility(View.GONE);
        menuView.findViewById(R.id.btnAppLimit).setVisibility(View.GONE);
        menuView.findViewById(R.id.divFrictionTop).setVisibility(View.GONE);
        menuView.findViewById(R.id.btnChangeFriction).setVisibility(View.GONE);
        menuView.findViewById(R.id.divAppInfoTop).setVisibility(View.GONE);
        menuView.findViewById(R.id.btnAppInfo).setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(menuView)
                .create();

        menuView.findViewById(R.id.btnToggleVault).setOnClickListener(v -> {
            addToVault(app);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showVaultOptions(ParentVaultEntry app) {
        View menuView = getLayoutInflater().inflate(R.layout.dialog_app_context_menu, null);
        TextView title = menuView.findViewById(R.id.tvMenuTitle);
        TextView action = menuView.findViewById(R.id.tvVaultAction);
        if (title != null) {
            title.setText(app.name);
        }
        if (action != null) {
            action.setText("Remove from Vault");
        }
        menuView.findViewById(R.id.divVaultTop).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.divLimitTop).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.btnAppLimit).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.divFrictionTop).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.btnChangeFriction).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.divAppInfoTop).setVisibility(View.GONE);
        menuView.findViewById(R.id.btnAppInfo).setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(menuView)
                .create();

        menuView.findViewById(R.id.btnToggleVault).setOnClickListener(v -> {
            removeFromVault(app);
            dialog.dismiss();
        });
        menuView.findViewById(R.id.btnChangeFriction).setOnClickListener(v -> {
            dialog.dismiss();
            showFrictionPicker(app);
        });
        menuView.findViewById(R.id.btnAppLimit).setOnClickListener(v -> {
            dialog.dismiss();
            showLimitPicker(app);
        });

        dialog.show();
    }

    private void addToVault(ParentAppEntry app) {
        if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name", app.name);
        data.put("packageName", app.packageName);
        data.put("friction", VaultPrefs.FRICTION_PLUS_ONE);
        data.put("dailyLimitMinutes", 0);
        data.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection("child_vault")
                .document(linkedChildUid)
                .collection("apps")
                .document(app.packageName)
                .set(data)
                .addOnSuccessListener(aVoid -> setPendingChanges(true));
    }

    private void removeFromVault(ParentVaultEntry app) {
        if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
            return;
        }
        firestore.collection("child_vault")
                .document(linkedChildUid)
                .collection("apps")
                .document(app.packageName)
                .delete()
                .addOnSuccessListener(aVoid -> setPendingChanges(true));
    }

    private void showFrictionPicker(ParentVaultEntry app) {
        View view = getLayoutInflater().inflate(R.layout.dialog_friction_picker, null);
        android.widget.RadioGroup rg = view.findViewById(R.id.rgFriction);
        int currentType = app.friction;
        if (currentType == VaultPrefs.FRICTION_PLUS_ONE) {
            rg.check(R.id.rbPlusOne);
        } else if (currentType == VaultPrefs.FRICTION_X2) {
            rg.check(R.id.rb2x);
        } else if (currentType == VaultPrefs.FRICTION_X3) {
            rg.check(R.id.rb3x);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.Button btnOkay = view.findViewById(R.id.btnOkay);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnOkay != null) {
            btnOkay.setOnClickListener(v -> {
                int type = VaultPrefs.FRICTION_PLUS_ONE;
                int checkedId = rg.getCheckedRadioButtonId();
                if (checkedId == R.id.rb2x) {
                    type = VaultPrefs.FRICTION_X2;
                } else if (checkedId == R.id.rb3x) {
                    type = VaultPrefs.FRICTION_X3;
                }
                updateVaultFriction(app, type);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void updateVaultFriction(ParentVaultEntry app, int type) {
        if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
            return;
        }
        firestore.collection("child_vault")
                .document(linkedChildUid)
                .collection("apps")
                .document(app.packageName)
                .update("friction", type, "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> setPendingChanges(true));
    }

    private void showLimitPicker(ParentVaultEntry app) {
        if (!canChangeLimitToday(app)) {
            showStyledWarningDialog("Limit already set",
                    "App limit can only be changed once a day. Please try again tomorrow.");
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_limit_picker, null);
        android.widget.TimePicker tp = view.findViewById(R.id.timePicker);
        tp.setIs24HourView(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            tp.setHour(0);
            tp.setMinute(0);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.Button btnOkay = view.findViewById(R.id.btnOkay);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnOkay != null) {
            btnOkay.setOnClickListener(v -> {
                int h = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                        ? tp.getHour()
                        : tp.getCurrentHour();
                int m = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                        ? tp.getMinute()
                        : tp.getCurrentMinute();
                updateVaultLimit(app, h * 60 + m);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private boolean canChangeLimitToday(ParentVaultEntry app) {
        if (app.limitChangedAt == null || app.limitChangedAt.trim().isEmpty()) {
            return true;
        }
        return !LocalDate.now().toString().equals(app.limitChangedAt);
    }

    private void updateVaultLimit(ParentVaultEntry app, int minutes) {
        if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
            return;
        }
        String today = LocalDate.now().toString();
        firestore.collection("child_vault")
                .document(linkedChildUid)
                .collection("apps")
                .document(app.packageName)
                .update("dailyLimitMinutes", minutes,
                        "limitChangedAt", today,
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> setPendingChanges(true));
    }

    private void unlinkAndReturnToLogin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        profileStore.unlinkLinkedChild(user).addOnCompleteListener(task -> {
            profileStore.clearSession(user.getUid());
            FirebaseAuth.getInstance().signOut();
            SessionPrefs.setParentLinkingComplete(this, false);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showStyledWarningDialog(String title, String message) {
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = view.findViewById(R.id.tvDialogMessage);
        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.Button btnAccept = view.findViewById(R.id.btnAccept);

        if (tvTitle != null) {
            tvTitle.setText(title);
        }
        if (tvMessage != null) {
            tvMessage.setText(message);
        }
        if (btnCancel != null) {
            btnCancel.setVisibility(View.GONE);
        }
        if (btnAccept != null) {
            btnAccept.setText("OK");
            btnAccept.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void showThemePicker() {
        final String[] labels = new String[] { "System default", "Light", "Dark" };
        final String[] values = new String[] {
                LauncherUiPrefs.THEME_SYSTEM,
                LauncherUiPrefs.THEME_LIGHT,
                LauncherUiPrefs.THEME_DARK
        };
        String current = LauncherUiPrefs.getTheme(this);
        showSingleChoiceDialog("Color theme", labels, values, current, value -> {
            LauncherUiPrefs.setTheme(this, value);
            LauncherUiPrefs.applyTheme(this);
            recreate();
        });
    }

    private void showFontSizePicker() {
        final String[] labels = new String[] { "Small", "Medium", "Large" };
        final String[] values = new String[] {
                LauncherUiPrefs.FONT_SIZE_SMALL,
                LauncherUiPrefs.FONT_SIZE_MEDIUM,
                LauncherUiPrefs.FONT_SIZE_LARGE
        };
        String current = LauncherUiPrefs.getFontSize(this);
        showSingleChoiceDialog("Font size", labels, values, current, value -> {
            LauncherUiPrefs.setFontSize(this, value);
            recreate();
        });
    }

    private void showRemoveLinkDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = view.findViewById(R.id.tvDialogMessage);
        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.Button btnAccept = view.findViewById(R.id.btnAccept);

        if (tvTitle != null) {
            tvTitle.setText("Remove link");
        }
        if (tvMessage != null) {
            tvMessage.setText("Are You Sure?");
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                dialog.dismiss();
                unlinkAndReturnToLogin();
            });
        }

        dialog.show();
    }

    private interface SelectionHandler {
        void onSelect(String value);
    }

    private void showSingleChoiceDialog(String title, String[] labels, String[] values, String current,
            SelectionHandler handler) {
        View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        TextView titleView = view.findViewById(R.id.tvDialogTitle);
        android.widget.RadioGroup group = view.findViewById(R.id.rgDialogOptions);
        titleView.setText(title);

        android.content.res.ColorStateList radioColors = androidx.core.content.ContextCompat.getColorStateList(this,
                R.color.dialog_radio_color);
        int checkedId = View.NO_ID;

        for (int i = 0; i < labels.length; i++) {
            androidx.appcompat.widget.AppCompatRadioButton rb = new androidx.appcompat.widget.AppCompatRadioButton(
                    this);
            rb.setId(View.generateViewId());
            rb.setText(labels[i]);
            rb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
            rb.setTextColor(radioColors);
            rb.setButtonTintList(radioColors);
            rb.setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10));

            android.widget.RadioGroup.LayoutParams lp = new android.widget.RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dpToPx(6), 0, dpToPx(6));
            rb.setLayoutParams(lp);
            group.addView(rb);

            if (values[i].equals(current)) {
                checkedId = rb.getId();
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        if (checkedId != View.NO_ID) {
            group.check(checkedId);
        }

        group.setOnCheckedChangeListener((g, id) -> {
            int index = -1;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i).getId() == id) {
                    index = i;
                    break;
                }
            }
            if (index >= 0 && index < values.length) {
                handler.onSelect(values[index]);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateSettingsSummaries() {
        TextView tvTheme = findViewById(R.id.tvThemeValue);
        TextView tvFontSize = findViewById(R.id.tvFontSizeValue);

        if (tvTheme != null) {
            String theme = LauncherUiPrefs.getTheme(this);
            if (LauncherUiPrefs.THEME_LIGHT.equals(theme)) {
                tvTheme.setText("Light");
            } else if (LauncherUiPrefs.THEME_DARK.equals(theme)) {
                tvTheme.setText("Dark");
            } else {
                tvTheme.setText("System default");
            }
        }

        if (tvFontSize != null) {
            String fontSize = LauncherUiPrefs.getFontSize(this);
            if (LauncherUiPrefs.FONT_SIZE_SMALL.equals(fontSize)) {
                tvFontSize.setText("Small");
            } else if (LauncherUiPrefs.FONT_SIZE_LARGE.equals(fontSize)) {
                tvFontSize.setText("Large");
            } else {
                tvFontSize.setText("Medium");
            }
        }
    }

    private void showLogoutDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        TextView msg = view.findViewById(R.id.tvDialogMessage);
        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.Button btnAccept = view.findViewById(R.id.btnAccept);

        if (msg != null) {
            msg.setText("Are You Sure?");
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    profileStore.clearSession(user.getUid());
                }
                FirebaseAuth.getInstance().signOut();
                SessionPrefs.setParentLinkingComplete(this, false);
                Intent roleSelection = new Intent(this, OnboardingActivity.class);
                roleSelection.putExtra("forceRoleSelection", true);
                startActivity(roleSelection);
                finishAffinity();
            });
        }

        dialog.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float[] normalize(List<Long> vals) {
        if (vals == null || vals.isEmpty()) {
            return new float[] { 0.05f };
        }
        long max = 1;
        for (Long v : vals) {
            if (v != null && v > max) {
                max = v;
            }
        }
        float[] out = new float[vals.size()];
        for (int i = 0; i < vals.size(); i++) {
            long v = vals.get(i) == null ? 0 : vals.get(i);
            out[i] = Math.max(0.05f, Math.min(1f, (float) v / max));
        }
        return out;
    }

    private String formatMinutes(long mins) {
        long h = mins / 60;
        long m = mins % 60;
        return h == 0 ? m + "m" : h + "h " + m + "m";
    }

    private static class ParentAppEntry {
        final String name;
        final String packageName;

        ParentAppEntry(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
    }

    private static class ParentVaultEntry extends ParentAppEntry {
        final int friction;
        final int dailyLimitMinutes;
        final String limitChangedAt;

        ParentVaultEntry(String name, String packageName, int friction,
                int dailyLimitMinutes, String limitChangedAt) {
            super(name, packageName);
            this.friction = friction;
            this.dailyLimitMinutes = dailyLimitMinutes;
            this.limitChangedAt = limitChangedAt;
        }
    }

    private interface OnItemLongClickListener {
        void onItemLongClick(ParentAppEntry app);
    }

    private class ParentAppTextAdapter extends RecyclerView.Adapter<ParentAppTextAdapter.VH> {

        private final List<ParentAppEntry> items = new ArrayList<>();
        private OnItemLongClickListener longClickListener;

        void setOnItemLongClickListener(OnItemLongClickListener listener) {
            longClickListener = listener;
        }

        void setItems(List<? extends ParentAppEntry> data) {
            items.clear();
            if (data != null) {
                items.addAll(data);
            }
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_parent_app_text, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            ParentAppEntry app = items.get(position);
            holder.name.setText(app.name);
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClick(app);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;

            VH(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvAppName);
            }
        }
    }
}
