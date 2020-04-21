package com.example.android.trivialdrivesample.util;

import android.util.Log;

public class IABLogger {

    boolean mDebugLog = false;
    String mDebugTag = "IabHelper";

    void logDebug(String msg) {
        if (mDebugLog) Log.d(mDebugTag, msg);
    }

    void logError(String msg) {
        Log.e(mDebugTag, "In-app billing error: " + msg);
    }

    void logWarn(String msg) {
        Log.w(mDebugTag, "In-app billing warning: " + msg);
    }
}
