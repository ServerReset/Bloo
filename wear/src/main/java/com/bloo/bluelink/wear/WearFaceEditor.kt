package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.RevealText
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntOffset

/**
 * The face in miniature -- the same four quarters the main screen draws -- opened by pressing the face's centre.
 * Tap a quarter to choose what it does ([onPickSlot] opens the list of controls for it); the centre tick, the
 * back button or a swipe right returns to the face. Each quarter shows what it holds now, so the result of a
 * choice is on screen when the list closes.
 */
@Composable
internal fun WearFaceEditor(car: VehicleSnapshot, slots: List<String>, onPickSlot: (Int) -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    WearBackable(onDone) {
        ScreenScaffold(timeText = { TimeText() }) {
            BoxWithConstraints(Modifier.fillMaxSize().wearAuroraWash(scheme.primary), contentAlignment = Alignment.Center) {
                // The face, at two thirds of the display; its fixed sizes scale with it as they do on the main screen.
                val face = minOf(maxWidth, maxHeight) * 0.6f
                val press = rememberQuadrantPress()
                CompositionLocalProvider(LocalFaceUnit provides face / 201.dp) {
                    // Clear of the clock above, and of the bezel below at a large font setting.
                    Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(face)) {
                            val actions = (0 until 4).map { i ->
                                val control = slots.getOrNull(i)?.let { WearFaceControls.byKey(it) }?.takeIf { it.available(car) }
                                WearQuickAction(
                                    label = control?.choice ?: "Empty",
                                    icon = control?.icon?.invoke(car) ?: AppIcons.Add,
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onPickSlot(i)
                                }
                            }
                            WearQuadrantGrid(actions, Modifier.fillMaxSize(), press)
                            WearRoundButton(
                                size = face * 0.27f,
                                description = "Done",
                                color = scheme.background,
                                modifier = Modifier.align(Alignment.Center)
                                    .offset { IntOffset((press.dx * face.roundToPx()).roundToInt(), (press.dy * face.roundToPx()).roundToInt()) },
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onDone()
                                },
                            ) {
                                Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(face * 0.16f))
                            }
                        }
                        RevealText(
                            "Tap a quarter to change it",
                            modifier = Modifier.padding(top = 8.dp, start = 28.dp, end = 28.dp).fitRound(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
