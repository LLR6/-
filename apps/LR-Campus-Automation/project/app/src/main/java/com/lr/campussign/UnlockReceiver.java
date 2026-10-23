package com.lr.campussign;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class UnlockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("pending_unlock", false)) return;
        p.edit().putBoolean("pending_unlock", false).putBoolean("recording", false)
                .putBoolean("replaying", true).putBoolean("test_replay", false).putInt("step_index", 0).apply();
        if (!AlarmReceiver.launchCampus(context)) {
            AlarmReceiver.notifyUser(context, "签到待继续", "已解锁，但没有找到今日校园，请手动打开。", true);
        }
    }
}
