package com.vod.launcher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;

/** First-run setup: creates Vod's folders in Android/media and extracts the loader resources shipped in the APK. */
public final class Installer {
    public interface Progress { void update(int done, int total, String what); }

    private Installer() {}

    private static String stamp(Context c) throws Exception {
        return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime + ":" + com.vod.launcher.utils.GeodeUtils.LAUNCHER_VERSION;
    }

    public static boolean isInstalled(Context c) {
        try {
            File marker = new File(Dirs.resources(c), ".vod-stamp");
            if (!marker.isFile()) return false;
            try (BufferedReader r = new BufferedReader(new FileReader(marker))) { return stamp(c).equals(r.readLine()); }
        } catch (Throwable t) { return false; }
    }

    public static synchronized void run(Context c, Progress p) throws Exception {
        if (isInstalled(c)) return;
        Dirs.save(c); Dirs.mods(c);
        File dest = Dirs.resources(c);
        dest.mkdirs();
        String[] names = c.getAssets().list("geode-resources");
        int total = names == null ? 0 : names.length;
        if (p != null) p.update(0, total, "Preparing folders");
        for (int i = 0; i < total; i++) {
            String n = names[i];
            try (InputStream in = c.getAssets().open("geode-resources/" + n); OutputStream os = new FileOutputStream(new File(dest, n))) {
                byte[] buf = new byte[1 << 16]; int k;
                while ((k = in.read(buf)) > 0) os.write(buf, 0, k);
            }
            if (p != null) p.update(i + 1, total, n);
        }
        try (FileOutputStream f = new FileOutputStream(new File(dest, ".vod-stamp"))) { f.write(stamp(c).getBytes()); }
    }
}
