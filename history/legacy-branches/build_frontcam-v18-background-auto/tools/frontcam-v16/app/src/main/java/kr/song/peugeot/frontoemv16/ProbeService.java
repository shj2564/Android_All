package kr.song.peugeot.frontoemv16;

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
    static final String ACTION_OBSERVE = "kr.song.peugeot.frontoemv16.OBSERVE";
    static final String ACTION_SOURCE2 = "kr.song.peugeot.frontoemv16.SOURCE2";
    static final String ACTION_STOP = "kr.song.peugeot.frontoemv16.STOP";
    static final String PREFS = "v16_log";
    static final String KEY_LOG = "log";

    private static final int MODE_OBSERVE = 1;
    private static final int MODE_SOURCE2 = 2;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServiceConnection connection;
    private boolean bound;
    private int mode;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            append("사용자 중지");
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_OBSERVE.equals(action)) {
            startTest(MODE_OBSERVE, "v16 R→D 상태 기록 시험 시작");
        } else if (ACTION_SOURCE2.equals(action)) {
            startTest(MODE_SOURCE2, "v16 R→D OEM source2 7초 시험 시작");
        }

        return START_NOT_STICKY;
    }

    private void startTest(int testMode, String title) {
        if (!running.compareAndSet(false, true)) {
            append("이미 시험 진행 중");
            return;
        }
        mode = testMode;
        clearAndAppend(title);
        append("근거: Raise panoramic CarCamera 경로의 videoSource=2");
        append("CAN/CANBox/SharedVar/GPIO 직접 쓰기=0");
        if (mode == MODE_SOURCE2) {
            append("R 해제 후 FrontViewActivity를 열고 Media source만 2로 전환");
        } else {
            append("읽기 전용: media source 변경 없음");
        }
        bindRegLink();
    }

    private void bindRegLink() {
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder droid) {
                append("DroidCarService 연결: " + name.flattenToShortString());
                try {
                    final IBinder car = RegLinkClient.getService(droid, "Car", RegLinkClient.CAR);
                    final IBinder media = RegLinkClient.getService(droid, "Media", RegLinkClient.MEDIA);
                    final IBinder shared = RegLinkClient.getService(droid, "SharedVariable", RegLinkClient.SHARED);
                    append("Car/Media/SharedVariable binder 연결 완료");
                    append(snapshot(car, media, shared, "초기"));
                    new Thread(new Runnable() {
                        @Override public void run() { monitor(car, media, shared); }
                    }, "FrontOEM-v16").start();
                } catch (Throwable t) {
                    append("binder 연결 오류: " + t);
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

    private void monitor(IBinder car, IBinder media, IBinder shared) {
        final long timeout = SystemClock.elapsedRealtime() + 45000L;
        boolean sawReverse = false;
        Boolean previousReverse = null;
        String previousState = null;

        try {
            while (running.get() && SystemClock.elapsedRealtime() < timeout) {
                boolean reverse = RegLinkClient.reversing(car);
                if (previousReverse == null || previousReverse.booleanValue() != reverse) {
                    append(snapshot(car, media, shared, "reversing=" + reverse));
                    previousReverse = Boolean.valueOf(reverse);
                }

                if (reverse) sawReverse = true;

                if (sawReverse && !reverse) {
                    append("R 해제 감지");
                    if (mode == MODE_SOURCE2) {
                        runSource2Test(car, media, shared);
                    } else {
                        observeAfterExit(car, media, shared, 8000L);
                    }
                    break;
                }

                String state = compactState(car, media, shared);
                if (previousState == null || !previousState.equals(state)) {
                    append("상태: " + state);
                    previousState = state;
                }
                SystemClock.sleep(150L);
            }

            if (!sawReverse) append("45초 내 R 상태 감지 실패");
            else if (running.get() && previousReverse != null && previousReverse.booleanValue())
                append("45초 내 R 해제 감지 실패");
        } catch (Throwable t) {
            append("모니터 오류: " + t);
        } finally {
            stopMonitor();
            stopSelf();
        }
    }

    private void observeAfterExit(IBinder car, IBinder media, IBinder shared, long duration) {
        long end = SystemClock.elapsedRealtime() + duration;
        String previous = null;
        while (running.get() && SystemClock.elapsedRealtime() < end) {
            try {
                String now = compactState(car, media, shared);
                if (previous == null || !previous.equals(now)) {
                    append("R해제 후: " + now);
                    previous = now;
                }
            } catch (Throwable t) {
                append("후속 조회 오류: " + t);
                break;
            }
            SystemClock.sleep(200L);
        }
        append("상태 기록 종료");
    }

    private void runSource2Test(IBinder car, IBinder media, IBinder shared) throws Exception {
        append(snapshot(car, media, shared, "R해제 직후"));

        // Let Reglink's reverse-close path finish first.
        SystemClock.sleep(500L);

        launchFrontView(false);
        append("FrontViewActivity 실행 요청");

        // FrontViewActivity's inherited onResume selects source 3.
        // Override it only after its onResume had time to run.
        SystemClock.sleep(450L);

        for (int i = 1; i <= 5 && running.get(); i++) {
            int before = safeMediaSource(media);
            int returned = RegLinkClient.mediaSwitch(media, 2);
            SystemClock.sleep(120L);
            int after = safeMediaSource(media);
            boolean signal = safeShared(shared, "camera.signal");
            append("source2 전환 #" + i
                    + " before=" + before
                    + " returnedPrevious=" + returned
                    + " after=" + after
                    + " signal=" + signal);
            SystemClock.sleep(180L);
        }

        append("OEM 입력(source 2) 관찰 7초 시작");
        long end = SystemClock.elapsedRealtime() + 7000L;
        String previous = null;
        while (running.get() && SystemClock.elapsedRealtime() < end) {
            String now = compactState(car, media, shared);
            if (previous == null || !previous.equals(now)) {
                append("SOURCE2: " + now);
                previous = now;
            }
            SystemClock.sleep(200L);
        }

        append("7초 시험 종료 → FrontView 닫기 / Android source 복귀");
        launchFrontView(true);
        SystemClock.sleep(250L);
        try {
            int prev = RegLinkClient.mediaSwitch(media, 1);
            append("media source 1(Android) 복귀 / previous=" + prev
                    + " current=" + safeMediaSource(media));
        } catch (Throwable t) {
            append("Android source 복귀 오류: " + t);
        }
    }

    private void launchFrontView(boolean finish) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(
                "com.reglink.apps.camera",
                "com.reglink.apps.camera.FrontViewActivity"));
        i.putExtra("finish", finish);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private String compactState(IBinder car, IBinder media, IBinder shared) {
        try {
            return "rev=" + RegLinkClient.reversing(car)
                    + " gear=" + RegLinkClient.gear(car)
                    + " panoramic=" + RegLinkClient.cameraRequired(car, "panoramic")
                    + " media=" + RegLinkClient.mediaSource(media)
                    + " signal=" + RegLinkClient.sharedBoolean(shared, "camera.signal", false)
                    + " reverseOpen=" + RegLinkClient.sharedBoolean(shared, "camera_controller_reverse_open", false)
                    + " frontOpen=" + RegLinkClient.sharedBoolean(shared, "camera_controller_front_open", false);
        } catch (Throwable t) {
            return "(state error " + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
        }
    }

    private String snapshot(IBinder car, IBinder media, IBinder shared, String label) {
        return label + " / " + compactState(car, media, shared);
    }

    private int safeMediaSource(IBinder media) {
        try { return RegLinkClient.mediaSource(media); }
        catch (Throwable t) { return -999; }
    }

    private boolean safeShared(IBinder shared, String key) {
        try { return RegLinkClient.sharedBoolean(shared, key, false); }
        catch (Throwable t) { return false; }
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
