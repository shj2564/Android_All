package kr.song.peugeot.reverseholdv12;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReverseHoldService extends Service {
    static final String ACTION_ARM = "kr.song.peugeot.reverseholdv12.ARM";
    static final String ACTION_STOP = "kr.song.peugeot.reverseholdv12.STOP";
    static final String PREFS = "reverse_hold_log";
    static final String KEY_LOG = "log";
    static final String FRONT_ACTION = "com.reglink.action.FrontCamera";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServiceConnection connection;
    private boolean bound;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            append("사용자 중지");
            stopMonitor();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_ARM.equals(action) && running.compareAndSet(false, true)) {
            clearAndAppend("v12 R→D 7초 HOLD 시험 시작");
            append("원리: gear_d 미사용, isReversing true→false만 감지");
            append("차량 CAN/SharedVar 쓰기 없음");
            bindRegLink();
        } else if (ACTION_ARM.equals(action)) {
            append("이미 시험 진행 중");
        }
        return START_NOT_STICKY;
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
                    }, "ReverseHold-v12").start();
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
                    append("R 해제 감지 → 7초 FrontCamera 유지 시험 시작");
                    holdFrontForSevenSeconds();
                    append("7초 유지 요청 종료");
                    break;
                }
                SystemClock.sleep(100L);
            }
            if (!sawReverse) append("45초 내 R 상태를 감지하지 못함");
            else if (running.get() && previous != null && previous.booleanValue())
                append("45초 내 R 해제를 감지하지 못함");
        } catch (Throwable t) {
            append("모니터 오류: " + t);
        } finally {
            stopMonitor();
            stopSelf();
        }
    }

    private void holdFrontForSevenSeconds() {
        ResolveInfo target = chooseFrontHandler();
        if (target == null || target.activityInfo == null) {
            append("FrontCamera handler 없음");
            return;
        }
        ActivityInfo ai = target.activityInfo;
        append("FrontCamera target=" + ai.packageName + "/" + ai.name
                + " exported=" + ai.exported
                + " permission=" + (ai.permission == null ? "(none)" : ai.permission));

        final long until = SystemClock.elapsedRealtime() + 7000L;
        int count = 0;
        while (running.get() && SystemClock.elapsedRealtime() < until) {
            try {
                Intent launch = new Intent(FRONT_ACTION);
                launch.setComponent(new ComponentName(ai.packageName, ai.name));
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
                count++;
                append("FrontCamera 유지 요청 #" + count);
            } catch (Throwable t) {
                append("FrontCamera 실행 실패: " + t);
                break;
            }
            SystemClock.sleep(850L);
        }
    }

    private ResolveInfo chooseFrontHandler() {
        try {
            Intent probe = new Intent(FRONT_ACTION);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(probe, 0);
            if (list == null || list.isEmpty()) return null;
            for (ResolveInfo r : list) {
                if (r.activityInfo != null && "com.reglink.apps.camera".equals(r.activityInfo.packageName)) return r;
            }
            return list.get(0);
        } catch (Throwable t) {
            append("FrontCamera handler 조회 실패: " + t);
            return null;
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
