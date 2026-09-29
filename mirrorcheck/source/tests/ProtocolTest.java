import kr.song.peugeot.mirrorcheck.Protocol;
import java.util.Arrays;

/** Fixed vectors and rejection cases derived from the two independently supplied APKs. */
public final class ProtocolTest {
    private static int checks;
    private static void check(boolean ok, String name) {
        checks++; if (!ok) throw new AssertionError(name);
    }
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }
    public static void main(String[] args) {
        // FYT carSet(0x1E, value) -> CanbusTypeRZC4.write: FD 05 80 1E value checksum.
        check("FD 05 80 1E 00 A3".equals(Protocol.hex(Protocol.raisePsaEncode(0x80, bytes(0x1E, 0)))), "OFF golden vector");
        check("FD 05 80 1E 01 A4".equals(Protocol.hex(Protocol.raisePsaEncode(0x80, bytes(0x1E, 1)))), "ON golden vector");
        check("FD 05 80 1E 00 A3".equals(Protocol.hex(Protocol.duduRzc4Encode(new int[] {0xE3, 5, 0x80, 0x1E, 0}))), "DUDU OFF golden vector");
        check("FD 05 80 1E 01 A4".equals(Protocol.hex(Protocol.duduRzc4Encode(new int[] {0xE3, 5, 0x80, 0x1E, 1}))), "DUDU ON golden vector");
        check("FD 08 80 1A 01 00 00 00 A3".equals(Protocol.hex(Protocol.raisePsaEncode(0x80, bytes(0x1A, 1, 0, 0, 0)))), "Existing Reglink trunk vector");
        check(Arrays.equals(bytes(0x1E, 1), Protocol.decodePayload(0x80, bytes(0xFD, 5, 0x80, 0x1E, 1, 0xA4))), "Decode independent fixed frame");
        check(Protocol.mirrorCandidate(0x38, bytes(0, 0, 0, 0, 8, 0, 0)).intValue() == 1, "Payload fifth-byte bit3");
        check(Protocol.mirrorCandidate(0x38, bytes(0, 0, 0, 0, 0, 0, 8)).intValue() == 0, "Raw offset must not be mistaken for payload index6");
        check(Protocol.mirrorCandidate(0x38, null) == null, "Missing packet remains unknown");
        check(Protocol.mirrorCandidate(0x38, bytes(0, 0, 0, 8)) == null, "Short packet remains unknown");
        check(Protocol.mirrorCandidate(0x39, bytes(0, 0, 0, 0, 8)) == null, "Other type remains unknown");
        check(Protocol.ascii(bytes(0x50, 0x53, 0x41, 0, 0x58)).equals("PSA"), "Version terminator");
        // Exhaust all possible values, with a changing unrelated byte to catch an offset error.
        for (int value = 0; value < 256; value++) {
            byte[] payload = bytes(0x80, 0x20, 0x40, 0x10, value, 0x40, 255 - value);
            byte[] frame = Protocol.raisePsaEncode(0x38, payload);
            int duduOffset = 1; // RZC4 parser calls onHandle(frame, frameStart+1, ...)
            int duduState = (frame[duduOffset + 6] >> 3) & 1;
            check(Protocol.mirrorCandidate(0x38, payload).intValue() == duduState, "DUDU/Reglink receive offsets " + value);
        }
        for (int index = 0; index < 6; index++) {
            byte[] corrupt = bytes(0xFD, 5, 0x80, 0x1E, 1, 0xA4); corrupt[index] ^= 1;
            boolean rejected = false;
            try { Protocol.decodePayload(0x80, corrupt); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Reject single-byte corruption " + index);
        }
        boolean rejected = false;
        try { Protocol.raisePsaEncode(0x80, new byte[253]); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject frame length overflow");
        int[] forbidden = {1, 2, 3, 4, 7, 9, 12, 13};
        for (int n : forbidden) check(!Protocol.allowedTransaction(Protocol.CANBOX, n), "Reject CANBox mutation/unneeded call " + n);
        for (int n : new int[] {5, 6, 8, 10, 11, 14}) check(Protocol.allowedTransaction(Protocol.CANBOX, n), "Allow required reader " + n);
        check(!Protocol.allowedTransaction("com.syu.ms", 5), "Reject different service descriptor");
        check(!Protocol.allowedTransaction(Protocol.DROID, 3), "Reject service registration");
        check(!Protocol.allowedTransaction(Protocol.DROID, 6), "Reject broadcast");
        System.out.println("PASS: " + checks + " checks; synthetic/static protocol validation only; no vehicle I/O.");
    }
}
