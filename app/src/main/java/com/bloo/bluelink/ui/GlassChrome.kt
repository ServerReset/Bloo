package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.bloo.bluelink.data.GlassStyle

/**
 * User's chosen floating-chrome material.
 *
 * A previous version of this file wired in a real refraction shader
 * (io.github.kyant0:backdrop) via a GraphicsLayer captured at the app root,
 * shared by every floating icon/search bar. That was pulled after it made
 * the app crash immediately after biometric unlock -- the exact fault
 * wasn't confirmed (no crash log was available), but the timing pointed
 * squarely at that native graphics-layer capture running through the
 * biometric-lock blur/unblur transition, and "the app doesn't open" trumps
 * a visual nicety. Both styles are now the same plain, safe semi-transparent
 * fill; callers use [glassContainerAlpha] on their own solid tint. Revisit
 * real refraction later on a narrower, lower-stakes surface (not the app
 * root) with a way to actually capture a crash log first.
 */
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.LIQUID }

/**
 * No-op placeholder kept so existing call sites (FloatingIcon, the Settings
 * title pill, the search bar) don't need touching. See the class doc above
 * for why this doesn't draw a real blur/shader right now.
 */
@Composable
fun GlassBackdrop(shape: Shape, modifier: Modifier = Modifier) {
}

/** Convenience placeholder for the common circular floating-icon case. */
@Composable
fun GlassBackdropCircle(modifier: Modifier = Modifier) {
}

/**
 * The alpha floating chrome's own solid tint should use. Both styles are
 * currently the same plain semi-transparent fill (see class doc) -- kept as
 * two named values so re-introducing a real shader effect later only means
 * changing this function and [GlassBackdrop], not every call site.
 */
@Composable
fun glassContainerAlpha(liquid: Float = 0.62f, frosted: Float = 0.62f): Float {
    return if (LocalGlassStyle.current == GlassStyle.LIQUID) liquid else frosted
}
