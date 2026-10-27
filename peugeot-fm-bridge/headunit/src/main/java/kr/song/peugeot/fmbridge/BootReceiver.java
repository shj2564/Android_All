package kr.song.peugeot.fmbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(ConfigStore.paired(c)) c.startService(new Intent(c,BridgeService.class));
    }
}
