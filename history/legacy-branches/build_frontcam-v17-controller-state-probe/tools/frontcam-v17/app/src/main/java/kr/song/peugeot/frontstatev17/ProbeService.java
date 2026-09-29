package kr.song.peugeot.frontstatev17;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProbeService extends Service {
    static final String ACTION_OBSERVE = "kr.song.peugeot.frontstatev17.OBSERVE";
    static final String ACTION_FRONT_OPEN = "kr.song.peugeot.frontstatev17.FRONT_OPEN";
    static final String ACTION_PANORAMIC = "kr.song.peugeot.frontstatev17.PANORAMIC";
    static final String ACTION_STOP = "kr.song.peugeot.frontstatev17.STOP";

    static final String PREFS = "v17_log";
    static final String KEY_LOG = "log";

    private static final String FRONT_OPEN_KEY = "camera_controller_front_open";
    private static final String REVERSE_OPEN_KEY = "camera_controller_reverse_open";
    private static final String PANORAMIC_KEY = "camera.car.panoramic";
    private static final String SIGNAL_KEY = "camera.signal";

    private static final int MODE_OBSERVE = 1;
    private static final int MODE_FRONT_OPEN = 2;
    private static final int MODE_PANORAMIC = 3;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServiceConnection connection;
    private boolean bound;
    private int mode;

    private IBinder carBinder;
    private IBinder mediaBinder;
    private IBinder sharedBinder;
    private boolean frontOriginal;
    private boolean panOriginal;
    private boolean frontOriginalKnown;
    private boolean panOriginalKnown;
    private boolean cameraLaunched;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            append("사용자 중지 요청");
            safeCleanup();
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_OBSERVE.equals(action)) startTest(MODE_OBSERVE, "v17 R→D 상태 추적만");
        else if (ACTION_FRONT_OPEN.equals(action)) startTest(MODE_FRONT_OPEN, "v17 frontOpen + FrontCamera(source3) 시험");
        else if (ACTION_PANORAMIC.equals(action)) startTest(MODE_PANORAMIC, "v17 camera.car.panoramic + CarCamera(source2) 시험");

        return START_NOT_STICKY;
    }

    private void startTest(int testMode, String title) {
        if (!running.compareAndSet(false, true)) {
            append("이미 시험 진행 중");
            return;
        }
        mode = testMode;
        clearAndAppend(title);
        append("트리거: ICarService.isReversing true→false");
        append("getGear 값은 참고만 사용(실차 v16에서 R 중에도 gear_n 확인)");
        append("CAN/CANBox/GPIO/MCU 직접 쓰기=0");
        if (mode == MODE_FRONT_OPEN) {
            append("변경: SharedVariable " + FRONT_OPEN_KEY + "=true → 7초 후 원복");
        } else if (mode == MODE_PANORAMIC) {
            append("변경: SharedVariable " + PANORAMIC_KEY + "=true → CarCamera(id=panoramic, source=2) → 7초 후 원복");
        } else {
            append("읽기 전용 시험");
        }
        bindRegLink();
    }

    private void bindRegLink() {
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder droid) {
                append("DroidCarService 연결: " + name.flattenToShortString());
                try {
                    carBinder = RegLinkClient.getService(droid, "Car", RegLinkClient.CAR);
                    mediaBinder = RegLinkClient.getService(droid, "Media", RegLinkClient.MEDIA);
                    sharedBinder = RegLinkClient.getService(droid, "SharedVariable", RegLinkClient.SHARED);
                    append("Car/Media/SharedVariable binder 연결 완료");
                    append(snapshot("초기"));
                    new Thread(new Runnable() {
                        @Override public void run() { monitor(); }
                    }, "FrontState-v17").start();
                } catch (Throwable t) {
                    append("binder 연결 오류: " + t);
                    safeCleanup();
                    stopMonitor();
                    stopSelf();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                append("DroidCarService 연결 끊김");
                safeCleanup();
                stopMonitor();
                stopSelf();
            }
        };

        try {
            Intent svc = new Intent("com.reglink.action.DroidCarService");
            svc.setComponent(new ComponentName("com.reglink.services", "com.reglink.services.DroidCarService"));
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

    private void monitor() {
        final long timeout = SystemClock.elapsedRealtime() + 50000L;
        boolean sawReverse = false;
        Boolean previousReverse = null;
        try {
            while (running.get() && SystemClock.elapsedRealtime() < timeout) {
                boolean reverse = RegLinkClient.reversing(carBinder);
                if (previousReverse == null || previousReverse.booleanValue() != reverse) {
                    append(snapshot("reversing=" + reverse));
                    previousReverse = Boolean.valueOf(reverse);
                }

                if (reverse) sawReverse = true;

                if (sawReverse && !reverse) {
                    append("R 해제 감지");
                    append(snapshot("R해제 직후"));
                    SystemClock.sleep(350L);

                    if (mode == MODE_FRONT_OPEN) runFrontOpen();
                    else if (mode == MODE_PANORAMIC) runPanoramic();
                    else observeFor(8000L, "OBSERVE");
                    break;
                }
                SystemClock.sleep(100L);
            }

            if (!sawReverse) append("50초 내 R 상태 감지 실패");
            else if (previousReverse != null && previousReverse.booleanValue())
                append("50초 내 R 해제 감지 실패");
        } catch (Throwable t) {
            append("모니터 오류: " + t);
        } finally {
            safeCleanup();
            bringTesterFront();
            stopMonitor();
            stopSelf();
        }
    }

    private void runFrontOpen() throws Exception {
        frontOriginal = safeShared(FRONT_OPEN_KEY);
        frontOriginalKnown = true;
        append("frontOpen 원래값=" + frontOriginal);
        RegLinkClient.sharedSetBoolean(sharedBinder, FRONT_OPEN_KEY, true);
        SystemClock.sleep(150L);
        append("frontOpen 설정 후=" + safeShared(FRONT_OPEN_KEY));

        Intent i = new Intent("com.reglink.action.FrontCamera");
        i.setComponent(new ComponentName(
                "com.reglink.apps.camera",
                "com.reglink.apps.camera.FrontCameraActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
        cameraLaunched = true;
        append("FrontCameraActivity 실행 요청 / 정적분석상 source=3");
        observeFor(7000L, "FRONT3");

        RegLinkClient.sharedSetBoolean(sharedBinder, FRONT_OPEN_KEY, frontOriginal);
        append("frontOpen 원복=" + safeShared(FRONT_OPEN_KEY));
    }

    private void runPanoramic() throws Exception {
        panOriginal = safeShared(PANORAMIC_KEY);
        panOriginalKnown = true;
        append("camera.car.panoramic 원래값=" + panOriginal);
        RegLinkClient.sharedSetBoolean(sharedBinder, PANORAMIC_KEY, true);
        SystemClock.sleep(150L);
        append("camera.car.panoramic 설정 후=" + safeShared(PANORAMIC_KEY));

        Intent i = new Intent("com.reglink.action.CarCamera");
        i.setComponent(new ComponentName(
                "com.reglink.apps.camera",
                "com.reglink.apps.camera.CarCameraActivity"));
        i.putExtra("id", "panoramic");
        i.putExtra("videoSource", 2);
        i.putExtra("mirror", false);
        i.putExtra("fullscreen", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
        cameraLaunched = true;
        append("CarCameraActivity 실행 / id=panoramic / videoSource=2");
        observeFor(7000L, "CAR2");

        RegLinkClient.sharedSetBoolean(sharedBinder, PANORAMIC_KEY, panOriginal);
        append("camera.car.panoramic 원복=" + safeShared(PANORAMIC_KEY));
    }

    private void observeFor(long duration, String tag) {
        long end = SystemClock.elapsedRealtime() + duration;
        String previous = null;
        while (running.get() && SystemClock.elapsedRealtime() < end) {
            String now = compactState();
            if (previous == null || !previous.equals(now)) {
                append(tag + ": " + now);
                previous = now;
            }
            SystemClock.sleep(150L);
        }
        append(tag + " 관찰 종료");
    }

    private String compactState() {
        try {
            return "rev=" + RegLinkClient.reversing(carBinder)
                    + " gear=" + RegLinkClient.gear(carBinder)
                    + " panoramicRequired=" + RegLinkClient.cameraRequired(carBinder, "panoramic")
                    + " media=" + RegLinkClient.mediaSource(mediaBinder)
                    + " signal=" + RegLinkClient.sharedBoolean(sharedBinder, SIGNAL_KEY, false)
                    + " reverseOpen=" + RegLinkClient.sharedBoolean(sharedBinder, REVERSE_OPEN_KEY, false)
                    + " frontOpen=" + RegLinkClient.sharedBoolean(sharedBinder, FRONT_OPEN_KEY, false)
                    + " carPan=" + RegLinkClient.sharedBoolean(sharedBinder, PANORAMIC_KEY, false);
        } catch (Throwable t) {
            return "(state error " + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
        }
    }

    private String snapshot(String label) { return label + " / " + compactState(); }

    private boolean safeShared(String key) {
        try { return sharedBinder != null && RegLinkClient.sharedBoolean(sharedBinder, key, false); }
        catch (Throwable t) { return false; }
    }

    private void safeCleanup() {
        // MODE_OBSERVE must remain read-only.
        if (mode == MODE_OBSERVE) return;

        if (sharedBinder != null) {
            if (frontOriginalKnown) {
                try { RegLinkClient.sharedSetBoolean(sharedBinder, FRONT_OPEN_KEY, frontOriginal); }
                catch (Throwable ignored) {}
            }
            if (panOriginalKnown) {
                try { RegLinkClient.sharedSetBoolean(sharedBinder, PANORAMIC_KEY, panOriginal); }
                catch (Throwable ignored) {}
            }
        }
        if (cameraLaunched && mediaBinder != null) {
            try { RegLinkClient.mediaSwitch(mediaBinder, 1); }
            catch (Throwable ignored) {}
        }
    }

    private void bringTesterFront() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Throwable ignored) {}
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
        mode = 0;
        frontOriginalKnown = false;
        panOriginalKnown = false;
        cameraLaunched = false;
        carBinder = null;
        mediaBinder = null;
        sharedBinder = null;
        if (bound && connection != null) {
            try { unbindService(connection); } catch (Throwable ignored) {}
        }
        bound = false;
        connection = null;
    }

    @Override public void onDestroy() {
        if (running.get()) safeCleanup();
        stopMonitor();
        super.onDestroy();
    }
}
