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
    private static final int PORT_PAIR = 6466;
    private static final int PORT_REMOTE = 6467;
    private static final String PREFS = "tvprefs";

    public interface Listener {
        void onPairingStarted(String code);
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
    private SSLSocket remoteSocket;
    private OutputStream out;
    private boolean connected = false;

    public TvClient(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    private SSLContext buildSslContext() throws Exception {
        TrustManager[] tm = { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tm, new SecureRandom());
        return ssl;
    }

    public void connect(String ip) {
        exec.execute(() -> {
            // Step 1: try direct remote connection (if already paired)
            try {
                SSLSocket sock = (SSLSocket) buildSslContext().getSocketFactory().createSocket();
                sock.connect(new InetSocketAddress(ip, PORT_REMOTE), 4000);
                sock.startHandshake();
                remoteSocket = sock;
                out = sock.getOutputStream();
                connected = true;
                // Send hello
                out.write(new byte[]{0x00, 0x04, 0x08, 0x01, 0x10, 0x01});
                out.flush();
                if (listener != null) listener.onConnected();
                // Keep-alive read loop
                InputStream in = sock.getInputStream();
                byte[] buf = new byte[256];
                while (!sock.isClosed()) { if (in.read(buf) < 0) break; }
                connected = false;
                if (listener != null) listener.onDisconnected();
            } catch (Exception e) {
                Log.i(TAG, "Direct connect failed, trying pairing: " + e.getMessage());
                // Step 2: pairing flow
                startPairing(ip);
            }
        });
    }

    private void startPairing(String ip) {
        exec.execute(() -> {
            try {
                SSLSocket sock = (SSLSocket) buildSslContext().getSocketFactory().createSocket();
                sock.connect(new InetSocketAddress(ip, PORT_PAIR), 4000);
                sock.startHandshake();

                InputStream in = sock.getInputStream();
                OutputStream out = sock.getOutputStream();

                // Send pairing request
                // Protobuf: PairingRequest { service_name: "YES Remote", client_name: "Android Phone" }
                String serviceName = "YES Remote";
                String clientName  = "Android Phone";
                byte[] sn = serviceName.getBytes("UTF-8");
                byte[] cn = clientName.getBytes("UTF-8");

                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                // field 1: service_name
                payload.write(0x0a); payload.write(sn.length); payload.write(sn);
                // field 2: client_name
                payload.write(0x12); payload.write(cn.length); payload.write(cn);

                byte[] p = payload.toByteArray();
                byte[] msg = new byte[p.length + 2];
                msg[0] = 0; msg[1] = (byte)p.length;
                System.arraycopy(p, 0, msg, 2, p.length);
                out.write(msg); out.flush();

                // Read pairing response - contains the PIN code
                byte[] header = new byte[2];
                if (in.read(header) < 2) throw new Exception("No pairing response");
                int len = header[1] & 0xFF;
                byte[] resp = new byte[len];
                in.read(resp);

                // Extract PIN from response (it's displayed on TV screen)
                // Notify UI to show "Look at TV for PIN"
                if (listener != null) listener.onPairingStarted("הסתכל על מסך הטלוויזיה");

                // Wait for secret (PIN) - TV shows 6-digit code
                // Read secret response
                byte[] secretResp = new byte[256];
                int n = in.read(secretResp);
                Log.d(TAG, "Pairing secret received: " + n + " bytes");

                sock.close();

                // Save paired state and connect on remote port
                saveIp(ip);
                savePaired(ip, true);

                // Now connect to remote port
                connectRemote(ip);

            } catch (Exception e) {
                Log.e(TAG, "Pairing failed: " + e.getMessage());
                connected = false;
                if (listener != null) listener.onError("שגיאת חיבור: " + e.getMessage());
            }
        });
    }

    private void connectRemote(String ip) {
        try {
            SSLSocket sock = (SSLSocket) buildSslContext().getSocketFactory().createSocket();
            sock.connect(new InetSocketAddress(ip, PORT_REMOTE), 4000);
            sock.startHandshake();
            remoteSocket = sock;
            out = sock.getOutputStream();
            connected = true;
            out.write(new byte[]{0x00, 0x04, 0x08, 0x01, 0x10, 0x01});
            out.flush();
            if (listener != null) listener.onConnected();
            InputStream in = sock.getInputStream();
            byte[] buf = new byte[256];
            while (!sock.isClosed()) { if (in.read(buf) < 0) break; }
            connected = false;
            if (listener != null) listener.onDisconnected();
        } catch (Exception e) {
            connected = false;
            if (listener != null) listener.onError(e.getMessage() != null ? e.getMessage() : "שגיאה");
        }
    }

    public void sendKey(int kc) {
        if (out == null) return;
        exec.execute(() -> {
            try { send(kc, 1); Thread.sleep(80); send(kc, 0); }
            catch (Exception e) { connected = false; if (listener != null) listener.onDisconnected(); }
        });
    }

    private void send(int kc, int action) throws Exception {
        byte[] p = {0x08, 0x01, 0x20, (byte)(kc & 0x7F), 0x28, (byte)(action & 0x01)};
        byte[] m = new byte[2 + p.length];
        m[0] = 0; m[1] = (byte)p.length;
        System.arraycopy(p, 0, m, 2, p.length);
        out.write(m); out.flush();
    }

    public void saveIp(String ip) { ctx.getSharedPreferences(PREFS,0).edit().putString("ip",ip).apply(); }
    public String getSavedIp() { return ctx.getSharedPreferences(PREFS,0).getString("ip",""); }
    public void savePaired(String ip, boolean paired) { ctx.getSharedPreferences(PREFS,0).edit().putBoolean("paired_"+ip, paired).apply(); }
    public boolean isPaired(String ip) { return ctx.getSharedPreferences(PREFS,0).getBoolean("paired_"+ip, false); }

    public void disconnect() {
        connected = false;
        try { if (remoteSocket != null) remoteSocket.close(); } catch (Exception ignored) {}
    }
}
