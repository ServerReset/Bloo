package com.bloo.bluelink.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import coil.compose.AsyncImage
import com.bloo.bluelink.ui.ActionRow
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.ui.IconBadge
import com.bloo.bluelink.ui.ScreenGutter
import com.bloo.bluelink.ui.SettingsCaption
import com.bloo.bluelink.ui.SettingsGroup
import com.bloo.bluelink.ui.SettingsSegmentedRow
import com.bloo.bluelink.ui.ToggleRow
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SafeMorphTextButton
import com.bloo.uicommon.SegmentOption
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.imageUrl
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
            // The app's own theme, like every other screen, instead of a hand-rolled dynamic-colour scheme.
            BlooTheme {
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
    /** This widget is the slim strip: it gets a shape and an ordered choice of controls instead of the full widget's options. */
    val strip: Boolean,
    val cars: List<VehicleSnapshot>,
    val photos: Map<String, String>,
    val choice: Choice,
) {
    companion object {
        fun empty() = Loaded(false, false, emptyList(), emptyMap(), Choice())
    }
}

/** What the user is choosing; one value so the preview and the save read the same thing. */
internal data class Choice(
    val vin: String? = null,
    val climate: Boolean = true,
    val charge: Boolean = true,
    val flash: Boolean = true,
    val refresh: Boolean = true,
    val compact: Boolean = false,
    val photo: Boolean = true,
    val blur: Int = WidgetPhoto.BLUR_MEDIUM,
    val extras: Boolean = true,
    val corner: WidgetCorner = WidgetCorner.MEDIUM,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    val levelTint: Boolean = false,
    val stripShape: StripShape = StripShape.PILL,
    /** The strip's buttons, most important first: a narrower strip keeps the front of this list. */
    val stripControls: List<StripControl> = StripControl.entries,
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
    val strip = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.className == BlooStripWidgetReceiver::class.java.name
    }.getOrDefault(false)
    return Loaded(
        sealed = sealed,
        strip = strip,
        cars = cars,
        photos = photos,
        choice = WidgetOptions.of(prefs).let { o ->
            // The same reading of the stored options the widget itself uses ([WidgetOptions.of]), so this screen and the
            // widget can never disagree about what is stored.
            Choice(
                vin = prefs[WidgetKeys.VIN] ?: cars.firstOrNull()?.vin,
                climate = o.climate, charge = o.charge, flash = o.flash, refresh = o.refresh,
                compact = o.compact, photo = o.photo, blur = o.blur, extras = o.extras,
                corner = o.corner, textSize = o.textSize, levelTint = o.levelTint,
                stripShape = StripShape.entries.getOrElse(prefs[WidgetKeys.STRIP_SHAPE] ?: 0) { StripShape.PILL },
                stripControls = StripControl.of(prefs, o),
            )
        },
    )
}

