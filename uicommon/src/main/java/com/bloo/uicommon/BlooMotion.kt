package com.bloo.uicommon

/**
 * App-wide spring animation constants used by the phone UI.
 *
 * [SoftDamping] is the standard damping ratio for most transitions — slightly
 * overdamped (0.82) so motions feel settled without oscillation. The framework's
 * [androidx.compose.animation.core.Spring.DampingRatioMediumBouncy] (0.5) is
 * used where a bouncier feel is intentional (e.g. the morph button's press punch).
 */
const val SoftDamping = 0.82f

// ExpressiveDamping (= 0.5f) was deleted here: no callers. Every "press punch" spring in the app
// hard-codes Spring.DampingRatioMediumBouncy or a literal 0.5f directly, so this named constant
// only implied a centralisation that did not exist. If bounce damping is ever genuinely unified,
// reintroduce it AND route the call sites through it in the same change.

/**
 * The morph button's two corner states, as a percentage of the shorter side:
 * a true pill at rest, a rounded rectangle while active or pressed.
 *
 * Here rather than in the surface because this pair IS the shape half of the
 * morph vocabulary. They used to be hard-coded identically in the phone's and
 * the watch's MorphButtons, which is fine until someone tunes one of them.
 *
 * Note this shares only the two corner numbers. The spring that animates between
 * them is supplied per caller and was deliberately allowed to differ by surface,
 * so the timing is not shared and should not be "unified" without a device in hand.
 */
const val PillCornerPercent = 50f
const val MorphedCornerPercent = 28f
