@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/** Quick-tiles surface: the per-car Quick Settings tile manager, its tiles and
 *  add/label/action helpers, plus the Updates status chip. Peeled out of
 *  SettingsWidgets.kt into its own file. */

import android.app.StatusBarManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val TileActions = listOf(
    Triple("doors", "Lock / unlock", Icons.Filled.Lock),
    Triple("climate", "Climate", Icons.Filled.Thermostat),
    Triple("charge", "Charge", Icons.Filled.Bolt),
    Triple("open", "Open", Icons.Filled.DirectionsCar),
)

/** Label for a tile action key (falls back to the key). */
internal fun tileActionLabel(cmd: String): String =
    TileActions.firstOrNull { it.first == cmd }?.second ?: cmd

/** Expressive per-car header: a tonal thumbnail/gradient bubble, name, and tile count. */
@Composable
internal fun CarTilesHeader(name: String, img: String?, assignedCount: Int, totalTiles: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarThumb(img = img, size = 44.dp, cornerRadius = 16.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (assignedCount == 0) "No tiles yet" else "$assignedCount of $totalTiles tiles used",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            // A slim capacity bar reads the per-car tile budget at a glance,
            // instead of just a count with no sense of how much room is left.
            Spacer(Modifier.height(6.dp))
            val fill by animateFloatAsState(
                targetValue = if (totalTiles > 0) assignedCount / totalTiles.toFloat() else 0f,
                animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                label = "tileCapacityFill",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary),
                )
            }
        }
    }
}

/** Shared muted hint line for the tile manager's empty/full states. */
@Composable
internal fun TileEmptyHint(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Per-car Quick Settings tile manager with live previews. Each car gets its
 *  own tonal card (mirroring CarSettingsCard's per-car container elsewhere in
 *  Settings) so two cars' tile groups never read as one continuous list. */
@Composable
internal fun QuickTilesManager(state: UiState, vm: AppViewModel) {
    if (state.vehicles.isEmpty()) {
        Text(
            "Add a car to set up quick tiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val count = com.bloo.bluelink.data.TILE_COUNT
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.vehicles.forEach { car ->
            val assigned = (0 until count).filter { state.tileConfigs.getOrNull(it)?.first == car.vin }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    CarTilesHeader(
                        name = car.name,
                        img = state.imageUrls[car.vin],
                        assignedCount = assigned.size,
                        totalTiles = count,
                    )
                    Spacer(Modifier.height(10.dp))
                    assigned.forEach { idx ->
                        key(idx) { QuickTileCard(idx, car.vin, state, vm) }
                    }
                    val free = (0 until count).firstOrNull { state.tileConfigs.getOrNull(it) == null }
                    when {
                        free != null -> AddTilePill(
                            label = if (assigned.isEmpty()) "Add a quick tile" else "Add another",
                            onClick = { vm.setTileAssignment(free, car.vin, if (assigned.isEmpty()) "doors" else "climate") },
                        )
                        assigned.isEmpty() -> TileEmptyHint("All $count tiles are in use. Remove one to add another.")
                    }
                }
            }
        }
    }
}

/**
 * Prompt the OS to add this configured tile straight to the Quick Settings shade.
 * The system dialog previews [label] + the action's icon before adding, so the
 * tile's name/properties are shown up front. On API < 33 (no add-tile API) we
 * guide the user to add it manually instead.
 */
