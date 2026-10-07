package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.SettingsStore
import androidx.compose.material.icons.filled.Watch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.PinCrypto
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.syncUri

/**
 * Step 1: a short welcome + feature highlights, sharing [OnboardingTipListPage]'s shape (no
 * per-item entrance animation).
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
 * Step 2: set the app up to work. Notifications and a lock (biometrics if available, else a PIN)
 * are required before Next unlocks; the lock card swaps to whichever the device supports.
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
        OnboardingStagger(0) {
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
                emphasis = ButtonEmphasis.Confirm,
            )
        }
        }
    }

    // One lock card, swapped to what this device can authenticate with.
    OnboardingStagger(1) {
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
                emphasis = ButtonEmphasis.Confirm,
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
    OnboardingStagger(2) {
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
            emphasis = ButtonEmphasis.Confirm,
        )
    }
    }
}

/**
 * First-run only: bring this device's setup in from another one. Picking a sync file joins it and
 * re-resolves where this device lands (straight to the app if the file already covered everything).
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

/**
 * First-run only: bring this device's setup in from another one. Picking a sync file joins it and
 * re-resolves where this device lands (straight to the app if the file already covered everything).
 * Anyone starting fresh just swipes on.
 */
@Composable
internal fun OnboardingLookPage(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    ThemeModeSegmentedRow(appearance) { vm.setThemeMode(it) }
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
 * The create-a-PIN mini form (also the base of the Settings PIN dialogs): two matching 4-8 digit
 * fields, Save enabled once valid. The caller decides what [existing] means; this form only reports
 * a valid new PIN.
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        PinField(
            value = pin,
            onValueChange = { pin = it; attempted = false },
            placeholder = "4–8 digit PIN",
            isError = attempted && pin.isNotEmpty() && pin.length < PinCrypto.PIN_MIN_DIGITS,
        )
        PinField(
            value = confirm,
            onValueChange = { confirm = it; attempted = false },
            placeholder = "Confirm PIN",
            isError = attempted && confirm.isNotEmpty() && pin != confirm,
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

/**
 * One onboarding Setup card: icon, title and body on a solid surface (not the busy Aurora
 * backdrop).
 */
@Composable
internal fun OnboardingSetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    /**
     * Required to leave this card: a "Required" chip until [done], so the user knows why Next is
     * disabled rather than just finding it greyed out.
     */
    required: Boolean = false,
    /**
     * The action that gets it done. Shown only until [done]: a finished item says so and gets out
     * of the way.
     */
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .onboardingPanel(14.dp)
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
                    done -> StatusChip("On", scheme.primary)
                    required -> StatusChip("Required", scheme.tertiary)
                }
            },
        )
        if (!done) content()
    }
}

/**
 * A single "tip" row: a primary-tinted icon beside a bold title and a muted one-line body, with a
 * hairline edge on the glass card. The welcome, tips and features cards each render a list of
 * these.
 */
@Composable
internal fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = StandardShape,
        color = scheme.surfaceContainerHighest.copy(alpha = 0.30f),
        border = androidx.compose.foundation.BorderStroke(1.dp, hairlineColor()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Same leading-circle icon badge as search results, the update pebble and the settings hero
        // stats.
        IconLeadRow(
            icon,
            tint = scheme.primary,
            title = title,
            subtitle = body,
            badgeSize = 28.dp,
            modifier = Modifier.padding(GapSection),
        )
    }
}

/**
 * Which alerts to get. The same switches as Settings → Notifications, the ones most people want
 * decided up front.
 */
@Composable
internal fun OnboardingAlertsPage(notif: SettingsStore.NotificationPrefs, vm: AppViewModel) {
    BodySmallText("Choose what Bloo tells you about. You can change any of these later in Settings.")
    Column(Modifier.fillMaxWidth().outlinedPanel(12.dp)) {
        ToggleRow("Live charging updates", notif.charging, description = "Progress and a Stop button while a car charges.") { vm.setNotifyCharging(it) }
        ToggleRow("Charge complete", notif.chargeComplete) { vm.setNotifyChargeComplete(it) }
        ToggleRow("Left unlocked", notif.unlocked) { vm.setNotifyUnlocked(it) }
        ToggleRow("Door left open", notif.doorOpen) { vm.setNotifyDoor(it) }
        ToggleRow("Service due", notif.service) { vm.setNotifyService(it) }
    }
}

/**
 * The watch: optional, so it is offered rather than required, with the same setup dialog Settings
 * uses.
 */
@Composable
internal fun OnboardingWatchPage() {
    var show by remember { mutableStateOf(false) }
    BodySmallText("Got a Wear OS watch? Bloo has a watch app with quick actions, a Tile and its own notifications. It installs straight from this phone, no Play Store needed.")
    SafeMorphTextButton("Set up my watch", onClick = { show = true }, icon = Icons.Filled.Watch, emphasis = ButtonEmphasis.Confirm)
    BodySmallText("No watch? Swipe on. You can set one up later from Settings → Backup & sync.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (show) SetupWatchDialog(phoneName = "this phone", onDismiss = { show = false })
}

/**
 * A glassy inner panel: a translucent pane with the shared frosted rim, used for the setup and tip
 * sub-cards so they layer as glass over the card's own glass rather than reading as flat outline.
 */
@Composable
private fun Modifier.onboardingPanel(padding: androidx.compose.ui.unit.Dp = GapGroup): Modifier =
    this
        .clip(StandardShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.30f), StandardShape)
        .glassRim(StandardShape)
        .padding(padding)
