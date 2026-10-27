package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The map that opens is the one that was in the pebble: moved between places, never rebuilt, and sized from the pebble's rectangle. */
class MapMorphTest {
    @get:Rule val rule = createComposeRule()

    @Test fun movingTheMapBetweenAListItemAndTheRootKeepsTheSameInstance() {
        var open by mutableStateOf(false)
        var built = 0
        rule.setContent {
            val movable = remember {
                movableContentOf { modifier: Modifier ->
                    remember { built++ }
                    Box(modifier.testTag("map"))
                }
            }
            Box(Modifier.fillMaxSize()) {
                // The pebble sits in a lazily composed list, the overlay at the root: two different compositions.
                LazyColumn { item { Box(Modifier.size(100.dp)) { if (!open) movable(Modifier.fillMaxSize()) } } }
                if (open) movable(Modifier.size(300.dp))
            }
        }
        rule.waitForIdle()
        assertEquals("built once in the pebble", 1, built)
        open = true
        rule.waitForIdle()
        assertEquals("moved to the full-screen layer, not rebuilt", 1, built)
        open = false
        rule.waitForIdle()
        assertEquals("moved back to the pebble, not rebuilt", 1, built)
    }

    @Test fun theMapStartsOnThePebbleRectangleAndEndsOnTheSheet() {
        val origin = Rect(80f, 400f, 680f, 700f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BlooTheme {
                ExpandableMapLayer(
                    isExpanded = true,
                    originBounds = origin,
                    location = GeoLocation(37.0, -122.0),
                    vehicleName = "Car",
                    deviceLocation = null,
                    mapState = remember { CarMapState() },
                    map = { modifier, _ -> Box(modifier.testTag("map")) },
                    hazeState = null,
                    onRefreshLocation = {},
                    onDismiss = {},
                )
            }
        }
        // The first frame: placed, and the spring has barely begun, so the map is still on the pebble's rectangle (a few dp of it at most).
        rule.mainClock.advanceTimeByFrame()
        val start = rule.onNodeWithTag("map").getBoundsInRoot()
        val density = rule.density.density
        // The pebble's rectangle is in screen coordinates and the bounds are in the compose root's: the root need not sit at the screen's corner.
        val rootOnScreen = rule.onRoot().fetchSemanticsNode().layoutInfo.coordinates.positionOnScreen()
        assertEquals("left", (origin.left - rootOnScreen.x) / density, start.left.value, 15f)
        assertEquals("top", (origin.top - rootOnScreen.y) / density, start.top.value, 15f)
        assertEquals("width", origin.width / density, (start.right - start.left).value, 15f)
        assertEquals("height", origin.height / density, (start.bottom - start.top).value, 15f)

        // Part-way through it is neither: it is growing, not jumping.
        rule.mainClock.advanceTimeBy(150)
        val mid = rule.onNodeWithTag("map").getBoundsInRoot()
        val startWidth = (start.right - start.left).value
        val midWidth = (mid.right - mid.left).value
        assertTrue("wider part-way through ($midWidth > $startWidth)", midWidth > startWidth)

        // Run the opening out: the map ends as the sheet, the full width and 85% of the height, bottom-anchored.
        rule.mainClock.advanceTimeBy(5_000)
        val root = rule.onRoot().getBoundsInRoot()
        val end = rule.onNodeWithTag("map").getBoundsInRoot()
        assertTrue("wider at the end than part-way ($midWidth)", (end.right - end.left).value > midWidth)
        assertEquals("full width", root.right.value - root.left.value, (end.right - end.left).value, 3f)
        assertEquals("85% of the height", (root.bottom.value - root.top.value) * 0.85f, (end.bottom - end.top).value, 3f)
        assertEquals("bottom-anchored", root.bottom.value, end.bottom.value, 3f)
    }

