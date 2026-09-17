package com.bloo.bluelink

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.Configuration
import com.bloo.bluelink.ui.BatterySaverState

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
 *
 * Also starts [BatterySaverState]'s one process-wide broadcast receiver here,
 * for the same reason as everything else on this list: exactly once, up front,
 * rather than one receiver per composable that happens to call
 * [com.bloo.bluelink.ui.isBatterySaverOn] -- which is now dozens of them per
 * screen (every glass surface, every battery-saver-aware spring).
 */
class BlooApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        BatterySaverState.ensureInitialized(this)
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
                // Even the crash screen itself couldn't come up -- there's nothing left
                // to show the user, so fall through to the kill below regardless.
            }
            // ALWAYS kill, even after a successful startActivity above -- this used to be
            // skipped so CrashActivity's own process could stay around for its check-for-
            // updates/install buttons, which sounded right but wasn't: most crashes
            // (anything in Compose composition/layout/draw, any click handler -- which is
            // most of them) happen ON THE MAIN THREAD, and this handler runs INLINE on
            // that same crashing thread. Once it returns without killing anything, the
            // main thread's Looper.loop() -- which was mid-dispatch when the exception hit
            // -- simply exits, and the main thread ends. The process stays alive (that's
            // why a background worker's notification could still land), but its message
            // loop is gone for good: no Activity, no Compose frame, nothing can ever be
            // scheduled on it again. The app just freezes solid, forever, on whatever was
            // last drawn -- worse than the plain crash this exists to make visible.
            // startActivity() above already handed the launch request to system_server
            // over a synchronous Binder call before this line runs, so killing the
            // process now does NOT lose it: since CrashActivity carries no android:process
            // override, Android simply spins up a brand-new, perfectly healthy process to
            // host it -- which is exactly what CrashActivity's own update-check/install
            // buttons need anyway, not this dying one.
            android.os.Process.killProcess(android.os.Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }
}
