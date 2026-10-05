package com.bloo.bluelink.ui

/**
 * Pure top-level timing/constant literals peeled out of AppViewModel.kt: the weather-freshness TTL,
 * auto-push debounce, orphaned-photo sweep delay, the update tile's undo/reminder windows, the
 * Shizuku permission request code, and the command double-tap lockout.
 */

/** How long a cached weather reading is considered fresh (15 minutes). */
internal const val WEATHER_TTL_MS = 15 * 60 * 1000L

// Floor between two published UiState.deviceLocation updates from the live fused-location collector
// (AppViewModel.beginLiveDeviceLocation) -- a real device OOM crash traced back to this: a fused
// provider can deliver a BURST of fixes well inside its own requested interval (GPS jitter while
// stationary, or "catching up" right after its first-ever fix, which is exactly what happens the
// moment ACCESS_FINE_LOCATION is freshly granted), each one structurally different enough (a few
// meters of jitter is still a different Double) that LocationPebble's own stateSlice treated every
// single one as a genuine change -- hundreds of recompositions in under a second, each allocating a
// full page's worth of objects.
internal const val MIN_DEVICE_LOCATION_INTERVAL_MS = 5000L

/**
 * Paired with [MIN_DEVICE_LOCATION_INTERVAL_MS] -- a fix under this floor is skipped only if it
 * ALSO hasn't moved meaningfully, so a genuine fast-moving car/phone (a passenger, a bike) still
 * gets a fresh dot rather than being throttled purely by the clock.
 */
internal const val MIN_DEVICE_LOCATION_MOVE_METERS = 15f

// Debounce window for the auto-push-on-change collector: a burst of edits (e.g. dragging pebbles,
// sliding a value) coalesces into one Drive write this long after the LAST change.
internal const val AUTO_PUSH_DEBOUNCE_MS = 2000L

// How long after launch the orphaned-car-photo sweep runs.
internal const val PHOTO_SWEEP_DELAY_MS = 8000L

// "Remind me": both the reminder-notification worker delay and the matching snooze window. Kept as
// one value so the two stay aligned (see snoozeUpdate).
internal const val UPDATE_REMINDER_DELAY_MS = 24L * 60 * 60 * 1000L
/** Request code for the Shizuku runtime-permission prompt (seamless install). */
internal const val SHIZUKU_INSTALL_REQUEST_CODE = 4711

/** Minimum time a command control stays locked after firing, to block double-taps. */
internal const val MIN_COMMAND_LOCK_MS = 3000L

/**
 * How far back the per-car remote-action history reaches: a rolling 30 days, pruned on every write.
 */
internal const val REMOTE_ACTION_HISTORY_DAYS = 30L

/** A hard ceiling on entries per car, well above what 30 days of ordinary use produces. */
internal const val REMOTE_ACTION_HISTORY_MAX = 200
