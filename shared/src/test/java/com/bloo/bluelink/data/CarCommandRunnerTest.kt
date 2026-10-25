package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pure-JVM tests for [CarCommandRunner]'s pure functions -- [resolveToggle]
 * and [optimistic]. They had no coverage at all, which is a
 * poor place for this app to have none: between them they decide whether a tap
 * sends LOCK or UNLOCK to somebody's car, and every
 * command surface routes through them.
 *
 * The functions themselves take a [VehicleSnapshot] and return a value, so none of
 * this needs Android, DataStore or a network -- same shape as [SyncMergeTest].
 *
 * What makes them worth pinning rather than reading is that they are three
 * functions that have to agree with each other AND with the `when` inside
 * [CarCommandRunner.execute], which independently re-derives the same toggle
 * directions from the same fields. The contract that ties them together is stated
 * in [resolveToggle]'s own docstring: a caller that writes [optimistic] to the
 * store BEFORE the command runs must resolve first, because [execute] decides
 * direction by re-reading that store. Get it wrong and the car does the opposite of
 * what the user tapped -- which has happened here twice, once on the widget and
 * once on the watch tile, per the comments at both call sites.
 */
class CarCommandRunnerTest {

    // Minimal snapshot; every field the functions under test touch is nullable and
    // defaults to null, which is itself one of the cases that matters most below.
    private fun snap(
        locked: Boolean? = null,
        climateOn: Boolean? = null,
        charging: Boolean? = null,
        brandIndicator: String = "H",
    ) = VehicleSnapshot(
        vin = "VIN1",
        name = "Test Car",
        model = "Ioniq 5",
        isEv = true,
        locked = locked,
        charging = charging,
        climateOn = climateOn,
        brandIndicator = brandIndicator,
    )

    // ---- resolveToggle: does a toggle pick the direction the user expects? ----

    @Test
    fun resolveToggleFlipsAgainstKnownState() {
        assertEquals(CarAction.UNLOCK, CarCommandRunner.resolveToggle(snap(locked = true), CarAction.TOGGLE_LOCK))
        assertEquals(CarAction.LOCK, CarCommandRunner.resolveToggle(snap(locked = false), CarAction.TOGGLE_LOCK))

        assertEquals(CarAction.CLIMATE_OFF, CarCommandRunner.resolveToggle(snap(climateOn = true), CarAction.TOGGLE_CLIMATE))
        assertEquals(CarAction.CLIMATE_ON, CarCommandRunner.resolveToggle(snap(climateOn = false), CarAction.TOGGLE_CLIMATE))

        assertEquals(CarAction.CHARGE_OFF, CarCommandRunner.resolveToggle(snap(charging = true), CarAction.TOGGLE_CHARGE))
        assertEquals(CarAction.CHARGE_ON, CarCommandRunner.resolveToggle(snap(charging = false), CarAction.TOGGLE_CHARGE))
    }

    /**
     * An UNKNOWN state resolves to the "on" direction, not to the "off" one, for all
     * three toggles. This is the deliberate reading of `== true`: a car that has
     * never reported its doors is treated as not-locked, so the toggle locks it.
     *
     * Worth a test of its own because locking a car the app knows nothing about is
     * the safe failure and unlocking it is not, so if anyone ever "simplifies" these
     * to `!snap.locked!!` or flips the comparison, that is the direction that must
     * not silently change.
     */
    @Test
    fun resolveToggleTreatsUnknownAsOff() {
        assertEquals(CarAction.LOCK, CarCommandRunner.resolveToggle(snap(), CarAction.TOGGLE_LOCK))
        assertEquals(CarAction.CLIMATE_ON, CarCommandRunner.resolveToggle(snap(), CarAction.TOGGLE_CLIMATE))
        assertEquals(CarAction.CHARGE_ON, CarCommandRunner.resolveToggle(snap(), CarAction.TOGGLE_CHARGE))
    }

