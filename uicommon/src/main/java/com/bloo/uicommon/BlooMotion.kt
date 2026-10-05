package com.bloo.uicommon

/**
 * App-wide spring animation constants used by the phone UI. [SoftDamping] is the standard damping
 * ratio for most transitions — slightly overdamped (0.82) so motions feel settled without
 * oscillation.
 */
const val SoftDamping = 0.82f

/**
 * The morph button's two corner states, as a percentage of the shorter side: a true pill at rest, a
 * rounded rectangle while active or pressed. Here rather than in the surface because this pair IS
 * the shape half of the morph vocabulary. Note this shares only the two corner numbers.
 */
const val PillCornerPercent = 50f
const val MorphedCornerPercent = 28f
