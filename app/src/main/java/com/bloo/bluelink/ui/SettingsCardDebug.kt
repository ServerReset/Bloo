@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.links
import kotlin.math.max

/** The "Debug" card: this device's diagnostics and the activity log, one place for support troubleshooting. */
@Composable
internal fun DebugCardContent(logs: List<String>, vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
    fun copy(label: String, text: String) {
        clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
    }
    SettingsCard("Debug", Icons.Filled.BugReport, vm, status = "${logs.size} log lines") {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("Device info") {
                DebugSettingsPanel(onCopyToClipboard = { copy("bloo debug", it) })
            }
            SettingsGroup("Activity log") {
                BodySmallText("${logs.size} lines, newest last.")
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    SafeMorphTextButton("Copy", onClick = { copy("bloo logs", logs.joinToString("\n")) })
                    SafeMorphTextButton("Clear", onClick = { vm.clearLogs() }, emphasis = ButtonEmphasis.Destructive)
                }
                val scroll = rememberScrollState()
                SelectionContainer {
                    Text(
                        text = logs.joinToString("\n").ifBlank { "No activity yet." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .fadingEdges(scroll)
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

/**
 * One row of the Credits card: a small icon chip, the project's name/description,
 * and its actual URL as a tappable link (opened via [openUrl] in a Custom Tab) --
 * replacing what used to be three plain, uncoloured, unclickable Text lines with no
 * visual distinction between them at all. Reported directly as wanting this whole
 * section "beefed out" with real links, not a flat wall of tiny grey text.
 */
@Composable
private fun CreditRow(entry: CreditEntry) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().outlinedPanel(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(
            entry.icon,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            size = 40.dp,
            iconSize = 20.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            BodySmallText(
                entry.description,
            )
            Spacer(Modifier.height(GapHairline))
            // The actual link, styled and tappable -- not a caption-coloured, inert
            // copy of the URL. clip+clickable (not the whole Row, which would make
            // the icon/name/description look tappable too when only the link is)
            // sized to just this Row's own content via wrapContentWidth, so the tap
            // target doesn't stretch across empty space to the card's far edge.
            Row(
                Modifier
                    .clip(TinyShape)
                    .hapticClickable { openUrl(context, entry.url) }
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.url.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.labelSmall,
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
