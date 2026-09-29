package kr.song.peugeot.mirrorcheck;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed, single-use mirror-setting permit for the configuration observed in SONG's log. */
public final class TrialPolicy {
    private TrialPolicy() { }
    public static final long PERMIT_TTL_MS = 5000;

    public static final class Snapshot {
        public final Map<String, String> fields;
        public final boolean connected, suspended;
        public final byte[] setting, offFrame, onFrame;
        public Snapshot(Map<String, String> fields, boolean connected, boolean suspended,
                        byte[] setting, byte[] offFrame, byte[] onFrame) {
            this.fields = new LinkedHashMap<String, String>(fields);
            this.connected = connected; this.suspended = suspended;
            this.setting = copy(setting); this.offFrame = copy(offFrame); this.onFrame = copy(onFrame);
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
        for (Map.Entry<String, String> entry : requiredFields().entrySet()) {
            if (!entry.getValue().equals(snapshot.fields.get(entry.getKey())))
                return "확인한 차량과 다른 항목: " + entry.getKey() + " = " + snapshot.fields.get(entry.getKey());
        }
        if (!snapshot.connected || snapshot.suspended) return "CANBox가 연결되지 않았거나 일시중지 상태입니다.";
        if (snapshot.setting == null || snapshot.setting.length != 9)
            return "0x38 기록 길이가 확인한 실차 형식(9바이트)과 다릅니다.";
        if (!Arrays.equals(snapshot.offFrame, Protocol.raisePsaEncode(0x80, new byte[] {0x1E, 0}))
                || !Arrays.equals(snapshot.onFrame, Protocol.raisePsaEncode(0x80, new byte[] {0x1E, 1})))
            return "서비스가 계산한 미러 명령 형식이 기준과 다릅니다.";
        return null;
    }

    public static Permit authorize(Snapshot snapshot, int requestedValue, long now) {
        if (requestedValue != 0 && requestedValue != 1) throw new IllegalArgumentException("Only OFF/ON is allowed");
        String reason = rejection(snapshot);
        if (reason != null) throw new SecurityException(reason);
        return new Permit(requestedValue, now);
    }

    public static final class Permit {
        private final int value;
        private final long created;
        // 0=pending, 1=claimed for submission, 2=cancelled/expired.
        private final AtomicInteger state = new AtomicInteger(0);
        private Permit(int value, long created) { this.value = value; this.created = created; }
        public void cancel() { state.compareAndSet(0, 2); }
        public boolean claimed() { return state.get() == 1; }
        public byte[] consume(long now) {
            if (now < created || now - created > PERMIT_TTL_MS) {
                cancel(); throw new SecurityException("설정 확인 시간이 만료됐습니다.");
            }
            if (!state.compareAndSet(0, 1)) throw new SecurityException("이미 사용했거나 취소한 설정 요청입니다.");
            return new byte[] {0x1E, (byte) value};
        }
    }
}
