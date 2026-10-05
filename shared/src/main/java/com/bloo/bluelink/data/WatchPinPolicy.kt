package com.bloo.bluelink.data

/** When a paired watch should ask for the app PIN. */
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
 * Pure decision logic for the watch PIN gate -- no Android, so it is exhaustively unit testable.
 * [WatchPinPolicy] holds the persisted timing plus the transient "already unlocked this session"
 * fact; this object only answers the two questions the UI asks of it.
 */
object WatchPinPolicy {

    fun locksOnOpen(timing: WatchLockTiming): Boolean =
        timing == WatchLockTiming.OPEN || timing == WatchLockTiming.BOTH

    /** Should sending a command require the PIN? */
    fun locksOnCommand(timing: WatchLockTiming): Boolean =
        timing == WatchLockTiming.COMMANDS || timing == WatchLockTiming.BOTH

    /**
     * The one gate the UI actually consults before revealing content or running a command.
     * [alreadyUnlockedThisSession] is the watch's in-memory "the user proved the PIN since the app
     * opened" flag.
     */
    fun requiresUnlock(
        timing: WatchLockTiming,
        event: GateEvent,
        alreadyUnlockedThisSession: Boolean,
    ): Boolean = when (event) {
        // "Opening the app" is a per-session gate: it asks unless this session already proved the
        // PIN at least once.
        GateEvent.OPEN_APP ->
            locksOnOpen(timing) && !alreadyUnlockedThisSession
        // "Sending commands" is the gate the user picked when they chose COMMANDS (or BOTH). A
        // command asks whenever the command gate is on AND this session has not already proven the
        // PIN.
        GateEvent.SEND_COMMAND ->
            locksOnCommand(timing) &&
                !(locksOnOpen(timing) && alreadyUnlockedThisSession)
    }

    /** Which interlock the UI is evaluating. */
    enum class GateEvent { OPEN_APP, SEND_COMMAND }
}
