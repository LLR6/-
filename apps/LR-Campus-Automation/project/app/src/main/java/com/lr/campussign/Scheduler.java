package com.lr.campussign;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public final class Scheduler {
    private Scheduler() {}

    private static PendingIntent alarmIntent(Context context) {
        Intent i = new Intent(context, AlarmReceiver.class).setAction("com.lr.campussign.DAILY_SIGN");
        return PendingIntent.getBroadcast(context, 9001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleNext(Context context) {
        SharedPreferences p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("daily_enabled", false)) return;
        int hour = p.getInt("hour", 21);
        int minute = p.getInt("minute", 30);
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(context);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarmIntent(context));
    }
}
