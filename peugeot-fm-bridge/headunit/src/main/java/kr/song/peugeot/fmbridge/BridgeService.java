package kr.song.peugeot.fmbridge;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public class BridgeService extends Service {
    private static final String TAG="CMZX-FM-Bridge";
    private volatile boolean stop=false;
    private Thread worker;
    private ReglinkRadioController radio;

    @Override public void onCreate(){
        super.onCreate();
        radio=new ReglinkRadioController(this);
        startWorker();
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(worker==null||!worker.isAlive())startWorker();
        return START_STICKY;
    }

    @Override public void onDestroy(){
        stop=true;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}

    private synchronized void startWorker(){
        if(worker!=null&&worker.isAlive())return;
        stop=false;
        worker=new Thread(this::loop,"peugeot-fm-bridge");
        worker.start();
    }

    private void loop(){
        int seq=ConfigStore.lastSeq(this);
        long lastBeat=0;
        while(!stop){
            if(!ConfigStore.paired(this)){SystemClock.sleep(2000);continue;}
            try{
                JSONObject next=getNext(seq);
                if(next!=null){
                    int n=next.getInt("seq");
                    JSONObject cmd=next.getJSONObject("command");
                    String result=execute(cmd);
                    seq=n;
                    ConfigStore.seq(this,seq);
                    postStatus(true,radio.getFreq(),result,null);
                    lastBeat=System.currentTimeMillis();
                }else if(System.currentTimeMillis()-lastBeat>30000){
                    postStatus(true,radio.getFreq(),"heartbeat",null);
                    lastBeat=System.currentTimeMillis();
                }
            }catch(Throwable e){
                Log.e(TAG,"loop",e);
                try{postStatus(false,0,null,e.getClass().getSimpleName());}catch(Throwable ignored){}
                SystemClock.sleep(2000);
            }
        }
    }

    private String execute(JSONObject c)throws Exception{
        String a=c.getString("action");
        switch(a){
            case "tune": return radio.tune(c.getInt("freq"))?"tune_ok":"tune_fail";
            case "seek_up": return radio.seek(true)?"seek_up_ok":"seek_up_fail";
            case "seek_down": return radio.seek(false)?"seek_down_ok":"seek_down_fail";
            case "step_up": return radio.step(true)?"step_up_ok":"step_up_fail";
            case "step_down": return radio.step(false)?"step_down_ok":"step_down_fail";
            case "scan": return radio.scan(true)?"scan_ok":"scan_fail";
            case "stop_scan": return radio.scan(false)?"stop_scan_ok":"stop_scan_fail";
            case "power_on": radio.activate(); return "power_on";
            case "power_off": return radio.power(false)?"power_off":"power_off_fail";
            default: return "ignored";
        }
    }

    private JSONObject getNext(int after)throws Exception{
        String u=ConfigStore.server(this)+"/v1/fm/next?after="+after+"&wait=20";
        HttpsURLConnection h=PinnedHttps.open(u);
        h.setConnectTimeout(5000);h.setReadTimeout(25000);h.setRequestMethod("GET");
        h.setRequestProperty("X-FM-Token",ConfigStore.token(this));
        int rc=h.getResponseCode();
        if(rc==204)return null;
        String text=PairClient.read(rc>=200&&rc<300?h.getInputStream():h.getErrorStream());
        if(rc<200||rc>=300)throw new IOException("HTTP "+rc+" "+text);
        return new JSONObject(text);
    }

    private void postStatus(boolean online,int freq,String result,String error)throws Exception{
        JSONObject b=new JSONObject().put("online",online);
        if(freq>0)b.put("freq",freq);
        if(result!=null)b.put("result",result);
        if(error!=null)b.put("error",error);

        HttpsURLConnection h=PinnedHttps.open(ConfigStore.server(this)+"/v1/fm/status");
        h.setConnectTimeout(5000);h.setReadTimeout(5000);h.setRequestMethod("POST");h.setDoOutput(true);
        h.setRequestProperty("X-FM-Token",ConfigStore.token(this));
        h.setRequestProperty("Content-Type","application/json");
        byte[] d=b.toString().getBytes(StandardCharsets.UTF_8);
        h.setFixedLengthStreamingMode(d.length);
        try(OutputStream o=h.getOutputStream()){o.write(d);}
        int rc=h.getResponseCode();
        if(rc<200||rc>=300)throw new IOException("status HTTP "+rc);
        InputStream in=h.getInputStream();while(in.read()!=-1){}in.close();
    }
}
