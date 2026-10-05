package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.VehicleStatus

/** Outcome of evaluating a status snapshot against the auto-lock policy. */
sealed interface LockDecision {
    data object Lock : LockDecision
    data class Skip(val reason: String) : LockDecision
}

/** Pure policy: should AutoLock attempt to lock, given this status? */
object LockPolicy {
    fun decide(status: VehicleStatus): LockDecision = when {
        status.doorLock == true -> LockDecision.Skip("Already locked")
        status.doorLock == null -> LockDecision.Skip("Lock state unknown")
        status.engine == true -> LockDecision.Skip("Engine running")
        status.doorOpen?.anyOpen == true -> LockDecision.Skip("A door is open")
        status.windowOpen?.anyOpen == true -> LockDecision.Skip("A window is open")
        else -> LockDecision.Lock
    }
}
