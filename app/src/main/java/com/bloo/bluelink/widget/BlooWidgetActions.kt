package com.bloo.bluelink.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CarCommandRunner
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

    val PARAM_VIN = ActionParameters.Key<String>("vin")
    val PARAM_ACTION = ActionParameters.Key<String>("action")
}

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
        val widget = BlooWidget()

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

        edit { it[WidgetKeys.PENDING] = "$vin:$action"; it.remove(WidgetKeys.NOTE) }
        val result = runCatching { CarCommandRunner.execute(context, CarCommand(vin, action)) }.getOrNull()
        edit {
            it.remove(WidgetKeys.PENDING)
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
