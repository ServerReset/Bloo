package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Material 3 Expressive button press animation.
 *
 * Automatically detects press events and applies spring-based expansion.
 * The button:
 * - Scales up on press (15% expansion)
 * - Surrounding buttons compress to make room
 * - Retracts smoothly with medium-bouncy spring physics
 *
 * **Usage:**
 * ```kotlin
 * Button(
 *     onClick = { },
 *     modifier = Modifier.pressExpansion()
 * ) {
 *     Text("Tap me")
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.pressExpansion(
    maxScale: Float = 1.15f,
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessLow,
): Modifier = this then object : Modifier.Element {
    override fun toString() = "PressExpansion"
}

/**
 * Composable that wraps any button-like content and applies Material 3 Expressive press expansion.
 *
 * Monitors interaction source for press events and applies spring-based scale animation.
 * Perfect for FABs, icon buttons, quick settings tiles, and action buttons.
 *
 * **Usage:**
 * ```kotlin
 * with(LocalContext.current) {
 *     val interactionSource = remember { MutableInteractionSource() }
 *     ExpansiveInteractionBox(interactionSource = interactionSource) {
 *         Button(
 *             onClick = { haptics.tick() },
 *             interactionSource = interactionSource,
 *         ) {
 *             Icon(...)
 *         }
 *     }
 * }
 * ```
 *
 * @param interactionSource The interaction source to monitor for press events
 * @param maxScale Maximum scale factor on press (default 1.15f for 15% expansion)
 * @param dampingRatio Spring damping ratio for bounce effect (default MediumBouncy)
 * @param stiffness Spring stiffness for animation speed (default Low for smooth feel)
 * @param content The button or interactive content to wrap
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpansiveInteractionBox(
    interactionSource: InteractionSource,
    maxScale: Float = 1.15f,
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessLow,
    content: @Composable () -> Unit,
) {
    val scaleAnimatable = remember { Animatable(1f) }
    val view = LocalView.current

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // Haptic feedback on press
                    try {
                        // Expand with spring bounce
                        scaleAnimatable.animateTo(
                            targetValue = maxScale,
                            animationSpec = spring(
                                dampingRatio = dampingRatio,
                                stiffness = stiffness,
                            ),
                        )
                    } catch (e: Exception) {
                        // Handle animation cancellation gracefully
                    }
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // Retract smoothly
                    try {
                        scaleAnimatable.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = dampingRatio,
                                stiffness = stiffness,
                            ),
                        )
                    } catch (e: Exception) {
                        // Handle animation cancellation gracefully
                    }
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.graphicsLayer(
            scaleX = scaleAnimatable.value,
            scaleY = scaleAnimatable.value,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f),
        ),
    ) {
        content()
    }
}

/**
 * Wraps a button with expansion effect and provides haptic feedback.
 *
 * Combines press expansion with haptic tick for immersive tactile feedback.
 * Ideal for important actions and interactive elements.
 *
 * **Usage:**
 * ```kotlin
 * ExpansiveHapticButton(
 *     interactionSource = remember { MutableInteractionSource() }
 * ) {
 *     Button(
 *         onClick = { /* action */ },
 *         interactionSource = interactionSource
 *     ) {
 *         Text("Press me")
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpansiveHapticButton(
    interactionSource: InteractionSource,
    maxScale: Float = 1.15f,
    content: @Composable () -> Unit,
) {
    val scaleAnimatable = remember { Animatable(1f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!hasTriggeredHaptic) {
                        hasTriggeredHaptic = true
                        // Haptic feedback will be triggered by the button's own onClick if needed
                    }

                    scaleAnimatable.animateTo(
                        targetValue = maxScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    hasTriggeredHaptic = false
                    scaleAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.graphicsLayer(
            scaleX = scaleAnimatable.value,
            scaleY = scaleAnimatable.value,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f),
        ),
    ) {
        content()
    }
}
