package com.bloo.bluelink.ui

import com.bloo.bluelink.data.VehiclePlatform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.platformOverridable
import kotlin.math.max
import com.bloo.bluelink.data.collapsedSections

/** One option in a [MorphSegmented] control; re-exported from :uicommon. */
typealias SegmentOption = com.bloo.uicommon.SegmentOption

/**
 * A full-width segmented selector: a tonal track whose active segment fills with the primary
 * accent. Thin wrapper over [com.bloo.uicommon.MorphSegmented] supplying M3 colours, label
 * typography and haptics.
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    trackHeight: Dp? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = containerColor ?: buttonContainer(),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurfaceVariant,
        // The one button label style, so a picker matches the buttons beside it.
        textStyle = ButtonLabelStyle,
        onTick = { haptics?.tick() },
        modifier = modifier,
        trackHeight = trackHeight ?: (if (options.any { it.icon != null }) 48.dp else 44.dp),
        // Hairline rim like every other interactive surface.
        borderColor = scheme.outline.copy(alpha = 0.18f),
    )
}

/**
 * A [MorphSegmented] over every entry of an enum: the option key is the enum's name, so call sites
 * state only labels and icons.
 */
@Composable
internal fun <T : Enum<T>> EnumSegmented(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    icon: (T) -> ImageVector? = { null },
) {
    MorphSegmented(
        options = entries.map { SegmentOption(it.name, label(it), icon(it)) },
        selectedKey = selected.name,
        onSelect = { key -> entries.firstOrNull { it.name == key }?.let(onSelect) },
    )
}

/** A car's powertrain (Gas/Hybrid/PHEV/EV), an icon per option. */
@Composable
internal fun PowertrainPicker(current: Powertrain, onSelect: (Powertrain) -> Unit) = EnumSegmented(
    Powertrain.entries, current, onSelect = onSelect,
    label = { when (it) { Powertrain.GAS -> "Gas"; Powertrain.HYBRID -> "Hybrid"; Powertrain.PHEV -> "PHEV"; Powertrain.EV -> "EV" } },
    icon = { when (it) { Powertrain.GAS -> Icons.Filled.LocalGasStation; Powertrain.HYBRID -> Icons.Filled.Bolt; Powertrain.PHEV -> Icons.Filled.Power; Powertrain.EV -> Icons.Filled.FlashOn } },
)

/**
 * A car's confirmed head-unit generation (Gen5W / ccNC). Only shown where [platformOverridable] is
 * true.
 */
@Composable
internal fun PlatformPicker(current: VehiclePlatform, onSelect: (VehiclePlatform) -> Unit) = EnumSegmented(
    VehiclePlatform.entries, current, onSelect = onSelect,
    label = { when (it) { VehiclePlatform.GEN5W -> "Gen5W"; VehiclePlatform.CCNC -> "ccNC" } },
)

/** The three theme modes as segment options, defined once for every call site. */
val ThemeModeOptions = ThemeMode.entries.map { SegmentOption(it.name, it.name.lowercase().replaceFirstChar(Char::uppercase), null) }

/**
 * The Display card's "Appearance" segmented row, shared with the Settings search entry and
 * onboarding.
 */
@Composable
fun ThemeModeSegmentedRow(
    appearance: SettingsStore.Appearance,
    onSelect: (ThemeMode) -> Unit,
) {
    SettingsSegmentedRow(
        label = "Appearance",
        options = ThemeModeOptions,
        selectedKey = appearance.themeMode.name,
        onSelect = { onSelect(ThemeMode.valueOf(it)) },
    )
}

/**
 * A labelled [MorphSegmented]: a caption above a full-width segmented control. Use instead of a
 * switch when the setting is a choice between two equal alternatives (°C/°F) rather than on/off.
 */
