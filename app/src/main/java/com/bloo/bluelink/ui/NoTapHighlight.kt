package com.bloo.bluelink.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * The app's [androidx.compose.foundation.LocalIndication]: nothing at all.
 *
 * Android's default press indication is a ripple -- a grey fill that washes over whatever was
 * tapped. This app already answers a press with its own language: buttons morph their shape and
 * spring their width (see ExpressiveButtons.kt), the floating chrome scales, pebbles bounce. The
 * ripple sat on top of all of that as a second, borrowed answer to the same event, and on the
 * translucent glass chrome it read as a grey smear rather than feedback.
 *
 * Provided once at the app root, so it reaches everything that resolves its indication the
 * normal way: every `Modifier.clickable` that does not name its own, and MorphButtonCore -- the
 * component behind essentially every button in this app -- which explicitly takes
 * `indication = LocalIndication.current`.
 *
 * An [IndicationNodeFactory] whose node draws nothing, rather than a null indication: null is
 * not assignable to the CompositionLocal, and a factory that creates an inert node is the
 * cheapest legal way to say "no visual response" (no draw call, no layer, no allocation beyond
 * the node itself).
 */
internal object NoTapHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    // Every instance is the same nothing, so all of them are equal -- this matters because
    // Compose compares indication instances to decide whether to recreate the node.
    override fun equals(other: Any?): Boolean = other is NoTapHighlight
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Kept as a typed alias so call sites read as intent rather than as a type name. */
internal val NoIndication: Indication = NoTapHighlight
