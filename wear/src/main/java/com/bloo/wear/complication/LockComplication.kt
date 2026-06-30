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
 * Watch-face complication for the selected car's lock state. Shows a closed or
 * open padlock that reflects the live state, and toggles lock/unlock on tap.
 */
class LockComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, locked = true, vin = null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = resolveComplicationCar(applicationContext, "LockComplication", request.complicationInstanceId)
            ?: return null
        return build(request.complicationType, snap.locked, snap.vin)
    }

    private fun build(type: ComplicationType, locked: Boolean?, vin: String?): ComplicationData? {
        val isLocked = locked == true
        val text = if (isLocked) "Locked" else "Unlocked"
        val iconRes = if (isLocked) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, iconRes)).build()
        val desc = PlainComplicationText.Builder(text).build()
        val tap = vin?.let { ComplicationTapReceiver.pendingIntent(this, it, WearAction.TOGGLE_LOCK) }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), desc)
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
