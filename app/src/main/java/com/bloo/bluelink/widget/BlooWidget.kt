package com.bloo.bluelink.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
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

/** Simple auto-adapting widget: 3 size tiers, never clips, minimal config. */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private class Theme(val accent: ColorProvider, val onAccent: ColorProvider)

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

        provideContent { GlanceTheme {
            val w = LocalSize.current.width
            val h = LocalSize.current.height
            val corner = when { w < 80.dp || h < 80.dp -> 14.dp; w < 160.dp || h < 100.dp -> 18.dp; else -> 24.dp }
            val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
            when {
                snap == null -> TapBox(base, configIntent(context, widgetId), "Tap to set up")
                w < 80.dp && h < 80.dp -> TinyBody(snap, base, theme)
                h < 70.dp -> CompactBody(snap, actions, w, base, theme)
                else -> FullBody(snap, actions, w, h, base, theme)
            }
        }}
    }

    @Composable
    private fun TapBox(base: GlanceModifier, intent: android.content.Intent, label: String) {
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium))
        }
    }

    private fun stateColor(snap: VehicleSnapshot, theme: Theme): ColorProvider =
        when { snap.charging == true -> ColorProvider(Color(0xFF2EBD59)); else -> theme.accent }

    @Composable
    private fun TinyBody(snap: VehicleSnapshot, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current; val intent = authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)
        val sc = stateColor(snap, theme)
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(GlanceModifier.height(2.dp))
                Box(GlanceModifier.size(5.dp).background(sc).cornerRadius(3.dp)) {}
            }
        }
    }

    @Composable
    private fun CompactBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current; val intent = authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)
        val narrow = w < 120.dp
        val maxBtns = if (narrow) 0 else min(actions.size, 3)
        Row(base.padding(horizontal = 8.dp, vertical = 4.dp).clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.padding(end = 8.dp)) {
                if (!narrow) Text(snap.name.take(14), maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold,
                            fontSize = if (narrow) 14.sp else 16.sp))
                    snap.rangeMi?.let { Spacer(GlanceModifier.width(4.dp)); Text("· ${it}mi", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
                }
            }
            actions.take(maxBtns).forEach { a -> Pill(0, snap.vin, a, 26.dp, snap, theme) }
        }
    }

    @Composable
    private fun FullBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, h: Dp, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current; val intent = authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)
        val sc = stateColor(snap, theme); val pct = (snap.percent ?: 0).coerceIn(0, 100)
        Column(base.padding(12.dp).clickable(actionStartActivity(intent))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.name.take(18), maxLines = 1, modifier = GlanceModifier.padding(end = 8.dp),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                StateChip(snap, theme, sc)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold,
                        fontSize = if (w > 200.dp) 40.sp else 28.sp))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)) }
                    Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            val barW = (w - 24.dp) * (pct / 100f)
            Box(GlanceModifier.fillMaxWidth().height(5.dp).background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.25f))).cornerRadius(3.dp)) {
                if (pct > 0) Box(GlanceModifier.width(barW).height(5.dp).background(theme.accent).cornerRadius(3.dp)) {}
            }
            Spacer(GlanceModifier.height(6.dp))
            Row {
                Box(GlanceModifier.padding(end = 6.dp)) { DetailBox("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }) }
                DetailBox("Climate", if (snap.climateOn == true) "On" else "Off")
            }
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                val perRow = if (h > w * 1.1f) 2 else 4
                actions.take(perRow * 2).chunked(perRow).forEach { chunk ->
                    Row { chunk.forEachIndexed { i, a -> if (i > 0) Spacer(GlanceModifier.width(6.dp)); Pill(0, snap.vin, a, 34.dp, snap, theme) } }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun StateChip(snap: VehicleSnapshot, theme: Theme, sc: ColorProvider) {
        val label = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        Box(GlanceModifier.background(sc).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun DetailBox(label: String, value: String) {
        Column(GlanceModifier.background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.12f))).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun Pill(widgetId: Int, vin: String, action: WidgetAction, h: Dp, snap: VehicleSnapshot, theme: Theme) {
        val ctx = LocalContext.current
        val icon = when (action) {
            WidgetAction.DOORS, WidgetAction.LOCK, WidgetAction.UNLOCK -> if (snap.locked == true) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
            WidgetAction.CLIMATE, WidgetAction.CLIMATE_ON, WidgetAction.CLIMATE_OFF -> R.drawable.ic_shortcut_climate
            WidgetAction.CHARGE -> R.drawable.ic_widget_bolt
            else -> R.drawable.ic_shortcut_car
        }
        Box(GlanceModifier.height(h).padding(end = 4.dp).background(theme.accent).cornerRadius(h / 2)
            .clickable(actionStartActivity(authIntent(ctx, widgetId, vin, action))),
            contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(icon), contentDescription = action.label, colorFilter = ColorFilter.tint(theme.onAccent), modifier = GlanceModifier.size(h * 0.5f))
                if (h >= 32.dp) Text(action.label.take(8), maxLines = 1, style = TextStyle(color = theme.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    private fun authIntent(ctx: Context, widgetId: Int, vin: String, action: WidgetAction): android.content.Intent =
        android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin); putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun configIntent(context: Context, widgetId: Int): android.content.Intent =
        android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
}
