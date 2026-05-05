package com.example.purelauncher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.app.role.RoleManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TimePicker;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.purelauncher.ui.views.BarChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LauncherActivity extends AppCompatActivity {

    private static final int PAGE_HOME = 0, PAGE_VAULT = 1, PAGE_SETTINGS = 2;

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm", Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            bindDateTime();
            scheduleNextClockTick();
        }
    };
    private final Runnable metricsTick = new Runnable() {
        @Override
        public void run() {
            bindMetrics();
            clock.postDelayed(this, 5_000L);
        }
    };

    private final TelemetryRepository repo = new TelemetryRepository();
    // profileStore kept for auth state routing but cloud sync features removed

    private int currentPage = PAGE_HOME;
    private boolean navExpanded = false;
    private boolean drawerOpen = false;
    private float screenH = 0f;
    private TelemetrySnapshot snap;
    private float swipeStartY = -1;
    private boolean isSwipingUp = false;
    private boolean drawerSidebarBuilt = false;
    private boolean metricsLoading = false;

    private View pageHome, pageVault, pageSettings;
    private View appDrawerSheet;
    private ImageView navHome, navVault, navSettings;
    private TextView tvTime;
    private TextView tvDate;
    private TextView tvHeadline;
    private ImageView btnPhone;
    private ImageView btnCamera;
    private View cardStats;
    private boolean askedHomeRole = false;

    private AppSearchAdapter drawerAdapter;
    private final List<AppSearchActivity.AppEntry> allApps = new ArrayList<>();
    private final List<AppSearchActivity.AppEntry> vaultApps = new ArrayList<>();

    private View cardVaultEmpty;
    private View vaultListContainer;
    private RecyclerView vaultRecycler;
    private AppSearchAdapter vaultAdapter;
    private LinearLayoutManager vaultLayoutManager;
    private TextView vaultLetterBubble;
    private TextView[] vaultLetterViews;
    private boolean[] vaultLetterHasApps;
    private int vaultLastSelectedIndex = -1;
    private boolean isVaultSearchActive = false;

    private int navIconPadding = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherUiPrefs.applyTheme(this);

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || !SessionPrefs.isChildAuthComplete(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_launcher);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, ins) -> {
            Insets sb = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return ins;
        });
        blockNotificationShade();

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenH = dm.heightPixels;

        pageHome = findViewById(R.id.pageHome);
        pageVault = findViewById(R.id.pageVault);
        pageSettings = findViewById(R.id.pageSettings);
        appDrawerSheet = findViewById(R.id.appDrawerSheet);
        navHome = findViewById(R.id.navHome);
        navVault = findViewById(R.id.navVault);
        navSettings = findViewById(R.id.navSettings);
        tvTime = findViewById(R.id.tvTime);
        tvDate = findViewById(R.id.tvDate);
        tvHeadline = findViewById(R.id.tvHeadline);
        btnPhone = findViewById(R.id.btnOpenPhone);
        btnCamera = findViewById(R.id.btnOpenCamera);
        cardStats = findViewById(R.id.cardStats);

        appDrawerSheet.post(() -> appDrawerSheet.setTranslationY(screenH));

        setupBottomNav();
        setupPageActions();
        setupSettingsPage();
        setupDrawer();
        setupVaultPage();
        setupHomeShortcuts();
        ensureDefaultLauncher();

        bindHeadline();
        bindMetrics();
        showPage(getIntent().getBooleanExtra("openVault", false) ? PAGE_VAULT : PAGE_HOME);

        if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD) {
            startService(new Intent(this, AppUsageGuardService.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD
                && !RequiredPermissions.allGranted(this)) {
            SessionPrefs.setPersonalPermissionsComplete(this, false);
            Intent intent = new Intent(this, PersonalPermissionsActivity.class);
            intent.putExtra("permissionRevoked", true);
            startActivity(intent);
            finish();
            return;
        }
        startClockUpdates();
        startMetricsUpdates();
        blockNotificationShade();
        reloadDrawerApps();
        refreshVaultPage();
        bindMetrics(); // Refresh local stats
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("openVault", false)) {
            if (drawerOpen) {
                closeDrawer();
            }
            showPage(PAGE_VAULT);
            refreshVaultPage();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        clock.removeCallbacks(tick);
        clock.removeCallbacks(metricsTick);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            blockNotificationShade();
    }

    private void blockNotificationShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                wic.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onBackPressed() {
        if (contextMenuOverlay != null) {
            dismissContextMenuOverlay();
            return;
        }
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (currentPage != PAGE_HOME) {
            showPage(PAGE_HOME);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (drawerOpen) {
            return super.dispatchTouchEvent(ev);
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                swipeStartY = ev.getRawY();
                isSwipingUp = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (swipeStartY > 0 && currentPage == PAGE_HOME) {
                    float dy = swipeStartY - ev.getRawY();
                    if (dy > 50) {
                        isSwipingUp = true;
                        appDrawerSheet.setTranslationY(Math.max(0, screenH - dy));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwipingUp) {
                    float dy = swipeStartY - ev.getRawY();
                    if (dy > screenH / 4)
                        openDrawer();
                    else
                        closeDrawer();
                    swipeStartY = -1;
                    isSwipingUp = false;
                    return true;
                }
                swipeStartY = -1;
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private TextView[] drawerLetterViews;
    private boolean[] drawerLetterHasApps;
    private int drawerLastSelectedIndex = -1;
    private TextView drawerLetterBubble;
    private LinearLayoutManager drawerLayoutManager;
    private View contextMenuOverlay;

    private void setupDrawer() {
        RecyclerView rv = appDrawerSheet.findViewById(R.id.rvApps);
        drawerLayoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(drawerLayoutManager);
        drawerAdapter = new AppSearchAdapter(this, allApps);
        drawerAdapter.setOnAppClickListener(app -> {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launchIntent);
            }
            hideKeyboard();
            closeDrawer();
        });
        drawerAdapter.setOnAppLongClickListener(this::showAppContextMenu);
        rv.setAdapter(drawerAdapter);

        EditText et = appDrawerSheet.findViewById(R.id.etSearchApps);
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterDrawer(s == null ? "" : s.toString());
            }
        });

        View close = appDrawerSheet.findViewById(R.id.ivSearchIcon);
        if (close != null) {
            close.setVisibility(View.VISIBLE);
            close.setOnClickListener(v -> closeDrawer());
        }

        drawerLetterBubble = appDrawerSheet.findViewById(R.id.tvLetterBubble);

        appDrawerSheet.setOnTouchListener(new View.OnTouchListener() {
            float startY, initY;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = ev.getRawY();
                        initY = v.getTranslationY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dy = ev.getRawY() - startY;
                        if (dy > 0)
                            v.setTranslationY(initY + dy);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (ev.getRawY() - startY > 150)
                            closeDrawer();
                        else
                            openDrawer();
                        break;
                }
                return true;
            }
        });

        reloadDrawerApps();
    }

    private void buildDrawerLetterSidebar() {
        android.widget.LinearLayout sidebar = appDrawerSheet.findViewById(R.id.letterSidebar);
        if (sidebar == null)
            return;
        sidebar.removeAllViews();
        drawerLetterViews = new TextView[LETTERS.length()];

        drawerLetterHasApps = new boolean[LETTERS.length()];
        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            for (AppSearchActivity.AppEntry app : allApps) {
                if (!app.label.trim().isEmpty() &&
                        Character.toUpperCase(app.label.charAt(0)) == letter) {
                    drawerLetterHasApps[i] = true;
                    break;
                }
            }
        }

        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(letter));
            tv.setTextSize(11f);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTag(letter);
            tv.setTextColor(drawerLetterHasApps[i] ? 0xFFCCCCCC : 0xFF444444);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            tv.setLayoutParams(lp);
            sidebar.addView(tv);
            drawerLetterViews[i] = tv;
        }

        sidebar.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float y = Math.max(0, Math.min(event.getY(), v.getHeight() - 1));
                int index = (int) ((y / v.getHeight()) * LETTERS.length());
                index = Math.max(0, Math.min(LETTERS.length() - 1, index));

                if (index != drawerLastSelectedIndex) {
                    if (drawerLastSelectedIndex >= 0)
                        animateDrawerLetterOut(drawerLastSelectedIndex);
                    drawerLastSelectedIndex = index;
                    animateDrawerLetterIn(index);
                }

                int pos = drawerAdapter.getPositionForLetter(LETTERS.charAt(index));
                if (pos >= 0)
                    drawerLayoutManager.scrollToPositionWithOffset(pos, 0);
                if (drawerLetterBubble != null) {
                    drawerLetterBubble.setText(String.valueOf(LETTERS.charAt(index)));
                    drawerLetterBubble.setVisibility(View.VISIBLE);
                }
                return true;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (drawerLastSelectedIndex >= 0) {
                    animateDrawerLetterOut(drawerLastSelectedIndex);
                    drawerLastSelectedIndex = -1;
                }
                if (drawerLetterBubble != null)
                    drawerLetterBubble.setVisibility(View.GONE);
                return true;
            }
            return false;
        });
    }

    private void animateDrawerLetterIn(int index) {
        if (drawerLetterViews == null || index < 0 || index >= drawerLetterViews.length)
            return;
        TextView tv = drawerLetterViews[index];
        tv.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0xFF555555);
        tv.setBackground(circle);
        tv.animate().scaleX(1.5f).scaleY(1.5f).setDuration(80).start();
    }

    private void animateDrawerLetterOut(int index) {
        if (drawerLetterViews == null || index < 0 || index >= drawerLetterViews.length)
            return;
        TextView tv = drawerLetterViews[index];
        tv.setTextColor(drawerLetterHasApps[index] ? 0xFFCCCCCC : 0xFF444444);
        tv.setBackground(null);
        tv.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
    }

    private void reloadDrawerApps() {
        new Thread(() -> {
            List<AppSearchActivity.AppEntry> loadedApps = loadApps(true);
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(loadedApps);
                if (drawerAdapter != null) {
                    EditText et = appDrawerSheet.findViewById(R.id.etSearchApps);
                    String q = et != null ? et.getText().toString() : "";
                    filterDrawer(q);
                }
                if (!drawerSidebarBuilt) {
                    buildDrawerLetterSidebar();
                    drawerSidebarBuilt = true;
                }
            });
        }).start();
    }

    private void filterDrawer(String q) {
        String lq = q.toLowerCase(Locale.getDefault()).trim();
        if (lq.isEmpty()) {
            drawerAdapter.updateItems(allApps);
            return;
        }
        List<AppSearchActivity.AppEntry> f = new ArrayList<>();
        for (AppSearchActivity.AppEntry e : allApps) {
            if (e.label.toLowerCase(Locale.getDefault()).contains(lq))
                f.add(e);
        }
        drawerAdapter.updateItems(f);
    }

    private void openDrawer() {
        drawerOpen = true;
        appDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appDrawerSheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .withEndAction(() -> appDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null))
                .start();
    }

    private void closeDrawer() {
        drawerOpen = false;
        dismissContextMenuOverlay();
        appDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appDrawerSheet.animate()
                .translationY(screenH)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    appDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null);
                    EditText et = appDrawerSheet.findViewById(R.id.etSearchApps);
                    if (et != null)
                        et.setText("");
                })
                .start();
        hideKeyboard();
    }

    private void showAppContextMenu(AppSearchActivity.AppEntry app) {
        showAppContextMenu(app, false);
    }

    private void showAppContextMenu(AppSearchActivity.AppEntry app, boolean vaultOnly) {
        dismissContextMenuOverlay();

        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissContextMenuOverlay());

        View menuView = getLayoutInflater().inflate(R.layout.dialog_app_context_menu, null);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
        lp.setMargins(40, 0, 40, 0);
        overlay.addView(menuView, lp);

        TextView tvTitle = menuView.findViewById(R.id.tvMenuTitle);
        tvTitle.setText(app.label);

        boolean inVault = VaultPrefs.getVaultedPackages(this).contains(app.packageName);
        TextView tvVaultAction = menuView.findViewById(R.id.tvVaultAction);
        tvVaultAction.setText(inVault ? "Remove from Vault" : "Add to Vault");
        int vaultToolsVisibility = inVault ? View.VISIBLE : View.GONE;
        menuView.findViewById(R.id.divVaultTop).setVisibility(View.VISIBLE);
        menuView.findViewById(R.id.divFrictionTop).setVisibility(vaultToolsVisibility);
        menuView.findViewById(R.id.btnChangeFriction).setVisibility(vaultToolsVisibility);
        menuView.findViewById(R.id.divLimitTop).setVisibility(vaultToolsVisibility);
        menuView.findViewById(R.id.btnAppLimit).setVisibility(vaultToolsVisibility);
        menuView.findViewById(R.id.divAppInfoTop).setVisibility(vaultOnly ? View.GONE : View.VISIBLE);
        menuView.findViewById(R.id.btnAppInfo).setVisibility(vaultOnly ? View.GONE : View.VISIBLE);

        menuView.findViewById(R.id.btnToggleVault).setOnClickListener(v -> {
            if (inVault)
                VaultPrefs.removeVaultedPackage(this, app.packageName);
            else
                VaultPrefs.addVaultedPackage(this, app.packageName);
            drawerSidebarBuilt = false;
            reloadDrawerApps();
            refreshVaultPage();
            bindMetrics();
            dismissContextMenuOverlay();
        });

        menuView.findViewById(R.id.btnAppInfo).setOnClickListener(v -> {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.packageName)));
            dismissContextMenuOverlay();
        });

        menuView.findViewById(R.id.btnChangeFriction).setOnClickListener(v -> {
            dismissContextMenuOverlay();
            showFrictionPicker(app);
        });

        menuView.findViewById(R.id.btnAppLimit).setOnClickListener(v -> {
            dismissContextMenuOverlay();
            showLimitPicker(app);
        });

        ViewGroup overlayParent = drawerOpen && appDrawerSheet instanceof ViewGroup
                ? (ViewGroup) appDrawerSheet
                : findViewById(android.R.id.content);
        overlayParent.addView(overlay,
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        contextMenuOverlay = overlay;

        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(200).start();
        menuView.setScaleX(0.9f);
        menuView.setScaleY(0.9f);
        menuView.setAlpha(0f);
        menuView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
    }

    private void dismissContextMenuOverlay() {
        if (contextMenuOverlay != null) {
            View overlay = contextMenuOverlay;
            contextMenuOverlay = null;
            overlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (overlay.getParent() != null) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
            }).start();
        }
    }

    // ── Vault ─────────────────────────────────────────────────────────────────

    private void setupVaultPage() {
        if (pageVault == null)
            return;

        cardVaultEmpty = pageVault.findViewById(R.id.cardVaultEmpty);
        vaultListContainer = pageVault.findViewById(R.id.vaultListContainer);
        vaultRecycler = pageVault.findViewById(R.id.rvVaultApps);

        if (vaultRecycler != null) {
            vaultLayoutManager = new LinearLayoutManager(this);
            vaultRecycler.setLayoutManager(vaultLayoutManager);
            vaultAdapter = new AppSearchAdapter(this, new ArrayList<>());
            vaultAdapter.setOnAppClickListener(app -> {
                Intent intent = new Intent(this, DialogFrictionGateActivity.class);
                intent.putExtra("packageName", app.packageName);
                intent.putExtra("appName", app.label);
                startActivity(intent);
            });
            vaultAdapter.setOnAppLongClickListener(this::showVaultAppOptions);
            vaultRecycler.setAdapter(vaultAdapter);
        }

        EditText search = pageVault.findViewById(R.id.etVaultSearchApps);
        if (search != null) {
            search.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    String q = s == null ? "" : s.toString();
                    isVaultSearchActive = !q.trim().isEmpty();
                    if (isVaultSearchActive && vaultLetterBubble != null) {
                        vaultLetterBubble.setVisibility(View.GONE);
                    }
                    filterVaultApps(q);
                }
            });
        }

        vaultLetterBubble = pageVault.findViewById(R.id.tvVaultLetterBubble);
        refreshVaultPage();
    }

    private void refreshVaultPage() {
        if (pageVault == null || vaultAdapter == null)
            return;

        vaultApps.clear();
        vaultApps.addAll(loadVaultedApps());

        boolean hasApps = !vaultApps.isEmpty();
        if (cardVaultEmpty != null)
            cardVaultEmpty.setVisibility(hasApps ? View.GONE : View.VISIBLE);
        if (vaultListContainer != null)
            vaultListContainer.setVisibility(hasApps ? View.VISIBLE : View.GONE);

        if (!hasApps)
            return;

        EditText search = pageVault.findViewById(R.id.etVaultSearchApps);
        String q = search == null ? "" : search.getText().toString();
        isVaultSearchActive = !q.trim().isEmpty();
        filterVaultApps(q);
        buildVaultLetterSidebar();
        bindMetrics();
    }

    private void filterVaultApps(String query) {
        if (vaultAdapter == null)
            return;
        String normalized = query == null ? "" : query.toLowerCase(Locale.getDefault()).trim();
        if (normalized.isEmpty()) {
            vaultAdapter.updateItems(vaultApps);
            return;
        }
        List<AppSearchActivity.AppEntry> filtered = new ArrayList<>();
        for (AppSearchActivity.AppEntry item : vaultApps) {
            if (item.label.toLowerCase(Locale.getDefault()).contains(normalized)) {
                filtered.add(item);
            }
        }
        vaultAdapter.updateItems(filtered);
    }

    private List<AppSearchActivity.AppEntry> loadVaultedApps() {
        Set<String> vaulted = VaultPrefs.getVaultedPackages(this);
        if (vaulted.isEmpty())
            return new ArrayList<>();

        PackageManager pm = getPackageManager();
        List<AppSearchActivity.AppEntry> items = new ArrayList<>();
        Set<String> cleaned = new HashSet<>();

        for (String pkg : vaulted) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence labelSeq = pm.getApplicationLabel(info);
                String label = labelSeq == null ? pkg : labelSeq.toString();
                items.add(new AppSearchActivity.AppEntry(label, "", pkg));
                cleaned.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        if (!cleaned.equals(vaulted))
            VaultPrefs.setVaultedPackages(this, cleaned);
        items.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return items;
    }

    private void showVaultAppOptions(AppSearchActivity.AppEntry app) {
        showAppContextMenu(app, true);
    }

    private void showFrictionPicker(AppSearchActivity.AppEntry app) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_friction_picker, null);
        RadioGroup rg = view.findViewById(R.id.rgFriction);
        int currentType = VaultPrefs.getFrictionType(this, app.packageName);
        if (currentType == VaultPrefs.FRICTION_PLUS_ONE)
            rg.check(R.id.rbPlusOne);
        else if (currentType == VaultPrefs.FRICTION_X2)
            rg.check(R.id.rb2x);
        else if (currentType == VaultPrefs.FRICTION_X3)
            rg.check(R.id.rb3x);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnOkay = view.findViewById(R.id.btnOkay);

        if (btnCancel != null)
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnOkay != null)
            btnOkay.setOnClickListener(v -> {
                int type = VaultPrefs.FRICTION_PLUS_ONE;
                int checkedId = rg.getCheckedRadioButtonId();
                if (checkedId == R.id.rb2x)
                    type = VaultPrefs.FRICTION_X2;
                else if (checkedId == R.id.rb3x)
                    type = VaultPrefs.FRICTION_X3;

                VaultPrefs.setFrictionType(this, app.packageName, type);
                Toast.makeText(this, "Friction set for " + app.label, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

        dialog.show();
    }

    private void showLimitPicker(AppSearchActivity.AppEntry app) {
        if (!VaultPrefs.canChangeLimitToday(this, app.packageName)) {
            new AlertDialog.Builder(this, R.style.DarkDialog)
                    .setTitle("Limit already set")
                    .setMessage("App limit can only be changed once a day. Please try again tomorrow.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_limit_picker, null);
        TimePicker tp = view.findViewById(R.id.timePicker);
        tp.setIs24HourView(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setHour(0);
            tp.setMinute(0);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialog)
                .setView(view)
                .create();

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnOkay = view.findViewById(R.id.btnOkay);

        if (btnCancel != null)
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnOkay != null)
            btnOkay.setOnClickListener(v -> {
                int h = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? tp.getHour() : tp.getCurrentHour();
                int m = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? tp.getMinute() : tp.getCurrentMinute();
                VaultPrefs.setAppLimitMinutes(this, app.packageName, h * 60 + m);
                Toast.makeText(this, "Limit set to " + h + "h " + m + "m", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

        dialog.show();
    }

    private void buildVaultLetterSidebar() {
        android.widget.LinearLayout sidebar = pageVault.findViewById(R.id.vaultLetterSidebar);
        if (sidebar == null)
            return;

        sidebar.removeAllViews();
        vaultLetterViews = new TextView[LETTERS.length()];
        vaultLetterHasApps = new boolean[LETTERS.length()];

        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            for (AppSearchActivity.AppEntry app : vaultApps) {
                if (!app.label.trim().isEmpty() &&
                        Character.toUpperCase(app.label.charAt(0)) == letter) {
                    vaultLetterHasApps[i] = true;
                    break;
                }
            }
        }

        for (int i = 0; i < LETTERS.length(); i++) {
            char letter = LETTERS.charAt(i);
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(letter));
            tv.setTextSize(11f);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTag(letter);
            tv.setTextColor(vaultLetterHasApps[i] ? 0xFFCCCCCC : 0xFF444444);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            tv.setLayoutParams(lp);
            sidebar.addView(tv);
            vaultLetterViews[i] = tv;
        }

        sidebar.setOnTouchListener((v, event) -> {
            if (isVaultSearchActive)
                return false;
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float y = Math.max(0, Math.min(event.getY(), v.getHeight() - 1));
                int index = (int) ((y / v.getHeight()) * LETTERS.length());
                index = Math.max(0, Math.min(LETTERS.length() - 1, index));

                if (index != vaultLastSelectedIndex) {
                    if (vaultLastSelectedIndex >= 0)
                        animateVaultLetterOut(vaultLastSelectedIndex);
                    vaultLastSelectedIndex = index;
                    animateVaultLetterIn(index);
                }

                scrollVaultToLetter(LETTERS.charAt(index));
                showVaultLetterBubble(LETTERS.charAt(index));
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (vaultLastSelectedIndex >= 0) {
                    animateVaultLetterOut(vaultLastSelectedIndex);
                    vaultLastSelectedIndex = -1;
                }
                hideVaultLetterBubble();
                return true;
            }
            return false;
        });
    }

    private void animateVaultLetterIn(int index) {
        if (vaultLetterViews == null || index < 0 || index >= vaultLetterViews.length)
            return;
        TextView tv = vaultLetterViews[index];
        tv.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0xFF555555);
        tv.setBackground(circle);
        tv.animate().scaleX(1.5f).scaleY(1.5f).setDuration(80).start();
    }

    private void animateVaultLetterOut(int index) {
        if (vaultLetterViews == null || index < 0 || index >= vaultLetterViews.length)
            return;
        TextView tv = vaultLetterViews[index];
        tv.setTextColor(vaultLetterHasApps[index] ? 0xFFCCCCCC : 0xFF444444);
        tv.setBackground(null);
        tv.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
    }

    private void scrollVaultToLetter(char letter) {
        if (vaultAdapter == null || vaultLayoutManager == null)
            return;
        int pos = vaultAdapter.getPositionForLetter(letter);
        if (pos >= 0)
            vaultLayoutManager.scrollToPositionWithOffset(pos, 0);
    }

    private void showVaultLetterBubble(char letter) {
        if (vaultLetterBubble == null)
            return;
        vaultLetterBubble.setText(String.valueOf(letter));
        vaultLetterBubble.setVisibility(View.VISIBLE);
    }

    private void hideVaultLetterBubble() {
        if (vaultLetterBubble != null)
            vaultLetterBubble.setVisibility(View.GONE);
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private void setupSettingsPage() {
        updateSettingsSummaries();

        View rowTheme = findViewById(R.id.rowTheme);
        if (rowTheme != null)
            rowTheme.setOnClickListener(v -> showThemePicker());

        View rowFontSize = findViewById(R.id.rowFontSize);
        if (rowFontSize != null)
            rowFontSize.setOnClickListener(v -> showFontSizePicker());

        View rowFont = findViewById(R.id.rowFont);
        if (rowFont != null)
            rowFont.setOnClickListener(v -> showFontPicker());

        View rowLinkDevice = findViewById(R.id.rowLinkDevice);
        if (rowLinkDevice != null) {
            rowLinkDevice.setOnClickListener(v -> {
                if (!NetworkUtils.isOnline(this)) {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(this, ChildQrActivity.class));
            });
        }

        View rowLogout = findViewById(R.id.rowLogout);
        if (rowLogout != null)
            rowLogout.setOnClickListener(v -> showLogoutDialog());

        LauncherUiPrefs.applyTypography(findViewById(R.id.main), this);
    }

    private void showThemePicker() {
        final String[] labels = new String[] { "System default", "Light", "Dark" };
        final String[] values = new String[] {
                LauncherUiPrefs.THEME_SYSTEM,
                LauncherUiPrefs.THEME_LIGHT,
                LauncherUiPrefs.THEME_DARK
        };
        String current = LauncherUiPrefs.getTheme(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Color theme")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LauncherUiPrefs.setTheme(this, values[which]);
                    LauncherUiPrefs.applyTheme(this);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFontSizePicker() {
        final String[] labels = new String[] { "Small", "Medium", "Large" };
        final String[] values = new String[] {
                LauncherUiPrefs.FONT_SIZE_SMALL,
                LauncherUiPrefs.FONT_SIZE_MEDIUM,
                LauncherUiPrefs.FONT_SIZE_LARGE
        };
        String current = LauncherUiPrefs.getFontSize(this);
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Font size")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LauncherUiPrefs.setFontSize(this, values[which]);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFontPicker() {
        final String[] labels = new String[] {
                "Default",
                "Monospace",
                "Goldman style",
                "OpenDyslexic style",
                "Ndot57 style"
        };
        final String[] values = new String[] {
                LauncherUiPrefs.FONT_DEFAULT,
                LauncherUiPrefs.FONT_MONOSPACE,
                LauncherUiPrefs.FONT_GOLDMAN,
                LauncherUiPrefs.FONT_OPENDYSLEXIC,
                LauncherUiPrefs.FONT_NDOT57
        };
        String current = LauncherUiPrefs.getFont(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Font")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LauncherUiPrefs.setFont(this, values[which]);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSettingsSummaries() {
        TextView tvTheme = findViewById(R.id.tvThemeValue);
        TextView tvFontSize = findViewById(R.id.tvFontSizeValue);
        TextView tvFont = findViewById(R.id.tvFontValue);

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

        if (tvFont != null) {
            String font = LauncherUiPrefs.getFont(this);
            if (LauncherUiPrefs.FONT_MONOSPACE.equals(font)) {
                tvFont.setText("Monospace");
            } else if (LauncherUiPrefs.FONT_GOLDMAN.equals(font)) {
                tvFont.setText("Goldman style");
            } else if (LauncherUiPrefs.FONT_OPENDYSLEXIC.equals(font)) {
                tvFont.setText("OpenDyslexic style");
            } else if (LauncherUiPrefs.FONT_NDOT57.equals(font)) {
                tvFont.setText("Ndot57 style");
            } else {
                tvFont.setText("Default");
            }
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Log out")
                .setMessage(
                        "After logging out, switch the default home app back to your device launcher in system Home settings. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Accept", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    SessionPrefs.setChildAuthComplete(this, false);
                    SessionPrefs.setPersonalPermissionsComplete(this, false);

                    Intent roleSelection = new Intent(this, OnboardingActivity.class);
                    roleSelection.putExtra("forceRoleSelection", true);
                    startActivity(roleSelection);
                    finishAffinity();
                })
                .show();
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        if (navIconPadding == 0 && navHome != null) {
            navIconPadding = navHome.getPaddingLeft();
        }
        navHome.setOnClickListener(v -> navClick(PAGE_HOME));
        navVault.setOnClickListener(v -> navClick(PAGE_VAULT));
        navSettings.setOnClickListener(v -> navClick(PAGE_SETTINGS));
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
        navHome.setVisibility(navExpanded || currentPage == PAGE_HOME ? View.VISIBLE : View.GONE);
        navVault.setVisibility(navExpanded || currentPage == PAGE_VAULT ? View.VISIBLE : View.GONE);
        navSettings.setVisibility(navExpanded || currentPage == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        alignNavIconMargins();
    }

    private void alignNavIconMargins() {
        boolean seenVisible = false;
        ImageView[] icons = new ImageView[] { navHome, navVault, navSettings };
        for (ImageView icon : icons) {
            if (icon == null)
                continue;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
            lp.setMarginStart(seenVisible ? dpToPx(16) : 0);
            icon.setLayoutParams(lp);
            if (icon.getVisibility() == View.VISIBLE) {
                seenVisible = true;
            }
        }
    }

    // ── Pages ─────────────────────────────────────────────────────────────────

    private void showPage(int p) {
        currentPage = p;
        pageHome.setVisibility(p == PAGE_HOME ? View.VISIBLE : View.GONE);
        pageVault.setVisibility(p == PAGE_VAULT ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(p == PAGE_SETTINGS ? View.VISIBLE : View.GONE);

        applyNavBackground(navHome, p == PAGE_HOME);
        applyNavBackground(navVault, p == PAGE_VAULT);
        applyNavBackground(navSettings, p == PAGE_SETTINGS);
        updateNavVis();
    }

    private void applyNavBackground(ImageView view, boolean active) {
        if (view == null)
            return;
        int padding = navIconPadding > 0 ? navIconPadding : dpToPx(12);
        view.setBackgroundResource(active ? R.drawable.bg_nav_active : 0);
        view.setPadding(padding, padding, padding, padding);
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    private void startClockUpdates() {
        bindDateTime();
        scheduleNextClockTick();
    }

    private void scheduleNextClockTick() {
        clock.removeCallbacks(tick);
        long now = System.currentTimeMillis();
        long delay = 60_000L - (now % 60_000L);
        if (delay < 1_000L)
            delay += 60_000L;
        clock.postDelayed(tick, delay);
    }

    private void bindDateTime() {
        Date now = new Date();
        if (tvTime != null)
            tvTime.setText(timeFmt.format(now));
        if (tvDate != null)
            tvDate.setText(dateFmt.format(now));
    }

    private void bindHeadline() {
        TextView tv = tvHeadline != null ? tvHeadline : findViewById(R.id.tvHeadline);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String name = u == null ? null : u.getDisplayName();
        tv.setText((name == null || name.trim().isEmpty()) ? "Stay Focused." : name.trim() + ", stay focused.");
    }

    private void bindMetrics() {
        if (metricsLoading)
            return;
        metricsLoading = true;
        new Thread(() -> {
            TelemetrySnapshot local = repo.collectLocalSnapshot(this);
            runOnUiThread(() -> {
                metricsLoading = false;
                renderMetrics(local);
            });
        }).start();
    }

    private void renderMetrics(TelemetrySnapshot s) {
        if (s == null)
            return;
        snap = s;
        TextView st = findViewById(R.id.tvScreenTime);
        TextView un = findViewById(R.id.tvUnlockCount);
        TextView fr = findViewById(R.id.tvFrictionCount);
        TextView va = findViewById(R.id.tvVaultedCount);
        BarChartView ch = findViewById(R.id.barChart);

        st.setText(
                s.dailyUsageMinutes.length > 0 ? fmtMin(s.dailyUsageMinutes[s.dailyUsageMinutes.length - 1]) : "0h 0m");
        un.setText(String.valueOf(s.unlockCount));
        fr.setText(String.valueOf(s.frictionCount));
        va.setText(String.valueOf(s.vaultedCount));
        ch.setSamples(norm(s.dailyUsageMinutes));

        ch.setOnBarTouchListener(new BarChartView.OnBarTouchListener() {
            @Override
            public void onBarTouch(int i, float v) {
                if (snap != null && i >= 0 && i < snap.dailyUsageMinutes.length) {
                    st.setText(fmtMin(snap.dailyUsageMinutes[i]));
                }
            }

            @Override
            public void onBarRelease() {
                if (snap != null && snap.dailyUsageMinutes.length > 0) {
                    st.setText(fmtMin(snap.dailyUsageMinutes[snap.dailyUsageMinutes.length - 1]));
                }
            }
        });

        View stats = cardStats != null ? cardStats : findViewById(R.id.cardStats);
        stats.setOnClickListener(v -> startActivity(new Intent(this, ScreenTimeActivity.class)));
        View reload = findViewById(R.id.btnReloadStats);
        if (reload != null) {
            reload.setOnClickListener(v -> {
                metricsLoading = false;
                bindMetrics();
                v.animate().rotationBy(360f).setDuration(350).start();
            });
        }
    }

    private void startMetricsUpdates() {
        clock.removeCallbacks(metricsTick);
        bindMetrics();
        clock.postDelayed(metricsTick, 5_000L);
    }

    private void setupPageActions() {
        View openVault = findViewById(R.id.btnOpenVaultFromSwipe);
        if (openVault != null) {
            openVault.setOnClickListener(v -> showPage(PAGE_VAULT));
        }
        View openSettings = findViewById(R.id.btnOpenSettingsFromSwipe);
        if (openSettings != null) {
            openSettings.setOnClickListener(v -> showPage(PAGE_SETTINGS));
        }
    }

    private void setupHomeShortcuts() {
        if (tvTime != null)
            tvTime.setClickable(false);
        if (tvDate != null) {
            tvDate.setOnClickListener(v -> openCalendarApp());
            tvDate.setClickable(true);
        }
        if (btnPhone != null)
            btnPhone.setOnClickListener(v -> openPhoneApp());
        if (btnCamera != null)
            btnCamera.setOnClickListener(v -> openCameraApp());
    }

    private void openCalendarApp() {
        launchBestEffortIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
                new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI));
    }

    private void openPhoneApp() {
        launchBestEffortIntent(new Intent(Intent.ACTION_DIAL));
    }

    private void openCameraApp() {
        launchBestEffortIntent(
                new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CAMERA"));
    }

    private void launchBestEffortIntent(Intent... intents) {
        PackageManager pm = getPackageManager();
        for (Intent intent : intents) {
            if (intent == null)
                continue;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(pm) != null) {
                try {
                    startActivity(intent);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        Toast.makeText(this, "No app found", Toast.LENGTH_SHORT).show();
    }

    private void ensureDefaultLauncher() {
        if (askedHomeRole)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                askedHomeRole = true;
                startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME));
            }
            return;
        }
        Intent settings = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (settings.resolveActivity(getPackageManager()) != null) {
            askedHomeRole = true;
            startActivity(settings);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AppSearchActivity.AppEntry> loadApps(boolean excludeVaulted) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ri = getPackageManager().queryIntentActivities(i, 0);
        Set<String> vault = excludeVaulted ? VaultPrefs.getVaultedPackages(this) : new HashSet<>();
        String self = getPackageName();
        List<AppSearchActivity.AppEntry> list = new ArrayList<>();
        for (ResolveInfo r : ri) {
            if (r.activityInfo == null)
                continue;
            String pkg = r.activityInfo.packageName;
            if (self.equals(pkg) || vault.contains(pkg))
                continue;
            CharSequence lbl = r.loadLabel(getPackageManager());
            list.add(new AppSearchActivity.AppEntry(lbl == null ? pkg : lbl.toString(), "", pkg));
        }
        list.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(
                    INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String fmtMin(long m) {
        return (m / 60) + "h " + (m % 60) + "m";
    }

    private float[] norm(long[] v) {
        if (v == null || v.length == 0)
            return new float[] { .2f, .2f, .2f, .2f, .2f, .2f, .2f };
        long mx = 1;
        for (long x : v)
            if (x > mx)
                mx = x;
        float[] o = new float[v.length];
        for (int i = 0; i < v.length; i++)
            o[i] = Math.max(.1f, Math.min(1f, (float) v[i] / mx));
        return o;
    }
}
