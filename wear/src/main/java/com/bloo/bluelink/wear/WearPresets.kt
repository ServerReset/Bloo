package com.bloo.bluelink.wear

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.climatePresetDetail
import com.bloo.bluelink.data.degValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The phone's saved climate presets, as the watch holds them: pushed with every sync, kept in plain prefs (nothing
 * secret) so the tile and a face quarter can start one without the phone being in reach to ask. A preset is per car,
 * exactly as on the phone. [fahrenheit] is the phone's own unit choice, so a preset reads the same on both.
 */
internal object WearPresets {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(ClimatePreset.serializer()))

    private val _presets = MutableStateFlow<Map<String, List<ClimatePreset>>>(emptyMap())
    val presets: StateFlow<Map<String, List<ClimatePreset>>> = _presets

    @Volatile var fahrenheit: Boolean = true
        private set

    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("bloo_watch_presets", Context.MODE_PRIVATE)

    /** Reads what was last stored, once per process (the tile runs without the app having opened). */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            _presets.value = p.getString("presets", null)
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            fahrenheit = p.getBoolean("fahrenheit", true)
            loaded = true
        }
    }

    /** Takes the phone's presets (it is the source of truth) and stores them. */
    fun update(context: Context, presets: Map<String, List<ClimatePreset>>, fahrenheit: Boolean) {
        ensureLoaded(context)
        this.fahrenheit = fahrenheit
        if (_presets.value == presets && prefs(context).getBoolean("fahrenheit", true) == fahrenheit) return
        _presets.value = presets
        prefs(context).edit {
            putString("presets", json.encodeToString(serializer, presets))
            putBoolean("fahrenheit", fahrenheit)
        }
    }

    fun forCar(vin: String): List<ClimatePreset> = _presets.value[vin].orEmpty()

    fun find(vin: String, id: String): ClimatePreset? = forCar(vin).firstOrNull { it.id == id }

    /** A preset by id from any car (the chooser names a stored choice without knowing which car it is for). */
    fun findAny(id: String): ClimatePreset? = _presets.value.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == id } }

    /** "79° · Defrost · Heat": what a preset will set; the same words as the phone (see climatePresetDetail). */
    fun detail(req: ClimateRequest): String = climatePresetDetail(req, fahrenheit)
}
