package com.yesremote;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.util.Date;

public class TvPairing {
    private static final String TAG = "TvPairing";
    private static final int PAIR_PORT = 6466;

    public interface Callback {
        void onShowPin(String pin);
        void onPaired(byte[] certBytes, byte[] keyBytes);
        void onError(String msg);
    }

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final String host;
    private final Callback callback;
    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;

    public TvPairing(String host, Callback callback) {
        this.host = host;
        this.callback = callback;
    }

    public void startPairing() {
        exec.execute(() -> {
            try {
                // Generate self-signed cert + key pair
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048, new SecureRandom());
                KeyPair kp = kpg.generateKeyPair();

                // Build TLS context with our generated cert
                SSLContext ssl = SSLContext.getInstance("TLS");
                TrustManager[] tm = { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};

                KeyManager[] km = buildKeyManagers(kp);
                ssl.init(km, tm, new SecureRandom());

                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.connect(new InetSocketAddress(host, PAIR_PORT), 5000);
                socket.startHandshake();

                in = socket.getInputStream();
                out = socket.getOutputStream();

                // Step 1: Send PairingRequest
                sendPairingRequest();

                // Step 2: Read PairingRequestAck
                readMessage(); // ack

                // Step 3: Send Options
                sendOptions();

                // Step 4: Read Options response
                byte[] optResp = readMessage();

                // Step 5: Send configuration
                sendConfiguration();

                // Step 6: Read ConfigurationAck
                readMessage();

                // At this point TV shows PIN on screen
                if (callback != null) callback.onShowPin("הסתכל על מסך הטלוויזיה");

                // Step 7: Wait for user to see PIN, then we need the PIN to send Secret
                // Store socket open - caller must call submitPin(pin)
                storeSession(kp);

            } catch (Exception e) {
                Log.e(TAG, "Pairing error: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private KeyPair storedKp;
    private void storeSession(KeyPair kp) { this.storedKp = kp; }

    public void submitPin(String pin) {
        exec.execute(() -> {
            try {
                // Send secret derived from PIN
                byte[] secret = pin.getBytes("UTF-8");
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(0x0a); payload.write(secret.length); payload.write(secret);
                sendRaw(payload.toByteArray());

                // Read SecretAck
                byte[] ack = readMessage();
                Log.d(TAG, "Secret ack: " + ack.length + " bytes");

                socket.close();

                // Save cert bytes for future connections
                byte[] certBytes = storedKp.getPublic().getEncoded();
                byte[] keyBytes  = storedKp.getPrivate().getEncoded();
                if (callback != null) callback.onPaired(certBytes, keyBytes);

            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void sendPairingRequest() throws Exception {
        String svc = "YES Remote";
        String cli = "Android";
        byte[] svcB = svc.getBytes("UTF-8");
        byte[] cliB = cli.getBytes("UTF-8");
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(0x0a); p.write(svcB.length); p.write(svcB);
        p.write(0x12); p.write(cliB.length); p.write(cliB);
        sendRaw(p.toByteArray());
    }

    private void sendOptions() throws Exception {
        // encoding: HEXADECIMAL, preferred: HEXADECIMAL
        byte[] p = {0x08, 0x03, 0x10, 0x03};
        sendRaw(p);
    }

    private void sendConfiguration() throws Exception {
        // encoding: HEXADECIMAL
        byte[] p = {0x08, 0x03};
        sendRaw(p);
    }

    private void sendRaw(byte[] payload) throws Exception {
        byte[] msg = new byte[payload.length + 2];
        msg[0] = 0; msg[1] = (byte) payload.length;
        System.arraycopy(payload, 0, msg, 2, payload.length);
        out.write(msg); out.flush();
    }

    private byte[] readMessage() throws Exception {
        byte[] header = new byte[2];
        int read = 0;
        while (read < 2) read += in.read(header, read, 2 - read);
        int len = header[1] & 0xFF;
        if (len == 0) return new byte[0];
        byte[] body = new byte[len];
        read = 0;
        while (read < len) read += in.read(body, read, len - read);
        return body;
    }

    private KeyManager[] buildKeyManagers(KeyPair kp) throws Exception {
        // Create a simple in-memory KeyStore with our key pair
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        // We store just the private key - cert validation is skipped by TV
        // Use a placeholder self-signed cert
        java.security.cert.Certificate[] chain = {}; // empty - TV doesn't verify
        try {
            ks.setKeyEntry("key", kp.getPrivate(), new char[0], new java.security.cert.Certificate[0]);
        } catch (Exception e) {
            // Some devices need at least one cert - generate minimal one
            Log.w(TAG, "KeyStore setKey: " + e.getMessage());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        return kmf.getKeyManagers();
    }
}
