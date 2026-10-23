package com.lr.campussign;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class AutomationService extends AccessibilityService {
    private static final String TAG = "LRAutomation";
    private android.content.SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRecordAt = 0L;
    private String lastRecordKey = "";
    private boolean replayLoopRunning = false;

    private final Runnable replayWatcher = new Runnable() {
        @Override
        public void run() {
            if (prefs == null) prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            if (prefs.getBoolean("replaying", false)) startReplayLoop(100);
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("accessibility_connected", true).apply();
        handler.removeCallbacks(replayWatcher);
        handler.post(replayWatcher);
        if (prefs.getBoolean("replaying", false)) startReplayLoop(100);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null) prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if (event == null) return;

        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        int eventCount = prefs.getInt("event_count", 0) + 1;
        prefs.edit()
                .putInt("event_count", eventCount)
                .putString("last_event_package", pkg)
                .putInt("last_event_type", event.getEventType())
                .putLong("last_event_time", System.currentTimeMillis())
                .apply();
        Log.i(TAG, "event #" + eventCount + " pkg=" + pkg + " type=" + event.getEventType()
                + " recording=" + prefs.getBoolean("recording", false));

        if (!MainActivity.CAMPUS_PACKAGE.equals(pkg)) return;

        if (prefs.getBoolean("recording", false) && !prefs.getBoolean("replaying", false)) {
            recordEvent(event);
        }
        if (prefs.getBoolean("replaying", false)) startReplayLoop(120);
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (prefs != null) prefs.edit().putBoolean("accessibility_connected", false).apply();
        handler.removeCallbacks(replayWatcher);
        super.onDestroy();
    }

    private void recordEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        AccessibilityNodeInfo node = event.getSource();
        try {
            if (node != null && node.isPassword()) return;
            JSONObject step = new JSONObject();
            String kind = type == AccessibilityEvent.TYPE_VIEW_CLICKED ? "click"
                    : type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ? "text" : "scroll";
            step.put("type", kind);
            step.put("viewId", node != null && node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
            step.put("text", safeText(node != null ? node.getText() : event.getText()));
            step.put("desc", node != null && node.getContentDescription() != null ? node.getContentDescription().toString() : "");
            Rect r = new Rect();
            if (node != null) node.getBoundsInScreen(r);
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            step.put("x", r.isEmpty() ? sw / 2 : r.centerX());
            step.put("y", r.isEmpty() ? sh / 2 : r.centerY());
            step.put("sw", sw);
            step.put("sh", sh);
            long now = System.currentTimeMillis();
            step.put("delay", lastRecordAt == 0L ? 700 : Math.min(3000, Math.max(250, now - lastRecordAt)));
            if ("text".equals(kind)) step.put("value", safeText(event.getText()));

            String key = kind + "|" + step.optString("viewId") + "|" + step.optString("text") + "|" + step.optString("desc");
            if (key.equals(lastRecordKey) && now - lastRecordAt < 450) return;
            lastRecordAt = now;
            lastRecordKey = key;

            JSONArray steps = new JSONArray(prefs.getString("steps", "[]"));
            if (steps.length() < 120) {
                steps.put(step);
                prefs.edit().putString("steps", steps.toString()).putInt("recorded_step_count", steps.length()).apply();
                Log.i(TAG, "recorded step " + steps.length() + ": " + step);
            }
        } catch (Exception e) {
            Log.e(TAG, "recordEvent failed", e);
        } finally {
            if (node != null) node.recycle();
        }
    }

    private String safeText(Object value) {
        if (value == null) return "";
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) return "";
            Object last = list.get(list.size() - 1);
            return last == null ? "" : last.toString();
        }
        return value.toString();
    }

    private void startReplayLoop(long delay) {
        if (replayLoopRunning) return;
        replayLoopRunning = true;
        handler.postDelayed(this::runNextStep, delay);
    }

    private void runNextStep() {
        if (!prefs.getBoolean("replaying", false)) {
            replayLoopRunning = false;
            return;
        }
        try {
            JSONArray steps = new JSONArray(prefs.getString("steps", "[]"));
            int index = prefs.getInt("step_index", 0);
            if (index >= steps.length()) {
                finishReplay("流程已走完", "LR 已完成录制路径导航，请检查当前页面。", false);
                return;
            }
            JSONObject step = steps.getJSONObject(index);
            if (looksLikeFinalSubmit(step)) {
                prefs.edit().putBoolean("replaying", false).putInt("step_index", index).putInt("retry_count", 0).apply();
                AlarmReceiver.notifyUser(this, "已到签到确认页", "自动化已暂停，请你本人检查定位/现场验证并确认最终签到。", true);
                replayLoopRunning = false;
                return;
            }

            long delay = Math.min(2400, Math.max(280, step.optLong("delay", 600)));
            handler.postDelayed(() -> executeStep(index, step), delay);
        } catch (Exception e) {
            finishReplay("自动化流程异常", "录制数据无法读取，请重新演示一次。", true);
        }
    }

    private void executeStep(int index, JSONObject step) {
        boolean ok = false;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                retryOrFail(index, step);
                return;
            }
            String type = step.optString("type", "click");
            AccessibilityNodeInfo target = findTarget(root, step);
            if ("click".equals(type)) {
                if (target != null) ok = clickNode(target);
                if (!ok) ok = clickByCoordinate(step);
            } else if ("text".equals(type)) {
                if (target != null && !target.isPassword()) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.optString("value", ""));
                    ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
            } else if ("scroll".equals(type)) {
                AccessibilityNodeInfo scroll = target != null ? target : root;
                ok = scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                if (!ok) ok = swipeUp();
            }
            if (target != null) target.recycle();
            root.recycle();
        } catch (Exception ignored) { }

        if (ok) {
            prefs.edit().putInt("step_index", index + 1).putInt("retry_count", 0).apply();
            handler.postDelayed(this::runNextStep, 250);
        } else {
            retryOrFail(index, step);
        }
    }

    private void retryOrFail(int index, JSONObject step) {
        int retries = prefs.getInt("retry_count", 0);
        if (retries < 2) {
            prefs.edit().putInt("retry_count", retries + 1).apply();
            handler.postDelayed(() -> executeStep(index, step), 900);
        } else {
            prefs.edit().putInt("retry_count", 0).apply();
            finishReplay("第 " + (index + 1) + " 步未识别", "今日校园页面可能变化了。请打开 LR 重新录制一次流程。", true);
        }
    }

    private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo root, JSONObject step) {
        String id = step.optString("viewId", "");
        if (!id.isEmpty()) {
            try {
                List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(id);
                if (byId != null && !byId.isEmpty()) return AccessibilityNodeInfo.obtain(byId.get(0));
            } catch (Exception ignored) { }
        }
        String text = step.optString("text", "").trim();
        if (!text.isEmpty() && text.length() <= 60) {
            try {
                List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText(text);
                if (byText != null && !byText.isEmpty()) return AccessibilityNodeInfo.obtain(byText.get(0));
            } catch (Exception ignored) { }
        }
        String desc = step.optString("desc", "").trim();
        if (!desc.isEmpty()) return findByDescription(root, desc);
        return null;
    }

    private AccessibilityNodeInfo findByDescription(AccessibilityNodeInfo node, String wanted) {
        if (node == null) return null;
        CharSequence d = node.getContentDescription();
        if (d != null && wanted.contentEquals(d)) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findByDescription(child, wanted);
            child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            AccessibilityNodeInfo parent = cur.getParent();
            if (cur != node) cur.recycle();
            cur = parent;
        }
        if (cur != null && cur != node) cur.recycle();
        return false;
    }

    private boolean clickByCoordinate(JSONObject step) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        int oldW = Math.max(1, step.optInt("sw", w));
        int oldH = Math.max(1, step.optInt("sh", h));
        float x = step.optInt("x", w / 2) * (w / (float) oldW);
        float y = step.optInt("y", h / 2) * (h / (float) oldH);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 80)).build();
        return dispatchGesture(g, null, null);
    }

    private boolean swipeUp() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        Path p = new Path();
        p.moveTo(w * 0.5f, h * 0.75f);
        p.lineTo(w * 0.5f, h * 0.30f);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 420)).build();
        return dispatchGesture(g, null, null);
    }

    private boolean looksLikeFinalSubmit(JSONObject step) {
        if (!"click".equals(step.optString("type", ""))) return false;
        String s = (step.optString("text", "") + " " + step.optString("desc", ""))
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return false;
        String[] exact = {"签到", "立即签到", "确认签到", "提交签到", "提交", "确认提交", "完成签到"};
        for (String k : exact) if (s.equals(k)) return true;
        return s.contains("确认签到") || s.contains("提交签到") || s.contains("立即签到") || s.contains("完成签到");
    }

    private void finishReplay(String title, String body, boolean alert) {
        prefs.edit().putBoolean("replaying", false).putInt("step_index", 0).putInt("retry_count", 0).apply();
        AlarmReceiver.notifyUser(this, title, body, alert);
        replayLoopRunning = false;
    }
}
