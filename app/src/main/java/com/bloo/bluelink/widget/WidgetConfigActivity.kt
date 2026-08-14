package com.bloo.bluelink.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.ui.MorphButton
import com.bloo.bluelink.ui.MorphChip
import com.bloo.bluelink.ui.MorphSegmented
import com.bloo.bluelink.ui.SegmentOption
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
            // Refresh every placed widget so the just-saved config takes effect. (Glance
            // exposes updateAll as a top-level extension; there's no single-widget `update`
            // extension in this API surface, and repainting all instances is correct here.)
            runCatching { CarWidget().updateAll(applicationContext) }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
            finish()
        }
    }
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
    var photoBackground by remember { mutableStateOf(initial.photoBackground) }
    var priority by remember { mutableStateOf(initial.priority) }
    // effectiveCorner, not corner, so a widget set up before this picker
    // existed opens showing the pill it is actually rendering.
    var corner by remember { mutableStateOf(initial.effectiveCorner) }
    var backgroundOpacity by remember { mutableStateOf(initial.safeBackgroundOpacity) }
    var textScale by remember { mutableStateOf(initial.safeTextScale) }
    var showHeader by remember { mutableStateOf(initial.showHeader) }
    var showFooter by remember { mutableStateOf(initial.showFooter) }
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
            Spacer(Modifier.height(18.dp))

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
            ToggleLine("Show charge gauge", showRing) { showRing = it }
            Spacer(Modifier.height(8.dp))
            // SIMPLE, not advanced. Where the car IS is one of the two things
            // a car widget is for, and this was buried in advanced mode -- so
            // for anyone who had never switched modes, the widget simply had
            // no location, with no indication one was available. It appears on
            // any tile with room to draw it (MEDIUM and up) and no-ops when
            // the car has no coordinates.
            ToggleLine("Show the car's location", showMap) { showMap = it }
            Spacer(Modifier.height(8.dp))
            // Both modes -- a small widget with no photo is just as common a
            // choice as a large advanced one, and this is purely visual, not a
            // knob that changes what data the widget reads. No-ops gracefully
            // to the normal themed surface for a car with no photo set.
            ToggleLine("Use car photo as background", photoBackground) { photoBackground = it }
            Spacer(Modifier.height(8.dp))
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

            // Button labels used to be a user-facing Auto/Always/Off picker here. Removed
            // on request -- it's automatic now (WidgetButtons.ActionButtons' own room
            // check), the same behavior AUTO always was, with no override to configure.

            // --- Small-size priority (both modes) ---
            Spacer(Modifier.height(16.dp))
            SectionLabel("On small sizes")
            Text(
                "Below about 2×2 cells there's only room for one -- pick which wins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            MorphSegmented(
                options = listOf(
                    SegmentOption(WidgetConfig.PRIORITY_INFO, "Info", null),
                    SegmentOption(WidgetConfig.PRIORITY_CONTROLS, "Controls", null),
                ),
                selectedKey = priority,
                onSelect = { priority = it },
            )

            // --- Advanced-only sections ---
            if (advanced) {
                // A visible seam between "the options everyone sees" above and the
                // six extra sections below -- without it this screen was one
                // undifferentiated scroll of pickers the moment Advanced turned
                // them all on at once, which read as far more than it actually
                // is. Same hairline every list divider in the app uses.
                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Info shown")
                ChipFlow {
                    WidgetInfoField.ALL.forEach { f ->
                        ToggleChip(f.label, f.key in infoFields) { on ->
                            if (on) { if (f.key !in infoFields) infoFields.add(f.key) } else infoFields.remove(f.key)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                // A field toggled on here only actually appears once the layout
                // has room for it -- same "declines rather than shrinking into
                // illegibility" contract the ring, hero number and info rows all
                // follow (see WidgetScale.kt). Said outright, because without
                // it a small placed widget makes every checked box here look
                // broken: turn a field on, nothing changes, no explanation why.
                Text(
                    "Fields show when the placed widget has room; a small widget may not fit all of them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))
                // Both only appear where a layout has room for them anyway;
                // turning them off buys that space back for the ring and stats.
                ToggleLine("Show name and status header", showHeader) { showHeader = it }
                Spacer(Modifier.height(8.dp))
                ToggleLine("Show last-updated footer", showFooter) { showFooter = it }

                // --- Shape ---
                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Corners")
                MorphSegmented(
                    options = listOf(
                        SegmentOption(WidgetConfig.CORNER_SHARP, "Sharp", null),
                        SegmentOption(WidgetConfig.CORNER_SOFT, "Soft", null),
                        SegmentOption(WidgetConfig.CORNER_ROUND, "Round", null),
                        SegmentOption(WidgetConfig.CORNER_PILL, "Pill", null),
                    ),
                    selectedKey = corner,
                    onSelect = { corner = it },
                )
                // Pill needs a short enough side to read as a stadium at all, so
                // above roughly 2x2 it falls back to the roundest ordinary corner
                // -- said outright rather than leaving it a mystery why a large
                // widget didn't change.
                if (corner == WidgetConfig.CORNER_PILL) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Full pill only on widgets about 2×2 cells or smaller; larger ones use Round.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // --- Background opacity ---
                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Background")
                if (photoBackground) {
                    Text(
                        "Using the car photo — opacity applies to the plain background only.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("1.0", "Solid", null),
                            SegmentOption("0.75", "75%", null),
                            SegmentOption("0.5", "50%", null),
                            SegmentOption("0.25", "25%", null),
                        ),
                        // Matched by value rather than identity so a config saved
                        // with any other number still lights up its nearest option
                        // instead of showing nothing selected.
                        selectedKey = listOf(1f, 0.75f, 0.5f, 0.25f)
                            .minBy { kotlin.math.abs(it - backgroundOpacity) }.toString(),
                        onSelect = { backgroundOpacity = it.toFloat() },
                    )
                }

                // --- Text size ---
                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Text size")
                MorphSegmented(
                    options = listOf(
                        SegmentOption("0.8", "Small", null),
                        SegmentOption("1.0", "Normal", null),
                        SegmentOption("1.2", "Large", null),
                        SegmentOption("1.4", "Largest", null),
                    ),
                    selectedKey = listOf(0.8f, 1f, 1.2f, 1.4f)
                        .minBy { kotlin.math.abs(it - textScale) }.toString(),
                    onSelect = { textScale = it.toFloat() },
                )
                Spacer(Modifier.height(3.dp))
                // fittedSp's own floor (WidgetScale.kt: never smaller than 78% of
                // its style, never below 9sp) shrinks any line that would
                // otherwise overflow -- which on a small placed widget can pull
                // "Largest" most of the way back down to what "Normal" already
                // looked like. Genuinely applied, just capped by the same space
                // every other module here respects; said outright so a bigger
                // choice with no visible size change doesn't read as ignored.
                Text(
                    "On small widgets, text may shrink back down to fit -- there just isn't room for the largest size.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Accent")
                // Color swatches, not text chips -- matches the app's own theme
                // picker (Settings > Theme > Built-in palettes), where every color
                // choice is shown, not just named. A "Charge green"/"Amber" label
                // alone doesn't tell you what you're about to pick.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentSwatch(MaterialTheme.colorScheme.primary, "Theme", accent == null) { accent = null }
                    WidgetAccent.ALL.forEach { ac ->
                        AccentSwatch(Color(ac.argb), ac.label, accent == ac.key) { accent = ac.key }
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Theme")
                ChipFlow {
                    SelectChip("Auto", theme == WidgetConfig.THEME_AUTO) { theme = WidgetConfig.THEME_AUTO }
                    SelectChip("Light", theme == WidgetConfig.THEME_LIGHT) { theme = WidgetConfig.THEME_LIGHT }
                    SelectChip("Dark", theme == WidgetConfig.THEME_DARK) { theme = WidgetConfig.THEME_DARK }
                }
            }

            Spacer(Modifier.height(28.dp))
            MorphButton(
                onClick = {
                    onSave(
                        WidgetConfig(
                            vin = vin,
                            // Sorted to the chips' own canonical order (the order they're
                            // laid out above), not the order they happened to be toggled
                            // on in -- otherwise the widget's actual button/stat order
                            // depended on click history instead of matching what this
                            // screen visually showed while picking them.
                            actions = actions.sortedBy { key -> WidgetAction.fromKey(key)?.ordinal ?: Int.MAX_VALUE },
                            infoFields = infoFields.sortedBy { key -> WidgetInfoField.fromKey(key)?.ordinal ?: Int.MAX_VALUE },
                            showRing = showRing,
                            showMap = showMap,
                            photoBackground = photoBackground,
                            priority = priority,
                            // The picker fully supersedes the old boolean, so
                            // it's written false here and `corner` is the only
                            // source of truth from this point on -- leaving it
                            // set would let effectiveCorner override a later
                            // switch away from Pill.
                            pillShape = false,
                            corner = corner,
                            backgroundOpacity = backgroundOpacity,
                            textScale = textScale,
                            showHeader = showHeader,
                            showFooter = showFooter,
                            accent = accent,
                            theme = theme,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A faint full-width rule between the advanced-only sections -- the only
 *  thing standing between "six extra sections in a row" and "one undivided
 *  wall of pickers" once Advanced turns them all on at once. */
@Composable private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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

// The app's own selectable-pill idiom (MorphChip, same as every other picker in
// Settings/onboarding/login) laid out in a wrapping flow -- was plain Material3
// FilterChip, which reads as generic stock Android chrome next to the rest of
// the app's morphing-corner, spring-animated controls.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    MorphChip(selected = selected, onClick = onClick, label = label)
}

@Composable private fun ToggleChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    MorphChip(selected = selected, onClick = { onChange(!selected) }, label = label)
}

/** A boolean setting as a full-width [MorphChip] toggle -- the same idiom the
 *  app already uses for every other on/off setting (see e.g. Settings'
 *  "Require unlock for actions" row), rather than a bespoke label+Switch row
 *  that would be the one boolean control in the app not built this way. */
@Composable private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    MorphChip(selected = checked, onClick = { onChange(!checked) }, label = label, modifier = Modifier.fillMaxWidth())
}

/** A round color swatch for picking the widget's accent override -- same shape
 *  language as Settings' own theme-palette picker (a ring that grows in on
 *  selection, a checkmark once selected) so choosing the widget's accent
 *  feels like the same control, not a different one that happens to live in
 *  a different screen. */
@Composable private fun AccentSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentSwatchRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
                .padding(ring)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

