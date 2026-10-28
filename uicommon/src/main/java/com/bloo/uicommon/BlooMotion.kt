package com.bloo.uicommon

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
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
 * The stiffness for things that pop up and float: pop-ups, toasts and the badge on a card. Fast enough to rise quickly, and with
 * [FloatingDamping] it carries a couple of small swings before it settles, roughly a second in all.
 */
const val FloatingStiffness = 200f

/**
 * The damping for pop-ups, toasts and badges. Low enough to swing past the rest point and come back under it once more, which is the small
 * bounce off the top and the bottom before it settles in the middle. Keep it low: higher values lose the second swing and read as a snap.
 */
const val FloatingDamping = 0.6f

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

/**
 * A spring that settles without overshoot: the fades, sizes and drags that must not bounce. [stiffness] is one of Spring's own constants.
 * For coroutines and top-level values.
 */
fun <T> calmSpring(stiffness: Float): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

/** The same calm spring, leaning to a plain tween under battery saver (see [lowPowerAwareSpring]). For composables. */
@Composable
fun <T> lowPowerCalm(stiffness: Float) = lowPowerAwareSpring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

/**
 * A soft spring: [SoftDamping], slightly overdamped, so a value settles without ringing. For coroutines and top-level values.
 */
fun <T> softSpring(stiffness: Float): SpringSpec<T> = spring(dampingRatio = SoftDamping, stiffness = stiffness)

/** The same soft spring, leaning to a plain tween under battery saver (see [lowPowerAwareSpring]). For composables. */
@Composable
fun <T> lowPowerSoft(stiffness: Float) = lowPowerAwareSpring<T>(dampingRatio = SoftDamping, stiffness = stiffness)

/** A pop-up's spring: [FloatingDamping] at [FloatingStiffness]. For coroutines and top-level values. */
fun <T> floatingSpring(): SpringSpec<T> = spring(dampingRatio = FloatingDamping, stiffness = FloatingStiffness)

/** A pop-up's spring, leaning to a plain tween under battery saver. For composables. */
@Composable
fun <T> lowPowerFloating() = lowPowerAwareSpring<T>(dampingRatio = FloatingDamping, stiffness = FloatingStiffness)

/**
 * The ease for anything that charges: it starts slowly, ramps up through the middle, and slows into its value. Every charge animation on the
 * phone and the watch uses it, so the fill, its colour, the limit marker and the sheen all move the same way.
 */
val ChargeRampEasing: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** How long a charge fill takes to ramp to its value. */
const val ChargeRampMs = 900

/** The charge ramp as an animation spec, for the fill, its colour and the limit marker. */
fun <T> chargeRamp(durationMs: Int = ChargeRampMs): TweenSpec<T> = tween(durationMillis = durationMs, easing = ChargeRampEasing)
