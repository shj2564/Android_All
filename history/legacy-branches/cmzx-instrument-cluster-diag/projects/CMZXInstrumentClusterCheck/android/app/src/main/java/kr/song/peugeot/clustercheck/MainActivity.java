package kr.song.peugeot.clustercheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler();
    private final List<Button> actions = new ArrayList<Button>();
    private TextView status, detail;
    private Spinner leftSpinner, rightSpinner;
    private Button apply;
    private String report = "아직 시험하지 않았습니다.";
    private volatile Attempt active;
    private boolean ready, foreground, destroyed;

    private static final String[] OPTIONS = {
        "선택하세요",
        "0 · 없음 (None)",
        "1 · 주행 보조 (Driving Assistance)",
        "2 · 엔진 정보 (Engine Info)",
        "3 · 가속도계 (Accelerometer)",
        "4 · 온도 (Temperature)",
        "5 · 내비게이션 (Navigation)",
        "6 · 회전계 (Tachometer)",
        "7 · 트립 컴퓨터 (Trip Computer)",
        "8 · 미디어 (Media)"
    };

    private static final class Attempt {
        final int left, right;
        final CountDownLatch connected = new CountDownLatch(1);
        volatile boolean cancelled, bound;
        volatile IBinder binder;
        volatile String bindError;
        volatile TrialPolicy.Permit permit;
        ServiceConnection connection;

        Attempt(int left, int right) {
            this.left = left; this.right = right;
        }

        boolean isCheckOnly() { return left < 0 || right < 0; }

        void cancelBeforeSubmission() {
            cancelled = true;
            TrialPolicy.Permit p = permit;
            if (p != null) p.cancel();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(18, 29, 44));
        scroll.addView(root);

        root.addView(text("5008 계기판 PERSONAL 설정 진단 · 0.1", 24));
        root.addView(text(
            "CMZXR62N-U1 / Raise PSA-RZ-15 전용 시험입니다.\n" +
            "앱을 여는 것만으로는 차량 설정을 보내지 않습니다.\n" +
            "정차·P단·주차브레이크 상태에서 먼저 차량 연결을 확인하세요.", 16));

        button(root, "1. 차량 연결 확인", new View.OnClickListener() {
            @Override public void onClick(View v) { start(-1, -1); }
        }, true);

        root.addView(text("왼쪽 PERSONAL 표시", 18));
        leftSpinner = new Spinner(this);
        leftSpinner.setAdapter(new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, OPTIONS));
        root.addView(leftSpinner);

        root.addView(text("오른쪽 PERSONAL 표시", 18));
        rightSpinner = new Spinner(this);
        rightSpinner.setAdapter(new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, OPTIONS));
        root.addView(rightSpinner);

        apply = button(root, "2. 선택값 1회 전송", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmSelected(); }
        }, true);

        status = text("차량 연결 확인 대기", 20);
        root.addView(status);

        detail = text(
            "값 0~8의 의미는 PSA 계기판 PERSONAL 항목과 Hiworld/DUDU 교차분석을 기준으로 표시합니다.\n" +
            "실제 차량 반영 여부는 계기판 PERSONAL 화면을 직접 확인하세요.", 15);
        detail.setTextIsSelectable(true);
        root.addView(detail);

        button(root, "시험 결과 복사", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("5008 계기판 시험", report));
                Toast.makeText(MainActivity.this, "시험 결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
            }
        }, false);

        button(root, "시험 결과 공유", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, report);
                try { startActivity(Intent.createChooser(send, "시험 결과 공유")); }
                catch (Exception e) {
                    Toast.makeText(MainActivity.this, "결과 복사를 이용하세요.", Toast.LENGTH_SHORT).show();
                }
            }
        }, false);

        button(root, "이전 시험 기록 보기", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (active != null) return;
                report = getSharedPreferences("cluster_trial", 0)
                    .getString("last_report", "저장된 시험 기록이 없습니다.");
                status.setText("이전에 저장한 기록");
                detail.setText(report);
            }
        }, false);

        setContentView(scroll);
        refreshButtons();
    }

    private TextView text(String value, int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(235, 241, 248));
        v.setPadding(0, 8, 0, 12);
        return v;
    }

    private Button button(LinearLayout root, String label, View.OnClickListener click, boolean action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setOnClickListener(click);
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
        if (action) actions.add(b);
        return b;
    }

    private void refreshButtons() {
        for (Button b : actions) b.setEnabled(active == null && foreground);
        if (apply != null) apply.setEnabled(active == null && foreground && ready);
        if (leftSpinner != null) leftSpinner.setEnabled(active == null && foreground && ready);
        if (rightSpinner != null) rightSpinner.setEnabled(active == null && foreground && ready);
    }

    private int selectedValue(Spinner spinner) {
        return spinner.getSelectedItemPosition() - 1;
    }

    private void confirmSelected() {
        if (active != null || !ready || !foreground) return;

        final int left = selectedValue(leftSpinner);
        final int right = selectedValue(rightSpinner);

        if (left < 0 || right < 0) {
            Toast.makeText(this, "좌·우 값을 모두 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String summary =
            "왼쪽: " + left + " · " + Protocol.label(left) + "\n" +
            "오른쪽: " + right + " · " + Protocol.label(right) + "\n\n" +
            "예상 프레임: " + Protocol.hex(Protocol.expectedClusterFrame(left, right));

        new AlertDialog.Builder(this)
            .setTitle("계기판 PERSONAL 설정을 1회 전송할까요?")
            .setMessage(
                "정차·P단·주차브레이크 상태에서 실행하세요.\n" +
                "자동 재전송·자동 원복은 하지 않습니다.\n\n" + summary)
            .setNegativeButton("취소", null)
            .setPositiveButton("1회 전송", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    start(left, right);
                }
            }).show();
    }

    private void start(int left, int right) {
        if (active != null || !foreground || ((left >= 0 || right >= 0) && !ready)) return;

        final Attempt attempt = new Attempt(left, right);
        active = attempt;
        ready = false;
        refreshButtons();

        report = "[CLUSTER_TRIAL_0_1]\n시각: " +
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()) +
            "\n요청: " +
            (attempt.isCheckOnly()
                ? "연결 확인 (설정 송신 없음)"
                : "LEFT=" + left + " / RIGHT=" + right + " 1회") +
            "\n";

        status.setText("차량 조건 확인 중");
        detail.setText(report);
        final String heading = report;

        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (active != attempt) return;
                attempt.cancelBeforeSubmission();
                status.setText("응답 대기 시간 초과");
                report +=
                    "\n응답이 지연되어 송신 전 요청을 취소했습니다. 자동 재전송하지 않습니다.";
                detail.setText(report);
            }
        }, 20000);

        Thread worker = new Thread(new Runnable() {
            @Override public void run() { execute(attempt, heading); }
        }, "ClusterTrial");
        worker.setDaemon(true);
        worker.start();
    }

    private void checkpoint(Attempt a) throws Exception {
        if (a.cancelled) throw new Exception("송신 전 작업이 취소됐습니다.");
    }

    private IBinder bind(final Attempt a) throws Exception {
        ui.post(new Runnable() {
            @Override public void run() {
                if (a.cancelled || destroyed) {
                    a.connected.countDown();
                    return;
                }

                a.connection = new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName n, IBinder b) {
                        a.binder = b;
                        a.connected.countDown();
                    }

                    @Override public void onServiceDisconnected(ComponentName n) {
                        a.cancelBeforeSubmission();
                    }
                };

                try {
                    Intent intent = new Intent("com.reglink.action.DroidCarService");
                    intent.setComponent(new ComponentName(
                        Protocol.SERVICE_PACKAGE,
                        "com.reglink.services.DroidCarService"));
                    a.bound = bindService(intent, a.connection, 0);

                    if (!a.bound) {
                        a.bindError = "실행 중인 차량 서비스에 연결되지 않았습니다.";
                        a.connected.countDown();
                    }
                } catch (Exception e) {
                    a.bindError = e.toString();
                    a.connected.countDown();
                }
            }
        });

        if (!a.connected.await(8, TimeUnit.SECONDS))
            throw new Exception("차량 서비스 연결 시간이 초과됐습니다.");

        checkpoint(a);

        if (a.binder == null)
            throw new Exception(a.bindError == null ? "차량 서비스가 없습니다." : a.bindError);

        return a.binder;
    }

    private void execute(final Attempt a, String heading) {
        StringBuilder out = new StringBuilder(heading);
        boolean permitted = false;
        boolean returned = false;

        try {
            PackageInfo info = getPackageManager().getPackageInfo(Protocol.SERVICE_PACKAGE, 0);

            Map<String, String> fields = new LinkedHashMap<String, String>();
            fields.put("serviceVersion", info.versionName);
            fields.put("serviceCode", String.valueOf(info.versionCode));
            fields.put("serviceSha256", sha256(info.applicationInfo.sourceDir));
            fields.put("api", String.valueOf(Build.VERSION.SDK_INT));
            fields.put("hardware", Build.HARDWARE);

            if (!Protocol.SOURCE_SHA256.equals(fields.get("serviceSha256")) ||
                info.versionCode != 26012914 ||
                !"260129-1424".equals(info.versionName))
                throw new Exception("기준 ReglinkService APK와 달라 연결하지 않았습니다.");

            checkpoint(a);

            BinderReader platform = new BinderReader(bind(a), Protocol.DROID);
            fields.putAll(platform.platformInfo());

            IBinder canBinder = platform.getCanBox();
            BinderReader box = new BinderReader(canBinder, Protocol.CANBOX);

            fields.put("canBoxType", box.name(6));
            fields.put("decoder", box.name(10));

            BinderReader.Packet version = box.lastPacket(0x7F);
            fields.put("boxVersion", version == null ? "" : Protocol.ascii(version.data));
            fields.put("envVersion", shared("Env.CANBoxVersion", 1));
            fields.put("scriptVersion", shared("script.version", 0));

            boolean connected = box.flag(5);
            boolean suspended = box.flag(14);

            byte[] frame00 = box.encodeCluster(0, 0);
            byte[] frame88 = box.encodeCluster(8, 8);

            TrialPolicy.Snapshot snapshot =
                new TrialPolicy.Snapshot(fields, connected, suspended, frame00, frame88);

            for (Map.Entry<String, String> e : fields.entrySet())
                out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');

            out.append("CANBox 연결: ").append(connected)
                .append(" / 일시중지: ").append(suspended).append('\n')
                .append("서비스 0/0 계산: ").append(Protocol.hex(frame00)).append('\n')
                .append("서비스 8/8 계산: ").append(Protocol.hex(frame88)).append('\n');

            String rejection = TrialPolicy.rejection(snapshot);
            if (rejection != null) throw new Exception(rejection);

            checkpoint(a);
            permitted = true;
            out.append("실차 기준 및 0x8C 인코딩: 일치\n");

            if (!a.isCheckOnly()) {
                if (!box.flag(5) ||
                    box.flag(14) ||
                    !"raise-psa".equals(box.name(10)) ||
                    !"raise".equals(box.name(6)))
                    throw new Exception("송신 직전에 CANBox 조건이 변경됐습니다.");

                checkpoint(a);

                a.permit = TrialPolicy.authorize(
                    snapshot, a.left, a.right, SystemClock.elapsedRealtime());

                if (a.cancelled) a.permit.cancel();

                byte[] expected = Protocol.expectedClusterFrame(a.left, a.right);
                byte[] service = box.encodeCluster(a.left, a.right);

                if (!java.util.Arrays.equals(expected, service))
                    throw new Exception("선택값의 서비스 인코딩 결과가 기준과 다릅니다.");

                out.append("왼쪽: ").append(a.left)
                    .append(" · ").append(Protocol.label(a.left)).append('\n')
                    .append("오른쪽: ").append(a.right)
                    .append(" · ").append(Protocol.label(a.right)).append('\n')
                    .append("요청 형식: ").append(Protocol.hex(expected)).append('\n');

                if (!getSharedPreferences("cluster_trial", 0).edit()
                    .putString("last_report", out.toString() + "설정 호출 직전 기록\n")
                    .commit())
                    throw new Exception("변경 전 기록을 저장하지 못해 전송하지 않았습니다.");

                new ClusterTrialBridge(canBinder).sendClusterOnce(a.permit);
                returned = true;

                out.append("설정 write 호출: 예외 없이 반환 (차량/ECU 적용 ACK 아님)\n")
                    .append("자동 재전송 없음 / 자동 원복 없음\n")
                    .append("계기판을 PERSONAL 모드로 바꿔 좌·우 표시가 선택값으로 변경됐는지 직접 확인하세요.\n");
            } else {
                out.append("차량 연결 확인 완료. 계기판 설정 명령은 전송하지 않았습니다.\n");
            }
        } catch (Exception e) {
            permitted = false;
            out.append("\n중단: ")
                .append(e.getClass().getSimpleName())
                .append(": ").append(e.getMessage()).append('\n');
        } finally {
            final boolean attempted = a.permit != null && a.permit.claimed();

            out.append("\n설정 호출 시도: ").append(attempted ? "1회" : "0회")
                .append("\n설정 호출 정상 반환: ").append(returned)
                .append("\n실제 계기판 반영: 사용자 확인 필요\n");

            final String body = out.toString();
            final boolean canContinue = permitted && !a.cancelled;

            getSharedPreferences("cluster_trial", 0)
                .edit().putString("last_report", body).commit();

            ui.post(new Runnable() {
                @Override public void run() {
                    if (a.bound && a.connection != null) {
                        try { unbindService(a.connection); }
                        catch (Exception ignored) { }
                        a.bound = false;
                    }

                    if (active != a) return;

                    active = null;
                    ready = canContinue;

                    if (destroyed) return;

                    report = body;
                    detail.setText(body);

                    status.setText(
                        attempted
                            ? "시험 기록 완료"
                            : canContinue
                                ? "차량 조건 일치 · 설정 시험 가능"
                                : "조건 확인 또는 응답 미완료");

                    refreshButtons();
                }
            });
        }
    }

    private String shared(String key, int type) {
        try {
            Bundle args = new Bundle();
            args.putInt("value_type", type);

            Bundle result = getContentResolver().call(
                Uri.parse("content://com.reglink.services.provider.sharedvar"),
                "get", key, args);

            if (result != null && result.get("value") != null)
                return String.valueOf(result.get("value"));
        } catch (Exception ignored) { }

        return "";
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream stream = new FileInputStream(path);

        try {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = stream.read(buffer)) != -1)
                digest.update(buffer, 0, count);
        } finally {
            stream.close();
        }

        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest())
            out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }

    @Override public void onResume() {
        super.onResume();
        foreground = true;
        refreshButtons();
    }

    @Override public void onPause() {
        foreground = false;
        Attempt a = active;
        if (a != null) a.cancelBeforeSubmission();
        super.onPause();
    }

    @Override public void onDestroy() {
        destroyed = true;
        foreground = false;
        Attempt a = active;
        if (a != null) a.cancelBeforeSubmission();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
