package kr.song.peugeot.fm;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText host, pin;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,32,36,20);
        TextView title=new TextView(this);
        title.setText("Peugeot FM · 로컬 직접 연결");
        title.setTextSize(23);root.addView(title);

        TextView hint=new TextView(this);
        hint.setText("\nVPS 없음 · 차량 올인원과 보조폰 Wi-Fi 직접 연결\n"+
                "CMZX FM Bridge 화면에서 표시한 IPv4와 페어링 코드를 아래에 입력하세요.\n"+
                "Android Auto 연결 중 같은 Wi-Fi 경로로 접근할 수 있어야 합니다.\n");
        root.addView(hint);

        host=new EditText(this);host.setSingleLine();
        host.setHint("올인원 IP 예: 192.168.49.1");
        String s=ConfigStore.server(this);
        host.setText(s.isEmpty()?"":s.replace("http://",""));
        root.addView(host);

        pin=new EditText(this);pin.setHint("올인원 화면의 6자리 코드");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(pin);
        Button probe=new Button(this);probe.setText("① Wi-Fi 연결 테스트");root.addView(probe);
        Button connect=new Button(this);connect.setText("② 올인원 페어링");root.addView(connect);
        Button diagnostics=new Button(this);diagnostics.setText("③ FM Binder 상태 확인");root.addView(diagnostics);
        Button tune=new Button(this);tune.setText("④ 실제 FM 95.7MHz 동작 확인 (정차 중)");root.addView(tune);
        Button seek=new Button(this);seek.setText("⑤ FM Seek + (정차 중)");root.addView(seek);
        tune.setOnClickListener(v->testRadio("tune",9570));
        seek.setOnClickListener(v->testRadio("seek_up",null));
        status=new TextView(this);status.setTextSize(16);
        status.setPadding(0,22,0,0);root.addView(status);

        probe.setOnClickListener(v->checkConnection());
        connect.setOnClickListener(v->pair());
        diagnostics.setOnClickListener(v->diagnostics());
        status.setText(ConfigStore.paired(this)
                ?"저장된 로컬 연결: "+ConfigStore.server(this)+"\n③ 상태 확인을 누르세요."
                :"미연결. ①부터 진행하세요.");
        setContentView(root);
    }

    private void checkConnection(){
        String h=host.getText().toString().trim();status.setText("Wi-Fi 라우팅 확인 중...");
        io.execute(()->{
            try {
                String body=RelayClient.health(this,h);
                show("올인원 직접 접속 성공 ✅\n"+body);
            }catch(Exception e){show("올인원 직접 접속 실패: "+e.getMessage()+
                    "\n올인원에 표시된 Wi-Fi IP를 확인하세요.\n"+
                    "Android Auto의 무선 연결망에서 일반 앱 통신이 차단될 수 있습니다.");}
        });
    }
    private void pair(){
        String h=host.getText().toString().trim(),p=pin.getText().toString().trim();
        status.setText("페어링 중...");
        io.execute(()->{
            try {RelayClient.pair(this,h,p);show("페어링 성공 ✅\n③ Binder 상태 확인을 누르세요.");}
            catch(Exception e){show("페어링 실패: "+e.getMessage());}
        });
    }
    private void diagnostics(){
        status.setText("FM 서비스 상태 확인 중...");
        io.execute(()->{
            try{
                JSONObject s=RelayClient.status(this);
                show("직접 연결 성공 ✅\n"+s.toString(2));
            }catch(Exception e){show("조회 실패: "+e.getMessage());}
        });
    }
    private void testRadio(String action,Integer freq){
        status.setText("실제 FM 명령 확인 중...");
        io.execute(()->{
            try{
                JSONObject result=RelayClient.command(this,action,freq);
                show("실제 튜너 명령 응답:\n"+result.toString(2)
                        +"\n※ 동작 후 CMZX 순정 라디오 주파수와 소리도 확인하세요.");
            }catch(Exception e){show("FM 명령 실패: "+e.getMessage());}
        });
    }
    private void show(String s){runOnUiThread(()->status.setText(s));}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
