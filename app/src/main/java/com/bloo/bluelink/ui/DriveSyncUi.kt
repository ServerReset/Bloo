package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.SyncMerge
import com.bloo.bluelink.data.WatchLockTiming
import com.bloo.bluelink.data.relativeLabel
import com.bloo.bluelink.data.removeSyncedDevice
import com.bloo.bluelink.data.setPrimaryDevice
import com.bloo.bluelink.data.setWatchLockTiming
import com.bloo.bluelink.data.syncDeviceName
import com.bloo.bluelink.data.watchLockTiming
import com.bloo.bluelink.wear.WatchPresence
import com.bloo.bluelink.wear.WatchSignIn
import com.bloo.uicommon.ReorderColumn
import kotlinx.coroutines.delay
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.SafeMorphTextButton
import com.bloo.uicommon.SegmentOption

/**
 * Multi-device Drive sync UI: the device list/reorder section, per-device row, and the setup dialog
 * + its choice row. Split out of Rows.kt to separate this self-contained cluster from the smaller
 * settings-row primitives there.
 */

/**
 * The synced-devices registry shown in the "Backup & sync" card: a drag-to-reorder list (same
 * gesture as the car-order list in Settings) where the TOP device is the primary — the source of
 * truth other devices adopt.
 */
@Composable
internal fun SyncDevicesSection(state: UiState, vm: AppViewModel) {
    if (state.syncDevices.isEmpty()) return
    SettingsGroup("Synced devices") { SyncDevicesContent(state, vm) }
}

/**
 * The inside of the "Synced devices" box: a hint, every phone (drag the top one to make it primary)
 * with its watch tucked underneath, and a warning when a device has gone quiet.
 */
