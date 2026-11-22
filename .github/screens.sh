#!/usr/bin/env bash
# Captures the debug build's sample garage on an emulator, for checking the app's look and motion without a
# real account (see DebugSampleGarage's "full" mode). Run by .github/workflows/screens.yml.
#
# Each screenshot is printed into the job log as a downscaled JPEG in base64, between
# "=====SHOT <name> BEGIN" and "=====SHOT <name> END", so it can be read back from the log alone. Frame
# timing from `dumpsys gfxinfo` is printed around the garage swipes. The emulator renders in software
# (SwiftShader), so the frame numbers are slower than a phone's: compare runs with each other, not with a device.
set -u

PKG=com.bloo.bluelink.debug
APP=com.bloo.bluelink
APK=$(ls app/build/outputs/apk/sideload/debug/*.apk | head -1)
command -v convert >/dev/null || sudo apt-get install -y -qq imagemagick >/dev/null

shot() {
  local name=$1
  adb exec-out screencap -p > "/tmp/$name.png"
  convert "/tmp/$name.png" -resize 540x -quality 80 "/tmp/$name.jpg"
  echo "=====SHOT $name BEGIN"
  base64 -w 0 "/tmp/$name.jpg"
  echo
  echo "=====SHOT $name END"
}

# Taps the centre of the first on-screen node whose text or content description matches the regex [$1].
tap_node() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
pat = re.compile(sys.argv[1], re.I)
xml = open("/tmp/ui.xml", encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r' content-desc="([^"]*)"', n)
    label = (t.group(1) if t else "") + " " + (d.group(1) if d else "")
    if pat.search(label):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x0, y0, x1, y1 = map(int, b.groups())
            print((x0 + x1) // 2, (y0 + y1) // 2)
            break
PY
)
  if [ -n "$xy" ]; then
    echo "tap '$1' at $xy"
    adb shell input tap $xy
  else
    echo "no node matches '$1'"
  fi
}

launch() {
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/$APP.MainActivity >/dev/null
  sleep "${1:-20}"
}

fixture() { adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd "$1" >/dev/null; sleep 2; }

frames() {
  echo "=====FRAMES $1"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|percentile|Number (Missed Vsync|High input latency|Slow UI thread|Slow bitmap uploads|Slow issue draw commands|Frame deadline missed)" | sed 's/^ *//'
}

adb install -r -g "$APK"
adb shell wm size
fixture full
fixture dark
launch 25
shot 01-garage-dark

# The card list, scrolled.
adb shell input swipe 540 1900 540 800 500; sleep 3
shot 02-garage-dark-scrolled
adb shell input swipe 540 800 540 1900 500; sleep 3

# Swiping between the two cars, timed.
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in 1 2 3 4 5 6; do
  adb shell input swipe 950 1200 130 1200 280; sleep 1.5
  adb shell input swipe 130 1200 950 1200 280; sleep 1.5
done
frames garage-swipes
adb shell input swipe 950 1200 130 1200 280; sleep 3
shot 03-second-car-dark
adb shell input swipe 130 1200 950 1200 280; sleep 3

# The climate card, opened.
tap_node '^ *Climate *$'; sleep 3
shot 04-climate-open-dark
adb shell input keyevent KEYCODE_BACK; sleep 2

# The map, opened from the location card.
adb shell input swipe 540 1900 540 900 500; sleep 2
tap_node 'map'; sleep 4
shot 05-map-dark
adb shell dumpsys gfxinfo $PKG reset >/dev/null
adb shell input swipe 300 1400 800 900 400; sleep 1
adb shell input swipe 800 900 300 1400 400; sleep 1
frames map-pan
adb shell input keyevent KEYCODE_BACK; sleep 2

# Settings.
launch 15
tap_node '^ *Settings *$'; sleep 4
shot 06-settings-dark
adb shell input swipe 540 1900 540 700 500; sleep 3
shot 07-settings-dark-scrolled

# The light theme.
fixture light
launch 20
shot 08-garage-light
tap_node '^ *Climate *$'; sleep 3
shot 09-climate-open-light

echo "=====CRASHES"
adb logcat -d -b crash | tail -60
echo "=====PERF LOG"
adb logcat -d | grep -E "BlooPerf|BlooStartup|Choreographer" | tail -80