@Composable
fun SettingsSegmentedRow(
    label: String,
    options: List<SegmentOption>,
    selectedKey: String,
    /** See ToggleRow's `description`. */
    description: String? = null,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LabelText(label)
        Spacer(Modifier.height(GapRow))
        MorphSegmented(options = options, selectedKey = selectedKey, onSelect = onSelect)
        if (description != null) SettingsCaption(description)
    }
}

/**
 * A Settings card built on [PebbleShell], the same expandable-card system as garage pebbles (shared
 * bounce/close springs, [StaggeredRevealColumn], tonal fill).
 */

/**
 * The outer wrapper every top-level Settings pebble sits in: full width, inter-card gap and
 * TalkBack heading semantics. Shared with the single-car case in SettingsScreen.
 */
@Composable
internal fun Modifier.settingsCardSlot(): Modifier =

    fillMaxWidth()
        .padding(bottom = SettingsCardGap)
        .semantics { heading() }

@Composable
internal fun SettingsCard(
    title: String,
    icon: ImageVector? = null,
    vm: AppViewModel,
    /**
     * For a card whose body is ONE control: render it on the title row and drop the
     * expand/collapse. Unconditional (not gated on simple mode) since there is nothing else to
     * show.
     */
    inlineSetting: (@Composable () -> Unit)? = null,
    /**
     * A short state string for the title row ("2 accounts", "On · auto"). Ignored when the card has
     * an [inlineSetting], whose control already says it.
     */
    status: String? = null,
    content: @Composable () -> Unit,
) {
    // Open/closed uses the car pebbles' collapse set and togglePebble (via SettingsPseudoVehicle),
    // so it survives process death like they do.
    val collapsed by vm.collapsedSections.collectAsStateWithLifecycle()
    val inline = inlineSetting != null
    val expanded = !inline && "$SETTINGS_CARD_VIN:$title" !in collapsed
    // heading() sits on the outer wrapper because PebbleShell exposes no hook into its title
    // Modifier; its header row is already one merged TalkBack stop.
    Box(Modifier.entrance("settings:$title").settingsCardSlot()) {
        PebbleShell(
            expanded = expanded,
            onToggle = { if (!inline) vm.togglePebble(SettingsPseudoVehicle, title) },
            icon = icon ?: Icons.Filled.Settings,
            title = title,
            canToggle = !inline,
            titleTrailing = inlineSetting ?: status?.takeIf { it.isNotBlank() }?.let {
                {
                    AnimatedText(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            // Hard right like every other settings control (titleTrailingAtEnd).
            titleTrailingAtEnd = true,
            // Cards space their own rows with explicit Spacers, so the shell must not add its row
            // gap.
            contentGap = 0.dp,
            content = { content() },
        )
    }
}

@Composable
internal fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    // Three-part row: label left, value hugging the button on the right. SpaceBetween, since
    // `weight(1f, fill = false)` still reserves half the width.
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(GapGroup))
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Spacer(Modifier.width(10.dp))
        // Eye icon, matching the login screen's password field.
        MorphIconButton(onClick = { show = !show }) {
            Icon(
                if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (show) "Hide $label" else "Show $label",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    // The same MorphButton every selectable option uses, with expansion animation.
    val choiceSource = remember { MutableInteractionSource() }
    MorphButton(
        onClick = { onSelect() },
        active = selected,
        interactionSource = choiceSource,
        // Idle colours stay at MorphButton's defaults; only SELECTED gets an explicit colour.
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = GapGroup),
        // Standard target height and button label type, like every tappable.
        minHeight = ButtonTargetHeight,
        modifier = Modifier.fillMaxWidth(),
        expressive = true,
        fillOnPress = true,
        groupWeight = GroupWeightProportional,
    ) {
    Text(
        label,
        Modifier.weight(1f),
        style = ButtonLabelStyle,
        fontWeight = FontWeight.SemiBold,
    )
    AnimatedVisibility(
        visible = selected,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(ButtonIconSize),
        )
    }
}
}
