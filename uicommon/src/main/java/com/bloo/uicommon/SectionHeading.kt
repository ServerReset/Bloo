package com.bloo.uicommon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The heading of a section: its glyph and its title on one line, and, when there is one, a one-word verdict at the far end
 * ([trailing]). One heading for the phone's cards and the watch's lists, so a section reads the same on both; the phone left-aligns
 * it, the watch centres it ([centered]) and brings its own text style and tint.
 */
@Composable
fun SectionHeading(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    iconTint: Color = LocalContentColor.current.copy(alpha = 0.7f),
    iconSize: Dp = 18.dp,
    titleStyle: TextStyle = PrimaryTextStyle(),
    titleColor: Color = LocalContentColor.current.copy(alpha = 0.85f),
    titleWeight: FontWeight = FontWeight.Bold,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
) {
    Row(
        modifier.fillMaxWidth().semantics { heading() },
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(if (centered) 6.dp else GapRow))
        val titleText: @Composable () -> Unit = {
            RevealText(
                title,
                style = titleStyle,
                color = titleColor,
                fontWeight = titleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (centered) titleText() else Box(Modifier.weight(1f)) { titleText() }
        if (trailing != null) {
            RevealText(
                trailing,
                style = SecondaryTextStyle(),
                color = trailingColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
