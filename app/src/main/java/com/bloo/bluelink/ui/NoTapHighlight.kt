package com.bloo.bluelink.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/** The app's [androidx.compose.foundation.LocalIndication]: nothing at all. */
internal object NoTapHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    // Every instance is the same nothing, so all of them are equal -- this matters because Compose
    // compares indication instances to decide whether to recreate the node.
    override fun equals(other: Any?): Boolean = other is NoTapHighlight
    override fun hashCode(): Int = System.identityHashCode(this)
}
