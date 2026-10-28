package com.bloo.bluelink.widget

import com.bloo.bluelink.data.CarCommandIds
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

/** A button the strip can carry, in the order the settings chose; the narrower the strip, the fewer from the front it keeps. */
internal enum class StripControl(val key: String, val label: String, val action: String) {
    LOCK(CarCommandIds.LOCK, "Lock", CarAction.TOGGLE_LOCK),
    CLIMATE("climate", "Climate", CarAction.TOGGLE_CLIMATE),
    CHARGE("charge", "Charge", CarAction.TOGGLE_CHARGE),
    FLASH("flash", "Flash", CarAction.FLASH_LIGHTS);

    companion object {
        fun parse(raw: String?): List<StripControl>? =
            raw?.split(",")?.mapNotNull { k -> entries.firstOrNull { it.key == k } }?.distinct()?.takeIf { it.isNotEmpty() }

        /** The chosen controls, or (a strip made before this choice existed) the ones its old switches left on. */
        fun of(prefs: Preferences, opts: WidgetOptions): List<StripControl> =
            parse(prefs[WidgetKeys.STRIP_CONTROLS]) ?: buildList {
                add(LOCK)
                if (opts.climate) add(CLIMATE)
                if (opts.charge) add(CHARGE)
                if (opts.flash) add(FLASH)
            }

        fun encode(list: List<StripControl>) = list.joinToString(",") { it.key }
    }
}

/** How many round buttons fit beside the readout in a strip [widthDp] wide (1..4). */
internal fun stripControlCount(widthDp: Float, buttonDp: Float = 38f): Int =
    ((widthDp - 28f - 96f + 8f) / (buttonDp + 8f)).toInt().coerceIn(1, 4)

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
    // Room for the name, the value, the state line and the bar (about 71dp at 1x) plus the padding; a font scale grows it.
    val fontScale = context.resources.configuration.fontScale * opts.textSize.scale
    val tall = size.height.value >= 96f * fontScale.coerceAtLeast(1f)
    val us = opts.textSize.scale
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(stripCorner(prefs, size.height)).clickable(open),
    ) {
        WidgetPhotoLayer(car, opts)
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
            val armed = prefs.unlockArmedFor(car.vin, now)
            // A failed command's note (the full widget's red line) takes the name's place for a few seconds, so a refused
            // lock or unlock is not silent on the strip.
            val note = prefs.freshNote(now)
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
            val controls = StripControl.of(prefs, opts)
                .filter { it != StripControl.CHARGE || car.hasBattery }
                // A strip whose only chosen button is one this car cannot do still has the lock.
                .ifEmpty { listOf(StripControl.LOCK) }
                .take(stripControlCount(size.width.value, if (tall) 44f else 38f))
            val diameter = if (tall) 44f else 38f
            controls.forEach { control ->
                Spacer(GlanceModifier.width(WidgetGap))
                ActionButton(car, control.action, pending, armed, heightDp = diameter)
            }
        }
    }
}

/** A pill is half the strip's height, so it stays a pill however tall the strip is stretched; a square keeps a fixed, softer corner. */
private fun stripCorner(prefs: Preferences, height: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val shape = StripShape.entries.getOrElse(prefs[WidgetKeys.STRIP_SHAPE] ?: 0) { StripShape.PILL }
    return shape.squareDp?.dp ?: (height / 2)
}

/** The system's entry point for the strip widget. */
class BlooStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlooStripWidget()
}
