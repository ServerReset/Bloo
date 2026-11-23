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
# A quarter of the pixels: the software renderer otherwise takes 3-5 s a frame, past the top of gfxinfo's scale, so both builds
# read the same. The blur and glass cost scales with pixels, so the comparison still measures them.
adb shell wm size 540x1200
adb shell wm density 210
sleep 5
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
  # Exact per-frame times from the last 120 frames (framestats), not gfxinfo's coarse buckets.
  adb shell dumpsys gfxinfo $PKG framestats > /tmp/fs.txt
  python3 - "$label" <<'PY'
import sys, statistics
rows, head = [], None
for line in open("/tmp/fs.txt"):
    f = line.strip().rstrip(",").split(",")
    if f[0] == "Flags":
        head = f; continue
    if head and len(f) == len(head) and f[0].isdigit() and f[0] == "0":
        rows.append(dict(zip(head, map(int, f))))
def ms(a, b): return [(r[b] - r[a]) / 1e6 for r in rows if r.get(a, 0) > 0 and r.get(b, 0) > r.get(a, 0)]
total = ms("IntendedVsync", "FrameCompleted")
ui = ms("HandleInputStart", "SyncQueued") if rows and "HandleInputStart" in rows[0] else []
gpu = ms("IssueDrawCommandsStart", "GpuCompleted") if rows and "GpuCompleted" in rows[0] else []
def show(name, xs):
    if xs: print(f"PERFLINE {sys.argv[1]} {name} n={len(xs)} mean={statistics.mean(xs):.1f}ms median={statistics.median(xs):.1f}ms")
show("total", total); show("ui", ui); show("gpu", gpu)
PY
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
