package com.bloo.uicommon

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one progress bar: the expressive wavy indicator, the same as every other progress in the app. Indeterminate (running, with no
 * end in sight) when [progress] is null.
 */
@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    if (progress != null) {
        LinearWavyProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth(), trackColor = track)
    } else {
        LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth(), trackColor = track)
    }
}
