@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.text.input.ImeAction
import com.bloo.bluelink.data.setChargerApiKey

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
