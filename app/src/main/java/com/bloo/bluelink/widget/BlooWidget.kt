package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
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
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.stateLineOrParked
import com.bloo.bluelink.data.updatedLabel

/** What the widget draws from: the saved cars, and whether the app is sealed after a restart. */
private data class WidgetData(val sealed: Boolean, val cars: List<VehicleSnapshot>)

/**
 * The widget's per-instance options, chosen on the configuration screen and stored in the widget's
 * own Glance state. Everything defaults to on/shown, so a widget placed without configuring still
 * does the useful thing.
 */
private data class WidgetOptions(
    val climate: Boolean,
    val charge: Boolean,
    val flash: Boolean,
    val refresh: Boolean,
    val compact: Boolean,
) {
    companion object {
        fun of(prefs: Preferences) = WidgetOptions(
            climate = prefs[WidgetKeys.SHOW_CLIMATE] ?: true,
            charge = prefs[WidgetKeys.SHOW_CHARGE] ?: true,
            flash = prefs[WidgetKeys.SHOW_FLASH] ?: true,
            refresh = prefs[WidgetKeys.SHOW_REFRESH] ?: true,
            compact = prefs[WidgetKeys.COMPACT] ?: false,
        )
    }
}

/**
 * The homescreen widget: one car's charge, range and lock at a glance, with lock, climate, charge,
 * flash and refresh controls. Material You colours, every size from 2x2 to 5x5 (the content adapts
 * to the span rather than overflowing it -- see [AdaptiveBody]), per-widget options, and a sealed
 * state that shows no car data until the app is signed in after a restart.
 */
class BlooWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
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
    val context = androidx.glance.LocalContext.current
    val opts = WidgetOptions.of(prefs)
    val open = actionStartActivity(Intent(context, MainActivity::class.java))
    val root = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(28.dp)
        .padding(if (opts.compact) 8.dp else 12.dp)
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
    val armed = prefs[WidgetKeys.ARMED]?.split("|")?.takeIf {
        it.size == 2 && it[0] == car.vin && now - (it[1].toLongOrNull() ?: 0L) < UNLOCK_CONFIRM_MS
    } != null
    val note = prefs[WidgetKeys.NOTE]?.split("|")?.takeIf {
        it.size == 2 && now - (it[1].toLongOrNull() ?: 0L) < NOTE_MS
    }?.get(0)

    AdaptiveBody(root, car, pending, armed, note, opts)
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

/**
 * ONE body that adapts to the span, instead of a layout per size. The value's weight, whether the
 * status/detail lines fit, how many controls to show and whether they carry labels are all derived
 * from the cells, so nothing overflows at any size from 2x2 to 5x5. [WidgetShape] decides only the
 * one structural split -- a bare 2x2 shares its row between the value and the lock button -- while
 * ROW/COLUMN/FULL all render the same value-over-actions flow, denser as the span grows.
 */
@Composable
private fun AdaptiveBody(root: GlanceModifier, car: VehicleSnapshot, pending: String?, armed: Boolean, note: String?, opts: WidgetOptions) {
    val size = LocalSize.current
    val cols = widgetCells(size.width)
    val rows = widgetCells(size.height)
    val shape = widgetShapeFor(size.width, size.height)

    val valueSp: TextUnit = when {
        shape == WidgetShape.MINIMAL -> 28.sp
        rows >= 4 -> 52.sp
        rows >= 3 -> 42.sp
        else -> 32.sp
    }
    // The status line needs a second row to sit under the value without crowding it.
    val showStatus = shape != WidgetShape.MINIMAL
    // The "updated X ago" line only where the header has room for a second line.
    val showUpdated = rows >= 3 && cols >= 3
    // The odometer / charge-limit line only on the roomiest layouts, and never when compact.
    val showDetails = rows >= 4 && cols >= 3 && !opts.compact
    // Labelled (text) buttons are wider than icon circles, so they only appear where four of them
    // actually fit; below that, icon-only controls.
    val labels = cols >= 5 && rows >= 3 && !opts.compact
    val maxActions = minOf(4, cols)

    Column(root) {
        Header(car, showUpdated = showUpdated, opts = opts, pending = pending)
        Spacer(GlanceModifier.defaultWeight())
        if (shape == WidgetShape.MINIMAL) {
            // 2x2: the value and the lock button share one row, so it fits without a status line.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    car.bigValue(),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = valueSp, fontWeight = FontWeight.Bold),
                )
                ActionButton(car, CarAction.TOGGLE_LOCK, pending, armed, label = null, compact = true)
            }
        } else {
            Text(car.bigValue(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = valueSp, fontWeight = FontWeight.Bold))
            if (showStatus) StatusText(car, note, if (rows >= 3) 13.sp else 12.sp)
            if (showDetails) {
                Spacer(GlanceModifier.height(4.dp))
                car.detailLine()?.let { line ->
                    Text(line, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                }
            }
            Spacer(GlanceModifier.height(if (opts.compact) 6.dp else 10.dp))
            Actions(car, pending, armed, opts, labels = labels, maxActions = maxActions)
        }
    }
}

