package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How a dialog rises from below the screen: bouncy, so it overshoots its resting place a little and
 * settles. Used for both the card's travel and the scrim's fade, which ride the same progress value
 * (1 = resting, 0 = fully off the bottom).
 */
internal val DialogEnterSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 300f)

/** How dark the page behind a dialog gets once the dialog is fully up. */
internal const val DialogScrimAlpha = 0.5f

/** A dialog that has just been dismissed: a recorded picture of its card, still to be animated away. */
internal class DialogGhost(
    val id: Long,
    val layer: GraphicsLayer,
    /** Where the enter animation had got to; the exit carries on from there rather than jumping. */
    val startProgress: Float,
    val release: () -> Unit,
)

/**
 * Finishes dialogs' exits. A platform dialog window vanishes the instant its caller stops composing
 * it, which leaves nothing to animate -- so [GlassAlertDialog] records its card into a graphics layer
 * as it draws and, when it leaves composition, hands that recording here. [DialogExitOverlay], mounted
 * once above the whole app, plays it dropping off the bottom. The recording is a picture, not the
 * dialog's content lambdas, so nothing re-runs and nothing can have gone stale.
 */
@Stable
internal class DialogExitHost {
    val ghosts = mutableStateListOf<DialogGhost>()
    private var nextId = 0L

    fun add(layer: GraphicsLayer, startProgress: Float, release: () -> Unit) {
        ghosts += DialogGhost(nextId++, layer, startProgress, release)
    }
}

internal val LocalDialogExitHost = staticCompositionLocalOf<DialogExitHost?> { null }

/** Plays every pending dialog exit. Put it last in the app's root so it draws over everything. */
@Composable
internal fun DialogExitOverlay(host: DialogExitHost) {
    host.ghosts.forEach { ghost ->
        key(ghost.id) {
            DialogExitGhost(ghost) {
                ghost.release()
                host.ghosts.remove(ghost)
            }
        }
    }
}

@Composable
private fun DialogExitGhost(ghost: DialogGhost, onDone: () -> Unit) {
    val progress = remember { Animatable(ghost.startProgress) }
    LaunchedEffect(ghost.id) {
        // A small anticipatory lift, then a quick accelerating drop off the bottom of the screen.
        progress.animateTo(minOf(ghost.startProgress, 1f) + 0.05f, tween(90))
        progress.animateTo(0f, tween(300, easing = FastOutLinearInEasing))
        onDone()
    }
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            // Swallow touches for the few hundred milliseconds it takes to leave, so a second tap
            // cannot land on whatever the dialog was covering.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black, alpha = DialogScrimAlpha * progress.value.coerceIn(0f, 1f))
            },
        )
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            val width = with(density) { ghost.layer.size.width.toDp() }
            val height = with(density) { ghost.layer.size.height.toDp() }
            Box(
                Modifier
                    .size(width, height)
                    .graphicsLayer { translationY = (1f - progress.value) * screenHeightPx }
                    .drawBehind { drawLayer(ghost.layer) },
            )
        }
    }
}
