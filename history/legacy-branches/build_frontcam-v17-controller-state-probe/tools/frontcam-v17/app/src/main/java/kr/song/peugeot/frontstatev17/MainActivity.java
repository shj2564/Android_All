package kr.song.peugeot.frontstatev17;

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
        setTitle("5008 전방상태 진단 v17");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 전방카메라 컨트롤 상태 v17");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v16 결과: R 중 reverseOpen=true, R 해제 즉시 false / frontOpen은 계속 false.\n"
                + "camera.apk 정적분석상 FrontCameraActivity는 frontOpen=false이면 즉시 종료합니다.\n"
                + "v17은 빠진 컨트롤 상태를 두 경로로 분리 시험합니다. CAN/CANBox/GPIO/MCU 쓰기는 하지 않습니다.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        add(root, "1. R→D 상태 추적만 (완전 읽기 전용)", ProbeService.ACTION_OBSERVE);
        add(root, "2. R→D → frontOpen=true + FrontCamera(source 3) 7초", ProbeService.ACTION_FRONT_OPEN);
        add(root, "3. R→D → camera.car.panoramic=true + CarCamera(source 2) 7초", ProbeService.ACTION_PANORAMIC);

        Button refresh = new Button(this);
        refresh.setText("결과 새로고침");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        root.addView(refresh);

        Button stop = new Button(this);
        stop.setText("시험 중지 / 상태 원복");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ProbeService.class);
                i.setAction(ProbeService.ACTION_STOP);
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

    private void add(LinearLayout root, String label, final String action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ProbeService.class);
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
        SharedPreferences p = getSharedPreferences(ProbeService.PREFS, MODE_PRIVATE);
        String s = p.getString(ProbeService.KEY_LOG, "");
        if (s.length() == 0) s = "아직 시험 기록이 없습니다.";
        log.setText("\n" + s);
    }
}
