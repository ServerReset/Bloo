package com.bloo.bluelink.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.serviceDue
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

/** Top-level (not nested in [CarWidget]) so the render layer's other files --
 *  [WidgetValues], and any further slice of CarWidget.kt's own composables --
 *  can take it as a plain parameter type without an outer-class qualifier.
 *  Nested-but-internal was tried first and does NOT do this: an unqualified
 *  `Render` in another file only resolves against top-level declarations,
 *  never a nested class of some other top-level type, however visible. */
internal data class Render(
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
) {
    /** Whether HeaderRow draws the car-switcher pill, which is taller than the
     *  two text lines beside it at the smaller text settings.
     *
     *  One definition, read by HeaderRow to decide whether to draw it and by
     *  [Scale.headerHeight] to reserve for it. As an inline condition in
     *  HeaderRow only, no budget could see it. */
    val hasSwitcher: Boolean get() = multiCar && config.vin == null

    /** The per-render facts every vertical budget needs. [size] is the only
     *  thing not already known here, so each tier builds this once from
     *  LocalSize rather than passing four arguments down. */
    fun frame(size: DpSize): Scale.Frame =
        Scale.Frame(size, theme.textScale, pillCorner(size), hasSwitcher)

    /** True when Content's root padding gets its pill-corner bonus. Derived from
     *  the same rule Content applies, so the padding it draws and the padding
     *  every budget subtracts cannot disagree -- they did for all 18 tiers,
     *  which each assumed plain contentPadding. */
    fun pillCorner(size: DpSize): Boolean =
        config.effectiveCorner == WidgetConfig.CORNER_PILL && Scale.pillAppliesAt(size)
}

class CarWidget : GlanceAppWidget() {

    // Exact = recompose for the real current size, so every launcher cell count
    // (and every mid-resize size) gets a layout tuned to its exact dimensions,
    // rather than snapping to a handful of Responsive buckets.
    override val sizeMode = SizeMode.Exact

