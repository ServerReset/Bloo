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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.bloo.uicommon.rememberConfirmArm
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.lastSyncMs
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.setSyncWifiOnly
import com.bloo.bluelink.data.syncFileFingerprint
import com.bloo.bluelink.data.syncUri
import com.bloo.bluelink.data.syncWifiOnly

/**
 * The "Backup & sync" card. Two ways to keep a setup safe: automatic Drive sync across devices
 * (the everyday one) and a manual snapshot file (advanced). Everything here is either one of
 * those or the state of the first.
 */
@Composable
internal fun BackupSyncCardContent(
    state: UiState,
    vm: AppViewModel,
    context: Context,
    advanced: Boolean,
) {
    val configured = state.syncUri != null
    val failed = configured && state.syncError != null
    val status = when {
        !configured -> "Not set up"
        failed -> "Sync failed"
        else -> com.bloo.bluelink.data.relativeLabel(state.lastSyncMs).takeIf { it.isNotBlank() }?.let { "Synced $it" } ?: "Active"
    }
    var showDriveDialog by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.setSyncUri(it) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importSettingsAndSync(context, it) }
    }
    if (showDriveDialog) {
        DriveSyncSetupDialog(
            onDismissRequest = { showDriveDialog = false },
            onSaveToDrive = { showDriveDialog = false; saveLauncher.launch("bloo_settings.json") },
            onOpenFromDrive = { showDriveDialog = false; openLauncher.launch(arrayOf("application/json")) },
            // Already syncing, or aware of another device: a new file here would split the fleet
            // across two, so the dialog warns and steers to joining the existing one.
            hasExistingSync = configured || state.syncDevices.size > 1,
        )
    }

    SettingsCard("Backup & sync", Icons.Filled.CloudSync, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            StatusHeaderRow(
                icon = when {
                    failed -> Icons.Filled.CloudOff
                    configured -> Icons.Filled.CloudDone
                    else -> Icons.Filled.CloudSync
                },
                tint = when {
                    failed -> MaterialTheme.colorScheme.error
                    configured -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                title = "Drive sync",
                status = status,
            )

            if (!configured) {
                BodySmallText("Keep your settings, layout and car setup the same on every device you use, through one file in your Google Drive.")
                SafeMorphTextButton("Set up auto-sync", onClick = { showDriveDialog = true }, icon = Icons.Filled.CloudSync, emphasis = ButtonEmphasis.Primary)
            } else {
                SafeMorphTextButton("Sync now", onClick = { vm.syncNow() }, icon = Icons.Filled.CloudSync, emphasis = ButtonEmphasis.Primary)
                state.syncError?.let { SyncErrorBanner(it) }
                SyncDevicesSection(state = state, vm = vm)
                SettingsGroup("Sync over") {
                    MorphSegmented(
                        options = listOf(SegmentOption("wifi", "Wi-Fi only", null), SegmentOption("any", "Any network", null)),
                        selectedKey = if (state.syncWifiOnly) "wifi" else "any",
                        onSelect = { vm.setSyncWifiOnly(it == "wifi") },
                    )
                }
                val disable = rememberConfirmArm()
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    SafeMorphTextButton("Change Drive file", onClick = { showDriveDialog = true })
                    SafeMorphTextButton(
                        text = if (disable.armed) "Tap again to disable" else "Disable",
                        onClick = { if (disable.armed) vm.clearSyncUri() else disable.arm() },
                        emphasis = ButtonEmphasis.Destructive,
                    )
                }
                SyncDiagnostics(state, vm)
            }

            // A one-shot export/import file is a power-user fallback next to the always-on
            // sync above, which is what most people want and shouldn't be buried.
            AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 1), enter = expandEnterSized(), exit = expandExitSized()) {
                ManualBackup(vm, context)
            }
        }
    }
}

/** A live sync failure: the one fact that never hides behind the diagnostics disclosure. */
@Composable
private fun SyncErrorBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BodySmallText(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
    }
}

/** Troubleshooting, folded away: when it last synced, the file's fingerprint, and the repair actions. */
@Composable
private fun SyncDiagnostics(state: UiState, vm: AppViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    SafeMorphTextButton(if (open) "Hide diagnostics" else "Diagnostics", onClick = { open = !open })
    AnimatedVisibility(visible = open, enter = expandEnterSized(Alignment.Bottom), exit = expandExitSized(Alignment.Bottom)) {
        SettingsGroup("Diagnostics") {
            val lastSync = com.bloo.bluelink.data.relativeLabel(state.lastSyncMs)
            StatusRow("Last synced", lastSync.ifBlank { "Never" })
            // Two phones truly on the SAME Drive file show the same code. If they differ they picked
            // different files (Drive allows duplicate names), the usual reason sync doesn't converge.
            state.syncFileFingerprint?.let { StatusRow("File ID", it, valueMono = true) }
            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                // A non-destructive round trip through the real provider, to confirm sync works.
                SafeMorphTextButton("Test sync", onClick = { vm.testSync() })
                // Adopt the primary's full settings -- only when a primary exists and it isn't this
                // device (pulling from yourself is a no-op).
                if (state.syncPrimaryId != null && state.syncPrimaryId != state.thisDeviceId) {
                    SafeMorphTextButton("Pull from primary", onClick = { vm.pullFromPrimary() })
                }
            }
        }
    }
}

/** A one-time snapshot file, outside Drive. Credentials are never in it. */
@Composable
private fun ManualBackup(vm: AppViewModel, context: Context) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importSettings(context, it) }
    }
    SettingsGroup("Manual backup") {
        BodySmallText("A one-time snapshot file. Credentials are never included.")
        ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            SafeMorphTextButton("Export", onClick = { vm.exportSettings(context) })
            SafeMorphTextButton("Restore", onClick = { importLauncher.launch("application/json") })
        }
    }
}
