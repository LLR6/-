#!/usr/bin/env bash
set -euo pipefail

APP_APK="apps/LR-Campus-Automation/project/app/build/outputs/apk/debug/app-debug.apk"
FIXTURE_APK="apps/LR-Campus-Automation/project/fixture-cpdaily/build/outputs/apk/debug/fixture-cpdaily-debug.apk"

adb install -r "$APP_APK"
adb install -r "$FIXTURE_APK"
adb shell pm grant com.lr.campussign android.permission.POST_NOTIFICATIONS || true

adb shell am start -n com.lr.campussign/.MainActivity >/dev/null
sleep 2
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
grep -q 'LR—今日校园自动化签到' /tmp/window.xml

adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services com.lr.campussign/com.lr.campussign.AutomationService
adb shell settings put secure accessibility_enabled 1
sleep 3

adb shell dumpsys accessibility | grep -q 'LR 今日校园自动化'

echo 'Tap LR record button'
adb shell input tap 540 917
sleep 2
adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml | grep -q 'name="recording" value="true"'

# Drive the fake CPDaily with View.performClick() inside its own process.
# This emits genuine framework accessibility click events without registering UiAutomation.
for label in 服务 签到入口 今晚签到任务; do
  echo "performClick fake CPDaily: $label"
  adb shell am broadcast -a com.wisedu.cpdaily.TEST_CLICK -p com.wisedu.cpdaily >/dev/null
  sleep 1
  adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml || true
 done

adb shell am start -n com.lr.campussign/.MainActivity >/dev/null
sleep 1
adb shell input tap 540 917
sleep 1

adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml > /tmp/prefs.xml
cat /tmp/prefs.xml
grep -q '服务' /tmp/prefs.xml
grep -q '签到入口' /tmp/prefs.xml
grep -q '今晚签到任务' /tmp/prefs.xml

echo 'PASS: recording persisted expected click events.'
adb shell am force-stop com.wisedu.cpdaily

echo 'Tap LR test replay button'
adb shell input tap 540 1077

for _ in $(seq 1 30); do
  sleep 1
  if adb shell run-as com.wisedu.cpdaily cat shared_prefs/fixture_state.xml 2>/dev/null | grep -q 'final'; then
    echo 'PASS: replay reached the final sign page.'
    adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml || true
    adb shell run-as com.wisedu.cpdaily cat shared_prefs/fixture_state.xml || true
    exit 0
  fi
done

echo 'FAIL: replay did not reach the final sign page.'
adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml || true
adb shell run-as com.wisedu.cpdaily cat shared_prefs/fixture_state.xml || true
adb shell dumpsys accessibility | grep -i -E 'campussign|AutomationService|enabled services|bound services' -C 5 || true
exit 1
