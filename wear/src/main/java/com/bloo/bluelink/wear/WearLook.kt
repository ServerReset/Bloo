package com.bloo.bluelink.wear

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.DeviceStatus
import com.bloo.bluelink.data.WatchLookPrefs
import com.bloo.bluelink.data.WatchSyncProtocol
import com.bloo.bluelink.ioScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * The watch's own settings as one thing -- the face's four controls, the manual reduced-effects mode and its auto lock --
 * kept in step with the phone, which stores them with its own settings (so Google Drive carries them too). The phone is
 * the source of truth once it has any: each push brings them ([applyOrSeed]); a change made here is sent up
 * ([sendToPhone]). The alert switches already travel the same way ([WearNotificationPrefs]).
 */
internal object WearLook {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("bloo_watch_look", Context.MODE_PRIVATE)

    /** The wearer's own "reduce effects" switch (blur, motion and animation off, as under Battery Saver). */
    fun reduceEffects(context: Context): Boolean = prefs(context).getBoolean("reduce_effects", false)

    fun setReduceEffects(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("reduce_effects", on) }
        DeviceStatus.setManualLean(on)
    }

    fun current(context: Context) = WatchLookPrefs(
        slots = WearFacePrefs.load(context),
        reduceEffects = reduceEffects(context),
        autoLock = WearAutoLock.isEnabled(context),
    )

    /** Tell the phone about a change made here, so it keeps it (and Drive with it) and the two stay the same. */
    fun sendToPhone(context: Context) {
        val app = context.applicationContext
        val bytes = Json.encodeToString(WatchLookPrefs.serializer(), current(app)).encodeToByteArray()
        ioScope().launch {
            runCatching {
                Wearable.getNodeClient(app).connectedNodes.await().forEach {
                    Wearable.getMessageClient(app).sendMessage(it.id, WatchSyncProtocol.PATH_LOOK_PREFS, bytes).await()
                }
            }
        }
    }

    private var seeded = false

    /** The phone's copy if it has one; otherwise offer this watch's own, once, so the phone has something to keep. */
    fun applyOrSeed(context: Context, look: WatchLookPrefs?) {
        val app = context.applicationContext
        if (look == null) {
            if (!seeded) { seeded = true; sendToPhone(app) }
            return
        }
        apply(app, look)
    }

    internal fun apply(context: Context, look: WatchLookPrefs) {
        if (look.slots.isNotEmpty()) {
            val slots = WearFacePrefs.parse(look.slots.joinToString(","))
            if (slots != WearFacePrefs.load(context)) {
                WearFacePrefs.save(context, slots)
                WearSurfaces.refresh(context)
            }
        }
        if (look.reduceEffects != reduceEffects(context)) setReduceEffects(context, look.reduceEffects)
        if (look.autoLock != WearAutoLock.isEnabled(context)) WearAutoLock.setEnabled(context, look.autoLock)
    }
}
