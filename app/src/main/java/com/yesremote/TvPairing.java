package com.yesremote;

import android.util.Log;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

public class TvPairing {
    private static final String TAG = "TvPairing";
    private static final int PORT = 6466;

    public interface Callback {
        void onShowPin();
        void onPaired(byte[] keyBytes, byte[] certBytes);
        void onError(String msg);
    }

    private final String host;
    private final Callback cb;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SSLSocket sock;
    private InputStream in;
    private OutputStream out;
    private KeyPair kp;
    private X509Certificate clientCert;

    public TvPairing(String host, Callback cb) {
        this.host = host;
        this.cb = cb;
    }

    public void start() {
        exec.execute(() -> {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                kp = kpg.generateKeyPair();

                X500Name name = new X500Name("CN=YesRemote");
                clientCert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(
                        name, BigInteger.valueOf(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis() - 86400000L),
                        new Date(System.currentTimeMillis() + 3650L * 86400000L),
                        name, kp.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));

                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("k", kp.getPrivate(), new char[0], new X509Certificate[]{clientCert});
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, new char[0]);

                SSLContext ssl = SSLContext.getInstance("TLSv1.2");
                ssl.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());

                sock = (SSLSocket) ssl.getSocketFactory().createSocket();
                sock.connect(new InetSocketAddress(host, PORT), 5000);
                sock.startHandshake();
                in  = sock.getInputStream();
                out = sock.getOutputStream();

                sendMsg(10, buildPairingRequest());
                readMsg();
                sendMsg(20, buildOptions());
                readMsg();
                sendMsg(30, buildConfiguration());
                readMsg();

                if (cb != null) cb.onShowPin();
            } catch (Exception e) {
                Log.e(TAG, "Pairing start error", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    public void sendPin(String pin) {
        exec.execute(() -> {
            try {
                X509Certificate serverCert = (X509Certificate) sock.getSession().getPeerCertificates()[0];
                RSAPublicKey clientPub = (RSAPublicKey) clientCert.getPublicKey();
                RSAPublicKey serverPub = (RSAPublicKey) serverCert.getPublicKey();
                byte[] clientMod = toUnsignedBytes(clientPub.getModulus());
                byte[] serverMod = toUnsignedBytes(serverPub.getModulus());
                byte[] pinBytes = new byte[pin.length()];
                for (int i = 0; i < pin.length(); i++)
                    pinBytes[i] = (byte) Character.getNumericValue(pin.charAt(i));
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(clientMod); sha.update(serverMod); sha.update(pinBytes);
                byte[] secret = sha.digest();
                sendMsg(40, buildSecret(secret));
                readMsg();
                sock.close();
                if (cb != null) cb.onPaired(kp.getPrivate().getEncoded(), clientCert.getEncoded());
            } catch (Exception e) {
                Log.e(TAG, "PIN error", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    private byte[] toUnsignedBytes(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        if (b[0] == 0) { byte[] t = new byte[b.length-1]; System.arraycopy(b,1,t,0,t.length); return t; }
        return b;
    }

    private byte[] buildPairingRequest() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeString(buf, 1, "YES Remote"); writeString(buf, 2, "Android"); return buf.toByteArray();
    }
    private byte[] buildOptions() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ByteArrayOutputStream enc = new ByteArrayOutputStream();
        writeVarint(enc, 1, 3); writeBytes(buf, 1, enc.toByteArray()); writeVarint(buf, 2, 1); return buf.toByteArray();
    }
    private byte[] buildConfiguration() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(); writeVarint(buf, 1, 3); return buf.toByteArray();
    }
    private byte[] buildSecret(byte[] secret) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(); writeBytes(buf, 1, secret); return buf.toByteArray();
    }
    private void writeVarint(ByteArrayOutputStream buf, int field, int value) {
        buf.write((field << 3) | 0); buf.write(value & 0x7F);
    }
    private void writeString(ByteArrayOutputStream buf, int field, String value) throws IOException {
        writeBytes(buf, field, value.getBytes("UTF-8"));
    }
    private void writeBytes(ByteArrayOutputStream buf, int field, byte[] value) throws IOException {
        buf.write((field << 3) | 2); writeRawVarint(buf, value.length); buf.write(value);
    }
    private void writeRawVarint(ByteArrayOutputStream buf, int value) {
        while ((value & ~0x7F) != 0) { buf.write((value & 0x7F) | 0x80); value >>>= 7; } buf.write(value);
    }
    private void sendMsg(int msgType, byte[] payload) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeVarint(inner, 1, msgType); writeBytes(inner, 2, payload);
        byte[] innerBytes = inner.toByteArray();
        byte[] frame = new byte[2 + innerBytes.length];
        frame[0] = 0x00; frame[1] = (byte) innerBytes.length;
        System.arraycopy(innerBytes, 0, frame, 2, innerBytes.length);
        out.write(frame); out.flush();
    }
    private byte[] readMsg() throws IOException {
        byte[] header = new byte[2]; int r = 0;
        while (r < 2) r += in.read(header, r, 2-r);
        int len = header[1] & 0xFF; byte[] body = new byte[len]; r = 0;
        while (r < len) r += in.read(body, r, len-r); return body;
    }
}
