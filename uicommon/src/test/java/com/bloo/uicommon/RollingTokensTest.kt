package com.bloo.uicommon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RollingTokenRow's tokenizer: digit runs roll, everything between them
 * crossfades, and the concatenation must render identically to a plain Text
 * (0dp spacing, order preserved). Malformed split = a readout that visually
 * loses characters during the roll.
 */
class RollingTokensTest {

    private fun joined(text: String): String =
        rollingTokens(text).joinToString("") { it.text }

    @Test
    fun `compound readout splits into digits and static runs`() {
        val t = rollingTokens("84% · 120 mi")
        assertEquals(true, t[0].digits)
        assertEquals("84", t[0].text)
        assertEquals("% · ", t[1].text)
        assertEquals("120", t[2].text)
        assertEquals(" mi", t[3].text)
    }

    @Test
    fun `relative-time caption rolls its number`() {
        val t = rollingTokens("12 min ago")
        assertEquals("12", t.first().text)
        assertTrue(t.first().digits)
        assertEquals(" min ago", t[1].text)
    }

    @Test
    fun `digit-free value passes through as one static run`() {
        val t = rollingTokens("Locked")
        assertEquals(1, t.size)
        assertEquals(false, t[0].digits)
    }

    @Test
    fun `concatenation is lossless`() {
        for (s in listOf("84% · 120 mi", "12 min ago", "1,250 mi", "v2441 ready", "27s", "verified", "", "a1b2c3")) {
            assertEquals(s, joined(s))
        }
    }
}
