package com.bloo.bluelink.ui

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton

/**
 * The "Debug" card: this device's diagnostics and the activity log, one place for support
 * troubleshooting.
 */
@Composable
internal fun DebugCardContent(
    vm: AppViewModel,
    clipboardScope: CoroutineScope,
    clipboard: Clipboard,
    aiAvailability: com.bloo.bluelink.data.AiAvailability? = null,
) {
    // The log is read here, only while this card is composed: collecting it in the Settings root re-copied up to
    // 500 lines on every log line even with the card hidden.
    val logsVersion by vm.logsVersion.collectAsStateWithLifecycle()
    // Newest first, here and in what Copy hands over: the latest line is the first thing seen, and the view opens on it.
    val logs = remember(logsVersion) { vm.logSnapshot().asReversed() }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun copy(label: String, text: String) {
        clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
    }
    SettingsCard("Debug", Icons.Filled.BugReport, vm, status = "${logs.size} log lines") {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("Device info") {
                DebugSettingsPanel(onCopyToClipboard = { copy("bloo debug", it) })
                // Why the AI features are or are not offered. This is a diagnostic line only: nothing AI
                // appears elsewhere unless this device reports a usable Gemini Nano.
                StatusRow("On-device AI (Gemini Nano)", aiAvailability?.describe() ?: "Checking…")
                MorphTextButton("Check again", onClick = { vm.reprobeAi() })
            }
            SettingsGroup("Performance") {
                BodySmallText("The cost of every frame (composition, layout, draw, GPU), main-thread stalls with the code that was running, recompositions and garbage collection, filed under what you were doing. Reset, use the app for a minute, then copy this and send it.")
                ActionRow {
                    MorphTextButton("Copy report", onClick = { copy("bloo performance", com.bloo.bluelink.perf.PerfMonitor.report()) })
                    MorphTextButton("Share log file", onClick = {
                        com.bloo.bluelink.perf.PerfFile.shareIntent(context)?.let { context.startActivity(android.content.Intent.createChooser(it, "Send the performance log")) }
                    })
                    MorphTextButton("Reset", onClick = { com.bloo.bluelink.perf.PerfMonitor.reset() }, emphasis = ButtonEmphasis.Deny)
                }
            }
            SettingsGroup("Activity log") {
                BodySmallText("${logs.size} lines, newest first.")
                ActionRow {
                    MorphTextButton("Copy", onClick = { copy("bloo logs", logs.joinToString("\n")) })
                    MorphTextButton("Clear", onClick = { vm.clearLogs() }, emphasis = ButtonEmphasis.Deny)
                }
                val scroll = rememberScrollState()
                SelectionContainer {
                    RevealText(
                        text = logs.joinToString("\n").ifBlank { "No activity yet." },
                        style = SecondaryTextStyle(),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).fadingEdges(scroll)
                            .verticalScroll(scroll),
                    )
                }
            }
        }
    }
}

/** "Credits" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun CreditsCardContent(vm: AppViewModel) {
                SettingsCard("Credits", AppIcons.Info, vm) {
                    Column {
                        val credits = remember {
                            listOf(
                                CreditEntry(
                                    "Coil",
                                    "Image loading throughout the app: car photos, map tiles, everything.",
                                    "https://github.com/coil-kt/coil",
                                    Icons.Filled.Image,
                                ),
                                CreditEntry(
                                    "Haze",
                                    "Real backdrop blur behind the status bar and the full-screen map sheet.",
                                    "https://github.com/chrisbanes/haze",
                                    Icons.Filled.BlurOn,
                                ),
                                CreditEntry(
                                    "i5-AutoLock",
                                    "AutoLock ported from Vel-San's original reference implementation.",
                                    "https://github.com/Vel-San/i5-AutoLock",
                                    AppIcons.Lock,
                                ),
                                CreditEntry(
                                    "Jetpack Compose",
                                    "The Compose toolkit the whole app is built with.",
                                    "https://developer.android.com/jetpack/compose",
                                    Icons.Filled.Widgets,
                                ),
                                CreditEntry(
                                    "Kotlin",
                                    "The language everything here, front to back, is written in.",
                                    "https://kotlinlang.org",
                                    Icons.Filled.Code,
                                ),
                                CreditEntry(
                                    "kotlinx.serialization",
                                    "Every persisted setting and cached response.",
                                    "https://github.com/Kotlin/kotlinx.serialization",
                                    Icons.Filled.DataObject,
                                ),
                                CreditEntry(
                                    "OkHttp",
                                    "Every network request this app makes.",
                                    "https://square.github.io/okhttp",
                                    Icons.Filled.Language,
                                ),
                                CreditEntry(
                                    "OpenStreetMap",
                                    "Map tiles: © OpenStreetMap contributors.",
                                    "https://www.openstreetmap.org/copyright",
                                    Icons.Filled.Map,
                                ),
                                CreditEntry(
                                    "Shizuku",
                                    "Optional silent-install path for updates, skipping the manual \"Install anyway\" prompt.",
                                    "https://github.com/RikkaApps/Shizuku",
                                    Icons.Filled.AdminPanelSettings,
                                ),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                            credits.forEach { entry -> CreditRow(entry) }
                        }
                    }
                }
}

/** One entry in the Credits card -- see [CreditRow]. */
private data class CreditEntry(
    val name: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
)

@Composable
private fun CreditRow(entry: CreditEntry) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().outlinedPanel(), horizontalArrangement = Arrangement.spacedBy(GapSection)) {
        IconBadge(
            entry.icon,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            size = 40.dp,
            iconSize = 20.dp,
        )
        Column(Modifier.weight(1f)) {
            RevealText(entry.name, style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            BodySmallText(entry.description)
            Spacer(Modifier.height(GapHairline))
            // The actual link, styled and tappable -- not a caption-coloured, inert copy of the
            // URL. clip+clickable (not the whole Row, which would make the icon/name/description
            // look tappable too when only the link is) sized to just this Row's own content via
            // wrapContentWidth, so the tap target doesn't stretch across empty space to the card's
            // far edge.
            Row(
                Modifier.clip(TinyShape)
                    .hapticClickable { openUrl(context, entry.url) }
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapHairline),
            ) {
                RevealText(
                    entry.url.removePrefix("https://").removePrefix("http://"),
                    style = SecondaryTextStyle(),
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open link",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
