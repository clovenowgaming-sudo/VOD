package com.vod.launcher.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.WindowInsets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Bridge between the Vod loader (native, see loader/src/platform/android/*.cpp) and Android.
 * Every static method here is looked up by name + signature from C++ — do not rename them.
 */
public final class GeodeUtils {
    public static final String TAG = "VodLauncher";
    public static final int REQ_OPEN_FILE = 7001, REQ_OPEN_FILES = 7002, REQ_OPEN_DIR = 7003, REQ_PERMISSION = 7100;
    public static final int LAUNCHER_VERSION = 100;
    public static final String INTERNAL_PREFIX = "internal:";

    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static Context appCtx;
    private static final ArrayList<String> launchArgs = new ArrayList<>();

    private GeodeUtils() {}

    // ---- natives implemented by the loader ----
    public static native void selectFileCallback(String path);
    public static native void selectFilesCallback(String[] paths);
    public static native void failedCallback();
    public static native void permissionCallback(boolean granted);
    public static native void resizeSurface(int width, int height);

    // ---- context ----
    public static void setContext(Activity a) {
        activity = new WeakReference<>(a);
        appCtx = a.getApplicationContext();
    }
    private static Activity act() { return activity.get(); }
    public static void setAdditionalLaunchArguments(String[] args) { launchArgs.clear(); if (args != null) for (String s : args) launchArgs.add(s); }
    public static void clearLaunchArguments() { launchArgs.clear(); }

    // ---- directories ----
    public static String getInternalDirectory() { return appCtx.getFilesDir().getAbsolutePath() + "/"; }

    public static String getBaseDirectory() {
        File d = appCtx.getExternalFilesDir(null);
        if (d == null) d = new File(appCtx.getFilesDir(), "ext");
        new File(d, "game").mkdirs();
        new File(d, "save").mkdirs();
        return d.getAbsolutePath();
    }

    // ---- misc info ----
    public static int getLauncherVersion() { return LAUNCHER_VERSION; }
    public static String getLaunchArguments() {
        StringBuilder sb = new StringBuilder();
        for (String s : launchArgs) { if (sb.length() > 0) sb.append(' '); sb.append(s); }
        return sb.toString();
    }
    public static boolean reportPlatformCapability(String id) {
        Log.i(TAG, "loader capability: " + id);
        return true;
    }

    public static int[] getScreenInsets() {
        int[] r = new int[4]; // left, bottom, right, top
        try {
            Activity a = act();
            if (a != null && Build.VERSION.SDK_INT >= 28) {
                WindowInsets wi = a.getWindow().getDecorView().getRootWindowInsets();
                if (wi != null) {
                    DisplayCutout dc = wi.getDisplayCutout();
                    if (dc != null) { r[0] = dc.getSafeInsetLeft(); r[1] = dc.getSafeInsetBottom(); r[2] = dc.getSafeInsetRight(); r[3] = dc.getSafeInsetTop(); }
                }
            }
        } catch (Throwable ignored) {}
        return r;
    }

    // ---- clipboard / links / folders ----
    public static void writeClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) appCtx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vod", text));
        } catch (Throwable t) { Log.w(TAG, "writeClipboard", t); }
    }
    public static String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) appCtx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0) { CharSequence t = cd.getItemAt(0).coerceToText(appCtx); return t == null ? "" : t.toString(); }
        } catch (Throwable t) { Log.w(TAG, "readClipboard", t); }
        return "";
    }
    public static void openWebview(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appCtx.startActivity(i);
        } catch (Throwable t) { Log.w(TAG, "openWebview", t); }
    }
    public static boolean openFolder(String path) {
        try {
            String base = "/storage/emulated/0/";
            String rel = path.startsWith(base) ? path.substring(base.length()) : null;
            if (rel == null) return false;
            Uri u = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(rel));
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "vnd.android.document/directory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appCtx.startActivity(i);
            return true;
        } catch (Throwable t) { Log.w(TAG, "openFolder", t); return false; }
    }

    // ---- pickers (results come back through onActivityResult) ----
    private static boolean launch(Intent i, int code) {
        Activity a = act();
        if (a == null) return false;
        try { a.startActivityForResult(i, code); return true; } catch (Throwable t) { Log.w(TAG, "picker", t); return false; }
    }
    public static boolean selectFile(String hint) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        return launch(i, REQ_OPEN_FILE);
    }
    public static boolean selectFiles(String hint) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return launch(i, REQ_OPEN_FILES);
    }
    public static boolean selectFolder(String hint) {
        return launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_OPEN_DIR);
    }
    /** "Save file": we hand back a path inside the app's external dir (downloads/). */
    public static boolean createFile(String name) {
        File dir = new File(getBaseDirectory(), "downloads");
        dir.mkdirs();
        final String p = new File(dir, name == null || name.isEmpty() ? "file" : name).getAbsolutePath();
        selectFileCallback(p);
        return true;
    }

    private static String copyToCache(Uri uri) {
        try {
            String name = "file";
            try (Cursor c = appCtx.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ix >= 0) name = c.getString(ix); }
            }
            File dir = new File(appCtx.getCacheDir(), "picked");
            dir.mkdirs();
            File out = new File(dir, name);
            try (InputStream in = appCtx.getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16]; int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Throwable t) { Log.w(TAG, "copyToCache", t); return null; }
    }

    private static String treeToPath(Uri tree) {
        String id = android.provider.DocumentsContract.getTreeDocumentId(tree); // "primary:Download/x"
        if (id != null && id.startsWith("primary:")) return "/storage/emulated/0/" + id.substring("primary:".length());
        return null;
    }

    public static void onActivityResult(int req, int result, Intent data) {
        if (req != REQ_OPEN_FILE && req != REQ_OPEN_FILES && req != REQ_OPEN_DIR) return;
        if (result != Activity.RESULT_OK || data == null) {
            if (req == REQ_OPEN_FILES) selectFilesCallback(new String[0]); else selectFileCallback("");
            return;
        }
        try {
            if (req == REQ_OPEN_FILE) {
                String p = copyToCache(data.getData());
                if (p == null) failedCallback(); else selectFileCallback(p);
            } else if (req == REQ_OPEN_DIR) {
                String p = treeToPath(data.getData());
                if (p == null) failedCallback(); else selectFileCallback(p);
            } else {
                ArrayList<String> out = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        String p = copyToCache(data.getClipData().getItemAt(i).getUri()); if (p != null) out.add(p);
                    }
                } else if (data.getData() != null) { String p = copyToCache(data.getData()); if (p != null) out.add(p); }
                selectFilesCallback(out.toArray(new String[0]));
            }
        } catch (Throwable t) { Log.w(TAG, "onActivityResult", t); failedCallback(); }
    }

    // ---- permissions ----
    public static boolean getPermissionStatus(String perm) {
        if (perm == null || perm.startsWith(INTERNAL_PREFIX)) return true;
        return appCtx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }
    public static void requestPermission(String perm) {
        Activity a = act();
        if (a == null || perm == null || perm.startsWith(INTERNAL_PREFIX)) { permissionCallback(true); return; }
        a.requestPermissions(new String[]{perm}, REQ_PERMISSION);
    }
    public static void onPermissionResult(int req, int[] grants) {
        if (req != REQ_PERMISSION) return;
        permissionCallback(grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED);
    }

    // ---- restart ----
    public static void restartGame() {
        try {
            Intent i = new Intent(appCtx, com.vod.launcher.GeometryDashActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(appCtx, 1, i,
                    android.app.PendingIntent.FLAG_CANCEL_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager am = (android.app.AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pi);
        } catch (Throwable t) { Log.w(TAG, "restartGame", t); }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
