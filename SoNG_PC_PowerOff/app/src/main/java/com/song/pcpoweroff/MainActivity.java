package com.song.pcpoweroff;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final String BASE = "https://52.78.116.196";
    private static final String PREFS = "song_power_prefs";
    private static final String KEY_TOKEN = "api_token";
    private static final String KEY_SHUTDOWN_PENDING_UNTIL = "shutdown_pending_until";
    private static final long SHUTDOWN_MONITOR_MS = 180_000L;
    private static final long SHUTDOWN_POLL_MS = 5_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status;
    private Button power;
    private Button refresh;
    private boolean pcOnline = false;
    private boolean shutdownMonitoring = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        String token = prefs.getString(KEY_TOKEN, "");
        if (!isValidToken(token)) {
            power.setEnabled(false);
            showTokenDialog(true);
        } else {
            resumeOrCheck();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && isValidToken(prefs.getString(KEY_TOKEN, ""))) resumeOrCheck();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        shutdownMonitoring = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(28);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(32), pad, dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("SoNG PC 종료");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("외부에서 SoNG-HoME 안전 종료");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(105, 105, 105));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        status = new TextView(this);
        status.setText("연결 확인 중…");
        status.setTextSize(17);
        status.setTextColor(Color.rgb(80, 80, 80));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = dp(34);
        root.addView(status, stLp);

        Space spacer = new Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(28)));

        power = new Button(this);
        power.setText("PC 종료");
        power.setTextSize(21);
        power.setTextColor(Color.WHITE);
        power.setAllCaps(false);
        power.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(198, 40, 40)));
        power.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_lock_power_off, 0, 0, 0);
        power.setCompoundDrawablePadding(dp(12));
        power.setOnClickListener(v -> confirmShutdown());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(70));
        root.addView(power, pLp);

        refresh = new Button(this);
        refresh.setText("상태 새로고침");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> checkHealth());
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, dp(52));
        rLp.topMargin = dp(14);
        root.addView(refresh, rLp);

        Button key = new Button(this);
        key.setText("인증키 변경");
        key.setAllCaps(false);
        key.setOnClickListener(v -> showTokenDialog(false));
        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(-1, dp(48));
        kLp.topMargin = dp(8);
        root.addView(key, kLp);

        TextView note = new TextView(this);
        note.setText("종료 요청 후 Windows에서 약 60초 유예 후 정상 종료됩니다.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(120, 120, 120));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-1, -2);
        nLp.topMargin = dp(24);
        root.addView(note, nLp);

        return root;
    }

    private void showTokenDialog(boolean firstRun) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("64자리 앱 인증키");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setText(prefs.getString(KEY_TOKEN, ""));
        input.setSelectAllOnFocus(true);
        int m = dp(18);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(m, 0, m, 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstRun ? "앱 인증키 설정" : "인증키 변경")
                .setMessage("최초 1회만 입력하면 이 휴대폰에 저장됩니다.")
                .setView(box)
                .setNegativeButton(firstRun ? "종료" : "취소", (d, w) -> {
                    if (firstRun) finish();
                })
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String token = input.getText().toString().trim().toLowerCase();
            if (!isValidToken(token)) {
                input.setError("64자리 영문/숫자(16진수) 키를 입력하세요.");
                return;
            }
            prefs.edit().putString(KEY_TOKEN, token).apply();
            dialog.dismiss();
            checkHealth();
        }));
        dialog.show();
    }

    private boolean isValidToken(String s) {
        return s != null && s.matches("[0-9a-fA-F]{64}");
    }

    private void resumeOrCheck() {
        long until = prefs.getLong(KEY_SHUTDOWN_PENDING_UNTIL, 0L);
        if (until > System.currentTimeMillis()) {
            startShutdownMonitor();
        } else {
            if (until != 0L) prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
            checkHealth();
        }
    }

    private void checkHealth() {
        final String token = prefs.getString(KEY_TOKEN, "");
        if (!isValidToken(token)) {
            status.setText("인증키가 필요합니다.");
            power.setEnabled(false);
            return;
        }
        setBusy(true, "VPS 및 PC 상태 확인 중…");
        executor.execute(() -> {
            try {
                Result r = request("GET", "/__songpower/health", token);
                if (r.code == 200 && r.body.contains("\"pc_online\":true")) {
                    runOnUiThread(() -> {
                        pcOnline = true;
                        status.setText("● PC 온라인 · VPS 연결 정상");
                        status.setTextColor(Color.rgb(35, 125, 55));
                        setBusy(false, null);
                    });
                } else if (r.code == 200) {
                    runOnUiThread(() -> {
                        pcOnline = false;
                        status.setText("○ PC 오프라인 · VPS 연결 정상");
                        status.setTextColor(Color.rgb(110, 110, 110));
                        setBusy(false, null);
                    });
                } else if (r.code == 401) {
                    runOnUiThread(() -> {
                        pcOnline = false;
                        status.setText("인증키가 올바르지 않습니다.");
                        status.setTextColor(Color.rgb(190, 40, 40));
                        setBusy(false, null);
                    });
                } else {
                    throw new Exception("HTTP " + r.code);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pcOnline = false;
                    status.setText("VPS 연결 실패");
                    status.setTextColor(Color.rgb(190, 40, 40));
                    setBusy(false, null);
                });
            }
        });
    }

    private void confirmShutdown() {
        if (!pcOnline) {
            Toast.makeText(this, "PC가 온라인일 때 종료할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("SoNG-HoME 종료")
                .setMessage("PC를 정상 종료할까요?\n저장하지 않은 작업이 있다면 먼저 저장하세요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("종료", (d, w) -> sendShutdown())
                .show();
    }

    private void sendShutdown() {
        final String token = prefs.getString(KEY_TOKEN, "");
        setBusy(true, "종료 요청 전송 중…");
        executor.execute(() -> {
            try {
                Result r = request("POST", "/__songpower/shutdown", token);
                if (r.code == 202 && r.body.contains("\"queued\":true")) {
                    prefs.edit().putLong(KEY_SHUTDOWN_PENDING_UNTIL,
                            System.currentTimeMillis() + SHUTDOWN_MONITOR_MS).apply();
                    runOnUiThread(this::startShutdownMonitor);
                } else if (r.code == 409) {
                    runOnUiThread(() -> {
                        status.setText("PC가 오프라인입니다.");
                        pcOnline = false;
                        setBusy(false, null);
                    });
                } else if (r.code == 401) {
                    runOnUiThread(() -> {
                        status.setText("인증키가 올바르지 않습니다.");
                        setBusy(false, null);
                    });
                } else {
                    throw new Exception("HTTP " + r.code);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("종료 요청 실패 · 다시 시도하세요.");
                    status.setTextColor(Color.rgb(190, 40, 40));
                    setBusy(false, null);
                });
            }
        });
    }

    private void startShutdownMonitor() {
        if (shutdownMonitoring) return;
        shutdownMonitoring = true;
        pcOnline = false;
        power.setEnabled(false);
        refresh.setEnabled(false);
        status.setText("⏳ 종료 중… PC 응답 확인 중");
        status.setTextColor(Color.rgb(230, 126, 34));
        pollShutdownState();
    }

    private void pollShutdownState() {
        if (!shutdownMonitoring || isFinishing() || isDestroyed()) return;
        final String token = prefs.getString(KEY_TOKEN, "");
        final long until = prefs.getLong(KEY_SHUTDOWN_PENDING_UNTIL, 0L);
        if (!isValidToken(token)) {
            shutdownMonitoring = false;
            prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
            status.setText("인증키가 필요합니다.");
            refresh.setEnabled(true);
            return;
        }
        executor.execute(() -> {
            try {
                Result r = request("GET", "/__songpower/health", token);
                boolean online = r.code == 200 && r.body.contains("\"pc_online\":true");
                runOnUiThread(() -> {
                    if (!shutdownMonitoring) return;
                    if (r.code == 401) {
                        shutdownMonitoring = false;
                        prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
                        status.setText("인증키가 올바르지 않습니다.");
                        status.setTextColor(Color.rgb(190, 40, 40));
                        refresh.setEnabled(true);
                        return;
                    }
                    if (!online && r.code == 200) {
                        shutdownMonitoring = false;
                        prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
                        pcOnline = false;
                        status.setText("○ PC 오프라인 · 종료 완료");
                        status.setTextColor(Color.rgb(110, 110, 110));
                        refresh.setEnabled(true);
                        power.setEnabled(false);
                        return;
                    }
                    if (System.currentTimeMillis() >= until) {
                        shutdownMonitoring = false;
                        prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
                        status.setText("종료 지연 · 상태 새로고침으로 확인하세요.");
                        status.setTextColor(Color.rgb(190, 120, 20));
                        refresh.setEnabled(true);
                        power.setEnabled(false);
                        return;
                    }
                    long remain = Math.max(0L, (until - System.currentTimeMillis()) / 1000L);
                    status.setText("⏳ 종료 중… PC 응답 대기 (" + remain + "초)");
                    status.setTextColor(Color.rgb(230, 126, 34));
                    handler.postDelayed(this::pollShutdownState, SHUTDOWN_POLL_MS);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!shutdownMonitoring) return;
                    if (System.currentTimeMillis() >= until) {
                        shutdownMonitoring = false;
                        prefs.edit().remove(KEY_SHUTDOWN_PENDING_UNTIL).apply();
                        status.setText("VPS 연결 실패 · 상태를 다시 확인하세요.");
                        status.setTextColor(Color.rgb(190, 40, 40));
                        refresh.setEnabled(true);
                    } else {
                        status.setText("⏳ 종료 중… VPS 재확인 중");
                        status.setTextColor(Color.rgb(230, 126, 34));
                        handler.postDelayed(this::pollShutdownState, SHUTDOWN_POLL_MS);
                    }
                });
            }
        });
    }

    private void setBusy(boolean busy, String text) {
        if (text != null) {
            status.setText(text);
            status.setTextColor(Color.rgb(80, 80, 80));
        }
        refresh.setEnabled(!busy);
        power.setEnabled(!busy && pcOnline);
    }

    private Result request(String method, String path, String token) throws Exception {
        URL url = new URL(BASE + path);
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setUseCaches(false);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method)) {
            byte[] data = "{}".getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(data.length);
            c.getOutputStream().write(data);
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readAll(stream);
        c.disconnect();
        return new Result(code, body);
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && sb.length() < 8192) sb.append(line);
        br.close();
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Result {
        final int code;
        final String body;
        Result(int code, String body) { this.code = code; this.body = body; }
    }
}
