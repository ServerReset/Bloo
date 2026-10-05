package com.bloo.bluelink.ui

/**
 * The collapsible pebble shell family: [Pebble], [PebbleShell], [PebbleHeaderAction] and
 * [SplitExpandButton].
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import com.bloo.bluelink.data.settingsMode

/**
 * A collapsible titled section that springs open/closed. Open state lives in the ViewModel (per car
 * + section).
 */
@Composable
internal fun Pebble(
    v: Vehicle,
    section: String,
    title: String,
    icon: ImageVector,
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    /** Drawn behind the header and body, clipped to the pebble's shape. */
    background: (@Composable BoxScope.() -> Unit)? = null,
    /** If true and in simple mode, the pebble is always expanded and cannot be collapsed. */
    alwaysExpandedInSimpleMode: Boolean = false,
    /**
     * For a pebble whose body is a single setting: in simple mode, render that control on the title
     * row (via `titleTrailing`) and skip the body/disclosure. Null leaves normal expand/collapse.
     */
    inlineSettingInSimpleMode: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forceExpanded = LocalForceExpanded.current
    val simpleMode = state.settingsMode != "advanced"
    val forceAlwaysExpanded = alwaysExpandedInSimpleMode && simpleMode
    val inlineSimple = inlineSettingInSimpleMode != null && simpleMode
    // The stored toggle only applies when neither special mode is active.
    val expanded = forceExpanded || forceAlwaysExpanded ||
        (!inlineSimple && state.isPebbleExpanded(v.vin, section))
    val canToggle = !forceAlwaysExpanded && !inlineSimple
    PebbleShell(
        expanded = expanded,
        onToggle = if (canToggle) { { vm.togglePebble(v, section) } } else { {} },
        icon = icon,
        title = title,
        modifier = modifier,
        summary = summary,
        containerColor = containerColor,
        headerAction = headerAction,
        forceExpanded = forceExpanded,
        canToggle = canToggle,
        titleTrailing = if (inlineSimple) inlineSettingInSimpleMode else null,
        titleTrailingAtEnd = inlineSimple,
        background = background,
        content = content,
    )
}

/**
 * The expand/collapse pebble shell; takes [expanded]/[onToggle] directly so non-section cards (the
 * update tile) can reuse it.
 */
