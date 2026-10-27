package kr.song.peugeot.fm;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import java.util.*;
import java.util.concurrent.*;

public class FmMediaService extends MediaBrowserServiceCompat {
    private static final String ROOT="fm_root";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private MediaSessionCompat session;
    private int lastFreq=9570;
    private static final int[][] STATIONS={{9170,917},{9250,925},{9330,933},{9370,937},{9470,947},{9570,957}};

    @Override public void onCreate() {
        super.onCreate();
        session=new MediaSessionCompat(this,"PeugeotFM");
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS|MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlayFromMediaId(String id, Bundle extras) {
                if (id == null) return;
                if (id.startsWith("freq:")) {
                    int f=Integer.parseInt(id.substring(5)); lastFreq=f; send("tune",f,label(f));
                } else if ("seek_up".equals(id)) send("seek_up",null,"다음 방송국");
                else if ("seek_down".equals(id)) send("seek_down",null,"이전 방송국");
                else if ("scan".equals(id)) send("scan",null,"FM 자동검색");
            }
            @Override public void onPlay() { send("tune",lastFreq,label(lastFreq)); }
            @Override public void onPause() { send("power_off",null,"FM 일시정지"); }
            @Override public void onStop() { send("power_off",null,"FM 정지"); }
            @Override public void onSkipToNext() { send("seek_up",null,"다음 방송국"); }
            @Override public void onSkipToPrevious() { send("seek_down",null,"이전 방송국"); }
        });
        session.setActive(true); setSessionToken(session.getSessionToken());
        setState(PlaybackStateCompat.STATE_STOPPED,"Peugeot FM");
    }

    @Nullable @Override public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(ROOT,null);
    }

    @Override public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        ArrayList<MediaBrowserCompat.MediaItem> list=new ArrayList<>();
        if (!ROOT.equals(parentId)) { result.sendResult(list); return; }
        if (!ConfigStore.paired(this)) {
            list.add(item("setup","휴대폰에서 먼저 VPS 페어링을 완료하세요",MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        } else {
            for (int[] s:STATIONS) list.add(item("freq:"+s[0],label(s[0]),MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            list.add(item("seek_down","◀ 이전 방송국 (Seek -)",MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            list.add(item("seek_up","다음 방송국 (Seek +) ▶",MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            list.add(item("scan","FM 자동검색",MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(list);
    }

    private MediaBrowserCompat.MediaItem item(String id,String title,int flags) {
        MediaDescriptionCompat d=new MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title)
                .setSubtitle("Peugeot 5008 GT · CMZX 실제 FM").build();
        return new MediaBrowserCompat.MediaItem(d,flags);
    }
    private String label(int f) { return String.format(Locale.KOREA,"%.1f MHz",f/100.0); }

    private void send(String action,Integer freq,String title) {
        setState(PlaybackStateCompat.STATE_BUFFERING,title);
        io.execute(() -> {
            boolean ok=false;
            try { org.json.JSONObject reply=RelayClient.command(this,action,freq);
                  ok=reply.optBoolean("ok");
            } catch(Exception e) { android.util.Log.w("PeugeotFM", "local bridge command failed", e); }
            session.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"Peugeot 5008 GT · 실제 FM 튜너").build());
            setState(ok?PlaybackStateCompat.STATE_PLAYING:PlaybackStateCompat.STATE_ERROR,title);
        });
    }

    private void setState(int state,String msg) {
        long actions=PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PAUSE|PlaybackStateCompat.ACTION_STOP|
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS|
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
        PlaybackStateCompat.Builder b=new PlaybackStateCompat.Builder().setActions(actions).setState(state,0,1f);
        if (state==PlaybackStateCompat.STATE_ERROR) b.setErrorMessage("CMZX 로컬 FM 연결/권한 실패");
        session.setPlaybackState(b.build());
    }
    @Override public void onDestroy() { session.release(); io.shutdownNow(); super.onDestroy(); }
}
