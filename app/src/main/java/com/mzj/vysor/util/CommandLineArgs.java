package com.mzj.vysor.util;

import java.util.Hashtable;

public class CommandLineArgs {

    public static Hashtable<String, String> parse(String[] array) {
        final Hashtable<String, String> hashtable = new Hashtable<>();
        for (int length = array.length, i = 0; i < length; ++i) {
            final String[] split = array[i].split("=", 2);
            String s = "";
            if (split.length == 2) {
                s = split[1];
            }
            hashtable.put(split[0], s);
        }

        return hashtable;
    }
}
