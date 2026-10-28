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
import com.bloo.bluelink.data.WeatherCode

/** The icon for a weather [condition] at day or night, so icon choices are consistent across the phone UI. */
fun weatherIcon(condition: WeatherCode, isDay: Boolean): ImageVector = when (condition) {
    WeatherCode.CLEAR -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.Nightlight
    WeatherCode.PARTLY_CLOUDY -> Icons.Filled.WbCloudy
    WeatherCode.CLOUDY, WeatherCode.UNKNOWN -> Icons.Filled.Cloud
    WeatherCode.FOG -> Icons.Filled.BlurOn
    WeatherCode.DRIZZLE -> Icons.Filled.Grain
    WeatherCode.RAIN, WeatherCode.SHOWERS -> Icons.Filled.Umbrella
    WeatherCode.SNOW -> Icons.Filled.AcUnit
    WeatherCode.THUNDERSTORM -> Icons.Filled.Thunderstorm
}

/** The tint for a weather [condition]: a colour per kind of weather, and [neutralColor] where there is nothing to say. */
fun weatherTint(condition: WeatherCode, isDay: Boolean, neutralColor: Color): Color = when (condition) {
    WeatherCode.CLEAR -> if (isDay) Color(com.bloo.bluelink.data.BlooColors.warn) else Color(0xFFB0BEC5)
    WeatherCode.PARTLY_CLOUDY -> Color(0xFF90A4AE)
    WeatherCode.CLOUDY, WeatherCode.FOG, WeatherCode.UNKNOWN -> neutralColor
    WeatherCode.DRIZZLE, WeatherCode.RAIN, WeatherCode.SHOWERS -> Color(0xFF4FC3F7)
    WeatherCode.SNOW -> Color(0xFF81D4FA)
    WeatherCode.THUNDERSTORM -> Color(0xFF9575CD)
}
