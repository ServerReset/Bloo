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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    var showBackground by remember { mutableStateOf(true) }
    var widgetShape by remember { mutableStateOf("rect") }
    var widgetStyle by remember { mutableStateOf("auto") }
    var accentHex by remember { mutableStateOf<String?>(null) }
    var showName by remember { mutableStateOf(true) }
    var showRange by remember { mutableStateOf(true) }
    var showState by remember { mutableStateOf(true) }
    val metrics = remember { mutableStateListOf("battery", "range") }
    var requireAuth by remember { mutableStateOf(true) }

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
        showBackground = SettingsStore(context).widgetShowBackground(widgetId)
        widgetShape = SettingsStore(context).widgetShape(widgetId)
        widgetStyle = SettingsStore(context).widgetStyle(widgetId)
        accentHex = SettingsStore(context).widgetAccent(widgetId)
        showName = SettingsStore(context).widgetShowName(widgetId)
        showRange = SettingsStore(context).widgetShowRange(widgetId)
        showState = SettingsStore(context).widgetShowState(widgetId)
        SettingsStore(context).widgetMetrics(widgetId).let { metrics.clear(); metrics.addAll(it) }
        requireAuth = SettingsStore(context).widgetRequireAuth(widgetId)
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Widget setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Choose which car to show and what each button does.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (loaded && cars.isEmpty()) {
            Text(
                "No cars yet — sign in to Bloo first, then come back to configure the widget.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            return@Column
        }

        // ── Car ────────────────────────────────────────────────────────────────
        SectionHeader("Car")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                cars.forEach { car ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = car.vin == selectedVin, onClick = { selectedVin = car.vin })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = car.vin == selectedVin, onClick = { selectedVin = car.vin })
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(car.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
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

        Spacer(Modifier.height(20.dp))

        // ── Buttons ────────────────────────────────────────────────────────────
        SectionHeader("Buttons")
        Text(
            "Buttons appear on the widget based on the layout. Assign up to four actions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}",
                            modifier = Modifier.width(24.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
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

        Spacer(Modifier.height(20.dp))

        // ── Appearance ─────────────────────────────────────────────────────────
        SectionHeader("Appearance")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Background", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Show the system widget background tint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showBackground, onCheckedChange = { showBackground = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Require authentication", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Authenticate before running widget commands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = requireAuth, onCheckedChange = { requireAuth = it })
                }
                Column {
                    Text("Shape", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("rect" to "Rounded", "pill" to "Pill").forEach { (key, label) ->
                            FilterChip(
                                selected = widgetShape == key,
                                onClick = { widgetShape = key },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Column {
                    Text("Layout", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Auto adapts to size. Minimal = one big number, Stats = metric grid, " +
                            "Ring = charge ring, Photo = your car photo, Dual = two big metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "auto" to "Auto", "minimal" to "Minimal", "stats" to "Stats",
                            "ring" to "Ring", "photo" to "Photo", "dual" to "Dual",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = widgetStyle == key,
                                onClick = { widgetStyle = key },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                // Accent override.
                Column {
                    Text("Accent", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = accentHex == null, onClick = { accentHex = null }, label = { Text("Match app") })
                        listOf("#005AC1", "#2EBD59", "#E5484D", "#7B4DFF", "#00696E", "#F5A623").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .selectable(selected = accentHex == hex) { accentHex = hex },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (accentHex == hex) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Show/hide elements.
                Column {
                    Text("Show", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showName, onClick = { showName = !showName }, label = { Text("Name") })
                        FilterChip(selected = showRange, onClick = { showRange = !showRange }, label = { Text("Range") })
                        FilterChip(selected = showState, onClick = { showState = !showState }, label = { Text("State") })
                    }
                }

                // Metrics shown by the Stats / Dual layouts.
                Column {
                    Text("Metrics (Stats / Dual)", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("battery", "range", "lock", "climate").forEach { m ->
                            FilterChip(
                                selected = m in metrics,
                                onClick = { if (m in metrics) metrics.remove(m) else metrics.add(m) },
                                label = { Text(m.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                val vin = selectedVin ?: return@Button
                scope.launch {
                    SettingsStore(context).setWidgetConfig(widgetId, vin, actions.toList())
                    SettingsStore(context).setWidgetShowBackground(widgetId, showBackground)
                    SettingsStore(context).setWidgetShape(widgetId, widgetShape)
                    SettingsStore(context).setWidgetStyle(widgetId, widgetStyle)
                    SettingsStore(context).setWidgetAccent(widgetId, accentHex)
                    SettingsStore(context).setWidgetShowName(widgetId, showName)
                    SettingsStore(context).setWidgetShowRange(widgetId, showRange)
                    SettingsStore(context).setWidgetShowState(widgetId, showState)
                    SettingsStore(context).setWidgetMetrics(widgetId, metrics.toList())
                    SettingsStore(context).setWidgetRequireAuth(widgetId, requireAuth)
                    onDone()
                }
            },
            enabled = selectedVin != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ActionPicker(currentKey: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = WidgetAction.fromKey(currentKey) ?: WidgetAction.OPEN
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(current.icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(current.label, maxLines = 1, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WidgetAction.ALL.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(action.icon),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(action.label)
                        }
                    },
                    onClick = { onPick(action.key); open = false },
                )
            }
        }
    }
}
