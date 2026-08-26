@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Rect

/**
 * Specifies which screen context the floating name pill is rendered in.
 * Each context has different positioning, styling, and interaction requirements.
 *
 * This enum drives automatic parameter resolution in FloatingNamePill, ensuring
 * consistent behavior across different surfaces while maintaining context-specific
 * customization.
 */
enum class FloatingNameContext {
    /**
     * Hero car name on garage/car detail screens.
     * - Photo-based text color (dynamic)
     * - Reserves space for Settings toggle (right side)
     * - Scroll-to-top on click
     * - Used for collision detection with page dots
     */
    HERO_CAR,

    /**
     * Settings screen name pill (standalone).
     * - Static onSurface text color
     * - Reserves space for back/action buttons (right side)
     * - Scroll grid to top on click
     * - No collision detection needed
     */
    SETTINGS,

    /**
     * Settings page within a pager (embedded context).
     * - Static onSurface text color
     * - Reserves space for action buttons (right side)
     * - Scroll grid to top on click
     * - Similar to SETTINGS but with different layout context
     */
    SETTINGS_EMBEDDED,
}

/**
 * Configuration values resolved from [FloatingNameContext].
 * Encapsulates all parameter mappings so they're in one place
 * and easy to update consistently.
 */
data class FloatingNameConfig(
    val cornerX: Dp,
    val reserveEnd: Dp,
    val usePhotoColor: Boolean,
    val label: String? = null,
)

/**
 * Resolve configuration based on context.
 * This extension function centralizes all parameter mapping logic,
 * making it easy to see what changes between contexts at a glance.
 */
val FloatingNameContext.config: FloatingNameConfig
    get() = when (this) {
        FloatingNameContext.HERO_CAR -> FloatingNameConfig(
            cornerX = 16.dp,
            reserveEnd = 72.dp,
            usePhotoColor = true,
            label = null
        )
        FloatingNameContext.SETTINGS -> FloatingNameConfig(
            cornerX = 60.dp,
            reserveEnd = 192.dp,
            usePhotoColor = false,
            label = null
        )
        FloatingNameContext.SETTINGS_EMBEDDED -> FloatingNameConfig(
            cornerX = 16.dp,
            reserveEnd = 192.dp,
            usePhotoColor = false,
            label = null
        )
    }

/**
 * Unified floating name pill composable that adapts to its context.
 *
 * This composable consolidates the hero car name pill (garage/detail screens)
 * and Settings screen name pill into a single implementation that knows its
 * context and adapts all parameters (positioning, colors, actions) automatically.
 *
 * **Benefits:**
 * - Single source of truth for floating name behavior
 * - Consistent animation specs across all contexts
 * - Self-documenting through context enum
 * - Easy to add new contexts in the future
 *
 * **Usage:**
 * ```kotlin
 * // Simple text-only version
 * FloatingNamePill(
 *     context = FloatingNameContext.SETTINGS,
 *     flight = settingsFlight,
 *     screenWidth = screenWidth,
 *     topInset = topInset,
 *     onScrollToTop = { scroll.invoke() }
 * ) {
 *     Text("Settings", style = MaterialTheme.typography.headlineSmall)
 * }
 * ```
 *
 * @param context Which screen/surface this pill is rendered in (auto-resolves parameters)
 * @param flight The TitleFlightSource backing the animation (life cycle state, position tracking)
 * @param screenWidth Available screen width for calculating maxWidth
 * @param topInset Status bar inset for cornerY calculation
 * @param onNameBoundsChanged Optional callback for bounds changes (used by hero car for collision detection)
 * @param onDockedChanged Optional callback when docking state settles (used by Settings for hand-off)
 * @param onScrollToTop Optional action when pill is clicked (can be null for read-only contexts)
 * @param extraContent Optional extra content shown alongside the name (e.g., page label)
 * @param content The flying text content (typically a Text, but can be AnimatedContent or other composable)
 */
@Composable
internal fun BoxScope.FloatingNamePill(
    context: FloatingNameContext,
    flight: TitleFlightSource,
    screenWidth: Dp,
    topInset: Dp,
    onNameBoundsChanged: ((Rect?) -> Unit)? = null,
    onSettledChanged: ((Boolean) -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
    extraContent: (@Composable RowScope.() -> Unit)? = null,
    measureContent: (@Composable () -> Unit)? = null,
    containerRelative: Boolean = false,
    content: @Composable () -> Unit,
) {
    val config = context.config
    val cornerY = topInset + HeaderCornerGap
    val maxWidth = screenWidth - config.cornerX - config.reserveEnd - 32.dp

    // Resolve text color based on context
    val textColor = resolveTextColor(context, flight)

    TitleFlightOverlay(
        flight = flight,
        cornerX = config.cornerX,
        cornerY = cornerY,
        reserveEnd = config.reserveEnd,
        maxWidth = maxWidth,
        textColorOverride = textColor,
        onClick = { onScrollToTop?.invoke() },
        extraContent = extraContent,
        measureContent = measureContent,
        onNameBoundsChanged = onNameBoundsChanged,
        containerRelative = containerRelative,
        onSettledChanged = onSettledChanged,
        content = content
    )
}

/**
 * Resolve the text color based on context and TitleFlightSource.
 *
 * For HERO_CAR: reads dynamic color from the flight's photo color
 * For SETTINGS/SETTINGS_EMBEDDED: uses static onSurface color
 */
@Composable
private fun resolveTextColor(
    context: FloatingNameContext,
    flight: TitleFlightSource,
): Color? = when (context) {
    // Hero car reads dynamic color from photo (null = read from flight internally)
    FloatingNameContext.HERO_CAR -> null

    // Settings contexts use static onSurface color
    FloatingNameContext.SETTINGS, FloatingNameContext.SETTINGS_EMBEDDED ->
        MaterialTheme.colorScheme.onSurface
}
