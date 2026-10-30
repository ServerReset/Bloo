package com.bloo.bluelink

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import coil.imageLoader
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.StartupLabels
import com.bloo.bluelink.data.StartupTrace
import com.bloo.bluelink.data.snapshot
import com.bloo.bluelink.data.warmUp
import com.bloo.bluelink.data.DeviceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bloo.uicommon.content

/**
 * Installs a process-wide uncaught exception handler first thing, handing a crash off to
 * [CrashActivity] with the stack trace, device/build info and [AppLog] history.
 *
 * Also implements [Configuration.Provider] for WorkManager on-demand initialization: the manifest
 * removes the default initializer, and WorkManager's own services call `WorkManager.getInstance`
 * directly (bypassing [com.bloo.bluelink.work.WorkManagerInit]), so a delegate is required.
 *
 * Also starts [DeviceStatus]'s single process-wide listeners (battery saver, thermal, lock state).
 */
class BlooApplication : Application(), Configuration.Provider, coil.ImageLoaderFactory {
    /**
     * Earliest hook the process gets -- runs before [onCreate] and before any ContentProvider
     * (WorkManager's initializer among them, were it still installed). Instrumented because
     * anything that lands here is invisible to every later mark.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Set BEFORE the first mark, not in onCreate: otherwise attachBaseContext's own mark and
        // onCreate's "begin" mark are emitted while the flag is still at its default, so a release
        // build logged exactly those two lines and then went quiet.
        StartupTrace.logcatEnabled = BuildConfig.DEBUG
        StartupTrace.mark(StartupLabels.APPLICATION_ATTACH_BASE_CONTEXT)
        // Begin collecting per-frame timings as early as a Looper exists; the monitor is idempotent
        // and self-terminating (see its own doc), so starting it here rather than at setContent()
        // means the very first Compose frames are inside the window.
        StartupFrameMonitor.start()
        // Schedule the gap report for after startup settles.
        StartupTrace.scheduleSummary()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build().also {
            // Reached the first time ANY caller asks WorkManager for an instance, which
            // self-initializes its Room database + executors on that thread. Timed because it is
            // the one piece of lazy init that can land on the main thread.
            StartupTrace.mark(StartupLabels.WORK_MANAGER_CONFIGURATION_REQUESTED)
        }

    /** Caps Coil's in-memory bitmap cache (80MB) and its disk cache (256MB), replacing the library defaults. A fixed byte cap rather
     *  than a percentage: the map screen streams an unbounded series of 256x256 tiles (~320 tiles at 80MB), and an earlier session
     *  heap grew to 220MB+ without an explicit bound. The disk cache keeps tiles past the server's own expiry, so a map that was
     *  looked at recently still draws without a connection; the byte cap is what bounds it. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizeBytes(80 * 1024 * 1024)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("coil_disk_cache"))
                .maxSizeBytes(256 * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    // Deliberately not chaining to the default handler: it owns the whole crash path (report, CrashActivity,
    // kill), and delegating would show the system "app has stopped" dialog over CrashActivity.
    @android.annotation.SuppressLint("DefaultUncaughtExceptionDelegation")
    override fun onCreate() {
        StartupTrace.mark(StartupLabels.APPLICATION_ON_CREATE_BEGIN)
        super.onCreate()
        // The activity log is kept in a file as well as in the panel's buffer, so a long session can be saved in full.
        com.bloo.bluelink.data.ActivityLogFile.init(this)
        com.bloo.bluelink.data.AppLog.sink = { com.bloo.bluelink.data.ActivityLogFile.append(it) }
        installStartupStrictMode()
        StartupTrace.mark(StartupLabels.APPLICATION_SUPER_ON_CREATE_DONE)
        DeviceStatus.ensureInitialized(this)
        // Keeps any homescreen widget in step with the car data.
        com.bloo.bluelink.widget.BlooWidgetUpdater.start(this)
        StartupTrace.mark(StartupLabels.DEVICE_STATUS_INITIALIZED)
        // Warm the biggest data classes off the main thread. SettingsStore() measured ~50ms on Main
        // on the first launch after install (1ms once warm) -- that is class-load/JIT of a very
        // large class, not its fields, and it lands inside AppViewModel's constructor, i.e. before
        // the first frame.
        Thread {
            runCatching {
                StartupTrace.trace(StartupLabels.CLASS_WARM_UP_STORES) {
                    val app = applicationContext
                    com.bloo.bluelink.data.SettingsStore(app)
                    com.bloo.bluelink.data.SnapshotStore(app)
                    com.bloo.bluelink.data.StatusCache(app)
                    com.bloo.bluelink.data.SessionStore(app)
                }
                // Encrypted-prefs warm-up (MasterKey + EncryptedSharedPreferences + Tink). See
                // CredentialStore.warmUp's own doc: this is the ~470ms the cold-start auto-login
                // otherwise pays on the critical path, right ahead of the app-lock check and the
                // garage load. Runs here, over a second earlier, on this same background thread.
                StartupTrace.trace(StartupLabels.CREDENTIAL_CRYPTO_WARM_UP) {
                    com.bloo.bluelink.data.CredentialStore(applicationContext).warmUp()
                }
                // OkHttp's class graph is the heaviest class-load chain; a throwaway client loads it here.
                StartupTrace.trace(StartupLabels.OKHTTP_CLASS_WARM_UP) {
                    okhttp3.OkHttpClient.Builder().build()
                }
                // Coil's singleton loader otherwise builds on main at the first map tile.
                StartupTrace.trace(StartupLabels.COIL_IMAGE_LOADER_WARM_UP) {
                    applicationContext.imageLoader
                }
                // Compose's own animation class graph (Spring/Animatable/AnimationVector, the
                // machinery every lowPowerAwareSpring() call and every pressed/expanded state
                // transition in the app rides on) had never been touched before the FIRST real
                // animation the user ever triggers -- typically the very first card they tap to
                // expand, which is also usually still inside the first several seconds of a cold
                // process, i.e. exactly the window ART hasn't finished JIT-warming yet.
                StartupTrace.trace(StartupLabels.COMPOSE_ANIMATION_CLASS_WARM_UP) {
                    androidx.compose.animation.core.Animatable(0f)
                    androidx.compose.animation.core.spring<Float>()
                    androidx.compose.animation.core.tween<Float>(140)
                }
                // Settings DataStore first read (file open + preferences protobuf parse), which the
                // auto-login's own appearance.first() otherwise pays on the path to the garage --
                // 452ms measured on the API 34 emulator.
                StartupTrace.trace(StartupLabels.SETTINGS_DATASTORE_WARM_UP) {
                    kotlinx.coroutines.runBlocking {
                        com.bloo.bluelink.data.SettingsStore(applicationContext).warmUp()
                        com.bloo.bluelink.data.StatusCache(applicationContext).warmUp()
                        // Snapshot and session stores feed the first garage reads.
                        com.bloo.bluelink.data.SnapshotStore(applicationContext).warmUp()
                        com.bloo.bluelink.data.SessionStore(applicationContext).warmUp()
                    }
                }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
        AppLog.log("▶ App starting: ${deviceSummary()}")
        // Watch presence + the live snapshot push (see app/.../wear/). Off the main thread, guarded
        // internally: a phone with no watch, or no Play Services at all, just reads as "no watch
        // paired" and the push is a no-op.
        Thread {
            runCatching { com.bloo.bluelink.wear.WatchPresence.start(applicationContext) }
            // Housekeeping: drop any staged update APK from a previous session -- it is only ever
            // read in the same session that downloaded it, so it is dead weight (multi-MB) between
            // sessions. See UpdateCleanup's own doc.
            runCatching { com.bloo.bluelink.update.UpdateCleanup.clearStagedDownloads(applicationContext) }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
        StartupTrace.mark(StartupLabels.APPLICATION_ON_CREATE_END)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = Log.getStackTraceString(throwable)
            Log.e("BlooCrash", "Uncaught exception on ${thread.name}:\n$trace")
            try {
                // Everything a bug report needs in ONE selectable/copyable blob, gathered here
                // because this is the only moment any of it is still available: the process gets
                // killed unconditionally right after this handler returns (see that kill's own
                // comment below), so CrashActivity itself starts fresh in a NEW process with none
                // of this in memory -- AppLog.lines most of all, since it's an in-memory ring
                // buffer with no disk backing (Settings' own copy of it is the only other reader,
                // and that's gone too the moment this process dies).
                val report = buildString {
                    appendLine("Bloo crashed on ${thread.name}")
                    appendLine("Device: ${deviceSummary()}")
                    appendLine()
                    appendLine("--- Stack trace ---")
                    appendLine(trace)
                    // Newest first, as the app's own log view is.
                    val logLines = AppLog.snapshot().asReversed()
                    appendLine("--- App log (most recent ${logLines.size} lines, newest first) ---")
                    if (logLines.isEmpty()) {
                        append("(empty: crashed before anything logged, or log() was never reached)")
                    } else {
                        append(logLines.joinToString("\n"))
                    }
                }
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, report)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Throwable) {
            // Even the crash screen itself couldn't come up -- there's nothing left to show the
            // user, so fall through to the kill below regardless.
            }
            // Always kill: this handler runs inline on the crashing (usually main) thread, and returning would
            // leave a dead Looper and a frozen process. startActivity() already reached system_server, so
            // CrashActivity starts in a fresh process.
            android.os.Process.killProcess(android.os.Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }

    /**
     * A real device report showed the heap climbing to 400-500MB at cold start (the known
     * hero-photo/vehicle-fetch spike -- see HeroLoadStagger's own doc) and then NEVER coming back
     * down for the rest of a 500+ second session, eventually OOMing on a routine coroutine resume
     * with under 1% of a 512MB (largeHeap) heap free *after* a GC -- i.e. genuinely retained, not
     * just garbage waiting for the next collection.
     */
    // TRIM_MEMORY_RUNNING_* are the levels onTrimMemory actually reports; the typed Coil
    // imageLoader access is still behind the experimental marker.
    @Suppress("DEPRECATION")
    @kotlin.OptIn(coil.annotation.ExperimentalCoilApi::class)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The system calls this on the MAIN thread. memoryCache.clear() is an in-memory bookkeeping
        // op (cheap, safe to run inline), but diskCache.clear() is real synchronous file I/O (Coil
        // has no async variant) -- running that inline here would block the main thread on disk
        // access precisely while the OS already considers the process under enough memory pressure
        // to be trimming it, which is exactly the wrong moment to also risk a janked/ANR'd frame.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AppLog.log("onTrimMemory($level): clearing Coil's memory cache")
            runCatching { imageLoader.memoryCache?.clear() }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            kotlin.concurrent.thread(name = "coil-trim-disk-cache") {
                runCatching { imageLoader.diskCache?.clear() }
            }
        }
    }

    /**
     * Debug-only StrictMode: reports every main-thread disk read/write and network call to logcat
     * (tag "StrictMode"), which is the fastest way to attribute a startup stall to the thread it
     * happened on.
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
            StartupTrace.mark(StartupLabels.STRICT_MODE_INSTALLED)
        }
    }

    /**
     * One line covering everything needed to tell "does this only happen on one device/ Android
     * version/build" apart from "this happens everywhere" -- the other half of what a bug report
     * needs alongside the stack trace and [AppLog] itself. [BuildConfig]'s
     * BUILD_RUN_NUMBER/BUILD_BRANCH (not versionCode/versionName, which stay fixed at 1/"0.1" --
     * see that field's own doc in build.gradle.kts) are what actually identify which CI build
     * produced this specific APK, the same pair [UpdateChecker] itself compares against GitHub's
     * build list.
     */
    private fun deviceSummary(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val branch = BuildConfig.BUILD_BRANCH.ifBlank { "(local build)" }
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · Bloo build ${BuildConfig.BUILD_RUN_NUMBER} " +
            "on $branch · $time"
    }
}
