package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.relativeLabel
import kotlinx.coroutines.delay

/**
 * "5 min ago" for [millis], kept current without a clock in the caller. Holds the LABEL in state rather than a clock, which is the
 * whole efficiency of it: under a minute the text really does change every few seconds, so tick at 10s; under an hour it can only
 * change once a minute; past that it cannot change more than every quarter of an hour. Null for no time; blank when none is recorded.
 */
@Composable
fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    var label by remember(millis) { mutableStateOf(relativeLabel(millis)) }
    LaunchedEffect(millis) {
        while (true) {
            val age = System.currentTimeMillis() - millis
            delay(
                when {
                    age < 60_000L -> 10_000L
                    age < 3_600_000L -> 60_000L
                    else -> 900_000L
                },
            )
            label = relativeLabel(millis)
        }
    }
    return label
}
