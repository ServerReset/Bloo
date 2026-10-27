package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The car's pin is drawn on the map, above its centre, in the theme's primary colour, and not covered by the tiles. */
class CarMapPinTest {
    @get:Rule val rule = createComposeRule()

    @Test fun thePinIsDrawnAboveTheCentreOfTheMap() {
        var primary = Color.Unspecified
        rule.setContent {
            BlooTheme {
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.size(300.dp, 150.dp).testTag("map")) {
                    CarMap(GeoLocation(37.0, -122.0), Modifier.size(300.dp, 150.dp), CarMapState(), null, false)
                }
            }
        }
        // The map starts a few frames after it is composed.
        rule.waitForIdle()
        repeat(12) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("map").captureToImage().toPixelMap()
        val cx = image.width / 2
        val cy = image.height / 2
        val density = rule.density.density
        // The pin's body: 40dp tall, its tip at the centre, so the pixels from 40dp above to the centre.
        var pinPixels = 0
        for (y in (cy - (40 * density).toInt()) until cy) {
            for (x in (cx - (20 * density).toInt()) until (cx + (20 * density).toInt())) {
                val c = image[x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1)]
                if (kotlin.math.abs(c.red - primary.red) < 0.06f && kotlin.math.abs(c.green - primary.green) < 0.06f && kotlin.math.abs(c.blue - primary.blue) < 0.06f) pinPixels++
            }
        }
        assertTrue("the car's pin is drawn above the centre of the map ($pinPixels pixels in the theme's primary colour)", pinPixels > 40)
    }
}
