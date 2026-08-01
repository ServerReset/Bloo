package com.bloo.bluelink.widget

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-widget-instance configuration, persisted on-device only.
 *
 * Each placed widget has a stable integer appWidgetId; this store keeps one
 * [WidgetConfig] JSON blob per id under its own DataStore file (separate from the
 * app's synced settings, so widget layout never roams to another device via Drive).
 * Reads fall back to a fresh default [WidgetConfig] whenever an id has no saved
 * config yet, so a just-dropped widget renders immediately.
 *
 * The whole map is stored as one JSON string so a read/write is a single atomic
 * DataStore edit; a corruption handler resets to empty rather than throwing into
 * the widget's separate process (which can't surface an error to the user).
 */
class WidgetConfigStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Serializable mirror of [WidgetConfig] (the UI model isn't itself @Serializable
     *  so the persisted shape can evolve independently of the runtime model). */
    @Serializable
    private data class Stored(
        val vin: String? = null,
        val actions: List<String> = WidgetAction.DEFAULTS,
        val infoFields: List<String> = WidgetInfoField.DEFAULTS,
        val showRing: Boolean = true,
        val showMap: Boolean = false,
        val photoBackground: Boolean = false,
        val priority: String = WidgetConfig.PRIORITY_INFO,
        val pillShape: Boolean = false,
        // Defaults here are what a config saved before these fields existed
        // decodes to (ignoreUnknownKeys handles the other direction), so they
        // deliberately match WidgetConfig's own -- an upgrade must never
        // restyle a widget the user already set up.
        val corner: String = WidgetConfig.CORNER_SOFT,
        val backgroundOpacity: Float = 1f,
        val textScale: Float = 1f,
        val showHeader: Boolean = true,
        val showFooter: Boolean = true,
        val accent: String? = null,
        val theme: String = WidgetConfig.THEME_AUTO,
    )

    private fun Stored.toConfig() = WidgetConfig(
        vin, actions, infoFields, showRing, showMap, photoBackground, priority, pillShape,
        corner, backgroundOpacity, textScale, showHeader, showFooter, accent, theme,
    )
    private fun WidgetConfig.toStored() = Stored(
        vin, actions, infoFields, showRing, showMap, photoBackground, priority, pillShape,
        corner, backgroundOpacity, textScale, showHeader, showFooter, accent, theme,
    )

    /** Read one widget's config, or a default if it's never been configured. */
    suspend fun get(widgetId: Int): WidgetConfig {
        val raw = context.widgetConfigStore.data.first()[key(widgetId)] ?: return WidgetConfig()
        return runCatching { json.decodeFromString(Stored.serializer(), raw).toConfig() }
            .getOrDefault(WidgetConfig())
    }

    /** Persist one widget's config. */
    suspend fun set(widgetId: Int, config: WidgetConfig) {
        context.widgetConfigStore.edit {
            it[key(widgetId)] = json.encodeToString(Stored.serializer(), config.toStored())
        }
    }

    /** Drop a widget's config when the instance is removed from the launcher, so
     *  stale layout choices can't leak into a future widget that reuses the id. */
    suspend fun clear(widgetId: Int) {
        context.widgetConfigStore.edit { it.remove(key(widgetId)) }
    }

    private fun key(widgetId: Int) = stringPreferencesKey("widget_cfg_$widgetId")

    private companion object {
        val Context.widgetConfigStore by preferencesDataStore(
            name = "bloo_widget_config",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )
    }
}
