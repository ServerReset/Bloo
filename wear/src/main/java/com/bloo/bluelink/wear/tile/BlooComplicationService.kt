package com.bloo.bluelink.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.chargeLeftLabel
import com.bloo.bluelink.data.doorLockLabel
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel

/**
 * The car's charge (or fuel) on any watch face, in every shape a face offers: a ring-with-value, short text, a long line
 * (name, charge, range and lock), or the glyph alone. Every shape carries the bolt glyph, and the ring shows it while the car
 * is charging. Tapping it opens the watch app.
 */
class BlooComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> rangedData(80f, "80%", "Battery", null, tap())
        ComplicationType.SHORT_TEXT -> shortTextData("80%", "Battery", bolt(), tap())
        ComplicationType.LONG_TEXT -> longTextData("Daisy", "80% · 248 mi · Locked", "Daisy, 80%", bolt(), tap())
        ComplicationType.MONOCHROMATIC_IMAGE -> glyphData(bolt(), "Battery", tap())
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = runCatching { SnapshotStore(applicationContext).current() }.getOrNull() ?: return NoDataComplicationData()
        val car: VehicleSnapshot = snapshot.selected ?: return NoDataComplicationData()
        // An old reading says so (aloud, and in the long line's title) instead of passing as current.
        val age = if (car.isStale()) car.updatedLabel() else null
        val description = "${car.name}, ${car.bigValue()}" + car.spokenAge()
        // The bolt says "charging": a car that is not (or has no battery) shows its figure without it.
        val icon = if (car.charging == true) bolt() else null
        return when (request.complicationType) {
            // A request is answered in the type it asked for: no level means no data for a ranged one (the system
            // rejects another type), not a text fallback.
            ComplicationType.RANGED_VALUE -> car.percent?.let {
                rangedData(it.coerceIn(0, 100).toFloat(), car.bigValue(), description, icon, tap())
            } ?: NoDataComplicationData()
            ComplicationType.SHORT_TEXT -> shortTextData(car.bigValue(), description, icon, tap())
            ComplicationType.LONG_TEXT ->
                longTextData(if (age != null) "${car.name} · $age" else car.name, complicationLongLine(car), description, icon, tap())
            ComplicationType.MONOCHROMATIC_IMAGE -> glyphData(bolt(), description, tap())
            else -> NoDataComplicationData()
        }
    }

    private fun tap() = applicationContext.complicationTap(0)

    private fun bolt() = applicationContext.complicationIcon(R.drawable.tile_bolt)
}

/**
 * One complication from the selected car: [text] returns what to show and how to say it aloud (null when the car has
 * nothing to report), [icon] the glyph that goes with it, and [tapKey] the face control a tap opens the app on (null just
 * opens the app). It answers as short text and as the glyph alone. A tap on a control runs as it does on the face, and for an
 * unlock asks the same "are you sure".
 */
abstract class BlooTextComplication(
    private val preview: Pair<String, String>,
    private val previewIcon: Int,
    private val requestCode: Int,
    private val tapKey: String?,
) : SuspendingComplicationDataSourceService() {
    /** The text and its spoken form for [car], or null for no data. */
    protected abstract fun text(car: VehicleSnapshot): Pair<String, String>?

    /** The glyph for [car]'s state (a closed padlock, an open one). */
    protected abstract fun icon(car: VehicleSnapshot): Int

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortTextData(preview.first, preview.second, glyph(previewIcon), tap())
        ComplicationType.MONOCHROMATIC_IMAGE -> glyphData(glyph(previewIcon), preview.second, tap())
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val glyphOnly = request.complicationType == ComplicationType.MONOCHROMATIC_IMAGE
        if (request.complicationType != ComplicationType.SHORT_TEXT && !glyphOnly) return NoDataComplicationData()
        val car = runCatching { SnapshotStore(applicationContext).current().selected }.getOrNull() ?: return NoDataComplicationData()
        val reading = text(car)
        // The glyph alone needs only the car's state (a padlock shows with or without a word to go with it).
        if (glyphOnly) return glyphData(glyph(icon(car)), (reading?.second ?: car.name) + car.spokenAge(), tap())
        reading ?: return NoDataComplicationData()
        // An old reading says so aloud, so a glance at stale data is not heard as current.
        return shortTextData(reading.first, reading.second + car.spokenAge(), glyph(icon(car)), tap())
    }

    private fun tap() = applicationContext.complicationTap(requestCode, tapKey)

    private fun glyph(res: Int) = applicationContext.complicationIcon(res)
}

/** The lock state ("Locked" or "Open") with a closed or open padlock; a tap runs the lock control. */
class BlooLockComplicationService : BlooTextComplication(("Locked" to "Car locked"), R.drawable.tile_lock, 1, "lock") {
    override fun text(car: VehicleSnapshot) = car.locked?.let { locked ->
        (if (locked) "Locked" else "Open") to "${car.name} is ${if (locked) "locked" else "unlocked"}"
    }
    override fun icon(car: VehicleSnapshot) = if (car.locked == false) R.drawable.tile_unlock else R.drawable.tile_lock
}

/** The range ("248 mi") with the app's glyph; a tap opens the app. */
class BlooRangeComplicationService : BlooTextComplication(("248 mi" to "Range 248 miles"), R.drawable.ic_stat_bloo, 2, null) {
    override fun text(car: VehicleSnapshot) = car.rangeMi?.let { "$it mi" to "${car.name} range $it miles" }
    override fun icon(car: VehicleSnapshot) = R.drawable.ic_stat_bloo
}

/** The climate ("On" or "Off") with the snowflake; a tap runs the climate control. */
class BlooClimateComplicationService : BlooTextComplication(("Off" to "Climate off"), R.drawable.tile_climate, 3, "climate") {
    override fun text(car: VehicleSnapshot) = car.climateOn?.let { on ->
        (if (on) "On" else "Off") to "${car.name} climate is ${if (on) "on" else "off"}"
    }
    override fun icon(car: VehicleSnapshot) = R.drawable.tile_climate
}

/**
 * "80% · 248 mi · Locked": what a face with room for a line says about the car, leaving out what it does not know. While the
 * car charges it says how long is left in place of the range.
 */
internal fun complicationLongLine(car: VehicleSnapshot): String =
    listOfNotNull(
        car.bigValue(),
        car.chargeLeftLabel()?.let { "$it left" } ?: car.rangeMi?.let { "$it mi" },
        // With no charge to lead with, the main value already IS the lock state; saying it twice reads as a stutter.
        doorLockLabel(car.locked).takeIf { car.percent != null },
    ).joinToString(" · ")

/** Asks the watch's glanceable surfaces (the Tile and the complication) to redraw from the latest snapshot. */
internal object WearSurfaces {
    fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching { TileService.getUpdater(app).requestUpdate(BlooTileService::class.java) }
        runCatching { TileService.getUpdater(app).requestUpdate(BlooClimateTileService::class.java) }
        listOf(
            BlooComplicationService::class.java, BlooLockComplicationService::class.java,
            BlooRangeComplicationService::class.java, BlooClimateComplicationService::class.java,
        ).forEach { service ->
            runCatching {
                ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, service)).requestUpdateAll()
            }
        }
    }
}
