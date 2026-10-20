package com.bloo.bluelink.wear

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.RevealText

/**
 * Where the four face controls are chosen: the face itself in miniature -- the same quarters the main screen
 * draws -- with the centre as the round Done button. Tap a quarter to pick it (it lights), then tap one of the
 * controls in the row beneath to put it there; the quarter changes at once, so the result is on screen as it is
 * chosen. Back, or Done, returns to the face.
 */
@Composable
internal fun WearFaceEditor(
    car: VehicleSnapshot,
    slots: List<String>,
    initialSlot: Int,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var selected by remember { mutableIntStateOf(initialSlot.coerceIn(0, 3)) }
    val options = remember(car) { WearFaceControls.all.filter { it.available(car) } }
    val current = slots.getOrNull(selected) ?: WearFaceControls.NONE
    val rowState = rememberLazyListState()
    // The row follows the quarter: picking another one scrolls its current control into view.
    LaunchedEffect(selected) {
        val at = options.indexOfFirst { it.key == slots.getOrNull(selected) }.let { if (it < 0) options.size else it }
        rowState.animateScrollToItem(at)
    }
    ScreenScaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize().wearAuroraWash(scheme.primary)) {
            Column(Modifier.fillMaxSize().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(122.dp)) {
                    val actions = (0 until 4).map { i ->
                        val control = slots.getOrNull(i)?.let { WearFaceControls.byKey(it) }?.takeIf { it.available(car) }
                        WearQuickAction(
                            label = WearFacePrefs.positions[i],
                            icon = control?.icon?.invoke(car) ?: Icons.Filled.Add,
                            // Lit = the quarter being chosen, not the car's own state.
                            active = i == selected,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            selected = i
                        }
                    }
                    WearQuadrantGrid(actions, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(scheme.background)
                            .semantics { contentDescription = "Done" }
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onDone()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                RevealText(
                    "${WearFacePrefs.positions[selected]} · ${WearFaceControls.choiceName(current)}",
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyRow(
                    state = rowState,
                    modifier = Modifier.padding(top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(options, key = { _, c -> c.key }) { _, c ->
                        EditorChip(c.choice, c.icon(car), lit = c.key == current) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onChange(slots.toMutableList().also { it[selected] = c.key })
                        }
                    }
                    item(key = "empty") {
                        EditorChip("Empty", Icons.Filled.Close, lit = current == WearFaceControls.NONE) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onChange(slots.toMutableList().also { it[selected] = WearFaceControls.NONE })
                        }
                    }
                }
            }
        }
    }
}

/** One control in the editor's row: its glyph and name in a pill, filled when it is the quarter's current one. */
@Composable
private fun EditorChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, lit: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (lit) scheme.primary else scheme.surfaceContainerHigh.copy(alpha = 0.7f), label = "chipFill")
    val ink = if (lit) scheme.onPrimary else scheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Text(label, color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
