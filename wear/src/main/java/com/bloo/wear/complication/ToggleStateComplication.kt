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

    private fun build(type: ComplicationType, on: Boolean?, vin: String?): ComplicationData? {
        val isOn = on == true
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, iconRes(isOn))).build()
        val desc = PlainComplicationText.Builder(description(isOn)).build()
        val tap = vin?.let { ComplicationTapReceiver.pendingIntent(this, it, action) }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(text(isOn)).build(), desc)
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
