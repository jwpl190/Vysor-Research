package com.mzj.vysor;

import android.os.Looper;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.virtualdisplay.StdOutDevice;
import com.mzj.vysor.util.AndroidDeviceUtils;
import com.mzj.vysor.util.ServiceLooper;
import com.xing.xbase.util.LogUtil;

public class Main {
    private static double resolution;
    private static AsyncServer server;
    private static String commandLinePassword;

    static {
        Main.resolution = 0.0;
        Main.server = new AsyncServer();
    }

    public static void main(String[] array) {
        try {
            ServiceLooper.prepare();
            AsyncHttpServer asyncHttpServer = new AsyncHttpServer();
            asyncHttpServer.websocket("/screen", new ScreenHandler());
            asyncHttpServer.listen(52174);
            System.out.print("start" + "\n");
//
//            AsyncHttpServer httpServer = new AsyncHttpServer();
//            WebServices.registerAllServices(httpServer);
//            httpServer.listen(server, 52174);
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
            webSocket.setClosedCallback(new CompletedCallback() {
                StdOutDevice device = StdOutDevice.genStdOutDevice(new BufferedDataSink(webSocket));

                @Override
                public void onCompleted(Exception ex) {
                    device.stop();
                }
            });
        }
    }
}
