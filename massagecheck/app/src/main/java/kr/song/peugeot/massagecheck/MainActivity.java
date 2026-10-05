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
    final Handler ui=new Handler();
    TextView status,log;
    Button checkButton, monitorButton, copyButton;
    volatile boolean busy=false,foreground=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(18*getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);root.setBackgroundColor(Color.rgb(18,29,44));sc.addView(root);
        root.addView(t("5008 마사지 물리버튼 진단 · V03",24));
        root.addView(t("읽기 전용 CAN 관찰: 차량에 어떤 명령도 보내지 않습니다.\n시동을 켠 상태에서 진행하세요.",16));
        checkButton=button("1. 차량 조건 및 현재 캐시 확인");root.addView(checkButton);
        monitorButton=button("2. 물리 버튼 18초 관찰 (송신 없음)");root.addView(monitorButton);
        root.addView(t("시험 순서: 관찰 시작 → 처음 3초는 버튼을 누르지 않음 → 4~12초 사이 운전석 버튼 2회, 조수석 버튼 2회 → 끝날 때까지 대기",16));
        status=t("대기 · 차량에 쓰기 명령 없음",19);root.addView(status);
        log=t("[MASSAGE_DIAG_V03]\n물리버튼 입력 검증을 시작하세요.",14);log.setTextIsSelectable(true);root.addView(log);
        copyButton=button("진단 결과 복사");root.addView(copyButton);
        checkButton.setOnClickListener(v->execute(false));
        monitorButton.setOnClickListener(v->execute(true));
        copyButton.setOnClickListener(v->{
            android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("MASSAGE_DIAG_V03",log.getText()));
            Toast.makeText(this,"진단 결과 복사",Toast.LENGTH_SHORT).show();
        });
        setContentView(sc);refresh();
    }
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);
        v.setTextColor(Color.rgb(235,241,248));v.setPadding(0,8,0,10);return v;}
    String hex(byte[] d){return Rx.fmt(d);}
    void execute(boolean monitor){
        if(busy||!foreground)return;
        busy=true;refresh();
        status.setText(monitor?"물리버튼 관찰 중 · 18초":"차량 조건 확인 중");
        new Thread(()->run(monitor),"MassageRxOnly").start();
    }
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
        void observe(Rx cb,boolean on)throws Exception{
            Parcel q=q(),a=Parcel.obtain();
            try{
                q.writeStrongBinder(cb);
                if(!r.transact(on?1:2,q,a,0))throw new Exception("CANBox 수신 콜백 미지원");
                a.readException();
            }finally{q.recycle();a.recycle();}
        }
    }

    static final class Rx extends Binder {
        static final String DESC="com.reglink.services.canbox.ICANBoxCallback";
        private long started=0;
        private boolean closed=false;
        private int count=0, massageCount=0;
        private byte[] lastMassage=null;
        private final ArrayList<String> events=new ArrayList<>();
        private final Map<Integer,byte[]> lastForType=new HashMap<>();
        Rx(){attachInterface(null,DESC);}
        synchronized void begin(){started=SystemClock.elapsedRealtime();count=0;massageCount=0;
            lastMassage=null;lastForType.clear();events.clear();}
        synchronized void close(){closed=true;}
        synchronized int count(){return count;}
        synchronized int massageCount(){return massageCount;}
        synchronized byte[] massage(){return lastMassage==null?null:lastMassage.clone();}
        synchronized List<String> events(){return new ArrayList<>(events);}
        @Override protected boolean onTransact(int code,Parcel q,Parcel r,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){if(r!=null)r.writeString(DESC);return true;}
            if(code!=1&&code!=2)return super.onTransact(code,q,r,flags);
            q.enforceInterface(DESC);
            if(code==1){
                if(q.readInt()!=0){
                    int type=q.readInt();byte[] packet=q.createByteArray();
                    if(packet!=null && packet.length<=1024){
                        synchronized(this){
                            if(!closed){
                                count++;
                                if(type==0x4e){massageCount++;lastMassage=packet.clone();}
                                byte[] last=lastForType.get(type);
                                boolean changed=!Arrays.equals(last,packet);
                                if(changed) {
                                    lastForType.put(type,packet.clone());
                                    if(events.size()<110)events.add("+"+(SystemClock.elapsedRealtime()-started)+
                                        "ms: 0x"+String.format(Locale.US,"%02X",type&255)+" "+fmt(packet));
                                }
                            }
                        }
                    }
                }
            } else {q.readInt();}
            if(r!=null)r.writeNoException();
            return true;
        }
        static String fmt(byte[] data){
            if(data==null)return "없음";
            StringBuilder out=new StringBuilder();
            for(byte b:data){if(out.length()>0)out.append(' ');out.append(String.format(Locale.US,"%02X",b&255));}
            return out.toString();
        }
    }

    void run(boolean monitor){
        StringBuilder out=new StringBuilder();
        ServiceConnection conn=null;boolean bound=false,registered=false;
        B box=null;Rx rx=null;
        try {
            out.append("[MASSAGE_DIAG_V03]\n시각: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())).append('\n');
            out.append("관찰모드=").append(monitor?"물리버튼 18초":"조건/캐시 확인").append(" · CAN 송신=0회\n");
            PackageInfo pi=getPackageManager().getPackageInfo(PKG,0);
            String h=sha256(pi.applicationInfo.sourceDir);
            if(pi.versionCode!=26012914||!"260129-1424".equals(pi.versionName)||!HASH.equals(h))
                throw new Exception("기준 ReglinkService 버전 또는 SHA 불일치");
            final CountDownLatch latch=new CountDownLatch(1);final IBinder[] hold=new IBinder[1];
            conn=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){hold[0]=b;latch.countDown();}
                public void onServiceDisconnected(ComponentName n){latch.countDown();}};
            Intent i=new Intent(ACTION);i.setComponent(new ComponentName(PKG,CLS));
            bound=bindService(i,conn,0);
            if(!bound||!latch.await(8,TimeUnit.SECONDS)||hold[0]==null)throw new Exception("차량 서비스 연결 실패");
            B droid=new B(hold[0],DROID);Map<String,String> f=droid.info();
            if(Build.VERSION.SDK_INT!=27||!"mt6765".equals(Build.HARDWARE)||
                !"psa_5008_2014_2019".equals(f.get("model"))||
                !"peugeot".equals(f.get("manufacturer"))||
                !"psa".equals(f.get("modelPlatform"))||
                !"generic".equals(f.get("configuration"))||
                !"CMZXR62N-U1".equals(f.get("productCode"))||
                !"95.08@260715".equals(f.get("mcuVersion")))
                throw new Exception("차량 프로필과 분석 기준 불일치");
            box=new B(droid.can(),CAN);
            String canType=box.name(6),decoder=box.name(10);
            boolean connected=box.flag(5),suspended=box.flag(14);
            if(!"raise".equals(canType)||!"raise-psa".equals(decoder)||!connected||suspended)
                throw new Exception("Raise PSA CAN 연결/상태 불일치");
            out.append("service=").append(pi.versionName).append(" / ").append(pi.versionCode)
                .append("\nCAN=").append(canType).append(" / ").append(decoder).append('\n');
            byte[] old=box.last(0x4e);
            out.append("이전 0x4E 캐시=").append(hex(old)).append(" (수신 시각 없음)\n");
            if(monitor){
                rx=new Rx();rx.begin();box.observe(rx,true);registered=true;
                // CAN write()/rawWrite()/request not used; physical-button input is observed passively.
                for(int n=0;n<90;n++)SystemClock.sleep(200);
                byte[] current=box.last(0x4e);
                out.append("관찰 후 0x4E 캐시=").append(hex(current)).append('\n');
                out.append("CAN 패킷 콜백 전체 수=").append(rx.count())
                    .append(" / 0x4E 수신 수=").append(rx.massageCount()).append('\n');
                out.append("마지막 실제 수신 0x4E=").append(hex(rx.massage())).append('\n');
                out.append("새/변경된 프레임 (최대 110개):\n");
                for(String event:rx.events())out.append(event).append('\n');
                out.append("참고: 콜백 미수신은 헤드유닛 관측점의 결과이며 물리버튼 전기적 고장을 의미하지 않습니다.\n");
            } else out.append("조건 확인 성공. 물리버튼 관찰을 실행하세요.");
            final String result=out.toString();
            ui.post(()->{busy=false;status.setText("진단 완료 · 차량 제어 없음");log.setText(result);refresh();});
        }catch(Exception e){
            out.append("\n중단: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            final String result=out.toString();
            ui.post(()->{busy=false;status.setText("조건 불일치 또는 연결 실패");log.setText(result);refresh();});
        }finally{
            if(rx!=null)rx.close();
            if(registered&&box!=null&&rx!=null)try{box.observe(rx,false);}catch(Exception ignored){}
            if(bound&&conn!=null)try{unbindService(conn);}catch(Exception ignored){}
        }
    }
    static String sha256(String path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");FileInputStream in=new FileInputStream(path);try{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}finally{in.close();}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    void refresh(){boolean e=foreground&&!busy;checkButton.setEnabled(e);monitorButton.setEnabled(e);}
    @Override public void onResume(){super.onResume();foreground=true;refresh();}
    @Override public void onPause(){foreground=false;refresh();super.onPause();}
}
