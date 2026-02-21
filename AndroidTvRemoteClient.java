package com.yesremote.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AndroidTvRemoteClient {

    private static final String TAG = "YesRemote";
    private static final int REMOTE_PORT = 6467;
    private static final String PREFS = "yes_prefs";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String msg);
    }

    // Android KeyEvent codes
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_1 = 8;
    public static final int KEYCODE_2 = 9;
    public static final int KEYCODE_3 = 10;
    public static final int KEYCODE_4 = 11;
    public static final int KEYCODE_5 = 12;
    public static final int KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14;
    public static final int KEYCODE_8 = 15;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_DPAD_UP     = 19;
    public static final int KEYCODE_DPAD_DOWN   = 20;
    public static final int KEYCODE_DPAD_LEFT   = 21;
    public static final int KEYCODE_DPAD_RIGHT  = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_BACK        = 4;
    public static final int KEYCODE_HOME        = 3;
    public static final int KEYCODE_MENU        = 82;
    public static final int KEYCODE_VOLUME_UP   = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_VOLUME_MUTE = 164;
    public static final int KEYCODE_CHANNEL_UP  = 166;
    public static final int KEYCODE_CHANNEL_DOWN= 167;
    public static final int KEYCODE_POWER       = 26;

    public static int digitKeycode(int d) { return KEYCODE_0 + d; }

    private final Context ctx;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private Listener listener;
    private SSLSocket socket;
    private OutputStream out;
    private boolean connected = false;

    public AndroidTvRemoteClient(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    public void connect(String ip) {
        exec.execute(() -> {
            try {
                TrustManager[] trustAll = { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, trustAll, new SecureRandom());

                SSLSocket sock = (SSLSocket) ssl.getSocketFactory().createSocket();
                sock.connect(new InetSocketAddress(ip, REMOTE_PORT), 5000);
                sock.startHandshake();

                socket = sock;
                out = sock.getOutputStream();
                connected = true;

                // Send hello
                byte[] hello = {0x00, 0x04, 0x08, 0x01, 0x10, 0x01};
                out.write(hello);
                out.flush();

                if (listener != null) listener.onConnected();

                // Read loop
                InputStream in = sock.getInputStream();
                byte[] buf = new byte[256];
                while (!sock.isClosed()) {
                    if (in.read(buf) < 0) break;
                }
            } catch (Exception e) {
                Log.e(TAG, "connect error: " + e.getMessage());
                connected = false;
                if (listener != null) listener.onError(e.getMessage() != null ? e.getMessage() : "שגיאת חיבור");
            }
        });
    }

    public void sendKey(int keycode) {
        if (out == null) return;
        exec.execute(() -> {
            try {
                // Android TV Remote v2 key event message
                sendKeyEvent(keycode, 1); // DOWN
                Thread.sleep(80);
                sendKeyEvent(keycode, 0); // UP
            } catch (Exception e) {
                connected = false;
                if (listener != null) listener.onDisconnected();
            }
        });
    }

    private void sendKeyEvent(int keycode, int action) throws Exception {
        // Protobuf: field1(event_type=1), field4(keycode), field5(action)
        byte kc = (byte)(keycode & 0x7F);
        byte ac = (byte)(action & 0x01);
        byte[] payload = {0x08, 0x01, 0x20, kc, 0x28, ac};
        byte[] msg = new byte[2 + payload.length];
        msg[0] = 0;
        msg[1] = (byte) payload.length;
        System.arraycopy(payload, 0, msg, 2, payload.length);
        out.write(msg);
        out.flush();
    }

    public void saveIp(String ip) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("ip", ip).apply();
    }

    public String getSavedIp() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ip", "");
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
