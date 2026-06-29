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
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.wear.R

/**
 * Watch-face complication for the selected car's climate state. Shows a snowflake
 * with on/off text reflecting the live state, and starts/stops climate on tap.
 */
class ClimateComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, climateOn = true, vin = null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = SnapshotStore(applicationContext).current().selected ?: return null
        return build(request.complicationType, snap.climateOn, snap.vin)
    }

    private fun build(type: ComplicationType, climateOn: Boolean?, vin: String?): ComplicationData? {
        val on = climateOn == true
        val text = if (on) "On" else "Off"
        val image = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_shortcut_climate),
        ).build()
        val desc = PlainComplicationText.Builder(if (on) "Climate on" else "Climate off").build()
        val tap = vin?.let { ComplicationTapReceiver.pendingIntent(this, it, WearAction.TOGGLE_CLIMATE) }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), desc)
                    .setTitle(PlainComplicationText.Builder("Climate").build())
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
