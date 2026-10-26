package com.bloo.bluelink.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The app's [androidx.compose.foundation.LocalIndication]. Android's default press indication is a ripple, a grey fill that
 * washes over whatever was tapped, so a press shows nothing here. A control that holds KEYBOARD focus (Tab, arrow keys, a
 * d-pad) shows a ring in the content colour instead: without one, someone using a keyboard cannot see where they are.
 */
internal object NoTapHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusRingNode(interactionSource)

    // Every instance is the same, so all of them are equal -- this matters because Compose
    // compares indication instances to decide whether to recreate the node.
    override fun equals(other: Any?): Boolean = other is NoTapHighlight
    override fun hashCode(): Int = System.identityHashCode(this)
}

private class FocusRingNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            val held = mutableListOf<FocusInteraction.Focus>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> held.add(interaction)
                    is FocusInteraction.Unfocus -> held.remove(interaction.focus)
                }
                val now = held.isNotEmpty()
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val width = 2.dp.toPx()
        val inset = width / 2f
        drawRoundRect(
            color = currentValueOf(LocalContentColor),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(minOf(size.minDimension / 2f, 16.dp.toPx())),
            style = Stroke(width),
        )
    }
}
