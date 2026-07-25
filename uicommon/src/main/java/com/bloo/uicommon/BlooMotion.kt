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
