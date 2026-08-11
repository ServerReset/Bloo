package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * Sixth slice out of CarWidget.kt: the action buttons -- which verbs this car
 * actually supports ([resolvedActions]), how a set of them is arranged and
 * whether they can afford labels ([ActionButtons]), and one button's own fill,
 * icon, label and click routing ([ActionButton]).
 *
 * These carry most of the widget's genuinely tricky sizing decisions -- the
 * row-versus-stack choice, the truncate-rather-than-overflow rule, the
 * all-or-nothing label test -- so they are worth reading as their own subject
 * rather than as three functions buried between eighteen tier layouts. The
 * capacity arithmetic they lean on already lives in Scale, where a JVM test
 * sweeps it; this file is the composition that consumes those numbers.
 */

/** The user's configured actions, filtered down to what this car's brand
 *  actually supports and capped to [max] -- the shared resolution behind
 *  both [ActionButtons] and the MICRO tier's single-button controls mode.
 *  Kia's US API (and the Canada backend) has no flash/horn endpoint --
 *  com.bloo.bluelink.data.Brand.fromIndicator(car.brandIndicator) is the
 *  same lookup Vehicle.supportsHornLights uses on the phone. Without this,
 *  a Kia user who'd configured Flash/Horn got a button that silently did
 *  nothing on every tap (WearCommandRunner routes it to KiaRepository's
 *  default no-op flashLights/hornAndLights). */
internal fun resolvedActions(car: VehicleSnapshot, render: Render, max: Int): List<WidgetAction> {
    val hornLightsSupported = com.bloo.bluelink.data.Brand.fromIndicator(car.brandIndicator)
        .let { it != com.bloo.bluelink.data.Brand.KIA && !it.isCanada }
    return render.config.actions.mapNotNull { WidgetAction.fromKey(it) }
        // Hide every charge verb on a car with no chargeable pack, not just the
        // CHARGE toggle -- see WidgetAction.NEEDS_BATTERY.
        .filter { it !in WidgetAction.NEEDS_BATTERY || car.hasBattery }
        .filter { (it != WidgetAction.FLASH && it != WidgetAction.HORN) || hornLightsSupported }
        .take(max)
}

/** The configured action buttons, capped to [max] for the current size.
 *  [vertical] stacks them in a column instead of a row -- used by the
 *  tall/narrow compact tier so a controls-priority widget gets real
 *  finger-sized buttons instead of squeezing several side by side into a
 *  too-narrow strip. */
