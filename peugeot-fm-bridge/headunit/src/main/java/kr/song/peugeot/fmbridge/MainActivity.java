package kr.song.peugeot.fmbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView state;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30,26,30,20);
        TextView title=new TextView(this);
        title.setText("CMZX FM Bridge · 직접 연결");
        title.setTextSize(22);root.addView(title);
        TextView desc=new TextView(this);
        desc.setText("\nVPS 사용 없음 · AASink 수정 없음\n올인원과 보조폰이 같은 Wi-Fi 연결에서 직접 통신합니다.\n");
        root.addView(desc);
        state=new TextView(this);state.setTextSize(17);root.addView(state);
        Button refresh=new Button(this);refresh.setText("IP / Binder 상태 확인");root.addView(refresh);
        refresh.setOnClickListener(v->showStatus());
        Button start=new Button(this);start.setText("로컬 FM 서비스 다시 시작");root.addView(start);
        start.setOnClickListener(v->{launchService();showStatus();});
        launchService();showStatus();setContentView(root);
    }

    private void launchService() {
        Intent i=new Intent(this,BridgeService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i);
        else startService(i);
    }

    private String ips() {
        StringBuilder s=new StringBuilder();
        try {
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            while(en.hasMoreElements()) {
                NetworkInterface nif=en.nextElement();
                if(!nif.isUp()||nif.isLoopback())continue;
                Enumeration<InetAddress> ips=nif.getInetAddresses();
                while(ips.hasMoreElements()){
                    InetAddress addr=ips.nextElement();
                    if(addr instanceof Inet4Address&&!addr.isLoopbackAddress()) {
                        s.append(nif.getName()).append(": ").append(addr.getHostAddress()).append(":38765\n");
                    }
                }
            }
        }catch(Exception ex){s.append("IP 확인 실패: ").append(ex.getMessage());}
        return s.length()==0?"연결 IP 없음":s.toString();
    }

    private void showStatus() {
        state.setText("로컬 서버 포트: 38765\n"+
                "페어링 코드: "+LocalFmServer.pairingCode(this)+"\n"+
                "토큰은 앱 내부에만 저장됩니다.\n\n"+
                "올인원 IP (보조폰에 입력):\n"+ips()+"\n"+
                "Binder 상태는 로컬 FM 명령 응답에도 표시됩니다.\n"+
                "※ Wi-Fi가 서로 라우팅 가능한지 보조폰에서 확인해야 합니다.");
    }
}
