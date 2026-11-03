package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.ClimateChoice
import com.bloo.bluelink.data.ClimateFeedback
import com.bloo.bluelink.data.WatchClimateFeedback
import com.bloo.bluelink.data.WatchSyncProtocol
import com.bloo.bluelink.data.decodeClimateChoices
import com.bloo.bluelink.data.encodeClimateChoices
import com.bloo.bluelink.data.privatePrefs
import com.bloo.bluelink.ioScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * The person's own past climate starts for each car, as the phone keeps them. The phone sends them with every sync, so the watch's smart
 * climate weighs the same history the phone does. Kept on disk too, so a cold start plans from them before the phone has said anything.
 */
internal object WearClimateChoices {
    private const val PREFS = "bloo_wear_climate_choices"
    private val _choices = MutableStateFlow<Map<String, List<ClimateChoice>>>(emptyMap())
    val choices: StateFlow<Map<String, List<ClimateChoice>>> = _choices

    /** The starts the person has set for [vin], oldest first. */
    fun choicesFor(vin: String): List<ClimateChoice> = _choices.value[vin].orEmpty()

    /** Reads what was stored last time, once. */
    fun load(context: Context) {
        if (_choices.value.isNotEmpty()) return
        _choices.value = context.privatePrefs(PREFS).all
            .mapNotNull { (vin, text) -> (text as? String)?.let { vin to decodeClimateChoices(it) } }
            .toMap()
    }

    private val _feedback = MutableStateFlow<Map<String, List<ClimateFeedback>>>(emptyMap())

    /** The feel answers for [vin], oldest first: the phone's, plus any the watch has given since. */
    fun feedbackFor(vin: String): List<ClimateFeedback> = _feedback.value[vin].orEmpty()

    /**
     * The person said how the plan felt on the watch: it is kept here (so the plan moves at once) and sent to the phone, which keeps it
     * with its own and sends the plan back.
     */
    fun recordFeedback(context: Context, vin: String, ambientF: Int, deltaF: Int) {
        _feedback.value = _feedback.value + (vin to (feedbackFor(vin) + ClimateFeedback(ambientF, deltaF)).takeLast(40))
        WearSmartClimate.replan()
        val bytes = Json.encodeToString(WatchClimateFeedback.serializer(), WatchClimateFeedback(vin, ambientF, deltaF)).encodeToByteArray()
        val app = context.applicationContext
        ioScope().launch {
            runCatching {
                Wearable.getNodeClient(app).connectedNodes.await().forEach {
                    Wearable.getMessageClient(app).sendMessage(it.id, WatchSyncProtocol.PATH_CLIMATE_FEEDBACK, bytes).await()
                }
            }
        }
    }

    /** The phone's feel answers arrived: kept, and any plan made from older ones is made again. */
    fun applyFeedback(map: Map<String, List<ClimateFeedback>>) {
        if (map == _feedback.value) return
        _feedback.value = map
        WearSmartClimate.replan()
    }

    /** The phone's choices arrived: kept, and any plan made from older ones is made again. Unchanged choices change nothing. */
    fun apply(context: Context, choices: Map<String, List<ClimateChoice>>) {
        if (choices == _choices.value) return
        val editor = context.privatePrefs(PREFS).edit().clear()
        choices.forEach { (vin, list) -> editor.putString(vin, encodeClimateChoices(list)) }
        editor.apply()
        _choices.value = choices
        WearSmartClimate.replan()
    }
}
