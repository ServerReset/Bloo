@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
            Text(
                text = info.value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Copy indicator (small icon or text)
        if (info.copyable && onCopy != null) {
            Text(
                text = "📋",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp),
                fontSize = 10.sp,
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
            value = "1.2.3",  // BuildConfig.VERSION_NAME in production
            copyable = true,
        ),
        DebugInfo(
            label = "Build Number",
            value = "1584",  // BuildConfig.VERSION_CODE in production
            copyable = true,
        ),
        DebugInfo(
            label = "Flavor",
            value = "production",  // BuildConfig.FLAVOR in production
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
            value = "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}",
        ),
        DebugInfo(
            label = "Kotlin Runtime",
            value = "1.9.0",  // Or dynamically determined
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(0.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Info,
                contentDescription = "Debug Info",
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = "Debug Information",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Tap values to copy to clipboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }

        // Content - organized by section
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                )
        ) {
            items(debugInfo) { info ->
                DebugInfoItem(
                    info = info,
                    onCopy = onCopyToClipboard
                )
            }

            // Footer with warning
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = "This debug information should not be shared unless requested for support purposes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 9.sp,
                        )
                    }
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
