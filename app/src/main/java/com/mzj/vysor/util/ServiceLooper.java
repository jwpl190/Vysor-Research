package com.mzj.vysor.util;

import android.os.Looper;

public class ServiceLooper {

    private static Looper looper;

    public static void prepare() {
        if (looper == null) {
            Looper.prepare();
        }
        looper = Looper.myLooper();
    }
}
