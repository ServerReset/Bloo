package com.bloo.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [WearPebbles], which decides what order the watch shows its tiles
 * in and what the reorder screen offers.
 *
 * The first tests in the wear module. Everything watch-only was previously verified
 * only by compiling, which is not the same as verifying it behaves -- and this is
 * ordering logic driven by a value SYNCED FROM THE PHONE, so it has to survive input
 * this device never produced: an order written by an older build that predates a
 * pebble, one carrying a key this version has never heard of, and one with duplicates.
 * Getting that wrong means a tile silently vanishing from someone's watch.
 *
 * [WearPebbles] is a plain object over lists and maps, so these need no Robolectric
 * despite living in a file that also declares DataStore delegates -- those compile to a
 * separate class that loading this object never touches.
 */
class WearPebblesTest {

    // ---- normalize ----------------------------------------------------------

    @Test
    fun `an empty order becomes the full default order`() {
        // A watch that has never synced must still show every pebble, not nothing.
        assertEquals(WearPebbles.DEFAULT_ORDER, WearPebbles.normalize(emptyList()))
    }

    @Test
    fun `summary is forced first however it arrives`() {
        assertEquals("summary", WearPebbles.normalize(listOf("weather", "summary")).first())
        assertEquals("summary", WearPebbles.normalize(listOf("charge")).first())
        // Present but last, and still only once.
        val out = WearPebbles.normalize(listOf("weather", "charge", "summary"))
        assertEquals("summary", out.first())
        assertEquals(1, out.count { it == "summary" })
    }

    @Test
    fun `a stored order missing newer pebbles gets them appended, never dropped`() {
        // The migration case: a device that stored its order before a pebble existed.
        // Whatever it does know keeps its relative order; the rest arrive behind it.
        val stored = listOf("summary", "weather", "charge")
        val out = WearPebbles.normalize(stored)
        assertEquals(listOf("summary", "weather", "charge"), out.take(3))
        assertEquals(WearPebbles.DEFAULT_ORDER.toSet(), out.toSet())
        assertEquals(WearPebbles.DEFAULT_ORDER.size, out.size)
    }

    @Test
    fun `unknown keys are dropped rather than rendered`() {
        // A key from a NEWER phone build the watch has no tile for. Dropping it is the
        // whole reason normalize exists; passing it through would reach TO_TILES as a
        // miss and silently contribute nothing anyway.
        val out = WearPebbles.normalize(listOf("summary", "not_a_pebble", "charge"))
        assertFalse(out.contains("not_a_pebble"))
        assertTrue(out.contains("charge"))
        assertEquals(WearPebbles.DEFAULT_ORDER.size, out.size)
    }

    @Test
    fun `duplicates collapse to one entry each`() {
        val out = WearPebbles.normalize(listOf("summary", "charge", "charge", "weather", "charge"))
        assertEquals(1, out.count { it == "charge" })
        assertEquals(out.size, out.distinct().size)
    }

    @Test
    fun `normalize is idempotent`() {
        // It runs on every read AND feeds tilesFor, so a second pass must not reorder
        // or re-append anything.
        val once = WearPebbles.normalize(listOf("weather", "summary", "charge", "junk"))
        assertEquals(once, WearPebbles.normalize(once))
    }

    // ---- reorderable --------------------------------------------------------

    @Test
    fun `reorderable is the normalized order minus the pinned summary`() {
        val out = WearPebbles.reorderable(listOf("weather", "charge"))
        assertFalse(out.contains("summary"), "summary is pinned first and not draggable")
        assertEquals(WearPebbles.normalize(listOf("weather", "charge")).drop(1), out)
    }

    // ---- tilesFor -----------------------------------------------------------

    @Test
    fun `tilesFor expands pebbles into their tiles and always appends the watch-only tail`() {
        val tiles = WearPebbles.tilesFor(WearPebbles.DEFAULT_ORDER)
        assertEquals(WearTiles.SUMMARY, tiles.first())
        // charge owns two tiles, and they stay adjacent and in order.
        val charge = tiles.indexOf(WearTiles.CHARGE)
        assertEquals(WearTiles.LIMITS, tiles[charge + 1])
        // The tail has no pebble of its own, so it can only come from TAIL.
        assertTrue(tiles.containsAll(listOf(WearTiles.ASSIST, WearTiles.MORE)))
        assertEquals(listOf(WearTiles.ASSIST, WearTiles.MORE), tiles.takeLast(2))
    }

    @Test
    fun `a hidden pebble drops its tiles but never the tail`() {
        val tiles = WearPebbles.tilesFor(WearPebbles.DEFAULT_ORDER, hiddenPebbles = setOf("charge"))
        assertFalse(tiles.contains(WearTiles.CHARGE))
        assertFalse(tiles.contains(WearTiles.LIMITS), "charge owns Limits too, so hiding it hides both")
        assertTrue(tiles.contains(WearTiles.SUMMARY))
        assertEquals(listOf(WearTiles.ASSIST, WearTiles.MORE), tiles.takeLast(2))
    }

    @Test
    fun `hiding every pebble still leaves the watch-only tiles`() {
        // Otherwise the user could hide their way into a blank carousel with no route
        // back to Settings, which lives in the More tile.
        val tiles = WearPebbles.tilesFor(WearPebbles.DEFAULT_ORDER, hiddenPebbles = WearPebbles.DEFAULT_ORDER.toSet())
        assertEquals(listOf(WearTiles.ASSIST, WearTiles.MORE), tiles)
    }

    @Test
    fun `pebbles that own no tile contribute nothing`() {
        // "controls" and "trips" are deliberately mapped to empty lists -- their content
        // lives elsewhere on the watch. They must not leave a gap or a phantom tile.
        val withThem = WearPebbles.tilesFor(listOf("summary", "controls", "trips", "charge"))
        val without = WearPebbles.tilesFor(listOf("summary", "charge"))
        assertEquals(without, withThem)
    }

    @Test
    fun `tilesFor never returns duplicates`() {
        val tiles = WearPebbles.tilesFor(listOf("summary", "charge", "charge", "summary"))
        assertEquals(tiles.size, tiles.distinct().size)
    }
}
