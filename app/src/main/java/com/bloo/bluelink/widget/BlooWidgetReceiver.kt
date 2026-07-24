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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Keep the broadcast alive via goAsync() so the DataStore write survives past
        // return of onDeleted — a bare CoroutineScope(Dispatchers.IO) can be killed on
        // process death before the write lands. finish() releases the receiver.
        val pending = goAsync()
        val store = SettingsStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { runCatching { store.clearWidgetConfig(it) } }
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
