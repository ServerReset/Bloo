package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** The stack's motion: a bouncy rise in, a quick retreat out, a damped reflow between slots. */
private val EnterSpring = spring<Float>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
private val ExitSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
private val ReflowSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

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
private fun BoxScope.ToastSlot(
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
