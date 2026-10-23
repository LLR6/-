package com.lr.campussign;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.telephony.SmsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        try {
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean locked = km.isDeviceLocked() || !pm.isInteractive();
            if (locked) {
                p.edit().putBoolean("pending_unlock", true).putBoolean("replaying", false).apply();
                sendSmsOnceIfAllowed(context, p);
                notifyUser(context, "该签到啦", "手机目前处于锁屏状态，解锁后 LR 会继续打开今日校园。", true);
            } else {
                p.edit().putBoolean("pending_unlock", false).putBoolean("recording", false)
                        .putBoolean("replaying", true).putBoolean("test_replay", false).putInt("step_index", 0).apply();
                if (!launchCampus(context)) {
                    notifyUser(context, "无法打开今日校园", "请确认今日校园已安装，然后点此手动签到。", true);
                }
            }
        } finally {
            Scheduler.scheduleNext(context);
        }
    }

    static boolean launchCampus(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(MainActivity.CAMPUS_PACKAGE);
        if (launch == null) return false;
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void notifyUser(Context context, String title, String body, boolean sound) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 40, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        if (!sound) {
            b.setSound(null);
            b.setVibrate(new long[]{0L});
        }
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2401, b.build());
    }

    private static void sendSmsOnceIfAllowed(Context context, SharedPreferences p) {
        if (!p.getBoolean("sms_enabled", false)) return;
        String phone = p.getString("phone", "");
        if (phone == null || phone.trim().isEmpty()) return;
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        if (today.equals(p.getString("last_sms_day", ""))) return;
        try {
            SmsManager.getDefault().sendTextMessage(phone.trim(), null,
                    "LR提醒：今日校园签到时间到了。手机当前锁屏，请解锁后完成签到。", null, null);
            p.edit().putString("last_sms_day", today).apply();
        } catch (Exception ignored) { }
    }
}
