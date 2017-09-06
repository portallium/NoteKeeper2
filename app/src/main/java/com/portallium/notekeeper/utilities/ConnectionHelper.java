package com.portallium.notekeeper.utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectionHelper {

    /**
     * Класс проверяет, есть ли в данный момент соединение с интернетом
     * @param context контекст, из которого метод вызывается
     * @return true, если соединение есть, false иначе
     */
    public static boolean isDeviceOnline(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        //should check null because in airplane mode it will be null
        return (netInfo != null && netInfo.isConnected());
    }
}
