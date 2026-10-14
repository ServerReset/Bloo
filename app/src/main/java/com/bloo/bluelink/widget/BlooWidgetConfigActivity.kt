package com.bloo.bluelink.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import coil.compose.AsyncImage
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.imageUrl
import com.bloo.bluelink.data.stateLineOrParked
import kotlinx.coroutines.launch

/**
 * The widget's settings: a live preview at the top that changes as you choose, then the car (as photo cards),
 * the look (photo background and how soft it is, the charge bar and chips, compact spacing) and the controls
 * (as chips you switch on and off). Runs when a widget is added and again from the launcher's "reconfigure".
 *
 * Never a dead end: with no cars (or sealed after a restart) it still offers "Add widget", which places the
 * widget showing its own empty state, rather than a message and a back button that silently cancels.
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
                            ConfigScreen(l, widgetId) { resultCode -> setResult(resultCode, resultIntent(widgetId)); finish() }
                        }
                    }
                }
            }
        }
    }
}

private fun resultIntent(widgetId: Int) =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

/** Everything the screen folds in one load: the cars (with their photos), and the widget's existing options. */
private data class Loaded(
    val sealed: Boolean,
    val cars: List<VehicleSnapshot>,
    val photos: Map<String, String>,
    val choice: Choice,
) {
    companion object {
        fun empty() = Loaded(false, emptyList(), emptyMap(), Choice())
    }
}

/** What the user is choosing; one value so the preview and the save read the same thing. */
private data class Choice(
    val vin: String? = null,
    val climate: Boolean = true,
    val charge: Boolean = true,
    val flash: Boolean = true,
    val refresh: Boolean = true,
    val compact: Boolean = false,
    val photo: Boolean = true,
    val blur: Int = WidgetPhoto.BLUR_MEDIUM,
    val extras: Boolean = true,
)

