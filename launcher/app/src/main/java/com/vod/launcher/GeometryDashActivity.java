package com.vod.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.customRobTop.BaseRobTopActivity;
import com.vod.launcher.utils.GeodeUtils;

import org.cocos2dx.lib.Cocos2dxGLSurfaceView;
import org.cocos2dx.lib.Cocos2dxHelper;
import org.cocos2dx.lib.Cocos2dxRenderer;
import org.fmod.FMOD;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Hosts Geometry Dash (installed separately) with the Vod/Geode loader injected. */
public class GeometryDashActivity extends Activity implements Cocos2dxHelper.Cocos2dxHelperListener {
    public static final String EXTRA_SAFE_MODE = "vod.safe_mode";
    private static final String TAG = "VodLauncher";
    private Cocos2dxGLSurfaceView glView;
    private boolean started = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();
        CrashReporter.installJavaHandler(getApplicationContext());
        CrashReporter.markStart(getApplicationContext());
        try {
            GeodeUtils.setContext(this);
            BaseRobTopActivity.setCurrentActivity(this);
            if (getIntent().getBooleanExtra(EXTRA_SAFE_MODE, false)) GeodeUtils.setAdditionalLaunchArguments(new String[]{"--geode:safe-mode"});
            else GeodeUtils.clearLaunchArguments();

            GameLibs.load(this);
            FMOD.init(this);
            Cocos2dxHelper.init(this, this);

            glView = new Cocos2dxGLSurfaceView(this);
            glView.setCocos2dxRenderer(new Cocos2dxRenderer());
            setContentView(glView);
            glView.requestFocus();
            started = true;
        } catch (Throwable t) {
            Log.e(TAG, "launch failed", t);
            CrashReporter.markClean(getApplicationContext());
            showError(t);
        }
    }

    private void showError(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        final String text = "Vod could not start the game\n\n" + sw;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1B1030"));
        root.setPadding(32, 48, 32, 32);
        TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#F5F3FF"));
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setText(text);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button copy = new Button(this);
        copy.setText("Copy error");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vod error", text));
        });
        root.addView(copy);
        Button close = new Button(this);
        close.setText("Back");
        close.setOnClickListener(v -> finish());
        root.addView(close);
        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onPause() {
        super.onPause();
        if (!started) return;
        BaseRobTopActivity.onNativePause();
        Cocos2dxHelper.onPause();
        glView.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!started) return;
        BaseRobTopActivity.onNativeResume();
        Cocos2dxHelper.onResume();
        glView.onResume();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        // the native game keeps process-wide state, so never reuse this process
        if (isFinishing()) CrashReporter.markClean(getApplicationContext());
        if (started && isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (started && glView != null && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)) return glView.onKeyDown(keyCode, event);
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == BaseRobTopActivity.REQ_IMPORT) BaseRobTopActivity.onImportResult(this, res, data);
        else GeodeUtils.onActivityResult(req, res, data);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        GeodeUtils.onPermissionResult(req, grants);
    }

    public void showKeyboard(boolean show) { if (glView != null) glView.showKeyboard(show); }
    public void runOnGLThread(Runnable r) { if (glView != null) glView.queueEvent(r); }

    // ---- Cocos2dxHelperListener ----
    @Override public void showDialog(final String title, final String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show());
    }

    @Override public void showEditTextDialog(final String title, final String message, final int inputMode,
                                             final int inputFlag, final int returnType, final int maxLength) {
        runOnUiThread(() -> {
            final EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            if (maxLength > 0) et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
            new AlertDialog.Builder(this).setTitle(title).setMessage(message).setView(et)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        final byte[] bytes = et.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        if (glView != null) glView.queueEvent(() -> Cocos2dxHelper.nativeSetEditTextDialogResult(bytes));
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
        });
    }
}
