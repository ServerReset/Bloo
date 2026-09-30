@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/** "Backup & sync" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun BackupSyncCardContent(
    state: UiState,
    vm: AppViewModel,
    context: Context,
    advanced: Boolean,
) {
            // Hoisted so the collapsed row's right side can show the same at-a-glance state
            // the in-card header does (see SettingsCard's `status`).
            val driveConfigured = state.syncUri != null
            val driveIcon = when {
                driveConfigured && state.syncError != null -> Icons.Filled.CloudOff
                driveConfigured -> Icons.Filled.CloudDone
                else -> Icons.Filled.CloudSync
            }
            val driveTint = when {
                driveConfigured && state.syncError != null -> MaterialTheme.colorScheme.error
                driveConfigured -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val driveStatus = when {
                !driveConfigured -> "Not set up"
                state.syncError != null -> "Sync failed"
                else -> com.bloo.bluelink.data.relativeLabel(state.lastSyncMs).takeIf { it.isNotBlank() }?.let { "Synced $it" } ?: "Active"
            }
            SettingsCard("Backup & sync", Icons.Filled.CloudSync, vm, status = driveStatus) {
                var showDriveDialog by remember { mutableStateOf(false) }
                val settingsImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri -> uri?.let { vm.importSettings(context, it) } }
                val driveSaveLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri -> uri?.let { vm.setSyncUri(it) } }
                val driveOpenLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }

                // At-a-glance status header: the state icon in a tonal circle
                // (matching the app's card-header language) + a bold title and a
                // colour-coded one-line state. Icon included: driveIcon itself
                // changes (cloud-sync/cloud-done/cloud-off) along with the tint,
                // so StatusHeaderRow's own icon crossfade covers it too.
                StatusHeaderRow(
                    icon = driveIcon,
                    tint = driveTint,
                    title = "Automatic Drive sync",
                    status = driveStatus,
                )
                Spacer(Modifier.height(GapGroup))
                if (showDriveDialog) {
                    DriveSyncSetupDialog(
                        onDismissRequest = { showDriveDialog = false },
                        onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
                        onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
                        // Already syncing, or aware of another device → creating a new
                        // file here would split the fleet across two files. Warn + steer
                        // to "Open from Drive".
                        hasExistingSync = state.syncUri != null || state.syncDevices.size > 1,
                    )
                }
                if (state.syncUri == null) {
                    // Not configured: one unmissable primary CTA, nothing else to
                    // read past. The old layout led with a paragraph explaining
                    // Drive sync and put setup in a quiet text button beside it.
                    //
                    // Sized like every other button in the app -- content width, standard
                    // padding -- not stretched to the card's own width. `active` still marks
                    // it as the primary action (same colour language "Stop" and "Install"
                    // use); fillMaxWidth on TOP of that was the oversized, one-off treatment
                    // reported from a real screenshot, not a second thing this control needs.
                    val setupSource = remember { MutableInteractionSource() }
                    MorphButton(
                        onClick = { showDriveDialog = true },
                        interactionSource = setupSource,
                        active = true,
                        expressive = true,
                    ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Set up auto-sync", pending = false) }
                } else {
                    // Configured: "Sync now" is THE daily control, so it leads —
                    // ahead of the device registry and the setup/teardown pair,
                    // which are both occasional by comparison.
                    val syncSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = syncSource,
                        enabled = true,
                    ) {
                        // Same fix as "Set up auto-sync" a few lines up: content width, not
                        // stretched to the card.
                        MorphButton(
                            onClick = { vm.syncNow() },
                            interactionSource = syncSource,
                            active = true,
                        ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Sync now", pending = false) }
                    }
                    // A live failure is the one fact that never hides behind the
                    // diagnostics disclosure below — if sync is broken, say so here.
                    state.syncError?.let { err ->
                        Spacer(Modifier.height(GapRow))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = GapRow),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            BodySmallText(
                                err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // The synced-devices registry: a drag-to-reorder list where the
                    // TOP device is primary (source of truth). See SyncDevicesSection.
                    SyncDevicesSection(state = state, vm = vm)
                    Spacer(Modifier.height(GapGroup))
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("wifi", "Wi-Fi only", null),
                            SegmentOption("any", "Any network", null),
                        ),
                        selectedKey = if (state.syncWifiOnly) "wifi" else "any",
                        onSelect = { vm.setSyncWifiOnly(it == "wifi") },
                    )
                    Spacer(Modifier.height(GapRow))
                    // equalWidths: this row sits directly under the Wi-Fi only/Any network
                    // segmented control, which splits its full width evenly -- left otherwise,
                    // the two buttons packed to their own content width and read as a mismatched
                    // pair next to the evenly-split control right above them.
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp, equalWidths = true) {
                        SafeMorphTextButton(
                            "Change Drive file",
                            onClick = { showDriveDialog = true },
                        )
                        SafeMorphTextButton(
                            "Disable",
                            onClick = { vm.clearSyncUri() },
                        )
                    }
                    // Troubleshooting tools, not daily controls: the last-synced
                    // stamp (already summarised in the header above), the file
                    // biometric, and the two repair actions all fold away by
                    // default so the card stops reading as a wall of equal pills.
                    Spacer(Modifier.height(GapRow))
                    var showSyncDiagnostics by rememberSaveable { mutableStateOf(false) }
                    val diagnosticsSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = diagnosticsSource,
                        enabled = true,
                    ) {
                        // Content-width, matching the equivalent "Troubleshooting steps" toggle
                        // in the Notifications card -- this one was still stretched full width.
                        MorphTextButton(
                            if (showSyncDiagnostics) "Hide diagnostics" else "Diagnostics",
                            interactionSource = diagnosticsSource,
                            onClick = { showSyncDiagnostics = !showSyncDiagnostics },
                        )
                    }
                    AnimatedVisibility(
                        visible = showSyncDiagnostics,
                        enter = expandEnterSized(Alignment.Bottom),
                        exit = expandExitSized(Alignment.Bottom),
                    ) {
                        Column {
                            Spacer(Modifier.height(GapRow))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(StandardShape)
                                    // glassTint, not a one-off surfaceContainerHighest/0.5f literal --
                                    // the same shared neutral fill every other glass surface in the
                                    // app uses, not a fourth slightly-different copy of the same idea.
                                    .background(glassTint(blurred = false))
                                    .padding(horizontal = 14.dp, vertical = GapGroup),
                                verticalArrangement = Arrangement.spacedBy(GapRow),
                            ) {
                                val lastSyncLabel = com.bloo.bluelink.data.relativeLabel(state.lastSyncMs)
                                StatusRow("Last synced", if (lastSyncLabel.isNotBlank()) lastSyncLabel else "Never")
                                // File-identity fingerprint: two phones truly on the SAME Drive
                                // file show the SAME code. If they differ, they picked different
                                // files (Drive allows duplicate names) — the #1 reason sync
                                // doesn't converge, now checkable at a glance across phones.
                                state.syncFileFingerprint?.let { fp ->
                                    StatusRow("File ID", fp, valueMono = true)
                                }
                            }
                            Spacer(Modifier.height(GapRow))
                            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                                // Non-destructive real-provider round-trip so the user can confirm
                                // sync actually works.
                                SafeMorphTextButton(
                                    "Test sync",
                                    onClick = { vm.testSync() }
                                )
                                // "Pull from primary now": force this device to adopt the
                                // primary's full settings — only when a primary exists AND it
                                // isn't this device (pulling from yourself is a no-op). When not
                                // shown, Test sync spans the row on its own.
                                if (state.syncPrimaryId != null && state.syncPrimaryId != state.thisDeviceId) {
                                    SafeMorphTextButton(
                                        "Pull from primary",
                                        onClick = { vm.pullFromPrimary() }
                                    )
                                }
                            }
                        }
                    }
                }

                // Advanced-only: a one-shot export/import file is a power-user
                // fallback (moving settings by hand, a local backup outside
                // Drive) next to the always-on automatic sync above, which is
                // what most people actually want and shouldn't be buried.
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 1), enter = expandEnterSized(), exit = expandExitSized()) {
                  Column {
                    Spacer(Modifier.height(GapGroup))
                    SectionDivider()
                    Spacer(Modifier.height(GapGroup))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemedIcon(Icons.Filled.Description, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Manual backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(GapRow))
                    BodySmallText(
                        "A one-time snapshot file. Credentials are never included.",
                    )
                    Spacer(Modifier.height(GapRow))
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                        SafeMorphTextButton(
                            "Export",
                            onClick = { vm.exportSettings(context) },
                        )
                        SafeMorphTextButton(
                            "Restore",
                            onClick = { settingsImportLauncher.launch("application/json") },
                        )
                    }
                  }
                }
            }
}
