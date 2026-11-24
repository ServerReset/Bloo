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
  # Root, for a hard kill of the app in the AutoLock test (its foreground watcher keeps "am kill" from touching it). Before any
  # logcat capture starts: it restarts adbd.
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell svc power stayon true
  adb shell input keyevent KEYCODE_WAKEUP
}
# Only the app's own test lines are captured.
capture() { adb logcat -c; (adb logcat -v time -s BlooTest:I > "/tmp/bt-$1.txt" 2>&1 &); }
fixture() { adb shell am broadcast -n $PKG/$APP.DebugFixturesReceiver --es cmd "$@" >/dev/null; }
launch() { adb shell am force-stop $PKG; adb shell am start -n $PKG/$APP.MainActivity >/dev/null; sleep "${1:-25}"; }
# Waits up to $2 seconds for a test line matching $1 in capture $3.
await() { for _ in $(seq 1 "$2"); do grep -qE "$1" "/tmp/bt-$3.txt" 2>/dev/null && return 0; sleep 1; done; return 1; }
# Any VIN-shaped run (17 letters and digits) is taken out here too, whatever the app wrote.
show() { echo "=====BLOOTEST $1"; sed 's/^[^ ]* [^ ]* //; s/[A-HJ-NPR-Z0-9]\{17\}/<vin>/g' "/tmp/bt-$1.txt" | grep -v "^---------" ; }
# Unlocks the emulator's lock screen with its PIN, until Android reports the user's storage unlocked (an app cannot start before).
unlock_device() {
  for attempt in 1 2 3 4; do
    adb shell input keyevent KEYCODE_WAKEUP; sleep 2
    adb shell input keyevent KEYCODE_MENU; sleep 2
    adb shell input swipe 540 1900 540 500 300; sleep 3
    adb shell input text $DEVICE_PIN; sleep 1
    adb shell input keyevent KEYCODE_ENTER; sleep 6
    if adb shell dumpsys user | grep -q "RUNNING_UNLOCKED"; then echo "device unlocked (attempt $attempt)"; return 0; fi
  done
  echo "device still locked:"; adb shell dumpsys user | grep -E "State:|RUNNING" | head -3
  return 1
}
device_state() {
  echo "device: $(adb shell dumpsys user | grep -oE 'RUNNING_[A-Z_]+' | head -1), app pid: $(adb shell pidof $PKG | tr -d '\r'), focus: $(adb shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{[^ ]* [^ ]* //; s/}.*//')"
}

booted
adb shell locksettings set-pin $DEVICE_PIN
adb install -r -g "$APK" >/dev/null
capture first
launch 25
fixture off
echo "--- signing in"
fixture testlogin --es user64 "$U64" --es password64 "$P64" --es pin64 "$N64" --es brand "$BRAND"
if await 'cars=[1-9]' 150 first; then echo "RESULT: signed in, cars loaded"; else echo "RESULT: the cars did not load"; fi
# Past the first-run pages, as someone who has used the app would be, so the restart lands on the garage.
fixture testonboarded
if await 'screen=Garage .*cars=[1-9]' 60 first; then echo "RESULT: garage shown"; else echo "RESULT: the garage was not shown"; fi
echo "--- app lock on"
fixture testapplock; sleep 5
# The car command runs only when BLOO_TEST_COMMAND=lock is set: the restart sign-in is what this run checks.
if [ "${BLOO_TEST_COMMAND:-}" = "lock" ]; then
  echo "--- locking the car"
  fixture testlockcar
  sleep 5
  if await 'pending=\[\] ' 150 first; then echo "RESULT: the lock command finished"; fi
fi
sleep 10

