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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.settingsMode
import com.bloo.uicommon.lowPowerSoft
import com.bloo.uicommon.lowPowerBounce
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.lowPowerAwareSpring
import com.bloo.uicommon.content

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
    /** Whether the card draws its rim and shadow. The hero turns it off for a floating photo. */
    edge: Boolean = true,
    /** Whether the card draws its glass backdrop. A cut-out car photo turns it off, so the page shows through the photo. */
    glass: Boolean = true,
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
    largeHeader: Boolean = false,
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
    com.bloo.bluelink.perf.TrackRecompositions("PebbleShell")
    val haptics = LocalHaptics.current
    // Frames while a pebble opens or closes are filed under it ("Garage/pebble-Trips"), not under the screen alone; not the first composition.
    var seenOnce by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (seenOnce) com.bloo.bluelink.perf.PerfMonitor.scope("pebble-$title", 900)
        seenOnce = true
    }
    // Collapsed = pill-soft corners; expanded = tighter rounded square. Direction picks the same
    // springs collapseEnter/collapseExit use for height so corners and height read as one card.
    var headerRowHeightPx by remember { mutableIntStateOf(0) }
    // Row width from onSizeChanged (not BoxWithConstraints: a SubcomposeLayout's deferred pass is
    // costly per pebble header). Default 1000.dp, not 0: it caps SplitExpandButton, so a low
    // default would force compact on the first frame.
    var headerRowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    // Collapsed, the card is a true pill: half the HEADER ROW's height, which is the collapsed card's
    // whole height (at rest the body is an AnimatedVisibility with nothing in it).
    //
    // This was briefly half the CARD's own measured height instead. That is right at rest and wrong
    // for the entire length of a close: a closing card starts at its EXPANDED height, so the target
    // became half of that, the corners jumped to a huge radius the moment the close began, and they
    // then chased the card down as it shrank -- the broken close animation.
    val collapsedCorner = if (headerRowHeightPx > 0) {
        with(density) { (headerRowHeightPx / 2f).toDp() }
    } else {
        PebbleCornerCollapsed
    }
    // True while the chevron is held; the whole card squares off with it, targeting
    // PebbleCornerExpanded (the shape expanding lands on) so hold and expand agree.
    var chevronPressed by remember { mutableStateOf(false) }
    val cornerTarget = when {
        chevronPressed || expanded -> PebbleCornerExpanded
        else -> collapsedCorner
    }
    val cornerSpec = if (expanded || chevronPressed) {
        lowPowerBounce<Dp>(stiffness = Spring.StiffnessLow)
    } else {
        lowPowerAwareSpring<Dp>(dampingRatio = PebbleCloseDamping, stiffness = Spring.StiffnessLow)
    }
    // Animated only when the USER changed something (open, close, hold). A target that moves because the card
    // was measured -- every pebble as it first fills in after a swipe -- snaps: springing it rebuilt the whole
    // card on every frame for nothing, right when the page was busiest.
    val cornerAnim = remember { androidx.compose.animation.core.Animatable(cornerTarget, Dp.VectorConverter) }
    val mode = expanded || chevronPressed
    val lastMode = remember { booleanArrayOf(mode) }
    LaunchedEffect(cornerTarget, mode) {
        if (mode == lastMode[0] && !cornerAnim.isRunning) {
            cornerAnim.snapTo(cornerTarget)
        } else {
            lastMode[0] = mode
            cornerAnim.animateTo(cornerTarget, cornerSpec)
        }
    }
    // Read when drawn, so the open/close spring reshapes the card without recomposing it every frame.
    val pebbleShape = remember { AnimatedCornerShape(cornerAnim.asState()) }
    Box(Modifier.fillMaxWidth()) {
        Card(
            Modifier.fillMaxWidth()
                // The card's bounding box is glass (GlassCardBackdrop, its first child below, so the blur
                // can be skipped while the pager moves); its own panels stay solid.
                .then(if (edge) Modifier.pebbleCardEdge(pebbleShape) else Modifier),
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
                if (glass) GlassCardBackdrop(pebbleShape, containerColor)
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
                        largeHeader = largeHeader,
                    )
                    // Animate the body sliding open/closed; the exit fades the block so the
                    // shrinking bottom edge doesn't visibly clip the content.
                    // The body only GROWS (nothing slides, nothing is cut at a line): each row pops in as the
                    // growing card reaches it, a beat behind the opening (StaggeredRevealColumn runs on the
                    // same spring as this growth).
                    AnimatedVisibility(
                        visible = expanded,
                        // Grow DOWNWARD from under the header (expandFrom = Top). The default
                        // (Bottom) bottom-anchored the body, so as the card grew the body slid down
                        // out from behind the header; top-anchoring lets each row just pop in place.
                        enter = expandVertically(
                            animationSpec = pebbleOpenSizeSpec(),
                            expandFrom = Alignment.Top,
                            clip = false,
                        ),
                        // No whole-body fade on the way out: closing is a per-row cascade too (see
                        // StaggeredRevealColumn), and a body fade would wash the cascade out.
                        // The same spring as the open, so the close mirrors it and the rows share the one clock on the way out.
                        exit = shrinkVertically(
                            animationSpec = pebbleOpenSizeSpec(),
                            shrinkTowards = Alignment.Top,
                            clip = false,
                        ),
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
                                lowPowerSoft(Spring.StiffnessMediumLow),
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

/**
 * A rounded rectangle whose corner is read when the outline is drawn, not when it is composed, so an animated
 * corner reshapes the clip without recomposing the card that wears it.
 */
internal class AnimatedCornerShape(private val corner: State<Dp>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        RoundedCornerShape(corner.value).createOutline(size, layoutDirection, density)
}
