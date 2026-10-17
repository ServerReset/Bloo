package com.bloo.bluelink.wear

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText

/**
 * The car's at-a-glance state, used as the page's hero value.
 *
 * Two callers, one component: the page's small readout, and -- with the ring stretched to the display
 * and the quick actions handed in as [body] -- the whole watch face. That is why the ring is a
 * [Modifier] and the middle is a slot rather than a fixed layout.
 */
@Composable
fun WearHero(
    value: String,
    caption: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    progress: Float? = null,
    /**
     * The ring's own size. A parameter rather than a constant because the SAME hero is the page's
     * small at-a-glance readout and, in full screen, the whole display: the caller passes
     * `Modifier.fillMaxWidth().aspectRatio(1f)` and the circle becomes the watch's face.
     */
    ring: Modifier = Modifier.size(136.dp),
    /**
     * What goes INSIDE the ring, under the value. The full-screen hero puts the quick actions here, so
     * the circle is the page rather than a badge above it.
     */
    body: (@Composable () -> Unit)? = null,
) {
    val text: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The standard rolling value, entered through :uicommon's AnimatedValue so the watch's
            // hero (battery %, lock state) rolls like the phone's.
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = MaterialTheme.typography.displaySmall.merge(
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        textAlign = TextAlign.Center,
                    ),
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
            body?.invoke()
        }
    }
    if (progress == null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { text() }
        return
    }
    // Lean (the watch's Battery Saver or a throttling watch, see PowerPolicy): the ring jumps instead of sweeping.
    val bouncy by com.bloo.bluelink.data.DeviceStatus.policy.collectAsState()
    val sweep by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        animationSpec = if (bouncy.bouncySprings) androidx.compose.animation.core.spring() else androidx.compose.animation.core.snap(),
        label = "heroRing",
    )
    val track = tint.copy(alpha = 0.18f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            ring.drawBehind {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(tint, -90f, 360f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            },
            contentAlignment = Alignment.Center,
        ) { text() }
    }
}
