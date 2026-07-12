package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.ui.MorphButton
import com.bloo.bluelink.ui.MorphButtonLabel
import com.bloo.bluelink.ui.MorphChip
import com.bloo.bluelink.ui.MorphTextButton
import com.bloo.bluelink.ui.ToggleRow
import kotlinx.coroutines.launch

class WidgetConfigActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContent {
            BlooTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WidgetConfigScreen(widgetId, onDone = { finishWith(widgetId) }, onCancel = { finish() })
                }
            }
        }
    }

    private fun finishWith(widgetId: Int) {
        lifecycleScope.launch {
            runCatching { BlooWidget().updateAll(applicationContext) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun WidgetConfigScreen(widgetId: Int, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cars by remember { mutableStateOf<List<VehicleSnapshot>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedVin by remember { mutableStateOf<String?>(null) }
    val actions = remember { mutableStateListOf<String>().apply { addAll(WidgetAction.DEFAULTS.map { it.key }) } }
    var requireAuth by remember { mutableStateOf(true) }
    var showPhotoBg by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cars = SnapshotStore(context).current().vehicles
        val existing = SettingsStore(context).widgetConfig(widgetId)
        if (existing != null) {
            selectedVin = existing.first.takeIf { vin -> cars.any { it.vin == vin } } ?: cars.firstOrNull()?.vin
            if (existing.second.isNotEmpty()) { actions.clear(); actions.addAll(existing.second.take(4)) }
        } else { selectedVin = cars.firstOrNull()?.vin }
        requireAuth = SettingsStore(context).widgetRequireAuth(widgetId)
        loaded = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Widget Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Choose a car and assign up to 4 actions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (loaded && cars.isEmpty()) {
            Text("No cars — sign in to Bloo first.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            MorphButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close", fontWeight = FontWeight.SemiBold) }
            return@Column
        }

        // Car picker (compact)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Car", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                cars.forEach { car ->
                    MorphButton(
                        onClick = { selectedVin = car.vin },
                        active = car.vin == selectedVin,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column { Text(car.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium); Text(car.model, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Actions (compact)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Buttons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                WidgetAction.ALL.forEach { action ->
                    MorphChip(
                        selected = action.key in actions,
                        onClick = { if (action.key in actions) actions.remove(action.key) else if (actions.size < 4) actions.add(action.key) },
                        label = action.label,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Options (compact)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ToggleRow(label = "Require authentication", checked = requireAuth, onChange = { requireAuth = it })
            }
        }
        Spacer(Modifier.height(20.dp))

        MorphButton(
            onClick = {
                val vin = selectedVin ?: return@MorphButton
                scope.launch {
                    SettingsStore(context).setWidgetConfig(widgetId, vin, actions.toList())
                    SettingsStore(context).setWidgetRequireAuth(widgetId, requireAuth)
                    onDone()
                }
            },
            enabled = selectedVin != null,
            modifier = Modifier.fillMaxWidth(),
        ) { MorphButtonLabel(Icons.Default.Check, "Save", pending = false) }
        Spacer(Modifier.height(8.dp))
        MorphTextButton("Cancel", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}
