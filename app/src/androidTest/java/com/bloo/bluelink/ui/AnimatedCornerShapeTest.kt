package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** An animated corner reshapes the clip, and does it without recomposing the card that wears it. */
class AnimatedCornerShapeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theClipFollowsTheCornerWithoutRecomposing() {
        val corner = mutableStateOf(0.dp)
        var compositions = 0
        rule.setContent {
            val state = remember { object : State<Dp> { override val value get() = corner.value } }
            SideEffect { compositions++ }
            Box(Modifier.size(100.dp).testTag("card").clip(AnimatedCornerShape(state)).background(Color.Red))
        }
        rule.waitForIdle()
        val before = compositions
        val square = rule.onNodeWithTag("card").captureToImage()
        assertEquals("square corner is filled", Color.Red.toArgb(), square.toPixelMap()[1, 1].toArgb())

        corner.value = 50.dp
        rule.waitForIdle()
        val round = rule.onNodeWithTag("card").captureToImage()
        assertNotEquals("round corner is clipped away", Color.Red.toArgb(), round.toPixelMap()[1, 1].toArgb())
        assertEquals("the card did not recompose", before, compositions)
    }
}
