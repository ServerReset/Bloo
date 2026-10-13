package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot

/** What the widget draws from: the saved cars, and whether the app is sealed after a restart. */
private data class WidgetData(val sealed: Boolean, val cars: List<VehicleSnapshot>)

/** The three layouts, picked from the size the launcher gives the widget. */
private val Small = DpSize(110.dp, 110.dp)
private val Wide = DpSize(250.dp, 110.dp)
private val Tall = DpSize(250.dp, 220.dp)

/**
 * The homescreen widget: one car's charge, range and lock at a glance, with lock, climate and charge
 * buttons. Material You colours (it follows the wallpaper and the light/dark theme), three responsive
 * layouts, and a sealed state that shows no car data until the app is signed in after a restart.
 */
class BlooWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Tall))
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sealed = BootGate.isLocked(context)
        val cars = if (sealed) emptyList() else runCatching { SnapshotStore(context).current().vehicles }.getOrDefault(emptyList())
        val data = WidgetData(sealed, cars)
        provideContent {
            GlanceTheme { WidgetContent(data) }
        }
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    val prefs = currentState<Preferences>()
    val size = LocalSize.current
    val context = androidx.glance.LocalContext.current
    val open = actionStartActivity(Intent(context, MainActivity::class.java))
    val root = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(28.dp)
        .padding(14.dp)
        .clickable(open)

    if (data.sealed) {
        Message(root, "Open Bloo to sign in", "Your phone restarted")
        return
    }
    val car = data.cars.firstOrNull { it.vin == prefs[WidgetKeys.VIN] } ?: data.cars.firstOrNull()
    if (car == null) {
        Message(root, "No car yet", "Open Bloo to add one")
        return
    }
    val pending = prefs[WidgetKeys.PENDING]
    val now = System.currentTimeMillis()
    val armed = prefs[WidgetKeys.ARMED]?.split("|")?.takeIf { it.size == 2 && it[0] == car.vin && now - (it[1].toLongOrNull() ?: 0L) < UNLOCK_CONFIRM_MS } != null
    val note = prefs[WidgetKeys.NOTE]?.split("|")?.takeIf { it.size == 2 && now - (it[1].toLongOrNull() ?: 0L) < NOTE_MS }?.get(0)

    when {
        size.width < Wide.width -> SmallLayout(root, car, pending, armed)
        size.height < Tall.height -> WideLayout(root, car, pending, armed, note)
        else -> TallLayout(root, car, pending, armed, note)
    }
}

