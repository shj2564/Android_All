package kr.song.peugeot.clustercheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler();
    private TextView status, log;
    private Spinner left, right;
    private Button apply;
    private boolean ready, busy, foreground;

    // 2019 Peugeot 5008 real-car verified mapping.
    // Values 0, 7, 8 produce no menu and are intentionally hidden.
    private static final String[] OPTIONS = {
        "선택하세요",
        "1 · 엔진 온도",
        "2 · 엔진 속도",
        "3 · 현재 정보",
        "4 · USB",
        "5 · 경고 수준",
        "6 · 주행 지원"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(18*getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        root.setBackgroundColor(Color.rgb(18,29,44));
        scroll.addView(root);

        TextView title=t("5008 계기판 PERSONAL 설정 · FINAL 1.1",24); root.addView(title);
        root.addView(t(
            "CMZXR62N-U1 / Raise PSA-RZ-15 전용\n" +
            "좌·우 PERSONAL 실차 확정 메뉴 1~6만 표시합니다.\n" +
            "0 / 7 / 8은 메뉴 없음으로 확인되어 숨겼습니다.\n" +
            "정차·P단·주차브레이크 상태에서 먼저 연결 확인을 누르세요.",16));

        Button check=new Button(this); check.setText("1. 차량 연결 확인"); root.addView(check);
        check.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ startCheck(); }});

        root.addView(t("왼쪽 PERSONAL 표시",18));
        left=new Spinner(this);
        left.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,OPTIONS));
        root.addView(left);

        root.addView(t("오른쪽 PERSONAL 표시",18));
        right=new Spinner(this);
        right.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,OPTIONS));
        root.addView(right);

        int savedLeft=getSharedPreferences("cluster_restore",0).getInt("last_left",-1);
        int savedRight=getSharedPreferences("cluster_restore",0).getInt("last_right",-1);
        if(savedLeft>=1&&savedLeft<=6) left.setSelection(savedLeft);
        if(savedRight>=1&&savedRight<=6) right.setSelection(savedRight);

        apply=new Button(this); apply.setText("2. PERSONAL 설정 적용"); root.addView(apply);
        apply.setEnabled(false);
        apply.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ confirmSend(); }});

        status=t("차량 연결 확인 대기",20); root.addView(status);
        log=t(
            "실차 확정 매핑(좌·우 동일)\n" +
            "1 엔진 온도 / 2 엔진 속도 / 3 현재 정보 / 4 USB / 5 경고 수준 / 6 주행 지원\n" +
            "0 / 7 / 8 = 메뉴 없음",14);
        log.setTextIsSelectable(true); root.addView(log);

        setContentView(scroll);
    }

    private TextView t(String s,int size){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(235,241,248));
        v.setPadding(0,8,0,12);
        return v;
    }

    private void confirmSend(){
        if(!ready||busy||!foreground)return;
        final int lv=left.getSelectedItemPosition();
        final int rv=right.getSelectedItemPosition();
        if(lv<1||rv<1){
            Toast.makeText(this,"좌·우 메뉴를 모두 선택하세요.",Toast.LENGTH_SHORT).show();
            return;
        }
        final String frame=Protocol.hex(Protocol.expectedClusterFrame(lv,rv));
        new AlertDialog.Builder(this)
            .setTitle("계기판 PERSONAL 설정 적용")
            .setMessage(
                "왼쪽: "+Protocol.leftLabel(lv)+"\n" +
                "오른쪽: "+Protocol.rightLabel(rv)+"\n\n" +
                "예상 프레임: "+frame+"\n\n" +
                "검증된 0x8C 좌/우 설정을 1회 전송합니다.")
            .setNegativeButton("취소",null)
            .setPositiveButton("적용",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int w){ startSend(lv,rv); }
            })
            .show();
    }

    private void startCheck(){
        if(busy||!foreground)return;
        busy=true; ready=false; refresh();
        status.setText("차량 조건 확인 중");
        new Thread(new Runnable(){ public void run(){ runCheck(false,-1,-1); }},"ClusterCheck").start();
    }

    private void startSend(final int lv,final int rv){
        if(busy||!foreground||!ready)return;
        busy=true; ready=false; refresh();
        status.setText("PERSONAL 설정 적용 중");
        new Thread(new Runnable(){ public void run(){ runCheck(true,lv,rv); }},"ClusterSend").start();
    }

    private void runCheck(boolean doSend,int lv,int rv){
        StringBuilder out=new StringBuilder();
        ServiceConnection conn=null;
        boolean bound=false;
        try{
            out.append("[CLUSTER_FINAL_1_1]\n");
            out.append("시각: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())).append('\n');

            PackageInfo pi=getPackageManager().getPackageInfo(Protocol.SERVICE_PACKAGE,0);
            String svcHash=sha256(pi.applicationInfo.sourceDir);
            if(pi.versionCode!=26012914||!"260129-1424".equals(pi.versionName)||!Protocol.SOURCE_SHA256.equals(svcHash))
                throw new Exception("기준 ReglinkService와 다릅니다.");

            final CountDownLatch latch=new CountDownLatch(1);
            final IBinder[] holder=new IBinder[1];
            conn=new ServiceConnection(){
                public void onServiceConnected(ComponentName n,IBinder b){holder[0]=b;latch.countDown();}
                public void onServiceDisconnected(ComponentName n){latch.countDown();}
            };

            Intent i=new Intent("com.reglink.action.DroidCarService");
            i.setComponent(new ComponentName(Protocol.SERVICE_PACKAGE,"com.reglink.services.DroidCarService"));
            bound=bindService(i,conn,0);
            if(!bound||!latch.await(8,TimeUnit.SECONDS)||holder[0]==null)
                throw new Exception("차량 서비스 연결 실패");

            BinderReader platform=new BinderReader(holder[0],Protocol.DROID);
            Map<String,String> f=new LinkedHashMap<String,String>();
            f.put("serviceSha256",svcHash);
            f.put("serviceVersion",pi.versionName);
            f.put("serviceCode",String.valueOf(pi.versionCode));
            f.put("api",String.valueOf(Build.VERSION.SDK_INT));
            f.put("hardware",Build.HARDWARE);
            f.putAll(platform.platformInfo());

            IBinder canBinder=platform.getCanBox();
            BinderReader box=new BinderReader(canBinder,Protocol.CANBOX);
            f.put("canBoxType",box.name(6));
            f.put("decoder",box.name(10));
            BinderReader.Packet ver=box.lastPacket(0x7F);
            f.put("boxVersion",ver==null?"":Protocol.ascii(ver.data));
            f.put("envVersion",shared("Env.CANBoxVersion",1));

            boolean connected=box.flag(5), suspended=box.flag(14);
            TrialPolicy.Snapshot snap=new TrialPolicy.Snapshot(
                f,connected,suspended,box.encodeCluster(0,0),box.encodeCluster(8,8));
            String reject=TrialPolicy.rejection(snap);

            for(Map.Entry<String,String> e:f.entrySet())
                out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            out.append("CANBox 연결: ").append(connected).append(" / 일시중지: ").append(suspended).append('\n');
            out.append("서비스 0/0 계산: ").append(Protocol.hex(snap.frame00)).append('\n');
            out.append("서비스 8/8 계산: ").append(Protocol.hex(snap.frame88)).append('\n');

            if(reject!=null) throw new Exception(reject);

            if(doSend){
                if(lv<1||lv>6||rv<1||rv>6)
                    throw new SecurityException("최종판 허용 범위는 1~6입니다.");

                TrialPolicy.Permit permit=TrialPolicy.authorize(snap,lv,rv,SystemClock.elapsedRealtime());
                byte[] expected=Protocol.expectedClusterFrame(lv,rv);
                byte[] service=box.encodeCluster(lv,rv);
                if(!java.util.Arrays.equals(expected,service))
                    throw new Exception("선택값 인코딩 불일치");

                out.append("LEFT=").append(lv).append(" · ").append(Protocol.leftLabel(lv))
                   .append(" / RIGHT=").append(rv).append(" · ").append(Protocol.rightLabel(rv)).append('\n');
                out.append("요청 형식: ").append(Protocol.hex(expected)).append('\n');

                new ClusterTrialBridge(canBinder).sendClusterOnce(permit);

                getSharedPreferences("cluster_restore",0).edit()
                    .putInt("last_left",lv)
                    .putInt("last_right",rv)
                    .apply();

                out.append("write 호출: 1회, 예외 없이 반환 (ECU ACK 아님)\n");
                out.append("계기판 PERSONAL 화면에서 실제 반영 여부를 확인하세요.\n");
            }else{
                out.append("차량 조건 일치 · 설정 송신 없음\n");
            }

            final String ok=out.toString();
            ui.post(new Runnable(){ public void run(){
                ready=true; busy=false;
                status.setText("차량 조건 일치 · PERSONAL 설정 가능");
                log.setText(ok); refresh();
            }});
        }catch(final Exception e){
            out.append("중단: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            final String fail=out.toString();
            ui.post(new Runnable(){ public void run(){
                ready=false; busy=false;
                status.setText("조건 불일치 또는 연결 실패");
                log.setText(fail); refresh();
            }});
        }finally{
            if(bound&&conn!=null){ try{ unbindService(conn); }catch(Exception ignored){} }
        }
    }

    private void refresh(){
        apply.setEnabled(foreground&&ready&&!busy);
        left.setEnabled(foreground&&ready&&!busy);
        right.setEnabled(foreground&&ready&&!busy);
    }

    private String shared(String key,int type){
        try{
            Bundle args=new Bundle();
            args.putInt("value_type",type);
            Bundle r=getContentResolver().call(
                Uri.parse("content://com.reglink.services.provider.sharedvar"),"get",key,args);
            if(r!=null&&r.get("value")!=null)return String.valueOf(r.get("value"));
        }catch(Exception ignored){}
        return "";
    }

    private static String sha256(String path)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        FileInputStream in=new FileInputStream(path);
        try{
            byte[] b=new byte[65536];
            int n;
            while((n=in.read(b))!=-1)d.update(b,0,n);
        }finally{
            in.close();
        }
        StringBuilder s=new StringBuilder();
        for(byte b:d.digest())s.append(String.format(Locale.US,"%02x",b&255));
        return s.toString();
    }

    @Override public void onResume(){super.onResume();foreground=true;refresh();}
    @Override public void onPause(){foreground=false;refresh();super.onPause();}
}