    /** Already-explicit verbs and the momentary ones pass through untouched --
     *  resolving must be safe to apply to any action, since both call sites run it
     *  over whatever verb arrives rather than checking first. */
    @Test
    fun resolveTogglePassesThroughNonToggles() {
        val s = snap(locked = true, climateOn = true, charging = true)
        for (action in listOf(
            CarAction.LOCK, CarAction.UNLOCK,
            CarAction.CLIMATE_ON, CarAction.CLIMATE_OFF,
            CarAction.CHARGE_ON, CarAction.CHARGE_OFF,
            CarAction.FLASH_LIGHTS, CarAction.HORN_AND_LIGHTS,
            CarAction.SET_CHARGE_LIMITS, CarAction.REFRESH,
        )) {
            assertEquals(action, CarCommandRunner.resolveToggle(s, action), "resolveToggle changed $action")
        }
    }

    // ---- optimistic: does the predicted snapshot match the command sent? ----

    /**
     * THE load-bearing invariant, and the reason this file exists: resolving a
     * toggle and then predicting its result must land on the same snapshot as
     * predicting the toggle directly. If these two ever disagree, the state the user
     * sees immediately after tapping is not the state the command produces.
     *
     * Checked across every combination of the three tri-state fields, including
     * null, rather than a couple of hand-picked cases -- it is cheap here and this
     * is exactly the arithmetic that has been wrong before.
     */
    @Test
    fun resolveThenPredictAgreesWithPredictingTheToggle() {
        val tri = listOf(true, false, null)
        for (l in tri) for (c in tri) for (ch in tri) {
            val s = snap(locked = l, climateOn = c, charging = ch)
            for (toggle in listOf(CarAction.TOGGLE_LOCK, CarAction.TOGGLE_CLIMATE, CarAction.TOGGLE_CHARGE)) {
                val viaResolve = CarCommandRunner.optimistic(s, CarCommandRunner.resolveToggle(s, toggle))
                val direct = CarCommandRunner.optimistic(s, toggle)
                assertEquals(direct, viaResolve, "$toggle disagreed at locked=$l climateOn=$c charging=$ch")
            }
        }
    }

    /** A toggle's prediction flips only its own field and leaves the other two
     *  exactly as they were, including when they are unknown. Guards against a
     *  copy/paste slip in [optimistic]'s `when`, where all nine branches are one
     *  line of the same shape. */
    @Test
    fun optimisticTouchesOnlyItsOwnField() {
        val s = snap(locked = true, climateOn = null, charging = false)

        val afterLock = CarCommandRunner.optimistic(s, CarAction.TOGGLE_LOCK)
        assertEquals(false, afterLock.locked)
        assertNull(afterLock.climateOn)
        assertEquals(false, afterLock.charging)

        val afterClimate = CarCommandRunner.optimistic(s, CarAction.TOGGLE_CLIMATE)
        assertEquals(true, afterClimate.climateOn)
        assertEquals(true, afterClimate.locked)
        assertEquals(false, afterClimate.charging)

        val afterCharge = CarCommandRunner.optimistic(s, CarAction.CHARGE_ON)
        assertEquals(true, afterCharge.charging)
        assertEquals(true, afterCharge.locked)
        assertNull(afterCharge.climateOn)
    }

    /**
     * A brand that cannot report climate state ([Brand.reportsClimateState] false --
     * today, only Hyundai EU) must never predict a climate boolean, in either
     * direction, from any of the three climate verbs -- it must predict `null`
     * ("unknown"), exactly as [CarCommandRunner.execute]'s own brand-aware
     * `climateFlag` helper already does for its optimistic write.
     *
     * This is [TileCommandRunner]'s counterpart to that fix: it calls
     * [optimistic] directly rather than going through [CarCommandRunner.execute],
     * so painting a confident "Climate on" for a car whose backend can never
     * confirm it was reachable straight from a command tap. Lock and charge are
     * unaffected -- EU reports both -- which the middle assertions pin so a
     * blanket `climateKnown` check applied to the wrong field would fail loudly.
     */
    @Test
    fun optimisticNeverGuessesClimateForABrandThatCannotReportIt() {
        val onCar = snap(climateOn = true, brandIndicator = "HEU")
        val offCar = snap(climateOn = false, brandIndicator = "HEU")
        val unknownCar = snap(climateOn = null, brandIndicator = "HEU")

        assertNull(CarCommandRunner.optimistic(onCar, CarAction.CLIMATE_OFF).climateOn)
        assertNull(CarCommandRunner.optimistic(offCar, CarAction.CLIMATE_ON).climateOn)
        assertNull(CarCommandRunner.optimistic(unknownCar, CarAction.TOGGLE_CLIMATE).climateOn)
        assertNull(CarCommandRunner.optimistic(onCar, CarAction.TOGGLE_CLIMATE).climateOn)

        // Lock and charge are untouched by the climate gate.
        val lockOff = CarCommandRunner.optimistic(snap(locked = false, brandIndicator = "HEU"), CarAction.LOCK)
        assertEquals(true, lockOff.locked)
        val chargeOff = CarCommandRunner.optimistic(snap(charging = false, brandIndicator = "HEU"), CarAction.CHARGE_ON)
        assertEquals(true, chargeOff.charging)
    }

