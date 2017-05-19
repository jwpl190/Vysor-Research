package com.mzj.vysor;

import android.graphics.Bitmap;

import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.ByteArrayOutputStream;

import com.mzj.vysor.util.AndroidDeviceUtils;

import com.koushikdutta.virtualdisplay.StdOutDevice;
import com.xing.xbase.util.LogUtil;

import static android.content.ContentValues.TAG;

class WebServices {

    static void registerAllServices(AsyncHttpServer httpServer) {
        registerH264(httpServer);
        registerScreenshot(httpServer);
    }

    private static void registerScreenshot(AsyncHttpServer httpServer) {
        httpServer.get("/screenshot.jpg", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    LogUtil.d("start request");
                    long startTime = System.currentTimeMillis();
                    Bitmap bitmap = ScreenShotFb.screenshot();
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bout);
                    bout.flush();
                    response.send("image/jpeg", bout.toByteArray());
                    long endTime = System.currentTimeMillis();
                    LogUtil.d("response time=" + (endTime - startTime));
                } catch (Exception e) {
                    response.code(500);
                    response.send(e.toString());
                }
            }
        });
    }

    private static void registerH264(AsyncHttpServer httpServer) {
        httpServer.get("/h264", new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                System.out.print("start h264" + "\n");
                AndroidDeviceUtils.turnScreenOn();
                response.getHeaders().set("Access-Control-Allow-Origin", "*");
                response.getHeaders().set("Connection", "close");
                response.setClosedCallback(new CompletedCallback() {
                    StdOutDevice device = StdOutDevice.genStdOutDevice(new BufferedDataSink(response));

                    @Override
                    public void onCompleted(Exception ex) {
                        device.stop();
                    }
                });
            }
        });
    }
}
