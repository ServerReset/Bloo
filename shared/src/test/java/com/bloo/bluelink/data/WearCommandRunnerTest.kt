package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pure-JVM tests for [WearCommandRunner]'s pure functions -- [resolveToggle],
 * [optimistic], [stateFor] and [withState]. They had no coverage at all, which is a
 * poor place for this app to have none: between them they decide whether a tap on a
 * widget button or a watch tile sends LOCK or UNLOCK to somebody's car, and every
 * out-of-process surface routes through them.
 *
 * The functions themselves take a [VehicleSnapshot] and return a value, so none of
 * this needs Android, DataStore or a network -- same shape as [SyncMergeTest].
 *
 * What makes them worth pinning rather than reading is that they are three
 * functions that have to agree with each other AND with the `when` inside
 * [WearCommandRunner.execute], which independently re-derives the same toggle
 * directions from the same fields. The contract that ties them together is stated
 * in [resolveToggle]'s own docstring: a caller that writes [optimistic] to the
 * store BEFORE the command runs must resolve first, because [execute] decides
 * direction by re-reading that store. Get it wrong and the car does the opposite of
 * what the user tapped -- which has happened here twice, once on the widget and
 * once on the watch tile, per the comments at both call sites.
 */
class WearCommandRunnerTest {

    // Minimal snapshot; every field the functions under test touch is nullable and
    // defaults to null, which is itself one of the cases that matters most below.
    private fun snap(
        locked: Boolean? = null,
        climateOn: Boolean? = null,
        charging: Boolean? = null,
    ) = VehicleSnapshot(
        vin = "VIN1",
        name = "Test Car",
        model = "Ioniq 5",
        isEv = true,
        locked = locked,
        charging = charging,
        climateOn = climateOn,
    )

    // ---- resolveToggle: does a toggle pick the direction the user expects? ----

    @Test
    fun resolveToggleFlipsAgainstKnownState() {
        assertEquals(WearAction.UNLOCK, WearCommandRunner.resolveToggle(snap(locked = true), WearAction.TOGGLE_LOCK))
        assertEquals(WearAction.LOCK, WearCommandRunner.resolveToggle(snap(locked = false), WearAction.TOGGLE_LOCK))

        assertEquals(WearAction.CLIMATE_OFF, WearCommandRunner.resolveToggle(snap(climateOn = true), WearAction.TOGGLE_CLIMATE))
        assertEquals(WearAction.CLIMATE_ON, WearCommandRunner.resolveToggle(snap(climateOn = false), WearAction.TOGGLE_CLIMATE))

        assertEquals(WearAction.CHARGE_OFF, WearCommandRunner.resolveToggle(snap(charging = true), WearAction.TOGGLE_CHARGE))
        assertEquals(WearAction.CHARGE_ON, WearCommandRunner.resolveToggle(snap(charging = false), WearAction.TOGGLE_CHARGE))
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
        assertEquals(WearAction.LOCK, WearCommandRunner.resolveToggle(snap(), WearAction.TOGGLE_LOCK))
        assertEquals(WearAction.CLIMATE_ON, WearCommandRunner.resolveToggle(snap(), WearAction.TOGGLE_CLIMATE))
        assertEquals(WearAction.CHARGE_ON, WearCommandRunner.resolveToggle(snap(), WearAction.TOGGLE_CHARGE))
    }

