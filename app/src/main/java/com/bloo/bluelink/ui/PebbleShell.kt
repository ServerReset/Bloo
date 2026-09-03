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
import androidx.compose.animation.Crossfade
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
import com.bloo.uicommon.seamCorner
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
        titleTrailingAtEnd = inlineSimple,
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
     * Push [titleTrailing] to the FAR END of the title row instead of letting it sit against the
     * name. A headline stat (the hero's percentage) belongs beside the name; an inline CONTROL --
     * the switch a single-setting card shows instead of an expand chevron -- belongs where every
     * other row's control is, hard right, or it reads as jammed into the label.
     */
    titleTrailingAtEnd: Boolean = false,
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
     * pre-scale layout size. Unused by every current caller (the hero used to
     * supply one for the now-removed floating name pill -- see
     * TitleFlight.kt's own doc); kept as a hook rather than deleted, since a
     * caller needing to read the title's own live position is a real, cheap-
     * to-need thing to want again.
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
            // The summary IS this tile's glanceable value, so it goes on the header row at
            // headline size rather than as a muted second line under a title that repeats what
            // the icon already says. See CoverTile.headline.
            headline = summary,
            // Which car this section belongs to. Cover pebbles are header-less, so a section
            // tile ("Charge", "Climate") named the section and nothing else -- which is what the
            // floating car-name overlay was added to fix, by drawing a second title over the
            // one this tile already has. On the title row it costs no height and cannot collide.
            trailingLabel = LocalCoverCarName.current,
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
                        // A section tile has exactly one action and the whole row to put it in,
                        // so it takes the shorter side-by-side pill rather than the stacked form
                        // that exists for fitting four into one row. 12dp back on every tile.
                        compact = true,
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
                        // ButtonIconGap, not a bespoke 10dp: this is the exact same "icon, then
                        // label" pair MorphButtonLabel standardises everywhere else in the app,
                        // and it was the one place still stating that gap by hand.
                        Spacer(Modifier.width(ButtonIconGap))
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
                            // The STATE, not its value. This is a StiffnessVeryLow spring, so it
                            // runs for a second or more, and reading it here put every one of
                            // those frames on the composition path for this whole header -- the
                            // Row, the title, the trailing slot, the split button -- when its
                            // only consumers are the .layout{} and graphicsLayer lambdas below,
                            // which invalidate layout and draw respectively and nothing else.
                            val headerTState = animateFloatAsState(
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
                            // A lambda, so each reader pulls the current value at ITS phase.
                            // growTitleOnExpand is false for every pebble but the hero, and then
                            // this is a constant that never reads the spring at all.
                            val titleScale: () -> Float = if (!growTitleOnExpand) {
                                { collapsedTitleScale }
                            } else {
                                { collapsedTitleScale + (1f - collapsedTitleScale) * headerTState.value }
                            }
                            // True whenever the title is sitting at its collapsed rest size rather
                            // than mid-grow -- which is EVERY pebble but the hero (growTitleOnExpand
                            // is false for the rest, so titleScale() is permanently
                            // collapsedTitleScale), and the hero itself whenever headerTState has
                            // settled BACK DOWN near 0f rather than mid-spring.
                            //
                            // A THRESHOLD, not `== 0f`: a StiffnessVeryLow spring is exactly the
                            // one this session already flagged as running "for a second or more",
                            // and collapsing the hero once and never expanding it again was the
                            // one path that could leave headerTState sitting at some very-nearly-
                            // but-not-bit-for-bit-zero value indefinitely -- which pinned this
                            // Text on the SCALED path forever afterward, permanently reproducing
                            // the very baseline mismatch this whole mechanism exists to avoid.
                            // Confirmed from a real report: expand the hero once, collapse it, and
                            // the name stayed misaligned from then on -- until swiping to another
                            // car and back gave it a fresh composition (headerTState starting
                            // exactly at its target, since there is no previous value to animate
                            // from on first composition) which "corrected itself" for exactly that
                            // reason.
                            //
                            // At rest, render the title in a NATIVE titleMedium Text instead of a
                            // scaled-down headlineSmall one. The scale trick above exists so the
                            // hero's name can grow smoothly through every intermediate size as the
                            // card expands -- a discrete style swap mid-animation would relayout
                            // and jank every frame (see the scale block's own doc). But scaling a
                            // BIGGER style down by its font-size ratio does not necessarily
                            // reproduce a native SMALLER style's own baseline-to-box-centre ratio --
                            // lineHeight is not always a fixed fraction of fontSize across type
                            // steps -- so a scaled headlineSmall sitting beside the hero's own
                            // titleMedium-styled numbers left their glyphs a few px off each
                            // other's baseline no matter how precisely CenterVertically (or a
                            // computed correction) tried to reconcile two DIFFERENT styles' boxes.
                            // Confirmed after two earlier attempts at exactly that, both from real
                            // screenshots.
                            //
                            // At true rest there is no animation to protect, so this renders the
                            // SAME font size through the SAME style object the numbers already use
                            // (HeroNumbers' pctStyle is titleMedium at t=0) -- identical metrics,
                            // so CenterVertically genuinely cannot land them apart. Every other
                            // pebble's title was already reading fine here (nothing beside it needs
                            // a text baseline), and this only swaps which style produces the same
                            // on-screen font size for them too.
                            val atRestScale = !growTitleOnExpand || headerTState.value < 0.001f
                            Row(
                                // Only stretched when the trailing slot is being pushed to the
                                // end -- a row that merely holds a name and a stat must stay
                                // shrink-wrapped, or the stat drifts away from the name.
                                modifier = if (titleTrailingAtEnd) Modifier.fillMaxWidth() else Modifier,
                                verticalAlignment = Alignment.CenterVertically,
                                // SpaceBetween, not a filled/weighted title, pushes titleTrailing to
                                // the row's far end. `weight(1f, fill = true)` did that job before by
                                // forcing this Text's own MEASURE constraints (minWidth == maxWidth ==
                                // the whole remaining row) regardless of how short the title actually
                                // was -- and the scaled-title `.layout{}` below measures against
                                // whatever width it is handed before shrinking it back down, so a
                                // short title ("Updates", "AI") in a wide forced box came out
                                // reporting -- and drawing -- a box far wider than its own glyphs,
                                // with the word adrift inside it rather than hugging the icon.
                                // Confirmed from two separate real screenshots. SpaceBetween reaches
                                // the same "trailing sits hard right" result from the OUTSIDE, off two
                                // children's natural widths, so the title is never measured wider than
                                // its own (possibly ellipsized) content.
                                horizontalArrangement = if (titleTrailingAtEnd) {
                                    Arrangement.SpaceBetween
                                } else {
                                    Arrangement.Start
                                },
                            ) {
                            // The title Text's own modifier chain up to (not including) the scale
                            // machinery -- shared by both branches below.
                            val titleBaseModifier = Modifier
                                // fill = false always now (see the Row's own doc above) -- weight
                                // still caps the title's MAX width to its fair share so a long
                                // title ellipsizes instead of pushing titleTrailing off the row,
                                // it just no longer forces the title to measure wider than its
                                // own content.
                                .weight(1f, fill = false)
                                // The hard minimum gap "Sounds & vibration" needed -- previously a
                                // Spacer sitting between title and titleTrailing as a third row
                                // child, moved onto the title itself so SpaceBetween above still
                                // sees exactly two children and gives the whole remaining width
                                // to one gap rather than splitting it around a spacer.
                                .then(if (titleTrailingAtEnd) Modifier.padding(end = 12.dp) else Modifier)
                            if (growTitleOnExpand) {
                                // Only the hero ever actually flips atRestScale -- every other
                                // pebble's is permanently true, so it takes the plain branch below
                                // with no Crossfade at all. Wrapping this in Crossfade softens the
                                // one moment the scaled-headlineSmall path and the native-titleMedium
                                // path hand off to each other: their box geometry isn't pixel-
                                // identical (see atRestScale's own doc -- scaling a bigger style down
                                // doesn't reproduce a smaller style's own baseline-to-box-centre ratio
                                // exactly), so swapping which one is drawn on a single frame was a
                                // real, reported pop right at the tail of the collapse. A short
                                // alpha cross-dissolve over the swap doesn't need either path to be
                                // pixel-perfect against the other -- it just means the viewer's eye
                                // is never asked to register a same-frame jump.
                                Crossfade(
                                    targetState = atRestScale,
                                    animationSpec = tween(140),
                                    modifier = titleBaseModifier,
                                    label = "heroTitleRestSwap",
                                ) { atRest ->
                                    Text(
                                        title,
                                        modifier = if (atRest) {
                                            Modifier
                                        } else {
                                            Modifier
                                                // Reports the DRAWN size. graphicsLayer scales the
                                                // drawing and leaves the measured size alone, so
                                                // without this the title's box stayed headline-TALL
                                                // while its glyphs were title-sized -- which made the
                                                // row taller than the text in it and pushed
                                                // everything beside the name out of line.
                                                //
                                                // Measure once at headlineSmall, then report width and
                                                // height multiplied by the same scale the layer draws
                                                // with, and place the (still full-size) content
                                                // centred on that smaller box so scaling about its
                                                // left-centre keeps the glyphs where the box says they
                                                // are. This is what lets `titleTrailing` sit against
                                                // the name's real edge rather than a headline-sized
                                                // box.
                                                .layout { measurable, constraints ->
                                                    // Measured against constraints widened by
                                                    // 1/titleScale. The Text is measured at
                                                    // headlineSmall and DRAWN scaled down to
                                                    // titleScale (~0.7), so measuring it against the
                                                    // raw width made it ellipsize on headline-sized
                                                    // glyphs and then shrink the result --
                                                    // "Announcements" became "Announce..." with a
                                                    // third of the row still empty. Widening first
                                                    // means it ellipsizes on the width it is actually
                                                    // drawn at, and the scaled-down report below still
                                                    // lands inside the real constraint.
                                                    val scale = titleScale()
                                                    val room = if (constraints.hasBoundedWidth && scale > 0f) {
                                                        constraints.copy(
                                                            maxWidth = (constraints.maxWidth / scale)
                                                                .roundToInt()
                                                                .coerceAtLeast(constraints.maxWidth),
                                                        )
                                                    } else {
                                                        constraints
                                                    }
                                                    val placeable = measurable.measure(room)
                                                    val w = (placeable.width * scale).roundToInt()
                                                    val h = (placeable.height * scale).roundToInt()
                                                    val yOffset = (h - placeable.height) / 2
                                                    layout(w, h) {
                                                        placeable.place(0, yOffset)
                                                    }
                                                }
                                                .graphicsLayer {
                                                    val s = titleScale()
                                                    scaleX = s
                                                    scaleY = s
                                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                                }
                                        }.then(titleModifier),
                                        style = if (atRest) MaterialTheme.typography.titleMedium else titleStyle,
                                        color = titleColor,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                // The common case: no grow/shrink, no rest-scale swap ever, so no
                                // Crossfade wrapper either -- always native titleMedium.
                                Text(
                                    title,
                                    modifier = titleBaseModifier.then(titleModifier),
                                    style = MaterialTheme.typography.titleMedium,
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
                            }
                            // Trailing content on the TITLE row, so a pebble that wants a
                            // headline stat does not need a third row for it. The hero puts
                            // its percentage and range here, which is what lets the collapsed
                            // card be name-and-numbers over a bar instead of three stacked
                            // lines with the bar stranded at the bottom.
                            //
                            // No Spacer before it any more, and no styling applied here:
                            // the slot owns both. The hero shows this only while collapsed,
                            // and a 10dp gap left behind when it goes would squeeze the
                            // expanded title for a node that is no longer in the row. The hard
                            // minimum gap for titleTrailingAtEnd now lives on the title's own
                            // trailing padding above, not as a separate Spacer here -- see that
                            // Text modifier's own doc.
                            // Plain Box, no forced baseline alignment: a `Modifier.alignByBaseline()`
                            // here once tried to line titleTrailing's glyphs up with the title's own
                            // baseline, reasoning that a Box (like Row/Column) forwards a single
                            // child's first baseline as its own. That held for the Box itself, but
                            // the hero's numbers report no baseline AT ALL by the time it would
                            // reach here -- AnimatedContent, RollingNumber's own Row and HeroNumbers'
                            // three-child Row each sit between the digits and this slot, and none of
                            // them forward one without an explicit alignByBaseline() opt-in on a
                            // child. With no baseline to align to, the Row's baseline placement fell
                            // back to the box's OWN bottom edge -- worse than the plain
                            // `verticalAlignment = CenterVertically` this Row already carries, which
                            // is what the numbers were built to sit in (see HeroNumbers' own
                            // `verticalAlign` doc). Falling back to that default here is the fix.
                            if (titleTrailing != null) {
                                Box { titleTrailing() }
                            }
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
                        // The gap between the header's text column and whatever control ends the
                        // row. Without it a title-row status ran straight into the chevron --
                        // "Imperial" and "Atkinson" touching the button beside them, reported
                        // from a real screenshot. The text column is weighted, so nothing else
                        // was ever going to introduce this space.
                        if (!forceExpanded && (headerAction != null || canToggle)) {
                            Spacer(Modifier.width(10.dp))
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
    // Each half gets its own shape: the OUTER corner morphs (pill when idle, rounded square
    // when that half's own state says morphed), the INNER corner is the shared seamCorner() --
    // the SAME idle/morphed nub the lock/horn/lights connected group and the split pills draw,
    // rather than a bespoke static 6dp this header was still carrying on its own. That
    // mismatch was real: every OTHER seamed pair in the app opens up as it presses (10dp ->
    // 16dp), and this one -- used by literally every card's own header -- did not, which is
    // what made a card's action+chevron read as a slightly different kind of control from the
    // connected lock group right below it on the same screen. Both halves are the same
    // MorphButton component; each one's own active/pressed state drives its OWN morph, forwarded
    // here as `morph` and fed straight into the shared helper.
    //
    // That seam nub is wrong when canToggle is false: the chevron half is never
    // rendered then (see the `if (canToggle)` below), so the action button sits
    // alone with nothing to seam against -- a small fixed corner on a side with
    // no neighbor just reads as a broken/half-finished pill (reported from a
    // real screenshot: the Summarize action, whose pebble is permanently
    // expanded in simple mode and so never shows a chevron). Full pill on both
    // sides in that case instead.
    val leftShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        val end = if (canToggle) seamCorner(morph) else CornerSize(percent = cp)
        RoundedCornerShape(
            topStart = CornerSize(percent = cp), bottomStart = CornerSize(percent = cp),
            topEnd = end, bottomEnd = end,
        )
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        val start = seamCorner(morph)
        RoundedCornerShape(
            topStart = start, bottomStart = start,
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

    // The climate icon's own spin now comes from MorphButtonLabel's `spinning` param below --
    // it already carries this exact ramp-up/hold/decelerate shape internally, so a second,
    // external copy of the same animation had nothing left to drive.

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
        // An action half joined to a chevron half is one control with a seam, so it must not
        // break onto two lines however narrow the header gets -- see `wrap`. It compacts to
        // glyphs instead, which is exactly right for a header running out of room.
        wrap = false,
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
                // The shared label -- same icon size, gap and type as every other button in
                // the app, including PrimaryActions' lock/horn/lights row, which this one
                // still drew nothing like: a bespoke 16dp icon, labelLarge text and a 6dp gap
                // that predated MorphButtonLabel entirely. Every card's own header action goes
                // through this one call site, so that mismatch was "the control pebble looks
                // different from the rest of the app" for literally every OTHER pebble at once.
                //
                // `spinning` replaces the hand-rolled `spinAngle` Animatable above -- the exact
                // same ramp-up/hold/decelerate shape already lives inside MorphButtonLabel's own
                // glyph, so driving a second, external copy of it here was duplicated animation
                // state for the same visual effect.
                //
                // widthIn still caps the label so a long action ("Downloading…") at a large font
                // size cannot grow this button unbounded and squeeze the pebble title -- but past
                // the cap this now compacts to the icon alone (the app's one fit rule) instead of
                // ellipsizing a half-cut word.
                //
                // 132dp, not 110: at 110 a perfectly ordinary short label ("Summarize", nine
                // letters and two wide 'm's at SemiBold) already landed past the cap and compacted
                // to the bare glyph -- a WIDE dark pill with a lone sparkle floating in it, reported
                // from a real screenshot. The group had already reserved this button its full,
                // uncapped intrinsic width (it measures the same subtree, cap included, so the
                // reservation and this box's own ceiling should describe the same content but did
                // not once the label was long enough to graze the old cap) -- so the pill stayed
                // wide while its content silently gave up the word inside it. 132dp comfortably
                // clears every short action label in the app ("Summarize", "Lock", "Stop", "Start",
                // "Install now") while still catching the genuinely long ones ("Downloading…",
                // "Installing…"), which is what this cap exists for.
                Box(Modifier.widthIn(max = 132.dp).graphicsLayer { translationY = bounceY.value }) {
                    MorphButtonLabel(
                        action.icon,
                        action.label,
                        pending = action.pending && !bouncing,
                        spinning = action.spinning,
                    )
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
                        modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer {
                            rotationZ = rotation + easterEggSpin
                        },
                    )
                }
            }
        }
    }
}
