package com.bloo.uicommon

/**
 * Puts a re-ranked subset back into the full order. Only the items in [shown] move; every other slot in [full] (hidden, pinned, or
 * otherwise left out of the list on screen) keeps its place, so reordering what is visible never drops what is not.
 *
 * [reordered] is the new order of the visible items, and may be a subset of [shown].
 */
fun <T> mergeShownOrder(full: List<T>, shown: Collection<T>, reordered: List<T>): List<T> {
    val shownSet = shown.toSet()
    val queue = ArrayDeque(reordered.filter { it in shownSet })
    return full.map { item ->
        if (item in shownSet && queue.isNotEmpty()) queue.removeFirst() else item
    }
}
