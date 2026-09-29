package kr.song.peugeot.rearsourceholdv16;

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
        setTitle("5008 Rear Source Hold v16");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 R→D Rear Source Hold 실차시험 v16");
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("목적: R에서 정상인 RearCameraActivity(source 2)를 R 해제 뒤에도 약 7초 재유지합니다.\n"
                + "FrontView/source 3은 사용하지 않습니다.\n"
                + "차량 CAN write/rawWrite, SharedVar set, FrontGearStart, MCU/decoder 설정 변경 없음.\n"
                + "각 시험은 P에서 시작 → R 3~5초 → D 순서로 진행하세요.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        addButton(root, "1. R 해제 즉시(0ms) Rear source 2 — 7초 유지", RearSourceHoldService.ACTION_ARM_0);
        addButton(root, "2. R 해제 +100ms Rear source 2 — 7초 유지", RearSourceHoldService.ACTION_ARM_100);
        addButton(root, "3. R 해제 +300ms Rear source 2 — 7초 유지", RearSourceHoldService.ACTION_ARM_300);
        addButton(root, "4. R 해제 +500ms Rear source 2 — 7초 유지", RearSourceHoldService.ACTION_ARM_500);
        addButton(root, "5. R 해제 +100ms Rear source 2 — 1회만 실행", RearSourceHoldService.ACTION_ARM_100_ONESHOT);

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
                Intent i = new Intent(MainActivity.this, RearSourceHoldService.class);
                i.setAction(RearSourceHoldService.ACTION_STOP);
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
                Intent i = new Intent(MainActivity.this, RearSourceHoldService.class);
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
        SharedPreferences p = getSharedPreferences(RearSourceHoldService.PREFS, MODE_PRIVATE);
        String s = p.getString(RearSourceHoldService.KEY_LOG, "");
        if (s.length() == 0) s = "아직 시험 기록이 없습니다.";
        log.setText("\n" + s);
    }
}
