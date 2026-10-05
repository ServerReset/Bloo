package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MaxToasts = 4

/** Minimum gap between one toast's expiry and the next, so they leave one after another. */
private const val ExpiryStaggerMs = 450L

/** The narrowest a toast beside the search pill may get before it lifts above the pill instead. */
private val MinBesideWidth = 220.dp

/** How long a message stays: longer text and errors linger longer, up to a cap. */
private fun toastDurationMs(message: String, type: String): Long =
    if (type == "error") (5_500L + message.length * 45L).coerceAtMost(10_000L)
    else (3_500L + message.length * 30L).coerceAtMost(6_500L)

/** One message on screen. Flipping [leaving] plays its exit; it is dropped from the stack once that finishes. */
@Stable
internal class Toast(val id: Long, val message: String, val type: String, expireAt: Long) {
    var expireAt by mutableLongStateOf(expireAt)
    var leaving by mutableStateOf(false)
}

/** The app's one toast stack, for any composable that needs to tell the user something. */
internal val LocalToasts = androidx.compose.runtime.staticCompositionLocalOf<ToastState?> { null }

/**
 * The stack of live toasts, oldest at the top and newest at the bottom. Each toast expires on its own clock,
 * but never before the one above it, so they always go oldest to newest however long each message is.
 */
@Stable
internal class ToastState {
    val items = mutableStateListOf<Toast>()
    private var nextId = 0L
    private var lastExpireAt = 0L

    fun show(message: String, type: String) {
        val now = SystemClock.elapsedRealtime()
        val expire = maxOf(now + toastDurationMs(message, type), lastExpireAt + ExpiryStaggerMs)
        lastExpireAt = expire
        val newest = items.lastOrNull { !it.leaving }
        if (newest != null && newest.message == message && newest.type == type) {
            newest.expireAt = expire
            return
        }
        items += Toast(nextId++, message, type, expire)
        items.filter { !it.leaving }.dropLast(MaxToasts).forEach { it.leaving = true }
    }

    fun dismiss(toast: Toast) {
        toast.leaving = true
    }
}

/** The toast stack, drawn above the screen and above the keyboard, newest at the bottom, going around the search pill. */
@Composable
internal fun ToastHost(
    state: ToastState,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bottomId = state.items.lastOrNull { !it.leaving }?.id

    // The search pill's live rect, read ONLY while a toast is up: the pill writes it on every drag frame, and a
    // frozen copy would leave the clearance stuck at "none" when the pill registers or moves after a toast mounts.
    val searchRect = if (bottomId == null) null else LocalFloatingRegistry.current.boundsOf(FloatingIds.Search)
    val window = LocalWindowInfo.current.containerSize
    val clearance = toastClearance(
        searchRect = searchRect,
        windowWidthPx = window.width.toFloat(),
        windowHeightPx = window.height.toFloat(),
        imeBottomPx = WindowInsets.ime.getBottom(density).toFloat(),
        navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat(),
        baseEdgePx = with(density) { ToastEdge.toPx() },
        gapPx = with(density) { GapRow.toPx() },
        minToastWidthPx = with(density) { MinBesideWidth.toPx() },
    )

    Column(
        modifier
            .fillMaxWidth()
            // The keyboard's inset wins when it is up (the stack rides just above it); the bar's keeps it clear of
            // the gesture bar otherwise.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = ToastEdge, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        state.items.forEach { toast ->
            key(toast.id) {
                // Only the bottom toast shares the pill's row, so only it is inset to slot beside a corner-docked
                // pill; the ones above keep their full width.
                val beside = if (toast.id == bottomId) {
                    Modifier.padding(
                        start = with(density) { clearance.startInsetPx.toDp() },
                        end = with(density) { clearance.endInsetPx.toDp() },
                    )
                } else {
                    Modifier
                }
                Box(beside) { ToastItem(toast, state, hazeState, onCopy) }
            }
        }
        // A centred (or too-narrow-to-sit-beside) pill lifts the whole stack: spacer as the last child.
        if (clearance.bottomLiftPx > 0f) Spacer(Modifier.height(with(density) { clearance.bottomLiftPx.toDp() }))
    }
}

private val ToastEdge = 16.dp

@Composable
private fun ToastItem(toast: Toast, state: ToastState, hazeState: HazeState, onCopy: (String) -> Unit) {
    // Its own clock, re-armed whenever the expiry moves (a repeat of the same message).
    LaunchedEffect(toast.id, toast.expireAt) {
        delay((toast.expireAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        toast.leaving = true
    }
    val pop = rememberPop(toast.leaving) { state.items.remove(toast) }
    // The stack makes room as the toast pops up and closes the gap as it retreats: its slot's height follows the
    // pop, so its neighbours slide with it instead of jumping.
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val h = (placeable.height * pop.value.coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, h) { placeable.place(0, 0) }
        },
    ) {
        ToastCard(toast, pop.asState(), onDismiss = { state.dismiss(toast) }, hazeState = hazeState, onCopy = onCopy)
    }
}

