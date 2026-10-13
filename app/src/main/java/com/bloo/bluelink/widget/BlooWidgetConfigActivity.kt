package com.bloo.bluelink.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.launch

/**
 * The widget's configuration screen: which car it shows, and its options (which controls appear, and
 * how tight it packs). Runs when a widget is added and again from the launcher's "reconfigure".
 *
 * It is deliberately never a dead end: with no cars (or sealed after a restart) it still offers
 * "Add widget", which finishes successfully so the widget is placed and shows its own empty state --
 * rather than leaving the user with a message and a back button that silently cancels.
 */
class BlooWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelled unless the user finishes, so backing out does not place a widget by accident.
        setResult(RESULT_CANCELED, resultIntent(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val loaded by produceState<Loaded?>(null) {
                        value = runCatching { loadConfig(context, widgetId) }.getOrDefault(Loaded.empty())
                    }
                    val l = loaded
                    if (l == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        key(l) {
                            ConfigBody(l, widgetId) { resultCode -> setResult(resultCode, resultIntent(widgetId)); finish() }
                        }
                    }
                }
            }
        }
    }
}

private fun resultIntent(widgetId: Int) =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

/** Everything the screen folds in one load: the cars, and the widget's existing options. */
private data class Loaded(
    val sealed: Boolean,
    val cars: List<VehicleSnapshot>,
    val vin: String?,
    val climate: Boolean,
    val charge: Boolean,
    val flash: Boolean,
    val refresh: Boolean,
    val compact: Boolean,
) {
    companion object {
        fun empty() = Loaded(false, emptyList(), null, true, true, true, true, false)
    }
}

private suspend fun loadConfig(context: Context, widgetId: Int): Loaded {
    val sealed = BootGate.isLocked(context)
    val cars = if (sealed) emptyList() else runCatching { SnapshotStore(context).current().vehicles }.getOrDefault(emptyList())
    // Existing options (a reconfigure), or the defaults when the widget is not bound yet.
    val prefs: Preferences = runCatching {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    }.getOrDefault(emptyPreferences())
    return Loaded(
        sealed = sealed,
        cars = cars,
        vin = prefs[WidgetKeys.VIN],
        climate = prefs[WidgetKeys.SHOW_CLIMATE] ?: true,
        charge = prefs[WidgetKeys.SHOW_CHARGE] ?: true,
        flash = prefs[WidgetKeys.SHOW_FLASH] ?: true,
        refresh = prefs[WidgetKeys.SHOW_REFRESH] ?: true,
        compact = prefs[WidgetKeys.COMPACT] ?: false,
    )
}

@Composable
private fun ConfigBody(loaded: Loaded, widgetId: Int, finish: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vin by remember { mutableStateOf(loaded.vin ?: loaded.cars.firstOrNull()?.vin) }
    var climate by remember { mutableStateOf(loaded.climate) }
    var charge by remember { mutableStateOf(loaded.charge) }
    var flash by remember { mutableStateOf(loaded.flash) }
    var refresh by remember { mutableStateOf(loaded.refresh) }
    var compact by remember { mutableStateOf(loaded.compact) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Bloo widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Your car at a glance. Everything below can be changed later from the launcher.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when {
                loaded.sealed -> Hint("Open Bloo and sign in to see your car. The widget can still be added and will fill in once you do.")
                loaded.cars.isEmpty() -> Hint("No cars yet. Add one in Bloo; the widget will pick it up automatically.")
                else -> {
                    Text("Car", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    loaded.cars.forEach { car ->
                        CarChoice(car, selected = car.vin == vin, onSelect = { vin = car.vin })
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            Text("Controls", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OptionRow("Climate", "Heat or cool the car", climate) { climate = it }
            OptionRow("Charge", "Start or stop charging", charge) { charge = it }
            OptionRow("Flash lights", "Flash the hazards", flash) { flash = it }
            OptionRow("Refresh", "Re-fetch the car's status", refresh) { refresh = it }
            Spacer(Modifier.height(16.dp))

            Text("Density", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OptionRow("Compact", "Tighter spacing, no detail line", compact) { compact = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { finish(Activity.RESULT_CANCELED) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { saveAndFinish(context, scope, widgetId, vin, climate, charge, flash, refresh, compact, finish) }, modifier = Modifier.weight(1f)) {
                Text(if (loaded.cars.isEmpty()) "Add widget" else "Done")
            }
        }
    }
}

private fun saveAndFinish(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    widgetId: Int,
    vin: String?,
    climate: Boolean,
    charge: Boolean,
    flash: Boolean,
    refresh: Boolean,
    compact: Boolean,
    finish: (Int) -> Unit,
) {
    scope.launch {
        // The widget may not be bound yet (its first add). Either way the launcher places it; on a
        // failure it simply uses its defaults, so never block finishing on the write.
        runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                p.toMutablePreferences().also { m ->
                    m.remove(WidgetKeys.VIN)
                    vin?.let { m[WidgetKeys.VIN] = it }
                    m[WidgetKeys.SHOW_CLIMATE] = climate
                    m[WidgetKeys.SHOW_CHARGE] = charge
                    m[WidgetKeys.SHOW_FLASH] = flash
                    m[WidgetKeys.SHOW_REFRESH] = refresh
                    m[WidgetKeys.COMPACT] = compact
                }
            }
            BlooWidget().update(context, glanceId)
        }
        finish(Activity.RESULT_OK)
    }
}

@Composable
private fun Hint(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CarChoice(car: VehicleSnapshot, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(car.name, style = MaterialTheme.typography.titleMedium)
            Text(car.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionRow(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
