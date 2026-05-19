package com.example.purelauncher;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.purelauncher.ui.views.BarChartView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParentScreenTimeActivity extends AppCompatActivity {

    private static final DateTimeFormatter NAV_FMT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());

    private final UserProfileStore profileStore = new UserProfileStore();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    private LocalDate selectedDate = LocalDate.now();
    private String childUid;
    private long[] chartValues;

    private TextView tvTotal;
    private TextView tvSubtitle;
    private TextView tvDateLabel;
    private View btnPrev;
    private View btnNext;
    private BarChartView chart;
    private RecyclerView rvApps;
    private View pbLoading;
    private AppListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        loadChildUid();
    }

    private void bindViews() {
        tvTotal = findViewById(R.id.tvTotal);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvDateLabel = findViewById(R.id.tvDateLabel);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        chart = findViewById(R.id.barChart);
        rvApps = findViewById(R.id.rvApps);
        pbLoading = findViewById(R.id.pbLoading);

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(new ArrayList<>());
        rvApps.setAdapter(adapter);
    }
//Binds the action
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

    private LocalDate minDate() {
        return LocalDate.now().minusDays(6);
    }

    private void loadChildUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        profileStore.getLinkedChildUid(user).addOnSuccessListener(uid -> {
            if (uid == null || uid.trim().isEmpty()) {
                Toast.makeText(this, "No child linked.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            childUid = uid.trim();
            load();
        });
    }

    private void load() {
        updateNavUi();
        pbLoading.setVisibility(View.VISIBLE);
        rvApps.setVisibility(View.INVISIBLE);
        if (childUid == null) {
            return;
        }
        if (chartValues == null) {
            firestore.collection("child_metrics")
                    .document(childUid)
                    .get()
                    .addOnSuccessListener(this::applyMetricsSnapshot)
                    .addOnFailureListener(error -> applyMetricsSnapshot(null));
        } else {
            loadDayUsage();
        }
    }

    private void applyMetricsSnapshot(DocumentSnapshot snapshot) {
        if (snapshot != null && snapshot.exists()) {
            @SuppressWarnings("unchecked")
            List<Long> daily = (List<Long>) snapshot.get("dailyUsageMinutes");
            chartValues = toLongArray(daily, 7);
        } else if (chartValues == null) {
            chartValues = new long[7];
        }
        renderChart();
        loadDayUsage();
    }

    private void loadDayUsage() {
        String dateKey = selectedDate.toString();
        firestore.collection("child_usage")
                .document(childUid)
                .collection("days")
                .document(dateKey)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AppListAdapter.Row> rows = new ArrayList<>();
                    long totalMinutes = 0L;
                    if (snapshot != null && snapshot.exists()) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> apps = (List<Map<String, Object>>) snapshot.get("apps");
                        if (apps != null) {
                            for (Map<String, Object> app : apps) {
                                String name = valueString(app.get("name"));
                                Long minutes = valueLong(app.get("minutes"));
                                if (name == null || name.trim().isEmpty()) {
                                    name = valueString(app.get("packageName"));
                                }
                                long mins = minutes == null ? 0L : minutes;
                                totalMinutes += mins;
                                rows.add(new AppListAdapter.Row(name, fmtMinutes(mins), mins));
                            }
                        }
                    }
                    rows.sort((a, b) -> Long.compare(b.minutesValue, a.minutesValue));
                    renderTotalFromSelection(totalMinutes);
                    adapter.setRows(rows);
                    pbLoading.setVisibility(View.GONE);
                    rvApps.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(error -> {
                    renderTotalFromSelection(0L);
                    adapter.setRows(new ArrayList<>());
                    pbLoading.setVisibility(View.GONE);
                    rvApps.setVisibility(View.VISIBLE);
                });
    }

    private void renderChart() {
        if (chart == null || chartValues == null) {
            return;
        }
        chart.setSamples(normalize(chartValues));
        int index = indexForDate(selectedDate);
        chart.setHighlightedBar(index);
        chart.setOnBarTouchListener(new BarChartView.OnBarTouchListener() {
            @Override
            public void onBarTouch(int i, float v) {
                renderTotal(i);
            }

            @Override
            public void onBarRelease() {
                renderTotal(index);
            }
        });
    }

    private void renderTotal(int barIdx) {
        if (chartValues == null || barIdx < 0 || barIdx >= chartValues.length) {
            return;
        }
        long val = chartValues[barIdx];
        LocalDate barDate = LocalDate.now().minusDays(6 - barIdx);
        tvTotal.setText(fmtMinutes(val));
        tvSubtitle.setText(dateLabel(barDate));
    }

    private void renderTotalFromSelection(long fallbackMinutes) {
        int index = indexForDate(selectedDate);
        long val = fallbackMinutes;
        if (chartValues != null && index >= 0 && index < chartValues.length) {
            val = chartValues[index];
        }
        tvTotal.setText(fmtMinutes(val));
        tvSubtitle.setText(dateLabel(selectedDate));
    }

    private void updateNavUi() {
        tvDateLabel.setText(dateLabel(selectedDate));
        btnPrev.setAlpha(selectedDate.isAfter(minDate()) ? 1f : 0.3f);
        btnNext.setAlpha(selectedDate.isBefore(LocalDate.now()) ? 1f : 0.3f);
    }

    private int indexForDate(LocalDate date) {
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now());
        return 6 - offset;
    }

    private String dateLabel(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today))
            return "Today";
        if (d.equals(today.minusDays(1)))
            return "Yesterday";
        return d.format(NAV_FMT);
    }

    private String fmtMinutes(long mins) {
        long h = mins / 60;
        long m = mins % 60;
        return h == 0 ? m + "m" : h + "h " + m + "m";
    }

    private float[] normalize(long[] vals) {
        if (vals == null || vals.length == 0)
            return new float[] { 0.05f };
        long max = 1;
        for (long v : vals)
            if (v > max)
                max = v;
        float[] out = new float[vals.length];
        for (int i = 0; i < vals.length; i++) {
            out[i] = Math.max(0.05f, Math.min(1f, (float) vals[i] / max));
        }
        return out;
    }

    private long[] toLongArray(List<Long> values, int size) {
        long[] out = new long[size];
        if (values == null) {
            return out;
        }
        for (int i = 0; i < size && i < values.size(); i++) {
            Long v = values.get(i);
            out[i] = v == null ? 0L : v;
        }
        return out;
    }

    private String valueString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long valueLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.VH> {

        private final List<Row> rows;

        AppListAdapter(List<Row> rows) {
            this.rows = rows;
        }

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Row r = rows.get(pos);
            h.icon.setVisibility(View.GONE);
            h.name.setText(r.appName);
            h.metric.setText(r.metric);
            h.metric.setVisibility(View.VISIBLE);
            if (h.sub != null) {
                h.sub.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class Row {
            final String appName;
            final String metric;
            final long minutesValue;

            Row(String appName, String metric, long minutesValue) {
                this.appName = appName;
                this.metric = metric;
                this.minutesValue = minutesValue;
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView metric;
            TextView sub;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.ivAppIcon);
                name = v.findViewById(R.id.tvAppName);
                metric = v.findViewById(R.id.tvAppTime);
                sub = v.findViewById(R.id.tvAppSubtitle);
            }
        }
    }
}
