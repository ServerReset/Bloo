package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.standardButtonIcon

/**
 * One of the car's actions on the watch face: one quarter of the face.
 *
 * The label is shown under the glyph and is the button's spoken description; with no [icon] the glyph comes from the app's own vocabulary
 * for the label (see [standardButtonIcon]).
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

/**
 * Up to four actions as the four quarters of the face: flat tiles that meet at the middle, the circle clipping their outer corners. Each
 * shows its glyph and its name, and a name wraps onto another line rather than being cut short. A missing action leaves a quiet quarter, so
 * the circle stays whole. Order: top-left, top-right, bottom-left, bottom-right.
 */
@Composable
internal fun WearQuadrantGrid(actions: List<WearQuickAction?>, modifier: Modifier = Modifier) {
    val slots = (0 until 4).map { actions.getOrNull(it) }
    val unit = LocalFaceUnit.current
    val gap = TileGap * unit
    Layout(
        content = { for (q in 0 until 4) QuarterTile(q, slots[q], Modifier.fillMaxSize()) },
        modifier = modifier.clip(CircleShape),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val g = gap.roundToPx()
        val leftW = ((w - g) / 2).coerceAtLeast(1)
        val rightW = (w - g - leftW).coerceAtLeast(1)
        val topH = ((h - g) / 2).coerceAtLeast(1)
        val bottomH = (h - g - topH).coerceAtLeast(1)
        val placeables = measurables.mapIndexed { q, m ->
            m.measure(Constraints.fixed(if (q % 2 == 0) leftW else rightW, if (q < 2) topH else bottomH))
        }
        layout(w, h) {
            placeables[0].place(0, 0)
            placeables[1].place(w - rightW, 0)
            placeables[2].place(0, h - bottomH)
            placeables[3].place(w - rightW, h - bottomH)
        }
    }
}

/** One quarter of the face. [quadrant]: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
@Composable
private fun QuarterTile(quadrant: Int, action: WearQuickAction?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val unit = LocalFaceUnit.current
    val shape = quarterShape(quadrant, innerCorner = 20.dp * unit)
    if (action == null) {
        Box(modifier.clip(shape).background(scheme.surfaceContainer))
        return
    }
    val haptics = LocalHapticFeedback.current
    val fill = if (action.active) scheme.primary else scheme.surfaceContainerHigh
    val ink = if (action.active) scheme.onPrimary else scheme.onSurface
    val glyph = action.icon ?: standardButtonIcon(action.label)
    Column(
        modifier
            .clip(shape)
            .background(fill)
            .clickable(enabled = !action.pending, role = Role.Button, onClickLabel = action.label) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action.onClick()
            }
            .semantics { contentDescription = action.label }
            .padding(8.dp * unit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            glyph?.let { MorphButtonLabel(icon = it, label = "", pending = action.pending, iconSize = 26.dp * unit) }
        }
        RevealText(
            action.label,
            style = TextStyle(fontSize = (12f * unit).sp),
            fontWeight = FontWeight.SemiBold,
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A quarter's own shape: a plain rectangle whose only rounded corner is the inner one, at the hub. The OUTER edge is not
 * the tile's business -- the grid is clipped to a circle -- which is what keeps it on the display's curve while the
 * tile grows and shrinks under a press.
 */
private fun quarterShape(quadrant: Int, innerCorner: Dp): RoundedCornerShape {
    val inner = CornerSize(innerCorner)
    val none = CornerSize(0.dp)
    return when (quadrant) {
        0 -> RoundedCornerShape(topStart = none, topEnd = none, bottomEnd = inner, bottomStart = none)
        1 -> RoundedCornerShape(topStart = none, topEnd = none, bottomEnd = none, bottomStart = inner)
        2 -> RoundedCornerShape(topStart = none, topEnd = inner, bottomEnd = none, bottomStart = none)
        else -> RoundedCornerShape(topStart = inner, topEnd = none, bottomEnd = none, bottomStart = none)
    }
}
