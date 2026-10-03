package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The app's ONE refresh indicator. Every [Refreshable] (each car's page, the garage) reports its
 * pull and its in-flight state here instead of drawing its own, and a single overlay at the app
 * root draws the result, so the indicator belongs to the app rather than to a page: refresh one car,
 * swipe to another, and it is still there, floating above everything, until the work is done.
 */
internal class GlobalRefresh {
    /** How far the user has pulled, 0..1+. Written by whichever page is being pulled. */
    var pull by mutableFloatStateOf(0f)

    private val refreshingKeys = mutableStateListOf<Any>()

    /** True while ANY page has a refresh in flight. */
    val refreshing: Boolean get() = refreshingKeys.isNotEmpty()

    fun setRefreshing(key: Any, on: Boolean) {
        if (on) { if (key !in refreshingKeys) refreshingKeys.add(key) } else refreshingKeys.remove(key)
    }
}

internal val LocalGlobalRefresh = staticCompositionLocalOf<GlobalRefresh?> { null }

/** Draws [GlobalRefresh]: a liquid-glass disc with the loading shape in it, dropping in from the top. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GlobalRefreshOverlay(state: GlobalRefresh) {
    val shown by animateFloatAsState(
        targetValue = if (state.refreshing) 1f else state.pull.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "globalRefreshShown",
    )
    if (shown <= 0.01f) return
    val density = LocalDensity.current
    val settlePx = WindowInsets.statusBars.getTop(density) + with(density) { 12.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        GlassSurface(
            shape = CircleShape,
            hazeState = LocalBackdropHaze.current,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(52.dp)
                .graphicsLayer {
                    translationY = (shown - 1f) * 56.dp.toPx() + shown * settlePx
                    alpha = shown.coerceIn(0f, 1f)
                    val k = 0.6f + 0.4f * shown.coerceIn(0f, 1f)
                    scaleX = k
                    scaleY = k
                },
        ) {
            if (state.refreshing) {
                LoadingIndicator(Modifier.padding(6.dp))
            } else {
                LoadingIndicator(progress = { state.pull.coerceIn(0f, 1f) }, modifier = Modifier.padding(6.dp))
            }
        }
    }
}
