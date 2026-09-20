package com.bloo.bluelink.autolock

import android.content.Context

/**
 * The pending-evaluation record for AutoLock's ALARM path -- the fallback that runs when the
 * OS refuses a background foreground-service start (see [AutoLockAlarm]).
 *
 * Why this has to be persisted at all: the service-based path keeps an evaluation's state in
 * memory, in one process, for as long as it runs. The alarm path cannot -- the trigger, the
 * walk-away confirmation and the grace deadline can each be delivered to a DIFFERENT process
 * instance (the system may kill the app in between; that is the whole reason the fallback
 * exists). So the handful of facts the later steps need are written to SharedPreferences,
 * which also means a reboot mid-countdown loses nothing important: the deadline is absolute.
 *
 * Deliberately tiny and unencrypted: a VIN, a car name, a deadline and a flag. Nothing here
 * is a credential (the command itself still goes through the normal session store when it
 * runs).
 */
internal object AutoLockPending {

    private const val PREFS = "bloo_autolock_pending"
    private const val KEY_PREFIX = "pending_"

    /** [deadlineMs] is wall-clock (System.currentTimeMillis) so it survives a reboot. */
    data class Record(
        val vin: String,
        val carName: String?,
        val deadlineMs: Long,
        val dryRun: Boolean,
        val walkConfirmed: Boolean = false,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(context: Context, record: Record) {
        prefs(context).edit()
            .putString(KEY_PREFIX + record.vin, encode(record))
            .apply()
    }

    fun get(context: Context, vin: String): Record? =
        prefs(context).getString(KEY_PREFIX + vin, null)?.let(::decode)

    /** Marks the walk-away confirmation for an existing record; no-op when none is pending. */
    fun markWalkConfirmed(context: Context, vin: String) {
        val current = get(context, vin) ?: return
        begin(context, current.copy(walkConfirmed = true))
    }

    fun clear(context: Context, vin: String) {
        prefs(context).edit().remove(KEY_PREFIX + vin).apply()
    }

    fun all(context: Context): List<Record> =
        prefs(context).all.values.mapNotNull { it as? String }.mapNotNull(::decode)

    // Pipe-separated, order-fixed, no escaping needed: VINs are alphanumeric and car names
    // are user text -- so the name is percent-encoded for the one character that could
    // appear in it and would break the split.
    private fun encode(r: Record): String = listOf(
        r.vin,
        r.carName.orEmpty().replace("|", "%7C"),
        r.deadlineMs.toString(),
        if (r.dryRun) "1" else "0",
        if (r.walkConfirmed) "1" else "0",
    ).joinToString("|")

    private fun decode(raw: String): Record? {
        val parts = raw.split("|")
        if (parts.size != 5) return null
        val deadline = parts[2].toLongOrNull() ?: return null
        return Record(
            vin = parts[0],
            carName = parts[1].takeIf { it.isNotEmpty() }?.replace("%7C", "|"),
            deadlineMs = deadline,
            dryRun = parts[3] == "1",
            walkConfirmed = parts[4] == "1",
        )
    }
}
