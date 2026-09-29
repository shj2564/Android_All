package kr.song.peugeot.mirrorcheck;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler();
    private TextView status, detail;
    private LinearLayout sampleControls;
    private String report = "아직 조회하지 않았습니다.";
    private String verifiedHeader = "";
    private ServiceConnection connection;
    private boolean bound, busy, sampleMode;
    private int generation;
    private Thread worker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(18, 29, 44));
        scroll.addView(root);
        root.addView(text("5008 미러 진단", 27, true));
        TextView intro = text("진단·설정 시험 0.3  |  CMZX / Reglink", 15, false);
        intro.setPadding(0, dp(5), 0, dp(12)); root.addView(intro);
        root.addView(text("조회·샘플은 차량 설정을 변경하지 않습니다.\n별도 ‘실차 미러 설정 시험’ 화면에서 켜기·끄기를 실행할 수 있습니다.", 17, false));
        LinearLayout actions = row(); root.addView(actions);
        addButton(actions, "샘플 보기", new View.OnClickListener() {
            @Override public void onClick(View v) { showSample(false); }
        });
        addButton(actions, "차량 정보 읽기", new View.OnClickListener() {
            @Override public void onClick(View v) { startLive(); }
        });
        LinearLayout trial = row(); root.addView(trial);
        addButton(trial, "실차 미러 설정 시험", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MirrorTrialActivity.class));
            }
        });
        sampleControls = row(); root.addView(sampleControls);
        addButton(sampleControls, "샘플 OFF", new View.OnClickListener() {
            @Override public void onClick(View v) { showSample(false); }
        });
        addButton(sampleControls, "샘플 ON", new View.OnClickListener() {
            @Override public void onClick(View v) { showSample(true); }
        });
        sampleControls.setVisibility(View.GONE);
        status = text("차량 정보 대기", 21, true);
        status.setTextColor(Color.rgb(132, 212, 240));
        status.setPadding(0, dp(12), 0, dp(10)); root.addView(status);
        detail = text("먼저 ‘샘플 보기’ 또는 ‘차량 정보 읽기’를 선택하세요.\n차량에서는 정차 후 조회해주세요.", 17, false);
        detail.setTextIsSelectable(true); detail.setLineSpacing(dp(3), 1.0f); root.addView(detail);
        LinearLayout sharing = row(); root.addView(sharing);
        addButton(sharing, "결과 복사", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("5008 미러 진단", report));
                Toast.makeText(MainActivity.this, "결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        addButton(sharing, "결과 공유", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT, report);
                try { startActivity(Intent.createChooser(intent, "진단 결과 공유")); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "공유 앱이 없습니다. 결과 복사를 사용하세요.", Toast.LENGTH_LONG).show(); }
            }
        });
        setContentView(scroll);
    }

    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + 0.5f); }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(235, 241, 248));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL); layout.setPadding(0, dp(10), 0, 0);
        return layout;
    }
    private void addButton(LinearLayout row, String label, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(label); button.setTextSize(16);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1);
        lp.setMargins(0, 0, dp(6), 0); row.addView(button, lp);
    }
    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
    }

    private void showSample(boolean enabled) {
        generation++; busy = false; sampleMode = true; disconnect();
        sampleControls.setVisibility(View.VISIBLE);
        byte[] data = new byte[7]; data[4] = (byte) (enabled ? 8 : 0);
        report = "[SAMPLE_NOT_VEHICLE_DATA]\n5008 미러 진단 0.3 / 샘플\n"
            + "아래 데이터는 앱이 만든 가상 값입니다.\n실제 차량의 상태가 아닙니다.\n\n"
            + "후진 미러 상태 예시: " + (enabled ? "ON" : "OFF")
            + "\n0x38 가상 데이터: " + Protocol.hex(data)
            + "\nDUDU 코드 기준 bit 후보: " + Protocol.mirrorCandidate(0x38, data)
            + "\n\n" + Protocol.offlineComparison()
            + "\n\n분석 결과: 서버 묶음 26082601 / 26091003의\nRaise PSA 파일은 동일하며 미러 토글이 없습니다."
            + "\n현재 샘플 화면에서는 차량에 명령을 전송하지 않습니다.";
        status.setText(enabled ? "샘플 · 가상 ON" : "샘플 · 가상 OFF");
        detail.setText(report);
    }

    private String deviceHeader() {
        return "[DEVICE_READ_ATTEMPT]\n5008 미러 진단 0.3 / 차량 정보 읽기\n조회 시각: " + timestamp()
            + "\nAndroid: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT
            + "\n기기: " + Build.MANUFACTURER + " " + Build.MODEL
            + "\n하드웨어: " + Build.HARDWARE + "\n빌드: " + Build.DISPLAY + "\n";
    }

    private void startLive() {
        if (busy || (worker != null && worker.isAlive())) {
            Toast.makeText(this, "이전 읽기를 처리하고 있습니다.", Toast.LENGTH_SHORT).show(); return;
        }
        final int token = ++generation; sampleMode = false; busy = true;
        disconnect(); sampleControls.setVisibility(View.GONE);
        report = deviceHeader(); verifiedHeader = report;
        status.setText("기준 앱 확인 중"); detail.setText(report);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (token == generation && busy) {
                    generation++; busy = false; disconnect();
                    report += "\n조회 시간이 초과되었습니다.\n차량 서비스의 응답이 아직 확인되지 않았습니다.";
                    status.setText("조회 응답 없음"); detail.setText(report);
                }
            }
        }, 15000);
        worker = new Thread(new Runnable() {
            @Override public void run() {
                StringBuilder header = new StringBuilder(verifiedHeader);
                try {
                    PackageInfo info = getPackageManager().getPackageInfo(Protocol.SERVICE_PACKAGE, 0);
                    header.append("서비스 앱: ").append(info.versionName).append(" / ").append(info.versionCode).append('\n');
                    if (info.versionCode != 26012914 || !"260129-1424".equals(info.versionName))
                        throw new Exception("분석한 서비스 버전과 달라 연결하지 않았습니다.");
                    String hash = sha256(info.applicationInfo.sourceDir);
                    header.append("서비스 APK SHA-256: ").append(hash).append('\n');
                    if (!Protocol.SOURCE_SHA256.equals(hash))
                        throw new Exception("분석한 APK와 파일이 달라 연결하지 않았습니다.");
                    header.append("기준 APK 일치: 확인\n");
                    final String checked = header.toString();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (token != generation) return;
                            verifiedHeader = checked; report = checked; detail.setText(report); startBinding(token);
                        }
                    });
                } catch (Exception e) {
                    String reason = e instanceof android.content.pm.PackageManager.NameNotFoundException
                        ? "이 기기에는 ReglinkService가 없습니다. 차량 밖에서는 샘플 보기를 사용할 수 있습니다."
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                    finish(token, "차량 연결 미확인", header.toString() + "\n" + reason);
                }
            }
        }, "MirrorCheck-Verify");
        worker.setDaemon(true); worker.start();
    }

    private void startBinding(final int token) {
        status.setText("기존 서비스 연결 중");
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, final IBinder service) {
                if (token != generation) return;
                status.setText("수신 기록 읽는 중");
                worker = new Thread(new Runnable() {
                    @Override public void run() { readLive(token, service); }
                }, "MirrorCheck-Read");
                worker.setDaemon(true); worker.start();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (token == generation && busy)
                    finish(token, "서비스 연결 끊김", report + "\n조회 중 서비스 연결이 끊겼습니다.");
            }
        };
        try {
            Intent intent = new Intent("com.reglink.action.DroidCarService");
            intent.setComponent(new ComponentName(Protocol.SERVICE_PACKAGE, "com.reglink.services.DroidCarService"));
            // Flags=0: bind only; do not start or restart the vehicle service.
            bound = bindService(intent, connection, 0);
            if (!bound) finish(token, "서비스 연결 미확인", report + "\n실행 중인 차량 서비스에 연결되지 않았습니다.");
        } catch (Exception e) { finish(token, "서비스 연결 미확인", report + "\n" + e.toString()); }
    }

    private void readLive(int token, IBinder service) {
        StringBuilder out = new StringBuilder(verifiedHeader);
        try {
            BinderReader platform = new BinderReader(service, Protocol.DROID);
            try {
                for (Map.Entry<String, String> e : platform.platformInfo().entrySet())
                    out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            } catch (Exception e) { out.append("차종 정보 조회: ").append(e.toString()).append('\n'); }
            BinderReader box = new BinderReader(platform.getCanBox(), Protocol.CANBOX);
            boolean connected = box.flag(5), suspended = box.flag(14);
            String type = box.name(6), decoder = box.name(10);
            out.append("\nCANBox 연결: ").append(connected).append("\nCANBox 일시중지: ").append(suspended)
                .append("\nCANBox 종류: ").append(type).append("\n디코더: ").append(decoder).append('\n');
            BinderReader.Packet setting = box.lastPacket(0x38), versionPacket = box.lastPacket(0x7F);
            String boxVersion = versionPacket == null ? "" : Protocol.ascii(versionPacket.data);
            out.append("0x7F 수신 버전: ").append(boxVersion).append('\n');
            out.append("0x38 수신 데이터: ").append(Protocol.hex(setting == null ? null : setting.data)).append('\n');
            String envVersion = readShared("Env.CANBoxVersion", 1);
            out.append("서비스의 CANBox 버전: ").append(envVersion).append('\n');
            out.append("메뉴 묶음 버전: ").append(readShared("script.version", 0)).append('\n');
            out.append("사용 중인 차종 파일: ").append(readShared("script.model_file", 1)).append('\n');
            out.append("차종 모듈 버전: ").append(readShared("script.model_version", 0)).append('\n');
            boolean knownDecoder = "raise".equals(type) && "raise-psa".equals(decoder);
            boolean matchingVersion = Protocol.REFERENCE_BOX.equals(boxVersion)
                || (boxVersion.length() == 0 && Protocol.REFERENCE_BOX.equals(envVersion));
            boolean conflict = boxVersion.length() > 0 && !"확인 안 됨".equals(envVersion)
                && envVersion.length() > 0 && !boxVersion.equals(envVersion);
            if (knownDecoder && matchingVersion && !conflict && connected && !suspended && setting != null) {
                Integer candidate = Protocol.mirrorCandidate(setting.type, setting.data);
                out.append("\n후진 미러 상태 후보: ").append(candidate == null ? "데이터 길이 부족" : candidate.intValue() == 1 ? "ON 추정" : "OFF 추정");
                out.append("\nDUDU 코드 기준 해석이며 실차 의미는 아직 검증하지 않았습니다.\n");
            } else out.append("\n후진 미러 상태: 판정 보류 (기준 조건 또는 수신 기록 부족)\n");
            if (knownDecoder) {
                for (int value = 0; value <= 1; value++) {
                    byte[] returned = box.encodeOnly(value);
                    byte[] expected = Protocol.raisePsaEncode(0x80, new byte[] {0x1E, (byte) value});
                    out.append("\n기존 서비스의 ").append(value == 1 ? "ON" : "OFF").append(" 형식 계산: ")
                        .append(Protocol.hex(returned)).append("\n대조 결과: ").append(Arrays.equals(returned, expected) ? "일치" : "불일치");
                }
            }
            out.append("\n\n읽기 완료. 차량 설정 명령은 전송하지 않았습니다.")
                .append("\n수신값은 서비스가 보관한 최근 기록이며 패킷별 수신 시각은 제공되지 않습니다.");
            finish(token, "차량 정보 읽기 완료", out.toString());
        } catch (Exception e) {
            out.append("\n조회 중단: ").append(e.toString());
            finish(token, "일부 정보 미확인", out.toString());
        }
    }

    private String readShared(String key, int valueType) {
        try {
            Bundle args = new Bundle(); args.putInt("value_type", valueType);
            Bundle value = getContentResolver().call(Uri.parse("content://com.reglink.services.provider.sharedvar"), "get", key, args);
            if (value != null && value.containsKey("value") && value.get("value") != null)
                return String.valueOf(value.get("value"));
        } catch (Exception ignored) { }
        return "확인 안 됨";
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(path);
        try { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) digest.update(buf, 0, n); }
        finally { in.close(); }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }
    private void finish(final int token, final String title, final String body) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (token != generation || sampleMode) return;
                busy = false; report = body; status.setText(title); detail.setText(report); disconnect();
                generation++; // invalidate the timeout and any late callback for this request
            }
        });
    }
    private void disconnect() {
        if (bound && connection != null) {
            try { unbindService(connection); } catch (Exception ignored) { }
        }
        bound = false; connection = null;
    }
    @Override public void onDestroy() {
        generation++; busy = false; disconnect(); ui.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
