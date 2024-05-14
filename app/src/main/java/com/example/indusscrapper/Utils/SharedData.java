package com.example.indusscrapper.Utils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public class SharedData {
    public static boolean startedChecking = false;

    public static String getDeviceInfo(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceName = Build.MODEL;

        return deviceName + "-" + androidId;
    }
}