@Composable
private fun ToastCard(toast: Toast, pop: State<Float>, onDismiss: () -> Unit, hazeState: HazeState, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, accent) = when (toast.type) {
        "success" -> AppIcons.CheckCircle to scheme.primary
        "info" -> AppIcons.Info to scheme.tertiary
        else -> Icons.Filled.ErrorOutline to scheme.error
    }
    val swipe = rememberSwipeToDismiss(toast.id, onDismiss)
    // The toast pops out of the search pill and retreats into it: its centre starts on the pill's centre and the
    // shared pop carries it to its slot (and back).
    val registry = LocalFloatingRegistry.current
    val origin = remember(toast.id) { registry.boundsOf(FloatingIds.Search) }
    var slotCenter by remember(toast.id) { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { slotCenter = it.boundsInRoot().center }
            .popGraphics(
                progress = { if (slotCenter == null) 0f else pop.value },
                fromX = { if (origin != null) slotCenter?.let { origin.center.x - it.x } ?: 0f else 0f },
                fromY = { if (origin != null) slotCenter?.let { origin.center.y - it.y } ?: 0f else 0f },
                minScale = 0.2f,
                alphaGain = 3f,
            ),
    ) {
        GlassSurface(
            // A pill, like the search bar it emerges from; mostly clear glass with just enough tint to read.
            shape = CircleShape,
            hazeState = hazeState,
            tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
            modifier = Modifier
                .fillMaxWidth()
                // The search pill's height, so a toast and the bar it emerges from read as the same element.
                .heightIn(min = 52.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .then(swipe),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = icon, tint = accent, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.width(GapGroup))
                SelectionContainer(Modifier.weight(1f)) {
                    Text(toast.message, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                }
                CopyButton(toast, accent, onCopy)
                MorphIconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Dismiss") }
            }
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

/**
 * Horizontal swipe-to-dismiss for a toast: it follows the finger and fades, then flies off past
 * [DismissDistance] or springs back. The finger's position is a plain float written straight from the gesture (no
 * coroutine per delta); the Animatable only runs the fly-off or spring-back once the finger lifts.
 */
@Composable
private fun rememberSwipeToDismiss(key: Any, onDismiss: () -> Unit): Modifier {
    var dragging by remember(key) { mutableStateOf(false) }
    val dragPx = remember(key) { mutableFloatStateOf(0f) }
    val settle = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { DismissDistance.toPx() }
    val offsetX by remember { derivedStateOf { if (dragging) dragPx.floatValue else settle.value } }
    fun release(flyOff: Boolean) {
        dragging = false
        val released = dragPx.floatValue
        scope.launch {
            settle.snapTo(released)
            if (flyOff && abs(released) > dismissPx) {
                settle.animateTo(if (released > 0) dismissPx * 4 else -dismissPx * 4)
                onDismiss()
            } else {
                settle.animateTo(0f)
            }
        }
    }
    return Modifier
        .offset { IntOffset(offsetX.roundToInt(), 0) }
        .graphicsLayer { alpha = (1f - abs(offsetX) / (dismissPx * 2.2f)).coerceIn(0f, 1f) }
        .pointerInput(key) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; dragPx.floatValue = settle.value },
                onHorizontalDrag = { change, amount -> change.consume(); dragPx.floatValue += amount },
                onDragEnd = { release(flyOff = true) },
                onDragCancel = { release(flyOff = false) },
            )
        }
}

private val DismissDistance = 110.dp

/** The bottom toast's inset and the stack's lift, from where the search element is. */
internal data class ToastClearance(val startInsetPx: Float, val endInsetPx: Float, val bottomLiftPx: Float)

/**
 * Where the stack goes around the search pill. A pill docked to a side lets the bottom toast slot beside it
 * (inset on that side) as long as at least [minToastWidthPx] is left for the toast; a centred pill, or one
 * docked so close to the middle that the toast would be squeezed, lifts the whole stack above it instead.
 */
internal fun toastClearance(
    searchRect: Rect?,
    windowWidthPx: Float,
    windowHeightPx: Float,
    imeBottomPx: Float,
    navBottomPx: Float,
    baseEdgePx: Float,
    gapPx: Float,
    minToastWidthPx: Float = 0f,
): ToastClearance {
    if (searchRect == null || windowWidthPx <= 0f) return ToastClearance(0f, 0f, 0f)
    val lift = ToastClearance(
        startInsetPx = 0f,
        endInsetPx = 0f,
        // From the pill's top up to the content bottom (window minus the IME and nav insets the column pads by), plus
        // two rows of breathing room.
        bottomLiftPx = ((windowHeightPx - imeBottomPx - navBottomPx) - searchRect.top).coerceAtLeast(0f) + gapPx + gapPx,
    )
    val contentWidth = windowWidthPx - 2 * baseEdgePx
    return when (SearchDock.fromFrac(((searchRect.left + searchRect.right) / 2f) / windowWidthPx)) {
        SearchDock.LEFT -> {
            val inset = (searchRect.right + gapPx - baseEdgePx).coerceAtLeast(0f)
            if (contentWidth - inset < minToastWidthPx) lift else ToastClearance(inset, 0f, 0f)
        }
        SearchDock.RIGHT -> {
            val inset = ((windowWidthPx - baseEdgePx) - (searchRect.left - gapPx)).coerceAtLeast(0f)
            if (contentWidth - inset < minToastWidthPx) lift else ToastClearance(0f, inset, 0f)
        }
        SearchDock.CENTER -> lift
    }
}
