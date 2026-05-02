package com.example.purelauncher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScreenTimeActivity extends AppCompatActivity {

    private static final DateTimeFormatter NAV_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());

    /** Oldest allowed selected date = today - 6 (we only track 7 days). */
    private LocalDate minDate() { return LocalDate.now().minusDays(6); }

    // ── Data ──────────────────────────────────────────────────────────────────
    private final TelemetryRepository repo    = new TelemetryRepository();
    private final Handler             uiPost  = new Handler(Looper.getMainLooper());

    private LocalDate selectedDate = LocalDate.now();

    // Cached per-load results
    private long[] chartValues = null;
    private List<TelemetryRepository.AppUsageEntry> screenList = new ArrayList<>();

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView tvTotal, tvSubtitle, tvDateLabel;
    private View btnPrev, btnNext;
    private com.purelauncher.ui.views.BarChartView chart;
    private RecyclerView rvApps;
    private View pbLoading;
    private AppListAdapter adapter;

    // ─────────────────────────────────────────────────────────────────────────
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_screen_time);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, ins) -> {
            Insets sb = ins.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return ins;
        });
        bindViews();
        bindActions();
        load();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        tvTotal     = findViewById(R.id.tvTotal);
        tvSubtitle  = findViewById(R.id.tvSubtitle);
        tvDateLabel = findViewById(R.id.tvDateLabel);
        btnPrev     = findViewById(R.id.btnPrev);
        btnNext     = findViewById(R.id.btnNext);
        chart       = findViewById(R.id.barChart);
        rvApps      = findViewById(R.id.rvApps);
        pbLoading   = findViewById(R.id.pbLoading);

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(this, new ArrayList<>());
        rvApps.setAdapter(adapter);
    }

    private void bindActions() {
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        btnPrev.setOnClickListener(v -> {
            if (selectedDate.isAfter(minDate())) {
                selectedDate = selectedDate.minusDays(1);
                load();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (selectedDate.isBefore(LocalDate.now())) {
                selectedDate = selectedDate.plusDays(1);
                load();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading (background thread)
    // ─────────────────────────────────────────────────────────────────────────

    private void load() {
        updateNavUi();
        pbLoading.setVisibility(View.VISIBLE);
        rvApps.setVisibility(View.INVISIBLE);
        new Thread(() -> {
            if (chartValues == null) {
                chartValues = repo.getDailyUsageForWeekEndingAt(this, LocalDate.now());
            }
            List<TelemetryRepository.AppUsageEntry> stList = repo.getAppUsageForDate(this, selectedDate);

            PackageManager pm = getPackageManager();
            List<AppListAdapter.Row> bgRows = new ArrayList<>();
            for (TelemetryRepository.AppUsageEntry e : stList) {
                bgRows.add(new AppListAdapter.Row(AppIconCache.getIcon(ScreenTimeActivity.this, e.packageName),
                        label(pm, e.packageName), fmtMinutes(e.minutes), null));
            }

            uiPost.post(() -> {
                screenList  = stList;
                renderAll(bgRows);
                pbLoading.setVisibility(View.GONE);
                rvApps.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private void renderAll(List<AppListAdapter.Row> rows) {
        renderChart();
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(selectedDate, LocalDate.now());
        int index = 6 - offset;
        renderTotal(index);
        adapter.setRows(rows);
    }

    private void renderChart() {
        chart.setSamples(normalize(chartValues));
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(selectedDate, LocalDate.now());
        int index = 6 - offset;
        chart.setHighlightedBar(index);
        chart.setOnBarTouchListener(new com.purelauncher.ui.views.BarChartView.OnBarTouchListener() {
            @Override public void onBarTouch(int i, float v) { renderTotal(i); }
            @Override public void onBarRelease()              { renderTotal(index); }
        });
    }

    private void renderTotal(int barIdx) {
        long val = (chartValues != null && barIdx >= 0 && barIdx < chartValues.length) ? chartValues[barIdx] : 0;
        LocalDate barDate = LocalDate.now().minusDays(6 - barIdx);
        tvTotal.setText(fmtMinutes(val));
        tvSubtitle.setText(dateLabel(barDate));
    }

    // List rendering now done using pre-fetched rows in renderAll()

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updateNavUi() {
        tvDateLabel.setText(dateLabel(selectedDate));
        btnPrev.setAlpha(selectedDate.isAfter(minDate()) ? 1f : 0.3f);
        btnNext.setAlpha(selectedDate.isBefore(LocalDate.now()) ? 1f : 0.3f);
    }

    private String dateLabel(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today))                return "Today";
        if (d.equals(today.minusDays(1)))   return "Yesterday";
        return d.format(NAV_FMT);
    }

    private String fmtMinutes(long mins) {
        long h = mins / 60, m = mins % 60;
        return h == 0 ? m + "m" : h + "h " + m + "m";
    }

    private float[] normalize(long[] vals) {
        if (vals == null || vals.length == 0) return new float[]{0.05f};
        long max = 1;
        for (long v : vals) if (v > max) max = v;
        float[] out = new float[vals.length];
        for (int i = 0; i < vals.length; i++)
            out[i] = Math.max(0.05f, Math.min(1f, (float) vals[i] / max));
        return out;
    }

    private String label(PackageManager pm, String pkg) {
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────────

    static class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.VH> {

        static final class Row {
            final Drawable icon;
            final String   appName;
            final String   metric;    // non-null for screen time (e.g. "2h 14m")
            final String   subtitle;  // non-null for notif / times-opened

            Row(Drawable icon, String appName, String metric, String subtitle) {
                this.icon = icon; this.appName = appName;
                this.metric = metric; this.subtitle = subtitle;
            }
        }

        private final Context   ctx;
        private final List<Row> rows;

        AppListAdapter(Context ctx, List<Row> rows) { this.ctx = ctx; this.rows = rows; }

        void setRows(List<Row> r) { rows.clear(); rows.addAll(r); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_app_usage, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Row r = rows.get(pos);
            h.icon.setImageDrawable(r.icon);
            h.name.setText(r.appName);
            if (r.metric != null) {
                h.metric.setText(r.metric);
                h.metric.setVisibility(View.VISIBLE);
                if (h.sub != null) h.sub.setVisibility(View.GONE);
            } else {
                h.metric.setVisibility(View.GONE);
                if (h.sub != null) { h.sub.setText(r.subtitle); h.sub.setVisibility(View.VISIBLE); }
            }
        }

        @Override public int getItemCount() { return rows.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView name, metric, sub;
            VH(View v) {
                super(v);
                icon   = v.findViewById(R.id.ivAppIcon);
                name   = v.findViewById(R.id.tvAppName);
                metric = v.findViewById(R.id.tvAppTime);
                sub    = v.findViewById(R.id.tvAppSubtitle);
            }
        }
    }
}
