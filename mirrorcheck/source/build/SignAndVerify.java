import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public final class SignAndVerify {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        FileInputStream input = new FileInputStream(args[0]);
        try { store.load(input, "mirror-demo-only".toCharArray()); } finally { input.close(); }
        PrivateKey key = (PrivateKey) store.getKey("mirror-demo", "mirror-demo-only".toCharArray());
        X509Certificate cert = (X509Certificate) store.getCertificate("mirror-demo");
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("mirror-demo", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(new File(args[1]))
            .setOutputApk(new File(args[2])).setMinSdkVersion(21)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false)
            .setV4SigningEnabled(false).build().sign();
        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[2])).setMinCheckedPlatformVersion(21)
            .setMaxCheckedPlatformVersion(36).build().verify();
        System.out.println("APK verified=" + result.isVerified() + " v1=" + result.isVerifiedUsingV1Scheme()
            + " v2=" + result.isVerifiedUsingV2Scheme());
        for (Object error : result.getErrors()) System.out.println("ERROR " + error);
        for (Object warning : result.getWarnings()) System.out.println("WARNING " + warning);
        if (!result.isVerified()) throw new IllegalStateException("APK verification failed");
    }
}
