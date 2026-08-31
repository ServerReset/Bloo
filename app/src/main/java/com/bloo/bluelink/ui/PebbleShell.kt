@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * The collapsible "pebble" shell family, peeled out of Pebbles.kt (which keeps
 * the per-section pebble composites and list plumbing). This file owns the
 * generic [Pebble] wrapper, the [PebbleShell] expand/collapse card, its
 * [PebbleHeaderAction] action model, and the split [SplitExpandButton] control
 * (action + chevron nub). Same package, so Pebbles.kt's call sites stay
 * internal-visible and verbatim.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A collapsible "pebble" - a titled section that springs open/closed with a
 * playful bounce. Open/closed state lives in the ViewModel (per car + section),
 * and the section order is user-configurable in Settings.
 */
@Composable
internal fun Pebble(
    v: Vehicle,
    section: String,
    title: String,
    icon: ImageVector,
    state: UiState,
    vm: AppViewModel,
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    /** Drawn BEHIND the header and body, clipped to the pebble's own shape.
     *  [PebbleShell] has always had this -- the hero's car photo uses it -- but
     *  [Pebble] did not forward it, so a per-car pebble could only ever have a flat
     *  fill. Forwarded now, which is what lets the AI pebble carry a gradient
     *  without either of them growing a special case for it. */
    background: (@Composable BoxScope.() -> Unit)? = null,
    /** If true and in simple mode, the pebble is always expanded and cannot be collapsed.
     *  Use for pebbles with a single setting that benefit from inline display without expand/collapse. */
    alwaysExpandedInSimpleMode: Boolean = false,
    /**
     * For a pebble whose entire body IS a single setting: in simple mode, render that setting's
     * control directly on the title row (via [PebbleShell]'s `titleTrailing` slot) instead of
     * behind an expand/collapse control, and skip the body/disclosure entirely -- there is
     * nothing left to disclose once the one thing it holds is already showing next to the name.
     * Null (the default) leaves the pebble's normal expand/collapse behavior untouched.
     *
     * Distinct from [alwaysExpandedInSimpleMode], which keeps the body (all of `content`)
     * permanently visible instead of replacing it -- that's for a pebble whose body is worth
     * seeing at a glance but isn't literally one control (the AI summary text, say). This is for
     * the narrower case the two are easy to conflate: an actual single control, which doesn't
     * need its own disclosure at all once it's inline.
     */
    inlineSettingInSimpleMode: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forceExpanded = LocalForceExpanded.current
    val simpleMode = state.settingsMode != "advanced"
    val forceAlwaysExpanded = alwaysExpandedInSimpleMode && simpleMode
    val inlineSimple = inlineSettingInSimpleMode != null && simpleMode
    // Body only ever opens via the user's own stored toggle when neither special mode is
    // active -- forceAlwaysExpanded already shows the body unconditionally, and inlineSimple
    // has nothing left to disclose (the one setting it holds is already on the title row).
    val expanded = forceExpanded || forceAlwaysExpanded ||
        (!inlineSimple && state.isPebbleExpanded(v.vin, section))
    val canToggle = !forceAlwaysExpanded && !inlineSimple
    PebbleShell(
        expanded = expanded,
        onToggle = if (canToggle) { { vm.togglePebble(v, section) } } else { {} },
        icon = icon,
        title = title,
        vm = vm,
        dragHandle = dragHandle,
        summary = summary,
        containerColor = containerColor,
        headerAction = headerAction,
        forceExpanded = forceExpanded,
        canToggle = canToggle,
        titleTrailing = if (inlineSimple) inlineSettingInSimpleMode else null,
        background = background,
        content = content,
    )
}

/**
 * The actual expand/collapse pebble shell -- [Pebble] derives [expanded]/
 * [onToggle] from a car+section key (state.isPebbleExpanded/vm.togglePebble);
 * this takes them directly so anything that isn't tied to a specific
 * vehicle/section (the update tile) can still get the exact same collapsible
 * card instead of a hand-rolled lookalike.
 */
