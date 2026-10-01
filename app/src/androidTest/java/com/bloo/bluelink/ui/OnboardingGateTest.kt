package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * First run: the setup card holds you until notifications are on and a lock is set. On the test
 * emulator neither is true (no notification grant, no biometrics enrolled), so Next must be disabled
 * there and say why.
 */
class OnboardingGateTest {
    @get:Rule val rule = createComposeRule()

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theSetupCardBlocksNextUntilNotificationsAndALockAreSet() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { BlooTheme { OnboardingScreen(vm, OnboardingMode.FirstRun) } }
        rule.waitUntil(10_000) { exists("Welcome to Bloo") }
        rule.onNodeWithText("Get started").performClick()           // -> restore card
        rule.waitUntil(10_000) { exists("Restore a setup") }
        rule.onNodeWithText("Next").performClick()                  // -> setup card
        rule.waitUntil(10_000) { exists("Turn on notifications") || exists("Set up PIN") || exists("Save PIN") }
        rule.waitUntil(10_000) { exists("Turn on notifications above to continue.") || exists("Set up the lock above to continue.") }
        rule.onNodeWithText("Next").assertIsNotEnabled()
        assertTrue(exists("Required"))
    }
}
