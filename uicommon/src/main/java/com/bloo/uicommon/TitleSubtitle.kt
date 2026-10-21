package com.bloo.uicommon

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * A name and, under it, what it is: the two lines of a list row, a preset, a car. One text column for the phone's rows and the
 * watch's, so a title and its subtitle are laid out the same on both. The defaults are the two text roles (primary over
 * secondary); a row that wants its own sizes or tones passes them. [maxLines] caps each line (the watch keeps one).
 */
@Composable
fun TitleSubtitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = PrimaryTextStyle(),
    titleColor: Color = Color.Unspecified,
    titleWeight: FontWeight? = null,
    subtitleStyle: TextStyle = SecondaryTextStyle(),
    subtitleColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        RevealText(title, style = titleStyle, color = titleColor, fontWeight = titleWeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            RevealText(subtitle, style = subtitleStyle, color = subtitleColor, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}
