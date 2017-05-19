package com.koushikdutta.virtualdisplay;

import android.annotation.TargetApi;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;

import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import com.mzj.vysor.ClientConfig;
import com.xing.xbase.util.LogUtil;

public class StdOutDevice extends EncoderDevice {
    public static final String TAG = StdOutDevice.class.getSimpleName();

    int bitrate;
    ByteBuffer codecPacket;
    OutputBufferCallback outputBufferCallback;
    MediaFormat outputFormat;
    BufferedDataSink sink;

    public StdOutDevice(final int n, final int n2, final BufferedDataSink sink) {
        super("stdout", n, n2);
        this.bitrate = 500000;
        this.sink = sink;
    }

    @Override
    public int getBitrate(final int n) {
        return this.bitrate;
    }

    public ByteBuffer getCodecPacket() {
        return this.codecPacket.duplicate();
    }

    public MediaFormat getOutputFormat() {
        return this.outputFormat;
    }

    @Override
    protected EncoderRunnable onSurfaceCreated(final MediaCodec mediaCodec) {
        return new Writer(mediaCodec);
    }

    @TargetApi(19)
    public void requestSyncFrame() {
        final Bundle parameters = new Bundle();
        parameters.putInt("request-sync", 0);
        this.mMediaCodec.setParameters(parameters);
    }

    @TargetApi(19)
    public void setBitrate(final int bitrate) {
        LogUtil.d("Bitrate: " + bitrate);
        if (this.mMediaCodec != null) {
            this.bitrate = bitrate;
            final Bundle parameters = new Bundle();
            parameters.putInt("video-bitrate", bitrate);
            this.mMediaCodec.setParameters(parameters);
        }
    }

    public void setOutputBufferCallack(final OutputBufferCallback outputBufferCallback) {
        this.outputBufferCallback = outputBufferCallback;
    }

    class Writer extends EncoderRunnable {
        public Writer(final MediaCodec mediaCodec) {
            super(mediaCodec);
        }

        @Override
        protected void encode() throws Exception {
            LogUtil.d("Writer started.");
            ByteBuffer[] outputBuffers = null;
            int i = 0;
            int n = 0;
            while (i == 0) {
                final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                final int dequeueOutputBuffer = this.venc.dequeueOutputBuffer(bufferInfo, -1L);
                if (dequeueOutputBuffer >= 0) {
                    if (n == 0) {
                        n = 1;
                        LogUtil.d("Got first buffer");
                    }
                    if (outputBuffers == null) {
                        outputBuffers = venc.getOutputBuffers();
                    }
                    final ByteBuffer byteBuffer = outputBuffers[dequeueOutputBuffer];
                    if ((0x2 & bufferInfo.flags) != 0x0) {
                        byteBuffer.position(bufferInfo.offset);
                        byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        (codecPacket = ByteBuffer.allocate(bufferInfo.size)).put(byteBuffer);
                        codecPacket.flip();
                    }
                    final ByteBuffer order =
                            ByteBufferList.obtain(12 + bufferInfo.size).order(ByteOrder.LITTLE_ENDIAN);
                    order.putInt(-4 + (12 + bufferInfo.size));
                    order.putInt((int) TimeUnit.MICROSECONDS.toMillis(bufferInfo.presentationTimeUs));
                    int n2;
                    if ((0x1 & bufferInfo.flags) != 0x0) {
                        n2 = 1;
                    } else {
                        n2 = 0;
                    }
                    order.putInt(n2);
                    byteBuffer.position(bufferInfo.offset);
                    byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    order.put(byteBuffer);
                    order.flip();
                    byteBuffer.clear();
                    sink.write(new ByteBufferList(order));
                    if (outputBufferCallback != null) {
                        outputBufferCallback.onOutputBuffer(byteBuffer, bufferInfo);
                    }
                    this.venc.releaseOutputBuffer(dequeueOutputBuffer, false);
                    if ((0x4 & bufferInfo.flags) != 0x0) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                } else if (dequeueOutputBuffer == -3) {
                    LogUtil.d("MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = null;
                } else {
                    if (dequeueOutputBuffer != -2) {
                        continue;
                    }
                    LogUtil.d("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                    outputFormat = venc.getOutputFormat();
                    LogUtil.d("output mWidth: " + outputFormat.getInteger("mWidth"));
                    LogUtil.d("output mHeight: " + outputFormat.getInteger("mHeight"));
                }
            }
            sink.end();
            LogUtil.d("Writer done");
        }
    }

    public static StdOutDevice current;

    public static StdOutDevice genStdOutDevice(BufferedDataSink sink) {
        if (current != null){
            current.stop();}
        current = null;
        Point point = SurfaceControlVirtualDisplayFactory.getEncodeSize();
        current = new StdOutDevice(point.x, point.y, sink);
        if (ClientConfig.resolution != 0.0) {
            current.setUseEncodingConstraints(false);
        }
        if (Build.VERSION.SDK_INT < 19) {
            current.useSurface(false);
        }

        if (current.supportsSurface()) {
            LogUtil.d("use virtual display");
            current.registerVirtualDisplay(new SurfaceControlVirtualDisplayFactory(), 0);
        } else {
            LogUtil.d("use legacy path");
            current.createDisplaySurface();
        }
        return current;
    }
}
