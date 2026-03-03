package com.yesremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class RemoteService extends Service {
    public static final String ACTION = "com.yesremote.ACTION_SEND_KEY";
    public static final String EXTRA  = "keycode";

    private static final String TAG = "RemoteService";
    private static final String CHANNEL_ID = "yes_remote_channel";
    private static final int NOTIF_ID = 1;

    private TvClient client;
    private String currentIp = "";
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
        startForeground(NOTIF_ID, buildNotification("מחפש TV..."));
        if (intent != null) {
            // טיפול בלחיצת כפתור מ-widget/discovery
            if (ACTION.equals(intent.getAction())) {
                int kc = intent.getIntExtra(EXTRA, -1);
                if (kc >= 0 && client != null) client.sendKey(kc);
                return START_STICKY;
            }

            String ip = intent.getStringExtra("ip");
            if (ip != null && !ip.equals(currentIp)) {
                currentIp = ip;
                connectToTv(ip);
            }
        }
        return START_STICKY; // מאתחל לבד אם נהרג
    }

    private void connectToTv(String ip) {
        client.disconnect();
        client.setListener(new TvClient.Listener() {
            public void onConnected() {
                Log.d(TAG, "Connected to " + ip);
                updateNotification("מחובר ל-" + ip);
            }
            public void onDisconnected() {
                Log.d(TAG, "Disconnected, reconnecting in 3s...");
                updateNotification("מתחבר מחדש...");
                // התחברות אוטומטית מחדש
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!currentIp.isEmpty()) connectToTv(currentIp);
                }, 3000);
            }
            public void onError(String m) {
                Log.e(TAG, "Error: " + m);
                updateNotification("שגיאה - מנסה שוב...");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!currentIp.isEmpty()) connectToTv(currentIp);
                }, 5000);
            }
        });
        client.connect(ip);
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
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (client != null) client.disconnect();
    }
}
