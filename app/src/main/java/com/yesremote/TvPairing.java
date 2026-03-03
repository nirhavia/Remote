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
    // פורט 6467 = pairing (לא 6466!)
    private static final int PORT = 6467;
    private static final String KEY_B64  = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDKeTyySZZ3B5tL7AxBwpUFtxM8vtVALKI1wtKdbSqzK4+TtMzZPXpBwkFc0zJleSBIBLoiBrQ6FIZkW2uv0MELSMsCU+8ffhYN9C7R/bwE+FL+Q1idRPzFmpapJTlMCEKGqWraI848ciK3fJ65j4KuCLEAw6Y1pqQwBDyBLAzBE6vi3IvO4L7CubS9ChQd9ShAGZQM7KdXgDY9jRCghOfZPuK3FudR1AdzjKikdpilf4UERzYFGD5DWUwdpDsGLxBErF5Q85TlJ6brh2AzY0rQf2eeGgh5tjwQUvgQP0LjHbz9nM+d4bZCtQ1hHniEuW+oD/MxeQhw6qjkQvCdupOlAgMBAAECggEACXhOrbMR6crOCWjGsPuyHySPLofpbvk3dAbC9ZCBzwQCUOEDtMRyl6lHh9kr8gGOkDfCYe2I1++WUpLREFXV9ZpnvlnhJQqvauMpHnK87MmVjiVlu2Na5D4k/k/KpIL9Y5GAeSf0ETEwbP8T6G9tKAkpiDTebQN4ifNkxhDintQghMTFHzPhGd5BFPcyQfPCMc0np000v7iLNrJZGcjZ/1hdnXcQrnmcNnnrfZXRY1fKo/UQ5aoB56Ybr16YLiJRlsDNfXzFiXXT+7tfYHfjobsjZNJxmwddkE9v0mezNT6nspkcPWr45P5Y/BJ4a0yEEDrasWi6ctksDqyWBk+/eQKBgQD/voMjSpqP7KIFYuOuboqVuOCWdBM5s0Dm3IfM9K0gwyBDqaEa/voWgu/fNaCpi9Rcy24ProUAb9oeWBXY7j/C4K4VT0qxqq2NtMZKfnfp0+2Yal41D6Noo3TQQw9DQCkN6ThgzmB/GYyAJ6Zf3/1Qne5rV5bomdnYpuzN6B2kHQKBgQDKrRV/Ua8Iq1gPS22Dl7zoYOalku5ttdhMKhULiXAcFxITgpPUwWc8aTh3CwyOZSx2pPw9LoQT0zQRu5gmhuMRwOozLzI/+bM1NTihTCrH2TLRxgbp3J6KtYxcThgKajJYU4a1jAa7mbgJENeJLkriSAtyaDPbhFUIoFVuEjCHKQKBgQDCTHyXUHPTSuXhj7sJaERz8ez3gaKloNF7VCr8hRwPmw+lOHgE6ZkZh0s02yqABZNHGOs6kM3Ngi1GBog6su/QYCECYaaPCuwmkCRirmjuRqvps051o7bzpdP28ivjXRiT0A+cRM89YSzEpNsbVjK/j+12siod991xY4jf+yyh5QKBgQCeH/oEsnsIHX5/uE6B+5G0D14D0iXZTKWrjq2KqbjhAZLly9uAg0ADHuih3+n08rSFAGWXakI7oW0fZKfpbxWblVJjiq/+v9b0bUh4d49tCmUeyww7yxeaitgub/NLtN0AknIoFE5wcRbnY891RLvB3Ymowemrm4woRcdBMEnSOQKBgQCZ1eK3iQt1IYSUqQw1ebvf+icho1+tUr1RaMUl6h0wDJEpz1qZMhREY9DWR0a9jcJ6M05QJ4dbLPfUCFL695u3JKPMHML6bDEtGgivKNxoZT1lbPq5lNqjitNzzfmn8nI0P4cwq//PUZdE4rp0P4oHMm5hS8BoGC9BR+Qg01lGlg==";
    private static final String CERT_B64 = "MIICszCCAZugAwIBAgITVy8c46h9+P5xuSE/o4HxoNAuJTANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDDAlhdHZyZW1vdGUwHhcNMjMwMTAxMDAwMDAwWhcNMzUwMTAxMDAwMDAwWjAUMRIwEAYDVQQDDAlhdHZyZW1vdGUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDKeTyySZZ3B5tL7AxBwpUFtxM8vtVALKI1wtKdbSqzK4+TtMzZPXpBwkFc0zJleSBIBLoiBrQ6FIZkW2uv0MELSMsCU+8ffhYN9C7R/bwE+FL+Q1idRPzFmpapJTlMCEKGqWraI848ciK3fJ65j4KuCLEAw6Y1pqQwBDyBLAzBE6vi3IvO4L7CubS9ChQd9ShAGZQM7KdXgDY9jRCghOfZPuK3FudR1AdzjKikdpilf4UERzYFGD5DWUwdpDsGLxBErF5Q85TlJ6brh2AzY0rQf2eeGgh5tjwQUvgQP0LjHbz9nM+d4bZCtQ1hHniEuW+oD/MxeQhw6qjkQvCdupOlAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAAeu5fiATvFbBGhpqU1lvQrEjUxLCUh0mXcE3Bs8BpqwcHhwYv/TfCs3ktPxePCDFLklNT+KHbOXQn4GLu6nUT7AM0HL3H17FZNVgPrUc5iQwKFH4ZqfPNvWApyo/0JPA41zY6BlhKWMSgxzY9+wwJoGp+G11aRvI9hchCWC8rKNh57MLR4SqFaW1+VNSLWbSg8vsa1QiHJn9i2JnmCsqPVcHMF1UzVYr4r9kkrgvhehmdADmEV6DmdMgL/gesuhUE9gy9gGXf23WQWOjKtptgQsPHH3OQnVBNYV03hr0H+Tsz49czPOX31bgPdhC+Hp/gtpljunWBV7gIgJj2Z84fg=";

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
                Log.d(TAG, "TLS OK port 6467: " + sock.getSession().getProtocol());

                // 1. PairingRequest
                sendMsg(buildPairingRequest());
                readMsg();
                // 2. Options
                sendMsg(new byte[]{8,2,16,(byte)200,1,(byte)162,1,8,10,4,8,3,16,6,24,1});
                readMsg();
                // 3. Configuration
                sendMsg(new byte[]{8,2,16,(byte)200,1,(byte)242,1,8,10,4,8,3,16,6,16,1});
                readMsg();

                if (cb != null) cb.onShowPin();
            } catch (Exception e) {
                Log.e(TAG, "start", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    public void sendPin(String pin) {
        // PIN הוא 6 תווים HEX, לוקחים 4 האחרונים ומ-decode-ים מ-hex
        exec.execute(() -> {
            try {
                X509Certificate srv = (X509Certificate) sock.getSession().getPeerCertificates()[0];
                RSAPublicKey cPub = (RSAPublicKey) clientCert.getPublicKey();
                RSAPublicKey sPub = (RSAPublicKey) srv.getPublicKey();

                // Secret = SHA256(clientMod + clientExp + serverMod + serverExp + hex2bin(last4))
                byte[] cMod = unsigned(cPub.getModulus());
                byte[] cExp = unsigned(cPub.getPublicExponent());
                byte[] sMod = unsigned(sPub.getModulus());
                byte[] sExp = unsigned(sPub.getPublicExponent());

                // לוקחים 4 תווים אחרונים מה-PIN ומ-decode-ים מ-hex לbytes
                String last4 = pin.substring(pin.length() - 4);
                byte[] pinBytes = hexToBytes(last4);

                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(cMod); sha.update(cExp);
                sha.update(sMod); sha.update(sExp);
                sha.update(pinBytes);
                byte[] secret = sha.digest();

                // שלח secret: [8,2,16,200,1,194,2,34,10,32,SECRET_32_BYTES]
                ByteArrayOutputStream msg = new ByteArrayOutputStream();
                msg.write(new byte[]{8,2,16,(byte)200,1,(byte)194,2,34,10,32});
                msg.write(secret);
                sendMsg(msg.toByteArray());
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

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            out[i/2] = (byte) Integer.parseInt(hex.substring(i, i+2), 16);
        return out;
    }
    private byte[] unsigned(java.math.BigInteger n) {
        byte[] b = n.abs().toByteArray();
        if (b[0] == 0) { byte[] t = new byte[b.length-1]; System.arraycopy(b,1,t,0,t.length); return t; }
        return b;
    }
    private byte[] buildPairingRequest() throws IOException {
        byte[] svc = "atvremote".getBytes("UTF-8");
        byte[] cli = "YesRemote".getBytes("UTF-8");
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(0x0A); inner.write(svc.length); inner.write(svc);
        inner.write(0x12); inner.write(cli.length); inner.write(cli);
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(new byte[]{8,2,16,(byte)200,1,82});
        outer.write(inner.size());
        outer.write(inner.toByteArray());
        return outer.toByteArray();
    }
    private void sendMsg(byte[] msg) throws IOException {
        out.write(msg.length);
        out.write(msg);
        out.flush();
        Log.d(TAG, "Sent " + msg.length + " bytes");
    }
    private byte[] readMsg() throws IOException {
        int len = in.read() & 0xFF;
        byte[] buf = new byte[len]; int r = 0;
        while (r < len) r += in.read(buf, r, len-r);
        Log.d(TAG, "Recv " + len + " bytes");
        return buf;
    }
}
