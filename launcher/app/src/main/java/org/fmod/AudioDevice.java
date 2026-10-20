package org.fmod;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Java AudioTrack fallback output used by FMOD when AAudio/OpenSL are unavailable. */
public class AudioDevice {
    private AudioTrack track;

    public boolean init(int channelConfigIndex, int sampleRate, int bufferLength, int bufferCount) {
        int ch = channelConfigIndex == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int nch = channelConfigIndex == 1 ? 1 : 2;
        int bytes = Math.max(AudioTrack.getMinBufferSize(sampleRate, ch, AudioFormat.ENCODING_PCM_16BIT), bufferLength * bufferCount * nch * 2);
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, ch, AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STREAM);
            track.play();
            return true;
        } catch (Throwable t) { track = null; return false; }
    }
    public void write(short[] data, int samples) { if (track != null) track.write(data, 0, samples); }
    public void close() { if (track != null) { try { track.stop(); } catch (Throwable ignored) {} track.release(); track = null; } }
}
