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

    public TvPairing(String host, Callback cb) { this.host = host; this.cb = cb; }

    public void start() {
        exec.execute(() -> {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                kp = kpg.generateKeyPair();
                X500Name name = new X500Name("CN=YesRemote");
                clientCert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(
                        name, BigInteger.ONE,
                        new Date(System.currentTimeMillis() - 86400000L),
                        new Date(System.currentTimeMillis() + 3650L * 86400000L),
                        name, kp.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));

                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("k", kp.getPrivate(), new char[0], new X509Certificate[]{clientCert});
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, new char[0]);
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());

                sock = (SSLSocket) ssl.getSocketFactory().createSocket();
                sock.setEnabledProtocols(sock.getSupportedProtocols());
                sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
                sock.connect(new InetSocketAddress(host, PORT), 5000);
                sock.startHandshake();
                in = sock.getInputStream();
                out = sock.getOutputStream();
                Log.d(TAG, "TLS OK: " + sock.getSession().getProtocol());

                sendMsg(10, buildPairingRequest());
                readMsg();
                sendMsg(20, buildOptions());
                readMsg();
                sendMsg(30, buildConfiguration());
                readMsg();
                if (cb != null) cb.onShowPin();
            } catch (Exception e) {
                Log.e(TAG, "start", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    public void sendPin(String pin) {
        exec.execute(() -> {
            try {
                X509Certificate serverCert = (X509Certificate) sock.getSession().getPeerCertificates()[0];
                RSAPublicKey cPub = (RSAPublicKey) clientCert.getPublicKey();
                RSAPublicKey sPub = (RSAPublicKey) serverCert.getPublicKey();
                byte[] cMod = unsigned(cPub.getModulus());
                byte[] sMod = unsigned(sPub.getModulus());

                // תיקון: PIN עם אותיות ומספרים - משתמש ב-UTF-8 bytes ישירות
                byte[] pinBytes = pin.getBytes("UTF-8");

                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(cMod); sha.update(sMod); sha.update(pinBytes);
                sendMsg(40, buildSecret(sha.digest()));
                readMsg();
                sock.close();
                if (cb != null) cb.onPaired(kp.getPrivate().getEncoded(), clientCert.getEncoded());
            } catch (Exception e) {
                Log.e(TAG, "pin", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    private byte[] unsigned(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        if (b[0] == 0) { byte[] t = new byte[b.length-1]; System.arraycopy(b,1,t,0,t.length); return t; }
        return b;
    }
    private byte[] buildPairingRequest() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeStr(b,1,"YES Remote"); writeStr(b,2,"Android"); return b.toByteArray();
    }
    private byte[] buildOptions() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        writeVar(e,1,3); writeBytes(b,1,e.toByteArray()); writeVar(b,2,1); return b.toByteArray();
    }
    private byte[] buildConfiguration() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); writeVar(b,1,3); return b.toByteArray();
    }
    private byte[] buildSecret(byte[] s) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); writeBytes(b,1,s); return b.toByteArray();
    }
    private void writeVar(ByteArrayOutputStream b, int f, int v) { b.write(f<<3); b.write(v&0x7F); }
    private void writeStr(ByteArrayOutputStream b, int f, String s) throws IOException { writeBytes(b,f,s.getBytes("UTF-8")); }
    private void writeBytes(ByteArrayOutputStream b, int f, byte[] v) throws IOException {
        b.write((f<<3)|2); writeRawVar(b,v.length); b.write(v);
    }
    private void writeRawVar(ByteArrayOutputStream b, int v) {
        while((v&~0x7F)!=0){b.write((v&0x7F)|0x80);v>>>=7;} b.write(v);
    }
    private void sendMsg(int t, byte[] p) throws IOException {
        ByteArrayOutputStream w = new ByteArrayOutputStream();
        writeVar(w,1,t); writeBytes(w,2,p);
        byte[] b = w.toByteArray();
        out.write(0x00); out.write(b.length); out.write(b); out.flush();
    }
    private byte[] readMsg() throws IOException {
        in.read(); int len = in.read();
        byte[] b = new byte[len]; int r = 0;
        while(r<len) r+=in.read(b,r,len-r); return b;
    }
}
