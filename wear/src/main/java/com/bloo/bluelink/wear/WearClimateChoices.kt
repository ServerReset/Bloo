package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.ClimateChoice
import com.bloo.bluelink.data.ComfortCaps
import com.bloo.bluelink.data.ComfortModel
import com.bloo.bluelink.data.ClimateFeedback
import com.bloo.bluelink.data.ClimateModel
import com.bloo.bluelink.data.decodeClimateModel
import com.bloo.bluelink.data.learn
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

    private val _models = MutableStateFlow<Map<String, ClimateModel>>(emptyMap())

    /** What smart climate has learned for [vin] (from the phone, or from the watch's own answers since), or null before any. */
    fun modelFor(vin: String): ClimateModel? = _models.value[vin]

    private val _comfortCaps = MutableStateFlow<Map<String, ComfortCaps>>(emptyMap())
    private val _comfortModels = MutableStateFlow<Map<String, ComfortModel>>(emptyMap())

    /** What the car can heat and cool, as the phone has it. */
    fun comfortCapsFor(vin: String): ComfortCaps = _comfortCaps.value[vin] ?: ComfortCaps()

    /** What smart comfort has learned for [vin], as the phone has it. */
    fun comfortModelFor(vin: String): ComfortModel? = _comfortModels.value[vin]

    /** The phone's comfort setup and learned comfort arrived: kept, and any plan made from older ones is made again. */
    fun applyComfort(caps: Map<String, String>, models: Map<String, String>) {
        val decodedCaps = caps.mapValues { ComfortCaps.decode(it.value) }
        val decodedModels = models.mapValues { ComfortModel.decode(it.value) }
        if (decodedCaps == _comfortCaps.value && decodedModels == _comfortModels.value) return
        _comfortCaps.value = decodedCaps
        _comfortModels.value = decodedModels
        WearSmartClimate.replan()
    }

    /** The phone's learned numbers arrived: kept, and any plan made from older ones is made again. */
    fun applyModels(map: Map<String, String>) {
        val decoded = map.mapValues { decodeClimateModel(it.value) }
        if (decoded == _models.value) return
        _models.value = decoded
        WearSmartClimate.replan()
    }

    /**
     * The person said how the plan felt on the watch: it is kept here (so the plan moves at once) and sent to the phone, which keeps it
     * with its own and sends the plan back.
     */
    fun recordFeedback(context: Context, vin: String, ambientF: Int, deltaF: Int) {
        _feedback.value = _feedback.value + (vin to (feedbackFor(vin) + ClimateFeedback(ambientF, deltaF)).takeLast(40))
        // The model learns the answer at once, so the plan moves now; the phone learns the same answer and its numbers take over on sync.
        _models.value = _models.value + (vin to (modelFor(vin) ?: ClimateModel()).learn(ambientF, deltaF))
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
