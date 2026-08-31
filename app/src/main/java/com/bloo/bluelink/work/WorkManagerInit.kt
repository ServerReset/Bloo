package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Replaces WorkManager's default auto-initialization.
 *
 * By default, `androidx.work:work-runtime` ships an `androidx.startup.InitializationProvider`
 * entry (`WorkManagerInitializer`) that Android instantiates as a ContentProvider during
 * process attach -- before `Application.onCreate()`, and therefore before `MainActivity.onCreate()`
 * and every deliberately-off-main-thread scheduling call in it. That provider's `onCreate()` runs
 * synchronously on the MAIN thread and does real work: opening/creating WorkManager's own Room
 * database, rescheduling any alarms, and standing up its executors. On a cold start this landed
 * squarely on the critical path to first frame, invisible to every other startup optimization in
 * this app -- all of which only touch code that runs AFTER this had already happened.
 *
 * The manifest (see `AndroidManifest.xml`) removes that automatic initializer via
 * `tools:node="remove"` on its `WorkManagerInitializer` meta-data. In its place, this object does
 * the same `WorkManager.initialize()` call on demand -- guarded so it runs at most once -- from
 * whichever call site needs a `WorkManager` instance first. Every "get a WorkManager" call in this
 * app must go through [of] rather than `WorkManager.getInstance()` directly, or it will crash with
 * "WorkManager is not initialized properly" now that the automatic path is gone.
 *
 * Thread safety: [ensureInitialized] is idempotent and cheap on the (overwhelmingly common) already-
 * initialized path -- one volatile read, no lock. The one-time `WorkManager.initialize()` call itself
 * still does the same disk I/O the automatic initializer used to; the fix is not eliminating that
 * work, it is moving it off the app's very first main-thread frame and onto whichever call site
 * actually needs it (already a background dispatcher for every cold-start scheduling call in
 * `MainActivity`, and for widget/tile entry points the OS invokes independently of app launch).
 */
internal object WorkManagerInit {
    @Volatile
    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
            initialized = true
        }
    }

    /** Drop-in replacement for `WorkManager.getInstance(context)` that guarantees
     *  initialization has happened first (see class doc for why that's no longer automatic). */
    fun of(context: Context): WorkManager {
        ensureInitialized(context)
        return WorkManager.getInstance(context)
    }
}
