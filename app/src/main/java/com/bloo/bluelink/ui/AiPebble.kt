@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle

/** Optional on-device Gemini Nano summary of the car's last-refreshed status. */
@Composable
internal fun AiPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val busy = v.vin in state.aiBusy
    val summary = state.aiSummaries[v.vin]
    Pebble(
        v, "ai", "AI summary", Icons.Filled.AutoAwesome, state, vm, modifier,
        // What the tile can tell you, not what engine it runs on. This was the constant string
        // "On-device Gemini Nano", which as a collapsed summary -- and, on the cover, as the
        // tile's whole headline -- spent the most prominent line saying something that is true
        // of this pebble forever and answers nothing. The engine is still named in the body copy
        // ("generated privately on your device"), where a fact you read once belongs.
        summary = when {
            busy -> "Summarizing…"
            summary != null -> "Summary ready"
            else -> "Not summarized yet"
        },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        // The one pebble whose subject is not a part of the car, so it is the one
        // that earns a different surface: a gradient marks "this was generated"
        // rather than measured, the same way the Summarize action is the only
        // header action that makes something rather than sending a command.
        //
        // Built from the scheme's own container roles rather than fixed hues, so it
        // follows the user's accent, their vibrancy setting and light/dark with no
        // second palette to maintain -- the mistake ChargeGreen's phone-side
        // re-declaration made, which is why colours live in tokens here.
        //
        // containerColor stays tertiaryContainer underneath. The gradient paints
        // over it, but it is what contentColorFor() reads to pick the text colour,
        // and all three stops are container-toned, so the contrast that colour was
        // chosen for holds across the whole sweep.
        background = {
            val scheme = MaterialTheme.colorScheme
            val brush = remember(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer) {
                Brush.linearGradient(
                    // Diagonal rather than vertical: a pebble is much wider than it
                    // is tall when collapsed, so a vertical sweep would compress to
                    // a flat band and read as a slightly-off solid fill.
                    0f to scheme.tertiaryContainer,
                    0.55f to scheme.primaryContainer.copy(alpha = 0.55f),
                    1f to scheme.secondaryContainer.copy(alpha = 0.65f),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }
            Spacer(Modifier.matchParentSize().background(brush))
        },
        headerAction = PebbleHeaderAction(
            label = "Summarize",
            icon = Icons.Filled.AutoAwesome,
            onClick = { vm.summarizeCar(v) },
            pending = busy,
        ),
        // NOT alwaysExpandedInSimpleMode -- see the note on LocationPebble. This tile has a
        // summary paragraph and a footnote, not a single setting, and the flag costs it its
        // chevron entirely.
    ) {
        // On the flip cover this tile fills the screen; two short text lines centred
        // in it read as a big empty purple void. Lead with a proper glance hero (big
        // icon + heading + status line) like the other cover tiles, then the copy.
        if (LocalForceExpanded.current) {
            // Shared CoverHero rhythm (converged 34dp icon + headline + status subline),
            // so the AI tile matches Climate/Info/Diagnostics/etc instead of its old
            // ad-hoc 48dp centered column.
            // No cover hero: its value was the tile TITLE verbatim ("AI summary") and its
            // subline was the tile subtitle verbatim ("On-device Gemini Nano"). Four lines
            // carrying two strings, before a word of the actual summary. CoverTile's headline
            // covers it; what follows is the summary itself, which is the point of the tile.
        }
        if (summary != null) {
            // Bulleted, not one raw string: the model's own prompt asks for a "* " bullet per
            // fact, and drawing that straight through showed the literal asterisk as text --
            // "* Daisy (2025) is charging..." -- confirmed from a real screenshot. Any line
            // that IS a bullet gets a real one; anything else (a stray lead-in sentence, if
            // the model ever writes one) prints as plain text, so this degrades safely rather
            // than assuming every summary is bulleted.
            Column(verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                summary.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    val bulleted = line.startsWith("* ") || line.startsWith("- ")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (bulleted) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                            )
                        }
                        Text(
                            if (bulleted) line.substring(2) else line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Text(
                "Summarizes this car's latest status, on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
            )
        }
        Text(
            "Reflects the last refresh. Tap Summarize to update.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
        )
    }
}

