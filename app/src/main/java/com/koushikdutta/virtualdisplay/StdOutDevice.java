package com.koushikdutta.virtualdisplay;

import android.annotation.TargetApi;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;

import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import com.koushikdutta.async.http.WebSocket;
import com.mzj.vysor.ClientConfig;
import com.mzj.vysor.Main;
import com.xing.xbase.util.FileUtil;

public class StdOutDevice extends EncoderDevice {
    public static final String TAG = StdOutDevice.class.getSimpleName();

    int bitrate;
    ByteBuffer codecPacket;
    OutputBufferCallback outputBufferCallback;
    MediaFormat outputFormat;
    WebSocket mWebSocket;
    private ByteBuffer SPS;
    private ByteBuffer PPS;
    private byte[] H264Header;

    public StdOutDevice(final int n, final int n2, WebSocket webSocket) {
        super("stdout", n, n2);
        bitrate = 500000;
        mWebSocket = webSocket;
    }

    @Override
    public int getBitrate(final int n) {
        return bitrate;
    }

    public ByteBuffer getCodecPacket() {
        return codecPacket.duplicate();
    }

    public MediaFormat getOutputFormat() {
        return outputFormat;
    }

    @Override
    protected EncoderRunnable onSurfaceCreated(final MediaCodec mediaCodec) {
        return new Writer(mediaCodec);
    }

    @TargetApi(19)
    public void requestSyncFrame() {
        final Bundle parameters = new Bundle();
        parameters.putInt("request-sync", 0);
        mMediaCodec.setParameters(parameters);
    }

    @TargetApi(19)
    public void setBitrate(final int bitrate) {
        System.out.print("Bitrate: " + bitrate);
        if (mMediaCodec != null) {
            this.bitrate = bitrate;
            final Bundle parameters = new Bundle();
            parameters.putInt("video-bitrate", bitrate);
            mMediaCodec.setParameters(parameters);
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
            System.out.print("Writer started." + "\n");
            ByteBuffer[] outputBuffers = null;
            int i = 0;
            int n = 0;
            while (i == 0) {
                final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                final int dequeueOutputBuffer = mEncoder.dequeueOutputBuffer(bufferInfo, -1L);
                if (dequeueOutputBuffer >= 0) {
                    if (n == 0) {
                        n = 1;
                        System.out.print("Got first buffer" + "\n");
                    }
                    if (outputBuffers == null) {
                        outputBuffers = mEncoder.getOutputBuffers();
                    }
                    final ByteBuffer byteBuffer = outputBuffers[dequeueOutputBuffer];
                    if ((0x2 & bufferInfo.flags) != 0x0) {
                        byteBuffer.position(bufferInfo.offset);
                        byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        (codecPacket = ByteBuffer.allocate(bufferInfo.size)).put(byteBuffer);
                        codecPacket.flip();
                    }
                    ByteBuffer order = ByteBufferList.obtain(12 + bufferInfo.size).order(ByteOrder.LITTLE_ENDIAN);
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
                    byte[] byteBuffers = generateH264Package(order);
                    try {
                        writeByte(Main.mFile, byteBuffers);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mWebSocket.send(byteBuffers);
                    if (outputBufferCallback != null) {
                        outputBufferCallback.onOutputBuffer(byteBuffer, bufferInfo);
                    }
                    mEncoder.releaseOutputBuffer(dequeueOutputBuffer, false);
                    if ((0x4 & bufferInfo.flags) != 0x0) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                } else {
                    if (dequeueOutputBuffer != -2) {
                        continue;
                    }
                    System.out.print("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED" + "\n");
                    outputFormat = mEncoder.getOutputFormat();
                    System.out.print("output mWidth: " + outputFormat.getInteger("mWidth") + "\n");
                    System.out.print("output mHeight: " + outputFormat.getInteger("mHeight") + "\n");
                }
            }
            System.out.print("Writer done" + "\n");
        }

        public byte[] generateH264Package(ByteBuffer frameData) {
            int frameDataLength = frameData.remaining();
            byte[] frameDataByte = new byte[frameDataLength];
            frameData.get(frameDataByte, 0, frameDataLength);
            if (H264Header != null) {
                byte[] outData = new byte[H264Header.length + frameDataByte.length];
                System.arraycopy(H264Header, 0, outData, 0, H264Header.length);
                System.arraycopy(frameDataByte, 0, outData, H264Header.length, frameDataByte.length);
                return outData;
            }
            return frameDataByte;
        }
    }

    public static StdOutDevice current;

    public static StdOutDevice genStdOutDevice(WebSocket webSocket) {
        if (current != null){
            current.stop();}
        current = null;
        Point point = SurfaceControlVirtualDisplayFactory.getEncodeSize();
        current = new StdOutDevice(point.x, point.y, webSocket);
        if (ClientConfig.resolution != 0.0) {
            current.setUseEncodingConstraints(false);
        }
        System.out.print("Build.VERSION.SDK_INT" + "\n");
        if (Build.VERSION.SDK_INT < 19) {
            current.useSurface(false);
        }

        if (current.supportsSurface()) {
            System.out.print("use virtual display" + "\n");
            final SurfaceControlVirtualDisplayFactory surfaceControlVirtualDisplayFactory =
                    new SurfaceControlVirtualDisplayFactory();
            current.registerVirtualDisplay(surfaceControlVirtualDisplayFactory, 0);
        } else {
            System.out.print("use legacy path" + "\n");
            current.createDisplaySurface();
        }
        return current;
    }

    public static void writeByte(File file, byte[] bytes) throws IOException {
        try {
            // 打开一个随机访问文件流，按读写方式
            RandomAccessFile randomFile = new RandomAccessFile(file.getAbsoluteFile(), "rw");
            // 文件长度，字节数
            long fileLength = randomFile.length();
            //将写文件指针移到文件尾。
            randomFile.seek(fileLength);
            randomFile.write(bytes);
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
