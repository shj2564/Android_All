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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AutoFrontService extends Service {
    static final String ACTION_START = "kr.song.peugeot.frontautov18.START";
    static final String ACTION_STOP = "kr.song.peugeot.frontautov18.STOP";

    static final String PREFS = "front_auto_v18";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_RUNNING = "running";
    static final String KEY_LOG = "log";
    static final String KEY_LAST_EVENT = "last_event";
    static final String KEY_SUCCESS_COUNT = "success_count";

    private static final String CHANNEL_ID = "front_auto_v182";
    private static final int NOTIFICATION_ID = 500819;

    // First R->D keeps the proven v16 timing.
    private static final long COLD_REVERSE_SETTLE_MS = 500L;
    private static final long COLD_FRONT_RESUME_MS = 450L;

    // Once FrontView exists, quick parking reversals use a warm handoff.
    private static final long WARM_REVERSE_SETTLE_MS = 120L;
    private static final long WARM_FRONT_RESUME_MS = 120L;
    private static final long FRONT_HOLD_MS = 7000L;
    private static final long POLL_MS = 80L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean monitorRunning = new AtomicBoolean(false);
    private final AtomicBoolean frontWarm = new AtomicBoolean(false);
    private final AtomicInteger generation = new AtomicInteger(0);
    private final ExecutorService cameraWorker = Executors.newSingleThreadExecutor();

    private volatile boolean currentReverse;

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
        startForeground(NOTIFICATION_ID, makeNotification("주차 카메라 자동전환 대기 중"));
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

        if (!isEnabled(this)) setEnabled(this, true);

        if (active.compareAndSet(false, true)) {
            setRunning(true);
            append("v18.2 parking-smooth 시작");
            append("첫 R해제는 v16 성공 타이밍 유지: 500ms → FrontView → 450ms → source2");
            append("주차 중 재전환은 FrontView를 닫지 않고 warm handoff 사용");
            append("R 우선 / media source2 유지 / gear 값 미사용");
            append("CAN/CANBox/SharedVar/GPIO/MCU 직접 쓰기=0");
            updateNotification("RegLink 연결 중");
            bindRegLink();
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
                    updateNotification("주차 카메라 자동전환 대기 중");
                    startMonitor();
                } catch (Throwable t) {
                    append("binder 연결 오류: " + compactError(t));
                    reconnectLater();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                append("DroidCarService 연결 끊김 → 재연결");
                reconnectLater();
            }
        };

        try {
            Intent svc = new Intent("com.reglink.action.DroidCarService");
            svc.setComponent(new ComponentName(
                    "com.reglink.services",
                    "com.reglink.services.DroidCarService"));
            boolean ok = bindService(svc, connection, 0);
            bound = ok;
            if (!ok) {
                append("DroidCarService bindService=false → 2초 후 재시도");
                main.postDelayed(bindRetry, 2000L);
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
                        currentReverse = reverse;

                        if (previousReverse == null) {
                            previousReverse = Boolean.valueOf(reverse);
                            append("감시 시작 / reversing=" + reverse + " gear=" + safeGear());
                        } else if (previousReverse.booleanValue() != reverse) {
                            boolean was = previousReverse.booleanValue();
                            previousReverse = Boolean.valueOf(reverse);
                            append("R 상태 변화: " + was + " → " + reverse + " / gear=" + safeGear());

                            if (!was && reverse) onReverseEntered();
                            else if (was && !reverse) onReverseExited();
                        }

                        SystemClock.sleep(POLL_MS);
                    }
                } catch (Throwable t) {
                    if (active.get()) append("감시 오류: " + compactError(t));
                } finally {
                    monitorRunning.set(false);
                    if (active.get()) reconnectLater();
                }
            }
        }, "FrontAuto-v182-monitor").start();
    }

    private void onReverseEntered() {
        int token = generation.incrementAndGet();
        setLastEvent("R 재진입 · 후방 우선");
        updateNotification("후방카메라 우선");

        // Important change from v18.1:
        // Do NOT finish FrontView here. Keeping it warm avoids an Activity close/open race
        // during R-D-R-D parking maneuvers. Native RearCameraActivity can sit on top.
        append("R 진입 token=" + token + " / FrontView warm=" + frontWarm.get() + " / 닫지 않음");

        try {
            if (media != null) {
                int before = safeMediaSource();
                int prev = RegLinkClient.mediaSwitch(media, 2);
                append("R 우선 source2 보강 / before=" + before + " previous=" + prev
                        + " after=" + safeMediaSource());
            }
        } catch (Throwable t) {
            append("R source2 보강 오류: " + compactError(t));
        }
    }

    private void onReverseExited() {
        final int token = generation.incrementAndGet();
        final boolean warm = frontWarm.get();

        setLastEvent(warm ? "R 해제 · warm 전방 전환" : "R 해제 · cold 전방 전환");
        updateNotification(warm ? "전방카메라 빠른 전환 준비" : "전방카메라 전환 준비");
        append("R 해제 token=" + token + " / warm=" + warm);

        cameraWorker.execute(new Runnable() {
            @Override public void run() {
                runFrontSequence(token, warm);
            }
        });
    }

    private void runFrontSequence(int token, boolean warmAtStart) {
        try {
            long settle = warmAtStart ? WARM_REVERSE_SETTLE_MS : COLD_REVERSE_SETTLE_MS;
            long resume = warmAtStart ? WARM_FRONT_RESUME_MS : COLD_FRONT_RESUME_MS;

            if (!cancelableDelay(token, settle, true)) return;

            launchFrontView(false);
            frontWarm.set(true);
            append((warmAtStart ? "warm" : "cold") + " FrontViewActivity foreground 요청");

            if (!cancelableDelay(token, resume, true)) return;

            int loops = warmAtStart ? 2 : 5;
            for (int i = 1; i <= loops; i++) {
                if (!isTokenActiveForFront(token)) return;

                int before = safeMediaSource();
                int prev = RegLinkClient.mediaSwitch(media, 2);

                if (!cancelableDelay(token, 90L, true)) return;

                int after = safeMediaSource();
                append((warmAtStart ? "warm" : "cold") + " source2 #" + i
                        + " before=" + before + " previous=" + prev + " after=" + after);

                if (i < loops && !cancelableDelay(token, warmAtStart ? 70L : 130L, true)) return;
            }

            if (!isTokenActiveForFront(token)) return;

            updateNotification("전방카메라 표시 중");
            append("전방 source2 표시 시작 / token=" + token);

            long end = SystemClock.elapsedRealtime() + FRONT_HOLD_MS;
            while (active.get() && SystemClock.elapsedRealtime() < end) {
                if (!isTokenActiveForFront(token)) {
                    append("전방 유지 중 새 R/D 이벤트 → warm 상태로 양도 / token=" + token);
                    return;
                }
                SystemClock.sleep(60L);
            }

            // Close only if absolutely no new shift happened for the full 7 seconds.
            if (!isTokenActiveForFront(token)) return;

            append("7초 동안 추가 변속 없음 → FrontView 종료");
            launchFrontView(true);

            if (!cancelableDelay(token, 220L, true)) return;

            if (isTokenActiveForFront(token)) {
                int previous = RegLinkClient.mediaSwitch(media, 1);
                frontWarm.set(false);
                incrementSuccess();
                setLastEvent("전방카메라 7초 표시 완료");
                append("Android source1 복귀 / previous=" + previous
                        + " current=" + safeMediaSource());
            }
        } catch (Throwable t) {
            append("전방 시퀀스 오류: " + compactError(t));
            // Never steal source 2 from reverse on an error.
            if (!currentReverse && token == generation.get()) {
                try {
                    launchFrontView(true);
                    SystemClock.sleep(100L);
                    if (!currentReverse && media != null) RegLinkClient.mediaSwitch(media, 1);
                } catch (Throwable ignored) {}
                frontWarm.set(false);
            }
        } finally {
            if (active.get()) {
                updateNotification(currentReverse ? "후방카메라 우선" : "주차 카메라 자동전환 대기 중");
            }
        }
    }

    private boolean cancelableDelay(int token, long millis, boolean requireNonReverse) {
        long end = SystemClock.elapsedRealtime() + millis;
        while (active.get() && SystemClock.elapsedRealtime() < end) {
            if (token != generation.get()) return false;
            if (requireNonReverse && currentReverse) return false;
            SystemClock.sleep(35L);
        }
        return active.get()
                && token == generation.get()
                && (!requireNonReverse || !currentReverse);
    }

    private boolean isTokenActiveForFront(int token) {
        return active.get()
                && token == generation.get()
                && !currentReverse;
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

        generation.incrementAndGet();
        frontWarm.set(false);

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
        generation.incrementAndGet();
        main.removeCallbacks(bindRetry);

        try {
            if (!currentReverse && frontWarm.get()) {
                launchFrontView(true);
                SystemClock.sleep(100L);
                if (media != null && !currentReverse) RegLinkClient.mediaSwitch(media, 1);
            }
        } catch (Throwable ignored) {}

        frontWarm.set(false);

        if (bound && connection != null) {
            try { unbindService(connection); } catch (Throwable ignored) {}
        }

        bound = false;
        connection = null;
        car = null;
        media = null;
        shared = null;
        monitorRunning.set(false);
        setRunning(false);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "5008 주차 카메라 자동전환",
                NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("R/D 반복 주차 시 전후방 영상 전환 지연 완화");
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
                .setContentTitle("5008 주차 카메라 v18.2")
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
        p.edit().putInt(KEY_SUCCESS_COUNT, p.getInt(KEY_SUCCESS_COUNT, 0) + 1).apply();
    }

    private void setLastEvent(String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(KEY_LAST_EVENT, value).apply();
    }

    private void setRunning(boolean running) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, running).apply();
    }

    private synchronized void append(String line) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String old = p.getString(KEY_LOG, "");
        String now = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String next = old + now + "  " + line + "\n";

        final int max = 30000;
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
