
package com.yesremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TvClient {
    private static final String TAG  = "TvClient";
    private static final int    PORT = 6466;
    private static final String PREFS = "tvprefs";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String m);
    }

    // keycodes
    public static final int
        KEY_0=7,  KEY_1=8,  KEY_2=9,  KEY_3=10, KEY_4=11,
        KEY_5=12, KEY_6=13, KEY_7=14, KEY_8=15, KEY_9=16,
        KEY_UP=19, KEY_DOWN=20, KEY_LEFT=21, KEY_RIGHT=22,
        KEY_OK=23, KEY_BACK=4, KEY_HOME=3, KEY_MENU=82,
        KEY_POWER=26,
        KEY_VOL_UP=24, KEY_VOL_DOWN=25, KEY_MUTE=164,
        KEY_CH_UP=166, KEY_CH_DOWN=167,
        KEY_LAST_CHANNEL=229;

    public static int digit(int d) { return KEY_0 + d; }

    private final Context ctx;
    private Listener     listener;
    private SSLSocket    socket;
    private OutputStream out;
    private InputStream  inp;
    private final Object outLock = new Object(); // ← lock לכתיבה
    private volatile boolean connected = false;
    private volatile boolean running   = false;

    public TvClient(Context ctx)           { this.ctx = ctx; }
    public void setListener(Listener l)    { this.listener = l; }
    public boolean isConnected()           { return connected; }

    // ─────────────────────────────────────────────
    // connect - thread חיבור
    // ─────────────────────────────────────────────
    public void connect(final String ip) {
        running = true;
        new Thread(() -> {
            try {
                javax.net.ssl.KeyManager[] km = loadCert(ip);
                if (km == null) { fire(2, "אין certificate"); return; }

                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(km, new TrustManager[]{ TRUST_ALL }, new SecureRandom());

                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.setEnabledProtocols(socket.getSupportedProtocols());
                socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
                socket.connect(new InetSocketAddress(ip, PORT), 8000);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.startHandshake();

                synchronized (outLock) {
                    out = socket.getOutputStream();
                }
                inp = socket.getInputStream();
                Log.d(TAG, "TLS ok");

                // ── handshake ──
                readMsg();   // server info

                sendRaw(new byte[]{10,34,8,(byte)238,4,18,29,24,1,34,1,49,42,15,
                    97,110,100,114,111,105,116,118,45,114,101,109,111,116,101,50,5,49,46,48,46,48});

                socket.setSoTimeout(2000);
                try { for(int i=0;i<5;i++) readMsg(); } catch(Exception ignored){}
                socket.setSoTimeout(0);

                sendRaw(new byte[]{18,3,8,(byte)238,4});

                socket.setSoTimeout(2000);
                try { for(int i=0;i<5;i++) readMsg(); } catch(Exception ignored){}
                socket.setSoTimeout(0);

                connected = true;
                fire(0, null);
                Log.d(TAG, "Ready");

                // ── לולאת קריאה: מטפל ב-ping ישירות ──
                while (running && !socket.isClosed()) {
                    byte[] msg = readMsg();
                    if (msg == null) break;
                    Log.d(TAG, "Recv: " + hex(msg));
                    // Ping = field 8, type LEN = tag 0x42
                    if (msg.length > 0 && (msg[0] & 0xFF) == 0x42) {
                        // Pong = Ping עם byte[0]: 0x42->0x4A (field8->field9)
                        // חייב לשקף בחזרה את אותו val שהשרת שלח!
                        byte[] pongMsg = msg.clone();
                        pongMsg[0] = 0x4A;
                        pong(pongMsg);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "conn err: " + e);
                fire(2, e.getMessage());
            } finally {
                connected = false;
                fire(1, null);
            }
        }, "TvClient-conn").start();
    }

    // ─────────────────────────────────────────────
    // sendKey - thread נפרד, לא חוסם את לולאת הקריאה
    // ─────────────────────────────────────────────
    public void sendKey(final int kc) {
        if (!connected) { Log.w(TAG,"not connected kc="+kc); return; }
        new Thread(() -> {
            try {
                byte[] press   = keyMsg(kc, 1);
                byte[] release = keyMsg(kc, 2);
                synchronized (outLock) {
                    if (out == null) return;
                    out.write(press.length);   out.write(press);
                    out.flush();
                }
                Thread.sleep(80);
                synchronized (outLock) {
                    if (out == null) return;
                    out.write(release.length); out.write(release);
                    out.flush();
                }
                Log.d(TAG, "sent kc=" + kc);
            } catch (Exception e) {
                Log.e(TAG, "sendKey err kc="+kc+": "+e);
            }
        }, "TvClient-key").start();
    }

    // ─────────────────────────────────────────────
    // pong - נשלח ישירות מthread הקריאה, SYNCHRONIZED
    // ─────────────────────────────────────────────
    private void pong(byte[] pongMsg) {
        try {
            synchronized (outLock) {
                if (out == null) return;
                out.write(pongMsg.length);
                out.write(pongMsg);
                out.flush();
            }
            Log.d(TAG, "pong sent: " + hex(pongMsg));
        } catch (Exception e) {
            Log.e(TAG, "pong err: " + e);
        }
    }

    private void sendRaw(byte[] payload) throws IOException {
        synchronized (outLock) {
            out.write(payload.length); out.write(payload); out.flush();
        }
    }

    private byte[] readMsg() throws IOException {
        int len = inp.read();
        if (len < 0) return null;
        byte[] buf = new byte[len & 0xFF];
        int r = 0;
        while (r < buf.length) {
            int n = inp.read(buf, r, buf.length - r);
            if (n < 0) return null;
            r += n;
        }
        return buf;
    }

    private byte[] keyMsg(int kc, int dir) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(0x08); varint(inner, kc);
        inner.write(0x10); varint(inner, dir);
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(0x52); varint(outer, inner.size()); outer.write(inner.toByteArray());
        return outer.toByteArray();
    }

    private void varint(ByteArrayOutputStream b, int v) {
        while ((v & ~0x7F) != 0) { b.write((v & 0x7F)|0x80); v >>>= 7; }
        b.write(v);
    }

    private String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X ", x & 0xFF));
        return s.toString().trim();
    }

    private void fire(int type, String msg) {
        if (listener == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if      (type == 0) listener.onConnected();
            else if (type == 1) listener.onDisconnected();
            else                listener.onError(msg);
        });
    }

    public void disconnect() {
        running = false;
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        synchronized (outLock) { out = null; }
    }

    // ─── SharedPreferences helpers ───
    public void savePairing(String ip, byte[] key, byte[] cert) {
        prefs().edit()
            .putString("key_"+ip,  Base64.encodeToString(key,  Base64.DEFAULT))
            .putString("cert_"+ip, Base64.encodeToString(cert, Base64.DEFAULT))
            .putBoolean("paired_"+ip, true).apply();
    }
    public void clearPairing(String ip) {
        disconnect();
        prefs().edit().remove("key_"+ip).remove("cert_"+ip).remove("paired_"+ip).apply();
    }
    public boolean isPaired(String ip)  { return prefs().getBoolean("paired_"+ip, false); }
    public void    saveIp(String ip)    { prefs().edit().putString("ip", ip).apply(); }
    public String  getSavedIp()         { return prefs().getString("ip", ""); }
    private SharedPreferences prefs()   { return ctx.getSharedPreferences(PREFS, 0); }

    private javax.net.ssl.KeyManager[] loadCert(String ip) {
        try {
            String keyB64  = prefs().getString("key_"+ip,  null);
            String certB64 = prefs().getString("cert_"+ip, null);
            if (keyB64==null||certB64==null) { Log.w(TAG,"no cert for "+ip); return null; }
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

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}
        public void checkServerTrusted(X509Certificate[] c, String a) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };
}
