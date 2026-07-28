package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Hosts [BlooWidget] and tidies up each widget's stored config when removed. */
class BlooWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BlooWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget added — start the periodic refresh worker. Without this, a user
        // who adds a widget without ever foregrounding the app (where MainActivity also
        // schedules it) would get no background refresh; and after the last widget is
        // removed (onDisabled cancels) then a new one added, refresh would stay dead
        // until the next app open. ExistingPeriodicWorkPolicy.KEEP makes it idempotent
        // with the MainActivity call.
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Keep the broadcast alive via goAsync() so the DataStore write survives past
        // return of onDeleted — a bare CoroutineScope(Dispatchers.IO) can be killed on
        // process death before the write lands. finish() releases the receiver.
        val pending = goAsync()
        val store = SettingsStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    runCatching { store.clearWidgetConfig(id) }
                    // Also delete this widget's cached map tile. Android reuses widget
                    // ids, so a new widget at a reused id with location enabled would
                    // otherwise decode the PREVIOUS widget's stale map until its own
                    // Location action runs. (clearWidgetConfig only clears prefs keys.)
                    runCatching { java.io.File(context.cacheDir, "widget_map_$id.png").delete() }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed — stop the periodic refresh worker so it doesn't run forever.
        WidgetRefreshWorker.cancel(context)
    }
}
