package com.bloo.bluelink.ui

import com.bloo.uicommon.segmentsOf
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
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
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Multi-device Drive sync UI: the device list/reorder section, per-device row, and the setup dialog
 * + its choice row. Split out of Rows.kt to separate this self-contained cluster from the smaller
 * settings-row primitives there.
 */

/**
 * The synced-devices registry shown in the "Backup & sync" card: a drag-to-reorder list (same
 * gesture as the car-order list in Settings) where the TOP device is the primary - the source of
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

    // The one order every device shows: the primary on top, the rest in the order the shared file keeps them (see
    // SyncMerge.orderDevices). Not by last seen, which differs from phone to phone.
    val ordered = remember(devices, state.syncPrimaryId) { SyncMerge.orderDevices(devices, state.syncPrimaryId) }

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
    BodySmallText("Drag to reorder. The top phone is primary.")
    val context = LocalContext.current
    val liveWatch by WatchPresence.watch.collectAsStateWithLifecycle()
    val signInState by WatchSignIn.state.collectAsStateWithLifecycle()
    // Only while the app is on screen (a backgrounded Settings keeps its composition, and the poll would keep asking the watch).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                WatchPresence.refresh(context)
                delay(WATCH_REFRESH_MS)
            }
        }
    }
    var showSetupWatch by remember { mutableStateOf(false) }
    // The one device or watch the wearer is being asked to remove (the dialog below says what that does).
    var removal by remember { mutableStateOf<Removal?>(null) }
    // Read again when the sign-in moves on, so the sign-in button goes away the moment the watch acknowledges it.
    val signedIn = remember(liveWatch, signInState) { liveWatch?.let { WatchSignIn.isSignedIn(context, it.id) } ?: false }

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
            // The watches attached to THIS phone, as the shared registry holds them (a watch written before it had a phone sits
            // under the primary). This phone's own live watch is drawn from the connection below, not twice.
            val attached = registryWatches.filter { w ->
                (w.hostId.ifBlank { state.syncPrimaryId }) == device.id && !(isSelf && isConnectedWatch(liveWatch, w))
            }
            SyncDeviceRow(
                device = device,
                isSelf = isSelf,
                isPrimary = device.id == state.syncPrimaryId,
                dragging = dragging,
                modifier = itemDragHandle,
                onRename = { renaming = true },
                onRemove = { removal = Removal(device.id, device.name, watch = false) },
            )
            // Drawn in the same item slot, after the phone row -- not reorderable items themselves.
            val live = liveWatch.takeIf { isSelf }
            val registered = attached
            live?.let { watch ->
                WearCompanionRow(
                    name = watch.name,
                    detail = if (signedIn) "Signed in · runs your car on its own" else "Connected · not signed in yet",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = null,
                    // Only while there is something to do: a watch that is already signed in has no sign-in button.
                    action = if (signedIn) null else {
                        {
                            MorphTextButton(
                                "Sign watch in",
                                onClick = { WatchSignIn.offer(context) },
                                icon = Icons.Filled.Watch,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SecondaryText("Lets it run your car without this phone")
                        }
                    },
                )
            }
            registered.forEach { watch ->
                WearCompanionRow(
                    name = watch.name,
                    detail = listOf(watch.model, relativeLabel(watch.lastSeenMs))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = { removal = Removal(watch.id, watch.name, watch = true) },
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
                    options = segmentsOf(WatchLockTiming.OFF.wireKey to "Off", WatchLockTiming.OPEN.wireKey to "Opening", WatchLockTiming.COMMANDS.wireKey to "Commands", WatchLockTiming.BOTH.wireKey to "Both"),
                    selectedKey = state.watchLockTiming.wireKey,
                    description = "Uses your app PIN.",
                    onSelect = { vm.setWatchLockTiming(WatchLockTiming.fromWire(it)) },
                    modifier = Modifier.padding(start = CompanionIndent),
                )
            }
        }
    }
    WatchSignInDialog()
    removal?.let { r ->
        GlassAlertDialog(
            onDismissRequest = { removal = null },
            icon = if (r.watch) Icons.Filled.Watch else Icons.Filled.Smartphone,
            title = "Remove ${r.name.ifBlank { if (r.watch) "this watch" else "this phone" }}?",
            text = {
                BodyMediumText(
                    "It leaves the list on every synced device. If it syncs again it comes back: Bloo never locks a device out of your sync.",
                )
            },
            buttons = {
                ConfirmCancelButtons(
                    "Remove",
                    onConfirm = { vm.removeSyncedDevice(r.id); removal = null },
                    onCancel = { removal = null },
                    confirmEmphasis = ButtonEmphasis.Deny,
                    cancel = "Keep",
                )
            },
        )
    }
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
                BodyMediumText("Shown in the devices list on all your synced devices.", color = scheme.onSurfaceVariant)
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
                ConfirmCancelButtons(
                    "Save",
                    onConfirm = {
                        if (draft.isNotBlank()) vm.renameThisDevice(draft)
                        renaming = false
                    },
                    onCancel = { renaming = false },
                    enabled = draft.isNotBlank(),
                )
            },
        )
    }
}

/** A device or watch the wearer has asked to remove, until they confirm or keep it. */
private data class Removal(val id: String, val name: String, val watch: Boolean)

/** How far a companion is inset under its phone row. */
private val CompanionIndent = 14.dp

private const val WATCH_REFRESH_MS = 6_000L

/** Whether a registry watch entry is the watch this phone is connected to right now. */
private fun isConnectedWatch(connected: WatchPresence.ConnectedWatch?, entry: SyncMerge.SyncDevice): Boolean =
    connected != null && entry.id == "watch:${connected.id}"
