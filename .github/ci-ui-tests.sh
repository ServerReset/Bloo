#!/usr/bin/env bash
# Runs the instrumented tests and, whatever happens, prints what the device logged about crashes
# and the app's own startup trace -- an emulator failure with no logcat is a guessing game.
set +e
adb logcat -c
./gradlew :app:connectedDebugAndroidTest --stacktrace
status=$?
echo "=== logcat: crashes ==="
adb logcat -d -v time AndroidRuntime:E ActivityManager:W '*:S' | tail -150
echo "=== logcat: Bloo ==="
adb logcat -d -v time | grep -i "bloo\|StartupTrace\|FATAL" | tail -150
exit $status
