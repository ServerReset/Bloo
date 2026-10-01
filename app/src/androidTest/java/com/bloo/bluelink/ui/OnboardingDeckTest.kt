package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The welcome-card deck, driven the way a person would: tap Next, swipe, and dismiss at the end. */
class OnboardingDeckTest {
    @get:Rule val rule = createComposeRule()

    private fun viewModel() = AppViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun replayDeckAdvancesBySwipeAndButtonAndDismissesAtTheEnd() {
        val vm = viewModel()
        vm.showWelcomeCards()
        rule.setContent { BlooTheme { OnboardingScreen(vm, OnboardingMode.Replay) } }
        rule.waitUntil(10_000) { shown("Welcome to Bloo") }
        rule.onNodeWithText("Welcome to Bloo").assertIsDisplayed()

        // The Next button moves to the second card.
        rule.onNodeWithText("Next").performClick()
        rule.waitUntil(10_000) { shown("Quick setup") }
        rule.onNodeWithText("Quick setup").assertIsDisplayed()

        // Swiping the card moves to the third.
        rule.onNodeWithText("Quick setup").performTouchInput { swipeLeft() }
        rule.waitUntil(10_000) { shown("Look and feel") }

        // Two more taps reach the last card, whose button dismisses the deck.
        rule.onNodeWithText("Next").performClick()
        rule.waitUntil(10_000) { shown("Getting around") }
        rule.onNodeWithText("Next").performClick()
        rule.waitUntil(10_000) { shown("More Bloo can do") }
        // The label swaps to "Dismiss" with the card; give its animation a moment to settle.
        try {
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithText("Dismiss").assertIsDisplayed() }.isSuccess
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("The last card never showed a Dismiss button. Semantics:\n" + rule.onRoot().printToString(40), e)
        }
        assertTrue("the deck is open before dismissing", vm.state.value.welcomeCardsOpen)
        rule.onNodeWithText("Dismiss").performClick()
        rule.waitUntil(5_000) { !vm.state.value.welcomeCardsOpen }
        assertFalse(vm.state.value.welcomeCardsOpen)
    }
}
