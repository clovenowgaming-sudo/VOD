package com.vod.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private TextView status;
    private Button launch, safe;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#F5F3FF"));
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Color.parseColor(primary ? "#7C4DFF" : "#2A1B4A"));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.vod_logo);
        root.addView(logo, new LinearLayout.LayoutParams(dp(120), dp(120)));

        TextView title = new TextView(this);
        title.setText("Vod");
        title.setTextColor(Color.parseColor("#F5F3FF"));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Mod loader for Geometry Dash");
        sub.setTextColor(Color.parseColor("#B7A9E6"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#F5F3FF"));
        status.setTextSize(14);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(dp(14));
        card.setColor(Color.parseColor("#2A1B4A"));
        status.setBackground(card);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(24);
        root.addView(status, slp);

        launch = button("Launch Geometry Dash", true);
        launch.setOnClickListener(v -> start(false));
        root.addView(launch);

        safe = button("Launch in safe mode (no mods)", false);
        safe.setOnClickListener(v -> start(true));
        root.addView(safe);

        Button copyPath = button("Copy mods folder path", false);
        copyPath.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vod mods", modsDir().getAbsolutePath()));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        root.addView(copyPath);

        Button store = button("Get Geometry Dash", false);
        store.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + GameLibs.GD_PACKAGE))));
        root.addView(store);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    private File modsDir() {
        File base = getExternalFilesDir(null);
        if (base == null) base = new File(getFilesDir(), "ext");
        File d = new File(base, "game/geode/mods");
        d.mkdirs();
        return d;
    }

    @Override protected void onResume() {
        super.onResume();
        boolean installed = GameLibs.isGdInstalled(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Geometry Dash: ");
        if (installed) {
            try {
                PackageInfo pi = GameLibs.gdPackage(this);
                sb.append("installed (").append(pi.versionName).append(", code ").append(GameLibs.versionCode(pi)).append(")");
            } catch (Exception e) { sb.append("installed"); }
        } else sb.append("not installed");
        sb.append("\nArchitecture: ").append(GameLibs.abi());
        sb.append("\nMods folder:\n").append(modsDir().getAbsolutePath());
        status.setText(sb);
        launch.setEnabled(installed);
        safe.setEnabled(installed);
        launch.setAlpha(installed ? 1f : 0.4f);
        safe.setAlpha(installed ? 1f : 0.4f);
    }

    private void start(boolean safeMode) {
        Intent i = new Intent(this, GeometryDashActivity.class);
        i.putExtra(GeometryDashActivity.EXTRA_SAFE_MODE, safeMode);
        startActivity(i);
    }
}
