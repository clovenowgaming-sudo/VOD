package org.fmod;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.net.Uri;

/** Support class FMOD's native Android backend expects (org.fmod.FMOD). */
public final class FMOD {
    private static Context gContext;
    private FMOD() {}

    public static void init(Context c) { gContext = c; }
    public static void close() { gContext = null; }
    public static boolean checkInit() { return gContext != null; }
    public static AssetManager getAssetManager() {
        // game assets (music, sfx) are in the Geometry Dash package
        try { return gContext.createPackageContext(com.vod.launcher.GameLibs.GD_PACKAGE, 0).getAssets(); }
        catch (Exception e) { return gContext == null ? null : gContext.getAssets(); }
    }

    private static AudioManager am() { return (AudioManager) gContext.getSystemService(Context.AUDIO_SERVICE); }

    public static int getOutputSampleRate() {
        try { return Integer.parseInt(am().getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)); } catch (Throwable t) { return 48000; }
    }
    public static int getOutputBlockSize() {
        try { return Integer.parseInt(am().getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)); } catch (Throwable t) { return 1024; }
    }
    public static boolean supportsLowLatency() { return gContext != null && gContext.getPackageManager().hasSystemFeature("android.hardware.audio.low_latency"); }
    public static boolean lowLatencyFlag() { return supportsLowLatency(); }
    public static boolean supportsAAudio() { return Build.VERSION.SDK_INT >= 27; }
    public static boolean proAudioFlag() { return gContext != null && gContext.getPackageManager().hasSystemFeature("android.hardware.audio.pro"); }
    public static boolean isBluetoothOn() { try { return am().isBluetoothA2dpOn(); } catch (Throwable t) { return false; } }
    public static AudioDeviceInfo[] getAudioDevices(int flags) {
        if (Build.VERSION.SDK_INT < 23) return new AudioDeviceInfo[0];
        return am().getDevices(flags);
    }
    public static int fileDescriptorFromUri(String uri) {
        try {
            ParcelFileDescriptor pfd = gContext.getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
            return pfd == null ? -1 : pfd.detachFd();
        } catch (Throwable t) { return -1; }
    }
}
