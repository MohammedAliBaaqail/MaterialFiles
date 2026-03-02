package com.sovworks.eds.android;

import android.util.Log;

public class Logger {
    public static final String TAG = "MaterialFilesVeraCrypt";

    public static void log(String message) {
        Log.i(TAG, message);
    }

    public static void debug(String message) {
        Log.d(TAG, message);
    }

    public static void log(Throwable e) {
        Log.e(TAG, "Error", e);
    }
}
