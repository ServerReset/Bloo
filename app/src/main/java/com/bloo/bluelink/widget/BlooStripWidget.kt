package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue

/**
 * The slim strip: one row, the car's name and charge on the left and its lock, climate and charge buttons on the
 * right. For the homescreen row where the full widget is too big. It speaks the same commands as the full widget
 * ([CarActionCallback] runs them, Unlock asks for its second tap) and follows the first car; the full widget is the
 * one with a car picker and options.
 */
class BlooStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sealed = BootGate.isLocked(context)
        val cars = if (sealed) emptyList() else runCatching { SnapshotStore(context).current().vehicles }.getOrDefault(emptyList())
        provideContent { GlanceTheme { StripContent(cars) } }
    }
}

@Composable
private fun StripContent(cars: List<VehicleSnapshot>) {
    val prefs = currentState<Preferences>()
    val context = androidx.glance.LocalContext.current
    val open = actionStartActivity(Intent(context, MainActivity::class.java))
    val car = cars.firstOrNull { it.vin == prefs[WidgetKeys.VIN] } ?: cars.firstOrNull()
    Row(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (car == null) {
            Text(
                if (cars.isEmpty()) "Open Bloo" else "Bloo",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            return@Row
        }
        val now = System.currentTimeMillis()
        val pending = prefs[WidgetKeys.PENDING]
        val armed = prefs[WidgetKeys.ARMED]?.split("|")?.takeIf {
            it.size == 2 && it[0] == car.vin && now - (it[1].toLongOrNull() ?: 0L) < UNLOCK_CONFIRM_MS
        } != null
        // A failed command's note (the full widget's red line) takes the name's place for a few seconds, so a refused
        // lock or unlock is not silent on the strip.
        val note = prefs[WidgetKeys.NOTE]?.split("|")?.takeIf {
            it.size == 2 && now - (it[1].toLongOrNull() ?: 0L) < NOTE_MS
        }?.get(0)
        Column(GlanceModifier.defaultWeight()) {
            Text(
                note ?: car.name, maxLines = 1,
                style = TextStyle(color = if (note != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                car.bigValue(), maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
        }
        ActionButton(car, CarAction.TOGGLE_LOCK, pending, armed, label = null, heightDp = 40f)
        Spacer(GlanceModifier.width(WidgetGap))
        ActionButton(car, CarAction.TOGGLE_CLIMATE, pending, armed, label = null, heightDp = 40f)
        if (car.hasBattery) {
            Spacer(GlanceModifier.width(WidgetGap))
            ActionButton(car, CarAction.TOGGLE_CHARGE, pending, armed, label = null, heightDp = 40f)
        }
    }
}

/** The system's entry point for the strip widget. */
class BlooStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlooStripWidget()
}