    /** Already-explicit verbs and the momentary ones pass through untouched --
     *  resolving must be safe to apply to any action, since both call sites run it
     *  over whatever verb arrives rather than checking first. */
    @Test
    fun resolveTogglePassesThroughNonToggles() {
        val s = snap(locked = true, climateOn = true, charging = true)
        for (action in listOf(
            WearAction.LOCK, WearAction.UNLOCK,
            WearAction.CLIMATE_ON, WearAction.CLIMATE_OFF,
            WearAction.CHARGE_ON, WearAction.CHARGE_OFF,
            WearAction.FLASH_LIGHTS, WearAction.HORN_AND_LIGHTS,
            WearAction.SET_CHARGE_LIMITS, WearAction.REFRESH,
        )) {
            assertEquals(action, WearCommandRunner.resolveToggle(s, action), "resolveToggle changed $action")
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
            for (toggle in listOf(WearAction.TOGGLE_LOCK, WearAction.TOGGLE_CLIMATE, WearAction.TOGGLE_CHARGE)) {
                val viaResolve = WearCommandRunner.optimistic(s, WearCommandRunner.resolveToggle(s, toggle))
                val direct = WearCommandRunner.optimistic(s, toggle)
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

        val afterLock = WearCommandRunner.optimistic(s, WearAction.TOGGLE_LOCK)
        assertEquals(false, afterLock.locked)
        assertNull(afterLock.climateOn)
        assertEquals(false, afterLock.charging)

        val afterClimate = WearCommandRunner.optimistic(s, WearAction.TOGGLE_CLIMATE)
        assertEquals(true, afterClimate.climateOn)
        assertEquals(true, afterClimate.locked)
        assertEquals(false, afterClimate.charging)

        val afterCharge = WearCommandRunner.optimistic(s, WearAction.CHARGE_ON)
        assertEquals(true, afterCharge.charging)
        assertEquals(true, afterCharge.locked)
        assertNull(afterCharge.climateOn)
    }

    /** The momentary and non-stateful verbs make the car do something visible but
     *  change nothing any surface displays, so they must predict the snapshot
     *  UNCHANGED -- identically, not merely equal. The comment in [execute] promises
     *  they "fall through optimistic() untouched"; this holds it to that. */
    @Test
    fun optimisticReturnsSameInstanceForNonStatefulVerbs() {
        val s = snap(locked = true, climateOn = true, charging = true)
        for (action in listOf(
            WearAction.FLASH_LIGHTS, WearAction.HORN_AND_LIGHTS,
            WearAction.SET_CHARGE_LIMITS, WearAction.REFRESH, "some_future_verb",
        )) {
            assertSame(s, WearCommandRunner.optimistic(s, action), "$action should not predict a change")
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

        for (a in listOf(WearAction.TOGGLE_LOCK, WearAction.LOCK, WearAction.UNLOCK)) {
            assertEquals(true, WearCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        for (a in listOf(WearAction.TOGGLE_CLIMATE, WearAction.CLIMATE_ON, WearAction.CLIMATE_OFF)) {
            assertEquals(false, WearCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        for (a in listOf(WearAction.TOGGLE_CHARGE, WearAction.CHARGE_ON, WearAction.CHARGE_OFF)) {
            assertNull(WearCommandRunner.stateFor(s, a), "wrong field for $a")
        }
        // Verbs that touch no stateful field have nothing to capture.
        for (a in listOf(WearAction.FLASH_LIGHTS, WearAction.SET_CHARGE_LIMITS, WearAction.REFRESH)) {
            assertNull(WearCommandRunner.stateFor(s, a))
        }
    }

    /** [withState] writes back to the same field [stateFor] read from, and leaves
     *  the other two alone -- the other half of the crossed-wire check above. */
    @Test
    fun withStateWritesOnlyTheFieldTheActionTouches() {
        val s = snap(locked = true, climateOn = true, charging = true)

        val l = WearCommandRunner.withState(s, WearAction.LOCK, null)
        assertNull(l.locked)
        assertEquals(true, l.climateOn)
        assertEquals(true, l.charging)

        val c = WearCommandRunner.withState(s, WearAction.CLIMATE_OFF, false)
        assertEquals(false, c.climateOn)
        assertEquals(true, c.locked)
        assertEquals(true, c.charging)

        val ch = WearCommandRunner.withState(s, WearAction.TOGGLE_CHARGE, null)
        assertNull(ch.charging)
        assertEquals(true, ch.locked)
        assertEquals(true, ch.climateOn)

        assertSame(s, WearCommandRunner.withState(s, WearAction.FLASH_LIGHTS, false))
    }

    /**
     * THE reason [stateFor] and [withState] exist, and the bug they replaced.
     *
     * Both revert sites -- WidgetActions' WidgetCommandWorker and WearComms'
     * runStandalone -- capture before the optimistic flip and restore after a
     * failure. This asserts the full round trip holds for EVERY tri-state starting
     * value, including null.
     *
     * Null is the case that used to break. The revert was
     * `optimistic(snap, inverse(action))`, which is an undo only when the flip
     * changed something: on a car that had never reported its doors, the flip wrote
     * `true` over a null and the inverse wrote `false`, so a failed command left the
     * widget or tile stating that a car it knew nothing about was unlocked. The
     * information needed to do better was gone by then -- optimistic() writes an
     * absolute value. Capturing beforehand is what makes null recoverable, and it is
     * worth recovering: the widget's StatusGlyph draws nothing at all for an unknown
     * lock state rather than guess, and the old revert quietly defeated that.
     */
    @Test
    fun captureThenRestoreRoundTripsEveryTriState() {
        for (before in listOf(true, false, null)) {
            for (toggle in listOf(WearAction.TOGGLE_LOCK, WearAction.TOGGLE_CLIMATE, WearAction.TOGGLE_CHARGE)) {
                val start = when (toggle) {
                    WearAction.TOGGLE_LOCK -> snap(locked = before)
                    WearAction.TOGGLE_CLIMATE -> snap(climateOn = before)
                    else -> snap(charging = before)
                }
                // Exactly the sequence both call sites run.
                val resolved = WearCommandRunner.resolveToggle(start, toggle)
                val captured = WearCommandRunner.stateFor(start, resolved)
                val flipped = WearCommandRunner.optimistic(start, resolved)
                val reverted = WearCommandRunner.withState(flipped, resolved, captured)

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
            val resolved = WearCommandRunner.resolveToggle(start, WearAction.TOGGLE_LOCK)
            val flipped = WearCommandRunner.optimistic(start, resolved)
            assertEquals(before != true, flipped.locked, "flip from $before went the wrong way")
        }
    }
}
