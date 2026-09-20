package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The contract [stateSlice] relies on to keep the pebble stack from recomposing
 * on every unrelated UiState emission: two slices are equal when the fields the
 * row reads are equal, REGARDLESS of what else changed in the state.
 *
 * If this ever regressed to whole-state equality, the pebbles would silently go
 * back to recomposing on every emission -- a perf regression, not a crash, so
 * nothing else would catch it. If it regressed the other way (unequal keys
 * comparing equal) a pebble would keep drawing stale data. Both directions are
 * pinned here.
 */
class KeyedSliceTest {

    @Test
    fun `equal keys compare equal even when the rest of the state differs`() {
        val a = UiState(refreshing = false, loading = false)
        val b = a.copy(refreshing = true, loading = true, aiBusy = setOf("OTHER"))
        // Keyed on a field both share: the slices must compare equal even though the
        // two states differ in refreshing/loading/aiBusy.
        val key = { s: UiState -> listOf(s.screen) }

        assertEquals(KeyedSlice(a, key(a)), KeyedSlice(b, key(b)))
    }

    @Test
    fun `a changed key compares unequal`() {
        val a = UiState(loading = false)
        val b = a.copy(loading = true)
        val key = { s: UiState -> listOf(s.loading) }

        assertNotEquals(KeyedSlice(a, key(a)), KeyedSlice(b, key(b)))
    }

    @Test
    fun `list keys compare by content, not identity`() {
        val a = UiState(loading = true)
        val b = a.copy(loading = true)
        assertEquals(KeyedSlice(a, listOf(true)), KeyedSlice(b, listOf(true)))
    }

    @Test
    fun `null keys compare equal`() {
        val a = UiState()
        assertEquals(KeyedSlice(a, null), KeyedSlice(a.copy(loading = true), null))
    }
}
