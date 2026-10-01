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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import kotlin.math.floor
import kotlin.math.max
import com.bloo.bluelink.data.settingsMode

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
    modifier: Modifier = Modifier,
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
    modifier: Modifier = Modifier,
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
     * Overrides the colour of [title] and of the leading [icon] beside it.
     * [Color.Unspecified] (the default) inherits, which is what every pebble but the
     * hero wants.
     *
     * The ICON is covered by this deliberately, and was the bug: it renders on the same
     * row, over the same backdrop, 10dp from the first glyph of the title, so having it
     * resolve its colour from somewhere else than the title is a guaranteed mismatch --
     * and it was one, repeatedly reported on the hero.
     *
     * The hero needs it because its `background` slot puts a PHOTO behind the header,
     * and the header is drawn over that with the surface's own content colour -- so an
     * expanded card rendered the car's name in near-black on a dark photo and it could
     * not be read. The photo already carries a scrim built for light text; nothing was
     * telling the text to be light. Reported from a real device.
     */
    titleColor: Color = Color.Unspecified,
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
     * of it, and over an arbitrary car photo that text disappears -- a scrim under the
     * text is the cheap, reliable answer and is what the hero does.
     *
     * Null for every other pebble, so nothing else gains a layer.
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Drawn ON TOP of the header and body, at the same layer [background] sits under.
     *
     * The hero's travelling charge numbers use this: they are positioned in the card's own
     * coordinate space and cross the header row on the way between the collapsed and expanded
     * anchors, so drawn in [background] they passed UNDERNEATH the header's buttons and read as
     * a clipped glitch -- reported directly as the range text "glitching when it goes below the
     * buttons during the collapse". Same coordinate space as [background] (both are the card's
     * own Box), so nothing about the anchor arithmetic changes; only the z-order does.
     *
     * Null for every other pebble.
     */
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    /** Vertical gap between [content]'s top-level rows. The default [GapRow] is the standard
     *  pebble rhythm; a caller whose content spaces ITSELF with explicit [Spacer]s (the
     *  Settings cards do) passes 0.dp so the two don't stack into doubled gaps. */
    contentGap: Dp = GapRow,
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
    // The row's own real, measured width -- read off the SAME onSizeChanged callback that
    // already reports headerRowHeightPx below, rather than a separate BoxWithConstraints
    // wrapper. Both would report the same number, but BoxWithConstraints is a
    // SubcomposeLayout: its content composes in a SEPARATE, deferred pass, which is real,
    // avoidable overhead on a composable that runs for EVERY pebble's header, on every car
    // page -- reported as an app-wide stutter switching between cars, where up to three full
    // pebble columns (beyondViewportPageCount = 1) compose or recompose at once. A plain
    // onSizeChanged callback is layout-phase only, no extra composition pass, the same trade
    // headerRowHeightPx already made for the corner radius just below.
    //
    // Default a generous 1000.dp, not headerRowHeightPx's small-is-safe 0: this value CAPS
    // SplitExpandButton, so an inaccurate LOW default (before the real width lands, one
    // frame after first composition) would wrongly force it to compact on that first frame.
    // A HIGH default means the first frame renders exactly as it always did -- uncapped --
    // and the real cap takes over a frame later, imperceptible and safe in the direction
    // that matters (never a false compact, only a one-frame-late correct one).
    var headerRowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    val collapsedCorner = if (headerRowHeightPx > 0) {
        with(density) { (headerRowHeightPx / 2f).toDp() }
    } else {
        PebbleCornerCollapsed
    }
    // True while the expand/collapse chevron (MorphExpandButton, or
    // SplitExpandButton's chevron half) is actively held down -- fed up via
    // onChevronPressChange from whichever of the two this pebble renders
    // below, so the WHOLE CARD squares off together with the small control
    // sitting on top of it, not just that control's own shape. Previously
    // only the chevron/split-button morphed on hold; the card underneath
    // stayed at its normal expanded/collapsed corner the whole time, which
    // read as the tiny control changing shape independently of the pebble it
    // belongs to -- reported directly: "not just the button shape goes
    // square but the actual collapsed pebble is square."
    //
    // Targets PebbleCornerExpanded -- the SAME corner the card already lands
    // on once actually expanded -- not a separate, squarer constant. A hold
    // is a preview of "this is about to expand", so it should morph to
    // exactly the shape expanding gets you; landing on a DIFFERENT (more
    // square) radius made holding and expanding look like two different
    // target shapes for the same card, reported directly as inconsistent.
    var chevronPressed by remember { mutableStateOf(false) }
    val corner by animateDpAsState(
        targetValue = when {
            chevronPressed || expanded -> PebbleCornerExpanded
            else -> collapsedCorner
        },
        // The press morph reuses the OPEN spring regardless of expanded/collapsed:
        // a hold is a direct, immediate response to the finger still on the
        // screen, not a settle-into-place motion, so it wants the livelier of
        // the two springs this card already had rather than a third one.
        animationSpec = if (expanded || chevronPressed) {
            lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            lowPowerAwareSpring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
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
                // pebbleCardEdge (GlassChrome.kt): frostedRim's alpha (0.10-0.24) is
                // tuned for chrome floating over an unpredictable car photo, where it
                // only has to beat that photo's contrast -- against a flat dark pebble
                // background it was nearly imperceptible, reading as "this setting
                // does nothing" even though it was working. The dedicated, considerably
                // bolder border this shares instead is what makes toggling it visible.
                .pebbleCardEdge(pebbleShape, pebbleOutline),
            shape = pebbleShape,
            // No shadow elevation: the whole card is scaled by ReorderColumn's drag lift and
            // the cold-start intro (a graphicsLayer on the pebble's own Box above), and the
            // default 1dp shadow is a spot shadow baked into that layer -- so it re-rasterized
            // on every frame of the lift, which is exactly the drag/float chug. The border
            // already carries the depth.
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                // Slightly translucent so the aurora/gradient reads faintly through the card,
                // giving the pebbles a glassy weight instead of a flat opaque slab. Kept high
                // enough (0.9) that text contrast is unchanged.
                containerColor = containerColor.copy(alpha = 0.9f),
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
                    //
                    // headerActionMaxWidth reserves room for "the other stuff in the row"
                    // (the leading icon, the two gaps either side of the title column, the
                    // row's own start/end padding, and a floor for the title itself) before
                    // handing whatever's left to SplitExpandButton. Without this, the action
                    // button -- a plain, non-weighted Row child -- was measured against the
                    // row's FULL width (Row measures non-weighted children before it knows
                    // what its weighted sibling, the title column, will need), so its own
                    // already-existing compact-to-icon-only fit rule never had a reason to
                    // fire: it always concluded it had more than enough room for "Summarize",
                    // even while the title beside it ("AI Summary" -> "AI summ...") and its
                    // status line ("Not summarized" -> "Not summar...") were being ellipsized
                    // for space the button was never actually using. Reported from a real
                    // screenshot: text truncating on the left while the button keeps its full
                    // label on the right, the opposite of the intended priority.
                    //
                    // 140dp for the title floor -- raised from an initial 90dp, which turned
                    // out to still lose the tug-of-war: 90dp was sized for the TITLE alone
                    // ("AI Summary", "Diagnostics") and this column also carries the STATUS
                    // line right underneath it ("Not summarized", "Imperial", "Atkinson"),
                    // a second, independently-ellipsizing Text competing for the exact same
                    // width. 140dp comfortably covers either line at 1x font scale with real
                    // margin, not just the shorter of the two -- explicitly biased toward the
                    // text over the button, per the follow-up report that it should be. This
                    // is a floor for how much the ACTION BUTTON gives way, not a guarantee the
                    // text never ellipsizes: a title long enough to still exceed 140dp keeps
                    // ellipsizing on its own past that, same as before -- this only changes
                    // who gives way FIRST when the two compete.
                    val headerActionMaxWidth = (
                        headerRowWidthDp - 16.dp - 20.dp - ButtonIconGap - 10.dp - 12.dp - 140.dp
                        ).coerceAtLeast(0.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Feeds collapsedCorner above: this row's height IS the
                            // whole card's collapsed height (the body is hidden then),
                            // and it's stable across the expand/collapse animation
                            // itself (only the body grows/shrinks below it), so this
                            // never fires mid-bounce with a transient wrong value. Also
                            // feeds headerActionMaxWidth above, off the SAME callback --
                            // see headerRowWidthDp's own doc for why this replaced a
                            // BoxWithConstraints wrapper here.
                            .onSizeChanged {
                                headerRowHeightPx = it.height
                                headerRowWidthDp = with(density) { it.width.toDp() }
                            }
                            .then(
                                if (forceExpanded || !canToggle) Modifier
                                else Modifier.clickable {
                                    if (expanded) haptics?.tick() else haptics?.click()
                                    onToggle()
                                },
                            )
                            .then(modifier)
                            .heightIn(min = PebbleHeaderHeight)
                            // Asymmetric padding: 16dp left, 12dp right (was 16dp),
                            // pushing buttons slightly right while keeping symmetry.
                            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tinted with [titleColor], not left to inherit LocalContentColor.
                        // This icon sits IMMEDIATELY before the title on the same row and on
                        // the same backdrop, so the two must resolve their colour from the
                        // same place -- and until now only the title did. That split is the
                        // reported "the car icon before the car's name doesn't follow the
                        // theme" bug, raised repeatedly: on the hero the header is drawn over
                        // a scrimmed car photo and the title travels to HeroOnPhoto for it
                        // (see titleColor's own doc), while this Icon kept inheriting the
                        // CARD's content colour -- near-black in a light theme, and tinted by
                        // whatever slice of the seed colour an active custom palette
                        // feeds onSurfaceVariant. So the name was legible over the photo and
                        // the glyph 10dp to its left was not, in the app's light theme AND in
                        // any custom palette.
                        //
                        // takeOrElse, not a bare pass-through: titleColor defaults to
                        // Color.Unspecified for every pebble but the hero, and Icon treats
                        // Unspecified as "no ColorFilter at all" -- i.e. the raw vector's own
                        // colour rather than the inherited content colour -- which would have
                        // turned this fix into a regression on all ~10 other pebbles. Falling
                        // back to LocalContentColor keeps them byte-identical to before and
                        // leaves the hero as the one card that overrides.
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = titleColor.takeOrElse { LocalContentColor.current },
                            modifier = Modifier.size(20.dp),
                        )
                        // ButtonIconGap, not a bespoke 10dp: this is the exact same "icon, then
                        // label" pair MorphButtonLabel standardises everywhere else in the app,
                        // and it was the one place still stating that gap by hand.
                        Spacer(Modifier.width(ButtonIconGap))
                        Column(Modifier.weight(1f)) {
                            PebbleTitleRow(
                                title = title,
                                expanded = expanded,
                                growTitleOnExpand = growTitleOnExpand,
                                titleColor = titleColor,
                                titleTrailing = titleTrailing,
                                titleTrailingAtEnd = titleTrailingAtEnd,
                            )
                            if (summary != null) {
                                AnimatedContent(
                                    targetState = summary,
                                    transitionSpec = { expandContentTransform() },
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
                        // AnimatedVisibility around BOTH the gap and the control it precedes --
                        // together, so the whole trailing area slides/fades away as one unit
                        // instead of the Spacer's width popping independently of what follows
                        // it. This is the OTHER half of the settings-mode header animation (see
                        // titleTrailing's own AnimatedVisibility above): a single-setting pebble
                        // with no headerAction loses its chevron entirely the moment
                        // inlineSettingInSimpleMode takes over in simple mode (canToggle flips
                        // false, the `else if (canToggle)` branch below stops matching at all),
                        // and that used to just vanish with no transition while titleTrailing's
                        // control popped in at the same instant on the same row. No "last known
                        // value" snapshot needed here unlike titleTrailing's: headerAction/
                        // onToggle/expanded aren't conditionally null the way titleTrailing was,
                        // they're just not rendered while `visible` is false.
                        AnimatedVisibility(
                            visible = !forceExpanded && (headerAction != null || canToggle),
                            enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(140)),
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        // The gap between the header's text column and whatever control ends the
                        // row. Without it a title-row status ran straight into the chevron --
                        // "Imperial" and "Atkinson" touching the button beside them, reported
                        // from a real screenshot. The text column is weighted, so nothing else
                        // was ever going to introduce this space.
                        Spacer(Modifier.width(10.dp))
                            if (headerAction != null) {
                                // Renders the action half regardless; canToggle decides whether
                                // the chevron half comes with it (and reshapes the action's
                                // seam corner when it does not -- see SplitExpandButton).
                                SplitExpandButton(
                                    action = headerAction,
                                    expanded = expanded,
                                    onToggle = onToggle,
                                    canToggle = canToggle,
                                    modifier = Modifier.widthIn(max = headerActionMaxWidth),
                                    onChevronPressChange = { chevronPressed = it },
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
                                    onPressChange = { chevronPressed = it },
                                )
                            }
                        }
                        }
                    }
                    // Normal pebbles: animate the body sliding open/closed. The EXIT is a
                    // real fade now (it used to be fade = false) -- reported directly that
                    // closing "just clips behind the bottom of the closed pebble area instead
                    // of properly going away": with no block fade, the body's own content was
                    // sliced by the card's shrinking bottom edge for the whole collapse, and
                    // the per-row cascade alone was not enough to hide it. Fading the block as
                    // the height shrinks lets the content dissolve ON the way down instead of
                    // being cut off by the edge, so it reads as the body going away rather than
                    // as a clip. The rows still cascade underneath it; the fade just masks the
                    // column edge that the shrink exposes.
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandEnterSized(),
                        exit = expandExitSized(fade = true),
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
                                lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                            verticalGap = contentGap,
                            content = content,
                        )
                    }
                }
                // Drawn last, ON TOP of the header and body -- see foreground's own doc.
                foreground?.invoke(this)
            }
        }
    }
}

