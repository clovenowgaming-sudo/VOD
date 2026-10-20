package com.vod.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds the installed Geometry Dash, extracts its native libs and loads everything in the right order. */
public final class GameLibs {
    public static final String GD_PACKAGE = "com.robtopx.geometryjump";
    public static final String TAG = "VodLauncher";
    private static boolean loaded = false;

    private GameLibs() {}

    public static boolean is64() { return Process.is64Bit(); }
    public static String abi() { return is64() ? "arm64-v8a" : "armeabi-v7a"; }
    public static String loaderLibName() { return is64() ? "libGeode.android64.so" : "libGeode.android32.so"; }

    public static PackageInfo gdPackage(Context c) throws PackageManager.NameNotFoundException {
        return c.getPackageManager().getPackageInfo(GD_PACKAGE, 0);
    }

    public static boolean isGdInstalled(Context c) {
        try { gdPackage(c); return true; } catch (Exception e) { return false; }
    }

    @SuppressWarnings("deprecation")
    public static long versionCode(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }

    /** GD libs: prefer the extracted native lib dir, otherwise pull them out of the (split) APKs. */
    private static File findLib(Context ctx, ApplicationInfo gd, long ver, String name) throws Exception {
        File direct = new File(gd.nativeLibraryDir, name);
        if (direct.isFile() && direct.length() > 0) return direct;

        File outDir = new File(ctx.getFilesDir(), "gdlibs/" + ver + "/" + abi());
        File out = new File(outDir, name);
        if (out.isFile() && out.length() > 0) return out;
        outDir.mkdirs();

        java.util.ArrayList<String> apks = new java.util.ArrayList<>();
        apks.add(gd.sourceDir);
        if (gd.splitSourceDirs != null) for (String s : gd.splitSourceDirs) apks.add(s);
        String entryName = "lib/" + abi() + "/" + name;
        for (String apk : apks) {
            try (ZipFile z = new ZipFile(apk)) {
                ZipEntry e = z.getEntry(entryName);
                if (e == null) continue;
                try (InputStream in = z.getInputStream(e); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                out.setReadable(true, false);
                return out;
            }
        }
        throw new java.io.FileNotFoundException(name + " not found in Geometry Dash (" + abi() + "). "
                + "Is your Geometry Dash build " + (is64() ? "64-bit" : "32-bit") + "?");
    }

    /** Copies the loader's resources (packaged by CI into assets/geode-resources) next to the game dir. */
    private static void installResources(Context ctx, long stamp) throws Exception {
        File dest = new File(com.vod.launcher.utils.GeodeUtils.getBaseDirectory(), "game/geode/resources/geode.loader");
        File marker = new File(dest, ".vod-stamp");
        String want = String.valueOf(stamp) + ":" + ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;
        if (marker.isFile()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(marker))) {
                if (want.equals(r.readLine())) return;
            }
        }
        dest.mkdirs();
        String[] names = ctx.getAssets().list("geode-resources");
        if (names != null) {
            for (String n : names) {
                try (InputStream in = ctx.getAssets().open("geode-resources/" + n); OutputStream os = new FileOutputStream(new File(dest, n))) {
                    byte[] buf = new byte[1 << 16]; int c;
                    while ((c = in.read(buf)) > 0) os.write(buf, 0, c);
                }
            }
        }
        try (FileOutputStream f = new FileOutputStream(marker)) { f.write(want.getBytes()); }
    }

    public static synchronized void load(Context ctx) throws Exception {
        if (loaded) return;
        PackageInfo pi = gdPackage(ctx);
        ApplicationInfo gd = pi.applicationInfo;
        long ver = versionCode(pi);

        // tell the loader which GD version this is (see Loader::Impl::getGameVersion)
        try (FileOutputStream f = new FileOutputStream(new File(ctx.getFilesDir(), "game_version.txt"))) {
            f.write(String.valueOf(ver).getBytes());
        }

        installResources(ctx, ver);

        File fmod = findLib(ctx, gd, ver, "libfmod.so");
        File cocos = findLib(ctx, gd, ver, "libcocos2dcpp.so");

        System.loadLibrary("c++_shared");
        LauncherFix.loadLibrary();
        System.load(fmod.getAbsolutePath());
        System.load(cocos.getAbsolutePath());

        LauncherFix.setOriginalDataPath("/data/data/" + GD_PACKAGE + "/");
        LauncherFix.setDataPath(ctx.getApplicationInfo().dataDir + "/");
        LauncherFix.performPatches();

        File loader = new File(ctx.getApplicationInfo().nativeLibraryDir, loaderLibName());
        if (!loader.isFile()) throw new java.io.FileNotFoundException("Vod loader missing: " + loader);
        Log.i(TAG, "loading " + loader);
        System.load(loader.getAbsolutePath());
        loaded = true;
    }
}
