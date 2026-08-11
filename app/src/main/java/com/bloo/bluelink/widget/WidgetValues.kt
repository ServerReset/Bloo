package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.relativeLabel
import com.bloo.bluelink.data.serviceDue

/**
 * Second slice out of CarWidget.kt's monolithic class: the plain (non-
 * [androidx.compose.runtime.Composable]) helpers that turn a [VehicleSnapshot]
 * into the strings and drawables the render layer actually draws. None of
 * these read composition-local state ([androidx.glance.LocalSize] etc.) --
 * every one is a pure function of its arguments -- which made them the
 * second-safest slice to pull out after [WidgetText]'s fit-checking chain.
 */

internal fun statusSubtitle(car: VehicleSnapshot): String {
    val parts = mutableListOf<String>()
    car.locked?.let { parts += if (it) "Locked" else "Unlocked" }
    if (car.charging == true) parts += "Charging"
    if (car.climateOn == true) parts += "Climate on"
    if (car.engineOn == true) parts += "Running"
    return parts.joinToString(" · ").ifBlank { car.model }
}

internal fun primaryValue(car: VehicleSnapshot, render: Render): String {
    // Hoist to a local val: rangeMi is a nullable public property in :shared, which
    // Kotlin won't smart-cast across a module boundary — a local copy is smart-castable.
    val range = car.rangeMi
    return when {
        car.percent != null -> "${car.percent}%" +
            (range?.let { " · ${formatDistance(it.toDouble(), render.metric)}" } ?: "")
        range != null -> formatDistance(range.toDouble(), render.metric)
        else -> car.model
    }
}

/** [field]'s label, adjusted for the car it's actually describing --
 *  [WidgetInfoField.PERCENT]'s stored label is "Battery", which is wrong
 *  for a gas or hybrid car with no chargeable pack: that same percent
 *  number is its FUEL level. Every other field's label is car-agnostic. */
internal fun fieldLabel(field: WidgetInfoField, car: VehicleSnapshot): String =
    if (field == WidgetInfoField.PERCENT && !car.hasBattery) "Fuel" else field.label

internal fun infoValue(field: WidgetInfoField, car: VehicleSnapshot, render: Render): String? = when (field) {
    WidgetInfoField.RANGE -> car.rangeMi?.let { formatDistance(it.toDouble(), render.metric) }
    WidgetInfoField.PERCENT -> car.percent?.let { "$it%" }
    // formatDistance, matching the RANGE row above and the phone. The raw car.odometer is
    // always MILES (parseOdometerMiles' contract), so displaying it unconverted showed a
    // metric user miles here while km sat one row up. parseOdometerMiles also strips the
    // thousands separators the raw string can carry.
    WidgetInfoField.ODOMETER ->
        parseOdometerMiles(car.odometer)?.let { formatDistance(it.toDouble(), render.metric) }
    WidgetInfoField.PLATE -> car.licensePlate?.takeIf { it.isNotBlank() }
    WidgetInfoField.SERVICE -> serviceDueLabel(car, render.metric)
    WidgetInfoField.UPDATED -> relativeLabel(car.fetchedAt.takeIf { it > 0 }).takeIf { it.isNotBlank() }
    // null (rather than a placeholder) when the car hasn't reported the
    // state, so InfoStack skips the row entirely instead of showing a
    // confident-looking "Unlocked" for something simply unknown.
    WidgetInfoField.LOCK -> car.locked?.let { if (it) "Locked" else "Unlocked" }
    WidgetInfoField.CLIMATE -> car.climateOn?.let { if (it) "On" else "Off" }
    WidgetInfoField.MODEL -> car.model.takeIf { it.isNotBlank() }
    // Null when the car isn't plugged in: the limit it would charge to is
    // real, but "80%" beside a car sitting in a driveway reads as a
    // current state rather than a setting.
    WidgetInfoField.LIMIT -> car.chargeLimitPct?.let { "$it%" }
    // Same null-means-unknown rule as LOCK/CLIMATE above.
    WidgetInfoField.ENGINE -> car.engineOn?.let { if (it) "Running" else "Off" }
    // Distinct from the ring, which conveys charging only by colour -- and several
    // tiers (RAIL, the COMPACT_TALLs, anything in controls-priority) draw no ring at
    // all, so on those this is the only way to see it.
    WidgetInfoField.CHARGING -> car.charging?.let { if (it) "Charging" else "Not charging" }
    // 3 decimals, not coordString's default 5. ~110 m is ample for "which car park
    // did I leave it in", and the default's "48.85660, 2.35220" is 17 characters --
    // the widest value any field can produce, which on a narrow tier forces FitText
    // to shrink the whole row's font. 13 characters is affordable.
    //
    // Deliberately still offered even though MapModule shows the same location
    // graphically: the map is suppressed by design on every Tall and Rail tier (a
    // real device showed it as "an unreadable, zoomed-in sliver of road" there), so
    // without this those tiers cannot show location in any form.
    WidgetInfoField.LOCATION ->
        car.lat?.let { la -> car.lon?.let { lo -> coordString(la, lo, decimals = 3) } }
}

