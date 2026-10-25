package com.bloo.bluelink.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.chargeLeftLabel
import com.bloo.bluelink.data.doorLockLabel

/**
 * The car's charge (or fuel) on any watch face, in every shape a face offers: a ring-with-value, short text, a long line
 * (name, charge, range and lock), or the glyph alone. Every shape carries the bolt glyph, and the ring shows it while the car
 * is charging. Tapping it opens the watch app. A sealed watch (after a restart, before the PIN) shows nothing about the
 * car, exactly as the Tile does.
 */
class BlooComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> ranged(80f, "80%", "Battery", charging = false)
        ComplicationType.SHORT_TEXT -> text("80%", "Battery")
        ComplicationType.LONG_TEXT -> longText("Daisy", "80% · 248 mi · Locked", "Daisy, 80%")
        ComplicationType.MONOCHROMATIC_IMAGE -> glyph("Battery")
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (WearBootGate.isLocked(applicationContext)) return NoDataComplicationData()
        val snapshot = runCatching { SnapshotStore(applicationContext).current() }.getOrNull() ?: return NoDataComplicationData()
        val car: VehicleSnapshot = snapshot.selected ?: return NoDataComplicationData()
        val description = "${car.name}, ${car.bigValue()}"
        return when (request.complicationType) {
            // A request is answered in the type it asked for: no level means no data for a ranged one (the system
            // rejects another type), not a text fallback.
            ComplicationType.RANGED_VALUE -> car.percent?.let {
                ranged(it.coerceIn(0, 100).toFloat(), car.bigValue(), description, charging = car.charging == true)
            } ?: NoDataComplicationData()
            ComplicationType.SHORT_TEXT -> text(car.bigValue(), description)
            ComplicationType.LONG_TEXT -> longText(car.name, longLine(car), description)
            ComplicationType.MONOCHROMATIC_IMAGE -> glyph(description)
            else -> NoDataComplicationData()
        }
    }

    /** "80% · 248 mi · Locked": what a face with room for a line says about the car, leaving out what it does not know. */
    private fun longLine(car: VehicleSnapshot): String =
        listOfNotNull(
            car.bigValue(),
            // Charging says how long is left; otherwise the range.
            car.chargeLeftLabel()?.let { "$it left" } ?: car.rangeMi?.let { "$it mi" },
            doorLockLabel(car.locked),
        ).joinToString(" · ")

    private fun open(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0, Intent(applicationContext, WearMainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun bolt(): MonochromaticImage =
        MonochromaticImage.Builder(Icon.createWithResource(applicationContext, R.drawable.tile_bolt)).build()

    private fun ranged(value: Float, shown: String, description: String, charging: Boolean): ComplicationData =
        RangedValueComplicationData.Builder(value, 0f, 100f, PlainComplicationText.Builder(description).build())
            .setText(PlainComplicationText.Builder(shown).build())
            // The bolt marks a car that is charging; a car that is not shows the number alone.
            .apply { if (charging) setMonochromeImage(bolt()) }
            .setTapAction(open())
            .build()

    private fun text(shown: String, description: String): ComplicationData =
        ShortTextComplicationData.Builder(PlainComplicationText.Builder(shown).build(), PlainComplicationText.Builder(description).build())
            .setMonochromeImage(bolt())
            .setTapAction(open())
            .build()

    private fun longText(title: String, line: String, description: String): ComplicationData =
        LongTextComplicationData.Builder(PlainComplicationText.Builder(line).build(), PlainComplicationText.Builder(description).build())
            .setTitle(PlainComplicationText.Builder(title).build())
            .setMonochromeImage(bolt())
            .setTapAction(open())
            .build()

    private fun glyph(description: String): ComplicationData =
        MonochromaticImageComplicationData.Builder(bolt(), PlainComplicationText.Builder(description).build())
            .setTapAction(open())
            .build()
}

/**
 * One complication from the selected car: [text] returns what to show and how to say it aloud (null when the car has
 * nothing to report), [icon] the glyph that goes with it, and [tapKey] the face control a tap opens the app on (null just
 * opens the app). It answers as short text and as the glyph alone. A tap on a control goes through the same PIN gate and,
 * for an unlock, the same "are you sure" as the face.
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
        ComplicationType.SHORT_TEXT -> build(preview, previewIcon)
        ComplicationType.MONOCHROMATIC_IMAGE -> glyph(preview.second, previewIcon)
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT && request.complicationType != ComplicationType.MONOCHROMATIC_IMAGE) {
            return NoDataComplicationData()
        }
        if (WearBootGate.isLocked(applicationContext)) return NoDataComplicationData()
        val car = runCatching { SnapshotStore(applicationContext).current().selected }.getOrNull() ?: return NoDataComplicationData()
        val shown = text(car) ?: return NoDataComplicationData()
        return if (request.complicationType == ComplicationType.MONOCHROMATIC_IMAGE) glyph(shown.second, icon(car)) else build(shown, icon(car))
    }

    private fun tap(): PendingIntent {
        val intent = Intent(applicationContext, WearMainActivity::class.java)
        tapKey?.let { intent.putExtra(WearMainActivity.EXTRA_ACTION, it) }
        return PendingIntent.getActivity(applicationContext, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun image(res: Int): MonochromaticImage =
        MonochromaticImage.Builder(Icon.createWithResource(applicationContext, res)).build()

    private fun build(shown: Pair<String, String>, res: Int): ComplicationData =
        ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(shown.first).build(), PlainComplicationText.Builder(shown.second).build(),
        )
            .setMonochromeImage(image(res))
            .setTapAction(tap())
            .build()

    private fun glyph(description: String, res: Int): ComplicationData =
        MonochromaticImageComplicationData.Builder(image(res), PlainComplicationText.Builder(description).build())
            .setTapAction(tap())
            .build()
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

/** Asks the watch's glanceable surfaces (the Tile and the complication) to redraw from the latest snapshot. */
internal object WearSurfaces {
    fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching { TileService.getUpdater(app).requestUpdate(BlooTileService::class.java) }
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
