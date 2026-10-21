package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.syncUri
import kotlinx.coroutines.launch
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.SafeMorphTextButton
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.SegmentOption
import com.bloo.uicommon.content

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
        // After a refusal the system dialog may never show again (a second denial, or "don't ask again", answers
        // instantly), and this step blocks Next: so the button then opens the app's notification settings, the one place
        // the permission can still be turned on. The resume re-check unblocks the step when it is.
        var denied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> if (!granted) denied = true; onNotifResult(granted) }
        OnboardingStagger(0) {
        OnboardingSetupCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Charge status, car alerts and app updates need this.",
            done = notifGranted,
            required = true,
        ) {
            MorphActionButton(
                label = if (denied) "Open notification settings" else "Turn on notifications",
                icon = Icons.Filled.Notifications,
                onClick = {
                    if (denied) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    } else {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
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
        body = "Coming from another device? Pick the file it syncs to, and its settings and car setup come with it.",
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
    // This card is in the BUTTON section of the deck, where the pager's userScrollEnabled is false:
    // telling anyone to "swipe on" here was an instruction they could not follow. The swipeable part
    // comes later and says so itself.
    BodySmallText("New here? Tap Next to set up fresh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * First-run only: bring this device's setup in from another one. Picking a sync file joins it and
 * re-resolves where this device lands (straight to the app if the file already covered everything).
 * Anyone starting fresh taps Next.
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
    BodySmallText("Both live in Settings as well.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Which alerts to get. The same switches as Settings → Notifications, the ones most people want
 * decided up front.
 */
@Composable
internal fun OnboardingAlertsPage(notif: SettingsStore.NotificationPrefs, vm: AppViewModel) {
    // No intro line: the card's own summary already says "Which alerts reach you", and the line that
    // used to open this body said the same thing a second time in different words -- the exact
    // duplication COPY.md's closing rule is about. The list starts where the summary stops.
    //
    // innerGlassPanel(GapSection), not outlinedPanel(12.dp): this is the app's ONE inner-glass panel
    // for a group of things inside a card, and SettingsPrimitives names onboarding items among its
    // users. The literal 12.dp is why this panel's inset did not match the setup cards beside it.
    Column(
        Modifier.fillMaxWidth().innerGlassPanel(GapSection),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
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
    BodySmallText("Got a Wear OS watch? Bloo has a watch app with quick actions, a Tile and its own notifications. On the Play version it installs from Google Play; on this copy it installs straight from this phone.")
    SafeMorphTextButton("Set up my watch", onClick = { show = true }, icon = Icons.Filled.Watch, emphasis = ButtonEmphasis.Confirm)
    BodySmallText("No watch? Swipe on. You can set one up later from Settings → Backup & sync.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (show) SetupWatchDialog(phoneName = "this phone", onDismiss = { show = false })
}

/**
 * A little live pebble the user can actually tap and expand, so "tap a pebble for details" is
 * something they do, not just read. Shown on the Getting around card.
 */
@Composable
internal fun OnboardingPebbleDemo() {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var open by remember { mutableStateOf(false) }
    GlassSurface(
        shape = StandardShape,
        liquid = false,
        shadow = false,
        onClick = {
            open = !open
            if (open) haptics?.click() else haptics?.tick()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize().padding(GapSection),
            verticalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.Bolt, tint = scheme.primary, size = 28.dp, iconSize = 15.dp)
                Spacer(Modifier.width(GapRow))
                RevealText(
                    "Charge",
                    style = PrimaryTextStyle(),
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = open) {
                RevealText(
                    "That is a pebble. Tap it for details, hold to reorder, and swipe its top row to change cars.",
                    style = SecondaryTextStyle(),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
