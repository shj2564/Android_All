package kr.song.peugeot.frontprobev13;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrontProbeService extends Service {
    static final String ACTION_QUERY = "kr.song.peugeot.frontprobev13.QUERY";
    static final String ACTION_MANUAL_IMPLICIT = "kr.song.peugeot.frontprobev13.MANUAL_IMPLICIT";
    static final String ACTION_MANUAL_EXPLICIT = "kr.song.peugeot.frontprobev13.MANUAL_EXPLICIT";
    static final String ACTION_ARM = "kr.song.peugeot.frontprobev13.ARM";
    static final String ACTION_STOP = "kr.song.peugeot.frontprobev13.STOP";

    static final String PREFS = "front_probe_log";
    static final String KEY_LOG = "log";

    static final String FRONT_ACTION = "com.reglink.action.FrontCamera";
    static final String CAMERA_PACKAGE = "com.reglink.apps.camera";

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

        if (ACTION_QUERY.equals(action)) {
            clearAndAppend("v13 FrontCamera 처리 경로 조회");
            dumpCameraPackage();
            dumpFrontHandlers();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_MANUAL_IMPLICIT.equals(action)) {
            clearAndAppend("v13 수동 implicit FrontCamera 1회 시험");
            dumpCameraPackage();
            dumpFrontHandlers();
            launchImplicitOnce();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_MANUAL_EXPLICIT.equals(action)) {
            clearAndAppend("v13 수동 explicit FrontCamera 1회 시험");
            dumpCameraPackage();
            dumpFrontHandlers();
            launchExplicitOnce();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_ARM.equals(action) && running.compareAndSet(false, true)) {
            clearAndAppend("v13 R 해제 → FrontCamera 7초 처리기 진단 시작");
            append("gear_d 미사용 / isReversing true→false 감지");
            append("R 해제 후 300ms 대기, explicit FrontCamera 약 850ms 간격 재요청");
            append("CAN write/rawWrite=0 / SharedVar set=0 / FrontGearStart 변경=0");
            dumpCameraPackage();
            dumpFrontHandlers();
            bindRegLink();
        } else if (ACTION_ARM.equals(action)) {
            append("이미 실차 시험 진행 중");
        }

        return START_NOT_STICKY;
    }

    private void dumpCameraPackage() {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(CAMERA_PACKAGE, PackageManager.GET_ACTIVITIES);
            ApplicationInfo app = pi.applicationInfo;
            append("camera package FOUND: " + CAMERA_PACKAGE
                    + " versionName=" + pi.versionName
                    + " versionCode=" + pi.versionCode);
            if (app != null) {
                append("camera sourceDir=" + app.sourceDir);
                append("camera flags: system=" + ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                        + " updatedSystem=" + ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                        + " uid=" + app.uid);
            }
            ActivityInfo[] acts = pi.activities;
            if (acts == null || acts.length == 0) {
                append("camera package activities=(none visible)");
            } else {
                append("camera package activities=" + acts.length);
                for (int i = 0; i < acts.length && i < 30; i++) {
                    ActivityInfo ai = acts[i];
                    append("  activity[" + i + "]=" + ai.name
                            + " exported=" + ai.exported
                            + " permission=" + nullText(ai.permission));
                }
            }
        } catch (Throwable t) {
            append("camera package 조회 실패: " + t);
        }
    }

    private void dumpFrontHandlers() {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(FRONT_ACTION);
        try {
            ResolveInfo resolved = pm.resolveActivity(probe, PackageManager.GET_RESOLVED_FILTER);
            if (resolved == null || resolved.activityInfo == null) {
                append("resolveActivity(" + FRONT_ACTION + ") = NONE");
            } else {
                append("resolveActivity=" + component(resolved.activityInfo)
                        + " exported=" + resolved.activityInfo.exported
                        + " permission=" + nullText(resolved.activityInfo.permission));
            }

            List<ResolveInfo> list = pm.queryIntentActivities(probe, PackageManager.GET_RESOLVED_FILTER);
            append("queryIntentActivities count=" + (list == null ? 0 : list.size()));
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    ResolveInfo ri = list.get(i);
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null) {
                        append("  handler[" + i + "]=(activityInfo null)");
                        continue;
                    }
                    append("  handler[" + i + "]=" + component(ai)
                            + " exported=" + ai.exported
                            + " permission=" + nullText(ai.permission));
                    dumpFilter("    ", ri.filter);
                }
            }

            Intent packageProbe = new Intent(FRONT_ACTION);
            packageProbe.setPackage(CAMERA_PACKAGE);
            List<ResolveInfo> own = pm.queryIntentActivities(packageProbe, PackageManager.GET_RESOLVED_FILTER);
            append("camera-package handler count=" + (own == null ? 0 : own.size()));
            if (own != null) {
                for (int i = 0; i < own.size(); i++) {
                    if (own.get(i).activityInfo != null)
                        append("  cameraHandler[" + i + "]=" + component(own.get(i).activityInfo));
                }
            }
        } catch (Throwable t) {
            append("FrontCamera handler 조회 실패: " + t);
        }
    }

    private void dumpFilter(String prefix, IntentFilter f) {
        if (f == null) return;
        try {
            StringBuilder a = new StringBuilder();
            for (int i = 0; i < f.countActions(); i++) {
                if (i > 0) a.append(",");
                a.append(f.getAction(i));
            }
            StringBuilder c = new StringBuilder();
            for (int i = 0; i < f.countCategories(); i++) {
                if (i > 0) c.append(",");
                c.append(f.getCategory(i));
            }
            append(prefix + "filter actions=[" + a + "] categories=[" + c + "]");
        } catch (Throwable t) {
            append(prefix + "filter dump error=" + t.getClass().getSimpleName());
        }
    }

    private void launchImplicitOnce() {
        try {
            Intent launch = new Intent(FRONT_ACTION);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            append("implicit startActivity 요청");
            startActivity(launch);
            append("implicit startActivity return=OK");
        } catch (Throwable t) {
            append("implicit FrontCamera 실패: " + t);
        }
    }

    private void launchExplicitOnce() {
        ResolveInfo target = chooseFrontHandler();
        if (target == null || target.activityInfo == null) {
            append("explicit 대상 handler 없음");
            return;
        }
        ActivityInfo ai = target.activityInfo;
        append("explicit target=" + component(ai));
        try {
            startActivity(makeExplicit(ai));
            append("explicit startActivity return=OK");
        } catch (Throwable t) {
            append("explicit FrontCamera 실패: " + t);
        }
    }

    private Intent makeExplicit(ActivityInfo ai) {
        Intent launch = new Intent(FRONT_ACTION);
        launch.setComponent(new ComponentName(ai.packageName, ai.name));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return launch;
    }

    private ResolveInfo chooseFrontHandler() {
        try {
            Intent probe = new Intent(FRONT_ACTION);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(
                    probe, PackageManager.GET_RESOLVED_FILTER);
            if (list == null || list.isEmpty()) return null;
            for (ResolveInfo r : list) {
                if (r.activityInfo != null && CAMERA_PACKAGE.equals(r.activityInfo.packageName)) return r;
            }
            return list.get(0);
        } catch (Throwable t) {
            append("FrontCamera 대상 선택 실패: " + t);
            return null;
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
                    }, "FrontProbe-v13").start();
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
                    append("R 해제 +300ms → FrontCamera 7초 재요청 시작");
                    holdFrontForSevenSeconds();
                    append("7초 재요청 종료");
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

    private void holdFrontForSevenSeconds() {
        ResolveInfo target = chooseFrontHandler();
        if (target == null || target.activityInfo == null) {
            append("FrontCamera handler 없음");
            return;
        }

        ActivityInfo ai = target.activityInfo;
        append("HOLD target=" + component(ai)
                + " exported=" + ai.exported
                + " permission=" + nullText(ai.permission));

        final long until = SystemClock.elapsedRealtime() + 7000L;
        int count = 0;
        while (running.get() && SystemClock.elapsedRealtime() < until) {
            try {
                startActivity(makeExplicit(ai));
                count++;
                append("FrontCamera explicit 요청 #" + count + " return=OK");
            } catch (Throwable t) {
                append("FrontCamera explicit 요청 실패 #" + (count + 1) + ": " + t);
                break;
            }
            SystemClock.sleep(850L);
        }
    }

    private String safeGear(IBinder car) {
        try { return String.valueOf(RegLinkClient.gear(car)); }
        catch (Throwable t) { return "(gear err " + t.getClass().getSimpleName() + ")"; }
    }

    private static String component(ActivityInfo ai) {
        return ai.packageName + "/" + ai.name;
    }

    private static String nullText(String s) {
        return s == null ? "(none)" : s;
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
