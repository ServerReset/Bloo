package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * over the watch's own Wireless debugging: the phone pairs with it, streams the watch APK from the
 * project's builds, and sideloads it ([WatchAdbInstaller]). Nothing is typed into a computer.
 *
 * The APK is prefetched in the background the moment the flow opens, so by the time pair and connect
 * are done the push is quick. The bytes are dropped when the dialog leaves.
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

    // Prefetched watch APK: downloaded in the background while the user is still pairing.
    var apk by remember { mutableStateOf<ByteArray?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(installer, apkUrl) {
        val held = installer ?: return@LaunchedEffect
        val url = apkUrl ?: return@LaunchedEffect
        if (apk != null) return@LaunchedEffect
        downloadError = null
        held.download(url) { downloadProgress = it }
            .onSuccess { apk = it }
            .onFailure { downloadError = it.message }
    }

    DisposableEffect(installer) {
        val held = installer
        onDispose {
            if (held != null) Thread { runCatching { held.close() } }.start()
            // Drop the prefetched APK with the dialog: nothing keeps several MB of it resident after
            // the install, success or not.
            apk = null
        }
    }

    var step by remember { mutableStateOf(WatchSetupStep.Prepare) }
    var host by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var installProgress by remember { mutableFloatStateOf(0f) }

    // The "have you done this" checklist on the first step; Start stays disabled until all are ticked,
    // so the user is not dropped into a pair screen unprepared.
    var checkedWifi by remember { mutableStateOf(false) }
    var checkedDebug by remember { mutableStateOf(false) }
    var checkedPair by remember { mutableStateOf(false) }
    val checklistReady = checkedWifi && checkedDebug && checkedPair

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
                    if (watch != null) {
                        BodyMediumText("${watch?.name?.ifBlank { "Your watch" }} is already connected to $phoneName. Bloo can send the latest watch app straight to it.")
                    } else {
                        BodyMediumText("Bloo for Wear isn't on Google Play, so $phoneName installs it over the watch's own Wireless debugging. Do this ON THE WATCH:")
                        BodyMediumText("1. Swipe down from the watch face → tap the gear (Settings).")
                        BodyMediumText("2. Settings → System → About → tap \"Build number\" 7 times, until it says you're a developer.")
                        BodyMediumText("3. Go back → Developer options → turn on \"Wireless debugging\".")
                        BodyMediumText("4. Check the watch is on the SAME Wi-Fi as this phone (Settings → Connectivity → Wi-Fi).")
                        BodyMediumText("Tick all three below when they're done:")
                        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                            ToggleRow("Same Wi-Fi as the phone", checkedWifi) { checkedWifi = it }
                            ToggleRow("Developer options are on", checkedDebug) { checkedDebug = it }
                            ToggleRow("Wireless debugging is ON", checkedPair) { checkedPair = it }
                        }
                    }
                }
                WatchSetupStep.Pair -> {
                    BodyMediumText("Open \"Pair new device\" on the watch and type in what it shows.")
                    SetupField(host, { host = it }, "Watch IP address", KeyboardType.Uri)
                    Row(horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                        SetupField(pairPort, { pairPort = it.filter(Char::isDigit) }, "Pairing port", KeyboardType.Number, Modifier.weight(1f))
                        SetupField(code, { code = it.filter(Char::isDigit).take(6) }, "Wi-Fi pairing code", KeyboardType.Number, Modifier.weight(1f))
                    }
                    BodySmallText(
                        "That code is six digits. The pairing port is the one NEXT TO it, not the port on " +
                            "the Wireless debugging screen behind it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WatchSetupStep.Install -> {
                    BodyMediumText("Back on the watch's main Wireless debugging screen, type the port shown under the IP address (NOT the pairing port).")
                    SetupField(connectPort, { connectPort = it.filter(Char::isDigit) }, "Connection port", KeyboardType.Number)
                    if (downloadError != null) {
                        BodySmallText("Couldn't fetch the watch app: $downloadError", color = MaterialTheme.colorScheme.error)
                    } else if (busy) {
                        // The install itself streams several MB over ADB, which is the slow part; show
                        // a bar so it does not look hung.
                        LinearProgressIndicator(
                            progress = { if (installProgress in 0.01f..0.99f) installProgress else 1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (apk == null) {
                        BodySmallText("Fetching the watch app… ${(downloadProgress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    }
                    BodySmallText(
                        "Keep the watch's Wireless debugging screen open the whole time (the port changes " +
                            "when you leave it), and stay on the same Wi-Fi.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WatchSetupStep.Done -> {
                    BodyMediumText("Bloo is installed on the watch. Open it there and it will connect to $phoneName.")
                    // The security nudge, now that the flow switches it off for the user where it can.
                    BodySmallText(
                        "Wireless debugging was turned off on the watch. If it still shows as on, turn it " +
                            "off yourself (Developer options) so its debug port is not left open.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
                    MorphActionButton(
                        label = "Wireless debugging is on",
                        icon = Icons.Filled.Watch,
                        onClick = { step = WatchSetupStep.Pair },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = checklistReady,
                    )
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
                    enabled = ready && connectPort.isNotBlank() && apk != null,
                    pending = busy,
                    onClick = {
                        val held = installer ?: return@MorphActionButton
                        val bytes = apk ?: return@MorphActionButton
                        busy = true
                        failed = false
                        installProgress = 0.15f
                        scope.launch {
                            status = "Connecting…"
                            val connected = held.connectTo(host, connectPort.toInt()).let { primary ->
                                if (primary.isSuccess || pairPort.isBlank() || pairPort == connectPort) primary
                                else held.connectTo(host, pairPort.toInt())
                            }
                            if (connected.isFailure) return@launch report(connected) {}
                            status = "Installing on the watch…"
                            installProgress = 0.55f
                            val installed = held.install(bytes)
                            if (installed.isSuccess) {
                                // Best effort: switch the watch's Wireless debugging back off for the
                                // user; the Done step still reminds them in case it no-op'd.
                                status = "Turning Wireless debugging off…"
                                held.disableWirelessDebugging()
                            }
                            report(installed) { step = WatchSetupStep.Done; status = null; apk = null }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = ButtonEmphasis.Confirm,
                )
                WatchSetupStep.Done -> Unit
            }
            SafeMorphTextButton(
                text = if (step == WatchSetupStep.Done) "Done" else "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
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
