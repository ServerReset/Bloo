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

    /** The layout tiers, smallest to largest. Chosen from the measured size. */
    private enum class Tier { MICRO, COMPACT_WIDE, COMPACT_TALL, MEDIUM, LARGE, XL }

    /** Below this width, [InfoStack] stops putting a value beside its label
     *  and starts stacking instead -- the same "give up on one line" width
     *  the original widget used for its own narrow-text fallback. */
    private val NARROW_WIDTH = 90.dp

    private fun tierFor(size: DpSize): Tier {
        val w = size.width.value
        val h = size.height.value
        // Ordered largest-first so the first match wins; each threshold is a clean
        // dp gate (roughly: XL ≥ 5x5, LARGE ≥ 4-wide, MEDIUM ≥ 2x2), with the two
        // COMPACT strips catching very lopsided small sizes before the tiny floor.
        return when {
            w >= 300f && h >= 300f -> Tier.XL
            w >= 240f && h >= 170f -> Tier.LARGE
            w >= 150f && h >= 150f -> Tier.MEDIUM
            w >= 150f && h < 150f && w >= h * 1.6f -> Tier.COMPACT_WIDE
            h >= 150f && w < 150f && h >= w * 1.4f -> Tier.COMPACT_TALL
            else -> Tier.MICRO
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
        val root = (if (photo == null) outerCorner.background(effective.theme.background) else outerCorner)
            .padding(if (corner >= 999.dp) 16.dp else 12.dp)
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
                    Tier.MICRO -> MicroLayout(car, effective)
                    Tier.COMPACT_WIDE -> CompactWideLayout(car, effective)
                    Tier.COMPACT_TALL -> CompactTallLayout(car, effective)
                    Tier.MEDIUM -> MediumLayout(car, effective)
                    Tier.LARGE -> LargeLayout(car, effective)
                    Tier.XL -> XlLayout(car, effective)
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
    private fun MicroLayout(car: VehicleSnapshot, render: Render) {
        // MICRO is the unbounded catch-all tier -- every other tier's own
        // threshold proves a minimum size the tier's fixed dp values were
        // designed to fit inside, but MICRO has no such floor beyond the
        // manifest's declared 40dp minimum (car_widget_info.xml). A ring/
        // glyph/icon sized for a "normal" ~64dp micro tile would overflow a
        // real 40dp one, so every size here scales down from its usual value
        // to whatever's actually measured, rather than assuming there's
        // always at least that much room.
        val size = LocalSize.current
        val fit = (minOf(size.width, size.height) - 20.dp).coerceAtLeast(12.dp)
        // Controls priority at this size means "this widget IS one button" --
        // there's no room for a real row of buttons at a usable tap size, so
        // just the first configured action fills the whole tile.
        if (controlsPriority(render)) {
            val action = resolvedActions(car, render, max = 1).firstOrNull()
            if (action != null) {
                val iconSize = fit.coerceIn(14.dp, 30.dp)
                ActionButton(action, car, render, modifier = GlanceModifier.fillMaxSize(), fixedHeight = false, iconSize = iconSize)
                return
            }
        }
        // A single glance: the fuel/charge ring if there's a percent to show and
        // the ring is on, else a lock glyph. Whole tile opens the app.
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            contentAlignment = Alignment.Center,
        ) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = fit.coerceIn(20.dp, 64.dp).value.toInt())
            } else {
                StatusGlyph(car, render.theme, sizeDp = fit.coerceIn(14.dp, 40.dp).value.toInt())
            }
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
        // gate against that width, which can be much shorter than the ring's
        // usual 56dp. Clamp to what's actually measured so the ring can never
        // be taller than the row it's centered in.
        val ringEdge = (LocalSize.current.height - 16.dp).coerceIn(20.dp, 56.dp)
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = ringEdge.value.toInt())
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(car.name, style = titleStyle(render.theme), maxLines = 1)
                PrimaryInfoLine(car, render)
            }
            Spacer(GlanceModifier.width(8.dp))
            ActionButtons(car, render, max = 3)
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
        // proves the HEIGHT is roomy, not the width, which can be much
        // narrower than the ring's usual 72dp.
        val ringEdge = (LocalSize.current.width - 16.dp).coerceIn(20.dp, 72.dp)
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(car.name, style = titleStyle(render.theme), maxLines = 1)
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
    private fun MediumLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as LargeLayout/XlLayout's own clamp: the header +
        // button rows can leave less than 76dp for the ring's weighted row at
        // MEDIUM's own minimum height (150dp).
        val ringEdge = (LocalSize.current.height * 0.5f).coerceIn(36.dp, 76.dp)
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
    private fun LargeLayout(car: VehicleSnapshot, render: Render) {
        // The ring's row shares this Column with the header/buttons/footer via
        // defaultWeight(), so at LARGE's own minimum height (170dp) a fixed
        // 96dp ring can be taller than what's actually left over once those
        // siblings claim their space -- unlike a plain size(), a weighted row
        // doesn't shrink the fixed-size Image inside it, it just clips it.
        // Scaling off the tile's own measured height (capped at the original
        // 96dp design size) keeps the ring proportioned at every size in
        // between instead of only being safe at the two ends.
        val ringEdge = (LocalSize.current.height * 0.42f).coerceIn(40.dp, 96.dp)
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
                        StatusGlyph(car, render.theme, sizeDp = 56)
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
    private fun XlLayout(car: VehicleSnapshot, render: Render) {
        // Same reasoning as LargeLayout's own ringEdge clamp.
        val ringEdge = (LocalSize.current.height * 0.42f).coerceIn(60.dp, 140.dp)
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
                    Text(primaryValue(car, render), style = titleStyle(render.theme), maxLines = 1)
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

    // ---- Modules -------------------------------------------------------------

    @Composable
    private fun HeaderRow(car: VehicleSnapshot, render: Render) {
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(openAction(LocalContext.current)), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(car.name, style = titleStyle(render.theme), maxLines = 1)
                Text(statusSubtitle(car), style = subtitleStyle(render.theme), maxLines = 1)
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
            Text(
                text,
                style = style,
                maxLines = 1,
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
        Text(primaryValue(car, render), style = subtitleStyle(render.theme), maxLines = 1)
    }

    /** The stacked read-only stats, honoring the user's chosen fields + order,
     *  capped to what fits ([max]). Glance has no overflow-detection callback
     *  the way real Compose Text does (RemoteViews just silently ellipsizes),
     *  so "might not fit" is decided ahead of time from the measured tile
     *  width instead of reactively -- below [NARROW_WIDTH] every row drops
     *  the label beside its value in favour of stacking the value on its own
     *  full-width line underneath, and [WidgetInfoField.PERCENT] specifically
     *  falls back further to one digit per line (see [VerticalDigits]) if
     *  even that's tight, so a reading like "82%" degrades to
     *
     *  8
     *  2
     *  %
     *
     *  rather than ever being cut off. */
    @Composable
    private fun InfoStack(car: VehicleSnapshot, render: Render, max: Int) {
        val fields = render.config.infoFields.mapNotNull { WidgetInfoField.fromKey(it) }.take(max)
        val narrow = LocalSize.current.width < NARROW_WIDTH
        Column {
            fields.forEach { field ->
                val value = infoValue(field, car, render) ?: return@forEach
                if (narrow) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(field.label, style = subtitleStyle(render.theme), maxLines = 1)
                        if (field == WidgetInfoField.PERCENT && value.length > 3) {
                            VerticalDigits(value, valueStyle(render.theme))
                        } else {
                            Text(value, style = valueStyle(render.theme), maxLines = 1)
                        }
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(field.label, style = subtitleStyle(render.theme), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(value, style = valueStyle(render.theme), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }

    /** Renders [text] one character per line, centered -- the fallback for a
     *  short but wide readout (a percent, mainly) that still wouldn't fit on
     *  its own full-width line at the narrowest tiers. */
    @Composable
    private fun VerticalDigits(text: String, style: TextStyle) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxWidth()) {
            text.forEach { ch -> Text(ch.toString(), maxLines = 1, style = style) }
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
        iconSize: Dp = 22.dp,
    ) {
        val theme = render.theme
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
            modifier = (if (fixedHeight) modifier.height(44.dp) else modifier)
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

    private fun titleStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    private fun subtitleStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurfaceVariant, fontSize = 12.sp)
    private fun valueStyle(theme: WidgetTheme) =
        TextStyle(color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)

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
                trackArgb = if (isDark) 0x33FFFFFF else 0x26000000,
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
