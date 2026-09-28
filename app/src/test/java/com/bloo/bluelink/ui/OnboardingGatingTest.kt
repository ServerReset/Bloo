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
        assertFalse(lockRequiredOnSetup(onSetup = false, canBio = true, biometricLock = false, appPinSet = false))
        assertFalse(lockRequiredOnSetup(onSetup = false, canBio = false, biometricLock = false, appPinSet = false))
    }

    @Test
    fun biometricDeviceRequiresBiometricsNotPin() {
        // canBio: biometrics is the required mechanism; a PIN does NOT satisfy the gate, and
        // biometrics being on satisfies it regardless of the PIN.
        assertTrue(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = false, appPinSet = false))
        assertTrue(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = false, appPinSet = true))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = true, appPinSet = false))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = true, biometricLock = true, appPinSet = true))
    }

    @Test
    fun noBiometricDeviceRequiresPinNotBiometrics() {
        // No biometrics: the PIN is the required mechanism, and a stale biometricLock flag
        // cannot satisfy it (the mechanism does not exist on this device).
        assertTrue(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = false, appPinSet = false))
        assertTrue(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = true, appPinSet = false))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = false, appPinSet = true))
        assertFalse(lockRequiredOnSetup(onSetup = true, canBio = false, biometricLock = true, appPinSet = true))
    }

    // --- setupIsBlocked (the actual Next gate) ----------------------------------------------

    @Test
    fun neverBlockedOffSetup() {
        for (supported in bools) for (granted in bools) for (bio in bools) for (lock in bools) for (pin in bools) {
            assertFalse(setupIsBlocked(false, supported, granted, bio, lock, pin))
        }
    }

    @Test
    fun blockedUntilEveryRequirementMeets() {
        // API 33, biometrics device: blocked until BOTH notifications granted AND biometrics on.
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = false, canBio = true, biometricLock = false, appPinSet = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = true, biometricLock = false, appPinSet = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = false, canBio = true, biometricLock = true, appPinSet = false))
        assertFalse(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = true, biometricLock = true, appPinSet = false))
    }

    @Test
    fun appi33NoBiometricBlocksUntilNotificationsAndPin() {
        assertFalse(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = false, biometricLock = false, appPinSet = true))
        assertTrue(setupIsBlocked(true, notificationsSupported = true, notifGranted = true, canBio = false, biometricLock = false, appPinSet = false))
    }

    @Test
    fun belowApi33OnlyTheLockGateApplies() {
        // Pre-33 there is no notification permission, so only the lock blocks.
        assertFalse(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = true, biometricLock = true, appPinSet = false))
        assertTrue(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = true, biometricLock = false, appPinSet = true))
        assertFalse(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = false, biometricLock = false, appPinSet = true))
        assertTrue(setupIsBlocked(true, notificationsSupported = false, notifGranted = false, canBio = false, biometricLock = false, appPinSet = false))
    }

    @Test
    fun fullFuzzNeverStrandedWithNoLockAndNeverLetsAnUnlockedDeviceThrough() {
        // Exhaustive over the 2^5 setup-relevant inputs: when NOT blocked, the device must
        // actually HAVE a usable lock (biometrics-on, or a PIN) AND its notification gate (if
        // applicable) must be satisfied. This is the invariant that matters -- never let a user
        // through with no way to lock the app.
        for (supported in bools) for (granted in bools) for (bio in bools) for (lock in bools) for (pin in bools) {
            val blocked = setupIsBlocked(true, supported, granted, bio, lock, pin)
            val hasLock = (bio && lock) || (!bio && pin)
            if (!blocked) {
                assertTrue(hasLock, "let through with no lock: bio=$bio lock=$lock pin=$pin")
                assertTrue(!(supported && !granted), "let through without notifications")
            }
            if (!hasLock || (supported && !granted)) {
                assertTrue(blocked, "should have blocked: supported=$supported granted=$granted bio=$bio lock=$lock pin=$pin")
            }
        }
    }

    private val bools = booleanArrayOf(true, false)
}
