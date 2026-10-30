package com.bloo.uicommon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The state of one pull-to-refresh gesture, shared by the phone and the watch: how far it has been pulled, where the pull was when the
 * finger lifted, and whether a release has asked for a refresh that the caller's own in-flight flag has not caught up with yet.
 */
open class PullRefreshState {
    /** 0 at rest, 1 at the point a release arms a refresh, past 1 while the pull is resisted. */
    var pull by mutableFloatStateOf(0f)

    /** Where the pull was when the finger lifted, so the indicator carries on from there instead of restarting. */
    var lastPull by mutableFloatStateOf(0f)

    /**
     * Set the instant a release arms a refresh, before the caller's own refreshing flag flips, so the indicator holds its place
     * instead of sliding back up and dropping down again.
     */
    var requested by mutableStateOf(false)
}

/** How much a pull past the arm point keeps travelling, resisted, as a fraction of the disc's travel. */
private const val OverPullTravel = 0.35f

/**
 * Where the disc sits for a pull of [pull]: 1:1 up to the arm point, then a resisted rubber band past it, so the disc can be pulled below
 * its rest and springs back onto it on release. Shared by the phone's indicator and the watch's disc.
 */
fun pullFingerPosition(pull: Float): Float {
    val raw = pull.coerceIn(0f, 1f)
    val over = (pull - 1f).coerceAtLeast(0f)
    return raw + over / (1f + over) * OverPullTravel
}

