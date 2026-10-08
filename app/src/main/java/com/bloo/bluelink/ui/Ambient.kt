package com.bloo.bluelink.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun borderlessFieldColors(): androidx.compose.material3.TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
    )
}

/**
 * The Screen.Loading bootstrapping placeholder -- see that state's own doc (AppViewModel.kt) for
 * why it exists.
 */
@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "loadingWordmarkFade",
    )
    Box(modifier.fillMaxSize()) {
        // Gated on the user's own setting, like the garage already gates it.
        if (LocalAppearance.current.auroraBackground) AuroraBackground(Modifier.matchParentSize())
        Text(
            "Bloo",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { this.alpha = alpha },
        )
    }
}

internal const val STALE_DEVICE_MS = 2L * 24 * 60 * 60 * 1000
