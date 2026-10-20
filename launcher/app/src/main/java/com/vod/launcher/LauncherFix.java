package com.vod.launcher;

/** Native helper bridge (see src/main/cpp/launcherfix.cpp). */
public final class LauncherFix {
    private LauncherFix() {}
    public static native void setDataPath(String path);
    public static native void setOriginalDataPath(String path);
    public static native void performPatches();
    public static void loadLibrary() { System.loadLibrary("vodlauncherfix"); }
}
