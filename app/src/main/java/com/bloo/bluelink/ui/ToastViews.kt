package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- The toast slot and card that ToastHost lays out ---

/** The stack's motion: a bouncy rise in, a quick retreat out, a damped reflow between slots. */
private val EnterSpring = spring<Float>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
private val ExitSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
private val ReflowSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

/**
 * One toast: it owns its expiry clock, rises out of the search bar into its slot and retreats back
 * into it on the way out, and drops itself from the stack the moment its exit finishes.
 *
 * It is laid out once, full-width at the bottom of the stack, and its whole motion is a
 * `graphicsLayer` translation: [slot] is the slot index it currently occupies (animated, so older
 * toasts glide up as newer ones arrive), and [enter] is how far it has risen out of the search.
 * Only the horizontal insets change a measured size, and only while the search settles somewhere
 * new.
 */
@Composable
internal fun BoxScope.ToastSlot(
    toast: Toast,
    index: Int,
    stepPx: Float,
    liftPx: Float,
    startInsetPx: Float,
    endInsetPx: Float,
    searchRect: Rect?,
    toastWidthPx: Float,
    windowWidthPx: Float,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    onGone: () -> Unit,
) {
    // Its own clock, re-armed whenever the expiry moves (a repeat of the same message).
    LaunchedEffect(toast.id, toast.expireAt) {
        delay((toast.expireAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        toast.leaving = true
    }
    // The slot it rests in, animated toward [index]. Starts AT its index (no initial glide).
    val slot = remember(toast.id) { Animatable(index.toFloat()) }
    LaunchedEffect(index) { slot.animateTo(index.toFloat(), ReflowSpring) }
    // 0 = merged with the search bar, 1 = resting in its slot.
    val enter = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.id) { enter.animateTo(1f, EnterSpring) }
    LaunchedEffect(toast.leaving) {
        if (toast.leaving) {
            enter.animateTo(0f, ExitSpring)
            onGone()
        }
    }
    val swipe = rememberSwipeToDismiss(toast.id) { toast.leaving = true }
    val density = LocalDensity.current
    // The placement springs are underdamped, so an inset mid-flight can dip below zero; padding
    // must not (it throws). Clamp before it becomes a Dp. The lift is a distance too, for the same
    // reason.
    val startInset = with(density) { startInsetPx.coerceAtLeast(0f).toDp() }
    val endInset = with(density) { endInsetPx.coerceAtLeast(0f).toDp() }
    val lift = liftPx.coerceAtLeast(0f)
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(start = startInset, end = endInset)
            .graphicsLayer {
                val merge = 1f - enter.value.coerceIn(0f, 1f)
                // Rise out of the search's own row and settle into the slot; retreat on the way out.
                translationY = -slot.value * stepPx - lift + merge * SearchElementHeight.toPx()
                // While still merged, sit over the search element itself: centred on it and squeezed
                // to its width, so each toast looks like it grew out of the search bubble and split
                // off, and on the way out merges back into it.
                if (searchRect != null && toastWidthPx > 0f) {
                    translationX = merge * (searchRect.center.x - windowWidthPx / 2f)
                    val grow = (searchRect.width / toastWidthPx).coerceIn(0.12f, 1f)
                    scaleX = 1f - merge * (1f - grow)
                }
            }
            .then(swipe),
    ) {
        ToastCard(
            toast = toast,
            // Fade the CONTENT, not the glass: the pill stays a solid piece of the bar while it
            // peels off, and an alpha on the glass layer would clip its shadow.
            contentAlpha = { enter.value.coerceIn(0f, 1f) },
            onDismiss = { toast.leaving = true },
            hazeState = hazeState,
            onCopy = onCopy,
        )
    }
}

@Composable
private fun ToastCard(
    toast: Toast,
    contentAlpha: () -> Float,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (icon, accent) = when (toast.type) {
        ToastKind.SUCCESS -> AppIcons.CheckCircle to scheme.primary
        ToastKind.INFO -> AppIcons.Info to scheme.tertiary
        else -> Icons.Filled.ErrorOutline to scheme.error
    }
    GlassSurface(
        // A pill, like the search bar it emerges from; mostly clear glass with just enough tint to read.
        shape = CircleShape,
        hazeState = hazeState,
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            // At least the search element's height: normally it is exactly the bar it emerged
            // from, but a long message may wrap to a second line and grow past it.
            .heightIn(min = SearchElementHeight)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(ToastCardTag),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = GapGroup, end = GapHairline, top = GapHairline, bottom = GapHairline)
                .graphicsLayer { alpha = contentAlpha() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = accent, size = 34.dp, iconSize = 19.dp)
            Spacer(Modifier.width(GapGroup))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    toast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A grouped repeat shows its count, so a burst of one message reads as one toast.
            if (toast.count > 1) {
                Spacer(Modifier.width(GapRow))
                Text("×${toast.count}", style = MaterialTheme.typography.labelMedium, color = accent)
            }
            CopyButton(toast, accent, onCopy)
            MorphIconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Dismiss") }
        }
    }
}

/** The copy button: the glyph swaps to a check for a moment so the tap visibly registers. */
@Composable
private fun CopyButton(toast: Toast, accent: Color, onCopy: (String) -> Unit) {
    var copied by remember(toast.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MorphIconButton(
        onClick = {
            onCopy(toast.message)
            copied = true
            scope.launch {
                delay(1400)
                copied = false
            }
        },
    ) {
        if (copied) Icon(AppIcons.Check, contentDescription = "Copied", tint = accent)
        else Icon(Icons.Filled.ContentCopy, contentDescription = "Copy message")
    }
}
