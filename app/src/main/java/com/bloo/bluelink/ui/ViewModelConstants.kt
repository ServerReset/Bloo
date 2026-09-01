package com.bloo.bluelink.ui

/**
 * Pure top-level timing/constant literals peeled out of AppViewModel.kt:
 * the weather-freshness TTL, auto-push debounce, orphaned-photo sweep delay,
 * the update tile's undo/reminder windows, the Shizuku permission request
 * code, and the command double-tap lockout. Every value is a literal, so
 * nothing here is imported -- and there is no Compose API usage, hence no
 * @file:OptIn.
 */

/** How long a cached weather reading is considered fresh (15 minutes). */
internal const val WEATHER_TTL_MS = 15 * 60 * 1000L

// Debounce window for the auto-push-on-change collector: a burst of edits (e.g.
// dragging pebbles, sliding a value) coalesces into one Drive write this long
// after the LAST change. Short enough to feel instant, long enough not to write
// per keystroke.
internal const val AUTO_PUSH_DEBOUNCE_MS = 2000L

// How long after launch the orphaned-car-photo sweep runs. Pure housekeeping with
// nothing waiting on it, so it yields to the garage load, the first status fetches,
// the first Drive pass and composition rather than competing with them.
internal const val PHOTO_SWEEP_DELAY_MS = 8000L

// How long the update tile lingers with an "Undo" strip after "Not now" before
// the dismiss commits — the call-back window.
internal const val UPDATE_DISMISS_UNDO_MS = 4500L

// "Remind me": both the reminder-notification worker delay and the matching
// snooze window. Kept as one value so the two stay aligned (see snoozeUpdate).
internal const val UPDATE_REMINDER_DELAY_MS = 24L * 60 * 60 * 1000L
/** Request code for the Shizuku runtime-permission prompt (seamless install). */
internal const val SHIZUKU_INSTALL_REQUEST_CODE = 4711

/** Minimum time a command control stays locked after firing, to block double-taps. */
internal const val MIN_COMMAND_LOCK_MS = 3000L

/**
 * How far back the per-car remote-action history reaches: a rolling 30 days, pruned on every
 * write. A window, not a count -- a car commanded twice a week and one commanded twenty times a
 * day should both answer "what did I do to this car recently", and a fixed 20-entry cap gave the
 * busy car about a day of history while the quiet one kept months.
 */
internal const val REMOTE_ACTION_HISTORY_DAYS = 30L

/**
 * A hard ceiling on entries per car, well above what 30 days of ordinary use produces. Purely a
 * backstop so a command loop or a wrong clock cannot grow this without bound; the 30-day window
 * above is what normally decides what is kept.
 */
internal const val REMOTE_ACTION_HISTORY_MAX = 200
