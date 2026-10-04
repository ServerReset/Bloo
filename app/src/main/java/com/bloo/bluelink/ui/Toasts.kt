package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Most toasts shown at once; a newer one past this starts the oldest one's exit. */
private const val MaxToasts = 4

/** Minimum gap between one toast's expiry and the next, so they leave one after another. */
private const val ExpiryStaggerMs = 450L

/** How long a message stays: longer text and errors linger longer, up to a cap. */
private fun toastDurationMs(message: String, type: String): Long =
    if (type == "error") (5_500L + message.length * 45L).coerceAtMost(10_000L)
    else (3_500L + message.length * 30L).coerceAtMost(6_500L)

/** One message on screen. [visible] drives its enter/exit animation and, once it has finished
 *  leaving, its removal from the stack. */
@Stable
internal class Toast(val id: Long, val message: String, val type: String, expireAt: Long) {
    var expireAt by mutableLongStateOf(expireAt)
    val visible = MutableTransitionState(false).apply { targetState = true }
}

/** The app's one toast stack, for any composable that needs to tell the user something. */
internal val LocalToasts = androidx.compose.runtime.staticCompositionLocalOf<ToastState?> { null }

/**
 * The stack of live toasts, oldest at the top and newest at the bottom. Each toast expires on
 * its own clock, but never before the one above it -- so they always go oldest to newest, however
 * long each message happens to be. A repeat of the newest message refreshes it instead of
 * stacking an identical copy.
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
        val newest = items.lastOrNull { it.visible.targetState }
        if (newest != null && newest.message == message && newest.type == type) {
            newest.expireAt = expire
            return
        }
        items += Toast(nextId++, message, type, expire)
        while (items.count { it.visible.targetState } > MaxToasts) {
            items.first { it.visible.targetState }.visible.targetState = false
        }
    }

    fun dismiss(toast: Toast) {
        toast.visible.targetState = false
    }
}

/** The toast stack, drawn above the screen and above the keyboard, newest at the bottom. */
@Composable
internal fun ToastHost(
    state: ToastState,
    hazeState: HazeState,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Negotiate with the search element: the BOTTOM toast (the one on the search's own row)
    // clears it, every toast stacked above stays full width. Reads the search's published bounds
    // and dock from the floating registry -- see FloatingRegistry.searchDock. Recomposes when the
    // search moves/docks, which is fine (it is off during a drag that would matter).
    val registry = LocalFloatingRegistry.current
    val density = LocalDensity.current
    val gapPx = with(density) { GapRow.toPx() }
    val baseEdgePx = with(density) { 16.dp.toPx() }

    val visible = state.items.filter { it.visible.targetState }
    val bottomId = visible.lastOrNull()?.id

    // The search's live rect, read ONLY while a toast is up (null otherwise, so the common
    // no-toast case never subscribes to the registry's bounds map -- which the search bubble
    // writes on every drag frame). LIVE, not snapshotted: the pill can register or move AFTER a
    // toast mounts, and a frozen rect left the clearance stuck at "none".
    val searchRect = if (bottomId == null) null else registry.boundsOf(FloatingIds.Search)
    // The one piece of search-negotiation geometry, kept pure and unit-tested (see
    // ToastClearanceTest): given the search's rect and the window, it says where the BOTTOM
    // toast's content box must be inset (start/end) and how far the whole stack must lift
    // (bottom), in pixels. Everything below just applies it.
    val windowSize = LocalWindowInfo.current.containerSize
    val imeBottomPx = with(density) { WindowInsets.ime.getBottom(density).toFloat() }
    val navBottomPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }
    val clearance = toastClearance(
        searchRect = searchRect,
        windowWidthPx = windowSize.width.toFloat(),
        windowHeightPx = windowSize.height.toFloat(),
        imeBottomPx = imeBottomPx,
        navBottomPx = navBottomPx,
        baseEdgePx = baseEdgePx,
        gapPx = gapPx,
    )
    val liftPx = with(density) { clearance.bottomLiftPx.toDp() }

    Column(
        modifier
            .fillMaxWidth()
            // IME first, then the navigation bar: the keyboard's inset wins when it is up
            // (the toast rides just above it), and the bar's inset keeps the toast clear of
            // the gesture bar otherwise. This host used to be the Scaffold's snackbar, which
            // the Scaffold lifted above the navigation bar for free -- as an edge-to-edge
            // overlay of its own it has to ask for that inset now.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        state.items.forEach { toast ->
            key(toast.id) {
                // Only the bottom toast sits on the search's own row, so only IT is inset
                // (start OR end) to slot beside a corner-docked pill; the ones above it stay
                // full width. A centred pill is handled by the column lift below instead.
                val box = if (toast.id == bottomId) {
                    Modifier.padding(
                        start = with(density) { clearance.startInsetPx.toDp() },
                        end = with(density) { clearance.endInsetPx.toDp() },
                    )
                } else {
                    Modifier
                }
                Box(box) { ToastItem(toast, state, hazeState, onCopy) }
            }
        }
        // The middle-search lift, as the LAST column child so it sits below every toast and
        // pushes the whole stack up clear of the centred pill. Absent (0dp) otherwise.
        if (liftPx > 0.dp) Spacer(Modifier.height(liftPx))
    }
}

