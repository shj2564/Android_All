package kr.song.peugeot.fmbridge;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local-only controller.  Does not use Peugeot VPS, cloud API, or AASink.
 * Binding the listener to 0.0.0.0 permits Wi-Fi Direct/hotspot network interfaces;
 * clients must still have a route between the two Android devices.
 */
final class LocalFmServer {
    static final int PORT=38765;
    private static final String TAG="CMZX-FM-Local";
    private final Context ctx;
    private final ReglinkRadioController radio;
    private final ExecutorService workers=new ThreadPoolExecutor(
            2,4,30L,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(24),
            new ThreadPoolExecutor.DiscardPolicy());
    private final AtomicInteger failures=new AtomicInteger();
    private volatile long failureWindow=0;
    private volatile ServerSocket socket;
    private volatile String status="starting";
    private volatile String lastResult="대기";
    private volatile int lastFreq=0;
    private Thread accepter;

    LocalFmServer(Context context) {
        ctx=context.getApplicationContext();
        radio=new ReglinkRadioController(ctx);
        ensureSecrets();
    }

    static String pairingCode(Context c) {
        return c.getSharedPreferences("local_bridge",0).getString("pin","설정 전");
    }
    static String authToken(Context c) {
        return c.getSharedPreferences("local_bridge",0).getString("token","");
    }

    private void ensureSecrets() {
        if (!authToken(ctx).isEmpty()) return;
        byte[] b=new byte[24]; SecureRandom sr=new SecureRandom(); sr.nextBytes(b);
        String tok=Base64.encodeToString(b, Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING);
        int pin=100000+sr.nextInt(900000);
        ctx.getSharedPreferences("local_bridge",0).edit()
                .putString("token",tok).putString("pin",Integer.toString(pin)).commit();
    }

