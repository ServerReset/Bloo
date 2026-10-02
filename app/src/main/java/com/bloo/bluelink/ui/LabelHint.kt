package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the hint lingers after the finger lifts, so it can actually be read. */
private const val HINT_LINGER_MS = 1100L

/**
 * Long-press help for a button that has shrunk to its symbol. Hold it and a glass bubble springs up
 * above it with the symbol and the button's full name, a heavy tick under the finger; let go and it
 * stays a moment, then fades. The press that opened it never counts as a tap, so reading what a
 * button does can't fire it. While the button still shows its label this does nothing at all and
 * never touches the gesture, so ordinary taps are exactly as they were.
 *
 * [collapsed] is read when a press starts, never during composition.
 */
@Composable
internal fun LabelHintHost(
    label: String,
    icon: ImageVector,
    collapsed: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var present by remember { mutableStateOf(false) }
    val t = remember { Animatable(0f) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(shown) {
        if (shown) {
            present = true
            t.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        } else if (present) {
            t.animateTo(0f, tween(170))
            present = false
        }
    }
    Box(
        Modifier.pointerInput(label) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!collapsed()) return@awaitEachGesture
                val early = withTimeoutOrNull(timeout) { waitForUpOrCancellation() }
                if (early != null || !collapsed()) return@awaitEachGesture
                // Held long enough: show the name, and swallow the lift so the button is not clicked.
                hideJob?.cancel()
                shown = true
                haptics?.heavy()
                waitForUpOrCancellation()?.consume()
                hideJob = scope.launch {
                    delay(HINT_LINGER_MS)
                    shown = false
                }
            }
        },
    ) {
        content()
        if (present) {
            Popup(
                popupPositionProvider = remember { AboveAnchorPositionProvider },
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                LabelHintBubble(label, icon, Modifier.graphicsLayer {
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
}

/** The bubble: dark glass pill, the glyph in an accent disc, the name, and a caret pointing down. */
@Composable
private fun LabelHintBubble(label: String, icon: ImageVector, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = scheme.inverseSurface.copy(alpha = 0.94f)
    Box(modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .padding(bottom = 7.dp)
                .dropShadow(CircleShape, color = Color.Black.copy(alpha = 0.35f), blurRadius = 16.dp, offsetY = 5.dp)
                .background(fill, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
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
                    color = scheme.inverseOnSurface,
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
