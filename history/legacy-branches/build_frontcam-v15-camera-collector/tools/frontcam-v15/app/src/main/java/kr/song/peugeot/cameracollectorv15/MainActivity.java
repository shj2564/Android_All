package kr.song.peugeot.cameracollectorv15;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class MainActivity extends Activity {
    private static final String CAMERA_PACKAGE = "com.reglink.apps.camera";
    private static final String AUTHORITY = "kr.song.peugeot.cameracollectorv15.files";
    private static final String[] KEYWORDS = new String[] {
            "front", "camera", "view", "source", "input", "channel", "avin", "cvbs",
            "signal", "video", "reverse", "gear", "mcu", "decoder", "droid", "reglink",
            "aux", "rear", "preview", "surface", "native", "tvin", "av"
    };

    private TextView log;
    private volatile boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("5008 카메라 수집 v15");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 22);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 camera.apk 수집 / 정적 스캔 v15");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v14 실차 결과: 후면 영상 정상 → R 해제 후 FrontViewActivity 전환 성공 → 전방은 '신호 없음'.\n"
                + "이 앱은 차량 제어를 전혀 하지 않고 /system/app/camera 자료만 읽습니다.\n"
                + "1번으로 내부 문자열을 먼저 확인하고, 2번으로 실제 camera.apk+oat/vdex 묶음을 공유합니다.");
        desc.setTextSize(15f);
        desc.setTextColor(Color.DKGRAY);
        root.addView(desc);

        Button inspect = new Button(this);
        inspect.setText("1. camera.apk 경로/해시/DEX 핵심문자열 스캔");
        inspect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runInspect(); }
        });
        root.addView(inspect);

        Button share = new Button(this);
        share.setText("2. camera 시스템앱 전체 ZIP 만들고 공유");
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runShare(); }
        });
        root.addView(share);

        Button apkOnly = new Button(this);
        apkOnly.setText("3. camera.apk만 복사해서 공유");
        apkOnly.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runShareApkOnly(); }
        });
        root.addView(apkOnly);

        Button clear = new Button(this);
        clear.setText("로그 지우기");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setLog(""); }
        });
        root.addView(clear);

        log = new TextView(this);
        log.setTextSize(13f);
        log.setTextIsSelectable(true);
        root.addView(log);

        setContentView(scroll);
        setLog("준비됨. 차량 설정/CAN 쓰기 기능은 없습니다.\n");
    }

    private void runInspect() {
        if (busy) return;
        busy = true;
        setLog("camera 패키지 정적 조회 시작...\n");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String result = inspectCamera();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setLog(result);
                            busy = false;
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            append("오류: " + t + "\n");
                            busy = false;
                        }
                    });
                }
            }
        }, "camera-inspect-v15").start();
    }

    private void runShare() {
        if (busy) return;
        busy = true;
        append("시스템 카메라 폴더 ZIP 생성 중...\n");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File zip = makePackageZip();
                    final String sha = sha256(zip);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            append("ZIP 완료: " + zip.getName() + " / " + zip.length() + " bytes\n");
                            append("ZIP SHA-256=" + sha + "\n");
                            busy = false;
                            shareFile(zip, "application/zip");
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            append("ZIP 생성 실패: " + t + "\n");
                            busy = false;
                        }
                    });
                }
            }
        }, "camera-zip-v15").start();
    }

    private void runShareApkOnly() {
        if (busy) return;
        busy = true;
        append("camera.apk 복사 중...\n");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File out = copyApkOnly();
                    final String sha = sha256(out);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            append("APK 복사 완료: " + out.length() + " bytes\n");
                            append("APK SHA-256=" + sha + "\n");
                            busy = false;
                            shareFile(out, "application/vnd.android.package-archive");
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            append("APK 복사 실패: " + t + "\n");
                            busy = false;
                        }
                    });
                }
            }
        }, "camera-apk-v15").start();
    }

    private String inspectCamera() throws Exception {
        StringBuilder out = new StringBuilder();
        PackageManager pm = getPackageManager();
        PackageInfo pi = pm.getPackageInfo(CAMERA_PACKAGE, 0);
        ApplicationInfo ai = pi.applicationInfo;
        if (ai == null) throw new IllegalStateException("applicationInfo=null");

        File apk = new File(ai.sourceDir);
        out.append("package=").append(CAMERA_PACKAGE).append('\n');
        out.append("versionName=").append(pi.versionName).append(" versionCode=").append(pi.versionCode).append('\n');
        out.append("sourceDir=").append(ai.sourceDir).append('\n');
        out.append("apk exists=").append(apk.exists())
                .append(" readable=").append(apk.canRead())
                .append(" size=").append(apk.length()).append('\n');
        if (apk.exists() && apk.canRead()) {
            out.append("camera.apk SHA-256=").append(sha256(apk)).append('\n');
        }

        File dir = apk.getParentFile();
        out.append("\n[system camera directory]\n");
        List<File> files = new ArrayList<File>();
        collectFiles(dir, files, 0);
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getAbsolutePath().compareTo(b.getAbsolutePath());
            }
        });
        long total = 0L;
        for (File f : files) {
            total += f.length();
            out.append(f.canRead() ? "R " : "- ")
                    .append(f.length()).append(" ")
                    .append(f.getAbsolutePath()).append('\n');
        }
        out.append("files=").append(files.size()).append(" totalBytes=").append(total).append('\n');

        out.append("\n[APK entries]\n");
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int shown = 0;
            while (en.hasMoreElements() && shown < 120) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("classes")
                        || e.getName().startsWith("lib/")
                        || e.getName().contains("res/raw")
                        || e.getName().contains("assets/")) {
                    out.append(e.getName()).append(" size=").append(e.getSize()).append('\n');
                    shown++;
                }
            }
        } finally {
            zf.close();
        }

        out.append("\n[DEX candidate strings]\n");
        List<String> strings = scanDexCandidateStrings(apk, 350);
        if (strings.isEmpty()) {
            out.append("(후보 문자열 없음 — classes.dex가 preopt/외부 VDEX일 수 있음)\n");
        } else {
            for (String s : strings) out.append(s).append('\n');
        }
        return out.toString();
    }

    private List<String> scanDexCandidateStrings(File apk, int limit) throws Exception {
        ArrayList<String> result = new ArrayList<String>();
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements() && result.size() < limit) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!(n.startsWith("classes") && n.endsWith(".dex"))) continue;
                InputStream in = new BufferedInputStream(zf.getInputStream(e));
                try {
                    byte[] data = readAllLimited(in, 64 * 1024 * 1024);
                    extractPrintableCandidates(n, data, result, limit);
                } finally {
                    in.close();
                }
            }
        } finally {
            zf.close();
        }
        Collections.sort(result);
        return result;
    }

    private void extractPrintableCandidates(String dexName, byte[] data, List<String> out, int limit) {
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= data.length && out.size() < limit; i++) {
            int v = i < data.length ? (data[i] & 0xff) : 0;
            boolean printable = v >= 32 && v <= 126;
            if (printable) {
                run.append((char) v);
                if (run.length() > 500) run.delete(0, 250);
            } else {
                if (run.length() >= 4) {
                    String s = run.toString();
                    if (matchesKeyword(s) && !containsExact(out, dexName + ": " + s)) {
                        out.add(dexName + ": " + s);
                    }
                }
                run.setLength(0);
            }
        }
    }

    private boolean matchesKeyword(String s) {
        String l = s.toLowerCase(Locale.US);
        for (String k : KEYWORDS) {
            if (l.contains(k)) return true;
        }
        return false;
    }

    private boolean containsExact(List<String> list, String value) {
        for (String s : list) if (s.equals(value)) return true;
        return false;
    }

    private byte[] readAllLimited(InputStream in, int max) throws Exception {
        byte[] buf = new byte[32768];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int total = 0;
        for (;;) {
            int n = in.read(buf);
            if (n < 0) break;
            total += n;
            if (total > max) throw new IllegalStateException("entry exceeds scan limit");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private File copyApkOnly() throws Exception {
        ApplicationInfo ai = getPackageManager().getApplicationInfo(CAMERA_PACKAGE, 0);
        File src = new File(ai.sourceDir);
        File shareDir = prepareShareDir();
        File dst = new File(shareDir, "CMZX-camera-20260525.apk");
        copyFile(src, dst);
        return dst;
    }

    private File makePackageZip() throws Exception {
        ApplicationInfo ai = getPackageManager().getApplicationInfo(CAMERA_PACKAGE, 0);
        File apk = new File(ai.sourceDir);
        File root = apk.getParentFile();
        if (root == null || !root.exists()) throw new IllegalStateException("camera root missing");

        File shareDir = prepareShareDir();
        File out = new File(shareDir, "CMZX-camera-systemapp-20260525-v15.zip");

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        try {
            ArrayList<File> files = new ArrayList<File>();
            collectFiles(root, files, 0);
            byte[] buf = new byte[32768];
            for (File f : files) {
                if (!f.isFile() || !f.canRead()) continue;
                String rel = relativePath(root, f);
                ZipEntry e = new ZipEntry("camera/" + rel);
                e.setTime(f.lastModified());
                zos.putNextEntry(e);
                InputStream in = new BufferedInputStream(new FileInputStream(f));
                try {
                    for (;;) {
                        int n = in.read(buf);
                        if (n < 0) break;
                        zos.write(buf, 0, n);
                    }
                } finally {
                    in.close();
                    zos.closeEntry();
                }
            }

            String info = "sourceDir=" + ai.sourceDir + "\n"
                    + "package=" + CAMERA_PACKAGE + "\n"
                    + "collector=v15\n";
            ZipEntry meta = new ZipEntry("COLLECTOR_INFO.txt");
            zos.putNextEntry(meta);
            zos.write(info.getBytes("UTF-8"));
            zos.closeEntry();
        } finally {
            zos.close();
        }
        return out;
    }

    private File prepareShareDir() throws Exception {
        File dir = new File(getCacheDir(), "share");
        deleteRecursively(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("cannot create share dir");
        }
        return dir;
    }

    private void shareFile(File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this, AUTHORITY, f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "ChatGPT로 공유 또는 파일 저장"));
        } catch (Throwable t) {
            append("공유 실행 실패: " + t + "\n");
        }
    }

    private void collectFiles(File dir, List<File> out, int depth) {
        if (dir == null || depth > 5 || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File f : children) {
            if (f.isDirectory()) collectFiles(f, out, depth + 1);
            else out.add(f);
        }
    }

    private String relativePath(File root, File f) {
        String a = root.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) {
            b = b.substring(a.length());
            while (b.startsWith("/")) b = b.substring(1);
        }
        return b.length() == 0 ? f.getName() : b;
    }

    private void copyFile(File src, File dst) throws Exception {
        InputStream in = new BufferedInputStream(new FileInputStream(src));
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst));
        try {
            byte[] buf = new byte[32768];
            for (;;) {
                int n = in.read(buf);
                if (n < 0) break;
                out.write(buf, 0, n);
            }
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
            try { out.close(); } catch (Throwable ignored) {}
        }
    }

    private String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            byte[] buf = new byte[32768];
            for (;;) {
                int n = in.read(buf);
                if (n < 0) break;
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        byte[] d = md.digest();
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(String.format(Locale.US, "%02x", b & 0xff));
        return s.toString();
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) deleteRecursively(x);
        }
        try { f.delete(); } catch (Throwable ignored) {}
    }

    private void setLog(String s) {
        log.setText(s == null ? "" : s);
    }

    private void append(String s) {
        log.append(s);
    }
}
