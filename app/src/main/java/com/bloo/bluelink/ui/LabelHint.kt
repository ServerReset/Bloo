package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.launch

/** How long the hint stays up after the long press that opened it. */
private const val HINT_SHOW_MS = 1600L

/** What a button's label says about itself, so its long press can show the name. */
internal class LabelHintState {
    var label: String = ""
    var icon: ImageVector? = null
    /** True while the button is showing only its symbol. Written from placement. */
    var collapsed: Boolean = false
    var shown by mutableStateOf(false)
    /** True from the moment the bubble is up until its exit animation ends. */
    var present by mutableStateOf(false)

    fun describe(label: String, icon: ImageVector) { this.label = label; this.icon = icon }

    /** The button's long press: only a symbol-only button has anything to explain. */
    fun onLongPress() { if (collapsed && label.isNotEmpty()) shown = true }
}

/** The button whose label is being composed, if any. Null outside a [MorphButton]. */
internal val LocalLabelHint = androidx.compose.runtime.staticCompositionLocalOf<LabelHintState?> { null }

/**
 * Long-press help for a button that has shrunk to its symbol: a glass bubble springs up above it with
 * the symbol and the button's full name, then fades. The press arrives through the button's own long
 * click (its clickable chrome), so this never sits in the way of a tap: a pointer handler over a
 * button's content would swallow the touch before the chrome underneath ever saw it.
 */
@Composable
internal fun LabelHintPopup(state: LabelHintState) {
    // Nothing is composed (no animation state, no effect) until the hint is actually shown: every
    // MorphButton in the app hosts one of these, and nearly all of them never open.
    if (state.shown || state.present) ActiveLabelHint(state)
}

@Composable
private fun ActiveLabelHint(state: LabelHintState) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(state.shown) {
        if (state.shown) {
            state.present = true
            val hide = launch { delay(HINT_SHOW_MS); state.shown = false }
            t.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            hide.join()
        } else {
            t.animateTo(0f, tween(MotionFast))
            state.present = false
        }
    }
    val icon = state.icon
    if (icon != null) {
        Popup(
            popupPositionProvider = remember { AboveAnchorPositionProvider },
            properties = PopupProperties(focusable = false, clippingEnabled = false),
        ) {
            LabelHintBubble(state.label, icon, Modifier.graphicsLayer {
                val p = t.value
                val s = 0.45f + 0.55f * p
                scaleX = s; scaleY = s
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 14.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, 1f)
            })
        }
    }
}

/** The bubble: dark glass pill, the glyph in an accent disc, the name, and a caret pointing down. */
@Composable
private fun LabelHintBubble(label: String, icon: ImageVector, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = scheme.primaryContainer.copy(alpha = 0.96f)
    Box(modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .padding(bottom = 7.dp)
                .dropShadow(CircleShape, color = scheme.primary.copy(alpha = 0.35f), blurRadius = 16.dp, offsetY = 5.dp)
                .background(fill, CircleShape)
                .border(1.dp, scheme.primary.copy(alpha = 0.45f), CircleShape),
        ) {
            Row(
                Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(14.dp, 8.dp)
                .drawBehind {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
                        },
                        fill,
                    )
                },
        )
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
