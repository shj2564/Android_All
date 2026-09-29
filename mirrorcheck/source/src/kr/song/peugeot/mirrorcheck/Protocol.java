package kr.song.peugeot.mirrorcheck;

import java.util.Arrays;
import java.util.Locale;

/** Pure offline calculations. This class has no Android, network or vehicle I/O. */
public final class Protocol {
    public static final String SERVICE_PACKAGE = "com.reglink.services";
    public static final String SOURCE_SHA256 = "8c7b9ee385fe2503dd592b6114436235a1e0bcefd8f6c80b6e9a3478b5d074d9";
    public static final String REFERENCE_BOX = "PSA-RZ-15-0128.212.06-HSE";
    public static final String DROID = "com.reglink.services.IDroidService";
    public static final String CANBOX = "com.reglink.services.canbox.ICANBoxService";

    private Protocol() { }

    /** Exhaustive allowlist: no write, rawWrite, suspend, resume or dispatch calls. */
    public static boolean allowedTransaction(String descriptor, int code) {
        if (DROID.equals(descriptor)) return code == 2 || code == 5;
        if (CANBOX.equals(descriptor))
            return code == 5 || code == 6 || code == 8 || code == 10 || code == 11 || code == 14;
        return false;
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

    /** Equivalent to the DUDU RZC4 host encoder, excluding its E9 MCU prefix. */
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

    public static byte[] decodePayload(int expectedType, byte[] frame) {
        if (frame == null || frame.length < 4 || (frame[0] & 255) != 0xFD
                || (frame[1] & 255) + 1 != frame.length || (frame[2] & 255) != expectedType)
            throw new IllegalArgumentException("Frame header/type/length mismatch");
        int sum = 0;
        for (int i = 1; i < frame.length - 1; i++) sum += frame[i] & 255;
        if ((sum & 255) != (frame[frame.length - 1] & 255))
            throw new IllegalArgumentException("Frame checksum mismatch");
        return Arrays.copyOfRange(frame, 3, frame.length - 1);
    }

    /** Interpretation from DUDU code; real CMZX vehicle semantics remain unverified. */
    public static Integer mirrorCandidate(int packetType, byte[] payload) {
        if (packetType != 0x38 || payload == null || payload.length < 5) return null;
        return Integer.valueOf((payload[4] >>> 3) & 1);
    }

    public static String hex(byte[] data) {
        if (data == null) return "수신 기록 없음";
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

    public static String offlineComparison() {
        byte[] off = raisePsaEncode(0x80, new byte[] {0x1E, 0});
        byte[] on = raisePsaEncode(0x80, new byte[] {0x1E, 1});
        boolean match = Arrays.equals(off, duduRzc4Encode(new int[] {0xE3, 5, 0x80, 0x1E, 0}))
            && Arrays.equals(on, duduRzc4Encode(new int[] {0xE3, 5, 0x80, 0x1E, 1}));
        return "코드 대조: " + (match ? "두 인코더 계산 일치" : "불일치")
            + "\nOFF 계산값: " + hex(off) + "\nON 계산값: " + hex(on)
            + "\n이 화면은 계산만 수행하며 위 값을 차량에 전송하지 않습니다.";
    }
}
