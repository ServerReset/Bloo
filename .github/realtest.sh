#!/usr/bin/env bash
# The real-account test, on an emulator: sign in with the account in the BLOO_TEST_* secrets, lock the first car, restart the
# emulator, and run the restart sign-in, counting the prompts. Run by the realtest job in .github/workflows/screens.yml.
#
# The CI log is public, so this prints NO screenshots and NO app log: only the app's "BlooTest" lines (see DebugAccountTest), which
# carry states and counts with every car's name and VIN taken out.
set -u
PKG=com.bloo.bluelink.debug
APP=com.bloo.bluelink
DEVICE_PIN=1111
APK=$(ls app/build/outputs/apk/sideload/debug/*.apk | head -1)

if [ -z "${BLOO_TEST_USER:-}" ] || [ -z "${BLOO_TEST_PASS:-}" ] || [ -z "${BLOO_TEST_PIN:-}" ]; then
  echo "RESULT: the BLOO_TEST_USER, BLOO_TEST_PASS and BLOO_TEST_PIN secrets are not set; nothing to test."
  adb emu kill >/dev/null 2>&1; exit 0
fi
b64() { printf '%s' "$1" | base64 -w 0; }
U64=$(b64 "$BLOO_TEST_USER"); P64=$(b64 "$BLOO_TEST_PASS"); N64=$(b64 "$BLOO_TEST_PIN")
BRAND=${BLOO_TEST_BRAND:-HYUNDAI}

booted() {
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  sleep 40
  adb shell svc power stayon true
  adb shell input keyevent KEYCODE_WAKEUP
}
# Only the app's own test lines are captured.
capture() { adb logcat -c; (adb logcat -v time -s BlooTest:I > "/tmp/bt-$1.txt" 2>&1 &); }
fixture() { adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd "$@" >/dev/null; }
launch() { adb shell am force-stop $PKG; adb shell am start -n $PKG/$APP.MainActivity >/dev/null; sleep "${1:-25}"; }
# Waits up to $2 seconds for a test line matching $1 in capture $3.
await() { for _ in $(seq 1 "$2"); do grep -qE "$1" "/tmp/bt-$3.txt" 2>/dev/null && return 0; sleep 1; done; return 1; }
show() { echo "=====BLOOTEST $1"; sed 's/^[^ ]* [^ ]* //' "/tmp/bt-$1.txt" | grep -v "^---------" ; }
unlock_device() {
  adb shell input keyevent KEYCODE_WAKEUP; sleep 2
  adb shell input swipe 540 1800 540 600 400; sleep 2
  adb shell input text $DEVICE_PIN; adb shell input keyevent KEYCODE_ENTER; sleep 4
}

booted
adb shell locksettings set-pin $DEVICE_PIN
adb install -r -g "$APK" >/dev/null
capture first
launch 25
fixture off
echo "--- signing in"
fixture testlogin --es user64 "$U64" --es password64 "$P64" --es pin64 "$N64" --es brand "$BRAND"
if await 'screen=Garage .*cars=[1-9]' 150 first; then echo "RESULT: signed in, garage loaded"; else echo "RESULT: the garage did not load"; fi
echo "--- app lock on"
fixture testapplock; sleep 5
echo "--- locking the car"
fixture testlockcar
sleep 5
if await 'pending=\[\] ' 150 first; then echo "RESULT: the lock command finished"; fi
sleep 10
show first

echo "--- restarting the emulator"
adb reboot
booted
capture restart
unlock_device
launch 30
if await 'login form: fingerprint offered=false bootReauth=true' 30 restart; then
  echo "RESULT: the restart sign-in form offers no fingerprint"
else
  echo "RESULT: the restart sign-in form was not seen without a fingerprint"
fi
echo "--- restart sign-in"
fixture testreauth --es user64 "$U64" --es password64 "$P64" --es pin64 "$N64" --es brand "$BRAND"
if await 'prompt: shown' 60 restart; then
  sleep 6
  adb shell input text $DEVICE_PIN; adb shell input keyevent KEYCODE_ENTER
fi
await 'screen=Garage .*locked=false' 150 restart
# Long enough for a second prompt (the app lock) to appear if it were going to.
sleep 45
show restart
shown=$(grep -c "prompt: shown" /tmp/bt-restart.txt)
echo "RESULT: prompts shown after the restart sign-in: $shown"
if grep -qE 'screen=Garage .*locked=false' /tmp/bt-restart.txt; then echo "RESULT: the app is open after the restart sign-in"; else echo "RESULT: the app did not open after the restart sign-in"; fi
adb emu kill >/dev/null 2>&1
exit 0