    @Test fun closingPutsTheSameMapBackInItsSlotOnTheSlotsRectangle() {
        var open by mutableStateOf(false)
        var built = 0
        var slot by mutableStateOf<Rect?>(null)
        var dismissed = 0
        rule.setContent {
            BlooTheme {
                val movable = remember {
                    movableContentOf { modifier: Modifier ->
                        remember { built++ }
                        Box(modifier.testTag("map"))
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    // The pebble's slot: it keeps its size and place whether or not the map is in it.
                    Box(
                        Modifier.offset(40.dp, 200.dp).size(200.dp, 120.dp).testTag("slot")
                            .onGloballyPositioned { slot = Rect(it.positionOnScreen(), it.size.toSize()) },
                    ) { if (!open) movable(Modifier.fillMaxSize()) }
                    val origin = slot
                    if (open && origin != null) {
                        ExpandableMapLayer(
                            isExpanded = true,
                            originBounds = origin,
                            location = GeoLocation(37.0, -122.0),
                            vehicleName = "Car",
                            deviceLocation = null,
                            mapState = remember { CarMapState() },
                            map = { modifier, _ -> movable(modifier) },
                            hazeState = null,
                            onRefreshLocation = {},
                            onDismiss = { dismissed++; open = false },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val slotBounds = rule.onNodeWithTag("slot").getBoundsInRoot()
        assertEquals("built once, in the slot", 1, built)

        open = true
        rule.waitForIdle()
        val openBounds = rule.onNodeWithTag("map").getBoundsInRoot()
        val root = rule.onRoot().getBoundsInRoot()
        assertTrue("open: much bigger than the slot", (openBounds.right - openBounds.left).value > (slotBounds.right - slotBounds.left).value * 2)
        assertEquals("open: bottom-anchored", root.bottom.value, openBounds.bottom.value, 3f)

        // Tap the scrim above the sheet: that closes it.
        rule.onRoot().performTouchInput { click(Offset(width / 2f, 5f)) }
        rule.waitForIdle()
        assertEquals("dismissed once", 1, dismissed)
        val back = rule.onNodeWithTag("map").getBoundsInRoot()
        assertEquals("back in the slot: left", slotBounds.left.value, back.left.value, 2f)
        assertEquals("back in the slot: top", slotBounds.top.value, back.top.value, 2f)
        assertEquals("back in the slot: width", (slotBounds.right - slotBounds.left).value, (back.right - back.left).value, 2f)
        assertEquals("back in the slot: height", (slotBounds.bottom - slotBounds.top).value, (back.bottom - back.top).value, 2f)
        assertEquals("the same map came back, nothing was rebuilt", 1, built)
    }

    @Test fun theRealMapKeepsTheCarAtTheCentreOfItsBoxWhileItResizes() {
        val origin = Rect(80f, 400f, 680f, 700f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BlooTheme {
                val state = remember { CarMapState() }
                val movable = remember {
                    movableContentOf { modifier: Modifier, inputs: MapInputs ->
                        CarMap(inputs.location, modifier.testTag("map"), state, inputs.deviceLocation, inputs.interactive, inputs.tileArea)
                    }
                }
                ExpandableMapLayer(
                    isExpanded = true,
                    originBounds = origin,
                    location = GeoLocation(37.0, -122.0),
                    vehicleName = "Car",
                    deviceLocation = null,
                    mapState = state,
                    map = { modifier, inputs -> movable(modifier, inputs) },
                    hazeState = null,
                    onRefreshLocation = {},
                    onDismiss = {},
                )
            }
        }
        // Let the map start (it waits a few frames the first time) and the opening get under way, then sample it as it grows.
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        var lastWidth = 0f
        repeat(6) { step ->
            rule.mainClock.advanceTimeBy(60)
            val box = rule.onNodeWithTag("map").getBoundsInRoot()
            val pin = rule.onNodeWithContentDescription("Car location").getBoundsInRoot()
            val boxCentreX = (box.left.value + box.right.value) / 2f
            val boxCentreY = (box.top.value + box.bottom.value) / 2f
            val width = box.right.value - box.left.value
            // The pin's tip (the bottom of its icon) is the car: it stays dead centre however big the box is at that moment.
            assertEquals("step $step: car x at the box centre (box ${width}dp wide)", boxCentreX, (pin.left.value + pin.right.value) / 2f, 2f)
            assertEquals("step $step: car y at the box centre", boxCentreY, pin.bottom.value, 2f)
            assertTrue("step $step: the box is growing ($width > $lastWidth)", width >= lastWidth)
            lastWidth = width
        }
    }
}
