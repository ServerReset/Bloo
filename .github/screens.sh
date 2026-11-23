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
command -v ffmpeg >/dev/null || sudo apt-get install -y -qq ffmpeg >/dev/null

shot() {
  local name=$1
  alive "$name"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
  adb exec-out screencap -p > "/tmp/$name.png"
  convert "/tmp/$name.png" -resize 540x -quality 80 "/tmp/$name.jpg"
  echo "=====SHOT $name BEGIN"
  base64 -w 0 "/tmp/$name.jpg"
  echo
  echo "=====SHOT $name END"
}

# Taps the centre of the first on-screen node whose text or content description matches the regex [$1], or the point [$2 $3]
# when the screen cannot be read: uiautomator gives up waiting for an idle screen while the ambient background is still moving.
tap_node() {
  rm -f /tmp/ui.xml; adb shell rm -f /sdcard/ui.xml
  adb shell uiautomator dump /sdcard/ui.xml 2>&1 | tail -1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
pat = re.compile(sys.argv[1], re.I)
xml = open("/tmp/ui.xml", encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r' content-desc="([^"]*)"', n)
    if any(pat.search(x.group(1).strip()) for x in (t, d) if x):
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
  elif [ -n "${2:-}" ]; then
    echo "no node matches '$1': tapping $2 $3"
    adb shell input tap "$2" "$3"
  else
    echo "no node matches '$1'"
  fi
}

launch() {
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/$APP.MainActivity >/dev/null
  sleep "${1:-20}"
}

# The main thread's stack from the newest "not responding" trace, if there is one: what the app was stuck on.
anr_stack() {
  local f
  f=$(adb shell 'ls -t /data/anr/ 2>/dev/null | head -1' | tr -d '\r')
  [ -z "$f" ] && return
  echo "=====ANR TRACE $f"
  adb shell "cat /data/anr/$f" | awk '/^"main"/{p=1} p{print} /^$/{if(p) exit}' | head -60
}

# Every text and content description on screen, so a run shows what the taps could have matched.
labels() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || return
  echo "=====LABELS"
  python3 - <<'PY'
import re
xml = open("/tmp/ui.xml", encoding="utf-8", errors="ignore").read()
seen = []
for n in re.findall(r'<node [^>]*>', xml):
    for attr in ("text", "content-desc"):
        m = re.search(' %s="([^"]+)"' % attr, n)
        if m and m.group(1) not in seen: seen.append(m.group(1))
print(" | ".join(seen[:80]))
PY
}

# The same garage swipes with the system Battery Saver on, which runs the app lean (solid fills instead of live
# blur, plain fades instead of springs): the difference between the two timings is what the glass costs.
lean_pass() {
  adb shell cmd power set-mode 1
  launch 20
  alive lean
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  for i in 1 2 3 4 5 6; do
    adb shell input swipe 950 1200 130 1200 280; sleep 1.5
    adb shell input swipe 130 1200 950 1200 280; sleep 1.5
  done
  frames garage-swipes-lean
  shot 10-garage-lean
  adb shell cmd power set-mode 0
}

fixture() { adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd "$1" >/dev/null; sleep 2; }

frames() {
  echo "=====FRAMES $1"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|percentile|Number (Missed Vsync|High input latency|Slow UI thread|Slow bitmap uploads|Slow issue draw commands|Frame deadline missed)" | sed 's/^ *//'
}

alive() {
  if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
    echo "=====DEVICE LOST before: $1"
    echo "=====LOGCAT TAIL"
    grep -vE "chatty|netd|wifi|Wifi|bluetooth|Bluetooth|audio|Audio" /tmp/logcat.txt | tail -150
    adb emu kill >/dev/null 2>&1
    exit 0
  fi
}

# Let the freshly booted system settle (the launcher was still starting when the first run began), and close any
# "isn't responding" dialog it left behind.
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
# Root first: it restarts adbd, which would cut a logcat stream started before it.
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb logcat -c
(adb logcat -v brief > /tmp/logcat.txt 2>&1 &)

adb install -r -g "$APK"
adb shell wm size

# Brings the app back to the front if anything (a stray gesture, the system) sent it away.
front() {
  # mCurrentFocus names the window in front on every recent release (mResumedActivity is gone from Android 14's dumpsys).
  if ! adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | grep -q "$PKG"; then
    echo "app not in front before $1: relaunching"
    launch 15
  fi
}

# Swipes between cars across the car card at the top, well inside the screen: a swipe from near the edge is the system's back
# gesture, and one lower down can land on a slider, which takes it. Slow (2.5 s): the emulator draws a frame every few seconds, and
# a quick swipe ends before it has seen enough of the drag to move the pager.
next_car() { adb shell input swipe 900 600 180 600 2500; sleep 6; }
prev_car() { adb shell input swipe 180 600 900 600 2500; sleep 6; }
scroll_down() { adb shell input swipe 540 1800 540 700 1800; sleep 4; }
scroll_up() { adb shell input swipe 540 700 540 1800 1800; sleep 4; }

fixture full
fixture dark
launch 25
shot 01-garage-dark
anr_stack
labels

front scrolled; scroll_down
shot 02-garage-dark-scrolled
scroll_up

front second-car; next_car
shot 03-second-car-dark
prev_car

front climate; scroll_down; scroll_down; scroll_down
labels
shot 04-garage-dark-lower
scroll_down
shot 04b-garage-dark-lowest
tap_node '^Climate$'; sleep 4
shot 05-climate-open-dark
adb shell input keyevent KEYCODE_BACK; sleep 2

front map; launch 20
# The Location card is under Car info, near the bottom of the garage.
for i in 1 2 3 4 5 6 7 8 9 10; do scroll_down; done
shot 06b-location-card
labels
labels
shot 06a-garage-dark-bottom
# The scrolls do not land in the same place on every run; one back keeps the Location card on screen, for the map to grow out of.
scroll_up
shot 06c-before-open
# The map's open and close, recorded and cut into frames: at the emulator's speed each motion takes seconds, so the frames show its
# whole path rather than one moment.
adb shell "screenrecord --time-limit 40 /sdcard/map.mp4" &
rec=$!
sleep 2
adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd openmap >/dev/null; sleep 14
shot 06-map-dark
adb shell input keyevent KEYCODE_BACK; sleep 20
wait $rec
adb pull /sdcard/map.mp4 /tmp/map.mp4 >/dev/null 2>&1
if [ -s /tmp/map.mp4 ]; then
  ffmpeg -loglevel error -i /tmp/map.mp4 -vf "fps=1/2,scale=360:-1" /tmp/mapframe_%02d.jpg
  for f in /tmp/mapframe_*.jpg; do
    n=$(basename "$f" .jpg)
    echo "=====SHOT motion-$n BEGIN"; base64 -w 0 "$f"; echo; echo "=====SHOT motion-$n END"
  done
fi

# Settings is a page of the car pager, after the last car: two swipes on from the first (the pager does not wrap back to it).
front settings; launch 20
next_car; next_car
shot 07-settings-dark
scroll_down
shot 08-settings-dark-scrolled

# The light theme.
fixture light
launch 25
shot 09-garage-light
front light-second; next_car
shot 10-second-car-light

echo "=====CRASHES"
adb logcat -d -b crash | tail -60
echo "=====PERF LOG"
grep -E "BlooPerf|BlooStartup|Choreographer" /tmp/logcat.txt | tail -80
adb emu kill >/dev/null 2>&1
exit 0
