@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.delay
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setDefaultClimatePreset
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setServiceIntervalMiles
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.openDeveloperOptions
import com.bloo.bluelink.data.requestBackgroundUnrestricted

/**
 * Ordered troubleshooting steps covering the two different ways this bar can fail to
 * show correctly: not starting/updating reliably AT ALL (steps 1-2, background
 * execution), and showing but never promoting to a status-bar/lock-screen chip
 * (steps 3-5) -- the second half is a failure mode this app can neither detect nor
 * fix from code past the first two steps, because every cause after that lives
 * outside the documented Android APIs (see [LiveCharge]'s class doc: all nine
 * code-checkable promotion conditions are satisfied unconditionally by
 * [LiveCharge.update]).
 *
 * Step 1 was reported from a real device as "live notifications are not triggering
 * all the time... whenever there is charging happening it should always pull a live
 * notification": the bar is posted/updated by a background WorkManager poll
 * (AlertWorker's 30-minute tick, and the 5-minute chain it kicks off once a car is
 * found charging), and neither one runs at all while the OS considers Bloo
 * battery-restricted -- a car that starts charging while the app hasn't been opened
 * in a while can sit unnoticed well past that 30-minute window, which reads
 * exactly like "not triggering," not like a chip-promotion problem.
 *
 * Step 5 is Samsung-only and was not theoretical: confirmed live on a real Samsung
 * phone running One UI 8.5 (fully patched, well past the general Live Updates
 * rollout) that the chip stayed dark even with every documented condition met AND
 * [LiveCharge.isPromotable] already reporting true, because One UI hides a SECOND
 * gate -- "Live notifications for all apps" -- inside Developer options, off by
 * default, invisible to the standard `canPostPromotedNotifications()` API this app
 * already checks. Flipping it was the fix. Samsung's OWN "put unused apps to sleep"
 * battery feature (step 1's own Samsung note) is a THIRD, separate gate again --
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only covers the standard Android one.
 * [LiveUpdateTroubleshootDialog] can't detect either OEM state itself (no API
 * exists to query them), only point at where to look.
 */
