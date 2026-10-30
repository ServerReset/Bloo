package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long an item that has just left the list stays drawn, so its row can shrink and fade before it goes. */
private const val LeaveMs = 500L

/**
 * The items to draw for a list whose [items] may drop some: an item that has just left stays in place for [LeaveMs], so its row can
 * shrink and fade out where it was instead of its gap closing in one frame. [resetKey] (a car's VIN, say) starts the list afresh.
 */
@Composable
fun <T> rememberDisplayedItems(items: List<T>, resetKey: Any): List<T> {
    var displayed by remember(resetKey) { mutableStateOf(items) }
    LaunchedEffect(items) {
        val leaving = displayed.filter { it !in items }
        if (leaving.isEmpty()) {
            displayed = items
        } else {
            // Each leaving item goes back where it was, after the items that are still here.
            val merged = items.toMutableList()
            displayed.forEachIndexed { i, s ->
                if (s !in items) merged.add(displayed.take(i).count { it in items }.coerceAtMost(merged.size), s)
            }
            displayed = merged
            delay(LeaveMs)
            displayed = items
        }
    }
    return displayed
}
