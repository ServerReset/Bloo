package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.BuildConfig

/** A debug information item shown in DebugSettingsPanel; [copyable] lets a tap copy the value. */
data class DebugInfo(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val copyable: Boolean = false,
)

/** Single debug information row: label, value, optional icon and copy. */
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
                    Modifier.clip(TinyShape).hapticClickable { onCopy(info.value) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = GapGroup, vertical = GapRow),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Theme typography so it scales with the user's font-size setting.
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            RollingNumber(
                info.value,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // A vector Icon, not an emoji: consistent with the app's ContentCopy affordance and
        // tintable.
        if (info.copyable && onCopy != null) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = GapRow).size(16.dp),
            )
        }
    }
}

/** Collects the debug information (app, device, runtime) shown in DebugSettingsPanel. */
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
 * Debug settings panel showing technical information about the app and device. [onCopyToClipboard]
 * is called when the user copies a value.
 */
@Composable
fun DebugSettingsPanel(
    modifier: Modifier = Modifier,
    onCopyToClipboard: ((String) -> Unit)? = null,
) {
    val debugInfo = getDebugInfo()

    // No self-styled card: it renders inside SettingsCard("Debug", ...), whose title row already
    // says so.
    Column(modifier.fillMaxWidth()) {
        Text(
            "Tap a copyable value to copy it to the clipboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(GapRow))

        // heightIn is required: the Settings LazyColumn item measures with unbounded max height,
        // which a vertically scrollable child cannot take (crash).
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(debugInfo) { info ->
                DebugInfoItem(
                    info = info,
                    onCopy = onCopyToClipboard
                )
            }

            item {
                Spacer(Modifier.height(GapRow))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GapRow),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                    )
                    Text(
                        text = "Share only when support asks.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