# AutoLock, end to end, in DRY RUN (it reads the real car's status but never sends a command): the disconnect, the walk, the
# countdown, the status check and the decision; the watcher staying up afterwards; a departure surviving the app being killed
# before the walk; and turning AutoLock off without a crash.
DBG="adb shell am broadcast -n $PKG/$APP.autolock.AutoLockDebugReceiver --es cmd"
svc_up() { adb shell dumpsys activity services "$PKG/$APP.autolock.AutoLockService" 2>/dev/null | grep -c "ServiceRecord"; }
count() { grep -cE "$1" "/tmp/bt-$2.txt" 2>/dev/null; }
# Waits up to $3 seconds for more lines matching $1 in capture $2 than there were when it was called.
await_more() { local n; n=$(count "$1" "$2"); for _ in $(seq 1 "$3"); do [ "$(count "$1" "$2")" -gt "$n" ] && return 0; sleep 1; done; return 1; }
DONE='autolock: .*detection=(LOCKED|SKIPPED|ERROR|ABORTED)'
echo "--- AutoLock"
# Waits up to 20 s for the watcher to be up ($1 = up) or down ($1 = down).
await_svc() { for _ in $(seq 1 20); do if [ "$1" = up ]; then [ "$(svc_up)" -gt 0 ] && return 0; else [ "$(svc_up)" -eq 0 ] && return 0; fi; sleep 1; done; return 1; }
fixture testautolockon
if await_svc up; then echo "RESULT: AutoLock on: the watcher is running"; else echo "RESULT: AutoLock on: the watcher is NOT running"; fi
$DBG trigger >/dev/null
if await 'autolock: detection=CONFIRMING' 30 first; then echo "RESULT: a disconnect waits for a walk"; else echo "RESULT: the disconnect did not start an evaluation"; fi
before=$(count "$DONE" first)
$DBG walk >/dev/null
for _ in $(seq 1 120); do [ "$(count "$DONE" first)" -gt "$before" ] && break; sleep 1; done
grep -E "detection=GRACE" -q /tmp/bt-first.txt && echo "RESULT: the walk started the countdown"
echo "RESULT: the evaluation ended: $(grep -E "$DONE" /tmp/bt-first.txt | tail -1 | sed 's/.*autolock: //')"
sleep 12
if [ "$(svc_up)" -gt 0 ]; then echo "RESULT: the watcher is still running after the evaluation"; else echo "RESULT: the watcher STOPPED after the evaluation"; fi

echo "--- AutoLock: the app is killed between the disconnect and the walk"
adb shell input keyevent KEYCODE_HOME; sleep 4
$DBG trigger >/dev/null
await_more 'autolock: detection=CONFIRMING' first 30
oldpid=$(adb shell pidof $PKG | tr -d '\r')
# A hard kill, as Android's low-memory killer does: no cleanup runs, nothing in memory survives.
adb shell kill -9 "$oldpid"; sleep 3
if [ "$(adb shell pidof $PKG | tr -d '\r')" != "$oldpid" ]; then echo "RESULT: the app process ($oldpid) was killed mid-evaluation"; else echo "RESULT: the app process could NOT be killed"; fi
before=$(count "$DONE" first)
$DBG walk >/dev/null
for _ in $(seq 1 120); do [ "$(count "$DONE" first)" -gt "$before" ] && break; sleep 1; done
newpid=$(adb shell pidof $PKG | tr -d '\r')
if [ "$(count "$DONE" first)" -gt "$before" ] && [ -n "$newpid" ] && [ "$newpid" != "$oldpid" ]; then
  echo "RESULT: the walk in the restarted app (process $newpid) resumed the departure: $(grep -E "$DONE" /tmp/bt-first.txt | tail -1 | sed 's/.*autolock: //')"
  grep -q "BlooTest( *$newpid).*detection=GRACE" /tmp/bt-first.txt && echo "RESULT: the resumed departure ran its countdown in the new process"
else
  echo "RESULT: the walk in the restarted app did NOT resume the departure"
fi

echo "--- AutoLock off"
launch 20
adb logcat -c -b crash
fixture testautolockoff; sleep 3
if [ -n "$(adb shell pidof $PKG | tr -d '\r')" ] && [ -z "$(adb logcat -d -b crash | grep -i "$PKG")" ]; then
  echo "RESULT: turning AutoLock off did not crash the app"
else
  echo "RESULT: turning AutoLock off CRASHED the app"
fi
if await_svc down; then echo "RESULT: the watcher stopped"; else echo "RESULT: the watcher is still running after AutoLock was turned off"; fi
show first

echo "--- restarting the emulator"
adb reboot
booted
unlock_device
device_state
capture restart
sleep 2
launch 30
device_state
if await 'login form: fingerprint offered=false bootReauth=true' 30 restart; then
  echo "RESULT: the restart sign-in form offers no fingerprint"
else
  echo "RESULT: the restart sign-in form was not seen without a fingerprint"
fi
echo "--- restart sign-in"
device_state
fixture testreauth --es user64 "$U64" --es password64 "$P64" --es pin64 "$N64" --es brand "$BRAND"
if await 'prompt: shown' 60 restart; then
  sleep 6
  device_state
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
