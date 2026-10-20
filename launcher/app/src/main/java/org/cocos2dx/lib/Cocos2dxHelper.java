package org.cocos2dx.lib;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.DisplayMetrics;

import com.vod.launcher.GameLibs;
import com.vod.launcher.utils.GeodeUtils;

import java.util.Locale;

public final class Cocos2dxHelper {
    public interface Cocos2dxHelperListener {
        void showDialog(String title, String message);
        void showEditTextDialog(String title, String message, int inputMode, int inputFlag, int returnType, int maxLength);
    }

    private static Cocos2dxHelperListener listener;
    private static AssetManager sAssetManager;
    private static String fileDirectory = "";
    private static String packageName = GameLibs.GD_PACKAGE;
    private static Cocos2dxAccelerometer accelerometer;
    private static boolean accelerometerEnabled = false;
    private static Context ctx;

    private Cocos2dxHelper() {}

    public static native void nativeSetApkPath(String path);
    public static native void nativeSetEditTextDialogResult(byte[] text);

    public static void init(Context context, Cocos2dxHelperListener l) throws Exception {
        ctx = context;
        listener = l;
        fileDirectory = GeodeUtils.getBaseDirectory() + "/save";
        // game assets live in the installed Geometry Dash package (incl. split apks)
        Context gd = context.createPackageContext(GameLibs.GD_PACKAGE, 0);
        sAssetManager = gd.getAssets();
        // same sequence as the original game activity: HSS assets first, then the apk path
        String apk = gd.getApplicationInfo().sourceDir;
        String ext = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        com.customRobTop.JniToCpp.setupHSSAssets(apk, ext);
        nativeSetApkPath(apk);
        accelerometer = new Cocos2dxAccelerometer(context);
        Cocos2dxBitmap.setContext(context);
    }

    // ---- called from native ----
    public static AssetManager getAssetManager() { return sAssetManager; }
    public static String getCocos2dxWritablePath() { return fileDirectory; }
    public static String getCocos2dxPackageName() { return packageName; }
    public static String getCurrentLanguage() { return Locale.getDefault().getLanguage(); }
    public static String getDeviceModel() { return Build.MODEL; }
    public static int getDPI() {
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        return (int) (m.density * 160f);
    }
    public static void showDialog(String title, String message) { if (listener != null) listener.showDialog(title, message); }
    public static void showEditTextDialog(String title, String message, int inputMode, int inputFlag, int returnType, int maxLength) {
        if (listener != null) listener.showEditTextDialog(title, message, inputMode, inputFlag, returnType, maxLength);
    }
    public static void terminateProcess() { android.os.Process.killProcess(android.os.Process.myPid()); }
    public static void enableAccelerometer() { accelerometerEnabled = true; if (accelerometer != null) accelerometer.enable(); }
    public static void disableAccelerometer() { accelerometerEnabled = false; if (accelerometer != null) accelerometer.disable(); }
    public static void setAccelerometerInterval(float interval) { if (accelerometer != null) accelerometer.setInterval(interval); }
    public static void openURL(String url) { GeodeUtils.openWebview(url); }

    public static void onResume() { if (accelerometerEnabled && accelerometer != null) accelerometer.enable(); }
    public static void onPause() { if (accelerometer != null) accelerometer.disable(); }
}
