package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.merged

private fun repo(context: Context) =
    BlueLinkRepository(BlueLinkApi(), SessionStore(context.applicationContext))

/** Cycle to the next car (looping) for all widgets. */
class SwitchCarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SnapshotStore(context.applicationContext).selectNext()
        BlooGlanceWidget().updateAll(context)
    }
}

/** Pull fresh status for the selected car and update the snapshot. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = SnapshotStore(context.applicationContext)
        val selected = store.current().selected ?: return
        runCatching { repo(context).status(selected.toVehicle(), refresh = false) }
            .getOrNull()
            ?.let { status -> store.updateVehicle(selected.merged(status)) }
        BlooGlanceWidget().updateAll(context)
    }
}

class LockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SnapshotStore(context.applicationContext).current().selected?.let {
            runCatching { repo(context).lock(it.toVehicle()) }
        }
        BlooGlanceWidget().updateAll(context)
    }
}

class UnlockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SnapshotStore(context.applicationContext).current().selected?.let {
            runCatching { repo(context).unlock(it.toVehicle()) }
        }
        BlooGlanceWidget().updateAll(context)
    }
}
