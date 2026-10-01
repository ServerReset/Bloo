package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** The glass dialog layer: a dialog rises into view, takes taps, and is fully gone after it leaves. */
class DialogLayerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aDialogAppearsAndIsRemovedOnceDismissed() {
        val host = DialogHost(HazeState())
        var show by mutableStateOf(true)
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalDialogHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        if (show) {
                            GlassAlertDialog(
                                onDismissRequest = { show = false },
                                title = "Hello dialog",
                                text = { Text("Some body text") },
                                buttons = { SafeMorphTextButton("Close", onClick = { show = false }) },
                            )
                        }
                        DialogLayer(host)
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Hello dialog").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Hello dialog").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithText("Hello dialog").fetchSemanticsNodes().isEmpty() }
        assertTrue("the leaving dialog is dropped from the layer", host.entries.isEmpty())
    }
}
