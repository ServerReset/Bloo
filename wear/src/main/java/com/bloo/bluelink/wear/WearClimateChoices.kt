package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.ClimateChoice
import com.bloo.bluelink.data.decodeClimateChoices
import com.bloo.bluelink.data.encodeClimateChoices
import com.bloo.bluelink.data.privatePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
