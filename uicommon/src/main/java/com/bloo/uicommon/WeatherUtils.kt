package com.bloo.uicommon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a WMO weather interpretation code + day/night flag to the appropriate
 * Material icon. Shared between phone and watch so icon choices are consistent.
 */
fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.Nightlight
    1, 2 -> Icons.Filled.WbCloudy
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.BlurOn
    51, 53, 55, 56, 57 -> Icons.Filled.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Filled.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

/**
 * Condition-appropriate accent colour for a weather icon. Mostly static ARGB
 * constants; callers pass [neutralColor] (typically `onSurfaceVariant` from
 * their Material theme) for overcast/fog/unknown conditions.
 */
fun weatherTint(code: Int, isDay: Boolean, neutralColor: Color): Color = when (code) {
    0 -> if (isDay) Color(0xFFFFB300) else Color(0xFFB0BEC5)
    1, 2 -> Color(0xFF90A4AE)
    3, 45, 48 -> neutralColor
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Color(0xFF4FC3F7)
    71, 73, 75, 77, 85, 86 -> Color(0xFF81D4FA)
    95, 96, 99 -> Color(0xFF9575CD)
    else -> neutralColor
}
