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
    /**
     * The EXPLICIT verb to send for the state this complication actually rendered.
     *
     * Not a TOGGLE_* verb, deliberately. A TOGGLE_* is resolved by whoever RECEIVES
     * it, against THEIR snapshot -- so the direction is decided from the phone's copy
     * of the state, not the copy the user was looking at on the watch face. That is a
     * bug that toWearCommand records as reported from a real device: "I press
     * the button, nothing happens, and the watch says the command succeeded" (a null
     * snapshot on the phone resolves TOGGLE_LOCK to LOCK, so tapping an already-locked
     * car sends a redundant lock that succeeds and changes nothing).
     *
     * That fix landed in toWearCommand and in the Wear tile, which bakes its direction
     * in at render time, and not here -- even though a complication is the surface that
     * sits rendered LONGEST between updates, so its state is the most likely of all of
     * them to have diverged from the phone's.
     *
     * [on] is the same value passed to [iconRes]/[text]/[description], so the verb and
     * the glyph can never disagree about which direction the tap means. null means the
     * state was never known; resolve it the way the receiver already would, so that
     * case is unchanged.
     */
    protected abstract fun actionFor(on: Boolean?): String

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
        // We have no dedicated "unknown" glyph, and iconRes(false) is a *definite*
        // off-state icon (for the lock complication, the open padlock) -- rendering
        // it for an unsynced state would be indistinguishable from a confirmed
        // "Unlocked" reading. SHORT_TEXT still carries the neutral "—" text, so we
        // simply omit the icon there when unknown; MONOCHROMATIC_IMAGE is icon-only
        // with no text escape hatch, so we return null (empty slot) rather than a
        // misleading padlock.
        val image = if (known) {
            MonochromaticImage.Builder(
                Icon.createWithResource(this, iconRes(isOn)),
            ).build()
        } else {
            null
        }
        val desc = PlainComplicationText.Builder(if (known) description(isOn) else "State unknown").build()
        val tap = vin?.let { ComplicationTapReceiver.pendingIntent(this, it, actionFor(on)) }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(if (known) text(isOn) else "—").build(),
                    desc,
                )
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .apply { image?.let { setMonochromaticImage(it) } }
                    .apply { tap?.let { setTapAction(it) } }
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                image?.let {
                    MonochromaticImageComplicationData.Builder(it, desc)
                        .apply { tap?.let { t -> setTapAction(t) } }
                        .build()
                }

            else -> null
        }
    }
}
