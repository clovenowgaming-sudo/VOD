package org.fmod;

import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Lets FMOD decode formats through Android's MediaCodec (used for AAC/M4A and others). */
public class MediaCodec {
    private long mCodecPtr;
    private android.media.MediaCodec mDecoder;
    private MediaExtractor mExtractor;
    private ByteBuffer[] mInputBuffers, mOutputBuffers;
    private boolean mInputFinished, mOutputFinished;
    private int mCurrentOutputBufferIndex = -1;
    private int channelCount, sampleRate;
    private long length;

    public static native long fmodGetSize(long codecPtr);
    public static native int fmodReadAt(long codecPtr, long position, byte[] buffer, int offset, int size);

    public int getChannelCount() { return channelCount; }
    public int getSampleRate() { return sampleRate; }
    public long getLength() { return length; }

    @SuppressWarnings("deprecation")
    public boolean init(long codecPtr) {
        if (Build.VERSION.SDK_INT < 23) return false;
        mCodecPtr = codecPtr;
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(new MediaDataSource() {
                @Override public int readAt(long pos, byte[] buf, int off, int size) throws IOException {
                    int n = fmodReadAt(mCodecPtr, pos, buf, off, size);
                    return n <= 0 ? -1 : n;
                }
                @Override public long getSize() { return fmodGetSize(mCodecPtr); }
                @Override public void close() {}
            });
            if (mExtractor.getTrackCount() < 1) return false;
            MediaFormat f = mExtractor.getTrackFormat(0);
            String mime = f.getString(MediaFormat.KEY_MIME);
            channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            length = f.containsKey(MediaFormat.KEY_DURATION) ? f.getLong(MediaFormat.KEY_DURATION) * sampleRate / 1000000L : 0;
            mExtractor.selectTrack(0);
            mDecoder = android.media.MediaCodec.createDecoderByType(mime);
            mDecoder.configure(f, null, null, 0);
            mDecoder.start();
            mInputBuffers = mDecoder.getInputBuffers();
            mOutputBuffers = mDecoder.getOutputBuffers();
            return true;
        } catch (Throwable t) { return false; }
    }

    @SuppressWarnings("deprecation")
    public int read(byte[] out, int size) {
        if (mDecoder == null) return -1;
        int written = 0;
        try {
            while (written < size && !mOutputFinished) {
                if (mCurrentOutputBufferIndex < 0) {
                    if (!mInputFinished) {
                        int in = mDecoder.dequeueInputBuffer(2000);
                        if (in >= 0) {
                            int n = mExtractor.readSampleData(mInputBuffers[in], 0);
                            if (n < 0) { mDecoder.queueInputBuffer(in, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); mInputFinished = true; }
                            else { mDecoder.queueInputBuffer(in, 0, n, mExtractor.getSampleTime(), 0); mExtractor.advance(); }
                        }
                    }
                    android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
                    int o = mDecoder.dequeueOutputBuffer(bi, 2000);
                    if (o >= 0) {
                        mCurrentOutputBufferIndex = o;
                        ByteBuffer ob = mOutputBuffers[o]; ob.position(bi.offset); ob.limit(bi.offset + bi.size);
                        if ((bi.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) mOutputFinished = true;
                    } else if (o == android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) mOutputBuffers = mDecoder.getOutputBuffers();
                    else if (o == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { MediaFormat nf = mDecoder.getOutputFormat(); channelCount = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT); sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE); }
                }
                if (mCurrentOutputBufferIndex >= 0) {
                    ByteBuffer ob = mOutputBuffers[mCurrentOutputBufferIndex];
                    int n = Math.min(ob.remaining(), size - written);
                    ob.get(out, written, n); written += n;
                    if (!ob.hasRemaining()) { mDecoder.releaseOutputBuffer(mCurrentOutputBufferIndex, false); mCurrentOutputBufferIndex = -1; }
                }
            }
        } catch (Throwable t) { return written > 0 ? written : -1; }
        return written == 0 && mOutputFinished ? -1 : written;
    }

    public void seek(int sample) {
        if (mExtractor == null) return;
        mExtractor.seekTo(sample * 1000000L / Math.max(1, sampleRate), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mDecoder.flush();
        mInputFinished = false; mOutputFinished = false;
        if (mCurrentOutputBufferIndex >= 0) mCurrentOutputBufferIndex = -1;
    }

    public void release() {
        try { if (mDecoder != null) { mDecoder.stop(); mDecoder.release(); } if (mExtractor != null) mExtractor.release(); } catch (Throwable ignored) {}
        mDecoder = null; mExtractor = null;
    }
}
