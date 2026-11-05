package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaturalIntentTest {
    @Test fun readsPlainJson() {
        val r = parseNaturalIntent("""{"intent":"climate_off","time":"21:00","car":null}""")!!
        assertEquals("climate_off", r.intent)
        assertEquals("21:00", r.time)
        assertNull(r.car)
    }

    @Test fun ignoresWordsAroundTheJson() {
        assertEquals("lock", parseNaturalIntent("Sure! {\"intent\": \"lock\"} Hope that helps.")!!.intent)
    }

    @Test fun unknownIntentBecomesNone() {
        assertEquals("none", parseNaturalIntent("""{"intent":"make_coffee"}""")!!.intent)
    }

    @Test fun badTimeIsDropped() {
        assertNull(parseNaturalIntent("""{"intent":"lock","time":"soon"}""")!!.time)
    }

    @Test fun noJsonIsNull() {
        assertNull(parseNaturalIntent("I cannot help with that."))
    }

    @Test fun promptNamesTheIntentsAndTheTime() {
        val p = naturalIntentPrompt("climate off at 9 pm", "17:30", listOf("Daisy"))
        assertEquals(true, p.contains("climate_off"))
        assertEquals(true, p.contains("Time now: 17:30"))
        assertEquals(true, p.contains("Daisy"))
    }
}
