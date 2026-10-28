package com.bloo.uicommon

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable

/**
 * App-wide spring animation constants used by the phone UI. [SoftDamping] is the standard damping
 * ratio for most transitions - slightly overdamped (0.82) so motions feel settled without
 * oscillation.
 */
const val SoftDamping = 0.82f

/**
 * The one bounce in the app: the physics the search bar uses when it rises or moves. Soft enough to stretch a little past its rest and
 * settle slowly, with no sharp snap. Every bouncy motion uses [BounceDamping] with [Spring.StiffnessLow]; the rest use their own
 * non-bouncy springs (drags follow a finger, fades and sizes do not bounce).
 */
const val BounceDamping = 0.6f

/**
 * The stiffness for things that pop up and float: pop-ups, toasts and the badge on a card. Lower than the rest of the bounces (Spring's
 * StiffnessVeryLow), so the rise is gentler and the settle takes long enough to read as a bounce rather than a snap. Same [BounceDamping].
 */
const val FloatingStiffness = 50f

/**
 * The morph button's two corner states, as a percentage of the shorter side: a true pill at rest, a
 * rounded rectangle while active or pressed. Here rather than in the surface because this pair IS
 * the shape half of the morph vocabulary. Note this shares only the two corner numbers.
 */
const val PillCornerPercent = 50f
const val MorphedCornerPercent = 28f

/**
 * The app's bounce as a spring: [BounceDamping] at [stiffness]. For coroutines and top-level values, where no composable can run.
 */
fun <T> bounceSpring(stiffness: Float = Spring.StiffnessLow): SpringSpec<T> = spring(dampingRatio = BounceDamping, stiffness = stiffness)

/** The same bounce, leaning to a plain tween under battery saver (see [lowPowerAwareSpring]). For composables. */
@Composable
fun <T> lowPowerBounce(stiffness: Float = Spring.StiffnessLow) = lowPowerAwareSpring<T>(dampingRatio = BounceDamping, stiffness = stiffness)
