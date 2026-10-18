package com.bloo.bluelink.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
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

/**
 * The car's charge (or fuel) on any watch face: a ring-with-value where the face has room for a ranged
 * complication, or the value as short text where it does not. Tapping it opens the watch app. A sealed watch
 * (after a restart, before the PIN) shows nothing about the car, exactly as the Tile does.
 */
class BlooComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> ranged(80f, "80%", "Battery")
        ComplicationType.SHORT_TEXT -> text("80%", "Battery")
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
            ComplicationType.RANGED_VALUE -> car.percent?.let { ranged(it.coerceIn(0, 100).toFloat(), car.bigValue(), description) }
                ?: NoDataComplicationData()
            ComplicationType.SHORT_TEXT -> text(car.bigValue(), description)
            else -> NoDataComplicationData()
        }
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0, Intent(applicationContext, WearMainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ranged(value: Float, shown: String, description: String): ComplicationData =
        RangedValueComplicationData.Builder(value, 0f, 100f, PlainComplicationText.Builder(description).build())
            .setText(PlainComplicationText.Builder(shown).build())
            .setTapAction(open())
            .build()

    private fun text(shown: String, description: String): ComplicationData =
        ShortTextComplicationData.Builder(PlainComplicationText.Builder(shown).build(), PlainComplicationText.Builder(description).build())
            .setTapAction(open())
            .build()
}

/**
 * One short-text complication from the selected car: [text] returns what to show and how to say it aloud (null when
 * the car has nothing to report), and [tapKey] is the face control a tap opens the app on (null just opens the app).
 * A tap on a control goes through the same PIN gate and, for an unlock, the same "are you sure" as the face.
 */
abstract class BlooTextComplication(
    private val preview: Pair<String, String>,
    private val requestCode: Int,
    private val tapKey: String?,
) : SuspendingComplicationDataSourceService() {

    /** The text and its spoken form for [car], or null for no data. */
    protected abstract fun text(car: VehicleSnapshot): Pair<String, String>?

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build(preview) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        if (WearBootGate.isLocked(applicationContext)) return NoDataComplicationData()
        val car = runCatching { SnapshotStore(applicationContext).current().selected }.getOrNull() ?: return NoDataComplicationData()
        return text(car)?.let(::build) ?: NoDataComplicationData()
    }

    private fun build(shown: Pair<String, String>): ComplicationData {
        val intent = Intent(applicationContext, WearMainActivity::class.java)
        tapKey?.let { intent.putExtra(WearMainActivity.EXTRA_ACTION, it) }
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(shown.first).build(), PlainComplicationText.Builder(shown.second).build(),
        )
            .setTapAction(PendingIntent.getActivity(applicationContext, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
    }
}

/** The lock state ("Locked" or "Open"); a tap runs the lock control. */
class BlooLockComplicationService : BlooTextComplication(("Locked" to "Car locked"), 1, "lock") {
    override fun text(car: VehicleSnapshot) = car.locked?.let { locked ->
        (if (locked) "Locked" else "Open") to "${car.name} is ${if (locked) "locked" else "unlocked"}"
    }
}

/** The range ("248 mi"); a tap opens the app. */
class BlooRangeComplicationService : BlooTextComplication(("248 mi" to "Range 248 miles"), 2, null) {
    override fun text(car: VehicleSnapshot) = car.rangeMi?.let { "$it mi" to "${car.name} range $it miles" }
}

/** The climate ("On" or "Off"); a tap runs the climate control. */
class BlooClimateComplicationService : BlooTextComplication(("Off" to "Climate off"), 3, "climate") {
    override fun text(car: VehicleSnapshot) = car.climateOn?.let { on ->
        (if (on) "On" else "Off") to "${car.name} climate is ${if (on) "on" else "off"}"
    }
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
