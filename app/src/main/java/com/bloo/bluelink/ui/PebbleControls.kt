package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.connectedGroupShape
import com.bloo.bluelink.data.supportsHornLights
import androidx.compose.runtime.derivedStateOf

/** Hero image + gauge (expanded view). */
@Composable
internal fun CriticalContent(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    /** Rides the hero card's own drag-handle slot. In the dual-column view this is the
     *  "swipe the hero card to switch cars" gesture (see [ExpandedCar]'s `onSwipeCar`); on
     *  the phone's single-column view it stays [Modifier] and the whole page swipes instead. */
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    // Narrow, DERIVED reads -- one per thing the hero actually draws.
    //
    // This used to start with `val state = stateSource.value`, which put this composable --
    // and therefore HeroHeader, the most expensive thing in the app -- into the invalidation
    // set of EVERY UiState emission: a status poll for a different car, a device-location
    // tick, a weather refresh, any command anywhere. The slice it then built
    // (`remember(...) { state }`) narrowed what HEROHEADER received, but nothing narrowed
    // what CriticalContent itself subscribed to, so the hero still recomposed each time.
    // A derived state absorbs those emissions and only invalidates its reader when the value
    // it exposes really changes.
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
        expandAction = onCollapse?.let {
            // Icon-only, deliberately: this is the dual-column view, where the header is
            // already narrower (one column) AND carries the same collapse chevron every
            // pebble has. A labeled "Back to all cars" split pill ate over 130dp of that
            // header, which is what pushed the car's own name into "truncating early"
            // territory once the column got narrow. The glyph alone (with its TalkBack
            // label) gives the name its room back; system back and the chevron still cover
            // the discoverability the label used to add.
            PebbleHeaderAction(
                label = "",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to all cars",
                onClick = it,
            )
        },
    )
}


/**
 * The lock/unlock quick control: the morphing [StateControl] with its status on
 * the left, in a pebble-shaped card with no header row and no expand chevron.
 *
 * It CANNOT go through [Pebble]/[PebbleShell] -- everything that shell gives a
 * pebble is a header row (icon + title + chevron/action) plus a body that
 * discloses behind it, and this pebble has neither: its one control is always
 * visible, and its recent-commands history is revealed by pressing the card's own
 * background rather than by a chevron (see `showHistory` below). So it rolls its
 * own Surface -- but every part of the shell's look that ISN'T the header is
 * deliberately mirrored here, value for value, because it sits in the same column
 * as cards that DO come from PebbleShell and any drift reads as a different kind
 * of object: the same [pebbleCardEdge] treatment, the same containerColor and its
 * matching content colour, the same 1dp card shadow, the same corner radii on the
 * same springs, and the same content insets. Anything changed in PebbleShell's own
 * card (not its header) wants changing here too.
 *
 * It can still be long-pressed and dragged to reorder, like any pebble.
 */
