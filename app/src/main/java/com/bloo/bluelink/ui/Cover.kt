@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.isPluggedOrCharging
import kotlinx.coroutines.flow.first

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
internal fun coverContentInset(): Dp = if (coverIsTiny()) 10.dp else 12.dp

/** True when the measured cover region is small enough to warrant the tighter of each pair
 *  below. Reads [LocalCoverMetrics], so it is the real region and not a guess from the config. */
@Composable
internal fun coverIsTiny(): Boolean = LocalCoverMetrics.current?.isTiny == true

/**
 * The cover tile's own spacing, as three named steps instead of the literals that had settled in
 * (a 14dp gap above the title, 10dp of vertical padding inside the scrolling body, 10dp between
 * its children and another 14dp below).
 *
 * Those totalled ~48dp of pure padding before a single glyph, on a screen whose usable height is
 * frequently under 300 -- roughly a sixth of the tile spent on air. Phone-sized gaps do not
 * transfer to a one-inch display: the same 14dp that reads as comfortable on a 6" screen is a
 * visible chunk of a cover tile. Each step is tighter here and tighter again when the region is
 * genuinely tiny, which buys back about 20dp of vertical room -- a whole extra line of body text
 * on most tiles -- without any gap collapsing to nothing.
 *
 * Touch targets are deliberately NOT in here and are not shrunk: the cover is operated by a thumb
 * on a small square, which is why the action bar and the grouped buttons are already LARGER here
 * than on the phone. Compactness comes out of padding, never out of what you have to hit.
 */
@Composable
internal fun coverTileEdgeGap(): Dp = if (coverIsTiny()) 8.dp else 10.dp

/** Vertical padding inside the tile's scrolling body. */
@Composable
internal fun coverBodyPad(): Dp = if (coverIsTiny()) 4.dp else 6.dp

/** Gap between the body's own children. */
@Composable
internal fun coverBodyGap(): Dp = if (coverIsTiny()) 6.dp else 8.dp

/**
 * How far the scroll fade reaches into a cover tile's body.
 *
 * Much shorter than the 28dp the phone uses, and deliberately so: 28dp is a small fraction of a
 * phone pebble and most of a line of text on a cover screen, so the top of the body sat visibly
 * washed out the moment the tile scrolled at all. Here the fade only has to say "there is more
 * above", not dissolve the content saying it.
 */
@Composable
internal fun coverFadeLength(): Dp = if (coverIsTiny()) 10.dp else 14.dp

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
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PebbleCornerExpanded)
    val outline = LocalAppearance.current.pebbleOutline
    Card(
        modifier = modifier
            .fillMaxSize()
            .dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (outline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
                } else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor),
        ),
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
        ).joinToString("  \u00b7  ")
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
                        icon = icon,
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

/**
 * The flip cover's home tile: the car, its charge, and its controls on ONE
 * screen.
 *
 * It replaces two separate cover pages that were each mostly empty. The old
 * home tile was the phone's HeroHeader reused verbatim -- a card built around
 * a photo -- but landed on a flat gradient here instead: HeroHeader never went
 * through PebbleShell/CoverTile's fill-height cover treatment, so its own
 * `cover` branch stopped being reachable once this tile replaced it as the
 * cover's actual home page, leaving that photo path built but orphaned. The
 * lock/horn controls then lived on their own second page with the same
 * emptiness under them. Neither page filled a screen; both together do, and
 * merging them means the thing you most want with the phone shut -- lock
 * state and the lock button -- is on the page it opens on rather than one
 * swipe away.
 *
 * The car's photo is now this tile's own background (via CoverTile's
 * `background` slot), full-bleed with the same scrim [HeroPhotoBackdrop]
 * already builds for the phone hero -- reusing that composable rather than a
 * second implementation, so the two can't drift. No photo set -> HeroVisual's
 * own brand-gradient fallback fills the same way, so the tile is never a
 * dead inset rectangle either way.
 *
 * The car's name leads, at headline size. It used to be a labelMedium line in
 * the shared top overlay, sharing that space with the page dots, which on this
 * screen made the single most identifying thing on it the smallest text on it.
 */
