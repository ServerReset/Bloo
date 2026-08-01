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

    /**
     * The layout tiers, smallest to largest, each with its own composable --
     * 14 in total, most of them (the 6 below MEDIUM) further doubled by
     * [WidgetConfig.priority] into a genuinely distinct info-vs-controls
     * layout, so a widget dropped small has real variety to grow into rather
     * than one shape stretched to fit every size. Every tier reuses the same
     * shared modules (HeaderRow, RingImage, InfoStack, ActionButtons, ...) --
     * what changes tier to tier is composition and proportion, not
     * reinvented logic, which is what keeps 14 layouts maintainable as one
     * set of building blocks instead of 14 independent implementations.
     */
    private enum class Tier {
        MICRO_TINY, MICRO,
        COMPACT_WIDE_NARROW, COMPACT_WIDE,
        COMPACT_TALL_NARROW, COMPACT_TALL,
        MEDIUM_SQUARE, MEDIUM_WIDE, MEDIUM_TALL,
        LARGE_WIDE, LARGE_TALL,
        XL_WIDE, XL_TALL, XL_SQUARE,
    }

    /** Below this width, [InfoStack] stops putting a value beside its label
     *  and starts stacking instead -- the same "give up on one line" width
     *  the original widget used for its own narrow-text fallback. */
    private val NARROW_WIDTH = 90.dp

    /**
     * Continuous size scaling, shared by every tier, so text/icons/padding/the
     * ring grow and shrink smoothly with the widget's exact measured size
     * instead of snapping between a handful of fixed per-tier constants --
     * the Tier enum still decides layout STRUCTURE (what appears, how it's
     * arranged), but everything about how big those pieces are now comes from
     * here, keyed off the same one signal ([progress]) at every tier boundary,
     * so nothing visibly jumps exactly where one tier hands off to the next.
     */
    private object Scale {
        // The real span this app's widget can ever be measured at: MIN matches
        // the manifest's declared floor (car_widget_info.xml minWidth/Height),
        // MAX is comfortably into XL territory -- past it every value here is
        // already at its ceiling, so a bigger widget just gets more empty
        // margin rather than ever-growing text.
        private const val MIN_DIM = 40f
        private const val MAX_DIM = 320f

        private fun lerp(t: Float, from: Float, to: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

        /** 0f at the smallest possible widget, 1f at MAX_DIM and up, based on
         *  the SHORTER measured side (the one that actually constrains how
         *  much can fit, regardless of how long the other side stretches). */
        fun progress(size: DpSize): Float {
            val short = minOf(size.width.value, size.height.value)
            return ((short - MIN_DIM) / (MAX_DIM - MIN_DIM)).coerceIn(0f, 1f)
        }

        /** The ring's continuous target size, capped by [maxAvailable] -- each
         *  tier already knows how much room it actually has left after its own
         *  header/button/footer siblings (see each RingImage call site's own
         *  comment), so this is deliberately two numbers combined: "how big
         *  the ring WANTS to be at this size" and "how big it's SAFE to be
         *  here," never just one or the other. */
        fun ring(size: DpSize, maxAvailable: Dp): Dp = minOf(lerp(progress(size), 28f, 140f).dp, maxAvailable)

        fun titleSp(size: DpSize): TextUnit = lerp(progress(size), 11f, 20f).sp
        fun subtitleSp(size: DpSize): TextUnit = lerp(progress(size), 9f, 13f).sp
        fun valueSp(size: DpSize): TextUnit = lerp(progress(size), 10f, 15f).sp

        /** The root content padding around every tier's layout. */
        fun contentPadding(size: DpSize): Dp = lerp(progress(size), 6f, 18f).dp

        fun buttonHeight(size: DpSize): Dp = lerp(progress(size), 32f, 48f).dp
        fun buttonIcon(size: DpSize): Dp = lerp(progress(size), 16f, 26f).dp
    }

    private fun tierFor(size: DpSize): Tier {
        val w = size.width.value
        val h = size.height.value
        val short = minOf(w, h)
        val aspect = w / h
        // Ordered largest-first so the first match wins; each size gate is the
        // same one the old 6-tier system used (roughly: XL ≥ 5x5, LARGE ≥
        // 4-wide, MEDIUM ≥ 2x2, the two COMPACT strips catching very lopsided
        // small sizes before the tiny floor) -- what's new is a second split
        // inside each band by aspect ratio (or, for the tiniest tiers, by
        // absolute size), so a wide 5x5 and a tall 5x5 actually get different
        // proportioned layouts instead of the same one letterboxed.
        return when {
            w >= 300f && h >= 300f -> when {
                aspect > 1.35f -> Tier.XL_WIDE
                aspect < 0.74f -> Tier.XL_TALL
                else -> Tier.XL_SQUARE
            }
            w >= 240f && h >= 170f -> if (aspect >= 1f) Tier.LARGE_WIDE else Tier.LARGE_TALL
            w >= 150f && h >= 150f -> when {
                aspect > 1.25f -> Tier.MEDIUM_WIDE
                aspect < 0.8f -> Tier.MEDIUM_TALL
                else -> Tier.MEDIUM_SQUARE
            }
            w >= 150f && h < 150f && w >= h * 1.6f -> if (w >= 220f) Tier.COMPACT_WIDE else Tier.COMPACT_WIDE_NARROW
            h >= 150f && w < 150f && h >= w * 1.4f -> if (h >= 220f) Tier.COMPACT_TALL else Tier.COMPACT_TALL_NARROW
            else -> if (short < 60f) Tier.MICRO_TINY else Tier.MICRO
        }
    }

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
        // Pill shape needs at least one short dimension to read as a stadium
        // rather than a barely-rounded rectangle, so it only ever kicks in at
        // small sizes (roughly 2x2 cells and under) -- same "only visible on
        // widgets sized about 2x2 cells or smaller" contract the original
        // widget's own pill option had, restored here rather than reinvented.
        // 999.dp clamps to a true pill against whichever side is shorter.
        val corner = if (render.config.pillShape && minOf(size.width, size.height) < 180.dp) 999.dp else 20.dp
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
        Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(corner)) {
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
                    Tier.MICRO_TINY -> MicroTinyLayout(car, effective)
                    Tier.MICRO -> MicroLayout(car, effective)
                    Tier.COMPACT_WIDE_NARROW -> CompactWideNarrowLayout(car, effective)
                    Tier.COMPACT_WIDE -> CompactWideLayout(car, effective)
                    Tier.COMPACT_TALL_NARROW -> CompactTallNarrowLayout(car, effective)
                    Tier.COMPACT_TALL -> CompactTallLayout(car, effective)
                    Tier.MEDIUM_SQUARE -> MediumSquareLayout(car, effective)
                    Tier.MEDIUM_WIDE -> MediumWideLayout(car, effective)
                    Tier.MEDIUM_TALL -> MediumTallLayout(car, effective)
                    Tier.LARGE_WIDE -> LargeWideLayout(car, effective)
                    Tier.LARGE_TALL -> LargeTallLayout(car, effective)
                    Tier.XL_WIDE -> XlWideLayout(car, effective)
                    Tier.XL_TALL -> XlTallLayout(car, effective)
                    Tier.XL_SQUARE -> XlSquareLayout(car, effective)
                }
            }
        }
    }

    // ---- Empty / signed-out --------------------------------------------------

    @Composable
    private fun EmptyState(root: GlanceModifier, theme: WidgetTheme) {
        Box(modifier = root.clickable(openAction(LocalContext.current)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Bloo",
                    style = TextStyle(color = theme.accentProvider, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "Open to sign in",
                    style = TextStyle(color = theme.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }

    // ---- Tier layouts --------------------------------------------------------

    /** True when this widget should show controls instead of info/ring at the
     *  MICRO/COMPACT_WIDE/COMPACT_TALL tiers -- see [WidgetConfig.priority]'s
     *  doc comment for why this only applies below MEDIUM. */
    private fun controlsPriority(render: Render) = render.config.priority == WidgetConfig.PRIORITY_CONTROLS

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
    private fun CompactWideNarrowLayout(car: VehicleSnapshot, render: Render) {
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 2)
            if (actions.isNotEmpty()) {
                ActionButtons(car, render, max = 2, modifier = GlanceModifier.fillMaxSize().padding(4.dp))
                return
            }
        }
        // Narrower than COMPACT_WIDE's own threshold -- no room for a
        // subtitle line beside the ring too, just the name, and only 2
        // buttons instead of 3.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height - 12.dp).coerceAtLeast(18.dp))
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.width(6.dp))
            }
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = size.width * 0.32f, modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            ActionButtons(car, render, max = 2, modifier = GlanceModifier.defaultWeight())
        }
    }

    @Composable
    private fun CompactWideLayout(car: VehicleSnapshot, render: Render) {
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 4)
            if (actions.isNotEmpty()) {
                ActionButtons(car, render, max = 4, modifier = GlanceModifier.fillMaxSize().padding(4.dp))
                return
            }
        }
        // COMPACT_WIDE's own tier threshold only proves the WIDTH is roomy
        // (>= 150dp) -- the height is whatever satisfies its aspect-ratio
        // gate against that width, which can be much shorter than the ring
        // wants to be. Scale.ring's continuous target is capped by whatever
        // height is actually available, so the ring can never be taller than
        // the row it's centered in.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height - 16.dp).coerceAtLeast(20.dp))
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.width(10.dp))
            }
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
                FitText(car.name, titleStyle(render.theme), maxWidth = size.width * 0.36f)
                PrimaryInfoLine(car, render)
            }
            Spacer(GlanceModifier.width(8.dp))
            ActionButtons(car, render, max = 3, modifier = GlanceModifier.defaultWeight())
        }
    }

    @Composable
    private fun CompactTallNarrowLayout(car: VehicleSnapshot, render: Render) {
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 2)
            if (actions.isNotEmpty()) {
                Box(GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                    ActionButtons(car, render, max = 2, vertical = true)
                }
                return
            }
        }
        // Shorter than COMPACT_TALL's own threshold -- name + ring/glyph +
        // a single button only, no room for the info stack too.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.width - 12.dp).coerceAtLeast(18.dp))
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
            ActionButtons(car, render, max = 1)
        }
    }

    @Composable
    private fun CompactTallLayout(car: VehicleSnapshot, render: Render) {
        if (controlsPriority(render)) {
            val actions = resolvedActions(car, render, max = 3)
            if (actions.isNotEmpty()) {
                Box(GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                    ActionButtons(car, render, max = 3, vertical = true)
                }
                return
            }
        }
        // Mirrors CompactWideLayout's own clamp: COMPACT_TALL's threshold only
        // proves the HEIGHT is roomy, not the width.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.width - 16.dp).coerceAtLeast(20.dp))
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                car.name, titleStyle(render.theme),
                maxWidth = size.width - 8.dp, horizontalAlignment = Alignment.CenterHorizontally,
            )
            Spacer(GlanceModifier.height(6.dp))
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.height(6.dp))
            }
            InfoStack(car, render, max = 2)
            Spacer(GlanceModifier.height(6.dp))
            ActionButtons(car, render, max = 2)
        }
    }

    @Composable
    private fun MediumSquareLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as the LARGE/XL tiers' own clamp: the header +
        // button rows can leave less than the ring's continuous target size
        // at MEDIUM's own minimum height (150dp).
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height * 0.5f).coerceAtLeast(36.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (render.config.showRing && car.percent != null) {
                    RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    Spacer(GlanceModifier.width(12.dp))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    InfoStack(car, render, max = 3)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            ActionButtons(car, render, max = 4)
        }
    }

    @Composable
    private fun MediumWideLayout(car: VehicleSnapshot, render: Render) {
        // Wide MEDIUM: put the ring beside the header/info/buttons stack
        // instead of above it, so a wide-but-short tile spends its extra
        // width on layout instead of leaving it empty beside a centered ring.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height * 0.7f).coerceAtLeast(36.dp))
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.width(12.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                HeaderRow(car, render)
                Spacer(GlanceModifier.height(6.dp))
                InfoStack(car, render, max = 3)
                Spacer(GlanceModifier.height(6.dp))
                ActionButtons(car, render, max = 4)
            }
        }
    }

    @Composable
    private fun MediumTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall MEDIUM: everything stacked in one column, ring centered --
        // the mirror of MediumWideLayout's side-by-side arrangement.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.width - 24.dp).coerceAtLeast(36.dp))
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.height(8.dp))
            }
            InfoStack(car, render, max = 3)
            Spacer(GlanceModifier.defaultWeight())
            ActionButtons(car, render, max = 4)
        }
    }

    @Composable
    private fun LargeWideLayout(car: VehicleSnapshot, render: Render) {
        // The ring's row shares this Column with the header/buttons/footer via
        // defaultWeight(), so at LARGE's own minimum height (170dp) the ring's
        // continuous target can be taller than what's actually left over once
        // those siblings claim their space -- unlike a plain size(), a
        // weighted row doesn't shrink the fixed-size Image inside it, it just
        // clips it. Capping by the tile's own measured height keeps the ring
        // proportioned at every size in between instead of only being safe at
        // the tier's two ends.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height * 0.42f).coerceAtLeast(40.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    } else {
                        StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
                    }
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    InfoStack(car, render, max = 4)
                    MapModule(render, heightDp = 72)
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            ActionButtons(car, render, max = 5)
            FooterRow(car, render)
        }
    }

    @Composable
    private fun LargeTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall LARGE: ring centered full-width above the info stack instead
        // of beside it -- there's more height to spend than width here, so a
        // side-by-side split would leave the info column cramped.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.width * 0.55f).coerceAtLeast(48.dp))
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
            }
            Spacer(GlanceModifier.height(10.dp))
            InfoStack(car, render, max = 4)
            MapModule(render, heightDp = 80)
            Spacer(GlanceModifier.defaultWeight())
            ActionButtons(car, render, max = 5)
            FooterRow(car, render)
        }
    }

    @Composable
    private fun XlWideLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as LargeWideLayout's own ringEdge clamp.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height * 0.42f).coerceAtLeast(60.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(14.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    } else {
                        StatusGlyph(car, render.theme, sizeDp = 88)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    FitText(primaryValue(car, render), titleStyle(render.theme), maxWidth = size.width * 0.4f, horizontalAlignment = Alignment.CenterHorizontally)
                }
                Spacer(GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    InfoStack(car, render, max = WidgetInfoField.ALL.size)
                    MapModule(render, heightDp = 96)
                }
            }
            Spacer(GlanceModifier.height(14.dp))
            ActionButtons(car, render, max = WidgetAction.ALL.size)
            FooterRow(car, render)
        }
    }

    @Composable
    private fun XlTallLayout(car: VehicleSnapshot, render: Render) {
        // Tall XL: one big centered ring up top with the primary value under
        // it, the full info stack and map stacked below rather than split
        // into side-by-side columns that would squeeze on a narrow-but-tall
        // dashboard-sized tile.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.width * 0.5f).coerceAtLeast(64.dp))
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(14.dp))
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
            InfoStack(car, render, max = WidgetInfoField.ALL.size)
            MapModule(render, heightDp = 96)
            Spacer(GlanceModifier.defaultWeight())
            ActionButtons(car, render, max = WidgetAction.ALL.size)
            FooterRow(car, render)
        }
    }

    @Composable
    private fun XlSquareLayout(car: VehicleSnapshot, render: Render) {
        // Square XL: a balanced ring-left / info-right split above a full-
        // width map, distinct from XlWideLayout's value-under-ring emphasis
        // and XlTallLayout's fully stacked column.
        val size = LocalSize.current
        val ringEdge = Scale.ring(size, (size.height * 0.38f).coerceAtLeast(56.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = ringEdge.value.toInt())
                    } else {
                        StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
                    }
                }
                Spacer(GlanceModifier.width(14.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    InfoStack(car, render, max = 4)
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            MapModule(render, heightDp = 88)
            Spacer(GlanceModifier.defaultWeight())
            ActionButtons(car, render, max = WidgetAction.ALL.size)
            FooterRow(car, render)
        }
    }

    // ---- Modules -------------------------------------------------------------

    @Composable
    private fun HeaderRow(car: VehicleSnapshot, render: Render) {
        val size = LocalSize.current
        // Rough reserve for the car-switcher pill (36dp + its own spacing)
        // when it's present -- an estimate, same spirit as every other
        // maxWidth passed to FitText in this file (see wouldOverflow).
        val pillReserve = if (render.multiCar && render.config.vin == null) 44.dp else 0.dp
        val textWidth = (size.width - pillReserve - 4.dp).coerceAtLeast(16.dp)
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
    @Composable
    private fun MapModule(render: Render, heightDp: Int) {
        val bmp = render.mapBitmap ?: return
        Spacer(GlanceModifier.height(8.dp))
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "Car location",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp).cornerRadius(14.dp),
        )
    }

    @Composable
    private fun PrimaryInfoLine(car: VehicleSnapshot, render: Render) {
        FitText(primaryValue(car, render), subtitleStyle(render.theme), maxWidth = LocalSize.current.width * 0.36f)
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
    private fun InfoStack(car: VehicleSnapshot, render: Render, max: Int) {
        val fields = render.config.infoFields.mapNotNull { WidgetInfoField.fromKey(it) }.take(max)
        val size = LocalSize.current
        val narrow = size.width < NARROW_WIDTH
        Column {
            fields.forEach { field ->
                val value = infoValue(field, car, render) ?: return@forEach
                if (narrow) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        FitText(field.label, subtitleStyle(render.theme), maxWidth = size.width - 4.dp)
                        FitText(value, valueStyle(render.theme), maxWidth = size.width - 4.dp)
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        FitText(
                            field.label, subtitleStyle(render.theme),
                            maxWidth = size.width * 0.5f, modifier = GlanceModifier.defaultWeight(),
                        )
                        FitText(value, valueStyle(render.theme), maxWidth = size.width * 0.45f)
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }

    /** Renders [text] one character per line, centered -- the universal
     *  fallback [FitText] drops down to whenever a label/value/name is
     *  estimated too wide for the line it's on, right down to a single
     *  character per row if that's what it takes (e.g. "82%" becoming
     *  "8" / "2" / "%") so nothing the widget shows is ever silently
     *  clipped or ellipsized, at any tier or any string length. */
    @Composable
    private fun VerticalText(text: String, style: TextStyle) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxWidth()) {
            text.forEach { ch -> if (!ch.isWhitespace()) Text(ch.toString(), maxLines = 1, style = style) }
        }
    }

    /** Rough estimate of whether [text] at [fontSize] would overflow
     *  [maxWidth] -- Glance/RemoteViews has no real text-measurement
     *  callback the way Compose's own `onTextLayout` does, so this is
     *  deliberately conservative (average glyph width ~0.6x font size)
     *  rather than exact; used only to decide ahead of time whether to fall
     *  back to [VerticalText], never to lay out pixel-perfect. */
    private fun wouldOverflow(text: String, fontSize: TextUnit?, maxWidth: Dp): Boolean {
        val sp = fontSize?.value ?: 12f
        return (text.length * sp * 0.6f) > maxWidth.value
    }

    /** Renders [text] on one line when it's estimated to fit inside
     *  [maxWidth], else falls back to [VerticalText] -- the shared "never
     *  cut off" contract every user-data label/value/name in the widget now
     *  goes through, generalized from what used to be a one-off fallback
     *  just for [WidgetInfoField.PERCENT]. */
    @Composable
    private fun FitText(
        text: String,
        style: TextStyle,
        maxWidth: Dp,
        modifier: GlanceModifier = GlanceModifier,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    ) {
        if (text.isBlank()) return
        if (wouldOverflow(text, style.fontSize, maxWidth)) {
            Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
                VerticalText(text, style)
            }
        } else {
            Text(text, style = style, maxLines = 1, modifier = modifier)
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
    ) {
        val actions = resolvedActions(car, render, max)
        if (actions.isEmpty()) return
        if (vertical) {
            Column(modifier = modifier) {
                actions.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.height(6.dp))
                    ActionButton(action, car, render, modifier = GlanceModifier.fillMaxWidth())
                }
            }
        } else {
            Row(modifier = modifier) {
                actions.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    ActionButton(action, car, render, modifier = GlanceModifier.defaultWeight())
                }
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
        iconSize: Dp = Scale.buttonIcon(LocalSize.current),
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
            modifier = (if (fixedHeight) modifier.height(Scale.buttonHeight(size)) else modifier)
                .background(bg)
                .cornerRadius(14.dp)
                .clickable(click),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconFor(action)),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(theme.onAccent),
                modifier = GlanceModifier.size(iconSize),
            )
        }
    }

    // ---- Small pieces --------------------------------------------------------

    @Composable
    private fun RingImage(car: VehicleSnapshot, render: Render, edgeDp: Int) {
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
        )
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "${car.percent ?: 0} percent",
            modifier = GlanceModifier.size(edgeDp.dp),
        )
    }

    @Composable
    private fun StatusGlyph(car: VehicleSnapshot, theme: WidgetTheme, sizeDp: Int) {
        val locked = car.locked == true
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
        Box(
            modifier = GlanceModifier.size(36.dp).cornerRadius(12.dp)
                .background(theme.surfaceVariant).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = "Switch car",
                colorFilter = ColorFilter.tint(theme.onSurfaceVariant),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }

    // ---- Text styles ---------------------------------------------------------
    // @Composable (not plain functions) purely so each can read LocalSize.current
    // itself -- every call site is already inside composition, so this scales
    // font size continuously with the widget's exact measured size (see Scale)
    // without having to thread a size param through every single caller.

    @Composable
    private fun titleStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurface, fontSize = Scale.titleSp(LocalSize.current), fontWeight = FontWeight.Bold)
    @Composable
    private fun subtitleStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurfaceVariant, fontSize = Scale.subtitleSp(LocalSize.current))
    @Composable
    private fun valueStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurface, fontSize = Scale.valueSp(LocalSize.current), fontWeight = FontWeight.Medium)

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

    private fun infoValue(field: WidgetInfoField, car: VehicleSnapshot, render: Render): String? = when (field) {
        WidgetInfoField.RANGE -> car.rangeMi?.let { formatDistance(it.toDouble(), render.metric) }
        WidgetInfoField.PERCENT -> car.percent?.let { "$it%" }
        WidgetInfoField.ODOMETER -> car.odometer?.takeIf { it.isNotBlank() }
        WidgetInfoField.PLATE -> car.licensePlate?.takeIf { it.isNotBlank() }
        WidgetInfoField.SERVICE -> serviceDueLabel(car, render.metric)
        WidgetInfoField.UPDATED -> relativeLabel(car.fetchedAt.takeIf { it > 0 }).takeIf { it.isNotBlank() }
    }

    private fun serviceDueLabel(car: VehicleSnapshot, metric: Boolean): String? {
        val last = car.lastServiceMiles ?: return null
        val interval = car.serviceIntervalMiles ?: return null
        val odo = car.odometer?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        val due = (last + interval - odo).coerceAtLeast(0)
        return "in ${formatDistance(due, metric)}"
    }

    private fun iconFor(action: WidgetAction): Int = when (action) {
        WidgetAction.LOCK -> R.drawable.ic_shortcut_lock
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
            val onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF1B1C20)
            val onSurfaceVariant = if (isDark) Color(0xFFC6C6CC) else Color(0xFF5C5E66)
            val surfaceVariant = if (isDark) Color(0xFF2A2C32) else Color(0xFFE7E7EC)
            return WidgetTheme(
                isDark = isDark,
                accent = accent,
                accentArgb = accent.toArgb(),
                accentProvider = ColorProvider(accent),
                onAccent = ColorProvider(onAccent),
                background = ColorProvider(background),
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
