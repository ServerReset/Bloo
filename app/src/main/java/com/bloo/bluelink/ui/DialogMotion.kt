package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** How dark the page behind a dialog gets once the dialog is fully up. */
internal const val DialogScrimAlpha = 0.42f

/**
 * One open dialog. Its content is handed up by [GlassAlertDialog] on every recomposition, so it
 * stays live.
 */
@Stable
internal class DialogEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
    var onDismiss by mutableStateOf({})

    /** True once the caller has stopped showing it; the layer plays the exit, then drops it. */
    var leaving by mutableStateOf(false)
}

/**
 * Where every dialog in the app is drawn: one layer inside the app's own window, above everything,
 * rather than a separate platform window per dialog.
 */
@Stable
internal class DialogHost(val hazeState: HazeState) {
    val entries = mutableStateListOf<DialogEntry>()
}

internal val LocalDialogHost = staticCompositionLocalOf<DialogHost?> { null }

/** Draws every open dialog, oldest first. Put it last in the app's root so it covers everything. */
@Composable
internal fun DialogLayer(host: DialogHost) {
    host.entries.forEachIndexed { index, entry ->
        key(entry) {
            DialogEntryView(host, entry, isTop = index == host.entries.lastIndex)
        }
    }
}

@Composable
private fun DialogEntryView(host: DialogHost, entry: DialogEntry, isTop: Boolean) {
    val progress = rememberPop(entry.leaving) { host.entries.remove(entry) }
    BackHandler(enabled = isTop && !entry.leaving) { entry.onDismiss() }

    val scheme = MaterialTheme.colorScheme
    val screenHeightPx = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().toPx() }
    Box(
        Modifier
            .fillMaxSize()
            // A leaving dialog swallows touches, so a second tap cannot land on what it covers.
            .then(
                if (entry.leaving) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // The page behind, dimmed in step with the card; tapping it dismisses.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = DialogScrimAlpha * progress.value.coerceIn(0f, 1f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!entry.leaving) entry.onDismiss()
                },
        )
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(
                shape = ExtraLargeShape,
                hazeState = host.hazeState,
                // Frosted rather than clear: enough of the theme's surface over the blur that the
                // text reads whatever is behind. Denser where the blur is unavailable.
                tint = scheme.surfaceContainerHigh.copy(alpha = if (canBlurBackdrops()) 0.58f else 0.97f),
                modifier = Modifier
                    .padding(horizontal = GapBlock)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Rises from below the screen.
                    .popGraphics({ progress.value }, fromY = { screenHeightPx })
                    // Swallows taps on the card so they do not fall through to the scrim.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                entry.content()
            }
        }
    }
}
