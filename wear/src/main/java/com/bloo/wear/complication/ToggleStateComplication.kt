package com.bloo.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * Shared base for the on/off toggle complications (lock, climate). Each renders a
 * state-reflecting icon + text as SHORT_TEXT or MONOCHROMATIC_IMAGE and toggles
 * its [action] on tap against the selected car; subclasses supply only what
 * differs. Data comes from the phone-synced snapshot — no network needed.
 *
 * (The charge complication is deliberately NOT a subclass: it's a ranged gauge
 * that taps through to the app rather than a toggle.)
 */
abstract class ToggleStateComplication : SuspendingComplicationDataSourceService() {

    /** The data-source name passed to [resolveComplicationCar] for per-instance car lookup. */
    protected abstract val dataSourceName: String
    /** SHORT_TEXT title line (e.g. "Lock"). */
    protected abstract val title: String
    /** The [com.bloo.bluelink.data.WearAction] fired on tap. */
    protected abstract val action: String

    /** This complication's live on/off state for [snap] (null = unknown). */
    protected abstract fun stateOf(snap: VehicleSnapshot): Boolean?
    protected abstract fun iconRes(on: Boolean): Int
    protected abstract fun text(on: Boolean): String
    protected abstract fun description(on: Boolean): String

    final override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, on = true, vin = null)

    final override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = resolveComplicationCar(applicationContext, dataSourceName, request.complicationInstanceId)
            ?: return null
        return build(request.complicationType, stateOf(snap), snap.vin)
    }

    final override fun onComplicationDeactivated(complicationInstanceId: Int) {
        clearComplicationConfig(applicationContext, dataSourceName, complicationInstanceId)
    }

    private fun build(type: ComplicationType, on: Boolean?, vin: String?): ComplicationData? {
        // `on == null` means the state hasn't synced yet, not that it's
        // confirmed off -- collapsing both into `isOn = false` rendered an
        // unsynced lock complication as a definite "Unlocked", which a user
        // glancing at their watch face could mistake for a real reading of
        // the car. Render an explicit neutral state instead, matching how
        // ChargeComplication already renders "—%" for an unknown percentage.
        val known = on != null
        val isOn = on == true
        val image = MonochromaticImage.Builder(
            Icon.createWithResource(this, if (known) iconRes(isOn) else iconRes(false)),
        ).build()
        val desc = PlainComplicationText.Builder(if (known) description(isOn) else "State unknown").build()
        val tap = vin?.let { ComplicationTapReceiver.pendingIntent(this, it, action) }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(if (known) text(isOn) else "—").build(),
                    desc,
                )
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setMonochromaticImage(image)
                    .apply { tap?.let { setTapAction(it) } }
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, desc)
                    .apply { tap?.let { setTapAction(it) } }
                    .build()

            else -> null
        }
    }
}
