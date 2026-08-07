package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.relativeLabel
import com.bloo.bluelink.ui.ThemeMode
import com.bloo.bluelink.ui.resolveWidgetAccent
import com.bloo.bluelink.ui.resolveWidgetIsDark
import kotlinx.coroutines.flow.first

/**
 * The adaptive Bloo home-screen widget.
 *
 * ONE widget composable scales natively from a 1×1 tile to a 7×7 dashboard by
 * measuring its actual size ([SizeMode.Exact] → [LocalSize]) and choosing a layout
 * TIER, then showing progressively more modules (status ring, info stats, action
 * buttons, footer) as space allows — never cramming. What appears is the
 * intersection of "what fits" (size tier) and "what the user asked for"
 * ([WidgetConfig], edited in [WidgetConfigActivity], whose option set adapts to the
 * app's simple/advanced mode).
 *
 * Colors are resolved once in [provideGlance] via [WidgetTheme] — the exact accent
 * (per-car palette / dynamic color / vibrancy), semantic charge/unlock/climate
 * colors, and dark/light surfaces the rest of the app uses (see [resolveWidgetAccent]
 * / [resolveWidgetIsDark]) — rather than the vanilla [GlanceTheme] default, which
 * resolves to Android's generic wallpaper-derived widget palette and has no
 * relationship to Bloo's own branding or the user's in-app theme choices.
 *
 * All data is read once in [provideGlance] (suspend) and handed to the content as a
 * plain [Render] holder; the composables themselves do no I/O.
 */
class CarWidget : GlanceAppWidget() {

