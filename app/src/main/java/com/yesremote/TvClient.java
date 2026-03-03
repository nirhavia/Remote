package com.yesremote;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

public class TvClient {
    private static final String TAG = "TvClient";
    // פורט 6466 = פקודות (לא 6467!)
    private static final int PORT = 6466;
    private static final String PREFS = "tvprefs";

    public interface Listener {
        void onConnected(); void onDisconnected(); void onError(String m);
    }

    public static final int
        KEY_0=7, KEY_1=8, KEY_2=9, KEY_3=10, KEY_4=11,
        KEY_5=12, KEY_6=13, KEY_7=14, KEY_8=15, KEY_9=16,
        KEY_UP=19, KEY_DOWN=20, KEY_LEFT=21, KEY_RIGHT=22,
        KEY_OK=23, KEY_BACK=4, KEY_HOME=3, KEY_MENU=82,
        KEY_POWER=26, KEY_VOL_UP=24, KEY_VOL_DOWN=25,
        KEY_MUTE=164, KEY_CH_UP=166, KEY_CH_DOWN=167,
        KEY_LAST_CHANNEL=229;

    public static int digit(int d) { return KEY_0 + d; }

    private final Context ctx;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private Listener listener;
    private SSLSocket socket;
    private OutputStream out;
    private InputStream inp;
    private volatile boolean connected = false;
    private Thread pingThread;

    public TvClient(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    public void connect(String ip) {
        exec.execute(() -> {
            try {
                KeyManager[] km = loadCert(ip);
                if (km == null) { if (listener!=null) listener.onError("אין certificate"); return; }
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(km, new TrustManager[]{new X509TrustManager(){
                    public void checkClientTrusted(X509Certificate[] c,String a){}
                    public void checkServerTrusted(X509Certificate[] c,String a){}
                    public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                }}, new SecureRandom());
                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.setEnabledProtocols(socket.getSupportedProtocols());
                socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();
                out = socket.getOutputStream();
                inp = socket.getInputStream();
                Log.d(TAG, "TLS OK port 6466");

                // קרא הודעת init מהשרת
                readMsg();

                // שלח Config message 1
                byte[] cfg1 = new byte[]{10,34,8,(byte)238,4,18,29,24,1,34,1,49,42,15,
                    97,110,100,114,111,105,116,118,45,114,101,109,111,116,101,50,5,49,46,48,46,48};
                sendRaw(cfg1);

                // קרא 2 תגובות
                readMsg();
                readMsg();

                // שלח Config message 2
                sendRaw(new byte[]{18,3,8,(byte)238,4});

                connected = true;
                if (listener != null) listener.onConnected();

                // לולאת קריאה - טפל ב-ping/pong
                while (!socket.isClosed()) {
                    byte[] msg = readMsg();
                    if (msg == null) break;
                    // Ping: מתחיל ב-66,6
                    if (msg.length >= 2 && (msg[0] & 0xFF) == 66 && (msg[1] & 0xFF) == 6) {
                        // Pong
                        out.write(new byte[]{74,2,8,25}); out.flush();
                    }
                }
                connected = false;
                if (listener != null) listener.onDisconnected();
            } catch (Exception e) {
                Log.e(TAG, "connect", e);
                connected = false;
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    private void sendRaw(byte[] payload) throws IOException {
        out.write(payload.length);
        out.write(payload);
        out.flush();
    }

    private byte[] readMsg() throws IOException {
        int len = inp.read();
        if (len < 0) return null;
        len &= 0xFF;
        byte[] buf = new byte[len]; int r = 0;
        while (r < len) {
            int n = inp.read(buf, r, len-r);
            if (n < 0) return null;
            r += n;
        }
        return buf;
    }

    private KeyManager[] loadCert(String ip) {
        try {
            android.content.SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
            String keyB64  = p.getString("key_" + ip, null);
            String certB64 = p.getString("cert_" + ip, null);
            if (keyB64 == null || certB64 == null) return null;
            PrivateKey pk = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(keyB64, Base64.DEFAULT)));
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.decode(certB64, Base64.DEFAULT)));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("k", pk, new char[0], new X509Certificate[]{cert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);
            return kmf.getKeyManagers();
        } catch (Exception e) { Log.e(TAG, "loadCert", e); return null; }
    }

    public void savePairing(String ip, byte[] keyBytes, byte[] certBytes) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("key_" + ip,  Base64.encodeToString(keyBytes,  Base64.DEFAULT))
            .putString("cert_" + ip, Base64.encodeToString(certBytes, Base64.DEFAULT))
            .putBoolean("paired_" + ip, true).apply();
    }
    public void clearPairing(String ip) {
        disconnect();
        ctx.getSharedPreferences(PREFS, 0).edit()
            .remove("key_"+ip).remove("cert_"+ip).remove("paired_"+ip).apply();
    }
    public boolean isPaired(String ip) { return ctx.getSharedPreferences(PREFS,0).getBoolean("paired_"+ip,false); }
    public void saveIp(String ip) { ctx.getSharedPreferences(PREFS,0).edit().putString("ip",ip).apply(); }
    public String getSavedIp() { return ctx.getSharedPreferences(PREFS,0).getString("ip",""); }

    public void sendKey(int kc) {
        if (!connected || out == null) return;
        exec.execute(() -> {
            try {
                // Press: [82, 4, 8, KEY, 16, 1]
                sendRaw(new byte[]{82,4,8,(byte)(kc&0xFF),16,1});
                Thread.sleep(80);
                // Release: [82, 4, 8, KEY, 16, 2]
                sendRaw(new byte[]{82,4,8,(byte)(kc&0xFF),16,2});
            } catch (Exception e) {
                connected = false;
                if (listener != null) listener.onDisconnected();
            }
        });
    }
    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
