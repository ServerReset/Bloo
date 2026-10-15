package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The migration bridge's notice. This one screen is the entire reason that build exists -- it is the
 * only thing that tells a copy on the old signing key why every update it is offered now fails, and
 * what to do instead -- so it is tested for what it actually says, not merely that it draws.
 *
 * (The notice only appears in a build with BuildConfig.LEGACY_SIGNING set, which is a build-time
 * constant; the composable itself is rendered directly here, so the copy is covered by the ordinary
 * UI suite rather than only by the one-off bridge build.)
 */
class LegacySigningNoticeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun itSaysTheKeyChangedAndHowToMoveOn() {
        val host = DialogHost(HazeState())
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalDialogHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        LegacySigningNotice(onExport = {})
                        DialogLayer(host)
                    }
                }
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Last update for this copy").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Last update for this copy").assertIsDisplayed()
        // The steps, not "displayed": the body scrolls, so a step can be present and below the fold.
        // Existence is what matters -- an instruction nobody can read is the failure being guarded.
        listOf(
            "1. Export your settings.",
            "2. Uninstall Bloo.",
            "3. Download the current Bloo.apk from the GitHub releases page.",
        ).forEach { step ->
            assertTrue(
                "the notice does not say \"$step\"",
                rule.onAllNodesWithText(step, substring = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        assertTrue(
            "the notice offers no way to reach the releases page",
            rule.onAllNodesWithText("Releases page").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the notice offers no way to make the backup it asks for",
            rule.onAllNodesWithText("Export backup").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun theExportButtonCallsBack() {
        val host = DialogHost(HazeState())
        var exports = 0
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalDialogHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        LegacySigningNotice(onExport = { exports++ })
                        DialogLayer(host)
                    }
                }
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Export backup").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Export backup").performClick()
        rule.waitForIdle()
        assertTrue("the export button did not fire", exports == 1)
    }

    @Test
    fun laterDismissesIt() {
        val host = DialogHost(HazeState())
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalDialogHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        LegacySigningNotice(onExport = {})
                        DialogLayer(host)
                    }
                }
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Last update for this copy").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Later").performClick()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Last update for this copy").fetchSemanticsNodes().isEmpty()
        }
        assertTrue("the dismissed notice is dropped from the layer", host.entries.isEmpty())
    }
}
