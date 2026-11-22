package com.bloo.bluelink.autolock

import com.bloo.bluelink.ui.AppViewModel

import com.bloo.bluelink.data.VehicleStatus

/** Outcome of evaluating a status snapshot against the auto-lock policy. */
sealed interface LockDecision {
    data object Lock : LockDecision
    data class Skip(val reason: String) : LockDecision
}

/**
 * Pure policy: should AutoLock attempt to lock, given this status? Ported from
 * i5-AutoLock's `LockPolicy`, onto Bloo's own [VehicleStatus] shape (`doorLock == true`
 * means locked here, matching [AppViewModel.lock]'s own convention).
 */
object LockPolicy {
    /** An open door or window does not stop the lock: a car locked around an open window is still better than one left unlocked. */
    fun decide(status: VehicleStatus): LockDecision = when {
        status.doorLock == true -> LockDecision.Skip("Already locked")
        status.doorLock == null -> LockDecision.Skip("Lock state unknown")
        status.engine == true -> LockDecision.Skip("Engine running")
        else -> LockDecision.Lock
    }
}
