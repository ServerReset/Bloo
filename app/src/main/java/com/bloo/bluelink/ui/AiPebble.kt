package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.LocalIsDarkTheme

/** Optional on-device Gemini Nano summary of the car's last-refreshed status. */
@Composable
internal fun AiPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val busy = v.vin in state.aiBusy
    val summary = state.aiSummaries[v.vin]
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    // The tile is its own colour, so its text is chosen for THAT colour, not borrowed from a theme role: near-white on the dark
    // violet in a dark theme, near-black on the pale one in a light theme. (Passing a colour that is not a theme role also makes
    // the shell use the ink set here, instead of a "tertiary container" pair whose text went dim purple on purple.)
    val ink = if (dark) Color(0xFFF4EFFF) else Color(0xFF1E1A2E)
    CompositionLocalProvider(LocalContentColor provides ink) {
    Pebble(
        v, "ai", "AI summary", AppIcons.AutoAwesome, state, vm, modifier,
        // What the tile can tell you, not what engine it runs on. The engine is still named in the
        // body copy ("generated privately on your device"), where a fact you read once belongs.
        summary = when {
            busy -> "Summarizing…"
            summary != null -> "Summary ready"
            else -> "Not summarized yet"
        },
        containerColor = aiTone(scheme.tertiaryContainer, dark, 0.45f),
        // The one pebble whose subject is not a part of the car, so it is the one that earns a
        // different surface: a gradient marks "this was generated" rather than measured, the same
        // way the Summarize action is the only header action that makes something rather than
        // sending a command.
        background = {
            val brush = remember(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer, dark) {
                // The text on the card is the theme's own: light on a dark theme, dark on a light one. So the gradient is
                // lightened toward white on a light theme and darkened toward black on a dark one, keeping that text readable.
                // Opaque: a see-through stop let the dark page behind mute the text, which read as unreadable.
                Brush.linearGradient(
                    // Diagonal rather than vertical: a pebble is much wider than it is tall when
                    // collapsed, so a vertical sweep would compress to a flat band and read as a
                    // slightly-off solid fill.
                    0f to aiTone(scheme.tertiaryContainer, dark, 0.45f),
                    0.55f to aiTone(scheme.primaryContainer, dark, 0.5f),
                    1f to aiTone(scheme.secondaryContainer, dark, 0.4f),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }
            Spacer(Modifier.matchParentSize().background(brush))
        },
        headerAction = PebbleHeaderAction(
            label = "Summarize",
            icon = AppIcons.AutoAwesome,
            onClick = { vm.summarizeCar(v) },
            pending = busy,
        ),
    ) {
        // Its old glance hero repeated the tile's own title and subtitle verbatim -- four lines
        // carrying two strings before a word of the actual summary. The summary IS the point of the
        // tile, so it leads.
        if (summary != null) {
            // Bulleted, not one raw string: the model's own prompt asks for a "* " bullet per fact,
            // and drawing that straight through showed the literal asterisk as text -- "* Daisy
            // (2025) is charging..." -- confirmed from a real screenshot.
            Column(verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                summary.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    val bulleted = line.startsWith("* ") || line.startsWith("- ")
                    Row(horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                        if (bulleted) RevealMutedText("•")
                        RevealText(if (bulleted) line.substring(2) else line, style = SecondaryTextStyle())
                    }
                }
            }
        } else {
            RevealMutedText("Summarizes this car's latest status, on your device.")
        }
        RevealMutedText("Reflects the last refresh. Tap Summarize to update.")
    }
    }
}

/** [c] moved [amount] of the way toward black in a dark theme, toward white in a light one: the tile's own surface. */
private fun aiTone(c: Color, dark: Boolean, amount: Float): Color = if (dark) lerp(c, Color.Black, amount) else lerp(c, Color.White, amount)
