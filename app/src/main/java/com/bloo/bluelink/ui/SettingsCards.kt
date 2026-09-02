@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.surfaceColorAtElevation
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
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.delay

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
            Spacer(Modifier.height(SettingsGapGroup))
            TroubleshootStep(
                1,
                "Not appearing or updating reliably at all -- especially if it takes a while after charging starts? Use the \"Tap to fix\" link above if it's showing: that's Android's battery-optimization exemption, needed for the background check that posts and updates the bar to run on schedule while the app isn't open." +
                    if (isSamsung) " Samsung also has its OWN separate \"sleeping apps\" restriction, not covered by that fix -- check Settings → Battery → Background usage limits → Sleeping apps / Deep sleeping apps and make sure Bloo isn't listed there." else "",
            )
            TroubleshootStep(2, "Make sure \"Live charging updates\" is on above, and the car is actually charging -- the bar only exists while charging is true.")
            TroubleshootStep(3, "Below Android 16, the chip can never appear anywhere -- only the plain progress bar in the shade. That's expected, not a bug.")
            TroubleshootStep(4, "On Android 16+, use the \"Tap to fix\" link above if it's showing for the status bar -- that's the OS's own per-app Live Updates permission.")
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
            val bgSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = bgSource,
                enabled = true,
            ) {
                // MorphTextButton, not a hand-rolled MorphButton{Text(...)} -- that Text had no
                // `style`, unlike "Close" below it.
                MorphTextButton(
                    "Allow background activity",
                    onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                    interactionSource = bgSource,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isSamsung) {
                val devSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = devSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Open Developer options",
                        onClick = { LiveCharge.openDeveloperOptions(context) },
                        interactionSource = devSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val closeSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = closeSource,
                enabled = true,
            ) {
                MorphTextButton(
                    "Close",
                    onDismiss,
                    interactionSource = closeSource,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
internal fun TroubleshootStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(bottom = 10.dp)) {
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
    dragHandle: Modifier,
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
        vm = vm,
        dragHandle = dragHandle,
        summary = "${v.model} · ${state.powertrainLabel(v)}",
        containerColor = cardBg,
        forceExpanded = !collapsible,
    ) {
        SettingsGroup("Powertrain") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }

        // Only Hyundai/Genesis US vehicles have a real head-unit generation to
        // confirm -- see Vehicle.platformOverridable's own doc. Every other
        // brand/region always resolves the same way regardless, so showing
        // this picker there would be a control with no actual effect.
        if (v.platformOverridable) {
            SettingsGroup("Head-unit generation") {
                Text(
                    "Bloo can't always tell Gen5W and ccNC head units apart from the " +
                        "API alone. Confirm which one this car has so features like " +
                        "Trips only show up when they're actually available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SettingsGapHairline))
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }

        SettingsGroup("Climate features") {
            Text(
                "The remote climate command controls four seat positions. Enable " +
                    "heating and/or cooling for the seats your car actually has.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeatPositions.forEach { pos ->
                SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                    { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
            }
            ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
        }

        if (state.settingsMode == "advanced") SettingsGroup("Default climate start") {
            val carPresets = state.climatePresets[v.vin].orEmpty()
            val currentDefault = state.defaultClimatePresets[v.vin] ?: "smart"
            Text(
                "When the climate Start button is tapped (collapsed view), " +
                    "the app runs your chosen preset or smart climate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SettingsGapHairline))
            MorphSegmented(
                options = buildList {
                    add(SegmentOption("smart", "Smart", null))
                    carPresets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                },
                selectedKey = currentDefault,
                onSelect = { key -> vm.setDefaultClimatePreset(v.vin, key.takeIf { it != "smart" }) },
            )
        }

        // Per-car palette override: existed in SettingsStore/AppViewModel
        // (setCarPaletteId) with no UI entry point anywhere -- only shown
        // once there's at least one custom palette to actually choose.
        val appearance = LocalAppearance.current
        if (state.settingsMode == "advanced" && appearance.customPalettes.isNotEmpty()) {
            SettingsGroup("Palette override") {
                Text(
                    "Give this car its own colour palette instead of the app-wide theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SettingsGapHairline))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appearance.customPalettes.forEach { palette ->
                        val selected = appearance.carCustomPaletteIds[v.vin] == palette.id
                        CustomPaletteSwatch(
                            palette = palette,
                            selected = selected,
                            onClick = { vm.setCarPaletteId(v.vin, if (selected) null else palette.id) },
                            onEdit = {},
                        )
                    }
                }
            }
        }

        SettingsGroup("Photo") {
            val storedImage = state.imageUrls[v.vin]
            // A live preview instead of just "Custom photo set" as plain
            // text -- there was no way to actually see the effect of a
            // photo change without leaving Settings and finding this car
            // on the garage screen.
            if (!storedImage.isNullOrBlank()) {
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
            if (storedImage != null && storedImage.startsWith("/")) {
                Text(
                    "Custom photo set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = storedImage ?: "",
                    onValueChange = { vm.setVehicleImage(v.vin, it) },
                    label = { Text("Image URL (blank = gradient)") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // A group, not a plain Row: pressing "Choose photo" should take its extra width
            // from "Clear" beside it rather than shove it sideways. See ExpressiveButtons.kt.
            ExpressiveButtonRow(spacing = 8.dp) {
                MorphTextButton("Choose photo", onClick = onPickPhoto)
                if (state.imageUrls[v.vin] != null) {
                    MorphTextButton("Clear", onClick = { vm.setVehicleImage(v.vin, "") })
                }
            }
        }

        // Identity & service tracking and pebble visibility are both
        // power-user record-keeping, not something a first-time or
        // casual user needs to see every time they open a car's
        // settings -- Simple mode now only shows what actually changes
        // which controls appear (photo, powertrain, seat/climate
        // features), matching Default climate start/Palette override
        // above.
        if (state.settingsMode == "advanced") {
            SettingsGroup("Identity & service") {
                SelectionContainer { StatusRow("VIN", v.vin) }
                OutlinedTextField(
                    value = state.licensePlates[v.vin] ?: "",
                    onValueChange = { vm.setLicensePlate(v.vin, it) },
                    label = { Text("License plate") },
                    singleLine = true,
                    shape = FieldShape,
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

            SettingsGroup("Sections shown") {
                val labels = mapOf(
                    "charge" to "Charge / fuel",
                    "climate" to "Climate",
                    "location" to "Location",
                    "weather" to "Weather",
                    "trips" to "Trips",
                    "info" to "Car info",
                    "diagnostics" to "Diagnostics",
                    "ai" to "AI summary",
                )
                com.bloo.bluelink.data.HIDEABLE_SECTIONS
                    // The AI toggle only matters when AI is enabled for this device.
                    .filter { it != "ai" || state.aiEnabled }
                    .forEach { sec ->
                        ToggleRow(labels[sec] ?: sec, !state.isPebbleHidden(v.vin, sec)) { show ->
                            vm.setSectionHidden(v, sec, !show)
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
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit)
            text.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(onSet)
        },
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { onSet(it.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A titled, boxed sub-group inside the per-car settings card, for hierarchy. */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
