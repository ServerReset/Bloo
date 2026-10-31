package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.roundToInt

/** The corner radius of every quarter tile, in the face's own units (see [LocalFaceUnit]). */
private val TileCorner = 10.dp

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
 * Up to four actions as the four quarters of the face: rounded tiles that meet at the middle, the circle clipping their outer corners. Each
 * shows its glyph and its name, and a name wraps onto another line rather than being cut short. Pressing one makes it grow and gives the
 * others room (see [QuadrantPress]). A missing action leaves a quiet quarter, so the circle stays whole. Order: top-left, top-right,
 * bottom-left, bottom-right.
 */
@Composable
internal fun WearQuadrantGrid(
    actions: List<WearQuickAction?>,
    modifier: Modifier = Modifier,
    press: QuadrantPress? = null,
    /** Room the bottom quarters keep clear of their labels (the curved name runs there), in face units. */
    bottomInset: Dp = 0.dp,
) {
    val slots = (0 until 4).map { actions.getOrNull(it) }
    val unit = LocalFaceUnit.current
    val gap = TileGap * unit
    Layout(
        content = {
            for (q in 0 until 4) QuarterTile(q, slots[q], Modifier.fillMaxSize(), press, if (q >= 2) bottomInset else 0.dp)
        },
        modifier = modifier.clip(CircleShape),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val g = gap.roundToPx()
        // The cross: the middle, moved by the press. Each quarter spans from the edge to half a gap short of the cross.
        val cx = (w / 2f + w * (press?.dx ?: 0f)).roundToInt()
        val cy = (h / 2f + h * (press?.dy ?: 0f)).roundToInt()
        val leftW = (cx - g / 2).coerceAtLeast(1)
        val rightW = (w - cx - g / 2).coerceAtLeast(1)
        val topH = (cy - g / 2).coerceAtLeast(1)
        val bottomH = (h - cy - g / 2).coerceAtLeast(1)
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
private fun QuarterTile(quadrant: Int, action: WearQuickAction?, modifier: Modifier, press: QuadrantPress?, bottomInset: Dp) {
    val scheme = MaterialTheme.colorScheme
    val unit = LocalFaceUnit.current
    val shape = RoundedCornerShape(TileCorner * unit)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // A press is shown by the tile growing and the others giving way, so the press is reported to the grid as it goes down and up.
    LaunchedEffect(pressed) { press?.press(quadrant, pressed) }
    DisposableEffect(Unit) { onDispose { press?.press(quadrant, false) } }
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
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = !action.pending,
                role = Role.Button,
                onClickLabel = action.label,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action.onClick()
            }
            .semantics { contentDescription = action.label }
            // The label stays well inside the round edge: the quarter's outer corner is cut by the circle.
            .padding(start = 14.dp * unit, end = 14.dp * unit, top = 10.dp * unit, bottom = (10.dp + bottomInset) * unit),
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
