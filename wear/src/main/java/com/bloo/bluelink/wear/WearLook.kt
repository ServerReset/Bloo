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
        // Only controls somebody chose: the defaults showing are not a choice, and must not overwrite the phone's.
        slots = if (WearFacePrefs.hasSaved(context)) WearFacePrefs.load(context) else emptyList(),
        reduceEffects = reduceEffects(context),
        autoLock = WearAutoLock.isEnabled(context),
    )

    /** Tell the phone about a change made here, so it keeps it (and Drive with it) and the two stay the same. */
    fun sendToPhone(context: Context) = send(context, markDirty = true)

    /**
     * A change made here that the phone has not confirmed yet: while it is unconfirmed the phone's older copy must not
     * overwrite it (the phone may simply have been out of reach), and it is sent again with the next push.
     */
    private fun dirty(context: Context) = prefs(context).getBoolean("look_dirty", false)

    private fun send(context: Context, markDirty: Boolean) {
        val app = context.applicationContext
        if (markDirty) prefs(app).edit { putBoolean("look_dirty", true) }
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
            if (!seeded) { seeded = true; send(app, markDirty = false) }
            return
        }
        if (dirty(app)) {
            // The phone confirms a change by pushing it back unchanged; until then ours stands, and goes up again.
            val mine = current(app)
            val confirmed = (mine.slots.isEmpty() || mine.slots == look.slots) &&
                mine.reduceEffects == look.reduceEffects && mine.autoLock == look.autoLock
            if (!confirmed) { send(app, markDirty = true); return }
            prefs(app).edit { putBoolean("look_dirty", false) }
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
