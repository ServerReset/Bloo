package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText

/*
 * The watch's reusable screens. Every scrolling screen is a [WearListScreen] (so the clock, the scroll
 * indicator, the round-display padding and the "no per-card scaling, each element fits on its own" rule are
 * written once), and every "pick one of these" screen is a [WearChoiceScreen].
 */

/**
 * A scrolling screen for the round display: the clock on top, a vertical list whose rows each fit the curve on
 * their own ([fitRound]), padded so the first and last row clear the bezel. [content] adds the rows.
 */
@Composable
fun WearListScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 28.dp),
    /** Wear OS's edge button, the round-screen way to put one primary action at the bottom (null for none). */
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }, edgeButton = edgeButton, modifier = modifier) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // No per-item scaling: each element fits the round display on its own.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/** One option on a [WearChoiceScreen]. */
data class WearChoice<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * A "pick one" screen: a title, the options as rows with the current one lit, and optionally an "Empty" row at
 * the end. A tap picks and the caller decides what happens next. Used by the face's control chooser; anything
 * else that needs a choice on the watch (a unit, a delay) is the same screen.
 */
@Composable
fun <T> WearChoiceScreen(
    title: String,
    choices: List<WearChoice<T>>,
    selected: T,
    onPick: (T) -> Unit,
    /** A last "none of these" row, or null for none. */
    empty: WearChoice<T>? = null,
) {
    WearListScreen {
        item { WearCenteredText(title, style = MaterialTheme.typography.titleSmall) }
        (choices + listOfNotNull(empty)).forEachIndexed { i, choice ->
            item(key = i) {
                WearActionRow(choice.label, choice.icon ?: Icons.Filled.Close.takeIf { choice === empty }, active = choice.value == selected, switch = true) {
                    onPick(choice.value)
                }
            }
        }
    }
}
