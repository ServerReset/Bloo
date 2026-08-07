package com.bloo.uicommon

/**
 * App-wide spring animation constants shared between phone and watch.
 *
 * [SoftDamping] is the standard damping ratio for most transitions — slightly
 * overdamped (0.82) so motions feel settled without oscillation. The framework's
 * [androidx.compose.animation.core.Spring.DampingRatioMediumBouncy] (0.5) is
 * used where a bouncier feel is intentional (e.g. morph-button on the watch).
 */
const val SoftDamping = 0.82f

/** Damping ratio for expressive "press punch" motions (button squeeze, PIN key) —
 *  a lively bounce. Same as Spring.DampingRatioMediumBouncy, named for intent. */
const val ExpressiveDamping = 0.5f

/** Damping ratio for gentle crossfades of colour/opacity — no visible overshoot. */
const val GentleDamping = 0.9f

/**
 * The morph button's two corner states, as a percentage of the shorter side:
 * a true pill at rest, a rounded rectangle while active or pressed.
 *
 * Here rather than in either surface because this pair IS the shape half of the
 * morph vocabulary, and phone and watch must agree on it for the two to read as
 * the same control. They were hard-coded identically in both MorphButtons, which
 * is fine until someone tunes one of them.
 *
 * Note this shares only the two corner numbers. The spring that animates between
 * them differs by surface on purpose — the watch runs a stiffer one and adds a
 * scale punch the phone does not have — so the timing is not shared and should
 * not be "unified" without a device in hand.
 */
const val PillCornerPercent = 50f
const val MorphedCornerPercent = 28f
