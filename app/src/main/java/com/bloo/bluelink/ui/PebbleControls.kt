package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.supportsHornLights
import androidx.compose.runtime.derivedStateOf

/** Hero image + gauge (expanded view). */
@Composable
internal fun CriticalContent(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    /**
     * Rides the hero card's drag-handle slot: in the dual-column view it is the "swipe to switch
     * cars" gesture.
     */
    modifier: Modifier = Modifier,
    /** Fires the hero's one trailing button while collapsed ("go full screen"). */
    onExpand: (() -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    /** True once expanded, so the trailing button becomes "leave full screen". */
    expanded: Boolean = false,
    /** Force the photo open and drop the hero's own chevron (the wide grid always shows the photo). */
    forceHero: Boolean = false,
) {
    // Narrow derived reads, one per thing the hero draws, so unrelated UiState emissions don't
    // recompose it.
    val status by remember(v.vin) { derivedStateOf { stateSource.value.statuses[v.vin] } }
    val imageUrl by remember(v.vin) { derivedStateOf { stateSource.value.imageUrls[v.vin] } }
    val hasBattery by remember(v.vin) { derivedStateOf { stateSource.value.hasBattery(v) } }
    val hasFuel by remember(v.vin) { derivedStateOf { stateSource.value.hasFuel(v) } }
    val photoExpanded by remember(v.vin) {
        derivedStateOf {
            stateSource.value.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION)
        }
    }
    val drivingLabel by remember(v.vin) { derivedStateOf { stateSource.value.drivingLabel(v) } }
    val metric = LocalAppearance.current.metricDistance
    HeroHeader(
        v, status, imageUrl, hasBattery, hasFuel, vm,
        modifier = modifier,
        drivingLabel = drivingLabel, metric = metric, photoExpanded = photoExpanded,
        // The wide view always shows the car photo; the user's own choice is remembered and restored
        // on the phone.
        forcedExpanded = forceHero,
        // ONE trailing button: "go full screen" while collapsed, "leave full screen" once expanded.
        // The hero's own expand/collapse chevron is replaced by it.
        expandAction = (if (expanded) onCollapse else onExpand)?.let { action ->
            PebbleHeaderAction(
                label = "",
                icon = if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (expanded) "Leave full screen" else "Go full screen",
                // While expanded the button reads as "on": the same active morph every toggle uses
                // (round -> squarish) plus the active fill, so its state is obvious at a glance.
                active = expanded,
                onClick = action,
            )
        },
    )
}

/**
 * The lock/unlock quick control: [PrimaryActions] with its status, in a pebble-shaped card with no
 * header or chevron.
 */
@Composable
internal fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    // Honours the pebbleOutline setting like every other pebble, since this one bypasses Pebble().
    val pebbleOutline = LocalAppearance.current.pebbleOutline
    // Recent remote commands, revealed by pressing the card's background. Deliberately has no
    // visible affordance; per-car and not saved, so reopening lands on the plain lock control.
    var showHistory by remember(v.vin) { mutableStateOf(false) }
    val history = state.remoteActionHistory[v.vin].orEmpty()
    // The corner follows only its own revealed history, never LocalForceExpanded: with no body to
    // disclose it would otherwise show expanded corners while collapsed.
    val expanded = showHistory
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = if (expanded) {
            lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            lowPowerAwareSpring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
        },
        label = "controlsPebbleCorner",
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .pointerInput(v.vin) { detectTapGestures { showHistory = !showHistory } }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (showHistory) "Hide recent remote actions" else "Show recent remote actions",
                    ) { showHistory = !showHistory; true },
                )
            }
            .pebbleCardEdge(shape, pebbleOutline)
            // The same glass fill as every PebbleShell card.
            .glassCardFill(shape, MaterialTheme.colorScheme.surfaceVariant),
        shape = shape,
        color = Color.Transparent,
        // contentColorFor the container, as PebbleShell's cardColors does, so muted labels share
        // the same base colour.
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
        // Card's default 1dp elevation, which a bare Surface lacks (nothing else covers it in light
        // mode); tonal elevation stays 0 as it would be a no-op.
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Asymmetric padding matches pebble header alignment. Height lives here, not on the
            // Surface, so the pebble keeps its resting silhouette; heightIn(min) lets it grow with
            // large accessibility fonts.
            Box(Modifier.fillMaxWidth().heightIn(min = ControlHeight).padding(start = GapGroup, end = GapHairline)) {
                // PrimaryActions' own default start padding (26.dp) plus this Box's 12.dp put the
                // lock icon noticeably further right than every other pebble's header icon (Charge,
                // Climate, ...), which only ever get Pebble's flat PebbleContentInset row padding.
                // The 4.dp here lines the two icons up: 4 + this Box's own 12 ==
                // PebbleContentInset.
                PrimaryActions(
                    v, state, vm,
                    contentPadding = PaddingValues(start = PebbleContentInset - 12.dp, end = 8.dp),
                )
            }
            // Gated on the toggle alone: RemoteActionsInline draws its own empty state, and a
            // silent no-op reads as a missing gesture.
            AnimatedVisibility(
                visible = showHistory,
                enter = expandEnterSized(Alignment.Top),
                exit = expandExitSized(Alignment.Top),
            ) {
                RemoteActionsInline(history)
            }
        }
    }
}

