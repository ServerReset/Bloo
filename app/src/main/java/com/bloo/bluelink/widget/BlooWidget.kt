package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.glance.layout.fillMaxHeight
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
import com.bloo.uicommon.container
import com.bloo.uicommon.content

/** What the widget draws from: the saved cars, and whether the app is sealed after a restart. */
private data class WidgetData(val sealed: Boolean, val cars: List<VehicleSnapshot>)

/**
 * The widget's per-instance options, chosen on the configuration screen and stored in the widget's
 * own Glance state. Everything defaults to on/shown, so a widget placed without configuring still
 * does the useful thing.
 */
internal data class WidgetOptions(
    val climate: Boolean,
    val charge: Boolean,
    val flash: Boolean,
    val refresh: Boolean,
    val compact: Boolean,
    val photo: Boolean,
    val blur: Int,
    val extras: Boolean,
    val corner: WidgetCorner,
    val textSize: WidgetTextSize,
    val levelTint: Boolean,
) {
    companion object {
        fun of(prefs: Preferences) = WidgetOptions(
            climate = prefs[WidgetKeys.SHOW_CLIMATE] ?: true,
            charge = prefs[WidgetKeys.SHOW_CHARGE] ?: true,
            flash = prefs[WidgetKeys.SHOW_FLASH] ?: true,
            refresh = prefs[WidgetKeys.SHOW_REFRESH] ?: true,
            compact = prefs[WidgetKeys.COMPACT] ?: false,
            photo = prefs[WidgetKeys.SHOW_PHOTO] ?: true,
            blur = prefs[WidgetKeys.PHOTO_BLUR] ?: WidgetPhoto.BLUR_MEDIUM,
            extras = prefs[WidgetKeys.SHOW_EXTRAS] ?: true,
            corner = WidgetCorner.entries.getOrElse(prefs[WidgetKeys.CORNER] ?: WidgetCorner.MEDIUM.ordinal) { WidgetCorner.MEDIUM },
            textSize = WidgetTextSize.entries.getOrElse(prefs[WidgetKeys.TEXT_SIZE] ?: WidgetTextSize.NORMAL.ordinal) { WidgetTextSize.NORMAL },
            levelTint = prefs[WidgetKeys.LEVEL_TINT] ?: false,
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
    // EXACT, not Responsive: Responsive renders the largest of a fixed set of nominal sizes that fits, and the
    // nominal cell sizes are far smaller than a modern launcher's real cells, so the content was laid out for a
    // smaller box than it got -- the empty space around everything. Exact hands the layout the real size, which
    // the text and controls then scale to fill.
    override val sizeMode = SizeMode.Exact
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
    val car = if (data.sealed) null else data.cars.firstOrNull { it.vin == prefs[WidgetKeys.VIN] } ?: data.cars.firstOrNull()
    // Tapping the widget opens the app on THIS car, not just the app.
    val open = actionStartActivity(openCarIntent(context, car?.vin))
    // The photo loads inside the session (not in provideGlance), so changing the car or the option in the
    // settings swaps it right away.
    var photo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoKey = if (opts.photo && car != null) "${car.vin}:${opts.blur}" else null
    androidx.compose.runtime.LaunchedEffect(photoKey) {
        photo = if (photoKey != null && car != null) WidgetPhoto.load(context, car.vin, opts.blur) else null
    }
    val pad = widgetPad(opts.compact).dp
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(opts.corner.dp.dp).clickable(open),
    ) {
        photo?.let { bmp ->
            Image(ImageProvider(bmp), null, modifier = GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.Crop)
            // A theme-matched veil over the photo so the text keeps its contrast in light and dark.
            Box(GlanceModifier.fillMaxSize().background(androidx.glance.color.ColorProvider(day = PhotoVeilDay, night = PhotoVeilNight))) {}
        }
        val root = GlanceModifier.fillMaxSize().padding(pad)
        if (data.sealed) {
            Message(root, "Open Bloo to sign in", "Your phone restarted")
        } else if (car == null) {
            Message(root, "No car yet", "Open Bloo to add one")
        } else {
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
    }
}

/** The veil over a photo background: light enough to keep the car visible, firm enough for the text. */
internal val PhotoVeilDay = androidx.compose.ui.graphics.Color(0xB3FFFFFF)
internal val PhotoVeilNight = androidx.compose.ui.graphics.Color(0x99000000)

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
 * ONE body that adapts to the widget's real size: [planWidget] budgets every line against the actual height
 * (in the user's font scale) so nothing is cut off, drops the least important lines first, and gives the big
 * value whatever height is left so no size sits half empty. A widget too short for a control row puts the
 * value and the lock side by side.
 */
@Composable
private fun AdaptiveBody(root: GlanceModifier, car: VehicleSnapshot, pending: String?, armed: Boolean, note: String?, opts: WidgetOptions) {
    val size = LocalSize.current
    val context = androidx.glance.LocalContext.current
    val actionCount = 1 + (if (opts.climate) 1 else 0) + (if (opts.charge && car.hasBattery) 1 else 0) + (if (opts.flash) 1 else 0)
    val plan = planWidget(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = context.resources.configuration.fontScale * opts.textSize.scale,
        compact = opts.compact,
        valueChars = car.bigValue().length,
        actionCount = actionCount,
        refreshButton = opts.refresh,
    )
    // The wearer's text size multiplies every size the plan chose; the plan was budgeted with it, so it still fits.
    val us = opts.textSize.scale
    val levelColor = if (opts.levelTint && car.percent != null) levelTint(car) else null
    val valueStyle = TextStyle(color = levelColor ?: GlanceTheme.colors.onSurface, fontSize = (plan.valueSp * us).sp, fontWeight = FontWeight.Bold)

    when (plan.arrangement) {
        WidgetArrangement.MINIMAL -> Column(root) {
            Header(car, showUpdated = false, opts = opts, pending = pending, nameSp = (plan.nameSp * us).sp)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(car.bigValue(), maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = valueStyle)
                ActionButton(car, CarAction.TOGGLE_LOCK, pending, armed, heightDp = plan.buttonHeightDp)
            }
        }
        WidgetArrangement.SIDE -> Row(root, verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                Header(car, showUpdated = plan.showUpdated, opts = opts, pending = pending, nameSp = (plan.nameSp * us).sp)
                Spacer(GlanceModifier.defaultWeight())
                Text(car.bigValue(), maxLines = 1, style = valueStyle)
                if (plan.showStatus) StatusText(car, note, (plan.statusSp * us).sp)
            }
            Spacer(GlanceModifier.width(12.dp))
            ControlGrid(car, pending, armed, opts, plan)
        }
        WidgetArrangement.STACK -> Box(root) {
            Column(GlanceModifier.fillMaxSize()) {
                Header(car, showUpdated = plan.showUpdated, opts = opts, pending = pending, nameSp = (plan.nameSp * us).sp)
                // What the plan left over is one fixed gap above the value.
                Spacer(GlanceModifier.height((plan.spareDp / 2f).dp))
                Text(car.bigValue(), maxLines = 1, style = valueStyle)
                if (plan.showStatus) StatusText(car, note, (plan.statusSp * us).sp)
                if (plan.showDetails) {
                    car.detailLine()?.let { line ->
                        Spacer(GlanceModifier.height(4.dp))
                        Text(line, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (plan.detailSp * us).sp))
                    }
                }
                if (plan.showBar && opts.extras) {
                    Spacer(GlanceModifier.height(WidgetGap * 2))
                    ChargeBar(car, levelColor)
                }
                if (plan.showChips && opts.extras) {
                    Spacer(GlanceModifier.height(WidgetGap))
                    StatusChips(car, plan.maxChips)
                }
            }
            // The controls sit on their own layer pinned to the bottom, so they are never pushed off the widget by
            // the readout above them (a tall, narrow widget used to lose them entirely).
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                Actions(car, pending, armed, opts, maxActions = plan.maxActions, heightDp = plan.buttonHeightDp, gridColumns = plan.gridColumns)
            }
        }
    }
}

