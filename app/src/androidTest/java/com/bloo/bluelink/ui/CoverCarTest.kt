package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The flip cover's car screen, measured on a real layout at the sizes cover displays actually come in:
 * the home card fills the screen, and every control on it is on screen and big enough to hit. This is
 * what stands in for looking at it on a flip phone.
 */
class CoverCarTest {
    @get:Rule val rule = createComposeRule()

    private val car = Vehicle(
        vin = "COVER1", regId = "r", name = "Daisy", model = "Ioniq", generation = "5",
        brandIndicator = "H", isEv = true,
    )

    private fun check(width: Dp, height: Dp) {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = AppViewModel(app)
        val state = mutableStateOf(UiState(vehicles = listOf(car)))
        rule.setContent {
            BlooTheme {
                Box(Modifier.size(width, height)) {
                    CoverCar(car, state, vm, HazeState())
                }
            }
        }
        rule.waitForIdle()
        val d = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val home = rule.onNodeWithTag(COVER_HOME_TAG).getBoundsInRoot()
        val homeH = home.bottom.value - home.top.value
        assertTrue("home card should fill most of a ${width}x$height cover, was ${homeH}dp tall", homeH >= height.value * 0.75f)
        val buttons = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.top < height.value * d } // the ones on the first screen
        assertTrue("the home card should offer at least two actions, found ${buttons.size}", buttons.size >= 2)
        buttons.forEach {
            assertTrue("a control runs off the left/right edge: $it", it.left >= -1f && it.right <= width.value * d + 1f)
            assertTrue("a control is under 40dp tall: ${it.height / d}dp", it.height / d >= 40f)
        }
    }

    @Test fun smallCover() = check(260.dp, 260.dp)

    @Test fun mediumCover() = check(300.dp, 300.dp)

    @Test fun largeCover() = check(360.dp, 340.dp)
}
