#!/usr/bin/env bash
# Runs the instrumented tests and, whatever happens, prints what the device logged about crashes
# and the app's own startup trace -- an emulator failure with no logcat is a guessing game.
set +e
adb logcat -c
./gradlew :app:connectedDebugAndroidTest --stacktrace
status=$?
echo "=== logcat: crash buffer ==="
adb logcat -d -b crash -v time | head -120
echo "=== logcat: app lines (all buffers) ==="
adb logcat -d -b all -v time | grep -i "com.bloo\|StartupTrace\|FATAL\|AndroidRuntime\|ANR in\|Process com.bloo" | grep -v "Slow operation" | head -150
exit $status
