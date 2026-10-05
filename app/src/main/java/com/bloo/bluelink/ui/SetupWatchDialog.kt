package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.WorkflowRun
import com.bloo.bluelink.wear.PhoneWatchSyncService
import com.bloo.bluelink.wear.WatchAdbInstaller
import com.bloo.bluelink.wear.WatchPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WatchSetupStep { Prepare, Pair, Install, Done }

/**
 * "Set up watch". The watch app is not on Google Play and never will be, so the first install goes
 * over the watch's own Wireless debugging: the phone pairs with it, downloads the watch APK from
 * the project's builds, and sideloads it ([WatchAdbInstaller]). Nothing is typed into a computer.
 */
@Composable
internal fun SetupWatchDialog(phoneName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watch by WatchPresence.watch.collectAsStateWithLifecycle()
    var update by remember { mutableStateOf<WorkflowRun?>(null) }
    LaunchedEffect(Unit) { update = UpdateApi.fetchLatestSuccessfulRun(UpdateApi.DEFAULT_BRANCH) }
    val apkUrl = update?.watchApkUrl

    // The ADB key pair is made up fresh for this setup (RSA keygen, so off the main thread) and
    // dropped with the dialog.
    var installer by remember { mutableStateOf<WatchAdbInstaller?>(null) }
    LaunchedEffect(Unit) {
        installer = withContext(Dispatchers.Default) { runCatching { WatchAdbInstaller() }.getOrNull() }
    }
    DisposableEffect(installer) {
        val held = installer
        onDispose { if (held != null) Thread { runCatching { held.close() } }.start() }
    }

    var step by remember { mutableStateOf(WatchSetupStep.Prepare) }
    var host by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun report(result: Result<Unit>, ok: () -> Unit) {
        busy = false
        failed = result.isFailure
        status = result.exceptionOrNull()?.let { it.message ?: "Something went wrong" }
        if (result.isSuccess) ok()
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Watch,
        title = "Set up watch",
        text = {
            when (step) {
                WatchSetupStep.Prepare -> {
                    BodyMediumText(
                        if (watch != null) {
                            "${watch?.name?.ifBlank { "Your watch" }} is already connected to $phoneName. Bloo can send the latest watch app straight to it."
                        } else {
                            "Bloo for Wear isn't on Google Play. $phoneName installs it over the watch's Wireless debugging instead. On the watch:"
                        },
                    )
                    if (watch == null) {
                        BodyMediumText("1. Join the same Wi-Fi as this phone.")
                        BodyMediumText("2. Settings → System → About → Versions, tap Build number seven times.")
                        BodyMediumText("3. Settings → Developer options → turn on Wireless debugging, then tap Pair new device.")
                    }
                }
                WatchSetupStep.Pair -> {
                    BodyMediumText("Type in what the watch's \"Pair new device\" screen shows.")
                    SetupField(host, { host = it }, "Watch IP address", KeyboardType.Uri)
                    Row(horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                        SetupField(pairPort, { pairPort = it.filter(Char::isDigit) }, "Pairing port", KeyboardType.Number, Modifier.weight(1f))
                        SetupField(code, { code = it.filter(Char::isDigit).take(6) }, "Pairing code", KeyboardType.Number, Modifier.weight(1f))
                    }
                }
                WatchSetupStep.Install -> {
                    BodyMediumText("Paired. Back on the Wireless debugging screen, enter the port shown under the IP address (it is NOT the pairing port).")
                    SetupField(connectPort, { connectPort = it.filter(Char::isDigit) }, "Connection port", KeyboardType.Number)
                    BodySmallText(
                        "If connecting fails: keep the Wireless debugging screen open (the port changes " +
                            "when you leave it), and make sure the watch and phone are on the same Wi‑Fi — a " +
                            "watch on its own mobile/Bluetooth network can't be reached this way.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WatchSetupStep.Done -> BodyMediumText("Bloo is installed on the watch. Open it there and it will connect to $phoneName. You can turn Wireless debugging off again.")
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        buttons = {
            val ready = installer != null && !busy
            when (step) {
                WatchSetupStep.Prepare -> if (watch != null && apkUrl != null) {
                    MorphActionButton(
                        label = "Send to watch",
                        icon = Icons.Filled.Watch,
                        onClick = {
                            PhoneWatchSyncService.pushWatchApk(context, apkUrl)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = ButtonEmphasis.Confirm,
                    )
                } else if (watch == null) {
                    MorphActionButton("Wireless debugging is on", Icons.Filled.Watch, { step = WatchSetupStep.Pair }, Modifier.fillMaxWidth())
                }
                WatchSetupStep.Pair -> MorphActionButton(
                    label = "Pair",
                    icon = Icons.Filled.Watch,
                    enabled = ready && host.isNotBlank() && pairPort.isNotBlank() && code.length == 6,
                    pending = busy,
                    onClick = {
                        val held = installer ?: return@MorphActionButton
                        busy = true
                        status = "Pairing…"
                        failed = false
                        scope.launch {
                            report(held.pairWith(host, pairPort.toInt(), code)) { step = WatchSetupStep.Install; status = null }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = ButtonEmphasis.Confirm,
                )
                WatchSetupStep.Install -> MorphActionButton(
                    label = "Install Bloo",
                    icon = Icons.Filled.Watch,
                    enabled = ready && connectPort.isNotBlank() && apkUrl != null,
                    pending = busy,
                    onClick = {
                        val held = installer ?: return@MorphActionButton
                        val url = apkUrl ?: return@MorphActionButton
                        busy = true
                        failed = false
                        scope.launch {
                            status = "Connecting…"
                            val connected = held.connectTo(host, connectPort.toInt())
                            if (connected.isFailure) return@launch report(connected) {}
                            status = "Downloading the watch app…"
                            val apk = held.download(url).getOrElse { return@launch report(Result.failure(it)) {} }
                            status = "Installing on the watch…"
                            report(held.install(apk)) { step = WatchSetupStep.Done; status = null }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = ButtonEmphasis.Confirm,
                )
                WatchSetupStep.Done -> Unit
            }
            MorphTextButton(if (step == WatchSetupStep.Done) "Done" else "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
private fun SetupField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
) {
    BlooTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
    )
}
