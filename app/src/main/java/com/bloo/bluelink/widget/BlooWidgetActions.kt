package com.bloo.bluelink.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.delay

internal object WidgetKeys {
    /** The car this widget shows (unset: the first car). */
    val VIN = stringPreferencesKey("vin")
    /** "vin:action" while that command is in flight. */
    val PENDING = stringPreferencesKey("pending")
    /** "vin|millis": an Unlock waiting for its confirming second tap. */
    val ARMED = stringPreferencesKey("armed")
    /** "text|millis": a short result line (a failure) shown for a few seconds. */
    val NOTE = stringPreferencesKey("note")

    // Per-widget options, chosen in the configuration screen.
    val SHOW_CLIMATE = booleanPreferencesKey("show_climate")
    val SHOW_CHARGE = booleanPreferencesKey("show_charge")
    val SHOW_FLASH = booleanPreferencesKey("show_flash")
    val SHOW_REFRESH = booleanPreferencesKey("show_refresh")
    /** Tighter padding and no detail rows, for a smaller widget. */
    val COMPACT = booleanPreferencesKey("compact")
    /** The car's photo, blurred, as the widget's background (on by default; nothing shows without a photo). */
    val SHOW_PHOTO = booleanPreferencesKey("show_photo")
    /** How soft that photo is: [WidgetPhoto.BLUR_LIGHT], MEDIUM or STRONG. */
    val PHOTO_BLUR = androidx.datastore.preferences.core.intPreferencesKey("photo_blur")
    /** The widget's corner roundness: [WidgetCorner] index (medium by default). */
    val CORNER = androidx.datastore.preferences.core.intPreferencesKey("corner")
    /** The widget's text size: [WidgetTextSize] index (normal by default). */
    val TEXT_SIZE = androidx.datastore.preferences.core.intPreferencesKey("text_size")
    /** Colour the value and the charge bar by level (red low, amber, green, blue charging); off follows the theme. */
    val LEVEL_TINT = booleanPreferencesKey("level_tint")
    /** Show the charge bar and status chips when there is room (on by default). */
    val SHOW_EXTRAS = booleanPreferencesKey("show_extras")

    val PARAM_VIN = ActionParameters.Key<String>("vin")
    val PARAM_ACTION = ActionParameters.Key<String>("action")
}

/** How round the widget's corners are: small, medium (the default), large. */
internal enum class WidgetCorner(val label: String, val dp: Int) { SMALL("Small", 16), MEDIUM("Medium", 28), LARGE("Large", 40) }

/** How big the widget's text is relative to the layout's own sizing. */
internal enum class WidgetTextSize(val label: String, val scale: Float) { SMALL("Small", 0.9f), NORMAL("Normal", 1f), LARGE("Large", 1.12f) }

/** How long the "tap again to unlock" prompt stays live. */
internal const val UNLOCK_CONFIRM_MS = 6_000L

/** How long a result note stays on the widget. */
internal const val NOTE_MS = 8_000L

/**
 * A quick-action button on the widget. It runs the command in the background through the same runner
 * the watch uses. Unlocking asks for a second tap, because a homescreen is easy to brush; everything
 * waits for the restart sign-in (see [BootGate]).
 */
class CarActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val vin = parameters[WidgetKeys.PARAM_VIN] ?: return
        val action = parameters[WidgetKeys.PARAM_ACTION] ?: return
        if (BootGate.isLocked(context)) return
        // The strip widget runs these commands too: redraw whichever widget this tap came from.
        val widget: androidx.glance.appwidget.GlanceAppWidget =
            if (androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIds(BlooStripWidget::class.java).contains(glanceId)) BlooStripWidget() else BlooWidget()

        suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { it.toMutablePreferences().also(block) }
            widget.update(context, glanceId)
        }

        // Unlocking from a homescreen needs a second tap.
        if (action == CarAction.TOGGLE_LOCK) {
            val locked = SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin }?.locked == true
            if (locked) {
                var proceed = false
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val now = System.currentTimeMillis()
                    val armed = prefs[WidgetKeys.ARMED]?.split("|")
                    proceed = armed != null && armed[0] == vin && now - (armed.getOrNull(1)?.toLongOrNull() ?: 0L) < UNLOCK_CONFIRM_MS
                    prefs.toMutablePreferences().also { if (proceed) it.remove(WidgetKeys.ARMED) else it[WidgetKeys.ARMED] = "$vin|$now" }
                }
                if (!proceed) {
                    widget.update(context, glanceId)
                    // The prompt expires on its own; redraw once it has.
                    delay(UNLOCK_CONFIRM_MS + 300)
                    widget.update(context, glanceId)
                    return
                }
            }
        }

        val pendingKey = "$vin:$action"
        edit { it[WidgetKeys.PENDING] = pendingKey; it.remove(WidgetKeys.NOTE) }
        val result = runCatching { com.bloo.bluelink.data.runCarCommand(context, CarCommand(vin, action)) }.getOrNull()
        edit {
            // Only this command's own marker: a second tap while this ran replaced it, and clearing that one here
            // switched off the busy state of a command that is still running.
            if (it[WidgetKeys.PENDING] == pendingKey) it.remove(WidgetKeys.PENDING)
            if (result == null || !result.ok) {
                it[WidgetKeys.NOTE] = "${result?.message?.take(60) ?: "Couldn't reach the car"}|${System.currentTimeMillis()}"
            }
        }
        if (result?.ok != true) {
            delay(NOTE_MS + 300)
            widget.update(context, glanceId)
        }
    }
}
