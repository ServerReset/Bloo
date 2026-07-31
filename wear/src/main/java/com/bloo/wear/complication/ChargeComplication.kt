package com.bloo.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.formatDistance
import com.bloo.wear.MainActivity
import com.bloo.wear.R
import com.bloo.wear.WearLocalStore
import kotlinx.coroutines.flow.first

/**
 * Watch-face complication for the selected car's charge/fuel level. Supports
 * SHORT_TEXT ("82%") and RANGED_VALUE (arc filled to 82%). Shows a bolt while
 * charging and taps through to open Bloo (optionally pre-selecting the car). Data
 * comes from the phone-synced [SnapshotStore] — no network needed.
 *
 * A [SuspendingComplicationDataSourceService] is a Wear OS system service the
 * watch-face host binds to and drives entirely through its own lifecycle — no
 * Activity, no Composable tree, no long-lived state. Each override is a
 * self-contained answer to one system request:
 *  - [getPreviewData] renders the complication picker's static preview; it must
 *    return instantly with plausible fake data (no suspension allowed).
 *  - [onComplicationRequest] answers the live-data request for one specific
 *    instanceId; being suspend it may read DataStore without blocking a UI thread.
 *  - [onComplicationDeactivated] garbage-collects that instance's per-slot car pin.
 */
class ChargeComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type, pct = 82, rangeMi = 210, isEv = true, charging = true)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = resolveComplicationCar(applicationContext, DATA_SOURCE, request.complicationInstanceId)
            ?: return null
        val metric = runCatching {
            WearLocalStore(applicationContext).flow.first().unitSystem == "metric"
        }.getOrDefault(false)
        return buildData(
            request.complicationType,
            snap.percent,
            snap.rangeMi,
            snap.hasBattery,
            snap.charging == true,
            snap.vin,
            metric,
        )
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        clearComplicationConfig(applicationContext, DATA_SOURCE, complicationInstanceId)
    }

    /**
     * Assemble the [ComplicationData] for whichever [type] the system asked for.
     * SHORT_TEXT is a compact "82%" plus optional range title + bolt; RANGED_VALUE
     * adds a 0–100 arc (percent coerced into range so a null/out-of-bounds reading
     * can't crash the builder). An unsupported type returns null (slot left blank).
     */
    private fun buildData(
        type: ComplicationType,
        pct: Int?,
        rangeMi: Int?,
        isEv: Boolean,
        charging: Boolean,
        vin: String? = null,
        metric: Boolean = false,
    ): ComplicationData? {
        val label = if (isEv) "Battery" else "Fuel"
        val text = pct?.let { "$it%" } ?: "—%"
        val descText = buildString {
            append(label)
            append(pct?.let { " $it%" } ?: " level unknown")
            if (charging) append(", charging")
        }
        val desc = PlainComplicationText.Builder(descText).build()
        val plainText = PlainComplicationText.Builder(text).build()
        val rangeTitle = rangeMi?.let { PlainComplicationText.Builder(formatDistance(it, metric)).build() }
        val tap = openAppIntent(vin)
        val bolt = if (charging) {
            MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_widget_bolt)).build()
        } else null

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plainText, desc)
                    .apply { rangeTitle?.let { setTitle(it) } }
                    .apply { bolt?.let { setMonochromaticImage(it) } }
                    .setTapAction(tap)
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = (pct ?: 0).coerceIn(0, 100).toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = desc,
                )
                    .setText(plainText)
                    .apply { rangeTitle?.let { setTitle(it) } }
                    .apply { bolt?.let { setMonochromaticImage(it) } }
                    .setTapAction(tap)
                    .build()

            else -> null
        }
    }

    /**
     * PendingIntent fired on tap — launches [MainActivity] (NEW_TASK, since a
     * complication tap isn't in an existing Activity stack; CLEAR_TOP to reuse a
     * running instance rather than stack a duplicate), optionally pre-selecting
     * [vin]. FLAG_IMMUTABLE is required for a PendingIntent handed to another
     * process (the watch-face host); the request code derives from the VIN so
     * per-car complications get distinct, non-colliding PendingIntents.
     */
    private fun openAppIntent(vin: String? = null): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            vin?.let { putExtra("vin", it) }
        }
        return PendingIntent.getActivity(
            this, (vin ?: "").hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val DATA_SOURCE = "ChargeComplication"
    }
}
