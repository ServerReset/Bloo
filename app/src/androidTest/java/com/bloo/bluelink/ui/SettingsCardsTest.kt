package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every rebuilt settings card renders, opens when its title is tapped, and shows its content.
 * Catches a card that crashes on composition or never reveals what it is meant to hold.
 */
class SettingsCardsTest {
    @get:Rule val rule = createComposeRule()

    private fun viewModel() = AppViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Renders [card], taps [title] to open it, and waits for [expected] to appear. */
    private fun checkCard(title: String, expected: String, card: @Composable (AppViewModel) -> Unit) {
        val vm = viewModel()
        rule.setContent {
            BlooTheme {
                Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { card(vm) }
            }
        }
        rule.waitUntil(10_000) { exists(title) }
        // Whether a card starts open is the app's own default; make it open either way, then
        // close it and open it again so toggling is exercised too.
        fun open() {
            if (!exists(expected)) rule.onAllNodesWithText(title).onFirst().performClick()
            try {
                rule.waitUntil(8_000) { exists(expected) }
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("the \"$title\" card never showed \"$expected\"", e)
            }
        }
        open()
        rule.onAllNodesWithText(title).onFirst().performClick()
        try {
            rule.waitUntil(8_000) { !exists(expected) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("the \"$title\" card did not close when its title was tapped", e)
        }
        open()
        assertTrue(exists(expected))
    }

    @Test fun accounts() = checkCard("Accounts", "Sign in") { vm ->
        val state by vm.state.collectAsState()
        AccountsCardContent(state, vm)
    }

    @Test fun backupAndSync() = checkCard("Backup & sync", "Set up auto-sync") { vm ->
        val state by vm.state.collectAsState()
        BackupSyncCardContent(state, vm, androidx.compose.ui.platform.LocalContext.current, advanced = true)
    }

    @Test fun location() = checkCard("Location", "My location") { vm ->
        val appearance by vm.appearance.collectAsState()
        LocationCardContent(appearance, vm)
    }

    @Test fun visuals() = checkCard("Visuals", "Show welcome cards") { vm ->
        val appearance by vm.appearance.collectAsState()
        VisualsCardContent(appearance, advanced = true, vm = vm)
    }

    @Test fun security() = checkCard("Security", "Set up PIN") { vm ->
        val state by vm.state.collectAsState()
        val appearance by vm.appearance.collectAsState()
        SecurityCardContent(state, vm, canBio = false, context = androidx.compose.ui.platform.LocalContext.current, appearance = appearance)
    }

    @Test fun ai() = checkCard("AI", "On-device AI (Gemini Nano)") { vm ->
        val state by vm.state.collectAsState()
        AiCardContent(state, advanced = true, vm = vm)
    }

    @Test fun debugAndLogsAreOneCard() = checkCard("Debug", "Activity log") { vm ->
        DebugCardContent(
            listOf("one", "two"), vm, CoroutineScope(Dispatchers.Main),
            androidx.compose.ui.platform.LocalClipboard.current,
        )
    }

    @Test fun credits() = checkCard("Credits", "OkHttp") { vm -> CreditsCardContent(vm) }
}
