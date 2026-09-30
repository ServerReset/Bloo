@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.flow.first

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
 * The Screen.Loading bootstrapping placeholder -- see that state's own doc
 * (AppViewModel.kt) for why it exists. Same AuroraBackground + "Bloo"
 * wordmark [LoginScreen] opens with, so if this resolves to Login next
 * there's nothing to visually reconcile: same backdrop, same brand mark,
 * already faded in. No form, no fields, nothing interactive -- this is a
 * "still deciding" placeholder, shown for however long the cold-start
 * auto-login coroutine takes to resolve, not a real destination on its own.
 *
 * The wordmark fades in on its own (not present from frame one) rather than
 * being static: a car-status app booting into a full-strength logo the
 * INSTANT the process starts reads as an abrupt, slightly jarring "already
 * finished loading" claim before anything has actually happened yet; easing
 * it in over a beat reads as the app settling into itself instead.
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
        // Gated on the user's own setting, like the garage already gates it. This is the FIRST
        // screen of every cold start, and it was painting a full-screen 44dp blur -- an offscreen
        // buffer for the whole window, a blur shader compiled on first use, and a 12.5fps drift
        // loop invalidating it -- unconditionally, including for people who had turned the aurora
        // background off. The most expensive frames in the app were the ones before it had drawn
        // anything, doing work that was switched off.
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

// A synced device not seen this long is flagged as possibly on a different Drive
// file (the two-files trap) in the sync settings. 2 days is well past any normal
// gap for a device in active use, so it doesn't false-alarm on a phone you simply
// didn't open yesterday.
internal const val STALE_DEVICE_MS = 2L * 24 * 60 * 60 * 1000