    void start() {
        if (accepter != null && accepter.isAlive()) return;
        accepter=new Thread(() -> {
            try {
                ServerSocket server=new ServerSocket();
                socket=server;
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress("0.0.0.0",PORT));
                status="listening";
                while(!server.isClosed()) {
                    Socket peer=server.accept();
                    peer.setSoTimeout(5000);
                    try { workers.execute(() -> serve(peer)); }
                    catch (RejectedExecutionException e) {
                        try { peer.close(); } catch(Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                status=e.getClass().getSimpleName()+": "+e.getMessage();
                Log.e(TAG,"listen failed",e);
            }
        },"fm-bridge-accept");
        accepter.setDaemon(true);
        accepter.start();
    }

    void stop() {
        try { if(socket != null) socket.close(); } catch(Exception ignored){}
        workers.shutdownNow();
    }

    String diagnostics() {
        return "서버: "+status+" / Port "+PORT+"\n"
            +"Binder: "+radio.diagnostics()+"\n"
            +"최근 명령: "+lastResult+"\n"
            +"최근 주파수: "+lastFreq;
    }

    private boolean authorized(String supplied) {
        byte[] expected=authToken(ctx).getBytes(StandardCharsets.UTF_8);
        byte[] actual=(supplied==null?"":supplied).getBytes(StandardCharsets.UTF_8);
        return expected.length>0 && MessageDigest.isEqual(expected,actual);
    }

    private void serve(Socket peer) {
        try (Socket c=peer) {
            InputStream in=c.getInputStream();
            ByteArrayOutputStream head=new ByteArrayOutputStream(2048);
            int state=0;
            while(head.size()<8192) {
                int b=in.read();
                if (b<0) return;
                head.write(b);
                state = b=='\r' && state==0 ? 1 :
                        b=='\n' && state==1 ? 2 :
                        b=='\r' && state==2 ? 3 :
                        b=='\n' && state==3 ? 4 : 0;
                if (state==4) break;
            }
            if(state!=4) { send(c,431,jsonError("headers too long"));return; }
            String[] lines=new String(head.toByteArray(),StandardCharsets.ISO_8859_1).split("\r\n");
            if(lines.length==0) return;
            String[] first=lines[0].split(" ");
            if(first.length<2) {send(c,400,jsonError("bad request"));return;}
            String method=first[0],path=first[1],token="",type="";
            int length=0;
            for(int i=1;i<lines.length;i++){
                int p=lines[i].indexOf(':');if(p<0)continue;
                String key=lines[i].substring(0,p).trim().toLowerCase(Locale.ROOT);
                String val=lines[i].substring(p+1).trim();
                if("content-length".equals(key))length=Integer.parseInt(val);
                if("x-fm-token".equals(key))token=val;
                if("content-type".equals(key))type=val;
            }
            if(length<0||length>4096) {send(c,413,jsonError("too large"));return;}
            byte[] data=new byte[length];
            int count=0;
            while(count<length){
                int n=in.read(data,count,length-count);
                if(n<0)return;
                count+=n;
            }
            JSONObject body=length==0?new JSONObject():new JSONObject(new String(data,StandardCharsets.UTF_8));
            if ("GET".equals(method)&&"/health".equals(path)) {
                send(c,200,new JSONObject().put("ok",true).put("device","CMZX").put("service","FM Local Bridge"));
                return;
            }
            if ("POST".equals(method)&&"/v1/pair".equals(path)) {
                long now=System.currentTimeMillis();
                if(now-failureWindow>60000){failureWindow=now;failures.set(0);}
                if(failures.get()>=8){send(c,429,jsonError("pair retry limit"));return;}
                String pin=body.optString("pin","");
                if(!pairingCode(ctx).equals(pin)){failures.incrementAndGet();send(c,403,jsonError("invalid pin"));return;}
                send(c,200,new JSONObject().put("ok",true).put("token",authToken(ctx)));return;
            }
            if(!authorized(token)){send(c,401,jsonError("unauthorized"));return;}
            if ("GET".equals(method)&&"/v1/fm/status".equals(path)) {
                int freq=radio.getFreq();
                if(freq>0)lastFreq=freq;
                send(c,200,new JSONObject().put("ok",true).put("freq",lastFreq)
                        .put("result",lastResult).put("binder",radio.diagnostics()));return;
            }
            if ("POST".equals(method)&&"/v1/fm/command".equals(path)) {
                String action=body.optString("action","");
                boolean ok=false;
                if("tune".equals(action)){
                    int f=body.optInt("freq",0);
                    if(f<8750||f>10800){send(c,400,jsonError("invalid frequency"));return;}
                    ok=radio.tune(f);
                }else if("seek_up".equals(action))ok=radio.seek(true);
                else if("seek_down".equals(action))ok=radio.seek(false);
                else if("step_up".equals(action))ok=radio.step(true);
                else if("step_down".equals(action))ok=radio.step(false);
                else if("scan".equals(action))ok=radio.scan(true);
                else if("stop_scan".equals(action))ok=radio.scan(false);
                else if("power_on".equals(action))ok=radio.activate();
                else if("power_off".equals(action))ok=radio.power(false);
                else {send(c,400,jsonError("invalid action"));return;}
                lastResult=action+" => "+(ok?"OK":"BINDER_FAILED");
                int f=radio.getFreq();if(f>0)lastFreq=f;
                send(c,ok?200:502,new JSONObject().put("ok",ok)
                        .put("action",action).put("freq",lastFreq)
                        .put("result",lastResult).put("binder",radio.diagnostics()));
                return;
            }
            send(c,404,jsonError("not found"));
        } catch(Throwable e) { Log.w(TAG,"request failed",e); }
    }

    private static JSONObject jsonError(String msg){
        try{return new JSONObject().put("ok",false).put("error",msg);}
        catch(Exception e){return new JSONObject();}
    }
    private static void send(Socket c,int code,JSONObject body)throws IOException{
        byte[] d=body.toString().getBytes(StandardCharsets.UTF_8);
        String msg=code==200?"OK":code==401?"Unauthorized":code==403?"Forbidden":
                code==502?"Bad Gateway":"Error";
        String h="HTTP/1.1 "+code+" "+msg+"\r\n"+
                "Content-Type: application/json; charset=utf-8\r\n"+
                "Cache-Control: no-store\r\n"+
                "Connection: close\r\n"+
                "Content-Length: "+d.length+"\r\n\r\n";
        OutputStream out=c.getOutputStream();
        out.write(h.getBytes(StandardCharsets.ISO_8859_1));
        out.write(d);out.flush();
    }
}
