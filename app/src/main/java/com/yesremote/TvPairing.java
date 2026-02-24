package com.yesremote;

import android.util.Base64;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

public class TvPairing {
    private static final String TAG = "TvPairing";
    private static final int PAIR_PORT = 6466;

    public interface Callback {
        void onShowPin();
        void onPaired(byte[] certBytes, byte[] keyBytes);
        void onError(String msg);
    }

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final String host;
    private final Callback callback;
    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;
    private KeyPair keyPair;
    private X509Certificate cert;

    public TvPairing(String host, Callback callback) {
        this.host = host;
        this.callback = callback;
    }

    public void startPairing() {
        exec.execute(() -> {
            try {
                // Generate key pair
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048, new SecureRandom());
                keyPair = kpg.generateKeyPair();

                // Generate self-signed certificate using BouncyCastle
                cert = generateCert(keyPair);

                // Build SSL context with our cert as client cert
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("client", keyPair.getPrivate(), new char[0],
                        new java.security.cert.Certificate[]{cert});

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, new char[0]);

                TrustManager[] tm = {new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};

                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(kmf.getKeyManagers(), tm, new SecureRandom());

                socket = (SSLSocket) ssl.getSocketFactory().createSocket();
                socket.connect(new InetSocketAddress(host, PAIR_PORT), 5000);
                socket.startHandshake();

                in  = socket.getInputStream();
                out = socket.getOutputStream();

                // Send PairingRequest
                sendPairingRequest();
                readMsg(); // ack

                // Send Options
                sendOptions();
                readMsg(); // options response

                // Send Configuration
                sendConfig();
                readMsg(); // config ack

                // TV now shows PIN on screen
                if (callback != null) callback.onShowPin();

            } catch (Exception e) {
                Log.e(TAG, "Pairing error: " + e.getMessage(), e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    public void submitPin(String pin) {
        exec.execute(() -> {
            try {
                // Compute secret from PIN + certificates
                byte[] secret = computeSecret(pin);
                ByteArrayOutputStream p = new ByteArrayOutputStream();
                p.write(0x0a); p.write(secret.length); p.write(secret);
                sendRaw(p.toByteArray());

                byte[] ack = readMsg();
                Log.d(TAG, "Paired! ack=" + ack.length);

                socket.close();

                byte[] certBytes = cert.getEncoded();
                byte[] keyBytes  = keyPair.getPrivate().getEncoded();
                if (callback != null) callback.onPaired(certBytes, keyBytes);

            } catch (Exception e) {
                Log.e(TAG, "Pin submit error: " + e.getMessage(), e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private byte[] computeSecret(String pin) throws Exception {
        // Android TV Remote v2: secret = SHA256(client_cert + server_cert + pin)
        java.security.cert.Certificate[] serverCerts = socket.getSession().getPeerCertificates();
        byte[] clientCertBytes = cert.getEncoded();
        byte[] serverCertBytes = serverCerts[0].getEncoded();
        byte[] pinBytes = pin.getBytes("UTF-8");

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(clientCertBytes);
        md.update(serverCertBytes);
        md.update(pinBytes);
        return md.digest();
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
        sendRaw(new byte[]{0x08, 0x03, 0x10, 0x03});
    }

    private void sendConfig() throws Exception {
        sendRaw(new byte[]{0x08, 0x03});
    }

    private void sendRaw(byte[] payload) throws Exception {
        byte[] msg = new byte[payload.length + 2];
        msg[0] = 0; msg[1] = (byte) payload.length;
        System.arraycopy(payload, 0, msg, 2, payload.length);
        out.write(msg); out.flush();
    }

    private byte[] readMsg() throws Exception {
        byte[] h = new byte[2];
        int r = 0; while (r < 2) r += in.read(h, r, 2-r);
        int len = h[1] & 0xFF;
        if (len == 0) return new byte[0];
        byte[] b = new byte[len];
        r = 0; while (r < len) r += in.read(b, r, len-r);
        return b;
    }

    private X509Certificate generateCert(KeyPair kp) throws Exception {
        X500Name name = new X500Name("CN=YesRemote,O=YesRemote,C=IL");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date from = new Date(System.currentTimeMillis() - 86400000L);
        Date to   = new Date(System.currentTimeMillis() + 10 * 365 * 86400000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, from, to, name, kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .build(kp.getPrivate());

        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
