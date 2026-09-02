@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.bloo.bluelink.BuildConfig

/**
 * Represents a debug information item displayed in DebugSettingsPanel.
 * Each item shows a label and associated value/information.
 *
 * @param label Human-readable label ("App Version", "Device Model", etc.)
 * @param value The actual value to display
 * @param icon Optional material icon for visual identification
 * @param copyable Whether tapping the item copies value to clipboard
 */
data class DebugInfo(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val copyable: Boolean = false,
)

/**
 * Single debug information row.
 * Shows label and value with optional icon and copy functionality.
 */
@Composable
private fun DebugInfoItem(
    info: DebugInfo,
    onCopy: ((String) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (info.copyable && onCopy != null) {
                    Modifier.clickable { onCopy(info.value) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Theme typography, not raw sp -- every other row in the app sizes off
            // MaterialTheme.typography so it scales with the user's own font-size setting;
            // this panel was the one place still stating pixel-locked sp values by hand.
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = info.value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // A real Icon, not a "📋" emoji glyph in a hand-backgrounded box -- the app's
        // copy affordance everywhere else (the snackbar in Screens.kt) is
        // Icons.Filled.ContentCopy, and an emoji renders inconsistently across devices
        // and doesn't tint/theme the way a vector icon does.
        if (info.copyable && onCopy != null) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
            )
        }
    }
}

/**
 * Collects all debug information for the device and app.
 * Used to populate DebugSettingsPanel with relevant data.
 *
 * **Include:**
 * - App version and build number
 * - Device model, OS, API level
 * - Runtime info (memory, network)
 * - Feature flags and configuration
 *
 * @return List of DebugInfo items ready for display
 */
@Composable
fun getDebugInfo(): List<DebugInfo> {
    return listOf(
        // App Info
        DebugInfo(
            label = "App Version",
            value = BuildConfig.VERSION_NAME,
            copyable = true,
        ),
        DebugInfo(
            label = "Build Number",
            value = BuildConfig.VERSION_CODE.toString(),
            copyable = true,
        ),
        DebugInfo(
            label = "Build Type",
            value = BuildConfig.BUILD_TYPE,
        ),

        // Device Info
        DebugInfo(
            label = "Device Model",
            value = Build.MODEL,
            copyable = true,
        ),
        DebugInfo(
            label = "Manufacturer",
            value = Build.MANUFACTURER,
        ),
        DebugInfo(
            label = "OS Version",
            value = "Android ${Build.VERSION.RELEASE}",
        ),
        DebugInfo(
            label = "API Level",
            value = Build.VERSION.SDK_INT.toString(),
        ),

        // Runtime Info
        DebugInfo(
            label = "Java Runtime",
            value = listOfNotNull(
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version")
            ).joinToString(" ").ifBlank { "Unknown" },
        ),
        DebugInfo(
            label = "Kotlin Runtime",
            value = KotlinVersion.CURRENT.toString(),
        ),
    )
}

/**
 * Debug settings panel showing technical information about the app and device.
 * Useful for:
 * - Support troubleshooting and diagnostics
 * - Verifying app/OS versions for feature compatibility
 * - Debugging device-specific issues
 * - Monitoring runtime performance metrics
 *
 * **Usage:**
 * ```kotlin
 * DebugSettingsPanel(
 *     onCopyToClipboard = { text -> clipboard.setText(AnnotatedString(text)) }
 * )
 * ```
 *
 * @param onCopyToClipboard Callback when user wants to copy a value to clipboard
 * @param modifier Optional modifier for the container
 */
@Composable
fun DebugSettingsPanel(
    onCopyToClipboard: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val debugInfo = getDebugInfo()

    // No self-styled outer card any more: this panel only ever renders inside
    // SettingsCard("Debug", ...) (see SettingsScreen.kt), whose own title row already says
    // "Debug" with a matching icon -- the "Debug Information" header this used to draw was a
    // second, redundant copy of that same fact in its own bespoke surfaceContainer/surface
    // stack, the "Redundant/duplicate content" pattern flagged elsewhere in the app. This is
    // now a plain caption plus the list, drawn straight on the card's own body like every
    // other SettingsCard's content.
    Column(modifier.fillMaxWidth()) {
        Text(
            "Tap a copyable value to copy it to the clipboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SettingsGapRow))

        // heightIn is NOT optional here. This panel is rendered inside the Settings screen's
        // LazyVerticalStaggeredGrid item{}, which measures its content with an UNBOUNDED max
        // height, and a vertically scrollable component measured with an infinite max height
        // throws outright -- so entering Advanced mode, which is the only way this card is
        // composed, crashed the screen every time. The Logs card and AnnouncementHistory both
        // already cap themselves at 300.dp against this exact failure; this panel was the one
        // that did not.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(debugInfo) { info ->
                DebugInfoItem(
                    info = info,
                    onCopy = onCopyToClipboard
                )
            }

            item {
                Spacer(Modifier.height(SettingsGapRow))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                    )
                    Text(
                        text = "This debug information should not be shared unless requested for support purposes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Compact debug info section for device/app status.
 * Lighter weight than DebugSettingsPanel, useful for status bar or quick reference.
 *
 * @param modifier Optional modifier
 */
@Composable
fun CompactDebugInfo(
    modifier: Modifier = Modifier,
) {
    val debugInfo = getDebugInfo().take(3)  // Just app version, device, OS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        debugInfo.forEach { info ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = info.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
                Text(
                    text = info.value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
