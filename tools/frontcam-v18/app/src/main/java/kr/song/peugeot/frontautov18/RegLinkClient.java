package kr.song.peugeot.frontautov18;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

final class RegLinkClient {
    static final String DROID = "com.reglink.services.IDroidService";
    static final String CAR = "com.reglink.services.ICarService";
    static final String MEDIA = "com.reglink.services.IMediaService";
    static final String SHARED = "com.reglink.services.ISharedVariableService";

    private RegLinkClient() {}

    static IBinder getService(IBinder droid, String name, String expectedDescriptor) throws RemoteException {
        if (droid == null) throw new RemoteException("Droid binder=null");
        String actual = droid.getInterfaceDescriptor();
        if (!DROID.equals(actual)) throw new RemoteException("Droid descriptor mismatch: " + actual);
        Parcel q = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(DROID);
            q.writeString(name);
            if (!droid.transact(2, q, r, 0))
                throw new RemoteException("getService transaction unsupported: " + name);
            r.readException();
            IBinder out = r.readStrongBinder();
            if (out == null) throw new RemoteException(name + " binder=null");
            String got = out.getInterfaceDescriptor();
            if (!expectedDescriptor.equals(got))
                throw new RemoteException(name + " descriptor mismatch: " + got);
            return out;
        } finally {
            q.recycle();
            r.recycle();
        }
    }

    static String gear(IBinder car) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(CAR);
            if (!car.transact(3, q, r, 0)) throw new RemoteException("getGear unsupported");
            r.readException();
            return r.readString();
        } finally { q.recycle(); r.recycle(); }
    }

    static boolean reversing(IBinder car) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(CAR);
            if (!car.transact(5, q, r, 0)) throw new RemoteException("isReversing unsupported");
            r.readException();
            return r.readInt() != 0;
        } finally { q.recycle(); r.recycle(); }
    }

    static boolean cameraRequired(IBinder car, String id) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(CAR);
            q.writeString(id);
            if (!car.transact(23, q, r, 0)) throw new RemoteException("isCameraRequired unsupported");
            r.readException();
            return r.readInt() != 0;
        } finally { q.recycle(); r.recycle(); }
    }

    static int mediaSwitch(IBinder media, int source) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(MEDIA);
            q.writeInt(source);
            if (!media.transact(2, q, r, 0)) throw new RemoteException("videoSwitch unsupported");
            r.readException();
            return r.readInt();
        } finally { q.recycle(); r.recycle(); }
    }

    static int mediaSource(IBinder media) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(MEDIA);
            if (!media.transact(4, q, r, 0)) throw new RemoteException("videoSource unsupported");
            r.readException();
            return r.readInt();
        } finally { q.recycle(); r.recycle(); }
    }

    static boolean sharedBoolean(IBinder shared, String key, boolean def) throws RemoteException {
        Parcel q = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(SHARED);
            q.writeString(key);
            q.writeInt(def ? 1 : 0);
            if (!shared.transact(9, q, r, 0))
                throw new RemoteException("getBoolean unsupported: " + key);
            r.readException();
            return r.readInt() != 0;
        } finally { q.recycle(); r.recycle(); }
    }
}
