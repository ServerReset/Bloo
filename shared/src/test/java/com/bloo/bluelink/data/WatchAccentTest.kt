package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchAccentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun thePhonesColoursTravelInsideAPayload() {
        val accent = WatchAccent(primary = 0xFF6750A4.toInt(), onPrimary = 0xFFFFFFFF.toInt(), secondary = 0xFF625B71.toInt(), tertiary = 0xFF7D5260.toInt())
        val payload = WatchSyncPayload(accent = accent)
        val back = json.decodeFromString(WatchSyncPayload.serializer(), json.encodeToString(WatchSyncPayload.serializer(), payload))
        assertEquals(accent, back.accent)
    }

    @Test fun aPayloadFromAnOlderPhoneHasNoColours() {
        // No `accent` field at all: the watch keeps the brand's colours rather than taking defaults from a phone that has none.
        val payload = json.decodeFromString(WatchSyncPayload.serializer(), "{}")
        assertNull(payload.accent)
    }
}