    // Exact = recompose for the real current size, so every launcher cell count
    // (and every mid-resize size) gets a layout tuned to its exact dimensions,
    // rather than snapping to a handful of Responsive buckets.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore(context).get(appWidgetId)
        val data = SnapshotStore(context).current()
        // A pinned widget shows ITS car or nothing — never silently swap to another.
        // Only a "follow" widget (null vin) tracks the app's currently-selected car.
        val car = if (config.vin != null) {
            data.vehicles.firstOrNull { it.vin == config.vin }
        } else {
            data.selected
        }
        val appearance = runCatching { SettingsStore(context).appearance.first() }.getOrNull()
            ?: SettingsStore.Appearance()
        val metric = appearance.unitSystem == "metric"
        val theme = WidgetTheme.resolve(context, appearance, config, car?.vin)
        val stale = car?.fetchedAt?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - it > com.bloo.bluelink.data.STALE_STATUS_MS
        } ?: false
        // The location map is fetched here (suspend) — not in the composables, which
        // can't do I/O — when the user enabled it and the car has coordinates. Sized
        // generously; the Image just scales it down for smaller layout slots.
        val mapBitmap = if (config.showMap && car?.lat != null && car.lon != null) {
            val density = context.resources.displayMetrics.density
            val edge = (150 * density).toInt()
            runCatching { WidgetMap.render(context, car.lat!!, car.lon!!, edge, theme.accentArgb) }.getOrNull()
        } else null
        // No-ops gracefully to the themed background when the car has no photo
        // set (SettingsStore.imageUrl is only ever a local file path here --
        // "/..." -- never a remote URL, matching how the app's own photo
        // picker stores it).
        val photoBitmap = if (config.photoBackground && car != null) {
            val path = runCatching { SettingsStore(context).imageUrl(car.vin) }.getOrNull()
            if (path != null && path.startsWith("/")) {
                WidgetPhoto.decodeCached(path)?.let { WidgetPhoto.blurredCached(it, path) }
            } else null
        } else null
        val render = Render(
            car = car,
            config = config,
            theme = theme,
            metric = metric,
            multiCar = data.vehicles.size > 1,
            stale = stale,
            mapBitmap = mapBitmap,
            photoBitmap = photoBitmap,
        )
        provideContent {
            GlanceTheme {
                Content(render)
            }
        }
    }

    private data class Render(
        val car: VehicleSnapshot?,
        val config: WidgetConfig,
        val theme: WidgetTheme,
        val metric: Boolean,
        val multiCar: Boolean,
        /** True when the car data is older than the staleness window — surfaces so
         *  the widget can flag "this may be out of date" instead of showing an
         *  hours-old lock/charge state as if it were live. */
        val stale: Boolean,
        /** Pre-fetched location map tile (null when disabled, no coords, or fetch
         *  failed) — I/O can't run in the Glance composables, so it's done in
         *  provideGlance and handed in ready to draw. */
        val mapBitmap: android.graphics.Bitmap?,
        /** Pre-fetched, pre-blurred car photo for the "Photo background" option
         *  (null when disabled or the car has no photo set) -- same reasoning
         *  as [mapBitmap], decoded/blurred in provideGlance via [WidgetPhoto]. */
        val photoBitmap: android.graphics.Bitmap?,
    )


    /** Below this width, [InfoStack] stops putting a value beside its label
     *  and starts stacking instead -- the same "give up on one line" width
     *  the original widget used for its own narrow-text fallback. */
    private val NARROW_WIDTH = 90.dp

    // Every size in this file -- text, icons, padding, the ring, the bar --
    // comes from [Scale], which now lives in WidgetScale.kt so its vertical
    // budgets can be tested. The [WidgetTier] enum still decides layout
    // STRUCTURE (what appears, how it's arranged); Scale decides how big.

    @Composable
    private fun Content(render: Render) {
        val car = render.car
        val photo = render.photoBitmap
        // Every text/tonal role swaps to a photo-safe variant when the photo
        // background is actually active (no photo set = falls straight back
        // to the normal themed surface) -- see WidgetTheme.forPhoto. Doing this
        // once here, on the Render itself, means every tier/module below just
        // keeps reading render.theme like normal and gets it for free.
        val effective = if (photo != null) render.copy(theme = render.theme.forPhoto()) else render
        val size = LocalSize.current
        // A pill only reads as a stadium while one side is short; past that
        // it would carve a large widget's own content into a lens, so it
        // falls back to the roundest ordinary corner rather than applying
        // literally. Every other choice applies at any size.
        val corner = when (render.config.effectiveCorner) {
            WidgetConfig.CORNER_SHARP -> 0.dp
            WidgetConfig.CORNER_ROUND -> 32.dp
            WidgetConfig.CORNER_PILL ->
                if (minOf(size.width, size.height) < 180.dp) 999.dp else 32.dp
            else -> 20.dp
        }
        val outerCorner = GlanceModifier.fillMaxSize().cornerRadius(corner)
        // Base padding scales continuously with size; a pill shape needs a
        // little extra on top of that so content doesn't clip against the
        // extreme corner curve.
        val basePadding = Scale.contentPadding(size)
        val root = (if (photo == null) outerCorner.background(effective.theme.background) else outerCorner)
            .padding(if (corner >= 999.dp) basePadding + 4.dp else basePadding)
        if (car == null) {
            EmptyState(root, effective.theme)
            return
        }
        // Whole-card tap target UNDER everything else: only specific inner
        // elements (HeaderRow, RailLayout's own column, a button) carried
        // their own clickable before this, so the photo/background and every
        // bit of empty space between modules did nothing at all when tapped
        // -- reported from a real device, most visible on a tall tile with a
        // lot of bare photo showing. Buttons and other inner clickables
        // still take priority for their own bounds; RemoteViews resolves an
        // overlapping tap to the innermost view that registered one.
        Box(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(corner).clickable(openAction(LocalContext.current)),
        ) {
            if (photo != null) {
                Image(
                    provider = ImageProvider(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                )
                // A flat dark scrim regardless of light/dark theme -- the photo's
                // own brightness varies too much car to car to trust either
                // theme's plain surface tint to stay legible under white text.
                Box(GlanceModifier.fillMaxSize().cornerRadius(corner).background(ColorProvider(Color(0f, 0f, 0f, 0.38f)))) {}
            }
            Box(modifier = root) {
                when (tierFor(size)) {
                    WidgetTier.MICRO_TINY -> MicroTinyLayout(car, effective)
                    WidgetTier.MICRO -> MicroLayout(car, effective)
                    WidgetTier.BANNER -> BannerLayout(car, effective)
                    WidgetTier.RAIL -> RailLayout(car, effective)
                    WidgetTier.COMPACT_SQUARE -> CompactSquareLayout(car, effective)
                    WidgetTier.COMPACT_WIDE_NARROW -> CompactWideNarrowLayout(car, effective)
                    WidgetTier.COMPACT_WIDE -> CompactWideLayout(car, effective)
                    WidgetTier.COMPACT_TALL_NARROW -> CompactTallNarrowLayout(car, effective)
                    WidgetTier.COMPACT_TALL -> CompactTallLayout(car, effective)
                    WidgetTier.MEDIUM_SQUARE -> MediumSquareLayout(car, effective)
                    WidgetTier.MEDIUM_WIDE -> MediumWideLayout(car, effective)
                    WidgetTier.MEDIUM_TALL -> MediumTallLayout(car, effective)
                    WidgetTier.LARGE_SQUARE -> LargeSquareLayout(car, effective)
                    WidgetTier.LARGE_WIDE -> LargeWideLayout(car, effective)
                    WidgetTier.LARGE_TALL -> LargeTallLayout(car, effective)
                    WidgetTier.XL_WIDE -> XlWideLayout(car, effective)
                    WidgetTier.XL_TALL -> XlTallLayout(car, effective)
                    WidgetTier.XL_SQUARE -> XlSquareLayout(car, effective)
                }
            }
        }
    }

    // ---- Empty / signed-out --------------------------------------------------

    @Composable
    private fun EmptyState(root: GlanceModifier, theme: WidgetTheme) {
        val size = LocalSize.current
        // [root] already carries Content's own padding, so the width text
        // actually gets is the tile minus both sides of it.
        val inner = (size.width - Scale.contentPadding(size) * 2).coerceAtLeast(16.dp)
        Box(modifier = root.clickable(openAction(LocalContext.current)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(
                    "Bloo",
                    TextStyle(color = theme.accentProvider, fontSize = Scale.titleSp(size), fontWeight = FontWeight.Bold),
                    maxWidth = inner,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                // The call to action only earns its space once there's room
                // for it to read as a sentence. Below that the wordmark
                // alone is the honest degradation -- the whole tile is
                // still tappable and still opens the app, so nothing is
                // lost but the prompt, which beats stacking "Open to sign
                // in" into a thirteen-row letter column on a 1x1 tile.
                if (size.height >= 72.dp && inner >= 80.dp) {
                    Spacer(GlanceModifier.height(4.dp))
                    FitText(
                        "Open to sign in",
                        TextStyle(color = theme.onSurfaceVariant, fontSize = Scale.subtitleSp(size)),
                        maxWidth = inner,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }

    // ---- Tier layouts --------------------------------------------------------

    /** True when this widget should show controls instead of info/ring at the
     *  MICRO/COMPACT_WIDE/COMPACT_TALL tiers -- see [WidgetConfig.priority]'s
     *  doc comment for why this only applies below MEDIUM. */
    private fun controlsPriority(render: Render) = render.config.priority == WidgetConfig.PRIORITY_CONTROLS

    /**
     * Budget-fraction status-ring/glyph edge for a controls-priority layout.
     *
     * Every controls-priority tier used to replace its ENTIRE content with
     * ActionButtons and return -- so "small sizes: Controls" meant a tile
     * showing literally nothing about the car itself: no charge, no lock
     * state, not even which car it was. Reported from a batch of real
     * device screenshots across every controls-priority tier.
     *
     * A small fraction of whatever budget the caller has, capped low: the
     * buttons stay the point of a controls-priority tile, this is a glance
     * at the car's own state alongside them, not a second ring module
     * competing for the same room. Returns 0 (via [Scale.ring]'s own floor)
     * when even that fraction can't read.
     */
    private fun controlsMiniStatusEdge(size: DpSize, budget: Dp, fraction: Float = 0.32f, cap: Dp = 40.dp): Dp =
        Scale.ring(size, minOf(budget * fraction, cap))

    /** Draws whichever of [RingImage]/[StatusGlyph] the config calls for at
     *  [edge], or nothing when [edge] is too small to read -- the shared
     *  body every controls-priority tier's mini status badge uses, so the
     *  ring/glyph choice can't drift between them. */
    @Composable
    private fun MiniStatus(car: VehicleSnapshot, render: Render, edge: Dp) {
        if (edge < 16.dp) return
        if (render.config.showRing && car.percent != null) {
            RingImage(car, render, edgeDp = edge.value.toInt())
        } else {
            StatusGlyph(car, render.theme, sizeDp = edge.value.toInt())
        }
    }

    @Composable
    private fun MicroTinyLayout(car: VehicleSnapshot, render: Render) {
        // The true floor -- literally no room for any text at all (a name at
        // any legible size would overflow a <60dp tile), so this is pure
        // iconography: ring/glyph, or one button filling the whole tile.
        val size = LocalSize.current
        val fit = (minOf(size.width, size.height) - 16.dp).coerceAtLeast(10.dp)
        if (controlsPriority(render)) {
            val action = resolvedActions(car, render, max = 1).firstOrNull()
            if (action != null) {
                val iconSize = fit.coerceIn(12.dp, 26.dp)
                ActionButton(action, car, render, modifier = GlanceModifier.fillMaxSize(), fixedHeight = false, iconSize = iconSize)
                return
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            contentAlignment = Alignment.Center,
        ) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = Scale.ring(size, fit).value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = fit.coerceIn(12.dp, 34.dp).value.toInt())
            }
        }
    }

    @Composable
    private fun MicroLayout(car: VehicleSnapshot, render: Render) {
        // A little roomier than MICRO_TINY -- same ring/glyph/button core,
        // but now there's just enough space for one tiny caption underneath
        // when the ring itself isn't shown. FitText's own vertical fallback
        // still covers the case where even that single caption is too wide.
        val size = LocalSize.current
        val fit = (minOf(size.width, size.height) - 22.dp).coerceAtLeast(14.dp)
        if (controlsPriority(render)) {
            val action = resolvedActions(car, render, max = 1).firstOrNull()
            if (action != null) {
                val iconSize = fit.coerceIn(14.dp, 30.dp)
                ActionButton(action, car, render, modifier = GlanceModifier.fillMaxSize(), fixedHeight = false, iconSize = iconSize)
                return
            }
        }
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = Scale.ring(size, fit).value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = fit.coerceIn(14.dp, 36.dp).value.toInt())
                Spacer(GlanceModifier.height(2.dp))
                FitText(
                    car.name, subtitleStyle(render.theme),
                    maxWidth = (size.width - 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                )
            }
        }
    }

    @Composable
    private fun BannerLayout(car: VehicleSnapshot, render: Render) {
        // A long thin horizontal strip, down to 640x40 -- a 16:1 tile the
        // launcher genuinely allows. Everything sits on ONE vertically
        // centered row, because there is no room for a header above or a
        // footer below: at 40dp tall the padded content box is 28dp, barely
        // two lines of small text.
        val size = LocalSize.current
        // Only take over the tile if there's actually something to show:
        // resolvedActions filters by brand (Kia and the Canada backend have
        // no flash/horn endpoint), so a widget configured with only those
        // resolves to an empty list, and returning here regardless would
        // render a completely blank banner.
        if (controlsPriority(render) && resolvedActions(car, render, max = 6).isNotEmpty()) {
            val edge = controlsMiniStatusEdge(size, size.height - Scale.contentPadding(size) * 2)
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.width(8.dp))
                }
                ActionButtons(car, render, max = 6, modifier = GlanceModifier.defaultWeight())
            }
            return
        }
        val showsRing = render.config.showRing && car.percent != null
        // The width each weighted child of this Row REALLY gets: the padded
        // content width, less the one fixed 8dp spacer between them, halved.
        //
        // Two things were wrong here, in opposite directions:
        //
        // It subtracted a ring edge whenever showsRing was true -- but a banner
        // ALWAYS takes the bar treatment (see below), so no ring is ever drawn on
        // this tier and that was pure loss. The `if (showsRing && !useBar)` ring
        // branch it reserved for could not run at all: useBar was assigned
        // showsRing, so the condition read `showsRing && !showsRing`. On a 300x78
        // tile with a 52dp ring edge that cost both weighted children ~30dp of
        // width they actually had, which is what made button labels drop out and
        // text shrink earlier than it needed to.
        //
        // And it measured against the RAW tile width while the root has already
        // applied Scale.contentPadding on both sides, so it over-reported by that
        // much -- harmless while the ring subtraction was masking it, but not once
        // that goes. Removing only the ring term would have handed ActionButtons a
        // slice wider than the row really has, and its capacity check would then
        // fit one button too many. Both corrections belong together; `w` is the
        // same padded width MediumWide and LargeWide already compute.
        val w = size.width - Scale.contentPadding(size) * 2
        val slice = ((w - 8.dp) / 2).coerceAtLeast(24.dp)
        // A banner is almost pure width, the shape a bar was built for -- it
        // reads its value from across a room in a fraction of the height a
        // ring needs. The circle is for compact/vertical tiles, and this tile
        // is neither, so the bar is not a fallback here -- it is the treatment.
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (showsRing) {
                    BarHero(car, render, width = slice)
                } else {
                    NameAndStat(car, render, width = slice)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            // A banner is nearly all width, so the buttons get a real share
            // of it rather than the thin sliver a normal compact row leaves.
            // The real slice, not a fraction-of-tile guess: this Row splits its
            // padded width, less the spacer above, evenly between the text
            // column and the buttons, so that is exactly what the capacity
            // maths should see.
            ActionButtons(
                car, render, max = 4,
                modifier = GlanceModifier.defaultWeight(),
                availableWidth = slice,
            )
        }
    }

    /** Fixed safety margin subtracted from a tall tier's free-column budget,
     *  on top of whatever's individually reserved (buttons, name). Covers the
     *  small incidental spacers between modules -- the gap before a map, the
     *  gap before info rows -- that would otherwise need reserving one at a
     *  time for a saving of a few dp. Shared by [RailLayout],
     *  [CompactTallNarrowLayout] and [CompactTallLayout]; swept for overflow
     *  in WidgetScaleTest with this exact value.
     *
     *  Was 12.dp: too small on a narrow, short tile where the button stack
     *  alone (plus its own gap-before spacer) already consumes the whole
     *  budget -- heroRoom then clamps to 0.dp, and clamping means this
     *  margin was never actually SUBTRACTED from anything, so the forced
     *  gap-before-map/gap-before-buttons spacers that render unconditionally
     *  whenever there's a map or a button row overflowed the tile by up to
     *  2-4dp regardless of how big this constant was. Bumped to cover that
     *  plus the matching bump to each layout's own maxStackedButtons
     *  overhead below, so the button count itself leaves room rather than
     *  relying on this margin alone. */
    private val TALL_TIER_MARGIN = 16.dp

    /** Minimum height a tall/narrow tier's hero (ring or glyph) is guaranteed
     *  before the button stack is even sized, so a widget configured with
     *  every action doesn't degrade to zero status -- just buttons filling
     *  the whole tile, with no charge, lock state, or which car it even is.
     *  Reported from a real device: a RAIL-shaped tile with 4 actions
     *  configured showed a name and four buttons and nothing else, because
     *  the button stack alone consumed the whole budget before the ring was
     *  ever sized. Subtracted from the budget maxStackedButtons sees, so a
     *  generous action list gets fewer stacked buttons rather than the ring
     *  losing the room entirely. [MIN_RING] is 24.dp; this is deliberately
     *  bigger so the ring reads as a real gauge, not the smallest legible
     *  circle. */
    private val MIN_HERO_RESERVE = 40.dp

    @Composable
    private fun RailLayout(car: VehicleSnapshot, render: Render) {
        // The vertical mirror of BANNER, down to 40x640. Deliberately shows
        // NO name: at 40dp wide the content box is 28dp, and any car name
        // there would letter-stack into a column taller than the tile (see
        // FitText). That is the only thing this tier gives up -- everything
        // else here now scales with the tile the way every other tier does.
        //
        // A Rail resized tall used to spend almost none of the extra height:
        // a ring capped well short of what the width allowed, at most 4
        // buttons regardless of how many were configured or how much room
        // there was to stack them, and no map even with location switched
        // on -- a small cluster centred in a sea of empty photo, reported
        // from real devices across several widget sizes in one batch. The
        // buttons' own reserved zone is sized for every CONFIGURED action
        // (not a fixed handful), and the ring/map split whatever is left the
        // same way [MediumTallLayout] and the other tall tiers already do.
        val size = LocalSize.current
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = size.height - Scale.contentPadding(size) * 2
            val edge = controlsMiniStatusEdge(size, size.width - Scale.contentPadding(size) * 2, fraction = 1f)
            val spacerH = if (edge >= 16.dp) 8.dp else 0.dp
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.height(spacerH))
                }
                ActionButtons(
                    car, render, max = WidgetAction.ALL.size, vertical = true,
                    modifier = GlanceModifier.defaultWeight(),
                    availableHeight = (budgetH - edge - spacerH).coerceAtLeast(0.dp),
                )
            }
            return
        }
        val budget = size.height - Scale.contentPadding(size) * 2
        // Reserved BEFORE the hero content is sized, same reasoning as the
        // map-before-ring fix elsewhere in this file: the thing with a real,
        // guaranteed size requirement has to claim its room first, or a
        // "hero grows to fill whatever's offered" element (the ring here)
        // just eats the space a variable-length button stack actually needs.
        //
        // The count itself is capped by what the BUDGET can actually hold,
        // not a flat number -- six stacked buttons at this tier's own button
        // height can exceed the whole content box near Rail's 220dp floor,
        // before the ring or a map has claimed anything. See maxStackedButtons.
        // overhead is 16.dp, not buttonZone's own 8.dp: buttonZone's trailing
        // +8.dp is matched here, PLUS the separate forced Spacer(8.dp) that
        // renders unconditionally right before ActionButtons below whenever
        // buttonCount > 0 -- that spacer isn't part of buttonZone, so a
        // button count chosen without reserving it too overflowed the tile
        // by up to 2dp whenever the map spacer also landed on top.
        val buttonCount = Scale.maxStackedButtons(
            size, (budget - MIN_HERO_RESERVE).coerceAtLeast(0.dp), overhead = 16.dp, cap = allActions.size,
        )
        val buttonZone = if (buttonCount > 0) {
            Scale.buttonHeight(size) * buttonCount + Scale.buttonGap(size) * (buttonCount - 1) + 8.dp
        } else {
            0.dp
        }
        // A small fixed margin on top of the buttons/name reservations,
        // covering the incidental spacers between modules (the gap before a
        // map, the gap before info rows) that aren't individually budgeted --
        // deliberately generous, the same trade [Scale.infoCap] already
        // makes: a few dp of unused room costs nothing, spilling past the
        // tile does.
        val heroRoom = (budget - buttonZone - TALL_TIER_MARGIN).coerceAtLeast(0.dp)
        // Never on RAIL: a location map needs real width to read as a place
        // rather than a random street fragment, and RAIL's whole point is
        // being under 110dp wide. Reported from a real device -- the map
        // filled almost the entire tile with an unreadable, zoomed-in sliver
        // of road while the ring shrank to a badge and the buttons to a
        // sliver of their own. That width the map would have claimed goes
        // to the ring instead, same as when the user has location off.
        val hasMap = false
        // capRows = 0: Rail shows no text, so tallSplit's row budget is
        // unused and the ring gets everything the map reserve didn't claim.
        val split = Scale.tallSplit(size, heroRoom, capRows = 0, textScale = render.theme.textScale, wantMap = hasMap)
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Weighted spacers ABOVE and below centre what's left when there
            // is no map to fill it, rather than piling everything at the top.
            if (!hasMap) Spacer(GlanceModifier.defaultWeight())
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = split.ring.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = split.ring.value.toInt())
            }
            if (hasMap) {
                Spacer(GlanceModifier.height(6.dp))
                MapFill(render, split.map)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            if (buttonCount > 0) {
                Spacer(GlanceModifier.height(8.dp))
                ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
            }
        }
    }

    @Composable
    private fun CompactSquareLayout(car: VehicleSnapshot, render: Render) {
        // Controls priority here gets a real 2x2 grid instead of a single
        // row/column -- there's enough room on a near-square 90dp+ tile for
        // four properly-sized buttons arranged like a mini keypad, a shape
        // none of the other controls-priority layouts use.
        val size = LocalSize.current
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 4)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, minOf(size.width, size.height) - Scale.contentPadding(size) * 2)
                Column(modifier = GlanceModifier.fillMaxSize().padding(4.dp)) {
                    if (edge >= 16.dp) {
                        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            MiniStatus(car, render, edge)
                        }
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    actions.chunked(2).forEachIndexed { i, row ->
                        if (i > 0) Spacer(GlanceModifier.height(4.dp))
                        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                            row.forEachIndexed { j, action ->
                                if (j > 0) Spacer(GlanceModifier.width(4.dp))
                                ActionButton(action, car, render, modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
                return
            }
        }
        val scale = render.theme.textScale
        // The last fraction-of-the-tile ring cap in this file, and it was
        // wrong the same way every other one was: 55% of the height assumes
        // the name above and the stat below are small, and they aren't at
        // larger text sizes. Of the COMPACT_SQUARE size range this column
        // overran its tile on 498 configurations, by as much as 33dp -- a
        // third of a small tile's content bleeding past the bottom edge,
        // which is what RemoteViews does with an overfull Column instead of
        // clipping it. Budgeted from what the text actually leaves now, and
        // capped by the width too so the circle stays a circle.
        val budget = size.height - Scale.contentPadding(size) * 2
        val left = (budget - Scale.lineHeight(Scale.titleSp(size).value, scale) - 8.dp).coerceAtLeast(0.dp)
        val rows = Scale.infoRowsIn(size, left, scale, cap = 1)
        val ringEdge = Scale.ring(
            size,
            minOf(left - Scale.infoBlockHeight(size, rows, scale), size.width - 8.dp),
        )
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = size.width - 8.dp, horizontalAlignment = Alignment.CenterHorizontally,
            )
            Spacer(GlanceModifier.height(4.dp))
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            Spacer(GlanceModifier.height(4.dp))
            if (rows > 0) InfoStack(car, render, max = rows)
        }
    }

    @Composable
    private fun CompactWideNarrowLayout(car: VehicleSnapshot, render: Render) {
        val size = LocalSize.current
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 2)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, (size.height - Scale.contentPadding(size) * 2))
                Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (edge >= 16.dp) {
                        MiniStatus(car, render, edge)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    ActionButtons(car, render, max = 2, modifier = GlanceModifier.defaultWeight())
                }
                return
            }
        }
        // Narrower than COMPACT_WIDE's own threshold -- no room for a
        // subtitle line beside the ring too, just the name, and only 2
        // buttons instead of 3.
        val ringEdge = Scale.ring(size, (size.height - 12.dp).coerceAtLeast(18.dp))
        val showsRing = render.config.showRing && car.percent != null
        // The width each weighted child of this Row REALLY gets: whatever is
        // left once the ring and the fixed spacers are taken out, split
        // between the text column and the buttons. The fraction-of-tile
        // guesses this replaces were wrong twice over -- they under-reported
        // the slice, and they kept assuming a ring was there even when one
        // isn't drawn, so a widget with the ring switched off still laid its
        // text and buttons out as if a third of the row were missing.
        val slice = ((size.width - (if (showsRing) ringEdge + 6.dp else 0.dp) - 6.dp) / 2)
            .coerceAtLeast(24.dp)
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (showsRing) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.width(6.dp))
            }
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = slice, modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            ActionButtons(car, render, max = 2, modifier = GlanceModifier.defaultWeight(), availableWidth = slice)
        }
    }

    @Composable
    private fun CompactWideLayout(car: VehicleSnapshot, render: Render) {
        val size = LocalSize.current
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 4)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, (size.height - Scale.contentPadding(size) * 2))
                Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (edge >= 16.dp) {
                        MiniStatus(car, render, edge)
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    ActionButtons(car, render, max = 4, modifier = GlanceModifier.defaultWeight())
                }
                return
            }
        }
        val showsRing = render.config.showRing && car.percent != null
        // The width each weighted child of this Row REALLY gets. Same two
        // corrections as BannerLayout's own slice -- see there for the long
        // version. In short: no ring is ever drawn on this tier, because useBar
        // was assigned showsRing and so the `showsRing && !useBar` ring branch
        // read `showsRing && !showsRing`; subtracting a ring edge therefore cost
        // both weighted children width they actually had. And the measurement
        // has to be against the PADDED content width, not the raw tile width, or
        // ActionButtons' capacity check is handed a slice wider than the row
        // really is and fits one button too many.
        //
        // The height-capped ringEdge this used to compute went with it: it existed
        // only to be subtracted here and to feed the unreachable branch.
        val w = size.width - Scale.contentPadding(size) * 2
        val slice = ((w - 8.dp) / 2).coerceAtLeast(24.dp)
        // Same call as BANNER: this tile is wide, not compact/vertical, so the
        // bar is the treatment here rather than a fallback for a shrinking ring.
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // BUG this fixes: ActionButtons' own default modifier is
            // fillMaxWidth(), which is correct when it's the sole/last child
            // of a Column (every other call site) but wrong here -- as a
            // plain, unweighted sibling of this Row's own weighted text
            // column, "fill max width" meant "claim the width of the WHOLE
            // row", not "whatever's left after the ring and text", pushing
            // the button row past the tile's right edge entirely (clipped
            // only by the outer corner's rounding, which is what made it
            // look like a button was cut in half rather than missing outright).
            // Giving it a weight too makes it share the remaining space
            // fairly with the text column instead of overrunning it.
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (showsRing) {
                    BarHero(car, render, width = slice)
                } else {
                    NameAndStat(car, render, width = slice)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            ActionButtons(car, render, max = 3, modifier = GlanceModifier.defaultWeight(), availableWidth = slice)
        }
    }

    @Composable
    private fun CompactTallNarrowLayout(car: VehicleSnapshot, render: Render) {
        // At its OWN minimum this tier really is name + ring/glyph + one
        // button with nothing to spare, which is what the old fixed caps
        // matched -- but a widget resized well past that floor kept the same
        // single button and no location no matter how tall it got. Same
        // treatment as Rail now: the button zone is sized for every
        // configured action, and whatever height that leaves is split
        // between the ring and an optional map the same way every other
        // tall tier already does.
        val size = LocalSize.current
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = size.height - Scale.contentPadding(size) * 2
            val edge = controlsMiniStatusEdge(size, size.width - Scale.contentPadding(size) * 2, fraction = 1f)
            val spacerH = if (edge >= 16.dp) 6.dp else 0.dp
            Column(GlanceModifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.height(spacerH))
                }
                ActionButtons(
                    car, render, max = WidgetAction.ALL.size, vertical = true,
                    modifier = GlanceModifier.defaultWeight(),
                    availableHeight = (budgetH - edge - spacerH - 8.dp).coerceAtLeast(0.dp),
                )
            }
            return
        }
        val budget = size.height - Scale.contentPadding(size) * 2
        val nameHeight = Scale.lineHeight(Scale.titleSp(size).value, render.theme.textScale) + 4.dp
        // Capped by what actually fits after the name, not a flat number --
        // see maxStackedButtons and RailLayout's own note on why.
        // overhead is 8.dp, not buttonZone's own 4.dp -- same reasoning as
        // RailLayout's own overhead comment: the separate forced
        // Spacer(4.dp) rendered right before ActionButtons below whenever
        // buttonCount > 0 isn't part of buttonZone and has to be reserved
        // here too, or the button count picked leaves no room for it.
        val buttonCount = Scale.maxStackedButtons(
            size, (budget - nameHeight - MIN_HERO_RESERVE).coerceAtLeast(0.dp), overhead = 8.dp,
            cap = allActions.size.coerceAtMost(4),
        )
        val buttonZone = if (buttonCount > 0) {
            Scale.buttonHeight(size) * buttonCount + Scale.buttonGap(size) * (buttonCount - 1) + 4.dp
        } else {
            0.dp
        }
        // Never on this tier either -- same reasoning as RailLayout's own
        // hasMap: under 150dp wide, a location map reads as an unreadable
        // zoomed-in street fragment rather than a place, and the width it
        // would have claimed is worth more spent on the ring/name/buttons
        // that already fit this tile.
        val hasMap = false
        val heroRoom = (budget - nameHeight - buttonZone - TALL_TIER_MARGIN).coerceAtLeast(0.dp)
        val split = Scale.tallSplit(size, heroRoom, capRows = 1, textScale = render.theme.textScale, wantMap = hasMap)
        // The width cap that already existed here, kept: it's what keeps the
        // circle round on a genuinely narrow tile.
        val ringEdge = minOf(split.ring, size.width - 12.dp)
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = size.width - 8.dp, horizontalAlignment = Alignment.CenterHorizontally,
            )
            Spacer(GlanceModifier.height(4.dp))
            if (!hasMap) Spacer(GlanceModifier.defaultWeight())
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            if (split.rows > 0) {
                Spacer(GlanceModifier.height(4.dp))
                InfoStack(car, render, max = split.rows)
            }
            if (hasMap) {
                Spacer(GlanceModifier.height(4.dp))
                MapFill(render, split.map)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            if (buttonCount > 0) {
                Spacer(GlanceModifier.height(4.dp))
                ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
            }
        }
    }

    @Composable
    private fun CompactTallLayout(car: VehicleSnapshot, render: Render) {
        // COMPACT_TALL's threshold only proves the HEIGHT is roomy, not the
        // width, so the ring is still capped against the width -- but the
        // height it does have should be USED. This tier used a fixed 2 info
        // rows and 2 buttons regardless of how tall it actually got resized,
        // and never showed a map even with location on. Reported from real
        // devices: a name, one info row, and two buttons with the rest of a
        // very tall tile left as bare photo above and below.
        val size = LocalSize.current
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = size.height - Scale.contentPadding(size) * 2
            val edge = controlsMiniStatusEdge(size, size.width - Scale.contentPadding(size) * 2, fraction = 1f)
            val spacerH = if (edge >= 16.dp) 8.dp else 0.dp
            Column(GlanceModifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.height(spacerH))
                }
                ActionButtons(
                    car, render, max = WidgetAction.ALL.size, vertical = true,
                    modifier = GlanceModifier.defaultWeight(),
                    availableHeight = (budgetH - edge - spacerH - 12.dp).coerceAtLeast(0.dp),
                )
            }
            return
        }
        val budget = size.height - Scale.contentPadding(size) * 2
        val nameHeight = Scale.lineHeight(Scale.titleSp(size).value, render.theme.textScale)
        // STACKED, not a single row -- this tier proved out tall, and a
        // single row of buttons truncates to whatever the WIDTH can fit
        // (three of four configured actions, on a real device) while all
        // the height that could have stacked the fourth sits empty above
        // and below. Same maxStackedButtons treatment RailLayout and
        // CompactTallNarrowLayout already use, so the button count is a
        // consequence of the real vertical budget rather than the width.
        // overhead = 20.dp: buttonZone's own trailing 12.dp PLUS the forced
        // Spacer(8.dp) below that renders whenever buttonCount > 0 and is not
        // part of buttonZone. Same rule RailLayout states (8 + 8 = 16) and
        // CompactTallNarrowLayout follows (4 + 4 = 8); this tier passed 12,
        // i.e. the trailing gap alone, so it under-reserved by exactly that
        // spacer -- the same defect both of those comments describe as fixed,
        // on the tier with the largest forced spacers of the three.
        // TALL_TIER_MARGIN cannot cover it: heroRoom clamps at zero, and when
        // it does the margin is never actually subtracted from anything.
        val buttonCount = Scale.maxStackedButtons(
            size, (budget - nameHeight - MIN_HERO_RESERVE).coerceAtLeast(0.dp), overhead = 20.dp,
            cap = allActions.size,
        )
        val buttonZone = if (buttonCount > 0) {
            Scale.buttonHeight(size) * buttonCount + Scale.buttonGap(size) * (buttonCount - 1) + 12.dp
        } else {
            0.dp
        }
        // Never on this tier -- still under 150dp wide (COMPACT_TALL's own
        // gate only proves the HEIGHT is roomy), same reasoning as RailLayout
        // and CompactTallNarrowLayout's own hasMap.
        val hasMap = false
        val heroRoom = (budget - nameHeight - buttonZone - TALL_TIER_MARGIN).coerceAtLeast(0.dp)
        val split = Scale.tallSplit(size, heroRoom, capRows = 4, textScale = render.theme.textScale, wantMap = hasMap)
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = size.width - 8.dp, horizontalAlignment = Alignment.CenterHorizontally,
            )
            // Weighted only when there's no map to fill it, so whatever the
            // ring and rows don't claim still reads as centred rather than
            // piled at the top.
            if (!hasMap) Spacer(GlanceModifier.defaultWeight())
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = split.ring.value.toInt())
                Spacer(GlanceModifier.height(6.dp))
            } else {
                // The glyph every sibling tier falls back to (Rail, both Compact
                // Tall Narrow and Square, Micro, LargeTall). Without it this tier
                // showed a name, info rows and buttons and NO status iconography
                // at all whenever the ring was switched off or percent was
                // unknown -- while still subtracting MIN_HERO_RESERVE from the
                // button budget for a hero that never rendered.
                StatusGlyph(car, render.theme, sizeDp = split.ring.value.toInt())
            }
            if (split.rows > 0) InfoStack(car, render, max = split.rows)
            if (hasMap) {
                MapFill(render, split.map)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            if (buttonCount > 0) {
                Spacer(GlanceModifier.height(8.dp))
                ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
            }
        }
    }

    @Composable
    private fun MediumSquareLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as the LARGE/XL tiers' own clamp: the header +
        // button rows can leave less than the ring's continuous target size
        // at MEDIUM's own minimum height (150dp).
        val size = LocalSize.current
        val ringRoom = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, false, 16.dp)
        val ringEdge = Scale.ring(size, ringRoom)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            ChargeBarFallback(car, render, ringEdge, ringRoom)
            // NOT .defaultWeight() on the row/column itself -- ringEdge is
            // already sized from ringRoom, the exact leftover this row has,
            // so it doesn't need to be stretched to claim more. A weighted
            // row with real drawn content (the ring, the info text) shares
            // the same failure mode as a weighted MapFill ahead of fixed
            // content elsewhere in this file: on a real device the
            // ActionButtons row below it rendered nothing at all, not merely
            // squeezed.
            //
            // A bare, contentless Spacer(defaultWeight()) right after it is
            // the safe way to reclaim whatever's left, though -- the same
            // pattern RailLayout already uses around its own hero content,
            // proven not to starve the fixed buttons that follow it. Without
            // this, whenever the ring hits Scale.ring's own 140dp curve
            // ceiling well below what ringRoom actually budgeted for it, the
            // slack collected as unclaimed blank space at the very bottom of
            // the tile instead of here.
            if (render.config.showRing && car.percent != null) {
                // RingWithContent auto-stacks vertically instead of
                // squeezing ring+info into a cramped row if the tile's
                // actual measured width can't fit them side by side.
                RingWithContent(
                    modifier = GlanceModifier.fillMaxWidth(),
                    minRowWidth = 140.dp,
                    ringWidth = ringEdge,
                    ring = { RingImage(car, render, edgeDp = ringEdge.value.toInt()) },
                    // hideFields drops PERCENT: RingImage's own centerText
                    // already bakes "82%" into the ring beside this stack, so a
                    // user with Battery/Fuel selected saw the same number twice
                    // on one tile. The LARGE and XL tiers already carried this
                    // guard; the two MEDIUM tiers drawing the identical ring did
                    // not. Only this branch needs it -- the else below has no
                    // ring, so there the field is the only place it appears.
                    content = { w ->
                        InfoStack(
                            car, render, max = Scale.infoCap(size, 3, render.theme.textScale),
                            availableWidth = w, hideFields = setOf(WidgetInfoField.PERCENT),
                        )
                    },
                )
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    InfoStack(car, render, max = Scale.infoCap(size, 3, render.theme.textScale))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(8.dp))
            // availableHeight pinned to one row's worth -- ringRoom only ever
            // reserved buttonHeight(size) ONCE for this whole button block,
            // not a stacked count. Left at its default (the whole tile's
            // height), ActionButtons' own row-vs-stack capacity check
            // compared against far more room than was ever actually budgeted
            // for it, and could pick the stacked column by mistake for any
            // configuration wider than it is tall -- ballooning the button
            // block to many times the one row this tier was ever built for.
            // See ActionButtons' own capacity-check comment for the full
            // mechanism; confirmed by reconstructing it for a realistic
            // XL_TALL size with several actions configured, which overflowed
            // by hundreds of dp before this fix.
            ActionButtons(car, render, max = 4, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun MediumWideLayout(car: VehicleSnapshot, render: Render) {
        // Wide MEDIUM: put the ring beside the header/info/buttons stack
        // instead of above it, so a wide-but-short tile spends its extra
        // width on layout instead of leaving it empty beside a centered
        // ring -- RingWithContent falls back to stacking if that width
        // doesn't actually pan out.
        val size = LocalSize.current
        // Every child is handed the width this column actually gets, so the
        // header's name, the info rows, and the button row all judge their
        // own fit against the real space beside the ring rather than the
        // whole tile.
        val content: @Composable (Dp) -> Unit = { w ->
            HeaderRow(car, render, availableWidth = w)
            Spacer(GlanceModifier.height(6.dp))
            InfoStack(car, render, max = Scale.infoCap(size, 3, render.theme.textScale), availableWidth = w)
            Spacer(GlanceModifier.height(6.dp))
            ActionButtons(car, render, max = 4, availableWidth = w, availableHeight = Scale.buttonHeight(size))
        }
        val showsRing = render.config.showRing && car.percent != null
        // A wide tile spends its axis better on a bar than a circle: it runs
        // the full width under the header, and the column gets the whole
        // tile instead of what was left beside a circle. Always preferred
        // now, not just as a fallback once the ring shrinks below
        // RING_WORTH_IT -- same call BANNER and COMPACT_WIDE make.
        if (showsRing) {
            val w = size.width - Scale.contentPadding(size) * 2
            val barH = Scale.barHeight(size)
            // What the header, buttons and this layout's own spacers left,
            // minus the bar itself, is what the rows and the map split --
            // tallSplit reserves the map FIRST and hands rows whatever's
            // left, the same three-way division every tall tier trusts.
            // This used to hand the SAME leftover to both independently
            // (rows sized from the full room, then the map ALSO capped at
            // that same full room) -- fine whenever rows was 0, a real
            // double-booking once it wasn't, overflowing the tile by
            // however tall the info rows came out and pushing the button
            // row past the bottom of it.
            val room = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, false, 18.dp)
            val restAfterBar = (room - barH).coerceAtLeast(0.dp)
            val split = Scale.tallSplit(
                size, restAfterBar, capRows = 2, textScale = render.theme.textScale, wantMap = render.mapBitmap != null,
            )
            val rows = split.rows
            Column(modifier = GlanceModifier.fillMaxSize()) {
                HeaderRow(car, render, availableWidth = w)
                Spacer(GlanceModifier.height(6.dp))
                ChargeBar(car, render.theme, width = w, height = barH)
                if (rows > 0) {
                    Spacer(GlanceModifier.height(6.dp))
                    InfoStack(car, render, max = rows, availableWidth = w)
                }
                // A FIXED-height module, not a weighted MapFill -- a weighted
                // element here left ActionButtons with no real room at all on
                // a real device (buttons rendered nothing, not even clipped,
                // just absent), regardless of how much space was actually
                // left over. Capped at split.map, the room tallSplit actually
                // reserved for it once the rows above had theirs.
                MapModule(render, split.map)
                // Same availableHeight fix as MediumSquareLayout's own note
                // -- ringRoom only ever reserved one row's worth for this.
                ActionButtons(car, render, max = 4, availableWidth = w, availableHeight = Scale.buttonHeight(size))
            }
            return
        }
        // No ring to show at all (off, or no percent yet): the column gets
        // the whole tile, same shape as the bar branch above without a
        // gauge of any kind. Unbudgeted branch -- content() itself pins
        // ActionButtons' height too, for the same reason.
        Column(modifier = GlanceModifier.fillMaxSize()) { content(size.width) }
    }

    @Composable
    private fun MediumTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall MEDIUM: everything stacked in one column, ring centered --
        // the mirror of MediumWideLayout's side-by-side arrangement.
        val size = LocalSize.current
        // Hand the ring everything left after the header, buttons, info rows
        // and the map's reserve, instead of a fixed curve plus a trailing
        // void -- see Scale.tallSplit for why the map has to be taken out
        // before the ring is sized rather than after.
        val split = Scale.tallSplit(
            size,
            Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, false, 16.dp),
            capRows = Scale.infoCap(size, 3, render.theme.textScale),
            textScale = render.theme.textScale,
            wantMap = render.mapBitmap != null,
        )
        val rows = split.rows
        val ringEdge = split.ring
        // Whatever is left after the header, ring, stats and buttons goes to
        // the map when the user has one enabled and the car has coordinates.
        // MEDIUM is the smallest tier that shows one at all now: it used to
        // start at LARGE, so a 2x2 spent its entire remainder on two weighted
        // spacers -- dead space by construction. A weighted map takes exactly
        // the same room and puts the car's location in it, and collapses to
        // nothing when there's no bitmap, which is when the spacers are the
        // right answer again.
        val showsRing = render.config.showRing && car.percent != null
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            // The bar the gauge falls back to when tallSplit left room for a
            // gauge but not for a RING: Scale.ring yields 0 below MIN_RING (24dp)
            // rather than drawing a smudge, and StatusGlyph declines at 0 too, so
            // without this the tier drew no gauge whatsoever in that case -- a
            // bar needs 10-14dp where a ring needs 24. Draws only when ringEdge
            // came back 0, out of room the ring was budgeted and didn't use, so
            // it can't squeeze anything below it.
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
            if (showsRing) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.height(8.dp))
            } else {
                // The same fallback glyph every sibling tier carries.
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            // PERCENT is dropped only when the ring is actually drawn -- its
            // centerText bakes the number in, so showing the field too printed it
            // twice. In the glyph branch nothing else carries it, so it stays.
            InfoStack(
                car, render, max = rows,
                hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
            )
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note: a weighted element ahead of
            // ActionButtons left it with no real room on a real device,
            // buttons rendered absent rather than merely clipped. MapModule
            // already draws nothing when there's no bitmap.
            MapModule(render, split.map)
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = 4, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun LargeWideLayout(car: VehicleSnapshot, render: Render) {
        // A wide tile spends its axis better on a bar than a circle. The
        // earlier version of this fix kept the ring's old side-by-side
        // column shape and just drew a bar inside it -- which bounded the
        // bar to a fraction of the tile's WIDTH the same way the ring used
        // to be bounded by height, so the bar read as "unusually small"
        // sitting in a narrow column with empty space above and below it
        // (RingWithContent centres its row vertically; a short bar in a
        // tall weighted row leaves slack on both sides). Reported from a
        // real device. Rebuilt to run the bar the FULL width under the
        // header instead, the same shape MediumWideLayout's own bar branch
        // already uses -- no side column, no wasted vertical margin.
        val size = LocalSize.current
        val w = size.width - Scale.contentPadding(size) * 2
        val showsRing = render.config.showRing && car.percent != null
        // BarHero's own default budget assumes it's the row's only vertical
        // content, true for BannerLayout/CompactWideLayout but not here --
        // this column also carries a header, footer, info rows, a map and a
        // button row. ringRoom's own header/footer/button/spacer subtraction
        // gives the safe leftover; tallSplit then divides THAT between the
        // map, the info rows and whatever's left for the hero, the exact
        // division every other tall tier already trusts -- reused here for
        // its whole three-way split, not just its ring size.
        //
        // wantMap = true is the fix for a real overflow this tier had: the
        // map used to be handed its own full natural height with NOTHING
        // subtracted from the budget for it, on the theory that a fixed-
        // height MapModule was safe because it wasn't a weighted MapFill.
        // Fixed-height only avoids the "weighted element swallows a
        // sibling's room" failure mode -- it does nothing to stop the SUM of
        // everything in the column from exceeding the tile if the map's own
        // height was never subtracted from what the hero and rows above it
        // were sized against. Reported from a real device: a header, a bar,
        // a map, and then nothing -- the buttons had overflowed off the
        // bottom of the tile's own allocated bounds.
        //
        // spacers = 30.dp, not the single-spacer 20.dp another tier might
        // pass: this column has THREE explicit 10.dp Spacers below the
        // header (before the hero, after it, before the buttons), not one,
        // and ringRoom's own spacers argument is the caller's one chance to
        // tell it about all of them at once.
        val ringRoom = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, render.config.showFooter, 30.dp)
        val split = Scale.tallSplit(
            size, ringRoom, capRows = 4, textScale = render.theme.textScale, wantMap = render.mapBitmap != null,
        )
        val rows = split.rows
        val heroAvail = split.ring
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            if (showsRing) {
                BarHero(car, render, width = w, avail = heroAvail, showNameFallback = false)
                Spacer(GlanceModifier.height(10.dp))
            }
            if (rows > 0) {
                InfoStack(
                    car, render, max = rows, availableWidth = w, footerShown = true,
                    hideFields = setOf(WidgetInfoField.PERCENT),
                )
            }
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note -- capped at split.map, the room
            // tallSplit actually reserved for it, not the map's own ideal
            // height.
            MapModule(render, split.map)
            Spacer(GlanceModifier.height(10.dp))
            // Same availableHeight fix as MediumSquareLayout's own note --
            // ringRoom only ever reserved one row's worth for this block.
            ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun LargeSquareLayout(car: VehicleSnapshot, render: Render) {
        // Square LARGE: same ring-left / info-right split as LargeWideLayout,
        // but the map runs full-width below the row instead of being
        // squeezed inside the narrower info column -- the balanced
        // ring/square version of the Wide/Square/Tall split MEDIUM and XL
        // already have, giving LARGE its own third shape too.
        val size = LocalSize.current
        val ringRoom = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, render.config.showFooter, 20.dp)
        // Full-width map below the row, so here it competes with the ring for
        // the same column and has to be taken out of the ring's budget first.
        val mapRoom = Scale.mapReserve(size, ringRoom, render.mapBitmap != null)
        val ringEdge = Scale.ring(size, ringRoom - mapRoom)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            ChargeBarFallback(car, render, ringEdge, ringRoom - mapRoom)
            // NOT .defaultWeight() -- same fix as MediumSquareLayout's own
            // note: ringEdge is already sized from ringRoom - mapRoom, the
            // real leftover this row has, so it doesn't need to be stretched
            // to fill anything. A weighted row here is the same failure mode
            // as a weighted MapFill ahead of fixed content -- ActionButtons
            // below it rendered nothing at all on a real device, not merely
            // squeezed. Matched to XlSquareLayout's own RingWithContent call,
            // which was already unweighted for this exact reason.
            RingWithContent(
                modifier = GlanceModifier.fillMaxWidth(),
                minRowWidth = 220.dp,
                ringWidth = ringEdge,
                ring = {
                    if (render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    } else {
                        StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
                    }
                },
                // hideFields drops PERCENT: RingImage's own centerText already
                // bakes "82%" into the ring bitmap right beside this stack --
                // without this, a user with Battery/Fuel in their chosen info
                // fields saw that exact number twice on the same tile, the
                // same duplicate-content bug already guarded against for the
                // bar-hero tiers' own percent and XlTallLayout's primaryValue.
                content = { w ->
                    InfoStack(
                        car, render, max = Scale.infoCap(size, 4, render.theme.textScale), availableWidth = w,
                        footerShown = true, hideFields = setOf(WidgetInfoField.PERCENT),
                    )
                },
            )
            // Bare, contentless Spacer -- see MediumSquareLayout's own note:
            // reclaims whatever Scale.ring's 140dp curve ceiling left
            // unclaimed once a big enough tile budgeted more than that for
            // it, without risking the "weighted content starves a later
            // fixed sibling" failure a weighted RingWithContent had here.
            Spacer(GlanceModifier.defaultWeight())
            MapModule(render, mapRoom)
            Spacer(GlanceModifier.height(10.dp))
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun LargeTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall LARGE: ring centered full-width above the info stack instead
        // of beside it -- there's more height to spend than width here, so a
        // side-by-side split would leave the info column cramped.
        val size = LocalSize.current
        val split = Scale.tallSplit(
            size,
            Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, render.config.showFooter, 20.dp),
            capRows = Scale.infoCap(size, 4, render.theme.textScale),
            textScale = render.theme.textScale,
            wantMap = render.mapBitmap != null,
        )
        val ringEdge = split.ring
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            // Bar fallback for when tallSplit left room for a gauge but not for
            // a RING -- see MediumTallLayout's own note. Both branches below
            // decline at 0 (RingImage and StatusGlyph each early-return), so
            // without this the tier drew no gauge at all, which is reachable at
            // 1.4x text on a tile near LARGE's own 240x170 floor.
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            Spacer(GlanceModifier.height(10.dp))
            // hideFields drops PERCENT -- RingImage's own centerText above
            // already bakes "82%" into the ring, the same duplicate-content
            // guard LargeSquareLayout's own InfoStack call now carries too.
            InfoStack(car, render, max = split.rows, footerShown = true, hideFields = setOf(WidgetInfoField.PERCENT))
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note: a weighted element ahead of
            // ActionButtons left it with no real room on a real device.
            MapModule(render, split.map)
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun XlWideLayout(car: VehicleSnapshot, render: Render) {
        // Same rebuild as LargeWideLayout, same reasoning: the bar used to
        // live in a side column bounded to a fraction of the tile's width
        // (mirroring the ring it replaced), which read as an undersized bar
        // floating in a tall, mostly empty row. Runs the full width under
        // the header now instead, matching MediumWideLayout's own shape.
        val size = LocalSize.current
        val w = size.width - Scale.contentPadding(size) * 2
        val showsRing = render.config.showRing && car.percent != null
        // See LargeWideLayout's own note: BarHero's default budget assumes
        // it's the row's only content, which isn't true here either, and
        // tallSplit does the map/row/leftover three-way split instead of
        // handing the map its own full natural height with nothing
        // subtracted from what everything else was budgeted against --
        // wantMap = true is the fix for the exact overflow LargeWideLayout's
        // own note describes.
        // spacers = 42.dp: three explicit 14.dp Spacers below the header in
        // this column, same reasoning as LargeWideLayout's own note.
        val ringRoom = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, render.config.showFooter, 42.dp)
        val split = Scale.tallSplit(
            size, ringRoom, capRows = WidgetInfoField.ALL.size, textScale = render.theme.textScale,
            wantMap = render.mapBitmap != null,
        )
        val rows = split.rows
        val heroAvail = split.ring
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(14.dp))
            if (showsRing) {
                BarHero(car, render, width = w, avail = heroAvail, showNameFallback = false)
                Spacer(GlanceModifier.height(14.dp))
            }
            if (rows > 0) {
                InfoStack(
                    car, render, max = rows, availableWidth = w, footerShown = true,
                    hideFields = setOf(WidgetInfoField.PERCENT),
                )
            }
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note -- capped at split.map, not the
            // map's own ideal height.
            MapModule(render, split.map)
            Spacer(GlanceModifier.height(14.dp))
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun XlTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall XL: one big centered ring up top with the primary value under
        // it, the full info stack and map stacked below rather than split
        // into side-by-side columns that would squeeze on a narrow-but-tall
        // dashboard-sized tile.
        val size = LocalSize.current
        // The primaryValue line under the ring ("69% · 219 mi") is real,
        // known-size content that was never subtracted from the budget
        // tallSplit divides between the ring, the info rows and the map --
        // ringRoom's own spacers argument only ever covered the fixed
        // Spacer()s in this column (14 + 8 + 14 = 36.dp), not the text line
        // sitting between two of them. Undercounting it meant ring + rows +
        // map could together claim EVERYTHING ringRoom reported free, this
        // line's own height included, overflowing the tile by however tall
        // it rendered -- confirmed by rebuilding this arithmetic outside the
        // codebase: up to 46dp on a real XL_TALL size, enough to push the
        // map and every button off the bottom of the tile's own bounds.
        val primaryValueHeight = Scale.lineHeight(Scale.titleSp(size).value, render.theme.textScale)
        val split = Scale.tallSplit(
            size,
            Scale.ringRoom(
                size, render.theme.textScale, render.config.showHeader, render.config.showFooter,
                36.dp + primaryValueHeight,
            ),
            capRows = Scale.infoCap(size, WidgetInfoField.ALL.size, render.theme.textScale),
            textScale = render.theme.textScale,
            wantMap = render.mapBitmap != null,
        )
        val ringEdge = split.ring
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(14.dp))
            // Same bar fallback as LargeTallLayout / MediumTallLayout: this tier
            // reserves a whole extra text line (primaryValueHeight) on top of
            // header, footer and buttons, so it runs out of ring room sooner
            // than its size suggests.
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            Spacer(GlanceModifier.height(8.dp))
            FitText(
                primaryValue(car, render), titleStyle(render.theme),
                maxWidth = size.width - 24.dp, horizontalAlignment = Alignment.CenterHorizontally,
            )
            Spacer(GlanceModifier.height(14.dp))
            InfoStack(
                car, render, max = split.rows, footerShown = true,
                hideFields = setOf(WidgetInfoField.RANGE, WidgetInfoField.PERCENT),
            )
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note: a weighted element ahead of
            // ActionButtons left it with no real room on a real device.
            MapModule(render, split.map)
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
        }
    }

    @Composable
    private fun XlSquareLayout(car: VehicleSnapshot, render: Render) {
        // Square XL: a balanced ring-left / info-right split above a full-
        // width map, distinct from XlWideLayout's value-under-ring emphasis
        // and XlTallLayout's fully stacked column.
        val size = LocalSize.current
        val ringRoom = Scale.ringRoom(size, render.theme.textScale, render.config.showHeader, render.config.showFooter, 24.dp)
        // Full-width map below the row, competing with the ring for the same
        // column -- reserved first, as in LargeSquareLayout.
        val mapRoom = Scale.mapReserve(size, ringRoom, render.mapBitmap != null)
        val ringEdge = Scale.ring(size, ringRoom - mapRoom)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(12.dp))
            ChargeBarFallback(car, render, ringEdge, ringRoom - mapRoom)
            RingWithContent(
                modifier = GlanceModifier.fillMaxWidth(),
                minRowWidth = 260.dp,
                ringWidth = ringEdge,
                ring = {
                    if (render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    } else {
                        StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
                    }
                },
                // hideFields drops PERCENT -- see LargeSquareLayout's own
                // note: RingImage's centerText already bakes it into the ring.
                content = { w ->
                    InfoStack(
                        car, render, max = Scale.infoCap(size, 4, render.theme.textScale), availableWidth = w,
                        footerShown = true, hideFields = setOf(WidgetInfoField.PERCENT),
                    )
                },
            )
            // Bare, contentless Spacer -- see LargeSquareLayout's own note:
            // reclaims whatever Scale.ring's 140dp curve ceiling left
            // unclaimed on a big XL_SQUARE tile without risking a weighted
            // RingWithContent starving the fixed content after it. XL_SQUARE
            // has no upper size bound, so this gap grows without one too --
            // confirmed up to 166dp of unclaimed space at 600x600 before
            // this fix, versus none once this spacer can claim it instead.
            Spacer(GlanceModifier.defaultWeight())
            // A FIXED-height module, not a weighted MapFill -- see
            // MediumWideLayout's own note.
            MapModule(render, mapRoom)
            // Same availableHeight fix as MediumSquareLayout's own note.
            ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
        }
    }

    // ---- Modules -------------------------------------------------------------

    @Composable
    private fun HeaderRow(
        car: VehicleSnapshot, render: Render,
        // Same reasoning as InfoStack's own availableWidth -- the header
        // sits inside RingWithContent's narrower column at the wide MEDIUM
        // tier, not across the whole tile.
        availableWidth: Dp = LocalSize.current.width,
    ) {
        // Gated here rather than at each of the dozen call sites, so the
        // option can't be honoured by some tiers and quietly ignored by
        // others as layouts get added.
        if (!render.config.showHeader) return
        // Rough reserve for the car-switcher pill (its own size plus
        // spacing) when it's present -- an estimate, same spirit as every
        // other maxWidth passed to FitText in this file (see wouldOverflow).
        val pillReserve = if (render.multiCar && render.config.vin == null) {
            Scale.pillSize(LocalSize.current) + 8.dp
        } else 0.dp
        val textWidth = (availableWidth - pillReserve - 4.dp).coerceAtLeast(16.dp)
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(openAction(LocalContext.current)), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                FitText(car.name, titleStyle(render.theme), maxWidth = textWidth)
                FitText(statusSubtitle(car), subtitleStyle(render.theme), maxWidth = textWidth)
            }
            if (render.multiCar && render.config.vin == null) {
                // Follow-selected widgets get a car switcher chevron.
                IconPill(
                    iconRes = R.drawable.ic_shortcut_car,
                    onClick = actionRunCallback<WidgetSwitchCarAction>(),
                    theme = render.theme,
                )
            }
        }
    }

    @Composable
    private fun FooterRow(car: VehicleSnapshot, render: Render) {
        if (!render.config.showFooter) return
        val updated = relativeLabel(car.fetchedAt.takeIf { it > 0 })
        if (updated.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            // Stale data gets an amber "· may be out of date" tail so an hours-old
            // lock/charge state can't masquerade as live. Tap the footer to refresh.
            val style = if (render.stale)
                TextStyle(color = ColorProvider(Color(BlooColors.warn)), fontSize = 11.sp)
            else subtitleStyle(render.theme)
            val text = if (render.stale) "Updated $updated · may be stale" else "Updated $updated"
            FitText(
                text,
                style,
                maxWidth = LocalSize.current.width - 8.dp,
                modifier = GlanceModifier.clickable(
                    actionRunCallback<WidgetRefreshAction>(actionParametersOf(WidgetKeys.VIN to car.vin)),
                ),
            )
        }
    }

    /** The location map thumbnail, shown only when the pre-fetched bitmap exists
     *  (config.showMap on + car has coords + tile fetched OK). Rounded corners to
     *  match the widget's card language. */
    /**
     * Fills a tall layout's leftover vertical space with the location map.
     *
     * Every big tier used to end with a bare `Spacer(defaultWeight())`, which
     * by definition collects ALL the slack in one place -- on a 600x520dp tile
     * that was a black gap taller than the content above it, with the buttons
     * shoved against the bottom edge. Reported from a real device.
     *
     * The map is exactly what belongs there: it's the one piece of content
     * that genuinely wants more room the more room there is, so it takes the
     * weight instead of a spacer and grows with the widget. Without
     * coordinates (or with the map switched off) it falls back to the spacer,
     * because leaving a gap is still better than stretching something that
     * was never meant to fill.
     */
    // Extension on ColumnScope, NOT a plain composable: defaultWeight() is
    // declared INSIDE Row/ColumnScope rather than on GlanceModifier itself, so
    // it only resolves where that receiver is in scope. Every other use in this
    // file happens to sit directly inside a Column lambda and gets it for free;
    // pulling this out into its own function is what surfaced that.
    /** The map as a weighted module, taking the slack its caller reserved
     *  for it ([room]) -- which is 0 when the caller worked out there wasn't
     *  enough of it to be a map, and then this is a plain spacer again. The
     *  weight is what actually sizes it; [room] is how the caller and this
     *  function agree on whether it is drawn at all. */
    @Composable
    private fun ColumnScope.MapFill(render: Render, room: Dp) {
        val bmp = render.mapBitmap
        if (bmp == null || room <= 0.dp) {
            Spacer(GlanceModifier.defaultWeight())
            return
        }
        Spacer(GlanceModifier.height(8.dp))
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "Car location",
            contentScale = ContentScale.Crop,
            // Tappable, like every other part of this widget. A map of where
            // the car is invites a tap more than anything else on the tile,
            // and it was the one large region that did nothing.
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                .cornerRadius(innerCorner(render.config))
                .clickable(openAction(LocalContext.current)),
        )
        Spacer(GlanceModifier.height(8.dp))
    }

    /** The map at a fixed height, for the tiers that place it among
     *  fixed-height siblings. [room] is what the caller has actually got
     *  left for it -- the height used to be [Scale.mapHeight] unconditionally,
     *  which on a cramped square tile was more than the whole column had
     *  left and pushed the ring beside it to nothing.
     *
     *  [room] is the TOTAL this module may spend, leading Spacer included --
     *  the image's own height is [room] minus that spacer, not [room]
     *  itself. This used to hand the Image the whole of [room] as its own
     *  cap, on top of the Spacer drawn right before it, so whenever the
     *  caller's own budget was the binding constraint (not [Scale.mapHeight]),
     *  the module consumed exactly 8dp more than it was ever given -- the
     *  same silent overflow class fixed everywhere else in this file, just
     *  reintroduced by a spacer the room math forgot to subtract. Confirmed
     *  by rebuilding [Scale.mapReserve]/[Scale.ringRoom] for real XL_WIDE
     *  sizes: the overflow reproduces to exactly 8dp whenever the map's own
     *  budget is capped below its ideal height, not just at one contrived
     *  point. */
    @Composable
    private fun MapModule(render: Render, room: Dp) {
        val bmp = render.mapBitmap ?: return
        // The MIN check is against the image's own height, not room itself
        // -- room includes the leading spacer, and a map judged "worth
        // drawing" has to mean the picture itself clears the floor, not the
        // spacer padding it out to look like it does.
        val imageHeight = minOf(Scale.mapHeight(LocalSize.current), (room - 8.dp).coerceAtLeast(0.dp))
        if (imageHeight < Scale.MAP_MIN) return
        Spacer(GlanceModifier.height(8.dp))
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "Car location",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxWidth()
                .height(imageHeight)
                .cornerRadius(innerCorner(render.config))
                .clickable(openAction(LocalContext.current)),
        )
    }

    /** [maxWidth] is passed in rather than derived from the tile, because
     *  this line's caller is the only thing that knows how much of the row
     *  it actually owns -- the old hard-coded "36% of the tile" was a
     *  CompactWide-specific guess baked into a shared module. */
    @Composable
    private fun PrimaryInfoLine(car: VehicleSnapshot, render: Render, maxWidth: Dp) {
        FitText(primaryValue(car, render), subtitleStyle(render.theme), maxWidth = maxWidth)
    }

    /**
     * The ordinary text pair -- the car's name over its primary stat -- with
     * the stat dropped when the row can't hold both lines.
     *
     * The pair was previously written out at each call site as two unguarded
     * [FitText]s. FitText budgets WIDTH only; nothing was checking that two
     * stacked lines fit the tile's HEIGHT, so on a 640x40 strip at the 1.4x
     * text size they overran it by 9.8dp -- and RemoteViews renders that as a
     * line bleeding past the bottom edge, not as a clip. The name always fits
     * on its own (both it and the budget are driven by the short side), so
     * the stat is the only thing this has to decide about.
     */
    @Composable
    private fun NameAndStat(car: VehicleSnapshot, render: Render, width: Dp) {
        val size = LocalSize.current
        val scale = render.theme.textScale
        val avail = size.height - Scale.contentPadding(size) * 2
        FitText(car.name, titleStyle(render.theme), maxWidth = width)
        val both = Scale.lineHeight(Scale.titleSp(size).value, scale) +
            Scale.lineHeight(Scale.subtitleSp(size).value, scale)
        if (both <= avail) PrimaryInfoLine(car, render, maxWidth = width)
    }

    /** The stacked read-only stats, honoring the user's chosen fields + order,
     *  capped to what fits ([max]). Glance has no overflow-detection callback
     *  the way real Compose Text does (RemoteViews just silently ellipsizes),
     *  so "might not fit" is decided ahead of time from the measured tile
     *  width instead of reactively -- below [NARROW_WIDTH] every row drops
     *  the label beside its value in favour of stacking the value on its own
     *  full-width line underneath, and [FitText] falls back further to one
     *  character per line (see [VerticalText]) for either one if even that's
     *  tight, so a reading like "82%" degrades to
     *
     *  8
     *  2
     *  %
     *
     *  rather than ever being cut off. */
    @Composable
    private fun InfoStack(
        car: VehicleSnapshot, render: Render, max: Int,
        // The width this stack's own column actually gets, which is NOT the
        // tile width whenever it sits beside a ring (see RingWithContent) --
        // measuring against the whole tile there would let a label clear the
        // wrap threshold on paper and still be clipped in a column half that
        // wide, which is exactly the failure this whole FitText path exists
        // to prevent.
        availableWidth: Dp = LocalSize.current.width,
        // True on tiers that also render FooterRow, which already reads
        // "Updated 9 min ago". Without this the same sentence appeared twice
        // on one widget -- once as an info row, once as the footer directly
        // beneath the buttons. Reported from a real device.
        footerShown: Boolean = false,
        // Non-empty on tiers whose hero already shows one or both of these
        // numbers itself -- XlTallLayout's primaryValue() headline reads
        // "69% · 361 mi" (both), the wide tiers' bar hero shows just "69%".
        // Without this, a user with Percent or Range in their chosen info
        // fields saw that exact number a second time, right below the
        // first, on the same tile. Reported from a real device screenshot.
        hideFields: Set<WidgetInfoField> = emptySet(),
    ) {
        val fields = render.config.infoFields
            .mapNotNull { WidgetInfoField.fromKey(it) }
            .filterNot { footerShown && it == WidgetInfoField.UPDATED }
            .filterNot { it in hideFields }
            .take(max)
        val narrow = availableWidth < NARROW_WIDTH
        // Two columns once the slot is wide enough for both halves to still
        // clear the narrow threshold. A single column on a genuinely wide
        // tier stacked six stats down a strip while the space beside them sat
        // empty, and made the block tall enough to crowd the ring and map it
        // shares a column with. Paired, the same stats read in half the
        // height, which is what the big tiers actually needed.
        val columnGap = 12.dp
        val paired = !narrow && fields.size > 2 &&
            (availableWidth - columnGap) / 2 >= NARROW_WIDTH + 20.dp
        if (paired) {
            val cellWidth = (availableWidth - columnGap) / 2
            Column {
                fields.chunked(2).forEach { pair ->
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        pair.forEachIndexed { i, field ->
                            if (i > 0) Spacer(GlanceModifier.width(columnGap))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                InfoRow(field, car, render, cellWidth)
                            }
                        }
                        // An odd count leaves the last row half-full; the empty
                        // half holds its place so the pair above stays aligned
                        // rather than the lone stat stretching across.
                        if (pair.size == 1) {
                            Spacer(GlanceModifier.width(columnGap))
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                    Spacer(GlanceModifier.height(2.dp))
                }
            }
            return
        }
        Column {
            fields.forEach { field ->
                val value = infoValue(field, car, render) ?: return@forEach
                if (narrow) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        FitText(fieldLabel(field, car), subtitleStyle(render.theme), maxWidth = availableWidth - 4.dp)
                        FitText(value, valueStyle(render.theme), maxWidth = availableWidth - 4.dp)
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        FitText(
                            fieldLabel(field, car), subtitleStyle(render.theme),
                            maxWidth = availableWidth * 0.5f, modifier = GlanceModifier.defaultWeight(),
                        )
                        FitText(value, valueStyle(render.theme), maxWidth = availableWidth * 0.45f)
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }

    /** One label/value stat, in whatever width its cell actually has. Shared
     *  by [InfoStack]'s single-column and paired layouts so the two can't
     *  drift in how they wrap or when they fall back to stacking. */
    @Composable
    private fun InfoRow(field: WidgetInfoField, car: VehicleSnapshot, render: Render, width: Dp) {
        val value = infoValue(field, car, render) ?: return
        if (width < NARROW_WIDTH) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                FitText(fieldLabel(field, car), subtitleStyle(render.theme), maxWidth = width - 4.dp)
                FitText(value, valueStyle(render.theme), maxWidth = width - 4.dp)
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                FitText(
                    fieldLabel(field, car), subtitleStyle(render.theme),
                    maxWidth = width * 0.5f, modifier = GlanceModifier.defaultWeight(),
                )
                FitText(value, valueStyle(render.theme), maxWidth = width * 0.45f)
            }
        }
    }

    /** Renders [text] one character per line, centered -- the LAST-resort
     *  fallback for a SHORT unsplittable token (a percent, mainly) that's
     *  still too wide for its line even on its own, e.g. "82%" becoming
     *  "8" / "2" / "%".
     *
     *  Deliberately last: stacking trades horizontal overflow for vertical
     *  extent, and a long token stacked this way (a 16-character name with
     *  no spaces) is 16 rows tall, which overflows a small tile's HEIGHT
     *  just as badly as the clipping it was avoiding. [FitText] therefore
     *  tries [wordWrap] and then [shrunkToFit] first, both of which keep
     *  text on ordinary horizontal lines, and only lands here for tokens
     *  short enough that the resulting stack stays small. */
    @Composable
    private fun VerticalText(text: String, style: TextStyle) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxWidth()) {
            text.forEach { ch -> if (!ch.isWhitespace()) Text(ch.toString(), maxLines = 1, style = style) }
        }
    }

    /** Average glyph width as a fraction of the font size, the one constant
     *  behind both [wouldOverflow] and its inverse in [shrunkToFit] -- they
     *  share it so the "does this fit" test and the "what size would fit"
     *  solve can never drift apart and disagree. Bold type sets measurably
     *  wider than regular at the same size, and every use of this estimate
     *  should err toward "won't fit" rather than let a title clip, so bold
     *  gets its own wider ratio instead of one average for everything. */
    private fun glyphRatio(style: TextStyle): Float =
        if (style.fontWeight == FontWeight.Bold) 0.64f else 0.6f

    /** Rough estimate of whether [text] in [style] would overflow
     *  [maxWidth] -- Glance/RemoteViews has no real text-measurement
     *  callback the way Compose's own `onTextLayout` does, so this is
     *  deliberately conservative rather than exact; used only to decide
     *  ahead of time which rung of [FitText]'s fallback chain to take,
     *  never to lay out pixel-perfect. */
    private fun wouldOverflow(text: String, style: TextStyle, maxWidth: Dp): Boolean {
        val sp = style.fontSize?.value ?: 12f
        return (text.length * sp * glyphRatio(style)) > maxWidth.value
    }

    /** Breaks [text] across lines at word boundaries (never mid-word), so a
     *  name like "Lana's Whip Deluxe" reads as real words per line rather
     *  than being dumped one letter per row.
     *
     *  Always returns at least one line and never gives up: a word too wide
     *  to fit becomes its own line and is handed to [FitLine], which shrinks
     *  or stacks just that word. This used to bail out entirely if ANY single
     *  word was too wide, which meant one long word dragged the whole string
     *  down to letter-stacking -- "Locked · Charging · Climate on" became a
     *  25-row column in a narrow slot purely because "Charging" alone didn't
     *  fit, even though every other word wrapped fine. */
    private fun wordWrap(text: String, style: TextStyle, maxWidth: Dp): List<String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var line = words.first()
        for (word in words.drop(1)) {
            val candidate = "$line $word"
            if (wouldOverflow(candidate, style, maxWidth)) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        lines += line
        return lines
    }

    /** The comfortable floors [shrunkToFit] won't shrink past: no smaller
     *  than 78% of the style's own size (so a shrunk line still reads as the
     *  same typographic step as its neighbours rather than a different one),
     *  and never below 9sp outright, which is about where widget text stops
     *  being legible at arm's length. */
    private val MIN_FONT_SCALE = 0.78f
    private val MIN_FONT_SP = 9f

    /** The floor when [FitText] has exhausted every better option and the
     *  only remaining choice is small-but-whole versus clipped. Below this
     *  nothing renders meaningfully at all, so there is nothing to gain by
     *  going further. */
    private val ABSOLUTE_MIN_SP = 5f

    /** How much of the available width [shrunkToFit] actually aims to fill.
     *  The small remainder is deliberate slack so a shrunk line lands inside
     *  its slot rather than exactly on its edge. */
    private val FIT_SLACK = 0.96f

    /** Longest token still worth stacking one character per row. A percent
     *  or a short code stacks to a handful of rows and stays readable; past
     *  this the column grows taller than the tile it is supposed to fit
     *  inside, which is just clipping again on the other axis. */
    private val MAX_STACK_CHARS = 6

    /** [FitText]'s second-choice fallback, for a single token that [wordWrap]
     *  can't help with (no spaces to break on): shrink the type just enough
     *  that it fits [maxWidth] on one ordinary line. Preferred over
     *  [VerticalText] because a slightly smaller word still reads as a word,
     *  where a stack of single letters reads as a puzzle -- and because
     *  stacking grows downward without bound, which is its own overflow.
     *
     *  [relaxed] drops the comfortable floors for [ABSOLUTE_MIN_SP], used
     *  only once stacking has been ruled out too. Returns null when even
     *  that won't fit. */
    private fun shrunkToFit(
        text: String, style: TextStyle, maxWidth: Dp, relaxed: Boolean = false,
    ): TextStyle? {
        val sp = style.fontSize?.value ?: return null
        if (text.isEmpty()) return null
        // Inverse of wouldOverflow's own estimate, sharing the same ratio:
        // the font size at which this string would exactly fill maxWidth.
        // Inverse of wouldOverflow, but aimed at [FIT_SLACK] of the width
        // rather than all of it. Solving for the size that fills maxWidth
        // EXACTLY leaves the result sitting right on the boundary, where
        // rounding and the estimate's own imprecision can tip it a hair over
        // and clip it -- the one thing this whole path exists to prevent.
        val needed = (maxWidth.value * FIT_SLACK) / (text.length * glyphRatio(style))
        if (needed >= sp) return style
        val floor = if (relaxed) ABSOLUTE_MIN_SP else maxOf(sp * MIN_FONT_SCALE, MIN_FONT_SP)
        if (needed < floor) return null
        return style.copy(fontSize = needed.sp)
    }

    /** The shared "never cut off" contract every user-data label, value and
     *  name in the widget goes through, generalized from what used to be a
     *  one-off fallback just for [WidgetInfoField.PERCENT].
     *
     *  Two stages. First [wordWrap] breaks the string into lines at word
     *  boundaries; then every resulting line independently goes through
     *  [FitLine], which owns the per-line fallbacks. Splitting it this way
     *  is what lets a single over-wide word shrink on its own without
     *  dragging the rest of the string down with it.
     *
     *  Every stage keeps the whole string on screen -- what changes is only
     *  how readable the result is, so the chain always takes the
     *  least-damaging option that actually fits. */
    @Composable
    private fun FitText(
        text: String,
        style: TextStyle,
        maxWidth: Dp,
        modifier: GlanceModifier = GlanceModifier,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    ) {
        if (text.isBlank()) return
        if (!wouldOverflow(text, style, maxWidth)) {
            Text(text, style = style, maxLines = 1, modifier = modifier)
            return
        }
        val lines = wordWrap(text, style, maxWidth)
        if (lines.size <= 1) {
            FitLine(lines.firstOrNull() ?: text, style, maxWidth, modifier, horizontalAlignment)
            return
        }
        Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
            lines.forEach { line ->
                FitLine(line, style, maxWidth, GlanceModifier, horizontalAlignment)
            }
        }
    }

    /** Renders ONE already-wrapped line, which by definition has no word
     *  break left to exploit, so the remaining options are all about size:
     *
     *  1. it fits [maxWidth] as-is, so render it;
     *  2. [shrunkToFit] shrinks the type just enough, staying legible;
     *  3. for a token short enough to stay a few rows tall, [VerticalText]
     *     stacks it one character per row;
     *  4. otherwise shrink past the comfortable floor, because a long stack
     *     would overflow the tile's height and that is just clipping again
     *     on the other axis. */
    @Composable
    private fun FitLine(
        text: String,
        style: TextStyle,
        maxWidth: Dp,
        modifier: GlanceModifier,
        horizontalAlignment: Alignment.Horizontal,
    ) {
        if (!wouldOverflow(text, style, maxWidth)) {
            Text(text, style = style, maxLines = 1, modifier = modifier)
            return
        }
        val shrunk = shrunkToFit(text, style, maxWidth)
        if (shrunk != null) {
            Text(text, style = shrunk, maxLines = 1, modifier = modifier)
            return
        }
        if (text.length > MAX_STACK_CHARS) {
            // Text too small to read is recoverable by resizing the widget;
            // text cut off the edge is not.
            val forced = shrunkToFit(text, style, maxWidth, relaxed = true)
            if (forced != null) {
                Text(text, style = forced, maxLines = 1, modifier = modifier)
                return
            }
        }
        Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
            VerticalText(text, style)
        }
    }

    /** The user's configured actions, filtered down to what this car's brand
     *  actually supports and capped to [max] -- the shared resolution behind
     *  both [ActionButtons] and the MICRO tier's single-button controls mode.
     *  Kia's US API (and the Canada backend) has no flash/horn endpoint --
     *  com.bloo.bluelink.data.Brand.fromIndicator(car.brandIndicator) is the
     *  same lookup Vehicle.supportsHornLights uses on the phone. Without this,
     *  a Kia user who'd configured Flash/Horn got a button that silently did
     *  nothing on every tap (WearCommandRunner routes it to KiaRepository's
     *  default no-op flashLights/hornAndLights). */
    private fun resolvedActions(car: VehicleSnapshot, render: Render, max: Int): List<WidgetAction> {
        val hornLightsSupported = com.bloo.bluelink.data.Brand.fromIndicator(car.brandIndicator)
            .let { it != com.bloo.bluelink.data.Brand.KIA && !it.isCanada }
        return render.config.actions.mapNotNull { WidgetAction.fromKey(it) }
            .filter { it != WidgetAction.CHARGE || car.hasBattery } // hide Charge on non-EV
            .filter { (it != WidgetAction.FLASH && it != WidgetAction.HORN) || hornLightsSupported }
            .take(max)
    }

    /** The configured action buttons, capped to [max] for the current size.
     *  [vertical] stacks them in a column instead of a row -- used by the
     *  tall/narrow compact tier so a controls-priority widget gets real
     *  finger-sized buttons instead of squeezing several side by side into a
     *  too-narrow strip. */
    @Composable
    private fun ActionButtons(
        car: VehicleSnapshot, render: Render, max: Int,
        vertical: Boolean = false, modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
        // How much width this row actually has to work with -- defaults to
        // the whole tile, but a caller where ActionButtons is a weighted
        // sibling (sharing a Row with a ring/text column) knows its own
        // slice is narrower than that and should say so.
        availableWidth: Dp = LocalSize.current.width,
        // The height this row/column actually has. Needed for the same reason
        // as availableWidth -- see the capacity note below.
        availableHeight: Dp = LocalSize.current.height -
            Scale.contentPadding(LocalSize.current) * 2,
    ) {
        val size = LocalSize.current
        val all = resolvedActions(car, render, max)
        if (all.isEmpty()) return
        // How many buttons ACTUALLY fit each way, rather than only asking
        // whether a row is too tight.
        //
        // The previous rule escalated to a vertical stack whenever a row
        // couldn't give every button a legible width -- which fixed the
        // horizontal squeeze by overflowing vertically instead. On a 300x78dp
        // banner with four actions configured, it stacked them into a ~146dp
        // column inside a 78dp widget: two buttons visible, the rest clipped
        // off the bottom. Reported from a real device.
        //
        // Now both axes get a capacity, and whichever orientation can show
        // the whole set wins; if neither can, the set is TRUNCATED to what
        // fits rather than drawn past the edge. Showing three of four buttons
        // is a real cost, but it's an honest one -- the alternative was
        // drawing four and letting the launcher clip two of them.
        // DENSITY over dropping controls. Both of these scale with the tile
        // rather than being flat, because a small widget's job is to show all
        // its buttons, not a tidy subset: at a flat 40dp minimum a 300x78
        // banner fit three of four actions and silently lost one. A 20dp
        // button on a tile that size is small, but it's a deliberate trade --
        // and still a real target, whereas a missing button can't be pressed.
        val gap = Scale.buttonGap(size)
        val minButtonWidth = Scale.minButtonWidth(size)
        val rowCapacity = ((availableWidth + gap) / (minButtonWidth + gap)).toInt()
        val colCapacity = ((availableHeight + gap) / (Scale.buttonHeight(size) + gap)).toInt()
        val stack = when {
            // An explicit request still has to fit; it just gets first refusal.
            vertical -> true
            rowCapacity >= all.size -> false
            colCapacity >= all.size -> true
            // Neither fits everything: prefer whichever shows more.
            else -> colCapacity > rowCapacity
        }
        val actions = all.take((if (stack) colCapacity else rowCapacity).coerceAtLeast(1))
        // Stretched to fill the WHOLE reserved zone in stack mode, not
        // sized to Scale.buttonHeight and centred inside it -- see the
        // Column branch below for why that room is real, deliberately
        // budgeted space rather than a leftover guess.
        val stackHeight = ((availableHeight - gap * (actions.size - 1)) / actions.size).coerceAtLeast(16.dp)
        val rowHeight = Scale.buttonHeight(size)
        // Labels are all-or-nothing across the row: measured against the
        // LONGEST label present, so the widest one setting cleanly is the
        // condition for any of them appearing.
        val perButton = if (stack) availableWidth
            else ((availableWidth - gap * (actions.size - 1)) / actions.size).coerceAtLeast(0.dp)
        val labelStyle = buttonLabelStyle(render.theme)
        val labelRoom = perButton - (Scale.buttonIcon(size) + 14.dp)
        val showLabels = (if (stack) stackHeight else rowHeight) >= 36.dp &&
            actions.all { !wouldOverflow(it.label, labelStyle, labelRoom) }
        // Centred both ways. When a layout hands ActionButtons the whole tile
        // -- the controls-priority tiers, where the buttons ARE the widget --
        // the block belongs in the middle of it, not against the top-left.
        if (stack) {
            // stackHeight (computed above) stretches every button to fill
            // the WHOLE reserved zone, not Scale.buttonHeight centred inside
            // it. Every caller that stacks buttons already reserves
            // availableHeight specifically FOR them (RailLayout/
            // CompactTallNarrowLayout/CompactTallLayout and their controls-
            // priority branches all pass it explicitly alongside
            // vertical = true) -- so this is real, deliberately budgeted
            // room, not a leftover guess. Buttons used to sit at a small
            // fixed height with the extra room showing as bare photo above
            // and below the stack even on a tile with a generous button
            // zone. Dividing that same zone evenly instead means two buttons
            // in a tall RAIL fill it edge to edge; six buttons in the same
            // zone still divide it evenly, just thinner each.
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.height(gap))
                    // Stacked: each button spans the full slice, so it's the
                    // orientation most likely to have room for a label.
                    ActionButton(
                        action, car, render,
                        modifier = GlanceModifier.fillMaxWidth(),
                        heightOverride = stackHeight,
                        showLabel = showLabels,
                    )
                }
            }
        } else {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                actions.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.width(gap))
                    ActionButton(
                        action, car, render,
                        modifier = GlanceModifier.defaultWeight(),
                        showLabel = showLabels,
                    )
                }
            }
        }
    }

    /**
     * A status ring/glyph beside a block of other content when there's
     * width to spare, or the ring stacked on top of that content when
     * there isn't -- the shared "should this go vertical instead" decision
     * for every tier that pairs the ring with something else (an info
     * stack, a header + buttons column, ...), so it's one reusable building
     * block instead of a hand-picked Row hard-coded per tier. [minRowWidth]
     * is how much width THIS specific pairing needs to read comfortably
     * side by side; below it, a vertical stack keeps both pieces legible
     * instead of squeezing them into a cramped row.
     *
     * [content] is handed the width its own slot ACTUALLY gets -- which in
     * the side-by-side case is only what's left after the ring, not the
     * whole tile. Everything inside it (info rows, a header, buttons) needs
     * that real number to decide whether its own text/buttons fit, because
     * measuring against the full tile width would let a label sail past
     * the wrap threshold and get clipped in a column half that wide.
     */
    @Composable
    private fun RingWithContent(
        modifier: GlanceModifier,
        minRowWidth: Dp,
        ringWidth: Dp,
        ring: @Composable () -> Unit,
        content: @Composable (Dp) -> Unit,
    ) {
        val tileWidth = LocalSize.current.width
        if (tileWidth >= minRowWidth) {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) { ring() }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    content((tileWidth - ringWidth - 12.dp).coerceAtLeast(24.dp))
                }
            }
        } else {
            Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                ring()
                Spacer(GlanceModifier.height(8.dp))
                content(tileWidth)
            }
        }
    }

    @Composable
    private fun ActionButton(
        action: WidgetAction, car: VehicleSnapshot, render: Render, modifier: GlanceModifier,
        // False only for the MICRO tier's single-button controls mode, where
        // the caller's own fillMaxSize() modifier should decide the button's
        // size instead of the usual fixed row/column height.
        fixedHeight: Boolean = true,
        // Overrides Scale.buttonHeight when set -- ActionButtons' own stack
        // branch uses this to stretch every button to fill its whole
        // reserved zone rather than sitting at the flat continuous-scale
        // height with the extra room left as bare photo around the stack.
        heightOverride: Dp? = null,
        iconSize: Dp = Scale.buttonIcon(LocalSize.current),
        // Whether to name the action beside its icon. Decided by the CALLER
        // for the whole row at once, never per button: labels have different
        // lengths, so a per-button test would label "Lock" and "Horn" while
        // leaving "Climate" and "Charge" as bare glyphs in the same row. All
        // or none is the only version that reads as designed.
        showLabel: Boolean = false,
    ) {
        val theme = render.theme
        val size = LocalSize.current
        // Every button defaults to the branded accent fill -- the "chunky, colored
        // action button" look is Bloo's own established visual language (phone,
        // watch, and the old widget all share it). It only swaps to a semantic
        // color while that specific state is actually true: red while unlocked
        // (a "you left this open" cue, matching every other unlocked indicator in
        // the app), teal while climate is running, green while charging.
        val bg = when {
            action == WidgetAction.LOCK && car.locked == false -> theme.unlocked
            // Same red cue as LOCK's own swap -- Unlock is the same state,
            // just reached from its own dedicated button instead of Lock's
            // toggle landing on it.
            action == WidgetAction.UNLOCK && car.locked == false -> theme.unlocked
            action == WidgetAction.CLIMATE && car.climateOn == true -> theme.climate
            action == WidgetAction.CHARGE && car.charging == true -> theme.charge
            else -> theme.accentProvider
        }
        val click = when (action.kind) {
            WidgetAction.Kind.NAV -> openAction(LocalContext.current)
            WidgetAction.Kind.REFRESH -> actionRunCallback<WidgetRefreshAction>(
                actionParametersOf(WidgetKeys.VIN to car.vin),
            )
            else -> actionRunCallback<WidgetCommandAction>(
                actionParametersOf(WidgetKeys.VIN to car.vin, WidgetKeys.ACTION to action.key),
            )
        }
        Box(
            modifier = (if (fixedHeight) modifier.height(heightOverride ?: Scale.buttonHeight(size)) else modifier)
                .background(bg)
                .cornerRadius(innerCorner(render.config))
                .clickable(click),
            contentAlignment = Alignment.Center,
        ) {
            // An icon alone is a guess -- a snowflake could be climate,
            // defrost, or "cool the battery". Where there's room, the button
            // says which.
            if (showLabel) {
                val labelStyle = buttonLabelStyle(theme)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(iconFor(action)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(theme.onAccent),
                        modifier = GlanceModifier.size(iconSize),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(action.label, style = labelStyle, maxLines = 1)
                }
            } else {
                Image(
                    provider = ImageProvider(iconFor(action)),
                    contentDescription = action.label,
                    colorFilter = ColorFilter.tint(theme.onAccent),
                    modifier = GlanceModifier.size(iconSize),
                )
            }
        }
    }

    /**
     * The corner radius for surfaces INSIDE the widget -- action buttons, the
     * map thumbnail -- derived from the same [WidgetConfig.corner] choice as
     * the outer container so the whole widget speaks one shape language.
     *
     * Deliberately not the container's own radius: at 32dp a button only
     * ~40dp tall is already a pill, so the inner scale is its own gentler
     * ramp rather than the same numbers reused.
     */
    private fun innerCorner(config: WidgetConfig): Dp = when (config.effectiveCorner) {
        WidgetConfig.CORNER_SHARP -> 0.dp
        WidgetConfig.CORNER_ROUND -> 18.dp
        WidgetConfig.CORNER_PILL -> 999.dp
        else -> 14.dp
    }

    /**
     * The bar treatment: a big percentage over a horizontal charge bar.
     *
     * A ring is the right hero when a tile has height to spend on it. On a
     * wide, short tile it is the wrong shape entirely -- it's bounded by the
     * SHORT side, so it shrinks to a token while the width it can't use sits
     * empty. This spends the axis that tile actually has: the number reads
     * from across a room, and the bar carries the same information as the
     * ring in a fraction of the height.
     *
     * It's also what the app's own hero card does, so a wide widget and the
     * app now show charge the same way instead of two different visual
     * languages for one value.
     */
    @Composable
    private fun BarHero(
        car: VehicleSnapshot, render: Render, width: Dp,
        // Budget the vertical the same way every other module here does: the
        // number sizes itself to what's left after the bar, the sub-line is
        // the first thing to go, and the whole treatment steps aside when
        // even the number can't be big enough to be one. Unbudgeted this
        // overran 422 sizes across the resize range -- worst a 220x40 strip
        // by 1.7dp, and RemoteViews doesn't clip an overflowing Column.
        //
        // Defaults to the WHOLE tile, correct for BannerLayout/
        // CompactWideLayout where this call is the row's only vertical
        // content. LargeWideLayout/XlWideLayout share their column with a
        // header, footer, info rows and a button row, so THEY pass the real
        // remainder instead -- otherwise this sized the hero number off the
        // full tile height while other modules were also claiming space out
        // of it, the same double-booking mistake this whole file exists to
        // avoid.
        avail: Dp = LocalSize.current.height - Scale.contentPadding(LocalSize.current) * 2,
        // False for LargeWideLayout/XlWideLayout, which already show the
        // car's name via their own HeaderRow above this call. NameAndStat's
        // fallback repeats it -- "Lanas Whip" once from the header, then
        // "Lanas Whip / 67% · 219 mi" again right under it -- on any tile
        // too short for the hero number to fit. Reported from a real
        // device. BannerLayout/CompactWideLayout have no header of their
        // own, so the fallback is the ONLY place their name shows and stays on.
        showNameFallback: Boolean = true,
    ) {
        val theme = render.theme
        val size = LocalSize.current
        val pct = car.percent
        val barH = Scale.barHeight(size)
        val heroSp = pct?.let { Scale.heroSpIn(size, avail, barH + 4.dp, theme.textScale) }
        if (pct == null || heroSp == null) {
            // No percentage to make a hero of, or no room to make it big
            // enough to be one. Either way the bar treatment isn't what this
            // tile wants, so it gets the ordinary name/stat pair rather than a
            // shrunken imitation of a hero -- unless the caller already has
            // its own header, in which case there's simply nothing to draw
            // here rather than a redundant name.
            if (showNameFallback) NameAndStat(car, render, width = width)
            return
        }
        val heroH = Scale.lineHeight(heroSp, 1f)
        val subH = Scale.lineHeight(Scale.subtitleSp(size).value, theme.textScale) + 4.dp
        val showSub = heroH + 4.dp + barH + subH <= avail
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            FitText(
                "$pct%",
                TextStyle(
                    color = theme.onSurface,
                    fontSize = heroSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxWidth = width,
            )
            Spacer(GlanceModifier.height(4.dp))
            ChargeBar(car, theme, width = width, height = barH)
            // NOT primaryValue: that leads with the percentage, which is the
            // 44sp number directly above it. The sub-line's job here is to say
            // what primaryValue's version of this layout couldn't fit -- whose
            // car it is, and how far it goes.
            val sub = listOfNotNull(
                car.name.takeIf { it.isNotBlank() },
                car.rangeMi?.let { formatDistance(it.toDouble(), render.metric) },
            ).joinToString(" · ").takeIf { showSub && it.isNotBlank() }
            if (sub != null) {
                Spacer(GlanceModifier.height(4.dp))
                FitText(sub, subtitleStyle(theme), maxWidth = width)
            }
        }
    }

    /**
     * The horizontal charge bar under [BarHero]'s number.
     *
     * Split at the car's charge limit when it reported one, so the stretch
     * the car has been told not to fill reads as a separate, dimmer segment
     * rather than as more headroom -- the same shape the phone's hero card
     * and the live charging notification now use for the same value.
     *
     * Glance has no fractional width, but the exact slot width is known
     * here, so every piece is computed in dp rather than guessed. Each
     * segment's fill is its own local share of the global charge, which is
     * what keeps a charge that has overrun its limit reading correctly
     * instead of clamping invisibly at the seam.
     */
    @Composable
    private fun ChargeBar(car: VehicleSnapshot, theme: WidgetTheme, width: Dp, height: Dp) {
        val pct = (car.percent ?: 0).coerceIn(0, 100)
        val frac = pct / 100f
        val limit = car.chargeLimitPct?.takeIf { it in 1..99 }
        // Split at the CHARGE: green is what's in the pack, grey is what
        // isn't, and the gap between them is the level. The limit is a marker
        // ON that bar, drawn below, not a second division of it -- the same
        // model the phone hero, the watch ring and the widget's own ring use.
        //
        // The common case is the charge sitting AT its own limit -- a car
        // set to 80% and charged to 80%, which is most of the time this bar
        // is drawn at all. The split and the dot then land on the same
        // pixel: a real gap cut into the bar right where the dot's own halo
        // is also trying to cut one, reading as a ragged double-notch rather
        // than either device on its own. Ported from the phone's own
        // ChargeSegmentBar, which found this the same way. Near the split
        // the gap yields and the dot alone carries it.
        val atSplit = limit != null && kotlin.math.abs(limit / 100f - frac) < 0.03f
        // The gap costs real width, so it is skipped on a narrow slot, at
        // the extremes where there is nothing to separate, and right at the
        // limit where the dot already marks the same spot.
        val gap = if (width >= 60.dp && frac > 0.02f && frac < 0.98f && !atSplit) 3.dp else 0.dp
        val usable = (width - gap).coerceAtLeast(0.dp)
        // Floored at the bar's own height when there is ANY charge, so a low
        // one reads as a rounded nub rather than a hairline: below that the
        // 50% corner radius eats the whole shape and 3% looks identical to 0%.
        // Capped at `usable` so the floor can't overrun a narrow slot, and
        // `rest` is derived from the result rather than computed in parallel,
        // so the two always still sum to the bar.
        val filled = if (frac <= 0f) 0.dp else minOf(usable, maxOf(usable * frac, height))
        val rest = (usable - filled).coerceAtLeast(0.dp)
        val fillColor = if (car.charging == true) theme.charge else theme.accentProvider
        // Glance has no z-stacking of a marker over a Row without a Box, so
        // the whole bar lives in one: the Row paints, the dot overlays.
        Box(modifier = GlanceModifier.width(width).height(height)) {
            Row(modifier = GlanceModifier.width(width).height(height)) {
                if (filled > 0.dp) {
                    Box(
                        modifier = GlanceModifier.width(filled).height(height)
                            .cornerRadius(height / 2).background(fillColor),
                    ) {}
                }
                if (rest > 0.dp) {
                    if (filled > 0.dp) Spacer(GlanceModifier.width(gap))
                    Box(
                        modifier = GlanceModifier.width(rest).height(height)
                            .cornerRadius(height / 2).background(theme.surfaceVariant),
                    ) {}
                }
            }
            // The marker. Positioned by a leading spacer rather than an offset
            // -- Glance has no translation modifier, so "put this at x" is
            // spelled "reserve x of empty space first".
            if (limit != null && width >= 60.dp) {
                val l = limit / 100f
                // Exactly the bar's height, NOT taller: the dot lives in a
                // Box sized to the bar (see above), which every caller has
                // already budgeted exactly `height` of room for -- a dot
                // that reads bigger without actually being taller than the
                // bar it sits on has to come from its own proportions, not
                // from claiming more room this composable was never given.
                val dot = height
                val x = (usable * l + (if (l > frac) gap else 0.dp) - dot / 2)
                    .coerceIn(0.dp, (width - dot).coerceAtLeast(0.dp))
                Row(modifier = GlanceModifier.width(width).height(height)) {
                    Spacer(GlanceModifier.width(x))
                    Box(
                        // FIXED colours, not swapped by which side of the fill
                        // this lands on -- see the phone's ChargeLimitDot for
                        // the full reasoning. Flipping the core between
                        // theme.background and fillColor made the marker a
                        // same-coloured hole once the charge reached the
                        // limit (background halo + background core), which is
                        // exactly the state this bar is in most of the time.
                        // theme.background (not surface -- WidgetTheme has no
                        // `surface`) cuts a visible window in the bar
                        // regardless of what's under it; onSurface inside
                        // that window is guaranteed to contrast against it.
                        modifier = GlanceModifier.size(dot)
                            .cornerRadius(dot / 2)
                            .background(theme.background),
                    ) {
                        // A PROPORTION of the dot (0.6x), not dot minus a flat
                        // 5dp -- the flat version left almost nothing at small
                        // sizes (an 8dp dot on a small tile's thin bar had a
                        // 3dp core, reading as a grey smudge rather than a
                        // ring with a visible centre) while barely mattering
                        // at large ones. Proportional keeps the ring-to-core
                        // ratio legible at every bar thickness this scales to.
                        val core = (dot * 0.6f).coerceAtLeast(3.dp)
                        Box(
                            modifier = GlanceModifier.size(core)
                                .cornerRadius(core / 2)
                                .background(theme.onSurface),
                        ) {}
                    }
                }
            }
        }
    }

    /**
     * The gauge for the big tiers when [Scale.ring] yielded nothing.
     *
     * [Scale.ringRoom] measures what the header, buttons and footer left, and
     * [Scale.ring] turns too little of it into NO ring rather than a smudge.
     * That is the right call for a ring -- but it meant a LARGE or XL tile at
     * the 1.4x text size, where those three can eat the whole height, showed
     * no charge indicator whatsoever. A 240x170 tile lands there today.
     *
     * A bar needs 10-14dp where a ring needs 24 at the very least, so it fits
     * where the ring couldn't. It is drawn out of the room the ring was
     * already budgeted and then didn't use, and only when that room genuinely
     * holds it, so it cannot squeeze the weighted content row beneath it.
     * Nothing changes on tiles where a ring does fit.
     */
    @Composable
    private fun ChargeBarFallback(car: VehicleSnapshot, render: Render, ringEdge: Dp, room: Dp) {
        if (!render.config.showRing || car.percent == null || ringEdge > 0.dp) return
        val size = LocalSize.current
        val barH = Scale.barHeight(size)
        if (room < barH + 8.dp) return
        ChargeBar(
            car, render.theme,
            width = size.width - Scale.contentPadding(size) * 2,
            height = barH,
        )
        Spacer(GlanceModifier.height(8.dp))
    }

    // ---- Small pieces --------------------------------------------------------

    @Composable
    private fun RingImage(car: VehicleSnapshot, render: Render, edgeDp: Int) {
        // Scale.ring yields 0 when the column can't fit a legible ring.
        if (edgeDp <= 0) return
        val ctx = LocalContext.current
        val density = ctx.resources.displayMetrics.density
        val px = (edgeDp * density).toInt().coerceAtLeast(24)
        val frac = (car.percent ?: 0) / 100f
        val arc = ChargeRing.arcColorFor(frac, car.charging == true, render.theme.accentArgb)
        val bmp = ChargeRing.render(
            sizePx = px,
            fraction = frac,
            arcColor = arc,
            trackColor = render.theme.trackArgb,
            centerText = car.percent?.let { "$it%" },
            centerColor = arc,
            // Only on rings big enough for the notch to read as one. Below
            // that it's a nick in a small circle, which says less than the
            // unbroken ring does.
            limitFraction = car.chargeLimitPct
                ?.takeIf { edgeDp >= 44 }
                ?.let { it.coerceIn(0, 100) / 100f },
        )
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "${car.percent ?: 0} percent",
            modifier = GlanceModifier.size(edgeDp.dp),
        )
    }

    @Composable
    private fun StatusGlyph(car: VehicleSnapshot, theme: WidgetTheme, sizeDp: Int) {
        if (sizeDp <= 0) return
        // A car that has never reported a lock state renders NOTHING, rather
        // than resolving `null` into the unlocked branch and putting a confident
        // red open padlock on the home screen for a state nobody has heard yet.
        // `infoValue`'s own LOCK case already draws this distinction (it returns
        // null rather than guessing), and the watch's ToggleStateComplication
        // omits its icon entirely for the same reason: a definite glyph is
        // indistinguishable from a confirmed reading.
        //
        // Yielding nothing is the same contract Scale.ring and heroSpIn use, and
        // the callers already handle it -- this is only reached when the ring is
        // off or percent is unknown, i.e. a tile that has little else to show
        // either way.
        val locked = car.locked ?: return
        val res = if (locked) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
        val tint = if (locked) theme.accentProvider else theme.unlocked
        Image(
            provider = ImageProvider(res),
            contentDescription = if (locked) "Locked" else "Unlocked",
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(sizeDp.dp),
        )
    }

    @Composable
    private fun IconPill(iconRes: Int, onClick: androidx.glance.action.Action, theme: WidgetTheme) {
        val size = LocalSize.current
        val pillSize = Scale.pillSize(size)
        Box(
            // Always a true circle (half its own size), not a fixed corner
            // radius -- a fixed radius reads as "barely rounded square" once
            // the pill itself scales up on larger tiles.
            modifier = GlanceModifier.size(pillSize).cornerRadius(pillSize / 2)
                .background(theme.surfaceVariant).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = "Switch car",
                colorFilter = ColorFilter.tint(theme.onSurfaceVariant),
                modifier = GlanceModifier.size(Scale.pillIcon(size)),
            )
        }
    }

    // ---- Text styles ---------------------------------------------------------
    // @Composable (not plain functions) purely so each can read LocalSize.current
    // itself -- every call site is already inside composition, so this scales
    // font size continuously with the widget's exact measured size (see Scale)
    // without having to thread a size param through every single caller.

    @Composable
    private fun titleStyle(theme: WidgetTheme) = TextStyle(
        color = theme.onSurface,
        fontSize = (Scale.titleSp(LocalSize.current).value * theme.textScale).sp,
        fontWeight = FontWeight.Bold,
    )
    @Composable
    private fun subtitleStyle(theme: WidgetTheme) = TextStyle(
        color = theme.onSurfaceVariant,
        fontSize = (Scale.subtitleSp(LocalSize.current).value * theme.textScale).sp,
    )
    @Composable
    private fun valueStyle(theme: WidgetTheme) = TextStyle(
        color = theme.onSurface,
        fontSize = (Scale.valueSp(LocalSize.current).value * theme.textScale).sp,
        fontWeight = FontWeight.Medium,
    )

    /**
     * The label style on an [ActionButton].
     *
     * Here rather than at its two use sites because those two sites are the fit
     * TEST and the RENDER: [ActionButtons] builds this style to ask whether every
     * label fits its slot (via [wouldOverflow], which measures character count
     * against font size) and [ActionButton] rebuilt an identical copy to actually
     * draw them. Two independent copies of the input to a measurement and the
     * thing being measured is the one duplication in this file that can't merely
     * look wrong -- drift either shows labels that don't fit, or hides labels that
     * would have.
     */
    @Composable
    private fun buttonLabelStyle(theme: WidgetTheme) = TextStyle(
        color = theme.onAccent,
        fontSize = (Scale.subtitleSp(LocalSize.current).value * theme.textScale).sp,
        fontWeight = FontWeight.Medium,
    )

    // ---- Value helpers -------------------------------------------------------

    private fun statusSubtitle(car: VehicleSnapshot): String {
        val parts = mutableListOf<String>()
        car.locked?.let { parts += if (it) "Locked" else "Unlocked" }
        if (car.charging == true) parts += "Charging"
        if (car.climateOn == true) parts += "Climate on"
        if (car.engineOn == true) parts += "Running"
        return parts.joinToString(" · ").ifBlank { car.model }
    }

    private fun primaryValue(car: VehicleSnapshot, render: Render): String {
        // Hoist to a local val: rangeMi is a nullable public property in :shared, which
        // Kotlin won't smart-cast across a module boundary — a local copy is smart-castable.
        val range = car.rangeMi
        return when {
            car.percent != null -> "${car.percent}%" +
                (range?.let { " · ${formatDistance(it.toDouble(), render.metric)}" } ?: "")
            range != null -> formatDistance(range.toDouble(), render.metric)
            else -> car.model
        }
    }

    /** [field]'s label, adjusted for the car it's actually describing --
     *  [WidgetInfoField.PERCENT]'s stored label is "Battery", which is wrong
     *  for a gas or hybrid car with no chargeable pack: that same percent
     *  number is its FUEL level. Every other field's label is car-agnostic. */
    private fun fieldLabel(field: WidgetInfoField, car: VehicleSnapshot): String =
        if (field == WidgetInfoField.PERCENT && !car.hasBattery) "Fuel" else field.label

    private fun infoValue(field: WidgetInfoField, car: VehicleSnapshot, render: Render): String? = when (field) {
        WidgetInfoField.RANGE -> car.rangeMi?.let { formatDistance(it.toDouble(), render.metric) }
        WidgetInfoField.PERCENT -> car.percent?.let { "$it%" }
        WidgetInfoField.ODOMETER -> car.odometer?.takeIf { it.isNotBlank() }
        WidgetInfoField.PLATE -> car.licensePlate?.takeIf { it.isNotBlank() }
        WidgetInfoField.SERVICE -> serviceDueLabel(car, render.metric)
        WidgetInfoField.UPDATED -> relativeLabel(car.fetchedAt.takeIf { it > 0 }).takeIf { it.isNotBlank() }
        // null (rather than a placeholder) when the car hasn't reported the
        // state, so InfoStack skips the row entirely instead of showing a
        // confident-looking "Unlocked" for something simply unknown.
        WidgetInfoField.LOCK -> car.locked?.let { if (it) "Locked" else "Unlocked" }
        WidgetInfoField.CLIMATE -> car.climateOn?.let { if (it) "On" else "Off" }
        WidgetInfoField.MODEL -> car.model.takeIf { it.isNotBlank() }
        // Null when the car isn't plugged in: the limit it would charge to is
        // real, but "80%" beside a car sitting in a driveway reads as a
        // current state rather than a setting.
        WidgetInfoField.LIMIT -> car.chargeLimitPct?.let { "$it%" }
    }

    private fun serviceDueLabel(car: VehicleSnapshot, metric: Boolean): String? {
        val last = car.lastServiceMiles ?: return null
        val interval = car.serviceIntervalMiles ?: return null
        // Odometer strings can arrive with thousands separators (e.g. "12,345")
        // and fractional miles; parseOdometerMiles strips/floors them to an Int.
        // Hand-rolling this with filter { isDigit() } dropped the decimal POINT
        // too, so "12,345.6" parsed as 123456 -- an odometer ten times too high,
        // which drove `due` negative, coerced it to 0, and left this field
        // reading "in 0 mi" forever (a service alert that never clears).
        val odo = parseOdometerMiles(car.odometer) ?: return null
        val due = (last + interval - odo).coerceAtLeast(0)
        return "in ${formatDistance(due, metric)}"
    }

    private fun iconFor(action: WidgetAction): Int = when (action) {
        WidgetAction.LOCK -> R.drawable.ic_shortcut_lock
        WidgetAction.UNLOCK -> R.drawable.ic_shortcut_unlock
        WidgetAction.CLIMATE -> R.drawable.ic_shortcut_climate
        WidgetAction.CHARGE -> R.drawable.ic_widget_charge
        WidgetAction.FLASH -> R.drawable.ic_widget_flash
        WidgetAction.HORN -> R.drawable.ic_widget_horn
        WidgetAction.REFRESH -> R.drawable.ic_widget_refresh
        WidgetAction.OPEN -> R.drawable.ic_shortcut_car
    }

    // Glance's reified actionStartActivity<T>() overload isn't available here, so
    // build the Intent explicitly (the (Intent, …) overload is the stable one).
    private fun openAction(context: Context) =
        actionStartActivity(Intent(context, MainActivity::class.java))
}