@Composable
internal fun PebbleShell(
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    title: String,
    vm: AppViewModel,
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    /**
     * Trailing content on the TITLE row -- a headline stat that would otherwise need a
     * third row of its own. Null for every other pebble.
     *
     * A composable slot rather than a string: the hero puts a styled, derived readout
     * here ([ChargeStatsLine]), not a caption. It owns its own leading gap -- there is no
     * [Spacer] before it here -- so a pebble with no trailing stat doesn't pay for one, and
     * the expanded hero's title isn't squeezed by a gap left behind an absent node.
     */
    titleTrailing: (@Composable () -> Unit)? = null,
    /**
     * Overrides the colour of [title] and [summary]. [Color.Unspecified] (the default)
     * inherits, which is what every pebble but the hero wants.
     *
     * The hero needs it because its `background` slot puts a PHOTO behind the header,
     * and the header is drawn over that with the surface's own content colour -- so an
     * expanded card rendered the car's name in near-black on a dark photo and it could
     * not be read. The photo already carries a scrim built for light text; nothing was
     * telling the text to be light. Reported from a real device.
     */
    titleColor: Color = Color.Unspecified,
    /**
     * Extra modifier appended to the title [Text] itself, AFTER its own scale
     * transform -- so a caller reading its position (e.g. via
     * `onGloballyPositioned`) gets the real, final on-screen bounds, not the
     * pre-scale layout size. Only the hero ever supplies one (see
     * [LocalHeroTitleFlight]); every other pebble takes the default no-op.
     */
    titleModifier: Modifier = Modifier,
    // onTitleWidth was deleted here. It reported the title's measured width so the hero could
    // offset its collapsed readout past the car name. That whole approach is gone: the numbers are
    // now trailing content ON this Row (see HeroCollapsedNumbers), so the Row positions them and
    // nothing needs to know how wide the name is.
    /**
     * Extra content in the header, under the title and [summary].
     *
     * A string is all `summary` can be, and the hero wants a graphical readout there when
     * collapsed: a mini charge bar plus its percentage. This is that slot and nothing more.
     * It renders inside the header's own text column, so it inherits the header's width,
     * padding and content colour, and sits above the chevron's row sibling rather than
     * competing with it for horizontal space.
     *
     * Null for every other pebble.
     */
    headerContent: (@Composable () -> Unit)? = null,
    /**
     * Whether the TITLE grows when this pebble expands.
     *
     * False for every pebble but the hero, and that is the point. The growth used to be
     * unconditional, so "Location", "Weather", "Diagnostics" and the rest all swelled from
     * titleMedium to headlineSmall on expand. On the hero it reads as the car's name taking
     * over the card it now fills; on a utility pebble it is just a heading changing size for
     * no reason, four of them doing it at once, and it fights the body content appearing
     * underneath.
     *
     * Also the only one where the cost is justified: the growth lerps a real font size, so
     * every frame misses the SINGLE-SLOT ParagraphLayoutCache and re-lays the text out. One
     * node doing that on one card is affordable; making it the default charged every pebble
     * for an effect only one of them wanted.
     */
    growTitleOnExpand: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    forceExpanded: Boolean = false,
    /** If false, the expand/collapse chevron is hidden and onToggle is not called. */
    canToggle: Boolean = true,
    /**
     * Drawn BEHIND the header and the collapsing body, inside the card's clip.
     *
     * A pebble is otherwise a plain vertical stack with no z-order, so nothing could sit
     * under the header. The hero needs that: its photo runs up behind the header row so
     * the title and the chevron overlay the top of the image.
     *
     * Whatever goes here is responsible for its own legibility. Header text lands on top
     * of it, and over an arbitrary car photo that text disappears -- the widget hit the
     * same thing and resolved it with a luminance check. A scrim under the text is the
     * cheap version and is what the hero does.
     *
     * Null for every other pebble, so nothing else gains a layer.
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    // Collapsed = pill-soft corners; expanded morphs to a tighter rounded square. Direction
    // picks between the SAME two springs collapseEnter/collapseExit use for the height, rather
    // than one flat spec for both directions -- that used to run regardless of direction, so
    // the corners settled on their own schedule while the height was doing something else
    // (bouncing open, or -- when closing briefly bounced too -- overshooting shut in a way
    // that read as disconnected from the collapse itself). Matching each direction's spring
    // exactly is what keeps the corners and the height reading as one card in both directions,
    // even though open bounces and close (deliberately, now) doesn't -- see collapseExit's own
    // doc for why closing settled on a calm spring instead.
    //
    // PebbleCornerCollapsed (38dp = ControlHeight/2) is only a FALLBACK, for the one frame
    // before the header row below has ever reported its own real height. It used to be the
    // only number in play, which made "fully rounded" a coincidence: true stadium ends need
    // corner = height/2 of the ACTUAL row, and the row only ever measures exactly
    // ControlHeight when nothing pushes it taller (headerContent's extra line, a wrapped
    // title, a bigger in-row action button) -- any of those left visibly flatter corners
    // than the pill-shaped buttons riding inside the same row, which is what was reported.
    // headerRowHeightPx (below) is that row's real measured height every time it changes;
    // corner now targets ITS half, so the card is a true capsule at whatever height this
    // pebble's own content actually needs, not just the one height it was tuned against.
    var headerRowHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val collapsedCorner = if (headerRowHeightPx > 0) {
        with(density) { (headerRowHeightPx / 2f).toDp() }
    } else {
        PebbleCornerCollapsed
    }
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else collapsedCorner,
        animationSpec = if (expanded) {
            spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            spring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
        },
        label = "pebbleCorner",
    )
    val fillHeight = LocalPebbleFillHeight.current
    // On the cover screen a pebble IS a cover tile -- same template as the
    // home tile and every other page (title band, centred body, actions
    // band). It used to be this same Card with the header row dropped and a
    // 30dp icon badge floating over the body's corner, which meant a pebble
    // page looked like a different kind of object from the home page and
    // named itself only to someone who already knew the iconography.
    // headerAction becomes the actions band, so the pebble's one control
    // lands in the same place, at the same size, as the home tile's four.
    if (fillHeight && expanded) {
        val act = headerAction?.takeIf { it.label.isNotEmpty() }
        CoverTile(
            title = title,
            icon = icon,
            subtitle = summary,
            containerColor = containerColor,
            scrollState = LocalCoverScrollState.current,
            actions = if (act == null) {
                null
            } else {
                {
                    CoverActionButton(
                        icon = act.icon,
                        label = act.label,
                        onClick = act.onClick,
                        active = act.active,
                        pending = act.pending,
                        enabled = act.enabled,
                    )
                }
            },
            body = content,
        )
        return
    }
    val pebbleShape = RoundedCornerShape(corner)
    // Off by default -- see Appearance.pebbleOutline's doc comment. Most
    // floating chrome always has a rim, but pebbles are the majority of
    // on-screen surface area, so a rim on every single one is a much bigger
    // visual commitment than one more floating button.
    val pebbleAppearance = LocalAppearance.current
    val pebbleOutline = pebbleAppearance.pebbleOutline
    Box(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)) {
        Card(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .dropShadow(pebbleShape, blurRadius = 12.dp, offsetY = 4.dp)
                // frostedRim's alpha (0.10-0.24) is tuned for chrome floating
                // over an unpredictable car photo, where it only has to beat
                // that photo's contrast -- against a flat dark pebble
                // background it was nearly imperceptible, reading as "this
                // setting does nothing" even though it was working. A
                // dedicated, considerably bolder border here instead, so
                // toggling this is actually visible.
                .then(
                    if (pebbleOutline) {
                        Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), pebbleShape)
                    } else Modifier,
                ),
            shape = pebbleShape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColorFor(containerColor),
            ),
        ) {
            // Box, so `background` can draw BEHIND the header and body. A pebble is
            // otherwise a plain vertical stack with no z-order, which is why an image
            // could not sit under the header before this.
            Box(Modifier.fillMaxWidth()) {
                background?.invoke(this)
                // No animateContentSize here (cover-screen tiles fill instead) --
                // the body below is already wrapped in its own AnimatedVisibility
                // with expandVertically/shrinkVertically, which smoothly animates
                // that exact same height delta on its own. Wrapping this Column in
                // a SECOND, independently-sprung animateContentSize on top of that
                // made every collapse/expand visibly lag and rubber-band: each
                // frame of the inner animation is itself a "content size changed"
                // event the outer animateContentSize then re-animates towards,
                // compounding two springs where the collapse only needs one.
                Column(
                    if (fillHeight) Modifier.fillMaxHeight() else Modifier,
                ) {
                    // Phone only. The cover screen never reaches here: PebbleShell
                    // returns above, through CoverTile, so a pebble on the cover is
                    // the same template as every other page there. What follows is
                    // the collapsible header + animated body card.
                    // Header: tap anywhere to toggle, long-press to drag-reorder. The
                    // action button and chevron handle their own clicks. Fixed min height
                    // so every collapsed pebble lines up.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Feeds collapsedCorner above: this row's height IS the
                            // whole card's collapsed height (the body is hidden then),
                            // and it's stable across the expand/collapse animation
                            // itself (only the body grows/shrinks below it), so this
                            // never fires mid-bounce with a transient wrong value.
                            .onSizeChanged { headerRowHeightPx = it.height }
                            .then(
                                if (forceExpanded || !canToggle) Modifier
                                else Modifier.clickable {
                                    if (expanded) haptics?.tick() else haptics?.click()
                                    onToggle()
                                },
                            )
                            .then(dragHandle)
                            .heightIn(min = PebbleHeaderHeight)
                            // Asymmetric padding: 16dp left, 12dp right (was 16dp),
                            // pushing buttons slightly right while keeping symmetry.
                            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            // Same heading fix as SettingsCard: with 8+ pebbles per
                            // car and no heading structure, TalkBack users could
                            // only reach a given section (Climate, Charge, ...) by
                            // swiping through every row of every pebble above it.
                            // The header grows and hardens as the pebble opens. Expanded, the
                            // hero's header sits over a photo, so bigger and higher-contrast
                            // is legibility rather than flourish -- and it makes opening feel
                            // like the card is coming forward instead of just getting taller.
                            //
                            // Interpolated on the theme's SPATIAL spec (type size is a spatial
                            // property) so it moves with the same physics as the expansion it
                            // belongs to, and lerped through real type steps rather than being
                            // scaled, so every frame is a genuine font size.
                            // A slow, lightly-bouncy spring, NOT the theme's default spatial
                            // spec. That default is tuned for a card's whole bounds, and driving
                            // a TYPE STEP with it read as rough: it is quick enough that a
                            // 16sp -> 24sp change lands in a handful of frames, and each of
                            // those frames is a genuine re-layout at a new font size, so what
                            // you see is a few discrete jumps rather than a glide.
                            //
                            // dampingRatio 0.62 gives a real overshoot -- the name grows a
                            // touch past its target and settles back -- and StiffnessVeryLow
                            // stretches it over enough frames for the intermediate sizes to
                            // read as motion instead of steps. Both halves matter: bounce with
                            // a fast spring is still steppy, and a slow spring without bounce
                            // is just a slower version of the same flat move.
                            // Only animates for the pebble that asked (the hero). For the
                            // rest the target is a constant 0, so the spring never leaves its
                            // resting value, titleStyle stays titleMedium, and the per-frame
                            // font-size relayout never happens at all.
                            val headerT by animateFloatAsState(
                                targetValue = if (expanded && growTitleOnExpand) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = Spring.StiffnessVeryLow,
                                ),
                                label = "pebbleHeaderGrow",
                            )
                            // Drawn at the LARGER size always and SCALED down, rather than
                            // lerping the font size. The lerp was the choppiness: a Text
                            // measures through ParagraphLayoutCache, which is single-slot, so a
                            // font size that changes every frame misses it every frame -- 100%
                            // invalidation, a full text relayout per frame, and the visible
                            // result is a few discrete steps rather than a glide. Scaling a
                            // layout measured ONCE is what Compose itself recommends for
                            // animated type, and it is draw-phase only.
                            //
                            // headlineSmall is the base and it scales DOWN, never up: text
                            // scaled down stays crisp, upscaling is what goes soft.
                            //
                            // transformOrigin pins the LEFT edge so the name grows out of its
                            // own start position instead of drifting sideways from the centre.
                            val titleStyle = MaterialTheme.typography.headlineSmall
                            // Ratio of the two real type steps, so the collapsed size still
                            // equals titleMedium exactly rather than a hand-picked number.
                            val collapsedTitleScale = with(LocalDensity.current) {
                                MaterialTheme.typography.titleMedium.fontSize.toPx() /
                                    MaterialTheme.typography.headlineSmall.fontSize.toPx()
                            }
                            // Plain arithmetic, not lerp(): this file imports the Color, TextStyle
                            // and Dp overloads of `lerp` but NOT the Float one from
                            // androidx.compose.ui.util, so a Float call does not resolve -- which
                            // is exactly how the first attempt at this broke the build. Spelling
                            // out the interpolation removes the dependency on which overload
                            // happens to be in scope.
                            val titleScale = if (!growTitleOnExpand) {
                                collapsedTitleScale
                            } else {
                                collapsedTitleScale + (1f - collapsedTitleScale) * headerT
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    // Reports the DRAWN size. graphicsLayer scales the drawing and
                                    // leaves the measured size alone, so without this the title's
                                    // box stayed headline-TALL while its glyphs were title-sized --
                                    // which made the row taller than the text in it and pushed
                                    // everything beside the name out of line.
                                    //
                                    // Measure once at headlineSmall, then report width and height
                                    // multiplied by the same scale the layer draws with, and place
                                    // the (still full-size) content centred on that smaller box so
                                    // scaling about its left-centre keeps the glyphs where the box
                                    // says they are. This is what lets `titleTrailing` sit against
                                    // the name's real edge rather than a headline-sized box.
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val w = (placeable.width * titleScale).roundToInt()
                                        val h = (placeable.height * titleScale).roundToInt()
                                        layout(w, h) {
                                            placeable.place(0, (h - placeable.height) / 2)
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = titleScale
                                        scaleY = titleScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    // Appended LAST -- after the .layout{} above, so a
                                    // caller reading this via onGloballyPositioned gets
                                    // the real, final (already-scaled) on-screen bounds.
                                    .then(titleModifier),
                                style = titleStyle,
                                color = titleColor,
                                fontWeight = FontWeight.Bold,
                                // Cap at one line: at a large display/font size the
                                // header action button (SplitExpandButton, now width-
                                // bounded below) used to squeeze this weighted Column
                                // so a title like "Location"/"Weather"/"Diagnostics"
                                // wrapped and visually collided with the button. One
                                // line + ellipsis keeps the title on its own line.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Trailing content on the TITLE row, so a pebble that wants a
                            // headline stat does not need a third row for it. The hero puts
                            // its percentage and range here, which is what lets the collapsed
                            // card be name-and-numbers over a bar instead of three stacked
                            // lines with the bar stranded at the bottom.
                            //
                            // No Spacer before it any more, and no styling applied here:
                            // the slot owns both. The hero shows this only while collapsed,
                            // and a 10dp gap left behind when it goes would squeeze the
                            // expanded title for a node that is no longer in the row.
                            titleTrailing?.invoke()
                            }
                            if (summary != null) {
                                AnimatedContent(
                                    targetState = summary,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                                        (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                                    },
                                    label = "pebbleSummary",
                                ) { s ->
                                    Text(
                                        s,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                                        maxLines = 1,
                                        // Ellipsize a long summary ("Set a location")
                                        // instead of hard-clipping it to "Set a…".
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            headerContent?.invoke()
                        }
                        if (!forceExpanded) {
                            if (headerAction != null) {
                                // Renders the action half regardless; canToggle decides whether
                                // the chevron half comes with it (and reshapes the action's
                                // seam corner when it does not -- see SplitExpandButton).
                                SplitExpandButton(
                                    action = headerAction,
                                    expanded = expanded,
                                    onToggle = onToggle,
                                    canToggle = canToggle,
                                )
                            } else if (canToggle) {
                                // Gated on canToggle, which this branch used to ignore. Without
                                // the gate a pebble with nothing to disclose still drew a
                                // chevron and, since onToggle is a no-op in that state, tapping
                                // it did nothing -- the exact "there should be no chevron" case
                                // that inlineSettingInSimpleMode and SettingsCard's inlineSetting
                                // exist to produce. Only pebbles carrying a headerAction ever
                                // honoured canToggle, purely because that path happened to
                                // forward it.
                                MorphExpandButton(
                                    expanded = expanded,
                                    onToggle = onToggle,
                                )
                            }
                        }
                    }
                    // Normal pebbles: animate the body sliding open/closed. fade = false on
                    // the exit -- StaggeredRevealColumn's rows own their own fade now (see
                    // collapseExit's own doc for why running a SECOND, block-level fade at the
                    // same time buried that per-row one and made closing look like it had no
                    // content animation at all).
                    AnimatedVisibility(
                        visible = expanded,
                        enter = collapseEnter(),
                        exit = collapseExit(fade = false),
                    ) {
                        // StaggeredRevealColumn, not a plain Column: every row pops in/out on
                        // its own as this cascades open/closed, instead of every row appearing
                        // together the instant the block-level AnimatedVisibility above reveals
                        // it. See that composable's own doc for why this is the ONE place that
                        // needed changing to give every pebble's rows this for free.
                        //
                        // `transition` here is `AnimatedVisibilityScope.transition` -- this
                        // lambda's implicit receiver, since it's the content of the
                        // AnimatedVisibility right above. Passing THAT (not a boolean) is what
                        // lets the row cascade register itself as part of the same Transition
                        // driving this card's own height/fade, so the card can't finish
                        // closing before the rows do -- see StaggeredRevealColumn's own doc.
                        StaggeredRevealColumn(
                            transition = transition,
                            // AnimatedVisibility only animates the whole block
                            // appearing and disappearing; content that changes
                            // WHILE expanded (an install step arriving, notes
                            // loading) still jumped the card's height. This
                            // animates those in place too.
                            modifier = Modifier.animateContentSize(
                                spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                            verticalGap = 8.dp,
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

internal class PebbleHeaderAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val pending: Boolean = false,
    val active: Boolean = false,
    val spinning: Boolean = false,
    val bounceIcon: Boolean = false,
    val activeContainer: Color? = null,
    val activeContent: Color? = null,
    val isWarning: Boolean = false,
    /** Explicit TalkBack label for icon-only actions (empty [label]) -- without
     *  it, an empty-label button inside a Surface (which doesn't merge
     *  descendant semantics) announces only "Button" with no indication of
     *  what it does. Only needed when [label] is blank. */
    val contentDescription: String? = null,
)

