package com.bloo.bluelink.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.layout
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

private const val MaxToasts = 4

/** Minimum gap between one toast's expiry and the next, so they leave one after another. */
private const val ExpiryStaggerMs = 450L

/** How far past its expiry a toast may linger before it is treated as stale (its clock never ran). */
private const val StaleAfterMs = 3_000L

/** How much longer than its own duration a repeating toast may live before it is dismissed regardless. */
private const val MaxLifetimeExtraMs = 8_000L

/** The screen-edge inset the search element and the toast stack share. */
private val ToastEdge = 16.dp

/** The narrowest a toast beside the search may get before it lifts above instead. */
private val MinBesideWidth = 220.dp

/** The widest a toast gets, however wide the display (a comfortable reading width). */
internal val MaxToastWidth = 420.dp

/** The toast card's root, so a test can pin its height to the search element's. */
internal const val ToastCardTag = "toastCard"

/** How long a message stays: longer text and errors linger longer, up to a cap. */
private fun toastDurationMs(message: String, type: ToastKind): Long =
    if (type == ToastKind.ERROR) (5_500L + message.length * 45L).coerceAtMost(10_000L)
    else (3_500L + message.length * 30L).coerceAtMost(6_500L)

/** One message on screen. Flipping [leaving] plays its exit; it is dropped from the stack once that finishes. */
@Stable
internal class Toast(val id: Long, val message: String, val type: ToastKind, expireAt: Long, hardExpireAt: Long = expireAt + MaxLifetimeExtraMs) {
    var expireAt by mutableLongStateOf(expireAt)
    /** The latest it may stay however often a repeat re-arms it: a message that keeps repeating must still go. */
    var hardExpireAt by mutableLongStateOf(hardExpireAt)
    var leaving by mutableStateOf(false)
    /** How many times this same message has repeated without leaving, shown as a count. */
    var count by mutableIntStateOf(1)
    /** False once split out of a group, so a later repeat starts a fresh group instead of rejoining it. */
    var groupable = true
}

/** The app's one toast stack, for any composable that needs to tell the user something. */
internal val LocalToasts = androidx.compose.runtime.staticCompositionLocalOf<ToastState?> { null }

/**
 * The stack of live toasts, oldest at the top and newest at the bottom. Each toast expires on its own clock,
 * but never before the one above it, so they always go oldest to newest however long each message is.
 *
 * Repeats are grouped: any live toast with the same message and kind is re-armed and its
 * [Toast.count] bumped rather than stacking an identical card, so a burst of one message reads as
 * one toast. Different messages or kinds all stay up.
 */
@Stable
internal class ToastState {
    val items = mutableStateListOf<Toast>()
    private var nextId = 0L

    /**
     * While true no toast's clock runs: the search results are covering the stack, the app is in the
     * background, or a finger is holding a toast. Set by the host through [pauseClocks].
     */
    var paused by mutableStateOf(false)
        private set
    private var pausedAt = 0L

    /** The expiry a new toast gets: its own duration, but never before the last live one plus a stagger. */
    private fun nextExpiry(now: Long, durationMs: Long): Long {
        val lastLive = items.filter { !it.leaving }.maxOfOrNull { it.expireAt } ?: 0L
        return maxOf(now + durationMs, lastLive + ExpiryStaggerMs)
    }

