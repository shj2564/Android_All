package kr.song.peugeot.clustercheck;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class TrialPolicy {
    private TrialPolicy() { }
    public static final long PERMIT_TTL_MS = 5000;

    public static final class Snapshot {
        public final Map<String, String> fields;
        public final boolean connected, suspended;
        public final byte[] frame00, frame88;
        public Snapshot(Map<String, String> fields, boolean connected, boolean suspended, byte[] frame00, byte[] frame88) {
            this.fields = new LinkedHashMap<String, String>(fields);
            this.connected = connected; this.suspended = suspended;
            this.frame00 = copy(frame00); this.frame88 = copy(frame88);
        }
    }

    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }

    public static Map<String, String> requiredFields() {
        Map<String, String> required = new LinkedHashMap<String, String>();
        required.put("serviceSha256", Protocol.SOURCE_SHA256);
        required.put("serviceVersion", "260129-1424");
        required.put("serviceCode", "26012914");
        required.put("api", "27");
        required.put("hardware", "mt6765");
        required.put("model", "psa_5008_2014_2019");
        required.put("manufacturer", "peugeot");
        required.put("modelPlatform", "psa");
        required.put("configuration", "generic");
        required.put("productCode", "CMZXR62N-U1");
        required.put("mcuVersion", "95.08@260715");
        required.put("canBoxType", "raise");
        required.put("decoder", "raise-psa");
        required.put("boxVersion", Protocol.REFERENCE_BOX);
        required.put("envVersion", Protocol.REFERENCE_BOX);
        return required;
    }

    public static String rejection(Snapshot snapshot) {
        if (snapshot == null) return "차량 확인 결과가 없습니다.";
        for (Map.Entry<String, String> e : requiredFields().entrySet()) {
            if (!e.getValue().equals(snapshot.fields.get(e.getKey())))
                return "확인한 차량과 다른 항목: " + e.getKey() + " = " + snapshot.fields.get(e.getKey());
        }
        if (!snapshot.connected || snapshot.suspended) return "CANBox가 연결되지 않았거나 일시중지 상태입니다.";
        if (!Arrays.equals(snapshot.frame00, Protocol.expectedClusterFrame(0, 0))) return "0/0 계기판 명령 인코딩이 기준과 다릅니다.";
        if (!Arrays.equals(snapshot.frame88, Protocol.expectedClusterFrame(8, 8))) return "8/8 계기판 명령 인코딩이 기준과 다릅니다.";
        if (!Protocol.matchesDudu(0, 0) || !Protocol.matchesDudu(8, 8)) return "DUDU/RZC4 교차검증이 실패했습니다.";
        return null;
    }

    public static Permit authorize(Snapshot snapshot, int left, int right, long now) {
        Protocol.validateGaugeValue(left); Protocol.validateGaugeValue(right);
        String reason = rejection(snapshot); if (reason != null) throw new SecurityException(reason);
        return new Permit(left, right, now);
    }

    public static final class Permit {
        private final int left, right;
        private final long created;
        private final AtomicInteger state = new AtomicInteger(0);
        private Permit(int left, int right, long created) { this.left = left; this.right = right; this.created = created; }

        public void cancel() { state.compareAndSet(0, 2); }
        public boolean claimed() { return state.get() == 1; }

        public byte[] consume(long now) {
            if (now < created || now - created > PERMIT_TTL_MS) {
                cancel();
                throw new SecurityException("설정 확인 시간이 만료됐습니다.");
            }
            if (!state.compareAndSet(0, 1))
                throw new SecurityException("이미 사용했거나 취소한 설정 요청입니다.");
            return Protocol.clusterPayload(left, right);
        }
    }
}
