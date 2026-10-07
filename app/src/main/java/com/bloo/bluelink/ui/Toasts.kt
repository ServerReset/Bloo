package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
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

/** The screen-edge inset the search element and the toast stack share. */
private val ToastEdge = 16.dp

/** The narrowest a toast beside the search may get before it lifts above instead. */
private val MinBesideWidth = 220.dp

/** How far each tucked toast peeks above the one in front of it while the stack is collapsed. */
private val CollapsedStep = 14.dp

/** How far each tucked toast is inset from the sides while the stack is collapsed (a card pile). */
private val CollapsedInset = 10.dp

/** The toast card's root, so a test can pin its height to the search element's. */
internal const val ToastCardTag = "toastCard"

/**
 * Everything a toast draws around its message: the start padding, the badge, the gap, the two 40dp
 * icon buttons and the end padding. Added to a measured message width to get the toast's natural
 * width, which is what decides whether it can slot beside the search or has to lift above it.
 */
private val ToastChromeWidth = 12.dp + 34.dp + 12.dp + 40.dp + 40.dp + 4.dp

/** How long a message stays: longer text and errors linger longer, up to a cap. */
private fun toastDurationMs(message: String, type: String): Long =
    if (type == "error") (5_500L + message.length * 45L).coerceAtMost(10_000L)
    else (3_500L + message.length * 30L).coerceAtMost(6_500L)

/** The stack's motion: a bouncy rise in, a quick retreat out, a damped reflow between slots. */
private val EnterSpring = spring<Float>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
private val ExitSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
private val ReflowSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

/** One message on screen. Flipping [leaving] plays its exit; it is dropped from the stack once that finishes. */
@Stable
internal class Toast(val id: Long, val message: String, val type: String, expireAt: Long) {
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
 * Repeats are grouped: the same message as the newest live toast re-arms it and bumps its [Toast.count]
 * rather than stacking an identical card, so a burst of one kind of message reads as one toast.
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
            newest.count += 1
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
 * When more than one toast is up they tuck into a pile; tapping the front one fans the whole pile
 * out to one full slot each, and tapping again tucks them back. The placement around the search is
 * itself animated, so dragging the search from the centre to a side SLIDES the stack from "above
 * it" to "beside it" instead of snapping.
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

    // The pile: tucked behind each other until tapped, then fanned out one full slot each.
    val stacked = visible.count { !it.leaving } > 1
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(stacked) { if (!stacked) expanded = false }
    val stepTarget = with(density) { (if (expanded) SearchElementHeight + GapRow else CollapsedStep).toPx() }
    val step by animateFloatAsState(
        stepTarget,
        lowPowerAwareSpring<Float>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
        label = "toastStep",
    )

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
                    stacked = stacked,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
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
 * toasts glide up as newer ones arrive and as the pile fans out), and [enter] is how far it has
 * risen out of the search. Only the horizontal insets change a measured size, and only while the
 * search settles somewhere new.
 */
@Composable
private fun BoxScope.ToastSlot(
    toast: Toast,
    index: Int,
    stepPx: Float,
    liftPx: Float,
    startInsetPx: Float,
    endInsetPx: Float,
    stacked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
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
    // The card-pile tuck: older toasts are inset from both sides while collapsed, flush when fanned.
    val stackInset = if (stacked && !expanded) CollapsedInset * index else 0.dp
    val density = LocalDensity.current
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(
                start = with(density) { startInsetPx.toDp() } + stackInset,
                end = with(density) { endInsetPx.toDp() } + stackInset,
            )
            .graphicsLayer {
                val p = enter.value.coerceIn(0f, 1f)
                // Rise out of the search's row (one toast-height below its slot) and settle into it;
                // retreat back down into the bar on the way out. The whole stack also floats up by
                // the search's lift when the search is centred.
                translationY = -slot.value * stepPx - liftPx + (1f - p) * SearchElementHeight.toPx()
            }
            .then(swipe),
    ) {
        ToastCard(
            toast = toast,
            front = index == 0,
            stacked = stacked,
            expanded = expanded,
            onToggle = onToggle,
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
    front: Boolean,
    stacked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    contentAlpha: () -> Float,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (icon, accent) = when (toast.type) {
        "success" -> AppIcons.CheckCircle to scheme.primary
        "info" -> AppIcons.Info to scheme.tertiary
        else -> Icons.Filled.ErrorOutline to scheme.error
    }
    GlassSurface(
        // A pill, like the search bar it emerges from; mostly clear glass with just enough tint to read.
        shape = CircleShape,
        hazeState = hazeState,
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            // EXACTLY the search element's height, not a min: a toast is the bar it emerged from.
            .height(SearchElementHeight)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(ToastCardTag),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Tapping the body of a stacked toast fans the pile out (and tucks it back). The
                // copy/close buttons keep their own taps.
                .clickable(enabled = stacked) { onToggle() }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A grouped repeat shows its count, so a burst of one message reads as one toast.
            if (toast.count > 1) {
                Spacer(Modifier.width(GapRow))
                Text("×${toast.count}", style = MaterialTheme.typography.labelMedium, color = accent)
            }
            // The pile's affordance: the front toast carries the expand/collapse chevron.
            if (stacked && front) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Show all",
                    modifier = Modifier.size(18.dp),
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
 * beside it (inset on that side) as long as at least [minToastWidthPx] -- the newest message's own
 * natural width -- is left for the toast; a centred search, or one docked so close to the middle that
 * the toast would be squeezed, lifts the whole stack above it instead.
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