@Composable
private fun ConfigScreen(loaded: Loaded, widgetId: Int, finish: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(loaded.choice) }
    val car = loaded.cars.firstOrNull { it.vin == choice.vin } ?: loaded.cars.firstOrNull()
    val photoUrl = car?.let { loaded.photos[it.vin] }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenGutter),
            verticalArrangement = Arrangement.spacedBy(GapSection),
        ) {
            Spacer(Modifier.height(GapRow))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapGroup)) {
                IconBadge(icon = Icons.Filled.Widgets, tint = MaterialTheme.colorScheme.primary, size = 44.dp, iconSize = 24.dp)
                Column {
                    RevealText("Your Bloo widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    SettingsCaption("Make it yours. You can change all of this later.", bottomGap = 0.dp)
                }
            }

            // The live preview: every choice below shows up here as you make it.
            // The preview at a chosen size, from the widget's own plan: tall and narrow shows what a tall and narrow
            // widget does, not just a medium one.
            if (loaded.strip) {
                // Buttons per width: the strip keeps the front of the list and drops the rest as it narrows.
                var stripWidth by remember { mutableStateOf(330) }
                SettingsSegmentedRow(
                    label = "Preview width",
                    options = listOf(SegmentOption("250", "Narrow"), SegmentOption("330", "Medium"), SegmentOption("410", "Wide")),
                    selectedKey = stripWidth.toString(),
                ) { stripWidth = it.toInt() }
                StripPreview(car, photoUrl, choice, stripControlCount(stripWidth.toFloat()))
            } else {
                var previewSize by remember { mutableStateOf(PreviewSize.TALL) }
                SettingsSegmentedRow(
                    label = "Preview size",
                    options = PreviewSize.entries.map { SegmentOption(it.name, it.label) },
                    selectedKey = previewSize.name,
                ) { previewSize = PreviewSize.valueOf(it) }
                WidgetPreview(car, photoUrl, choice, previewSize)
            }

            when {
                loaded.sealed -> NoteGroup("Open Bloo and sign in to see your car. The widget can still be added and fills in once you do.")
                loaded.cars.isEmpty() -> NoteGroup("No cars yet. Add one in Bloo and the widget picks it up by itself.")
                else -> SettingsGroup("Car") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(GapGroup)) {
                        items(loaded.cars, key = { it.vin }) { c ->
                            CarCard(c, loaded.photos[c.vin], selected = c.vin == car?.vin) { choice = choice.copy(vin = c.vin) }
                        }
                    }
                }
            }

            if (loaded.strip) {
                SettingsGroup("Shape") {
                    SettingsSegmentedRow(
                        label = "Corners",
                        options = StripShape.entries.map { SegmentOption(it.name, it.label) },
                        selectedKey = choice.stripShape.name,
                    ) { choice = choice.copy(stripShape = StripShape.valueOf(it)) }
                }
            }

            SettingsGroup("Background") {
                ToggleRow(
                    "Car photo",
                    choice.photo,
                    description = if (photoUrl == null && car != null) "Add a photo of ${car.name} in Bloo to use it here" else "Your car's photo, softly blurred behind the widget",
                ) { choice = choice.copy(photo = it) }
            }

            SettingsGroup("Controls") {
                if (loaded.strip) {
                    SettingsCaption("Tap to add or remove. The strip keeps the first ones you picked when it is narrow, and drops the rest. Unlock always asks for a second tap.", bottomGap = 0.dp)
                    ActionRow {
                        StripControl.entries.forEach { c ->
                            val index = choice.stripControls.indexOf(c)
                            SafeMorphTextButton(
                                if (index >= 0) "${index + 1} ${c.label}" else c.label,
                                onClick = {
                                    val now = choice.stripControls
                                    // Never empty: a strip with no button is just a label.
                                    choice = choice.copy(stripControls = if (c in now) (now - c).ifEmpty { now } else now + c)
                                },
                                icon = stripControlIcon(c),
                                active = index >= 0,
                            )
                        }
                    }
                } else {
                    SettingsCaption("Tap to choose what the widget can do. Unlock always asks for a second tap.", bottomGap = 0.dp)
                    ActionRow {
                        SafeMorphTextButton("Lock", onClick = {}, icon = Icons.Filled.Lock, active = true, enabled = false)
                        SafeMorphTextButton("Climate", onClick = { choice = choice.copy(climate = !choice.climate) }, icon = Icons.Filled.AcUnit, active = choice.climate)
                        SafeMorphTextButton("Charge", onClick = { choice = choice.copy(charge = !choice.charge) }, icon = Icons.Filled.Bolt, active = choice.charge)
                        SafeMorphTextButton("Flash", onClick = { choice = choice.copy(flash = !choice.flash) }, icon = Icons.Filled.FlashOn, active = choice.flash)
                        SafeMorphTextButton("Refresh", onClick = { choice = choice.copy(refresh = !choice.refresh) }, icon = Icons.Filled.Refresh, active = choice.refresh)
                    }
                }
            }
            Spacer(Modifier.height(GapRow))
        }
        ActionRow(Modifier.padding(horizontal = ScreenGutter, vertical = GapGroup)) {
            SafeMorphTextButton("Cancel", onClick = { finish(Activity.RESULT_CANCELED) })
            SafeMorphTextButton(
                if (loaded.cars.isEmpty()) "Add widget" else "Done",
                onClick = { saveAndFinish(context, scope, widgetId, choice, finish) },
                emphasis = ButtonEmphasis.Confirm,
            )
        }
    }
}

/** A note where a group's controls would be (no cars, or sealed): the app's standard group with one caption. */
@Composable
private fun NoteGroup(text: String) = SettingsGroup { SettingsCaption(text, bottomGap = 0.dp) }

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

private fun stripControlIcon(c: StripControl) = when (c) {
    StripControl.LOCK -> Icons.Filled.Lock
    StripControl.CLIMATE -> Icons.Filled.AcUnit
    StripControl.CHARGE -> Icons.Filled.Bolt
    StripControl.FLASH -> Icons.Filled.FlashOn
}

/** The strip as the settings draw it: its shape, photo and the first [count] chosen buttons, as the real strip would keep them. */
@Composable
private fun StripPreview(car: VehicleSnapshot?, photoUrl: String?, choice: Choice, count: Int) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(choice.stripShape.squareDp?.dp ?: 50.dp)
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(shape).background(scheme.surfaceContainerHigh),
    ) {
        if (choice.photo && photoUrl != null) {
            AsyncImage(
                model = if (photoUrl.startsWith("/")) java.io.File(photoUrl) else photoUrl,
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(scheme.surface.copy(alpha = 0.6f)))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(car?.name ?: "Your car", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(car?.bigValue() ?: "82%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            choice.stripControls.take(count).forEach { c ->
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(stripControlIcon(c), null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
        }
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
                    m[WidgetKeys.CORNER] = choice.corner.ordinal
                    m[WidgetKeys.TEXT_SIZE] = choice.textSize.ordinal
                    m[WidgetKeys.LEVEL_TINT] = choice.levelTint
                    m[WidgetKeys.STRIP_SHAPE] = choice.stripShape.ordinal
                    m[WidgetKeys.STRIP_CONTROLS] = StripControl.encode(choice.stripControls)
                }
            }
            // The strip shares this screen: redraw whichever widget this id belongs to.
            if (GlanceAppWidgetManager(context).getGlanceIds(BlooStripWidget::class.java).contains(glanceId)) {
                BlooStripWidget().update(context, glanceId)
            } else {
                BlooWidget().update(context, glanceId)
            }
        }
        finish(Activity.RESULT_OK)
    }
}
