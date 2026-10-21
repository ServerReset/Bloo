package com.bloo.uicommon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A fact on one line: its label on the left (the muted tone, so the value reads first) and its value on the right, with long
 * values taking the room the label leaves. The one label-and-value row for the phone's cards and the watch's lists; the colours
 * are the caller's, since the phone's tone depends on the card it sits in.
 */
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    valueColor: Color = Color.Unspecified,
    valueMono: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Natural width, not weight: a 50/50 split starved a long value into wrapping. The value is the row's weighted child.
        RevealText(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val baseStyle = LocalTextStyle.current
            val onSurface = MaterialTheme.colorScheme.onSurface
            val resolvedValueColor = if (valueColor == Color.Unspecified) onSurface else valueColor
            // Memoised: TextStyle.copy on every recomposition is wasted work for a value that rarely changes.
            val valueStyle = remember(baseStyle, resolvedValueColor, valueMono) {
                baseStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = resolvedValueColor,
                    textAlign = TextAlign.End,
                    fontFamily = if (valueMono) FontFamily.Monospace else baseStyle.fontFamily,
                )
            }
            AnimatedValue(
                value = value,
                style = valueStyle,
                maxLines = 2,
                reduceMotion = LocalReduceMotion.current,
            )
        }
    }
}