/**
 * The lock/unlock control plus brand-conditional Flash-lights/Horn icon actions, shared by
 * every quick-action surface; each passes its own [contentPadding] to match its inset convention.
 *
 * Built directly from the new button family: the control is a three-member [ButtonCluster] exactly
 * like a hero card's header control ([SplitExpandButton]) but with three buttons instead of two,
 * with the lock state as the pebble's own "title" beside it. This replaces the old stateful-control
 * component (the last place still using it), which is now gone.
 */
@Composable
internal fun PrimaryActions(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    contentPadding: PaddingValues = PaddingValues(start = 26.dp, end = 8.dp),
) {
    val status = state.statusFor(v)
    val locked = status?.doorLock
    val pending = state.isPending(v.vin, "doors")
    val hlPending = state.isPending(v.vin, "hornLights")
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    // The lock button highlights while UNLOCKED, so the "Lock" action draws the eye; the state line
    // reads red then too. Locked is the calm default.
    val highlighted = locked == false
    val stateText = when {
        pending -> "Sending…"
        locked == true -> "Locked"
        locked == false -> "Unlocked"
        else -> "Unknown"
    }
    val stateColor = when {
        locked == false -> scheme.error
        else -> mutedContentColor()
    }
    // Caps the cluster's width: the status is weighted, but a long "Unlocked" plus a wide cluster
    // would otherwise squeeze the lock label to a glyph. Reserves room for the status column.
    var rowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    val groupMaxWidth = (rowWidthDp - 120.dp - GapGroup).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            // heightIn(min): the status and button text outgrow ControlHeight at large font sizes.
            .heightIn(min = ControlHeight)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The lock state, as this control's own title -- the same slot a pebble header gives its
        // name. Icon + word, side by side.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            if (pending) {
                LoadingIndicator(Modifier.size(22.dp))
            } else {
                Icon(
                    if (locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedContent(
                targetState = stateText,
                transitionSpec = { expandContentTransform() },
                label = "lockStateText",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = stateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(GapGroup))
        // The control: a three-button connected cluster, the hero header's own silhouette.
        val mainSource = remember { MutableInteractionSource() }
        val lockContent: @Composable RowScope.() -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                val buttonIcon = if (locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock
                MorphButtonLabel(buttonIcon, if (locked == true) "Unlock" else "Lock", pending)
                // Reserves the width of the longer label so the button doesn't jump on the flip.
                Box(Modifier.alpha(0f)) { MorphButtonLabel(Icons.Filled.Lock, "Unlock", false) }
            }
        }
        ButtonCluster(
            buttons = buildList {
                if (v.supportsHornLights) {
                    add(
                        ClusterButton(
                            onClick = { vm.flashLights(v) },
                            enabled = !hlPending,
                            square = true,
                            contentPadding = PaddingValues(0.dp),
                        ) { Icon(Icons.Filled.FlashOn, contentDescription = "Flash lights", modifier = Modifier.size(22.dp)) },
                    )
                    add(
                        ClusterButton(
                            onClick = { vm.hornAndLights(v) },
                            enabled = !hlPending,
                            square = true,
                            contentPadding = PaddingValues(0.dp),
                        ) { Icon(Icons.Filled.Campaign, contentDescription = "Horn & lights", modifier = Modifier.size(22.dp)) },
                    )
                }
                add(
                    ClusterButton(
                        onClick = { haptics?.heavy(); if (locked == true) vm.unlock(v) else vm.lock(v) },
                        onClickHaptic = { haptics?.heavy() },
                        enabled = !pending,
                        active = highlighted,
                        activeContainerColor = scheme.primary,
                        activeContentColor = scheme.onPrimary,
                        interactionSource = mainSource,
                        weight = GroupWeightProportional,
                    ) { lockContent() },
                )
            },
            // The same target height as the hero header control and every other button.
            modifier = Modifier
                .widthIn(max = groupMaxWidth)
                .heightIn(min = ButtonTargetHeight)
                .testTag(PrimaryActionsClusterTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
    }
}

/** The lock control's button cluster, for the instrumented height test. */
internal const val PrimaryActionsClusterTag = "primaryActionsCluster"
