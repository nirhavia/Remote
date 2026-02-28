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
    private X509Certificate cert;

    public TvPairing(String host, Callback cb) { this.host = host; this.cb = cb; }

    public void start() {
        exec.execute(() -> {
            try {
                // 1. Generate RSA keypair
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                kp = kpg.generateKeyPair();

                // 2. Self-signed cert with BouncyCastle
                X500Name name = new X500Name("CN=YesRemote");
                cert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(name,
                        BigInteger.valueOf(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis() - 86400000L),
                        new Date(System.currentTimeMillis() + 3650L * 86400000L),
                        name, kp.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));

                // 3. TLS with our cert as client cert
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("k", kp.getPrivate(), new char[0], new X509Certificate[]{cert});
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
                in  = new DataInputStream(sock.getInputStream());
                out = sock.getOutputStream();

                // 4. Send PairingRequest message
                // Type 10 = PairingRequest, field1=service_name, field2=client_name
                write(buildMsg(10, encode(1, "YES Remote") + encode(2, "Android")));
                read(); // PairingRequestAck

                // 5. Send Options
                // encoding=HEXADECIMAL(3), preferred_role=ROLE_INPUT(1)
                write(buildMsg(20, encodeVarint(1, 3) + encodeVarint(2, 3)));
                read(); // OptionsAck

                // 6. Send Configuration  
                // encoding=HEXADECIMAL(3)
                write(buildMsg(30, encodeVarint(1, 3)));
                read(); // ConfigurationAck

                // TV now shows PIN
                if (cb != null) cb.onShowPin();

            } catch (Exception e) {
                Log.e(TAG, "Pairing error", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    public void sendPin(String pin) {
        exec.execute(() -> {
            try {
                // Secret = HMAC-SHA256 of PIN using combined cert bytes as key
                // Based on Home Assistant implementation
                byte[] clientCert = cert.getEncoded();
                X509Certificate serverCert = (X509Certificate) sock.getSession().getPeerCertificates()[0];
                byte[] serverCertBytes = serverCert.getEncoded();

                // Combined key = client_cert + server_cert
                byte[] combined = new byte[clientCert.length + serverCertBytes.length];
                System.arraycopy(clientCert, 0, combined, 0, clientCert.length);
                System.arraycopy(serverCertBytes, 0, combined, clientCert.length, serverCertBytes.length);

                // Get PIN code bytes (each digit separately based on HA implementation)
                byte[] pinBytes = new byte[pin.length()];
                for (int i = 0; i < pin.length(); i++) {
                    pinBytes[i] = (byte)(pin.charAt(i) - '0');
                }

                // SHA256(combined + pin_bytes)
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(combined);
                sha.update(pinBytes);
                byte[] secret = sha.digest();

                // Send Secret message (type 40, field1=secret)
                write(buildMsg(40, encodeBytes(1, secret)));
                byte[] ack = read(); // SecretAck

                sock.close();

                if (cb != null) cb.onPaired(
                    kp.getPrivate().getEncoded(),
                    cert.getEncoded()
                );
            } catch (Exception e) {
                Log.e(TAG, "PIN error", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    // Protobuf helpers
    private String encode(int field, String val) throws Exception {
        byte[] b = val.getBytes("UTF-8");
        return (char)(field << 3 | 2) + "" + (char)b.length + new String(b, "ISO-8859-1");
    }
    private String encodeVarint(int field, int val) {
        return "" + (char)(field << 3) + (char)val;
    }
    private String encodeBytes(int field, byte[] val) throws Exception {
        String header = "" + (char)(field << 3 | 2) + (char)val.length;
        return header + new String(val, "ISO-8859-1");
    }
    private byte[] buildMsg(int type, String payload) throws Exception {
        byte[] p = payload.getBytes("ISO-8859-1");
        // Outer: field1=type(varint), field2=payload(bytes)
        byte[] inner = new byte[4 + p.length];
        inner[0] = (char)(1 << 3);       // field1, varint
        inner[1] = (byte) type;           // message type
        inner[2] = (char)(2 << 3 | 2);   // field2, length-delimited
        inner[3] = (byte) p.length;
        System.arraycopy(p, 0, inner, 4, p.length);
        byte[] msg = new byte[inner.length + 2];
        msg[0] = 0;
        msg[1] = (byte) inner.length;
        System.arraycopy(inner, 0, msg, 2, inner.length);
        return msg;
    }
    private void write(byte[] data) throws Exception { out.write(data); out.flush(); }
    private byte[] read() throws Exception {
        byte[] h = new byte[2];
        int r = 0; while(r < 2) r += in.read(h, r, 2-r);
        int len = h[1] & 0xFF;
        byte[] b = new byte[len];
        r = 0; while(r < len) r += in.read(b, r, len-r);
        return b;
    }
}
