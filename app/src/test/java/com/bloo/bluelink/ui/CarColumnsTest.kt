package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** How many cars share a window: one on a phone, more as the window widens, up to four on a desktop-sized one. */
class CarColumnsTest {
    @Test fun aPhoneShowsOneCar() {
        assertEquals(1, carColumnsFor(360f))
        assertEquals(1, carColumnsFor(599f))
    }

    @Test fun aTabletShowsTwoThenThree() {
        assertEquals(2, carColumnsFor(600f))
        assertEquals(2, carColumnsFor(899f))
        assertEquals(3, carColumnsFor(900f))
        assertEquals(3, carColumnsFor(1499f))
    }

    @Test fun aDesktopWindowShowsFour() {
        assertEquals(4, carColumnsFor(1500f))
        assertEquals(4, carColumnsFor(2560f))
    }
}
