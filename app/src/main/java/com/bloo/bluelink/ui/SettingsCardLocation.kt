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

/** The "Location" card: the place weather and "my location" follow. */
@Composable
internal fun LocationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val place = appearance.weatherLabel
    SettingsCard("Location", Icons.Filled.LocationOn, vm, status = place ?: "Device location") {
        var query by remember { mutableStateOf("") }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.useDeviceLocationForWeather() else vm.reportError("Location permission denied. Type a place instead")
        }
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            if (place == null) {
                BodySmallText("\"My location\" follows your device until you set a place below.")
            } else {
                IconLeadRow(
                    Icons.Filled.LocationOn,
                    tint = MaterialTheme.colorScheme.primary,
                    title = place,
                    subtitle = "Weather and the map use this place",
                    trailing = { SafeMorphTextButton("Clear", onClick = { vm.clearWeatherLocation() }, fillOnPress = false) },
                )
            }
            BlooTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("City or place") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                MorphActionButton(
                    label = "Set place",
                    icon = Icons.Filled.Place,
                    enabled = query.isNotBlank(),
                    onClick = { vm.setWeatherPlace(query); query = "" },
                )
                MorphActionButton(
                    label = "My location",
                    icon = Icons.Filled.MyLocation,
                    onClick = { permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                )
            }
        }
    }
}

/** The "Map & Navigation" card: the Open Charge Map key that powers nearby chargers. */
@Composable
internal fun MapNavigationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val saved = appearance.chargerApiKey ?: ""
    SettingsCard("Map & Navigation", Icons.Filled.Map, vm, status = if (saved.isBlank()) "No API key" else "Key set") {
        var key by remember { mutableStateOf(saved) }
        val commit = { vm.setChargerApiKey(key.ifBlank { null }, null) }
        SettingsGroup("Open Charge Map API key") {
            BodySmallText("Needed for nearby EV chargers. Get a free key at openchargemap.org/site/develop/api.")
            BlooTextField(
                value = key,
                onValueChange = { key = it },
                placeholder = { Text("Paste API key here") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            SafeMorphTextButton("Save key", onClick = { commit() }, enabled = key != saved, emphasis = ButtonEmphasis.Primary)
        }
    }
}
