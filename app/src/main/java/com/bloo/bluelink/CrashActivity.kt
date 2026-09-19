package com.bloo.bluelink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.installDownloadedApk
import com.bloo.bluelink.ui.MorphTextButton
import com.bloo.bluelink.ui.SettingsGroup
import com.bloo.bluelink.update.ShizukuInstaller
import com.bloo.bluelink.update.UpdateCheckResult
import com.bloo.bluelink.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shown instead of silently restarting into [MainActivity] after an uncaught
 * exception -- see [BlooApplication]. Deliberately has no dependency on
 * BlooTheme/AppViewModel/anything else in the app: whatever just crashed it might be
 * reachable again through either of those, so this stays a plain MaterialTheme
 * default and nothing more. [SettingsGroup] and [MorphTextButton] are safe exceptions --
 * both are pure functions of MaterialTheme plus composition locals that default to off/null
 * with no provider (LocalHaptics, LocalExpressiveGrowth, battery-saver state), verified
 * against their own definitions rather than assumed, so borrowing the app's standard
 * components here for a consistent look carries none of the risk a real BlooTheme/
 * AppViewModel dependency would. The whole point is a SELECTABLE, COPYABLE stack trace
 * that can be read off the device directly, with no adb already attached at the moment
 * of the crash.
 *
 * [BlooApplication]'s handler ALWAYS kills the crashed process right after launching
 * this (see its own doc for why that has to be unconditional, not skipped for this
 * activity's sake) -- which is fine: startActivity() already handed the launch off to
 * system_server before the kill, so this always comes up in a brand-new, healthy
 * process regardless. That fresh process is exactly what lets the check/download/
 * install buttons below talk to the same GitHub-release update pipeline the normal
 * app uses ([UpdateChecker]/[UpdateApi]/[ShizukuInstaller]) with a fully working main
 * thread under them, completely independent of whatever state just crashed the old one.
 */
class CrashActivity : ComponentActivity() {
    companion object {
        /** Carries the FULL report now, not just a bare stack trace -- see
         *  [BlooApplication.onCreate]'s own `report` local for what's actually in it
         *  (device/build info, the stack trace, and the [com.bloo.bluelink.data.AppLog]
         *  history leading up to the crash). Kept this name rather than renaming it: it's
         *  a public Intent-extra key, and BlooApplication (the only writer) and this
         *  Activity (the only reader) are the only two things that ever need to agree on
         *  it, so a rename here would only be busywork, not a real compatibility concern. */
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    private fun apkCacheFile(): File = File(File(cacheDir, "apk"), "Bloo.apk")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val report = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "(no crash report captured)"
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Header()
                        UpdateRecoveryPanel(apkFile = apkCacheFile())
                        CrashLog(report)
                    }
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    @Composable
    private fun Header() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Autorenew,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text("Bloo crashed", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        MorphTextButton("Restart Bloo", onClick = ::restartApp)
    }

    @Composable
    private fun UpdateRecoveryPanel(apkFile: File) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }
        var checkResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
        var downloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }
        var installing by remember { mutableStateOf(false) }
        // Starts false regardless of whether apkFile already exists on disk: a leftover
        // Bloo.apk from a PRIOR session (crashed mid-download, or just never installed) isn't
        // something this session downloaded, and trusting it blindly showed "Install now"
        // immediately on screen load with no download having happened yet -- possibly for a
        // stale or genuinely partial/corrupt file. Only a download that completes below
        // (ok == true) ever sets this true, so Install only ever appears after THIS screen
        // actually finished fetching an APK.
        var apkReady by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        SettingsGroup("Update") {
            Text(
                "Grab a fresh build without leaving this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MorphTextButton(
                text = if (checking) "Checking…" else "Check for updates",
                enabled = !checking && !downloading && !installing,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    checking = true
                    statusMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { UpdateChecker.checkPhone(context, force = true) }
                        checkResult = result
                        checking = false
                        statusMessage = when (result) {
                            is UpdateCheckResult.UpToDate -> "Already on the latest build."
                            is UpdateCheckResult.Skipped -> "Couldn't check just now -- try again."
                            is UpdateCheckResult.Failed -> result.error ?: "Couldn't reach GitHub."
                            is UpdateCheckResult.Available -> null
                        }
                    }
                },
            )

            if (apkReady && !downloading) {
                MorphTextButton(
                    text = if (installing) "Installing…" else "Install now",
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        installing = true
                        statusMessage = "Installing…"
                        scope.launch(Dispatchers.IO) {
                            val seamless = ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()
                            val ok = if (seamless) {
                                ShizukuInstaller.installApk(apkFile, context.packageName).isSuccess
                            } else {
                                false
                            }
                            withContext(Dispatchers.Main) {
                                installing = false
                                if (ok) {
                                    statusMessage = "Update installed. Reopen Bloo to finish."
                                } else if (!installDownloadedApk(context, apkFile)) {
                                    statusMessage = "Couldn't open the installer. Find Bloo.apk in your downloads."
                                } else {
                                    statusMessage = "Opening the installer…"
                                }
                            }
                        }
                    },
                )
            }

            val info = (checkResult as? UpdateCheckResult.Available)?.info
            if (info != null) {
                Text("Build ${info.run.runNumber} is available.", style = MaterialTheme.typography.bodySmall)
                MorphTextButton(
                    text = if (downloading) "Downloading… ${(downloadProgress * 100).toInt()}%" else "Download build ${info.run.runNumber}",
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val url = info.run.phoneApkUrl
                        if (url == null) {
                            statusMessage = "No direct download for this build -- use GitHub instead."
                            return@MorphTextButton
                        }
                        downloading = true
                        downloadProgress = 0f
                        statusMessage = null
                        scope.launch {
                            val ok = UpdateApi.downloadApk(url, apkFile) { downloadProgress = it }
                            downloading = false
                            apkReady = ok
                            statusMessage = if (ok) null else "Download failed -- check your connection and try again."
                        }
                    },
                )
            }

            if (downloading) {
                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
            }

            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            MorphTextButton(
                "Open GitHub instead",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateApi.RELEASES_URL))
                    runCatching { context.startActivity(browserIntent) }
                },
            )
        }
    }

    @Composable
    private fun CrashLog(report: String) {
        val clipboard = LocalClipboardManager.current
        SettingsGroup("Crash report") {
            Text(
                "Copy this and send it back -- device, build, the exact reason the app " +
                    "stopped, and what it was doing right before that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MorphTextButton(
                "Copy",
                icon = Icons.Filled.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(report)) },
            )
            SelectionContainer {
                Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