/** The line under the value: a failure note (in red) else the car's state line. */
@Composable
private fun StatusText(car: VehicleSnapshot, note: String?, size: TextUnit) {
    Text(
        note ?: car.stateLineOrParked(), maxLines = 1,
        style = TextStyle(
            color = if (note != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            fontSize = size,
        ),
    )
}

// --- Header and controls -----------------------------------------------------------------------

@Composable
private fun Header(car: VehicleSnapshot, showUpdated: Boolean, opts: WidgetOptions, pending: String?) {
    val stale = car.isStale()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.ic_widget_car), null,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                car.name, maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            if (showUpdated) {
                car.updatedLabel()?.let { updated ->
                    Text(
                        updated,
                        style = TextStyle(color = if (stale) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
            }
        }
        if (opts.refresh) RefreshButton(car, pending)
    }
}

/** A small refresh control in the header: re-fetches this car's status through the same runner. */
@Composable
private fun RefreshButton(car: VehicleSnapshot, pending: String?) {
    val busy = pending == "${car.vin}:${CarAction.REFRESH}"
    Box(
        modifier = GlanceModifier
            .size(32.dp)
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(actionFor(car, CarAction.REFRESH, busy)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(R.drawable.ic_widget_refresh), "Refresh",
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
        )
    }
}

/**
 * The controls, honouring the widget's options and the room available: at most [maxActions] of them,
 * left to right, so a narrow widget never pushes a button off the edge.
 */
@Composable
private fun Actions(car: VehicleSnapshot, pending: String?, armed: Boolean, opts: WidgetOptions, labels: Boolean, maxActions: Int) {
    val actions = buildList {
        add(WidgetAction(CarAction.TOGGLE_LOCK, if (labels) lockLabel(car) else null))
        if (opts.climate) add(WidgetAction(CarAction.TOGGLE_CLIMATE, if (labels) "Climate" else null))
        if (opts.charge && car.hasBattery) add(WidgetAction(CarAction.TOGGLE_CHARGE, if (labels) "Charge" else null))
        if (opts.flash) add(WidgetAction(CarAction.FLASH_LIGHTS, if (labels) "Flash" else null))
    }.take(maxActions)
    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { i, a ->
            if (i > 0) Spacer(GlanceModifier.width(8.dp))
            ActionButton(car, a.action, pending, armed, label = a.label)
        }
    }
}

/** One control to draw: which command, and the word beside it (null = icon only). */
private data class WidgetAction(val action: String, val label: String?)

/** The lock button's own word, showing what a tap DOES: "Unlock" while locked, "Lock" while open. */
private fun lockLabel(car: VehicleSnapshot): String = if (car.locked == true) "Unlock" else "Lock"

/**
 * One round (or pill, with a [label]) action button. It shows the car's CURRENT state (a lock closed
 * or open, climate and charge lit when on), ignores taps while its command is in flight, and for an
 * Unlock asks "Confirm" first.
 */
@Composable
private fun ActionButton(car: VehicleSnapshot, action: String, pending: String?, armed: Boolean, label: String?, compact: Boolean = false) {
    val busy = pending == "${car.vin}:$action"
    val locked = car.locked == true
    val active = when (action) {
        CarAction.TOGGLE_CLIMATE -> car.climateOn == true
        CarAction.TOGGLE_CHARGE -> car.charging == true
        else -> false
    }
    val icon = when (action) {
        CarAction.TOGGLE_LOCK -> if (locked) R.drawable.ic_widget_unlock else R.drawable.ic_widget_lock
        CarAction.TOGGLE_CLIMATE -> R.drawable.ic_widget_climate
        CarAction.FLASH_LIGHTS -> R.drawable.ic_widget_flash
        else -> R.drawable.ic_widget_bolt
    }
    val text = when {
        busy -> "…"
        action == CarAction.TOGGLE_LOCK && armed -> "Confirm"
        else -> label
    }
    val container: ColorProvider = if (active || armed) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val content: ColorProvider = if (active || armed) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val h = if (compact) 32.dp else 38.dp
    Box(
        modifier = GlanceModifier
            .height(h)
            .then(if (text == null) GlanceModifier.width(h) else GlanceModifier.padding(horizontal = 12.dp))
            .background(container)
            .cornerRadius(h / 2)
            .clickable(actionFor(car, action, busy)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // An unlabelled control is an icon circle; a labelled one is a TEXT pill -- dropping the
            // glyph keeps four of them inside a wide widget, which icon+label never fit.
            if (text == null) {
                Image(
                    ImageProvider(icon), null,
                    modifier = GlanceModifier.size(if (compact) 16.dp else 18.dp),
                    colorFilter = ColorFilter.tint(content),
                )
            } else {
                Text(text, maxLines = 1, style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

/**
 * What a tap does: run the command, unless it is already busy, when it opens the app instead (so a
 * stuck command is one tap from the app rather than a dead button).
 */
@Composable
private fun actionFor(car: VehicleSnapshot, action: String, busy: Boolean): Action =
    if (busy) {
        actionStartActivity(Intent(androidx.glance.LocalContext.current, MainActivity::class.java))
    } else {
        actionRunCallback<CarActionCallback>(
            actionParametersOf(WidgetKeys.PARAM_VIN to car.vin, WidgetKeys.PARAM_ACTION to action),
        )
    }

// --- The car's words ---------------------------------------------------------------------------

/** The detail line the roomiest layouts show: charge limit, odometer, service — whichever exist. */
private fun VehicleSnapshot.detailLine(): String? = listOfNotNull(
    chargeLimitPct?.let { "Limit $it%" },
    odometer?.takeIf { it.isNotBlank() }?.let { "Odo $it" },
    lastServiceMiles?.let { "Service ${it} mi" },
).joinToString("   ·   ").ifBlank { null }
