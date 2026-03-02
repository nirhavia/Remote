package com.yesremote;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

public class TvPairing {
    private static final String TAG = "TvPairing";
    private static final int PORT = 6466;
    private static final String KEY_B64  = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCs5l1Sql1WRNNn8mnnWW9rhTzRAkrnpXrjKwOe6oRWEmFE1x7uP9x70GOPwbw8OKHCvh913He63QQLfyBVfVupKVDbK3HOGQ3Z8b9dUaWDF89uwOd5bVJQIO9qTtEBbapQK7jzJzWS/YtnXqtvYmmCfKAIbtEXemK1A4DFxDAlvc99LTooY8EegcKVBIclG5nhXQo1hVcbLvYIfM8VHAMrhK/O1dufyOodlOYYi/Ac8tFiHiWl6m6QiWKbCFmN5Gp1fE9WOuFYmkRbYV/f2vNH8H14D+eYqEQYSlnnidwfW8LkYDMwv7vkHNP2FNbIV6jTheysxJGll9T29wuxjydNAgMBAAECggEAANUx4+EK3AyEJtRbFZCFDIkywXWiXg/vp0J2HQDXgav1qKWPR5R/1QosQJgCoRj2ESsQmbplypJKn7I4D1Fa7KtUnAmkiFqZ33qI8m50k/dnD2CB0kA9jOyWWqEQjqeJkiNJG8ViPlgBoOOALeQqdUTGZzlUFn8yFIR0zVRkQE+ctaedJvf8hnoOEjoG94hbBSY4GD0JoKFrR/mFLprbieFwkLcuTJA+oAB/Q9HE99ynXTEiaKAurDptLsq9woohmZRqhcZF7S6PbTmnxZ8wWb8h11drcXr9zPQfl1c6Bgxc/nWlkuBGlaTH0NrErBmSFjaIjG43RCW7M+AqEF613QKBgQDd60FhfgCWyxzMDWFNttczf284nU7c1/qT3ElSDQgZs+83SPvf+LOIKCZrGhpMF7xIHpP+38kxGKRwZpMtbga0446+SOLy1tbilT07+u4Jn5NWCJPDO8tuSIApLPtljIXWoWMMpdiEqaXYMy0ZUT05VEnnKFGV3LxdRiy60AF/kwKBgQDHc+xF8i6XbsR8HQx+uJ2zSWfPD5cX3LZ7iNBr5kE16WjGhYRHAtL/5nbqHLFXAXb2D4C+Wg9tQO/3by4rWyfvp4qLOH2/6XHKS1iWBJXjw0aCsTu1VtVO9oIcnlHie0YTD+sC+dwHciBgWUYyn7nG/PlxrXWplK+pNX5nJ8BJnwKBgAPha0FDLMt2PcirqznqqpSx88XvqkNeW3lebsHKjIu2g8ZZtl3SQYFuAk35JOCTwa0ZK8lXLHN5VNbKVGSE+gULvaFCMQXCD/viVDHKT4NHkRH+EGdnkkUZa3RM3xCFhomcRNkhxUl8lfPT4UQCEaoA+VHbeKHAPGL9KScTIBOVAoGBAIxV5EjSvjWOmnE5fzEqdMtROtlV/tmrUjpZaUyCFh/4ut/z0b6lHhEv9zuCNMUjIrC+97b3ZyNYLX/LmpCm8tKM785FUTVW69mKaiojz9MR8urCCWDuV+fXSnUYcEUKt6Nx78mIRGh4xI8GQX4dJHn+RQTXJ5LKK07DdMzgC0vBAoGBALtSdq8f0LuOjJeiGC95uPNSClb4mD71XDevOq7SbQ/rfrit9IJSBUtxhCSZnRJrmJSDNhoVE4Zc1X2EKKn8Poej0/KywYFowFrb9eL9XEQDgSdyNrKJH2hWRA+unpiIFSlvi23R1tXDa4JKK6Rt4teVVEIA4tMg1kdQ374vwYbN";
    private static final String CERT_B64 = "MIICtDCCAZygAwIBAgIUbJPs5F9IELXMIfMYdMSEy+IHKTAwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJWWVzUmVtb3RlMB4XDTIzMDEwMTAwMDAwMFoXDTM1MDEwMTAwMDAwMFowFDESMBAGA1UEAwwJWWVzUmVtb3RlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArOZdUqpdVkTTZ/Jp51lva4U80QJK56V64ysDnuqEVhJhRNce7j/ce9Bjj8G8PDihwr4fddx3ut0EC38gVX1bqSlQ2ytxzhkN2fG/XVGlgxfPbsDneW1SUCDvak7RAW2qUCu48yc1kv2LZ16rb2JpgnygCG7RF3pitQOAxcQwJb3PfS06KGPBHoHClQSHJRuZ4V0KNYVXGy72CHzPFRwDK4SvztXbn8jqHZTmGIvwHPLRYh4lpepukIlimwhZjeRqdXxPVjrhWJpEW2Ff39rzR/B9eA/nmKhEGEpZ54ncH1vC5GAzML+75BzT9hTWyFeo04XsrMSRpZfU9vcLsY8nTQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQCAUO8mzh/fuFM+l9m35OkN+QawVsG9dflfSCBmvPKK4zav+zoI48iiJ7G6WuwkRd2oYpjca/hEjSCFvbFgvWdf788y5a7DNr3Y6+uE/kZDdoRnjaEx3JGYi77Rjoj8AHyIci2XlJVU64AStVafU0BmgkHskv5ZJk9f7Feg4CXeevJbA2m4Uno6Tkkyv9dQLD+8BZY6YNrstGLsSENzQM0cEG06ozayBZ3epLMcGEfcnlB6mWnEe9kNQU5ISP2irHBggwbw1bI0gtgNUX9+hdu3dcFdcGeNx7ipgJs9SdvXRMvK6vDSaiIRavB0bE7faYwxUSqhUJLxQZWmQT1hHl3c";

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
    private PrivateKey privKey;
    private X509Certificate clientCert;

    public TvPairing(String host, Callback cb) { this.host = host; this.cb = cb; }

    private void loadCert() throws Exception {
        privKey = KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(KEY_B64, Base64.DEFAULT)));
        clientCert = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(Base64.decode(CERT_B64, Base64.DEFAULT)));
    }

    public void start() {
        exec.execute(() -> {
            try {
                loadCert();
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("k", privKey, new char[0], new X509Certificate[]{clientCert});
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
                X509Certificate srv = (X509Certificate) sock.getSession().getPeerCertificates()[0];
                byte[] cMod = unsigned(((RSAPublicKey) clientCert.getPublicKey()).getModulus());
                byte[] sMod = unsigned(((RSAPublicKey) srv.getPublicKey()).getModulus());
                // PIN עם אותיות ומספרים - UTF-8 bytes
                byte[] pinBytes = pin.getBytes("UTF-8");
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(cMod); sha.update(sMod); sha.update(pinBytes);
                sendMsg(40, buildSecret(sha.digest()));
                readMsg();
                sock.close();
                if (cb != null) cb.onPaired(
                    Base64.decode(KEY_B64, Base64.DEFAULT),
                    Base64.decode(CERT_B64, Base64.DEFAULT));
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
        ByteArrayOutputStream b = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
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
        while((v&~0x7F)!=0){b.write((v&0x7F)|0x80);v>>>=7;}b.write(v);
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
