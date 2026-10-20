package com.vod.launcher;

import android.content.Context;
import java.io.File;

/** Vod keeps its data in Android/media/com.vod.launcher (same idea as the Geode launcher). */
public final class Dirs {
    private Dirs() {}

    @SuppressWarnings("deprecation")
    public static File base(Context c) {
        File d = null;
        try {
            File[] m = c.getExternalMediaDirs();
            if (m != null && m.length > 0) d = m[0];
        } catch (Throwable ignored) {}
        if (d == null) d = new File(c.getFilesDir(), "ext");
        d.mkdirs();
        return d;
    }
    public static File game(Context c) { File d = new File(base(c), "game"); d.mkdirs(); return d; }
    public static File save(Context c) { File d = new File(base(c), "save"); d.mkdirs(); return d; }
    public static File mods(Context c) { File d = new File(game(c), "geode/mods"); d.mkdirs(); return d; }
    public static File resources(Context c) { return new File(game(c), "geode/resources/geode.loader"); }
}
