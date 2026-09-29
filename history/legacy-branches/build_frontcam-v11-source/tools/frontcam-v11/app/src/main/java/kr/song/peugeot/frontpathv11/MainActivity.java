package kr.song.peugeot.frontpathv11;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class MainActivity extends Activity {
    private static final String ACTION_FRONT_CAMERA = "com.reglink.action.FrontCamera";
    private TextView report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 18, 24, 18);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("5008 Front Camera Path v11\nSOURCE BUILD / READ-FIRST TEST");
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        report = new TextView(this);
        report.setTextSize(17f);
        report.setTextIsSelectable(true);
        root.addView(report);

        Button refresh = new Button(this);
        refresh.setText("1. FrontCamera 액션 확인");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                inspectAction();
            }
        });
        root.addView(refresh);

        Button launch = new Button(this);
        launch.setText("2. FrontCamera 화면 실행 시험");
        launch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launchFrontCamera();
            }
        });
        root.addView(launch);

        TextView safety = new TextView(this);
        safety.setText("\n차량 CAN/SharedVar 설정 변경 없음.\n정차/P/주차브레이크 상태에서만 2번을 누르세요.");
        safety.setTextSize(15f);
        root.addView(safety);

        setContentView(scroll);
        inspectAction();
    }

    private void inspectAction() {
        StringBuilder out = new StringBuilder();
        out.append("action = ").append(ACTION_FRONT_CAMERA).append('\n');

        PackageManager pm = getPackageManager();
        Intent intent = new Intent(ACTION_FRONT_CAMERA);

        ResolveInfo defaultHandler = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        out.append("default handler = ");
        if (defaultHandler == null || defaultHandler.activityInfo == null) {
            out.append("(none)\n");
        } else {
            appendActivity(out, defaultHandler.activityInfo);
        }

        List<ResolveInfo> all = pm.queryIntentActivities(intent, 0);
        out.append("all handlers = ").append(all == null ? 0 : all.size()).append('\n');
        if (all != null) {
            for (int i = 0; i < all.size(); i++) {
                ActivityInfo ai = all.get(i).activityInfo;
                out.append("[").append(i).append("] ");
                if (ai == null) out.append("(no ActivityInfo)\n");
                else appendActivity(out, ai);
            }
        }

        out.append("\n이 단계는 조회만 수행합니다.");
        report.setText(out.toString());
    }

    private static void appendActivity(StringBuilder out, ActivityInfo ai) {
        out.append(ai.packageName).append('/').append(ai.name)
           .append(" exported=").append(ai.exported)
           .append(" permission=").append(ai.permission == null ? "(none)" : ai.permission)
           .append('\n');
    }

    private void launchFrontCamera() {
        String before = report.getText().toString();
        try {
            Intent intent = new Intent(ACTION_FRONT_CAMERA);
            startActivity(intent);
            report.setText(before + "\n\nLAUNCH: startActivity() returned normally.");
        } catch (ActivityNotFoundException e) {
            report.setText(before + "\n\nLAUNCH ERROR: ActivityNotFoundException\n" + e.toString());
        } catch (SecurityException e) {
            report.setText(before + "\n\nLAUNCH ERROR: SecurityException\n" + e.toString());
        } catch (Throwable t) {
            report.setText(before + "\n\nLAUNCH ERROR: " + t.getClass().getName() + "\n" + String.valueOf(t.getMessage()));
        }
    }
}
