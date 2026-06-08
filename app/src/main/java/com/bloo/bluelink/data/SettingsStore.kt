package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.ui.FontChoice
import com.bloo.bluelink.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "bloo_settings")

/** Which seat heat/cool functions a specific car actually has (user-configured). */
data class SeatConfig(
    val frontHeat: Boolean = true,
    val frontCool: Boolean = false,
    val rearHeat: Boolean = false,
    val rearCool: Boolean = false,
) {
    val any: Boolean get() = frontHeat || frontCool || rearHeat || rearCool
}

/** User-confirmed powertrain (the US API only exposes EV vs gas). */
enum class Powertrain { GAS, HYBRID, PHEV, EV }

/** Reorderable detail sections, in their default order. */
val DEFAULT_SECTIONS = listOf("climate", "charge", "information", "diagnostics")

/** App appearance preferences, kept separate from the session so sign-out keeps them. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("font_choice")
        val DYNAMIC = stringPreferencesKey("dynamic_color")
        val BIOMETRIC = stringPreferencesKey("biometric_lock")
        val LAST_VIN = stringPreferencesKey("last_vehicle_vin")
        val ORDER = stringPreferencesKey("vehicle_order")
    }

    data class Appearance(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val fontChoice: FontChoice = FontChoice.SYSTEM,
        val dynamicColor: Boolean = true,
        val biometricLock: Boolean = false,
    )

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            fontChoice = prefs[Keys.FONT]?.let { runCatching { FontChoice.valueOf(it) }.getOrNull() }
                ?: FontChoice.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC]?.toBooleanStrictOrNull() ?: true,
            biometricLock = prefs[Keys.BIOMETRIC]?.toBooleanStrictOrNull() ?: false,
        )
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC] = enabled.toString() }
    }

    suspend fun lastVehicleVin(): String? =
        context.settingsDataStore.data.first()[Keys.LAST_VIN]

    suspend fun setLastVehicleVin(vin: String) {
        context.settingsDataStore.edit { it[Keys.LAST_VIN] = vin }
    }

    /** User-defined display order of vehicles (by VIN). */
    suspend fun vehicleOrder(): List<String> =
        context.settingsDataStore.data.first()[Keys.ORDER]
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    suspend fun setVehicleOrder(order: List<String>) {
        context.settingsDataStore.edit { it[Keys.ORDER] = order.joinToString("\n") }
    }

    /** Optional user-set photo URL per vehicle (empty = use the default gradient). */
    suspend fun imageUrl(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("img_$vin")]?.takeIf { it.isNotBlank() }

    suspend fun setImageUrl(vin: String, url: String) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("img_$vin")
            if (url.isBlank()) it.remove(key) else it[key] = url.trim()
        }
    }

    // --- Per-car seat capability (the API has no reliable flags) ---------

    suspend fun seatConfig(vin: String, defaultRearHeat: Boolean = false): SeatConfig {
        val p = context.settingsDataStore.data.first()
        return SeatConfig(
            frontHeat = p[booleanPreferencesKey("seat_fh_$vin")] ?: true,
            frontCool = p[booleanPreferencesKey("seat_fc_$vin")] ?: false,
            rearHeat = p[booleanPreferencesKey("seat_rh_$vin")] ?: defaultRearHeat,
            rearCool = p[booleanPreferencesKey("seat_rc_$vin")] ?: false,
        )
    }

    suspend fun setSeatFlag(vin: String, field: String, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("seat_${field}_$vin")] = value }
    }

    // --- Per-car section order -------------------------------------------

    suspend fun sectionOrder(vin: String): List<String> {
        val saved = context.settingsDataStore.data.first()[stringPreferencesKey("sections_$vin")]
            ?.split(",")?.filter { it.isNotBlank() }
        val valid = saved?.filter { it in DEFAULT_SECTIONS } ?: emptyList()
        // Keep any defaults that aren't in the saved list (e.g. newly added sections).
        return (valid + DEFAULT_SECTIONS.filter { it !in valid })
    }

    suspend fun setSectionOrder(vin: String, order: List<String>) {
        context.settingsDataStore.edit { it[stringPreferencesKey("sections_$vin")] = order.joinToString(",") }
    }

    // --- Per-car powertrain override -------------------------------------

    suspend fun powertrain(vin: String): Powertrain? =
        context.settingsDataStore.data.first()[stringPreferencesKey("ptrain_$vin")]
            ?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }

    suspend fun setPowertrain(vin: String, value: Powertrain) {
        context.settingsDataStore.edit { it[stringPreferencesKey("ptrain_$vin")] = value.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setFontChoice(choice: FontChoice) {
        context.settingsDataStore.edit { it[Keys.FONT] = choice.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled.toString() }
    }
}
