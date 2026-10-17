package com.bloo.bluelink.wear

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One of the car's actions on the watch face: one quarter of [WearCarFace]'s circle.
 *
 * The label is not lost when it is not drawn -- it becomes the button's spoken description, and it is
 * what asks the app's own vocabulary for the glyph when [icon] is null.
 */
data class WearQuickAction(
    val label: String,
    /** Null asks the app's own vocabulary for the glyph this label uses (see [standardButtonIcon]). */
    val icon: ImageVector? = null,
    val active: Boolean = false,
    /** The command is in flight: the glyph becomes the app's shared spinner and taps are ignored. */
    val pending: Boolean = false,
    val onClick: () -> Unit,
)