@Composable
private fun ToastItem(toast: Toast, state: ToastState, hazeState: HazeState, onCopy: (String) -> Unit) {
    // Its own clock: re-armed whenever the expiry moves (a repeat of the same message).
    LaunchedEffect(toast.id, toast.expireAt) {
        delay((toast.expireAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        toast.visible.targetState = false
    }
    // Gone from the list only once its exit animation has finished.
    LaunchedEffect(toast.id) {
        snapshotFlow {
            !toast.visible.targetState && !toast.visible.currentState
        }.first { it }
        state.items.remove(toast)
    }
    AnimatedVisibility(
        visibleState = toast.visible,
        enter = expandEnterSized(Alignment.Bottom),
        exit = expandExitSized(Alignment.Bottom) + fadeOut(),
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
    // A plain float written straight from the gesture (no coroutine per delta) follows the finger;
    // the Animatable only runs the spring-back or the fly-off once the finger lifts.
    var dragging by remember(toast.id) { mutableStateOf(false) }
    val dragPx = remember(toast.id) { mutableFloatStateOf(0f) }
    val settle = remember(toast.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { 110.dp.toPx() }
    val offsetX by remember { derivedStateOf { if (dragging) dragPx.floatValue else settle.value } }
    // The toast BLOBS OUT of the search bubble: it starts as a disc exactly over the bubble and swells,
    // corners easing from a circle to a card, into its slot. [origin] is the bubble's last known place
    // (null when search isn't on screen, and then the toast simply appears).
    val registry = LocalFloatingRegistry.current
    val origin = remember(toast.id) { registry.boundsOf(FloatingIds.Search) }
    val emerge = remember(toast.id) { Animatable(if (origin == null) 1f else 0f) }
    LaunchedEffect(toast.id) {
        emerge.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    var slotTopLeft by remember(toast.id) { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { slotTopLeft = it.positionInRoot() }
            .graphicsLayer {
                val e = emerge.value
                val o = origin
                val tl = slotTopLeft
                if (o != null && e < 1f) {
                    if (tl == null) {
                        alpha = 0f
                    } else {
                        clip = true
                        val full = Rect(0f, 0f, size.width, size.height)
                        val start = Rect(o.left - tl.x, o.top - tl.y, o.right - tl.x, o.bottom - tl.y)
                        shape = BlobShape(lerp(start, full, e), 0.5f)
                    }
                }
            },
    ) {
    GlassSurface(
        // A rounded card, not the search bar's own pill shape: a one-line toast read fine as
        // a pill, but a five-line error became a stadium with fully-round ends, which looked
        // like a sliver of text wrapped in a lozenge. The blob STILL emerges from the search
        // circle (see `origin`/`BlobShape`, which morphs rect -> full over `emerge`), so the
        // "it came out of search" read is unchanged; only the resting shape is.
        // A pill (fully rounded ends), not a fixed 24dp corner: the toasts read as smooth
        // rounded pills, the app's floating-chrome language.
        shape = RoundedCornerShape(50),
        hazeState = hazeState,
        // Mostly clear: the glass does the work (refraction over a barely-there tint), but enough tint
        // that the words read the instant it lands.
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.16f else 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            // Same height as the search pill (SearchLayer's barH), so a toast and the bar it
            // emerges from read as the same-sized element rather than a taller one.
            .heightIn(min = 52.dp)
            // Announced by TalkBack without the user hunting for it.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(offsetX) / (dismissPx * 2.2f)).coerceIn(0f, 1f) }
            .pointerInput(toast.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragPx.floatValue = settle.value
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragPx.floatValue += amount
                    },
                    onDragEnd = {
                        dragging = false
                        val released = dragPx.floatValue
                        scope.launch {
                            settle.snapTo(released)
                            if (abs(released) > dismissPx) {
                                settle.animateTo(if (released > 0) dismissPx * 4 else -dismissPx * 4)
                                onDismiss()
                            } else {
                                settle.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        val released = dragPx.floatValue
                        scope.launch {
                            settle.snapTo(released)
                            settle.animateTo(0f)
                        }
                    },
                )
            },
    ) {
        Row(
            Modifier
                // The words arrive once the blob is most of the way out.
                .graphicsLayer { alpha = ((emerge.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = accent, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(GapGroup))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    toast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                )
            }
            // Copy is always offered now, not only on errors. An error detail is the
            // message that most often wants pasting into a bug report, but a build number,
            // an address, or any other info toast is worth a tap too -- gating it by type
            // meant the one time a user DID want a success text there was no button. The
            // glyph swaps to a check for a moment so the tap visibly registers.
            var copied by remember(toast.id) { mutableStateOf(false) }
            val copyScope = rememberCoroutineScope()
            MorphIconButton(
                onClick = {
                    onCopy(toast.message)
                    copied = true
                    copyScope.launch {
                        delay(1400)
                        copied = false
                    }
                },
            ) {
                if (copied) {
                    Icon(AppIcons.Check, contentDescription = "Copied", tint = accent)
                } else {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy message")
                }
            }
            MorphIconButton(onClick = onDismiss) {
                Icon(AppIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
    }
}


/** A rounded rectangle at [rect] whose corner radius is [cornerFraction] of its shorter side: the blob. */
private class BlobShape(private val rect: Rect, private val cornerFraction: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = minOf(rect.width, rect.height) * cornerFraction
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(r, r)),
        )
    }
}

/**
 * The bottom toast's inset and the stack's lift, from where the search element is.
 *
 * Pure and JVM-testable (see ToastClearanceTest) so the arithmetic that decides "slot beside
 * the pill / lift above it / leave it alone" cannot silently regress the way it did when it was
 * inline and compared raw root coordinates against the wrong edge.
 *
 * Rules:
 *  - No search rect -> all zeros (full width, no lift).
 *  - Search centre in the middle third -> [bottomLiftPx] lifts the whole stack above it (the
 *    lift is measured from the rect's TOP up to the content bottom, i.e. excluding the IME/nav
 *    insets the toast column already pads by).
 *  - Search centre left/right -> the bottom toast is inset on that side so it ends clear of the
 *    pill (start for LEFT, end for RIGHT).
 */
internal data class ToastClearance(val startInsetPx: Float, val endInsetPx: Float, val bottomLiftPx: Float)

internal fun toastClearance(
    searchRect: Rect?,
    windowWidthPx: Float,
    windowHeightPx: Float,
    imeBottomPx: Float,
    navBottomPx: Float,
    baseEdgePx: Float,
    gapPx: Float,
): ToastClearance {
    if (searchRect == null || windowWidthPx <= 0f) return ToastClearance(0f, 0f, 0f)
    val dock = SearchDock.fromFrac(((searchRect.left + searchRect.right) / 2f) / windowWidthPx)
    return when (dock) {
        // Beside a LEFT-docked pill: the toast's content box starts at baseEdgePx, so pad the
        // start so it begins just right of the pill.
        SearchDock.LEFT -> ToastClearance(
            startInsetPx = (searchRect.right + gapPx - baseEdgePx).coerceAtLeast(0f),
            endInsetPx = 0f,
            bottomLiftPx = 0f,
        )
        // Beside a RIGHT-docked pill: the content box ends at windowWidth - baseEdgePx, so pad
        // the end so it ends just left of the pill.
        SearchDock.RIGHT -> ToastClearance(
            startInsetPx = 0f,
            endInsetPx = ((windowWidthPx - baseEdgePx) - (searchRect.left - gapPx)).coerceAtLeast(0f),
            bottomLiftPx = 0f,
        )
        // Middle: lift the whole stack above the pill, measured from the pill's top up to the
        // content bottom (window minus the IME and nav insets the column already pads by), plus
        // two rows of breathing room.
        SearchDock.CENTER -> ToastClearance(
            startInsetPx = 0f,
            endInsetPx = 0f,
            bottomLiftPx = ((windowHeightPx - imeBottomPx - navBottomPx) - searchRect.top)
                .coerceAtLeast(0f) + gapPx + gapPx,
        )
    }
}
