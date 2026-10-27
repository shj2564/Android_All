package com.google.android.projection.protocol;

import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * CMZX65-U1 / Reglink physical-FM bridge for the Android Auto Radio endpoint.
 * This class deliberately shadows the stock AASink RadioStub at runtime.
 */
public final class RadioStub extends Radio {
    private static final String TAG = "CMZX-AA-Radio";

    private static final int STATUS_OK = 0;
    private static final int STATUS_INVALID = -20;
    private static final int FM_ID = 2;
    private static final int FM_TYPE = 1;
    private static final int FM_MIN_HZ = 88000000;
    private static final int FM_MAX_HZ = 108000000;
    private static final int FM_SPACING_HZ = 100000;
    private static final int FALLBACK_HZ = 95700000;

    private static final String DROID_DESC = "com.reglink.services.IDroidService";
    private static final String RADIO_DESC = "com.reglink.services.IRadioService";
    private static final String MEDIA_DESC = "com.reglink.services.IMediaService";
    private static final String MEDIA_HUB_DESC = "com.reglink.services.IMediaHubService";

    private IRadioEndpoint endpoint;
    private IBinder droid;
    private IBinder radio;
    private IBinder media;
    private IBinder mediaHub;
    private boolean sourceEnabled;
    private boolean muted;
    private int currentHz = FALLBACK_HZ;

    public RadioStub() {
        super();
        refreshFrequency();
    }

    @Override
    public void setRadioEndpoint(IRadioEndpoint ep) {
        this.endpoint = ep;
    }

    @Override
    public List getRadioProperties() {
        List ranges = Collections.singletonList(new Range(FM_MIN_HZ, FM_MAX_HZ));
        List spacings = Collections.singletonList(Integer.valueOf(FM_SPACING_HZ));
        RadioProperties fm = new RadioProperties(
                FM_ID, FM_TYPE, ranges, spacings, FM_SPACING_HZ,
                true, 1, 2, true, true, 1,
                false, false, 0);
        return Collections.singletonList(fm);
    }

