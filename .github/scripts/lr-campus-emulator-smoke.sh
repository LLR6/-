#!/usr/bin/env bash
set -euo pipefail

APP_APK="apps/LR-Campus-Automation/project/app/build/outputs/apk/debug/app-debug.apk"
FIXTURE_APK="apps/LR-Campus-Automation/project/fixture-cpdaily/build/outputs/apk/debug/fixture-cpdaily-debug.apk"

adb install -r "$APP_APK"
adb install -r "$FIXTURE_APK"
adb shell pm grant com.lr.campussign android.permission.POST_NOTIFICATIONS || true

# Enable LR accessibility service in the emulator and verify Android really binds it.
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services com.lr.campussign/com.lr.campussign.AutomationService
adb shell settings put secure accessibility_enabled 1
sleep 3

echo '=== accessibility settings ==='
adb shell settings get secure enabled_accessibility_services || true
adb shell settings get secure accessibility_enabled || true
echo '=== accessibility dumpsys ==='
adb shell dumpsys accessibility | grep -i -E 'campussign|AutomationService|enabled services|bound services' -C 3 || true

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
}

click_text() {
  local wanted="$1"
  for _ in $(seq 1 15); do
    dump_ui
    coords=$(WANTED="$wanted" python3 -c 'import os,re,xml.etree.ElementTree as ET; w=os.environ["WANTED"]; root=ET.parse("/tmp/window.xml").getroot(); n=next((x for x in root.iter("node") if x.attrib.get("text","")==w),None); m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds","")) if n is not None else None; print(f"{(int(m.group(1))+int(m.group(3)))//2} {(int(m.group(2))+int(m.group(4)))//2}" if m else "")')
    if [ -n "$coords" ]; then
      echo "Tap '$wanted' at $coords"
      adb shell input tap $coords
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Could not find UI text: $wanted"
  cat /tmp/window.xml || true
  return 1
}

adb shell am force-stop com.lr.campussign
adb shell am force-stop com.wisedu.cpdaily
adb shell am start -n com.lr.campussign/.MainActivity >/dev/null
sleep 2

dump_ui
grep -q 'LR—今日校园自动化签到' /tmp/window.xml

click_text '开始演示并录制'
sleep 1
echo '=== after start recording ==='
adb shell dumpsys accessibility | grep -i -E 'campussign|AutomationService|enabled services|bound services' -C 3 || true
click_text '服务'
click_text '签到入口'
click_text '今晚签到任务'

adb shell am start -n com.lr.campussign/.MainActivity >/dev/null
sleep 1
click_text '结束演示并保存'

adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml > /tmp/prefs.xml
cat /tmp/prefs.xml
if ! grep -q '服务' /tmp/prefs.xml; then
  echo 'FAIL: recording stored no expected accessibility click events.'
  echo '=== final accessibility dumpsys ==='
  adb shell dumpsys accessibility | grep -i -E 'campussign|AutomationService|enabled services|bound services' -C 5 || true
  adb shell logcat -d -t 300 | grep -i -E 'campussign|AutomationService|AccessibilityService' || true
  exit 1
fi
grep -q '签到入口' /tmp/prefs.xml
grep -q '今晚签到任务' /tmp/prefs.xml

adb shell am force-stop com.wisedu.cpdaily
click_text '测试回放（最终签到前停下）'

for _ in $(seq 1 25); do
  sleep 1
  dump_ui
  if grep -q '晚间签到' /tmp/window.xml && grep -q '模拟定位已完成' /tmp/window.xml; then
    echo 'PASS: replay reached the final sign page.'
    adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml || true
    exit 0
  fi
done

echo 'FAIL: replay did not reach the final sign page.'
adb shell run-as com.lr.campussign cat shared_prefs/lr_campus_prefs.xml || true
cat /tmp/window.xml || true
exit 1
