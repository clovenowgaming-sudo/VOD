package com.customRobTop;

/** Native callbacks implemented inside libcocos2dcpp.so. */
public final class JniToCpp {
    private JniToCpp() {}
    public static native void hideLoadingCircle();
    public static native void resumeSound();
    public static native void filesPicked(String[] paths);
    public static native void fileShareFinished(boolean ok);
    public static native void userDidAttemptToRateApp();
    public static native void textInputDone();
    public static native void textInputCaretToEnd();
}
