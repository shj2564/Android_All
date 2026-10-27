package kr.song.peugeot.fmbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView status; private EditText server,code;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40,40,40,40);
        TextView title=new TextView(this); title.setText("CMZX FM Bridge"); title.setTextSize(24); root.addView(title);
        TextView info=new TextView(this); info.setText("\nAndroid Auto의 Peugeot FM 명령을 실제 Reglink FM 튜너로 전달합니다.\n"); root.addView(info);
        server=new EditText(this); server.setHint("VPS 주소"); server.setText(ConfigStore.server(this)); root.addView(server);
        code=new EditText(this); code.setHint("8자리 페어링 코드"); code.setInputType(2); root.addView(code);
        Button pair=new Button(this); pair.setText("VPS 연결 / 브리지 시작"); root.addView(pair);
        status=new TextView(this); status.setPadding(0,24,0,0); status.setTextSize(16); root.addView(status);
        pair.setOnClickListener(v->pair());
        if(ConfigStore.paired(this)) startService(new Intent(this,BridgeService.class));
        show(); setContentView(root);
    }
    private void show(){status.setText(ConfigStore.paired(this)?"브리지 연결됨 ✅\n부팅 후 자동 실행됩니다.":"아직 페어링되지 않았습니다.");}
    private void pair(){
        String s=server.getText().toString().trim(),p=code.getText().toString().trim(); status.setText("연결 중...");
        io.execute(()->{try{PairClient.pair(this,s,p); startService(new Intent(this,BridgeService.class));
            runOnUiThread(()->{code.setText("");show();});}
            catch(Exception e){runOnUiThread(()->status.setText("연결 실패: "+e.getMessage()));}});
    }
}
