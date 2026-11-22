package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** One parser for the file's tests, made once rather than per use. */
private val lenient = Json { ignoreUnknownKeys = true }

class WatchLookPrefsTest {
    @Test fun theWatchsSettingsSurviveTheWire() {
        val look = WatchLookPrefs(slots = listOf("lock", "climate", "flash", "none"), reduceEffects = true, autoLock = true)
        val back = lenient.decodeFromString(WatchLookPrefs.serializer(), lenient.encodeToString(WatchLookPrefs.serializer(), look))
        assertEquals(look, back)
    }

    @Test fun aPayloadFromAnOlderPhoneHasNoSettings() {
        // No `look` field at all: the watch then offers its own rather than taking defaults from a phone that has none.
        val payload = lenient.decodeFromString(WatchSyncPayload.serializer(), "{}")
        assertNull(payload.look)
    }

    @Test fun theSettingsTravelInsideAPayload() {
        val payload = WatchSyncPayload(look = WatchLookPrefs(slots = listOf("lock", "climate", "charge", "flash")))
        val back = lenient.decodeFromString(WatchSyncPayload.serializer(), lenient.encodeToString(WatchSyncPayload.serializer(), payload))
        assertEquals(listOf("lock", "climate", "charge", "flash"), back.look?.slots)
    }
}

class WatchCommandPathTest {
    @Test fun eachCommandHasItsOwnPath() {
        assertEquals("/bloo/command/abc", WatchSyncProtocol.commandPath("abc"))
        assertEquals(false, WatchSyncProtocol.commandPath("a") == WatchSyncProtocol.commandPath("b"))
    }

    @Test fun commandPathsAreRecognisedButTheResultPathIsNot() {
        assertEquals(true, WatchSyncProtocol.isCommandPath(WatchSyncProtocol.PATH_COMMAND))
        assertEquals(true, WatchSyncProtocol.isCommandPath(WatchSyncProtocol.commandPath("abc")))
        // "/bloo/command_result" starts with "/bloo/command" but is the phone's answer, not a request.
        assertEquals(false, WatchSyncProtocol.isCommandPath(WatchSyncProtocol.PATH_COMMAND_RESULT))
        assertEquals(false, WatchSyncProtocol.isCommandPath(WatchSyncProtocol.PATH_SNAPSHOT))
    }
}

class WatchNotifyDefaultsTest {
    @Test fun everyAlertIsOnByDefaultAutoLockIncluded() {
        val p = WatchNotifyPrefs()
        assertEquals(true, p.charging)
        assertEquals(true, p.chargeComplete)
        assertEquals(true, p.lowBattery)
        assertEquals(true, p.autoLock)
        // A payload from an older phone has no autoLock field: it reads as on.
        val old = lenient.decodeFromString(WatchNotifyPrefs.serializer(), """{"charging":false}""")
        assertEquals(true, old.autoLock)
        assertEquals(false, old.charging)
    }
}
