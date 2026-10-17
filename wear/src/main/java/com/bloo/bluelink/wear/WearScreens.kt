package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.RevealText

/*
 * The watch's standard full screens, so every one-off screen is the same shape: a centred icon badge, a title,
 * a line of detail, and (for a question) the round ✕ and ✓ every Wear OS confirmation uses.
 */

/** A message that fills the screen: no cars, waiting for the phone, a code to confirm. [extra] sits under the text. */
@Composable
fun WearMessageScreen(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    extra: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenBadge(icon, tint)
                RevealText(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                detail?.let {
                    RevealText(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                }
                extra?.invoke()
            }
        }
    }
}

/**
 * A yes/no question on its own screen -- unlocking the car, say: the icon, the question, and the round ✕ and ✓.
 * [destructive] colours the ✓ as a warning for an action that opens or stops something.
 */
@Composable
fun WearConfirmScreen(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    WearMessageScreen(icon = icon, title = title, detail = detail, tint = if (destructive) scheme.error else scheme.primary) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundChoice(Icons.Filled.Close, "Cancel", scheme.secondaryContainer, scheme.onSecondaryContainer, onCancel)
            RoundChoice(
                Icons.Filled.Check, confirmLabel,
                if (destructive) scheme.error else scheme.primary,
                if (destructive) scheme.onError else scheme.onPrimary,
                onConfirm,
            )
        }
    }
}

/** One of a confirmation's two round buttons: the app's own button, as a 52dp circle. */
@Composable
private fun RoundChoice(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).semantics { contentDescription = label },
        containerColor = container,
        contentColor = content,
        contentPadding = PaddingValues(0.dp),
        minHeight = 0.dp,
        pillCornerPercent = 50f,
        morphedCornerPercent = 50f,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** The tinted circle a full screen's icon sits in. */
@Composable
private fun ScreenBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
