@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.connectedGroupShape
import com.bloo.bluelink.data.supportsHornLights
import kotlinx.coroutines.flow.first
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
    val metric = LocalAppearance.current.unitSystem == "metric"
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
            .pebbleCardEdge(shape, pebbleOutline),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
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
        shadowElevation = 1.dp,
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
 * column, [ControlsPebble], and the cover screen's main tile), each
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

/**
 * A chunky stateful control: shows the current state and a button offering the
 * *opposite* action. The button is always a clearly filled control that morphs
 * from a pill (calm) to a rounded square (highlighted).
 */
@Composable
internal fun StateControl(
    name: String,
    isOn: Boolean?,
    stateOn: String,
    stateOff: String,
    turnOn: String,
    turnOff: String,
    icon: ImageVector,
    deactivateIcon: ImageVector? = null,
    pending: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    enabled: Boolean = true,
    disabledNote: String? = null,
    highlightWhenOff: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    offTextColor: Color? = null,
    groupActions: List<GroupIconAction> = emptyList(),
) {
    // Which state is the "highlighted" (on) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    // The row's own real, measured width -- onSizeChanged, not BoxWithConstraints. Both
    // report the same number, but BoxWithConstraints is a SubcomposeLayout under the hood:
    // its content is composed in a SEPARATE pass, deferred until the constraints are known,
    // which is real, avoidable overhead on something this exact composable is not rare --
    // it draws EVERY car's Lock/Unlock row, so every car page composing during a swipe
    // (beyondViewportPageCount = 1 means up to three at once) paid it. onSizeChanged is a
    // plain draw/layout callback on the SAME Row that was always here, no extra composition
    // pass at all -- the identical trade PebbleHeaderHeight's own rowHeightDp already makes
    // a few lines below in this same file's sibling function.
    //
    // Default a generous 1000.dp, not a small one: this value is read to CAP the button
    // group, so an inaccurate LOW default (before the real width lands, one frame after
    // first composition) would wrongly force it to compact on that first frame. A HIGH
    // default instead means the first frame renders exactly as it always did -- uncapped --
    // and the real cap takes over a frame later, imperceptible and safe in the direction
    // that matters (never a false compact, only a one-frame-late correct one).
    var rowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    // Without this cap, the button group -- a plain (non-weighted) Row child -- is measured
    // against the row's FULL width, because Row measures non-weighted children BEFORE it
    // knows what a weighted sibling (the name/state column, `widthIn(min = 120.dp)` a few
    // lines down) will need. That min is a REQUEST, not a guarantee the Row's own weight
    // arithmetic honours on its own: when the row overall is too narrow for both, Row still
    // hands the column `Constraints(minWidth = share, maxWidth = share)` off its OWN
    // (button-first) division of space, and widthIn(min) cannot raise that ABOVE an
    // already-fixed, SMALLER incoming max -- it coerces down to fit the parent's hard upper
    // bound instead of forcing the parent to overflow, which is the opposite of what a
    // "floor" sounds like it should do. So on a narrow enough row, "Unlocked" truncated to
    // "Unlock…" while the button group beside it kept its own full, unconstrained width --
    // reported from a real screenshot, and the fix genuinely requires the button group's own
    // width to be bounded first, not a bigger number on the column's own floor (already tried
    // once, on the pebble-header version of this exact bug, and it only bought a bit more
    // room rather than fixing the actual priority).
    val groupMaxWidth = (rowWidthDp - 120.dp - 12.dp).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Fill the button's height so the status reads as one tall control.
        // Standard spacing between label and buttons, matching other pebble controls.
        Column(Modifier.fillMaxHeight().widthIn(min = 120.dp), verticalArrangement = Arrangement.Center) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColorTarget = when {
                !enabled -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
            }
            val stateColor by androidx.compose.animation.animateColorAsState(
                stateColorTarget,
                animationSpec = tween(250),
                label = "stateColor",
            )
            when {
                // With no title, the lock state is the headline — icon AND word, side by side.
                name.isBlank() -> {
                    val stateIcon = when (isOn) {
                        true -> icon
                        false -> Icons.Filled.LockOpen
                        else -> icon
                    }
                    if (pending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(Modifier.size(22.dp))
                            Text(
                                "Sending…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = Pair(stateIcon, stateText),
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 1.1f, animationSpec = tween(150)))
                            },
                            // Default is TopStart: "Locked"/"Unlocked" render at
                            // slightly different intrinsic heights, so without
                            // this the old and new icon+label rows didn't align
                            // to the same vertical center during the crossfade,
                            // reading as the whole control nudging on toggle.
                            contentAlignment = Alignment.CenterStart,
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // null, not `label`: the Text right after already
                                // carries the same words, so a non-null
                                // description here was a redundant swipe stop
                                // ("Locked" from the icon, then "Locked" again
                                // from the text) -- purely decorative now that
                                // the label is announced once.
                                Icon(ic, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    // If this column ever gets squeezed tight
                                    // again (groupActions content changes,
                                    // narrower screens), ellipsize instead of
                                    // wrapping mid-word ("Locke"/"d" on two
                                    // lines) -- a clipped label at least still
                                    // reads as one intact word.
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                else -> AnimatedContent(
                    targetState = stateText,
                    transitionSpec = { expandContentTransform() },
                    label = "stateTextAnim",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        // 12dp spacing between text and buttons to match the previous spacedBy arrangement
        Spacer(Modifier.width(12.dp))
        val haptics = LocalHaptics.current
        // Any extra icon actions (horn/lights) plus the lock/unlock button
        // form one Material 3 "connected" button group -- a single Row (one
        // child of the outer Row, so its own 2dp spacing isn't also getting
        // the outer Row's 12dp spacedBy piled on top) instead of a separate
        // icon cluster sitting next to an unrelated pill.
        val segmentCount = groupActions.size + 1
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a
        // thumb on a ~1-inch square) than on the phone (mouse-precise finger taps in
        // a full pebble).
        //
        // Gated on LocalPebbleFillHeight, NOT LocalForceExpanded: the comment here used to
        // claim LocalForceExpanded is "true only on the cover" and that was simply wrong --
        // the wide layout's HotspotSlot provides it too, for both of its slots (see
        // HotspotSlot). Only the cover provides LocalPebbleFillHeight (see its doc in
        // Widgets.kt), so that is the one that actually means "cover".
        //
        // This was the reported "the controls pebble looks different and worse" in the
        // multi-column layout, and it was visible ONLY there: ControlsPebble is this
        // composable's only caller, and "controls" has no cover tile at all (see
        // CompactCar's `tiles` mapping), so the cover-sized 58dp/26dp targets could never
        // reach the cover and only ever fired in the hot-spot column -- an over-filled band
        // of chunky buttons in a 76dp-tall card, 8dp taller and with 4dp bigger icons than
        // the identical control renders at in the single-column list, and eating 24dp more
        // of the row's width away from the "Locked"/"Unlocked" label beside it (see
        // groupMaxWidth). The phone sizes are the right ones for a pebble in a column,
        // which is what the hot-spot slot is.
        //
        // Kept as a branch rather than deleted: it is the correct treatment for a genuine
        // cover tile, and the cover already renders pebbles through this same SinglePebble
        // path, so a "controls" tile becoming available there needs no new plumbing.
        val coverTargets = LocalPebbleFillHeight.current
        val groupBtnSize = if (coverTargets) 58.dp else 50.dp
        val actionIconSize = if (coverTargets) 26.dp else 22.dp
        // Standard gap between connected button elements (matches SplitExpandButton's
        // own 3dp gap for visual consistency across all grouped controls).
        // ExpressiveButtonRow, not a plain Row. THIS is the app's clearest "several buttons
        // sharing one space" -- a connected group whose silhouette is one pill -- so it is
        // exactly where pressing one segment should widen it and squeeze the others, with the
        // group's own outer width never changing. Every segment was a bare MorphButton with no
        // press wrapper at all, which is why no amount of work on SafeExpansiveButton ever made
        // this cluster move: there was nothing here to animate.
        //
        // Modifier.size on a segment is not in the way: fixed-size constraints are still
        // coerced into the ones the parent hands down, so the width the group assigns wins.
        // wrap = false: this is a CONNECTED group -- one silhouette with seams in it -- so a
        // seam appearing on the next line down would not be a control at all. Too narrow, and
        // the segments compact instead.
        val mainSource = remember { MutableInteractionSource() }
        // Lock/unlock button content: reserves width for both "Lock" and "Unlock" labels
        // so the button doesn't collapse during the optimistic state flip mid-release.
        // See MorphButton call below for why this matters.
        val lockContent: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
                Box(Modifier.alpha(0f)) {
                    MorphButtonLabel(icon, if (isOn == true) turnOn else turnOff, false, iconSize = actionIconSize)
                }
            }
        }
        if (groupActions.isEmpty()) {
            // No ExpressiveButtonRow at all here -- most cars have no horn/lights support
            // (Kia's US API has none, see the doc above), so this is the common case, and it
            // was reported directly as "the unlock button doesn't animate when tapped": a
            // connected group's OWN defining rule is that its outer footprint never changes
            // on press -- growth is redistributed FROM a neighbour, never from thin air (see
            // ExpressiveButtonGroup's/ExpressivePressGrowth's own doc: "a member with none
            // carries nothing to reserve for"). A lone member has no neighbour to take width
            // from, so wrapping it in the group gave it a press fraction that was faithfully
            // computed and just as faithfully multiplied by a reserve of zero -- not a bug in
            // the animation itself, a design that only works with two or more real segments,
            // silently applied to the one-segment case too. SafeExpansiveButton's OTHER path
            // (outside a group) grows the button for real, which is exactly the fallback a
            // solitary button wants and every other lone MorphButton in the app already gets.
            SafeExpansiveButton(
                interactionSource = mainSource,
                enabled = enabled && !pending,
                // Same cap the group version applies via its own Modifier -- see groupMaxWidth's
                // doc above for why this can't be worked out from inside the button itself.
                modifier = Modifier.widthIn(max = groupMaxWidth),
            ) {
                MorphButton(
                    onClick = { if (isOn == true) onDeactivate() else onActivate() },
                    onClickHaptic = { haptics?.heavy() },
                    enabled = enabled && !pending,
                    interactionSource = mainSource,
                    active = highlighted,
                    activeContainerColor = highlightColor,
                    activeContentColor = highlightContentColor,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow),
                    modifier = Modifier.heightIn(min = groupBtnSize),
                ) {
                    lockContent()
                }
            }
        } else {
            ExpressiveButtonRow(
                // Caps this group's own room at groupMaxWidth -- see that val's own doc above
                // for why the group cannot work this out for itself.
                modifier = Modifier.widthIn(max = groupMaxWidth),
                spacing = 3.dp,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
                wrap = false,
            ) {
                groupActions.forEachIndexed { i, action ->
                    val actionSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(interactionSource = actionSource, enabled = action.enabled) {
                        MorphButton(
                            onClick = action.onClick,
                            enabled = action.enabled,
                            interactionSource = actionSource,
                            contentPadding = PaddingValues(0.dp),
                            shapeForCorner = { morph, cp -> connectedGroupShape(i, segmentCount, cp, morph) },
                            modifier = Modifier.size(groupBtnSize),
                        ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(actionIconSize)) }
                    }
                }
                // Pill when off, rounded rectangle + highlight colour when on - same
                // as the climate/charge controls -- except when it's part of a
                // group, where the connected shape takes over (see MorphButton's
                // shape param doc): a connected group's silhouette is static, not
                // something one segment morphs independently of the others.
                SafeExpansiveButton(interactionSource = mainSource, enabled = enabled && !pending) {
                    MorphButton(
                        onClick = { if (isOn == true) onDeactivate() else onActivate() },
                        onClickHaptic = { haptics?.heavy() },
                        enabled = enabled && !pending,
                        interactionSource = mainSource,
                        active = highlighted,
                        activeContainerColor = highlightColor,
                        activeContentColor = highlightContentColor,
                        shapeForCorner = { morph, cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp, morph) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow),
                        modifier = Modifier.heightIn(min = groupBtnSize),
                    ) {
                        lockContent()
                    }
                }
            }
        }
    }
}
