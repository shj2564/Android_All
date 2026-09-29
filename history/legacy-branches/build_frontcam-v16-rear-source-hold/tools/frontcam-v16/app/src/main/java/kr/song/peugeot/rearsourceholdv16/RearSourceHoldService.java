package kr.song.peugeot.rearsourceholdv16;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RearSourceHoldService extends Service {
    static final String ACTION_ARM_0 = "kr.song.peugeot.rearsourceholdv16.ARM_0";
    static final String ACTION_ARM_100 = "kr.song.peugeot.rearsourceholdv16.ARM_100";
    static final String ACTION_ARM_300 = "kr.song.peugeot.rearsourceholdv16.ARM_300";
    static final String ACTION_ARM_500 = "kr.song.peugeot.rearsourceholdv16.ARM_500";
    static final String ACTION_ARM_100_ONESHOT = "kr.song.peugeot.rearsourceholdv16.ARM_100_ONESHOT";
    static final String ACTION_STOP = "kr.song.peugeot.rearsourceholdv16.STOP";

    static final String PREFS = "rear_source_hold_log";
    static final String KEY_LOG = "log";

    private static final String CAMERA_PACKAGE = "com.reglink.apps.camera";
    private static final String REAR_ACTIVITY = "com.reglink.apps.camera.RearCameraActivity";
    private static final String REAR_ACTION = "com.reglink.action.RearCamera";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServiceConnection connection;
    private boolean bound;

    private long postReverseDelayMs;
    private boolean repeatForSevenSeconds;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            append("사용자 중지");
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_ARM_0.equals(action)) {
            arm(0L, true, "R 해제 즉시 / Rear source 2 / 7초");
        } else if (ACTION_ARM_100.equals(action)) {
            arm(100L, true, "R 해제 +100ms / Rear source 2 / 7초");
        } else if (ACTION_ARM_300.equals(action)) {
            arm(300L, true, "R 해제 +300ms / Rear source 2 / 7초");
        } else if (ACTION_ARM_500.equals(action)) {
            arm(500L, true, "R 해제 +500ms / Rear source 2 / 7초");
        } else if (ACTION_ARM_100_ONESHOT.equals(action)) {
            arm(100L, false, "R 해제 +100ms / Rear source 2 / 1회");
        }

        return START_NOT_STICKY;
    }

    private void arm(long delayMs, boolean repeat, String label) {
        if (!running.compareAndSet(false, true)) {
            append("이미 시험 진행 중 — 먼저 중지하거나 기존 시험 종료를 기다리세요.");
            return;
        }

        postReverseDelayMs = delayMs;
        repeatForSevenSeconds = repeat;

        clearAndAppend("v16 실차시험 시작: " + label);
        append("트리거: isReversing true → false");
        append("getGear()는 로그에만 사용, gear_d에는 의존하지 않음");
        append("대상: " + CAMERA_PACKAGE + "/" + REAR_ACTIVITY);
        append("FrontCameraActivity/FrontViewActivity(source 3) 사용 안 함");
        append("CAN write/rawWrite=0 / SharedVar set=0 / FrontGearStart=0 / MCU 설정 변경=0");

        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(
                    new ComponentName(CAMERA_PACKAGE, REAR_ACTIVITY), 0);
            append("Rear target 확인: exported=" + ai.exported
                    + " permission=" + (ai.permission == null ? "(none)" : ai.permission));
        } catch (Throwable t) {
            append("Rear target 조회 실패: " + t);
            running.set(false);
            stopSelf();
            return;
        }

        bindRegLink();
    }

    private void bindRegLink() {
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                append("DroidCarService 연결: " + name.flattenToShortString());
                try {
                    final IBinder car = RegLinkClient.getCar(service);
                    append("Car binder 연결 / gear=" + safeGear(car)
                            + " / reversing=" + RegLinkClient.reversing(car));
                    new Thread(new Runnable() {
                        @Override public void run() { monitor(car); }
                    }, "RearSourceHold-v16").start();
                } catch (Throwable t) {
                    append("Car binder 오류: " + t);
                    stopMonitor();
                    stopSelf();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                append("DroidCarService 연결 끊김");
                stopMonitor();
                stopSelf();
            }
        };

        try {
            Intent svc = new Intent("com.reglink.action.DroidCarService");
            svc.setComponent(new ComponentName(
                    "com.reglink.services",
                    "com.reglink.services.DroidCarService"));
            bound = bindService(svc, connection, 0);
            if (!bound) {
                append("DroidCarService bindService=false");
                stopMonitor();
                stopSelf();
            }
        } catch (Throwable t) {
            append("서비스 연결 예외: " + t);
            stopMonitor();
            stopSelf();
        }
    }

    private void monitor(IBinder car) {
        final long deadline = SystemClock.elapsedRealtime() + 45000L;
        boolean sawReverse = false;
        Boolean previous = null;

        try {
            while (running.get() && SystemClock.elapsedRealtime() < deadline) {
                boolean reverse = RegLinkClient.reversing(car);

                if (previous == null || previous.booleanValue() != reverse) {
                    append("상태 변화: reversing=" + reverse + " gear=" + safeGear(car));
                    previous = Boolean.valueOf(reverse);
                }

                if (reverse) sawReverse = true;

                if (sawReverse && !reverse) {
                    append("R 해제 감지 / gear=" + safeGear(car));

                    if (postReverseDelayMs > 0L) {
                        append("대기 " + postReverseDelayMs + "ms");
                        SystemClock.sleep(postReverseDelayMs);
                    }

                    if (repeatForSevenSeconds) {
                        append("Rear source 2 재유지 시작 — 약 7초");
                        holdRearForSevenSeconds();
                        append("Rear source 2 재유지 종료");
                    } else {
                        append("Rear source 2 1회 실행");
                        launchRear(1);
                    }
                    break;
                }

                SystemClock.sleep(50L);
            }

            if (!sawReverse) {
                append("45초 내 R 상태를 감지하지 못함");
            } else if (running.get() && previous != null && previous.booleanValue()) {
                append("45초 내 R 해제를 감지하지 못함");
            }
        } catch (Throwable t) {
            append("모니터 오류: " + t);
        } finally {
            stopMonitor();
            stopSelf();
        }
    }

    private void holdRearForSevenSeconds() {
        final long until = SystemClock.elapsedRealtime() + 7000L;
        int count = 0;

        while (running.get() && SystemClock.elapsedRealtime() < until) {
            count++;
            if (!launchRear(count)) break;

            // RearCameraActivity가 R 해제 시 자체 종료하더라도
            // source 2가 살아 있는지 실차에서 관찰할 수 있도록 재요청한다.
            SystemClock.sleep(350L);
        }
    }

    private boolean launchRear(int count) {
        try {
            Intent launch = new Intent(REAR_ACTION);
            launch.setComponent(new ComponentName(CAMERA_PACKAGE, REAR_ACTIVITY));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);

            if (count <= 3 || count % 5 == 0) {
                append("RearCameraActivity 요청 #" + count + " return=OK");
            }
            return true;
        } catch (Throwable t) {
            append("RearCameraActivity 실행 실패 #" + count + ": " + t);
            return false;
        }
    }

    private String safeGear(IBinder car) {
        try { return String.valueOf(RegLinkClient.gear(car)); }
        catch (Throwable t) { return "(gear err " + t.getClass().getSimpleName() + ")"; }
    }

    private synchronized void clearAndAppend(String line) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(KEY_LOG, "").commit();
        append(line);
    }

    private synchronized void append(String line) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String old = p.getString(KEY_LOG, "");
        String now = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        p.edit().putString(KEY_LOG, old + now + "  " + line + "\n").commit();
    }

    private void stopMonitor() {
        running.set(false);
        if (bound && connection != null) {
            try { unbindService(connection); } catch (Throwable ignored) {}
        }
        bound = false;
        connection = null;
    }

    @Override public void onDestroy() {
        stopMonitor();
        super.onDestroy();
    }
}
