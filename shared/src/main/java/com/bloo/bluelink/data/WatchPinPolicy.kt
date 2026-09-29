package com.bloo.bluelink.data

/**
 * When a paired watch should ask for the app PIN.
 *
 * A separate axis from the phone's own [LockTiming] (screen off / immediate): a watch is an
 * auxiliary surface, so its two meaningful gates are "on opening the app" and "on sending a
 * command to the car", and the user may want either or both.
 *
 * Kept as string wire values (like [LockTiming]) so one persisted value survives renames and
 * an unrecognised one falls back to the safest reading rather than throwing.
 */
enum class WatchLockTiming(val wireKey: String, val label: String) {
    OFF("off", "Off"),
    OPEN("open", "Opening the app"),
    COMMANDS("commands", "Sending commands"),
    BOTH("both", "Opening & commands"),
    ;

    companion object {
        fun fromWire(raw: String?): WatchLockTiming =
            entries.firstOrNull { it.wireKey == raw } ?: OFF
    }
}

/**
 * Pure decision logic for the watch PIN gate -- no Android, so it is exhaustively unit
 * testable. [WatchPinPolicy] holds the persisted timing plus the transient "already unlocked
 * this session" fact; this object only answers the two questions the UI asks of it.
 */
object WatchPinPolicy {

    /** Should the watch show the PIN screen right now, on opening the app? */
    fun locksOnOpen(timing: WatchLockTiming): Boolean =
        timing == WatchLockTiming.OPEN || timing == WatchLockTiming.BOTH

    /** Should sending a command require the PIN? */
    fun locksOnCommand(timing: WatchLockTiming): Boolean =
        timing == WatchLockTiming.COMMANDS || timing == WatchLockTiming.BOTH

    /**
     * The one gate the UI actually consults before revealing content or running a command.
     *
     * [alreadyUnlockedThisSession] is the watch's in-memory "the user proved the PIN since the
     * app opened" flag. It only ever satisfies the OPEN gate -- "opening the app" is a
     * per-session gate, so an open-unlock unlocks the session. The COMMANDS gate is per-command
     * when the timing is COMMANDS-only (a command is the sensitive act), but once OPEN is also
     * on, a session unlock covers both, because the user has already authenticated this session.
     *
     * Encoded as two explicit cases rather than a boolean tangle so the intent is legible and
     * the tests can name each.
     */
    fun requiresUnlock(
        timing: WatchLockTiming,
        event: GateEvent,
        alreadyUnlockedThisSession: Boolean,
    ): Boolean = when (event) {
        // "Opening the app" is a per-session gate: it asks unless this session already proved
        // the PIN at least once.
        GateEvent.OPEN_APP ->
            locksOnOpen(timing) && !alreadyUnlockedThisSession
        // "Sending commands" is the gate the user picked when they chose COMMANDS (or BOTH). A
        // command asks whenever the command gate is on AND this session has not already proven
        // the PIN. The session flag can only ever have been set by an OPEN unlock, so under
        // COMMANDS-only (no open-gate) it is always false and every command asks; under BOTH a
        // single open-unlock covers the session.
        GateEvent.SEND_COMMAND ->
            locksOnCommand(timing) &&
                !(locksOnOpen(timing) && alreadyUnlockedThisSession)
    }

    /** Which interlock the UI is evaluating. */
    enum class GateEvent { OPEN_APP, SEND_COMMAND }
}
