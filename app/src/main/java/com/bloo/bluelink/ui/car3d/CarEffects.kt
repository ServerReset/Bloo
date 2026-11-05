package com.bloo.bluelink.ui.car3d

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** How the cabin is being conditioned, for the airflow effect. */

/**
 * Effects drawn over the 3D car, positioned by projecting points on the model with [camera]:
 * air being drawn into the cowl intake and flowing through the cabin while climate runs
 * (warm or cool, and up the windshield when defrosting), and current flowing down the charge
 * cable into the port while charging.
 *
 * [cablePlugged] says how far the cable is pushed into the port (0..1), so the current only
 * flows once it's seated.
 */
@Composable
fun CarEffects(
    camera: CarCamera,
    climate: ClimateFx?,
    defrost: Boolean,
    charging: Boolean,
    cablePlugged: () -> Float,
    modifier: Modifier = Modifier,
    ownsCamera: Boolean = false,
    shape: CarShape = CarShape.IONIQ5,
) {
    val flows = Flows.of(shape)
    // Fade each effect in and out rather than popping.
    val air = remember { Animatable(if (climate != null) 1f else 0f) }
    val power = remember { Animatable(if (charging) 1f else 0f) }
    LaunchedEffect(climate) { air.animateTo(if (climate != null) 1f else 0f, tween(900)) }
    LaunchedEffect(charging) { power.animateTo(if (charging) 1f else 0f, tween(700)) }
    var airColor by remember { mutableFloatStateOf(0f) }
    if (climate != null) airColor = if (climate == ClimateFx.HEAT) 0f else 1f

    var time by remember { mutableFloatStateOf(0f) }
    val active = climate != null || charging || air.value > 0f || power.value > 0f
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }

    Canvas(modifier) {
        if (!active) return@Canvas
        if (ownsCamera) {
            camera.setSize(size.width.toInt(), size.height.toInt())
            camera.update()
        } else if (camera.width != size.width.toInt() || camera.height != size.height.toInt()) {
            return@Canvas // the renderer hasn't laid out yet
        }
        val t = time
        if (air.value > 0f) {
            val tint = lerp(OverlayColors.heat, OverlayColors.cool, airColor)
            val heat = airColor < 0.5f
            drawAirflow(flows, camera, t, tint, air.value, heat, defrost)
        }
        if (power.value > 0f) drawCharging(flows, camera, t, OverlayColors.charge, power.value * cablePlugged().coerceIn(0f, 1f))
    }
}

/** A polyline through the model, resampled at even spacing so particles move at a steady speed. */
private class Path3(points: List<DoubleArray>, spacing: Double = 0.03) {
    val xyz: DoubleArray
    val count: Int
    val length: Double

