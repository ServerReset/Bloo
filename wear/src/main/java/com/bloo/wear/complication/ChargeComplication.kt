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
import kotlinx.coroutines.flow.first

/**
 * Watch-face complication for the selected car's charge/fuel level. Supports
 * SHORT_TEXT (e.g. "82%") and RANGED_VALUE (arc filled to 82 %). Shows a bolt
 * while charging, and taps through to open Bloo. Data comes from the phone-synced
 * [SnapshotStore] — no network needed.
 *
 * Mechanism: this is a [SuspendingComplicationDataSourceService], a Wear OS
 * system service (declared in the manifest, NOT started/bound by this app's
 * own code) that the watch-face host process binds to and drives entirely
 * through its own lifecycle -- there is no Activity, no Composable tree, and
 * no long-lived state here; every override below is a self-contained,
 * stateless answer to a single system-issued request:
 *  - [getPreviewData] is called synchronously by the complication *picker* UI
 *    (when the user is choosing a complication for a watch-face slot, before
 *    ever actually placing this one) to render a representative static
 *    preview -- it must return instantly with plausible fake data, since
 *    there's no live car to read yet and no suspension is allowed.
 *  - [onComplicationRequest] is called by the watch face itself (on its own
 *    schedule -- an initial render, a periodic refresh tick, or an explicit
 *    [androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester]
 *    push like [ComplicationConfigActivity] issues after a slot's car
 *    changes) whenever it needs fresh content for one specific
 *    `complicationInstanceId` (one physical slot on one watch face). Being a
 *    suspend function, it CAN do real async work (reading DataStore) without
 *    blocking a UI thread -- the system awaits the coroutine and applies
 *    whatever [ComplicationData] it returns (or clears the slot on null).
 *    [resolveComplicationCar] resolves which car this instance/slot is
 *    configured for (falling back to the app's selected car) before this
 *    reads its live snapshot.
 *  - [onComplicationDeactivated] is called when a slot showing this
 *    complication is removed from a watch face (the user picked something
 *    else, or removed the face) -- used here purely to garbage-collect the
 *    per-instance car pin this complication no longer needs.
 */
class ChargeComplication : SuspendingComplicationDataSourceService() {

    /** Static, instantly-available fake data for the complication picker's
     *  preview -- see the class doc comment. Values are representative, not
     *  read from any real car. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type, pct = 82, rangeMi = 210, isEv = true, charging = true)

    /** Answer one system-issued request for live data on a specific
     *  complication instance. Resolves which car this slot is pinned to (or
     *  null if no car / not configured, in which case the slot is left
     *  cleared), reads whether the user prefers metric units from the
     *  watch-local store, and builds the appropriate [ComplicationData] shape
     *  for whatever [ComplicationType] the watch face is actually requesting
     *  (a single instance can be asked for different types depending on the
     *  face's own slot configuration). */
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snap = resolveComplicationCar(applicationContext, "ChargeComplication", request.complicationInstanceId)
            ?: return null
        val metric = runCatching { com.bloo.wear.WearLocalStore(this).flow.first().unitSystem == "metric" }.getOrDefault(false)
        return buildData(request.complicationType, snap.percent, snap.rangeMi, snap.hasBattery, snap.charging == true, snap.vin, metric)
    }

    /** The system's signal that this complication instance no longer exists on
     *  any watch face -- clean up the per-instance car pin so it doesn't leak
     *  indefinitely in [ComplicationCarStore] for a slot that will never be
     *  queried again. */
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        clearComplicationConfig(applicationContext, "ChargeComplication", complicationInstanceId)
    }

    /**
     * Assemble the actual [ComplicationData] for whichever [type] the system
     * asked for, from raw car values. Builds the shared text/description/tap
     * pieces once, then branches only on the container shape:
     *  - SHORT_TEXT: a compact "82%" plus an optional range title and bolt icon.
     *  - RANGED_VALUE: the same text, but also declares a numeric 0-100 range
     *    so the watch face can render it as a filled arc/progress ring; the
     *    percent is coerced into that range so a null/out-of-bounds reading
     *    can't crash the builder (defaults to an empty ring at 0 rather than
     *    a missing value being ambiguous with "0%").
     *  - Anything else (a type this data source doesn't support) returns
     *    null, which the system will simply not render.
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

    /** The PendingIntent fired when the user taps this complication on the
     *  watch face -- launches [MainActivity] directly (NEW_TASK since a
     *  complication tap isn't coming from within an existing Activity stack;
     *  CLEAR_TOP to reuse/reset an already-running instance rather than
     *  stacking a duplicate), optionally pre-selecting [vin] so tapping a
     *  specific car's complication opens straight to that car. FLAG_IMMUTABLE
     *  is required for PendingIntents handed to another process (the
     *  watch-face host) on modern Android; the request code is derived from
     *  the VIN so distinct per-car complications get distinct, non-colliding
     *  PendingIntents rather than one overwriting another's extras. */
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
}
