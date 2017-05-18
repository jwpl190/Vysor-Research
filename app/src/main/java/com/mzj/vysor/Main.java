package com.mzj.vysor;

import android.os.Looper;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;

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
            AsyncHttpServer httpServer = new AsyncHttpServer();
            ServiceLooper.prepare();
            WebServices.registerAllServices(httpServer);
            httpServer.listen(server, 52174);
            Looper.loop();
        } catch (Exception e) {
            LogUtil.d(e.toString());
        }
    }
}
