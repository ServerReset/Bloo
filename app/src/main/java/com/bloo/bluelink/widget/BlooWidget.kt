package com.bloo.bluelink.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.defaultWeight
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.R
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.vehicleStateLabel
import com.bloo.bluelink.ui.resolveWidgetAccent
import kotlinx.coroutines.flow.first
import kotlin.math.min

class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private class Theme(
        val accent: ColorProvider,
        val onAccent: ColorProvider,
    )

    private val chargeGreen = Color(0xFF2EBD59)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = settings.widgetConfig(widgetId)
        val snap = cfg?.let { c ->
            SnapshotStore(context).current().vehicles.firstOrNull { it.vin == c.first }
        }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        val appearance = settings.appearance.first()
        val accentColor = resolveWidgetAccent(context, appearance, snap?.vin)
        val onAccent = if (accentColor.luminance() > 0.5f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, 0.22f)))
        else Color.White
        val theme = Theme(ColorProvider(accentColor), ColorProvider(onAccent))

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val corner = when {
                    w < 80.dp || h < 80.dp -> 14.dp
                    w < 160.dp || h < 100.dp -> 18.dp
                    else -> 24.dp
                }
                val base = GlanceModifier.fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(corner)

                when {
                    snap == null -> UnconfiguredBox(widgetId, base)
                    w < 80.dp && h < 80.dp -> TinyLayout(snap, base, theme)
                    h < 70.dp -> CompactLayout(snap, actions, w, base, theme)
                    else -> FullLayout(snap, actions, w, h, base, theme)
                }
            }
        }
    }

    @Composable
    private fun UnconfiguredBox(widgetId: Int, base: GlanceModifier) {
        val context = LocalContext.current
        Box(
            modifier = base.clickable(actionStartActivity(configIntent(context, widgetId))),
            contentAlignment = Alignment.Center,
        ) {
            Text("Tap to set up", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium))
        }
    }

    private fun stateOf(snap: VehicleSnapshot, theme: Theme): Pair<String, ColorProvider> {
        val label = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        val color = when {
            snap.engineOn == true -> theme.accent
            snap.charging == true -> ColorProvider(chargeGreen)
            else -> theme.accent
        }
        return label to color
    }

    @Composable
    private fun StateChip(label: String, color: ColorProvider) {
        Box(
            GlanceModifier.background(color).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun TinyLayout(snap: VehicleSnapshot, base: GlanceModifier, theme: Theme) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, 0, snap.vin, WidgetAction.OPEN))
        val (_, stateColor) = stateOf(snap, theme)
        Box(base.clickable(open), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(GlanceModifier.height(2.dp))
                Box(GlanceModifier.size(5.dp).background(stateColor).cornerRadius(3.dp)) {}
            }
        }
    }

    @Composable
    private fun CompactLayout(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, 0, snap.vin, WidgetAction.OPEN))
        val narrow = w < 120.dp
        val maxBtns = if (narrow) 0 else min(actions.size, ((w - 100.dp) / 44.dp).toInt().coerceIn(0, 4))
        Box(base.padding(horizontal = 8.dp, vertical = 4.dp).clickable(open)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    if (!narrow) Text(snap.name.take(16), maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (narrow) 14.sp else 16.sp))
                        snap.rangeMi?.let { Spacer(GlanceModifier.width(4.dp)); Text("· ${it}mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
                    }
                }
                actions.take(maxBtns).forEach { a ->
                    ActionPill(0, snap.vin, a, 28.dp, snap, theme)
                }
            }
        }
    }

    @Composable
    private fun FullLayout(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, h: Dp, base: GlanceModifier, theme: Theme) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, 0, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap, theme)
        val btnH = 34.dp
        val portrait = h > w * 1.1f
        val perRow = if (portrait) 2 else 4

        Column(base.padding(12.dp).clickable(open)) {
            // Name + state chip row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.name.take(20), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                StateChip(stateLabel, stateColor)
            }
            Spacer(GlanceModifier.height(6.dp))
            // Big percent + range
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (w > 200.dp) 40.sp else 28.sp))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)) }
                    Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
            // Battery bar
            Spacer(GlanceModifier.height(4.dp))
            val pct = (snap.percent ?: 0).coerceIn(0, 100)
            Box(GlanceModifier.fillMaxWidth().height(5.dp).background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.25f))).cornerRadius(3.dp)) {
                if (pct > 0) Box(GlanceModifier.fillMaxWidth(pct / 100f).height(5.dp).background(theme.accent).cornerRadius(3.dp)) {}
            }
            // Detail row
            Spacer(GlanceModifier.height(6.dp))
            Row {
                DetailCell("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                DetailCell("Climate", if (snap.climateOn == true) "On" else "Off", GlanceModifier.defaultWeight())
            }
            // Action buttons
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                val chunks = actions.take(perRow * 2).chunked(perRow)
                chunks.forEach { chunk ->
                    Row {
                        chunk.forEachIndexed { i, a ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            ActionPill(0, snap.vin, a, btnH, snap, theme)
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun DetailCell(label: String, value: String, modifier: GlanceModifier) {
        Column(modifier.background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.12f))).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun ActionPill(widgetId: Int, vin: String, action: WidgetAction, h: Dp, snap: VehicleSnapshot, theme: Theme) {
        val context = LocalContext.current
        val icon = when (action) {
            WidgetAction.DOORS, WidgetAction.LOCK, WidgetAction.UNLOCK ->
                if (snap.locked == true) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
            WidgetAction.CLIMATE, WidgetAction.CLIMATE_ON, WidgetAction.CLIMATE_OFF -> R.drawable.ic_shortcut_climate
            WidgetAction.CHARGE -> R.drawable.ic_widget_bolt
            else -> R.drawable.ic_shortcut_car
        }
        Box(
            GlanceModifier.height(h).defaultWeight()
                .background(theme.accent).cornerRadius(h / 2)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(icon), contentDescription = action.label,
                    colorFilter = androidx.glance.ColorFilter.tint(theme.onAccent),
                    modifier = GlanceModifier.size(h * 0.5f))
                if (h >= 32.dp) Text(action.label.take(8), maxLines = 1,
                    style = TextStyle(color = theme.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    private fun authIntent(context: Context, widgetId: Int, vin: String, action: WidgetAction): android.content.Intent {
        val intent = android.content.Intent(context, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    private fun configIntent(context: Context, widgetId: Int): android.content.Intent {
        val intent = android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return intent
    }
}
