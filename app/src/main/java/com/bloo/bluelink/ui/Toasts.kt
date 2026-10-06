package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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

/** The narrowest a toast beside the search may get before it lifts above instead. */
private val MinBesideWidth = 220.dp

/** The screen-edge inset the search element and the toast stack share. */
private val ToastEdge = 16.dp

/** The toast card's root, so a test can pin its height to the search element's. */
internal const val ToastCardTag = "toastCard"

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

/**
 * The toast stack, drawn above the screen and above the keyboard, newest at the bottom, going around
 * the search element.
 *
 * The stack and the search element share ONE height ([SearchElementHeight]) and ONE edge inset, and
 * the stack pads by the search element's OWN bottom inset (`nav union ime`), so the newest toast's
 * bottom lines up exactly with the search bar's bottom when it slots beside it. The newest toast
 * shares the search's row (inset on the search's side); the older ones stack full-width above it. A
 * centred search, or a docked one with no room left beside it, lifts the whole stack above instead.
 */
@Composable
internal fun ToastHost(
    state: ToastState,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The newest toast is the one that shares the search element's own row; the older ones stack
    // above it.
    val newestId = state.items.lastOrNull { !it.leaving }?.id

    // The search element's live rect, read ONLY while a toast is up: it writes it on every drag
    // frame, and a frozen copy would leave the clearance stuck when it registers or moves after a
    // toast mounts.
    val searchRect = if (newestId == null) null else LocalFloatingRegistry.current.boundsOf(FloatingIds.Search)
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
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = ToastEdge, end = ToastEdge, top = GapRow, bottom = ToastEdge),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        state.items.forEach { toast ->
            key(toast.id) {
                // Only the newest toast shares the search element's row, so only it is inset to slot
                // beside a corner-docked search; the ones above keep their full width.
                val beside = if (toast.id == newestId) {
                    Modifier.padding(
                        start = with(density) { clearance.startInsetPx.toDp() },
                        end = with(density) { clearance.endInsetPx.toDp() },
                    )
                } else {
                    Modifier
                }
                Box(beside) { ToastSlot(toast, state, hazeState, onCopy) }
            }
        }
        // A centred (or too-narrow-to-sit-beside) search lifts the whole stack above it.
        if (clearance.bottomLiftPx > 0f) Spacer(Modifier.height(with(density) { clearance.bottomLiftPx.toDp() }))
    }
}

/**
 * One toast's slot in the stack: it owns the toast's expiry clock, plays a single expand/fade enter
 * and exit (which is also what makes the stack close the gap around it), and drops itself from the
 * stack the moment the exit finishes.
 */
@Composable
private fun ToastSlot(toast: Toast, state: ToastState, hazeState: HazeState, onCopy: (String) -> Unit) {
    // Its own clock, re-armed whenever the expiry moves (a repeat of the same message).
    LaunchedEffect(toast.id, toast.expireAt) {
        delay((toast.expireAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        toast.leaving = true
    }
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }
    LaunchedEffect(toast.leaving) { if (toast.leaving) visibleState.targetState = false }
    // Drop it from the stack the instant its exit finishes, so its neighbours close the gap.
    LaunchedEffect(visibleState.currentState, visibleState.isIdle) {
        if (visibleState.isIdle && !visibleState.currentState) state.items.remove(toast)
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
    ) {
        ToastCard(toast, onDismiss = { state.dismiss(toast) }, hazeState = hazeState, onCopy = onCopy)
    }
}

@Composable
private fun ToastCard(toast: Toast, onDismiss: () -> Unit, hazeState: HazeState, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, accent) = when (toast.type) {
        "success" -> AppIcons.CheckCircle to scheme.primary
        "info" -> AppIcons.Info to scheme.tertiary
        else -> Icons.Filled.ErrorOutline to scheme.error
    }
    val swipe = rememberSwipeToDismiss(toast.id, onDismiss)
    GlassSurface(
        // A pill, like the search bar it emerges from; mostly clear glass with just enough tint to read.
        shape = CircleShape,
        hazeState = hazeState,
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            // EXACTLY the search element's height, not a min: a toast is the bar it emerged from, so
            // the icon buttons (40dp) get the small padding that keeps the content inside it.
            .height(SearchElementHeight)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(ToastCardTag)
            .then(swipe),
    ) {
        Row(
            Modifier.padding(start = GapGroup, end = GapHairline, top = GapHairline, bottom = GapHairline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = accent, size = 34.dp, iconSize = 19.dp)
            Spacer(Modifier.width(GapGroup))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    toast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** The newest toast's inset and the stack's lift, from where the search element is. */
internal data class ToastClearance(val startInsetPx: Float, val endInsetPx: Float, val bottomLiftPx: Float)

/**
 * Where the stack goes around the search element. A search docked to a side lets the NEWEST toast slot
 * beside it (inset on that side) as long as at least [minToastWidthPx] is left for the toast; a
 * centred search, or one docked so close to the middle that the toast would be squeezed, lifts the
 * whole stack above it instead.
 *
 * The lift is measured against the stack's OWN content bottom: the toast column sits at the search's
 * inset (`max(nav, ime)`) plus [baseEdgePx], so the newest toast's bottom lines up exactly with the
 * search bar's bottom when it slots beside it.
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
    // The toast column's own bottom inset, the same `nav union ime` the search element uses.
    val contentBottom = windowHeightPx - maxOf(imeBottomPx, navBottomPx) - baseEdgePx
    val lift = ToastClearance(
        startInsetPx = 0f,
        endInsetPx = 0f,
        // From the search's top up to the toast content's bottom, plus one gap of breathing room.
        bottomLiftPx = (contentBottom - searchRect.top + gapPx).coerceAtLeast(0f),
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
