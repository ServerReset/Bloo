package com.bloo.bluelink

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.Configuration

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
 *
 * Also implements [Configuration.Provider] for WorkManager's *on-demand initialization*.
 * The manifest removes WorkManager's default `androidx.startup` initializer (see its own
 * comment there) to keep that Room-database-opening work off the very first main-thread
 * frame, and [com.bloo.bluelink.work.WorkManagerInit] was meant to replace it -- but that
 * object only initializes WorkManager for call sites that go through `WorkManagerInit.of()`.
 * WorkManager's own internal entry points -- `SystemJobService`/`SystemAlarmService`, which
 * is what JobScheduler/AlarmManager actually invoke to run a scheduled periodic worker in a
 * freshly cold-started process -- call `WorkManager.getInstance(context)` directly, with no
 * way to route them through our wrapper. Without a delegate, that call threw
 * "WorkManager is not initialized properly", which the exception handler just below caught
 * and turned into an immediate process kill: every background firing of a periodic worker
 * (the widget refresh among them) silently crash-looped instead of running. Implementing
 * this interface is what tells WorkManager's *own* getInstance() to lazily self-initialize
 * from this Configuration the first time ANY caller -- ours or the library's own internal
 * ones -- asks for an instance, which is the officially supported replacement for the
 * removed auto-initializer, not just a same-process convenience.
 */
class BlooApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

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
