package com.bloo.bluelink.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel

/*
 * The building blocks every Bloo complication is made of, so each data source says WHAT it shows and none repeats HOW a
 * complication is put together: its text, its glyph, the tap that opens the app and the way an old reading is spoken.
 */

private fun plain(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

/** [res] as a complication glyph. */
internal fun Context.complicationIcon(res: Int): MonochromaticImage =
    MonochromaticImage.Builder(Icon.createWithResource(this, res)).build()

/** A tap that opens the watch app, on the face control [tapKey] when there is one ([requestCode] keeps each source's intent distinct). */
internal fun Context.complicationTap(requestCode: Int, tapKey: String? = null): PendingIntent {
    val intent = Intent(this, WearMainActivity::class.java)
    tapKey?.let { intent.putExtra(WearMainActivity.EXTRA_ACTION, it) }
    return PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** ", updated 2 hr ago" for a reading old enough to doubt, else nothing: how a spoken description owns up to its age. */
internal fun VehicleSnapshot.spokenAge(): String =
    if (isStale()) updatedLabel()?.let { ", updated $it" }.orEmpty() else ""

internal fun shortTextData(shown: String, description: String, icon: MonochromaticImage?, tap: PendingIntent): ComplicationData =
    ShortTextComplicationData.Builder(plain(shown), plain(description))
        .apply { icon?.let { setMonochromaticImage(it) } }
        .setTapAction(tap)
        .build()

internal fun longTextData(title: String, line: String, description: String, icon: MonochromaticImage?, tap: PendingIntent): ComplicationData =
    LongTextComplicationData.Builder(plain(line), plain(description))
        .setTitle(plain(title))
        .apply { icon?.let { setMonochromaticImage(it) } }
        .setTapAction(tap)
        .build()

internal fun glyphData(icon: MonochromaticImage, description: String, tap: PendingIntent): ComplicationData =
    MonochromaticImageComplicationData.Builder(icon, plain(description))
        .setTapAction(tap)
        .build()

internal fun rangedData(value: Float, shown: String, description: String, icon: MonochromaticImage?, tap: PendingIntent): ComplicationData =
    RangedValueComplicationData.Builder(value, 0f, 100f, plain(description))
        .setText(plain(shown))
        .apply { icon?.let { setMonochromaticImage(it) } }
        .setTapAction(tap)
        .build()
