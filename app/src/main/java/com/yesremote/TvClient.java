package com.yesremote;
import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

public class TvClient {
    private static final String TAG = "TvClient";
    private static final int PORT = 6467;
    private static final String PREFS = "tvprefs";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String m);
    }

    public static final int KEY_0=7,KEY_1=8,KEY_2=9,KEY_3=10,KEY_4=11,KEY_5=12,KEY_6=13,KEY_7=14,KEY_8=15,KEY_9=16;
    public static final int KEY_UP=19,KEY_DOWN=20,KEY_LEFT=21,KEY_RIGHT=22,KEY_OK=23;
    public static final int KEY_BACK=4,KEY_HOME=3,KEY_MENU=82,KEY_POWER=26;
    public static final int KEY_VOL_UP=24,KEY_VOL_DOWN=25,KEY_MUTE=164;
    public static final int KEY_CH_UP=166,KEY_CH_DOWN=167;
    public static int digit(int d){return KEY_0+d;}

    private final Context ctx;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private Listener listener;
    private SSLSocket socket;
    private OutputStream out;
    private boolean connected = false;

    public TvClient(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    public void connect(String ip) {
        exec.execute(() -> {
            try {
                TrustManager[] tm = { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};

                // Load saved client cert if paired
                KeyManager[] km = loadClientCert(ip);

                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(km, tm, new SecureRandom());

                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();
                out = socket.getOutputStream();
                connected = true;

                out.write(new byte[]{0x00, 0x04, 0x08, 0x01, 0x10, 0x01});
                out.flush();

                if (listener != null) listener.onConnected();

                InputStream in = socket.getInputStream();
                byte[] buf = new byte[256];
                while (!socket.isClosed()) { if (in.read(buf) < 0) break; }
                connected = false;
                if (listener != null) listener.onDisconnected();

            } catch (Exception e) {
                Log.e(TAG, "Connect error: " + e.getMessage());
                connected = false;
                if (listener != null) listener.onError(e.getMessage() != null ? e.getMessage() : "שגיאת חיבור");
            }
        });
    }

    private KeyManager[] loadClientCert(String ip) {
        try {
            String keyB64 = ctx.getSharedPreferences(PREFS, 0).getString("key_" + ip, null);
            if (keyB64 == null) return null;
            byte[] keyBytes = android.util.Base64.decode(keyB64, android.util.Base64.DEFAULT);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey pk = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keyBytes));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("key", pk, new char[0], new java.security.cert.Certificate[0]);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            return null;
        }
    }

    public void savePairingResult(String ip, byte[] keyBytes) {
        String keyB64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.DEFAULT);
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("key_" + ip, keyB64)
            .putBoolean("paired_" + ip, true)
            .apply();
    }

    public boolean isPaired(String ip) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean("paired_" + ip, false);
    }

    public void sendKey(int kc) {
        if (out == null || !connected) return;
        exec.execute(() -> {
            try { send(kc, 1); Thread.sleep(80); send(kc, 0); }
            catch (Exception e) { connected = false; if (listener != null) listener.onDisconnected(); }
        });
    }

    private void send(int kc, int action) throws Exception {
        byte[] p = {0x08, 0x01, 0x20, (byte)(kc & 0x7F), 0x28, (byte)(action & 0x01)};
        byte[] m = new byte[2 + p.length];
        m[0] = 0; m[1] = (byte) p.length;
        System.arraycopy(p, 0, m, 2, p.length);
        out.write(m); out.flush();
    }

    public void saveIp(String ip) { ctx.getSharedPreferences(PREFS,0).edit().putString("ip",ip).apply(); }
    public String getSavedIp()    { return ctx.getSharedPreferences(PREFS,0).getString("ip",""); }
    public void disconnect() { connected=false; try{if(socket!=null)socket.close();}catch(Exception ignored){} }
}
