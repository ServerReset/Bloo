@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle

/**
 * Measured, adaptive metrics for the cover-screen content region, provided by
 * [CoverScaffold] via [LocalCoverMetrics]. Tiles read this instead of guessing:
 * everything is derived from the REAL available space, so the cover adapts to any
 * cover size, aspect, camera-bump position, and font scale rather than cramming
 * against fixed assumptions.
 *
 * @property widthDp / heightDp measured size of the content region (post-inset).
 * @property isTiny true when the shorter usable side is below [COVER_TINY_DP] —
 *   tiles show fewer secondary rows / a tighter type step when tiny.
 * @property contentPadding the single merged inset (nav bar ∪ display cutout ∪
 *   camera-bump clearance ∪ base gutter), applied ONCE by the tile region.
 */
@androidx.compose.runtime.Immutable
data class CoverMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val isTiny: Boolean,
    val contentPadding: PaddingValues,
)


internal val LocalCoverMetrics = staticCompositionLocalOf<CoverMetrics?> { null }


/** Below this (shorter usable side, dp) the cover is "tiny" — trim to essentials. */
internal const val COVER_TINY_DP = 300f


/**
 * Horizontal content inset for cover pebbles.
 *
 * The one real consumer of [CoverMetrics.isTiny] -- [LocalCoverMetrics] was provided by
 * [CoverScaffold] and documented at length ("everything is derived from the REAL
 * available space... rather than cramming against fixed assumptions"), but nothing
 * actually read `isTiny` anywhere; every cover dimension was a flat constant
 * regardless of how small the measured region came out. This trims the inset by 4dp
 * on a tiny cover, which is a real fraction of a screen whose shorter usable side is
 * already under 300dp -- a fixed 16dp on both sides was costing that tile
 * proportionally more room than the same inset costs a larger cover.
 */
@Composable
internal fun coverContentInset(): Dp = if (coverIsTiny()) GapRow else GapGroup


/** True when the measured cover region is small enough to warrant the tighter of each pair
 *  below. Reads [LocalCoverMetrics], so it is the real region and not a guess from the config. */
@Composable
internal fun coverIsTiny(): Boolean = LocalCoverMetrics.current?.isTiny == true


/**
 * The cover tile's own spacing, expressed on the app's shared gap scale (see [GapHairline]
 * ... [GapSection]) rather than the bespoke 6/10/14dp literals that had settled in -- a cover
 * runs the same vertical rhythm as the phone, just one step tighter when the region is tiny.
 */
@Composable
internal fun coverTileEdgeGap(): Dp = if (coverIsTiny()) GapRow else GapGroup


/** Vertical padding inside the tile's scrolling body. */
@Composable
internal fun coverBodyPad(): Dp = if (coverIsTiny()) GapHairline else GapRow


/** Gap between the body's own children. */
@Composable
internal fun coverBodyGap(): Dp = if (coverIsTiny()) GapHairline else GapRow


/**
 * How far the scroll fade reaches into a cover tile's body. Shorter than the phone's
 * [HeroPhotoSlideDistance]-class fade, since on a cover the fade only needs to say "there
 * is more above", not dissolve a whole line of text.
 */
@Composable
internal fun coverFadeLength(): Dp = if (coverIsTiny()) GapRow else GapGroup


/**
 * The car the current cover page belongs to, provided by CompactCar so a tile deep inside it can
 * name its car without every tile composable having to take a Vehicle it otherwise never reads.
 */
internal val LocalCoverCarName = staticCompositionLocalOf<String?> { null }



/**
 * THE cover-screen tile template. Every page on the flip cover is one of
 * these, so they all read as the same object with different contents rather
 * than as a stack of unrelated cards.
 *
 * Three bands, always in this order:
 *  1. TITLE -- a small icon and the tile's name at title size, with an
 *     optional state [subtitle] under it. Cover pebbles used to have no title
 *     at all: the header row is dropped in fill-height mode (it cost ~76dp
 *     before a single line of content) and all that was left was a 30dp icon
 *     badge floating over the body's top-start corner. That badge said which
 *     tile you were on only if you already knew the iconography, and it
 *     overlapped the content it sat on.
 *  2. BODY -- weighted, so it takes everything left over, and centred within
 *     that. Scrolls when it's taller than the space, using the caller's
 *     [scrollState] so the cover pager can tell "scroll the tile" from "page
 *     to the next tile".
 *  3. ACTIONS -- an optional bottom bar pinned outside the scroll area, so a
 *     tile's controls are reachable no matter where its body is scrolled to.
 *
 * The bands are the standard; what goes in them is per-tile. That is the
 * whole point: the home tile's four-button bar and a pebble's single pinned
 * action are the same band in the same place at the same height, so paging
 * between them moves the content and nothing else.
 */
