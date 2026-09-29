package kr.song.peugeot.frontoemv16;

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
        setTitle("5008 OEM 전방경로 v16");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 OEM 전방영상 경로 v16");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("camera.apk 정적분석 결과를 반영한 시험입니다.\n"
                + "FrontViewActivity의 source 3 대신, Raise panoramic 경로가 사용하는 source 2를 R 해제 후 유지해 봅니다.\n"
                + "CANBox/CAN/SharedVar 쓰기 없음. Media videoSource 전환만 시험합니다.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        add(root, "1. R→D 상태 기록만 — 영상경로 변경 없음", ProbeService.ACTION_OBSERVE);
        add(root, "2. R→D 후 OEM 입력(source 2) 7초 시험", ProbeService.ACTION_SOURCE2);

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
