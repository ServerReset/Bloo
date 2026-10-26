package com.bloo.bluelink.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.bloo.bluelink.data.VehicleSnapshot

/*
 * What the full widget and the strip share: the photo behind them, and the two short-lived marks the buttons leave in the widget's
 * state (an Unlock waiting for its second tap, and a failed command's note).
 */

/**
 * The car's photo as the widget's background, under a theme-matched veil so the text keeps its contrast in light and dark. The photo
 * loads inside the session (not in provideGlance), so changing the car or the option in the settings swaps it at once.
 */
@Composable
internal fun WidgetPhotoLayer(car: VehicleSnapshot?, opts: WidgetOptions) {
    val context = androidx.glance.LocalContext.current
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val night = WidgetPhoto.isNight(context)
    val photoKey = if (opts.photo && car != null) "${car.vin}:${opts.blur}:$night" else null
    LaunchedEffect(photoKey) {
        photo = if (photoKey != null && car != null) WidgetPhoto.load(context, car.vin, opts.blur, night) else null
    }
    photo?.let { bmp ->
        Image(ImageProvider(bmp), null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(GlanceModifier.fillMaxSize().background(ColorProvider(day = PhotoVeilDay, night = PhotoVeilNight))) {}
    }
}

/** Whether Unlock was tapped once on [vin] and is still waiting for the confirming second tap. */
internal fun Preferences.unlockArmedFor(vin: String, now: Long = System.currentTimeMillis()): Boolean =
    this[WidgetKeys.ARMED]?.split("|")?.takeIf {
        it.size == 2 && it[0] == vin && now - (it[1].toLongOrNull() ?: 0L) < UNLOCK_CONFIRM_MS
    } != null

/** A failed command's note while it is fresh (it takes the car name's place for a few seconds), else null. */
internal fun Preferences.freshNote(now: Long = System.currentTimeMillis()): String? =
    this[WidgetKeys.NOTE]?.split("|")?.takeIf { it.size == 2 && now - (it[1].toLongOrNull() ?: 0L) < NOTE_MS }?.get(0)
