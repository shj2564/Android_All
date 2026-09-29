package kr.song.peugeot.mirrorcheck;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Narrow reader for the exact uploaded ReglinkService APK. */
public final class BinderReader {
    public static final class Packet {
        public final int type;
        public final byte[] data;
        Packet(int type, byte[] data) { this.type = type; this.data = data; }
    }

    private final IBinder remote;
    private final String descriptor;

    public BinderReader(IBinder remote, String expected) throws RemoteException {
        if (remote == null) throw new RemoteException("서비스 응답이 없습니다.");
        if (!expected.equals(remote.getInterfaceDescriptor()))
            throw new RemoteException("예상한 서비스 인터페이스와 다릅니다.");
        this.remote = remote;
        descriptor = expected;
    }

    private Parcel request() {
        Parcel p = Parcel.obtain(); p.writeInterfaceToken(descriptor); return p;
    }

    private Parcel exchange(int code, Parcel request) throws RemoteException {
        Parcel result = Parcel.obtain();
        boolean success = false;
        try {
            if (!Protocol.allowedTransaction(descriptor, code))
                throw new SecurityException("읽기 허용 목록에 없는 호출입니다.");
            if (!remote.transact(code, request, result, 0))
                throw new RemoteException("이 읽기 기능을 지원하지 않습니다.");
            result.readException();
            success = true;
            return result;
        } finally {
            request.recycle();
            if (!success) result.recycle();
        }
    }

    public IBinder getCanBox() throws RemoteException {
        Parcel q = request(); q.writeString("CANBox"); Parcel r = exchange(2, q);
        try { return r.readStrongBinder(); } finally { r.recycle(); }
    }

    public Map<String, String> platformInfo() throws RemoteException {
        String[] keys = {"model", "platform", "version", "mcuVersion", "manufacturer", "configuration",
            "modelPlatform", "productCode", "productName", "productManufacturerCode", "productManufacturerName",
            "readableManufacturerName", "readableModelName", "readableConfigName", "canBoxType"};
        Map<String, String> out = new LinkedHashMap<String, String>();
        Parcel r = exchange(5, request());
        try {
            if (r.readInt() == 0) return out;
            for (String key : keys) {
                if (r.dataAvail() < 4) throw new RemoteException("차종 정보 형식이 기준 APK와 다릅니다.");
                out.put(key, r.readString());
            }
            return out;
        } finally { r.recycle(); }
    }

    public boolean flag(int code) throws RemoteException {
        if (code != 5 && code != 14) throw new IllegalArgumentException("Unsupported flag");
        Parcel r = exchange(code, request());
        try { return r.readInt() != 0; } finally { r.recycle(); }
    }

    public String name(int code) throws RemoteException {
        if (code != 6 && code != 10) throw new IllegalArgumentException("Unsupported name");
        Parcel r = exchange(code, request());
        try { return r.readString(); } finally { r.recycle(); }
    }

    public Packet lastPacket(int type) throws RemoteException {
        if (type != 0x38 && type != 0x7F) throw new IllegalArgumentException("Unsupported packet");
        Parcel q = request(); q.writeInt(type); Parcel r = exchange(8, q);
        try {
            if (r.readInt() == 0) return null;
            int resultType = r.readInt(); byte[] data = r.createByteArray();
            if (resultType != type || data == null || data.length > 1024)
                throw new RemoteException("수신 기록 형식이 기준 APK와 다릅니다.");
            return new Packet(resultType, data);
        } finally { r.recycle(); }
    }

    /** Uploaded CANBoxService.encodePkt calls only decoder.encode; it performs no I/O. */
    public byte[] encodeOnly(int enabled) throws RemoteException {
        if (enabled != 0 && enabled != 1) throw new IllegalArgumentException("Expected 0/1");
        Parcel q = request();
        q.writeInt(1); // nullable CANBoxPacket is present
        q.writeInt(0x80);
        q.writeByteArray(new byte[] {0x1E, (byte) enabled});
        Parcel r = exchange(11, q);
        try { return r.createByteArray(); } finally { r.recycle(); }
    }
}