    /** The momentary and non-stateful verbs make the car do something visible but
     *  change nothing any surface displays, so they must predict the snapshot
     *  UNCHANGED -- identically, not merely equal. The comment in [execute] promises
     *  they "fall through optimistic() untouched"; this holds it to that. */
    @Test
    fun optimisticReturnsSameInstanceForNonStatefulVerbs() {
        val s = snap(locked = true, climateOn = true, charging = true)
        for (action in listOf(
            CarAction.FLASH_LIGHTS, CarAction.HORN_AND_LIGHTS,
            CarAction.SET_CHARGE_LIMITS, CarAction.REFRESH, "some_future_verb",
        )) {
            assertSame(s, CarCommandRunner.optimistic(s, action), "$action should not predict a change")
        }
    }

    /** The optimistic flip must actually CHANGE the field, from every tri-state starting value (null included). */
    @Test
    fun theOptimisticFlipAlwaysChangesTheCapturedField() {
        for (before in listOf(true, false, null)) {
            val start = snap(locked = before)
            val resolved = CarCommandRunner.resolveToggle(start, CarAction.TOGGLE_LOCK)
            val flipped = CarCommandRunner.optimistic(start, resolved)
            assertEquals(before != true, flipped.locked, "flip from $before went the wrong way")
        }
    }

    @Test
    fun smartClimateIsAClimateToggle() {
        assertEquals(CarAction.CLIMATE_OFF, CarCommandRunner.resolveToggle(snap(climateOn = true), CarAction.CLIMATE_SMART))
        assertEquals(CarAction.CLIMATE_ON, CarCommandRunner.resolveToggle(snap(climateOn = false), CarAction.CLIMATE_SMART))
        assertEquals(true, CarCommandRunner.optimistic(snap(climateOn = false), CarAction.CLIMATE_SMART).climateOn)
    }

    @Test
    fun aCommandCarriesAPresetsClimateSettings() {
        val req = ClimateRequest(
            tempF = 71, defrost = true, durationMinutes = 7,
            steeringWheelHeat = WheelHeatLevel.fromApi(1), seatFrontLeft = SeatLevel.fromApi(2),
        )
        val cmd = CarCommand("VIN1", CarAction.CLIMATE_ON).withClimate(req)
        assertEquals(71, cmd.tempF)
        assertEquals(7, cmd.durationMinutes)
        assertEquals(true, cmd.defrost)
        assertEquals(req.steeringWheelHeat.apiValue, cmd.steeringWheelHeat)
        assertEquals(req.seatFrontLeft.apiValue, cmd.seatFrontLeft)
        // No request: the command is left as it was.
        assertEquals(CarCommand("VIN1", CarAction.CLIMATE_ON), CarCommand("VIN1", CarAction.CLIMATE_ON).withClimate(null))
    }

    @Test
    fun smartClimateRequestFollowsTheWeather() {
        val hot = smartClimateRequest(Weather(tempC = 38.0, feelsLikeC = 38.0, highC = null, lowC = null, windKph = 0.0, humidity = null, isDay = true, code = 0))
        assertEquals(smartClimateTargetF(ambientFahrenheit(38.0)), hot.tempF)
    }
}
