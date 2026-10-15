package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.UpdateApi

/**
 * The migration notice, shown only by the one-off bridge build (BuildConfig.LEGACY_SIGNING -- see the
 * `legacySigning` comment in app/build.gradle.kts).
 *
 * That build is signed with the debug keystore that every pre-Play Bloo release used. Android refuses
 * to install an update signed with a different key than the copy already on the device, so once the
 * app moved to the Play upload key, those installs could no longer take ANY new build -- which is
 * exactly what a user sees when they tap Update: the download completes and the installer says "App
 * not installed". Nothing on the app's side can change that; the only fix is a fresh install.
 *
 * So this build exists to say so, and it is the last one those installs will ever be offered. It
 * shows on every launch rather than once: it is the entire reason the build exists, and a dismissal
 * that outlives the process would leave someone on a copy that can never move forward with no way to
 * find out why. "Later" is still there -- the old copy keeps working as well as it ever did.
 *
 * The ordering of the steps is deliberate. Uninstalling takes the saved Bluelink sign-in, every
 * setting and the whole layout with it, so the export is put FIRST and has a button of its own:
 * telling someone to back up after they have already uninstalled is telling them too late.
 */
@Composable
internal fun LegacySigningNotice(onExport: () -> Unit) {
    // Per-process, so a dismissal is for this sitting only. Deliberately not persisted.
    var dismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val body = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (dismissed) return
    GlassAlertDialog(
        onDismissRequest = { dismissed = true },
        title = "Last update for this copy",
        icon = AppIcons.Warning,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                RevealText(
                    "Bloo is signed with a new key from here on. Android only installs an update " +
                        "signed with the same key as the copy already on the phone, so this build " +
                        "is the last one this copy can take — no update can get past that.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RevealText("To move to the current version:", style = MaterialTheme.typography.bodyMedium)
                RevealText(
                    "1. Export your settings. This is the one step that can't be undone later — " +
                        "uninstalling takes your cars, colours and layout with it. Tap \"Export backup\" " +
                        "below, or find it under Settings, Backup & sync.",
                    style = body,
                    color = muted,
                )
                RevealText(
                    "2. Uninstall Bloo.",
                    style = body,
                    color = muted,
                )
                RevealText(
                    "3. Download the current Bloo.apk from the GitHub releases page.",
                    style = body,
                    color = muted,
                )
                RevealText(
                    "4. Install it, sign in again, then restore the backup you exported in step 1.",
                    style = body,
                    color = muted,
                )
                RevealText(
                    "If you had Drive sync on, your settings come back on their own once you sign in. " +
                        "This copy keeps working as it is — it just won't be updated again.",
                    style = body,
                    color = muted,
                )
            }
        },
        buttons = {
            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = GapGroup) {
                SafeMorphTextButton(text = "Later", onClick = { dismissed = true })
                MorphActionButton(
                    label = "Export backup",
                    icon = Icons.Filled.Share,
                    onClick = onExport,
                )
                MorphActionButton(
                    label = "Releases page",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        dismissed = true
                        openUrl(context, UpdateApi.RELEASES_URL)
                    },
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
        },
    )
}
