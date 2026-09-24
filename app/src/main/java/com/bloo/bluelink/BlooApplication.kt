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
class BlooApplication : Application(), Configuration.Provider, coil.ImageLoaderFactory {

    /**
     * Earliest hook the process gets -- runs before [onCreate] and before any
     * ContentProvider (WorkManager's initializer among them, were it still installed).
     * Instrumented because anything that lands here is invisible to every later mark.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Set BEFORE the first mark, not in onCreate: otherwise attachBaseContext's own
        // mark and onCreate's "begin" mark are emitted while the flag is still at its
        // default, so a release build logged exactly those two lines and then went quiet.
        StartupTrace.logcatEnabled = BuildConfig.DEBUG
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

    /**
     * A firm, explicit cap on Coil's own in-memory bitmap cache, replacing the library's
     * singleton default (built the first time any `context.imageLoader` call resolves it,
     * with no factory to override it -- which is what every AsyncImage in this app,
     * chiefly [com.bloo.bluelink.ui.CarMap]'s per-tile grid, was doing).
     *
     * WHY this exists: a Pixel 10 Pro crash report showed heap climbing from ~5MB at cold
     * start to 220-255MB over one real session with nothing else in [AppLog] that could
     * plausibly account for it (the network calls in that window were failing DNS
     * lookups, so no vehicle-status payload was ever large; WorkManager's own periodic
     * fetch ran fine at that same inflated heap size, so it wasn't the leak), eventually
     * OOM-ing on a routine coroutine-cancellation string concat -- i.e. the crash itself
     * was just whatever tiny allocation happened to land after the heap was already
     * effectively full. This app has no `android:largeHeap`, so its process heap ceiling
     * here was 256MB -- and it is also the one app screen that keeps requesting genuinely
     * NEW images for as long as it's open: panning/driving crosses into fresh map tiles
     * continuously, each a real cache MISS, not a repeat of something already shown.
     * Coil's own default cache sizes itself off `ActivityManager.getMemoryClass()`, which
     * already reflects this same 256MB ceiling -- so in principle its default 25% share
     * (~64MB) should have self-limited well under the crash's 220MB+, but with no factory
     * here to make that bound explicit and verifiable, this app had no code-level
     * guarantee of it at all, only whatever the library's own heuristic happened to
     * decide. This makes the limit an explicit, deliberate constant instead of an
     * inherited default this app never actually chose.
     *
     * `maxSizeBytes` (a fixed 48MB), not `maxSizePercent`: percent-of-available-memory is
     * the right default for a typical app showing a handful of images at a time, but this
     * app's one heavy consumer is an effectively-unbounded STREAM of same-sized 256x256
     * tiles for as long as a map is on screen, where the right cap is "how many tiles is
     * it reasonable to hold at once" (48MB / ~256KB decoded per tile ≈ 190 tiles -- several
     * screens' worth in every direction), not a fraction of whatever this specific device
     * happens to report as available.
     */
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
        installStartupStrictMode()
        StartupTrace.mark("Application.super.onCreate done")
        BatterySaverState.ensureInitialized(this)
        StartupTrace.mark("BatterySaverState.ensureInitialized done")
        // Warm the biggest data classes off the main thread. SettingsStore() measured
        // ~50ms on Main on the first launch after install (1ms once warm) -- that is
        // class-load/JIT of a very large class, not its fields, and it lands inside
        // AppViewModel's constructor, i.e. before the first frame. Touching the classes
        // here, on Default, moves that cost off the critical path; a failure to warm in
        // time is harmless (the constructor simply pays it as before).
        Thread {
            runCatching {
                StartupTrace.trace("class warm-up (stores)") {
                    val app = applicationContext
                    com.bloo.bluelink.data.SettingsStore(app)
                    com.bloo.bluelink.data.SnapshotStore(app)
                    com.bloo.bluelink.data.StatusCache(app)
                    com.bloo.bluelink.data.SessionStore(app)
                }
                // Encrypted-prefs warm-up (MasterKey + EncryptedSharedPreferences + Tink).
                // See CredentialStore.warmUp's own doc: this is the ~470ms the cold-start
                // auto-login otherwise pays on the critical path, right ahead of the app-lock
                // check and the garage load. Runs here, over a second earlier, on this same
                // background thread.
                StartupTrace.trace("credential crypto warm-up") {
                    com.bloo.bluelink.data.CredentialStore(applicationContext).warmUp()
                }
                // OkHttp's class graph (Dispatcher, ExecutorService, ConnectionPool, route
                // database, the whole interceptor/JSON chain) is the heaviest class-load
                // chain in the app, and the cold-start auto-login builds a shared client per
                // signed-in brand on the path to the garage (timed as "Repos constructed",
                // 270-530ms). Building a throwaway client here loads that graph on this
                // thread instead; the repos' own clients are then construction-only.
                StartupTrace.trace("okhttp class warm-up") {
                    okhttp3.OkHttpClient.Builder().build()
                }
                // Coil's singleton ImageLoader (now built by newImageLoader() above) is
                // otherwise lazily constructed on whatever thread first touches
                // context.imageLoader -- CarMap's first tile request, on the main thread,
                // the instant a car with a location renders. Building it here instead pays
                // its own class-load plus the disk cache directory's first mkdir off that
                // path; a caller reaching context.imageLoader before this finishes just
                // gets the same singleton construction inline, same as if this didn't exist.
                StartupTrace.trace("coil image loader warm-up") {
                    applicationContext.imageLoader
                }
                // Compose's own animation class graph (Spring/Animatable/AnimationVector,
                // the machinery every lowPowerAwareSpring() call and every pressed/expanded
                // state transition in the app rides on) had never been touched before the
                // FIRST real animation the user ever triggers -- typically the very first
                // card they tap to expand, which is also usually still inside the first
                // several seconds of a cold process, i.e. exactly the window ART hasn't
                // finished JIT-warming yet. This only pays the one-time class-verification/
                // linking cost (a real but small slice of "why does the first tap feel
                // choppy") -- it does NOT eliminate ART's own interpreter-to-JIT warm-up
                // curve for the actual per-frame animation math, which is a genuinely
                // different cost this can't reach without a baseline profile (a real device/
                // emulator to generate one against, which this environment doesn't have).
                // Constructing these off-main is still strictly better than paying even
                // the small part of it inline on the user's first tap.
                StartupTrace.trace("compose animation class warm-up") {
                    androidx.compose.animation.core.Animatable(0f)
                    androidx.compose.animation.core.spring<Float>()
                    androidx.compose.animation.core.tween<Float>(140)
                }
                // Settings DataStore first read (file open + preferences protobuf parse),
                // which the auto-login's own appearance.first() otherwise pays on the path to
                // the garage -- 452ms measured on the API 34 emulator. DataStore caches the
                // parsed data after this, so the real read becomes ~free. runBlocking on this
                // background thread is fine; it is not the main thread.
                StartupTrace.trace("settings datastore warm-up") {
                    kotlinx.coroutines.runBlocking {
                        com.bloo.bluelink.data.SettingsStore(applicationContext).warmUp()
                        com.bloo.bluelink.data.StatusCache(applicationContext).warmUp()
                        // The snapshot (cached garage) and session stores feed the two reads
                        // publishCachedGarage/loadGarageInner make right after this, so their
                        // file-open belongs here too -- a warm read is ~free vs a cold one.
                        com.bloo.bluelink.data.SnapshotStore(applicationContext).warmUp()
                        com.bloo.bluelink.data.SessionStore(applicationContext).warmUp()
                    }
                }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
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
     * A real device report showed the heap climbing to 400-500MB at cold start (the
     * known hero-photo/vehicle-fetch spike -- see HeroLoadStagger's own doc) and then
     * NEVER coming back down for the rest of a 500+ second session, eventually OOMing
     * on a routine coroutine resume with under 1% of a 512MB (largeHeap) heap free
     * *after* a GC -- i.e. genuinely retained, not just garbage waiting for the next
     * collection. `newImageLoader()`'s own doc already caps Coil's in-memory bitmap
     * cache at 48MB explicitly for exactly this class of report, but a cap on how much
     * Coil is WILLING to hold is not the same as the OS telling it to actually let go
     * under real pressure -- this is that second half: [ComponentCallbacks2]'s own
     * ladder of "how bad is it" levels, forwarded to Coil's memory cache (and, at the
     * worst levels, the disk cache too, since a disk write under this much pressure is
     * itself a cost worth avoiding) so cached bitmaps are the first thing given up
     * before the OS has to start killing things -- including this process.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AppLog.log("onTrimMemory($level): clearing Coil's memory cache")
            runCatching { imageLoader.memoryCache?.clear() }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            runCatching { imageLoader.diskCache?.clear() }
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
