#!/usr/bin/env bash
# Before/after frame timing on one emulator: the same gestures on the baseline build and on this commit, alternated (base, head,
# base, head) so a slow patch of the runner does not land on one side only. Run by the perf job in .github/workflows/screens.yml.
# The emulator renders in software, so the numbers are far slower than a phone's; only the comparison between the two builds means
# anything.
set -u
PKG=com.bloo.bluelink.debug
APP=com.bloo.bluelink
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
adb shell wm size

fixture() { adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd "$1" >/dev/null; sleep 2; }

round() {
  local label=$1 apk=$2
  adb uninstall $PKG >/dev/null 2>&1
  adb install -r -g "$apk" >/dev/null
  adb shell am start -n $PKG/$APP.MainActivity >/dev/null; sleep 8
  fixture full; fixture dark
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/$APP.MainActivity >/dev/null
  sleep 30
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  for i in 1 2 3; do
    adb shell input swipe 900 600 180 600 2500; sleep 5
    adb shell input swipe 180 600 900 600 2500; sleep 5
  done
  for i in 1 2 3; do adb shell input swipe 540 1800 540 700 1800; sleep 4; done
  for i in 1 2 3; do adb shell input swipe 540 700 540 1800 1800; sleep 4; done
  echo "=====PERF $label"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th|Number Slow UI thread|Number Slow issue draw|Number Frame deadline missed" | sed 's/^ *//'
  # The app's own record of the same stretch (PerfMonitor): GPU and UI-thread time per frame where the device reports them.
  adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2
}

round base-1 /tmp/base.apk
round head-1 /tmp/head.apk
round base-2 /tmp/base.apk
round head-2 /tmp/head.apk
adb emu kill >/dev/null 2>&1
exit 0
