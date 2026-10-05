package com.bloo.bluelink.ui

import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long a symbol-only button must be held before it starts to explain itself. */
internal const val HOLD_TO_EXPAND_MS = 350L

/** Space between the lifted button and the spot it left. */
private val LiftGap = 16.dp

/** Pause after the lifted button lands above the finger before its name unfolds. */
private const val NAME_UNFOLD_DELAY_MS = 60L

/**
 * Hold a button that has shrunk to its symbol and it explains itself with its own body: the phone buzzes and the
 * button shakes, then THAT button lifts out of its row (its neighbours close the gap) and floats above the finger,
 * and widens to show its symbol and its name. Let go and it drops back (its neighbours make room again). The hold
 * never presses the button.
 *
 * The lifted button is the button's own composable drawn a second time in a floating layer (so no card or clip can
 * cut it off) while the original, still under the finger, steps out of the layout and out of sight.
 */
internal class LabelHintState {
    var label: String = ""
    var icon: ImageVector? = null

    /** True while the button is showing only its symbol. Written from placement. */
    var collapsed by mutableStateOf(false)

    /** True while a finger is down on the button (even if it has slipped off it); read by the hold. */
    var fingerDown by mutableStateOf(false)

    /** True from the moment the button lifts until it has dropped back. */
    var lifting by mutableStateOf(false)

    /** 0 = the button sits in its slot; 1 = it is lifted above the finger and out of the layout. */
    var lift by mutableFloatStateOf(0f)

    /** The button's own wobble in degrees, read in a graphics layer (draw phase only). */
    var shakeDegrees by mutableFloatStateOf(0f)

    /** True once the lifted copy should widen from its symbol to symbol + name. */
    var showName by mutableStateOf(false)

    /** Set on a copy: lay out just the symbol until the name unfolds. */
    var glyphOnly by mutableStateOf(false)

    /** Where the button's slot is, in window pixels, captured when the lift starts. */
    var anchor: Rect = Rect.Zero

    /** The button's coordinates, for capturing [anchor]. */
    var coordinates: LayoutCoordinates? = null

    /** True from the moment the button lifts until its click has been swallowed. */
    var suppressClick = false

    /** Where the floating copy ended up, so its flight can start from the slot. */
    var finalTopLeft by mutableStateOf(IntOffset.Zero)

    val lifted: Boolean get() = lift > 0f

    fun describe(label: String, icon: ImageVector) { this.label = label; this.icon = icon }

    /** The hold has gone on long enough: capture where the slot is and swallow the click that follows. */
    fun beginLift() {
        anchor = coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
        suppressClick = true
        showName = false
        lifting = true
    }

    /** Fold the name away and drop the button back into its slot. */
    suspend fun endLift() {
        shakeDegrees = 0f
        showName = false
        if (lift > 0f) animate(lift, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)) { v, _ -> lift = v }
        lift = 0f
        lifting = false
    }

    /** The lift itself: out of the row and up above the finger. */
    suspend fun raise() {
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)) { v, _ -> lift = v }
        delay(NAME_UNFOLD_DELAY_MS)
        showName = true
    }

    /** A quick decaying side-to-side wobble: the button telling you it noticed the hold. */
    suspend fun shake() {
        val steps = floatArrayOf(1f, -1f, 0.7f, -0.7f, 0.4f, -0.4f, 0f)
        var from = 0f
        for (s in steps) {
            val to = 7f * s
            val start = from
            animate(0f, 1f, animationSpec = tween(45)) { v, _ -> shakeDegrees = start + (to - start) * v }
            from = to
        }
        shakeDegrees = 0f
    }
}

/** The button whose label is being composed, if any. Null outside a [MorphButton]. */
internal val LocalLabelHint = androidx.compose.runtime.staticCompositionLocalOf<LabelHintState?> { null }

/**
 * Watches whether a finger is down on the button without ever consuming an event, so it can sit over a button's own
 * gesture handling. The hold reads it to keep the button popped out for as long as the finger stays down.
 */
internal fun Modifier.trackFinger(hint: LabelHintState): Modifier = pointerInput(hint) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            hint.fingerDown = event.changes.any { it.pressed }
        }
    }
}

/**
 * The floating layer for a lifted button: draws [button] (the button's own body, composed again) above where the
 * slot was, riding the lift up and back. Costs nothing until a button is actually held.
 */
@Composable
internal fun LabelHintLayer(state: LabelHintState, button: @Composable (LabelHintState) -> Unit) {
    if (!state.lifted) return
    val gapPx = with(LocalDensity.current) { LiftGap.toPx() }
    val provider = remember(state.anchor, gapPx) { AboveSlotPositionProvider(state, gapPx) }
    // The copy shows just its symbol until the name unfolds, then springs wider.
    val copy = remember { LabelHintState().apply { glyphOnly = true } }
    SideEffect { copy.glyphOnly = !state.showName }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(
            Modifier
                .graphicsLayer {
                    val a = state.anchor
                    val to = state.finalTopLeft
                    val inv = 1f - state.lift
                    // Starts exactly on the slot, ends above the finger.
                    translationX = (a.left - to.x) * inv
                    translationY = (a.top - to.y) * inv
                    val s = 1f + 0.06f * state.lift
                    scaleX = s
                    scaleY = s
                }
                .animateContentSize(spring(dampingRatio = 0.6f, stiffness = 380f)),
        ) { button(copy) }
    }
}

/** Centred above the slot the button left, kept inside the window; records where it settled. */
private class AboveSlotPositionProvider(private val state: LabelHintState, private val gapPx: Float) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val a = state.anchor
        val x = (a.center.x - popupContentSize.width / 2f).roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = (a.top - popupContentSize.height - gapPx).roundToInt()
        val y = if (above >= 0) above else (a.bottom + gapPx).roundToInt()
        return IntOffset(x, y).also { state.finalTopLeft = it }
    }
}
