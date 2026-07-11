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

/**
 * Bloo widget — 4 auto-adapting tiers that always work:
 * Tiny (percent only), Compact (row), Standard (column), Wide (photo row).
 * No photo/location/map. Buttons auto-size. Never clips.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

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
            ColorProvider(Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, 0.22f))))
        else ColorProvider(Color.White)
        val chargeGreen = ColorProvider(Color(0xFF2EBD59))

        provideContent { GlanceTheme {
            val w = LocalSize.current.width; val h = LocalSize.current.height
            val corner = when { w < 80.dp || h < 80.dp -> 14.dp; else -> 22.dp }
            val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)

            when {
                snap == null -> TapBox(base, configIntent(context, widgetId))
                w < 70.dp && h < 70.dp -> TinyLayout(snap, base, chargeGreen)
                h < 65.dp -> CompactRow(snap, actions, base, chargeGreen, onAccent)
                w > 200.dp && w > h * 1.5f -> WideRow(snap, actions, base, chargeGreen, onAccent)
                else -> StandardCol(snap, actions, w, h, base, chargeGreen, onAccent)
            }
        }}
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun stateColor(snap: VehicleSnapshot, chargeGreen: ColorProvider): ColorProvider =
        when { snap.charging == true -> chargeGreen; else -> ColorProvider(Color(0.6f, 0.6f, 0.65f, 0.5f)) }

    @Composable
    private fun Pill(h: Dp, icon: Int, label: String, onClick: () -> Unit, accent: ColorProvider, onAccent: ColorProvider) {
        Box(GlanceModifier.height(h).padding(end = 4.dp).background(accent).cornerRadius(h / 2)
            .clickable(onClick), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(icon), contentDescription = label,
                    colorFilter = ColorFilter.tint(onAccent), modifier = GlanceModifier.size(h * 0.5f))
                if (h >= 28.dp) Text(label.take(8), maxLines = 1,
                    style = TextStyle(color = onAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    private fun ActionPills(actions: List<WidgetAction>, vin: String, h: Dp, theme: ColorProvider, onAccent: ColorProvider, max: Int = 4) {
        val ctx = LocalContext.current
        actions.take(max).forEach { a ->
            val icon = when (a) {
                WidgetAction.DOORS, WidgetAction.LOCK, WidgetAction.UNLOCK -> R.drawable.ic_shortcut_lock
                WidgetAction.CLIMATE, WidgetAction.CLIMATE_ON, WidgetAction.CLIMATE_OFF -> R.drawable.ic_shortcut_climate
                WidgetAction.CHARGE -> R.drawable.ic_widget_bolt
                else -> R.drawable.ic_shortcut_car
            }
            val intent = android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
                this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, 0)
                putExtra(WidgetAuthActivity.EXTRA_VIN, vin); putExtra(WidgetAuthActivity.EXTRA_ACTION, a.key)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Pill(h, icon, a.label, { actionStartActivity(intent) }, theme, onAccent)
        }
    }

    private fun configIntent(context: Context, widgetId: Int): android.content.Intent =
        android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

    // ── Layouts ────────────────────────────────────────────────────────────

    @Composable
    private fun TapBox(base: GlanceModifier, intent: android.content.Intent) {
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Text("Tap to set up", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun TinyLayout(snap: VehicleSnapshot, base: GlanceModifier, chargeGreen: ColorProvider) {
        val ctx = LocalContext.current; val sc = stateColor(snap, chargeGreen)
        val intent = android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_VIN, snap.vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, WidgetAction.OPEN.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Box(GlanceModifier.size(5.dp).background(sc).cornerRadius(3.dp)) {}
            }
        }
    }

    @Composable
    private fun CompactRow(snap: VehicleSnapshot, actions: List<WidgetAction>, base: GlanceModifier, chargeGreen: ColorProvider, onAccent: ColorProvider) {
        val ctx = LocalContext.current; val sc = stateColor(snap, chargeGreen)
        val btnH = 26.dp; val narrow = LocalSize.current.width < 120.dp
        val intent = android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_VIN, snap.vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, WidgetAction.OPEN.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Row(base.padding(horizontal = 8.dp).clickable(actionStartActivity(intent)), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.padding(end = 6.dp)) {
                if (!narrow) Text(snap.name.take(14), maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (narrow) 14.sp else 16.sp))
                    snap.rangeMi?.let { Spacer(GlanceModifier.width(4.dp)); Text("· ${it}mi", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
                }
            }
            ActionPills(actions, snap.vin, btnH, ColorProvider(Color.White).let { sc }, onAccent, max = 2)
        }
    }

    @Composable
    private fun StandardCol(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, h: Dp, base: GlanceModifier, chargeGreen: ColorProvider, onAccent: ColorProvider) {
        val ctx = LocalContext.current; val pct = (snap.percent ?: 0).coerceIn(0, 100)
        val sc = stateColor(snap, chargeGreen); val stLabel = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        val btnH = if (h < 120.dp) 28.dp else 34.dp
        val intent = android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_VIN, snap.vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, WidgetAction.OPEN.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Column(base.padding(12.dp).clickable(actionStartActivity(intent))) {
            // Name + state chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.name.take(18), maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Box(GlanceModifier.background(sc).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Text(stLabel, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            // Percent + range
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (w > 180.dp) 36.sp else 28.sp))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)) }
                    Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
            // Battery bar
            Spacer(GlanceModifier.height(4.dp))
            val barW = (w - 24.dp) * (pct / 100f)
            Box(GlanceModifier.fillMaxWidth().height(5.dp).background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.25f))).cornerRadius(3.dp)) {
                if (pct > 0) Box(GlanceModifier.width(barW).height(5.dp).background(sc).cornerRadius(3.dp)) {}
            }
            // Details
            Spacer(GlanceModifier.height(6.dp))
            Row {
                Box(GlanceModifier.padding(end = 6.dp)) { DetailBox("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }) }
                DetailBox("Climate", if (snap.climateOn == true) "On" else "Off")
            }
            // Buttons
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                val perRow = if (h > w * 1.1f) 2 else 4
                actions.take(perRow * 2).chunked(perRow).forEach { chunk ->
                    Row { chunk.forEachIndexed { i, a -> if (i > 0) Spacer(GlanceModifier.width(6.dp)); ActionPills(listOf(a), snap.vin, btnH, sc, onAccent, 1) } }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun WideRow(snap: VehicleSnapshot, actions: List<WidgetAction>, base: GlanceModifier, chargeGreen: ColorProvider, onAccent: ColorProvider) {
        val ctx = LocalContext.current; val sc = stateColor(snap, chargeGreen)
        val intent = android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_VIN, snap.vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, WidgetAction.OPEN.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Row(base.padding(10.dp).clickable(actionStartActivity(intent)), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.padding(end = 10.dp)) {
                Text(snap.name.take(16), maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 28.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    Column {
                        snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)) }
                        Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                    }
                }
            }
            ActionPills(actions, snap.vin, 30.dp, sc, onAccent, max = 4)
        }
    }

    @Composable
    private fun DetailBox(label: String, value: String) {
        Column(GlanceModifier.background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.12f))).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}