/**
 * The widget's fully-resolved color set for one render, built once in
 * [CarWidget.provideGlance] instead of leaning on the vanilla [GlanceTheme]
 * default (which resolves to Android's generic wallpaper-derived Material You
 * palette on API 31+, with no relationship to Bloo's own branding or the
 * user's actual in-app theme settings). [accent] mirrors exactly what the rest
 * of the app shows for this car (per-car custom palette → global custom
 * palette → dynamic color → the built-in Expressive palette, all vibrancy-
 * scaled — see [resolveWidgetAccent]), unless the widget's own config picked
 * an explicit [WidgetAccent] override. [isDark] similarly follows the app's
 * real theme setting unless this widget's [WidgetConfig.theme] overrides it.
 */
private data class WidgetTheme(
    val isDark: Boolean,
    val accent: Color,
    val accentArgb: Int,
    val accentProvider: ColorProvider,
    val onAccent: ColorProvider,
    val background: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val surfaceVariant: ColorProvider,
    val trackArgb: Int,
    val charge: ColorProvider,
    val unlocked: ColorProvider,
    val climate: ColorProvider,
    /** Multiplier applied to every font size the widget derives from its
     *  measured size. Carried here because this is already the per-render
     *  resolved-styling holder threaded to every module, so the text helpers
     *  can reach it without a config parameter on each one.
     *
     *  Scaling up cannot overflow: FitText measures against the scaled size,
     *  so a larger scale simply reaches its wrap and shrink rungs sooner. */
    val textScale: Float = 1f,
) {
    companion object {
        fun resolve(
            context: Context,
            appearance: SettingsStore.Appearance,
            config: WidgetConfig,
            vin: String?,
        ): WidgetTheme {
            val forceDark = when (config.theme) {
                WidgetConfig.THEME_LIGHT -> false
                WidgetConfig.THEME_DARK -> true
                else -> null
            }
            val isDark = resolveWidgetIsDark(context, appearance, forceDark)
            val accent = config.accent?.let { WidgetAccent.fromKey(it) }?.let { Color(it.argb) }
                ?: resolveWidgetAccent(context, appearance, vin, forceDark)
            val onAccent = if (accent.luminance() > 0.5f) Color(0xFF16171B) else Color.White
            // True black only when actually following the app's own AMOLED setting
            // (config.theme == "auto") -- a per-widget Light/Dark override is asking
            // for a deliberately different look than the app, not a void background.
            val amoled = config.theme == WidgetConfig.THEME_AUTO &&
                (appearance.themeMode == ThemeMode.AMOLED ||
                    (appearance.themeMode == ThemeMode.SYSTEM_AMOLED && isDark))
            val background = when {
                amoled -> Color.Black
                isDark -> Color(0xFF1C1D22)
                else -> Color(0xFFF4F4F7)
            }
            // Translucency is applied to the resolved surface here rather
            // than at the call site, because this is the only place the
            // background is still a Color rather than an opaque
            // ColorProvider. The photo background path never reads this, so
            // the option correctly has no effect there -- a photo brings its
            // own backdrop and its own scrim.
            val tinted = background.copy(alpha = background.alpha * config.safeBackgroundOpacity)
            val onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF1B1C20)
            val onSurfaceVariant = if (isDark) Color(0xFFC6C6CC) else Color(0xFF5C5E66)
            val surfaceVariant = if (isDark) Color(0xFF2A2C32) else Color(0xFFE7E7EC)
            return WidgetTheme(
                isDark = isDark,
                accent = accent,
                accentArgb = accent.toArgb(),
                accentProvider = ColorProvider(accent),
                onAccent = ColorProvider(onAccent),
                background = ColorProvider(tinted),
                textScale = config.safeTextScale,
                onSurface = ColorProvider(onSurface),
                onSurfaceVariant = ColorProvider(onSurfaceVariant),
                surfaceVariant = ColorProvider(surfaceVariant),
                // Was 0x33 (20%) in dark mode -- against a true-black AMOLED
                // background specifically, that read as barely-there rather
                // than "faint unfilled track," so the ring's own bright arc
                // looked like two disconnected floating curves instead of one
                // continuous circle with a filled portion. Bumped until the
                // empty segment reads as clearly part of the same shape.
                trackArgb = if (isDark) 0x4DFFFFFF else 0x33000000,
                charge = ColorProvider(Color(BlooColors.chargeGreen)),
                unlocked = ColorProvider(Color(BlooColors.heat)),
                climate = ColorProvider(Color(BlooColors.climateTeal)),
            )
        }
    }

    /**
     * Text/tonal roles re-tuned for legibility over an arbitrary car photo
     * instead of a flat themed surface -- text goes to a fixed near-white
     * (the photo already gets a dark scrim behind it, see [CarWidget.Content]),
     * and every state-color button fill (accent/charge/unlocked/climate) picks
     * up the same "frosted glass over a photo" translucency the rest of the
     * app's glass surfaces use, rather than sitting fully opaque on top of the
     * photo like a flat sticker.
     */
    fun forPhoto(): WidgetTheme {
        fun glassy(c: Color) = c.copy(alpha = 0.62f)
        return copy(
            onSurface = ColorProvider(Color.White),
            onSurfaceVariant = ColorProvider(Color(0xFFE4E4E8)),
            surfaceVariant = ColorProvider(Color(0x3DFFFFFF)),
            accentProvider = ColorProvider(glassy(accent)),
            charge = ColorProvider(glassy(Color(BlooColors.chargeGreen))),
            unlocked = ColorProvider(glassy(Color(BlooColors.heat))),
            climate = ColorProvider(glassy(Color(BlooColors.climateTeal))),
        )
    }
}
