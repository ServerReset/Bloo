package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive press feedback, in two flavours that exist for one reason: a press
 * effect that changes real LAYOUT size is only safe when something contains that size change.
 *
 * - [SafeExpansiveButton] -- the general one, used by ~80 call sites all over the app. Paint
 *   only: it scales what is DRAWN and never touches layout, so it is safe absolutely anywhere,
 *   including inside a `LazyColumn` item (which every Settings card is).
 * - [ExpressiveButtonGroup] -- for buttons that sit side by side and should genuinely shove
 *   each other around on press. Real widths, but redistributed WITHIN the group, whose own
 *   outer footprint never changes.
 *
 * **Why the split, in detail, because two earlier attempts got this wrong and broke the app:**
 *
 * Growing one button's real width pushes its neighbours only because the size change propagates
 * outward -- the Row remeasures, then its parent, and so on up. That is the whole point of the
 * effect and also exactly what makes it dangerous applied indiscriminately: on the Settings
 * screen these buttons live inside `LazyColumn` items, and an item that changes
 * its own measured size during a scroll is a well-known way to crash a lazy layout
 * (reported here as "the logs pebble crashes when scrolled over" -- the Logs card's
 * Copy/Clear/Show buttons are these). A second attempt cached the natural width in a
 * `mutableIntStateOf` and wrote it from inside the measure block; writing snapshot state during
 * the layout phase invalidates the layout that is currently running, which is what the reported
 * stutter was.
 *
 * So: the general wrapper does not change layout at all, and the buttons that actually have a
 * neighbour worth pushing opt into [ExpressiveButtonGroup], which keeps the size change bottled
 * up inside itself -- its children's widths are redistributed against each other so their total
 * is unchanged, and nothing outside the group ever sees a different size. Both use the same
 * spring, so the two read as one effect.
 */

/** 1.0 -> 1.3: the pressed button claims 30% more.
 *
 *  Doubled from the Material 3 Expressive baseline (15%) -- the stretch read as barely there on
 *  a real device, especially on the squeezing side: a neighbour giving back 15% of its own width
 *  is a couple of dp, easy to miss entirely next to the button doing the growing. This is the
 *  ONE constant behind the whole effect (both a standalone button's own growth and a group's
 *  push-and-squeeze), so turning it up here turns it up everywhere at once, the button and the
 *  neighbour it shoves both reading as a clearly bigger push. */
internal const val ExpressivePressGrowth = 0.30f

/**
 * A group member's resting width is its content plus one reserve PER NEIGHBOUR it actually
 * shares a seam with -- not a flat allowance every member carries regardless of what's beside
 * it. A button that hugs its content has NO slack: its width IS its label plus its padding, so
 * a neighbour pressing beside it has nothing to give. Earlier rules tried to find slack inside
 * that width -- shrink to the minimum intrinsic width (the longest word, so labels ellipsized,
 * since a button label is one line that never wraps), or shrink into the padding (nothing at
 * all for a button with tight padding, and nothing for a fixed-size icon).
 *
 * So the slack is not found, it is RESERVED, and it is reserved PER SEAM: each internal boundary
 * between two adjacent members gets its own capacity ([ExpressivePressGrowth] of the larger of
 * the two members either side of it), split evenly between them at rest. A member with one
 * neighbour (the end of a line) carries one seam's worth; a member with two (the middle of a
 * three-plus segment group) carries two; a member with none carries nothing to reserve for. See
 * the per-line measure block for where this is actually built and spent -- growing a member only
 * ever draws on the seam(s) it shares with a real neighbour, never from a member two seats away
 * it was never touching, and squeezing a member only ever moves the shared seam edge, never its
 * own free outer edge.
 */

/** Damping for the press spring. High enough not to ring: see expressivePressFraction. */
private const val PressDamping = 0.88f

/**
 * 0 at rest, 1 fully pushed. Driven by the interaction stream rather than by a held/not-held
 * boolean, because a TAP is the common case and a boolean cannot express one.
 *
 * With `targetValue = if (pressed) 1 else 0`, a quick tap flips the target to 1 and back within
 * a few milliseconds, so the spring is already heading home before it has travelled anywhere:
 * you press a button and almost nothing happens, which is the reported "it only does it when I
 * hold it". Here a press runs the push to completion FIRST and only then lets the release run
 * it back -- the collector suspends while each leg animates, so the interactions queue behind
 * it. Tap and hold therefore differ only in how long the button dwells at full push, which is
 * what a physical button does.
 */
