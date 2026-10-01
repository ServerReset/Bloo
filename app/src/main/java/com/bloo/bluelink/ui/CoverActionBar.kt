@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.isPluggedOrCharging

/**
 * The cover screen's bottom control bar: one tap each for the actions that
 * live in the pebble headers on the phone -- lock, climate, charge, horn.
 *
 * Those header actions are the whole point of every pebble; on the cover they
 * were reachable only by swiping to the matching page, and two of them (climate
 * and charge) not at all, because those pages open on a glance hero rather than
 * their header. A shut phone is the surface where "just lock it" matters most,
 * so they get a permanent, full-width, thumb-height row instead -- the
 * [CoverTile] actions band, which every cover page now has.
 *
 * Buttons are sized by weight rather than fixed width, so a car with no
 * horn/lights support or no battery gets three fat buttons rather than four
 * narrow ones with a hole where the fourth was.
 */
@Composable
internal fun CoverActionBar(v: Vehicle, state: State<UiState>, vm: AppViewModel) {
    val status by remember(v.vin) { derivedStateOf { state.value.statusFor(v) } }
    val ev = status?.evStatus
    val locked = status?.doorLock
    val charging = ev?.batteryCharge == true
    val plugged = ev.isPluggedOrCharging
    val climateOn = status?.airCtrlOn == true
    val loading by remember { derivedStateOf { state.value.loading } }
    val enabled = !loading
    val doorsPending by remember(v.vin) { derivedStateOf { state.value.isPending(v.vin, "doors") } }
    val climatePending by remember(v.vin) { derivedStateOf { state.value.isPending(v.vin, "climate") } }
    val chargePending by remember(v.vin) { derivedStateOf { state.value.isPending(v.vin, "charge") } }
    val hornPending by remember(v.vin) { derivedStateOf { state.value.isPending(v.vin, "hornLights") } }
    val hasBattery by remember(v.vin) { derivedStateOf { state.value.hasBattery(v) } }
    // iconOnly on all four: this row sits beside the identity pill (which just lost its
    // own icon for the same reason -- see CoverTile's showIdentityIcon doc), and the
    // labels wrapping it onto a second line on a ~1-inch cover is exactly what was
    // reported directly, with a screenshot -- "remove the button names... so it all
    // fits on one row." label itself stays the button's accessible name regardless
    // (CoverActionButton's own iconOnly doc).
    CoverActionButton(
        icon = if (locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock,
        label = if (locked == true) "Unlock" else "Lock",
        iconOnly = true,
        // Attention, not confirmation: an unlocked car is the state worth
        // colouring, matching StateControl's own highlightWhenOff.
        attention = locked == false,
        pending = doorsPending,
        enabled = enabled,
        onClick = { if (locked == true) vm.unlock(v) else vm.lock(v) },
    )
    CoverActionButton(
        icon = Icons.Filled.Thermostat,
        label = if (climateOn) "Stop" else "Climate",
        iconOnly = true,
        active = climateOn,
        pending = climatePending,
        enabled = enabled,
        onClick = { vm.toggleClimate(v) },
    )
    if (hasBattery) {
        CoverActionButton(
            icon = Icons.Filled.Bolt,
            label = if (charging) "Stop" else "Charge",
            iconOnly = true,
            active = charging,
            pending = chargePending,
            // The car can't start a charge it isn't plugged into, and the
            // Charge pebble's own header button is gated the same way.
            enabled = enabled && plugged,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
        )
    }
    if (v.supportsHornLights) {
        // One button doing double duty rather than a fifth icon squeezed into an
        // already-tight row on a ~1-inch cover: tap for the combined "Horn &
        // lights" the main phone UI leads with, long-press for lights-only --
        // silent, useful for finding a car in a dark lot without honking. The
        // main phone screen offers both as separate buttons in a group
        // (PrimaryActions); flashLights had no cover-screen path at all before
        // this, reported as a real feature gap. Long-press is already an
        // established cover gesture (the tile-scrubber rail, the edge-trace
        // refresh), so this isn't a new interaction language for the surface.
        CoverActionButton(
            icon = Icons.Filled.Campaign,
            label = "Horn",
            iconOnly = true,
            // Both flashLights and hornAndLights run under the same "hornLights"
            // pending key (AppViewModel), so one check covers either.
            pending = hornPending,
            enabled = enabled,
            onClick = { vm.hornAndLights(v) },
            onLongClick = { vm.flashLights(v) },
        )
    }
}


/** One button in [CoverActionBar]: icon over a short label, filling its share
 *  of the row. Colour carries state -- [active] for a running command's target
 *  state, [attention] for a state the user probably wants to change. */
@Composable
internal fun CoverActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    /**
     * Lay the icon and label side by side in a shorter pill, instead of stacking them.
     *
     * Stacking exists so four buttons can share one row on a one-inch panel -- each column is
     * too narrow for an icon and a word side by side. A tile with a SINGLE action has no such
     * constraint: it has the whole row, so stacking spends 56dp of height to put a word under a
     * glyph that could sit beside it. The compact form is the shape the phone's own header
     * action already uses, so it also reads as the same control in both places.
     */
    compact: Boolean = false,
    /** True hides [label]'s own Text entirely, showing just the glyph -- [label] stays
     *  required regardless, now carrying the button's `contentDescription` instead of its
     *  visible text, so a screen reader still hears what tapping it does. Reported
     *  directly, with a screenshot: the hero tile's own action row (this button's most
     *  common caller) was wrapping onto a second line on a ~1-inch cover once its
     *  identity pill plus four full icon+label buttons ran out of width -- "remove the
     *  button names... so it all fits on one row." */
    iconOnly: Boolean = false,
    active: Boolean = false,
    attention: Boolean = false,
    pending: Boolean = false,
    enabled: Boolean = true,
    // A second action on the same button, reached by holding rather than
    // tapping -- null for every caller but the horn/flash one. Kept optional
    // rather than every button growing a second gesture it has no use for.
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    // Same MorphButton as everywhere: active commands wear the primary highlight, the "worth
    // changing" state wears the error container, and idle is the standard button fill. It used
    // to PIN both corner percents to the same square 16dp value so a cover bar button "never
    // morphs" -- reported directly as wanting the cover buttons to use the standard framework
    // and change shape on press like every other group button, so that override is gone and it
    // now morphs (pill at rest -> rounded square while pressed) exactly like MorphActionButton.

    // The content tone for every state the core reaches: active->onPrimary,
    // attention->onErrorContainer, else onSurface. Passed as BOTH the idle
    // content and the disabled content (full alpha, so the cover button's own
    // 45% whole-pill fade is the ONLY dim when disabled -- the core's default
    // label-only fade would compound on top of it).
    val contentFor = if (active) scheme.onPrimary
        else if (attention) scheme.onErrorContainer
        else scheme.onSurface
    val coverSource = remember { MutableInteractionSource() }
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.click() },
        onLongClick = onLongClick?.let { fn -> { haptics?.tick(); fn() } },
        enabled = enabled && !pending,
        active = active,
        interactionSource = coverSource,
        containerColor = if (attention) scheme.errorContainer else buttonContainer(),
        contentColor = contentFor,
        disabledContentColor = contentFor,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = GapHairline),
        minHeight = 0.dp,
        // No weight(1f): its parent here is SafeExpansiveButton's own layout, not the row,
        // so it was silently doing nothing. The equal share now comes from the group.
        //
        // `.height(...)`, with NO `.fillMaxHeight()` before it -- an earlier attempt at
        // this fix kept `.fillMaxHeight().heightIn(min = max = X)`, which does NOT work:
        // Modifier chains apply outer-to-inner, so `.fillMaxHeight()` (outer) ALREADY
        // locks the incoming constraint to `minHeight = maxHeight = parent's max` before
        // `.heightIn` (inner) ever runs -- and `.heightIn`'s own max cannot go below an
        // incoming min that's already fixed higher, so the "cap" silently had no effect
        // at all. Confirmed still broken after that first attempt.
        //
        // The real problem `.fillMaxHeight()` was chasing: this button's row sits inside
        // CoverTile's bottom band -- a plain Column, bottom-aligned inside a Box that
        // fills the WHOLE tile. A Box hands every child (including a bottom-aligned one)
        // the SAME max-height constraint it was given itself, so `.fillMaxHeight()` filled
        // all the way up into that full-tile max -- reported from a real screenshot as one
        // lone action button (a Charge tile's single "Stop", with no identity-pill sibling
        // narrow enough to visually mask the effect) ballooning to cover most of the tile,
        // overlapping the readout above it. `.height(X)` measures this button at exactly
        // X regardless of what the parent offers -- no `fillMaxHeight()` in the chain
        // means there's nothing left to override.
        modifier = Modifier
            .height(if (compact) 44.dp else 56.dp)
            .alpha(if (enabled) 1f else 0.45f),
        expressive = true,
    ) {
    val glyph: @Composable () -> Unit = {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            // iconOnly moves `label` here as the contentDescription instead of null --
            // with no visible text anywhere on the button, this glyph is the ONLY node
            // left for a screen reader to describe it by.
            Icon(icon, contentDescription = if (iconOnly) label else null, modifier = Modifier.size(22.dp))
        }
    }
    val text: @Composable () -> Unit = {
        com.bloo.uicommon.FittedText(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = LocalContentColor.current,
            ),
        )
    }
    if (iconOnly) {
        // Genuinely icon-only -- not icon-plus-hidden-label -- same "skip the layout for
        // the half that isn't there" rule every other icon-only path in this app follows
        // (MorphButtonLabel's own, CoverIdentityPill's new one above).
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            glyph()
        }
    } else if (compact) {
        Row(
            // fillMaxHEIGHT, not fillMaxSize. MorphButtonCore sizes itself from its content,
            // and its own doc spells out the consequence of a content child that fills:
            // "the button had no intrinsic size of its own and simply took whatever the
            // incoming constraints allowed". In a full-width action row that is the whole
            // panel -- which is the enormous button, and no amount of guarding equalWidths
            // above could have helped, because the stretch was coming from inside.
            Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph()
            text()
        }
    } else {
        Column(
            // fillMaxHeight for the same reason. The stacked form is only ever used where
            // the group hands out an exact width anyway, so it never depended on filling.
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            glyph()
            Spacer(Modifier.height(3.dp))
            text()
        }
    }
}
}


