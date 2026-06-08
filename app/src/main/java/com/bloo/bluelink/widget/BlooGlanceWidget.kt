package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import android.content.Context
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * A single, fully responsive home-screen widget. It reflows from a 1x1 tile
 * (just the charge/fuel %) up to a full-screen panel, reading the on-disk
 * snapshot the app maintains. Includes car switching and quick commands.
 */
class BlooGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(60.dp, 60.dp),    // 1x1
            DpSize(140.dp, 60.dp),   // 2x1
            DpSize(180.dp, 130.dp),  // small-medium
            DpSize(260.dp, 200.dp),  // medium-large
            DpSize(320.dp, 320.dp),  // large-full
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = SnapshotStore(context.applicationContext).current()
        provideContent {
            WidgetRoot(data)
        }
    }
}

private val Bg = ColorProvider(Color(0xFF12141C))
private val OnBg = ColorProvider(Color(0xFFF2F4F8))
private val Accent = ColorProvider(Color(0xFF7AA8FF))
private val Muted = ColorProvider(Color(0xFFAEB6C2))

class BlooWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlooGlanceWidget()
}

@Composable
private fun WidgetRoot(data: SnapshotStore.SnapshotData) {
    val size = LocalSize.current
    val selected = data.selected
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (selected == null) {
            Text("Open Bloo to sign in", style = body())
            return@Column
        }
        when {
            size.width < 120.dp -> TinyLayout(selected)
            size.width < 220.dp -> SmallLayout(data, selected)
            else -> LargeLayout(data, selected, size.height >= 240.dp)
        }
    }
}

@Composable
private fun TinyLayout(v: VehicleSnapshot) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(percentText(v), style = title(26))
        Text(if (v.isEv) "battery" else "fuel", style = caption())
    }
}

@Composable
private fun SmallLayout(data: SnapshotStore.SnapshotData, v: VehicleSnapshot) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(data, v, compact = true)
        Spacer(GlanceModifier.height(4.dp))
        Text(percentText(v), style = title(28))
        Text(rangeText(v), style = caption())
        Spacer(GlanceModifier.height(6.dp))
        Row {
            Button("Lock", actionRunCallback<LockAction>())
            Spacer(GlanceModifier.width(6.dp))
            Button("Unlock", actionRunCallback<UnlockAction>())
        }
    }
}

@Composable
private fun LargeLayout(data: SnapshotStore.SnapshotData, v: VehicleSnapshot, tall: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(data, v, compact = false)
        Spacer(GlanceModifier.height(6.dp))
        Text(percentText(v), style = title(40))
        Text(rangeText(v), style = body())
        v.locked?.let { Text(if (it) "Doors locked" else "Doors unlocked", style = caption()) }
        if (v.charging == true) Text("Charging", style = caption())
        if (tall) {
            v.model.let { Text(it, style = caption()) }
            v.updated?.let { Text("Updated $it", style = caption()) }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button("Lock", actionRunCallback<LockAction>())
            Spacer(GlanceModifier.width(6.dp))
            Button("Unlock", actionRunCallback<UnlockAction>())
            Spacer(GlanceModifier.width(6.dp))
            Button("Refresh", actionRunCallback<RefreshAction>())
        }
    }
}

@Composable
private fun HeaderRow(data: SnapshotStore.SnapshotData, v: VehicleSnapshot, compact: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            v.name,
            style = TextStyle(
                color = OnBg,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
    if (data.vehicles.size > 1) {
        Button("↔ Switch car", actionRunCallback<SwitchCarAction>())
    }
}

private fun percentText(v: VehicleSnapshot) = v.percent?.let { "$it%" } ?: "—"
private fun rangeText(v: VehicleSnapshot) = v.rangeMi?.let { "$it mi range" } ?: ""

@Composable private fun title(sizeSp: Int) =
    TextStyle(color = Accent, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold)

@Composable private fun body() =
    TextStyle(color = OnBg, fontSize = 14.sp)

@Composable private fun caption() =
    TextStyle(color = Muted, fontSize = 12.sp)
