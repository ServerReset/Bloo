package com.bloo.uicommon

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The shared button-geometry helpers, pulled out of the app so the pill
 * math — the corner percentages, the split-pill seams, the connected-group
 * silhouettes — lives in ONE place and every phone control builds
 * from byte-identical shapes.
 */

/** The standard text-field corner, shared by every input in both apps. */
val FieldShape: RoundedCornerShape = RoundedCornerShape(18.dp)

/** The shared inner (seam) corner: a soft nub at rest that opens toward the morphed radius as
 *  the segment is pressed. Stated in Dp, so it is the same physical corner at any row height. */
fun seamCorner(morph: Float): CornerSize {
    val idle = 10.dp
    val morphed = 16.dp
    return CornerSize(idle + (morphed - idle) * morph)
}
