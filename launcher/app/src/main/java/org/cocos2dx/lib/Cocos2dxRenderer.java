package org.cocos2dx.lib;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Cocos2dxRenderer implements GLSurfaceView.Renderer {
    private static long animationIntervalNs = (long) (1.0 / 60 * 1e9);
    private long lastTick = System.nanoTime();
    private int screenWidth, screenHeight;
    private volatile boolean initialized = false;

    // ---- natives exported by libcocos2dcpp.so ----
    public static native void nativeInit(int w, int h);
    public static native void nativeRender();
    public static native void nativeOnPause();
    public static native void nativeOnResume();
    public static native void nativeOnSurfaceChanged(int w, int h);
    public static native boolean nativeKeyDown(int keyCode);
    public static native void nativeInsertText(String text);
    public static native void nativeDeleteBackward();
    public static native String nativeGetContentText();
    public static native void nativeTouchesBegin(int id, float x, float y, double ts);
    public static native void nativeTouchesEnd(int id, float x, float y, double ts);
    public static native void nativeTouchesMove(int[] ids, float[] xs, float[] ys, double ts);
    public static native void nativeTouchesCancel(int[] ids, float[] xs, float[] ys, double ts);
    public static native void nativeScroll(float a, float b, float c, float d);
    public static native void nativeKeyboardWillShow(int height);
    public static native void nativeTextClosed();
    public static native void nativeKeyboardEvent(int key, boolean down, boolean repeat, double ts, boolean shift, boolean ctrl, boolean alt);

    /** Called from native (Director::setAnimationInterval). */
    public static void setAnimationInterval(double interval) { animationIntervalNs = (long) (interval * 1e9); }

    public void setScreenWidthAndHeight(int w, int h) { screenWidth = w; screenHeight = h; }
    public boolean isInitialized() { return initialized; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        lastTick = System.nanoTime();
        logGl();
        nativeInit(screenWidth, screenHeight);
        initialized = true;
    }
    @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
        if (initialized && (w != screenWidth || h != screenHeight)) {
            screenWidth = w; screenHeight = h;
            nativeOnSurfaceChanged(w, h);
        }
    }
    @Override public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        long dt = now - lastTick;
        if (dt < animationIntervalNs) {
            try { Thread.sleep((animationIntervalNs - dt) / 1000000L); } catch (InterruptedException ignored) {}
        }
        lastTick = System.nanoTime();
        nativeRender();
    }

    private static void logGl() {
        try {
            int[] mts = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, mts, 0);
            String ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            Log.i("VodLauncher", "GL_VERSION=" + GLES20.glGetString(GLES20.GL_VERSION) + " RENDERER=" + GLES20.glGetString(GLES20.GL_RENDERER)
                    + " MAX_TEXTURE_SIZE=" + mts[0] + " npot=" + (ext != null && ext.contains("GL_OES_texture_npot"))
                    + " glError=" + GLES20.glGetError());
        } catch (Throwable t) { Log.e("VodLauncher", "logGl failed", t); }
    }

    public void handleOnPause() { if (initialized) nativeOnPause(); }
    public void handleOnResume() { if (initialized) nativeOnResume(); }
}
