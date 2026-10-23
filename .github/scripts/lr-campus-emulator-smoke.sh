#!/usr/bin/env bash
set -euo pipefail

APP_APK="apps/LR-Campus-Automation/project/app/build/outputs/apk/debug/app-debug.apk"
FIXTURE_APK="apps/LR-Campus-Automation/project/fixture-cpdaily/build/outputs/apk/debug/fixture-cpdaily-debug.apk"

adb install -r "$APP_APK"
adb install -r "$FIXTURE_APK"
adb shell pm grant com.lr.campussign android.permission.POST_NOTIFICATIONS || true

# Start LR first. Do NOT force-stop it after enabling accessibility: force-stop can tear down the service.
adb shell am start -n com.lr.campussign/.MainActivity >/dev/null
sleep 2

# One UI dump only to assert the screen renders. UiAutomation may temporarily displace accessibility,
# so enable LR's service AFTER this dump and never call uiautomator during record/replay.
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
grep -q 'LR—今日校园自动化签到' /tmp/window.xml

adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services com.lr.campussign/com.lr.campussign.AutomationService
adb shell settings put secure accessibility_enabled 1
sleep 3

echo '=== accessibility before recording ==='
adb shell settings get secure enabled_accessibility_services || true
adb shell dumpsys accessibility | grep -i -E 'campussign|AutomationService|enabled services|bound services' -C 3 || true
adb shell dumpsys accessibility | grep -q 'LR 今日校园自动化'

# LR's first button center on the deterministic Pixel_2 test profile.
echo 'Tap LR record button'
adb shell input tap 540 917
sleep 2

# Fake CPDaily uses the same button position on each page. These are raw input taps,
# so they do not register a UiAutomation accessibility service and cannot evict LR's service.
for label in 服务 签到入口 今晚签到任务; do
  echo "Tap fake CPDaily: $label"
  adb shell input tap 540 517
  sleep 1
 done

# Return to LR and stop recording using the same primary-button coordinate.
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

# Reset fake CPDaily to Home; restarting it calls showHome().
adb shell am force-stop com.wisedu.cpdaily

# Test replay button is the second button on the LR action card.
echo 'Tap LR test replay button'
adb shell input tap 540 1077

# Verify replay by reading the fake CPDaily's own state, not by using UiAutomator.
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
