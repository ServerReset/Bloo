#!/usr/bin/env bash
# Captures screenshots of the freshly built APK on the CI emulator and writes them into
# docs/screenshots/, so the README always shows the most recent build. The workflow then commits
# whatever changed.
#
# The app needs a signed-in account to reach the car screens, and CI has none, so these are the
# pre-login screens (welcome, onboarding, sign in). They are captured at rest, on a phone-sized
# emulator, in both themes where the first screen allows it.
set +e

OUT=docs/screenshots
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/release/Bloo.apk

# A generous wait: first launch does onboarding animation plus an update check.
adb shell am force-stop com.bloo.bluelink
adb shell am start -W -n com.bloo.bluelink/.MainActivity >/dev/null 2>&1
sleep 12
adb exec-out screencap -p > "$OUT/01-welcome.png"

# Dark theme too, if the system can be toggled on this API level.
adb shell "cmd uimode night yes" >/dev/null 2>&1
sleep 2
adb exec-out screencap -p > "$OUT/02-welcome-dark.png"
adb shell "cmd uimode night no" >/dev/null 2>&1

echo "Captured:"
ls -la "$OUT"
