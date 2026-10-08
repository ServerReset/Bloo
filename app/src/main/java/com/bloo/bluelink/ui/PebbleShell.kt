package com.bloo.bluelink.ui

/**
 * The collapsible pebble shell family: [Pebble], [PebbleShell], [PebbleHeaderAction] and
 * [SplitExpandButton].
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import com.bloo.bluelink.data.settingsMode

/**
 * A collapsible "pebble" - a titled section that springs open/closed with a playful bounce.
 * Open/closed state lives in the ViewModel (per car + section), and the section order is
 * user-configurable in Settings.
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
    /**
     * For a pebble whose body is a single setting: in simple mode, render that control on the title
     * row (via `titleTrailing`) and skip the body/disclosure. Null leaves normal expand/collapse.
     */
    inlineSettingInSimpleMode: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forceExpanded = LocalForceExpanded.current
    val simpleMode = state.settingsMode != "advanced"
    val inlineSimple = inlineSettingInSimpleMode != null && simpleMode
    // Body only ever opens via the user's own stored toggle unless simple mode is inlining the one
    // setting on the title row (which has nothing left to disclose).
    val expanded = forceExpanded || (!inlineSimple && state.isPebbleExpanded(v.vin, section))
    val canToggle = !inlineSimple
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
     * Trailing content on the TITLE row -- a headline stat that would otherwise need a third row of
     * its own. Null for every other pebble. A composable slot rather than a string: the hero puts a
     * styled, derived readout here ([ChargeStatsLine]), not a caption.
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
     * Whether the TITLE grows when this pebble expands. False for every pebble but the hero, and
     * that is the point. Also the only one where the cost is justified: the growth lerps a real
     * font size, so every frame misses the SINGLE-SLOT ParagraphLayoutCache and re-lays the text
     * out.
     */
    growTitleOnExpand: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    forceExpanded: Boolean = false,
    /** If false, the chevron is hidden and onToggle is not called. */
    canToggle: Boolean = true,
    /**
     * Drawn BEHIND the header and the collapsing body, inside the card's clip. A pebble is
     * otherwise a plain vertical stack with no z-order, so nothing could sit under the header. The
     * hero needs that: its photo runs up behind the header row so the title and the chevron overlay
     * the top of the image.
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
    // Off by default -- see Appearance.pebbleOutline's doc comment. Most floating chrome always has
    // a rim, but pebbles are the majority of on-screen surface area, so a rim on every single one
    // is a much bigger visual commitment than one more floating button.
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
            // No shadow elevation: the whole card is scaled by ReorderColumn's drag lift and the
            // cold-start intro (a graphicsLayer on the pebble's own Box above), and the default 1dp
            // shadow is a spot shadow baked into that layer -- so it re-rasterized on every frame
            // of the lift, which is exactly the drag/float chug.
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
                            // AnimatedVisibility only animates the whole block appearing and
                            // disappearing; content that changes WHILE expanded (an install step
                            // arriving, notes loading) still jumped the card's height. This
                            // animates those in place too.
                            modifier = Modifier.animateContentSize(
                                lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            ).padding(start = GapSection, end = GapSection, bottom = GapSection, top = GapHairline),
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
