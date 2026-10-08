package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
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
    fun replayDeckAdvancesByButtonThenSwipesToTheEnd() {
        val vm = viewModel()
        vm.showWelcomeCards()
        rule.setContent { BlooTheme { OnboardingScreen(vm, OnboardingMode.Replay) } }
        waitForCard("Welcome to Bloo", "Welcome")
        rule.onNodeWithText("Welcome to Bloo").assertIsDisplayed()

        // The bar's Next moves the button cards one by one.
        nextTo("Quick setup")
        rule.onNodeWithText("Quick setup").assertIsDisplayed()
        nextTo("Look and feel")
        nextTo("What to tell you")
        nextTo("Your watch")
        // The trailing information cards swipe instead of tapping Next; the bar animates away.
        nextTo("Getting around")
        rule.onRoot().performTouchInput { swipeLeft() }
        waitForCard("More Bloo can do", "after a swipe")
        // The last swipe hands off to the app and closes the deck.
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitUntil(30_000) { !vm.state.value.welcomeCardsOpen }
        assertFalse(vm.state.value.welcomeCardsOpen)
    }
}
