package com.mzj.vysor;

import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Looper;
import android.view.Surface;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.virtualdisplay.StdOutDevice;
import com.koushikdutta.virtualdisplay.SurfaceControlVirtualDisplayFactory;
import com.koushikdutta.virtualdisplay.VirtualDisplay;
import com.mzj.vysor.util.AndroidDeviceUtils;
import com.mzj.vysor.util.ServiceLooper;
import com.xing.xbase.util.FileUtil;
import com.xing.xbase.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static double resolution;
    private static AsyncServer server;
    private static String commandLinePassword;
    private static ScreenRecorder mRecorder;
    public static File mFile;

    static {
        Main.resolution = 0.0;
        Main.server = new AsyncServer();
    }

    public static void main(String[] array) {
        try {
            ServiceLooper.prepare();
            AsyncHttpServer asyncHttpServer = new AsyncHttpServer();
            asyncHttpServer.websocket("/h264", new ScreenHandler());
            asyncHttpServer.listen(52174);

            Point point = SurfaceControlVirtualDisplayFactory.getEncodeSize();
            Param.ScreenWIDTH = point.x;
            Param.ScreenHEIGHT = point.y;

//            mRecorder = new ScreenRecorder();
//            AsyncHttpServer httpServer = new AsyncHttpServer();
//            httpServer.websocket("/h264", mRecorder);
//            httpServer.listen(52174);
//            mRecorder.start();
            FileUtil.init("111");
            mFile = new File(Environment.getExternalStorageDirectory() + "/" + "1.264");
            if (mFile.exists()) {
                mFile.delete();
            }
            try {
                mFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

//            AsyncHttpServer httpServer = new AsyncHttpServer();
//            WebServices.registerAllServices(httpServer);
//            httpServer.listen(server, 52174);

            System.out.print("start" + "\n");
            Looper.loop();
        } catch (Exception e) {
            LogUtil.d(e.toString());
        }
    }

    private static class ScreenHandler implements AsyncHttpServer.WebSocketRequestCallback {

        @Override
        public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
            System.out.println("connected" + "\n");

            AndroidDeviceUtils.turnScreenOn();
            StdOutDevice.genStdOutDevice(webSocket);
        }
    }

    public static class ScreenRecorder extends Thread implements AsyncHttpServer.WebSocketRequestCallback {
        private MediaCodec mEncoder;
        private Surface mSurface;
        private AtomicBoolean mQuit = new AtomicBoolean(false);
        private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        private VirtualDisplay mVirtualDisplay;
        private WebSocket mWebSocket;
        private boolean isconnect;
        private boolean s = true;
        private ByteBuffer SPS;
        private ByteBuffer PPS;
        private byte[] H264Header;

        public final void quit() {
            mQuit.set(true);
        }

        @Override
        public void run() {
            try {
                try {
                    prepareEncoder();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mVirtualDisplay = (new SurfaceControlVirtualDisplayFactory()).createVirtualDisplay("sharescreen", Param.ScreenWIDTH, Param.ScreenHEIGHT, 0, 3, mSurface, null);
                recordVirtualDisplay();
            } finally {
                release();
            }
        }

        public void recordVirtualDisplay() {
            System.out.print("开始发送视频流" + "\n");
            while (!mQuit.get()) {
                int index = mEncoder.dequeueOutputBuffer(mBufferInfo, Param.TIMEOUT_US);
                System.out.print(index + "\n");
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = mEncoder.getOutputFormat();
                    SPS = outputFormat.getByteBuffer("csd-0");    // SPS
                    PPS = outputFormat.getByteBuffer("csd-1");    // PPS
                    int spslength = SPS.remaining();
                    int ppslength = PPS.remaining();

                    byte[] header = new byte[spslength + ppslength];
                    SPS.get(header, 0, spslength);
                    PPS.get(header, spslength, ppslength);
                    H264Header = header;
                } else if (index >= 0) {
                    ByteBuffer encodedData;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        encodedData = mEncoder.getOutputBuffer(index);
                    } else {
                        encodedData = mEncoder.getOutputBuffers()[index];
                    }
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mBufferInfo.size = 0;
                    }
                    if (mBufferInfo.size == 0) {
                        encodedData = null;
                    }
                    if (encodedData != null) {
                        encodedData.position(mBufferInfo.offset);
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                        if (mWebSocket != null && mWebSocket.isOpen()) {
                            if (!isconnect) {
                                System.out.print("连接中" + "\n");
                                isconnect = true;
                            }
                            byte[] byteBuffer = generateH264Package(encodedData);
                            System.out.print("byteBuffer.length : " + byteBuffer.length + "\n");
//                        try {
//                            com.shangluo.sharescreen.util.FileUtil.writeByte(mFile,byteBuffer);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
                            mWebSocket.send(byteBuffer);
                        } else if (mWebSocket != null && !mWebSocket.isOpen()) {
                            if (isconnect) {
                                System.out.print("连接已断开" + "\n");
                                isconnect = false;
                            }
                        }
                    }
                    mEncoder.releaseOutputBuffer(index, false);
                }
            }
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

        public void prepareEncoder() throws IOException {
            //MediaFormat这个类是用来定义视频格式相关信息的
            //video/avc,这里的avc是高级视频编码Advanced Video Coding
            //mWidth和mHeight是视频的尺寸，这个尺寸不能超过视频采集时采集到的尺寸，否则会直接crash
            MediaFormat format = MediaFormat.createVideoFormat(Param.MIME_TYPE, Param.ScreenWIDTH, Param.ScreenHEIGHT);
            //COLOR_FormatSurface这里表明数据将是一个graphicbuffer元数据
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            //设置码率，通常码率越高，视频越清晰，但是对应的视频也越大，这个值我默认设置成了2000000，也就是通常所说的2M
            format.setInteger(MediaFormat.KEY_BIT_RATE, Param.BITRATE);
            //设置帧率，通常这个值越高，视频会显得越流畅，一般默认我设置成30，你最低可以设置成24，不要低于这个值，低于24会明显卡顿
            format.setInteger(MediaFormat.KEY_FRAME_RATE, Param.FRAME_RATE);
            //IFRAME_INTERVAL是指的帧间隔，这是个很有意思的值，它指的是，关键帧的间隔时间。通常情况下，你设置成多少问题都不大。
            //比如你设置成10，那就是10秒一个关键帧。但是，如果你有需求要做视频的预览，那你最好设置成1
            //因为如果你设置成10，那你会发现，10秒内的预览都是一个截图
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Param.IFRAME_INTERVAL);
            //创建一个MediaCodec的实例
            mEncoder = MediaCodec.createEncoderByType(Param.MIME_TYPE);
            //定义这个实例的格式，也就是上面我们定义的format，其他参数不用过于关注
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //这一步非常关键，它设置的，是MediaCodec的编码源，也就是说，我要告诉mEncoder，你给我解码哪些流。
            //很出乎大家的意料，MediaCodec并没有要求我们传一个流文件进去，而是要求我们指定一个surface
            //而这个surface，其实就是我们在上一讲MediaProjection中用来展示屏幕采集数据的surface
            mSurface = mEncoder.createInputSurface();
            mEncoder.start();
        }

        public void release() {
            if (mEncoder != null) {
                try {
                    mEncoder.stop();
                    mEncoder.release();
                    mEncoder = null;
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
        }

        @Override
        public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
            mWebSocket = webSocket;
            LogUtil.e("{width:" + Param.ScreenWIDTH + ",height:" + Param.ScreenHEIGHT + "}");
        }
    }
}
