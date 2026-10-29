package com.bloo.bluelink.ui

import com.bloo.uicommon.calmSpring
import com.bloo.uicommon.floatingSpring
import com.bloo.uicommon.IconBadge
import com.bloo.uicommon.MorphIconButton
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.DisposableEffect
import com.bloo.uicommon.SecondaryTextStyle
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.layout
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
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
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.canBlurBackdrops
import androidx.compose.ui.graphics.TransformOrigin

// --- The toast slot and card that ToastHost lays out ---

/**
 * The toasts' motion: a toast rises in from below the screen with an overshoot (it visibly jumps, then settles), leaves by
 * a quick no-bounce slide back off the screen, and the toasts around it glide on a damped reflow when the slots change.
 * It never comes from, or goes into, the search bar: the bar is just something the toast passes in front of or behind.
 */
private val EnterSpring = floatingSpring<Float>()
private val ExitSpring = calmSpring<Float>(Spring.StiffnessMedium)
private val ReflowSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

/** How much of each older toast peeks out above the newest while the pile is folded. */
private val PeekHeight = 9.dp

/** How far a vertical drag must go to fan the pile out (up) or fold it (down). */
private val StackSwipe = 36.dp

/**
 * One toast: it owns its expiry clock, rises in from below the screen into its slot and slides back off the screen on the
 * way out, and removes itself from the stack the moment its exit finishes.
 *
 * It is laid out once, full-width at the bottom of the stack, and its whole motion is a `graphicsLayer`
 * transform read in the draw phase: [slot] is the slot it currently occupies (animated, so older toasts glide
 * as newer ones arrive), [enter] is how far it has risen from below the screen (it overshoots past 1, which is the
 * jump), [exit] is how far it has left, and [expandFrac] is how far the folded pile is fanned out. Folded, the toasts
 * behind the newest are shrunken slivers peeking above it, sharing its width -- which beside a docked search is the
 * gap next to it.
 */
