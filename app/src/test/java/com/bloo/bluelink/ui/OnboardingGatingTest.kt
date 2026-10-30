package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Truth-table pins for the onboarding SETUP-step gate. These three functions are what decide
 * whether the user can leave setup, so a regression here either lets someone through with no way
 * to lock the app (a security hole) or strands them on setup forever (a soft-lock). Pure
 * functions so the whole matrix is pinned without an Activity or a permission dialog.
 */
class OnboardingGatingTest {

    // --- notifRequiredOnSetup ---------------------------------------------------------------

    @Test
    fun notificationsNotRequiredOffSetup() {
        assertFalse(notifRequiredOnSetup(onSetup = false, notificationsSupported = true, notifGranted = false))
    }

    @Test
    fun notificationsNotRequiredBelowApi33() {
        // POST_NOTIFICATIONS does not exist before Tiramisu, so it can never be required there.
        assertFalse(notifRequiredOnSetup(onSetup = true, notificationsSupported = false, notifGranted = false))
    }

    @Test
    fun notificationsRequiredOnSetupUntilGranted() {
        assertTrue(notifRequiredOnSetup(onSetup = true, notificationsSupported = true, notifGranted = false))
        assertFalse(notifRequiredOnSetup(onSetup = true, notificationsSupported = true, notifGranted = true))
    }

    // --- lockRequiredOnSetup ----------------------------------------------------------------

    @Test
    fun lockNotRequiredOffSetup() {
        assertFalse(lockRequiredOnSetup(onSetup = false, canBio = true, biometricLock = false))
        assertFalse(lockRequiredOnSetup(onSetup = false, canBio = false, biometricLock = false))
    }

    @Test
    fun biometricDeviceRequiresTheAppLockToBeOn() {
        // The phone's own lock is the unlock, so the app lock must be on before setup can finish.
        assertTrue(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = false))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = true))
    }

    @Test
    fun noScreenLockDeviceDoesNotBlockSetup() {
        // Nothing can authenticate, so there is nothing to turn on: the card explains it and Next
        // still works. A stale biometricLock flag changes nothing here.
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = false))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = true))
    }

    // --- setupIsBlocked (the actual Next gate) ----------------------------------------------

    @Test
    fun neverBlockedOffSetup() {
        for (supported in bools) for (granted in bools) for (bio in bools) for (lock in bools) {
            assertFalse(setupIsBlocked(false, supported, granted, bio, lock))
        }
    }

    @Test
    fun blockedUntilEveryRequirementMeets() {
        // API 33, biometrics device: blocked until BOTH notifications granted AND the app lock on.
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = false, canBio = true, biometricLock = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = true, biometricLock = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = false, canBio = true, biometricLock = true))
        assertFalse(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = true, biometricLock = true))
    }

    @Test
    fun appi33NoScreenLockOnlyBlocksOnNotifications() {
        assertFalse(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = false, biometricLock = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = false, canBio = false, biometricLock = false))
    }

    @Test
    fun belowApi33OnlyTheLockGateApplies() {
        // Pre-33 there is no notification permission, so only the lock blocks.
        assertFalse(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = true, biometricLock = true))
        assertTrue(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = true, biometricLock = false))
        assertFalse(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = false, biometricLock = false))
    }

    @Test
    fun fullFuzzNeverLetsAnUnlockedDeviceThrough() {
        // Exhaustive over the setup-relevant inputs: when NOT blocked, a device that can authenticate
        // must have the app lock on, and its notification gate (if applicable) must be satisfied. This
        // is the invariant that matters -- never let a user through without the lock they can have.
        for (supported in bools) for (granted in bools) for (bio in bools) for (lock in bools) {
            val blocked = setupIsBlocked(true, supported, granted, bio, lock)
            if (!blocked) {
                assertTrue(!bio || lock, "let through with the lock off: bio=$bio lock=$lock")
                assertTrue(!(supported && !granted), "let through without notifications")
            }
            if ((bio && !lock) || (supported && !granted)) {
                assertTrue(blocked, "should have blocked: supported=$supported granted=$granted bio=$bio lock=$lock")
            }
        }
    }

    private val bools = booleanArrayOf(true, false)
}
