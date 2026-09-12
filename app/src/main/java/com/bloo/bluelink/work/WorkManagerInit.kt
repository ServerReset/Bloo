package com.bloo.bluelink.work

import android.content.Context
import androidx.work.WorkManager

/**
 * Thin wrapper around `WorkManager.getInstance(context)`.
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
 * `tools:node="remove"` on its `WorkManagerInitializer` meta-data. The replacement is
 * [com.bloo.bluelink.BlooApplication] implementing `Configuration.Provider` -- WorkManager's
 * officially supported *on-demand initialization* hook, which lazily self-initializes the first
 * time ANY call site (this object's own callers, but just as importantly WorkManager's own
 * internal `SystemJobService`/`SystemAlarmService`, which is what actually runs a scheduled
 * periodic worker after the process was cold-started to service it) asks `WorkManager` for an
 * instance. This object used to hand-roll that guard itself with an explicit
 * `WorkManager.initialize()` call -- which only ever helped call sites that went through [of],
 * and left every OS-triggered entry point still crashing with "WorkManager is not initialized
 * properly" since those call `WorkManager.getInstance()` directly and have no way to route
 * through this wrapper. Now that the delegate lives on the Application itself, ALL of those
 * paths self-initialize the same way, and this wrapper is kept only as the one spelling every
 * call site in this app already uses.
 */
internal object WorkManagerInit {
    fun of(context: Context): WorkManager = WorkManager.getInstance(context.applicationContext)
}
