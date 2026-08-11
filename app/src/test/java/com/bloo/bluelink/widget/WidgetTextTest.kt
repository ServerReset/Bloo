package com.bloo.bluelink.widget

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Direct tests of the text-fitting chain now that [wordWrap], [wouldOverflow]
 * and [shrunkToFit] moved out of CarWidget.kt into WidgetText.kt: as private
 * members of a GlanceAppWidget subclass they were only reachable indirectly,
 * through a composable's rendered output, so nothing exercised their actual
 * string logic in isolation until this file could exist.
 */
class WidgetTextTest {

    private val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)

    @Test
    fun `wordWrap never drops or reorders a word`() {
        val text = "Locked Charging Climate on"
        val lines = wordWrap(text, style, 40.dp)
        assertEquals(text.split(" "), lines.joinToString(" ").split(" "))
    }

    @Test
    fun `wordWrap returns the whole string on one line when it already fits`() {
        val text = "Locked"
        val lines = wordWrap(text, style, 400.dp)
        assertEquals(listOf(text), lines)
    }

    @Test
    fun `wordWrap of blank text returns no lines, not one empty line`() {
        assertEquals(emptyList(), wordWrap("", style, 100.dp))
        assertEquals(emptyList(), wordWrap("   ", style, 100.dp))
    }

    @Test
    fun `a single long word too wide for any line still becomes exactly one line`() {
        // No spaces to break on: wordWrap can't help further, FitLine's own
        // shrink/stack chain is what has to carry it -- but wordWrap itself
        // must not give up and lose the word.
        val word = "Supercalifragilisticexpialidocious"
        val lines = wordWrap(word, style, 20.dp)
        assertEquals(listOf(word), lines)
    }

    @Test
    fun `wouldOverflow agrees with Scale overflows for the same inputs`() {
        val text = "Charging"
        assertEquals(
            Scale.overflows(text.length, styleSp(style), isBold(style), 200.dp),
            wouldOverflow(text, style, 200.dp),
        )
    }

    @Test
    fun `isBold reflects only the Bold font weight`() {
        assertTrue(isBold(style.copy(fontWeight = FontWeight.Bold)))
        assertFalse(isBold(style.copy(fontWeight = FontWeight.Normal)))
        assertFalse(isBold(style.copy(fontWeight = FontWeight.Medium)))
    }

    @Test
    fun `styleSp falls back to 12sp when no font size is set`() {
        assertEquals(12f, styleSp(style.copy(fontSize = null)))
        assertEquals(14f, styleSp(style))
    }

    @Test
    fun `shrunkToFit never returns a style that still overflows`() {
        val text = "Battery percentage"
        val shrunk = shrunkToFit(text, style, 30.dp)
        if (shrunk != null) {
            assertFalse(wouldOverflow(text, shrunk, 30.dp), "shrunkToFit returned a style that still overflows")
        }
        // Either it fit (non-null, no overflow) or it genuinely can't (null) --
        // there is no third outcome, which this test exists to pin.
    }

    @Test
    fun `shrunkToFit returns the original style unchanged when nothing needs to shrink`() {
        // Not just an equal copy -- the same instance, so Glance can skip
        // recomposing on it (see WidgetText.kt's own doc comment on this).
        val text = "OK"
        val result = shrunkToFit(text, style, 400.dp)
        assertTrue(result === style)
    }

    @Test
    fun `shrunkToFit with no font size on the style returns null`() {
        assertEquals(null, shrunkToFit("Locked", style.copy(fontSize = null), 30.dp))
    }
}
