package com.yesremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class RemoteService extends Service {
    private static final String TAG = "RemoteService";
    private static final String CHANNEL_ID = "yes_remote_channel";
    private static final int NOTIF_ID = 1;
    private static final String PREFS = "tvprefs";

    public static final String ACTION = "com.yesremote.ACTION_SEND_KEY";
    public static final String EXTRA  = "keycode";

    private TvClient client;
    private String currentIp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public RemoteService getService() { return RemoteService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        client = new TvClient(this);
        createNotificationChannel();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // פקודה מווידג'ט - אל תגע ב-startForeground
        if (intent != null && ACTION.equals(intent.getAction())) {
            int kc = intent.getIntExtra(EXTRA, -1);
            Log.d(TAG, "Widget key kc=" + kc + " connected=" + client.isConnected());
            if (kc >= 0) {
                if (client.isConnected()) {
                    client.sendKey(kc);
                } else {
                    // התחבר מחדש ושלח את הפקודה אחרי 2 שניות
                    String ip = getSavedIp();
                    if (!ip.isEmpty()) {
                        Log.d(TAG, "Reconnecting to " + ip + " then sending kc=" + kc);
                        connectToTv(ip);
                        final int finalKc = kc;
                        handler.postDelayed(() -> {
                            if (client.isConnected()) client.sendKey(finalKc);
                            else Log.w(TAG, "Still not connected after reconnect");
                        }, 2500);
                    }
                }
            }
            return START_STICKY;
        }

        // הפעלה מ-MainActivity - startForeground מותר כאן
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, buildNotification("מחפש TV..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIF_ID, buildNotification("מחפש TV..."));
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        if (intent != null) {
            String ip = intent.getStringExtra("ip");
            if (ip != null && !ip.isEmpty() && !ip.equals(currentIp)) {
                currentIp = ip;
                saveIp(ip);
                connectToTv(ip);
            } else if (currentIp.isEmpty()) {
                // נסה IP שמור
                String saved = getSavedIp();
                if (!saved.isEmpty()) {
                    currentIp = saved;
                    connectToTv(saved);
                }
            }
        }
        return START_STICKY;
    }

    private void connectToTv(String ip) {
        client.disconnect();
        client.setListener(new TvClient.Listener() {
            public void onConnected() {
                Log.d(TAG, "Connected to " + ip);
                updateNotification("מחובר ל-" + ip);
            }
            public void onDisconnected() {
                Log.d(TAG, "Disconnected, retry in 3s");
                updateNotification("מתחבר מחדש...");
                handler.postDelayed(() -> {
                    if (!currentIp.isEmpty()) connectToTv(currentIp);
                }, 3000);
            }
            public void onError(String m) {
                Log.e(TAG, "Error: " + m);
                handler.postDelayed(() -> {
                    if (!currentIp.isEmpty()) connectToTv(currentIp);
                }, 5000);
            }
        });
        client.connect(ip);
    }

    private String getSavedIp() {
        return getSharedPreferences(PREFS, 0).getString("ip", "");
    }

    private void saveIp(String ip) {
        getSharedPreferences(PREFS, 0).edit().putString("ip", ip).apply();
    }

    public TvClient getClient() { return client; }
    public boolean isConnected() { return client != null && client.isConnected(); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "YES Remote", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("שמור על חיבור לטלוויזיה");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("YES שלט")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (client != null) client.disconnect();
    }
}
