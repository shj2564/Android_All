package kr.song.peugeot.frontautov18;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView log;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("5008 주차 카메라 자동복구");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 주차 카메라 자동복구 v18.2");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v16 실차 성공 로직을 유지하면서 협소 주차용 빠른 전환을 추가했습니다.\n"
                + "첫 R→D는 기존 성공 타이밍 유지, 이후 R↔D 반복 시 FrontView를 닫지 않고 warm 상태로 유지합니다.\n"
                + "R 재진입 즉시 후방 우선 / 전방 7초 타이머는 다음 변속 때 자동 취소·재설정.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        status = new TextView(this);
        status.setTextSize(18f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setPadding(0, 16, 0, 16);
        root.addView(status);

        Button start = new Button(this);
        start.setText("백그라운드 자동복구 켜기");
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AutoFrontService.setEnabled(MainActivity.this, true);
                startAutoService();
                refresh();
            }
        });
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("백그라운드 자동복구 끄기");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AutoFrontService.setEnabled(MainActivity.this, false);
                Intent i = new Intent(MainActivity.this, AutoFrontService.class);
                i.setAction(AutoFrontService.ACTION_STOP);
                try { startService(i); } catch (Throwable ignored) {}
                refresh();
            }
        });
        root.addView(stop);

        Button clear = new Button(this);
        clear.setText("진단 로그 지우기");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(AutoFrontService.PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(AutoFrontService.KEY_LOG, "")
                        .apply();
                refresh();
            }
        });
        root.addView(clear);

        TextView note = new TextView(this);
        note.setText("\n※ 설치 후 이 앱을 한 번 실행하면 자동복구가 기본 ON으로 시작됩니다.\n"
                + "※ 이후 정상 부팅 때 자동 시작합니다.\n"
                + "※ gear 값은 실차에서 신뢰할 수 없어 사용하지 않고 isReversing true→false만 사용합니다.\n"
                + "※ CAN/CANBox/SharedVar/GPIO/MCU 직접 쓰기는 없습니다.\n");
        note.setTextSize(14f);
        root.addView(note);

        log = new TextView(this);
        log.setTextSize(13f);
        log.setTextIsSelectable(true);
        root.addView(log);

        setContentView(scroll);

        // Auto-build: first launch should immediately enable the background service.
        if (AutoFrontService.isEnabled(this)) startAutoService();
    }

    private void startAutoService() {
        Intent i = new Intent(this, AutoFrontService.class);
        i.setAction(AutoFrontService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Throwable t) {
            getSharedPreferences(AutoFrontService.PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(AutoFrontService.KEY_LAST_EVENT,
                            "서비스 시작 오류: " + t.getClass().getSimpleName())
                    .apply();
        }
    }

    private void refresh() {
        SharedPreferences p = getSharedPreferences(AutoFrontService.PREFS, MODE_PRIVATE);
        boolean enabled = p.getBoolean(AutoFrontService.KEY_ENABLED, true);
        boolean running = p.getBoolean(AutoFrontService.KEY_RUNNING, false);
        int count = p.getInt(AutoFrontService.KEY_SUCCESS_COUNT, 0);
        String last = p.getString(AutoFrontService.KEY_LAST_EVENT, "없음");
        String body = p.getString(AutoFrontService.KEY_LOG, "");

        status.setText("자동복구: " + (enabled ? "ON" : "OFF")
                + "  |  서비스: " + (running ? "실행 중" : "정지")
                + "\n성공 횟수: " + count
                + "  |  마지막 상태: " + last);

        if (body.length() == 0) body = "아직 기록이 없습니다.";
        log.setText("\n" + body);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }
}
