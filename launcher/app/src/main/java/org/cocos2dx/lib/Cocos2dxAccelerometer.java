package org.cocos2dx.lib;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

public class Cocos2dxAccelerometer implements SensorEventListener {
    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private int intervalUs = SensorManager.SENSOR_DELAY_GAME;
    private boolean enabled = false;

    public Cocos2dxAccelerometer(Context c) {
        context = c;
        sensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public static native void onSensorChanged(float x, float y, float z, long timestamp);

    public void enable() {
        if (accelerometer == null || enabled) return;
        sensorManager.registerListener(this, accelerometer, intervalUs);
        enabled = true;
    }
    public void disable() {
        if (!enabled) return;
        sensorManager.unregisterListener(this);
        enabled = false;
    }
    public void setInterval(float seconds) {
        intervalUs = Math.max(1000, (int) (seconds * 1000000f));
        if (enabled) { disable(); enable(); }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        @SuppressWarnings("deprecation")
        int rot = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        float t;
        switch (rot) {
            case Surface.ROTATION_90: t = x; x = -y; y = t; break;
            case Surface.ROTATION_180: x = -x; y = -y; break;
            case Surface.ROTATION_270: t = x; x = y; y = -t; break;
            default: break;
        }
        onSensorChanged(x, y, z, e.timestamp);
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
