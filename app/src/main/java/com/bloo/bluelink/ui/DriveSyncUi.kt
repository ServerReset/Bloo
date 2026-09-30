@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.bloo.uicommon.dropShadow
import com.bloo.uicommon.rememberConfirmArm
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import com.bloo.uicommon.ReorderColumn
import com.bloo.bluelink.data.platform
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
    val devices = state.syncDevices
    if (devices.isEmpty()) return
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

    Spacer(Modifier.height(GapGroup))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        TitleSmallText("Synced devices")
    }
    Spacer(Modifier.height(2.dp))
    BodySmallText(
        "Drag to reorder. The top device is primary. A paired watch rides under its phone.",
    )
    Spacer(Modifier.height(GapRow))

    // A watch is a COMPANION, not a peer: it never appears as a reorderable, primary-eligible
    // row. It rides UNDER the phone that owns it. The watch that is connected over the Data Layer
    // belongs to THIS phone (the registry can't say -- the watch never writes to Drive); any
    // watch the registry does carry rides under the primary phone.
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
                SetupWatchRow(
                    phoneName = device.name.ifBlank { "this phone" },
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
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Device name") },
                    placeholder = { Text(Build.MODEL ?: "This device") },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            buttons = {
                val saveRenameSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = saveRenameSource,
                    enabled = draft.isNotBlank(),
                ) {
                    // MorphTextButton, not a hand-rolled MorphButton{Text(...)} -- that Text had
                    // no `style`. Primary colours stand in for the old `active = true`.
                    MorphTextButton(
                        "Save",
                        onClick = {
                            if (draft.isNotBlank()) vm.renameThisDevice(draft)
                            renaming = false
                        },
                        interactionSource = saveRenameSource,
                        enabled = draft.isNotBlank(),
                        emphasis = ButtonEmphasis.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
    val shape = RoundedCornerShape(18.dp)
    val container =
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        // glassTint (GlassChrome.kt), not surfaceContainerHigh -- the same shared
        // neutral fill every other glass surface in the app uses now, no exceptions.
        else glassTint(blurred = false)
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

/**
 * A Wear OS companion shown UNDER the phone it belongs to, not as a peer row. Deliberately not a
 * [SyncDeviceRow]: a watch has no drag handle and no star, because it depends on its phone.
 * [onRemove] is null for a live Data Layer watch -- pairing is managed by Wear OS, not by Bloo.
 */
@Composable
private fun WearCompanionRow(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glassTint(blurred = false))
            .padding(horizontal = 10.dp, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.Watch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.ifBlank { "Watch" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text("Companion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) {
            val confirmRemove = rememberConfirmArm()
            MorphIconButton(onClick = { if (confirmRemove.armed) onRemove() else confirmRemove.arm() }) {
                Icon(
                    AppIcons.Close,
                    contentDescription = if (confirmRemove.armed) "Tap again to remove ${name.ifBlank { "this watch" }}"
                    else "Remove ${name.ifBlank { "this watch" }} from synced devices",
                    tint = if (confirmRemove.armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The empty-companion slot: a "Set up watch" action hanging off this phone's row. */
@Composable
private fun SetupWatchRow(phoneName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            SafeMorphTextButton("Set up watch", onClick = onClick, icon = Icons.Filled.Watch)
            LabelSmallText("Connects to $phoneName")
        }
    }
}

/**
 * "Set up watch". First install remains a platform constraint: Android/Wear OS does not let a
 * phone silently install an APK onto a watch unless the app is published through Play. Once
 * Bloo for Wear is installed and paired, this pushes updates directly over the Data Layer.
 */
@Composable
private fun SetupWatchDialog(phoneName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val watch by com.bloo.bluelink.wear.WatchPresence.watch.collectAsStateWithLifecycle()
    var update by remember { mutableStateOf<com.bloo.bluelink.data.WorkflowRun?>(null) }
    LaunchedEffect(Unit) {
        update = com.bloo.bluelink.data.UpdateApi.fetchLatestSuccessfulRun(
            com.bloo.bluelink.data.UpdateApi.DEFAULT_BRANCH,
        )
    }
    val apkUrl = update?.watchApkUrl
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Watch,
        title = "Set up watch",
        text = {
            if (watch != null) {
                BodyMediumText("${watch?.name?.ifBlank { "Your watch" }} is connected to $phoneName. Bloo can push the watch app and future updates straight to it.")
            } else {
                BodyMediumText("Install Bloo for Wear from Google Play on your watch and open it once. It will connect to $phoneName.")
            }
        },
        buttons = {
            if (watch != null && apkUrl != null) {
                MorphActionButton(
                    label = "Send to watch",
                    icon = Icons.Filled.Watch,
                    onClick = {
                        com.bloo.bluelink.wear.PhoneWatchSyncService.pushWatchApk(context, apkUrl)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (watch == null) {
                MorphActionButton(
                    label = "Open Play Store",
                    icon = Icons.Filled.Shop,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "market://details?id=com.bloo.bluelink.wear".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MorphTextButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        },
    )
}

/**
 * The Google Drive sync setup dialog, shared between onboarding and the
 * Settings "Backup & sync" card so both look and behave identically (they
 * used to be two separately hand-rolled dialogs -- one BlooDialog, one plain
 * AlertDialog with an awkward confirmButton/dismissButton split -- that had
 * drifted out of sync with each other). Two tappable choice cards instead of
 * three same-weight text buttons, so "start fresh" vs. "join an existing
 * sync" reads as an actual decision rather than an arbitrary button order.
 */
@Composable
internal fun DriveSyncSetupDialog(
    onDismissRequest: () -> Unit,
    onSaveToDrive: () -> Unit,
    onOpenFromDrive: () -> Unit,
    // True when this device has synced before / knows about other devices. In
    // that case "Save to Drive" would create a SEPARATE new file (Google Drive
    // allows duplicate names) — the exact trap that leaves two devices on two
    // files that never converge — so it's gated behind a warning + confirm, and
    // "Open from Drive" (join the existing file) is emphasized as the right path.
    hasExistingSync: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Local warning step: first tap of "Save to Drive" while already synced flips
    // this on and swaps the row for a warning + explicit "Create anyway"; the
    // recommended action is to join the existing file instead.
    var warnNewFile by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = Icons.Filled.Cloud,
        title = "Google Drive sync",
        text = {
            BodyMediumText(
                "Keep settings in sync across devices with one Drive file.",
                color = scheme.onSurfaceVariant,
            )
            // Join first — it's the correct choice when another device already set
            // sync up, and making it the emphasized (active) card steers people away
            // from accidentally creating a second file.
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen,
                title = "Open from Drive",
                subtitle = "Join the file another device set up to share settings.",
                emphasized = hasExistingSync,
                onClick = onOpenFromDrive,
            )
            if (warnNewFile) {
                // The trap, spelled out, with the safe alternative one tap away.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(StandardShape)
                        .background(scheme.errorContainer.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    Text(
                        "Starts a new Drive file. Your devices stop sharing settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                    )
                    MorphButton(
                        onClick = onSaveToDrive,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) { Text("Create a new file anyway", style = ButtonLabelStyle, color = scheme.error) }
                }
            } else {
                DriveSyncChoiceRow(
                    icon = Icons.Filled.CreateNewFolder,
                    title = "Save to Drive",
                    subtitle = "Start fresh with this device's settings.",
                    onClick = { if (hasExistingSync) warnNewFile = true else onSaveToDrive() },
                )
            }
        },
        buttons = {
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
internal fun DriveSyncChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    // Highlights this choice as the recommended one (filled/active MorphButton).
    emphasized: Boolean = false,
) {
    // The app's standard button component (MorphButton), not a bespoke
    // Surface row -- so this dialog's actions look and feel like every other
    // button in the app instead of a one-off.
    MorphButton(
        onClick = onClick,
        active = emphasized,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Spacer(Modifier.width(ButtonIconGap))
        Column(horizontalAlignment = Alignment.Start) {
            TitleSmallText(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
