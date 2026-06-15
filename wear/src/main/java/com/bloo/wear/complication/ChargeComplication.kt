package com.bloo.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bloo.bluelink.data.SnapshotStore

/**
 * Watch-face complication slot showing the selected car's charge/fuel level.
 * Supports SHORT_TEXT (e.g. "82%") and RANGED_VALUE (arc filled to 82 %).
 * The data comes from the phone-synced [SnapshotStore] — no network needed.
 */
class ChargeComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type, pct = 82, rangeMi = 210)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = SnapshotStore(applicationContext).current().vehicles.firstOrNull()
            ?: return null
        return buildData(request.complicationType, pct = snap.percent, rangeMi = snap.rangeMi)
    }

    private fun buildData(type: ComplicationType, pct: Int?, rangeMi: Int?): ComplicationData? {
        val text = pct?.let { "$it%" } ?: return null
        val desc = PlainComplicationText.Builder("Battery $text").build()
        val plainText = PlainComplicationText.Builder(text).build()
        val title = PlainComplicationText.Builder("Bloo").build()
        val rangeTitle = rangeMi?.let { PlainComplicationText.Builder("$it mi").build() }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plainText, desc)
                    .setTitle(title)
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = pct.toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = desc,
                )
                    .setText(plainText)
                    .setTitle(rangeTitle ?: title)
                    .build()

            else -> null
        }
    }
}
