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
            ComplicationType.RANGED_VALUE -> car.percent?.let { ranged(it.coerceIn(0, 100).toFloat(), car.bigValue(), description) }
                ?: text(car.bigValue(), description)
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
 * The car's lock state on any watch face, as short text ("Locked" or "Open"). Tapping it opens the watch app on
 * the lock control, so it runs through the same PIN gate and, for an unlock, the same "are you sure" as the face.
 */
class BlooLockComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) lockText("Locked", "Car locked") else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        if (WearBootGate.isLocked(applicationContext)) return NoDataComplicationData()
        val car = runCatching { SnapshotStore(applicationContext).current().selected }.getOrNull() ?: return NoDataComplicationData()
        val locked = car.locked ?: return NoDataComplicationData()
        return lockText(if (locked) "Locked" else "Open", "${car.name} is ${if (locked) "locked" else "unlocked"}")
    }

    private fun lockText(shown: String, description: String): ComplicationData =
        ShortTextComplicationData.Builder(PlainComplicationText.Builder(shown).build(), PlainComplicationText.Builder(description).build())
            .setTapAction(
                PendingIntent.getActivity(
                    applicationContext, 1,
                    Intent(applicationContext, WearMainActivity::class.java).putExtra(WearMainActivity.EXTRA_ACTION, "lock"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
}

/** Asks the watch's glanceable surfaces (the Tile and the complication) to redraw from the latest snapshot. */
internal object WearSurfaces {
    fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching { TileService.getUpdater(app).requestUpdate(BlooTileService::class.java) }
        listOf(BlooComplicationService::class.java, BlooLockComplicationService::class.java).forEach { service ->
            runCatching {
                ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, service)).requestUpdateAll()
            }
        }
    }
}
