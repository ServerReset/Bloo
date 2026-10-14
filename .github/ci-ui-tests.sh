#!/usr/bin/env bash
# Runs the instrumented tests and, whatever happens, prints what the device logged about crashes
# and the app's own startup trace -- an emulator failure with no logcat is a guessing game.
#
# EVERY adb call is wrapped in `timeout`, and that is the point of this script's shape. When the
# emulator dies mid-suite -- which a busy runner does to a device this heavy -- adb itself stops
# answering, and an unwrapped call then blocks FOREVER. That is exactly what was happening: the
# gradle run failed in under three minutes, the script moved on to `adb logcat`, and the step sat
# there until the job's own 30-minute cap killed it. A run that had already failed looked like a
# suite that takes half an hour and then just stops.
set +e
ADB="timeout 60 adb"

$ADB logcat -c
./gradlew :app:connectedSideloadDebugAndroidTest --stacktrace
status=$?
echo "=== logcat: crash buffer ==="
$ADB logcat -d -b crash -v time | head -120
echo "=== logcat: startup trace ==="
$ADB logcat -d -b all -v time -s BlooStartup:I | head -200
echo "=== am start -W ==="
$ADB shell am force-stop com.bloo.bluelink
$ADB shell am start -W -n com.bloo.bluelink/.MainActivity | head -20
sleep 8
echo "=== startup trace after cold start ==="
$ADB logcat -d -b all -v time -s BlooStartup:I | tail -60
echo "=== app lines (all buffers) ==="
$ADB logcat -d -b all -v time | grep -i "com.bloo\|StartupTrace\|FATAL\|AndroidRuntime\|ANR in\|Process com.bloo" | grep -v "Slow operation" | head -150
exit $status
