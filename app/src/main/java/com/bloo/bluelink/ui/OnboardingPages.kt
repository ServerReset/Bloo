@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.SettingsStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.syncUri

/**
 * Step 1: a short welcome + feature highlights. See [OnboardingTipListPage]'s own doc for why
 * this shares that shape (including its no-per-item-entrance-animation rule) with the two
 * closing pages -- this page is where that rule was originally learned the hard way.
 */
@Composable
internal fun OnboardingWelcomePage() {
    OnboardingTipListPage(
        tips = listOf(
            Triple(AppIcons.Bolt, "Live status", "Battery, fuel, and lock state at a glance"),
            Triple(Icons.Filled.Thermostat, "Remote climate", "Warm it up or cool it down before you get in"),
            Triple(Icons.Filled.SwapHoriz, "Multiple cars", "Swipe between every car on your account"),
        ),
    )
}


/**
 * Step 2: get the app set up to actually work. Two things are REQUIRED before Next unlocks,
 * because the app is genuinely worse without them and "I'll do it later" reliably means never:
 *
 *  - **Notifications**, so charge/alerts/updates can reach the user at all (API 33+).
 *  - **A lock** -- biometrics when the device has them, otherwise a PIN. Without one of these
 *    the app can never lock itself, so the lock card SWAPS to whichever the device supports
 *    instead of showing both and letting the user skip the one that actually matters.
 *
 * Drive/manual sync stays optional: a restored backup can still skip the per-car setup screens
 * later in this flow for any car it already configured (see [buildOnboardingSteps]'
 * `preConfiguredVins`).
 */