@Composable
internal fun BoxScope.ToastSlot(
    toast: Toast,
    index: Int,
    stepPx: Float,
    liftPx: Float,
    startInsetPx: Float,
    endInsetPx: Float,
    expandFrac: () -> Float,
    stackSize: Int,
    bottomInsetPx: Float,
    onToggleStack: () -> Unit,
    onSetExpanded: (Boolean) -> Unit,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    paused: Boolean,
    onHold: (Boolean) -> Unit,
    onGone: () -> Unit,
) {
    // Its own clock, re-armed whenever the expiry moves (a repeat of the same message).
    // Not while paused (search covering the stack, app in the background, a finger on a toast, the pile
    // fanned out): the clock restarts from the extended expiry when the pause ends.
    LaunchedEffect(toast.id, toast.expireAt, toast.hardExpireAt, paused) {
        if (paused) return@LaunchedEffect
        delay((minOf(toast.expireAt, toast.hardExpireAt) - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        toast.leaving = true
    }
    // A finger resting on the toast holds its clock. If the toast is torn down mid-press the hold is
    // released with it, so a vanished toast can never leave the whole stack paused.
    var pressing by remember(toast.id) { mutableStateOf(false) }
    DisposableEffect(toast.id) { onDispose { if (pressing) onHold(false) } }
    // The slot it rests in, animated toward [index]. Starts AT its index (no initial glide).
    val slot = remember(toast.id) { Animatable(index.toFloat()) }
    LaunchedEffect(index) { slot.animateTo(index.toFloat(), ReflowSpring) }
    // 0 = below the screen, 1 = resting in its slot; overshoots past 1 on the way up.
    val enter = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.id) { enter.animateTo(1f, EnterSpring) }
    // How it leaves: dismissed or expired, wherever it is in the stack (on top, in the middle, behind another), it drops
    // behind everything -- the other toasts and the search bar -- and off the bottom of the screen. A swipe is the one
    // exception: that toast has already flown off the side it was swiped to, and is dropped at once.
    val exit = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.leaving) {
        if (!toast.leaving) return@LaunchedEffect
        if (!toast.flung) exit.animateTo(1f, ExitSpring)
        onGone()
    }
    val swipe = Modifier.rememberSwipeToDismiss(toast.id) {
        toast.flung = true
        toast.leaving = true
    }
    val density = LocalDensity.current
    // The placement springs are underdamped, so an inset mid-flight can dip below zero; padding
    // must not (it throws). Clamp before it becomes a Dp. The lift is a distance too, for the same
    // reason.
    val startInset = with(density) { startInsetPx.coerceAtLeast(0f).toDp() }
    val endInset = with(density) { endInsetPx.coerceAtLeast(0f).toDp() }
    val lift = liftPx.coerceAtLeast(0f)
    val peekPx = with(density) { PeekHeight.toPx() }
    val barPx = with(density) { ChromeBarHeight.toPx() }
    val swipePx = with(density) { StackSwipe.toPx() }
    // Read through derivedStateOf: composition only hears the fold/fan flip, not every frame of the animation.
    val folded by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { expandFrac() < 0.5f } }
    val foldedNow by androidx.compose.runtime.rememberUpdatedState(folded)
    val toggle by androidx.compose.runtime.rememberUpdatedState(onSetExpanded)
    var vDrag by remember(toast.id) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(
        Modifier.align(Alignment.BottomStart)
            // A leaving toast goes behind every other one (and so behind the search bar, which is drawn after the stack).
            .zIndex(if (toast.leaving) -1f else 0f)
            .fillMaxWidth()
            .padding(start = startInset, end = endInset)
            .graphicsLayer {
                val e = expandFrac()
                val s = slot.value
                val enterV = enter.value
                // Folded, each older toast peeks a sliver above the one in front (two at most); fanned out,
                // each takes its own full slot.
                val depth = minOf(s, 2f)
                val stackY = -depth * peekPx + (-s * stepPx + depth * peekPx) * e
                val baseY = stackY - lift
                val merge = 1f - enterV
                var ty = baseY
                var tx = 0f
                // Below the screen's bottom edge, from where it rests: its own height plus the stack's offset from the bottom.
                val offscreen = size.height - baseY + bottomInsetPx + 8f
                if (merge > 0f) {
                    ty = baseY + merge * offscreen
                } else if (merge < 0f) {
                    // The overshoot of the rise: a touch past the slot, then settling.
                    ty = baseY + merge * barPx * 0.6f
                }
                // Straight down and out, fully opaque: it is drawn behind everything, so it passes behind what is in front.
                ty += exit.value * offscreen
                // Stretches tall in flight (a quick squash-and-stretch), shrinks as it sits deeper in the pile.
                val stretch = 1f + 0.09f * kotlin.math.sin(Math.PI.toFloat() * enterV.coerceIn(0f, 1f))
                val shrink = 1f - 0.05f * depth * (1f - e)
                translationX = tx
                translationY = ty
                scaleX = shrink
                scaleY = stretch * shrink
                transformOrigin = TransformOrigin(0.5f, 1f)
                // Deeper than two behind the newest, a folded toast is not there at all.
                alpha = 1f - (1f - e) * (s - 2f).coerceIn(0f, 1f)
            }
            .pointerInput(toast.id) {
                // Observes only (Initial pass, never consumes), so the swipe and the buttons still work.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressing = true
                    onHold(true)
                    waitForUpOrCancellation(PointerEventPass.Initial)
                    pressing = false
                    onHold(false)
                }
            }
            .then(swipe)
            // Up fans the pile out, down folds it back: the same gesture a notification stack answers to.
            .pointerInput(toast.id, stackSize > 1) {
                if (stackSize > 1) {
                    detectVerticalDragGestures(
                        onDragStart = { vDrag = 0f },
                        onVerticalDrag = { change, amount -> change.consume(); vDrag += amount },
                        onDragEnd = {
                            if (vDrag < -swipePx && foldedNow) toggle(true)
                            else if (vDrag > swipePx && !foldedNow) toggle(false)
                            vDrag = 0f
                        },
                        onDragCancel = { vDrag = 0f },
                    )
                }
            },
    ) {
        // The card stops at MaxToastWidth in the stack above the search, centred in the stack; beside the
        // search it takes the whole gap. The width BLENDS between the two (driven by the same spring as the
        // insets), so moving the search never snaps a toast to full width before it animates.
        val beside = startInsetPx > 1f || endInsetPx > 1f
        val besideFrac = animateFloatAsState(if (beside) 1f else 0f, calmSpring(Spring.StiffnessMediumLow), label = "toastBeside")
        Box(
            Modifier.layout { measurable, constraints ->
                val avail = constraints.maxWidth
                val capped = minOf(avail, MaxToastWidth.roundToPx())
                val w = (capped + (avail - capped) * besideFrac.value).roundToInt().coerceIn(0, avail)
                val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(avail, p.height) { p.place(Alignment.CenterHorizontally.align(p.width, avail, layoutDirection), 0) }
            },
        ) {
            ToastCard(
                toast = toast,
                // Fade the CONTENT, not the glass: the pill stays a solid piece of the bar while it peels
                // off, and a toast folded behind the newest shows only its edge.
                contentAlpha = {
                    val behind = slot.value.coerceIn(0f, 1f) * (1f - expandFrac())
                    enter.value.coerceIn(0f, 1f) * (1f - behind)
                },
                onDismiss = { toast.leaving = true },
                hazeState = hazeState,
                onCopy = onCopy,
                // A tap on a pile fans it out or folds it back.
                onTap = if (stackSize > 1) onToggleStack else null,
            )
        }
    }
}

@Composable
private fun ToastCard(
    toast: Toast,
    contentAlpha: () -> Float,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    onTap: (() -> Unit)?,
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
        // A pile fans out or folds when tapped.
        onClick = onTap,
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
        modifier = Modifier.fillMaxWidth()
            // At least the search element's height: normally it is exactly the bar it emerged
            // from, but a long message may wrap to a second line and grow past it.
            .heightIn(min = ChromeBarHeight)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(ToastCardTag),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = GapGroup, end = GapHairline, top = GapHairline, bottom = GapHairline)
                .graphicsLayer { alpha = contentAlpha() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = accent, size = 34.dp, iconSize = 19.dp)
            Spacer(Modifier.width(GapGroup))
            SelectionContainer(Modifier.weight(1f)) {
                RevealText(toast.message, style = SecondaryTextStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            CopyButton(toast, accent, onCopy)
            MorphIconButton(onClick = onDismiss, description = "Dismiss") { Icon(AppIcons.Close, contentDescription = null) }
        }
    }
}

/** The copy button: the glyph swaps to a check for a moment so the tap visibly registers. */
@Composable
private fun CopyButton(toast: Toast, accent: Color, onCopy: (String) -> Unit) {
    var copied by remember(toast.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MorphIconButton(
        description = if (copied) "Copied" else "Copy message",
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
