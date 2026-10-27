package com.google.android.projection.protocol;

import java.util.List;

public abstract class Radio {
    public Radio() {}

    public static class Range {
        public Range(int min, int max) {}
    }

    public static class RadioProperties {
        public RadioProperties(int radioId, int type, List ranges, List spacings, int channelSpacing,
                               boolean backgroundTuner, int region, int rds, boolean afSwitch,
                               boolean ta, int trafficService, boolean audioLoopback,
                               boolean muteCapability, int stationPresetsAccess) {}
    }

    public static class RadioStationInfo {
        public RadioStationInfo(int radioType, int channel, int subChannel) {}
    }

    public static class RadioState {
        public RadioState(boolean sourceEnabled, boolean muted, int activeRadioId,
                          RadioStationInfo stationInfo, List programList, List presetLists) {}
    }

    public interface IRadioEndpoint {
        int sendActiveRadioResponse(int status, int radioId, RadioStationInfo station);
        int sendCancelResponse(int status, int radioId);
        int sendChannelSpacingResponse(int status, int radioId, int spacing);
        int sendMuteResponse(int status, int radioId, boolean muted);
        int sendProgramListResponse(int status, int radioId, boolean complete, List list);
        int sendRadioSourceResponse(int status, boolean enabled);
        int sendRadioStationInfoNotification(int radioId, RadioStationInfo station);
        int sendScanResponse(int status, int radioId, boolean scanning);
        int sendSeekResponse(int status, int radioId);
        int sendStationPresetsResponse(List presets);
        int sendStepResponse(int status, int radioId);
        int sendTrafficUpdateResponse(int status, int radioId, List incidents);
        int sendTuneResponse(int status, int radioId);
    }

    public abstract int cancel(int radioId);
    public abstract int configureChannelSpacing(int radioId, int spacing);
    public abstract int enableRadioSource();
    public abstract RadioState getCurrentRadioState();
    public abstract int getProgramList(int radioId);
    public abstract List getRadioProperties();
    public abstract int getTrafficUpdate(int radioId);
    public abstract int mute(int radioId, boolean muted);
    public abstract int scan(int radioId, boolean start, boolean directionUp, boolean skipSubChannel);
    public abstract int seek(int radioId, boolean directionUp, boolean skipSubChannel);
    public abstract int selectRadio(int radioId);
    public abstract void setRadioEndpoint(IRadioEndpoint endpoint);
    public abstract int step(int radioId, boolean directionUp, boolean skipSubChannel);
    public abstract int tune(int radioId, int channel, int subChannel);
}
