package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

public class PersonalFeatureTourActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button backButton;
    private Button nextButton;
    private TextView counterText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_feature_tour);

        View main = findViewById(R.id.main);
        final int basePaddingLeft = main.getPaddingLeft();
        final int basePaddingTop = main.getPaddingTop();
        final int basePaddingRight = main.getPaddingRight();
        final int basePaddingBottom = main.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    basePaddingLeft + systemBars.left,
                    basePaddingTop + systemBars.top,
                    basePaddingRight + systemBars.right,
                    basePaddingBottom + systemBars.bottom
            );
            return insets;
        });

        viewPager = findViewById(R.id.vpFeatures);
        backButton = findViewById(R.id.btnBack);
        nextButton = findViewById(R.id.btnNext);
        counterText = findViewById(R.id.tvPagerCounter);

        List<FeaturePage> pages = Arrays.asList(
                new FeaturePage(
                        R.drawable.ic_lock,
                        "App Vault with friction",
                        "Place distracting apps behind intentional friction so unlocking becomes a conscious choice."
                ),
                new FeaturePage(
                        R.drawable.ic_policy,
                        "App locking after time",
                        "Set app usage limits. When time is up, PureLauncher locks access to help you reset focus."
                ),
                new FeaturePage(
                        R.drawable.ic_home,
                        "Minimalist interface",
                        "A clean, low-noise home screen keeps your attention on what matters and reduces triggers."
                ),
                new FeaturePage(
                        R.drawable.ic_chart_bar,
                        "More freedom, less screen time",
                        "Track usage trends and reclaim attention with small daily wins that build long-term habits."
                )
        );

        viewPager.setAdapter(new PersonalFeatureAdapter(pages));
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                renderControls(position, pages.size());
            }
        });

        backButton.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current == 0) {
                finish();
                return;
            }
            viewPager.setCurrentItem(current - 1, true);
        });

        nextButton.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < pages.size() - 1) {
                viewPager.setCurrentItem(current + 1, true);
                return;
            }
            SessionPrefs.setPersonalTourComplete(this, true);
            Intent intent = new Intent(this, PersonalPermissionsActivity.class);
            startActivity(intent);
        });

        renderControls(0, pages.size());
    }

    private void renderControls(int position, int total) {
        counterText.setText((position + 1) + " / " + total);
        backButton.setEnabled(position > 0);
        backButton.setAlpha(position > 0 ? 1f : 0.55f);
        nextButton.setText(position == total - 1 ? "Continue" : "Next");
    }

    private static final class FeaturePage {
        final int iconRes;
        final String title;
        final String subtitle;

        FeaturePage(int iconRes, String title, String subtitle) {
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final class PersonalFeatureAdapter
            extends RecyclerView.Adapter<PersonalFeatureAdapter.FeatureViewHolder> {

        private final List<FeaturePage> pages;

        PersonalFeatureAdapter(List<FeaturePage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public FeatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_personal_feature_page, parent, false);
            return new FeatureViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FeatureViewHolder holder, int position) {
            FeaturePage page = pages.get(position);
            holder.icon.setImageResource(page.iconRes);
            holder.title.setText(page.title);
            holder.subtitle.setText(page.subtitle);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static final class FeatureViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;

            FeatureViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.ivFeatureIcon);
                title = itemView.findViewById(R.id.tvFeatureTitle);
                subtitle = itemView.findViewById(R.id.tvFeatureSubtitle);
            }
        }
    }
}
