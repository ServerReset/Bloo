package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning

// The phone's design-system vocabulary that the button system is built on, moved here so the watch
// can use the same one: the icon for a given button label, the spin used by an in-flight glyph, and
// the three motion durations every transition in the app is measured against.

const val MotionShort = 180   // fades and small slides
const val MotionMedium = 320  // panels, cards, colour sweeps
const val MotionLong = 400    // large or emphasised moves

object AppIcons {
  // Icons.Filled.X, not a bare `X`: these are extension properties, and a bare name would resolve
  // to this very property (self-referencing initializer).
  val Settings = Icons.Filled.Settings
  val Lock = Icons.Filled.Lock
  val Bolt = Icons.Filled.Bolt
  val Close = Icons.Filled.Close
  val ArrowForward = Icons.AutoMirrored.Filled.ArrowForward
  val Search = Icons.Filled.Search
  val Check = Icons.Filled.Check
  val CheckCircle = Icons.Filled.CheckCircle
  val Info = Icons.Filled.Info
  val DirectionsCar = Icons.Filled.DirectionsCar
  val AutoAwesome = Icons.Filled.AutoAwesome
  val Warning = Icons.Filled.Warning
  val Fullscreen = Icons.Filled.Fullscreen
  val FullscreenExit = Icons.Filled.FullscreenExit
}

@Composable
fun rememberSpinAngle(spinning: Boolean): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val angle = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(spinning) {
        if (spinning) {
            angle.animateTo(angle.value + 360f, androidx.compose.animation.core.tween(850, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            while (true) {
                angle.animateTo(angle.value + 360f, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing))
            }
        } else if (angle.value != 0f) {
            val target = kotlin.math.ceil(angle.value / 360f) * 360f
            angle.animateTo(target, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            angle.snapTo(0f)
        }
    }
    return angle
}
