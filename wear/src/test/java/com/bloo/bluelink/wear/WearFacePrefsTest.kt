package com.bloo.bluelink.wear

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the face does with whatever is stored: a good choice is kept, anything else falls back to the defaults. */
class WearFacePrefsTest {
    @Test fun `a stored choice is kept`() {
        assertEquals(listOf("refresh", "lock", "none", "horn"), WearFacePrefs.parse("refresh,lock,none,horn"))
    }

    @Test fun `nothing stored gives the defaults`() {
        assertEquals(WearFaceControls.defaults, WearFacePrefs.parse(null))
    }

    @Test fun `the wrong number of slots gives the defaults`() {
        assertEquals(WearFaceControls.defaults, WearFacePrefs.parse("lock,climate"))
    }

    @Test fun `an unknown key gives the defaults rather than a broken quarter`() {
        assertEquals(WearFaceControls.defaults, WearFacePrefs.parse("lock,climate,charge,nonsense"))
    }
}
