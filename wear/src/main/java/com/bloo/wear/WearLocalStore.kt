package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearLocalStore by preferencesDataStore(name = "bloo_wear_local")

object WearTiles {
    const val SUMMARY = "summary"
    const val LOCK = "lock"
    const val CLIMATE = "climate"
    const val COMFORT = "comfort"
    const val PRESETS = "presets"
    const val CHARGE = "charge"
    const val LIMITS = "limits"
    const val LOCATION = "location"
    const val WEATHER = "weather"
    const val INFO = "info"
    const val DIAGNOSTICS = "diagnostics"
    const val AI = "ai"
    const val ASSIST = "assist"
    const val MORE = "more"

    val DEFAULT_ORDER = listOf(
        SUMMARY, LOCK, CLIMATE, COMFORT, PRESETS, CHARGE, LIMITS,
        LOCATION, WEATHER, INFO, DIAGNOSTICS, AI, ASSIST, MORE,
    )

    val LABELS = mapOf(
        SUMMARY to "Summary",
        LOCK to "Lock",
        CLIMATE to "Climate",
        COMFORT to "Comfort",
        PRESETS to "Presets",
        CHARGE to "Charge",
        LIMITS to "Charge limits",
        LOCATION to "Location",
        WEATHER to "Weather",
        INFO to "Info",
        DIAGNOSTICS to "Diagnostics",
        AI to "AI summary",
        ASSIST to "Assist",
        MORE to "More",
    )
}

data class WearLocalSettings(
    val fontScale: Float = 1f,
    val tileOrder: List<String> = WearTiles.DEFAULT_ORDER,
)

class WearLocalStore(private val context: Context) {

    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyTileOrder = stringPreferencesKey("tile_order")

    val flow: Flow<WearLocalSettings> = context.wearLocalStore.data.map { prefs ->
        val fontScale = prefs[keyFontScale]?.coerceIn(0.8f, 1.4f) ?: 1f
        val savedOrder = prefs[keyTileOrder]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: WearTiles.DEFAULT_ORDER
        // Merge: keep saved order, append any new keys that aren't in it yet.
        val knownKeys = WearTiles.DEFAULT_ORDER.toSet()
        val merged = savedOrder.filter { it in knownKeys } +
            WearTiles.DEFAULT_ORDER.filter { it !in savedOrder }
        WearLocalSettings(fontScale = fontScale, tileOrder = merged)
    }

    suspend fun setFontScale(f: Float) {
        context.wearLocalStore.edit { it[keyFontScale] = f.coerceIn(0.8f, 1.4f) }
    }

    suspend fun setTileOrder(order: List<String>) {
        context.wearLocalStore.edit { it[keyTileOrder] = order.joinToString(",") }
    }
}
