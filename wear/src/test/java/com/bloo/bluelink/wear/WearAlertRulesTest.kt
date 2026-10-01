package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchNotifyPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WearAlertRulesTest {
    private fun car(pct: Int?, charging: Boolean?) =
        VehicleSnapshot(vin = "V", name = "Car", model = "M", isEv = true, percent = pct, charging = charging)

    private val allOn = WatchNotifyPrefs(charging = true, chargeComplete = true, lowBattery = true)

    @Test fun chargingCardFollowsChargingAndThePreference() {
        assertTrue(planWearAlerts(car(50, true), car(51, true), allOn).chargingCard)
        assertFalse(planWearAlerts(car(50, true), car(51, true), allOn.copy(charging = false)).chargingCard)
        assertFalse(planWearAlerts(car(50, false), car(50, false), allOn).chargingCard)
    }

    @Test fun chargedFiresOnceWhenAChargeEndsNearFull() {
        assertTrue(planWearAlerts(car(99, true), car(100, false), allOn).chargeComplete)
        assertFalse(planWearAlerts(car(100, false), car(100, false), allOn).chargeComplete)
    }

    @Test fun chargedDoesNotFireForAnInterruptedLowCharge() {
        assertFalse(planWearAlerts(car(40, true), car(40, false), allOn).chargeComplete)
    }

    @Test fun chargedRespectsItsPreference() {
        assertFalse(planWearAlerts(car(99, true), car(100, false), allOn.copy(chargeComplete = false)).chargeComplete)
    }

    @Test fun lowBatteryFiresOnlyOnTheStepAcrossTheLine() {
        assertTrue(planWearAlerts(car(20, false), car(19, false), allOn).lowBattery)
        assertFalse(planWearAlerts(car(19, false), car(18, false), allOn).lowBattery)
        assertFalse(planWearAlerts(car(30, false), car(25, false), allOn).lowBattery)
    }

    @Test fun lowBatteryIsSilentWhileCharging() {
        assertFalse(planWearAlerts(car(20, true), car(19, true), allOn).lowBattery)
    }

    @Test fun aCarSeenForTheFirstTimeRaisesNoEventAlerts() {
        val plan = planWearAlerts(null, car(5, false), allOn)
        assertFalse(plan.chargeComplete)
        assertFalse(plan.lowBattery)
    }

    @Test fun unknownChargeLevelsRaiseNothing() {
        assertEquals(WearAlertPlan(false, false, false), planWearAlerts(car(null, false), car(null, false), allOn))
    }
}
