package com.google.android.projection.protocol;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * Minimal physical-tuner follower for the stock AASink RadioStub.
 * The stock stub remains responsible for Android Auto state/presets/JNI marshalling.
 */
public final class ReglinkRadioBridge {
    private static final String TAG = "CMZX-AA-Radio";
    private static final String DROID_DESC = "com.reglink.services.IDroidService";
    private static final String RADIO_DESC = "com.reglink.services.IRadioService";
    private static final String HUB_DESC = "com.reglink.services.IMediaHubService";

    private ReglinkRadioBridge() {}

    private static IBinder serviceManagerGet(String name) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method m = sm.getDeclaredMethod("getService", String.class);
            m.setAccessible(true);
            return (IBinder)m.invoke(null, name);
        } catch (Throwable t) {
            Log.w(TAG, "ServiceManager " + name, t);
            return null;
        }
    }

    private static IBinder reglink(String name) {
        IBinder droid = serviceManagerGet("reglink.droid");
        if (droid == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DROID_DESC);
            data.writeString(name);
            if (!droid.transact(2, data, reply, 0)) return null;
            reply.readException();
            return reply.readStrongBinder();
        } catch (Throwable t) {
            Log.w(TAG, "getService " + name, t);
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean radioVoid(int code) {
        IBinder b = reglink("Radio");
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            if (!b.transact(code, data, reply, 0)) return false;
            reply.readException();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "radio tx " + code, t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean power(boolean on) {
        IBinder b = reglink("Radio");
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            data.writeInt(on ? 1 : 0);
            if (!b.transact(3, data, reply, 0)) return false;
            reply.readException();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "radio power", t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean activeClient() {
        IBinder b = reglink("MediaHub");
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(HUB_DESC);
            data.writeString("radio");
            if (!b.transact(3, data, reply, 0)) return false;
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            Log.w(TAG, "activeClient radio", t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static void activate() {
        try {
            activeClient();
            power(true);
        } catch (Throwable t) {
            Log.w(TAG, "activate", t);
        }
    }

    public static void mute(int radioId, boolean muted) {
        if (radioId != 2) return;
        try {
            if (muted) power(false); else activate();
        } catch (Throwable t) {
            Log.w(TAG, "mute", t);
        }
    }

    public static void tune(int radioId, int channelHz) {
        if (radioId != 2) return;
        try {
            activate();
            IBinder b = reglink("Radio");
            if (b == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(RADIO_DESC);
                data.writeString("fm");
                data.writeInt(channelHz / 10000);
                if (b.transact(6, data, reply, 0)) reply.readException();
            } finally {
                reply.recycle();
                data.recycle();
            }
        } catch (Throwable t) {
            Log.w(TAG, "tune " + channelHz, t);
        }
    }

    public static void seek(int radioId, boolean up) {
        if (radioId != 2) return;
        try {
            activate();
            radioVoid(up ? 10 : 11);
        } catch (Throwable t) {
            Log.w(TAG, "seek", t);
        }
    }

    public static void step(int radioId, boolean up) {
        if (radioId != 2) return;
        try {
            activate();
            radioVoid(up ? 8 : 9);
        } catch (Throwable t) {
            Log.w(TAG, "step", t);
        }
    }

    public static void scan(int radioId, boolean start) {
        if (radioId != 2) return;
        try {
            activate();
            radioVoid(start ? 12 : 13);
        } catch (Throwable t) {
            Log.w(TAG, "scan", t);
        }
    }
}
