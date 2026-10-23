package com.wisedu.cpdaily;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private void markStage(String stage) {
        getSharedPreferences("fixture_state", MODE_PRIVATE).edit().putString("stage", stage).apply();
    }

    private void base(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(80), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(24);
        h.setTextColor(Color.rgb(35, 45, 70));
        h.setGravity(Gravity.CENTER);
        root.addView(h, new LinearLayout.LayoutParams(-1, dp(70)));
        setContentView(root);
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.topMargin = dp(18);
        root.addView(b, p);
        return b;
    }

    private void showHome() {
        markStage("home");
        base("今日校园 · 自动化测试页面");
        button("服务", v -> showServices());
    }

    private void showServices() {
        markStage("services");
        base("服务大厅");
        button("签到入口", v -> showSignList());
    }

    private void showSignList() {
        markStage("list");
        base("签到任务");
        button("今晚签到任务", v -> showFinal());
    }

    private void showFinal() {
        markStage("final");
        base("晚间签到");
        TextView info = new TextView(this);
        info.setText("模拟定位已完成 · 等待最终确认");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        root.addView(info, new LinearLayout.LayoutParams(-1, dp(60)));
        button("签到", v -> {
            markStage("submitted");
            TextView done = new TextView(this);
            done.setText("签到成功（测试）");
            done.setTextSize(20);
            done.setTextColor(Color.rgb(20, 130, 70));
            done.setGravity(Gravity.CENTER);
            root.addView(done, new LinearLayout.LayoutParams(-1, dp(70)));
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
