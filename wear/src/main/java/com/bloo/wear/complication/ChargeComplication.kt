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
        buildData(type, pct = 82, rangeMi = 210, isEv = true)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Match the tile: show the user's selected car, not just the first.
        val snap = SnapshotStore(applicationContext).current().selected
            ?: return null
        return buildData(request.complicationType, pct = snap.percent, rangeMi = snap.rangeMi, isEv = snap.isEv)
    }

    private fun buildData(type: ComplicationType, pct: Int?, rangeMi: Int?, isEv: Boolean): ComplicationData? {
        val label = if (isEv) "Battery" else "Fuel"
        // Show "—%" rather than a blank slot when the level isn't known yet.
        val text = pct?.let { "$it%" } ?: "—%"
        val desc = PlainComplicationText.Builder(
            pct?.let { "$label $it%" } ?: "$label level unknown"
        ).build()
        val plainText = PlainComplicationText.Builder(text).build()
        val rangeTitle = rangeMi?.let { PlainComplicationText.Builder("$it mi").build() }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plainText, desc)
                    // Range is more useful than a brand name in the cramped title slot;
                    // omit the title entirely when there's no range so "%" isn't crowded.
                    .apply { rangeTitle?.let { setTitle(it) } }
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = (pct ?: 0).toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = desc,
                )
                    .setText(plainText)
                    .apply { rangeTitle?.let { setTitle(it) } }
                    .build()

            else -> null
        }
    }
}
