package com.koushikdutta.virtualdisplay;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.os.Build;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import com.mzj.vysor.renderscript.YuvConverter;
import com.xing.xbase.util.LogUtil;

public class EncoderFeeder {
    private static final String LOGTAG = "Feeder";
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_Y = 120;
    int colorFormat;
    int height;
    int rotation;
    MediaCodec venc;
    int width;

    public EncoderFeeder(final MediaCodec venc, final int width, final int height, final int colorFormat) {
        this.venc = venc;
        this.width = width;
        this.height = height;
        this.colorFormat = colorFormat;
    }

    private void feedMe() throws Exception {
        ByteBuffer[] inputBuffers = null;
        String s;
        if (Build.VERSION.SDK_INT <= 17) {
            s = "android.view.Surface";
        } else {
            s = "android.view.SurfaceControl";
        }
        final Method declaredMethod = Class.forName(s).getDeclaredMethod("screenshot", Integer.TYPE, Integer.TYPE);
        final YuvConverter yPlaneConverter = YuvConverter.createYPlaneConverter();
        YuvConverter uConverter = null;
        YuvConverter vConverter = null;
        YuvConverter uvConverter;
        if (isSemiPlanarYUV(this.colorFormat)) {
            uvConverter = YuvConverter.createUVConverter();
        } else {
            uConverter = YuvConverter.createUConverter();
            vConverter = YuvConverter.createVConverter();
            inputBuffers = null;
            uvConverter = null;
        }
        while (true) {
            final int dequeueInputBuffer = this.venc.dequeueInputBuffer(-1L);
            if (dequeueInputBuffer >= 0) {
                if (inputBuffers == null) {
                    inputBuffers = this.venc.getInputBuffers();
                }
                final ByteBuffer byteBuffer = inputBuffers[dequeueInputBuffer];
                byteBuffer.clear();
                Bitmap scaledBitmap = (Bitmap) declaredMethod.invoke(null, this.width, this.height);
                if (this.rotation != 0) {
                    final Matrix matrix = new Matrix();
                    if (this.rotation == 1) {
                        matrix.postRotate(-90.0f);
                    } else if (this.rotation == 2) {
                        matrix.postRotate(-180.0f);
                    } else if (this.rotation == 3) {
                        matrix.postRotate(-270.0f);
                    }
                    scaledBitmap =
                            Bitmap.createScaledBitmap(Bitmap.createBitmap(scaledBitmap, 0, 0, this.width, this.height, matrix, false),
                                    this.width, this.height, false);
                }
                LogUtil.d("Bitmap info " + scaledBitmap.getWidth() + " " + scaledBitmap.getHeight() + " " +
                        scaledBitmap.getRowBytes());
                final Bitmap scaledBitmap2 = Bitmap.createScaledBitmap(scaledBitmap, this.width / 2, this.height / 2, false);
                int n = yPlaneConverter.convert(scaledBitmap, byteBuffer);
                if (Build.VERSION.SDK_INT < 18 && this.venc.getName().toLowerCase().contains("qcom")) {
                    final int n2 = n % 2048;
                    if (n2 != 0) {
                        final int n3 = 2048 - n2;
                        byteBuffer.position(n3 + byteBuffer.position());
                        n += n3;
                    }
                }
                int n4;
                if (uvConverter != null) {
                    n4 = n + uvConverter.convert(scaledBitmap2, byteBuffer);
                } else {
                    n4 = n + uConverter.convert(scaledBitmap2, byteBuffer) + vConverter.convert(scaledBitmap2, byteBuffer);
                }
                byteBuffer.clear();
                this.venc.queueInputBuffer(dequeueInputBuffer, 0, n4,
                        TimeUnit.MILLISECONDS.toMicros(SystemClock.elapsedRealtime()), 0);
            }
        }
    }

    private static boolean isSemiPlanarYUV(final int n) {
        boolean b;
        switch (n) {
            default: {
                throw new RuntimeException("unknown format " + n);
            }
            case 19:
            case 20: {
                b = false;
                break;
            }
            case 21:
            case 39:
            case 2130706688: {
                b = true;
                break;
            }
        }
        return b;
    }

    public void feed() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    EncoderFeeder.this.feedMe();
                } catch (Exception ex) {
                    LogUtil.d("Error", (Throwable) ex);
                }
            }
        }).start();
    }

    public void setRotation(final int rotation) {
        this.rotation = rotation;
    }
}
