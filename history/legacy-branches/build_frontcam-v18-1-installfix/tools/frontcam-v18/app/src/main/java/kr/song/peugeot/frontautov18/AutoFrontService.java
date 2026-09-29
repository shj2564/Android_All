package kr.song.peugeot.frontautov18;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutoFrontService extends Service {
    static final String ACTION_START = "kr.song.peugeot.frontautov18.START";
    static final String ACTION_STOP = "kr.song.peugeot.frontautov18.STOP";

    static final String PREFS = "front_auto_v18";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_RUNNING = "running";
    static final String KEY_LOG = "log";
    static final String KEY_LAST_EVENT = "last_event";
    static final String KEY_SUCCESS_COUNT = "success_count";

    private static final String CHANNEL_ID = "front_auto_v18";
    private static final int NOTIFICATION_ID = 500818;

    // Keep the successful v16 timing untouched.
    private static final long REVERSE_CLOSE_SETTLE_MS = 500L;
    private static final long FRONT_VIEW_RESUME_MS = 450L;
    private static final long FRONT_HOLD_MS = 7000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean monitorRunning = new AtomicBoolean(false);
    private final AtomicBoolean sequenceRunning = new AtomicBoolean(false);

    private ServiceConnection connection;
    private boolean bound;
    private IBinder car;
    private IBinder media;
    private IBinder shared;

    private final Runnable bindRetry = new Runnable() {
        @Override public void run() { bindRegLink(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, makeNotification("전방카메라 자동복구 대기 중"));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            setEnabled(this, false);
            append("자동복구 사용자 중지");
            shutdown();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isEnabled(this)) {
            // First launch/install defaults to enabled because this build is specifically an auto-recovery build.
            setEnabled(this, true);
        }

        if (active.compareAndSet(false, true)) {
            setRunning(true);
            append("v18 백그라운드 자동복구 시작");
            append("v16 성공 로직 고정: R해제 → 500ms → FrontView → 450ms → source2 → 7초");
            append("CAN/CANBox/SharedVar/GPIO/MCU 직접 쓰기=0");
            updateNotification("RegLink 연결 중");
            bindRegLink();
        } else {
            updateNotification(sequenceRunning.get() ? "전방영상 표시 중" : "전방카메라 자동복구 대기 중");
        }

        return START_STICKY;
    }

    static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context c, boolean enabled) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private void bindRegLink() {
        if (!active.get() || bound) return;
        main.removeCallbacks(bindRetry);

        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder droid) {
                if (!active.get()) return;
                bound = true;
                append("DroidCarService 연결: " + name.flattenToShortString());
                try {
                    car = RegLinkClient.getService(droid, "Car", RegLinkClient.CAR);
                    media = RegLinkClient.getService(droid, "Media", RegLinkClient.MEDIA);
                    shared = RegLinkClient.getService(droid, "SharedVariable", RegLinkClient.SHARED);
                    append("Car/Media/SharedVariable binder 연결 완료");
                    updateNotification("전방카메라 자동복구 대기 중");
                    startMonitor();
                } catch (Throwable t) {
                    append("binder 연결 오류: " + compactError(t));
                    reconnectLater();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                append("DroidCarService 연결 끊김 → 재연결 대기");
                reconnectLater();
            }
        };

        try {
            Intent svc = new Intent("com.reglink.action.DroidCarService");
            svc.setComponent(new ComponentName(
                    "com.reglink.services",
                    "com.reglink.services.DroidCarService"));
            boolean ok = bindService(svc, connection, 0);
            if (!ok) {
                append("DroidCarService bindService=false → 2초 후 재시도");
                bound = false;
                main.postDelayed(bindRetry, 2000L);
            } else {
                bound = true;
            }
        } catch (Throwable t) {
            append("서비스 연결 예외: " + compactError(t));
            bound = false;
            main.postDelayed(bindRetry, 2000L);
        }
    }

    private void startMonitor() {
        if (!active.get()) return;
        if (!monitorRunning.compareAndSet(false, true)) return;

        new Thread(new Runnable() {
            @Override public void run() {
                Boolean previousReverse = null;
                try {
                    while (active.get()) {
                        boolean reverse = RegLinkClient.reversing(car);

                        if (previousReverse == null) {
                            previousReverse = Boolean.valueOf(reverse);
                            append("감시 시작 / reversing=" + reverse + " gear=" + safeGear());
                        } else if (previousReverse.booleanValue() != reverse) {
                            boolean wasReverse = previousReverse.booleanValue();
                            previousReverse = Boolean.valueOf(reverse);
                            append("R 상태 변화: " + wasReverse + " → " + reverse
                                    + " / gear=" + safeGear());

                            if (wasReverse && !reverse) {
                                runVerifiedV16Sequence();
                            }
                        }

                        SystemClock.sleep(150L);
                    }
                } catch (Throwable t) {
                    if (active.get()) append("감시 오류: " + compactError(t));
                } finally {
                    monitorRunning.set(false);
                    if (active.get()) reconnectLater();
                }
            }
        }, "FrontAuto-v18").start();
    }

    private void runVerifiedV16Sequence() {
        if (!sequenceRunning.compareAndSet(false, true)) return;

        try {
            setLastEvent("R 해제 감지");
            updateNotification("R 해제 감지 · 전방 전환 준비");
            append("R 해제 감지 → v16 성공 시퀀스 시작");

            // v16: let Reglink's reverse-close path finish first.
            SystemClock.sleep(REVERSE_CLOSE_SETTLE_MS);

            if (!active.get()) return;
            if (safeReversing()) {
                append("500ms 내 R 재진입 → 전방 전환 취소");
                return;
            }

            launchFrontView(false);
            append("FrontViewActivity 실행 요청");

            // v16: FrontView's onResume temporarily selects source 3.
            SystemClock.sleep(FRONT_VIEW_RESUME_MS);

            if (!active.get()) {
                finishFrontIfSafe();
                return;
            }
            if (safeReversing()) {
                append("FrontView 준비 중 R 재진입 → 즉시 취소");
                closeFrontForReverse();
                return;
            }

            // Preserve v16's repeated source-2 override exactly.
            for (int i = 1; i <= 5 && active.get(); i++) {
                if (safeReversing()) {
                    append("source2 전환 중 R 재진입 → 즉시 취소");
                    closeFrontForReverse();
                    return;
                }

                int before = safeMediaSource();
                int returned = RegLinkClient.mediaSwitch(media, 2);
                SystemClock.sleep(120L);
                int after = safeMediaSource();

                append("source2 #" + i
                        + " before=" + before
                        + " returnedPrevious=" + returned
                        + " after=" + after);

                SystemClock.sleep(180L);
            }

            if (!active.get()) {
                finishFrontIfSafe();
                return;
            }

            updateNotification("전방카메라 표시 중");
            append("OEM source2 전방영상 7초 유지 시작");
            long end = SystemClock.elapsedRealtime() + FRONT_HOLD_MS;

            while (active.get() && SystemClock.elapsedRealtime() < end) {
                if (safeReversing()) {
                    append("7초 유지 중 R 재진입 → 전방 종료, 후방 우선");
                    closeFrontForReverse();
                    return;
                }
                SystemClock.sleep(120L);
            }

            if (!active.get()) {
                finishFrontIfSafe();
                return;
            }

            append("7초 완료 → FrontView 닫기");
            launchFrontView(true);
            SystemClock.sleep(250L);

            // Never force Android source while the driver has re-entered R.
            if (!safeReversing()) {
                int previous = RegLinkClient.mediaSwitch(media, 1);
                append("Android source 1 복귀 / previous=" + previous
                        + " current=" + safeMediaSource());
            } else {
                append("종료 시 R 재진입 상태 → Android source 복귀 생략");
            }

            incrementSuccess();
            setLastEvent("전방카메라 7초 자동표시 완료");
            append("자동 전방표시 완료");
        } catch (Throwable t) {
            append("자동 전방 시퀀스 오류: " + compactError(t));
            finishFrontIfSafe();
        } finally {
            sequenceRunning.set(false);
            if (active.get()) updateNotification("전방카메라 자동복구 대기 중");
        }
    }

    private void closeFrontForReverse() {
        try { launchFrontView(true); } catch (Throwable ignored) {}
        // Do not switch media to Android here. Reverse camera must retain source 2.
    }

    private void finishFrontIfSafe() {
        try { launchFrontView(true); } catch (Throwable ignored) {}
        SystemClock.sleep(100L);
        try {
            if (!safeReversing() && media != null) RegLinkClient.mediaSwitch(media, 1);
        } catch (Throwable ignored) {}
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

    private boolean safeReversing() {
        try { return car != null && RegLinkClient.reversing(car); }
        catch (Throwable t) { return false; }
    }

    private String safeGear() {
        try { return car == null ? "(no car)" : String.valueOf(RegLinkClient.gear(car)); }
        catch (Throwable t) { return "(gear err)"; }
    }

    private int safeMediaSource() {
        try { return media == null ? -999 : RegLinkClient.mediaSource(media); }
        catch (Throwable t) { return -999; }
    }

    private void reconnectLater() {
        if (!active.get()) return;
        main.post(new Runnable() {
            @Override public void run() {
                if (!active.get()) return;
                if (bound && connection != null) {
                    try { unbindService(connection); } catch (Throwable ignored) {}
                }
                bound = false;
                connection = null;
                car = null;
                media = null;
                shared = null;
                main.removeCallbacks(bindRetry);
                main.postDelayed(bindRetry, 1500L);
                updateNotification("RegLink 재연결 중");
            }
        });
    }

    private void shutdown() {
        active.set(false);
        main.removeCallbacks(bindRetry);

        if (sequenceRunning.get()) finishFrontIfSafe();

        if (bound && connection != null) {
            try { unbindService(connection); } catch (Throwable ignored) {}
        }

        bound = false;
        connection = null;
        car = null;
        media = null;
        shared = null;
        monitorRunning.set(false);
        sequenceRunning.set(false);
        setRunning(false);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "5008 전방카메라 자동복구",
                NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("R 해제 후 전방카메라 자동표시 서비스");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification makeNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("5008 전방카메라 자동복구")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, makeNotification(text));
    }

    private void incrementSuccess() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int count = p.getInt(KEY_SUCCESS_COUNT, 0) + 1;
        p.edit().putInt(KEY_SUCCESS_COUNT, count).apply();
    }

    private void setLastEvent(String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LAST_EVENT, value)
                .apply();
    }

    private void setRunning(boolean running) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, running)
                .apply();
    }

    private synchronized void append(String line) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String old = p.getString(KEY_LOG, "");
        String now = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String next = old + now + "  " + line + "\n";

        // Keep the background log bounded so SharedPreferences does not grow indefinitely.
        final int max = 28000;
        if (next.length() > max) {
            int cut = next.length() - max;
            int nl = next.indexOf('\n', cut);
            if (nl >= 0 && nl + 1 < next.length()) next = next.substring(nl + 1);
            else next = next.substring(cut);
        }

        p.edit().putString(KEY_LOG, next).apply();
    }

    private String compactError(Throwable t) {
        if (t == null) return "(null)";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    @Override public void onDestroy() {
        boolean restart = isEnabled(this) && active.get();
        shutdown();
        stopForeground(true);
        super.onDestroy();

        // START_STICKY handles normal process recreation. This delayed restart also helps
        // on head units whose service manager kills an app process during ACC transitions.
        if (restart) {
            try {
                Intent i = new Intent(this, AutoFrontService.class);
                i.setAction(ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } catch (Throwable ignored) {}
        }
    }
}