@Composable
internal fun LiveUpdateTroubleshootDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Info,
        title = "Live update not showing?",
        text = {
            Text("A few things to check, in order:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(GapGroup))
            TroubleshootStep(
                1,
                "Not reliable, especially after charging starts? Tap \"Tap to fix\" above to allow background updates." +
                    if (isSamsung) " On Samsung, also allow Bloo under Settings → Battery → Background usage limits." else "",
            )
            TroubleshootStep(2, "Check \"Live charging updates\" is on and the car is charging.")
            TroubleshootStep(3, "Below Android 16 only the shade progress bar shows.")
            TroubleshootStep(4, "On Android 16+, tap \"Tap to fix\" above to allow Live Updates.")
            if (isSamsung) {
                TroubleshootStep(
                    5,
                    "Samsung phones have a SECOND, separate switch this app can't see or set: " +
                        "Settings → Developer options → a \"Live notifications\" toggle " +
                        "(exact wording varies by One UI version). If Developer options aren't " +
                        "enabled yet: Settings → About phone → tap \"Build number\" 7 times.",
                )
            }
        },
        buttons = {
            MorphTextButton(
                "Allow background activity",
                onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isSamsung) {
                SafeMorphTextButton(
                    "Open Developer options",
                    onClick = { LiveCharge.openDeveloperOptions(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SafeMorphTextButton(
                "Close",
                onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun TroubleshootStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(bottom = GapGroup)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One reorderable car entry in Settings; tap to expand its setup + photo. */
@Composable
internal fun CarSettingsCard(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    collapsible: Boolean = true,
) {
    val seats = state.seatConfigs[v.vin] ?: SeatConfig()
    val cardBg by androidx.compose.animation.animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "carCardBg",
    )
    // The exact same collapsible pebble every car's own pebble list on the
    // garage screen uses -- same bounce-open/calm-close springs, same corner
    // morph, same per-row staggered reveal, same "hold the header to drag"
    // idiom (no separate drag-handle icon; PebbleShell never draws one, and
    // this card used to be the only place in Settings that did). It used to
    // be its own bespoke Card + Row + AnimatedVisibility, a lookalike that
    // drifted from every other pebble's motion any time that shared spec
    // changed, which is what "standard" was pointing at.
    //
    // The collapsed header traded the old car-photo thumbnail for the same
    // icon + title + summary shape every other pebble uses -- the photo
    // itself is unchanged and still front-and-centre in the Photo group
    // below once expanded, so nothing about it is actually lost, only where
    // it first appears.
    PebbleShell(
        expanded = expanded,
        onToggle = onToggle,
        icon = Icons.Filled.DirectionsCar,
        title = v.name,
        modifier = modifier,
        summary = "${v.model} · ${state.powertrainLabel(v)}",
        containerColor = cardBg,
        forceExpanded = !collapsible,
    ) {
        val advanced = state.settingsMode == "advanced"

        // Order is the order you would set a car up in: how it looks, what it is, what it can do,
        // what it does by itself, and -- advanced only -- the paperwork.
        SettingsGroup("Photo") {
            val storedImage = state.imageUrls[v.vin]
            val hasPhoto = !storedImage.isNullOrBlank()
            // A live preview, so the effect of a change is visible without leaving Settings.
            if (hasPhoto) {
                AsyncImage(
                    model = rememberPhotoModel(storedImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            // A group, not a plain Row: the buttons share the row's width and press against each other.
            ExpressiveButtonRow(spacing = 8.dp) {
                MorphTextButton(if (hasPhoto) "Change photo" else "Choose photo", onClick = onPickPhoto)
                if (hasPhoto) MorphTextButton("Clear", onClick = { vm.setVehicleImage(v.vin, "") })
            }
        }

        SettingsGroup("Vehicle") {
            LabelText("Powertrain")
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
            // Only Hyundai/Genesis US vehicles have a real head-unit generation to confirm -- see
            // Vehicle.platformOverridable. Everywhere else it resolves the same way regardless, so
            // a picker there would be a control with no effect.
            if (v.platformOverridable) {
                Spacer(Modifier.height(GapRow))
                LabelText("Head unit")
                MutedText("Confirm this car's head unit -- the API can't always tell. Some features only show when supported.")
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }

        SettingsGroup("Climate") {
            MutedText("Which seats your car has, and whether each can heat, cool, or both.")
            SeatPositions.forEach { pos ->
                SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                    { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
            }
            ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
            PopVisible(visible = advanced) {
                Column {
                    SectionDivider(alpha = 0.5f)
                    Spacer(Modifier.height(GapRow))
                    LabelText("Default start")
                    MutedText("What the Start button runs: smart climate, or one of your presets.")
                    val carPresets = state.climatePresets[v.vin].orEmpty()
                    MorphSegmented(
                        options = buildList {
                            add(SegmentOption("smart", "Smart", null))
                            carPresets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                        },
                        selectedKey = state.defaultClimatePresets[v.vin] ?: "smart",
                        onSelect = { key -> vm.setDefaultClimatePreset(v.vin, key.takeIf { it != "smart" }) },
                    )
                }
            }
        }

        AutoLockSettingsGroup(v, vm)

        if (advanced) {
            SettingsGroup("Identity & service") {
                SelectionContainer { StatusRow("VIN", v.vin) }
                BlooTextField(
                    value = state.licensePlates[v.vin] ?: "",
                    onValueChange = { vm.setLicensePlate(v.vin, it) },
                    label = { Text("License plate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.weight(1f)) {
                        vm.setLastServiceMiles(v.vin, it)
                    }
                    MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.weight(1f)) {
                        vm.setServiceIntervalMiles(v.vin, it)
                    }
                }
            }
        }
    }
}

/**
 * A digits-only "minutes" field for the notification-delay settings, clamped to 1..120.
 * It owns the edit buffer: [initial] seeds it and re-seeds whenever the persisted value
 * changes (via `remember(initial)`), while [onSet] fires only for an in-range number, so
 * a half-typed or out-of-range value is shown but never persisted. The three delay fields
 * (door-open, running, unlocked) differ only in seed, label and setter.
 */
@Composable
internal fun MinutesField(initial: Int, label: String, onSet: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    BlooTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit)
            text.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(onSet)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = GapRow),
    )
}

/**
 * A digits-only mileage field. The service card lays two of these side by side (each
 * `Modifier.weight(1f)`) while the search index surfaces the same two one at a time
 * (`Modifier.fillMaxWidth()`), so the width sits with the caller; everything else --
 * the digit filter, number keyboard, single line and [FieldShape] -- is identical and
 * lives here so the four copies can't drift apart.
 */
@Composable
internal fun MilesField(value: Int?, label: String, modifier: Modifier, onSet: (Int?) -> Unit) {
    BlooTextField(
        value = value?.toString() ?: "",
        onValueChange = { onSet(it.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * A hairline-outlined box, for the few things that need a visible edge on a glass card (a log, an
 * onboarding item). No fill: cards are glass, and a solid dark box dropped on them read as a hole.
 */
@Composable
internal fun Modifier.outlinedPanel(padding: Dp = 12.dp): Modifier =
    this.clip(StandardShape)
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), StandardShape)
        .padding(padding)

/** A titled sub-group inside a settings card: a heading, then its controls, straight on the card. */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
