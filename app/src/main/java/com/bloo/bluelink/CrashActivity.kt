package com.bloo.bluelink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.installDownloadedApk
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
 * default and nothing more. The whole point is a SELECTABLE stack trace that can be
 * read and copied directly off the device, with no adb already attached at the
 * moment of the crash.
 *
 * [BlooApplication]'s handler deliberately does NOT kill the process after launching
 * this -- this activity is the one thing standing between "app is bricked" and "back
 * to GitHub in a browser to grab a fresh APK by hand," so the check/download/install
 * buttons below talk directly to the same GitHub-release update pipeline the normal
 * app uses ([UpdateChecker]/[UpdateApi]/[ShizukuInstaller]), completely independent
 * of whatever state just crashed. "Restart Bloo" is the only thing here that tears
 * the process down, and only once the user actually asks for it.
 */
class CrashActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    private fun apkCacheFile(): File = File(File(cacheDir, "apk"), "Bloo.apk")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val trace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "(no stack trace captured)"
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        RebootHeader()
                        Spacer(Modifier.height(20.dp))
                        UpdateRecoveryPanel(apkFile = apkCacheFile())
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Copy this and send it back -- it's the exact reason the app stopped.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer {
                            Text(trace, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
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
    private fun RebootHeader() {
        val infiniteTransition = rememberInfiniteTransition(label = "reboot")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
            label = "angle",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Autorenew,
                contentDescription = null,
                modifier = Modifier.size(40.dp).rotate(angle),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Bloo crashed", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Rebooting the vibes. Give it a second.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::restartApp) { Text("Restart Bloo") }
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
        var apkReady by remember { mutableStateOf(apkFile.exists() && apkFile.length() > 0) }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Stuck? Try grabbing a fresh build.", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This works even though the app just crashed -- no need to go back to GitHub by hand.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                val info = (checkResult as? UpdateCheckResult.Available)?.info
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = !checking && !downloading && !installing,
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
                    ) {
                        if (checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Check for updates")
                    }

                    if (apkReady && !downloading) {
                        Button(
                            enabled = !installing,
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
                        ) {
                            if (installing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Install now")
                        }
                    }
                }

                AnimatedVisibility(visible = info != null) {
                    if (info != null) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Text("Build ${info.run.runNumber} is available.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !downloading,
                                onClick = {
                                    val url = info.run.phoneApkUrl
                                    if (url == null) {
                                        statusMessage = "No direct download for this build -- use GitHub instead."
                                        return@Button
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
                            ) {
                                Text(if (downloading) "Downloading… ${(downloadProgress * 100).toInt()}%" else "Download build ${info.run.runNumber}")
                            }
                        }
                    }
                }

                if (downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                }

                statusMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateApi.RELEASES_URL))
                    runCatching { context.startActivity(browserIntent) }
                }) {
                    Text("Open GitHub instead")
                }
            }
        }
    }
}
