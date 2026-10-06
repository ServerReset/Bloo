package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The map's bottom toolbar is one connected [ButtonCluster] (the standard framework), one button per
 * [MapFeature], each of which fires its own action.
 */
class MapFeatureRowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun everyFeatureIsAButtonThatFiresItsAction() {
        var clicked = ""
        rule.setContent {
            BlooTheme {
                MapFeatureRow(
                    features = listOf(
                        MapFeature(Icons.Filled.DirectionsCar, "Car") { clicked = "Car" },
                        MapFeature(Icons.Filled.MyLocation, "Me") { clicked = "Me" },
                        MapFeature(Icons.Filled.Share, "Share") { clicked = "Share" },
                        MapFeature(Icons.Filled.Map, "Open in Maps") { clicked = "Open in Maps" },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Me").performClick()
        assertEquals("Me", clicked)
        rule.onNodeWithText("Share").performClick()
        assertEquals("Share", clicked)
    }
}