@Composable
internal fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    // Was frostedRim unconditionally -- every other pebble instead gates a
    // bolder dedicated border on the pebbleOutline setting (see Pebble()),
    // frostedRim's alpha being tuned for chrome over a car photo and nearly
    // invisible against a flat pebble background either way. This pebble
    // rolls its own Surface instead of going through Pebble(), so it had been
    // missed -- the setting simply did nothing here.
    val pebbleOutline = LocalAppearance.current.pebbleOutline
    // Recent remote commands for this car, revealed by pressing this pebble's own background.
    // Deliberately undiscoverable-by-chrome: no chevron, no header, no affordance of any kind --
    // the history is a thing you find, not a control the pebble advertises. Kept per-car and NOT
    // rememberSaveable: reopening the app should land on the plain lock control, not on whatever
    // was last revealed.
    var showHistory by remember(v.vin) { mutableStateOf(false) }
    val history = state.remoteActionHistory[v.vin].orEmpty()
    // This pebble's corner follows ONLY its own revealed history, never the surrounding
    // context's force-expand.
    //
    // It used to follow `LocalForceExpanded` too, on the theory that a pinned pebble in the
    // wide layout's HotspotSlot (which force-expands both slots) should share the squarer
    // expanded silhouette of whatever sits beside it. That was reported as the bug it is once
    // the dual-column view actually shipped: this lock pebble has NO body to disclose -- its
    // one control is always visible and its history is a press-to-reveal, not an expand -- so
    // it was drawing the expanded (20dp) corner while genuinely being collapsed, which read as
    // "the locked pebble has expanded corners even though it isn't expanded." The primary
    // hotspot slot still force-expands for every OTHER pebble's sake; this one simply stops
    // listening, so it is a true capsule at rest and only squares off when its history opens.
    //
    // PebbleCornerCollapsed (38dp = ControlHeight/2) stays a plain constant rather than
    // PebbleShell's measured headerRowHeightPx / 2: this pebble's resting content is
    // hard-pinned to ControlHeight below, so half of it is knowable up front and is exactly
    // PebbleCornerCollapsed.
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
            // The same glass fill every PebbleShell card has: this card used to be a solid slab.
            .glassCardFill(shape, MaterialTheme.colorScheme.surfaceVariant),
        shape = shape,
        color = Color.Transparent,
        // contentColorFor(the container), exactly as PebbleShell's own
        // CardDefaults.cardColors(...) resolves it -- so onSurfaceVariant, not the onSurface
        // this used to hardcode. Every muted label in the app is LocalContentColor at
        // MutedContentAlpha, so a card handing its content the WRONG base colour drifts every
        // one of them: "Locked"/"Unlocked" and the horn/lights icons here were rendering off a
        // brighter base than the identically-styled text one pebble below.
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
        // Material's Card carries 1dp of elevation by default (CardTokens.ContainerElevation),
        // which every PebbleShell card therefore gets and a bare Surface does not -- and
        // pebbleCardEdge deliberately draws NO shadow in light mode, so light mode was the one
        // place nothing else was covering for it and this card read visibly flatter than its
        // neighbours. Tonal elevation is left at 0 on purpose: it would be a no-op anyway
        // (Surface only tints when the colour IS colorScheme.surface) and the shadow is the
        // part Card actually contributes here.
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Asymmetric padding to match pebble header alignment: more left, less right.
            // Height stays pinned here (not on the Surface) so the pebble keeps its exact
            // resting silhouette and only grows when the history is actually showing.
            Box(Modifier.fillMaxWidth().height(ControlHeight).padding(start = 12.dp, end = 4.dp)) {
                // PrimaryActions' own default start padding (26.dp) plus this
                // Box's 12.dp put the lock icon noticeably further right than
                // every other pebble's header icon (Charge, Climate, ...), which
                // only ever get Pebble's flat PebbleContentInset row padding. The 4.dp here
                // lines the two icons up: 4 + this Box's own 12 == PebbleContentInset.
                //
                // The end inset is split the same way and lands on 12dp (4 here + 8 below), not
                // the 16dp it used to total: PebbleShell's header row is deliberately asymmetric
                // -- `padding(start = 16.dp, end = 12.dp)` -- so a pebble's trailing control sits
                // 12dp from the card edge. At 16dp this pebble's button group stopped 4dp short
                // of where every other pebble's chevron/action stops, which reads as a narrower
                // card rather than as a different inset.
                PrimaryActions(
                    v, state, vm,
                    contentPadding = PaddingValues(start = PebbleContentInset - 12.dp, end = 8.dp),
                )
            }
            // Gated on the toggle ALONE, not on there being history. RemoteActionsInline draws
            // its own empty state, and a reveal that silently stays shut on a car with no
            // history yet is indistinguishable from the gesture not existing.
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
 * The lock/unlock [StateControl] plus its brand-conditional grouped
 * Flash-lights/Horn-and-lights icon actions -- shared by every place a
 * car's primary quick-action needs to render (the dual-column critical
 * column, [ControlsPebble], and a fill-height glance context), each
 * supplying its own [contentPadding] to line the icon up with that
 * particular container's own inset convention.
 */
@Composable
internal fun PrimaryActions(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    contentPadding: PaddingValues = PaddingValues(start = 26.dp, end = 8.dp),
) {
    val status = state.statusFor(v)
    Column(Modifier.fillMaxWidth().padding(contentPadding)) {
        StateControl(
            name = "",
            isOn = status?.doorLock,
            stateOn = "Locked", stateOff = "Unlocked",
            turnOn = "Lock", turnOff = "Unlock",
            icon = Icons.Filled.Lock, deactivateIcon = Icons.Filled.LockOpen,
            pending = state.isPending(v.vin, "doors"),
            onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
            highlightWhenOff = true,
            offTextColor = MaterialTheme.colorScheme.error,
            // Kia's US API has no equivalent endpoint (see Vehicle.supportsHornLights),
            // so these only appear for Hyundai/Genesis, matching what those apps show.
            // A connected M3 button group with the Lock/Unlock button (see
            // StateControl/connectedGroupShape) -- icon-only, since a labelled
            // "Lights"/"Horn" pill this size squeezed the weighted name/state
            // column (the "Locked"/"Unlocked" label) down to nothing. contentDescription
            // keeps them labelled for TalkBack even with no visible text.
            groupActions = if (v.supportsHornLights) {
                val hlPending = state.isPending(v.vin, "hornLights")
                listOf(
                    GroupIconAction(Icons.Filled.FlashOn, "Flash lights", !hlPending) { vm.flashLights(v) },
                    GroupIconAction(Icons.Filled.Campaign, "Horn & lights", !hlPending) { vm.hornAndLights(v) },
                )
            } else emptyList(),
        )
    }
}
