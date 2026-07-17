package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearViewModel

private const val PIN_LENGTH = 4

/** Small filled/empty dots showing how many of [PIN_LENGTH] digits are entered. */
@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(PIN_LENGTH) { i ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(38.dp))
                    } else {
                        PinKey(key, onClick = { if (key == "⌫") onBackspace() else onDigit(key) })
                    }
                }
            }
        }
    }
}

/**
 * A self-contained 4-digit PIN entry pad: collects exactly [PIN_LENGTH]
 * digits, then calls [onSubmit] and clears itself. [error], when non-null, is
 * shown above the dots and triggers a reject haptic + clears any partial entry
 * (used for "wrong PIN" after a failed [onSubmit]).
 */
@Composable
fun PinEntryScreen(
    title: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    error: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    var buffer by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(error) {
        if (error != null) {
            haptics.reject()
            buffer = ""
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        PinDots(buffer.length)
        Spacer(Modifier.height(8.dp))
        PinKeypad(
            onDigit = { d ->
                if (buffer.length < PIN_LENGTH) {
                    haptics.tick()
                    val next = buffer + d
                    buffer = next
                    if (next.length == PIN_LENGTH) {
                        buffer = ""
                        onSubmit(next)
                    }
                }
            },
            onBackspace = { if (buffer.isNotEmpty()) { haptics.tick(); buffer = buffer.dropLast(1) } },
        )
        if (onCancel != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
    }
}

/** The full-screen gate shown while [WearViewModel]'s pinLocked state is true. */
@Composable
fun PinLockScreen(vm: WearViewModel) {
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PinEntryScreen(
            title = "Enter PIN",
            error = error,
            onSubmit = { pin ->
                vm.submitPin(pin) { ok -> error = if (ok) null else "Wrong PIN" }
            },
        )
    }
}

/** Which PIN-management flow [PinManagementOverlay] is running. */
enum class PinFlowMode { SET, CHANGE, REMOVE }

private enum class PinFlowStep { CONFIRM_CURRENT, ENTER_NEW, CONFIRM_NEW, REMOVING }

/**
 * A full-screen overlay driving the settings screen's "Set/Change/Remove PIN"
 * flows: SET goes straight to entering a new PIN (twice, to confirm); CHANGE
 * and REMOVE both require the current PIN first. Calls [onDone] when finished
 * or cancelled -- the caller (SettingsScreen) is responsible for clearing
 * whatever state triggered showing this.
 */
@Composable
fun PinManagementOverlay(vm: WearViewModel, mode: PinFlowMode, onDone: () -> Unit) {
    var step by remember { mutableStateOf(if (mode == PinFlowMode.SET) PinFlowStep.ENTER_NEW else PinFlowStep.CONFIRM_CURRENT) }
    var firstEntry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (step) {
            PinFlowStep.CONFIRM_CURRENT -> PinEntryScreen(
                title = "Enter current PIN",
                error = error,
                onCancel = onDone,
                onSubmit = { pin ->
                    vm.verifyPinForManagement(pin) { ok ->
                        if (ok) {
                            error = null
                            step = if (mode == PinFlowMode.REMOVE) PinFlowStep.REMOVING else PinFlowStep.ENTER_NEW
                        } else {
                            error = "Wrong PIN"
                        }
                    }
                },
            )
            PinFlowStep.ENTER_NEW -> PinEntryScreen(
                title = "Set a new PIN",
                error = error,
                onCancel = onDone,
                onSubmit = { pin -> error = null; firstEntry = pin; step = PinFlowStep.CONFIRM_NEW },
            )
            PinFlowStep.CONFIRM_NEW -> PinEntryScreen(
                title = "Confirm PIN",
                onCancel = onDone,
                onSubmit = { pin ->
                    if (pin == firstEntry) {
                        vm.setPin(pin) { onDone() }
                    } else {
                        error = "Didn't match -- try again"
                        step = PinFlowStep.ENTER_NEW
                    }
                },
            )
            PinFlowStep.REMOVING -> LaunchedEffect(Unit) { vm.clearPin(); onDone() }
        }
    }
}
