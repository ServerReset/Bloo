package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * One reflowing widget rendered at several fixed footprints (1x1 … 5x5 — see the
 * receivers + provider XML). Reads the on-disk snapshot. Command buttons launch
 * [CommandActivity], which authenticates (fingerprint/PIN) then sends + toasts.
 */
class BlooGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = SnapshotStore(context.applicationContext).current()
        provideContent { WidgetRoot(data) }
    }
}

// Fixed-size picker entries (see res/xml/bloo_w_*.xml).
class BlooWidget2x1Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget4x1Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget5x1Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget6x1Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget4x2Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget5x2Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }
class BlooWidget6x2Receiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = BlooGlanceWidget() }

private val Bg = ColorProvider(Color(0xFF12141C))
private val OnBg = ColorProvider(Color(0xFFF2F4F8))
private val Accent = ColorProvider(Color(0xFF7AA8FF))
private val Green = ColorProvider(Color(0xFF2EBD59))
private val Muted = ColorProvider(Color(0xFFAEB6C2))

@Composable
private fun WidgetRoot(data: SnapshotStore.SnapshotData) {
    val size = LocalSize.current
    val selected = data.selected
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(24.dp)
            .padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (selected == null) {
            Text("Open Bloo", style = body())
            return@Column
        }
        when {
            size.width < 130.dp -> TinyLayout(selected)
            size.height < 120.dp -> WideShortLayout(data, selected, size.width)
            else -> LargeLayout(data, selected, size.height >= 220.dp)
        }
    }
}

@Composable
private fun TinyLayout(v: VehicleSnapshot) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(percentText(v), style = title(24))
        Text(if (v.charging == true) "charging" else if (v.isEv) "battery" else "fuel", style = caption())
    }
}

@Composable
private fun WideShortLayout(data: SnapshotStore.SnapshotData, v: VehicleSnapshot, width: Dp) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(v.name, style = TextStyle(color = OnBg, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(percentText(v), style = title(28))
            Text(
                if (v.charging == true) "Charging · ${rangeText(v)}" else rangeText(v),
                style = if (v.charging == true) {
                    TextStyle(color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    caption()
                },
            )
        }
        DoorsButton(v)
        if (width >= 300.dp) {
            Spacer(GlanceModifier.width(6.dp))
            ClimateButton(v)
        }
        if (width >= 380.dp && v.isEv) {
            Spacer(GlanceModifier.width(6.dp))
            ChargeButton(v)
        }
    }
}

@Composable
private fun LargeLayout(data: SnapshotStore.SnapshotData, v: VehicleSnapshot, tall: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(data, v)
        Spacer(GlanceModifier.height(6.dp))
        Text(percentText(v), style = title(40))
        Text(rangeText(v), style = body())
        v.locked?.let { Text(if (it) "Doors locked" else "Doors unlocked", style = caption()) }
        if (v.charging == true) Text("Charging", style = TextStyle(color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        if (tall) {
            Text(v.model, style = caption())
            v.updated?.let { Text("Updated $it", style = caption()) }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            DoorsButton(v)
            Spacer(GlanceModifier.width(6.dp))
            ClimateButton(v)
        }
        if (v.isEv) {
            Spacer(GlanceModifier.height(6.dp))
            ChargeButton(v)
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button("Refresh", actionRunCallback<RefreshAction>())
            if (data.vehicles.size > 1) {
                Spacer(GlanceModifier.width(6.dp))
                Button("↔ Car", actionRunCallback<SwitchCarAction>())
            }
        }
    }
}

@Composable
private fun HeaderRow(data: SnapshotStore.SnapshotData, v: VehicleSnapshot) {
    Text(
        v.name,
        style = TextStyle(color = OnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

// --- Auth-gated command buttons ------------------------------------------

@Composable
private fun DoorsButton(v: VehicleSnapshot) {
    if (v.locked == true) CommandButton("Unlock", "unlock", v.vin) else CommandButton("Lock", "lock", v.vin)
}

@Composable
private fun ClimateButton(v: VehicleSnapshot) = CommandButton("Climate", "climate_on", v.vin)

@Composable
private fun ChargeButton(v: VehicleSnapshot) {
    if (v.charging == true) CommandButton("Stop charge", "charge_off", v.vin)
    else CommandButton("Charge", "charge_on", v.vin)
}

@Composable
private fun CommandButton(label: String, action: String, vin: String) {
    val context = LocalContext.current
    val intent = Intent(context, CommandActivity::class.java).apply {
        putExtra(CommandActivity.EXTRA_ACTION, action)
        // Unique data so each button gets its own PendingIntent.
        data = Uri.parse("bloo://cmd/$action/$vin")
    }
    Button(label, androidx.glance.appwidget.action.actionStartActivity(intent))
}

private fun percentText(v: VehicleSnapshot) = v.percent?.let { "$it%" } ?: "—"
private fun rangeText(v: VehicleSnapshot) = v.rangeMi?.let { "$it mi range" } ?: ""

@Composable private fun title(sizeSp: Int) =
    TextStyle(color = Accent, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold)

@Composable private fun body() = TextStyle(color = OnBg, fontSize = 14.sp)

@Composable private fun caption() = TextStyle(color = Muted, fontSize = 12.sp)
