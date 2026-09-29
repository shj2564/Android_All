package kr.song.peugeot.mirrorcheck;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** The only setting-write path; no rawWrite, generic packet API or automatic retries. */
public final class MirrorTrialBridge {
    private static final String CALLBACK = "com.reglink.services.canbox.ICANBoxCallback";
    private final IBinder remote;
    public MirrorTrialBridge(IBinder remote) throws RemoteException {
        if (remote == null || !Protocol.CANBOX.equals(remote.getInterfaceDescriptor()))
            throw new RemoteException("CANBox 인터페이스가 기준과 다릅니다.");
        this.remote = remote;
    }

    public void sendMirrorOnce(TrialPolicy.Permit permit) throws RemoteException {
        if (permit == null) throw new SecurityException("검증한 설정 요청이 없습니다.");
        Parcel q = Parcel.obtain(), r = Parcel.obtain();
        try {
            q.writeInterfaceToken(Protocol.CANBOX);
            q.writeInt(1); // nullable CANBoxPacket present
            q.writeInt(0x80); // fixed PSA vehicle-setting packet
            q.writeByteArray(permit.consume(SystemClock.elapsedRealtime()));
            if (!remote.transact(4, q, r, 0)) throw new RemoteException("write 호출을 지원하지 않습니다.");
            r.readException(); // void response; this is NOT a vehicle acknowledgement.
        } finally { q.recycle(); r.recycle(); }
    }

    public void observe(Observer observer, boolean register) throws RemoteException {
        Parcel q = Parcel.obtain(), r = Parcel.obtain();
        try {
            q.writeInterfaceToken(Protocol.CANBOX); q.writeStrongBinder(observer);
            if (!remote.transact(register ? 1 : 2, q, r, 0))
                throw new RemoteException("수신 관찰 등록/해제를 지원하지 않습니다.");
            r.readException();
        } finally { q.recycle(); r.recycle(); }
    }

    public static final class Observer extends Binder {
        private final List<String> events = new ArrayList<String>();
        private long started = SystemClock.elapsedRealtime();
        private byte[] last;
        private boolean closed;
        public Observer() { attachInterface(null, CALLBACK); }
        public synchronized void markStart() { events.clear(); last = null; started = SystemClock.elapsedRealtime(); }
        public synchronized byte[] lastPacket() { return last == null ? null : last.clone(); }
        public synchronized List<String> events() { return new ArrayList<String>(events); }
        public synchronized void close() { closed = true; }
        @Override protected boolean onTransact(int code, Parcel q, Parcel r, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { if (r != null) r.writeString(CALLBACK); return true; }
            if (code != 1 && code != 2) return super.onTransact(code, q, r, flags);
            q.enforceInterface(CALLBACK);
            if (code == 1) {
                if (q.readInt() != 0) {
                    int type = q.readInt(); byte[] data = q.createByteArray();
                    if (type == 0x38 && data != null && data.length <= 1024) {
                        synchronized (this) {
                            if (!closed) {
                                last = data.clone();
                                if (events.size() < 40) events.add("+" + (SystemClock.elapsedRealtime() - started)
                                    + " ms: 0x38 " + Protocol.hex(data));
                            }
                        }
                    }
                }
            } else {
                boolean connected = q.readInt() != 0;
                synchronized (this) {
                    if (!closed && events.size() < 40) events.add("CANBox 연결 알림: " + connected);
                }
            }
            if (r != null) r.writeNoException();
            return true;
        }
    }
}