private suspend fun loadConfig(context: Context, widgetId: Int): Loaded {
    val sealed = BootGate.isLocked(context)
    val cars = if (sealed) emptyList() else runCatching { SnapshotStore(context).current().vehicles }.getOrDefault(emptyList())
    val settings = SettingsStore(context)
    val photos = cars.mapNotNull { car ->
        runCatching { settings.imageUrl(car.vin) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { car.vin to it }
    }.toMap()
    // Existing options (a reconfigure), or the defaults when the widget is not bound yet.
    val prefs: Preferences = runCatching {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    }.getOrDefault(emptyPreferences())
    return Loaded(
        sealed = sealed,
        cars = cars,
        photos = photos,
        choice = Choice(
            vin = prefs[WidgetKeys.VIN] ?: cars.firstOrNull()?.vin,
            climate = prefs[WidgetKeys.SHOW_CLIMATE] ?: true,
            charge = prefs[WidgetKeys.SHOW_CHARGE] ?: true,
            flash = prefs[WidgetKeys.SHOW_FLASH] ?: true,
            refresh = prefs[WidgetKeys.SHOW_REFRESH] ?: true,
            compact = prefs[WidgetKeys.COMPACT] ?: false,
            photo = prefs[WidgetKeys.SHOW_PHOTO] ?: true,
            blur = prefs[WidgetKeys.PHOTO_BLUR] ?: WidgetPhoto.BLUR_MEDIUM,
            extras = prefs[WidgetKeys.SHOW_EXTRAS] ?: true,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(loaded: Loaded, widgetId: Int, finish: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(loaded.choice) }
    val car = loaded.cars.firstOrNull { it.vin == choice.vin } ?: loaded.cars.firstOrNull()
    val photoUrl = car?.let { loaded.photos[it.vin] }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Widgets, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your Bloo widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Make it yours. You can change all of this later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The live preview: every choice below shows up here as you make it.
            WidgetPreview(car, photoUrl, choice)

            when {
                loaded.sealed -> Hint("Open Bloo and sign in to see your car. The widget can still be added and fills in once you do.")
                loaded.cars.isEmpty() -> Hint("No cars yet. Add one in Bloo and the widget picks it up by itself.")
                else -> Section("Car", Icons.Filled.DirectionsCar) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(loaded.cars, key = { it.vin }) { c ->
                            CarCard(c, loaded.photos[c.vin], selected = c.vin == car?.vin) { choice = choice.copy(vin = c.vin) }
                        }
                    }
                }
            }

            Section("Look", Icons.Filled.Palette) {
                OptionRow(
                    Icons.Filled.Photo, "Photo background",
                    if (photoUrl == null && car != null) "Add a photo of ${car.name} in Bloo to use it here" else "Your car's photo, softly blurred",
                    choice.photo,
                ) { choice = choice.copy(photo = it) }
                if (choice.photo) {
                    Text("Softness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val options = listOf("Light" to WidgetPhoto.BLUR_LIGHT, "Medium" to WidgetPhoto.BLUR_MEDIUM, "Strong" to WidgetPhoto.BLUR_STRONG)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (label, level) ->
                            SegmentedButton(
                                selected = choice.blur == level,
                                onClick = { choice = choice.copy(blur = level) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OptionRow(Icons.Filled.Bolt, "Charge bar and status", "Shown on taller widgets where there is room", choice.extras) {
                    choice = choice.copy(extras = it)
                }
                OptionRow(Icons.Filled.ViewCompact, "Compact", "Tighter spacing and fewer lines", choice.compact) {
                    choice = choice.copy(compact = it)
                }
            }

            Section("Controls", Icons.Filled.TouchApp) {
                Text(
                    "Tap to choose what the widget can do. Unlock always asks for a second tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlChip("Lock", Icons.Filled.Lock, selected = true, enabled = false) {}
                    ControlChip("Climate", Icons.Filled.AcUnit, choice.climate) { choice = choice.copy(climate = !choice.climate) }
                    ControlChip("Charge", Icons.Filled.Bolt, choice.charge) { choice = choice.copy(charge = !choice.charge) }
                    ControlChip("Flash", Icons.Filled.FlashOn, choice.flash) { choice = choice.copy(flash = !choice.flash) }
                    ControlChip("Refresh", Icons.Filled.Refresh, choice.refresh) { choice = choice.copy(refresh = !choice.refresh) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = { finish(Activity.RESULT_CANCELED) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
            Button(
                onClick = { saveAndFinish(context, scope, widgetId, choice, finish) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (loaded.cars.isEmpty()) "Add widget" else "Done", fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * A stand-in of the widget as it will look (a medium size), drawn with ordinary Compose so it can change live
 * as the choices change: the photo backdrop and its softness, the readout, the bar and the controls.
 */
@Composable
private fun WidgetPreview(car: VehicleSnapshot?, photoUrl: String?, choice: Choice) {
    val scheme = MaterialTheme.colorScheme
    val pad by animateDpAsState(if (choice.compact) 12.dp else 18.dp, label = "previewPad")
    val blurRadius = when (choice.blur) {
        WidgetPhoto.BLUR_LIGHT -> 6.dp
        WidgetPhoto.BLUR_STRONG -> 28.dp
        else -> 14.dp
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .padding(18.dp),
    ) {
        // A small "homescreen" behind the preview, so the widget reads as an object on a wallpaper.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(26.dp))
                .background(scheme.surfaceContainer),
        ) {
            if (choice.photo && photoUrl != null) {
                AsyncImage(
                    model = if (photoUrl.startsWith("/")) java.io.File(photoUrl) else photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.15f).blur(blurRadius),
                )
                Box(Modifier.fillMaxSize().background(scheme.surface.copy(alpha = if (isSystemInDarkTheme()) 0.55f else 0.65f)))
            }
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(car?.name ?: "Your car", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (choice.refresh) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Refresh, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(car?.bigValue() ?: "80%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                        Text(
                            car?.stateLineOrParked() ?: "Locked · 210 mi",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (choice.extras && !choice.compact) {
                            Spacer(Modifier.height(6.dp))
                            val pct = (car?.percent ?: 80) / 100f
                            Box(Modifier.fillMaxWidth(0.9f).height(6.dp).clip(CircleShape).background(scheme.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(pct).height(6.dp).clip(CircleShape).background(scheme.primary))
                            }
                        }
                    }
                    // The controls, as the widget's round buttons.
                    val controls = buildList {
                        add(Icons.Filled.Lock)
                        if (choice.climate) add(Icons.Filled.AcUnit)
                        if (choice.charge) add(Icons.Filled.Bolt)
                        if (choice.flash) add(Icons.Filled.FlashOn)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        controls.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { icon ->
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A titled group: an icon, a heading, and its options on a soft panel. */
@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) { content() }
    }
}

/** A car to show, as a card with its photo (or the palette when it has none); the chosen one is ringed. */
@Composable
private fun CarCard(car: VehicleSnapshot, photoUrl: String?, selected: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "carRing")
    val lift by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.6f), label = "carLift")
    Column(
        Modifier
            .width(150.dp)
            .scale(lift)
            .clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(2.dp, ring), RoundedCornerShape(22.dp))
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest)
            .clickable(onClick = onSelect),
    ) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = if (photoUrl.startsWith("/")) java.io.File(photoUrl) else photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.DirectionsCar, null,
                    tint = scheme.onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp).align(Alignment.Center),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(car.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(car.bigValue(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ControlChip(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        shape = CircleShape,
        modifier = Modifier.height(40.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun saveAndFinish(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    widgetId: Int,
    choice: Choice,
    finish: (Int) -> Unit,
) {
    scope.launch {
        // The widget may not be bound yet (its first add). Either way the launcher places it; on a failure it
        // simply uses its defaults, so never block finishing on the write.
        runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                p.toMutablePreferences().also { m ->
                    m.remove(WidgetKeys.VIN)
                    choice.vin?.let { m[WidgetKeys.VIN] = it }
                    m[WidgetKeys.SHOW_CLIMATE] = choice.climate
                    m[WidgetKeys.SHOW_CHARGE] = choice.charge
                    m[WidgetKeys.SHOW_FLASH] = choice.flash
                    m[WidgetKeys.SHOW_REFRESH] = choice.refresh
                    m[WidgetKeys.COMPACT] = choice.compact
                    m[WidgetKeys.SHOW_PHOTO] = choice.photo
                    m[WidgetKeys.PHOTO_BLUR] = choice.blur
                    m[WidgetKeys.SHOW_EXTRAS] = choice.extras
                }
            }
            BlooWidget().update(context, glanceId)
        }
        finish(Activity.RESULT_OK)
    }
}
