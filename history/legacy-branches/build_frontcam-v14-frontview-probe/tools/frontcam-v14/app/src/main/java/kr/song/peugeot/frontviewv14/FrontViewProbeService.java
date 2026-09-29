package kr.song.peugeot.frontviewv14;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrontViewProbeService extends Service {
    static final String ACTION_FRONT_CAMERA = "kr.song.peugeot.frontviewv14.FRONT_CAMERA";
    static final String ACTION_FRONT_VIEW = "kr.song.peugeot.frontviewv14.FRONT_VIEW";
    static final String ACTION_REAR_CAMERA = "kr.song.peugeot.frontviewv14.REAR_CAMERA";
    static final String ACTION_ARM_FRONT_VIEW = "kr.song.peugeot.frontviewv14.ARM_FRONT_VIEW";
    static final String ACTION_ARM_FRONT_CAMERA = "kr.song.peugeot.frontviewv14.ARM_FRONT_CAMERA";
    static final String ACTION_STOP = "kr.song.peugeot.frontviewv14.STOP";

    static final String PREFS = "frontview_probe_log";
    static final String KEY_LOG = "log";

    private static final String CAMERA_PACKAGE = "com.reglink.apps.camera";
    private static final String FRONT_CAMERA = "com.reglink.apps.camera.FrontCameraActivity";
    private static final String FRONT_VIEW = "com.reglink.apps.camera.FrontViewActivity";
    private static final String REAR_CAMERA = "com.reglink.apps.camera.RearCameraActivity";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServiceConnection connection;
    private boolean bound;
    private String pendingClass;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            append("사용자 중지");
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_FRONT_CAMERA.equals(action)) {
            clearAndAppend("FrontCameraActivity 직접 1회");
            launchOnce(FRONT_CAMERA);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_FRONT_VIEW.equals(action)) {
            clearAndAppend("FrontViewActivity 직접 1회");
            launchOnce(FRONT_VIEW);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_REAR_CAMERA.equals(action)) {
            clearAndAppend("RearCameraActivity 직접 1회 비교");
            launchOnce(REAR_CAMERA);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_ARM_FRONT_VIEW.equals(action)) {
            arm(FRONT_VIEW, "R→D 후 FrontViewActivity +300ms");
            return START_NOT_STICKY;
        }

        if (ACTION_ARM_FRONT_CAMERA.equals(action)) {
            arm(FRONT_CAMERA, "R→D 후 FrontCameraActivity +300ms");
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void arm(String className, String label) {
        if (!running.compareAndSet(false, true)) {
            append("이미 실차 시험 진행 중");
            return;
        }
        clearAndAppend("v14 " + label + " 시험 시작");
        append("gear_d 미사용 / isReversing true→false만 감지");
        append("CAN write/rawWrite=0 / SharedVar set=0 / FrontGearStart 변경=0");
        pendingClass = className;
        bindRegLink();
    }

    private void launchOnce(String className) {
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo ai = pm.getActivityInfo(
                    new ComponentName(CAMERA_PACKAGE, className), 0);
            append("target=" + ai.packageName + "/" + ai.name
                    + " exported=" + ai.exported
                    + " permission=" + (ai.permission == null ? "(none)" : ai.permission));
            Intent i = new Intent();
            i.setComponent(new ComponentName(CAMERA_PACKAGE, className));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            append("startActivity return=OK");
        } catch (Throwable t) {
            append("실행 실패: " + t);
        }
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
                    }, "FrontViewProbe-v14").start();
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
                    "com.reglink.services", "com.reglink.services.DroidCarService"));
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
        final long end = SystemClock.elapsedRealtime() + 45000L;
        boolean sawReverse = false;
        Boolean previous = null;

        try {
            while (running.get() && SystemClock.elapsedRealtime() < end) {
                boolean reverse = RegLinkClient.reversing(car);

                if (previous == null || previous.booleanValue() != reverse) {
                    append("상태 변화: reversing=" + reverse + " gear=" + safeGear(car));
                    previous = Boolean.valueOf(reverse);
                }

                if (reverse) sawReverse = true;

                if (sawReverse && !reverse) {
                    append("R 해제 감지");
                    SystemClock.sleep(300L);
                    append("R 해제 +300ms → "
                            + (pendingClass == null ? "(null)" : pendingClass) + " 실행");
                    launchOnce(pendingClass);
                    break;
                }

                SystemClock.sleep(100L);
            }

            if (!sawReverse) append("45초 내 R 상태 감지 실패");
            else if (running.get() && previous != null && previous.booleanValue())
                append("45초 내 R 해제 감지 실패");
        } catch (Throwable t) {
            append("모니터 오류: " + t);
        } finally {
            stopMonitor();
            stopSelf();
        }
    }

    private String safeGear(IBinder car) {
        try { return String.valueOf(RegLinkClient.gear(car)); }
        catch (Throwable t) { return "(gear err " + t.getClass().getSimpleName() + ")"; }
    }

    private synchronized void clearAndAppend(String line) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LOG, "").commit();
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
        pendingClass = null;
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
