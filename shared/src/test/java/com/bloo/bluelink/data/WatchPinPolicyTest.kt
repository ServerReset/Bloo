package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exhaustive pins for the watch PIN gate. The gate is the one place a mistake means either a
 * watch that never asks for the PIN (a security hole) or one that asks constantly (unusable),
 * so every timing x event x session-state combination is asserted, not sampled.
 */
class WatchPinPolicyTest {

    @Test
    fun fromWire_mapsKnownKeys_andFallsBackToOff() {
        assertEquals(WatchLockTiming.OFF, WatchLockTiming.fromWire("off"))
        assertEquals(WatchLockTiming.OPEN, WatchLockTiming.fromWire("open"))
        assertEquals(WatchLockTiming.COMMANDS, WatchLockTiming.fromWire("commands"))
        assertEquals(WatchLockTiming.BOTH, WatchLockTiming.fromWire("both"))
        // Unknown / null / legacy junk must read as OFF, never throw.
        assertEquals(WatchLockTiming.OFF, WatchLockTiming.fromWire(null))
        assertEquals(WatchLockTiming.OFF, WatchLockTiming.fromWire(""))
        assertEquals(WatchLockTiming.OFF, WatchLockTiming.fromWire("screen_off"))
        assertEquals(WatchLockTiming.OFF, WatchLockTiming.fromWire("BOTH"))
    }

    @Test
    fun locksOnOpen_onlyForOpenAndBoth() {
        assertFalse(WatchPinPolicy.locksOnOpen(WatchLockTiming.OFF))
        assertTrue(WatchPinPolicy.locksOnOpen(WatchLockTiming.OPEN))
        assertFalse(WatchPinPolicy.locksOnOpen(WatchLockTiming.COMMANDS))
        assertTrue(WatchPinPolicy.locksOnOpen(WatchLockTiming.BOTH))
    }

    @Test
    fun locksOnCommand_onlyForCommandsAndBoth() {
        assertFalse(WatchPinPolicy.locksOnCommand(WatchLockTiming.OFF))
        assertFalse(WatchPinPolicy.locksOnCommand(WatchLockTiming.OPEN))
        assertTrue(WatchPinPolicy.locksOnCommand(WatchLockTiming.COMMANDS))
        assertTrue(WatchPinPolicy.locksOnCommand(WatchLockTiming.BOTH))
    }

    @Test
    fun openGate_asksUntilSessionUnlocked() {
        for (t in WatchLockTiming.entries) {
            val expectedBefore = WatchPinPolicy.locksOnOpen(t)
            assertEquals(
                expectedBefore,
                WatchPinPolicy.requiresUnlock(t, WatchPinPolicy.GateEvent.OPEN_APP, alreadyUnlockedThisSession = false),
                "open, not-yet-unlocked, timing=$t",
            )
            // Once the session is unlocked, opening never re-asks for ANY timing.
            assertFalse(
                WatchPinPolicy.requiresUnlock(t, WatchPinPolicy.GateEvent.OPEN_APP, alreadyUnlockedThisSession = true),
                "open, already unlocked, timing=$t",
            )
        }
    }

    @Test
    fun commandGate_followsTheCommandFlagAndSession() {
        // OFF: never.
        assertFalse(WatchPinPolicy.requiresUnlock(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.SEND_COMMAND, false))
        assertFalse(WatchPinPolicy.requiresUnlock(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.SEND_COMMAND, true))

        // OPEN-only: the command gate is off, so commands never ask.
        assertFalse(WatchPinPolicy.requiresUnlock(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.SEND_COMMAND, false))
        assertFalse(WatchPinPolicy.requiresUnlock(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.SEND_COMMAND, true))

        // COMMANDS-only: every command asks (no open-unlock exists to satisfy it).
        assertTrue(WatchPinPolicy.requiresUnlock(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.SEND_COMMAND, false))
        assertTrue(WatchPinPolicy.requiresUnlock(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.SEND_COMMAND, true))

        // BOTH: a command asks only until the session is unlocked at open.
        assertTrue(WatchPinPolicy.requiresUnlock(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.SEND_COMMAND, false))
        assertFalse(WatchPinPolicy.requiresUnlock(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.SEND_COMMAND, true))
    }

    @Test
    fun fullMatrix_hasNoSurprises() {
        // One assertion per (timing, event, unlocked) cell, spelled out so any future change to
        // the policy has to consciously edit this table.
        val expected = mapOf(
            // OFF: nothing ever asks.
            Triple(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.OPEN_APP, false) to false,
            Triple(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.OPEN_APP, true) to false,
            Triple(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.SEND_COMMAND, false) to false,
            Triple(WatchLockTiming.OFF, WatchPinPolicy.GateEvent.SEND_COMMAND, true) to false,
            // OPEN: app opens ask until unlocked; commands never ask (a command is not the
            // gated act under OPEN).
            Triple(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.OPEN_APP, false) to true,
            Triple(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.OPEN_APP, true) to false,
            Triple(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.SEND_COMMAND, false) to false,
            Triple(WatchLockTiming.OPEN, WatchPinPolicy.GateEvent.SEND_COMMAND, true) to false,
            // COMMANDS: commands always ask; opening never does.
            Triple(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.OPEN_APP, false) to false,
            Triple(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.OPEN_APP, true) to false,
            Triple(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.SEND_COMMAND, false) to true,
            Triple(WatchLockTiming.COMMANDS, WatchPinPolicy.GateEvent.SEND_COMMAND, true) to true,
            // BOTH: opening asks until unlocked; commands then follow that unlock.
            Triple(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.OPEN_APP, false) to true,
            Triple(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.OPEN_APP, true) to false,
            Triple(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.SEND_COMMAND, false) to true,
            Triple(WatchLockTiming.BOTH, WatchPinPolicy.GateEvent.SEND_COMMAND, true) to false,
        )
        for (t in WatchLockTiming.entries) {
            for (e in WatchPinPolicy.GateEvent.entries) {
                for (u in listOf(false, true)) {
                    val key = Triple(t, e, u)
                    val got = WatchPinPolicy.requiresUnlock(t, e, u)
                    assertEquals(expected[key], got, "cell timing=$t event=$e unlocked=$u")
                }
            }
        }
    }
}
