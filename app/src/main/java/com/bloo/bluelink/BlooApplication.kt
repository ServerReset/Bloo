package com.bloo.bluelink

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.StartupTrace
import com.bloo.bluelink.ui.BatterySaverState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a process-wide uncaught exception handler as the very first thing this
 * process does -- before MainActivity, WorkManager or any Compose code runs. A fatal
 * crash already lands in logcat under "AndroidRuntime" regardless of this, but there
 * was previously no way to see WHICH exception without already having `adb logcat`
 * attached at the exact moment it happened, which isn't something a user hitting a
 * silent "app has stopped" can do after the fact. This makes the crash itself hand
 * off to [CrashActivity] -- a plain, dependency-free, selectable-text screen with the
 * full stack trace, plus device/build info and the [AppLog] history leading up to it
 * (see [deviceSummary] and this handler's own `report` below) -- so it can be read
 * (and copied straight off the device, all of it in one paste) without ever needing
 * adb or reproducing it a second time with logging already running.
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

    /**
     * Earliest hook the process gets -- runs before [onCreate] and before any
     * ContentProvider (WorkManager's initializer among them, were it still installed).
     * Instrumented because anything that lands here is invisible to every later mark.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupTrace.mark("Application.attachBaseContext")
        // Begin collecting per-frame timings as early as a Looper exists; the monitor is
        // idempotent and self-terminating (see its own doc), so starting it here rather
        // than at setContent() means the very first Compose frames are inside the window.
        StartupFrameMonitor.start()
        // And schedule the self-summarising gap report for once startup has settled.
        StartupTrace.scheduleSummary()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build().also {
            // Reached the first time ANY caller asks WorkManager for an instance, which
            // self-initializes its Room database + executors on that thread. Timed
            // because it is the one piece of lazy init that can land on the main thread.
            StartupTrace.mark("WorkManager configuration requested (lazy init)")
        }

    // @SuppressLint("DefaultUncaughtExceptionDelegation") is deliberate, not an oversight:
    // this handler does NOT chain to the previously-installed default handler. It owns the
    // whole crash path itself -- it writes the report, starts CrashActivity in a fresh
    // process, and then kills this one unconditionally (see the kill's own comment below).
    // Delegating would hand the crash to the platform's own handler, which raises the
    // system "app has stopped" dialog on top of CrashActivity -- two crash UIs at once,
    // which is exactly what this screen exists to replace. Lint's check assumes the
    // conventional delegate-and-return shape; this handler cannot use it.
    @android.annotation.SuppressLint("DefaultUncaughtExceptionDelegation")
    override fun onCreate() {
        StartupTrace.mark("Application.onCreate: begin")
        super.onCreate()
        // Keep the startup trace quiet in a release build (the in-memory AppLog copy and
        // the marks themselves remain available); a debug build is where logcat timing
        // is actually read.
        StartupTrace.logcatEnabled = BuildConfig.DEBUG
        installStartupStrictMode()
        StartupTrace.mark("Application.super.onCreate done")
        BatterySaverState.ensureInitialized(this)
        StartupTrace.mark("BatterySaverState.ensureInitialized done")
        AppLog.log("▶ App starting -- ${deviceSummary()}")
        StartupTrace.mark("Application.onCreate: end")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = Log.getStackTraceString(throwable)
            Log.e("BlooCrash", "Uncaught exception on ${thread.name}:\n$trace")
            try {
                // Everything a bug report needs in ONE selectable/copyable blob, gathered
                // here because this is the only moment any of it is still available: the
                // process gets killed unconditionally right after this handler returns (see
                // that kill's own comment below), so CrashActivity itself starts fresh in a
                // NEW process with none of this in memory -- AppLog.lines most of all, since
                // it's an in-memory ring buffer with no disk backing (Settings' own copy of
                // it is the only other reader, and that's gone too the moment this process
                // dies). Reported directly: a crash report with just the stack trace couldn't
                // answer "what was the app actually doing right before this" or "does this
                // only happen on one device/Android version" without asking the user to
                // reproduce it a second time with more logging attached -- exactly what
                // CrashActivity's own doc says this screen exists to avoid needing.
                val report = buildString {
                    appendLine("Bloo crashed on ${thread.name}")
                    appendLine("Device: ${deviceSummary()}")
                    appendLine()
                    appendLine("--- Stack trace ---")
                    appendLine(trace)
                    appendLine("--- App log (most recent ${AppLog.lines.value.size} lines) ---")
                    if (AppLog.lines.value.isEmpty()) {
                        append("(empty -- crashed before anything logged, or log() was never reached)")
                    } else {
                        append(AppLog.lines.value.joinToString("\n"))
                    }
                }
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, report)
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

    /**
     * Debug-only StrictMode: reports every main-thread disk read/write and network call to
     * logcat (tag "StrictMode"), which is the fastest way to attribute a startup stall to
     * the thread it happened on. A cold start that blocks the main thread on a DataStore
     * read, a SharedPreferences load or an OkHttp call shows up here as the offending
     * stack, next to its own BlooStartup phase mark. VmPolicy also flags leaked
     * closeables/Activities. Never enabled in release: the checks themselves cost time,
     * and their whole purpose is to be read by a developer with logcat attached.
     */
    private fun installStartupStrictMode() {
        if (!BuildConfig.DEBUG) return
        runCatching {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build(),
            )
            StartupTrace.mark("StrictMode installed (debug): main-thread disk/network will be logged")
        }
    }

    /**
     * One line covering everything needed to tell "does this only happen on one device/
     * Android version/build" apart from "this happens everywhere" -- the other half of what
     * a bug report needs alongside the stack trace and [AppLog] itself. [BuildConfig]'s
     * BUILD_RUN_NUMBER/BUILD_BRANCH (not versionCode/versionName, which stay fixed at 1/"0.1"
     * -- see that field's own doc in build.gradle.kts) are what actually identify which CI
     * build produced this specific APK, the same pair [UpdateChecker] itself compares against
     * GitHub's build list.
     */
    private fun deviceSummary(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val branch = BuildConfig.BUILD_BRANCH.ifBlank { "(local build)" }
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · Bloo build ${BuildConfig.BUILD_RUN_NUMBER} " +
            "on $branch · $time"
    }
}