@Composable
internal fun CoverMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val metric = LocalAppearance.current.unitSystem == "metric"
    val imageUrl = state.imageUrls[v.vin]
    val hasPhoto = !imageUrl.isNullOrBlank()
    // The car's own name is this tile's title -- the template's title band is
    // where every other page says what it is, so the home page says which car.
    // Lock leads the subtitle because it is the reason to look at a shut
    // phone; driving/charging state is left to ChargeFuelBar's own status
    // line, which is directly below it and already says both.
    val bits = listOfNotNull(
        status?.doorLock?.let { if (it) "Locked" else "Unlocked" },
        if (status?.airCtrlOn == true) "Climate on" else null,
    )
    // Same trade the phone hero makes over its own photo (HeroPhotoBackdrop's scrim
    // is built for HeroOnPhoto text): a fixed near-white reads correctly against
    // that scrim regardless of the photo's own brightness, where the theme's usual
    // onSurface/error tones would not. Lock's own attention colour (error, an
    // unlocked car) still needs to read as a WARNING over a photo, not just legible
    // -- swapped to a fixed warm red rather than the theme's errorContainer-tuned
    // MaterialTheme.colorScheme.error, which is calibrated against a flat surface.
    val titleColor = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.onSurface
    val subtitleColor = when {
        status?.doorLock == false -> if (hasPhoto) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error
        hasPhoto -> HeroOnPhoto.copy(alpha = MutedContentAlpha)
        else -> null
    }
    CoverTile(
        title = v.name,
        icon = Icons.Filled.DirectionsCar,
        // Lock/climate state rides the END of the header row rather than a second line beneath
        // it -- the same slot a section tile uses for the car's name, which the home tile does
        // not need because its headline IS the car. One line instead of two, and the state a
        // person opens a shut phone to check sits on the same line as the name rather than in
        // muted text under it. subtitleColor still turns it red on an unlocked car.
        trailingLabel = bits.joinToString(" · ").ifBlank { null },
        subtitleColor = subtitleColor,
        iconTint = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.primary,
        titleColor = titleColor,
        background = {
            // height is inert when fill = true -- HeroVisual only reads it in the
            // non-fill, non-aspectRatio branch (see its own `sizeModifier` when) --
            // so there's no real value to pass; this Box has no BoxWithConstraints
            // scope to measure one from anyway.
            HeroPhotoBackdrop(v, imageUrl, height = 0.dp, corner = PebbleCornerExpanded, fill = true)
        },
        actions = { CoverActionBar(v, state, vm) },
    ) {
        CompositionLocalProvider(LocalContentColor provides titleColor) {
        ChargeFuelBar(
            status,
            state.hasBattery(v),
            state.hasFuel(v),
            state.drivingLabel(v),
            metric = metric,
        )
        }
    }
}

/**
 * The cover screen's bottom control bar: one tap each for the actions that
 * live in the pebble headers on the phone -- lock, climate, charge, horn.
 *
 * Those header actions are the whole point of every pebble; on the cover they
 * were reachable only by swiping to the matching page, and two of them (climate
 * and charge) not at all, because those pages open on a glance hero rather than
 * their header. A shut phone is the surface where "just lock it" matters most,
 * so they get a permanent, full-width, thumb-height row instead -- the
 * [CoverTile] actions band, which every cover page now has.
 *
 * Buttons are sized by weight rather than fixed width, so a car with no
 * horn/lights support or no battery gets three fat buttons rather than four
 * narrow ones with a hole where the fourth was.
 */