@Composable
internal fun ActionButtons(
    car: VehicleSnapshot, render: Render, max: Int,
    vertical: Boolean = false, modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    // How much width this row actually has to work with -- defaults to
    // the whole tile, but a caller where ActionButtons is a weighted
    // sibling (sharing a Row with a ring/text column) knows its own
    // slice is narrower than that and should say so.
    // Scale.innerWidth, not the raw tile width. The line below already used the padded
    // Scale.innerHeight, so these two defaults disagreed about what "available" means --
    // every caller that overrode neither fed the row-capacity check a width the row does
    // not have (the root padding, plus the pill corner's extra 4dp per side on the tiers
    // that get one). Scale.Frame exists precisely so this cannot be open-coded.
    availableWidth: Dp = Scale.innerWidth(render.frame(LocalSize.current)),
    // The height this row/column actually has. Needed for the same reason
    // as availableWidth -- see the capacity note below.
    availableHeight: Dp = Scale.innerHeight(render.frame(LocalSize.current)),
) {
    val size = LocalSize.current
    val all = resolvedActions(car, render, max)
    if (all.isEmpty()) return
    // How many buttons ACTUALLY fit each way, rather than only asking
    // whether a row is too tight.
    //
    // The previous rule escalated to a vertical stack whenever a row
    // couldn't give every button a legible width -- which fixed the
    // horizontal squeeze by overflowing vertically instead. On a 300x78dp
    // banner with four actions configured, it stacked them into a ~146dp
    // column inside a 78dp widget: two buttons visible, the rest clipped
    // off the bottom. Reported from a real device.
    //
    // Now both axes get a capacity, and whichever orientation can show
    // the whole set wins; if neither can, the set is TRUNCATED to what
    // fits rather than drawn past the edge. Showing three of four buttons
    // is a real cost, but it's an honest one -- the alternative was
    // drawing four and letting the launcher clip two of them.
    // DENSITY over dropping controls. Both of these scale with the tile
    // rather than being flat, because a small widget's job is to show all
    // its buttons, not a tidy subset: at a flat 40dp minimum a 300x78
    // banner fit three of four actions and silently lost one. A 20dp
    // button on a tile that size is small, but it's a deliberate trade --
    // and still a real target, whereas a missing button can't be pressed.
    val gap = Scale.buttonGap(size)
    // Capacity now comes from Scale, so WidgetScaleTest can sweep the numbers this
    // composable actually draws with. It used to compute them here, where the sweep
    // could not reach them -- which is why the widget's real button geometry was the
    // widest untested surface in the file.
    val rowCapacity = Scale.buttonsAcross(size, availableWidth)
    val colCapacity = Scale.buttonsDown(size, availableHeight)
    val stack = when {
        // An explicit request still has to fit; it just gets first refusal.
        vertical -> true
        rowCapacity >= all.size -> false
        colCapacity >= all.size -> true
        // Neither fits everything: prefer whichever shows more.
        else -> colCapacity > rowCapacity
    }
    val actions = all.take(
        Scale.buttonsForced(if (stack) colCapacity else rowCapacity, all.size),
    )
    // Stretched to fill the WHOLE reserved zone in stack mode, not
    // sized to Scale.buttonHeight and centred inside it -- see the
    // Column branch below for why that room is real, deliberately
    // budgeted space rather than a leftover guess.
    // Both clamped to the budget by Scale now. rowHeight was Scale.buttonHeight(size),
    // which is capped by the whole TILE and knew nothing about a smaller reservation a
    // tier had handed over; stackHeight ended in .coerceAtLeast(16.dp), so a 10dp
    // reservation produced a 16dp button. Either way the excess left the tile, because
    // RemoteViews does not clip an overflowing Column.
    val stackHeight = Scale.stackedButtonHeight(size, availableHeight, actions.size)
    val rowHeight = Scale.rowButtonHeight(size, availableHeight)
    // Labels are all-or-nothing across the row: measured against the
    // LONGEST label present, so the widest one setting cleanly is the
    // condition for any of them appearing.
    val perButton = if (stack) availableWidth
        else ((availableWidth - gap * (actions.size - 1)) / actions.size).coerceAtLeast(0.dp)
    val labelStyle = buttonLabelStyle(render.theme)
    val labelRoom = perButton - (Scale.buttonIcon(size) + 14.dp)
    // AUTO keeps the original all-or-nothing room check: label only when the row
    // (or stack) is tall enough AND every configured button's own longest label
    // actually fits. ALWAYS/OFF are a user override of that judgement -- ALWAYS is
    // still safe to force even on a cramped tile because ActionButton renders the
    // label through FitLine now, not a bare Text, so a forced label SHRINKS to the
    // real per-button room rather than running past the button's edge (RemoteViews
    // doesn't clip). OFF just skips the room math entirely.
    val roomForLabels = (if (stack) stackHeight else rowHeight) >= 36.dp &&
        actions.all { !wouldOverflow(it.label, labelStyle, labelRoom) }
    val showLabels = when (render.config.buttonLabels) {
        WidgetConfig.BUTTON_LABELS_ALWAYS -> true
        WidgetConfig.BUTTON_LABELS_OFF -> false
        else -> roomForLabels
    }
    // Centred both ways. When a layout hands ActionButtons the whole tile
    // -- the controls-priority tiers, where the buttons ARE the widget --
    // the block belongs in the middle of it, not against the top-left.
    if (stack) {
        // stackHeight (computed above) stretches every button to fill
        // the WHOLE reserved zone, not Scale.buttonHeight centred inside
        // it. Every caller that stacks buttons already reserves
        // availableHeight specifically FOR them (RailLayout/
        // CompactTallNarrowLayout/CompactTallLayout and their controls-
        // priority branches all pass it explicitly alongside
        // vertical = true) -- so this is real, deliberately budgeted
        // room, not a leftover guess. Buttons used to sit at a small
        // fixed height with the extra room showing as bare photo above
        // and below the stack even on a tile with a generous button
        // zone. Dividing that same zone evenly instead means two buttons
        // in a tall RAIL fill it edge to edge; six buttons in the same
        // zone still divide it evenly, just thinner each.
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { i, action ->
                if (i > 0) Spacer(GlanceModifier.height(gap))
                // Stacked: each button spans the full slice, so it's the
                // orientation most likely to have room for a label.
                ActionButton(
                    action, car, render,
                    modifier = GlanceModifier.fillMaxWidth(),
                    heightOverride = stackHeight,
                    showLabel = showLabels,
                    labelMaxWidth = labelRoom.coerceAtLeast(0.dp),
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actions.forEachIndexed { i, action ->
                if (i > 0) Spacer(GlanceModifier.width(gap))
                ActionButton(
                    action, car, render,
                    modifier = GlanceModifier.defaultWeight(),
                    showLabel = showLabels,
                    labelMaxWidth = labelRoom.coerceAtLeast(0.dp),
                )
            }
        }
    }
}

@Composable
internal fun ActionButton(
    action: WidgetAction, car: VehicleSnapshot, render: Render, modifier: GlanceModifier,
    // False only for the MICRO tier's single-button controls mode, where
    // the caller's own fillMaxSize() modifier should decide the button's
    // size instead of the usual fixed row/column height.
    fixedHeight: Boolean = true,
    // Overrides Scale.buttonHeight when set -- ActionButtons' own stack
    // branch uses this to stretch every button to fill its whole
    // reserved zone rather than sitting at the flat continuous-scale
    // height with the extra room left as bare photo around the stack.
    heightOverride: Dp? = null,
    iconSize: Dp = Scale.buttonIcon(LocalSize.current),
    // Whether to name the action beside its icon. Decided by the CALLER
    // for the whole row at once, never per button: labels have different
    // lengths, so a per-button test would label "Lock" and "Horn" while
    // leaving "Climate" and "Charge" as bare glyphs in the same row. All
    // or none is the only version that reads as designed.
    showLabel: Boolean = false,
    // The room the label text itself has to work with -- icon size and the spacer
    // already subtracted by the caller (see ActionButtons' labelRoom). Only matters
    // when showLabel is true; the callers that never pass one (the MICRO tier's
    // single-button controls mode, the small controls-priority row) never set
    // showLabel either, so the generous default is inert there.
    labelMaxWidth: Dp = 200.dp,
) {
    val theme = render.theme
    val size = LocalSize.current
    // Every button defaults to the branded accent fill -- the "chunky, colored
    // action button" look is Bloo's own established visual language (phone,
    // watch, and the old widget all share it). It only swaps to a semantic
    // color while that specific state is actually true: red while unlocked
    // (a "you left this open" cue, matching every other unlocked indicator in
    // the app), teal while climate is running, green while charging.
    val bg = when {
        action == WidgetAction.LOCK && car.locked == false -> theme.unlocked
        // Same red cue as LOCK's own swap -- Unlock is the same state,
        // just reached from its own dedicated button instead of Lock's
        // toggle landing on it.
        action == WidgetAction.UNLOCK && car.locked == false -> theme.unlocked
        // CLIMATE_ON/CHARGE_ON swap with their toggles: same live state, reached
        // from a dedicated button, exactly as UNLOCK sits beside LOCK above.
        //
        // CLIMATE_OFF and CHARGE_OFF deliberately do NOT get a swap, even though
        // UNLOCK's precedent is "light up when the state you'd produce is already
        // true". The swap exists to flag a NOTABLE live state -- left unlocked,
        // climate burning power, charging in progress. "Climate off" and "not
        // charging" are the resting states; there is no theme colour for them
        // because none is wanted, and inventing one would make a parked, idle car
        // look like it needed attention.
        (action == WidgetAction.CLIMATE || action == WidgetAction.CLIMATE_ON) &&
            car.climateOn == true -> theme.climate
        (action == WidgetAction.CHARGE || action == WidgetAction.CHARGE_ON) &&
            car.charging == true -> theme.charge
        else -> theme.accentProvider
    }
    val click = when (action.kind) {
        WidgetAction.Kind.NAV -> openAction(LocalContext.current)
        WidgetAction.Kind.REFRESH -> actionRunCallback<WidgetRefreshAction>(
            actionParametersOf(WidgetKeys.VIN to car.vin),
        )
        else -> actionRunCallback<WidgetCommandAction>(
            actionParametersOf(WidgetKeys.VIN to car.vin, WidgetKeys.ACTION to action.key),
        )
    }
    Box(
        modifier = (if (fixedHeight) modifier.height(heightOverride ?: Scale.buttonHeight(size)) else modifier)
            .background(bg)
            .cornerRadius(innerCorner(render.config))
            .clickable(click),
        contentAlignment = Alignment.Center,
    ) {
        // An icon alone is a guess -- a snowflake could be climate,
        // defrost, or "cool the battery". Where there's room, the button
        // says which.
        if (showLabel) {
            val labelStyle = buttonLabelStyle(theme)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(iconFor(action)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(theme.onAccent),
                    modifier = GlanceModifier.size(iconSize),
                )
                Spacer(GlanceModifier.width(6.dp))
                // FitLine, not a bare Text: BUTTON_LABELS_ALWAYS forces showLabel
                // true regardless of ActionButtons' own room check, so this has to
                // be the one thing in the file that CAN'T overflow -- it shrinks
                // the label to labelMaxWidth instead, the same fallback chain every
                // other label in the widget already trusts (RemoteViews doesn't
                // clip, so "forced on" and "silently unreadable" are the same bug
                // if this were still a plain Text).
                FitLine(action.label, labelStyle, labelMaxWidth, GlanceModifier, Alignment.Start)
            }
        } else {
            Image(
                provider = ImageProvider(iconFor(action)),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(theme.onAccent),
                modifier = GlanceModifier.size(iconSize),
            )
        }
    }
}
