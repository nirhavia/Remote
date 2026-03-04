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
    private static final int PORT = 6466;
    private static final String PREFS = "tvprefs";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String m);
    }

    public static final int
        KEY_0=7,  KEY_1=8,  KEY_2=9,  KEY_3=10, KEY_4=11,
        KEY_5=12, KEY_6=13, KEY_7=14, KEY_8=15, KEY_9=16,
        KEY_UP=19, KEY_DOWN=20, KEY_LEFT=21, KEY_RIGHT=22,
        KEY_OK=23, KEY_BACK=4, KEY_HOME=3, KEY_MENU=82,
        KEY_POWER=26, KEY_VOL_UP=24, KEY_VOL_DOWN=25,
        KEY_MUTE=164, KEY_CH_UP=166, KEY_CH_DOWN=167,
        // KEYCODE_LAST_CHANNEL = 229, אבל YES משתמשת ב-KEYCODE_TV_INPUT = 178
        // נשלח שניהם ונראה מה עובד
        KEY_LAST_CHANNEL=229,
        KEY_TV_INPUT=178;

    public static int digit(int d) { return KEY_0 + d; }

    private final Context ctx;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private Listener listener;
    private SSLSocket socket;
    private volatile OutputStream out;
    private volatile InputStream inp;
    private volatile boolean connected = false;

    public TvClient(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    public void connect(String ip) {
        exec.execute(() -> {
            try {
                KeyManager[] km = loadCert(ip);
                if (km == null) {
                    if (listener != null) listener.onError("אין certificate");
                    return;
                }
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(km, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());
                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.setEnabledProtocols(socket.getSupportedProtocols());
                socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
                socket.connect(new InetSocketAddress(ip, PORT), 8000);
                socket.setKeepAlive(true);  // ← מונע ניתוק idle
                socket.startHandshake();
                out = socket.getOutputStream();
                inp = socket.getInputStream();
                Log.d(TAG, "TLS connected");

                // handshake
                byte[] serverInfo = readMsg();
                Log.d(TAG, "Server info: " + serverInfo.length + " bytes");

                byte[] cfg1 = {10,34,8,(byte)238,4,18,29,24,1,34,1,49,42,15,
                    97,110,100,114,111,105,116,118,45,114,101,109,111,116,101,50,5,49,46,48,46,48};
                writeMsg(cfg1);

                socket.setSoTimeout(2000);
                try { for(int i=0;i<5;i++) { byte[] r=readMsg(); Log.d(TAG,"cfg1:"+bytesToHex(r)); } }
                catch(java.net.SocketTimeoutException ignored){}
                socket.setSoTimeout(0);

                writeMsg(new byte[]{18,3,8,(byte)238,4});

                socket.setSoTimeout(2000);
                try { for(int i=0;i<5;i++) { byte[] r=readMsg(); Log.d(TAG,"cfg2:"+bytesToHex(r)); } }
                catch(java.net.SocketTimeoutException ignored){}
                socket.setSoTimeout(0);

                connected = true;
                if (listener != null) listener.onConnected();
                Log.d(TAG, "✅ Ready!");

                // לולאת ping/pong - thread עצמאי, לא מחכה ל-sendKey
                while (!socket.isClosed()) {
                    byte[] msg = readMsg();
                    if (msg == null) break;
                    // Ping tag = 0x42 (field8, LEN)
                    if (msg.length >= 1 && (msg[0] & 0xFF) == 0x42) {
                        Log.d(TAG, "Ping! sending pong");
                        // Pong שנשלח ישירות על ה-stream - לא דרך synchronized
                        // כי ping/pong חייב להיות מהיר
                        OutputStream o = out;
                        if (o != null) {
                            byte[] pong = {0x4A,0x02,0x08,0x19};
                            o.write(pong.length);
                            o.write(pong);
                            o.flush();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "connect error: " + e.getMessage());
                if (listener != null) listener.onError(e.getMessage());
            } finally {
                connected = false;
                if (listener != null) listener.onDisconnected();
            }
        });
    }

    public void sendKey(int kc) {
        if (!connected || out == null) { Log.w(TAG,"not connected kc="+kc); return; }
        exec.execute(() -> {
            try {
                // שלח press ואז release בלי synchronized ממושך
                byte[] press   = buildKeyMsg(kc, 1);
                byte[] release = buildKeyMsg(kc, 2);
                OutputStream o = out;
                if (o == null) return;
                o.write(press.length);   o.write(press);   o.flush();
                Thread.sleep(80);
                o.write(release.length); o.write(release); o.flush();
                Log.d(TAG, "Key sent: " + kc);
            } catch (Exception e) {
                Log.e(TAG, "sendKey error kc="+kc+": "+e.getMessage());
            }
        });
    }

    private byte[] buildKeyMsg(int keycode, int direction) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(0x08); writeVarint(inner, keycode);
        inner.write(0x10); writeVarint(inner, direction);
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(0x52);
        writeVarint(outer, inner.size());
        outer.write(inner.toByteArray());
        return outer.toByteArray();
    }

    private void writeVarint(ByteArrayOutputStream b, int v) {
        while ((v & ~0x7F) != 0) { b.write((v & 0x7F) | 0x80); v >>>= 7; }
        b.write(v);
    }

    private void writeMsg(byte[] payload) throws IOException {
        out.write(payload.length); out.write(payload); out.flush();
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

    private String bytesToHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte v : b) sb.append(String.format("%02X ", v&0xFF));
        return sb.toString().trim();
    }

    private KeyManager[] loadCert(String ip) {
        try {
            android.content.SharedPreferences p = ctx.getSharedPreferences(PREFS,0);
            String keyB64  = p.getString("key_"+ip, null);
            String certB64 = p.getString("cert_"+ip, null);
            if (keyB64==null||certB64==null) { Log.w(TAG,"No cert for "+ip); return null; }
            PrivateKey pk = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(keyB64,Base64.DEFAULT)));
            X509Certificate cert = (X509Certificate)CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.decode(certB64,Base64.DEFAULT)));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null,null);
            ks.setKeyEntry("k",pk,new char[0],new X509Certificate[]{cert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks,new char[0]);
            return kmf.getKeyManagers();
        } catch(Exception e){Log.e(TAG,"loadCert",e);return null;}
    }

    public void savePairing(String ip,byte[] keyBytes,byte[] certBytes) {
        ctx.getSharedPreferences(PREFS,0).edit()
            .putString("key_"+ip, Base64.encodeToString(keyBytes, Base64.DEFAULT))
            .putString("cert_"+ip,Base64.encodeToString(certBytes,Base64.DEFAULT))
            .putBoolean("paired_"+ip,true).apply();
    }
    public void clearPairing(String ip) {
        disconnect();
        ctx.getSharedPreferences(PREFS,0).edit()
            .remove("key_"+ip).remove("cert_"+ip).remove("paired_"+ip).apply();
    }
    public boolean isPaired(String ip) {
        return ctx.getSharedPreferences(PREFS,0).getBoolean("paired_"+ip,false);
    }
    public void saveIp(String ip){ctx.getSharedPreferences(PREFS,0).edit().putString("ip",ip).apply();}
    public String getSavedIp(){return ctx.getSharedPreferences(PREFS,0).getString("ip","");}
    public void disconnect(){
        connected=false;
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
    }
}