    /**
     * Replaces Glance's default composition-failure UI, a bare "Can't show content"
     * that offers the user nothing to do about it, with a themed panel that at least
     * opens the app on tap.
     *
     * Deliberately plain [RemoteViews]: this is not a composable and not a suspend
     * function, so neither Glance content nor the per-car [WidgetTheme] (which needs
     * suspending DataStore reads) is reachable here. The layout leans on
     * `?android:attr` for light/dark, exactly as `car_widget_loading.xml` does --
     * and composing a theme at the moment composition has just failed would be the
     * wrong instinct anyway.
     *
     * Note `errorUiLayout` is NOT the hook to use: in glance-appwidget 1.1.1 it is a
     * private final field with only an internal getter, so it cannot be overridden --
     * verified against the resolved 1.1.1 artifact, not the docs.
     *
     * The [throwable] is intentionally dropped rather than logged: there is not one
     * `android.util.Log` call anywhere in `:app` or `:shared`, and quietly starting a
     * logging convention inside an error handler is not the place to make that call.
     * It does mean a composition crash stays undiagnosable, which is a real cost --
     * this is the line to add a log to if that ever needs chasing.
     */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        val views = RemoteViews(context.packageName, R.layout.car_widget_error)
        views.setOnClickPendingIntent(
            R.id.widget_error_root,
            PendingIntent.getActivity(
                context,
                // Keyed per widget so two failed widgets don't share (and overwrite)
                // one another's PendingIntent.
                appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore(context).get(appWidgetId)
        val snapshots = SnapshotStore(context)
        val data = snapshots.current()
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
            // Observe the snapshot store INSIDE the composition, which is what
            // GlanceAppWidget's own KDoc instructs: "load initial data before calling
            // provideContent, and then observe your sources of data within the composition
            // (e.g. collectAsState). This ensures that your widget will continue to update
            // while the composition is active."
            //
            // Without this, `updateAll()` on a data change could silently do nothing.
            // update() only calls session.updateGlance() when a session was already running,
            // and updateGlance() merely re-reads GLANCE STATE -- the content flow is held as
            // `remember { widget.runGlance(...) }`, so the suspend body above is never
            // re-invoked. Every value it computed stays frozen for the life of the session.
            //
            // That is not theoretical here. Tapping a widget action writes an optimistic
            // snapshot and calls updateAll(), then the real status lands seconds later and
            // calls updateAll() again -- inside the same session (~45s after provideContent,
            // ~5s on a dozing device). The SECOND update was the one carrying the truth, and
            // it was the one dropped. Symptom: "I sent a command from the widget and it never
            // updated."
            val live by snapshots.payload.collectAsState(initial = data)
            val liveCar = if (config.vin != null) {
                live.vehicles.firstOrNull { it.vin == config.vin }
            } else {
                live.selected
            }
            // Staleness is recomputed from the live fetchedAt, not carried over: the whole
            // point of a live update is that the age changed.
            val liveStale = liveCar?.fetchedAt?.takeIf { it > 0 }?.let {
                System.currentTimeMillis() - it > com.bloo.bluelink.data.STALE_STATUS_MS
            } ?: false
            GlanceTheme {
                // theme, mapBitmap and photoBitmap deliberately keep their cold-path values.
                // All three need I/O, which Glance composables cannot do, and all three are
                // keyed to the car's IDENTITY rather than its data -- which does not change
                // within a session (switching cars goes through WidgetSwitchCarAction, and
                // that starts a new one). What changes live is the numbers, and those are
                // exactly what this passes through.
                Content(
                    render.copy(
                        car = liveCar ?: render.car,
                        stale = liveStale,
                        multiCar = live.vehicles.size > 1,
                    ),
                )
            }
        }
    }

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
                if (Scale.pillAppliesAt(size)) 999.dp else 32.dp
            else -> 20.dp
        }
        val outerCorner = GlanceModifier.fillMaxSize().cornerRadius(corner)
        // Base padding scales continuously with size; a pill shape needs a
        // little extra on top of that so content doesn't clip against the
        // extreme corner curve.
        //
        // Through Scale.rootPadding rather than open-coded, because this is the
        // figure every tier's vertical budget has to subtract. It was computed here
        // and nowhere else, so all 18 tiers subtracted plain contentPadding and
        // over-reported their column by 8dp on any pill widget under 180dp.
        val root = (if (photo == null) outerCorner.background(effective.theme.background) else outerCorner)
            .padding(Scale.rootPadding(size, effective.pillCorner(size)))
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
                // innerWidth, not size.width - 8: the caption is inside Content's root padding.
                // NOT singleLine -- this is the terminal element in a centred column with slack,
                // so wrapping is an acceptable last resort here (unlike the compact name tiers).
                FitText(
                    car.name, subtitleStyle(render.theme),
                    maxWidth = Scale.innerWidth(render.frame(size)), horizontalAlignment = Alignment.CenterHorizontally,
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
        val frame = render.frame(size)
        // Only take over the tile if there's actually something to show:
        // resolvedActions filters by brand (Kia and the Canada backend have
        // no flash/horn endpoint), so a widget configured with only those
        // resolves to an empty list, and returning here regardless would
        // render a completely blank banner.
        if (controlsPriority(render) && resolvedActions(car, render, max = 6).isNotEmpty()) {
            val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
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
        val w = Scale.innerWidth(frame)
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
    // Moved to Scale.TALL_TIER_MARGIN. It lived here as a private val, which meant
    // WidgetScaleTest duplicated its VALUE with a comment admitting so -- a silent
    // drift vector on top of the three copies of the arithmetic that used it. The
    // reasoning above still applies; it just belongs next to the function that
    // applies it, so both the composables and the sweep read one definition.

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
    // Moved to Scale.MIN_HERO_RESERVE. It lived here as a private val, which meant
    // WidgetScaleTest duplicated its VALUE with a comment admitting so -- a silent
    // drift vector on top of the three copies of the arithmetic that used it. The
    // reasoning above still applies; it just belongs next to the function that
    // applies it, so both the composables and the sweep read one definition.

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
        val frame = render.frame(size)
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = Scale.innerHeight(frame)
            val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
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
        // The whole name+button+ring/split reservation is WidgetLayout.tallPlan now -- the ONE
        // definition of this tier's budget that the WidgetScaleTest sweep also calls, so the
        // numbers the widget renders and the numbers the sweep asserts can't drift. RAIL's spec
        // (no name, no rows, no map) lives in WidgetLayout; the render tree below is unchanged.
        // A location map is never eligible on RAIL: at under 110dp wide it reads as a random
        // zoomed-in street fragment (reported from a real device), so that width goes to the ring.
        val plan = WidgetLayout.tallPlan(WidgetTier.RAIL, size, render.theme.textScale, allActions.size)
        val buttonCount = plan.buttonCount
        val buttonZone = plan.buttonZone
        val split = plan.split
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Weighted spacers above and below centre the ring in whatever the button stack
            // left, rather than piling it at the top. RAIL draws no map (the plan's spec has
            // no map slot), so the ring simply sits between two weighted spacers.
            Spacer(GlanceModifier.defaultWeight())
            RingOrGlyph(car, render, edgeDp = split.ring.value.toInt())
            Spacer(GlanceModifier.defaultWeight())
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
        val frame = render.frame(size)
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 4)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, minOf(Scale.innerWidth(frame), Scale.innerHeight(frame)))
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
        val budget = Scale.innerHeight(frame)
        val left = (budget - Scale.lineHeight(Scale.titleSp(size).value, scale) - 8.dp).coerceAtLeast(0.dp)
        val rows = Scale.infoRowsIn(size, left, scale, cap = 1)
        val ringEdge = Scale.ring(
            size,
            minOf(left - Scale.infoBlockHeight(size, rows, scale), size.width - 8.dp),
        )
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // innerWidth, not size.width - 8: the name is inside Content's root padding, so its
            // real width is the tile minus that padding (up to 18dp/side), not a flat 8dp. And
            // singleLine, because the column reserves exactly one nameHeight line for it below.
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
                singleLine = true,
            )
            Spacer(GlanceModifier.height(4.dp))
            RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            Spacer(GlanceModifier.height(4.dp))
            if (rows > 0) InfoStack(car, render, max = rows)
        }
    }

    @Composable
    private fun CompactWideNarrowLayout(car: VehicleSnapshot, render: Render) {
        val size = LocalSize.current
        val frame = render.frame(size)
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 2)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
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
        // Against the real inner height. This was `size.height - 12.dp`, a literal
        // standing in for 2 * contentPadding -- which spans 12dp to 36dp, so the ring
        // exceeded the padded box by the difference whenever the height term bound.
        val ringEdge = Scale.ring(size, Scale.innerHeight(frame).coerceAtLeast(18.dp))
        // Not just `showRing && percent != null`: Scale.ring returns 0 when the
        // column can't fit a legible circle, and RingImage early-returns on that.
        // Asking the question as "is a ring configured" left the 6dp spacer below
        // rendering beside nothing on every tile too short for one.
        val drawsRing = render.config.showRing && car.percent != null && ringEdge > 0.dp
        // The width each weighted child of this Row REALLY gets: whatever is
        // left once the ring and the fixed spacers are taken out, split
        // between the text column and the buttons. The fraction-of-tile
        // guesses this replaces were wrong twice over -- they under-reported
        // the slice, and they kept assuming a ring was there even when one
        // isn't drawn, so a widget with the ring switched off still laid its
        // text and buttons out as if a third of the row were missing.
        //
        // Against the PADDED width, not the raw tile width. BANNER and
        // COMPACT_WIDE both carry this correction and both spell out why -- the
        // root has already applied Scale.contentPadding on both sides, so raw
        // width over-reports by that much and ActionButtons' capacity check
        // fits one button too many. This tier was written from the same
        // template and never got it, because its own two-part fix looked
        // complete: the ring term genuinely does belong here (unlike in those
        // two, this tile really draws one), which made the missing padding term
        // easy to overlook while comparing the shape of the formulas rather
        // than their terms.
        val w = Scale.innerWidth(frame)
        val slice = ((w - (if (drawsRing) ringEdge + 6.dp else 0.dp) - 6.dp) / 2)
            .coerceAtLeast(24.dp)
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (drawsRing) {
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
        val frame = render.frame(size)
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 4)
            if (actions.isNotEmpty()) {
                val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
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
        val w = Scale.innerWidth(frame)
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
        val frame = render.frame(size)
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = Scale.innerHeight(frame)
            val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
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
        // One WidgetLayout.tallPlan call owns the name + button + split reservation (the sweep
        // calls the same one). This tier's spec: a name line (+4dp gap), buttons capped at 4 so
        // a tall-but-narrow tile doesn't become a button ladder, one info row, no map -- under
        // 150dp wide a location map reads as an unreadable street fragment, so the width goes to
        // the ring/name/buttons instead. The render tree below is unchanged.
        val plan = WidgetLayout.tallPlan(WidgetTier.COMPACT_TALL_NARROW, size, render.theme.textScale, allActions.size)
        val buttonCount = plan.buttonCount
        val buttonZone = plan.buttonZone
        val split = plan.split
        // The width cap that already existed here, kept: it's what keeps the
        // circle round on a genuinely narrow tile.
        val ringEdge = minOf(split.ring, size.width - 12.dp)
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // innerWidth + singleLine: inside Content's root padding, and the column reserves
            // one nameHeight line for this. See CompactSquareLayout's own note.
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
                singleLine = true,
            )
            Spacer(GlanceModifier.height(4.dp))
            // No map on this tier, so the ring is centred by a weighted spacer above and below.
            Spacer(GlanceModifier.defaultWeight())
            RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            if (split.rows > 0) {
                Spacer(GlanceModifier.height(4.dp))
                InfoStack(car, render, max = split.rows)
            }
            Spacer(GlanceModifier.defaultWeight())
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
        val frame = render.frame(size)
        val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
        if (controlsPriority(render) && allActions.isNotEmpty()) {
            val budgetH = Scale.innerHeight(frame)
            val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
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
        // One WidgetLayout.tallPlan call owns the name + button + split reservation (the sweep
        // calls the same one). This tier's spec: a name line (no extra gap), buttons STACKED and
        // capped at every configured action (a single row truncated to width while tall space
        // sat empty -- reported from a device), up to 4 info rows, no map (still under 150dp
        // wide). Its 20dp button overhead (12 trailing + the forced 8dp pre-button spacer) lives
        // in WidgetLayout now; this tier once passed only 12 and under-reserved that spacer.
        val plan = WidgetLayout.tallPlan(WidgetTier.COMPACT_TALL, size, render.theme.textScale, allActions.size)
        val buttonCount = plan.buttonCount
        val buttonZone = plan.buttonZone
        val split = plan.split
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // innerWidth + singleLine: inside Content's root padding, and the column reserves
            // one nameHeight line for this. See CompactSquareLayout's own note.
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
                singleLine = true,
            )
            // No map on this tier, so a weighted spacer above the hero centres what the ring
            // and rows don't claim rather than piling it at the top.
            Spacer(GlanceModifier.defaultWeight())
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
            Spacer(GlanceModifier.defaultWeight())
            if (buttonCount > 0) {
                Spacer(GlanceModifier.height(8.dp))
                ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
            }
        }
    }

    // The per-square-tier row-width thresholds (below which info rows stack under the ring
    // instead of beside it) moved into WidgetLayout.SquareSpec, read via
    // WidgetLayout.squareRowWidth. They must agree between RingWithContent's minRowWidth and
    // squarePlan's sideBySide decision -- one home guarantees that, where two literals could
    // drift and reinstate the overflow squareSplit exists to remove.

    @Composable
    private fun MediumSquareLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as the LARGE/XL tiers' own clamp: the header +
        // button rows can leave less than the ring's continuous target size
        // at MEDIUM's own minimum height (150dp).
        val size = LocalSize.current
        val frame = render.frame(size)
        // WidgetLayout.squarePlan owns the ringRoom -> squareSplit sequence and this tier's
        // constants (16dp spacer allowance = the two Spacer(8.dp) in this column, capRows 3, no
        // footer, no map). It replaced an infoCap row estimate that overflowed thousands of
        // sizes (worst 22.4dp at 150x150, 1.4x text); the sweep asserts the assembled column
        // fits by calling the same squarePlan.
        val split = WidgetLayout.squarePlan(
            WidgetTier.MEDIUM_SQUARE, frame,
            showHeader = render.config.showHeader, showFooter = false, wantMap = false,
        ).split
        val ringEdge = split.ring
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
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
                    minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.MEDIUM_SQUARE),
                    ringWidth = ringEdge,
                    frame = render.frame(LocalSize.current),
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
                            car, render, max = split.rows,
                            availableWidth = w, hideFields = setOf(WidgetInfoField.PERCENT),
                        )
                    },
                )
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    InfoStack(car, render, max = split.rows)
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
        val frame = render.frame(size)
        // Every child is handed the width this column actually gets, so the
        // header's name, the info rows, and the button row all judge their
        // own fit against the real space beside the ring rather than the
        // whole tile.
        // Rows bounded by what is actually LEFT, the way the sibling branch below does it, rather
        // than by Scale.infoCap's flat 38% of raw tile height. This is the no-gauge path, so the
        // column holds exactly a header, the rows and a button row plus two 6dp spacers -- every
        // term of that is a Scale function, so the remainder is computable instead of guessable.
        //
        // infoCap knew nothing about the header or the buttons, so whenever it came out tighter it
        // dropped rows that fit and the tile showed less than it had room for. Where it came out
        // LOOSER, the branch's own comment conceded the problem ("Unbudgeted branch"): nothing was
        // stopping the rows from pushing the buttons off the bottom.
        val rowsRoom = (
            Scale.innerHeight(frame) - Scale.headerHeight(frame) - Scale.buttonHeight(size) - 12.dp
            ).coerceAtLeast(0.dp)
        val content: @Composable (Dp) -> Unit = { w ->
            HeaderRow(car, render, availableWidth = w)
            Spacer(GlanceModifier.height(6.dp))
            InfoStack(
                car, render,
                max = Scale.infoRowsIn(size, rowsRoom, render.theme.textScale, 3),
                availableWidth = w,
            )
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
            val w = Scale.innerWidth(frame)
            val barH = Scale.barHeight(size)
            // WidgetLayout.mediumWideBarPlan owns the ringRoom(18dp, no footer) - bar -> tallSplit
            // (capRows 2) sequence -- the same plan the sweep's wide-medium-bar test calls, so
            // the reservation and the assertion can't drift. tallSplit reserves the map first
            // and hands rows the rest; the bar is subtracted before that three-way division.
            val split = WidgetLayout.mediumWideBarPlan(
                frame, showHeader = render.config.showHeader, barHeight = barH,
                wantMap = render.mapBitmap != null,
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
        // innerWidth, not size.width. The ring branch above hands its column
        // Scale.innerWidth(frame); this one handed over the RAW tile width, so the header's name,
        // the info rows and the button row all judged their own fit against space the root padding
        // had already taken -- two sibling branches of one function disagreeing about what "the
        // width available" means.
        Column(modifier = GlanceModifier.fillMaxSize()) { content(Scale.innerWidth(frame)) }
    }

    @Composable
    private fun MediumTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall MEDIUM: everything stacked in one column, ring centered --
        // the mirror of MediumWideLayout's side-by-side arrangement.
        val size = LocalSize.current
        val frame = render.frame(size)
        // Hand the ring everything left after the header, buttons, info rows
        // and the map's reserve, instead of a fixed curve plus a trailing
        // void -- see Scale.tallSplit for why the map has to be taken out
        // before the ring is sized rather than after.
        // WidgetLayout.ringHeroPlan owns this tier's spacer allowance (16dp) and capRows 3 (a
        // real per-tier maximum, not an infoCap fraction of raw tile height) -- the same plan the
        // sweep calls. MEDIUM_TALL has no footer and no primaryValue line.
        val split = WidgetLayout.ringHeroPlan(
            WidgetTier.MEDIUM_TALL, frame,
            showHeader = render.config.showHeader, showFooter = false,
            wantMap = render.mapBitmap != null,
        ).split
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
        val frame = render.frame(size)
        val w = Scale.innerWidth(frame)
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
        // WidgetLayout.wideBarPlan owns this tier's spacer allowance (30dp = the three explicit
        // 10dp Spacers below the header: before the hero, after it, before the buttons) and its
        // capRows 4 -- the same plan the sweep calls, so they can't drift.
        val split = WidgetLayout.wideBarPlan(
            WidgetTier.LARGE_WIDE, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
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
                    // Hide PERCENT only when the ring's BarHero is actually drawing it above.
                    // With the ring off, BarHero doesn't render (see the `if (showsRing)` block),
                    // so the Battery field is the ONLY place the percent would appear -- hiding it
                    // unconditionally dropped it entirely. Mirrors MediumTall/MediumSquare.
                    hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
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
        val frame = render.frame(size)
        // Through Scale.squareSplit so the info rows come out of the same
        // column budget as the ring instead of Scale.infoCap's fraction of the
        // raw tile height -- see squareSplit's note for what that overflowed.
        // WidgetLayout.squarePlan owns this tier's constants (20dp spacer allowance, capRows 4,
        // footer, map). The sweep calls the same plan.
        val split = WidgetLayout.squarePlan(
            WidgetTier.LARGE_SQUARE, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
        // Full-width map below the row, so here it competes with the ring for
        // the same column and has to be taken out of the ring's budget first.
        val mapRoom = split.map
        val ringEdge = split.ring
        // RingOrGlyph draws the percent-bearing ring only when both hold; otherwise the
        // icon-only glyph shows and carries no number.
        val showsRing = render.config.showRing && car.percent != null
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
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
                minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.LARGE_SQUARE),
                ringWidth = ringEdge,
                frame = render.frame(LocalSize.current),
                ring = {
                    RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
                },
                // Hide PERCENT only when the ring is drawn: RingImage's centerText already bakes
                // "82%" into the ring beside this stack, so showing the field too printed it
                // twice. But with the ring off the glyph carries no number, so the field is the
                // only place the percent appears -- hiding it unconditionally dropped it. Mirrors
                // MediumSquare/MediumTall.
                content = { w ->
                    InfoStack(
                        car, render, max = split.rows, availableWidth = w,
                        footerShown = true,
                        hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
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
        val frame = render.frame(size)
        // WidgetLayout.ringHeroPlan owns this tier's spacer allowance (20dp) and capRows 4 -- the
        // same plan the sweep calls. LARGE_TALL has a footer, no primaryValue line.
        val split = WidgetLayout.ringHeroPlan(
            WidgetTier.LARGE_TALL, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
        val ringEdge = split.ring
        // Matches RingOrGlyph's own guard: the ring (which bakes "82%" into its centre) draws
        // only when both hold; otherwise the icon-only glyph shows and carries no number.
        val showsRing = render.config.showRing && car.percent != null
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
            RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            Spacer(GlanceModifier.height(10.dp))
            // Hide PERCENT only when the ring is actually drawn (its centerText bakes "82%" in,
            // so showing the field too would print it twice). With the ring off the glyph shows
            // instead and carries no number, so the Battery field is the only place the percent
            // appears -- dropping it there hid it entirely. Mirrors MediumTallLayout.
            InfoStack(
                car, render, max = split.rows, footerShown = true,
                hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
            )
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
        val frame = render.frame(size)
        val w = Scale.innerWidth(frame)
        val showsRing = render.config.showRing && car.percent != null
        // See LargeWideLayout's own note: BarHero's default budget assumes
        // it's the row's only content, which isn't true here either, and
        // tallSplit does the map/row/leftover three-way split instead of
        // handing the map its own full natural height with nothing
        // subtracted from what everything else was budgeted against --
        // wantMap = true is the fix for the exact overflow LargeWideLayout's
        // own note describes.
        // WidgetLayout.wideBarPlan owns this tier's spacer allowance (42dp = three explicit 14dp
        // Spacers below the header) and its capRows (all info fields), shared with the sweep.
        val split = WidgetLayout.wideBarPlan(
            WidgetTier.XL_WIDE, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
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
                    // See LargeWideLayout: hide PERCENT only when the ring's BarHero draws it.
                    hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
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
        val frame = render.frame(size)
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
        // WidgetLayout.ringHeroPlan owns this tier's 36dp base spacer allowance PLUS the extra
        // title line it reserves for the primaryValue ("69% . 219 mi") drawn under the ring --
        // undercounting that line once overflowed the tile by up to 46dp. capRows is all info
        // fields. The sweep calls the same plan.
        val split = WidgetLayout.ringHeroPlan(
            WidgetTier.XL_TALL, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
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
            RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            Spacer(GlanceModifier.height(8.dp))
            // innerWidth + singleLine: this value line's height (primaryValueHeight, one
            // Scale.lineHeight) is reserved once in ringRoom's spacers, so it must not wrap;
            // and it sits inside Content's root padding, so innerWidth is its real width. The
            // old `- 24.dp` was both a guess at the padding and unbounded on lines.
            FitText(
                primaryValue(car, render), titleStyle(render.theme),
                maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
                singleLine = true,
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
        val frame = render.frame(size)
        // WidgetLayout.squarePlan owns this tier's constants (24dp spacer allowance, capRows 4,
        // footer, map) -- the same plan the sweep calls, so rows come out of the same column
        // budget as the ring instead of an infoCap fraction of raw tile height.
        val split = WidgetLayout.squarePlan(
            WidgetTier.XL_SQUARE, frame,
            showHeader = render.config.showHeader, showFooter = render.config.showFooter,
            wantMap = render.mapBitmap != null,
        ).split
        // Full-width map below the row, competing with the ring for the same
        // column -- reserved first, as in LargeSquareLayout.
        val mapRoom = split.map
        val ringEdge = split.ring
        // RingOrGlyph draws the percent-bearing ring only when both hold; else the glyph (no
        // number) shows, so the Battery field becomes the sole carrier of the percent.
        val showsRing = render.config.showRing && car.percent != null
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            FooterRow(car, render)
            Spacer(GlanceModifier.height(12.dp))
            ChargeBarFallback(car, render, ringEdge, split.ringRoom)
            RingWithContent(
                modifier = GlanceModifier.fillMaxWidth(),
                minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.XL_SQUARE),
                ringWidth = ringEdge,
                frame = render.frame(LocalSize.current),
                ring = {
                    RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
                },
                // Hide PERCENT only when the ring draws it -- see LargeSquareLayout's own note;
                // with the ring off the field is the only place the percent shows.
                content = { w ->
                    InfoStack(
                        car, render, max = split.rows, availableWidth = w,
                        footerShown = true,
                        hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
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
        /** Needed for [Scale.innerWidth] -- the padded content box, which is what `content`
         *  actually gets. Cannot be derived from LocalSize alone: rootPadding depends on the
         *  frame's pill corner. */
        frame: Scale.Frame,
        ring: @Composable () -> Unit,
        content: @Composable (Dp) -> Unit,
    ) {
        // TWO widths, deliberately, and they are not interchangeable.
        //
        // `tileWidth` is the raw tile and stays the BRANCH test: minRowWidth was tuned against
        // the raw width, so testing the padded width instead would silently flip the row/column
        // choice on every tile sitting near the threshold -- a layout change dressed up as a bug
        // fix.
        //
        // `inner` is the padded content box, and it is what `content` is handed. That was the
        // bug: content received the raw width, so a label was judged against space the root
        // padding had already taken. This function's own KDoc states the requirement -- "needs
        // that real number to decide whether its own text/buttons fit, because measuring against
        // the full tile width would let a label sail past the wrap threshold and get clipped in a
        // column half that wide" -- and the code handed over the full tile width.
        val tileWidth = LocalSize.current.width
        val inner = Scale.innerWidth(frame)
        if (tileWidth >= minRowWidth) {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) { ring() }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    content((inner - ringWidth - 12.dp).coerceAtLeast(24.dp))
                }
            }
        } else {
            Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                ring()
                Spacer(GlanceModifier.height(8.dp))
                content(inner)
            }
        }
    }

    // ---- Small pieces --------------------------------------------------------

    // Text styles and innerCorner now live in WidgetStyles.kt -- see that file
    // for why the styling leaf moved out ahead of the modules that draw with it.
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
internal data class WidgetTheme(
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
