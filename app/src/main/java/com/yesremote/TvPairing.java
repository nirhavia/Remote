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
    private static final String KEY_B64  = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDepppODf8VohOztrrYhgiSMoULt+ljATyxIf48Lti14occli4K5Suu9JzJdvJtLJ2iNeYZ1uWSaiX+Lffy9ii3BV1zYsGTWzhLk00dcsSVwJ9yTYcmCjboMyGEFje5ix0csolB0FsKFG2r/Pzc0WUl0rm8SkgtkDkzuA05bwksY+VERSlnzzkwbLGywORjEFBPPOsrMhQVW2/qomqu0gagVpfPVKvefKPfYYKxwjwz317SYbKMWKU2ywxqD1ECKkP61En3nKn9a8f4VeesabCg+UMRsnUU9KZla9D7FzUXjxd7UOz9IPsvUKhcjv5SlOdOErFdla40tTXh/isW8UyRAgMBAAECggEAAXX6YTS6TbwIgQ9b3nvSppLqSI9mWp3xdgARGPf9uAPKeyeJGc0zIiy8sp2zLYAzcJMF9XlxfCpyIV7P16pBziGc4Vruhjb9AZHv2ZedLpZa9XcIwfkjLxbWy3UR3IfLIxsQx3vEfSZxKcW5KwG8tijRyyh5nMLrfSlh/NR7rOzfu3sNBQPcXw237iw8h6r59gl+OcJF/N85G3ZxFCgAejubJssFS1FrmOH3hMHRFSDzoWrEJEEHZ5Jonx5QD+k5phIim6Puu29to4eSWv8xWHz2LYTT2TCAqBz9uNvGrHc7+QiXBEI9uhXcSCFzclOTeqOVjt6Ud8rMAQXElvnpIQKBgQD5BC7ZryqZc7yqGTzjZ1V1Atqn7IOony3T+YBGWCbmInweCtMcCo6gDW8ez9f7Hc277I5HO3uh2BSVJ1/gWwxaLRTzOYLugF+RvEuV1VfKj7EjtMiAKSUEZ50vdVHSDIHNOpcnJLHSn8r98L/913HLN5UQeMPPvWMo93w59o2ybQKBgQDk5SC62ESK82gAhSyqHo3UvdM4+VLjCktjkiVkR7ycLze3cj7Ur8iO84vAU21TkxJf/8j3NfiaOxpeiZIpE+UFm93kkbkQZ4UGCU9y11bBWIpntZ4g94JUOr4PKAEzRXNt0HcQWqmC2/jx3kZSxYrQ8iMEpHCgVAmKC/uft09MNQKBgFN2cXWI3pBcWGny15OouN3lPQB3p5FG+QdJYxMwzKjp+gvfuO53I2LF3e7H3y5NyP0pxm5do4yVbiCn99ys57D510HGjvn9kQq5v+PShABitQ6ws1sxbzTQvcCAZBIxGvh8oNj/1ZIw+MqwfMlKAtwIHzBMKeVhJvE+MicDWm7pAoGAD9bHIAbNH2xeewK8J317xQfpsNyX3rwcoWRAkCLiq1AdI+WU8XTEhRfXSNS9EPZxZBE4H9stO8bxOS993LfStkOl5CYtTzRKoTNNux2plDGDSk/oBFH8Q8XY1wWmp5ybszKkuo37guGj5WDDLwEabQEEaVLTtFR8YIAZxmCR7S0CgYBYUxGoc3CvhfjSLNUbeSaCDIZbZ6NEnqwLQy1cj5d8dr7nnqu7ePSXlY0Ng1wES9YQniD0C3w6NfxyOuMhxyjeoMH4AC+HVANZP8GcvDEyN2dNN9H7u37MLPkxm1fMuGq7gsecnJOxQlqjCNZ5gJiOZJWxRCEblfDKJeNyEJe1Dw==";
    private static final String CERT_B64 = "MIICtDCCAZygAwIBAgIUaCsm+qVcAZuSoPKPGJ4rKR2aGW8wDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJWWVzUmVtb3RlMB4XDTIzMDEwMTAwMDAwMFoXDTM1MDEwMTAwMDAwMFowFDESMBAGA1UEAwwJWWVzUmVtb3RlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3qaaTg3/FaITs7a62IYIkjKFC7fpYwE8sSH+PC7YteKHHJYuCuUrrvScyXbybSydojXmGdblkmol/i338vYotwVdc2LBk1s4S5NNHXLElcCfck2HJgo26DMhhBY3uYsdHLKJQdBbChRtq/z83NFlJdK5vEpILZA5M7gNOW8JLGPlREUpZ885MGyxssDkYxBQTzzrKzIUFVtv6qJqrtIGoFaXz1Sr3nyj32GCscI8M99e0mGyjFilNssMag9RAipD+tRJ95yp/WvH+FXnrGmwoPlDEbJ1FPSmZWvQ+xc1F48Xe1Ds/SD7L1CoXI7+UpTnThKxXZWuNLU14f4rFvFMkQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQCK848vEvZWUtyLucE1VVG2mmNucj5egQwTwH42EEKIpn99v7ZwFMby4njFYTVKswwy+9K78DkFAHvunwvi53llY9WSCr8DsWxCrplw/8ra2DdOVyMBgnyZEL9kaOz3VIE65b9qXfmz+LdY76XKymuddPHbeKoF48N8q2ccf6vJJF3Dciv6WnZ3GZ9Ej0bsd3XuObfwrvQr2K0NOlwSw3Mln1sizOQYb6kYE45vkiC/BpI5bblJyq53dtVu4S0d2sdesjdrc34Lsp8tWg4vPfh4UukGcesGUZhYZuKOuI7uZiQVAZnuwTUk8kercExgTF44v7MyMn33nPp3E2CzoiWx";

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
        byte[] kd = Base64.decode(KEY_B64,  Base64.DEFAULT);
        byte[] cd = Base64.decode(CERT_B64, Base64.DEFAULT);
        privKey    = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(kd));
        clientCert = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(cd));
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
                in  = sock.getInputStream();
                out = sock.getOutputStream();
                Log.d(TAG, "TLS OK protocol=" + sock.getSession().getProtocol());
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
                byte[] cMod = unsigned(((RSAPublicKey)clientCert.getPublicKey()).getModulus());
                byte[] sMod = unsigned(((RSAPublicKey)srv.getPublicKey()).getModulus());
                byte[] pb = new byte[pin.length()];
                for (int i=0;i<pin.length();i++) pb[i]=(byte)Character.getNumericValue(pin.charAt(i));
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(cMod); sha.update(sMod); sha.update(pb);
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
        byte[] b=n.toByteArray();
        if(b[0]==0){byte[] t=new byte[b.length-1];System.arraycopy(b,1,t,0,t.length);return t;}
        return b;
    }
    private byte[] buildPairingRequest() throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        writeStr(b,1,"YES Remote");writeStr(b,2,"Android");return b.toByteArray();
    }
    private byte[] buildOptions() throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(),e=new ByteArrayOutputStream();
        writeVar(e,1,3);writeBytes(b,1,e.toByteArray());writeVar(b,2,1);return b.toByteArray();
    }
    private byte[] buildConfiguration() throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();writeVar(b,1,3);return b.toByteArray();
    }
    private byte[] buildSecret(byte[] s) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();writeBytes(b,1,s);return b.toByteArray();
    }
    private void writeVar(ByteArrayOutputStream b,int f,int v){b.write(f<<3);b.write(v&0x7F);}
    private void writeStr(ByteArrayOutputStream b,int f,String s) throws IOException{writeBytes(b,f,s.getBytes("UTF-8"));}
    private void writeBytes(ByteArrayOutputStream b,int f,byte[] v) throws IOException{
        b.write((f<<3)|2);writeRawVar(b,v.length);b.write(v);
    }
    private void writeRawVar(ByteArrayOutputStream b,int v){
        while((v&~0x7F)!=0){b.write((v&0x7F)|0x80);v>>>=7;}b.write(v);
    }
    private void sendMsg(int t,byte[] p) throws IOException {
        ByteArrayOutputStream w=new ByteArrayOutputStream();
        writeVar(w,1,t);writeBytes(w,2,p);
        byte[] b=w.toByteArray();
        out.write(0x00);out.write(b.length);out.write(b);out.flush();
    }
    private byte[] readMsg() throws IOException {
        in.read();int len=in.read();
        byte[] b=new byte[len];int r=0;
        while(r<len)r+=in.read(b,r,len-r);return b;
    }
}
