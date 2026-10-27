package kr.song.peugeot.fm;

import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class PinnedHttps {
    private static javax.net.ssl.SSLSocketFactory factory;

    static HttpsURLConnection open(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setSSLSocketFactory(factory());
        c.setHostnameVerifier((hostname, session) -> true);
        return c;
    }

    private static synchronized javax.net.ssl.SSLSocketFactory factory() throws Exception {
        if (factory != null) return factory;
        final String expected = BridgeConfig.CERT_SHA256;
        X509TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                if (chain == null || chain.length == 0) throw new java.security.cert.CertificateException("no cert");
                try {
                    byte[] d = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                    StringBuilder s = new StringBuilder();
                    for (byte b : d) s.append(String.format("%02X", b));
                    if (!expected.equals(s.toString()))
                        throw new java.security.cert.CertificateException("pin mismatch");
                } catch (java.security.cert.CertificateException e) { throw e; }
                catch (Exception e) { throw new java.security.cert.CertificateException(e); }
            }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{tm}, new java.security.SecureRandom());
        factory = sc.getSocketFactory();
        return factory;
    }
    private PinnedHttps() {}
}
