package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pure-JVM tests for [WearCommandRunner]'s three pure functions -- [resolveToggle],
 * [optimistic] and [inverse]. They had no coverage at all, which is a poor place for
 * this app to have none: between them they decide whether a tap on a widget button
 * or a watch tile sends LOCK or UNLOCK to somebody's car, and every out-of-process
 * surface routes through them.
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

    // ---- inverse: reverting a failed command ----

    @Test
    fun inverseSwapsExplicitVerbsAndIsItsOwnUndo() {
        val pairs = listOf(
            WearAction.LOCK to WearAction.UNLOCK,
            WearAction.CLIMATE_ON to WearAction.CLIMATE_OFF,
            WearAction.CHARGE_ON to WearAction.CHARGE_OFF,
        )
        for ((on, off) in pairs) {
            assertEquals(off, WearCommandRunner.inverse(on))
            assertEquals(on, WearCommandRunner.inverse(off))
            // Applying inverse twice is the identity, which is what makes it usable
            // as "undo" at all.
            assertEquals(on, WearCommandRunner.inverse(WearCommandRunner.inverse(on)))
        }
    }

    /** TOGGLE_* is its own inverse, and the momentary verbs invert to themselves
     *  (there is nothing to undo). */
    @Test
    fun inverseIsIdentityForTogglesAndMomentaryVerbs() {
        for (action in listOf(
            WearAction.TOGGLE_LOCK, WearAction.TOGGLE_CLIMATE, WearAction.TOGGLE_CHARGE,
            WearAction.FLASH_LIGHTS, WearAction.HORN_AND_LIGHTS, WearAction.SET_CHARGE_LIMITS,
        )) {
            assertEquals(action, WearCommandRunner.inverse(action))
        }
    }

    /**
     * Reverting a failed command with [inverse] restores the original state only
     * when the original was known. When it was null, the revert invents a definite
     * `false` out of nothing.
     *
     * This test asserts the behaviour as it currently is, and it is asserting a
     * defect, deliberately: both revert call sites (WidgetActions'
     * WidgetCommandWorker and WearComms' runStandalone) apply
     * `optimistic(snap, inverse(action))` to undo a flip, which cannot recover
     * information [optimistic] threw away. So a car that had never reported its
     * doors, whose LOCK command then failed, ends up displayed as "Unlocked" --
     * a confident claim built entirely out of a missing value, on exactly the
     * surfaces (widget, tile) that have no room to explain themselves.
     *
     * It matters because null is handled carefully elsewhere on purpose: the
     * widget's StatusGlyph draws nothing at all rather than guess at an unknown
     * lock state. This path quietly undoes that protection.
     *
     * Fixing it needs the pre-flip value carried through to the revert instead of
     * being re-derived from a verb -- the widget's across WorkManager input data,
     * the watch's out of applyOptimistic. When that lands, the null case in this
     * test should start failing, and the assertions below should become
     * `assertNull`. The two known-state cases must keep passing either way.
     */
    @Test
    fun inverseRevertRestoresKnownStateButNotUnknownState() {
        for (known in listOf(true, false)) {
            val before = snap(locked = known)
            val resolved = WearCommandRunner.resolveToggle(before, WearAction.TOGGLE_LOCK)
            val flipped = WearCommandRunner.optimistic(before, resolved)
            val reverted = WearCommandRunner.optimistic(flipped, WearCommandRunner.inverse(resolved))
            assertEquals(known, reverted.locked, "revert lost a known state")
            assertEquals(before, reverted, "revert should be a full round trip")
        }

        // The unknown case does NOT round-trip. Pinned so the day someone fixes it,
        // this test tells them they did.
        val before = snap(locked = null)
        val resolved = WearCommandRunner.resolveToggle(before, WearAction.TOGGLE_LOCK)
        assertEquals(WearAction.LOCK, resolved)
        val flipped = WearCommandRunner.optimistic(before, resolved)
        assertEquals(true, flipped.locked)
        val reverted = WearCommandRunner.optimistic(flipped, WearCommandRunner.inverse(resolved))
        assertEquals(false, reverted.locked, "current behaviour: unknown reverts to a definite false")
    }
}
