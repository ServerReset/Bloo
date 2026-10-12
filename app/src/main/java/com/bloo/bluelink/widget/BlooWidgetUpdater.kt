package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the widget in step with the app: whenever the car snapshot changes (a refresh, a command, an
 * alert poll) the widget redraws, and it redraws on demand after the restart sign-in. A widget only
 * costs anything while at least one is on a homescreen.
 */
object BlooWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    /** Starts following the snapshot store (once per process). */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            SnapshotStore(app).payload.debounce(600).collect { updateNow(app) }
        }
    }

    /** Redraws every placed widget, if there are any. */
    suspend fun updateNow(context: Context) {
        val app = context.applicationContext
        runCatching {
            if (GlanceAppWidgetManager(app).getGlanceIds(BlooWidget::class.java).isNotEmpty()) BlooWidget().updateAll(app)
        }
    }

    /** [updateNow] from code that is not suspending. */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        scope.launch { updateNow(app) }
    }
}
