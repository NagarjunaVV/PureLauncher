package com.example.purelauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class FrequentAppsAdapter extends RecyclerView.Adapter<FrequentAppsAdapter.AppViewHolder> {

    private final Context context;
    private final List<AppEntry> apps;

    FrequentAppsAdapter(Context context, List<AppEntry> apps) {
        this.context = context;
        this.apps = apps;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_row, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppEntry app = apps.get(position);
        holder.name.setText(app.label);
        holder.category.setText(app.category);
        holder.clickInfo.setText("Tap to open");

        Drawable icon;
        try {
            icon = context.getPackageManager().getApplicationIcon(app.packageName);
        } catch (Exception ignored) {
            icon = context.getDrawable(R.drawable.ic_placeholder_app);
        }

        holder.icon.setImageDrawable(icon);
        holder.itemView.setOnClickListener(v -> {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                context.startActivity(launchIntent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

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

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView category;
        final TextView clickInfo;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivAppIcon);
            name = itemView.findViewById(R.id.tvAppName);
            category = itemView.findViewById(R.id.tvCategory);
            clickInfo = itemView.findViewById(R.id.tvClickInfo);
        }
    }
}
