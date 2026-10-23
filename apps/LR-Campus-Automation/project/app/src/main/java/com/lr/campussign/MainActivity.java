package com.lr.campussign;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String PREFS = "lr_campus_prefs";
    public static final String CAMPUS_PACKAGE = "com.wisedu.cpdaily";
    public static final String CHANNEL_ID = "lr_campus_sign";
    private SharedPreferences prefs;
    private TextView statusText;
    private TextView timeText;
    private Button recordButton;
    private EditText phoneInput;
    private Switch dailySwitch;
    private Switch smsSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        requestBasicPermissions();
        setContentView(buildUi());
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusText != null) refreshUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (phoneInput != null) prefs.edit().putString("phone", phoneInput.getText().toString().trim()).apply();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(20), dp(18), dp(20));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(46, 92, 255), Color.rgb(111, 75, 255)});
        heroBg.setCornerRadius(dp(24));
        hero.setBackground(heroBg);

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(Color.WHITE);
        avatarBg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBg);
        avatar.setClipToOutline(true);
        hero.addView(avatar, new LinearLayout.LayoutParams(dp(70), dp(70)));
        loadAvatar(avatar);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(16), 0, 0, 0);
        TextView title = text("LR—今日校园自动化签到", 21, Color.WHITE, Typeface.BOLD);
        TextView sub = text("录一次 · 每晚自动导航 · 锁屏提醒", 13, 0xDFFFFFFF, Typeface.NORMAL);
        sub.setPadding(0, dp(7), 0, 0);
        titleBox.addView(title);
        titleBox.addView(sub);
        hero.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(hero, matchWrap(dp(0)));

        LinearLayout statusCard = card(root, "运行状态");
        statusText = text("正在检查…", 17, Color.rgb(25, 35, 55), Typeface.BOLD);
        statusText.setPadding(0, dp(5), 0, dp(3));
        statusCard.addView(statusText);
        TextView tip = text("只在今日校园内回放已录制操作；密码不会记录。", 13, Color.rgb(105, 113, 132), Typeface.NORMAL);
        statusCard.addView(tip);

        LinearLayout actionCard = card(root, "操作流程");
        recordButton = primaryButton("开始演示并录制");
        recordButton.setOnClickListener(v -> toggleRecording());
        actionCard.addView(recordButton, buttonParams());

        Button test = secondaryButton("测试回放（最终签到前停下）");
        test.setOnClickListener(v -> startReplay(true));
        actionCard.addView(test, buttonParams());

        Button open = secondaryButton("打开今日校园");
        open.setOnClickListener(v -> launchCampus());
        actionCard.addView(open, buttonParams());

        Button access = secondaryButton("开启 / 检查无障碍权限");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        actionCard.addView(access, buttonParams());

        LinearLayout scheduleCard = card(root, "每日定时");
        timeText = text("21:30", 30, Color.rgb(35, 64, 160), Typeface.BOLD);
        timeText.setGravity(Gravity.CENTER_HORIZONTAL);
        timeText.setPadding(0, dp(4), 0, dp(10));
        timeText.setOnClickListener(v -> pickTime());
        scheduleCard.addView(timeText, new LinearLayout.LayoutParams(-1, -2));
        TextView timeHint = text("点时间修改。到点若手机已解锁，会自动打开今日校园并开始回放。", 13,
                Color.rgb(105, 113, 132), Typeface.NORMAL);
        scheduleCard.addView(timeHint);
        dailySwitch = new Switch(this);
        dailySwitch.setText("启用每日自动化");
        dailySwitch.setTextSize(16);
        dailySwitch.setPadding(0, dp(12), 0, 0);
        dailySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("daily_enabled", isChecked).apply();
            if (isChecked) {
                Scheduler.scheduleNext(this);
                requestExactAlarmPermissionIfNeeded();
            } else Scheduler.cancel(this);
            refreshUi();
        });
        scheduleCard.addView(dailySwitch);

        LinearLayout smsCard = card(root, "锁屏提醒");
        phoneInput = new EditText(this);
        phoneInput.setHint("接收提醒的手机号");
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phoneInput.setText(prefs.getString("phone", ""));
        phoneInput.setTextSize(16);
        smsCard.addView(phoneInput, new LinearLayout.LayoutParams(-1, dp(54)));
        smsSwitch = new Switch(this);
        smsSwitch.setText("锁屏时短信提醒（通知始终兜底）");
        smsSwitch.setTextSize(15);
        smsSwitch.setPadding(0, dp(7), 0, 0);
        smsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("sms_enabled", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 102);
            }
        });
        smsCard.addView(smsSwitch);
        TextView smsHint = text("如果到点仍处于安全锁屏，软件不会保存或绕过锁屏密码；会发送一次提醒，解锁后继续待执行流程。",
                13, Color.rgb(105, 113, 132), Typeface.NORMAL);
        smsHint.setPadding(0, dp(7), 0, 0);
        smsCard.addView(smsHint);

        LinearLayout safeCard = card(root, "自动化规则");
        safeCard.addView(rule("✓", "优先识别控件 ID / 文字，识别不到再用相对坐标"));
        safeCard.addView(rule("✓", "今日校园未安装、页面变化或步骤失败会立即提醒"));
        safeCard.addView(rule("✓", "拍照、人脸、动态码等现场验证交给官方页面处理"));
        safeCard.addView(rule("!", "到最终“签到 / 提交 / 确认签到”时暂停，由你本人确认"));

        return scroll;
    }

    private void toggleRecording() {
        boolean recording = prefs.getBoolean("recording", false);
        if (!recording) {
            prefs.edit().putBoolean("recording", true).putBoolean("replaying", false)
                    .putString("steps", "[]").putInt("step_index", 0).apply();
            Toast.makeText(this, "开始录制：请像平时一样操作，最后不要提交签到", Toast.LENGTH_LONG).show();
            launchCampus();
        } else {
            prefs.edit().putBoolean("recording", false).apply();
            Toast.makeText(this, "演示已保存", Toast.LENGTH_SHORT).show();
        }
        refreshUi();
    }

    private void startReplay(boolean test) {
        String steps = prefs.getString("steps", "[]");
        if (steps == null || steps.length() <= 2) {
            Toast.makeText(this, "还没有录制流程，请先演示一次", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putBoolean("recording", false).putBoolean("replaying", true)
                .putBoolean("test_replay", test).putInt("step_index", 0).apply();
        launchCampus();
    }

    public void launchCampus() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(CAMPUS_PACKAGE);
        if (intent == null) {
            Toast.makeText(this, "没有找到今日校园（" + CAMPUS_PACKAGE + "）", Toast.LENGTH_LONG).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void pickTime() {
        int h = prefs.getInt("hour", 21), m = prefs.getInt("minute", 30);
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            prefs.edit().putInt("hour", hourOfDay).putInt("minute", minute).apply();
            if (prefs.getBoolean("daily_enabled", false)) Scheduler.scheduleNext(this);
            refreshUi();
        }, h, m, true).show();
    }

    private void refreshUi() {
        boolean rec = prefs.getBoolean("recording", false);
        boolean rep = prefs.getBoolean("replaying", false);
        boolean pending = prefs.getBoolean("pending_unlock", false);
        if (statusText != null) {
            String s = rec ? "● 正在录制你的操作" : rep ? "● 正在回放流程" : pending ? "● 等待解锁后继续" : "● 已就绪";
            statusText.setText(s);
        }
        if (recordButton != null) recordButton.setText(rec ? "结束演示并保存" : "开始演示并录制");
        if (timeText != null) timeText.setText(String.format(Locale.CHINA, "%02d:%02d", prefs.getInt("hour", 21), prefs.getInt("minute", 30)));
        if (dailySwitch != null) dailySwitch.setChecked(prefs.getBoolean("daily_enabled", false));
        if (smsSwitch != null) smsSwitch.setChecked(prefs.getBoolean("sms_enabled", false));
    }

    private void requestBasicPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "签到提醒", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("每日签到、锁屏和自动化异常提醒");
            c.enableVibration(true);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private void loadAvatar(ImageView view) {
        new Thread(() -> {
            try {
                Bitmap b = BitmapFactory.decodeStream(new URL("https://avatars.githubusercontent.com/u/221054959?v=4").openStream());
                runOnUiThread(() -> view.setImageBitmap(b));
            } catch (Exception ignored) { }
        }).start();
    }

    private LinearLayout card(LinearLayout root, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(229, 233, 242));
        card.setBackground(bg);
        TextView t = text(title, 15, Color.rgb(85, 94, 118), Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(7));
        card.addView(t);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(14);
        root.addView(card, p);
        return card;
    }

    private TextView rule(String mark, String content) {
        TextView v = text(mark + "  " + content, 14, Color.rgb(62, 72, 96), Typeface.NORMAL);
        v.setPadding(0, dp(5), 0, dp(5));
        return v;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(46, 92, 255), Color.rgb(106, 73, 245)});
        bg.setCornerRadius(dp(15));
        b.setBackground(bg);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(45, 67, 130));
        b.setTextSize(14);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(243, 246, 255));
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), Color.rgb(214, 222, 246));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(9);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = top;
        return p;
    }

    private TextView text(String s, float sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