@Composable
internal fun PebbleShell(
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    /**
     * Trailing content on the title row (e.g. the hero's [ChargeStatsLine]). Null for other
     * pebbles. It owns its leading gap so an absent stat costs nothing.
     */
    titleTrailing: (@Composable () -> Unit)? = null,
    /**
     * Pushes [titleTrailing] to the far end of the title row (for inline controls) instead of
     * beside the name.
     */
    titleTrailingAtEnd: Boolean = false,
    /**
     * Overrides the colour of [title] and the leading [icon] together so they never mismatch.
     * [Color.Unspecified] inherits; the hero sets it to stay legible over its photo.
     */
    titleColor: Color = Color.Unspecified,
    /**
     * Extra header content under the title and [summary] (the hero's collapsed charge readout).
     * Renders inside the header's text column. Null for other pebbles.
     */
    headerContent: (@Composable () -> Unit)? = null,
    /**
     * Whether the title grows on expand. Hero only: the growth lerps a real font size, so every
     * frame misses the ParagraphLayoutCache and re-lays the text out.
     */
    growTitleOnExpand: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    forceExpanded: Boolean = false,
    /** If false, the chevron is hidden and onToggle is not called. */
    canToggle: Boolean = true,
    /**
     * Drawn behind the header and collapsing body, inside the card's clip (the hero's photo). Must
     * handle its own text legibility (scrim). Null for other pebbles.
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Drawn on top of the header and body, in the same coordinate space as [background] (the hero's
     * travelling charge numbers cross the header buttons). Null for other pebbles.
     */
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Vertical gap between [content]'s top-level rows; pass 0.dp if the content spaces itself with
     * [Spacer]s.
     */
    contentGap: Dp = GapRow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    // Collapsed = pill-soft corners; expanded = tighter rounded square. Direction picks the same
    // springs collapseEnter/collapseExit use for height so corners and height read as one card.
    var headerRowHeightPx by remember { mutableIntStateOf(0) }
    // Row width from onSizeChanged (not BoxWithConstraints: a SubcomposeLayout's deferred pass is
    // costly per pebble header). Default 1000.dp, not 0: it caps SplitExpandButton, so a low
    // default would force compact on the first frame.
    var headerRowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    val collapsedCorner = if (headerRowHeightPx > 0) {
        with(density) { (headerRowHeightPx / 2f).toDp() }
    } else {
        PebbleCornerCollapsed
    }
    // True while the chevron is held; the whole card squares off with it, targeting
    // PebbleCornerExpanded (the shape expanding lands on) so hold and expand agree.
    var chevronPressed by remember { mutableStateOf(false) }
    val corner by animateDpAsState(
        targetValue = when {
            chevronPressed || expanded -> PebbleCornerExpanded
            else -> collapsedCorner
        },
        // The press morph reuses the open spring: a hold is an immediate response, not a settle.
        animationSpec = if (expanded || chevronPressed) {
            lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            lowPowerAwareSpring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
        },
        label = "pebbleCorner",
    )
    val pebbleShape = RoundedCornerShape(corner)
    // Off by default; see Appearance.pebbleOutline.
    val pebbleAppearance = LocalAppearance.current
    val pebbleOutline = pebbleAppearance.pebbleOutline
    Box(Modifier.fillMaxWidth()) {
        Card(
            Modifier
                .fillMaxWidth()
                .pebbleCardEdge(pebbleShape, pebbleOutline)
                // The card's bounding box is glass; its own panels stay solid.
                .glassCardFill(pebbleShape, containerColor),
            shape = pebbleShape,
            // No shadow elevation: the card is scaled by the drag lift/intro layer, and a default
            // spot shadow would re-rasterize every frame. The border carries the depth.
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                // Slightly translucent so the aurora reads faintly through; 0.9 keeps text
                // contrast.
                containerColor = Color.Transparent,
                contentColor = contentColorFor(containerColor),
            ),
        ) {
            // Box, so `background` can draw behind the header and body.
            Box(Modifier.fillMaxWidth()) {
                background?.invoke(this)
                // No animateContentSize: the body's AnimatedVisibility already animates the height,
                // and a second spring compounds and rubber-bands.
                Column {
                    PebbleHeaderRow(
                        icon = icon,
                        title = title,
                        summary = summary,
                        expanded = expanded,
                        forceExpanded = forceExpanded,
                        canToggle = canToggle,
                        onToggle = onToggle,
                        modifier = modifier,
                        titleColor = titleColor,
                        growTitleOnExpand = growTitleOnExpand,
                        titleTrailing = titleTrailing,
                        titleTrailingAtEnd = titleTrailingAtEnd,
                        headerContent = headerContent,
                        headerAction = headerAction,
                        rowWidthDp = headerRowWidthDp,
                        onMeasured = { h, w -> headerRowHeightPx = h; headerRowWidthDp = w },
                        onChevronPressChange = { chevronPressed = it },
                    )
                    // Animate the body sliding open/closed; the exit fades the block so the
                    // shrinking bottom edge doesn't visibly clip the content.
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandEnterSized(),
                        exit = expandExitSized(fade = true),
                    ) {
                        // StaggeredRevealColumn cascades rows; `transition` is this
                        // AnimatedVisibilityScope's, so the card cannot finish closing before its
                        // rows do.
                        StaggeredRevealColumn(
                            transition = transition,
                            // Animates height changes while expanded (install steps, loading
                            // notes).
                            modifier = Modifier.animateContentSize(
                                lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                            verticalGap = contentGap,
                            content = content,
                        )
                    }
                }
                // Drawn last, on top of the header and body (see foreground).
                foreground?.invoke(this)
            }
        }
    }
}