@Composable
private fun SyncDevicesContent(state: UiState, vm: AppViewModel) {
    val devices = state.syncDevices
    var renaming by remember { mutableStateOf(false) }

    // Order the list so the primary is on top (that's the invariant the drag gesture maintains);
    // everyone else falls in by most-recently-seen. Dragging a device to the top sets it primary,
    // after which this same sort keeps it there — so the visual order and the "primary" concept
    // stay in lockstep.
    val ordered = remember(devices, state.syncPrimaryId) {
        devices.sortedWith(
            compareByDescending<SyncMerge.SyncDevice> { it.id == state.syncPrimaryId }
                .thenByDescending { it.lastSeenMs },
        )
    }

    val primaryName = ordered.firstOrNull { it.id == state.syncPrimaryId }?.name?.ifBlank { null }
    val phones = remember(ordered) { ordered.filter { !it.isWatch } }
    val registryWatches = remember(ordered) { ordered.filter { it.isWatch } }
    // Two lines, not one. The count and who is primary is a status readout; how to change it is an
    // instruction. They were a single 20-word sentence, which made the status half read as a command
    // and buried the one thing worth knowing -- that the top phone is what every other device adopts.
    BodySmallText(
        buildString {
            append("${phones.size} phone${if (phones.size == 1) "" else "s"}")
            primaryName?.let { append(" · $it is primary") }
        },
    )
    BodySmallText("Drag to reorder. The top phone is the one every other device adopts.")
    val watchHost = phones.firstOrNull { it.id == state.syncPrimaryId } ?: phones.firstOrNull()
    val context = LocalContext.current
    val liveWatch by WatchPresence.watch.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            WatchPresence.refresh(context)
            delay(WATCH_REFRESH_MS)
        }
    }
    var showSetupWatch by remember { mutableStateOf(false) }

    ReorderColumn(
        items = phones,
        keyOf = { it.id },
        // Dropped in a new order → the new TOP device becomes primary. setPrimaryDevice persists it
        // + triggers a sync so every device converges on the choice.
        onReorder = { reordered -> reordered.firstOrNull()?.let { vm.setPrimaryDevice(it.id) } },
        spacing = GapRow,
    ) { device, itemDragHandle, dragging ->
        // A Column, not loose children: a ReorderColumn item slot stacks its children on top of
        // each other, which drew the watch row and "Set up watch" straight over the phone row.
        //
        // spacedBy(GapRow), not a Spacer between each pair: that is how every other stack of rows in
        // this app is spaced (SettingsGroup, the seat list, the PIN form), and it was six Spacers here.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            val isSelf = device.id == state.thisDeviceId
            SyncDeviceRow(
                device = device,
                isSelf = isSelf,
                isPrimary = device.id == state.syncPrimaryId,
                dragging = dragging,
                modifier = itemDragHandle,
                onRename = { renaming = true },
                onRemove = { vm.removeSyncedDevice(device.id) },
            )
            // Drawn in the same item slot, after the phone row -- not reorderable items themselves.
            val live = liveWatch.takeIf { isSelf }
            val registered = registryWatches.takeIf { device.id == watchHost?.id && live == null }.orEmpty()
            live?.let { watch ->
                WearCompanionRow(
                    name = watch.name,
                    detail = "Connected to this phone",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = null,
                )
                CompanionActionRow(
                    label = "Sign watch in",
                    caption = "Lets it run your car without this phone",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onClick = { WatchSignIn.offer(context) },
                )
            }
            registered.forEach { watch ->
                WearCompanionRow(
                    name = watch.name,
                    detail = listOf(watch.model, relativeLabel(watch.lastSeenMs))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = { vm.removeSyncedDevice(watch.id) },
                )
            }
            if (isSelf && live == null && registered.isEmpty()) {
                // Under THIS phone on purpose: setting a watch up connects it to the device you are
                // holding, and the placement says so.
                CompanionActionRow(
                    label = "Set up watch",
                    caption = "Connects to ${device.name.ifBlank { "this phone" }}",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onClick = { showSetupWatch = true },
                )
            }
            // The watch PIN gate: nothing to lock unless a watch is present.
            if (isSelf && (live != null || registered.isNotEmpty())) {
                SettingsSegmentedRow(
                    label = "Get the PIN from your watch",
                    options = listOf(
                        SegmentOption(WatchLockTiming.OFF.wireKey, "Off", null),
                        SegmentOption(WatchLockTiming.OPEN.wireKey, "Opening", null),
                        SegmentOption(WatchLockTiming.COMMANDS.wireKey, "Commands", null),
                        SegmentOption(WatchLockTiming.BOTH.wireKey, "Both", null),
                    ),
                    selectedKey = state.watchLockTiming.wireKey,
                    description = "Uses your app PIN.",
                    onSelect = { vm.setWatchLockTiming(WatchLockTiming.fromWire(it)) },
                    modifier = Modifier.padding(start = CompanionIndent),
                )
            }
        }
    }
    WatchSignInDialog()
    if (showSetupWatch) {
        SetupWatchDialog(
            phoneName = phones.firstOrNull { it.id == state.thisDeviceId }?.name?.ifBlank { null } ?: "this phone",
            onDismiss = { showSetupWatch = false },
        )
    }

    if (renaming) {
        var draft by remember { mutableStateOf(state.syncDeviceName) }
        val scheme = MaterialTheme.colorScheme
        // Stacked full-width buttons.
        GlassAlertDialog(
            onDismissRequest = { renaming = false },
            icon = Icons.Filled.Smartphone,
            title = "Rename this device",
            text = {
                BodyMediumText(
                    "Shown in the devices list on all your synced devices.",
                    color = scheme.onSurfaceVariant,
                )
                // "Name this device" is an ordinary form field, and it was the only one of those
                // wearing the credential look.
                BlooTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { RevealText("Device name") },
                    placeholder = { RevealText(Build.MODEL ?: "This device") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            buttons = {
                MorphTextButton(
                    "Save",
                    onClick = {
                        if (draft.isNotBlank()) vm.renameThisDevice(draft)
                        renaming = false
                    },
                    enabled = draft.isNotBlank(),
                    emphasis = ButtonEmphasis.Confirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                SafeMorphTextButton(
                    "Cancel",
                    onClick = { renaming = false },
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = ButtonEmphasis.Deny,
                )
            },
        )
    }
}

/** How far a companion is inset under its phone row. */
private val CompanionIndent = 22.dp

private const val WATCH_REFRESH_MS = 6_000L
