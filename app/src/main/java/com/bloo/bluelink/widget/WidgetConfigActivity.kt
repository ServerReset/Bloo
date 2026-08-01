package com.bloo.bluelink.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ui.BlooTheme
import kotlinx.coroutines.launch

/**
 * Configure screen for a placed [CarWidget], opened on first drop and via the
 * launcher's widget-settings long-press.
 *
 * THE ADAPTIVE PART: the option set shown here follows the app's own simple vs
 * advanced mode ([SettingsStore.settingsMode]). Simple mode shows a clean, small
 * set — pick a car, choose which of the three core toggles to show, and whether to
 * show the status ring. Advanced mode reveals the full control: every action
 * button, the read-only info fields, the location map, an accent colour, and a
 * per-widget theme override. Both edit the same [WidgetConfig]; advanced simply
 * exposes more of it, so a user is never overwhelmed by knobs they didn't ask for.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default result to CANCELED so backing out of first-drop config removes
        // the half-placed widget (the standard configure-activity contract).
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        lifecycleScope.launch {
            val advanced = runCatching { SettingsStore(applicationContext).settingsMode() }.getOrNull() == "advanced"
            val cars = runCatching { SnapshotStore(applicationContext).current().vehicles }.getOrDefault(emptyList())
            val existing = WidgetConfigStore(applicationContext).get(widgetId)
            setContent {
                BlooTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ConfigScreen(
                            advanced = advanced,
                            cars = cars,
                            initial = existing,
                            onSave = { cfg -> save(cfg) },
                        )
                    }
                }
            }
        }
    }

    private fun save(config: WidgetConfig) {
        lifecycleScope.launch {
            WidgetConfigStore(applicationContext).set(widgetId, config)
            runCatching { CarWidget().update(applicationContext, glanceIdFor(widgetId)) }
                .onFailure { runCatching { CarWidget().updateAll(applicationContext) } }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
            finish()
        }
    }

    private suspend fun glanceIdFor(id: Int) =
        androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
            .getGlanceIds(CarWidget::class.java)
            .first { androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext).getAppWidgetId(it) == id }
}

@Composable
private fun ConfigScreen(
    advanced: Boolean,
    cars: List<VehicleSnapshot>,
    initial: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
) {
    var vin by remember { mutableStateOf(initial.vin) }
    val actions = remember { mutableStateListOf<String>().apply { addAll(initial.actions) } }
    val infoFields = remember { mutableStateListOf<String>().apply { addAll(initial.infoFields) } }
    var showRing by remember { mutableStateOf(initial.showRing) }
    var showMap by remember { mutableStateOf(initial.showMap) }
    var accent by remember { mutableStateOf(initial.accent) }
    var theme by remember { mutableStateOf(initial.theme) }

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Widget setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (advanced) "Advanced mode — full control." else "Choose a car and what to show.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            // --- Car (both modes) ---
            SectionLabel("Car")
            ChipFlow {
                if (cars.size > 1) {
                    SelectChip("Follow selected", vin == null) { vin = null }
                }
                cars.forEach { car ->
                    SelectChip(car.name, vin == car.vin) { vin = car.vin }
                }
                if (cars.isEmpty()) {
                    Text("Sign in on the app first.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            // --- Status ring (both modes) ---
            ToggleLine("Show status ring", showRing) { showRing = it }

            // --- Controls ---
            Spacer(Modifier.height(16.dp))
            SectionLabel("Controls")
            val actionChoices = if (advanced) WidgetAction.ALL else WidgetAction.SIMPLE_CHOICES
            ChipFlow {
                actionChoices.forEach { a ->
                    ToggleChip(a.label, a.key in actions) { on ->
                        if (on) { if (a.key !in actions) actions.add(a.key) } else actions.remove(a.key)
                    }
                }
            }

            // --- Advanced-only sections ---
            if (advanced) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Info shown")
                ChipFlow {
                    WidgetInfoField.ALL.forEach { f ->
                        ToggleChip(f.label, f.key in infoFields) { on ->
                            if (on) { if (f.key !in infoFields) infoFields.add(f.key) } else infoFields.remove(f.key)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                ToggleLine("Show location map (large sizes)", showMap) { showMap = it }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Accent")
                ChipFlow {
                    SelectChip("Theme", accent == null) { accent = null }
                    WidgetAccent.ALL.forEach { ac ->
                        SelectChip(ac.label, accent == ac.key) { accent = ac.key }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Theme")
                ChipFlow {
                    SelectChip("Auto", theme == WidgetConfig.THEME_AUTO) { theme = WidgetConfig.THEME_AUTO }
                    SelectChip("Light", theme == WidgetConfig.THEME_LIGHT) { theme = WidgetConfig.THEME_LIGHT }
                    SelectChip("Dark", theme == WidgetConfig.THEME_DARK) { theme = WidgetConfig.THEME_DARK }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onSave(
                        WidgetConfig(
                            vin = vin,
                            actions = actions.toList(),
                            infoFields = infoFields.toList(),
                            showRing = showRing,
                            showMap = showMap,
                            accent = accent,
                            theme = theme,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add widget") }
        }
    }
}

@Composable private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable private fun ToggleChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onChange(!selected) }, label = { Text(label) })
}

@Composable private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