/** The charge (or fuel) as a bar, with the charge limit's share in a lighter tone when it is known. */
@Composable
internal fun ChargeBar(car: VehicleSnapshot, tint: androidx.glance.unit.ColorProvider? = null) {
    val pct = car.percent ?: return
    androidx.glance.appwidget.LinearProgressIndicator(
        progress = (pct / 100f).coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(WIDGET_BAR_DP.dp).cornerRadius((WIDGET_BAR_DP / 2).dp),
        color = tint ?: GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
}

/** Small pills for the states worth a glance on a big widget: climate and charging. */
@Composable
private fun StatusChips(car: VehicleSnapshot, maxChips: Int = 3) {
    val chips = buildList {
        car.climateOn?.let { add((if (it) "Climate on" else "Climate off") to it) }
        if (car.hasBattery) car.charging?.let { add((if (it) "Charging" else "Not charging") to it) }
        car.chargeLimitPct?.let { add("Limit $it%" to false) }
    }.take(maxChips)
    if (chips.isEmpty()) return
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        chips.forEachIndexed { i, (text, on) ->
            if (i > 0) Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier
                    .height(WIDGET_CHIP_DP.dp)
                    .cornerRadius((WIDGET_CHIP_DP / 2).dp)
                    .background(if (on) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.secondaryContainer)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text, maxLines = 1,
                    style = TextStyle(
                        color = if (on) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

/** SIDE: the controls as a grid of circles beside the readout. */
@Composable
private fun ControlGrid(car: VehicleSnapshot, pending: String?, armed: Boolean, opts: WidgetOptions, plan: WidgetPlan) {
    val actions = buildList {
        add(CarAction.TOGGLE_LOCK)
        if (opts.climate) add(CarAction.TOGGLE_CLIMATE)
        if (opts.charge && car.hasBattery) add(CarAction.TOGGLE_CHARGE)
        if (opts.flash) add(CarAction.FLASH_LIGHTS)
    }.take(plan.maxActions)
    val cols = plan.gridColumns.coerceAtLeast(1)
    Column(verticalAlignment = Alignment.CenterVertically) {
        actions.chunked(cols).forEachIndexed { r, rowActions ->
            if (r > 0) Spacer(GlanceModifier.height(WidgetGap))
            Row {
                rowActions.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.width(WidgetGap))
                    ActionButton(car, action, pending, armed, heightDp = plan.buttonHeightDp)
                }
            }
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
            fontWeight = FontWeight.Medium,
        ),
    )
}

// --- Header and controls -----------------------------------------------------------------------

@Composable
private fun Header(car: VehicleSnapshot, showUpdated: Boolean, opts: WidgetOptions, pending: String?, nameSp: TextUnit = 15.sp) {
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
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = nameSp, fontWeight = FontWeight.Bold),
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
private fun Actions(car: VehicleSnapshot, pending: String?, armed: Boolean, opts: WidgetOptions, maxActions: Int, heightDp: Float, gridColumns: Int = 0) {
    val actions = buildList {
        add(CarAction.TOGGLE_LOCK)
        if (opts.climate) add(CarAction.TOGGLE_CLIMATE)
        if (opts.charge && car.hasBattery) add(CarAction.TOGGLE_CHARGE)
        if (opts.flash) add(CarAction.FLASH_LIGHTS)
    }.take(maxActions)
    // The controls SHARE the row, each an equal slice: no empty run beside them, and every tap target is as
    // big as the widget allows (never under the 44dp the row is tall).
    if (gridColumns > 0) {
        // A narrow widget: the controls wrap into rows of [gridColumns], every cell an equal slice and a
        // lone last control taking its whole row.
        Column(GlanceModifier.fillMaxWidth()) {
            actions.chunked(gridColumns).forEachIndexed { r, rowActions ->
                if (r > 0) Spacer(GlanceModifier.height(WidgetGap))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    rowActions.forEachIndexed { i, a ->
                        if (i > 0) Spacer(GlanceModifier.width(WidgetGap))
                        ActionButton(car, a, pending, armed, modifier = GlanceModifier.defaultWeight(), heightDp = heightDp)
                    }
                }
            }
        }
        return
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { i, a ->
            if (i > 0) Spacer(GlanceModifier.width(WidgetGap))
            ActionButton(car, a, pending, armed, modifier = GlanceModifier.defaultWeight(), heightDp = heightDp)
        }
    }
}

/**
 * THE widget control: one button for every size and layout. Left to size itself it is a circle of [heightDp]; given a
 * [modifier] with a width (an equal share of its row or grid cell) it flexes into a pill that fills it, so one shape
 * serves a single row, a grid, a skinny column and the round buttons beside the readout. It always shows an icon (the
 * car's CURRENT state: a lock closed or open, climate and charge lit when on), ignores taps while its command is in
 * flight, and for an Unlock asks "OK?" first.
 */
@Composable
internal fun ActionButton(
    car: VehicleSnapshot,
    action: String,
    pending: String?,
    armed: Boolean,
    /** A width from the row (an equal share); none = a circle. */
    modifier: GlanceModifier? = null,
    /** The control's height from the plan (taller on a taller widget). */
    heightDp: Float = 46f,
) {
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
    val spoken = when (action) {
        CarAction.TOGGLE_LOCK -> if (locked) "Unlock" else "Lock"
        CarAction.TOGGLE_CLIMATE -> "Climate"
        CarAction.TOGGLE_CHARGE -> "Charge"
        CarAction.FLASH_LIGHTS -> "Flash lights"
        else -> action
    }
    // Armed is the pending UNLOCK: only the lock button is waiting on it. (It used to colour every button, so tapping
    // Unlock lit Climate, Charge and Flash too.)
    val armedHere = armed && action == CarAction.TOGGLE_LOCK
    val text = when {
        busy -> "..."
        armedHere -> "OK?"
        else -> null
    }
    val lit = active || armedHere
    val container: ColorProvider = if (lit) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val content: ColorProvider = if (lit) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val h = heightDp.dp
    Box(
        modifier = (modifier ?: GlanceModifier.width(h))
            .height(h)
            .background(container)
            .cornerRadius(h / 2)
            .clickable(actionFor(car, action, busy)),
        contentAlignment = Alignment.Center,
    ) {
        if (text == null) {
            Image(
                // Said aloud by a screen reader: the button's only content is this glyph.
                ImageProvider(icon), spoken,
                modifier = GlanceModifier.size(if (heightDp >= 56f) 26.dp else 22.dp),
                colorFilter = ColorFilter.tint(content),
            )
        } else {
            Text(text, maxLines = 1, style = TextStyle(color = content, fontSize = if (heightDp >= 56f) 16.sp else 14.sp, fontWeight = FontWeight.Bold))
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

/** The gap between the widget's controls and lines (its padding is [widgetPad]). */
internal val WidgetGap = 8.dp

// --- The car's words ---------------------------------------------------------------------------

/** The detail line the roomiest layouts show: charge limit, odometer, service - whichever exist. */
internal fun VehicleSnapshot.detailLine(): String? = listOfNotNull(
    chargeLimitPct?.let { "Limit $it%" },
    odometer?.takeIf { it.isNotBlank() }?.let { "Odo $it" },
    lastServiceMiles?.let { "Service ${it} mi" },
).joinToString("   ·   ").ifBlank { null }

/** The value's and the bar's colour by level (red low, amber, green, blue charging), shared with the watch. */
internal fun levelTint(car: VehicleSnapshot): androidx.glance.unit.ColorProvider =
    androidx.glance.unit.ColorProvider(
        androidx.compose.ui.graphics.Color(com.bloo.bluelink.data.BlooColors.levelColor(car.percent, car.charging == true, 0xFF888888.toInt())),
    )

/**
 * The intent a tap on the widget fires: the app, on the car the widget shows (the same "open" a launcher shortcut
 * makes, so a locked app waits for its unlock first). The data uri makes each car's intent distinct, because Android
 * treats two intents differing only in extras as the same one -- two widgets for two cars would share a tap.
 */
internal fun openCarIntent(context: Context, vin: String?): Intent {
    val intent = Intent(context, MainActivity::class.java)
    if (vin == null) return intent
    return intent
        .setAction(com.bloo.bluelink.Shortcuts.ACTION)
        .setData(android.net.Uri.parse("bloo://car/${android.net.Uri.encode(vin)}"))
        .putExtra(com.bloo.bluelink.Shortcuts.EXTRA_VIN, vin)
        .putExtra(com.bloo.bluelink.Shortcuts.EXTRA_CMD, "open")
}
