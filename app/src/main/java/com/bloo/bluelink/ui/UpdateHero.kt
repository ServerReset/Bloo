package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonIconSize
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.bounceSpring
import kotlinx.coroutines.launch

/**
 * The version jump at a glance, shared by the update pebble and the Settings update card: the build
 * you have, an arrow, and the build waiting, the new one in the accent colour and larger. The
 * header summary carries the "build N" wording, so this row stays just the two numbers and the
 * arrow.
 *
 * It lands in two beats, every time it is shown: the new build pops in from small with a ring of
 * accent colour blooming out behind it, then the build you have and the arrow slide in beside it.
 */
@Composable
internal fun UpdateDeltaHero(currentBuild: Int, newBuild: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pop = remember(newBuild) { Animatable(0.4f) }
    val bloom = remember(newBuild) { Animatable(1f) }
    var fromShown by remember(newBuild) { mutableStateOf(false) }
    LaunchedEffect(newBuild) {
        launch {
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch { pop.animateTo(1f, bounceSpring(stiffness = Spring.StiffnessLow)) }
        kotlinx.coroutines.delay(200)
        fromShown = true
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        AnimatedVisibility(
            visible = fromShown && currentBuild > 0,
            enter = fadeIn(tween(MotionShort)) + slideInHorizontally { -it / 2 } + expandHorizontally(),
            exit = fadeOut(tween(MotionShort)) + shrinkHorizontally(),
        ) {
            Row(
                Modifier.padding(end = GapRow),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapRow),
            ) {
                RollingNumber("$currentBuild", style = PrimaryTextStyle(), fontWeight = FontWeight.Normal, color = scheme.onSurface)
                Icon(
                    AppIcons.ArrowForward,
                    contentDescription = "to",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(ButtonIconSize),
                )
            }
        }
        Box(
            Modifier
                .drawBehind {
                    // The fanfare: a ring of the accent colour blooms out from behind the new build and fades as it goes.
                    val t = bloom.value
                    drawCircle(color = scheme.primary, radius = size.minDimension * (0.5f + 0.9f * t), alpha = 0.35f * (1f - t))
                }
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        ) {
            RollingNumber(
                "$newBuild",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        }
    }
}