@Composable
private fun Message(root: GlanceModifier, title: String, detail: String) {
    Column(root, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            ImageProvider(R.drawable.ic_widget_car), null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text(detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}

/** ~2x2: the charge, the lock state and one lock button. */
@Composable
private fun SmallLayout(root: GlanceModifier, car: VehicleSnapshot, pending: String?, armed: Boolean) {
    Column(root) {
        Header(car, compact = true)
        Spacer(GlanceModifier.defaultWeight())
        Text(car.bigValue(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                car.stateLine(), maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            ActionButton(car, CarAction.TOGGLE_LOCK, pending, armed, label = null)
        }
    }
}

/** ~4x2: adds the actions in a row. */
@Composable
private fun WideLayout(root: GlanceModifier, car: VehicleSnapshot, pending: String?, armed: Boolean, note: String?) {
    Column(root) {
        Header(car, compact = false)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(GlanceModifier.defaultWeight()) {
                Text(car.bigValue(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                Text(note ?: car.stateLine(), maxLines = 1, style = TextStyle(color = if (note != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
            Actions(car, pending, armed, labels = false)
        }
    }
}

/** ~4x4: bigger numbers and labelled actions. */
@Composable
private fun TallLayout(root: GlanceModifier, car: VehicleSnapshot, pending: String?, armed: Boolean, note: String?) {
    Column(root) {
        Header(car, compact = false)
        Spacer(GlanceModifier.defaultWeight())
        Text(car.bigValue(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 52.sp, fontWeight = FontWeight.Bold))
        Text(note ?: car.stateLine(), maxLines = 1, style = TextStyle(color = if (note != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
        Spacer(GlanceModifier.height(14.dp))
        Actions(car, pending, armed, labels = true)
    }
}

@Composable
private fun Header(car: VehicleSnapshot, compact: Boolean) {
    val stale = car.isStale()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.ic_widget_car), null,
            modifier = GlanceModifier.size(if (compact) 14.dp else 16.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            car.name, maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!compact) {
            Text(
                car.updatedText(),
                style = TextStyle(color = if (stale) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }
}

/** Lock, climate and (for a car with a battery) charge. */
@Composable
private fun Actions(car: VehicleSnapshot, pending: String?, armed: Boolean, labels: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = if (labels) Alignment.Start else Alignment.End) {
        ActionButton(car, CarAction.TOGGLE_LOCK, pending, armed, label = if (labels) "Lock" else null)
        Spacer(GlanceModifier.width(8.dp))
        ActionButton(car, CarAction.TOGGLE_CLIMATE, pending, armed = false, label = if (labels) "Climate" else null)
        if (car.hasBattery) {
            Spacer(GlanceModifier.width(8.dp))
            ActionButton(car, CarAction.TOGGLE_CHARGE, pending, armed = false, label = if (labels) "Charge" else null)
        }
    }
}

/**
 * One round (or pill, with a [label]) action button. It shows the car's CURRENT state (a lock closed or
 * open, climate and charge lit when on), ignores taps while its command is in flight, and for an Unlock
 * asks "Confirm" first.
 */
@Composable
private fun ActionButton(car: VehicleSnapshot, action: String, pending: String?, armed: Boolean, label: String?) {
    val busy = pending == "${car.vin}:$action"
    val locked = car.locked == true
    val active = when (action) {
        CarAction.TOGGLE_CLIMATE -> car.climateOn == true
        CarAction.TOGGLE_CHARGE -> car.charging == true
        else -> false
    }
    val icon = when (action) {
        CarAction.TOGGLE_LOCK -> if (locked) R.drawable.ic_widget_lock else R.drawable.ic_widget_unlock
        CarAction.TOGGLE_CLIMATE -> R.drawable.ic_widget_climate
        else -> R.drawable.ic_widget_bolt
    }
    val text = when {
        busy -> "…"
        action == CarAction.TOGGLE_LOCK && armed -> "Confirm"
        else -> label
    }
    val container: ColorProvider = if (active || armed) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val content: ColorProvider = if (active || armed) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val context = androidx.glance.LocalContext.current
    val click: Action = if (busy) actionStartActivity(Intent(context, MainActivity::class.java)) else actionRunCallback<CarActionCallback>(
        actionParametersOf(WidgetKeys.PARAM_VIN to car.vin, WidgetKeys.PARAM_ACTION to action),
    )
    Box(
        modifier = GlanceModifier
            .height(40.dp)
            .then(if (text == null) GlanceModifier.width(40.dp) else GlanceModifier.padding(horizontal = 14.dp))
            .background(container)
            .cornerRadius(20.dp)
            .clickable(click),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(icon), null, modifier = GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(content))
            if (text != null) {
                Spacer(GlanceModifier.width(6.dp))
                Text(text, maxLines = 1, style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

// --- The car's words ---------------------------------------------------------------------------

private fun VehicleSnapshot.bigValue(): String =
    percent?.let { "$it%" } ?: locked?.let { if (it) "Locked" else "Unlocked" } ?: "—"

private fun VehicleSnapshot.stateLine(): String = listOfNotNull(
    locked?.let { if (it) "Locked" else "Unlocked" }.takeIf { percent != null },
    if (charging == true) "Charging" else null,
    rangeMi?.let { "$it mi" },
).joinToString(" · ").ifBlank { "Parked" }

private fun VehicleSnapshot.isStale(): Boolean =
    fetchedAt > 0L && System.currentTimeMillis() - fetchedAt > 30 * 60_000L

private fun VehicleSnapshot.updatedText(): String {
    if (fetchedAt <= 0L) return ""
    val minutes = ((System.currentTimeMillis() - fetchedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}
