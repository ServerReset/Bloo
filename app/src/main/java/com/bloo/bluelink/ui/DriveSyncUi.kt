@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.ReorderColumn
import com.bloo.bluelink.data.removeSyncedDevice
import com.bloo.bluelink.data.setPrimaryDevice
import com.bloo.bluelink.data.setWatchLockTiming
import com.bloo.bluelink.data.syncDeviceName
import com.bloo.bluelink.data.watchLockTiming

/**
 * Multi-device Drive sync UI: the device list/reorder section, per-device row,
 * and the setup dialog + its choice row. Split out of Rows.kt to separate this
 * self-contained cluster from the smaller settings-row primitives there.
 */

/**
 * The synced-devices registry shown in the "Backup & sync" card: a
 * drag-to-reorder list (same gesture as the car-order list in Settings) where
 * the TOP device is the primary — the source of truth other devices adopt.
 * Dragging a device to the top makes it primary. Each row shows a drag handle, a
 * device icon (★ on the primary), its name (with a "This device" marker for
 * self + a rename affordance), model, and how long ago it last synced. Renders
 * nothing until the first sync populates the registry.
 */
@Composable
internal fun SyncDevicesSection(state: UiState, vm: AppViewModel) {
    if (state.syncDevices.isEmpty()) return
    SettingsGroup("Synced devices") { SyncDevicesContent(state, vm) }
}

/** The inside of the "Synced devices" box: a hint, every phone (drag the top one to make it primary)
 *  with its watch tucked underneath, and a warning when a device has gone quiet. */
@Composable
private fun SyncDevicesContent(state: UiState, vm: AppViewModel) {
    val devices = state.syncDevices
    var renaming by remember { mutableStateOf(false) }

    // Order the list so the primary is on top (that's the invariant the drag
    // gesture maintains); everyone else falls in by most-recently-seen. Dragging
    // a device to the top sets it primary, after which this same sort keeps it
    // there — so the visual order and the "primary" concept stay in lockstep.
    val ordered = remember(devices, state.syncPrimaryId) {
        devices.sortedWith(
            compareByDescending<com.bloo.bluelink.data.SyncMerge.SyncDevice> { it.id == state.syncPrimaryId }
                .thenByDescending { it.lastSeenMs },
        )
    }

    BodySmallText("Drag to reorder. The top device is primary. A paired watch rides under its phone.")
    val phones = ordered.filter { !it.isWatch }
    val registryWatches = ordered.filter { it.isWatch }
    val watchHost = phones.firstOrNull { it.id == state.syncPrimaryId } ?: phones.firstOrNull()
    val context = LocalContext.current
    val liveWatch by com.bloo.bluelink.wear.WatchPresence.watch.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            com.bloo.bluelink.wear.WatchPresence.refresh(context)
            delay(WATCH_REFRESH_MS)
        }
    }
    var showSetupWatch by remember { mutableStateOf(false) }

    ReorderColumn(
        items = phones,
        keyOf = { it.id },
        // Dropped in a new order → the new TOP device becomes primary. setPrimaryDevice
        // persists it + triggers a sync so every device converges on the choice.
        onReorder = { reordered -> reordered.firstOrNull()?.let { vm.setPrimaryDevice(it.id) } },
        spacing = 8.dp,
    ) { device, itemDragHandle, dragging ->
        // A Column, not loose children: a ReorderColumn item slot stacks its children on top of
        // each other, which drew the watch row and "Set up watch" straight over the phone row.
        Column(Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.height(6.dp))
                WearCompanionRow(
                    name = watch.name,
                    detail = "Connected to this phone",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = null,
                )
                Spacer(Modifier.height(6.dp))
                CompanionActionRow(
                    label = "Sign watch in",
                    caption = "Lets it run your car without this phone",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onClick = { com.bloo.bluelink.wear.WatchSignIn.offer(context) },
                )
            }
            registered.forEach { watch ->
                Spacer(Modifier.height(6.dp))
                WearCompanionRow(
                    name = watch.name,
                    detail = listOf(watch.model, com.bloo.bluelink.data.relativeLabel(watch.lastSeenMs))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(start = CompanionIndent),
                    onRemove = { vm.removeSyncedDevice(watch.id) },
                )
            }
            if (isSelf && live == null && registered.isEmpty()) {
                // Under THIS phone on purpose: setting a watch up connects it to the device you
                // are holding, and the placement says so.
                Spacer(Modifier.height(6.dp))
                CompanionActionRow(
                    label = "Set up watch",
                    caption = "Connects to ${device.name.ifBlank { "this phone" }}",
                    modifier = Modifier.padding(start = CompanionIndent),
                    onClick = { showSetupWatch = true },
                )
            }
            // The watch PIN gate: nothing to lock unless a watch is present.
            if (isSelf && (live != null || registered.isNotEmpty())) {
                Spacer(Modifier.height(GapRow))
                Box(Modifier.padding(start = CompanionIndent)) {
                    SettingsSegmentedRow(
                        label = "Ask the watch for my PIN",
                        options = listOf(
                            SegmentOption(com.bloo.bluelink.data.WatchLockTiming.OFF.wireKey, "Off", null),
                            SegmentOption(com.bloo.bluelink.data.WatchLockTiming.OPEN.wireKey, "Opening", null),
                            SegmentOption(com.bloo.bluelink.data.WatchLockTiming.COMMANDS.wireKey, "Commands", null),
                            SegmentOption(com.bloo.bluelink.data.WatchLockTiming.BOTH.wireKey, "Both", null),
                        ),
                        selectedKey = state.watchLockTiming.wireKey,
                        description = "Uses your app PIN.",
                        onSelect = { vm.setWatchLockTiming(com.bloo.bluelink.data.WatchLockTiming.fromWire(it)) },
                    )
                }
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

    // Advisory: if a peer hasn't checked in for a while but this device just
    // synced, it likely drifted onto a DIFFERENT Drive file (a device can't see
    // another's file directly — the File ID at the top is the real cross-check).
    val now = System.currentTimeMillis()
    val stalePeer = devices.any { it.id != state.thisDeviceId && it.lastSeenMs > 0 && now - it.lastSeenMs > STALE_DEVICE_MS }
    if (stalePeer) {
        Spacer(Modifier.height(GapRow))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "A device hasn't synced recently. It may be on a different Drive file. Reconnect via Change Drive file → Open from Drive.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(state.syncDeviceName) }
        val scheme = MaterialTheme.colorScheme
        // Standardized on the shared GlassAlertDialog shell (was the legacy
        // BlooDialog, now removed). Stacked full-width buttons.
        GlassAlertDialog(
            onDismissRequest = { renaming = false },
            icon = Icons.Filled.Smartphone,
            title = "Rename this device",
            text = {
                BodyMediumText(
                    "Shown in the devices list on all your synced devices.",
                    color = scheme.onSurfaceVariant,
                )
                // FieldShape with the DEFAULT outlined colours -- the same field
                // ClimatePebble's "Save preset" and the palette editor's "Name" draw
                // inside this identical glass shell. borderlessFieldColors() (which
                // this used to pass) is the credential-over-glass treatment: the lock
                // screen, onboarding's PIN pair and the PIN dialog, where the field
                // sits on the aurora and needs its own opaque fill. "Name this device"
                // is an ordinary form field, and it was the only one of those wearing
                // the credential look.
                BlooTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Device name") },
                    placeholder = { Text(Build.MODEL ?: "This device") },
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
                    emphasis = ButtonEmphasis.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                SafeMorphTextButton(
                    "Cancel",
                    onClick = { renaming = false },
                    modifier = Modifier.fillMaxWidth()
                )
            },
        )
    }
}


