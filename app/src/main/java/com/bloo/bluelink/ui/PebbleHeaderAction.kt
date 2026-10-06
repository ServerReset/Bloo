package com.bloo.bluelink.ui

/**
 * The collapsible "pebble" shell family: [Pebble], [PebbleShell], [PebbleHeaderAction] and
 * [SplitExpandButton].
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The pebble header action model and its split-button control. Split out of PebbleShell.kt --
 * Pebble/PebbleShell (staying there) reference PebbleHeaderAction only as a parameter type, needing
 * no import for the same-package reference.
 */
internal class PebbleHeaderAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val pending: Boolean = false,
    val active: Boolean = false,
    val spinning: Boolean = false,
    val bounceIcon: Boolean = false,
    val activeContainer: Color? = null,
    val activeContent: Color? = null,
    val isWarning: Boolean = false,
    /**
     * Explicit TalkBack label for icon-only actions (empty [label]) -- without it, an empty-label
     * button inside a Surface (which doesn't merge descendant semantics) announces only "Button"
     * with no indication of what it does. Only needed when [label] is blank.
     */
    val contentDescription: String? = null,
)

/**
 * The header control of a pebble: the action button plus the chevron that opens the pebble, as one [ButtonCluster].
 * The chevron alone (no [action]) or the action alone (no toggle) is a single fully round button.
 */
@Composable
internal fun SplitExpandButton(
    action: PebbleHeaderAction?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    canToggle: Boolean = true,
    onChevronPressChange: ((Boolean) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val chevron = rememberChevronSpin(expanded, label = "splitChevron")
    // The location button's icon bounces when it is tapped.
    val bounceY = remember { Animatable(0f) }
    val bounceScope = rememberCoroutineScope()
    val bounceUpSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    val bounceDownSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    var bouncing by remember { mutableStateOf(false) }

    val chevronSource = remember { MutableInteractionSource() }
    if (onChevronPressChange != null && canToggle) {
        val pressed by chevronSource.collectIsPressedAsState()
        LaunchedEffect(pressed) { onChevronPressChange(pressed) }
        // A collapse unmounting the chevron mid-press must not leave the pebble squared.
        DisposableEffect(Unit) { onDispose { onChevronPressChange(false) } }
    }

    val actionButton = action?.let { action ->
    val description = action.contentDescription?.takeIf { action.label.isEmpty() }
    ClusterButton(
        onClick = {
            if (action.bounceIcon) bounceScope.launch {
                bouncing = true
                bounceY.animateTo(-9f, bounceUpSpring)
                bounceY.animateTo(0f, bounceDownSpring)
                bouncing = false
            }
            action.onClick()
        },
        enabled = action.enabled && !action.pending,
        active = action.active,
        containerColor = if (action.isWarning) scheme.errorContainer else null,
        contentColor = if (action.isWarning) scheme.onErrorContainer else null,
        activeContainerColor = action.activeContainer,
        activeContentColor = action.activeContent,
        // An icon-only action is a true circle.
        square = action.label.isEmpty(),
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
    ) {
        Box(Modifier.widthIn(max = 132.dp).graphicsLayer { translationY = bounceY.value }) {
            MorphButtonLabel(action.icon, action.label, pending = action.pending && !bouncing, spinning = action.spinning)
        }
    }
    }
    val chevronButton = ClusterButton(
        onClick = onToggle,
        onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
        // Easter egg: hold the chevron to spin it (two eased turns) and buzz. A long press never
        // toggles -- only a tap does.
        onLongClick = { chevron.spin(); haptics?.heavy() },
        active = expanded,
        square = true,
        contentPadding = PaddingValues(start = 13.dp, end = 12.dp),
        interactionSource = chevronSource,
        // The icon's own description is the NEXT action; this announces the CURRENT state on focus.
        modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) { ExpandChevronIcon(expanded, chevron) }

    ButtonCluster(
        listOfNotNull(actionButton, chevronButton.takeIf { canToggle }),
        // The same target height as the legacy lock/unlock/horn/lights group (and every other
        // button), so a pebble's main header action lines up with the quick-action buttons instead
        // of resting a few dp short. The pebble header is already PebbleHeaderHeight (76dp), so
        // this does not inflate it.
        modifier.heightIn(min = ButtonTargetHeight),
    )
}