    @Synchronized
    fun show(message: String, type: ToastKind) {
        // A blank message is a caller bug, not something to put a pill on screen for.
        if (message.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        // Toasts that overstayed because no host was mounted to run their clocks (a show before the
        // UI exists, or a screen swap) are gone, not replayed as a burst later.
        items.removeAll { !paused && it.expireAt + StaleAfterMs < now }
        // Created while the clocks are paused: resuming adds the whole pause to every live toast, so this one
        // starts the pause already owed, and nets out to its intended duration.
        val expire = nextExpiry(now, toastDurationMs(message, type)) - (if (paused) now - pausedAt else 0L)
        // Identical toasts collapse into one entry wherever it sits in the stack: re-arm it and bump
        // its count instead of pushing a duplicate card.
        val existing = items.lastOrNull { !it.leaving && it.groupable && it.message == message && it.type == type }
        if (existing != null) {
            existing.expireAt = expire
            existing.count += 1
            return
        }
        items += Toast(nextId++, message, type, expire, now + toastDurationMs(message, type) + MaxLifetimeExtraMs)
        trimToLimit()
    }

    /** Tapping a grouped toast splits it back into one card per occurrence (up to [MaxToasts]). */
    @Synchronized
    fun expand(toast: Toast) {
        if (toast.count <= 1 || toast.leaving || toast !in items) return
        val extra = minOf(toast.count, MaxToasts) - 1
        toast.count = 1
        toast.groupable = false
        val at = items.indexOf(toast) + 1
        // One card per occurrence, each leaving a beat after the one above it like any other stack.
        repeat(extra) { i ->
            items.add(at + i, Toast(nextId++, toast.message, toast.type, toast.expireAt + (i + 1) * ExpiryStaggerMs, toast.hardExpireAt + (i + 1) * ExpiryStaggerMs).also { it.groupable = false })
        }
        trimToLimit()
    }

    /** Never more than [MaxToasts] live; the oldest are sent out, and any that have finished leaving are dropped. */
    private fun trimToLimit() {
        items.filter { !it.leaving }.dropLast(MaxToasts).forEach { it.leaving = true }
        // Leaving toasts are removed by their own exit animation. A backlog of them (a burst while the
        // stack is parked or unmounted) would never animate out, so cap that too.
        val leaving = items.filter { it.leaving }
        if (leaving.size > MaxToasts) items.removeAll(leaving.dropLast(MaxToasts).toSet())
    }

    @Synchronized
    fun dismiss(toast: Toast) {
        toast.leaving = true
    }

    /**
     * Stops or restarts every toast's clock. On resume each live toast gets back the time it spent
     * paused, so a message the user could not see is not spent while they were away from it.
     */
    @Synchronized
    fun pauseClocks(value: Boolean) {
        if (value == paused) return
        val now = SystemClock.elapsedRealtime()
        if (value) {
            pausedAt = now
        } else {
            val spent = now - pausedAt
            items.forEach { if (!it.leaving) { it.expireAt += spent; it.hardExpireAt += spent } }
        }
        paused = value
    }
}

/**
 * The toast stack, drawn above the screen and above the keyboard, newest at the bottom, going around
 * the search element.
 *
 * The stack is a single one-toast-tall [Box]: every toast is laid out at the same place and moved
 * purely by an animated `graphicsLayer` translation, so a toast entering, leaving or the stack
 * reflowing around it never re-lays-out (and so never re-rasterises the glass of) its neighbours.
 *
 * Every toast gets its own full slot (oldest on top, newest at the bottom); nothing is tucked behind
 * the newest or clipped. The placement around the search is itself animated, so dragging the search
 * from the centre to a side SLIDES the stack from "above it" to "beside it" instead of snapping.
 */
@Composable
internal fun ToastHost(
    state: ToastState,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchor = LocalSearchAnchor.current

    // One clock for the whole stack: it stops while the search results cover it, while the app is
    // not on screen, and while a finger holds any toast.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var held by remember { mutableIntStateOf(0) }
    val shouldPause = anchor.searchOpen || !resumed || held > 0
    LaunchedEffect(shouldPause) { state.pauseClocks(shouldPause) }
    // Leaving the host (a screen swap) must not leave the clocks stopped for good.
    DisposableEffect(state) { onDispose { state.pauseClocks(false) } }

    val visible = state.items
    // The newest toast is the one that shares the search element's own row; the older ones stack
    // above it.
    val newest = visible.lastOrNull { !it.leaving }
    val newestId = newest?.id

    // The search element's RESTING rect, read only while a toast is up.
    val searchRect = if (newestId == null) null else anchor.rect
    val window = LocalWindowInfo.current.containerSize
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val edgePx = with(density) { ToastEdge.toPx() }
    val gapPx = with(density) { GapRow.toPx() }
    val contentWidthPx = window.width.toFloat() - 2 * edgePx
    val minWidthPx = with(density) { MinBesideWidth.toPx() }

    val clearance = toastClearance(
        searchRect = searchRect,
        windowWidthPx = window.width.toFloat(),
        windowHeightPx = window.height.toFloat(),
        imeBottomPx = imeBottomPx,
        navBottomPx = navBottomPx,
        baseEdgePx = edgePx,
        gapPx = gapPx,
        // A docked search slots the toast beside it as long as the fixed beside-minimum fits; a
        // longer message wraps to its second line rather than lifting the whole stack above the
        // search (above is reserved for a centred search, or a window too narrow for even that).
        minToastWidthPx = minWidthPx,
    )

    // Animate the placement, so moving the search between the centre and a side SLIDES the stack
    // rather than snapping it.
    val placeSpec = lowPowerAwareSpring<Float>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
    val startInset by animateFloatAsState(clearance.startInsetPx, placeSpec, label = "toastStartInset")
    val endInset by animateFloatAsState(clearance.endInsetPx, placeSpec, label = "toastEndInset")
    val lift by animateFloatAsState(clearance.bottomLiftPx, placeSpec, label = "toastLift")

    // Every toast gets its own full slot; oldest above, newest at the bottom, nothing tucked.
    val step = with(density) { (ChromeBarHeight + GapRow).toPx() }

    // While the search results are up the toasts fade out and step aside (kept alive, just not placed
    // or hit-tested), so they never sit over the panel; they return when it closes.
    val showState = animateFloatAsState(if (anchor.searchOpen) 0f else 1f, tween(MotionShort), label = "toastShow")
    // The fade is read in the layer; composition sees only "parked or not", so the host does not recompose on
    // every frame of the search panel opening and closing.
    val parked by remember { derivedStateOf { anchor.searchOpen && showState.value < 0.01f } }
    Box(
        modifier
            .graphicsLayer { alpha = showState.value }
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) { if (!parked) p.place(0, 0) }
            }
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = ToastEdge, end = ToastEdge, bottom = ToastEdge),
    ) {
        visible.forEachIndexed { i, toast ->
            key(toast.id) {
                // Slot 0 is the NEWEST (in the search's own row); older toasts count up from it, so
                // the list (oldest first) runs the other way.
                val index = visible.lastIndex - i
                ToastSlot(
                    toast = toast,
                    index = index,
                    stepPx = step,
                    liftPx = lift,
                    // Only the newest toast shares the search element's row, so only it is inset to
                    // slot beside a corner-docked search; the ones above keep their full width.
                    startInsetPx = if (index == 0) startInset else 0f,
                    endInsetPx = if (index == 0) endInset else 0f,
                    searchRect = searchRect,
                    toastWidthPx = contentWidthPx,
                    windowWidthPx = window.width.toFloat(),
                    hazeState = hazeState,
                    onCopy = onCopy,
                    onExpand = { state.expand(toast) },
                    paused = state.paused,
                    onHold = { down -> held = (held + if (down) 1 else -1).coerceAtLeast(0) },
                    onGone = { state.items.remove(toast) },
                )
            }
        }
    }
}