@Composable
internal fun CoverActionBar(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val ev = status?.evStatus
    val locked = status?.doorLock
    val charging = ev?.batteryCharge == true
    val plugged = ev.isPluggedOrCharging
    val climateOn = status?.airCtrlOn == true
    val enabled = !state.loading
    CoverActionButton(
        icon = if (locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock,
        label = if (locked == true) "Unlock" else "Lock",
        // Attention, not confirmation: an unlocked car is the state worth
        // colouring, matching StateControl's own highlightWhenOff.
        attention = locked == false,
        pending = state.isPending(v.vin, "doors"),
        enabled = enabled,
        onClick = { if (locked == true) vm.unlock(v) else vm.lock(v) },
    )
    CoverActionButton(
        icon = Icons.Filled.Thermostat,
        label = if (climateOn) "Stop" else "Climate",
        active = climateOn,
        pending = state.isPending(v.vin, "climate"),
        enabled = enabled,
        onClick = { vm.toggleClimate(v) },
    )
    if (state.hasBattery(v)) {
        CoverActionButton(
            icon = Icons.Filled.Bolt,
            label = if (charging) "Stop" else "Charge",
            active = charging,
            pending = state.isPending(v.vin, "charge"),
            // The car can't start a charge it isn't plugged into, and the
            // Charge pebble's own header button is gated the same way.
            enabled = enabled && plugged,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
        )
    }
    if (v.supportsHornLights) {
        // One button doing double duty rather than a fifth icon squeezed into an
        // already-tight row on a ~1-inch cover: tap for the combined "Horn &
        // lights" the main phone UI leads with, long-press for lights-only --
        // silent, useful for finding a car in a dark lot without honking. The
        // main phone screen offers both as separate buttons in a group
        // (PrimaryActions); flashLights had no cover-screen path at all before
        // this, reported as a real feature gap. Long-press is already an
        // established cover gesture (the tile-scrubber rail, the edge-trace
        // refresh), so this isn't a new interaction language for the surface.
        CoverActionButton(
            icon = Icons.Filled.Campaign,
            label = "Horn",
            // Both flashLights and hornAndLights run under the same "hornLights"
            // pending key (AppViewModel), so one check covers either.
            pending = state.isPending(v.vin, "hornLights"),
            enabled = enabled,
            onClick = { vm.hornAndLights(v) },
            onLongClick = { vm.flashLights(v) },
        )
    }
}

/** One button in [CoverActionBar]: icon over a short label, filling its share
 *  of the row. Colour carries state -- [active] for a running command's target
 *  state, [attention] for a state the user probably wants to change. */
@Composable
internal fun CoverActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    /**
     * Lay the icon and label side by side in a shorter pill, instead of stacking them.
     *
     * Stacking exists so four buttons can share one row on a one-inch panel -- each column is
     * too narrow for an icon and a word side by side. A tile with a SINGLE action has no such
     * constraint: it has the whole row, so stacking spends 56dp of height to put a word under a
     * glyph that could sit beside it. The compact form is the shape the phone's own header
     * action already uses, so it also reads as the same control in both places.
     */
    compact: Boolean = false,
    active: Boolean = false,
    attention: Boolean = false,
    pending: Boolean = false,
    enabled: Boolean = true,
    // A second action on the same button, reached by holding rather than
    // tapping -- null for every caller but the horn/flash one. Kept optional
    // rather than every button growing a second gesture it has no use for.
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    // Same MorphButton as everywhere: active commands wear the primary
    // highlight, the "worth changing" state wears the error container, and
    // idle is the standard button fill. It simply pins both corner percents
    // to the same square 16dp value so a cover bar button never morphs.
    val squarePct = 100f * 16.dp.value / 56.dp.value
    // The content tone for every state the core reaches: active->onPrimary,
    // attention->onErrorContainer, else onSurface. Passed as BOTH the idle
    // content and the disabled content (full alpha, so the cover button's own
    // 45% whole-pill fade is the ONLY dim when disabled -- the core's default
    // label-only fade would compound on top of it).
    val contentFor = if (active) scheme.onPrimary
        else if (attention) scheme.onErrorContainer
        else scheme.onSurface
    val coverSource = remember { MutableInteractionSource() }
    SafeExpansiveButton(
        interactionSource = coverSource,
        enabled = enabled && !pending,
    ) {
        MorphButton(
            onClick = { onClick() },
            onClickHaptic = { haptics?.click() },
            onLongClick = onLongClick?.let { fn -> { haptics?.tick(); fn() } },
            enabled = enabled && !pending,
            active = active,
            interactionSource = coverSource,
            containerColor = if (attention) scheme.errorContainer else buttonContainer(),
            contentColor = contentFor,
            disabledContentColor = contentFor,
            pillCornerPercent = squarePct,
            morphedCornerPercent = squarePct,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            minHeight = 0.dp,
            // The actions carry the row's weight, so they split whatever the identity pill
            // leaves rather than every member claiming an equal share. See the bottom band.
            groupWeight = 1f,
            // No weight(1f): its parent here is SafeExpansiveButton's own layout, not the row,
            // so it was silently doing nothing. The equal share now comes from the group.
            modifier = Modifier
                .height(if (compact) 44.dp else 56.dp)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
        val glyph: @Composable () -> Unit = {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        val text: @Composable () -> Unit = {
            com.bloo.uicommon.FittedText(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = LocalContentColor.current,
                ),
            )
        }
        if (compact) {
            Row(
                // fillMaxHEIGHT, not fillMaxSize. MorphButtonCore sizes itself from its content,
                // and its own doc spells out the consequence of a content child that fills:
                // "the button had no intrinsic size of its own and simply took whatever the
                // incoming constraints allowed". In a full-width action row that is the whole
                // panel -- which is the enormous button, and no amount of guarding equalWidths
                // above could have helped, because the stretch was coming from inside.
                Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph()
                text()
            }
        } else {
            Column(
                // fillMaxHeight for the same reason. The stacked form is only ever used where
                // the group hands out an exact width anyway, so it never depended on filling.
                Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                glyph()
                Spacer(Modifier.height(3.dp))
                text()
            }
        }
    }
    }
}

