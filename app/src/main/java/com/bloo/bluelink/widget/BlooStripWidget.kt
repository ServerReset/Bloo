package com.bloo.bluelink.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.glance.layout.height
import androidx.glance.layout.Box
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
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.stateLineOrParked

/**
 * The slim strip: one row, the car's name and charge on the left and its lock, climate and charge buttons on the
 * right. For the homescreen row where the full widget is too big. It speaks the same commands as the full widget
 * ([CarActionCallback] runs them, Unlock asks for its second tap) and follows the first car; the full widget is the
 * one with a car picker and options.
 */
class BlooStripWidget : GlanceAppWidget() {
    // Exact: the strip lays itself out from its real size (a roomier one gains a state line and a charge bar).
    override val sizeMode = SizeMode.Exact
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
    val opts = WidgetOptions.of(prefs)
    val size = androidx.glance.LocalSize.current
    val car = cars.firstOrNull { it.vin == prefs[WidgetKeys.VIN] } ?: cars.firstOrNull()
    val open = actionStartActivity(openCarIntent(context, car?.vin))
    // The strip follows the same options as the full widget (corners, text size, photo, level colour, which controls),
    // set in the same settings screen, and lays itself out from its real size: a roomier strip adds a state line and a
    // charge bar, a short one stays to the value.
    val tall = size.height >= 68.dp
    val us = opts.textSize.scale
    // The photo loads inside the session, so changing the car or the option in the settings swaps it at once.
    var photo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoKey = if (opts.photo && car != null) "${car.vin}:${opts.blur}" else null
    androidx.compose.runtime.LaunchedEffect(photoKey) {
        photo = if (photoKey != null && car != null) WidgetPhoto.load(context, car.vin, opts.blur) else null
    }
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(opts.corner.dp.dp).clickable(open),
    ) {
        photo?.let { bmp ->
            androidx.glance.Image(
                androidx.glance.ImageProvider(bmp), null,
                modifier = GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.Crop,
            )
            Box(GlanceModifier.fillMaxSize().background(androidx.glance.color.ColorProvider(day = PhotoVeilDay, night = PhotoVeilNight))) {}
        }
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = if (tall) 10.dp else 6.dp),
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
            val levelColor = if (opts.levelTint && car.percent != null) levelTint(car) else null
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note ?: car.name, maxLines = 1,
                    style = TextStyle(
                        color = if (note != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = (12 * us).sp, fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    car.bigValue(), maxLines = 1,
                    style = TextStyle(color = levelColor ?: GlanceTheme.colors.onSurface, fontSize = ((if (tall) 26 else 22) * us).sp, fontWeight = FontWeight.Bold),
                )
                if (tall) {
                    Text(
                        car.stateLineOrParked(), maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (11 * us).sp),
                    )
                    if (opts.extras && car.percent != null) {
                        Spacer(GlanceModifier.height(5.dp))
                        ChargeBar(car, levelColor)
                    }
                }
            }
            // The controls the settings chose, as many as the width holds beside the readout.
            val controls = buildList {
                add(CarAction.TOGGLE_LOCK)
                if (opts.climate) add(CarAction.TOGGLE_CLIMATE)
                if (opts.charge && car.hasBattery) add(CarAction.TOGGLE_CHARGE)
                if (opts.flash) add(CarAction.FLASH_LIGHTS)
            }.take(((size.width.value - 28f - 96f + 8f) / 48f).toInt().coerceIn(1, 4))
            val diameter = if (tall) 44f else 38f
            controls.forEach { action ->
                Spacer(GlanceModifier.width(WidgetGap))
                ActionButton(car, action, pending, armed, label = null, heightDp = diameter)
            }
        }
    }
}

/** The system's entry point for the strip widget. */
class BlooStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlooStripWidget()
}
