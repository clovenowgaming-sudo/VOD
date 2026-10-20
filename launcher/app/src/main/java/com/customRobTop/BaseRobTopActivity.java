package com.customRobTop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import com.vod.launcher.GeometryDashActivity;
import com.vod.launcher.ShareProvider;
import com.vod.launcher.utils.GeodeUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Static helpers the original game calls through JNI (ads, sharing, keyboard...).
 * Things that need Google services (ads, achievements, recording) are no-ops here.
 */
public final class BaseRobTopActivity {
    private static final String TAG = "VodLauncher";
    public static final int REQ_IMPORT = 7200;
    public static WeakReference<Activity> me = new WeakReference<>(null);
    public static boolean blockBackButton = false;
    public static boolean isPaused = false;
    public static int keyboardInputFlags = 0;

    private BaseRobTopActivity() {}

    public static void setCurrentActivity(Activity a) { me = new WeakReference<>(a); }
    private static Activity act() { return me.get(); }

    // ---- keyboard / back ----
    public static void setBlockBackButton(boolean b) { blockBackButton = b; }
    public static boolean getBlockBackButton() { return blockBackButton; }
    public static void setKeyboardInputFlags(int f) { keyboardInputFlags = f; }
    public static void setKeyboardState(boolean show) {
        Activity a = act();
        if (a instanceof GeometryDashActivity) ((GeometryDashActivity) a).showKeyboard(show);
    }
    public static void onToggleKeyboard() {}
    public static void loadingFinished() { try { JniToCpp.hideLoadingCircle(); } catch (Throwable ignored) {} }
    public static void onNativePause() { isPaused = true; }
    public static void onNativeResume() { isPaused = false; }
    public static boolean isPaused() { return isPaused; }
    public static boolean shouldResumeSound() { return true; }

    // ---- clipboard / links / mail ----
    public static void copyToClipboard(String text) { GeodeUtils.writeClipboard(text); }
    public static String getClipboardString() { return GeodeUtils.readClipboard(); }
    public static void openURL(String url) { GeodeUtils.openWebview(url); }
    public static void openAppPage() { GeodeUtils.openWebview("https://play.google.com/store/apps/details?id=" + com.vod.launcher.GameLibs.GD_PACKAGE); }
    public static void sendMail(String subject, String body, String to) {
        Activity a = act(); if (a == null) return;
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (to == null ? "" : to)))
                .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body);
        try { a.startActivity(Intent.createChooser(i, "Mail")); } catch (Throwable t) { Log.w(TAG, "sendMail", t); }
    }
    public static String getUserID() {
        Activity a = act();
        return a == null ? "" : Settings.Secure.getString(a.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    // ---- files ----
    public static void shareFiles(String[] paths) {
        Activity a = act(); if (a == null || paths == null) return;
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (String p : paths) uris.add(ShareProvider.uriFor(new File(p)));
            Intent i = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0)); else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            a.startActivity(Intent.createChooser(i, "Share"));
        } catch (Throwable t) { Log.w(TAG, "shareFiles", t); try { JniToCpp.fileShareFinished(false); } catch (Throwable ignored) {} }
    }
    public static void pickImportFiles() {
        Activity a = act(); if (a == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { a.startActivityForResult(i, REQ_IMPORT); } catch (Throwable t) { Log.w(TAG, "pickImportFiles", t); }
    }
    /** Called by the activity when the import picker returns; copies into cache and reports paths to the game. */
    public static void onImportResult(Context c, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null) { try { JniToCpp.filesPicked(new String[0]); } catch (Throwable ignored) {} return; }
        ArrayList<String> out = new ArrayList<>();
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) { ClipData cd = data.getClipData(); for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri()); }
        else if (data.getData() != null) uris.add(data.getData());
        File dir = new File(c.getCacheDir(), "import"); dir.mkdirs();
        for (Uri u : uris) {
            try {
                String name = u.getLastPathSegment(); if (name == null) name = "level"; name = name.substring(name.lastIndexOf('/') + 1).replace(':', '_');
                File f = new File(dir, name);
                try (java.io.InputStream in = c.getContentResolver().openInputStream(u); java.io.OutputStream os = new java.io.FileOutputStream(f)) {
                    byte[] buf = new byte[1 << 16]; int n; while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                out.add(f.getAbsolutePath());
            } catch (Throwable t) { Log.w(TAG, "import", t); }
        }
        try { JniToCpp.filesPicked(out.toArray(new String[0])); } catch (Throwable ignored) {}
    }

    // ---- device ----
    public static boolean isNetworkAvailable() {
        Activity a = act(); if (a == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) { return true; }
    }
    public static float getDeviceRefreshRate() {
        Activity a = act(); if (a == null) return 60f;
        try { @SuppressWarnings("deprecation") Display d = a.getWindowManager().getDefaultDisplay(); return d.getRefreshRate(); } catch (Throwable t) { return 60f; }
    }

    // ---- not supported in Vod: ads, recording, play games, rating ----
    public static void tryShowRateDialog(String s) { try { JniToCpp.userDidAttemptToRateApp(); } catch (Throwable ignored) {} }
    public static void logEvent(String e) {}
    public static void unlockAchievement(String id) {}
    public static void showAchievements() {}
    public static boolean gameServicesIsSignedIn() { return false; }
    public static void gameServicesSignIn() {}
    public static void gameServicesSignOut() {}
    public static void enableBanner() {} public static void disableBanner() {} public static void enableBannerNoRefresh() {} public static void queueRefreshBanner() {}
    public static void cacheInterstitial() {} public static void showInterstitial() {} public static boolean hasCachedInterstitial() { return false; }
    public static void cacheRewardedVideo() {} public static void showRewardedVideo() {} public static boolean hasCachedRewardedVideo() { return false; }
    public static boolean isEveryplaySupported() { return false; } public static void setupEveryplay() {} public static void showEveryplay() {}
    public static void setEveryplayMetadata(String a, String b) {}
    public static boolean isRecordingSupported() { return false; } public static boolean isRecording() { return false; } public static boolean isRecordingPaused() { return false; }
    public static void startRecording() {} public static void stopRecording() {} public static void pauseRecording() {} public static void resumeRecording() {} public static void playLastRecording() {}
}
