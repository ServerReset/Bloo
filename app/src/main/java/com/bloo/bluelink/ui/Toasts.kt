package com.bloo.bluelink.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.items.forEach { toast ->
            key(toast.id) { ToastItem(toast, state, hazeState, onCopy) }
        }
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
        enter = expandEnterSized(Alignment.Bottom) + fadeIn(),
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
    GlassSurface(
        shape = LargeShape,
        hazeState = hazeState,
        // High alpha even over a real blur: a toast reports something that just happened and has to
        // read the instant it appears rather than melt into the screen behind it.
        tint = scheme.surfaceContainerHigh.copy(alpha = if (canBlurBackdrops()) 0.82f else 0.96f),
        modifier = Modifier
            .fillMaxWidth()
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
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = accent, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    toast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                )
            }
            // Only errors are worth copying (to paste into a bug report); a "Saved" toast is not.
            if (toast.type != "success" && toast.type != "info") {
                MorphIconButton(onClick = { onCopy(toast.message) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
            }
            MorphIconButton(onClick = onDismiss) {
                Icon(AppIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
}
