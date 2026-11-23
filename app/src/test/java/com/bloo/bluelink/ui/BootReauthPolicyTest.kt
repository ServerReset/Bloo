package com.bloo.bluelink.ui

import com.bloo.bluelink.ui.BootReauthPolicy.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** After a phone restart: typed details only, then at most one fingerprint or screen-lock prompt before the app is open. */
class BootReauthPolicyTest {
    /**
     * The whole restart sign-in, as the view model runs it: details, at most one prompt, then the cold start. Returns how many
     * prompts the person saw and whether the app ended up open and unlocked.
     */
    private fun signIn(detailsRight: Boolean, vaultLocked: Boolean, appLock: Boolean, promptPasses: Boolean = true): Pair<Int, Boolean> {
        var prompts = 0
        // A sealed vault cannot say whether the details match until the prompt has opened it.
        val firstCheck = if (vaultLocked) false else detailsRight
        val opened = when (BootReauthPolicy.afterDetails(firstCheck, vaultLocked, appLock, canPrompt = true)) {
            Step.Reject, Step.CannotPrompt -> false
            Step.Open -> true
            Step.Prompt -> {
                prompts++
                BootReauthPolicy.opensAfterPrompt(promptPasses, detailsRight)
            }
        }
        if (!opened) return prompts to false
        // The cold start that follows, straight after the sign-in.
        val locks = BootReauthPolicy.coldStartLocks(lockOn = appLock, justBooted = true, deviceLocked = false, justConfirmed = true)
        if (locks) prompts++
        return prompts to true
    }

    @Test fun theRestartSignInOffersNoFingerprint() {
        assertFalse(BootReauthPolicy.offersFingerprint(canUseBiometrics = true, bootReauth = true))
        assertTrue(BootReauthPolicy.offersFingerprint(canUseBiometrics = true, bootReauth = false), "an ordinary sign-in still offers it")
    }

    @Test fun withTheAppLockOnItIsExactlyOnePrompt() {
        assertEquals(1 to true, signIn(detailsRight = true, vaultLocked = false, appLock = true))
        assertEquals(1 to true, signIn(detailsRight = true, vaultLocked = true, appLock = true))
    }

    @Test fun aSealedVaultIsOpenedByThatSamePrompt() {
        assertEquals(1 to true, signIn(detailsRight = true, vaultLocked = true, appLock = false))
    }

    @Test fun nothingSealedAndNoAppLockNeedsNoPrompt() {
        assertEquals(0 to true, signIn(detailsRight = true, vaultLocked = false, appLock = false))
    }

    @Test fun wrongDetailsNeverOpen() {
        assertEquals(0 to false, signIn(detailsRight = false, vaultLocked = false, appLock = true))
        assertEquals(1 to false, signIn(detailsRight = false, vaultLocked = true, appLock = true), "known wrong only once unsealed")
    }

    @Test fun aFailedPromptDoesNotOpen() {
        assertEquals(1 to false, signIn(detailsRight = true, vaultLocked = true, appLock = true, promptPasses = false))
    }

    @Test fun anOrdinaryColdStartStillLocks() {
        assertTrue(BootReauthPolicy.coldStartLocks(lockOn = true, justBooted = false, deviceLocked = false, justConfirmed = false))
        assertTrue(BootReauthPolicy.coldStartLocks(lockOn = true, justBooted = true, deviceLocked = true, justConfirmed = false))
        assertFalse(BootReauthPolicy.coldStartLocks(lockOn = false, justBooted = false, deviceLocked = false, justConfirmed = false))
    }
}
