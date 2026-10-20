package com.customRobTop;

/** Native callbacks implemented inside libcocos2dcpp.so. */
public final class JniToCpp {
    private JniToCpp() {}
    /** (path of Geometry Dash's APK, external storage root) - the game reads its textures/sounds from the APK through this. */
    public static native void setupHSSAssets(String apkPath, String externalStoragePath);
    public static native void hideLoadingCircle();
    public static native void resumeSound();
    public static native void filesPicked(String[] paths);
    public static native void fileShareFinished(boolean ok);
    public static native void userDidAttemptToRateApp();
    public static native void textInputDone();
    public static native void textInputCaretToEnd();
}
