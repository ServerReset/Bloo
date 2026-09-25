@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import android.net.Uri
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.links
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope

/**
 * The second half of Settings' per-card content functions (Location through
 * Credits) -- split out of SettingsScreen.kt purely to keep that file smaller.
 * All promoted private -> internal since SettingsScreen, which stays there,
 * calls each one exactly once from its LazyColumn.
 */

/** "Location" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun LocationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard("Location", Icons.Filled.LocationOn, vm) {
                BodySmallText(
                    "Where \"my location\" points for weather -- and, once set that way, the " +
                        "same live position the map's own device dot and \"distance to car\" use.",
                )
                Spacer(Modifier.height(SettingsGapRow))
                var weatherQuery by remember { mutableStateOf("") }
                val locationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) vm.useDeviceLocationForWeather()
                    else vm.reportError("Location permission denied. Type a place instead")
                }
                // PopVisible, not a bare `?.let` -- was snapping in/out with zero
                // animation whenever a place got set or cleared.
                PopVisible(visible = appearance.weatherLabel != null) {
                  Column(Modifier.fillMaxWidth()) {
                    // fillMaxWidth() on the Row, not just the Column: a weighted child
                    // needs its immediate parent to actually claim the available width,
                    // not just an ancestor further up -- without it here, the place-name
                    // Text (weight(1f)) had no real width to size against and wrapped
                    // character-by-character ("S/u/n/n/y/v/a/l/e" one letter per line),
                    // ballooning the whole card's height. Same class of bug StatusRow's
                    // own doc warns about; maxLines/ellipsis added as the same guard.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ThemedIcon(Icons.Filled.LocationOn, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            appearance.weatherLabel.orEmpty(),
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val weatherClearSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = weatherClearSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Clear",
                                onClick = { vm.clearWeatherLocation() },
                                interactionSource = weatherClearSource,
                            )
                        }
                    }
                    Spacer(Modifier.height(SettingsGapRow))
                  }
                }
                OutlinedTextField(
                    value = weatherQuery,
                    onValueChange = { weatherQuery = it },
                    label = { Text("City or place") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
                Spacer(Modifier.height(SettingsGapRow))
                // FlowRow, not a fixed 50/50 Row: at a large display/font size each
                // half was too narrow for "Set place" / "My location", clipping them
                // to "Set a…". FlowRow keeps them side-by-side when they fit and wraps
                // the second button onto its own full-width line when they don't, so
                // the labels stay whole at any font scale.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val setPlaceSource = remember { MutableInteractionSource() }
                    // weight on the WRAPPER: it is the FlowRow's child, and the button inside
                    // it is not, so the weight there was read by nobody and these two never
                    // split the line evenly the way the FlowRow note above assumes.
                    SafeExpansiveButton(
                        interactionSource = setPlaceSource,
                        enabled = weatherQuery.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        // Both halves of this pair are the shared MorphActionButton now. They
                        // sit in one row doing the same kind of thing, and were a text button
                        // beside a default-filled one -- the mismatch reads as two different
                        // controls when it is one choice with two answers.
                        MorphActionButton(
                            label = "Set place",
                            icon = Icons.Filled.Place,
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = setPlaceSource,
                            enabled = weatherQuery.isNotBlank(),
                            onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                        )
                    }
                    val myLocationSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = myLocationSource,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        MorphActionButton(
                            label = "My location",
                            icon = Icons.Filled.MyLocation,
                            onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = myLocationSource,
                        )
                    }
                }
            }
}

/** "Logs" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun LogsCardContent(logs: List<String>, vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
            SettingsCard("Logs", AppIcons.Info, vm) {
                // No local expand state any more. The card's OWN chevron (PebbleShell's, via
                // SettingsCard) already governs this body -- nothing inside a collapsed card is
                // composed at all -- so the "Show"/"Hide" button that used to live on this row
                // was a second disclosure for the same content: open the card, then open the log
                // again. One control, the outer one.
                val lineCount = logs.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemedIcon(AppIcons.Info, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    BodyMediumText(
                        "Activity log  ·  $lineCount lines",
                        modifier = Modifier.weight(1f),
                    )
                    // Always present now: they used to appear only once the inner disclosure
                    // was opened, which is exactly the second step that made this card feel
                    // like it opened twice.
                    ExpressiveButtonRow(spacing = 0.dp) {
                            val copySource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = copySource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    "Copy",
                                    onClick = {
                                        clipboardScope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("bloo logs", logs.joinToString("\n"))),
                                            )
                                        }
                                    },
                                    interactionSource = copySource,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            val clearSource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = clearSource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    "Clear",
                                    onClick = { vm.clearLogs() },
                                    interactionSource = clearSource,
                                )
                            }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Column {
                    Spacer(Modifier.height(SettingsGapHairline))
                        SectionDivider()
                        Spacer(Modifier.height(SettingsGapHairline))
                        val logScroll = rememberScrollState()
                        SelectionContainer {
                            Text(
                                text = logs.joinToString("\n").ifBlank { "No activity yet." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .fadingEdges(logScroll)
                                    .verticalScroll(logScroll),
                            )
                        }
                        Spacer(Modifier.height(SettingsGapHairline))
                        if (lineCount > 0) {
                            LabelSmallText(
                                "Earliest entries at the top. The newest $lineCount lines are shown.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
}

/** "Map & Navigation" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun MapNavigationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard("Map & Navigation", Icons.Filled.Map, vm) {
                TitleSmallText("Open Charge Map API Key")
                BodySmallText(
                    "Required to show nearby EV chargers on the expanded map. Get a free key at openchargemap.org (My Profile → My Apps).",
                )
                Spacer(Modifier.height(SettingsGapRow))
                var keyInput by remember { mutableStateOf(appearance.chargerApiKey ?: "") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste API key here") },
                        singleLine = true,
                        shape = FieldShape,
                        colors = borderlessFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null)
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    MorphTextButton(
                        "Save",
                        onClick = { vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null) },
                        enabled = keyInput.isNotBlank() && keyInput != (appearance.chargerApiKey ?: ""),
                        showIcon = false,
                    )
                }
            }
}

/** "Notifications" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun NotificationsCardContent(notif: SettingsStore.NotificationPrefs, vm: AppViewModel) {
            SettingsCard("Notifications", Icons.Filled.Notifications, vm) {
                // Icon-badge + status-line header, matching Backup & sync/Updates --
                // this card used to open straight into a wall of toggles with no
                // at-a-glance read of how many alerts were actually live.
                val alertToggles = listOf(notif.charging, notif.service, notif.doorOpen, notif.running, notif.unlocked, notif.carStarted, notif.chargeComplete)
                val alertsOn = alertToggles.count { it }
                val notifTint = if (alertsOn > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                // Icons.Filled.Notifications only -- NotificationsActive/Off aren't in
                // this project's icon set (confirmed by CI), so the on/off read comes
                // from the tint + status line alone, same as every other header here
                // that doesn't have a distinct icon per state.
                StatusHeaderRow(
                    icon = Icons.Filled.Notifications,
                    tint = notifTint,
                    title = "Alerts",
                    status = if (alertsOn == 0) "All off" else "$alertsOn of ${alertToggles.size} on",
                )
                Spacer(Modifier.height(SettingsGapGroup))
                // First, not last: every other switch in this card is an
                // ALERT the user hopes never fires. This is a live surface
                // they watch on purpose while the car charges.
                ToggleRow(
                    "Live charging updates",
                    notif.charging,
                    description = "A progress bar in the shade and, on Android 16+, in the status bar and " +
                        "lock screen while the car charges -- with the charge limit marked and " +
                        "a Stop button.",
                ) { vm.setNotifyCharging(it) }
                // Whether it actually promotes to the status-bar chip is a
                // system decision this app cannot force -- Android 16+ has a
                // real API to check the user's per-app toggle for it, though,
                // so query it instead of guessing.
                // PopVisible, not a bare `if` -- same consistency fix as the minute
                // fields below: this whole troubleshooting block used to snap in/out
                // with the charging toggle with no animation at all.
                PopVisible(visible = notif.charging) {
                    var showTroubleshoot by remember { mutableStateOf(false) }
                    Column {
                    // Version-independent, unlike the chip-promotion check below: this is
                    // about whether the background poll that would post/update the bar at
                    // all gets to run while the app isn't open, which matters on every
                    // Android version this app supports.
                    run {
                        val ctx = LocalContext.current
                        // Real buttons, not clickable labels. These three were bare lines of
                        // small text whose only affordance was the words "Tap to fix" -- the
                        // one place in Settings where something important to press did not
                        // look pressable, and an 8dp-tall target when it was.
                        if (!LiveCharge.isBackgroundUnrestricted(ctx)) {
                            // The question is a caption and the action is a button, rather
                            // than one long sentence inside the button: a standard button label
                            // is one line that never wraps (see MorphButtonLabel), and "Not
                            // starting when charging begins? Tap to fix" does not fit one
                            // inside a Settings card.
                            SettingsCaption("Not starting when charging begins?", bottomGap = SettingsGapHairline)
                            MorphTextButton(
                                text = "Allow background",
                                onClick = { LiveCharge.requestBackgroundUnrestricted(ctx) },
                                contentColor = MaterialTheme.colorScheme.primary,
                                icon = AppIcons.Warning,
                            )
                            Spacer(Modifier.height(SettingsGapRow))
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        val ctx = LocalContext.current
                        if (!LiveCharge.isPromotable(ctx)) {
                            SettingsCaption("Not showing in the status bar?", bottomGap = SettingsGapHairline)
                            MorphTextButton(
                                text = "Open system settings",
                                onClick = { LiveCharge.openLiveUpdateSettings(ctx) },
                                contentColor = MaterialTheme.colorScheme.primary,
                                icon = AppIcons.Warning,
                            )
                            Spacer(Modifier.height(SettingsGapRow))
                        }
                    }
                    // Still shown even when isPromotable is already true: that check only
                    // covers the generic Android permission, and at least one real OEM (see
                    // LiveUpdateTroubleshootDialog) gates the chip behind a second switch that
                    // permission can't see -- confirmed on a real device this app had no way
                    // to detect from here.
                    MorphTextButton(
                        text = "Troubleshooting steps",
                        onClick = { showTroubleshoot = true },
                        icon = AppIcons.Info,
                    )
                    Spacer(Modifier.height(SettingsGapRow))
                    if (showTroubleshoot) {
                        LiveUpdateTroubleshootDialog(onDismiss = { showTroubleshoot = false })
                    }
                    }
                }
                SectionDivider(alpha = 0.5f)
                Spacer(Modifier.height(SettingsGapRow))

                ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
                ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
                // PopVisible, not a bare `if` -- these three minute fields used to snap in
                // and out with zero animation, the one inconsistency left in a card whose
                // header now springs and whose sibling cards (the update pebble, search
                // results) all pop their own conditional rows the same way.
                PopVisible(visible = notif.doorOpen) {
                    MinutesField(notif.doorOpenMinutes, "Door-open minutes", vm::setDoorOpenMinutes)
                }
                ToggleRow("Car-running alerts", notif.running) { vm.setNotifyRunning(it) }
                PopVisible(visible = notif.running) {
                    MinutesField(notif.runningMinutes, "Running minutes", vm::setRunningMinutes)
                }
                ToggleRow("Left-unlocked alerts", notif.unlocked) { vm.setNotifyUnlocked(it) }
                PopVisible(visible = notif.unlocked) {
                    MinutesField(notif.unlockedMinutes, "Unlocked minutes", vm::setUnlockedMinutes)
                }
                ToggleRow("Car started notifications", notif.carStarted) { vm.setNotifyCarStarted(it) }
                ToggleRow("Charge complete notifications", notif.chargeComplete) { vm.setNotifyChargeComplete(it) }
                Text(
                    "Background checks run roughly every 30 minutes, so alerts may " +
                        "arrive a little after your set time. Door and running alerts " +
                        "include a one-tap action to lock or turn the car off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
}

/** "Security" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun SecurityCardContent(
    state: UiState,
    vm: AppViewModel,
    canBio: Boolean,
    context: Context,
    appearance: SettingsStore.Appearance,
) {
            SettingsCard("Security", AppIcons.Lock, vm) {
                // Same icon-badge + status-line header Notifications/Backup & sync use --
                // this card used to open straight into a segmented row with no glanceable
                // read of whether the app lock is actually on.
                val locked = canBio && appearance.biometricLock
                val securityTint = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                val securityStatus = when {
                    !canBio -> "No fingerprint enrolled"
                    locked -> "Locked · ${appearance.lockTiming.label}"
                    else -> "Not locked"
                }
                StatusHeaderRow(
                    icon = if (locked) AppIcons.Lock else AppIcons.LockOpen,
                    tint = securityTint,
                    title = "App lock",
                    status = securityStatus,
                )
                Spacer(Modifier.height(SettingsGapGroup))
                if (canBio) {
                    // One control, three states. This used to be a "require fingerprint"
                    // on/off toggle plus a separate lock-timing row, which let people save
                    // contradictory combinations (lock ON with "never re-lock", or a lock
                    // timing shown while the lock itself was OFF). A single group keeps the
                    // flag and its timing as one decision:
                    //   Off        -> no lock at all
                    //   Screen off -> lock on launch, re-lock when the screen turns off
                    //   Immediate  -> lock on launch, re-lock the moment it's backgrounded
                    val timingOn = appearance.biometricLock
                    SettingsSegmentedRow(
                        label = "App lock",
                        options = listOf(
                            SegmentOption("off", LockTiming.OFF.label, null),
                            SegmentOption("screen_off", LockTiming.SCREEN_OFF.label, null),
                            SegmentOption("immediate", LockTiming.IMMEDIATE.label, null),
                        ),
                        selectedKey = when {
                            !timingOn -> "off"
                            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
                            else -> "screen_off"
                        },
                        onSelect = { key ->
                            when (key) {
                                "off" -> {
                                    // Turning the lock OFF needs the same authentication
                                    // turning it on does, otherwise reaching Settings from an
                                    // already-unlocked app lets a single unauthenticated tap
                                    // permanently remove the lock on future cold launches --
                                    // turning momentary physical access into standing access
                                    // to unlocking the car. Failing to authenticate keeps the
                                    // lock on.
                                    val activity = context.findFragmentActivity()
                                    // The cold-start lock decision is (biometricLock && canBio)
                                    // OR a PIN being set (see AppViewModel's own doc) -- clearing
                                    // ONLY biometricLock here used to leave the app locking on
                                    // every launch anyway whenever a PIN was also set, reported
                                    // directly as "turn off locking, it still asks every time".
                                    // "App lock: Off" is this control's own promise that the app
                                    // stops asking at all, so it has to clear BOTH mechanisms --
                                    // the same biometric confirmation already required to get
                                    // here is treated as proof of identity everywhere else in
                                    // this screen (enabling/disabling the lock itself), so
                                    // reusing it to also drop the PIN isn't a weaker gate than
                                    // the PIN removal dialog's own "enter the current PIN" check,
                                    // just a different, already-trusted proof of the same thing.
                                    val pinAlsoSet = state.appPinSet
                                    if (activity == null) {
                                        // Fail closed -- keep the lock -- but say so,
                                        // rather than leaving the control looking stuck.
                                        vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                                    } else {
                                        showBiometricPrompt(
                                            activity = activity,
                                            title = "Turn off app lock",
                                            subtitle = if (pinAlsoSet) {
                                                "Confirm to stop requiring it -- this also removes your PIN"
                                            } else {
                                                "Confirm to stop requiring it"
                                            },
                                            onSuccess = {
                                                vm.setBiometricLock(false)
                                                if (pinAlsoSet) vm.removeAppPin()
                                            },
                                            onError = { },
                                        )
                                    }
                                }
                                else -> {
                                    val timing = if (key == "immediate") LockTiming.IMMEDIATE else LockTiming.SCREEN_OFF
                                    if (timingOn) {
                                        // Already locked: changing *when* it re-locks needs no
                                        // extra proof -- the user just proved who they are to
                                        // be in here, and tightening the timing is harmless.
                                        vm.setLockTiming(timing)
                                    } else {
                                        // Turning the lock ON proves who you are first, then
                                        // both arms it and sets when it re-locks.
                                        val activity = context.findFragmentActivity()
                                        if (activity == null) {
                                            vm.reportInfo("Couldn't verify it's you. The lock wasn't turned on.")
                                        } else {
                                            showBiometricPrompt(
                                                activity = activity,
                                                title = "Enable fingerprint lock",
                                                subtitle = "Confirm to require it on launch",
                                                onSuccess = {
                                                    vm.setBiometricLock(true)
                                                    vm.setLockTiming(timing)
                                                },
                                                onError = { },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                } else {
                    BodyMediumText(
                        "No fingerprint/biometric is enrolled on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // --- App PIN ---
                // The device unlock PIN: the required mechanism on devices with
                // no biometrics, an optional backup on those that have them.
                // Separate from the biometric rows above because it is a second,
                // independent mechanism, not a mode of the first one.
                Spacer(Modifier.height(SettingsGapGroup))
                SectionDivider(alpha = 0.4f)
                Spacer(Modifier.height(SettingsGapHairline))
                var pinDialog by remember { mutableStateOf<String?>(null) }
                val pinSet = state.appPinSet
                StatusHeaderRow(
                    icon = if (pinSet) AppIcons.Lock else Icons.Filled.Pin,
                    tint = if (pinSet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "App PIN",
                    status = if (pinSet) "On · 4-8 digits" else "Off",
                )
                Spacer(Modifier.height(SettingsGapHairline))
                BodySmallText(
                    if (canBio)
                        "A 4-8 digit PIN that works as a backup when fingerprints aren't available."
                    else
                        "This device has no fingerprints, so the app unlocks with this PIN.",
                )
                Spacer(Modifier.height(SettingsGapGroup))
                ExpressiveButtonRow(spacing = 8.dp) {
                    val pinSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = pinSource,
                        enabled = true,
                    ) {
                        // The shared action button. "Remove" beside it keeps its errorContainer
                        // tone on purpose -- red is the one difference in this row that carries
                        // meaning, so the OTHER half is what had to move onto the standard look.
                        MorphActionButton(
                            label = if (pinSet) "Change PIN" else "Set up PIN",
                            icon = if (pinSet) Icons.Filled.LockReset else AppIcons.Lock,
                            onClick = { pinDialog = "set" },
                            interactionSource = pinSource,
                        )
                    }
                    if (pinSet) {
                        val removeSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = removeSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Remove",
                                onClick = { pinDialog = "remove" },
                                interactionSource = removeSource,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                PinDialogs(
                    mode = pinDialog,
                    onDismiss = { pinDialog = null },
                    vm = vm,
                    state = state,
                    canBio = canBio,
                )
            }
}

/** "Sounds & vibration" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun SoundsVibrationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard(
                "Sounds & vibration",
                Icons.Filled.Vibration,
                vm,
                inlineSetting = { InlineToggle(appearance.hapticsEnabled) { vm.setHapticsEnabled(it) } },
            ) {}
}

/** "Theme" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun ThemeCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
            SettingsCard("Theme", Icons.Filled.Palette, vm) {
                // Same icon-badge + status-line header as the rest of this pass.
                val themeTint = MaterialTheme.colorScheme.tertiary
                val themeLabel = when (appearance.themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
                StatusHeaderRow(
                    icon = Icons.Filled.Palette,
                    tint = themeTint,
                    title = "Display mode",
                    status = if (appearance.auroraBackground) "$themeLabel · Aurora" else themeLabel,
                )
                Spacer(Modifier.height(SettingsGapGroup))
                // Dark is always true black (OLED-friendly) now -- see blooColorScheme's
                // own doc. This used to also offer separate "AMOLED"/"+AMOLED" options
                // alongside plain Dark/System; there was no reason to make dark mode
                // dimmer than it needs to be by default, so that's just what Dark/System
                // (while dark) do now, with no extra choice to make.
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
                // Advanced-only, same tier as the dynamic-color block below --
                // Aurora's motion sub-option is power-user territory, not
                // something a simple-mode user needs (the built-in solid-surface
                // background covers everyone else).
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 5), enter = expandEnterSized(), exit = expandExitSized()) {
                  Column {
                    Spacer(Modifier.height(SettingsGapRow))
                    ToggleRow("Aurora background", appearance.auroraBackground) { vm.setAuroraBackground(it) }
                    BodySmallText(
                        "Show a gradient aurora behind the content instead of a solid surface.",
                    )
                    // Same AnimatedVisibility-wraps-a-Column idiom as the dynamic-
                    // color section below, instead of a bare `if` -- this whole
                    // Motion block otherwise just materialized the instant the
                    // toggle above flipped on.
                    AnimatedVisibility(
                        visible = appearance.auroraBackground,
                        enter = expandEnterSized(),
                        exit = expandExitSized(),
                    ) {
                        Column {
                            Spacer(Modifier.height(SettingsGapRow))
                            Text("Motion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(SettingsGapHairline))
                            MorphSegmented(
                                options = listOf(
                                    SegmentOption("static", "Static", null),
                                    SegmentOption("motion", "Motion", null),
                                ),
                                selectedKey = appearance.auroraMotion,
                                onSelect = { vm.setAuroraMotion(it) },
                            )
                        }
                    }
                  }
                }
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 6), enter = expandEnterSized(), exit = expandExitSized()) {
                  // AnimatedVisibility lays out a single child, not an implicit
                  // Column of its content lambda's composables -- without this
                  // wrapper the Spacer/Divider/Toggle/Slider siblings below would
                  // all stack on top of each other instead of flowing vertically.
                  Column {
                    Spacer(Modifier.height(SettingsGapGroup))
                    SectionDivider()
                    Spacer(Modifier.height(SettingsGapRow))
                    ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                    BodySmallText(
                        "Uses your wallpaper palette on Android 12+. Turn off to choose a built-in palette below.",
                    )
                    AnimatedVisibility(
                        visible = !appearance.dynamicColor,
                        enter = expandEnterSized(),
                        exit = expandExitSized(),
                    ) {
                        Column {
                            Spacer(Modifier.height(SettingsGapRow))
                            LabelText("Built-in palettes")
                            Spacer(Modifier.height(SettingsGapHairline))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorPalette.entries.forEach { palette ->
                                    PaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                                        onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                                    )
                                }
                            }
                            // Custom palettes: the create/edit dialog and per-palette
                            // selection existed (SettingsStore + AppViewModel) but had
                            // no entry point anywhere in the UI after the old Color
                            // card was merged into this Theme card -- restore it here.
                            Spacer(Modifier.height(SettingsGapRow))
                            LabelText("Custom palettes")
                            Spacer(Modifier.height(SettingsGapHairline))
                            var editingPalette by remember { mutableStateOf<CustomPaletteData?>(null) }
                            var showPaletteEditor by remember { mutableStateOf(false) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                appearance.customPalettes.forEach { palette ->
                                    CustomPaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == palette.id,
                                        onClick = { vm.setActiveCustomPaletteId(palette.id) },
                                        onEdit = { editingPalette = palette; showPaletteEditor = true },
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable { editingPalette = null; showPaletteEditor = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "New custom palette")
                                    }
                                    Spacer(Modifier.height(SettingsGapHairline))
                                    LabelSmallText("New")
                                }
                            }
                            if (showPaletteEditor) {
                                PaletteEditorDialog(
                                    editing = editingPalette,
                                    onSave = { vm.saveCustomPalette(it); vm.setActiveCustomPaletteId(it.id); showPaletteEditor = false },
                                    onDelete = { vm.deleteCustomPalette(it); showPaletteEditor = false },
                                    onDismiss = { showPaletteEditor = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(SettingsGapRow))
                    VibrancySlider(appearance, vm)
                    Spacer(Modifier.height(SettingsGapRow))
                    ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                  }
                }
            }
}

/** "Credits" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun CreditsCardContent(vm: AppViewModel) {
                SettingsCard("Credits", AppIcons.Info, vm) {
                    Column {
                        val credits = remember {
                            listOf(
                                CreditEntry(
                                    "Coil",
                                    "Image loading throughout the app -- car photos, map tiles, everything.",
                                    "https://github.com/coil-kt/coil",
                                    Icons.Filled.Image,
                                ),
                                CreditEntry(
                                    "Haze",
                                    "Real backdrop blur behind the status bar and the full-screen map sheet.",
                                    "https://github.com/chrisbanes/haze",
                                    Icons.Filled.BlurOn,
                                ),
                                CreditEntry(
                                    "i5-AutoLock",
                                    "AutoLock ported from Vel-San's original reference implementation.",
                                    "https://github.com/Vel-San/i5-AutoLock",
                                    AppIcons.Lock,
                                ),
                                CreditEntry(
                                    "Jetpack Compose",
                                    "The UI toolkit this entire app -- every screen, every pebble, every animation -- is built with.",
                                    "https://developer.android.com/jetpack/compose",
                                    Icons.Filled.Widgets,
                                ),
                                CreditEntry(
                                    "Kotlin",
                                    "The language everything here, front to back, is written in.",
                                    "https://kotlinlang.org",
                                    Icons.Filled.Code,
                                ),
                                CreditEntry(
                                    "kotlinx.serialization",
                                    "Every persisted setting and cached response.",
                                    "https://github.com/Kotlin/kotlinx.serialization",
                                    Icons.Filled.DataObject,
                                ),
                                CreditEntry(
                                    "OkHttp",
                                    "Every network request this app makes.",
                                    "https://square.github.io/okhttp",
                                    Icons.Filled.Language,
                                ),
                                CreditEntry(
                                    "OpenStreetMap",
                                    "Map tiles for the car's and device's location, on the phone and the flip cover. © OpenStreetMap contributors.",
                                    "https://www.openstreetmap.org/copyright",
                                    Icons.Filled.Map,
                                ),
                                CreditEntry(
                                    "Shizuku",
                                    "Optional silent-install path for updates, skipping the manual \"Install anyway\" prompt.",
                                    "https://github.com/RikkaApps/Shizuku",
                                    Icons.Filled.AdminPanelSettings,
                                ),
                            )
                        }
                        credits.forEachIndexed { index, entry ->
                            CreditRow(entry)
                            if (index != credits.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
}

/** One entry in the Credits card -- see [CreditRow]. */
private data class CreditEntry(
    val name: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
)

/**
 * One row of the Credits card: a small icon chip, the project's name/description,
 * and its actual URL as a tappable link (opened via [openUrl] in a Custom Tab) --
 * replacing what used to be three plain, uncoloured, unclickable Text lines with no
 * visual distinction between them at all. Reported directly as wanting this whole
 * section "beefed out" with real links, not a flat wall of tiny grey text.
 */
@Composable
private fun CreditRow(entry: CreditEntry) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(
            entry.icon,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            size = 40.dp,
            iconSize = 20.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            BodySmallText(
                entry.description,
            )
            Spacer(Modifier.height(6.dp))
            // The actual link, styled and tappable -- not a caption-coloured, inert
            // copy of the URL. clip+clickable (not the whole Row, which would make
            // the icon/name/description look tappable too when only the link is)
            // sized to just this Row's own content via wrapContentWidth, so the tap
            // target doesn't stretch across empty space to the card's far edge.
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { openUrl(context, entry.url) }
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.url.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open link",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
