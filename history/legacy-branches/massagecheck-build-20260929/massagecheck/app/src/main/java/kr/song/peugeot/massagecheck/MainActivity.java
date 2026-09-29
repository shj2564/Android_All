package kr.song.peugeot.massagecheck;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final String PKG="com.reglink.services", ACTION="com.reglink.action.DroidCarService",
        CLS="com.reglink.services.DroidCarService", DROID="com.reglink.services.IDroidService",
        CAN="com.reglink.services.canbox.ICANBoxService",
        HASH="8c7b9ee385fe2503dd592b6114436235a1e0bcefd8f6c80b6e9a3478b5d074d9",
        BOX="PSA-RZ-15-0128.212.06-HSE";
    static final int MASSAGE=0x85, DRIVER=0x06, PASSENGER=0x07;
    final Handler ui=new Handler();
    TextView status,log; Spinner dLevel,dType,pLevel,pType; Button dSend,pSend;
    volatile boolean ready=false,busy=false,foreground=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); int p=(int)(18*getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p); root.setBackgroundColor(Color.rgb(18,29,44)); sc.addView(root);
        root.addView(t("5008 마사지 복구 · v0.1",24));
        root.addView(t("CMZXR62N-U1 / Raise PSA-RZ-15 전용\n현재 Raise 스크립트의 마사지 송신 비트 순서 오류를 바로잡아 1회 명령으로 시험합니다.",16));
        Button check=new Button(this); check.setText("1. 차량 연결 및 현재 상태 확인"); root.addView(check);
        check.setOnClickListener(v->check());
        root.addView(t("운전석 마사지",20));
        dLevel=levelSpinner(); root.addView(dLevel); dType=typeSpinner(); root.addView(dType);
        dSend=new Button(this); dSend.setText("운전석 적용"); root.addView(dSend);
        root.addView(t("조수석 마사지",20));
        pLevel=levelSpinner(); root.addView(pLevel); pType=typeSpinner(); root.addView(pType);
        pSend=new Button(this); pSend.setText("조수석 적용"); root.addView(pSend);
        status=t("차량 연결 확인 대기",19); root.addView(status);
        log=t("수정 포맷: 0x85 / 운전석 0x06 / 조수석 0x07 / 값=(강도<<4)|타입",14);
        log.setTextIsSelectable(true); root.addView(log);
        dSend.setOnClickListener(v->confirm(DRIVER)); pSend.setOnClickListener(v->confirm(PASSENGER));
        setContentView(sc); refresh();
    }
    TextView t(String s,int z){ TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.rgb(235,241,248));v.setPadding(0,8,0,10);return v; }
    Spinner levelSpinner(){ Spinner s=new Spinner(this); s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"OFF","LOW","MID","HIGH"})); return s; }
    Spinner typeSpinner(){ Spinner s=new Spinner(this); s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"TYPE 1","TYPE 2","TYPE 3","TYPE 4","TYPE 5"})); return s; }
    int pack(int level,int type){ if(level<0||level>3||type<1||type>5)throw new IllegalArgumentException();return (level<<4)|type; }
    byte[] payload(int sub,int level,int type){return new byte[]{(byte)sub,(byte)pack(level,type)};}
    byte[] expected(byte[] p){ byte[] f=new byte[p.length+4];f[0]=(byte)0xFD;f[1]=(byte)(p.length+3);f[2]=(byte)MASSAGE;System.arraycopy(p,0,f,3,p.length);int sum=0;for(int i=1;i<f.length-1;i++)sum+=f[i]&255;f[f.length-1]=(byte)sum;return f; }
    String hex(byte[] d){ if(d==null)return "기록 없음";StringBuilder s=new StringBuilder();for(byte x:d){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString(); }

    void confirm(final int sub){
        if(!ready||busy||!foreground)return;
        final Spinner ls=sub==DRIVER?dLevel:pLevel, ts=sub==DRIVER?dType:pType;
        final int level=ls.getSelectedItemPosition(), type=ts.getSelectedItemPosition()+1;
        final byte[] p=payload(sub,level,type);
        new AlertDialog.Builder(this).setTitle(sub==DRIVER?"운전석 마사지 적용":"조수석 마사지 적용")
            .setMessage("강도="+level+" / 타입="+type+"\n패킷 payload: "+hex(p)+"\n\n검증 후 1회만 전송합니다.")
            .setNegativeButton("취소",null).setPositiveButton("전송",(d,w)->send(sub,level,type)).show();
    }
    void check(){ if(busy||!foreground)return;busy=true;ready=false;refresh();status.setText("차량 조건 확인 중");new Thread(()->run(false,0,0,0),"MassageCheck").start(); }
    void send(int sub,int level,int type){ if(busy||!ready||!foreground)return;busy=true;ready=false;refresh();status.setText("마사지 명령 검증 및 전송 중");new Thread(()->run(true,sub,level,type),"MassageSend").start(); }

    static final class B {
        final IBinder r; final String desc;
        B(IBinder x,String d)throws Exception{if(x==null||!d.equals(x.getInterfaceDescriptor()))throw new Exception("Binder 인터페이스 불일치");r=x;desc=d;}
        Parcel q(){Parcel p=Parcel.obtain();p.writeInterfaceToken(desc);return p;}
        Parcel x(int code,Parcel q)throws Exception{Parcel a=Parcel.obtain();boolean ok=false;try{if(!r.transact(code,q,a,0))throw new Exception("transact "+code+" 미지원");a.readException();ok=true;return a;}finally{q.recycle();if(!ok)a.recycle();}}
        IBinder can()throws Exception{Parcel q=q();q.writeString("CANBox");Parcel a=x(2,q);try{return a.readStrongBinder();}finally{a.recycle();}}
        Map<String,String> info()throws Exception{String[] k={"model","platform","version","mcuVersion","manufacturer","configuration","modelPlatform","productCode","productName","productManufacturerCode","productManufacturerName","readableManufacturerName","readableModelName","readableConfigName","canBoxType"};Map<String,String> m=new LinkedHashMap<>();Parcel a=x(5,q());try{if(a.readInt()==0)return m;for(String z:k)m.put(z,a.readString());return m;}finally{a.recycle();}}
        boolean flag(int c)throws Exception{Parcel a=x(c,q());try{return a.readInt()!=0;}finally{a.recycle();}}
        String name(int c)throws Exception{Parcel a=x(c,q());try{return a.readString();}finally{a.recycle();}}
        byte[] last(int type)throws Exception{Parcel q=q();q.writeInt(type);Parcel a=x(8,q);try{if(a.readInt()==0)return null;a.readInt();return a.createByteArray();}finally{a.recycle();}}
        byte[] encode(int type,byte[] p)throws Exception{Parcel q=q();q.writeInt(1);q.writeInt(type);q.writeByteArray(p);Parcel a=x(11,q);try{return a.createByteArray();}finally{a.recycle();}}
        void write(int type,byte[] p)throws Exception{Parcel q=q(),a=Parcel.obtain();try{q.writeInt(1);q.writeInt(type);q.writeByteArray(p);if(!r.transact(4,q,a,0))throw new Exception("write 미지원");a.readException();}finally{q.recycle();a.recycle();}}
    }

    void run(boolean doSend,int sub,int level,int type){
        StringBuilder out=new StringBuilder();ServiceConnection conn=null;boolean bound=false;
        try{
            out.append("[MASSAGE_REPAIR_V01]\n시각: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())).append('\n');
            PackageInfo pi=getPackageManager().getPackageInfo(PKG,0);String h=sha256(pi.applicationInfo.sourceDir);
            if(pi.versionCode!=26012914||!"260129-1424".equals(pi.versionName)||!HASH.equals(h))throw new Exception("기준 ReglinkService와 다릅니다.");
            final CountDownLatch latch=new CountDownLatch(1);final IBinder[] hold=new IBinder[1];
            conn=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){hold[0]=b;latch.countDown();}public void onServiceDisconnected(ComponentName n){latch.countDown();}};
            Intent i=new Intent(ACTION);i.setComponent(new ComponentName(PKG,CLS));bound=bindService(i,conn,0);
            if(!bound||!latch.await(8,TimeUnit.SECONDS)||hold[0]==null)throw new Exception("차량 서비스 연결 실패");
            B droid=new B(hold[0],DROID);Map<String,String> f=droid.info();
            if(Build.VERSION.SDK_INT!=27||!"mt6765".equals(Build.HARDWARE)||!"psa_5008_2014_2019".equals(f.get("model"))||!"peugeot".equals(f.get("manufacturer"))||!"psa".equals(f.get("modelPlatform"))||!"generic".equals(f.get("configuration"))||!"CMZXR62N-U1".equals(f.get("productCode"))||!"95.08@260715".equals(f.get("mcuVersion")))throw new Exception("확인된 실차 조건과 다릅니다.");
            IBinder cb=droid.can();B box=new B(cb,CAN);
            String canType=box.name(6),decoder=box.name(10);boolean connected=box.flag(5),suspended=box.flag(14);
            if(!"raise".equals(canType)||!"raise-psa".equals(decoder)||!connected||suspended)throw new Exception("Raise PSA CANBox 상태 불일치");
            byte[] state=box.last(0x4e);
            out.append("service=").append(pi.versionName).append(" / ").append(pi.versionCode).append("\nCAN=").append(canType).append(" / ").append(decoder).append("\n0x4E cache=").append(hex(state)).append('\n');
            if(state!=null&&state.length>=7){
                int dp=state[5]&255,pp=state[6]&255;
                out.append("현재 운전석: 강도 ").append((dp&0x30)>>4).append(" / 타입 ").append(dp&0x0f).append('\n');
                out.append("현재 조수석: 강도 ").append((pp&0x30)>>4).append(" / 타입 ").append(pp&0x0f).append('\n');
            }
            if(doSend){
                byte[] p=payload(sub,level,type), exp=expected(p), enc=box.encode(MASSAGE,p);
                if(!Arrays.equals(exp,enc))throw new Exception("Reglink 인코더와 독립 계산 불일치");
                out.append("좌석=").append(sub==DRIVER?"운전석":"조수석").append(" / 강도=").append(level).append(" / 타입=").append(type).append('\n');
                out.append("요청 형식=").append(hex(exp)).append('\n');
                box.write(MASSAGE,p);
                out.append("write 호출: 1회, 예외 없이 반환\n실제 시트 마사지 동작을 확인하세요.");
            }else out.append("차량 조건 일치 · 송신 없음");
            final String result=out.toString();ui.post(()->{busy=false;ready=true;status.setText("차량 조건 일치 · 마사지 시험 가능");log.setText(result);refresh();});
        }catch(final Exception e){
            out.append("\n중단: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            final String result=out.toString();ui.post(()->{busy=false;ready=false;status.setText("조건 불일치 또는 연결 실패");log.setText(result);refresh();});
        }finally{if(bound&&conn!=null)try{unbindService(conn);}catch(Exception ignored){}}
    }
    static String sha256(String path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");FileInputStream in=new FileInputStream(path);try{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}finally{in.close();}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    void refresh(){boolean e=foreground&&ready&&!busy;dSend.setEnabled(e);pSend.setEnabled(e);dLevel.setEnabled(e);dType.setEnabled(e);pLevel.setEnabled(e);pType.setEnabled(e);}
    @Override public void onResume(){super.onResume();foreground=true;refresh();}
    @Override public void onPause(){foreground=false;refresh();super.onPause();}
}
