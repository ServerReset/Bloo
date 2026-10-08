package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bloo.bluelink.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The lock/unlock control, rebuilt on the new button family ([PrimaryActions]) now that the legacy
 * StateControl is gone: the lock button and the brand-conditional flash/horn icon buttons form a
 * three-member cluster at the same target height as every other button.
 */
class PrimaryActionsTest {
    @get:Rule val rule = createComposeRule()

    private fun vehicle(hyundai: Boolean) = Vehicle(
        vin = "TESTVIN0000000000",
        regId = "reg",
        name = "Ioniq 5",
        model = "Ioniq 5",
        generation = "GN5",
        brandIndicator = if (hyundai) "HYUNDAI" else "K",
        isEv = true,
    )

    private fun render(hyundai: Boolean) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        val v = vehicle(hyundai)
        rule.setContent {
            BlooTheme {
                val state by vm.state.collectAsState()
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    PrimaryActions(v, state, vm)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun descExists(text: String) =
        rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun hyundaiShowsTheLockAndFlashAndHornButtons() {
        render(hyundai = true)
        assertTrue("the lock button is present", exists("Lock"))
        assertTrue("flash lights is present", descExists("Flash lights"))
        assertTrue("horn & lights is present", descExists("Horn & lights"))
    }

    /** Kia US has no horn/lights endpoint, so the cluster drops to the lock button alone. */
    @Test
    fun kiaShowsOnlyTheLockButton() {
        render(hyundai = false)
        assertTrue("the lock button is present", exists("Lock"))
        assertTrue("no flash lights for Kia US", !descExists("Flash lights"))
        assertTrue("no horn & lights for Kia US", !descExists("Horn & lights"))
    }

    /** The cluster is at the shared button height. */
    @Test
    fun theClusterIsTheTargetHeight() {
        render(hyundai = true)
        val h = rule.onNodeWithTag(PrimaryActionsClusterTag)
            .getBoundsInRoot().let { it.bottom.value - it.top.value }
        assertEquals("the lock cluster is ButtonTargetHeight tall", ButtonTargetHeight.value, h, 1f)
    }
}
