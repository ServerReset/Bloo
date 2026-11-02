package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/*
 * The watch's backdrop blur. The pages behind a floating element are the Haze source ([WearCarPage] marks them), and the floating elements
 * (the name, the toast, the pull disc) blur what is under them. Haze falls back to a flat look below Android 12, where it cannot blur.
 */

/** How soft the watch's floating glass is: a small kernel, since the round display is small. */
private val WearBlurRadius = 10.dp

/** Blurs whatever [backdrop] has drawn under this element. */
internal fun Modifier.wearBlur(backdrop: HazeState): Modifier = this.hazeBlur(
    input = HazeInput.Sources(backdrop),
    style = HazeBlurStyle { blurRadius(WearBlurRadius) },
    performanceMode = HazePerformanceMode.Balanced,
)

/**
 * The car's name over every page of the car on screen: a blurred band along the bottom of the ring, with the name curved on it. One element,
 * floating above the pages, so it reads the same wherever the wearer is.
 */
@Composable
internal fun WearNameOverlay(name: String, color: Color, backdrop: HazeState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = minOf(maxWidth, maxHeight) / ReferenceFace
        CompositionLocalProvider(LocalFaceUnit provides unit) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(bottomBandShape(unit))
                    .wearBlur(backdrop)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)),
            )
            CurvedName(name, color, Modifier.fillMaxSize())
        }
    }
}

/**
 * The bottom half of the ring between the display's edge and the name's inner edge, in face units: the band the name is curved on. The
 * name sits on its baseline 2.5 units in from the edge and rises inward about 14 units, so the band covers that.
 */
private fun bottomBandShape(unit: Float): GenericShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) / 2f
    val outer = r - 1f * unit
    val inner = r - 14f * unit
    moveTo(cx + outer, cy)
    arcTo(Rect(cx - outer, cy - outer, cx + outer, cy + outer), 0f, 180f, false)
    lineTo(cx - inner, cy)
    arcTo(Rect(cx - inner, cy - inner, cx + inner, cy + inner), 180f, -180f, false)
    close()
}
