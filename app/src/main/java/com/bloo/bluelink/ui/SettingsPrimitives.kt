package com.bloo.bluelink.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.GroupHeading
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.hairlineColor
import com.bloo.uicommon.content

/**
 * A hairline-outlined box, for the few things that need a visible edge on a glass card (a log, an
 * onboarding item). No fill: cards are glass, and a solid dark box dropped on them read as a hole.
 */
@Composable
internal fun Modifier.outlinedPanel(padding: Dp = 12.dp): Modifier =
    this.clip(StandardShape)
        .border(1.dp, hairlineColor(), StandardShape)
        .padding(padding)

/**
 * A full-width callout: a rounded [shape] filled with a [color] that is already translucent (an alpha of a container role), padded
 * by [padding]. One definition for the warning banner, the "update available" panel and the sections' inset panel.
 */
@Composable
internal fun Modifier.tintedPanel(
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape = StandardShape,
    padding: PaddingValues = PaddingValues(GapSection),
): Modifier = this.fillMaxWidth().clip(shape).background(color).padding(padding)

/**
 * The app's ONE inner glass panel: a soft tonal fill with the shared frosted rim, for a group of things
 * sitting inside a glass card (settings groups, onboarding items, tips). One definition so they cannot drift.
 */
@Composable
internal fun Modifier.innerGlassPanel(padding: Dp = GapSection): Modifier =
    this.clip(StandardShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f), StandardShape)
        .liquidRim(StandardShape)
        .padding(padding)

/**
 * A titled sub-group inside a settings card: a heading, then its controls, on its own filled card.
 * A filled card (a tonal fill plus the shared frosted rim), not a bare outlined box, so the groups
 * read as cards sitting inside the card's box rather than boxes nested in a box.
 *
 * [title] may be null/blank: the group then draws no heading. Used where a heading would only repeat
 * the control it sits above (a lone "Aurora background" toggle under a "Background" heading), which
 * adds a word and no meaning.
 */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title.isNullOrBlank()) {
        Column(Modifier.fillMaxWidth().innerGlassPanel(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            content()
        }
        return
    }
    // A titled group folds: its heading is the button, and the group remembers whether it was open.
    var open by rememberSaveable(title) { mutableStateOf(true) }
    val spin = rememberChevronSpin(open, label = "settingsGroup")
    Column(Modifier.fillMaxWidth().innerGlassPanel(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { GroupHeading(title) }
            ExpandChevronIcon(open, spin)
        }
        AnimatedVisibility(visible = open, enter = expandEnterSized(), exit = expandExitSized(fade = false)) {
            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                content()
            }
        }
    }
}

/**
 * A digits-only "minutes" field for the notification-delay settings, clamped to 1..120. It owns the
 * edit buffer: [initial] seeds it and re-seeds whenever the persisted value changes (via
 * `remember(initial)`), while [onSet] fires only for an in-range number, so a half-typed or
 * out-of-range value is shown but never persisted.
 */
@Composable
internal fun MinutesField(initial: Int, label: String, onSet: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    BlooTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit)
            text.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(onSet)
        },
        label = { RevealText(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = GapRow),
    )
}

/** A digits-only mileage field. */
@Composable
internal fun MilesField(value: Int?, label: String, modifier: Modifier, onSet: (Int?) -> Unit) {
    BlooTextField(
        value = value?.toString() ?: "",
        onValueChange = { onSet(it.filter(Char::isDigit).toIntOrNull()) },
        label = { RevealText(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