@Composable
internal fun expressivePressFraction(interactionSource: InteractionSource, enabled: Boolean): State<Float> {
    val anim = remember { Animatable(0f) }
    // Tracks whether a press is CURRENTLY held, shared with the enabled-watching effect below.
    // Needed because a tap's own onClick routinely disables the button as its direct side
    // effect (Lock/Unlock -> pending, "Check for updates" -> updateChecking) -- so `enabled`
    // flips false while the SAME gesture's Release is still in flight. Keying the interaction
    // collector below on `enabled` (the old code did) restarted its whole coroutine scope right
    // then, cancelling the in-progress `runTo` leg and leaving the button snapped to rest with
    // no visible push at all: reported as "lock/unlock doesn't animate" and "same bug on Check
    // for updates" -- both buttons that disable themselves on tap; ordinary buttons that stay
    // enabled through their own click never showed it.
    val isPressed = remember { mutableStateOf(false) }
    // True from the moment a Release/Cancel starts the animation BACK to rest until it actually
    // gets there. Needed for the exact same reason `isPressed` is: `enabled` still flips false
    // WHILE this leg is running, not after -- Release is processed (isPressed already false)
    // before onClick ever runs, and a click that disables itself does so essentially the same
    // frame. Without this, the enabled-effect below saw `!isPressed.value` already true (release
    // already registered) and snapped straight to 0 instead of letting the leg it raced past
    // play out -- so the "fix" for the push getting cancelled left the RELEASE half of the exact
    // same gesture getting cut short instead, on the exact same two buttons this was reported on:
    // a quick tap looked like it barely moved, or didn't spring back, rather than not moving at
    // all -- easy to miss next to the original "doesn't animate at all" symptom this was chasing.
    val isSettling = remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        // Barely-bouncy and quick, because this fraction drives real WIDTH. A bouncy, slow
        // spring is the right feel for something that only paints -- it was the original
        // graphicsLayer scale -- but on width every overshoot frame re-measures the row and
        // drags the neighbours back and forth with it, which reads as wobble rather than as
        // life. The push still springs; it just does not ring.
        val spec = spring<Float>(dampingRatio = PressDamping, stiffness = Spring.StiffnessMedium)
        // Held by press IDENTITY, not a count: a press can legitimately be released before
        // this collector even gets to react to it (see below), and a cancelled gesture would
        // otherwise leave a plain counter unbalanced and the button stuck "held" forever.
        val held = mutableSetOf<PressInteraction.Press>()
        // The one animation leg currently running, so a new interaction can cancel it and
        // start the next leg immediately rather than queuing behind it.
        var leg: Job? = null
        fun runTo(target: Float, finishPushFirst: Boolean) {
            leg?.cancel()
            leg = launch {
                // Marks the WHOLE leg, not just the final animateTo below -- finishPushFirst's
                // own animateTo(1f) is still part of "getting back to rest", and a snapTo landing
                // in the middle of THAT leg would be exactly as visible a cut as one landing
                // during the release spring itself.
                if (target == 0f) isSettling.value = true
                try {
                    // Finish the push before returning. THIS is what makes a tap feel like a
                    // push: a press and its release can be milliseconds apart, and simply
                    // springing toward whatever the current state is would send the animation
                    // home before it had travelled anywhere -- press a button, watch nothing
                    // happen. Tap and hold differ only in how long the button dwells at full
                    // push, like a physical one.
                    if (finishPushFirst && anim.value < 1f) {
                        anim.animateTo(1f, spring(dampingRatio = PressDamping, stiffness = Spring.StiffnessHigh))
                    }
                    anim.animateTo(target, spec)
                } finally {
                    // Runs on a clean finish AND on cancellation (a new press interrupting the
                    // release, say) -- either way this leg is done owning the settle, and the
                    // NEXT leg (or the enabled-effect) is free to act without a stale true
                    // blocking it forever.
                    if (target == 0f) isSettling.value = false
                }
            }
        }
        // Collecting the raw interaction FLOW, not a derived `pressed` boolean, and reacting
        // to each event directly inside collect -- not via a separate LaunchedEffect keyed on
        // that boolean. A fast tap's Press and Release can both be written before Compose ever
        // runs the recomposition that would read an intermediate value: composition only ever
        // reflects the LATEST value of a piece of state, so a boolean that goes true-then-false
        // between two recompositions can read as having stayed false throughout, and a
        // LaunchedEffect keyed on it never restarts at all -- the animation silently never
        // runs. That was "quick tap barely moves, hold is fine": a hold has enough dwell time
        // to always be observed; a tap does not. collect() has no such gap -- every emitted
        // Press and Release is processed in order regardless of composition/frame timing.
        //
        // The collector itself never suspends on the animation (each leg runs in its own
        // launched child, cancelled and replaced rather than awaited) -- interactionSource's
        // flow drops OLDEST on overflow, so a collector blocked for a whole animateTo across a
        // burst of rapid presses could lose one and leave a button stuck open.
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    held += interaction
                    isPressed.value = true
                    runTo(1f, finishPushFirst = false)
                }
                is PressInteraction.Release -> {
                    held -= interaction.press
                    if (held.isEmpty()) {
                        isPressed.value = false
                        runTo(0f, finishPushFirst = true)
                    }
                }
                is PressInteraction.Cancel -> {
                    held -= interaction.press
                    if (held.isEmpty()) {
                        isPressed.value = false
                        runTo(0f, finishPushFirst = true)
                    }
                }
                else -> {}
            }
        }
    }
    // Separate from the collector above ON PURPOSE, so a click-triggered disable can no longer
    // cancel that coroutine (see the comment on `isPressed`). This one only ever snaps to rest,
    // and only when NEITHER `isPressed` NOR `isSettling` is true -- a button disabled while
    // genuinely mid-press (some other cause, not its own click finishing) still gets pulled
    // back to 0 instead of being left visually stuck mid-push with no gesture left to release
    // it, and a button whose own tap disabled it (Lock/Unlock, Check for updates) gets to
    // finish its own push-then-release cycle uninterrupted -- `isSettling` is what makes that
    // second case actually true: Release fires (and `isPressed` goes false) BEFORE onClick
    // even runs, so by the time the click's own disable reaches this effect, `isPressed` alone
    // was already false and this used to snap the release spring to a dead stop mid-flight.
    LaunchedEffect(enabled) {
        if (!enabled && !isPressed.value && !isSettling.value) anim.snapTo(0f)
    }
    return anim.asState()
}

