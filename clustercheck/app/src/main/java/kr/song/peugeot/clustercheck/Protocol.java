package kr.song.peugeot.clustercheck;

import java.util.Arrays;
import java.util.Locale;

public final class Protocol {
    public static final String SERVICE_PACKAGE = "com.reglink.services";
    public static final String SOURCE_SHA256 = "8c7b9ee385fe2503dd592b6114436235a1e0bcefd8f6c80b6e9a3478b5d074d9";
    public static final String REFERENCE_BOX = "PSA-RZ-15-0128.212.06-HSE";
    public static final String DROID = "com.reglink.services.IDroidService";
    public static final String CANBOX = "com.reglink.services.canbox.ICANBoxService";
    public static final int TYPE_CLUSTER = 0x8C;

    private Protocol() { }

    public static boolean allowedTransaction(String descriptor, int code) {
        if (DROID.equals(descriptor)) return code == 2 || code == 5;
        if (CANBOX.equals(descriptor))
            return code == 5 || code == 6 || code == 8 || code == 10 || code == 11 || code == 14;
        return false;
    }

    public static void validateGaugeValue(int value) {
        if (value < 0 || value > 8) throw new IllegalArgumentException("Gauge value must be 0..8");
    }

    public static byte[] clusterPayload(int left, int right) {
        validateGaugeValue(left); validateGaugeValue(right);
        return new byte[] {(byte) left, (byte) right};
    }

    public static byte[] raisePsaEncode(int type, byte[] payload) {
        if (type < 0 || type > 255 || payload == null || payload.length > 252)
            throw new IllegalArgumentException("Invalid Raise PSA packet");
        byte[] frame = new byte[payload.length + 4];
        frame[0] = (byte) 0xFD;
        frame[1] = (byte) (payload.length + 3);
        frame[2] = (byte) type;
        System.arraycopy(payload, 0, frame, 3, payload.length);
        int sum = 0;
        for (int i = 1; i < frame.length - 1; i++) sum += frame[i] & 255;
        frame[frame.length - 1] = (byte) sum;
        return frame;
    }

    public static byte[] duduRzc4Encode(int[] mcuArguments) {
        if (mcuArguments == null || mcuArguments.length < 3 || (mcuArguments[0] & 255) != 0xE3)
            throw new IllegalArgumentException("Expected FYT E3 arguments");
        byte[] frame = new byte[mcuArguments.length + 1];
        frame[0] = (byte) 0xFD;
        int sum = 0;
        for (int i = 1; i < mcuArguments.length; i++) {
            frame[i] = (byte) mcuArguments[i];
            sum += mcuArguments[i];
        }
        frame[frame.length - 1] = (byte) sum;
        return frame;
    }

    public static byte[] expectedClusterFrame(int left, int right) {
        return raisePsaEncode(TYPE_CLUSTER, clusterPayload(left, right));
    }

    public static boolean matchesDudu(int left, int right) {
        return Arrays.equals(expectedClusterFrame(left, right),
            duduRzc4Encode(new int[] {0xE3, 0x05, TYPE_CLUSTER, left, right}));
    }

    // 2026-09-27 real-car mapping confirmed by user; LEFT and RIGHT menus are identical.
    public static String rightLabel(int value) {
        switch (value) {
            case 0: return "0 · 메뉴 없음";
            case 1: return "1 · 엔진 온도";
            case 2: return "2 · 엔진 속도";
            case 3: return "3 · 현재 정보";
            case 4: return "4 · USB";
            case 5: return "5 · 경고 수준";
            case 6: return "6 · 주행 지원";
            case 7: return "7 · 메뉴 없음";
            case 8: return "8 · 메뉴 없음";
            default: return "선택 안 됨";
        }
    }

    public static String leftLabel(int value) {
        return rightLabel(value);
    }

    public static boolean isUnsupported(int value) {
        return value == 0 || value == 7 || value == 8;
    }

    public static String hex(byte[] data) {
        if (data == null) return "기록 없음";
        StringBuilder out = new StringBuilder();
        for (byte b : data) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format(Locale.US, "%02X", b & 255));
        }
        return out.toString();
    }

    public static String ascii(byte[] data) {
        if (data == null) return "";
        StringBuilder out = new StringBuilder();
        for (byte b : data) {
            int c = b & 255;
            if (c == 0) break;
            out.append(c >= 32 && c < 127 ? (char) c : '?');
        }
        return out.toString().trim();
    }
}