@Composable
internal fun OnboardingSetupPage(
    vm: AppViewModel,
    state: UiState,
    context: android.content.Context,
    canBio: Boolean,
    biometricLock: Boolean,
    notifGranted: Boolean,
    onNotifResult: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> onNotifResult(granted) }
        OnboardingSetupCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Charge status, car alerts and app updates need this.",
            done = notifGranted,
            required = true,
        ) {
            MorphActionButton(
                label = "Turn on notifications",
                icon = Icons.Filled.Notifications,
                onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Primary,
            )
        }
    }

    // ONE lock card, swapped to whatever this device can actually authenticate with.
    if (canBio) {
        val bioEnabled = biometricLock
        OnboardingSetupCard(
            icon = Icons.Filled.Fingerprint,
            title = "Biometric lock",
            body = "Require your biometrics to open Bloo.",
            done = bioEnabled,
            required = true,
        ) {
            MorphActionButton(
                label = "Turn on biometric lock",
                icon = Icons.Filled.Fingerprint,
                onClick = {
                    context.findFragmentActivity()?.let { activity ->
                        showBiometricPrompt(
                            activity = activity,
                            title = "Enable biometric lock",
                            subtitle = "Confirm to require it when opening Bloo",
                            onSuccess = { vm.setBiometricLock(true) },
                            onError = {},
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Primary,
            )
        }
    } else {
        OnboardingSetupCard(
            icon = AppIcons.Lock,
            title = "PIN lock",
            body = "This device has no biometrics, so a 4-8 digit PIN locks the app.",
            done = state.appPinSet,
            required = true,
        ) {
            OnboardingPinForm(
                existing = state.appPinSet,
                onSet = { pin -> vm.setAppPin(pin) },
            )
        }
    }

    // --- Sync across devices (Google Drive or a plain file) ---
    var showDriveDialog by remember { mutableStateOf(false) }
    val driveSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.setSyncUri(it) } }
    val driveOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }
    if (showDriveDialog) {
        DriveSyncSetupDialog(
            onDismissRequest = { showDriveDialog = false },
            onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
            onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
        )
    }
    val syncEnabled = state.syncUri != null
    OnboardingSetupCard(
        icon = Icons.Filled.CloudSync,
        title = "Sync across devices",
        body = if (syncEnabled) {
            "Your settings and car photos back up to Google Drive automatically."
        } else {
            "Keep your settings, layout and car photos the same on every device."
        },
        done = syncEnabled,
    ) {
        MorphActionButton(
            label = "Set up Drive sync",
            icon = Icons.Filled.Cloud,
            onClick = { showDriveDialog = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/** One card in the onboarding Setup step: icon + title + body on a solid
 *  surface -- not directly on the animated Aurora background, which made
 *  plain text here hard to read against a busy, colourful, moving backdrop
 *  -- with [content] (a MorphButton or a "done" status row) below. [done]
 *  tints the icon chip to the primary color as a lightweight "this one's
 *  handled" cue, matching the checkmark treatment MorphButton itself already
 *  uses for its own active state. */
/**
 * First-run only: bring this device's setup in from another one. Picking a sync file joins it and
 * re-resolves where this device lands (straight to the app if the file already covered everything).
 * Anyone starting fresh just swipes on.
 */
@Composable
internal fun OnboardingRestorePage(vm: AppViewModel) {
    val context = LocalContext.current
    var restoring by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoring = true
            vm.restoreFromSyncThenContinue(context, uri)
        }
    }
    OnboardingSetupCard(
        icon = Icons.Filled.CloudSync,
        title = "Restore from sync",
        body = "Already use Bloo elsewhere? Pick the sync file that device uses and its theme, layout, alerts, presets and car setup come with it.",
        done = false,
    ) {
        MorphActionButton(
            label = "Choose sync file",
            icon = Icons.Filled.Cloud,
            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring,
            pending = restoring,
        )
    }
    BodySmallText("New here? Swipe on to set up fresh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}


/** How the app looks and reads: theme and units, the two choices everyone has an opinion on. */
@Composable
internal fun OnboardingLookPage(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Appearance",
        options = listOf(
            SegmentOption(ThemeMode.SYSTEM.name, "System", null),
            SegmentOption(ThemeMode.LIGHT.name, "Light", null),
            SegmentOption(ThemeMode.DARK.name, "Dark", null),
        ),
        selectedKey = appearance.themeMode.name,
        onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
    )
    SettingsSegmentedRow(
        label = "Units",
        options = listOf(
            SegmentOption("imperial", "Imperial", null),
            SegmentOption("metric", "Metric", null),
        ),
        selectedKey = appearance.unitSystem,
        onSelect = { vm.setUnitSystem(it) },
    )
    BodySmallText("Both live in Settings whenever you want to change them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}


/**
 * The create-a-PIN mini form used by onboarding (and, in a slimmer re-use,
 * the building block of the Settings set/change/remove dialogs): two
 * matching 4-8 digit fields, a haptic'd Save only once valid. [existing]
 * true just swaps the call to "Replace PIN" semantics -- the caller handles
 * what that means; this form only ever validates and reports a valid new
 * PIN.
 */
@Composable
internal fun OnboardingPinForm(
    existing: Boolean,
    onSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val valid = pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS &&
        pin == confirm
    val sanitize: (String) -> String = { it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        BlooTextField(
            value = pin,
            onValueChange = { pin = sanitize(it); attempted = false },
            placeholder = { Text("4–8 digit PIN") },
            singleLine = true,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && pin.isNotEmpty() && pin.length < PinCrypto.PIN_MIN_DIGITS,
            modifier = Modifier.fillMaxWidth(),
        )
        BlooTextField(
            value = confirm,
            onValueChange = { confirm = sanitize(it); attempted = false },
            placeholder = { Text("Confirm PIN") },
            singleLine = true,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && confirm.isNotEmpty() && pin != confirm,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && (pin.length < PinCrypto.PIN_MIN_DIGITS || pin != confirm)) {
            Text(
                "PINs must be 4-8 digits and match.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        val pinLabel: String = if (existing) "Replace PIN" else "Save PIN"
        MorphActionButton(
            label = pinLabel,
            icon = AppIcons.Lock,
            onClick = {
                if (valid) {
                    haptics?.click()
                    onSet(pin)
                    pin = ""
                    confirm = ""
                } else {
                    attempted = true
                    haptics?.tick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.isNotEmpty() && confirm.isNotEmpty(),
        )
    }
}


@Composable
internal fun OnboardingSetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    /** Required to leave this card: a "Required" chip until [done], so the user knows why Next
     *  is disabled rather than just finding it greyed out. */
    required: Boolean = false,
    /** The action that gets it done. Shown only until [done]: a finished item says so and gets out of the way. */
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .outlinedPanel(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        IconLeadRow(
            if (done) AppIcons.CheckCircle else icon,
            tint = if (done) scheme.primary else scheme.onSurfaceVariant,
            title = title,
            subtitle = body,
            trailing = {
                when {
                    done -> StatusChip("On", scheme.primary, scheme.onPrimary)
                    required -> StatusChip("Required", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                }
            },
        )
        if (!done) content()
    }
}


/** A small filled pill: the "On" / "Required" tag on a setup card. */
@Composable
private fun StatusChip(label: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}


/**
 * A single "tip" row: a primary-tinted icon beside a bold title and a muted one-line body, with a
 * hairline edge on the glass card. The welcome, tips and features cards each render a list of these.
 */
@Composable
internal fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = StandardShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The same leading-circle icon badge the search results list, the update pebble,
        // and the settings hero stats all already use -- a bare tinted icon here was the
        // one place left still doing it differently for no reason tied to this screen.
        IconLeadRow(
            icon,
            tint = scheme.primary,
            title = title,
            subtitle = body,
            badgeSize = 28.dp,
            modifier = Modifier.padding(14.dp),
        )
    }
}


/** One card per car: powertrain, head-unit generation, seats and steering-wheel heat, wired straight to the view model. */
@Composable
internal fun OnboardingCarPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    sc: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Powertrain", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        val currentPt = state.powertrainOf(vehicle)
        PowertrainPicker(current = currentPt) { pt -> vm.setPowertrain(vehicle, pt) }
    }

    // Only Hyundai/Genesis US vehicles have a real head-unit generation to
    // confirm -- see platformOverridable's own doc.
    if (vehicle.platformOverridable) {
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            Text("Head-unit generation", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
            PlatformPicker(current = state.platformOf(vehicle)) { pt -> vm.setPlatform(vehicle, pt) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Seats", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .outlinedPanel(12.dp),
        ) {
            SeatPositions.forEachIndexed { i: Int, pos: SeatPosition ->
                if (i > 0) SectionDivider(alpha = 0.35f)
                // The shared SeatConfigRow (Rows.kt) -- the exact row, with the exact
                // signature, that the per-car Settings card renders. The wizard used to keep
                // its own copy (WizardSeatRow + WizardToggleChip): same label + Heat/Cool
                // pair, but on half-height bespoke pills instead of MorphChip, and its "Cool"
                // chip carried a ❄️ the Settings one never had. One row, two places.
                SeatConfigRow(
                    pos.label,
                    pos.heat(sc),
                    pos.cool(sc),
                    onHeat = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.heatKey, enabled) },
                    onCool = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.coolKey, enabled) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Extras", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        // ToggleRow -- the app's one boolean-setting control, and the same row the
        // per-car Settings card shows this exact flag on ("Heated steering wheel").
        // This was a bespoke checkmark-pill MorphButton: a THIRD treatment of one
        // boolean, next to Settings' ToggleRow and the wizard's own steering page
        // (which hand-rolled a switch row of its own, see WizardSteeringPage). Same
        // label as Settings too, so the wizard doesn't teach a name the app then
        // stops using.
        ToggleRow("Heated steering wheel", sc.steeringWheel) { vm.setSeatFlag(vehicle, "sw", it) }
    }
}
