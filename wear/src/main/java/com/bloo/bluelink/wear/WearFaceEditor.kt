package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.RevealText
import kotlin.math.roundToInt

/**
 * The face in miniature -- the same four quarters the main screen draws -- opened by pressing the face's centre.
 * Tap a quarter to choose what it does ([onPickSlot] opens the list of controls for it); the centre tick, the
 * back button or a swipe right returns to the face. Each quarter shows what it holds now, so the result of a
 * choice is on screen when the list closes.
 */
@Composable
internal fun WearFaceEditor(
    car: VehicleSnapshot,
    slots: List<String>,
    onPickSlot: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    WearBackable(onDone) {
        ScreenScaffold(timeText = { TimeText() }) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxSize().wearAuroraWash(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                // The face, at two thirds of the display; its fixed sizes scale with it as they do on the main screen.
                val face = minOf(maxWidth, maxHeight) * 0.66f
                val press = rememberQuadrantPress()
                androidx.compose.runtime.CompositionLocalProvider(LocalFaceUnit provides face / 201.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(face)) {
                            val actions = (0 until 4).map { i ->
                                val control = slots.getOrNull(i)?.let { WearFaceControls.byKey(it) }?.takeIf { it.available(car) }
                                WearQuickAction(
                                    label = control?.choice ?: "Empty",
                                    icon = control?.icon?.invoke(car) ?: Icons.Filled.Add,
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onPickSlot(i)
                                }
                            }
                            WearQuadrantGrid(actions, Modifier.fillMaxSize(), press)
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .offset { androidx.compose.ui.unit.IntOffset((press.dx * face.roundToPx()).roundToInt(), (press.dy * face.roundToPx()).roundToInt()) }
                                    .size(face * 0.27f)
                                    .clip(CircleShape)
                                    .background(scheme.background)
                                    .semantics { contentDescription = "Done" }
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onDone()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(face * 0.16f))
                            }
                        }
                        RevealText(
                            "Tap a quarter to change it",
                            modifier = Modifier.padding(top = 8.dp, start = 28.dp, end = 28.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
