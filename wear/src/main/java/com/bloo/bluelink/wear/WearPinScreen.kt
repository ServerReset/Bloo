package com.bloo.bluelink.wear

import com.bloo.uicommon.Dot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.RevealText
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Icon
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The watch's PIN entry: a 3x4 keypad that FITS the face it is on. On a correct PIN [onUnlocked]
 * fires; the caller owns the session state and the policy (see [WatchPinStore] and the shared
 * `WatchPinPolicy`).
 *
 * The keys are sized from the space actually available, and that is a fix rather than a flourish.
 * Four rows of the old fixed 48dp keys are 210dp on a face that is 227dp at its widest -- before the
 * title, the entry dots, the error line and the "which PIN" line, which are another ~100dp. The
 * screen overflowed by about the height of its last row, and its last row is del/0/ok: the PIN could
 * be typed and never confirmed. Sizing from the real constraints means it fits on every face.
 */
@Composable
fun WearPinScreen(store: WatchPinStore, title: String, modifier: Modifier = Modifier, onUnlocked: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    // A wrist is often not looked at: a right PIN buzzes yes, a wrong one buzzes no, and the dots shake.
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun reject() {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch {
            for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
        }
    }

    fun submit(pin: String) {
        when (val result = store.verify(pin)) {
            is PinVerifyResult.Ok -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                entered = ""
                message = null
                onUnlocked()
            }
            is PinVerifyResult.Wrong -> {
                reject()
                entered = ""
                message = "Wrong PIN"
            }
            is PinVerifyResult.LockedOut -> {
                reject()
                entered = ""
                message = "Locked. Try again in ${com.bloo.bluelink.data.formatLockoutSeconds(result.remainingMs)}"
            }
        }
    }

    // Keypad 1-9, then 0 and delete. Three per row.
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok"))
    val gap = 6.dp

    BoxWithConstraints(modifier.fillMaxSize().wearAuroraWash(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        val sizing = pinPadSizing(maxWidth, maxHeight, gap)
        val key = sizing.key

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // One slot for both. The error is what matters when there is one; the title is context the
            // user has already read by then.
            RevealText(
                message ?: title,
                style = MaterialTheme.typography.titleSmall,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            // Dots for the entered digits -- never the digits themselves. A fixed height so the keypad
            // does not jump down when the first digit lands.
            Row(Modifier.height(12.dp).graphicsLayer { translationX = shake.value * density }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(entered.length.coerceAtMost(8)) {
                    Dot(MaterialTheme.colorScheme.primary, 10.dp)
                }
            }
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        WearKey(k, key) {
                            when (k) {
                                "del" -> entered = entered.dropLast(1)
                                "ok" -> if (entered.isNotEmpty()) submit(entered)
                                else -> {
                                    val remaining = store.lockoutRemainingSeconds()
                                    if (remaining > 0) {
                                        message = "Locked. Try again in ${com.bloo.bluelink.data.formatLockoutSeconds(remaining * 1000)}"
                                    } else if (entered.length < 8) {
                                        entered += k
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (sizing.showCaption) {
                RevealText(
                    "The PIN you set on your phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The keypad's answer for a given space: the key size, and whether the "which PIN" line fits too. */
internal data class PinPadSizing(val key: Dp, val showCaption: Boolean)

/** What the text above and below the keypad needs. Generous on purpose: UNDER-reserving is what puts
 *  a key under the bezel. The title slot is two lines because a lockout message wraps. */
internal val PIN_PAD_TEXT = 28.dp + 12.dp
internal val PIN_PAD_CAPTION = 14.dp
internal val PIN_PAD_GAP = 6.dp

/**
 * The keypad's arithmetic, out of the composable so it can be tested -- which is the point, because
 * the version that shipped had no arithmetic at all: the keys were a constant 48dp and the pad was
 * taller than the watch. Keys are square and as large as BOTH the width (three to a row) and the
 * height left over after the text allows.
 *
 * The "which PIN" line is the least important thing on this screen, so it is the first to go: on a
 * small face, dropping it buys back ~5dp on every key, and a key that can be pressed matters more
 * than a sentence that is only needed once.
 */
internal fun pinPadSizing(maxWidth: Dp, maxHeight: Dp, gap: Dp = PIN_PAD_GAP): PinPadSizing {
    val byWidth = (maxWidth - gap * 2) / 3
    val text = PIN_PAD_TEXT + gap * 5
    val caption = PIN_PAD_CAPTION + gap
    val showCaption = minOf(byWidth, (maxHeight - text - caption) / 4) >= 34.dp
    val key = minOf(byWidth, (maxHeight - text - if (showCaption) caption else 0.dp) / 4)
        .coerceIn(1.dp, 52.dp)
    return PinPadSizing(key, showCaption)
}

/** One key: a digit, or the backspace and confirm glyphs (the shared ones) with a spoken name, since a glyph alone reads as nothing. */
@Composable
private fun WearKey(label: String, size: Dp, onClick: () -> Unit) {
    val glyph = when (label) {
        "del" -> AppIcons.Backspace
        "ok" -> AppIcons.Check
        else -> null
    }
    val spoken = when (label) {
        "del" -> "Delete"
        "ok" -> "Confirm"
        else -> label
    }
    WearRoundButton(size, spoken, onClick = onClick) {
        if (glyph != null) {
            // The glyph scales with the key, so a small key is a small key rather than a cropped big one.
            Icon(glyph, contentDescription = null, modifier = Modifier.size(size * 0.46f))
        } else {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontSize = (size * 0.42f).value.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
