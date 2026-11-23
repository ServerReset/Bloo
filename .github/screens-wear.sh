#!/usr/bin/env bash
# Captures the watch app's debug build on a Wear OS emulator, with sample cars (see WearDebugFixturesReceiver), for checking its look.
# Pictures go into the job log as base64 JPEGs between "=====SHOT <name> BEGIN" and "=====SHOT <name> END", as in screens.sh.
set -u

PKG=com.bloo.bluelink
APK=$(ls wear/build/outputs/apk/sideload/debug/*.apk | head -1)
command -v convert >/dev/null || sudo apt-get install -y -qq imagemagick >/dev/null

shot() {
  local name=$1
  adb exec-out screencap -p > "/tmp/$name.png"
  convert "/tmp/$name.png" -resize 360x -quality 85 "/tmp/$name.jpg"
  echo "=====SHOT $name BEGIN"
  base64 -w 0 "/tmp/$name.jpg"
  echo
  echo "=====SHOT $name END"
}

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 45
adb install -r -g "$APK"
read -r W H <<<"$(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')"
echo "screen $W x $H"
cx=$((W / 2)); cy=$((H / 2))

adb shell am broadcast -n $PKG/$PKG.wear.WearDebugFixturesReceiver >/dev/null
sleep 3
adb shell am start -n $PKG/$PKG.wear.WearMainActivity >/dev/null
sleep 25
shot w01-car
# The car page scrolls down to its controls (slow: the emulator draws in software).
adb shell input swipe $cx $((H * 3 / 4)) $cx $((H / 4)) 2000; sleep 5
shot w02-car-scrolled
adb shell input swipe $cx $((H / 4)) $cx $((H * 3 / 4)) 2000; sleep 5
# The next car.
adb shell input swipe $((W * 7 / 8)) $cy $((W / 8)) $cy 2000; sleep 6
shot w03-second-car
adb shell input swipe $cx $((H * 3 / 4)) $cx $((H / 4)) 2000; sleep 5
shot w04-second-car-scrolled

echo "=====CRASHES"
adb logcat -d -b crash | tail -40
adb emu kill >/dev/null 2>&1
exit 0
