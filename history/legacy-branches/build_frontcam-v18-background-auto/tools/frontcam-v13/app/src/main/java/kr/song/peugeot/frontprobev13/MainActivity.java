package kr.song.peugeot.frontprobev13;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView log;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("5008 전방 경로 진단 v13");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 전방 카메라 HANDLER PROBE v13");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v12 다음 단계: R 해제 트리거와 화면 호출 경로를 분리 진단합니다.\n"
                + "차량 CAN write/rawWrite, SharedVar set, FrontGearStart 변경은 하지 않습니다.\n"
                + "먼저 1번으로 실제 FrontCamera 처리 컴포넌트를 확인하세요.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        addButton(root, "1. FrontCamera 핸들러/카메라 앱 조회", FrontProbeService.ACTION_QUERY);
        addButton(root, "2. 수동 시험 — implicit FrontCamera 1회", FrontProbeService.ACTION_MANUAL_IMPLICIT);
        addButton(root, "3. 수동 시험 — explicit 처리기 1회", FrontProbeService.ACTION_MANUAL_EXPLICIT);
        addButton(root, "4. 실차 시험 — P → R(3~5초) → D / 7초 재요청", FrontProbeService.ACTION_ARM);

        Button refresh = new Button(this);
        refresh.setText("결과 새로고침");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        root.addView(refresh);

        Button stop = new Button(this);
        stop.setText("시험 중지");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, FrontProbeService.class);
                i.setAction(FrontProbeService.ACTION_STOP);
                startService(i);
                refresh();
            }
        });
        root.addView(stop);

        log = new TextView(this);
        log.setTextSize(14f);
        log.setTextIsSelectable(true);
        root.addView(log);

        setContentView(scroll);
        refresh();
    }

    private void addButton(LinearLayout root, String label, final String action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, FrontProbeService.class);
                i.setAction(action);
                startService(i);
                refresh();
            }
        });
        root.addView(b);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        SharedPreferences p = getSharedPreferences(FrontProbeService.PREFS, MODE_PRIVATE);
        String s = p.getString(FrontProbeService.KEY_LOG, "");
        if (s.length() == 0) s = "아직 진단 기록이 없습니다.";
        log.setText("\n" + s);
    }
}
