package com.vod.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class LogActivity extends Activity {
    private String text = "Loading...";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1B1030"));
        root.setPadding(24, 40, 24, 24);

        final TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#F5F3FF"));
        tv.setTextSize(10);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setText(text);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button copy = new Button(this);
        copy.setText("Copy log");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vod log", text));
            Toast.makeText(this, "Copied (" + text.length() + " chars)", Toast.LENGTH_SHORT).show();
        });
        root.addView(copy);

        Button share = new Button(this);
        share.setText("Share log");
        share.setOnClickListener(v -> {
            String t = text.length() > 200000 ? text.substring(text.length() - 200000) : text;
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t);
            startActivity(Intent.createChooser(i, "Share log"));
        });
        root.addView(share);

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);

        new Thread(() -> {
            final String s = CrashReporter.collect(getApplicationContext());
            runOnUiThread(() -> { text = s; tv.setText(s); });
        }).start();
    }
}
