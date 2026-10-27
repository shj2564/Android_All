package kr.song.peugeot.fmbridge;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent i) {
        Intent service=new Intent(context,BridgeService.class);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);
        else context.startService(service);
    }
}
