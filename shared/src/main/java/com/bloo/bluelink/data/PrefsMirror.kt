package com.bloo.bluelink.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer

/**
 * A setting another device sends (the watch's copy of the phone's glass, typeface and colours), held in memory as a [StateFlow] and
 * kept on disk, so a cold start uses the last one sent before anything new arrives. Each of those settings wrote out this same
 * load / compare / store / emit by hand.
 *
 * Stored as JSON under one key. [legacy] reads what an older build stored under its own keys, so an update keeps the setting.
 */
class PrefsMirror<T>(
    private val prefsName: String,
    private val serializer: KSerializer<T>,
    initial: T,
    private val legacy: (SharedPreferences) -> T? = { null },
) {
    private val _value = MutableStateFlow(initial)
    val value: StateFlow<T> = _value

    @Volatile private var loaded = false

    /** Reads what was stored last time, once per process; later calls change nothing. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.privatePrefs(prefsName)
        val stored = prefs.getString(KEY, null)?.let { runCatching { BlooJson.decodeFromString(serializer, it) }.getOrNull() }
            ?: runCatching { legacy(prefs) }.getOrNull()
        if (stored != null) _value.value = stored
    }

    /** A new value arrived: held at once and kept. Null (nothing sent) or the same value changes nothing; returns whether it changed. */
    fun apply(context: Context, value: T?): Boolean {
        if (value == null || value == _value.value) return false
        context.privatePrefs(prefsName).edit { putString(KEY, BlooJson.encodeToString(serializer, value)) }
        _value.value = value
        return true
    }

    private companion object {
        const val KEY = "json"
    }
}
