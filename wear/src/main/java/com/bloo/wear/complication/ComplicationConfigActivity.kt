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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.bloo.bluelink.data.WearSettingsPayload
import com.bloo.wear.WearSettingsStore
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.MorphButton
import com.bloo.wear.ui.roundSafeHorizontalPadding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Launched by the watch-face complication picker (manifest action
 * com.bloo.wear.COMPLICATION_CONFIG) when the user picks/configures a Bloo
 * complication. Lets them choose which car THIS slot shows — stored keyed by the
 * complication instance id — then requests an immediate update for that slot.
 *
 * A configurable complication data source declares this Activity in its manifest
 * entry; the system launches it (instead of rendering straight away) the first
 * time the user adds the complication to a slot, passing the numeric instance id
 * and the data-source [ComponentName] as extras. This screen writes the chosen
 * VIN into [ComplicationCarStore] keyed by (dataSource, complicationId) — the same
 * store the complications read in onComplicationRequest — then asks the system
 * ([ComplicationDataSourceUpdateRequester]) to re-render that one instance
 * immediately. setResult(RESULT_OK/RESULT_CANCELED) reports the outcome back to
 * the watch-face configuration flow.
 */
class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val complicationId = intent.getIntExtra(EXTRA_COMPLICATION_ID, -1)
        // The provider ComponentName's short class name (e.g. "ChargeComplication")
        // doubles as the per-data-source key used throughout ComplicationCarStore —
        // this app has several complication services, each configuring its own slots.
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
            var settings by remember { mutableStateOf<WearSettingsPayload?>(null) }
            // Read back the currently-pinned VIN so reopening this screen highlights
            // the active selection (and "Follow selected" when unpinned).
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
                    contentPadding = PaddingValues(horizontal = roundSafeHorizontalPadding(), vertical = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { ListHeader { Text("Show which car?", textAlign = TextAlign.Center) } }
                    if (!loaded) {
                        item {
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
                                "No cars yet — sign in on your phone first, then it syncs here.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (cars.isNotEmpty()) {
                        // "Follow selected" mirrors the Settings Tile pool: unpin so
                        // this slot tracks whatever car is selected in the app.
                        item {
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

    /**
     * Pin this instance to a specific car: persist the VIN keyed by (dataSource,
     * complicationId), tell the system to re-request data for that instance right
     * away (runCatching since the requester can fail if the data source was
     * unregistered — non-fatal, the pin is saved and applies on the next natural
     * refresh), then close with RESULT_OK.
     */
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

    /** Unpin this instance so it follows the currently-selected car again — same
     *  immediate-refresh-then-close pattern as [choose]. */
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
        // Stable android.support.wearable extras delivered by the complication picker.
        private const val EXTRA_COMPLICATION_ID =
            "android.support.wearable.complications.EXTRA_CONFIG_COMPLICATION_ID"
        private const val EXTRA_PROVIDER_COMPONENT =
            "android.support.wearable.complications.EXTRA_CONFIG_PROVIDER_COMPONENT"
    }
}
