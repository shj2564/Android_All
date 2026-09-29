package kr.song.peugeot.mirrorcheck;

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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** User-operated, one-setting experiment. Opening this activity never sends a command. */
public final class MirrorTrialActivity extends Activity {
    private final Handler ui = new Handler();
    private final List<Button> actions = new ArrayList<Button>();
    private TextView status, detail;
    private Button enable, disable;
    private String report = "아직 시험하지 않았습니다.";
    private volatile Attempt active;
    private boolean ready, foreground, destroyed;

    private static final class Attempt {
        final int value;
        final CountDownLatch connected = new CountDownLatch(1);
        volatile boolean cancelled, bound;
        volatile IBinder binder;
        volatile String bindError;
        volatile TrialPolicy.Permit permit;
        ServiceConnection connection;
        Attempt(int value) { this.value = value; }
        void cancelBeforeSubmission() {
            cancelled = true;
            TrialPolicy.Permit current = permit;
            if (current != null) current.cancel();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding); root.setBackgroundColor(Color.rgb(18, 29, 44));
        scroll.addView(root);
        root.addView(text("후진 미러 설정 시험 · 0.3", 24));
        root.addView(text("이 화면의 켜기·끄기는 실제 차량 설정을 변경합니다.\n먼저 정차 상태에서 차량 연결을 확인하세요.\n한 번 누를 때 미러 설정 명령 1회만 시도합니다.", 17));
        button(root, "1. 차량 연결 확인", new View.OnClickListener() {
            @Override public void onClick(View v) { start(-1); }
        }, true);
        enable = button(root, "2. 미러 하향 켜기", new View.OnClickListener() {
            @Override public void onClick(View v) { confirm(1); }
        }, true);
        disable = button(root, "미러 하향 끄기", new View.OnClickListener() {
            @Override public void onClick(View v) { confirm(0); }
        }, true);
        status = text("차량 연결 확인 대기", 21); root.addView(status);
        detail = text("켜기는 순정 후진 하향 기능을 활성화하는 시험입니다.\n바로 거울을 움직이는 명령은 아닙니다.", 16);
        detail.setTextIsSelectable(true); root.addView(detail);
        button(root, "시험 결과 복사", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("5008 미러 시험", report));
                Toast.makeText(MirrorTrialActivity.this, "시험 결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
            }
        }, false);
        button(root, "시험 결과 공유", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, report);
                try { startActivity(Intent.createChooser(send, "시험 결과 공유")); }
                catch (Exception e) { Toast.makeText(MirrorTrialActivity.this, "결과 복사를 이용하세요.", Toast.LENGTH_SHORT).show(); }
            }
        }, false);
        button(root, "이전 시험 기록 보기", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (active != null) return;
                report = getSharedPreferences("mirror_trial", 0).getString("last_report", "저장된 시험 기록이 없습니다.");
                status.setText("이전에 저장한 기록"); detail.setText(report);
            }
        }, false);
        setContentView(scroll); refreshButtons();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(235, 241, 248)); view.setPadding(0, 8, 0, 12); return view;
    }
    private Button button(LinearLayout root, String label, View.OnClickListener click, boolean action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(17); button.setOnClickListener(click);
        root.addView(button, new LinearLayout.LayoutParams(-1, -2)); if (action) actions.add(button); return button;
    }
    private void refreshButtons() {
        for (Button button : actions) button.setEnabled(active == null && foreground);
        enable.setEnabled(active == null && foreground && ready);
        disable.setEnabled(active == null && foreground && ready);
    }
    private void confirm(final int value) {
        if (active != null || !ready || !foreground) return;
        new AlertDialog.Builder(this).setTitle(value == 1 ? "후진 미러 하향을 켤까요?" : "후진 미러 하향을 끌까요?")
            .setMessage("정차·P단·주차브레이크 상태에서 실행하세요.\n확인한 차량 조건을 다시 검사한 뒤 이 설정 한 항목만 전송합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton(value == 1 ? "켜기 1회 실행" : "끄기 1회 실행", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { start(value); }
            }).show();
    }
    private void start(int value) {
        if (active != null || !foreground || (value != -1 && !ready)) return;
        final Attempt attempt = new Attempt(value); active = attempt; ready = false; refreshButtons();
        report = "[MIRROR_TRIAL_0_3]\n시각: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())
            + "\n요청: " + (value < 0 ? "연결 확인 (설정 송신 없음)" : value == 1 ? "ON 1회" : "OFF 1회") + "\n";
        status.setText("차량 조건 확인 중"); detail.setText(report);
        final String heading = report;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (active != attempt) return;
                attempt.cancelBeforeSubmission();
                status.setText("응답 대기 시간 초과");
                report += "\n응답이 지연되고 있습니다. 자동 재전송하지 않습니다.\n설정 호출이 이미 시작됐다면 차량 적용 여부는 미확인입니다.";
                detail.setText(report);
            }
        }, 25000);
        Thread worker = new Thread(new Runnable() { @Override public void run() { execute(attempt, heading); } }, "MirrorTrial");
        worker.setDaemon(true); worker.start();
    }

    private void checkpoint(Attempt attempt) throws Exception {
        if (attempt.cancelled) throw new Exception("송신 전 작업이 취소됐습니다.");
    }
    private IBinder bind(final Attempt attempt) throws Exception {
        ui.post(new Runnable() {
            @Override public void run() {
                if (attempt.cancelled || destroyed) { attempt.connected.countDown(); return; }
                attempt.connection = new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                        attempt.binder = binder; attempt.connected.countDown();
                    }
                    @Override public void onServiceDisconnected(ComponentName name) { attempt.cancelBeforeSubmission(); }
                };
                try {
                    Intent intent = new Intent("com.reglink.action.DroidCarService");
                    intent.setComponent(new ComponentName(Protocol.SERVICE_PACKAGE, "com.reglink.services.DroidCarService"));
                    attempt.bound = bindService(intent, attempt.connection, 0);
                    if (!attempt.bound) { attempt.bindError = "실행 중인 차량 서비스에 연결되지 않았습니다."; attempt.connected.countDown(); }
                } catch (Exception e) { attempt.bindError = e.toString(); attempt.connected.countDown(); }
            }
        });
        if (!attempt.connected.await(8, TimeUnit.SECONDS)) throw new Exception("차량 서비스 연결 시간이 초과됐습니다.");
        checkpoint(attempt);
        if (attempt.binder == null) throw new Exception(attempt.bindError == null ? "차량 서비스가 없습니다." : attempt.bindError);
        return attempt.binder;
    }

    private void execute(final Attempt attempt, String heading) {
        StringBuilder out = new StringBuilder(heading);
        MirrorTrialBridge bridge = null; MirrorTrialBridge.Observer observer = null;
        boolean registered = false, permitted = false, returned = false;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(Protocol.SERVICE_PACKAGE, 0);
            Map<String, String> fields = new LinkedHashMap<String, String>();
            fields.put("serviceVersion", info.versionName); fields.put("serviceCode", String.valueOf(info.versionCode));
            fields.put("serviceSha256", sha256(info.applicationInfo.sourceDir));
            fields.put("api", String.valueOf(Build.VERSION.SDK_INT)); fields.put("hardware", Build.HARDWARE);
            if (!Protocol.SOURCE_SHA256.equals(fields.get("serviceSha256")) || info.versionCode != 26012914
                    || !"260129-1424".equals(info.versionName)) throw new Exception("기준 서비스 APK와 달라 연결하지 않았습니다.");
            checkpoint(attempt);
            BinderReader platform = new BinderReader(bind(attempt), Protocol.DROID);
            fields.putAll(platform.platformInfo());
            IBinder canBinder = platform.getCanBox(); BinderReader box = new BinderReader(canBinder, Protocol.CANBOX);
            fields.put("canBoxType", box.name(6)); fields.put("decoder", box.name(10));
            BinderReader.Packet version = box.lastPacket(0x7F);
            fields.put("boxVersion", version == null ? "" : Protocol.ascii(version.data));
            fields.put("envVersion", shared("Env.CANBoxVersion", 1));
            fields.put("scriptVersion", shared("script.version", 0));
            byte[] off = box.encodeOnly(0), on = box.encodeOnly(1);
            boolean connected = box.flag(5), suspended = box.flag(14);
            BinderReader.Packet before = box.lastPacket(0x38);
            TrialPolicy.Snapshot snapshot = new TrialPolicy.Snapshot(fields, connected, suspended,
                before == null ? null : before.data, off, on);
            for (Map.Entry<String, String> e : fields.entrySet()) out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            out.append("CANBox 연결: ").append(connected).append(" / 일시중지: ").append(suspended)
                .append("\n변경 전 0x38 캐시: ").append(Protocol.hex(snapshot.setting)).append('\n');
            String rejection = TrialPolicy.rejection(snapshot);
            if (rejection != null) throw new Exception(rejection);
            checkpoint(attempt); permitted = true;
            out.append("실차 기준 및 명령 계산: 일치\n현재 후보값: ")
                .append(Protocol.mirrorCandidate(0x38, snapshot.setting).intValue() == 1 ? "ON" : "OFF").append('\n');
            if (attempt.value >= 0) {
                bridge = new MirrorTrialBridge(canBinder); observer = new MirrorTrialBridge.Observer();
                try { registered = true; bridge.observe(observer, true); }
                catch (Exception e) { out.append("콜백 관찰 미등록: ").append(e.toString()).append("\n캐시 변화만 확인합니다.\n"); }
                checkpoint(attempt);
                // Re-read decoder and connection immediately before creating a one-use permit.
                if (!box.flag(5) || box.flag(14) || !"raise-psa".equals(box.name(10)) || !"raise".equals(box.name(6)))
                    throw new Exception("송신 직전에 CANBox 조건이 변경됐습니다.");
                attempt.permit = TrialPolicy.authorize(snapshot, attempt.value, SystemClock.elapsedRealtime());
                if (attempt.cancelled) attempt.permit.cancel();
                observer.markStart();
                out.append("요청 형식: ").append(Protocol.hex(attempt.value == 1 ? on : off)).append('\n');
                // Persist the baseline before the sole setting-write attempt. Never auto-restore.
                if (!getSharedPreferences("mirror_trial", 0).edit().putString("last_report", out.toString() + "설정 호출 직전 기록\n").commit())
                    throw new Exception("변경 전 기록을 저장하지 못해 설정을 전송하지 않았습니다.");
                bridge.sendMirrorOnce(attempt.permit); returned = true;
                out.append("설정 write 호출: 예외 없이 반환 (차량 적용 확인은 아님)\n");
                preview(attempt, "설정 요청 후 8초 관찰 중", out.toString());
                long started = SystemClock.elapsedRealtime(); byte[] lastCache = snapshot.setting;
                for (int sample = 1; sample <= 4; sample++) {
                    Thread.sleep(2000);
                    BinderReader.Packet packet = box.lastPacket(0x38);
                    lastCache = packet == null ? null : packet.data;
                    out.append("+").append(SystemClock.elapsedRealtime() - started).append(" ms 캐시: ").append(Protocol.hex(lastCache)).append('\n');
                }
                List<String> events = observer.events();
                out.append("\n호출 직전부터 관찰한 앱 수신 콜백:\n");
                if (events.isEmpty()) out.append("관찰된 알림 없음\n");
                for (String event : events) out.append(event).append('\n');
                Integer after = Protocol.mirrorCandidate(0x38, lastCache);
                boolean changed = lastCache != null && !Arrays.equals(snapshot.setting, lastCache);
                out.append("\n수신 캐시 변화: ").append(changed).append("\n후보값: ")
                    .append(after == null ? "판정 불가" : after.intValue() == 1 ? "ON" : "OFF").append('\n');
                if (after != null && after.intValue() == attempt.value)
                    out.append("후보 비트가 요청값과 일치합니다. 실제 거울 동작을 별도로 확인하세요.\n");
                else out.append("요청값 반영을 확인하지 못했습니다. 자동 재전송하지 않습니다.\n");
                out.append("캐시는 패킷 수신 시각이 없으며 콜백도 차량 적용 응답(ACK)으로 단정하지 않습니다.\n");
            } else out.append("차량 연결 확인 완료. 설정 명령은 전송하지 않았습니다.\n");
        } catch (Exception e) {
            permitted = false;
            out.append("\n중단: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        } finally {
            if (observer != null) observer.close();
            if (registered && bridge != null) {
                try { bridge.observe(observer, false); }
                catch (Exception e) { out.append("관찰 해제 응답 미확인: ").append(e.toString()).append('\n'); }
            }
            final boolean attempted = attempt.permit != null && attempt.permit.claimed();
            out.append("\n설정 호출 시도: ").append(attempted ? "1회" : "0회")
                .append("\n설정 호출 정상 반환: ").append(returned)
                .append("\n실제 미러 동작: 사용자 확인 필요\n");
            final String body = out.toString(); final boolean canContinue = permitted && !attempt.cancelled;
            getSharedPreferences("mirror_trial", 0).edit().putString("last_report", body).commit();
            ui.post(new Runnable() {
                @Override public void run() {
                    if (attempt.bound && attempt.connection != null) {
                        try { unbindService(attempt.connection); } catch (Exception ignored) { }
                        attempt.bound = false;
                    }
                    if (active != attempt) return;
                    active = null; ready = canContinue;
                    if (destroyed) return;
                    report = body; detail.setText(body);
                    status.setText(attempted ? "시험 기록 완료" : canContinue ? "차량 조건 일치 · 설정 시험 가능" : "조건 확인 또는 응답 미완료");
                    refreshButtons();
                }
            });
        }
    }
    private void preview(final Attempt attempt, final String title, final String body) {
        ui.post(new Runnable() { @Override public void run() {
            if (active == attempt && !destroyed) { report = body; status.setText(title); detail.setText(body); }
        } });
    }
    private String shared(String key, int type) {
        try {
            Bundle args = new Bundle(); args.putInt("value_type", type);
            Bundle result = getContentResolver().call(Uri.parse("content://com.reglink.services.provider.sharedvar"), "get", key, args);
            if (result != null && result.get("value") != null) return String.valueOf(result.get("value"));
        } catch (Exception ignored) { }
        return "";
    }
    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); FileInputStream stream = new FileInputStream(path);
        try { byte[] buffer = new byte[65536]; int count; while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count); }
        finally { stream.close(); }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.US, "%02x", b & 255)); return out.toString();
    }
    @Override public void onResume() { super.onResume(); foreground = true; refreshButtons(); }
    @Override public void onPause() {
        foreground = false;
        Attempt attempt = active;
        if (attempt != null) attempt.cancelBeforeSubmission();
        super.onPause();
    }
    @Override public void onDestroy() {
        destroyed = true; foreground = false;
        Attempt attempt = active;
        if (attempt != null) attempt.cancelBeforeSubmission();
        super.onDestroy();
    }
}