internal fun addTileToQuickSettings(context: Context, index: Int, cmd: String, label: String, unlocked: Boolean) {
    val iconRes = com.bloo.bluelink.tiles.BlooTileService.iconResFor(cmd, unlocked)
    val requested = com.bloo.bluelink.tiles.BlooTileService.requestAddToQuickSettings(
        context, index, label, iconRes,
    ) { result ->
        val msg = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "“$label” added to Quick Settings"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "“$label” is already in Quick Settings"
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    if (!requested) {
        Toast.makeText(
            context,
            "Open Quick Settings, tap edit, and add “$label” from the tile list.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

internal fun tileSummary(cmd: String, climateTarget: String, presetName: String?): String = when (cmd) {
    "doors" -> "Lock / unlock"
    "climate" -> when (climateTarget) {
        "smart" -> "Climate · Smart"
        "default" -> "Climate · Basic"
        else -> "Climate · ${presetName ?: "Preset"}"
    }
    "charge" -> "Start / stop charge"
    "open" -> "Opens the app"
    else -> cmd
}

/**
 * One configured tile, built on the exact same [PebbleShell] every car pebble
 * uses (see [UpdateAvailableTile] for the other non-car-scoped caller) instead
 * of a bespoke static-shape split row -- its collapsed header IS the live
 * preview (icon, name, current state), and its [PebbleHeaderAction] doubles as
 * the actual "Add" button so the common case (configure once, add it) never
 * needs to expand at all. Expanding is only for changing the action, custom
 * name, what climate runs, or removing the tile.
 */
@Composable
internal fun QuickTileCard(index: Int, vin: String, state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val cmd = state.tileConfigs.getOrNull(index)?.second ?: "doors"
    val customName = state.tileLabels.getOrNull(index)?.takeIf { it.isNotBlank() }
    val presets = state.climatePresets[vin].orEmpty()
    val target = state.tileClimateTargets.getOrNull(index) ?: "default"
    val presetName = presets.firstOrNull { it.id == target }?.name
    var expanded by remember { mutableStateOf(false) }

    // Live car state so the preview matches what the tile will actually show.
    val status = state.vehicles.firstOrNull { it.vin == vin }?.let { state.statusFor(it) }
    val active = when (cmd) {
        "doors" -> status?.doorLock == false
        "climate" -> status?.airCtrlOn == true
        "charge" -> status?.evStatus?.batteryCharge == true
        else -> false
    }
    val liveLabel = when (cmd) {
        "doors" -> status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
        "climate" -> if (status?.airCtrlOn == true) "On" else null
        "charge" -> if (status?.evStatus?.batteryCharge == true) "Charging" else null
        else -> null
    }
    val headerIcon = when (cmd) {
        "doors" -> if (status?.doorLock == false) Icons.Filled.LockOpen else Icons.Filled.Lock
        "climate" -> Icons.Filled.Thermostat
        "charge" -> Icons.Filled.Bolt
        else -> Icons.Filled.DirectionsCar
    }
    val title = if (cmd == "open") "Open" else (customName ?: tileActionLabel(cmd))

    PebbleShell(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        icon = headerIcon,
        title = title,
        vm = vm,
        summary = liveLabel ?: tileSummary(cmd, target, presetName),
        headerAction = PebbleHeaderAction(
            label = "Add",
            icon = Icons.Filled.Add,
            active = active,
            onClick = { addTileToQuickSettings(context, index, cmd, title, unlocked = status?.doorLock == false) },
        ),
    ) {
        Text("Action", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        MorphSegmented(
            options = TileActions.map { (key, label, icon) ->
                SegmentOption(key, if (key == "doors") "Lock" else label, icon)
            },
            selectedKey = cmd,
            onSelect = { key -> vm.setTileAssignment(index, vin, key) },
        )

        if (cmd != "open") {
            Spacer(Modifier.height(10.dp))
            var name by remember(state.tileLabels.getOrNull(index)) {
                mutableStateOf(customName.orEmpty())
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; vm.setTileLabel(index, it) },
                label = { Text("Custom name (optional)") },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (cmd == "climate") {
            Spacer(Modifier.height(10.dp))
            Text("Runs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            MorphSegmented(
                options = buildList {
                    add(SegmentOption("default", "Basic", null))
                    add(SegmentOption("smart", "Smart", null))
                    presets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                },
                selectedKey = target,
                onSelect = { vm.setTileClimateTarget(index, it) },
            )
        }

        Spacer(Modifier.height(4.dp))
        MorphButton(
            onClick = { vm.setTileAssignment(index, null, null) },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- that Text had no `style`.
            // Content-width, matching every other standalone CTA in the app.
            MorphButtonLabel(Icons.Filled.Close, "Remove tile", pending = false)
        }
    }
}

/** An outlined "add" pill that morphs like the app's other buttons with expansion animation. */
@Composable
internal fun AddTilePill(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    SafeExpansiveButton(
        interactionSource = interactionSource,
        enabled = true,
    ) {
        MorphButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- that Text had no `style`.
            MorphButtonLabel(Icons.Filled.Add, label, pending = false)
        }
    }
}

/**
 * The Updates card's tonal status chip, split out of the card body so the
 * spring-animated tint (`updateTint`) only recomposes this small Row/Icon/Text
 * scope on every animation frame, instead of the whole card content lambda
 * (which also hosts the RollingNumber hero stat and outer Surface/Row layout).
 */
@Composable
internal fun UpdateStatusChip(state: UiState) {
    val updateTint by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            state.updateAvailable != null -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        // Sprung rather than snapped -- "up to date" turning tertiary the instant
        // a check lands is the one moment this card actually has news, and a cut
        // read as flat next to how much of the rest of the app now springs.
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "settingsUpdateTint",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(updateTint.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = updateTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = when {
                state.updateChecking -> "Checking…"
                state.updateAvailable != null -> "Build ${state.updateAvailable.run.runNumber} ready"
                else -> "Up to date"
            },
            label = "settingsUpdateChipText",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelMedium, color = updateTint, fontWeight = FontWeight.Bold)
        }
    }
}
