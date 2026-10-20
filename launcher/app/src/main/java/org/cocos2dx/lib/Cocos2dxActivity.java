package org.cocos2dx.lib;

/** Static entry points the native cocos code expects on this class. */
public final class Cocos2dxActivity {
    private Cocos2dxActivity() {}
    public static void openURL(String url) { com.vod.launcher.utils.GeodeUtils.openWebview(url); }
}
