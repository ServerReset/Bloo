package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class WearAutoLockTest {
    private fun car(vin: String, locked: Boolean?, engineOn: Boolean? = false) =
        VehicleSnapshot(vin = vin, name = vin, model = "M", isEv = true, locked = locked, engineOn = engineOn)

    @Test fun locksTheCarsThatAreNotAlreadyLockedAndNotRunning() {
        val targets = WearAutoLock.targets(
            listOf(
                car("A", locked = false),
                car("B", locked = true),
                car("C", locked = null),
            ),
        )
        assertEquals(listOf("A", "C"), targets)
    }

    @Test fun neverLocksACarWithTheEngineRunning() {
        val targets = WearAutoLock.targets(
            listOf(
                car("A", locked = false, engineOn = true),
                car("B", locked = false, engineOn = false),
            ),
        )
        assertEquals(listOf("B"), targets)
    }

    @Test fun anUnknownLockStateStillLocks() {
        // Locking an already-locked car is a harmless no-op, and many cars report no lock state at all.
        assertEquals(listOf("A"), WearAutoLock.targets(listOf(car("A", locked = null))))
    }

    @Test fun noCarsMeansNothingToDo() {
        assertEquals(emptyList(), WearAutoLock.targets(emptyList()))
    }

    @Test fun itIsTaggedBeta() {
        assertTrue(WearAutoLock.BETA)
        assertFalse(WearAutoLock.GRACE_MS <= 0L)
    }
}
