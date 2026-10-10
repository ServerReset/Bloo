package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the off-screen map marker's clamping, so "my location" always lands on the box edge. */
class MapMarkerMathTest {

    @Test
    fun insideTheBoxIsNotOffBox() {
        assertFalse(markerOffBox(10f, 10f, halfW = 100f, halfH = 100f, marginPx = 20f))
    }

    @Test
    fun pastTheInsetEdgeIsOffBox() {
        assertTrue(markerOffBox(90f, 0f, halfW = 100f, halfH = 100f, marginPx = 20f))
        assertTrue(markerOffBox(0f, -90f, halfW = 100f, halfH = 100f, marginPx = 20f))
    }

    @Test
    fun clampsToTheHorizontalEdgeFirst() {
        val (x, y) = clampMarkerToEdge(200f, 40f, halfW = 100f, halfH = 100f, marginPx = 20f)
        assertEquals(80f, x, 0.01f)
        assertEquals(16f, y, 0.01f)
    }

    @Test
    fun clampsToTheVerticalEdgeFirst() {
        val (x, y) = clampMarkerToEdge(30f, -200f, halfW = 100f, halfH = 100f, marginPx = 20f)
        assertEquals(12f, x, 0.01f)
        assertEquals(-80f, y, 0.01f)
    }

    @Test
    fun theCentreStaysPut() {
        val (x, y) = clampMarkerToEdge(0f, 0f, halfW = 100f, halfH = 100f, marginPx = 20f)
        assertEquals(0f, x)
        assertEquals(0f, y)
    }
}
