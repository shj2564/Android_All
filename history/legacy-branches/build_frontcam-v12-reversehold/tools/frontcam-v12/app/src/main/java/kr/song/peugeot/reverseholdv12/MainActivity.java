package kr.song.peugeot.reverseholdv12;

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
        setTitle("5008 R→D 7초 시험");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 R→D 7초 HOLD 시험 v12");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("실제 D 기어값을 사용하지 않습니다.\n"
                + "isReversing=true → false를 감지하면 FrontCamera 화면을 7초 동안 유지 요청합니다.\n"
                + "CAN write/rawWrite, FrontGearStart 변경, SharedVar set 없음.");
        desc.setTextSize(16f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        Button arm = new Button(this);
        arm.setText("1. 시험 시작 — 누른 뒤 P → R(3~5초) → D");
        arm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ReverseHoldService.class);
                i.setAction(ReverseHoldService.ACTION_ARM);
                startService(i);
                refresh();
            }
        });
        root.addView(arm);

        Button refresh = new Button(this);
        refresh.setText("2. 결과 새로고침");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        root.addView(refresh);

        Button stop = new Button(this);
        stop.setText("시험 중지");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ReverseHoldService.class);
                i.setAction(ReverseHoldService.ACTION_STOP);
                startService(i);
                refresh();
            }
        });
        root.addView(stop);

        log = new TextView(this);
        log.setTextSize(15f);
        log.setTextIsSelectable(true);
        root.addView(log);
        setContentView(scroll);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        SharedPreferences p = getSharedPreferences(ReverseHoldService.PREFS, MODE_PRIVATE);
        String s = p.getString(ReverseHoldService.KEY_LOG, "");
        if (s.length() == 0) s = "아직 시험 기록이 없습니다.";
        log.setText("\n" + s);
    }
}
