package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The welcome-card deck, driven the way a person would: tap Next through it and dismiss at the end. */
class OnboardingDeckTest {
    @get:Rule val rule = createComposeRule()

    private fun viewModel() = AppViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun onCard(title: String) =
        rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun waitForCard(title: String, what: String) {
        try {
            rule.waitUntil(10_000) { onCard(title) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("expected the deck on \"$title\" ($what). Semantics:\n" + rule.onRoot().printToString(40), e)
        }
    }

    private fun nextTo(title: String) {
        rule.onNodeWithText("Next").performClick()
        waitForCard(title, "after Next")
    }

    @Test
    fun replayDeckAdvancesByButtonAndDismissesAtTheEnd() {
        val vm = viewModel()
        vm.showWelcomeCards()
        rule.setContent { BlooTheme { OnboardingScreen(vm, OnboardingMode.Replay) } }
        waitForCard("Welcome to Bloo", "Welcome")
        rule.onNodeWithText("Welcome to Bloo").assertIsDisplayed()

        // The bar's Next moves card by card: there is no swipe any more.
        nextTo("Quick setup")
        rule.onNodeWithText("Quick setup").assertIsDisplayed()
        nextTo("Look and feel")
        nextTo("What to tell you")
        nextTo("Your watch")
        nextTo("Getting around")
        nextTo("More Bloo can do")

        // The last card's button dismisses the deck.
        try {
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithText("Dismiss").assertIsDisplayed() }.isSuccess
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("The last card never showed a Dismiss button. Semantics:\n" + rule.onRoot().printToString(40), e)
        }
        assertTrue("the deck is open before dismissing", vm.state.value.welcomeCardsOpen)
        rule.onNodeWithText("Dismiss").performClick()
        // The exit plays fireworks and a 1s fade before the deck closes; software-rendered emulators are slow at it.
        rule.waitUntil(30_000) { !vm.state.value.welcomeCardsOpen }
        assertFalse(vm.state.value.welcomeCardsOpen)
    }
}