/** One row in the drag-to-reorder [SyncDevicesSection]: a frosted card with a
 *  drag handle, a device icon (★ when primary), the device name (+ a "This
 *  device" chip and a rename button for self), model, and last-seen. Styled to
 *  match the card language of the rest of Settings; lifts slightly while dragged. */
@Composable
internal fun SyncDeviceRow(
    device: com.bloo.bluelink.data.SyncMerge.SyncDevice,
    isSelf: Boolean,
    isPrimary: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onRename: () -> Unit,
    /** Kick this device out of the registry -- never offered for [isSelf] (see
     *  SettingsStore.removeSyncedDevice's own doc for why this can't remove
     *  yourself: it's a courtesy prune of a stale/unrecognised peer, not a way
     *  to leave sync on the device you're actually holding). */
    onRemove: () -> Unit,
) {
    val shape = StandardShape
    val container =
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        // glassTint (GlassChrome.kt), not surfaceContainerHigh -- the same shared
        // neutral fill every other glass surface in the app uses now, no exceptions.
        else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            // The drag-lift shadow, at a weight the current theme can carry. It used
            // to be dropShadow's bare default colour (0.38-alpha black), and this row
            // is the worst case for that: its fill is glassTint(blurred = false),
            // which in light mode is surfaceContainer at 0.12 alpha -- so the lifted
            // row was a barely-there pale film with a hard black silhouette under it,
            // i.e. a black smudge following the finger rather than a card lifting off
            // the page. Same split as glassDropShadow (GlassChrome.kt), where the same
            // root cause was finally tracked down for every floating GlassSurface.
            .then(
                if (dragging) {
                    Modifier.dropShadow(
                        shape,
                        color = Color.Black.copy(alpha = if (appIsDarkTheme()) 0.38f else 0.12f),
                        blurRadius = 14.dp,
                        offsetY = 4.dp,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle — the grab affordance, same idiom as the car-order list.
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            if (isPrimary) Icons.Filled.Star else Icons.Filled.Smartphone,
            contentDescription = if (isPrimary) "Primary device" else null,
            tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name.ifBlank { "Unnamed device" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPrimary || isSelf) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelf) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "This device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val seen = com.bloo.bluelink.data.relativeLabel(device.lastSeenMs)
            val sub = buildString {
                if (isPrimary) append("Primary")
                val model = device.model.takeIf { it.isNotBlank() }
                if (isPrimary && model != null) append(" · ")
                if (model != null) append(model)
                if (seen.isNotBlank()) { if (isNotEmpty()) append(" · "); append(seen) }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelf) {
            MorphIconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this device", modifier = Modifier.size(18.dp))
            }
        } else {
            // Tap-again-to-confirm, same 4s-auto-reset pattern as every other
            // destructive action in the app (account sign-out, palette/preset
            // delete) -- one accidental tap on a device you use daily should
            // never kick it, but a real kick shouldn't need a whole dialog either.
            val confirmRemove = rememberConfirmArm()
            MorphIconButton(
                onClick = {
                    if (confirmRemove.armed) onRemove() else confirmRemove.arm()
                },
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = if (confirmRemove.armed) {
                        "Tap again to remove ${device.name.ifBlank { "this device" }}"
                    } else {
                        "Remove ${device.name.ifBlank { "this device" }} from synced devices"
                    },
                    tint = if (confirmRemove.armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


/** How far a companion is inset under its phone row. */
private val CompanionIndent = 22.dp

private const val WATCH_REFRESH_MS = 6_000L
