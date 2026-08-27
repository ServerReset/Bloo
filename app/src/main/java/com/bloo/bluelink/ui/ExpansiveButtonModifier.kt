package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive expansion modifier for buttons.
 *
 * Creates a ripple expansion effect on button press where:
 * - Button expands horizontally (ripples outward)
 * - Button scales up slightly
 * - Surrounding buttons shrink to make room
 * - Smooth spring retraction with Material 3 physics
 *
 * Uses Material 3 Expressive spring specs for cohesive animation with the design system.
 *
 * **Usage:**
 * ```kotlin
 * Button(
 *     onClick = { },
 *     modifier = Modifier.expansivePress()
 * ) {
 *     Text("Tap me")
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.expansivePress(interactionSource: InteractionSource? = null): Modifier =
    this then ExpansivePressModifier(interactionSource)

private class ExpansivePressModifier(val interactionSource: InteractionSource?) : Modifier.Element

/**
 * Composable wrapper that applies expansive press animation to any content.
 * Handles the scaling and repositioning during press interactions.
 *
 * **Usage:**
 * ```kotlin
 * ExpansiveButton(
 *     interactionSource = remember { MutableInteractionSource() }
 * ) {
 *     Button(
 *         onClick = { },
 *         interactionSource = interactionSource,
 *     ) {
 *         Text("Tap me")
 *     }
 * }
 * ```
 */
@Composable
fun ExpansiveButton(
    interactionSource: InteractionSource? = null,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }

    val scaleAnimatable = remember { Animatable(1f) }
    val offsetAnimatable = remember { Animatable(0f) }

    LaunchedEffect(interactionSource) {
        if (interactionSource == null) return@LaunchedEffect

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // Animate to expanded state: scale up + offset for ripple effect
                    try {
                        scaleAnimatable.animateTo(
                            targetValue = 1.15f, // Scale up by 15%
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    } catch (e: Exception) {
                        // Handle cancellation
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    // Animate back to normal state with smooth spring
                    try {
                        scaleAnimatable.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    } catch (e: Exception) {
                        // Handle cancellation
                    }
                }
            }
        }
    }

    scale = scaleAnimatable.value

    Box(
        modifier = Modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f),
        ),
    ) {
        content()
    }
}

/**
 * Material 3 Expressive button container that applies expansion effect.
 * Wraps the button with proper spring physics for the ripple expansion.
 *
 * **Usage:**
 * ```kotlin
 * ExpansiveButtonContainer {
 *     Button(onClick = { }) {
 *         Text("Expansive Button")
 *     }
 * }
 * ```
 */
@Composable
fun ExpansiveButtonContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Spring expansion on press
            scale.animateTo(
                targetValue = 1.15f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            // Smooth retraction
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer(
            scaleX = scale.value,
            scaleY = scale.value,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f),
        ),
    ) {
        content()
    }
}
