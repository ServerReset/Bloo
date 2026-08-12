package com.bloo.bluelink.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-JVM coverage for [EuApi.normalizeBattery12V], the 12V-reading filter added
 * after an audit found the CCS2 parse path skipping the sentinel/reliability
 * guard the reference project (`normalize_battery_soc` in
 * hyundai_kia_connect_api's utils.py) applies to the exact same field. Without
 * it, a documented sentinel (255/0xFF, an early ICCU-failure symptom on IONIQ 5 /
 * Kia EV) or an explicit "sensor unreliable" flag would have rendered as a real
 * percentage -- "255% . Good" -- instead of the unknown state the car is
 * actually reporting.
 *
 * Also covers [EuApi.parseStatus]'s plug-detection field, which the same audit
 * found reading the charge-port DOOR's open/closed state instead of whether a
 * cable is actually connected.
 */
class EuApiTest {

    private val api = EuApi(Brand.HYUNDAI_EU)

    // ---- plug detection: ConnectorFastening.State, not ChargingDoor.State ----

    /** Minimal CCS2 `Green` tree with just the fields [EuApi.parseStatus] reads
     *  for plug state, so a fixture only has to say what this test cares about. */
    private fun greenFixture(chargingDoorState: Int?, connectorFasteningState: Int?) = buildJsonObject {
        putJsonObject("Green") {
            if (chargingDoorState != null) {
                putJsonObject("ChargingDoor") { put("State", chargingDoorState) }
            }
            putJsonObject("ChargingInformation") {
                if (connectorFasteningState != null) {
                    putJsonObject("ConnectorFastening") { put("State", connectorFasteningState) }
                }
            }
        }
    }

    @Test
    fun `an open charge-port door with nothing plugged in is not read as plugged in`() {
        // The exact real-device shape reported: door open (ChargingDoor.State == 1),
        // no cable connected (ConnectorFastening.State == 0). The old code read
        // ChargingDoor.State directly and would have called this "Plugged in (DC fast)".
        val status = api.parseStatus(greenFixture(chargingDoorState = 1, connectorFasteningState = 0))
        assertEquals(0, status.evStatus?.batteryPlugin)
    }

    @Test
    fun `a closed door over a connected cable is read as plugged in`() {
        // The reverse real shape: door auto-closed over an inserted cable
        // (ChargingDoor.State == 0), connector actually fastened (== 1).
        val status = api.parseStatus(greenFixture(chargingDoorState = 0, connectorFasteningState = 1))
        assertEquals(1, status.evStatus?.batteryPlugin)
    }

    @Test
    fun `plug state tracks the connector field regardless of the door field`() {
        for (door in listOf(0, 1, 2)) {
            assertEquals(
                0, api.parseStatus(greenFixture(door, connectorFasteningState = 0)).evStatus?.batteryPlugin,
                "door=$door should not affect an unplugged connector reading",
            )
            assertEquals(
                1, api.parseStatus(greenFixture(door, connectorFasteningState = 1)).evStatus?.batteryPlugin,
                "door=$door should not affect a plugged connector reading",
            )
        }
    }

    // ---- normalizeBattery12V ----

    @Test
    fun `an ordinary reading passes through unchanged`() {
        assertEquals(80, api.normalizeBattery12V(level = 80, sensorReliability = 0))
        assertEquals(0, api.normalizeBattery12V(level = 0, sensorReliability = null))
        assertEquals(100, api.normalizeBattery12V(level = 100, sensorReliability = 2))
    }

    @Test
    fun `sensorReliability of exactly 1 means unreliable, regardless of the value`() {
        assertNull(api.normalizeBattery12V(level = 80, sensorReliability = 1))
        // Other reliability codes are not the "unreliable" flag -- only 1 is.
        assertEquals(80, api.normalizeBattery12V(level = 80, sensorReliability = 0))
        assertEquals(80, api.normalizeBattery12V(level = 80, sensorReliability = 2))
    }

    @Test
    fun `out-of-range values are sentinels, not real percentages`() {
        // The documented 0xFF sentinel.
        assertNull(api.normalizeBattery12V(level = 255, sensorReliability = 0))
        assertNull(api.normalizeBattery12V(level = -1, sensorReliability = 0))
        assertNull(api.normalizeBattery12V(level = 101, sensorReliability = 0))
        // The boundaries themselves are valid.
        assertEquals(0, api.normalizeBattery12V(level = 0, sensorReliability = 0))
        assertEquals(100, api.normalizeBattery12V(level = 100, sensorReliability = 0))
    }

    @Test
    fun `a missing level is unknown, not zero`() {
        assertNull(api.normalizeBattery12V(level = null, sensorReliability = 0))
        assertNull(api.normalizeBattery12V(level = null, sensorReliability = null))
    }
}