@Composable
internal fun CoverTile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    scrollState: ScrollState? = null,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    /**
     * A short secondary label pinned to the END of the title row -- in practice the car's name on
     * a section tile ("Charge          Kona").
     *
     * It lives on the title ROW rather than in an overlay because the cover screen previously had
     * both: a floating car-name overlay drawn over the pager, reserving no space, sitting in
     * exactly the band where each tile draws its own title. Two titles, one band. On the title
     * row it costs no extra height at all, cannot collide with anything, and the tile finally
     * says both what it is and whose car it belongs to in one line.
     */
    trailingLabel: String? = null,
    /**
     * The tile's one glanceable VALUE, rendered large on the header row in place of [title].
     *
     * A section tile used to say its subject three or four times over: the title named the
     * section, the subtitle carried the pebble's summary, and the body opened with a hero whose
     * value was -- on climate, info, location, trips, fuel and AI -- the very same expression as
     * that subtitle, ten dp below it. Location managed the address three times at once.
     *
     * So the summary IS the value, and it belongs on the header row where the eye lands, at
     * headline size, with the section carried by the icon beside it and the car by
     * [trailingLabel]. One line: "[bolt] Charging   Kona". [title] remains as the fallback for a
     * tile with nothing to report yet, and as the tile's identity for the scrubber rail.
     */
    headline: String? = null,
    actions: (@Composable () -> Unit)? = null,
    // Drawn BEHIND the title/body/actions, inside the card's own clip -- the same
    // slot PebbleShell's own `background` is for the phone hero, and for the same
    // reason: CoverMainTile uses this for a full-bleed car photo. Whatever's here
    // is responsible for its own legibility (see titleColor/iconTint below); null
    // for every other tile, so nothing else pays for the extra Box.
    background: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Defaults to the tone that PAIRS with [containerColor], not to onSurface.
     *
     * onSurface is the right colour only for a tile on the default surfaceVariant. The AI tile
     * sets containerColor = tertiaryContainer, so its header was drawing near-white text on a
     * pale lavender card -- legible in the sense that the pixels were there, and unreadable in
     * every sense that matters. The Card below already resolves contentColorFor(containerColor)
     * for everything else in the tile; the header was the one part opting out of it.
     */
    titleColor: Color = contentColorFor(containerColor),
    iconTint: Color = MaterialTheme.colorScheme.primary,
    /** False drops [icon] from the identity pill specifically (its only actual use site --
     *  see below), leaving just the text. [icon] itself stays required: every OTHER tile
     *  still wants it there, identifying what kind of tile this is next to its own car
     *  name. [CoverMainTile] is the one exception -- its own hero card IS the car, so a
     *  car glyph next to its own name repeated the exact same information the tile is
     *  already about, and cost real horizontal room the action buttons needed on a
     *  ~1-inch cover. Reported directly, with a screenshot: "remove the car icon... so it
     *  all fits on one row." */
    showIdentityIcon: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PebbleCornerExpanded)
    val outline = LocalAppearance.current.pebbleOutline
    // Glass tile with unified blur styling instead of plain card surface
    GlassSurface(
        modifier = modifier
            .fillMaxSize()
            .pebbleCardEdge(shape, outline),
        shape = shape,
        hazeState = hazeState,
    ) {
      Box(Modifier.fillMaxSize()) {
        background?.invoke(this)
        // The identity, as ONE pill that rides in the action row.
        //
        // At the top this was a full-width row of its own: it took the first fifth of a cover
        // screen before any content appeared, on a display where a fifth is most of what there
        // is. As a pill it costs whatever is left over beside the buttons -- and when nothing
        // is left over, the group's own wrapping puts it on the line above them. Neither
        // outcome is coded for; both fall out of the row it now lives in.
        val identityText = listOfNotNull(
            (headline?.takeIf { it.isNotBlank() } ?: title).takeIf { it.isNotBlank() },
            trailingLabel?.takeIf { it.isNotBlank() },
        ).joinToString("  \u00b7  ").let { if (it.length > 22) it.take(21).trimEnd() + "\u2026" else it }
        // The identity pill + action buttons are anchored to the tile's own bottom edge and
        // NEVER move, no matter how much or little body content there is -- the scrolling
        // content (below) runs the tile's FULL height behind them, the same "chrome floats,
        // content flows behind it" relationship every other floating bar in this app already
        // has with its own content (see searchBarClearance's own doc for the phone-side
        // version of the same idea). This used to be the other way around: the identity/
        // actions row was an ordinary Column sibling AFTER the scroll area, so it physically
        // pushed the scroll area's own bottom edge up to make room for itself -- which is
        // exactly what let short content leave a dead gap above it (already fixed once) and
        // meant content could never be seen passing behind it, only stopping short of it.
        //
        // bottomBandHeightPx is that row's own LIVE measured height (read via
        // onGloballyPositioned below), fed back as the scroll content's own bottom padding --
        // not a guessed constant, since the row's real height depends on the tile's own state
        // (a two-line subtitle vs. none, how many action buttons actually fit on one line).
        var bottomBandHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize().padding(horizontal = coverContentInset())) {
            Spacer(Modifier.height(coverTileEdgeGap()))
            // Extra top clearance whenever the search bubble is DOCKED into the camera-cutout
            // band (see coverCutoutBand's own doc) -- it parks right at the top edge, in the
            // same corner this Column's own content starts drawing from. Without this, a tile
            // whose content leads with a flush-left heading or a full-width image (Climate's
            // "Smart climate", the location map) had its own first few dp sitting directly
            // under the bubble, visually cut by it -- confirmed from a real screenshot.
            //
            // The band's own real height, not just CoverBandSearchDock (the bubble's fixed
            // docked SIZE) alone: a camera-island band can genuinely be taller than the bubble
            // that sits inside it, and reserving only the bubble's own size under-cleared the
            // rest of that band on those devices -- confirmed from a real screenshot (the
            // tile's first content row still starting under the band). maxOf keeps
            // CoverBandSearchDock as the floor for a band that reports smaller than the bubble
            // itself, which would otherwise under-reserve the other direction.
            coverCutoutBand()?.let { band ->
                Spacer(Modifier.height(maxOf(band.heightDp.dp, CoverBandSearchDock)))
            }
            // A scrolling Box that anchors its content to the TOP, rather than a
            // BoxWithConstraints whose only job was to read maxHeight and feed it back as
            // heightIn(min = ...).
            //
            // TopCenter, not Center: this box used to sit right under a header that ate the
            // top third of the tile, so a short body barely had room to look centred OR
            // top-anchored -- they read almost the same. Anchoring to the top instead reads
            // as "the content that's here", not "whatever fits, wherever it lands" -- and a
            // tall body still scrolls exactly as before, since TopCenter only matters once
            // content is SHORTER than the box.
            //
            // Plain weight(1f) now (not fill = false): this Box is the ONLY thing left in this
            // Column below the two Spacers -- the identity/actions row moved out to its own
            // bottom-anchored overlay below -- so there is no longer a following sibling for
            // short content to leave a dead gap in front of. Filling the whole remaining tile
            // height is exactly right now: it is what lets content keep scrolling behind the
            // floating row rather than stopping at wherever the content itself happens to end.
            val scroll = scrollState ?: rememberScrollState()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingEdges(scroll, length = coverFadeLength())
                    .verticalScroll(scroll),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = coverBodyPad())
                        // The bottom row's own live height plus one more coverBodyGap() of
                        // breathing room (the same gap that used to sit between the scroll
                        // area and the row when it was an in-flow sibling) -- so the LAST real
                        // content row can be scrolled fully clear of the band, with a little air
                        // to spare, instead of ending up flush against it or hidden behind it.
                        .padding(bottom = with(density) { bottomBandHeightPx.toDp() } + coverBodyGap()),
                    verticalArrangement = Arrangement.spacedBy(coverBodyGap()),
                    content = body,
                )
            }
        }
        // The bottom band itself: subtitle (if any) + the identity/actions row, anchored to
        // the tile's own bottom edge on top of the scrolling content above, reporting its own
        // real height back into bottomBandHeightPx so that content always has enough reserved
        // room to fully clear it.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = coverContentInset())
                .onGloballyPositioned { bottomBandHeightPx = it.size.height },
        ) {
            // The one thing that cannot be a pill: a sentence. It keeps its own muted line,
            // still in the bottom band, still above the row rather than at the top of the tile.
            // Only when it is not already the identity -- a caller that hands over the same
            // string twice should get one line, not two.
            if (!subtitle.isNullOrBlank() && subtitle != (headline ?: title)) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    // Not MutedContentAlpha atop the Card's own contentColor: that colour is
                    // already a lower-contrast MD3 role, and muting it again compounds two
                    // dimming steps into text reported as "overly gray" on a small screen.
                    color = subtitleColor ?: LocalContentColor.current.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = coverBodyPad()),
                )
            }
            // ONE row holds the identity and the actions, and the group decides how they
            // share it. No equalWidths: the identity pill must keep its natural size, so the
            // ACTIONS carry the weight and split whatever the pill leaves. That is also what
            // equalWidths used to be reaching for, minus the assumption that every member
            // deserves the same share.
            ExpressiveButtonRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = coverTileEdgeGap()),
                spacing = 6.dp,
                lineSpacing = coverBodyPad(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (identityText.isNotBlank()) {
                    CoverIdentityPill(
                        icon = if (showIdentityIcon) icon else null,
                        text = identityText,
                        iconTint = iconTint,
                    )
                }
                actions?.invoke()
            }
        }
      }
    }
}
