package org.cocos2dx.lib;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.vod.launcher.utils.GeodeUtils;

public class Cocos2dxGLSurfaceView extends GLSurfaceView {
    private Cocos2dxRenderer renderer;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean keyboardWanted = false;

    public Cocos2dxGLSurfaceView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        setPreserveEGLContextOnPause(true);
    }

    public void setCocos2dxRenderer(Cocos2dxRenderer r) { renderer = r; setRenderer(r); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (renderer == null) return;
        renderer.setScreenWidthAndHeight(w, h);
        if (renderer.isInitialized()) {
            queueEvent(() -> { try { GeodeUtils.resizeSurface(w, h); } catch (Throwable ignored) {} });
        }
    }

    @Override public void onPause() { queueEvent(() -> renderer.handleOnPause()); super.onPause(); }
    @Override public void onResume() { super.onResume(); queueEvent(() -> renderer.handleOnResume()); }

    // ---- touch ----
    private static double ts(MotionEvent e) { return e.getEventTime() / 1000.0; }

    @Override public boolean onTouchEvent(final MotionEvent e) {
        final double t = ts(e);
        final int count = e.getPointerCount();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int i = e.getActionIndex();
                final int id = e.getPointerId(i); final float x = e.getX(i), y = e.getY(i);
                queueEvent(() -> Cocos2dxRenderer.nativeTouchesBegin(id, x, y, t));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                final int i = e.getActionIndex();
                final int id = e.getPointerId(i); final float x = e.getX(i), y = e.getY(i);
                queueEvent(() -> Cocos2dxRenderer.nativeTouchesEnd(id, x, y, t));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int[] ids = new int[count]; final float[] xs = new float[count], ys = new float[count];
                for (int i = 0; i < count; i++) { ids[i] = e.getPointerId(i); xs[i] = e.getX(i); ys[i] = e.getY(i); }
                queueEvent(() -> Cocos2dxRenderer.nativeTouchesMove(ids, xs, ys, t));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                final int[] ids = new int[count]; final float[] xs = new float[count], ys = new float[count];
                for (int i = 0; i < count; i++) { ids[i] = e.getPointerId(i); xs[i] = e.getX(i); ys[i] = e.getY(i); }
                queueEvent(() -> Cocos2dxRenderer.nativeTouchesCancel(ids, xs, ys, t));
                break;
            }
            default: break;
        }
        return true;
    }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            final float sx = e.getAxisValue(MotionEvent.AXIS_HSCROLL), sy = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            final float x = e.getX(), y = e.getY();
            queueEvent(() -> Cocos2dxRenderer.nativeScroll(sx, sy, x, y));
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    // ---- keys ----
    @Override public boolean onKeyDown(final int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            queueEvent(() -> Cocos2dxRenderer.nativeKeyDown(keyCode));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) { queueEvent(() -> Cocos2dxRenderer.nativeInsertText("\n")); return true; }
        if (keyCode == KeyEvent.KEYCODE_DEL) { queueEvent(Cocos2dxRenderer::nativeDeleteBackward); return true; }
        int u = event.getUnicodeChar();
        if (u > 0 && !event.isCtrlPressed()) { final String s = new String(Character.toChars(u)); queueEvent(() -> Cocos2dxRenderer.nativeInsertText(s)); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // ---- soft keyboard (text fields in the game) ----
    public void showKeyboard(final boolean show) {
        keyboardWanted = show;
        ui.post(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (show) { requestFocus(); imm.restartInput(this); imm.showSoftInput(this, 0); }
            else { imm.hideSoftInputFromWindow(getWindowToken(), 0); queueEvent(Cocos2dxRenderer::nativeTextClosed); }
        });
    }

    @Override public boolean onCheckIsTextEditor() { return keyboardWanted; }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                final String s = text.toString();
                queueEvent(() -> Cocos2dxRenderer.nativeInsertText(s));
                return true;
            }
            @Override public boolean setComposingText(CharSequence text, int pos) { return commitText(text, pos); }
            @Override public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < Math.max(1, before); i++) queueEvent(Cocos2dxRenderer::nativeDeleteBackward);
                return true;
            }
            @Override public boolean sendKeyEvent(KeyEvent ev) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(ev.getKeyCode(), ev);
                return true;
            }
            @Override public boolean performEditorAction(int actionCode) {
                queueEvent(() -> Cocos2dxRenderer.nativeInsertText("\n"));
                showKeyboard(false);
                return true;
            }
        };
    }
}
