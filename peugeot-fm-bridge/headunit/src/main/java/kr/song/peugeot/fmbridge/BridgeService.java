package kr.song.peugeot.fmbridge;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class BridgeService extends Service {
    private LocalFmServer server;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(
                    "cmzx_fm","CMZX FM Bridge",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,launch,
                Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE:
                        PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?
                new Notification.Builder(this,"cmzx_fm"):new Notification.Builder(this);
        b.setContentTitle("CMZX FM 로컬 브리지")
                .setContentText("외부 VPS 없이 보조폰 직접 연결 중")
                .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(true);
        startForeground(38765,b.build());
        server=new LocalFmServer(this);
        server.start();
    }
    static void begin(Service c) {}
    @Override public int onStartCommand(Intent i,int flags,int id) {return START_STICKY;}
    @Override public IBinder onBind(Intent i) {return null;}
    @Override public void onDestroy(){if(server!=null)server.stop();super.onDestroy();}
}
