package com.lr.campussign;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String PREFS = "lr_campus_prefs";
    public static final String CAMPUS_PACKAGE = "com.wisedu.cpdaily";
    public static final String CHANNEL_ID = "lr_campus_sign";

    private SharedPreferences prefs;
    private TextView statusText;
    private TextView timeText;
    private TextView diagnosticText;
    private TextView honorText;
    private Button recordButton;
    private EditText phoneInput;
    private Switch dailySwitch;
    private Switch smsSwitch;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable diagnosticTicker = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            uiHandler.postDelayed(this, 1000);
        }
    };

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
        uiHandler.removeCallbacks(diagnosticTicker);
        uiHandler.post(diagnosticTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(diagnosticTicker);
        if (phoneInput != null) {
            prefs.edit().putString("phone", phoneInput.getText().toString().trim()).apply();
        }
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
        TextView sub = text("荣耀 90 Pro 诊断版 · v0.3", 13, 0xDFFFFFFF, Typeface.NORMAL);
        sub.setPadding(0, dp(7), 0, 0);
        titleBox.addView(title);
        titleBox.addView(sub);
        hero.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(hero, matchWrap(0));

        LinearLayout statusCard = card(root, "运行状态");
        statusText = text("正在检查…", 17, Color.rgb(25, 35, 55), Typeface.BOLD);
        statusText.setPadding(0, dp(5), 0, dp(3));
        statusCard.addView(statusText);
        statusCard.addView(text("只在今日校园内记录/回放操作；密码输入不会保存。", 13,
                Color.rgb(105, 113, 132), Typeface.NORMAL));

        LinearLayout diagCard = card(root, "真机诊断 · 关键");
        diagnosticText = text("正在读取诊断状态…", 14, Color.rgb(43, 54, 82), Typeface.NORMAL);
        diagnosticText.setLineSpacing(0, 1.18f);
        diagCard.addView(diagnosticText);

        Button copyDiag = secondaryButton("复制诊断信息");
        copyDiag.setOnClickListener(v -> copyDiagnostics());
        diagCard.addView(copyDiag, buttonParams());

        Button clearDiag = secondaryButton("清空事件计数后重新测试");
        clearDiag.setOnClickListener(v -> {
            prefs.edit()
                    .putInt("event_count", 0)
                    .putInt("recorded_step_count", countSteps())
                    .remove("last_event_package")
                    .remove("last_event_type")
                    .remove("last_event_time")
                    .apply();
            refreshUi();
            Toast.makeText(this, "诊断计数已清空，现在去今日校园点几下再回来", Toast.LENGTH_LONG).show();
        });
        diagCard.addView(clearDiag, buttonParams());

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

        LinearLayout honorCard = card(root, "荣耀 90 Pro / MagicOS");
        honorText = text("正在检测设备…", 13, Color.rgb(71, 80, 104), Typeface.NORMAL);
        honorText.setLineSpacing(0, 1.16f);
        honorCard.addView(honorText);

        Button appSettings = secondaryButton("打开 LR 应用详情设置");
        appSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开应用详情设置", Toast.LENGTH_SHORT).show();
            }
        });
        honorCard.addView(appSettings, buttonParams());

        Button batterySettings = secondaryButton("打开电池优化设置");
        batterySettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                } catch (Exception ignored) {
                    Toast.makeText(this, "请在系统设置中搜索“电池优化”", Toast.LENGTH_SHORT).show();
                }
            }
        });
        honorCard.addView(batterySettings, buttonParams());

        LinearLayout scheduleCard = card(root, "每日定时");
        timeText = text("21:30", 30, Color.rgb(35, 64, 160), Typeface.BOLD);
        timeText.setGravity(Gravity.CENTER_HORIZONTAL);
        timeText.setPadding(0, dp(4), 0, dp(10));
        timeText.setOnClickListener(v -> pickTime());
        scheduleCard.addView(timeText, new LinearLayout.LayoutParams(-1, -2));
        scheduleCard.addView(text("点时间修改。到点若手机已解锁，会打开今日校园并开始回放。", 13,
                Color.rgb(105, 113, 132), Typeface.NORMAL));

        dailySwitch = new Switch(this);
        dailySwitch.setText("启用每日自动化");
        dailySwitch.setTextSize(16);
        dailySwitch.setPadding(0, dp(12), 0, 0);
        dailySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("daily_enabled", isChecked).apply();
            if (isChecked) {
                Scheduler.scheduleNext(this);
                requestExactAlarmPermissionIfNeeded();
            } else {
                Scheduler.cancel(this);
            }
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
            if (isChecked && Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 102);
            }
        });
        smsCard.addView(smsSwitch);
        TextView smsHint = text("到点若仍处于安全锁屏，软件不会保存或绕过锁屏密码；会发送一次提醒，解锁后继续待执行流程。",
                13, Color.rgb(105, 113, 132), Typeface.NORMAL);
        smsHint.setPadding(0, dp(7), 0, 0);
        smsCard.addView(smsHint);

        LinearLayout safeCard = card(root, "自动化规则");
        safeCard.addView(rule("✓", "优先识别控件 ID / 文字，识别不到再用相对坐标"));
        safeCard.addView(rule("✓", "今日校园未安装、页面变化或步骤失败会立即提醒"));
        safeCard.addView(rule("✓", "拍照、人脸、动态码等验证交给官方页面处理"));
        safeCard.addView(rule("!", "到最终“签到 / 提交 / 确认签到”时暂停，由你本人确认"));

        return scroll;
    }

    private void toggleRecording() {
        boolean recording = prefs.getBoolean("recording", false);
        if (!recording) {
            prefs.edit()
                    .putBoolean("recording", true)
                    .putBoolean("replaying", false)
                    .putString("steps", "[]")
                    .putInt("step_index", 0)
                    .putInt("recorded_step_count", 0)
                    .putInt("event_count", 0)
                    .remove("last_event_package")
                    .remove("last_event_type")
                    .remove("last_event_time")
                    .apply();
            Toast.makeText(this, "开始录制：请像平时一样操作，最后不要提交签到", Toast.LENGTH_LONG).show();
            launchCampus();
        } else {
            prefs.edit().putBoolean("recording", false).putInt("recorded_step_count", countSteps()).apply();
            Toast.makeText(this, "演示已保存，共 " + countSteps() + " 步", Toast.LENGTH_SHORT).show();
        }
        refreshUi();
    }

    private void startReplay(boolean test) {
        int stepCount = countSteps();
        if (stepCount <= 0) {
            Toast.makeText(this, "还没有录制到步骤。请先点“开始演示并录制”，操作几步后回来查看诊断信息。", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit()
                .putBoolean("recording", false)
                .putBoolean("replaying", true)
                .putBoolean("test_replay", test)
                .putInt("step_index", 0)
                .putInt("retry_count", 0)
                .apply();
        launchCampus();
        Toast.makeText(this, "已启动回放，共 " + stepCount + " 步", Toast.LENGTH_SHORT).show();
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
            String s = rec ? "● 正在录制你的操作"
                    : rep ? "● 正在回放流程"
                    : pending ? "● 等待解锁后继续"
                    : "● 已就绪";
            statusText.setText(s);
        }
        if (recordButton != null) recordButton.setText(rec ? "结束演示并保存" : "开始演示并录制");
        if (timeText != null) {
            timeText.setText(String.format(Locale.CHINA, "%02d:%02d",
                    prefs.getInt("hour", 21), prefs.getInt("minute", 30)));
        }
        if (dailySwitch != null) dailySwitch.setChecked(prefs.getBoolean("daily_enabled", false));
        if (smsSwitch != null) smsSwitch.setChecked(prefs.getBoolean("sms_enabled", false));
        if (diagnosticText != null) diagnosticText.setText(buildDiagnostics());
        if (honorText != null) honorText.setText(buildHonorHint());
    }

    private String buildDiagnostics() {
        boolean enabled = isAccessibilityEnabledInSystem();
        boolean connected = prefs.getBoolean("accessibility_connected", false);
        int events = prefs.getInt("event_count", 0);
        int steps = countSteps();
        int index = prefs.getInt("step_index", 0);
        String lastPkg = prefs.getString("last_event_package", "—");
        int lastType = prefs.getInt("last_event_type", 0);
        long lastTime = prefs.getLong("last_event_time", 0L);

        String eventName;
        if (lastType == 0) {
            eventName = "—";
        } else {
            try {
                eventName = AccessibilityEvent.eventTypeToString(lastType) + " (" + lastType + ")";
            } catch (Exception e) {
                eventName = String.valueOf(lastType);
            }
        }

        String time = lastTime <= 0 ? "—"
                : new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(lastTime));

        return "系统无障碍开关：" + (enabled ? "✅ 已开启" : "❌ 未开启")
                + "\n服务连接状态：" + (connected ? "✅ 已连接" : "❌ 未连接 / 可能被系统回收")
                + "\n收到事件总数：" + events
                + "\n已录制步骤数：" + steps
                + "\n当前回放进度：" + index + " / " + steps
                + "\n最近事件应用：" + lastPkg
                + "\n最近事件类型：" + eventName
                + "\n最近事件时间：" + time;
    }

    private String buildHonorHint() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String brand = Build.BRAND == null ? "" : Build.BRAND;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        boolean honor = maker.toLowerCase(Locale.ROOT).contains("honor")
                || brand.toLowerCase(Locale.ROOT).contains("honor");

        String device = "设备：" + maker + " " + brand + " " + model;
        if (!honor) {
            return device + "\n未识别为荣耀设备，但仍建议关闭本应用的电池优化。";
        }
        return device
                + "\n✅ 已识别荣耀 / MagicOS 环境"
                + "\n建议在系统设置里允许：自启动、关联启动、后台活动；关闭本应用电池优化。"
                + "\n再把 LR 放到最近任务中并加锁，避免息屏后被系统清理。"
                + "\n如果“系统无障碍已开启”但“服务连接状态未连接”，基本就是后台/无障碍服务被 MagicOS 回收。";
    }

    private boolean isAccessibilityEnabledInSystem() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String component = getPackageName() + "/" + AutomationService.class.getName();
            return enabled.toLowerCase(Locale.ROOT).contains(component.toLowerCase(Locale.ROOT))
                    || enabled.toLowerCase(Locale.ROOT).contains((getPackageName() + "/.AutomationService").toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    private int countSteps() {
        try {
            return new JSONArray(prefs.getString("steps", "[]")).length();
        } catch (Exception e) {
            return 0;
        }
    }

    private void copyDiagnostics() {
        String text = "LR 今日校园自动化诊断\n"
                + buildDiagnostics() + "\n"
                + buildHonorHint() + "\n"
                + "Android API：" + Build.VERSION.SDK_INT;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LR诊断信息", text));
        Toast.makeText(this, "诊断信息已复制，直接发给我即可", Toast.LENGTH_SHORT).show();
    }

    private void requestBasicPermissions() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "签到提醒",
                    NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("每日签到、锁屏和自动化异常提醒");
            c.enableVibration(true);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private void loadAvatar(ImageView view) {
        new Thread(() -> {
            try {
                Bitmap b = BitmapFactory.decodeStream(
                        new URL("https://avatars.githubusercontent.com/u/221054959?v=4").openStream());
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