/**
 * The tile's identity, wearing a button's shape but holding still: glyph, state word and car
 * name in a pill the same height and radius as the actions beside it.
 *
 * It is a group MEMBER, so it takes part in the row's sizing and gives width up when a real
 * button beside it is pressed -- but nothing ever presses it, so it never takes any. That is
 * what makes "info sitting in the action row" work as a layout rather than as a special case:
 * when it and the buttons fit on one line, it sits beside them; when they do not, the group's
 * own wrapping puts it on the line above. Neither outcome is coded for.
 *
 * Not clickable, and marked so: it looks like a button because it shares the row's shape
 * language, not because there is anything to press.
 *
 * Its text tone is fixed to onSurface rather than threaded in from the tile's own titleColor.
 * titleColor is calibrated for text painted directly over the tile's containerColor (or, on the
 * home tile, over a car photo) -- exactly what CoverActionButton's own contentFor doc already
 * flags as the wrong tone for a control that draws its OWN opaque buttonContainer() fill on top
 * of whatever is behind it. This pill is that same case: on the AI tile titleColor resolved to
 * onTertiaryContainer, a tone tuned for a pale lavender card, painted onto a neutral grey chip --
 * legible in the sense the pixels were there, and visibly dimmer than every other tile's identity
 * text. onSurface is what CoverActionButton already settled on for the same neutral fill.
 */
