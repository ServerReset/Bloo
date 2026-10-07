package com.bloo.bluelink.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Plays a short celebratory sound for the first-run confetti moment. If a bundled clip exists at
 * `res/raw/celebrate` (drop in a royalty-free fireworks / party-popper file to use it), that's
 * played; otherwise it falls back to the device's default notification sound. No audio is
 * synthesized.
 */
object Fireworks {

    fun playSound(context: Context) {
        val ctx = context.applicationContext
        runCatching {
            // getIdentifier by name, deliberately: the `celebrate` clip is an OPTIONAL drop-in
            // resource (see this object's own doc) -- a compile-time R.raw.celebrate reference
            // would fail the build for every checkout that has not added one.
            @Suppress("DiscouragedApi")
            val resId = ctx.resources.getIdentifier("celebrate", "raw", ctx.packageName)
            if (resId != 0) {
                MediaPlayer.create(ctx, resId)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(ctx, uri)?.play()
            }
        }
    }
}

internal class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)
/**
 * A short, lightweight particle-burst fireworks animation drawn on a Canvas. Seven [Burst]s are
 * generated once (`remember`) with randomized position, start-delay, lifetime, hue, particle count,
 * and max radius.
 */
@Composable
internal fun FireworksOverlay(modifier: Modifier = Modifier, bursts: Int = 7) {
    val burstList = remember(bursts) {
        val r = kotlin.random.Random(System.nanoTime())
        List(bursts) {
            Burst(
                x = r.nextFloat() * 0.8f + 0.1f,
                y = r.nextFloat() * 0.5f + 0.12f,
                start = r.nextFloat() * 0.55f,
                life = r.nextFloat() * 0.25f + 0.35f,
                hue = r.nextFloat() * 360f,
                count = 18 + r.nextInt(14),
                maxR = r.nextFloat() * 0.12f + 0.14f,
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600)) }
    Canvas(modifier) {
        burstList.forEach { b ->
            val local = ((t.value - b.start) / b.life)
            if (local <= 0f || local >= 1f) return@forEach
            val cx = b.x * size.width
            val cy = b.y * size.height
            val r = local * b.maxR * size.height
            val alpha = (1f - local).coerceIn(0f, 1f)
            val color = Color.hsv(b.hue, 0.85f, 1f).copy(alpha = alpha)
            for (k in 0 until b.count) {
                val ang = (k.toFloat() / b.count) * (2f * Math.PI.toFloat())
                val px = cx + kotlin.math.cos(ang) * r
                val py = cy + kotlin.math.sin(ang) * r + local * local * size.height * 0.06f
                drawCircle(color, radius = 5f * alpha + 1.5f, center = Offset(px, py))
            }
        }
    }
}
