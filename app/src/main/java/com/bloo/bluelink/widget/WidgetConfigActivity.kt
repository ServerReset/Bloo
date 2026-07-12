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
    var requireAuth by remember { mutableStateOf(true) }
    val actions = remember { mutableStateListOf<String>().apply {
        addAll(WidgetAction.DEFAULTS.map { it.key })
    }}

    LaunchedEffect(Unit) {
        cars = SnapshotStore(context).current().vehicles
        requireAuth = SettingsStore(context).widgetRequireAuth(widgetId)
        val existing = SettingsStore(context).widgetConfig(widgetId)
        if (existing != null) {
            selectedVin = existing.first.takeIf { vin -> cars.any { it.vin == vin } }
                ?: cars.firstOrNull()?.vin
            if (existing.second.isNotEmpty()) {
                actions.clear()
                actions.addAll(existing.second.take(4))
            }
        } else {
            selectedVin = cars.firstOrNull()?.vin
        }
        loaded = true
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Widget Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Pick a car and up to 4 actions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        if (loaded && cars.isEmpty()) {
            Text("No cars yet — sign in to Bloo first.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            MorphButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close", fontWeight = FontWeight.SemiBold) }
            return@Column
        }

        Text("Car", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        cars.forEach { car ->
            MorphButton(
                onClick = { selectedVin = car.vin },
                active = car.vin == selectedVin,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(car.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Text(car.model, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(20.dp))

        Text("Buttons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetAction.ALL.forEach { action ->
                MorphChip(
                    selected = action.key in actions,
                    onClick = {
                        if (action.key in actions) actions.remove(action.key)
                        else if (actions.size < 4) actions.add(action.key)
                    },
                    label = action.label,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        MorphChip(
            selected = requireAuth,
            onClick = { requireAuth = !requireAuth },
            label = "Require unlock for actions",
        )
        Text(
            "Ask for fingerprint or PIN before lock, climate, and charge buttons run.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(28.dp))
        MorphButton(
            onClick = {
                val vin = selectedVin ?: return@MorphButton
                scope.launch {
                    val store = SettingsStore(context)
                    store.setWidgetConfig(widgetId, vin, actions.toList())
                    store.setWidgetRequireAuth(widgetId, requireAuth)
                    onDone()
                }
            },
            enabled = selectedVin != null,
            modifier = Modifier.fillMaxWidth(),
        ) { MorphButtonLabel(Icons.Default.Check, "Save", pending = false, iconSize = 18.dp) }
        Spacer(Modifier.height(8.dp))
        MorphTextButton("Cancel", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}
