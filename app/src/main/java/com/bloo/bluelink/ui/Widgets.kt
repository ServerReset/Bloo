package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.SegmentOption
import dev.chrisbanes.haze.HazeState
import com.bloo.uicommon.ButtonTargetHeight
import com.bloo.uicommon.ButtonLabelStyle

/**
 * True while the Activity is in multi-window/split-screen/freeform mode. Set from
 * [com.bloo.bluelink.MainActivity].
 */
internal var inMultiWindowMode by mutableStateOf(false)

/**
 * The strip's own tint, over the backdrop. Heavier than a glass surface's own tint, because this is a
 * thin band the system icons have to read against, and the backdrop under it (the aurora's own top,
 * which is its faintest part) is dark: at the shared glass weight the band simply read as black.
 */
private const val StatusStripTintAlpha = 0.18f

/**
 * Whether the status strip may be drawn.
 *
 * It exists to sit on the app's own window edge and stops meaning anything when the app does not own
 * that edge: in multi-window, freeform or split screen the window is a card inside someone else's
 * layout and its top is not the device's, and in immersive full screen there is no status bar inset
 * for it to occupy.
 */
@Composable
private fun statusStripAllowed(): Boolean =
    !inMultiWindowMode && WindowInsets.statusBars.asPaddingValues().calculateTopPadding() > 0.dp

/**
 * The status bar: clear, with the app running behind it, frosted -- nearly fully at the top edge, still a
 * little at the bottom of the strip -- so the system icons always sit on a calm pane while the cards and the
 * aurora show through. It blurs [haze], which carries the content AND the backdrop (see Screens), so what
 * scrolls under the strip is what frosts. A faint tint follows the same taper. Skipped whenever the
 * app does not own the top of its window -- see [statusStripAllowed].
 */
@Composable
internal fun StatusBarScrim(haze: HazeState? = LocalBackdropHaze.current) {
    if (!statusStripAllowed()) return
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val dark = appIsDarkTheme()
    val tint = if (dark) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val topAlpha = if (dark) StatusStripTintAlpha else StatusStripTintAlpha * 2f
    Box(Modifier.fillMaxWidth().height(topInset).clipToBounds()) {
        if (haze != null && canBlurBackdrops()) {
            Box(Modifier.matchParentSize().appHazeEffect(haze, progressive = true, progressiveEnd = StatusStripBottomFrost))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(tint.copy(alpha = topAlpha), tint.copy(alpha = topAlpha * 0.3f))),
            ),
        )
    }
}

/** How much of the status bar's frost is left at its bottom edge (1 = as frosted as the top). */
private const val StatusStripBottomFrost = 0.10f

/**
 * Settings mode (Simple/Advanced) toggle hanging flush from the status bar as one piece of chrome.
 */
@Composable
internal fun SettingsModeTab(
    settingsMode: String,
    onSettingsModeChange: (String) -> Unit,
    hazeState: HazeState? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme

    // Sits below the status bar like the other floating header chrome, so it doesn't blur into the
    // system icons.
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = HeaderCornerGap, end = HeaderCornerGap),
        contentAlignment = Alignment.TopEnd,
    ) {
        GlassSurface(
            shape = StandardShape,
            modifier = Modifier.width(172.dp),
            hazeState = hazeState,
        ) {
            com.bloo.uicommon.MorphSegmented(
                options = listOf(
                    SegmentOption("simple", "Simple", null),
                    SegmentOption("advanced", "Advanced", null),
                ),
                selectedKey = settingsMode,
                onSelect = onSettingsModeChange,
                containerColor = Color.Transparent,
                indicatorColor = scheme.primary,
                selectedTextColor = scheme.onPrimary,
                unselectedTextColor = scheme.onSurfaceVariant,
                textStyle = ButtonLabelStyle,
                onTick = { haptics?.tick() },
                trackHeight = HeaderButtonSize,
                borderColor = null,
            )
        }
    }
}

/** The shared gap below the status bar that free-floating header elements align to. */
internal val HeaderCornerGap = 12.dp

/**
 * The one shared size every free-floating header BUTTON -- [FloatingIcon]'s circle, and anything
 * meant to sit in the same row as one -- is drawn at. The app's one button height.
 */
internal val HeaderButtonSize = ButtonTargetHeight

/**
 * A small translucent circular icon button used as a floating overlay control, with
 * [HeaderCornerGap] of breathing room around the [HeaderButtonSize] circle.
 */
@Composable
internal fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Overrides for surfaces over a dark scrim (the lock overlay's back arrow uses plain white, not
    // the glass fill).
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    /**
     * The screen's [HazeState] for a real backdrop blur; null keeps a plain glass tint with no
     * blur.
     */
    hazeState: HazeState? = null,
    /**
     * Swaps [icon] for a spinning [LoadingIndicator] and ignores taps while the action is in
     * flight.
     */
    busy: Boolean = false,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "floatIconScale",
    )
    // GlassSurface supplies the shared fill/blur/rim/shadow.
    GlassSurface(
        shape = CircleShape,
        modifier = modifier
            .padding(HeaderCornerGap)
            .size(HeaderButtonSize)
            // Lambda form: the press spring is read at DRAW time, so the animation never recomposes
            // this button (the arg-taking overload reads it in composition instead -- see
            // ExpressiveButtons.kt for the same fix).
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .ambientRing(CircleShape),
        hazeState = hazeState,
        tint = containerColor ?: glassTint(hazeState != null && canBlurBackdrops()),
        contentColor = contentColor,
        contentDescription = description,
        interactionSource = interaction,
        onClick = { if (!busy) { haptics?.click(); onClick() } },
    ) {
        if (busy) {
            LoadingIndicator(Modifier.size(22.dp))
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}

/**
 * The current [SettingsStore.Appearance], provided once at the app root so pebbles read it instead
 * of each collecting the flow. The default degrades gracefully outside the provider.
 */
internal val LocalAppearance = staticCompositionLocalOf { SettingsStore.Appearance() }

/**
 * When true (pinned pebbles and full-screen/car-glance contexts), pebbles render permanently open
 * with no collapse chevron or drag handle.
 */
internal val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * The live pull-to-refresh distance (0..1+), published by [Refreshable] so the floating overlays in
 * [GarageScreen] (settings/back buttons) can track the pull in real time instead of only
 * animating once refresh starts.
 */
internal val LocalPullFraction =

    staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float>> { mutableFloatStateOf(0f) }
