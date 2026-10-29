package com.bloo.uicommon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * The name of a group of rows inside a section (the section has its own heading, [SectionHeading]; this names a part of it). One
 * sub-heading for the phone's cards and the watch's lists, announced to screen readers as a heading on both. The defaults are the
 * phone's: left-aligned, bold, in the accent colour; the watch centres it and brings its own style.
 */
@Composable
fun GroupHeading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PrimaryTextStyle(),
    color: Color = MaterialTheme.colorScheme.primary,
    weight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Start,
) {
    RevealText(
        text,
        modifier = modifier.cascadeItem().semantics { heading() },
        style = style,
        color = color,
        fontWeight = weight,
        textAlign = textAlign,
        maxLines = 1,
    )
}
