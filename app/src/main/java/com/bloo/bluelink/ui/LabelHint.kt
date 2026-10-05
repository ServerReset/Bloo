package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.delay

/** How long a symbol-only button must be held before it explains itself. */
internal const val HOLD_TO_EXPAND_MS = 350L

/** Pause between the bubble popping up and its label unfolding. */
private const val LABEL_UNFOLD_DELAY_MS = 90L

/**
 * What a button's label says about itself, so holding it can show what it does.
 *
 * Hold a button that has shrunk to its symbol: the phone builds a vibration and the button shakes,
 * then a copy of it pops up ABOVE the finger as a bubble and unfolds to show the symbol AND the name.
 * Lift and the bubble folds away. The bubble is a separate window, so it is never clipped by a tight
 * button group and never changes the layout under the finger.
 */
internal class LabelHintState {
    var label: String = ""
    var icon: ImageVector? = null
    /** True while the button is showing only its symbol. Written from placement. */
    var collapsed: Boolean = false
    /** True while the button is held and its bubble should be up. */
    var shown by mutableStateOf(false)
    /** True from the bubble's first frame until its fold-away finishes. */
    var present by mutableStateOf(false)
    /** The button's own wobble in degrees, read in a graphics layer (draw phase only). */
    var shakeDegrees by mutableFloatStateOf(0f)

    fun describe(label: String, icon: ImageVector) { this.label = label; this.icon = icon }

    /** The hold crossed the threshold on a symbol-only button: raise the bubble. */
    fun onHoldStart() { if (collapsed && label.isNotEmpty()) shown = true }

    /** Hold ended (lift/cancel): fold the bubble away. */
    fun onHoldEnd() { shown = false; shakeDegrees = 0f }
}

/** The button whose label is being composed, if any. Null outside a [MorphButton]. */
internal val LocalLabelHint = androidx.compose.runtime.staticCompositionLocalOf<LabelHintState?> { null }

/** A quick decaying side-to-side wobble, for the button that was just held long enough. */
internal suspend fun LabelHintState.shake() {
    val amp = 7f
    val steps = floatArrayOf(1f, -1f, 0.7f, -0.7f, 0.4f, -0.4f, 0f)
    var from = 0f
    for (s in steps) {
        val to = amp * s
        val start = from
        animate(0f, 1f, animationSpec = tween(45)) { v, _ -> shakeDegrees = start + (to - start) * v }
        from = to
    }
    shakeDegrees = 0f
}

/**
 * The hold-to-explain bubble: pops up above the button (springing, from the button's own size), then
 * the name unfolds beside the symbol. Costs nothing until a button is actually held.
 */
@Composable
internal fun LabelHintPopup(state: LabelHintState) {
    if (!state.shown && !state.present) return
    LabelHintBubbleHost(state)
}

@Composable
private fun LabelHintBubbleHost(state: LabelHintState) {
    val pop = remember { Animatable(0f) }
    var unfolded by remember { mutableStateOf(false) }
    LaunchedEffect(state.shown) {
        if (state.shown) {
            state.present = true
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        } else {
            unfolded = false
            delay(120)
            pop.animateTo(0f, tween(140))
            state.present = false
        }
    }
    LaunchedEffect(state.shown) {
        if (state.shown) {
            delay(LABEL_UNFOLD_DELAY_MS)
            unfolded = true
        }
    }
    val icon = state.icon ?: return
    Popup(
        popupPositionProvider = remember { AboveAnchorPositionProvider },
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        LabelHintBubble(
            state.label,
            icon,
            unfolded,
            Modifier.graphicsLayer {
                val p = pop.value
                val s = 0.4f + 0.6f * p
                scaleX = s
                scaleY = s
                alpha = (p * 2f).coerceIn(0f, 1f)
                // Rises from the button's own spot to above the finger.
                translationY = (1f - p) * 22.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        )
    }
}

/** The bubble: a glass-toned pill, the symbol in an accent disc, and the name that unfolds beside it. */
@Composable
private fun LabelHintBubble(label: String, icon: ImageVector, unfolded: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .dropShadow(CircleShape, color = scheme.primary.copy(alpha = 0.35f), blurRadius = 16.dp, offsetY = 5.dp)
                .background(scheme.primaryContainer.copy(alpha = 0.97f), CircleShape)
                .border(1.dp, scheme.primary.copy(alpha = 0.45f), CircleShape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            AnimatedVisibility(
                visible = unfolded,
                enter = expandHorizontally(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Start) + fadeIn(tween(120)),
                exit = shrinkHorizontally(tween(120), shrinkTowards = Alignment.Start) + fadeOut(tween(90)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Box(Modifier.width(12.dp))
                }
            }
        }
    }
}

/** Centred above the anchor, flipping below it when there is no room, and kept inside the window. */
private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom
        return IntOffset(x, y)
    }
}