/**
 * The tile's identity, wearing a button's shape but holding still: glyph, state word and car
 * name in a pill the same height and radius as the actions beside it.
 *
 * It is a group MEMBER, so it takes part in the row's sizing and gives width up when a real
 * button beside it is pressed -- but nothing ever presses it, so it never takes any. That is
 * what makes "info sitting in the action row" work as a layout rather than as a special case:
 * when it and the buttons fit on one line, it sits beside them; when they do not, the group's
 * own wrapping puts it on the line above. Neither outcome is coded for.
 *
 * Not clickable, and marked so: it looks like a button because it shares the row's shape
 * language, not because there is anything to press.
 *
 * Its text tone is fixed to onSurface rather than threaded in from the tile's own titleColor.
 * titleColor is calibrated for text painted directly over the tile's containerColor (or, on the
 * home tile, over a car photo) -- exactly what CoverActionButton's own contentFor doc already
 * flags as the wrong tone for a control that draws its OWN opaque buttonContainer() fill on top
 * of whatever is behind it. This pill is that same case: on the AI tile titleColor resolved to
 * onTertiaryContainer, a tone tuned for a pale lavender card, painted onto a neutral grey chip --
 * legible in the sense the pixels were there, and visibly dimmer than every other tile's identity
 * text. onSurface is what CoverActionButton already settled on for the same neutral fill.
 */
@Composable
private fun CoverIdentityPill(
    icon: ImageVector,
    text: String,
    iconTint: Color,
) {
    val idle = remember { MutableInteractionSource() }
    SafeExpansiveButton(interactionSource = idle, enabled = false) {
        Box(
            Modifier
                .heightIn(min = ButtonTargetHeight)
                .clip(CircleShape)
                .background(buttonContainer())
                .padding(horizontal = 14.dp)
                .semantics(mergeDescendants = true) { contentDescription = text },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                // The glyph keeps its accent -- it is carrying state the words are not (a
                // charging bolt, a snowflake) -- while the text takes the pill's content tone.
                MorphButtonLabel(icon, text, pending = false, iconTint = iconTint)
            }
        }
    }
}
