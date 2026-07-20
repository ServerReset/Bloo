package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var photoBg by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var pillShape by remember { mutableStateOf(false) }
    var layoutMode by remember { mutableStateOf("info") }
    var backgroundAlpha by remember { mutableStateOf(0) }
    val actions = remember { mutableStateListOf<String>().apply { addAll(WidgetAction.DEFAULTS.map { it.key }) } }

    LaunchedEffect(Unit) {
        val store = SettingsStore(context)
        cars = SnapshotStore(context).current().vehicles
        requireAuth = store.widgetRequireAuth(widgetId)
        photoBg = store.widgetPhotoBackground(widgetId)
        showLocation = store.widgetShowLocation(widgetId)
        pillShape = store.widgetPillShape(widgetId)
        layoutMode = store.widgetLayoutMode(widgetId)
        backgroundAlpha = store.widgetBackgroundAlpha(widgetId)
        val existing = store.widgetConfig(widgetId)
        if (existing != null) {
            selectedVin = existing.first.takeIf { vin -> cars.any { it.vin == vin } } ?: cars.firstOrNull()?.vin
            if (existing.second.isNotEmpty()) { actions.clear(); actions.addAll(existing.second.take(4)) }
        } else {
            selectedVin = cars.firstOrNull()?.vin
        }
        loaded = true
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp).safeDrawingPadding(),
    ) {
        Text("Widget setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Choose a car and up to 4 actions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (loaded && cars.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("No cars yet — sign in to Bloo first.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            MorphButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close", fontWeight = FontWeight.SemiBold) }
            return@Column
        }

        SectionLabel("Car")
        cars.forEach { car ->
            MorphButton(
                onClick = { selectedVin = car.vin },
                active = car.vin == selectedVin,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(car.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(car.model, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(5.dp))
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Buttons")
        // Two-column chip grid — far more compact than one chip per row.
        WidgetAction.ALL.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { action ->
                    MorphChip(
                        selected = action.key in actions,
                        onClick = {
                            if (action.key in actions) actions.remove(action.key)
                            else if (actions.size < 4) actions.add(action.key)
                        },
                        label = action.label,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel("Options")
        MorphChip(requireAuth, { requireAuth = !requireAuth }, "Require unlock for actions", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        MorphChip(photoBg, { photoBg = !photoBg }, "Use car photo as background", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        MorphChip(showLocation, { showLocation = !showLocation }, "Show location map (large widgets)", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        MorphChip(pillShape, { pillShape = !pillShape }, "Pill shape (extreme rounding)", Modifier.fillMaxWidth())
        // Pill shape silently no-ops above ~1.5x1.5 home-screen cells (the
        // rounding needs padding room the layout only reserves at that size)
        // -- said outright instead of leaving it a mystery why nothing changed.
        if (pillShape) {
            Spacer(Modifier.height(3.dp))
            Text(
                "Only visible on widgets sized about 2×2 cells or smaller.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Background transparency", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().alpha(if (photoBg) 0.4f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val scheme = MaterialTheme.colorScheme
            com.bloo.uicommon.AnimatedSlider(
                value = backgroundAlpha.toFloat(),
                onValueChange = { backgroundAlpha = it.roundToInt() },
                valueRange = 0f..9f,
                steps = 8,
                accent = scheme.primary,
                inactiveColor = scheme.surfaceContainerHighest,
                dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
                dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                reduceMotion = false,
                onStepTick = { },
                onSettle = { },
            )
        }
        Text(
            when {
                // A photo background fully overrides this tint (see BlooWidget's
                // photoBgActive branch) -- the slider stayed interactive with no
                // indication it currently does nothing.
                photoBg -> "Not used with a photo background"
                backgroundAlpha == 0 -> "Opaque"
                backgroundAlpha == 9 -> "Nearly transparent"
                else -> "Level ${backgroundAlpha}/9"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text("Layout", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        com.bloo.bluelink.ui.MorphSegmented(
            options = listOf(
                com.bloo.bluelink.ui.SegmentOption("info", "Info", null),
                com.bloo.bluelink.ui.SegmentOption("controls", "Controls", null),
            ),
            selectedKey = layoutMode,
            onSelect = { layoutMode = it },
        )

        Spacer(Modifier.height(18.dp))
        MorphButton(
            onClick = {
                val vin = selectedVin ?: return@MorphButton
                scope.launch {
                    val store = SettingsStore(context)
                    store.setWidgetConfig(widgetId, vin, actions.toList())
                    store.setWidgetRequireAuth(widgetId, requireAuth)
                    store.setWidgetPhotoBackground(widgetId, photoBg)
                    store.setWidgetShowLocation(widgetId, showLocation)
                    store.setWidgetPillShape(widgetId, pillShape)
                    store.setWidgetLayoutMode(widgetId, layoutMode)
                    store.setWidgetBackgroundAlpha(widgetId, backgroundAlpha)
                    onDone()
                }
            },
            enabled = selectedVin != null,
            modifier = Modifier.fillMaxWidth(),
        ) { MorphButtonLabel(Icons.Default.Check, "Save", pending = false, iconSize = 18.dp) }
        Spacer(Modifier.height(6.dp))
        MorphTextButton("Cancel", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}
