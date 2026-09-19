package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pure-JVM tests for [CarCommandRunner]'s pure functions -- [resolveToggle],
 * [optimistic], [stateFor] and [withState]. They had no coverage at all, which is a
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

    // ---- stateFor / withState: reverting a failed command ----

    /** [stateFor] reads the field the action's prediction will overwrite, and reads
     *  the RIGHT one -- given three same-shaped branches over three same-typed
     *  fields, a crossed wire here would revert the wrong thing and be invisible in
     *  every other test. Checked with three distinct values so no two can be
     *  confused. */
    @Test
    fun stateForReadsTheFieldTheActionTouches() {
        val s = snap(locked = true, climateOn = false, charging = null)

        for (a in listOf(CarAction.TOGGLE_LOCK, CarAction.LOCK, CarAction.UNLOCK)) {
            assertEquals(true, CarCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        for (a in listOf(CarAction.TOGGLE_CLIMATE, CarAction.CLIMATE_ON, CarAction.CLIMATE_OFF)) {
            assertEquals(false, CarCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        for (a in listOf(CarAction.TOGGLE_CHARGE, CarAction.CHARGE_ON, CarAction.CHARGE_OFF)) {
            assertNull(CarCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        // Verbs that touch no stateful field have nothing to capture.
        for (a in listOf(CarAction.FLASH_LIGHTS, CarAction.SET_CHARGE_LIMITS, CarAction.REFRESH)) {
            assertNull(CarCommandRunner.stateFor(s, a))
        }
    }

    /** [withState] writes back to the same field [stateFor] read from, and leaves
     *  the other two alone -- the other half of the crossed-wire check above. */
    @Test
    fun withStateWritesOnlyTheFieldTheActionTouches() {
        val s = snap(locked = true, climateOn = true, charging = true)

        val l = CarCommandRunner.withState(s, CarAction.LOCK, null)
        assertNull(l.locked)
        assertEquals(true, l.climateOn)
        assertEquals(true, l.charging)

        val c = CarCommandRunner.withState(s, CarAction.CLIMATE_OFF, false)
        assertEquals(false, c.climateOn)
        assertEquals(true, c.locked)
        assertEquals(true, c.charging)

        val ch = CarCommandRunner.withState(s, CarAction.TOGGLE_CHARGE, null)
        assertNull(ch.charging)
        assertEquals(true, ch.locked)
        assertEquals(true, ch.climateOn)

        assertSame(s, CarCommandRunner.withState(s, CarAction.FLASH_LIGHTS, false))
    }

    /**
     * THE reason [stateFor] and [withState] exist, and the bug they replaced.
     *
     * The revert sites that used to call these -- WidgetActions' WidgetCommandWorker
     * and MainToSecondaryComms' runStandalone, both since removed -- captured before
     * the optimistic flip and restored after a failure. This asserts the full round
     * trip holds for EVERY tri-state starting value, including null.
     *
     * Null is the case that used to break. The revert was
     * `optimistic(snap, inverse(action))`, which is an undo only when the flip
     * changed something: on a car that had never reported its doors, the flip wrote
     * `true` over a null and the inverse wrote `false`, so a failed command left a
     * surface stating that a car it knew nothing about was unlocked. The
     * information needed to do better was gone by then -- optimistic() writes an
     * absolute value. Capturing beforehand is what makes null recoverable, and it is
     * worth recovering: the phone UI draws nothing at all for an unknown
     * lock state rather than guess, and the old revert quietly defeated that.
     */
    @Test
    fun captureThenRestoreRoundTripsEveryTriState() {
        for (before in listOf(true, false, null)) {
            for (toggle in listOf(CarAction.TOGGLE_LOCK, CarAction.TOGGLE_CLIMATE, CarAction.TOGGLE_CHARGE)) {
                val start = when (toggle) {
                    CarAction.TOGGLE_LOCK -> snap(locked = before)
                    CarAction.TOGGLE_CLIMATE -> snap(climateOn = before)
                    else -> snap(charging = before)
                }
                // Exactly the sequence both call sites run.
                val resolved = CarCommandRunner.resolveToggle(start, toggle)
                val captured = CarCommandRunner.stateFor(start, resolved)
                val flipped = CarCommandRunner.optimistic(start, resolved)
                val reverted = CarCommandRunner.withState(flipped, resolved, captured)

                assertEquals(before, captured, "$toggle captured the wrong value from $before")
                assertEquals(start, reverted, "$toggle failed to round-trip from $before")
            }
        }
    }

    /** The optimistic flip must actually CHANGE something, or the revert would have
     *  nothing to undo and the round-trip test above would pass trivially. */
    @Test
    fun theOptimisticFlipAlwaysChangesTheCapturedField() {
        for (before in listOf(true, false, null)) {
            val start = snap(locked = before)
            val resolved = CarCommandRunner.resolveToggle(start, CarAction.TOGGLE_LOCK)
            val flipped = CarCommandRunner.optimistic(start, resolved)
            assertEquals(before != true, flipped.locked, "flip from $before went the wrong way")
        }
    }
}
