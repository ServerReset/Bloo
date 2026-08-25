package com.bloo.bluelink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.bloo.uicommon.frostedRim as sharedFrostedRim
import com.bloo.uicommon.ambientRing as sharedAmbientRing

/*
 * The phone app's floating-chrome helpers are now ONE shared kit in
 * :uicommon (see com.bloo.uicommon.GlassChrome), usable by the watch and
 * widget surfaces too. This file keeps the app's original call sites
 * unchanged by re-supplying the theme tint they used to read internally:
 * the platform Material is the one thing the shared module deliberately
 * does NOT depend on, so the wrapper passes onSurface in.
 *
 * - glassContainerAlpha: identical to the shared one (no color needed).
 * - frostedRim / ambientRing: thin alias wrappers.
 */

/** See [com.bloo.uicommon.glassContainerAlpha] -- identical value. */
fun glassContainerAlpha(frosted: Float = 0.68f): Float =
    com.bloo.uicommon.glassContainerAlpha(frosted)

/**
 * The phone's default rim: the shared [com.bloo.uicommon.frostedRim] with
 * this platform's onSurface (the watch reads its own; a widget reads its
 * own). The tint passed here is what makes the rim follow the theme's
 * light/dark state.
 */
@Composable
fun Modifier.frostedRim(shape: Shape): Modifier =
    this.sharedFrostedRim(shape, MaterialTheme.colorScheme.onSurface)

/** See [com.bloo.uicommon.ambientRing]. */
fun Modifier.ambientRing(shape: Shape): Modifier =
    this.sharedAmbientRing(shape)