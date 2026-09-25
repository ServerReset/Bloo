@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.ChargerFilters
import com.bloo.bluelink.data.ChargerStation
import com.bloo.bluelink.data.matches
import dev.chrisbanes.haze.HazeState

/**
 * The expanded map's "Chargers" layer, end to end -- see [com.bloo.bluelink.data.ChargerApi]'s
 * own doc for the data source (Open Charge Map, a free third-party charger database this app
 * deliberately does not maintain a competing copy of).
 *
 * [ChargerFilterBar] is the ONLY piece that needs its own file: the marker pins themselves
 * live inside [CarMap] (the same tile-coordinate projection every other marker on that map
 * already uses -- there was no reason to duplicate that math here), and the map-level toggle
 * is just one more [MapFeature] in [ExpandableMapLayer]'s existing row.
 */

/**
 * Speed + network filter controls for the currently-loaded [chargers], shown only while the
 * "Chargers" map feature is toggled on. A count line up top ("N chargers nearby") reflects
 * the CURRENT filters, not the raw fetch, so narrowing to a speed/network with nothing nearby
 * reads as "0 chargers" rather than silently doing nothing.
 *
 * The network row is built from whatever operator names are actually present in [chargers] --
 * this app has no hand-maintained brand list to fall back on or drift out of sync with (see
 * [com.bloo.bluelink.data.ChargerStation.network]'s own doc), so the choices on offer are
 * exactly whatever the current search actually found. Empty (no row at all) when every result
 * is missing operator data, or before any fetch has returned yet.
 */
@Composable
internal fun ChargerFilterBar(
    chargers: List<ChargerStation>,
    filters: ChargerFilters,
    loading: Boolean,
    /** Set only on a genuine fetch failure -- see [com.bloo.bluelink.data.ChargerApi.nearby]'s
     *  own doc. Switches this whole bar to a compact error state (message + retry + an inline
     *  API key field, since a missing/invalid key is the single most likely cause) instead of
     *  the normal speed/network controls. */
    error: String?,
    mapHazeState: HazeState,
    onSetMinKw: (Int) -> Unit,
    onToggleNetwork: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recomputed only when the fetch itself changes (a new List instance), not on every
    // filter tweak or unrelated recomposition of the map around this bar.
    val networks = remember(chargers) { chargers.mapNotNull { it.network }.distinct().sorted() }
    val matchCount = remember(chargers, filters) { chargers.count { it.matches(filters) } }
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        hazeState = mapHazeState,
        modifier = modifier.fillMaxWidth().widthIn(max = 520.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open Charge Map needs a free API key per app. Add it in Settings > Map & Navigation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MorphTextButton("Retry", onClick = onRetry, showIcon = false, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    when {
                        loading -> "Searching for chargers…"
                        matchCount == 1 -> "1 charger nearby"
                        else -> "$matchCount chargers nearby"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                MorphSegmented(
                    options = listOf(
                        SegmentOption("0", "Any speed", null),
                        SegmentOption("50", "50kW+", null),
                        SegmentOption("150", "150kW+", null),
                    ),
                    selectedKey = filters.minKw.toString(),
                    onSelect = { key -> onSetMinKw(key.toIntOrNull() ?: 0) },
                )
                if (networks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        networks.forEach { network ->
                            MorphChip(
                                selected = network in filters.networks,
                                onClick = { onToggleNetwork(network) },
                                label = network,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick info for whichever charger pin was last tapped -- name, network and top
 * speed, with a close button. Deliberately not a dialog/bottom sheet of its own:
 * this is meant to be glanced at and dismissed without interrupting whatever else
 * is happening on the map (panning, the filter bar), so it's just one more row in
 * the same bottom-anchored stack those live in.
 */
@Composable
internal fun ChargerInfoCard(
    charger: ChargerStation,
    mapHazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        hazeState = mapHazeState,
        modifier = modifier.fillMaxWidth().widthIn(max = 520.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EvStation, contentDescription = null, tint = ChargeGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    charger.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Network and top speed, whichever are actually known -- a station
                // Open Charge Map has no operator on file for just omits that half
                // rather than showing a placeholder like "Unknown network".
                val subtitle = listOfNotNull(
                    charger.network,
                    charger.maxKw?.let { "up to ${it.toInt()}kW" },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val dismissSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(interactionSource = dismissSource, enabled = true) {
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                    hazeState = mapHazeState,
                    onClick = onDismiss,
                    contentDescription = "Dismiss",
                    interactionSource = dismissSource,
                    shadow = false,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
