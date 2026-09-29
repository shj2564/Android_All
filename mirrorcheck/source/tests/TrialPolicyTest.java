import kr.song.peugeot.mirrorcheck.TrialPolicy;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class TrialPolicyTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++; if (!condition) throw new AssertionError(name);
    }
    private static Map<String, String> observed() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("serviceSha256", "8c7b9ee385fe2503dd592b6114436235a1e0bcefd8f6c80b6e9a3478b5d074d9");
        m.put("serviceVersion", "260129-1424"); m.put("serviceCode", "26012914");
        m.put("api", "27"); m.put("hardware", "mt6765"); m.put("model", "psa_5008_2014_2019");
        m.put("manufacturer", "peugeot"); m.put("modelPlatform", "psa"); m.put("configuration", "generic");
        m.put("productCode", "CMZXR62N-U1"); m.put("mcuVersion", "95.08@260715");
        m.put("canBoxType", "raise"); m.put("decoder", "raise-psa");
        m.put("boxVersion", "PSA-RZ-15-0128.212.06-HSE"); m.put("envVersion", "PSA-RZ-15-0128.212.06-HSE");
        return m;
    }
    private static byte[] received() { return new byte[] {0, 0x49, (byte)0xE5, 0, (byte)0xA0, (byte)0xF0, (byte)0xC3, 0x1F, (byte)0x80}; }
    private static byte[] off() { return new byte[] {(byte)0xFD, 5, (byte)0x80, 0x1E, 0, (byte)0xA3}; }
    private static byte[] on() { return new byte[] {(byte)0xFD, 5, (byte)0x80, 0x1E, 1, (byte)0xA4}; }
    private static TrialPolicy.Snapshot snapshot(Map<String, String> m) {
        return new TrialPolicy.Snapshot(m, true, false, received(), off(), on());
    }
    private static void mustReject(Runnable run, String reason) {
        boolean rejected = false;
        try { run.run(); } catch (SecurityException e) { rejected = true; } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, reason);
    }
    public static void main(String[] args) throws Exception {
        final TrialPolicy.Snapshot valid = snapshot(observed());
        check(TrialPolicy.rejection(valid) == null, "Actual v0.2 configuration accepted");
        check(TrialPolicy.rejection(null) != null, "Missing snapshot rejected");
        for (String key : observed().keySet()) {
            Map<String, String> changed = observed(); changed.put(key, "different");
            check(TrialPolicy.rejection(snapshot(changed)) != null, "Reject changed " + key);
            changed.remove(key);
            check(TrialPolicy.rejection(snapshot(changed)) != null, "Reject missing " + key);
        }
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), false, false, received(), off(), on())) != null, "Disconnected");
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), true, true, received(), off(), on())) != null, "Suspended");
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), true, false, null, off(), on())) != null, "No state");
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), true, false, new byte[8], off(), on())) != null, "Wrong state length");
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), true, false, received(), on(), on())) != null, "Wrong OFF encoding");
        check(TrialPolicy.rejection(new TrialPolicy.Snapshot(observed(), true, false, received(), off(), off())) != null, "Wrong ON encoding");
        for (int value = 0; value <= 1; value++) {
            final TrialPolicy.Permit permit = TrialPolicy.authorize(valid, value, 1000);
            check(Arrays.equals(permit.consume(1001), new byte[] {0x1E, (byte)value}), "Fixed setting bytes " + value);
            check(permit.claimed(), "Submitted state " + value);
            mustReject(new Runnable() { public void run() { permit.consume(1002); } }, "No second submission");
        }
        mustReject(new Runnable() { public void run() { TrialPolicy.authorize(valid, 2, 1000); } }, "Invalid target");
        mustReject(new Runnable() { public void run() { TrialPolicy.authorize(valid, -1, 1000); } }, "Read action cannot become write");
        final TrialPolicy.Permit expired = TrialPolicy.authorize(valid, 1, 1000);
        mustReject(new Runnable() { public void run() { expired.consume(6001); } }, "Expired permit");
        check(!expired.claimed(), "Expired never submitted");
        final TrialPolicy.Permit reversedClock = TrialPolicy.authorize(valid, 1, 1000);
        mustReject(new Runnable() { public void run() { reversedClock.consume(999); } }, "Clock reversal");
        final TrialPolicy.Permit cancelled = TrialPolicy.authorize(valid, 1, 1000); cancelled.cancel();
        mustReject(new Runnable() { public void run() { cancelled.consume(1001); } }, "Cancelled permit");
        check(!cancelled.claimed(), "Cancelled never submitted");
        final TrialPolicy.Permit race = TrialPolicy.authorize(valid, 1, 1000);
        final CountDownLatch go = new CountDownLatch(1), done = new CountDownLatch(24);
        final AtomicInteger won = new AtomicInteger();
        for (int i = 0; i < 24; i++) new Thread(new Runnable() { public void run() {
            try { go.await(); race.consume(1001); won.incrementAndGet(); } catch (Exception expected) { } finally { done.countDown(); }
        } }).start();
        go.countDown(); done.await(); check(won.get() == 1, "Concurrent taps claim exactly once");
        race.cancel(); check(race.claimed(), "Cancel cannot misreport a submitted command as unsent");
        System.out.println("PASS: " + checks + " trial-policy checks; host-side only, no Android or vehicle I/O.");
    }
}
