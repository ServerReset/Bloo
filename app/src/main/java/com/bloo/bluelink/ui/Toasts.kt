package com.bloo.bluelink.ui

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

private const val MaxToasts = 4

/** Minimum gap between one toast's expiry and the next, so they leave one after another. */
private const val ExpiryStaggerMs = 450L

/** The screen-edge inset the search element and the toast stack share. */
private val ToastEdge = 16.dp

/** The narrowest a toast beside the search may get before it lifts above instead. */
private val MinBesideWidth = 220.dp

/** The toast card's root, so a test can pin its height to the search element's. */
internal const val ToastCardTag = "toastCard"

/**
 * Everything a toast draws around its message: the start padding, the badge, the gap, the two 40dp
 * icon buttons and the end padding. Added to a measured message width to get the toast's natural
 * width, which is what decides whether it can slot beside the search or has to lift above it.
 */
private val ToastChromeWidth = 12.dp + 34.dp + 12.dp + 40.dp + 40.dp + 4.dp

/** How long a message stays: longer text and errors linger longer, up to a cap. */
private fun toastDurationMs(message: String, type: ToastKind): Long =
    if (type == ToastKind.ERROR) (5_500L + message.length * 45L).coerceAtMost(10_000L)
    else (3_500L + message.length * 30L).coerceAtMost(6_500L)

/** One message on screen. Flipping [leaving] plays its exit; it is dropped from the stack once that finishes. */
@Stable
internal class Toast(val id: Long, val message: String, val type: ToastKind, expireAt: Long) {
    var expireAt by mutableLongStateOf(expireAt)
    var leaving by mutableStateOf(false)
    /** How many times this same message has repeated without leaving, shown as a count. */
    var count by mutableIntStateOf(1)
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
    private var lastExpireAt = 0L

    fun show(message: String, type: ToastKind) {
        val now = SystemClock.elapsedRealtime()
        val expire = maxOf(now + toastDurationMs(message, type), lastExpireAt + ExpiryStaggerMs)
        lastExpireAt = expire
        // Identical toasts collapse into one entry wherever it sits in the stack: re-arm it and bump
        // its count instead of pushing a duplicate card.
        val existing = items.lastOrNull { !it.leaving && it.message == message && it.type == type }
        if (existing != null) {
            existing.expireAt = expire
            existing.count += 1
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
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium
    val anchor = LocalSearchAnchor.current

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
    val chromePx = with(density) { ToastChromeWidth.toPx() }
    val maxWidthPx = contentWidthPx * 0.92f

    // THIS message decides beside vs above: measure its natural width and hand that to the clearance
    // maths instead of one fixed minimum.
    val newestWidthPx = newest
        ?.let { naturalToastWidthPx(measurer, it.message, textStyle, chromePx, minWidthPx, maxWidthPx) }
        ?: minWidthPx

    val clearance = toastClearance(
        searchRect = searchRect,
        windowWidthPx = window.width.toFloat(),
        windowHeightPx = window.height.toFloat(),
        imeBottomPx = imeBottomPx,
        navBottomPx = navBottomPx,
        baseEdgePx = edgePx,
        gapPx = gapPx,
        minToastWidthPx = newestWidthPx,
    )

    // Animate the placement, so moving the search between the centre and a side SLIDES the stack
    // rather than snapping it.
    val placeSpec = lowPowerAwareSpring<Float>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
    val startInset by animateFloatAsState(clearance.startInsetPx, placeSpec, label = "toastStartInset")
    val endInset by animateFloatAsState(clearance.endInsetPx, placeSpec, label = "toastEndInset")
    val lift by animateFloatAsState(clearance.bottomLiftPx, placeSpec, label = "toastLift")

    // Every toast gets its own full slot; oldest above, newest at the bottom, nothing tucked.
    val step = with(density) { (SearchElementHeight + GapRow).toPx() }

    Box(
        modifier
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
                    onGone = { state.items.remove(toast) },
                )
            }
        }
    }
}

/** A message's natural toast width: the measured single-line text plus the toast's chrome. */
private fun naturalToastWidthPx(
    measurer: TextMeasurer,
    message: String,
    style: TextStyle,
    chromePx: Float,
    minPx: Float,
    maxPx: Float,
): Float {
    val textPx = measurer.measure(
        text = AnnotatedString(message),
        style = style,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxPx.roundToInt()),
    ).size.width
    return (chromePx + textPx).coerceIn(minPx, maxPx)
}

