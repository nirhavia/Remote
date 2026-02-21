package com.yesremote;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.yesremote.remote.AndroidTvRemoteClient;

public class RemoteService extends Service {
    public static final String ACTION_KEY = "com.yesremote.SEND_KEY";
    public static final String EXTRA_KEY  = "keycode";

    private AndroidTvRemoteClient client;

    @Override public void onCreate() {
        super.onCreate();
        client = new AndroidTvRemoteClient(this);
        String ip = client.getSavedIp();
        if (!ip.isEmpty()) client.connect(ip);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_KEY.equals(intent.getAction())) {
            int kc = intent.getIntExtra(EXTRA_KEY, -1);
            if (kc >= 0) {
                if (!client.isConnected()) { String ip = client.getSavedIp(); if (!ip.isEmpty()) client.connect(ip); }
                client.sendKey(kc);
            }
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() { super.onDestroy(); client.disconnect(); }
}