    init {
        val smooth = catmullRom(points, 10)
        val cum = DoubleArray(smooth.size)
        for (i in 1 until smooth.size) cum[i] = cum[i - 1] + dist(smooth[i - 1], smooth[i])
        length = cum.last()
        count = (length / spacing).toInt() + 1
        xyz = DoubleArray(count * 3)
        var j = 0
        for (k in 0 until count) {
            val s = k * length / (count - 1)
            while (j < cum.size - 2 && cum[j + 1] < s) j++
            val f = ((s - cum[j]) / (cum[j + 1] - cum[j]).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
            for (c in 0 until 3) xyz[k * 3 + c] = smooth[j][c] + (smooth[j + 1][c] - smooth[j][c]) * f
        }
    }

    /** Point at arc length [s] (clamped), written into [out]. */
    fun at(s: Double, out: DoubleArray) {
        val u = (s / length).coerceIn(0.0, 1.0) * (count - 1)
        val i = u.toInt().coerceAtMost(count - 2)
        val f = u - i
        for (c in 0 until 3) out[c] = xyz[i * 3 + c] + (xyz[(i + 1) * 3 + c] - xyz[i * 3 + c]) * f
    }

    companion object {
        fun dist(a: DoubleArray, b: DoubleArray): Double {
            val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun catmullRom(p: List<DoubleArray>, steps: Int): List<DoubleArray> {
            val pts = listOf(p.first()) + p + listOf(p.last())
            val out = ArrayList<DoubleArray>()
            for (i in 1 until pts.size - 2) {
                val p0 = pts[i - 1]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[i + 2]
                for (s in 0 until steps) {
                    val t = s.toDouble() / steps
                    out += DoubleArray(3) { c ->
                        0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t * t +
                            (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t * t * t)
                    }
                }
            }
            out += p.last()
            return out
        }
    }
}

private fun v(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)

/**
 * The particle paths for one car shape (see [CarShape]): air drawn over the bonnet into the cowl
 * intake, through the cabin, and up the windshield, and the charging cable.
 */
private class Flows(val shape: CarShape) {
    private val sc = shape.cowl.first
    private val yc = shape.cowl.second
    private val nose = shape.nose
    private val k = shape.halfWidth / 0.98 // lanes spread with the car's width
    private val lanes = doubleArrayOf(-0.62, -0.37, -0.12, 0.12, 0.37, 0.62).map { it * k }
    private fun hoodPoint(f: Double) = nose - f * (nose - sc)

    /** Outside air drawn over the bonnet into the intake at the base of the windshield. */
    val intake = lanes.map { x ->
        Path3(
            listOf(
                v(x * 1.3, yc + 0.005, nose + 0.7),
                v(x * 1.2, yc - 0.045, nose + 0.25),
                v(x * 1.05, shape.hoodAt(hoodPoint(0.19)) + 0.1, hoodPoint(0.19)),
                v(x * 0.9, shape.hoodAt(hoodPoint(0.48)) + 0.085, hoodPoint(0.48)),
                v(x * 0.76, shape.hoodAt(hoodPoint(0.77)) + 0.06, hoodPoint(0.77)),
                v(x * 0.64, yc + 0.06, sc + 0.09),
                v(x * 0.58, yc, sc),
            ),
        )
    }

    /** Conditioned air from the dash vents back through the cabin (seen through the glass). */
    val cabin = doubleArrayOf(-0.6, -0.14, 0.14, 0.6).map { it * k }.map { x ->
        val y = yc + 0.085
        val z = sc - 0.53
        Path3(listOf(v(x, y, z), v(x, y + 0.11, z - 0.38), v(x * 0.95, y + 0.16, z - 0.83), v(x * 0.9, y + 0.11, z - 1.28), v(x * 0.85, y + 0.06, z - 1.73)))
    }

    /** Defrost: air from the vents at the base of the windshield, up its inside. */
    val defrost = doubleArrayOf(-0.58, -0.36, -0.13, 0.13, 0.36, 0.58).map { it * k }.map { x ->
        val (ze, ye) = shape.defrostEnd
        fun at(f: Double) = (sc - 0.11 + (ze - sc + 0.11) * f) to (yc + 0.025 + (ye - yc - 0.025) * f)
        val p = listOf(0.0, 0.27, 0.53, 0.8, 1.0).map { at(it) }
        Path3(listOf(v(x, p[0].second, p[0].first), v(x, p[1].second, p[1].first), v(x * 1.04, p[2].second, p[2].first), v(x * 1.08, p[3].second, p[3].first), v(x * 1.1, p[4].second, p[4].first)))
    }

    val cable = Path3(shape.cable.reversed(), spacing = 0.02)

    val frontNormal = v(0.0, 0.45, 0.89)
    val frontPoint = v(0.0, yc + 0.025, sc + 0.49)
    val glassNormal = v(0.0, 0.85, 0.53)
    val glassPoint = v(0.0, yc + 0.275, sc - 0.51)

    companion object {
        private val cache = HashMap<CarShape, Flows>()
        fun of(shape: CarShape) = synchronized(cache) { cache.getOrPut(shape) { Flows(shape) } }
    }
}

private fun smoothstep(e0: Double, e1: Double, x: Double): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return (t * t * (3 - 2 * t)).toFloat()
}

private val scratchA = DoubleArray(3)
private val scratchB = DoubleArray(3)
private val pa = DoubleArray(3)
private val pb = DoubleArray(3)

/**
 * Draws streaks of air moving along [paths]: each particle is a short comet whose head is
 * opaque and whose tail fades, sized with perspective. [wobble] makes warm air shimmer.
 */
private fun DrawScope.drawStreams(
    cam: CarCamera,
    paths: List<Path3>,
    t: Float,
    color: Color,
    alpha: Float,
    speed: Double,
    perPath: Int,
    tail: Double,
    width: Double,
    wobble: Double,
    lead: Double,
) {
    if (alpha <= 0.01f) return
    val segments = 12
    paths.forEachIndexed { pi, path ->
        val cycle = path.length + tail
        for (k in 0 until perPath) {
            // Golden-ratio phases spread the particles evenly without looking regular.
            val phase = ((pi * 0.618 + k.toDouble() / perPath + pi * 0.137) % 1.0) * cycle
            val head = (t * speed + phase) % cycle
            for (sIdx in 0 until segments) {
                val s0 = head - tail * sIdx / segments
                val s1 = head - tail * (sIdx + 1) / segments
                if (s0 < 0 || s1 > path.length) continue
                path.at(s0.coerceAtMost(path.length), scratchA)
                path.at(s1.coerceAtLeast(0.0), scratchB)
                if (wobble > 0) {
                    scratchA[1] += wobble * sin(2 * PI * (s0 * 1.7 - t * 0.8) + pi)
                    scratchB[1] += wobble * sin(2 * PI * (s1 * 1.7 - t * 0.8) + pi)
                }
                if (!cam.project(scratchA[0], scratchA[1], scratchA[2], pa)) continue
                if (!cam.project(scratchB[0], scratchB[1], scratchB[2], pb)) continue
                // Fade in where the stream starts (air arriving) and out where it's drawn in.
                val along = s0 / path.length
                val edge = smoothstep(0.0, lead, along) * smoothstep(1.0, 0.86, along)
                val fall = 1f - sIdx.toFloat() / segments
                val a = alpha * edge * fall * fall
                if (a <= 0.01f) continue
                val px = cam.pixelsPerMetre(pa[2]) * width * (0.35 + 0.65 * fall)
                drawLine(
                    color.copy(alpha = a.coerceIn(0f, 1f)),
                    Offset(pa[0].toFloat(), pa[1].toFloat()),
                    Offset(pb[0].toFloat(), pb[1].toFloat()),
                    strokeWidth = max(px, 1.0).toFloat(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawAirflow(Flows: Flows, cam: CarCamera, t: Float, tint: Color, strength: Float, heat: Boolean, defrost: Boolean) {
    val front = smoothstep(-0.15, 0.35, cam.facing(Flows.frontPoint, Flows.frontNormal))
    val glass = smoothstep(-0.25, 0.3, cam.facing(Flows.glassPoint, Flows.glassNormal))
    val wobble = if (heat) 0.014 else 0.004
    // Inside first, since it's behind the glass.
    drawStreams(cam, Flows.cabin, t, tint, strength * 0.4f, 0.5, 4, 0.45, 0.014, wobble, 0.25)
    if (defrost) drawStreams(cam, Flows.defrost, t, tint, strength * 0.55f * glass, 0.45, 3, 0.35, 0.012, wobble * 0.5, 0.2)
    drawStreams(cam, Flows.intake, t, tint, strength * 0.8f * front, 0.8, 4, 0.6, 0.017, wobble, 0.3)
}

private fun DrawScope.drawCharging(Flows: Flows, cam: CarCamera, t: Float, green: Color, strength: Float) {
    if (strength <= 0.01f) return
    val port = Flows.shape.chargePort
    val visible = smoothstep(0.0, 0.35, cam.facing(port, Flows.shape.chargePortNormal))
    val a = strength * visible
    if (a <= 0.01f) return
    val bright = lerp(green, Color.White, 0.55f)

    // Indicator light at the port, breathing slowly as the car charges.
    if (cam.project(port[0], port[1], port[2], pa)) {
        val breath = 0.55f + 0.45f * sin(t * 2.6f).let { it * it }
        val r = (cam.pixelsPerMetre(pa[2]) * 0.085).toFloat()
        val c = Offset(pa[0].toFloat(), pa[1].toFloat())
        drawCircle(Brush.radialGradient(listOf(green.copy(alpha = 0.75f * a * breath), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        drawCircle(bright.copy(alpha = 0.9f * a * breath), (r * 0.16f).coerceAtLeast(1.5f), c)
    }

    // Pulses of current running along the cable into the port.
    val path = Flows.cable
    val pulses = 4
    val speed = 0.9
    for (k in 0 until pulses) {
        val s = (t * speed + k * path.length / pulses) % path.length
        path.at(s, scratchA)
        val x = port[0] + scratchA[0]
        val y = port[1] + scratchA[1]
        val z = port[2] + scratchA[2]
        if (!cam.project(x, y, z, pa)) continue
        val near = s / path.length
        val fade = smoothstep(0.0, 0.25, near) * smoothstep(1.0, 0.94, near)
        val ppm = cam.pixelsPerMetre(pa[2])
        val c = Offset(pa[0].toFloat(), pa[1].toFloat())
        val glow = (ppm * 0.07).toFloat()
        drawCircle(Brush.radialGradient(listOf(green.copy(alpha = 0.55f * a * fade), Color.Transparent), c, glow), glow, c)
        drawCircle(bright.copy(alpha = a * fade), (ppm * 0.016).toFloat().coerceAtLeast(1.5f), c)
    }
}

/** The colours of the air and charge effects, as the app's dark theme gives them. */
private object OverlayColors {
    val heat = Color(0xFFFF7A4D)
    val cool = Color(0xFF55B6EE)
    val charge = Color(0xFF44D58C)
}
