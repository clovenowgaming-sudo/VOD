package com.vod.launcher;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Collects everything useful after a failed launch: java crash, Geode crash/log files and logcat. */
public final class CrashReporter {
    private CrashReporter() {}

    private static File flag(Context c) { return new File(c.getFilesDir(), "launching.flag"); }
    private static File javaCrash(Context c) { return new File(c.getFilesDir(), "java-crash.txt"); }

    public static void markStart(Context c) {
        try (FileOutputStream f = new FileOutputStream(flag(c))) { f.write(String.valueOf(System.currentTimeMillis()).getBytes()); } catch (Throwable ignored) {}
        javaCrash(c).delete();
    }
    public static void markClean(Context c) { flag(c).delete(); }
    public static boolean lastLaunchFailed(Context c) { return flag(c).exists() || javaCrash(c).exists(); }

    public static void installJavaHandler(final Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                try (FileOutputStream f = new FileOutputStream(javaCrash(c))) { f.write(("Thread: " + t.getName() + "\n" + sw).getBytes()); }
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private static String tail(File f, int maxChars) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192]; int n;
            while ((n = r.read(buf)) > 0) { sb.append(buf, 0, n); if (sb.length() > maxChars * 2) sb.delete(0, sb.length() - maxChars); }
            if (sb.length() > maxChars) sb.delete(0, sb.length() - maxChars);
            return sb.toString();
        } catch (Throwable t) { return "(cannot read " + f + ": " + t + ")"; }
    }

    private static File newest(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        File best = null;
        for (File f : fs) if (f.isFile() && (best == null || f.lastModified() > best.lastModified())) best = f;
        return best;
    }

    private static String logcat() {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000").redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) { sb.append(line).append('\n'); if (sb.length() > 400000) sb.delete(0, 100000); }
            }
            if (sb.length() > 120000) sb.delete(0, sb.length() - 120000);
            return sb.toString();
        } catch (Throwable t) { return "(logcat failed: " + t + ")"; }
    }

    public static String collect(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("== Vod log ==\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append("), ABI ").append(GameLibs.abi()).append('\n');
        try { sb.append("GD version code: ").append(GameLibs.versionCode(GameLibs.gdPackage(c))).append('\n'); } catch (Throwable t) { sb.append("GD: not found\n"); }
        sb.append("Launcher version: ").append(com.vod.launcher.utils.GeodeUtils.LAUNCHER_VERSION).append("\n\n");

        if (javaCrash(c).exists()) sb.append("== Java crash ==\n").append(tail(javaCrash(c), 20000)).append("\n\n");

        File base = Dirs.base(c);
        File geode = new File(base, "game/geode");
        File crash = newest(new File(geode, "crashlogs"));
        if (crash != null) sb.append("== Geode crash log: ").append(crash.getName()).append(" ==\n").append(tail(crash, 40000)).append("\n\n");
        File log = newest(new File(geode, "logs"));
        if (log != null) sb.append("== Geode log: ").append(log.getName()).append(" ==\n").append(tail(log, 30000)).append("\n\n");

        sb.append("== logcat (this app) ==\n").append(logcat());
        return sb.toString();
    }
}
