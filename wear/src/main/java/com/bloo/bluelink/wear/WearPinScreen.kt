package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * The watch's PIN entry. A 3x4 keypad, sized for a round watch face. On a correct PIN [onUnlocked]
 * fires; the caller owns the session state and the policy (see [WatchPinStore] and the shared
 * `WatchPinPolicy`).
 */
@Composable
fun WearPinScreen(
    store: WatchPinStore,
    title: String,
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun submit(pin: String) {
        when (val result = store.verify(pin)) {
            is PinVerifyResult.Ok -> {
                entered = ""
                message = null
                onUnlocked()
            }
            is PinVerifyResult.Wrong -> {
                entered = ""
                message = "Wrong PIN"
            }
            is PinVerifyResult.LockedOut -> {
                entered = ""
                message = "Locked. Try again in ${com.bloo.bluelink.data.formatLockoutSeconds(result.remainingMs)}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        // Dots for the entered digits -- never the digits themselves.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(entered.length.coerceAtMost(8)) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        // Keypad 1-9, then 0 and delete. Three per row.
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok"))
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in row) {
                    WearKey(key) {
                        when (key) {
                            "del" -> entered = entered.dropLast(1)
                            "ok" -> if (entered.isNotEmpty()) submit(entered)
                            else -> {
                                val remaining = store.lockoutRemainingSeconds()
                                if (remaining > 0) {
                                    message = "Locked. Try again in ${com.bloo.bluelink.data.formatLockoutSeconds(remaining * 1000)}"
                                } else if (entered.length < 8) {
                                    entered += key
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Unlocks with the PIN set on your phone",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WearKey(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                when (label) {
                    "del" -> "\u232B"
                    "ok" -> "\u2713"
                    else -> label
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
