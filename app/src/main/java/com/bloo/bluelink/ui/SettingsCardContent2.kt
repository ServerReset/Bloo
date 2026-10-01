@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.links
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import android.content.ClipData
import com.bloo.bluelink.data.setChargerApiKey

/**
 * The second half of Settings' per-card content functions (Location through
 * Credits) -- split out of SettingsScreen.kt purely to keep that file smaller.
 * All promoted private -> internal since SettingsScreen, which stays there,
 * calls each one exactly once from its LazyColumn.
 */

/** "Location" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun LocationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val place = appearance.weatherLabel
    SettingsCard(
        "Location",
        Icons.Filled.LocationOn,
        vm,
        // The collapsed row now names the current place on the right instead of showing
        // nothing -- the same "tell you what it knows while shut" treatment the rest of
        // Settings' cards get. Redone body: ONE clear statement of where "my location"
        // points, then the controls, rather than a description + a place row + a field +
        // two buttons all at the same visual weight.
        status = place ?: "Device location",
    ) {
        var weatherQuery by remember { mutableStateOf("") }
        val locationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) vm.useDeviceLocationForWeather()
            else vm.reportError("Location permission denied. Type a place instead")
        }
        // The current place, as the card's own leading statement. When nothing is set, say
        // what "my location" falls back to rather than leaving the top of the card blank.
        if (place == null) {
            BodySmallText("\"My location\" follows your device until you set a place below.")
            Spacer(Modifier.height(GapGroup))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ThemedIcon(Icons.Filled.LocationOn, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    place,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SafeMorphTextButton(
                    "Clear",
                    onClick = { vm.clearWeatherLocation() },
                    fillOnPress = false,
                )
            }
            Spacer(Modifier.height(GapGroup))
        }
        // One standard button row: the field on its own full-width line, then the app's
        // standard action buttons beneath it -- the same shape every other Settings card's
        // controls use, instead of a FlowRow of two half-width buttons.
        BlooTextField(
            value = weatherQuery,
            onValueChange = { weatherQuery = it },
            label = { Text("City or place") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        )
        Spacer(Modifier.height(GapRow))
        ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            val setPlaceSource = remember { MutableInteractionSource() }
            MorphActionButton(
                label = "Set place",
                icon = Icons.Filled.Place,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = setPlaceSource,
                enabled = weatherQuery.isNotBlank(),
                onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                expressive = true,
            )
            val myLocationSource = remember { MutableInteractionSource() }
            MorphActionButton(
                label = "My location",
                icon = Icons.Filled.MyLocation,
                onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                modifier = Modifier.fillMaxWidth(),
                interactionSource = myLocationSource,
                expressive = true,
            )
        }
    }
}

/** "Logs" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun LogsCardContent(logs: List<String>, vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
            SettingsCard("Logs", AppIcons.Info, vm, status = "${logs.size} lines") {
                // No local expand state any more. The card's OWN chevron (PebbleShell's, via
                // SettingsCard) already governs this body -- nothing inside a collapsed card
                // is composed at all -- so the "Show"/"Hide" button that used to live on this row
                // was a second disclosure for the same content: open the card, then open the log
                // again. One control, the outer one.
                val lineCount = logs.size
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ThemedIcon(AppIcons.Info, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    BodyMediumText(
                        "Activity log  ·  $lineCount lines",
                        modifier = Modifier.weight(1f),
                    )
                    // Always present now: they used to appear only once the inner disclosure
                    // was opened, which is exactly the second step that made this card feel
                    // like it opened twice.
                    ExpressiveButtonRow(spacing = 0.dp) {
                            SafeMorphTextButton(
                                "Copy",
                                onClick = {
                                    clipboardScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("bloo logs", logs.joinToString("\n"))),
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            SafeMorphTextButton(
                                "Clear",
                                onClick = { vm.clearLogs() },
                            )
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Column {
                    Spacer(Modifier.height(GapHairline))
                        SectionDivider()
                        Spacer(Modifier.height(GapHairline))
                        val logScroll = rememberScrollState()
                        SelectionContainer {
                            Text(
                                text = logs.joinToString("\n").ifBlank { "No activity yet." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .fadingEdges(logScroll)
                                    .verticalScroll(logScroll),
                            )
                        }
                        Spacer(Modifier.height(GapHairline))
                        if (lineCount > 0) {
                            LabelSmallText(
                                "Newest $lineCount lines.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
}

/** "Map & Navigation" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun MapNavigationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard(
                "Map & Navigation",
                Icons.Filled.Map,
                vm,
                // Collapsed right-side read of whether nearby chargers can load at all.
                status = if (appearance.chargerApiKey.isNullOrBlank()) "No API key" else "Key set",
            ) {
                TitleSmallText("Open Charge Map API Key")
                BodySmallText(
                    "Needed for nearby EV chargers. Get a free key at openchargemap.org/site/develop/api.",
                )
                Spacer(Modifier.height(GapRow))
                var keyInput by remember { mutableStateOf(appearance.chargerApiKey ?: "") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BlooTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste API key here") },
                        singleLine = true,
                        colors = borderlessFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null)
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    MorphTextButton(
                        "Save",
                        onClick = { vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null) },
                        // Enabled whenever the field differs from what is saved, INCLUDING when it
                        // is blank -- blank is how you clear a saved key, and requiring a
                        // non-blank value here made clearing impossible from this button (the
                        // handler already turns a blank into null). Standard Save glyph, not
                        // suppressed: every other action button in Settings leads with one.
                        enabled = keyInput != (appearance.chargerApiKey ?: ""),
                    )
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
