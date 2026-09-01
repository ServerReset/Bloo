@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.uicommon

/**
 * Page-dot indicator rail, pure Compose Foundation.
 *
 * Material-free by construction: the consuming app (phone garage, cover,
 * and anything else showing a pager) resolves theme colors into a
 * [PagerDotColors] instance and supplies the frosted chrome, so the exact
 * same component renders under compose.material3 or wear.compose.material3
 * without either platform importing the other.
 */
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.StrokeCap

/** Page-dot indicator rail, pure Compose Foundation -- see [PagerDots]. */
/** Page indicator dots, optionally with long-press-to-refresh -- holding the
 *  indicator for one second triggers [onRefresh]. Passing null drops the whole
 *  gesture (and its fill-ring) entirely. The consuming app resolves
 *  [PagerDotColors] from its own theme and supplies the frosted pill chrome
 *  ([ambientRing]/[dropShadow]/[frostedRim] from this same module), so this
 *  component stays Material-free and renders identically under
 *  compose.material3 and wear.compose.material3. */

/** Material-free color spec for [PagerDots]: the one theme coupling a
 *  cross-surface call site must supply. Defaults are neutral grayscale so a
 *  caller that has no theme (e.g. tests) still gets a visible control.
 *  Immutable by convention -- callers should construct once per composition
 *  and pass the same instance; @Immutable keeps recomposition skipping the
 *  repeat loop cheap. */
@Immutable
data class PagerDotColors(
    /** Fill of the dot at the current page. */
    val active: Color = Color.Black,
    /** Fill of every non-current dot. */
    val inactive: Color = Color.Gray,
    /** Track of the long-press-to-refresh ring. */
    val ringTrack: Color = Color.LightGray,
    /** Fill of the refresh ring as the user holds (consumer = primary). */
    val ringFill: Color = Color.DarkGray,
    /** Fill of the pill behind the dots (consumer = surfaceContainerHighest at frosted alpha). */
    val pill: Color = Color.White.copy(alpha = 0.6f),
)

@Composable
fun PagerDots(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    /** Ticked once when the long-press-to-refresh gesture lands (lifted from
     *  Haptics on the consumer side; null = no tick). */
    haptics: (() -> Unit)? = null,
    /** Theme resolution: inactive/active dots, ring colors and the pill fill. */
    colors: PagerDotColors = PagerDotColors(),
) {
    val expandProgress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }

    if (onRefresh != null) {
        LaunchedEffect(holding) {
            if (holding) {
                expandProgress.snapTo(0f)
                expandProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                )
                onRefresh.invoke()
                // Linger the full ring briefly, then ease it back to nothing --
                // this must happen BEFORE flipping `holding` back to false,
                // because that write re-keys (and thus cancels) this very
                // LaunchedEffect(holding) coroutine, which used to kill the
                // delay+collapse before it ever ran (the ring snapped away).
                delay(300)
                expandProgress.animateTo(0f, tween(200))
                holding = false
            } else if (expandProgress.value > 0f) {
                // Released (or the gesture was cancelled) before the hold
                // completed -- LaunchedEffect(holding) cancels the coroutine
                // above outright when holding flips back to false, which used to
                // leave the ring frozen at whatever fill it had reached instead
                // of easing back to nothing (matches the edge-trace gesture's
                // own release/cancel handling elsewhere on the cover screen).
                expandProgress.animateTo(0f, tween(200))
            }
        }
    }

    // Getting out of a colliding floater's way is NOT handled here any more: the consumer
    // applies Modifier.dodgeFloating (see the app's FloatingSystem.kt), so every floating
    // element negotiates through one shared registry instead of this control carrying its own
    // private copy of the rule and a `nameBoundsPx` parameter that had to be hand-threaded
    // down through four composables to reach it.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Overlay ring that fills as the user holds. The Canvas node is
        // always present and the visibility + progress reads happen INSIDE
        // its draw lambda: during the 1s hold the Animatable ticks every
        // frame, and drawing is what needs that value -- reading it in
        // composition would recompose this control per frame for no reason
        // (and a conditional Canvas node would churn the layout tree).
        Canvas(Modifier.size(36.dp)) {
            if (onRefresh != null && expandProgress.value > 0.01f) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val sweep = size.minDimension - stroke
                drawArc(
                    color = colors.ringTrack,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(sweep, sweep),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = colors.ringFill,
                    startAngle = -90f, sweepAngle = expandProgress.value.coerceIn(0f, 1f) * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(sweep, sweep),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .then(
                    if (onRefresh != null) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                haptics?.invoke()
                                holding = true
                                try { waitForUpOrCancellation() }
                                finally { holding = false }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                // This whole control is a raw pointerInput gesture (long-press
                // to refresh) with zero semantics -- with TalkBack's touch
                // exploration intercepting single-finger gestures, it was both
                // unreachable as its own focus stop and the long-press gesture
                // itself couldn't be triggered. contentDescription announces
                // which car is showing (the dots' only visual information);
                // onLongClick exposes the refresh gesture as a real
                // accessibility action instead of a gesture no assistive
                // technology can perform.
                .then(
                    if (onRefresh != null) {
                        Modifier.semantics {
                            contentDescription = "Car ${current + 1} of $count"
                            onLongClick("Refresh") { onRefresh(); true }
                        }
                    } else {
                        Modifier.semantics { contentDescription = "Car ${current + 1} of $count" }
                    },
                )
                // Was relying only on Material's own tonal shadowElevation (2dp) --
                // barely-there against a car photo, same gap as every other
                // piece of floating chrome the frostedRim/dropShadow pass
                // already covers (FloatingIcon, the name pill, the Settings
                // pill). This is one of the most visible floating pills in the
                // app (car-switcher dots at the top of the garage), so it
                // shouldn't have been the one left out.
                .ambientRing(CircleShape)
                .dropShadow(CircleShape)
                // Frosted chrome is parameterized the same way as colors: each
                // platform's wrappers pass their own rim helpers in future while
                // the defaults below keep the pill legible on any backdrop.
                .frostedRim(CircleShape, onSurface = colors.pill)
                .clip(CircleShape)
                .background(colors.pill, CircleShape),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { i ->
                    val selected = i == current
                    val w by animateDpAsState(if (selected) 20.dp else 7.dp, label = "dotW")
                    val color by androidx.compose.animation.animateColorAsState(
                        if (selected) colors.active else colors.inactive,
                        label = "dotC",
                    )
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(color))
                }
            }
        }
    }
}
