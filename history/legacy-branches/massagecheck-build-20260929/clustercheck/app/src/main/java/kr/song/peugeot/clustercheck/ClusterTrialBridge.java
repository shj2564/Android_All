package kr.song.peugeot.clustercheck;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

public final class ClusterTrialBridge {
    private final IBinder remote;

    public ClusterTrialBridge(IBinder remote) throws RemoteException {
        if (remote == null || !Protocol.CANBOX.equals(remote.getInterfaceDescriptor()))
            throw new RemoteException("CANBox 인터페이스가 기준과 다릅니다.");
        this.remote = remote;
    }

    public void sendClusterOnce(TrialPolicy.Permit permit) throws RemoteException {
        if (permit == null) throw new SecurityException("검증한 설정 요청이 없습니다.");
        Parcel q = Parcel.obtain(), r = Parcel.obtain();
        try {
            q.writeInterfaceToken(Protocol.CANBOX);
            q.writeInt(1);
            q.writeInt(Protocol.TYPE_CLUSTER);
            q.writeByteArray(permit.consume(SystemClock.elapsedRealtime()));
            if (!remote.transact(4, q, r, 0))
                throw new RemoteException("write 호출을 지원하지 않습니다.");
            r.readException();
        } finally {
            q.recycle();
            r.recycle();
        }
    }
}
