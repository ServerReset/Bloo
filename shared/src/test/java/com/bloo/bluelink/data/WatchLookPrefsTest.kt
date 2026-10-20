package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchLookPrefsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun theWatchsSettingsSurviveTheWire() {
        val look = WatchLookPrefs(slots = listOf("lock", "climate", "flash", "none"), reduceEffects = true, autoLock = true)
        val back = json.decodeFromString(WatchLookPrefs.serializer(), json.encodeToString(WatchLookPrefs.serializer(), look))
        assertEquals(look, back)
    }

    @Test fun aPayloadFromAnOlderPhoneHasNoSettings() {
        // No `look` field at all: the watch then offers its own rather than taking defaults from a phone that has none.
        val payload = json.decodeFromString(WatchSyncPayload.serializer(), """{"lockTiming":"off"}""")
        assertNull(payload.look)
    }

    @Test fun theSettingsTravelInsideAPayload() {
        val payload = WatchSyncPayload(look = WatchLookPrefs(slots = listOf("lock", "climate", "charge", "flash")))
        val back = json.decodeFromString(WatchSyncPayload.serializer(), json.encodeToString(WatchSyncPayload.serializer(), payload))
        assertEquals(listOf("lock", "climate", "charge", "flash"), back.look?.slots)
    }
}
