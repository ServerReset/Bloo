package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import org.junit.Rule
import org.junit.Test

/**
 * The map's "you are here" dot must render whenever a device fix exists, and be absent when there
 * is none. Regression: the dot was gated behind an approximate-FINE permission check, so a user who
 * granted only COARSE location never saw it.
 */
class CarMapDeviceDotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(device: GeoLocation?) {
        rule.setContent {
            BlooTheme {
                Box(Modifier.size(300.dp)) {
                    CarMap(location = GeoLocation(37.0, -122.0), deviceLocation = device)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theDeviceDotShowsWhenAFixExists() {
        render(GeoLocation(37.001, -122.001))
        rule.onNodeWithContentDescription("Your location").assertExists()
    }

    @Test
    fun theDeviceDotIsAbsentWithoutAFix() {
        render(null)
        rule.onNodeWithContentDescription("Your location").assertDoesNotExist()
    }
}
