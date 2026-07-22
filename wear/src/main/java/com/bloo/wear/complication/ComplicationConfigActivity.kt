package com.bloo.wear.complication

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MyLocation
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.wear.WearSettingsStore
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.MorphButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Launched by the watch-face complication picker when the user picks/configures a
 * Bloo complication. Lets them choose which car THIS slot shows, stored keyed by
 * the complication instance id, then requests an immediate update for that slot.
 *
 * Mechanism: a complication data source can declare (via its manifest entry)
 * that it's configurable, in which case the system launches this Activity
 * -- instead of going straight to rendering -- the first time the user adds
 * it to a watch-face slot, passing the complication's numeric instance id and
 * the ComponentName of the specific data source class (there can be more than
 * one complication service in this app) as extras. This activity's whole job
 * is to let the user finish that one-time (or later re-opened) setup: it
 * writes the chosen car's VIN into [ComplicationCarStore] keyed by
 * (dataSource, complicationId) -- the same store [ChargeComplication] and its
 * siblings read from in their own onComplicationRequest -- then explicitly
 * asks the system ([ComplicationDataSourceUpdateRequester]) to re-render that
 * one instance immediately, rather than waiting for its next natural refresh.
 * `setResult(RESULT_OK/RESULT_CANCELED)` communicates success/cancellation
 * back to the watch-face configuration flow that launched this.
 */
class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val complicationId = intent.getIntExtra(EXTRA_COMPLICATION_ID, -1)
        // The provider ComponentName's short class name (e.g. "ChargeComplication",
        // stripped of its package prefix) doubles as the per-data-source key used
        // throughout ComplicationCarStore -- this app can have several distinct
        // complication services, each configuring its own slots independently.
        val component: ComponentName? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PROVIDER_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROVIDER_COMPONENT)
        }
        if (complicationId == -1 || component == null) { finish(); return }
        val dataSource = component.shortClassName.substringAfterLast('.')

        setContent {
            var cars by remember { mutableStateOf<List<VehicleSnapshot>>(emptyList()) }
            var loaded by remember { mutableStateOf(false) }
            var settings by remember { mutableStateOf<com.bloo.bluelink.data.WearSettingsPayload?>(null) }
            // The store this exact screen writes to (see choose()) already
            // answers "which car is this slot currently pinned to" -- it just
            // wasn't being read back here, so reopening this screen to check
            // or change a slot never highlighted the current selection.
            var currentVin by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                cars = runCatching { SnapshotStore(applicationContext).current().vehicles }.getOrDefault(emptyList())
                settings = runCatching { WearSettingsStore(applicationContext).flow.first() }.getOrNull()
                currentVin = runCatching { ComplicationCarStore(applicationContext).vinFor(dataSource, complicationId) }.getOrNull()
                loaded = true
            }
            BlooWearTheme(settings) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = com.bloo.wear.ui.roundSafeHorizontalPadding(), vertical = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { ListHeader { Text("Show which car?", textAlign = TextAlign.Center) } }
                    if (!loaded) {
                        item {
                            // Matches the loading-state pattern used everywhere
                            // else in the app (TripsScreen, LoginScreen,
                            // WatchApp) -- a bare spinner with no label here was
                            // the one screen with nothing for TalkBack to
                            // announce mid-load either.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Loading cars…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else if (cars.isEmpty()) {
                        item {
                            Text(
                                "No cars yet -- sign in on your phone first, then it syncs here.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (cars.isNotEmpty()) {
                        item {
                            // The Tile pool (Settings screen) has a "Follow
                            // selected" option alongside concrete cars; this
                            // screen only ever listed concrete cars, with no
                            // way back to "just follow whatever I'm looking
                            // at" once a car was explicitly pinned here.
                            MorphButton(
                                label = "Follow selected",
                                icon = Icons.Filled.MyLocation,
                                active = currentVin == null,
                                activeColor = MaterialTheme.colorScheme.primary,
                                pending = false,
                                onClick = { followSelected(dataSource, complicationId, component) },
                            )
                        }
                    }
                    items(cars, key = { it.vin }) { car ->
                        MorphButton(
                            label = car.name,
                            icon = Icons.Filled.DirectionsCar,
                            active = car.vin == currentVin,
                            activeColor = MaterialTheme.colorScheme.primary,
                            pending = false,
                            onClick = { choose(dataSource, complicationId, component, car.vin) },
                        )
                    }
                }
            }
        }
    }

    /** Pin this complication instance to a specific car: persists the VIN
     *  keyed by (dataSource, complicationId), then tells the system to
     *  re-request data for that instance right away (`runCatching` since the
     *  requester call can fail if the data source has since been
     *  unregistered -- non-fatal, the pin is already saved and will apply
     *  on the next natural refresh regardless), and closes with RESULT_OK. */
    private fun choose(dataSource: String, complicationId: Int, component: ComponentName, vin: String) {
        lifecycleScope.launch {
            ComplicationCarStore(applicationContext).setVin(dataSource, complicationId, vin)
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(applicationContext, component)
                    .requestUpdate(complicationId)
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    /** Unpin this complication instance so it goes back to following whichever
     *  car is currently selected in the main app, instead of a fixed VIN --
     *  same immediate-refresh-then-close pattern as [choose]. */
    private fun followSelected(dataSource: String, complicationId: Int, component: ComponentName) {
        lifecycleScope.launch {
            ComplicationCarStore(applicationContext).clear(dataSource, complicationId)
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(applicationContext, component)
                    .requestUpdate(complicationId)
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        // Stable android.support.wearable values delivered by the complication picker.
        private const val EXTRA_COMPLICATION_ID =
            "android.support.wearable.complications.EXTRA_CONFIG_COMPLICATION_ID"
        private const val EXTRA_PROVIDER_COMPONENT =
            "android.support.wearable.complications.EXTRA_CONFIG_PROVIDER_COMPONENT"
    }
}