@Composable
fun CoverIdentityPill(
    icon: ImageVector?,
    text: String,
    iconTint: Color,
) {
    val idle = remember { MutableInteractionSource() }
    // enabled = true, NOT false: this is an INFO pill, not a disabled button. Wrapping it in
    // SafeExpansiveButton(enabled = false) faded the whole thing to 50%, which is why the cover's
    // identity read as greyed-out/broken. It still joins the row as a group member (the group
    // flag lives on the wrapper) and never takes any press width, so the buttons beside it keep
    // their own sizing -- it just draws at full strength now.
    SafeExpansiveButton(interactionSource = idle, enabled = true) {
        Box(
            Modifier
                .heightIn(min = ButtonTargetHeight)
                .clip(CircleShape)
                .background(buttonContainer())
                .padding(horizontal = 14.dp)
                .semantics(mergeDescendants = true) { contentDescription = text },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                if (icon != null) {
                    // The glyph keeps its accent -- it is carrying state the words are not (a
                    // charging bolt, a snowflake) -- while the text takes the pill's content tone.
                    MorphButtonLabel(icon, text, pending = false, iconTint = iconTint)
                } else {
                    // No icon, genuinely -- not icon-plus-empty-space -- same "skip the glyph
                    // layout entirely rather than reserve a gap for nothing" rule
                    // MorphButtonLabel's own icon-only path already follows in reverse.
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
