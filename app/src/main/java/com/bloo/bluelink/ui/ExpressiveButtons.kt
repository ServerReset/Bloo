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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive press feedback in two flavours: [SafeExpansiveButton] is
 * paint-plus-own-width and safe anywhere; [ExpressiveButtonGroup] redistributes widths WITHIN the
 * group so its outer footprint never changes.
 */

/**
 * 1.0 -> 1.3: the pressed button claims 30% more. The single constant behind every press push and
 * squeeze.
 */
internal const val ExpressivePressGrowth = 0.30f

/**
 * A group member's resting width is its content plus one reserve per neighbour it shares a seam
 * with.
 */

/** Damping for the press spring. High enough not to ring: see expressivePressFraction. */
private const val PressDamping = 0.88f

/**
 * 0 at rest, 1 fully pushed. Driven by the interaction stream, not a held boolean, so a quick tap
 * still runs the push to completion before the release runs it back.
 */
@Composable
internal fun expressivePressFraction(interactionSource: InteractionSource, enabled: Boolean): State<Float> {
    val anim = remember { Animatable(0f) }
    // Tracks whether a press is CURRENTLY held, shared with the enabled-watching effect below.
    // Needed because a tap's own onClick routinely disables the button as its direct side effect
    // (Lock/Unlock -> pending, "Check for updates" -> updateChecking) -- so `enabled` flips false
    // while the SAME gesture's Release is still in flight.
    val isPressed = remember { mutableStateOf(false) }
    // True from the start of the leg back to rest until it finishes, so a self-disabling click
    // cannot snap it.
    val isSettling = remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        // Barely-bouncy and quick, because this fraction drives real WIDTH. A bouncy, slow spring
        // is the right feel for something that only paints -- it was the original graphicsLayer
        // scale -- but on width every overshoot frame re-measures the row and drags the neighbours
        // back and forth with it, which reads as wobble rather than as life.
        val spec = spring<Float>(dampingRatio = PressDamping, stiffness = Spring.StiffnessMedium)
        // Held by press identity, so a cancelled gesture cannot leave the button stuck pressed.
        val held = mutableSetOf<PressInteraction.Press>()
        // The running animation leg; a new interaction cancels it and starts the next.
        var leg: Job? = null
        fun runTo(target: Float, finishPushFirst: Boolean) {
            leg?.cancel()
            leg = launch {
                // Covers the whole leg, including the finish-push animateTo.
                if (target == 0f) isSettling.value = true
                try {
                    // Finish the push first so a tap reads as a push rather than springing home
                    // untravelled.
                    if (finishPushFirst && anim.value < 1f) {
                        anim.animateTo(1f, spring(dampingRatio = PressDamping, stiffness = Spring.StiffnessHigh))
                    }
                    anim.animateTo(target, spec)
                } finally {
                    // Runs on a clean finish AND on cancellation (a new press interrupting the
                    // release, say) -- either way this leg is done owning the settle, and the NEXT
                    // leg (or the enabled-effect) is free to act without a stale true blocking it
                    // forever.
                    if (target == 0f) isSettling.value = false
                }
            }
        }
        // Collect the raw interaction flow, not a derived boolean: a fast tap's Press and Release
        // can both land between recompositions. Legs run in child jobs so the collector never
        // suspends and drops events.
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
    // Separate from the collector so a click-triggered disable cannot cancel it. Snaps to rest only
    // when neither pressed nor settling, so a self-disabling tap still finishes its
    // push-then-release.
    LaunchedEffect(enabled) {
        if (!enabled && !isPressed.value && !isSettling.value) anim.snapTo(0f)
    }
    return anim.asState()
}

/**
 * Press feedback for a standalone button: it is re-measured wider and springs back, pushing
 * neighbours along. The press fraction is read in the measure block, so a press invalidates layout
 * only, never composition.
 */
