package org.cocos2dx.lib;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Renders text into a bitmap and hands the RGBA pixels to the native texture code. */
public final class Cocos2dxBitmap {
    private static final int H_LEFT = 1, H_RIGHT = 2, H_CENTER = 3;
    private static final int V_TOP = 1, V_BOTTOM = 2, V_CENTER = 3;
    private static Context context;
    private static final HashMap<String, Typeface> faces = new HashMap<>();

    private Cocos2dxBitmap() {}

    public static native void nativeInitBitmapDC(int width, int height, byte[] pixels);

    public static void setContext(Context c) { context = c; }

    private static Typeface typeface(String name) {
        Typeface t = faces.get(name);
        if (t != null) return t;
        try {
            if (name != null && name.toLowerCase().endsWith(".ttf")) {
                AssetManager am = Cocos2dxHelper.getAssetManager();
                t = Typeface.createFromAsset(am, name);
            } else {
                t = Typeface.create(name == null ? "sans-serif" : name, Typeface.NORMAL);
            }
        } catch (Throwable e) { t = Typeface.DEFAULT; }
        faces.put(name, t);
        return t;
    }

    private static Paint newPaint(String fontName, int size, int hAlign) {
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setTextSize(size);
        p.setAntiAlias(true);
        p.setTypeface(typeface(fontName));
        p.setTextAlign(hAlign == H_RIGHT ? Paint.Align.RIGHT : hAlign == H_CENTER ? Paint.Align.CENTER : Paint.Align.LEFT);
        return p;
    }

    private static List<String> layout(String text, int maxWidth, Paint p) {
        ArrayList<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (maxWidth <= 0 || p.measureText(line) <= maxWidth) { out.add(line); continue; }
            StringBuilder cur = new StringBuilder();
            for (String word : line.split(" ", -1)) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (p.measureText(trial) <= maxWidth) { cur = new StringBuilder(trial); continue; }
                if (cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(); }
                // hard-break very long words
                String w = word;
                while (p.measureText(w) > maxWidth && w.length() > 1) {
                    int n = p.breakText(w, true, maxWidth, null);
                    if (n <= 0) n = 1;
                    out.add(w.substring(0, n)); w = w.substring(n);
                }
                cur = new StringBuilder(w);
            }
            out.add(cur.toString());
        }
        return out;
    }

    public static void createTextBitmap(String content, String fontName, int fontSize, int alignment, int width, int height) {
        createTextBitmapShadowStroke(content, fontName, fontSize, 1f, 1f, 1f, alignment, width, height,
                false, 0, 0, 0, false, 0, 0, 0, 0);
    }

    public static void createTextBitmapShadowStroke(String content, String fontName, int fontSize,
                                                    float r, float g, float b, int alignment, int width, int height,
                                                    boolean shadow, float dx, float dy, float blur,
                                                    boolean stroke, float sr, float sg, float sb, float strokeSize) {
        content = refactorString(content);
        int hAlign = alignment & 0x0F;
        int vAlign = (alignment >> 4) & 0x0F;
        Paint paint = newPaint(fontName, fontSize, hAlign);
        paint.setColor(Color.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255)));
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int lineH = fm.bottom - fm.top;
        List<String> lines = layout(content, width, paint);

        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, (int) Math.ceil(paint.measureText(l)));
        int pad = (stroke ? (int) Math.ceil(strokeSize) : 0) + (shadow ? (int) Math.ceil(Math.abs(Math.max(dx, dy)) + blur) : 0);
        int bw = width > 0 ? width : maxW + pad * 2;
        int bh = height > 0 ? height : lineH * lines.size() + pad * 2;
        if (bw <= 0 || bh <= 0) return;

        Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);

        int totalH = lineH * lines.size();
        int y0;
        switch (vAlign) {
            case V_TOP: y0 = pad; break;
            case V_BOTTOM: y0 = bh - totalH - pad; break;
            default: y0 = (bh - totalH) / 2; break;
        }
        int x;
        switch (hAlign) {
            case H_LEFT: x = pad; break;
            case H_RIGHT: x = bw - pad; break;
            default: x = bw / 2; break;
        }

        Paint strokePaint = null;
        if (stroke) {
            strokePaint = new Paint(paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeSize);
            strokePaint.setColor(Color.rgb((int) (sr * 255), (int) (sg * 255), (int) (sb * 255)));
        }
        if (shadow) paint.setShadowLayer(blur, dx, -dy, Color.argb(160, 0, 0, 0));

        int y = y0 - fm.top;
        for (String l : lines) {
            if (strokePaint != null) canvas.drawText(l, x, y, strokePaint);
            canvas.drawText(l, x, y, paint);
            y += lineH;
        }
        initNativeObject(bm);
    }

    private static void initNativeObject(Bitmap bm) {
        byte[] px = getPixels(bm);
        if (px == null) return;
        nativeInitBitmapDC(bm.getWidth(), bm.getHeight(), px);
    }

    private static byte[] getPixels(Bitmap bm) {
        if (bm == null) return null;
        ByteBuffer buf = ByteBuffer.allocate(bm.getWidth() * bm.getHeight() * 4);
        bm.copyPixelsToBuffer(buf);
        return buf.array();
    }

    private static String refactorString(String s) { return s == null ? "" : s.replace("\r", ""); }

    public static int getFontSizeAccordingHeight(int height) {
        Paint p = new Paint();
        Rect bounds = new Rect();
        p.setTypeface(Typeface.DEFAULT);
        float size = 1f;
        while (size < 2000f) {
            p.setTextSize(size);
            p.getTextBounds("SghMNy", 0, 6, bounds);
            if (bounds.height() >= height) break;
            size += 1f;
        }
        return (int) (size - 1f);
    }

    public static String getStringWithEllipsis(String text, float width, float fontSize) {
        if (TextUtils.isEmpty(text)) return "";
        TextPaint p = new TextPaint();
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(fontSize);
        return TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString();
    }
}
