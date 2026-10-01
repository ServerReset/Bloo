@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setShowSearch
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.unitSystem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.composed
import com.bloo.bluelink.data.links
import kotlin.math.max
import com.bloo.bluelink.data.setChargerApiKey
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.selected
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.deleteCustomPalette
import com.bloo.bluelink.data.saveCustomPalette
import com.bloo.bluelink.data.setActiveCustomPaletteId
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setAuroraMotion
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setColorPalette
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setLockTiming
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setThemeMode
import com.bloo.uicommon.rememberConfirmArm

/** The "Logs" card: the activity log, copyable and clearable. */
@Composable
internal fun LogsCardContent(logs: List<String>, vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
    SettingsCard("Logs", AppIcons.Info, vm, status = "${logs.size} lines") {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            IconLeadRow(
                AppIcons.Info,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Activity log",
                subtitle = "${logs.size} lines, newest last",
            )
            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                SafeMorphTextButton("Copy", onClick = {
                    clipboardScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo logs", logs.joinToString("\n"))))
                    }
                })
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
                        .clip(StandardShape)
                        .background(glassPanelFill())
                        .padding(12.dp)
                        .heightIn(max = 300.dp)
                        .fadingEdges(scroll)
                        .verticalScroll(scroll),
                )
            }
        }
    }
}

/** The "Debug" card: the developer panel. */
@Composable
internal fun DebugCardContent(vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
    SettingsCard("Debug", Icons.Filled.BugReport, vm) {
        DebugSettingsPanel(
            onCopyToClipboard = { text ->
                clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo debug", text))) }
            },
        )
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
                        credits.forEachIndexed { index, entry ->
                            CreditRow(entry)
                            if (index != credits.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = GapRow),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
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
        Modifier.fillMaxWidth(),
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
                    .clip(RoundedCornerShape(6.dp))
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