@Composable
fun SafeExpansiveButton(
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** See [ExpressiveGroupData.weight]. Ignored outside a group. */
    groupWeight: Float = 0f,
    /**
     * For a labelled button on a row of its own: rests at its natural width on the row's start edge
     * (left in LTR, right in RTL) and, pressed, widens to fill the row.
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
            // Providing FALSE inside makes joining a group idempotent. LocalExpressiveGrowth TRUE,
            // though (unlike the group flag) -- a member's own width is still being smoothly
            // driven, by the group's seam-reserve layout instead of this file's standalone one, so
            // MorphButton's animateContentSize still needs to step aside for the same reason.
            CompositionLocalProvider(LocalExpressiveGroup provides false, LocalExpressiveGrowth provides true) {
                ExpressiveContent(enabled, content)
            }
        }
        return
    }
    // Standalone, it grows for real (a graphicsLayer scale would distort the label and move
    // nothing).
    val naturals = remember { NaturalWidths() }
    Layout(
        content = {
            CompositionLocalProvider(LocalExpressiveGrowth provides true) {
                ExpressiveContent(enabled, content)
            }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(0, 0) {}
            // Read at layout time so a press invalidates layout only.
            val p = press
            val cached = naturals.widths?.firstOrNull() ?: 0
            // One measure per child per pass (measuring twice throws), so the resting width is
            // cached. minWidth is zeroed for the resting measure so a parent that forces a width
            // still records the natural size there is room to grow from.
            val naturalConstraints = constraints.copy(minWidth = 0)
            val placeables = if (p <= 0.001f || cached <= 0) {
                measurables.map { it.measure(naturalConstraints) }
                    .also { naturals.widths = intArrayOf(it.maxOf { pl -> pl.width }) }
            } else {
                val room = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                // Filling needs a bounded edge; on an unbounded axis it falls back to the small
                // push.
                val grown = if (fillOnPress && constraints.hasBoundedWidth) {
                    cached + ((room - cached) * p.coerceIn(0f, 1f)).roundToInt()
                } else {
                    (cached * (1f + ExpressivePressGrowth * p)).roundToInt()
                }
                val target = grown.coerceAtMost(room).coerceAtLeast(0)
                measurables.map { it.measure(constraints.copy(minWidth = target, maxWidth = target)) }
            }
            // Stacked at the origin, like the Box this replaced: a handful of call sites emit more
            // than one root into this slot (a button with a label and an AnimatedVisibility beside
            // it), and measuring only the first would make the rest silently vanish.
            val w = placeables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            // placeRelative: alone on a row the button rests on the start edge.
            layout(w, h) { placeables.forEach { it.placeRelative(0, 0) } }
        },
    )
}

/**
 * True inside an [ExpressiveButtonGroup]: [SafeExpansiveButton] hands its press fraction to the
 * group.
 */
internal val LocalExpressiveGroup = staticCompositionLocalOf { false }

/**
 * True for content that [SafeExpansiveButton] is already smoothly resizing on press -- either
 * because it joined a group (the seam-reserve layout) or because it is growing for real on its own
 * (this file's own `Layout` above). [MorphButton] reads this to skip its own `animateContentSize`:
 * that modifier exists for a genuine content change (a label swapping to a longer one, say), and
 * left unconditional it ALSO tried to re-smooth a width SafeExpansiveButton was already smoothly
 * driving frame by frame with its own, deliberately non-bouncy spring (see
 * `expressivePressFraction`'s own doc on why that spring specifically does not ring) -- two
 * different springs chasing the same width at once, which read as the button wobbling on press.
 */
internal val LocalExpressiveGrowth = staticCompositionLocalOf { false }

/**
 * Renders [content]; when disabled, wraps it in a Box that merges semantics and reports disabled so
 * a screen reader announces the control once.
 */
@Composable
private fun ExpressiveContent(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        content()
    } else {
        // propagateMinConstraints matches the enabled branch so the disabled measure is identical.
        Box(
            Modifier
                .semantics(mergeDescendants = true) { disabled() },
            propagateMinConstraints = true,
        ) { content() }
    }
}
