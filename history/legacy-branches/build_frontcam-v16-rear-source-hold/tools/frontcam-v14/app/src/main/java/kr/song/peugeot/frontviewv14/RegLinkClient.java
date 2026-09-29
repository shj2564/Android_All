package kr.song.peugeot.frontviewv14;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

final class RegLinkClient {
    static final String DROID = "com.reglink.services.IDroidService";
    static final String CAR = "com.reglink.services.ICarService";

    private RegLinkClient() {}

    static IBinder getCar(IBinder droid) throws RemoteException {
        if (droid == null) throw new RemoteException("DroidCarService binder=null");
        String actual = droid.getInterfaceDescriptor();
        if (!DROID.equals(actual)) throw new RemoteException("Droid descriptor mismatch: " + actual);
        Parcel q = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(DROID);
            q.writeString("Car");
            if (!droid.transact(2, q, r, 0)) throw new RemoteException("IDroidService transaction 2 unsupported");
            r.readException();
            IBinder car = r.readStrongBinder();
            if (car == null) throw new RemoteException("Car binder=null");
            String carDescriptor = car.getInterfaceDescriptor();
            if (!CAR.equals(carDescriptor)) throw new RemoteException("Car descriptor mismatch: " + carDescriptor);
            return car;
        } finally {
            q.recycle();
            r.recycle();
        }
    }

    static String gear(IBinder car) throws RemoteException {
        Parcel q = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(CAR);
            if (!car.transact(3, q, r, 0)) throw new RemoteException("getGear transaction unsupported");
            r.readException();
            return r.readString();
        } finally {
            q.recycle();
            r.recycle();
        }
    }

    static boolean reversing(IBinder car) throws RemoteException {
        Parcel q = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            q.writeInterfaceToken(CAR);
            if (!car.transact(5, q, r, 0)) throw new RemoteException("isReversing transaction unsupported");
            r.readException();
            return r.readInt() != 0;
        } finally {
            q.recycle();
            r.recycle();
        }
    }
}
