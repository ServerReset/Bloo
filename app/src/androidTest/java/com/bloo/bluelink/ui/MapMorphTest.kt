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

    /** The real map inside the real layer, with the pebble at [origin]; [state] is the map's pan and zoom, [dismissed] counts closes. */
    private fun setRealMap(origin: Rect, state: CarMapState, onDismiss: () -> Unit) {
        rule.setContent {
            BlooTheme {
                val movable = remember {
                    movableContentOf { modifier: Modifier, inputs: MapInputs ->
                        CarMap(inputs.location, modifier.testTag("map"), state, inputs.deviceLocation, inputs.interactive, inputs.tileArea, inputs.panWeight)
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
                    onDismiss = onDismiss,
                )
            }
        }
    }

    /** Where the car's pin tip is, in the compose root's dp. */
    private fun pinTip(): Pair<Float, Float> {
        // Wait until the pin is laid out: before its first placement it has no bounds to read.
        val node = rule.onNodeWithContentDescription("Car location").fetchSemanticsNode()
        val pin = node.boundsInRoot
        check(pin.width > 0f && pin.height > 0f) { "the pin has not been placed yet: $pin" }
        return (pin.left + pin.right) / 2f / rule.density.density to pin.bottom / rule.density.density
    }

    @Test fun theCarStaysWhereItWasOnScreenWhileTheMapOpens() {
        val origin = Rect(80f, 400f, 680f, 700f)
        rule.mainClock.autoAdvance = false
        setRealMap(origin, CarMapState()) {}
        // Let the map start (it waits a few frames the first time) and the opening get under way.
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        val density = rule.density.density
        val rootOnScreen = rule.onRoot().fetchSemanticsNode().layoutInfo.coordinates.positionOnScreen()
        val wantX = (origin.center.x - rootOnScreen.x) / density
        val wantY = (origin.center.y - rootOnScreen.y) / density
        // The pin is where the pebble's pin was, at every point of the opening: the map just gets bigger around it.
        repeat(6) { step ->
            rule.mainClock.advanceTimeBy(60)
            val (x, y) = pinTip()
            assertEquals("step $step: pin x", wantX, x, 3f)
            assertEquals("step $step: pin y", wantY, y, 3f)
        }
        rule.mainClock.advanceTimeBy(5_000)
        val (x, y) = pinTip()
        assertEquals("open: pin x", wantX, x, 3f)
        assertEquals("open: pin y", wantY, y, 3f)
        // And so the open map is off-centre about it, as the pebble was: the pin is nowhere near the middle of the sheet.
        val box = rule.onNodeWithTag("map").getBoundsInRoot()
        val boxCentreY = (box.top.value + box.bottom.value) / 2f
        assertTrue("open: the map is off-centre about the pin", kotlin.math.abs(boxCentreY - y) > 40f)
    }

    @Test fun closingGlidesThePinToTheMiddleOfTheCollapsedMapInStepWithTheCollapse() {
        val origin = Rect(80f, 400f, 680f, 700f)
        val state = CarMapState()
        var dismissed = 0
        rule.mainClock.autoAdvance = false
        setRealMap(origin, state) { dismissed++ }
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.advanceTimeBy(5_000)
        val density = rule.density.density
        val rootOnScreen = rule.onRoot().fetchSemanticsNode().layoutInfo.coordinates.positionOnScreen()
        val wantX = (origin.center.x - rootOnScreen.x) / density
        val wantY = (origin.center.y - rootOnScreen.y) / density

        // Pan the open map: the pin is now away from where the pebble's was.
        state.pan(100f, -150f)
        rule.mainClock.advanceTimeByFrame()
        val (px, py) = pinTip()
        assertEquals("panned: pin x moved with the finger", wantX + 100f / density, px, 3f)
        assertEquals("panned: pin y moved with the finger", wantY - 150f / density, py, 3f)

        // Close it (a tap above the sheet) and watch the pin come in as the map collapses.
        rule.onRoot().performTouchInput { click(Offset(width / 2f, 5f)) }
        var last = Float.MAX_VALUE
        repeat(14) { step ->
            rule.mainClock.advanceTimeBy(40)
            val (x, y) = pinTip()
            val away = kotlin.math.hypot(x - wantX, y - wantY)
            assertTrue("step $step: the pin only comes closer ($away <= $last)", away <= last + 2f)
            last = away
        }
        rule.mainClock.advanceTimeBy(5_000)
        val (x, y) = pinTip()
        assertEquals("closed: pin x at the middle of the collapsed map", wantX, x, 3f)
        assertEquals("closed: pin y at the middle of the collapsed map", wantY, y, 3f)
        assertEquals("dismissed once", 1, dismissed)
    }
}
