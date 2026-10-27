package kr.song.peugeot.fm;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText server, code;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40,40,40,40);
        TextView title = new TextView(this); title.setText("Peugeot FM · Android Auto"); title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(-1,-2));
        TextView info = new TextView(this);
        info.setText("\nAndroid Auto에서 실제 CMZX FM을 제어합니다.\n최초 1회만 페어링 코드를 입력하세요.\n");
        root.addView(info);
        server = new EditText(this); server.setHint("VPS 주소"); server.setText(ConfigStore.server(this));
        root.addView(server, new LinearLayout.LayoutParams(-1,-2));
        code = new EditText(this); code.setHint("8자리 페어링 코드"); code.setInputType(2);
        root.addView(code, new LinearLayout.LayoutParams(-1,-2));
        Button pair = new Button(this); pair.setText("VPS 연결 / 페어링");
        root.addView(pair, new LinearLayout.LayoutParams(-1,-2));
        status = new TextView(this); status.setTextSize(16); status.setPadding(0,24,0,0);
        root.addView(status);
        pair.setOnClickListener(v -> doPair());
        showStatus();
        setContentView(root);
    }

    private void showStatus() {
        status.setText(ConfigStore.paired(this)
                ? "연결됨 ✅\nAndroid Auto 앱 목록에서 Peugeot FM을 선택하세요."
                : "아직 페어링되지 않았습니다.");
    }

    private void doPair() {
        final String s=server.getText().toString().trim(), p=code.getText().toString().trim();
        status.setText("연결 중...");
        io.execute(() -> {
            try {
                RelayClient.pair(this,s,p);
                runOnUiThread(() -> { code.setText(""); showStatus(); });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("연결 실패: " + e.getMessage()));
            }
        });
    }
}
