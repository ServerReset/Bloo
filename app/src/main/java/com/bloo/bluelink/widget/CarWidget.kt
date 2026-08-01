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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.relativeLabel
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
        val car = config.vin?.let { vin -> data.vehicles.firstOrNull { it.vin == vin } } ?: data.selected
        val appearance = runCatching { SettingsStore(context).appearance.first() }.getOrNull()
        val metric = appearance?.unitSystem == "metric"
        val stale = car?.fetchedAt?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - it > com.bloo.bluelink.data.STALE_STATUS_MS
        } ?: false
        // The location map is fetched here (suspend) — not in the composables, which
        // can't do I/O — when the user enabled it and the car has coordinates. Sized
        // generously; the Image just scales it down for smaller layout slots.
        val mapBitmap = if (config.showMap && car?.lat != null && car.lon != null) {
            val density = context.resources.displayMetrics.density
            val edge = (150 * density).toInt()
            val accent = config.accent?.let { WidgetAccent.fromKey(it)?.argb } ?: BlooColors.cool
            runCatching { WidgetMap.render(context, car.lat!!, car.lon!!, edge, accent) }.getOrNull()
        } else null
        val render = Render(
            car = car,
            config = config,
            metric = metric,
            multiCar = data.vehicles.size > 1,
            stale = stale,
            mapBitmap = mapBitmap,
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
    )

    /** The layout tiers, smallest to largest. Chosen from the measured size. */
    private enum class Tier { MICRO, COMPACT_WIDE, COMPACT_TALL, MEDIUM, LARGE, XL }

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
        val root = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
        if (car == null) {
            EmptyState(root)
            return
        }
        val size = LocalSize.current
        Box(modifier = root) {
            when (tierFor(size)) {
                Tier.MICRO -> MicroLayout(car, render)
                Tier.COMPACT_WIDE -> CompactWideLayout(car, render)
                Tier.COMPACT_TALL -> CompactTallLayout(car, render)
                Tier.MEDIUM -> MediumLayout(car, render)
                Tier.LARGE -> LargeLayout(car, render)
                Tier.XL -> XlLayout(car, render)
            }
        }
    }

    // ---- Empty / signed-out --------------------------------------------------

    @Composable
    private fun EmptyState(root: GlanceModifier) {
        Box(modifier = root.clickable(openAction(LocalContext.current)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Bloo",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "Open to sign in",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }

    // ---- Tier layouts --------------------------------------------------------

    @Composable
    private fun MicroLayout(car: VehicleSnapshot, render: Render) {
        // A single glance: the ring if this car has a battery + ring is on, else a
        // lock glyph. Whole tile opens the app.
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
            contentAlignment = Alignment.Center,
        ) {
            if (car.hasBattery && render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = 64)
            } else {
                StatusGlyph(car, sizeDp = 40)
            }
        }
    }

    @Composable
    private fun CompactWideLayout(car: VehicleSnapshot, render: Render) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (car.hasBattery && render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = 56)
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(car.name, style = titleStyle(), maxLines = 1)
                PrimaryInfoLine(car, render)
            }
            Spacer(GlanceModifier.width(8.dp))
            ActionButtons(car, render, max = 3)
        }
    }

    @Composable
    private fun CompactTallLayout(car: VehicleSnapshot, render: Render) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(car.name, style = titleStyle(), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            if (car.hasBattery && render.config.showRing && car.percent != null) {
                RingImage(car, render, edgeDp = 72)
                Spacer(GlanceModifier.height(6.dp))
            }
            InfoStack(car, render, max = 2)
            Spacer(GlanceModifier.height(6.dp))
            ActionButtons(car, render, max = 2)
        }
    }

    @Composable
    private fun MediumLayout(car: VehicleSnapshot, render: Render) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (car.hasBattery && render.config.showRing && car.percent != null) {
                    RingImage(car, render, edgeDp = 76)
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
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (car.hasBattery && render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = 96)
                    } else {
                        StatusGlyph(car, sizeDp = 56)
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
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render)
            Spacer(GlanceModifier.height(14.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (car.hasBattery && render.config.showRing && car.percent != null) {
                        RingImage(car, render, edgeDp = 140)
                    } else {
                        StatusGlyph(car, sizeDp = 88)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Text(primaryValue(car, render), style = titleStyle(), maxLines = 1)
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
                Text(car.name, style = titleStyle(), maxLines = 1)
                Text(statusSubtitle(car), style = subtitleStyle(), maxLines = 1)
            }
            if (render.multiCar && render.config.vin == null) {
                // Follow-selected widgets get a car switcher chevron.
                IconPill(
                    iconRes = R.drawable.ic_shortcut_car,
                    onClick = actionRunCallback<WidgetSwitchCarAction>(),
                    tint = GlanceTheme.colors.onSurfaceVariant,
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
            else subtitleStyle()
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
        Text(primaryValue(car, render), style = subtitleStyle(), maxLines = 1)
    }

    /** The stacked read-only stats, honoring the user's chosen fields + order,
     *  capped to what fits ([max]). */
    @Composable
    private fun InfoStack(car: VehicleSnapshot, render: Render, max: Int) {
        val fields = render.config.infoFields.mapNotNull { WidgetInfoField.fromKey(it) }.take(max)
        Column {
            fields.forEach { field ->
                val value = infoValue(field, car, render) ?: return@forEach
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(field.label, style = subtitleStyle(), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(value, style = valueStyle(), maxLines = 1)
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }

    /** The configured action buttons, capped to [max] for the current size. */
    @Composable
    private fun ActionButtons(car: VehicleSnapshot, render: Render, max: Int) {
        val actions = render.config.actions.mapNotNull { WidgetAction.fromKey(it) }
            .filter { it != WidgetAction.CHARGE || car.hasBattery } // hide Charge on non-EV
            .take(max)
        if (actions.isEmpty()) return
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            actions.forEachIndexed { i, action ->
                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                ActionButton(action, car, render, modifier = GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun ActionButton(action: WidgetAction, car: VehicleSnapshot, render: Render, modifier: GlanceModifier) {
        val active = when (action) {
            WidgetAction.LOCK -> car.locked == true
            WidgetAction.CLIMATE -> car.climateOn == true
            WidgetAction.CHARGE -> car.charging == true
            else -> false
        }
        val bg = if (active) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
        val fg = if (active) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
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
            modifier = modifier
                .height(44.dp)
                .background(bg)
                .cornerRadius(14.dp)
                .clickable(click),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconFor(action)),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(fg),
                modifier = GlanceModifier.size(22.dp),
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
        val accent = render.config.accent?.let { WidgetAccent.fromKey(it)?.argb } ?: DEFAULT_ACCENT
        val arc = ChargeRing.arcColorFor(frac, car.charging == true, accent)
        val track = TRACK_COLOR
        val bmp = ChargeRing.render(
            sizePx = px,
            fraction = frac,
            arcColor = arc,
            trackColor = track,
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
    private fun StatusGlyph(car: VehicleSnapshot, sizeDp: Int) {
        val locked = car.locked == true
        val res = if (locked) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
        val tint = if (locked) GlanceTheme.colors.primary else GlanceTheme.colors.error
        Image(
            provider = ImageProvider(res),
            contentDescription = if (locked) "Locked" else "Unlocked",
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(sizeDp.dp),
        )
    }

    @Composable
    private fun IconPill(iconRes: Int, onClick: androidx.glance.action.Action, tint: ColorProvider) {
        Box(
            modifier = GlanceModifier.size(36.dp).cornerRadius(12.dp)
                .background(GlanceTheme.colors.secondaryContainer).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = "Switch car",
                colorFilter = ColorFilter.tint(tint),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }

    // ---- Text styles ---------------------------------------------------------

    @Composable private fun titleStyle() =
        TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    @Composable private fun subtitleStyle() =
        TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    @Composable private fun valueStyle() =
        TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)

    // ---- Value helpers -------------------------------------------------------

    private fun statusSubtitle(car: VehicleSnapshot): String {
        val parts = mutableListOf<String>()
        car.locked?.let { parts += if (it) "Locked" else "Unlocked" }
        if (car.charging == true) parts += "Charging"
        if (car.climateOn == true) parts += "Climate on"
        if (car.engineOn == true) parts += "Running"
        return parts.joinToString(" · ").ifBlank { car.model }
    }

    private fun primaryValue(car: VehicleSnapshot, render: Render): String = when {
        car.hasBattery && car.percent != null -> "${car.percent}%" +
            (car.rangeMi?.let { " · ${formatDistance(it, render.metric)}" } ?: "")
        car.rangeMi != null -> formatDistance(car.rangeMi, render.metric)
        else -> car.model
    }

    private fun infoValue(field: WidgetInfoField, car: VehicleSnapshot, render: Render): String? = when (field) {
        WidgetInfoField.RANGE -> car.rangeMi?.let { formatDistance(it, render.metric) }
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
        WidgetAction.HORN -> R.drawable.ic_widget_flash
        WidgetAction.REFRESH -> R.drawable.ic_widget_refresh
        WidgetAction.OPEN -> R.drawable.ic_shortcut_car
    }

    // Glance's reified actionStartActivity<T>() overload isn't available here, so
    // build the Intent explicitly (the (Intent, …) overload is the stable one).
    private fun openAction(context: Context) =
        actionStartActivity(Intent(context, MainActivity::class.java))

    private companion object {
        // Deterministic ring colours (the widget process can't easily resolve a
        // theme ColorProvider to an int for the Canvas). Accent defaults to Bloo's
        // brand blue; charging/low states override in ChargeRing.arcColorFor.
        val DEFAULT_ACCENT = BlooColors.cool
        val TRACK_COLOR = 0x33888888
    }
}