/**
 * Press feedback for a standalone button: it is re-measured ~15% wider and springs back, so the
 * pill genuinely grows and anything beside it in a Row is pushed along.
 *
 * The press fraction is read inside the measure block, so a press invalidates LAYOUT only --
 * composition never re-runs for the animation, which is what keeps this affordable at the ~80
 * call sites that use it.
 *
 * For a row of buttons that should share a fixed footprint and shove each other instead of
 * pushing the row wider, use [ExpressiveButtonRow]; these buttons detect it and join in.
 */
@Composable
fun SafeExpansiveButton(
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** See [ExpressiveGroupData.weight]. Ignored outside a group. */
    groupWeight: Float = 0f,
    /**
     * For a labelled button on a row of its own: rests at its natural width on the row's start
     * edge (left in LTR, right in RTL) and, pressed, widens to fill the row. False keeps the
     * small [ExpressivePressGrowth] push, which is what an icon-only button or a button that
     * shares its row with other content wants -- stretching those would squeeze the neighbours.
     * Ignored inside a group, where the group's own layout decides.
     */
    fillOnPress: Boolean = false,
    content: @Composable () -> Unit,
) {
    val press by expressivePressFraction(interactionSource, enabled)
    // Inside an [ExpressiveButtonRow]/[ExpressiveButtonGroup] this button joins the group, which
    // takes the extra width off its NEIGHBOURS so the row's own footprint never changes.
    if (LocalExpressiveGroup.current) {
        Box(
            modifier.then(ExpressiveGroupData({ press }, groupWeight)),
            propagateMinConstraints = true,
        ) {
            // Providing FALSE inside makes joining a group idempotent. MorphButton now joins on
            // its own when it finds itself in one (that is what stopped whole rows of buttons
            // from ever animating), and without this every call site that already wraps its
            // button explicitly would wrap it a second time -- a redundant layout node and a
            // second press spring per button, whose parent data the group would not even read,
            // since it belongs to a Box inside this one rather than to the group itself.
            // LocalExpressiveGrowth TRUE, though (unlike the group flag) -- a member's own width
            // is still being smoothly driven, by the group's seam-reserve layout instead of this
            // file's standalone one, so MorphButton's animateContentSize still needs to step
            // aside for the same reason.
            CompositionLocalProvider(LocalExpressiveGroup provides false, LocalExpressiveGrowth provides true) {
                if (enabled) {
                    content()
                } else {
                    Box(
                        Modifier
                            .alpha(0.5f)
                            .semantics(mergeDescendants = true) { disabled() }
                    ) {
                        content()
                    }
                }
            }
        }
        return
    }
    // On its own, it grows for real: the button is re-measured at a wider width, so the pill
    // itself gets wider and whatever sits next to it in a Row is pushed aside. That IS the
    // requested effect, and it is what a graphicsLayer scale could never deliver -- a scale
    // stretches the pixels of a button whose measured size never changed, so nothing moves and
    // the label distorts.
    //
    // The earlier objection to real growth was that a size change inside a Settings lazy item
    // crashes the grid. That reasoning came from the reported Logs-card crash -- which turns out
    // to be DebugSettingsPanel's unbounded LazyColumn one card further down, not a size change at
    // all. Lazy layouts remeasure on content size changes constantly (every AnimatedVisibility in
    // these same cards does it); there was never anything here to be afraid of.
    val naturals = remember { NaturalWidths() }
    Layout(
        content = {
            CompositionLocalProvider(LocalExpressiveGrowth provides true) {
                if (enabled) {
                    content()
                } else {
                    Box(
                        Modifier
                            .alpha(0.5f)
                            .semantics(mergeDescendants = true) { disabled() }
                    ) {
                        content()
                    }
                }
            }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(0, 0) {}
            // Read at LAYOUT time, so a press invalidates layout only and never composition.
            val p = press
            val cached = naturals.widths?.firstOrNull() ?: 0
            // One measure per child per pass, never two: measuring the same Measurable twice in
            // a single pass throws, so the resting width is CACHED on the resting passes and the
            // pressed passes size themselves from that cache.
            // minWidth ZEROED for the resting measure. Measuring with the incoming constraints
            // meant that in any parent that forces a width -- a fillMaxWidth row, most of them --
            // the button was recorded at that forced width, and the pressed pass then computed
            // `cached * (1 + ExpressivePressGrowth)` and coerced it straight back down to
            // maxWidth. Target == cached, so the button never moved. That is why these still did
            // not animate. A child that
            // genuinely wants the full width still gets it from its own fillMaxWidth; one that
            // does not now records its real natural size, which is what there is room to grow
            // from.
            val naturalConstraints = constraints.copy(minWidth = 0)
            val placeables = if (p <= 0.001f || cached <= 0) {
                measurables.map { it.measure(naturalConstraints) }
                    .also { naturals.widths = intArrayOf(it.maxOf { pl -> pl.width }) }
            } else {
                val room = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                // Filling needs a real edge to fill to; on an unbounded axis (a horizontally
                // scrolling row) there is none, so it falls back to the small push.
                val grown = if (fillOnPress && constraints.hasBoundedWidth) {
                    cached + ((room - cached) * p.coerceIn(0f, 1f)).roundToInt()
                } else {
                    (cached * (1f + ExpressivePressGrowth * p)).roundToInt()
                }
                val target = grown.coerceAtMost(room).coerceAtLeast(0)
                measurables.map { it.measure(constraints.copy(minWidth = target, maxWidth = target)) }
            }
            // Stacked at the origin, like the Box this replaced: a handful of call sites emit
            // more than one root into this slot (a button with a label and an AnimatedVisibility
            // beside it), and measuring only the first would make the rest silently vanish.
            val w = placeables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) { placeables.forEach { it.place(0, 0) } }
        },
    )
}

/**
 * True inside an [ExpressiveButtonGroup], which is how [SafeExpansiveButton] knows to hand its
 * press fraction to the group's layout instead of scaling itself.
 */
internal val LocalExpressiveGroup = staticCompositionLocalOf { false }

/**
 * True for content that [SafeExpansiveButton] is already smoothly resizing on press -- either
 * because it joined a group (the seam-reserve layout) or because it is growing for real on its
 * own (this file's own `Layout` above). [MorphButton] reads this to skip its own
 * `animateContentSize`: that modifier exists for a genuine content change (a label swapping to a
 * longer one, say), and left unconditional it ALSO tried to re-smooth a width SafeExpansiveButton
 * was already smoothly driving frame by frame with its own, deliberately non-bouncy spring (see
 * `expressivePressFraction`'s own doc on why that spring specifically does not ring) -- two
 * different springs chasing the same width at once, which read as the button wobbling on press.
 * Not folded into [LocalExpressiveGroup] itself: that flag also means "hand my width to the
 * group's own layout instead of measuring for real," which is not true in the standalone-growth
 * case, so the two needed to be readable independently.
 */
internal val LocalExpressiveGrowth = staticCompositionLocalOf { false }
