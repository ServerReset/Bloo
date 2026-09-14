package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * A deliberately basic home-screen widget: the car's name, its current
 * charge/fuel percentage, and a simple 3-segment level gauge (the same
 * red/amber/green bands a dashboard fuel or battery gauge uses).
 *
 * Built with only standard Glance components -- [GlanceTheme] for colors
 * (so it follows the device's Material You / dark-light state automatically,
 * with no separate theme-resolution system of its own to maintain), plain
 * [Column]/[Row]/[Text]/[Box], and [defaultWeight] for the three equal gauge
 * segments. No config screen, no per-widget options, no photo backgrounds --
 * just the one glanceable readout.
 *
 * [SizeMode.Exact] plus [LocalSize] lets [Content] pick between two layouts
 * so the widget still looks intentional at every size a launcher might grant
 * it: a single compact row at very small/short sizes (a 1x1 or 2x1 grid
 * cell), and the full name + percent + gauge layout everywhere else. Text
 * sizes and paddings also scale a little with the measured size so a large
 * widget doesn't look like a small one just stretched.
 */
class CarWidget : GlanceAppWidget() {

    // Exact, not Responsive: recomposes for the real current size on every
    // resize, so Content always tunes itself to the actual dimensions
    // instead of snapping to a handful of preset buckets.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppLog.log("Widget: provideGlance started")
        runCatching { WidgetRefreshWorker.schedule(context) }

        // All I/O happens here, once, before composition -- the composables
        // below do no suspending work of their own.
        val car = try {
            SnapshotStore(context).current().selected
        } catch (t: Throwable) {
            AppLog.log("Widget: failed to load snapshot: ${t.message}")
            null
        }

        provideContent {
            GlanceTheme {
                Content(car)
            }
        }
    }
}

@Composable
private fun Content(car: VehicleSnapshot?) {
    val size = LocalSize.current
    // Below roughly 2 grid cells tall there's no room for the gauge bar
    // without either clipping it or squeezing the text unreadably small --
    // this is the same "drop to the compact form instead of cramming"
    // rule the rest of the app's own layouts use.
    val compact = size.height < 80.dp

    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            car == null -> Text(
                "No car",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
            )
            compact -> CompactLayout(car)
            else -> FullLayout(car, size.height)
        }
    }
}

/** Very small sizes (roughly a 1x1 or 2x1 grid cell): name and percent share
 *  one row, no gauge -- there isn't a legible amount of vertical room for one. */
@Composable
private fun CompactLayout(car: VehicleSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            car.name,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            percentLabel(car),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Everything above the compact threshold: name row, a big percent readout
 *  with its "Charge"/"Fuel" label, and the 3-segment gauge underneath. */
@Composable
private fun FullLayout(car: VehicleSnapshot, heightDp: Dp) {
    // A little extra headroom on genuinely tall/large widgets reads as
    // "designed for this size" rather than the same small card just
    // floating in more empty space.
    val big = heightDp > 160.dp
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            car.name,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(if (big) 10.dp else 6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                percentLabel(car),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (big) 34.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (car.hasBattery) "Charge" else "Fuel",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp),
            )
        }
        Spacer(GlanceModifier.height(if (big) 14.dp else 10.dp))
        LevelGauge(percent = car.percent, height = if (big) 14.dp else 10.dp)
    }
}

/** Fallback text for a car with no reported percent yet, rather than a bare
 *  blank where a number belongs. */
private fun percentLabel(car: VehicleSnapshot): String = car.percent?.let { "$it%" } ?: "--"

/**
 * The 3-segment level gauge: equal red/amber/green bands, each drawn at full
 * strength once [percent] has reached into it and dimmed (a plain muted
 * track) otherwise -- the same "how many bars are lit" reading a fuel or
 * battery gauge gives at a glance, without needing an exact number to make
 * sense of it.
 *
 * Bands are fixed thirds (0-33 / 33-67 / 67-100) rather than tied to any
 * per-car charge limit -- this widget has none of that config, and thirds
 * read the same way for a gas tank as they do for a battery.
 */
@Composable
private fun LevelGauge(percent: Int?, height: Dp) {
    val pct = (percent ?: 0).coerceIn(0, 100)
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        horizontalAlignment = Alignment.Start,
    ) {
        GaugeSegment(litColor = BlooColors.heat, reached = pct > 0, modifier = GlanceModifier.defaultWeight().fillMaxSize())
        Spacer(GlanceModifier.width(3.dp))
        GaugeSegment(litColor = BlooColors.warn, reached = pct > 33, modifier = GlanceModifier.defaultWeight().fillMaxSize())
        Spacer(GlanceModifier.width(3.dp))
        GaugeSegment(litColor = BlooColors.chargeGreen, reached = pct > 67, modifier = GlanceModifier.defaultWeight().fillMaxSize())
    }
}

@Composable
private fun GaugeSegment(litColor: Int, reached: Boolean, modifier: GlanceModifier) {
    val color = if (reached) Color(litColor) else Color(litColor).copy(alpha = 0.18f)
    Box(modifier = modifier.background(ColorProvider(color)).cornerRadius(4.dp)) {}
}
