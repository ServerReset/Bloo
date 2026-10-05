package com.bloo.bluelink

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import coil.imageLoader
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.StartupTrace
import com.bloo.bluelink.ui.BatterySaverState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bloo.bluelink.data.snapshot
import com.bloo.bluelink.data.warmUp

/**
 * Installs a process-wide uncaught exception handler first thing, handing a crash off to
 * [CrashActivity] with the stack trace, device/build info and [AppLog] history.
 *
 * Also implements [Configuration.Provider] for WorkManager on-demand initialization: the manifest
 * removes the default initializer, and WorkManager's own services call `WorkManager.getInstance`
 * directly (bypassing [com.bloo.bluelink.work.WorkManagerInit]), so a delegate is required.
 *
 * Also starts [BatterySaverState]'s single process-wide broadcast receiver.
 */
class BlooApplication : Application(), Configuration.Provider, coil.ImageLoaderFactory {

    /** Earliest hook the process gets, before [onCreate] and any ContentProvider. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Set before the first mark so release builds log nothing.
        StartupTrace.logcatEnabled = BuildConfig.DEBUG
        StartupTrace.mark("Application.attachBaseContext")
        // Idempotent and self-terminating; starting here puts the first Compose frames inside the window.
        StartupFrameMonitor.start()
        // Schedule the gap report for after startup settles.
        StartupTrace.scheduleSummary()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build().also {
            // First WorkManager request self-initializes Room + executors; timed as lazy init that can land on main.
            StartupTrace.mark("WorkManager configuration requested (lazy init)")
        }

    /** Caps Coil's in-memory bitmap cache explicitly (48MB), replacing the library default.
     *  A fixed byte cap rather than a percentage: the map screen streams an unbounded series of 256x256 tiles
     *  (~190 tiles at 48MB), and an earlier session heap grew to 220MB+ without an explicit bound. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizeBytes(48 * 1024 * 1024)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("coil_disk_cache"))
                .maxSizeBytes(100 * 1024 * 1024)
                .build()
        }
        .build()

    // Deliberately not chaining to the default handler: it owns the whole crash path (report, CrashActivity,
    // kill), and delegating would show the system "app has stopped" dialog over CrashActivity.
    @android.annotation.SuppressLint("DefaultUncaughtExceptionDelegation")
    override fun onCreate() {
        StartupTrace.mark("Application.onCreate: begin")
        super.onCreate()
        installStartupStrictMode()
        StartupTrace.mark("Application.super.onCreate done")
        BatterySaverState.ensureInitialized(this)
        StartupTrace.mark("BatterySaverState.ensureInitialized done")
        // Warm the biggest classes off the main thread (SettingsStore costs ~50ms of class-load on Main otherwise); failure is harmless.
        Thread {
            runCatching {
                StartupTrace.trace("class warm-up (stores)") {
                    val app = applicationContext
                    com.bloo.bluelink.data.SettingsStore(app)
                    com.bloo.bluelink.data.SnapshotStore(app)
                    com.bloo.bluelink.data.StatusCache(app)
                    com.bloo.bluelink.data.SessionStore(app)
                }
                // Encrypted-prefs warm-up (MasterKey + Tink), ~470ms off the cold-start auto-login path.
                StartupTrace.trace("credential crypto warm-up") {
                    com.bloo.bluelink.data.CredentialStore(applicationContext).warmUp()
                }
                // OkHttp's class graph is the heaviest class-load chain; a throwaway client loads it here.
                StartupTrace.trace("okhttp class warm-up") {
                    okhttp3.OkHttpClient.Builder().build()
                }
                // Coil's singleton loader otherwise builds on main at the first map tile.
                StartupTrace.trace("coil image loader warm-up") {
                    applicationContext.imageLoader
                }
                // Compose animation class graph: pays one-time class verification here, not on the first tap.
                StartupTrace.trace("compose animation class warm-up") {
                    androidx.compose.animation.core.Animatable(0f)
                    androidx.compose.animation.core.spring<Float>()
                    androidx.compose.animation.core.tween<Float>(140)
                }
                // Settings DataStore first read (file open + parse) off the auto-login path; runBlocking is fine off main.
                StartupTrace.trace("settings datastore warm-up") {
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
        AppLog.log("▶ App starting -- ${deviceSummary()}")
        // Watch presence + live snapshot push (see app/.../wear/); a no-op without a watch or Play Services.
        Thread {
            runCatching { com.bloo.bluelink.wear.WatchPresence.start(applicationContext) }
            // Housekeeping: drop any staged update APK from a previous session.
            runCatching { com.bloo.bluelink.update.UpdateCleanup.clearStagedDownloads(applicationContext) }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
        StartupTrace.mark("Application.onCreate: end")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = Log.getStackTraceString(throwable)
            Log.e("BlooCrash", "Uncaught exception on ${thread.name}:\n$trace")
            try {
                // Gather everything a report needs now: the process is killed right after, and AppLog is in-memory only.
                val report = buildString {
                    appendLine("Bloo crashed on ${thread.name}")
                    appendLine("Device: ${deviceSummary()}")
                    appendLine()
                    appendLine("--- Stack trace ---")
                    appendLine(trace)
                    val logLines = AppLog.snapshot()
                    appendLine("--- App log (most recent ${logLines.size} lines) ---")
                    if (logLines.isEmpty()) {
                        append("(empty -- crashed before anything logged, or log() was never reached)")
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
            // The crash screen could not start; fall through to the kill.
            }
            // Always kill: this handler runs inline on the crashing (usually main) thread, and returning would
            // leave a dead Looper and a frozen process. startActivity() already reached system_server, so
            // CrashActivity starts in a fresh process.
            android.os.Process.killProcess(android.os.Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }

    /** Forwards [ComponentCallbacks2] trim levels to Coil's caches (disk too at the worst levels),
     *  so cached bitmaps are given up before the OS kills the process. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Called on main: memory cache clear is cheap, but diskCache.clear() is synchronous I/O, so it runs on a background thread.
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

    /** Debug-only StrictMode: logs main-thread disk/network use (tag "StrictMode") to attribute startup stalls. */
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

    /** One-line device and build summary for crash reports; BUILD_RUN_NUMBER/BUILD_BRANCH identify the CI build. */
    private fun deviceSummary(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val branch = BuildConfig.BUILD_BRANCH.ifBlank { "(local build)" }
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · Bloo build ${BuildConfig.BUILD_RUN_NUMBER} " +
            "on $branch · $time"
    }
}