    @Override
    public RadioState getCurrentRadioState() {
        boolean powered = radioIsPowerOn();
        sourceEnabled = sourceEnabled || powered;
        refreshFrequency();
        return new RadioState(sourceEnabled, muted, FM_ID, station(),
                Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public int enableRadioSource() {
        boolean ok = activatePhysicalRadio();
        sourceEnabled = ok;
        int status = ok ? STATUS_OK : STATUS_INVALID;
        int r = endpoint != null ? endpoint.sendRadioSourceResponse(status, ok) : status;
        if (ok) notifyStation();
        return r;
    }

    @Override
    public int selectRadio(int radioId) {
        if (!validId(radioId)) {
            return endpoint != null ? endpoint.sendActiveRadioResponse(STATUS_INVALID, radioId, station()) : STATUS_INVALID;
        }
        boolean ok = activatePhysicalRadio();
        refreshFrequency();
        int status = ok ? STATUS_OK : STATUS_INVALID;
        int r = endpoint != null ? endpoint.sendActiveRadioResponse(status, FM_ID, station()) : status;
        if (ok) notifyStation();
        return r;
    }

    @Override
    public int configureChannelSpacing(int radioId, int spacing) {
        int status = (validId(radioId) && spacing == FM_SPACING_HZ) ? STATUS_OK : STATUS_INVALID;
        return endpoint != null ? endpoint.sendChannelSpacingResponse(status, radioId, FM_SPACING_HZ) : status;
    }

    @Override
    public int mute(int radioId, boolean newMuted) {
        if (!validId(radioId)) {
            return endpoint != null ? endpoint.sendMuteResponse(STATUS_INVALID, radioId, newMuted) : STATUS_INVALID;
        }
        boolean ok;
        if (newMuted) {
            ok = radioPower(false);
            muted = true;
        } else {
            ok = activatePhysicalRadio();
            muted = false;
        }
        int status = ok ? STATUS_OK : STATUS_INVALID;
        return endpoint != null ? endpoint.sendMuteResponse(status, radioId, newMuted) : status;
    }

    @Override
    public int tune(int radioId, int channelHz, int subChannel) {
        if (!validId(radioId) || channelHz < FM_MIN_HZ || channelHz > FM_MAX_HZ) {
            return endpoint != null ? endpoint.sendTuneResponse(STATUS_INVALID, radioId) : STATUS_INVALID;
        }
        boolean ok = activatePhysicalRadio() && radioSetBandFreq("fm", channelHz / 10000);
        if (ok) {
            currentHz = channelHz;
            SystemClock.sleep(80);
            refreshFrequency();
        }
        int status = ok ? STATUS_OK : STATUS_INVALID;
        int r = endpoint != null ? endpoint.sendTuneResponse(status, radioId) : status;
        if (ok) notifyStation();
        return r;
    }

    @Override
    public int step(int radioId, boolean directionUp, boolean skipSubChannel) {
        if (!validId(radioId)) {
            return endpoint != null ? endpoint.sendStepResponse(STATUS_INVALID, radioId) : STATUS_INVALID;
        }
        boolean ok = activatePhysicalRadio();
        int old = refreshFrequency();
        if (ok) ok = radioVoid(directionUp ? 8 : 9);
        if (ok) waitForFrequencyChange(old, 800);
        int status = ok ? STATUS_OK : STATUS_INVALID;
        int r = endpoint != null ? endpoint.sendStepResponse(status, radioId) : status;
        if (ok) notifyStation();
        return r;
    }

    @Override
    public int seek(int radioId, boolean directionUp, boolean skipSubChannel) {
        if (!validId(radioId)) {
            return endpoint != null ? endpoint.sendSeekResponse(STATUS_INVALID, radioId) : STATUS_INVALID;
        }
        boolean ok = activatePhysicalRadio();
        int old = refreshFrequency();
        if (ok) ok = radioVoid(directionUp ? 10 : 11);
        if (ok) waitForFrequencyChange(old, 2400);
        int status = ok ? STATUS_OK : STATUS_INVALID;
        int r = endpoint != null ? endpoint.sendSeekResponse(status, radioId) : status;
        if (ok) notifyStation();
        return r;
    }

    @Override
    public int scan(int radioId, boolean start, boolean directionUp, boolean skipSubChannel) {
        if (!validId(radioId)) {
            return endpoint != null ? endpoint.sendScanResponse(STATUS_INVALID, radioId, false) : STATUS_INVALID;
        }
        boolean ok = activatePhysicalRadio();
        if (ok) ok = radioVoid(start ? 12 : 13);
        int status = ok ? STATUS_OK : STATUS_INVALID;
        return endpoint != null ? endpoint.sendScanResponse(status, radioId, start && ok) : status;
    }

    @Override
    public int cancel(int radioId) {
        boolean ok = validId(radioId) && radioVoid(13);
        int status = ok ? STATUS_OK : STATUS_INVALID;
        return endpoint != null ? endpoint.sendCancelResponse(status, radioId) : status;
    }

    @Override
    public int getProgramList(int radioId) {
        int status = validId(radioId) ? STATUS_OK : STATUS_INVALID;
        return endpoint != null
                ? endpoint.sendProgramListResponse(status, radioId, true, Collections.emptyList())
                : status;
    }

    @Override
    public int getTrafficUpdate(int radioId) {
        int status = validId(radioId) ? STATUS_OK : STATUS_INVALID;
        return endpoint != null
                ? endpoint.sendTrafficUpdateResponse(status, radioId, Collections.emptyList())
                : status;
    }

    private boolean validId(int radioId) {
        return radioId == FM_ID;
    }

    private RadioStationInfo station() {
        return new RadioStationInfo(FM_TYPE, currentHz, 0);
    }

    private void notifyStation() {
        if (endpoint != null) {
            endpoint.sendRadioStationInfoNotification(FM_ID, station());
        }
    }

    private int waitForFrequencyChange(int oldHz, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        int v = oldHz;
        while (SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(100);
            v = refreshFrequency();
            if (v > 0 && v != oldHz) break;
        }
        return v;
    }

    private int refreshFrequency() {
        int raw = radioGetFreq();
        if (raw > 0) currentHz = raw * 10000;
        if (currentHz < FM_MIN_HZ || currentHz > FM_MAX_HZ) currentHz = FALLBACK_HZ;
        return currentHz;
    }

    private boolean activatePhysicalRadio() {
        try {
            boolean switched = mediaSwitchToRadio();
            activateExistingRadioHubClient();
            boolean powered = radioPower(true);
            sourceEnabled = powered || switched;
            muted = false;
            refreshFrequency();
            return powered || switched;
        } catch (Throwable t) {
            Log.e(TAG, "activatePhysicalRadio", t);
            return false;
        }
    }

    private IBinder getSystemServiceBinder(String name) {
        try {
            Method m = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
            m.setAccessible(true);
            return (IBinder) m.invoke(null, name);
        } catch (Throwable t) {
            Log.e(TAG, "ServiceManager.getService(" + name + ")", t);
            return null;
        }
    }

    private IBinder getDroid() {
        if (droid == null || !droid.pingBinder()) droid = getSystemServiceBinder("reglink.droid");
        return droid;
    }

    private IBinder getReglinkService(String name) {
        IBinder d = getDroid();
        if (d == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DROID_DESC);
            data.writeString(name);
            if (!d.transact(2, data, reply, 0)) return null;
            reply.readException();
            return reply.readStrongBinder();
        } catch (Throwable t) {
            Log.e(TAG, "getReglinkService " + name, t);
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private IBinder getRadio() {
        if (radio == null || !radio.pingBinder()) radio = getReglinkService("Radio");
        return radio;
    }

    private IBinder getMedia() {
        if (media == null || !media.pingBinder()) media = getReglinkService("Media");
        return media;
    }

    private IBinder getMediaHub() {
        if (mediaHub == null || !mediaHub.pingBinder()) mediaHub = getReglinkService("MediaHub");
        return mediaHub;
    }

    private boolean radioPower(boolean on) {
        IBinder b = getRadio();
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
            Log.e(TAG, "radioPower", t);
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private boolean radioIsPowerOn() {
        IBinder b = getRadio();
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            if (!b.transact(15, data, reply, 0)) return false;
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private int radioGetFreq() {
        IBinder b = getRadio();
        if (b == null) return 0;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            if (!b.transact(7, data, reply, 0)) return 0;
            reply.readException();
            return reply.readInt();
        } catch (Throwable t) {
            Log.e(TAG, "radioGetFreq", t);
            return 0;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private boolean radioSetBandFreq(String band, int freq) {
        IBinder b = getRadio();
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            data.writeString(band);
            data.writeInt(freq);
            if (!b.transact(6, data, reply, 0)) return false;
            reply.readException();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "radioSetBandFreq", t);
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private boolean radioVoid(int transactionCode) {
        IBinder b = getRadio();
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RADIO_DESC);
            if (!b.transact(transactionCode, data, reply, 0)) return false;
            reply.readException();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "radioVoid tx=" + transactionCode, t);
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private boolean mediaSwitchToRadio() {
        IBinder b = getMedia();
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MEDIA_DESC);
            data.writeInt(1);
            data.writeInt(1);
            data.writeInt(1);
            data.writeInt(0);
            data.writeInt(0);
            data.writeInt(1);
            if (!b.transact(1, data, reply, 0)) return false;
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            Log.e(TAG, "mediaSwitchToRadio", t);
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }

    private boolean activateExistingRadioHubClient() {
        IBinder b = getMediaHub();
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MEDIA_HUB_DESC);
            data.writeString("radio");
            if (!b.transact(3, data, reply, 0)) return false;
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            Log.w(TAG, "activeClient(radio) unavailable", t);
            return false;
        } finally {
            reply.recycle(); data.recycle();
        }
    }
}