/**
 * Right-side expand control for pebbles that also have an action button.
 * Left half: the action (label + icon); right half: chevron nub. Together
 * they form a connected split pill, identical in style to [PresetPill].
 */
@Composable
internal fun SplitExpandButton(
    action: PebbleHeaderAction,
    expanded: Boolean,
    onToggle: () -> Unit,
    canToggle: Boolean = true,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitChevron",
    )

    // Easter egg: HOLD the chevron (long-press) to trigger a one-shot spin
    // animation with a vibration. A long press does NOT toggle the pebble --
    // only a plain tap does. After the spin completes the chevron returns to
    // normal operation and can be held again.
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )

    // The row's own real, measured height. The halves' corners are expressed
    // as a PERCENT of the short side (the shared MorphButton model -- exact
    // pills by construction, no fixed-dp radius that could exceed an edge),
    // and the chevron's morphed corner is "10dp" in that language, so the
    // percent is derived from the measured height: 10dp / rowHeight.
    var rowHeightDp by remember { mutableStateOf(52.dp) }
    val density = LocalDensity.current
    val morphedPercent = 100f * 10.dp.value / rowHeightDp.value
    val inner = 6.dp
    // Each half gets its own shape: the OUTER corner morphs (pill when idle,
    // 10dp rounded square when that half's own state says morphed), the INNER
    // corner stays a small fixed seam nub -- meant to seam against the chevron
    // half immediately to its right. Both halves are the same MorphButton
    // component; each one's active/pressed state drives only ITS morph.
    //
    // That seam nub is wrong when canToggle is false: the chevron half is never
    // rendered then (see the `if (canToggle)` below), so the action button sits
    // alone with nothing to seam against -- a small fixed corner on a side with
    // no neighbor just reads as a broken/half-finished pill (reported from a
    // real screenshot: the Summarize action, whose pebble is permanently
    // expanded in simple mode and so never shows a chevron). Full pill on both
    // sides in that case instead.
    val leftShapeForCorner: (Float, Int) -> Shape = { _, cp ->
        val end = if (canToggle) CornerSize(inner) else CornerSize(percent = cp)
        RoundedCornerShape(
            topStart = CornerSize(percent = cp), bottomStart = CornerSize(percent = cp),
            topEnd = end, bottomEnd = end,
        )
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { _, cp ->
        RoundedCornerShape(
            topStart = CornerSize(inner), bottomStart = CornerSize(inner),
            topEnd = CornerSize(percent = cp), bottomEnd = CornerSize(percent = cp),
        )
    }

    val defaultContainer = buttonContainer()
    val leftContainer = if (action.isWarning) MaterialTheme.colorScheme.errorContainer else defaultContainer
    val leftFg = when {
        action.isWarning -> MaterialTheme.colorScheme.onErrorContainer
        action.active -> (action.activeContent ?: MaterialTheme.colorScheme.onPrimary)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Bounce animation for the location button's icon.
    val bounceY = remember { Animatable(0f) }
    val bounceScope = rememberCoroutineScope()
    var bouncing by remember { mutableStateOf(false) }

    // Spinning animation for the climate button's icon.
    val spinAngle = remember { Animatable(0f) }
    LaunchedEffect(action.spinning) {
        if (action.spinning) {
            spinAngle.animateTo(
                targetValue = spinAngle.value + 360f,
                animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
            )
            while (true) {
                spinAngle.animateTo(
                    targetValue = spinAngle.value + 360f,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                )
            }
        } else if (spinAngle.value != 0f) {
            val target = kotlin.math.ceil(spinAngle.value / 360f) * 360f
            spinAngle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
            spinAngle.snapTo(0f)
        }
    }

    // ExpressiveButtonGroup, not a plain Row: these two halves are the app's clearest case of
    // buttons that should physically shove each other on press (they are a single connected
    // pill), and the group is what makes that safe -- it redistributes width BETWEEN the halves
    // so its own outer size never changes, which matters because this header also renders inside
    // Settings' LazyVerticalStaggeredGrid items, where a size change during scroll crashes the
    // grid. See ExpressiveButtons.kt for the full why.
    ExpressiveButtonGroup(
        modifier = Modifier
            // A fixed 52dp target (the old content-driven ~40dp pill read as
            // undersized next to the 76dp header it sits in -- reported from
            // a real screenshot). IntrinsicSize.Min still reconciles the two
            // halves to the SAME height; heightIn supplies the floor.
            .height(IntrinsicSize.Min)
            .heightIn(min = rowHeightDp)
            // Real measured height, so the 10dp corner percent above lands on
            // the right radius -- see that val's own doc.
            .onSizeChanged { rowHeightDp = with(density) { it.height.toDp() } },
        spacing = 3.dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left half — the action (label + icon) button with expansion animation.
        val actionSource = remember { MutableInteractionSource() }
        GroupButton(
            interactionSource = actionSource,
            enabled = action.enabled && !action.pending,
        ) {
            MorphButton(
                onClick = {
                    if (action.bounceIcon) bounceScope.launch {
                        bouncing = true
                        bounceY.animateTo(-9f, spring(stiffness = Spring.StiffnessHigh))
                        bounceY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        bouncing = false
                    }
                    action.onClick()
                },
                enabled = action.enabled && !action.pending,
                active = action.active,
                interactionSource = actionSource,
                containerColor = leftContainer,
                contentColor = leftFg,
                activeContainerColor = action.activeContainer ?: MaterialTheme.colorScheme.primary,
                activeContentColor = action.activeContent ?: MaterialTheme.colorScheme.onPrimary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                shapeForCorner = leftShapeForCorner,
                morphedCornerPercent = morphedPercent,
                pillCornerPercent = 50f,
                // The halves keep their measured ~42-46dp height; the standard
                // 48dp touch floor would inflate the whole pebble header.
                minHeight = 0.dp,
                modifier = Modifier.fillMaxHeight().then(
                    run {
                        // Local copy so the smart cast works inside the semantics
                        // lambda (class properties are not stable for smart-cast
                        // capture; the !! form read fine but would crash if the
                        // guard and the call ever drifted apart).
                        val desc = action.contentDescription
                        if (action.label.isEmpty() && desc != null) {
                            Modifier.semantics { contentDescription = desc }
                        } else Modifier
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.graphicsLayer { translationY = bounceY.value },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (action.pending && !bouncing) {
                        LoadingIndicator(Modifier.size(16.dp))
                    } else {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            // graphicsLayer lambda, not rotate(): rotate() reads the
                            // Animatable in composition, and the spin runs for as long
                            // as climate is on - recomposing this button every frame
                            // indefinitely. The lambda defers the read to the draw phase.
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = spinAngle.value },
                        )
                    }
                    if (action.label.isNotEmpty()) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            // Cap the label width so a long action ("Summarize",
                            // "Downloading…") at a large font size can't grow this button
                            // unbounded and squeeze the pebble title into wrapping/overlap.
                            // The label ellipsizes past the cap; the icon still identifies it.
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp),
                        )
                    }
                }
            }
        }
        // Right half — chevron nub with expansion animation.
        // Hidden if canToggle is false (single-setting pebbles in simple mode).
        if (canToggle) {
            val chevronSource = remember { MutableInteractionSource() }
            GroupButton(
                interactionSource = chevronSource,
                enabled = true,
            ) {
                MorphButton(
                    onClick = { onToggle() },
                    onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
                    onLongClick = {
                        // Easter egg: hold the chevron to spin it + vibrate.
                        if (!easterEggTriggered) {
                            easterEggTriggered = true
                            haptics?.heavy()
                        }
                    },
                    active = expanded,
                    interactionSource = chevronSource,
                    contentPadding = PaddingValues(start = 13.dp, end = 12.dp),
                    shapeForCorner = rightShapeForCorner,
                    morphedCornerPercent = morphedPercent,
                    pillCornerPercent = 50f,
                    minHeight = 0.dp,
                    // The icon's own contentDescription below is the NEXT action
                    // ("Expand"/"Collapse"); this is the CURRENT state -- without it
                    // TalkBack only ever hears what tapping will do, never whether the
                    // pebble is presently open, so distinguishing the two took a
                    // double-tap-and-listen-again instead of being announced on focus.
                    // widthIn(min = rowHeightDp) keeps the nub a square at the row's
                    // fixed height so its pill end is a true semicircle by percent.
                    modifier = Modifier.fillMaxHeight().widthIn(min = rowHeightDp)
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        // Larger chevron icon (24dp to match action button icon size), with
                        // easter egg spin animation when the chevron is held.
                        // rotationZ in a graphicsLayer LAMBDA, not Modifier.rotate(): rotate()
                        // takes the angle as an argument, so the spring is read in COMPOSITION
                        // and this Icon recomposes on every frame of every expand/collapse, on
                        // every pebble header in the app. Read in the lambda it is draw-phase.
                        modifier = Modifier.size(24.dp).graphicsLayer {
                            rotationZ = rotation + easterEggSpin
                        },
                    )
                }
            }
        }
    }
}
