package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText

/**
 * A big centred value with a caption under it (the sign-in code to confirm, a status). The car page's face is
 * [WearCarFace]; this is the plain readout for everything else.
 */
@Composable
fun WearHero(
    value: String,
    caption: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The standard rolling value, so a value on the watch rolls like the phone's.
        com.bloo.uicommon.AnimatedValue(
            value = value,
            style = MaterialTheme.typography.displaySmall.merge(
                androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center),
            ),
            maxLines = 1,
            reduceMotion = false,
        )
        RevealText(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