internal fun serviceDueLabel(car: VehicleSnapshot, metric: Boolean): String? {
    // Odometer strings can arrive with thousands separators (e.g. "12,345")
    // and fractional miles; parseOdometerMiles strips/floors them to an Int.
    // Hand-rolling this with filter { isDigit() } dropped the decimal POINT
    // too, so "12,345.6" parsed as 123456 -- an odometer ten times too high,
    // which drove `due` negative, coerced it to 0, and left this field
    // reading "in 0 mi" forever (a service alert that never clears).
    //
    // The arithmetic itself is the shared serviceDue, which also does the
    // three null checks this used to spell out. It had been re-inlined here,
    // one helper away from the parseOdometerMiles the comment above is about --
    // and re-inlining a shared formatter is exactly how that bug got in.
    val due = serviceDue(
        odometerMiles = parseOdometerMiles(car.odometer),
        lastServiceMiles = car.lastServiceMiles,
        intervalMiles = car.serviceIntervalMiles,
    ) ?: return null
    // Clamped, unlike the phone's and the watch's readouts, which render a
    // negative as "overdue N mi". A widget field has room for one short phrase,
    // so it says "in 0 mi" and leans on the app for the detail.
    return "in ${formatDistance(due.coerceAtLeast(0), metric)}"
}

internal fun iconFor(action: WidgetAction): Int = when (action) {
    WidgetAction.LOCK -> R.drawable.ic_shortcut_lock
    WidgetAction.UNLOCK -> R.drawable.ic_shortcut_unlock
    WidgetAction.CLIMATE -> R.drawable.ic_shortcut_climate
    WidgetAction.CHARGE -> R.drawable.ic_widget_charge
    WidgetAction.FLASH -> R.drawable.ic_widget_flash
    WidgetAction.HORN -> R.drawable.ic_widget_horn
    WidgetAction.REFRESH -> R.drawable.ic_widget_refresh
    WidgetAction.OPEN -> R.drawable.ic_shortcut_car
    // Reusing the toggles' glyphs: there is no separate on/off asset, and drawing
    // one is not obviously an improvement -- a climate icon plus the word "on" is
    // clearer than a bespoke glyph a user has to learn. These are also the actions
    // most likely to be configured WITH labels shown, being explicit by nature.
    WidgetAction.CLIMATE_ON, WidgetAction.CLIMATE_OFF -> R.drawable.ic_shortcut_climate
    WidgetAction.CHARGE_ON, WidgetAction.CHARGE_OFF -> R.drawable.ic_widget_charge
}

// Glance's reified actionStartActivity<T>() overload isn't available here, so
// build the Intent explicitly (the (Intent, …) overload is the stable one).
internal fun openAction(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java))
