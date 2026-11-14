package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.VehicleStatus

/** Outcome of evaluating a status snapshot against the auto-lock policy. */
sealed interface LockDecision {
    data object Lock : LockDecision
    data class Skip(val reason: String) : LockDecision
}

/**
 * Pure policy: should AutoLock attempt to lock, given this status? Ported from
 * i5-AutoLock's `LockPolicy`, onto Bloo's own [VehicleStatus] shape (`doorLock == true`
 * means locked here, matching [com.bloo.bluelink.ui.AppViewModel.lock]'s own convention).
 */
object LockPolicy {
    fun decide(status: VehicleStatus, dontLockIfOpen: Boolean): LockDecision = when {
        status.doorLock == true -> LockDecision.Skip("Already locked")
        status.doorLock == null -> LockDecision.Skip("Lock state unknown")
        status.engine == true -> LockDecision.Skip("Engine running")
        dontLockIfOpen && status.doorOpen?.anyOpen == true -> LockDecision.Skip("A door is open")
        dontLockIfOpen && status.windowOpen?.anyOpen == true -> LockDecision.Skip("A window is open")
        else -> LockDecision.Lock
    }
}
