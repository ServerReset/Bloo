package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.ui.BlooTheme
import kotlinx.coroutines.launch

/**
 * Configures one home-screen widget: which car it's pinned to and what its four
 * buttons do. Launched both on first placement and from the launcher's long-press
 * "settings" (the provider is declared reconfigurable). Preloads any existing
 * config so reconfiguring is non-destructive.
 */
class WidgetConfigActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        // Default to cancelled so backing out doesn't leave a half-set widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            BlooTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WidgetConfigScreen(
                        widgetId = widgetId,
                        onDone = { finishWith(widgetId) },
                        onCancel = { finish() },
                    )
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

    LaunchedEffect(Unit) {
        cars = SnapshotStore(context).current().vehicles
        val existing = SettingsStore(context).widgetConfig(widgetId)
        if (existing != null) {
            selectedVin = existing.first
            if (existing.second.isNotEmpty()) {
                actions.clear()
                actions.addAll(existing.second.take(4))
                while (actions.size < 4) actions.add(WidgetAction.OPEN.key)
            }
        } else {
            selectedVin = cars.firstOrNull()?.vin
        }
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Bloo widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Pick a car and what each of the four buttons does.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (loaded && cars.isEmpty()) {
            Text(
                "No cars yet — open Bloo and sign in first, then add the widget.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            return@Column
        }

        // ---- Car selection ----
        Text("Car", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                cars.forEach { car ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = car.vin == selectedVin, onClick = { selectedVin = car.vin })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = car.vin == selectedVin, onClick = { selectedVin = car.vin })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(car.name, fontWeight = FontWeight.Medium)
                            Text(
                                car.model,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- Button assignment ----
        Text("Buttons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Button ${i + 1}",
                            modifier = Modifier.width(84.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        ActionPicker(
                            currentKey = actions.getOrNull(i) ?: WidgetAction.OPEN.key,
                            onPick = { key -> actions[i] = key },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val vin = selectedVin ?: return@Button
                scope.launch {
                    SettingsStore(context).setWidgetConfig(widgetId, vin, actions.toList())
                    onDone()
                }
            },
            enabled = selectedVin != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save widget") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ActionPicker(currentKey: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = WidgetAction.fromKey(currentKey) ?: WidgetAction.OPEN
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current.label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WidgetAction.ALL.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = { onPick(action.key); open = false },
                )
            }
        }
    }
}
