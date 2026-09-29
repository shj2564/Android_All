package kr.song.peugeot.frontviewv14;

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
        setTitle("5008 전방뷰 진단 v14");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 FrontView 직접 진단 v14");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v13 결과: FrontCameraActivity는 검정 화면 후 즉시 종료.\n"
                + "v14는 FrontCameraActivity와 FrontViewActivity를 분리해 직접 시험합니다.\n"
                + "CAN/SharedVar/FrontGearStart/MCU 설정 변경 없음.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        addButton(root, "1. FrontCameraActivity 직접 1회", FrontViewProbeService.ACTION_FRONT_CAMERA);
        addButton(root, "2. FrontViewActivity 직접 1회", FrontViewProbeService.ACTION_FRONT_VIEW);
        addButton(root, "3. RearCameraActivity 직접 1회(비교)", FrontViewProbeService.ACTION_REAR_CAMERA);
        addButton(root, "4. 실차 R→D 후 FrontViewActivity 300ms 실행", FrontViewProbeService.ACTION_ARM_FRONT_VIEW);
        addButton(root, "5. 실차 R→D 후 FrontCameraActivity 300ms 실행", FrontViewProbeService.ACTION_ARM_FRONT_CAMERA);

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
                Intent i = new Intent(MainActivity.this, FrontViewProbeService.class);
                i.setAction(FrontViewProbeService.ACTION_STOP);
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
                Intent i = new Intent(MainActivity.this, FrontViewProbeService.class);
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
        SharedPreferences p = getSharedPreferences(FrontViewProbeService.PREFS, MODE_PRIVATE);
        String s = p.getString(FrontViewProbeService.KEY_LOG, "");
        if (s.length() == 0) s = "아직 진단 기록이 없습니다.";
        log.setText("\n" + s);
    }
}
