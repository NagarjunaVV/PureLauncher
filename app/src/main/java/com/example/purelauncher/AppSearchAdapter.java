package com.example.purelauncher;

import android.content.Context;
import android.content.ClipData;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class AppSearchAdapter extends RecyclerView.Adapter<AppSearchAdapter.AppViewHolder> {

    /** Called when the user clicks an app row. */
    interface OnAppClickListener {
        void onAppClick(AppSearchActivity.AppEntry app);
    }

    /** Called when the user long-presses an app row. */
    interface OnAppLongClickListener {
        void onAppLongClick(AppSearchActivity.AppEntry app);
    }

    private final Context context;
    private final List<AppSearchActivity.AppEntry> apps;
    private OnAppClickListener clickListener;
    private OnAppLongClickListener longClickListener;

    AppSearchAdapter(Context context, List<AppSearchActivity.AppEntry> apps) {
        this.context = context;
        this.apps = new ArrayList<>(apps);
    }

    void setOnAppClickListener(OnAppClickListener l) {
        this.clickListener = l;
    }

    void setOnAppLongClickListener(OnAppLongClickListener l) {
        this.longClickListener = l;
    }

    void updateItems(List<AppSearchActivity.AppEntry> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    /** Returns the position of the first app whose label starts with the given letter. */
    int getPositionForLetter(char letter) {
        String letterStr = String.valueOf(letter).toUpperCase();
        for (int i = 0; i < apps.size(); i++) {
            String label = apps.get(i).label.trim().toUpperCase();
            if (!label.isEmpty() && label.substring(0, 1).compareTo(letterStr) >= 0) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_row_text, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppSearchActivity.AppEntry app = apps.get(position);
        holder.name.setText(app.label);
        
        if (holder.icon != null) {
            holder.icon.setTag(app.packageName);
            holder.icon.setImageDrawable(null);
            AppIconCache.loadIconAsync(context, app.packageName, icon -> {
                holder.icon.post(() -> {
                    if (app.packageName.equals(holder.icon.getTag())) {
                        holder.icon.setImageDrawable(icon);
                    }
                });
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAppClick(app);
            } else {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) context.startActivity(launchIntent);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onAppLongClick(app);
            return true;
        });

        if (holder.dragHandle != null) {
            holder.dragHandle.setOnLongClickListener(v -> {
                ClipData clip = ClipData.newPlainText("package", app.packageName);
                ViewCompat.startDragAndDrop(v, clip, new View.DragShadowBuilder(v), app.packageName, 0);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() { return apps.size(); }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        public final TextView name;
        public final TextView dragHandle;
        public final ImageView icon;
        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvAppName);
            dragHandle = itemView.findViewById(R.id.tvDragHandle);
            icon = itemView.findViewById(R.id.ivAppIcon);
        }
    }
}
