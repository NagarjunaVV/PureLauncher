package com.example.purelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class WidgetsAdapter extends RecyclerView.Adapter<WidgetsAdapter.VH> {

    interface Callback {
        void onAddClicked();
        void onAppLongPressed(String packageName);
    }

    private static final int TYPE_APP = 0;
    private static final int TYPE_ADD = 1;

    private final Context ctx;
    private final List<String> packages;
    private final Callback cb;

    WidgetsAdapter(Context ctx, List<String> packages, Callback cb) {
        this.ctx = ctx;
        this.packages = new ArrayList<>(packages);
        this.cb = cb;
    }

    void setPackages(List<String> updated) {
        packages.clear();
        packages.addAll(updated);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int pos) {
        return pos < packages.size() ? TYPE_APP : TYPE_ADD;
    }

    @Override public int getItemCount() { return packages.size() + 1; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_APP ? R.layout.item_widget_tile : R.layout.item_widget_add;
        return new VH(LayoutInflater.from(ctx).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        if (getItemViewType(pos) == TYPE_ADD) {
            h.itemView.setOnClickListener(v -> cb.onAddClicked());
            return;
        }
        String pkg = packages.get(pos);
        PackageManager pm = ctx.getPackageManager();
        try {
            h.name.setText(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            Drawable icon = pm.getApplicationIcon(pkg);
            h.icon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            h.name.setText(pkg);
            h.icon.setImageResource(R.drawable.ic_placeholder_app);
        }
        h.itemView.setOnClickListener(v -> {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) ctx.startActivity(i);
        });
        h.itemView.setOnLongClickListener(v -> { cb.onAppLongPressed(pkg); return true; });
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView  name;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.ivWidgetIcon);
            name = v.findViewById(R.id.tvWidgetName);
        }
    }
}
