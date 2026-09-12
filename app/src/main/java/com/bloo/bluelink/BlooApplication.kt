package com.bloo.bluelink

import android.app.Application
import android.content.Intent
import android.util.Log

/**
 * Installs a process-wide uncaught exception handler as the very first thing this
 * process does -- before MainActivity, WorkManager or any Compose code runs. A fatal
 * crash already lands in logcat under "AndroidRuntime" regardless of this, but there
 * was previously no way to see WHICH exception without already having `adb logcat`
 * attached at the exact moment it happened, which isn't something a user hitting a
 * silent "app has stopped" can do after the fact. This makes the crash itself hand
 * off to [CrashActivity] -- a plain, dependency-free, selectable-text screen with the
 * full stack trace -- so it can be read (and copied straight off the device) without
 * ever needing adb or reproducing it a second time with logging already running.
 */
class BlooApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = Log.getStackTraceString(throwable)
            Log.e("BlooCrash", "Uncaught exception on ${thread.name}:\n$trace")
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Throwable) {
                // If even launching the crash screen fails, there's nothing left to
                // do but fall through to the process kill below -- the exception is
                // still in logcat either way (logged above).
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }
}
