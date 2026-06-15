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
        val store = SettingsStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { runCatching { store.clearWidgetConfig(it) } }
        }
    }
}
