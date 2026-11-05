package com.bloo.bluelink.ui.car3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Orbit camera around the car, in the model's own metres (glTF axes: +X = driver's side,
 * +Y = up, +Z = front; the tyres sit on y = 0).
 *
 * Plain math with no Filament types, so the 3D renderer, the effects drawn over it and the
 * screenshot tests all use the same projection. tools/model/appcam.py mirrors it for renders.
 */
class CarCamera(shape: CarShape = CarShape.IONIQ5) {
    /** The car's box (mirrors left out), used to fit it on screen. */
    private val box = boxOf(shape)

    /** Degrees around the car: 0 looks at the nose, positive moves toward the driver's side. */
    var azimuth = HOME_AZIMUTH
    /** Degrees above the horizon. */
    var elevation = HOME_ELEVATION
    /** Multiplies the distance that fits the whole car on screen. */
    var zoom = 1.0

    var width = 1
        private set
    var height = 1
        private set

    /** Column-major, OpenGL conventions (what Filament's custom projection expects). */
    val projection = DoubleArray(16)
    val view = DoubleArray(16)
    val viewProjection = DoubleArray(16)
    val eye = DoubleArray(3)

    private var fitted = 8.0
    private var fittedAspect = -1.0

    fun setSize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        perspective(projection, FOV_Y, aspect, NEAR, FAR)
    }

    val aspect get() = width.toDouble() / height

    /** Pixels per metre at clip-space depth [w], for sizing effects with perspective. */
    fun pixelsPerMetre(w: Double) = projection[5] * height / 2 / w

    /** Recomputes the view from azimuth, elevation and zoom. Call once per frame. */
    fun update() {
        if (abs(aspect - fittedAspect) > 1e-3) {
            fitted = fitDistance(aspect, box)
            fittedAspect = aspect
        }
        eyeAt(azimuth, elevation, fitted * zoom, eye)
        lookAt(view, eye, TARGET)
        multiply(viewProjection, projection, view)
    }

    /**
     * Projects a model-space point to pixels. [out] gets x, y and the clip w (its depth).
     * Returns false when the point is behind the camera.
     */
    fun project(x: Double, y: Double, z: Double, out: DoubleArray): Boolean {
        val m = viewProjection
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw < 0.05) return false
        out[0] = (cx / cw * 0.5 + 0.5) * width
        out[1] = (0.5 - cy / cw * 0.5) * height
        out[2] = cw
        return true
    }

    /** Cosine between a surface normal at [p] and the direction to the camera (1 = facing it). */
    fun facing(p: DoubleArray, n: DoubleArray): Double {
        val dx = eye[0] - p[0]
        val dy = eye[1] - p[1]
        val dz = eye[2] - p[2]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        return (dx * n[0] + dy * n[1] + dz * n[2]) / len
    }

    companion object {
        const val FOV_Y = 30.0
        const val NEAR = 0.1
        const val FAR = 60.0
        const val HOME_AZIMUTH = 40.0
        const val HOME_ELEVATION = 12.0
        const val MIN_ELEVATION = 4.0
        const val MAX_ELEVATION = 30.0
        /** Looking at the charge port on the rear right quarter panel. */
        const val PORT_AZIMUTH = -120.0
        val TARGET = doubleArrayOf(0.0, 0.58, -0.05)

        fun boxOf(s: CarShape) = buildList {
            for (x in doubleArrayOf(-s.halfWidth, s.halfWidth)) for (y in doubleArrayOf(0.0, s.height)) for (z in doubleArrayOf(-s.halfLength, s.halfLength)) add(doubleArrayOf(x, y, z))
        }

        private fun eyeAt(az: Double, el: Double, d: Double, out: DoubleArray) {
            val a = az * PI / 180
            val e = el * PI / 180
            out[0] = TARGET[0] + d * cos(e) * sin(a)
            out[1] = TARGET[1] + d * sin(e)
            out[2] = TARGET[2] + d * cos(e) * cos(a)
        }

        /** Smallest distance at which the whole car fits all the way around, so orbiting never clips it. */
        fun fitDistance(aspect: Double, box: List<DoubleArray> = boxOf(CarShape.IONIQ5)): Double {
            val p = DoubleArray(16).also { perspective(it, FOV_Y, aspect, NEAR, FAR) }
            val v = DoubleArray(16)
            val vp = DoubleArray(16)
            val e = DoubleArray(3)
            fun fits(d: Double): Boolean {
                for (el in doubleArrayOf(HOME_ELEVATION)) {
                    var az = 0.0
                    while (az < 360.0) {
                        eyeAt(az, el, d, e)
                        lookAt(v, e, TARGET)
                        multiply(vp, p, v)
                        for (c in box) {
                            val cx = vp[0] * c[0] + vp[4] * c[1] + vp[8] * c[2] + vp[12]
                            val cy = vp[1] * c[0] + vp[5] * c[1] + vp[9] * c[2] + vp[13]
                            val cw = vp[3] * c[0] + vp[7] * c[1] + vp[11] * c[2] + vp[15]
                            if (cw <= 0 || abs(cx / cw) > FIT_X || abs(cy / cw) > FIT_Y) return false
                        }
                        az += 15.0
                    }
                }
                return true
            }
            var lo = 2.0
            var hi = 30.0
            repeat(40) {
                val mid = (lo + hi) / 2
                if (fits(mid)) hi = mid else lo = mid
            }
            return hi
        }

        private const val FIT_X = 1.0
        private const val FIT_Y = 0.92

        fun perspective(m: DoubleArray, fovY: Double, aspect: Double, near: Double, far: Double) {
            val f = 1 / tan(fovY * PI / 360)
            m.fill(0.0)
            m[0] = f / aspect
            m[5] = f
            m[10] = (far + near) / (near - far)
            m[11] = -1.0
            m[14] = 2 * far * near / (near - far)
        }

        fun lookAt(m: DoubleArray, eye: DoubleArray, target: DoubleArray) {
            var fx = target[0] - eye[0]
            var fy = target[1] - eye[1]
            var fz = target[2] - eye[2]
            val fl = sqrt(fx * fx + fy * fy + fz * fz)
            fx /= fl; fy /= fl; fz /= fl
            // s = f x up(0,1,0)
            var sx = -fz
            var sz = fx
            val sl = sqrt(sx * sx + sz * sz)
            sx /= sl; sz /= sl
            // u = s x f
            val ux = -sz * fy
            val uy = sz * fx - sx * fz
            val uz = sx * fy
            m.fill(0.0)
            m[0] = sx; m[4] = 0.0; m[8] = sz; m[12] = -(sx * eye[0] + sz * eye[2])
            m[1] = ux; m[5] = uy; m[9] = uz; m[13] = -(ux * eye[0] + uy * eye[1] + uz * eye[2])
            m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = fx * eye[0] + fy * eye[1] + fz * eye[2]
            m[15] = 1.0
        }

        fun multiply(out: DoubleArray, a: DoubleArray, b: DoubleArray) {
            for (c in 0 until 4) for (r in 0 until 4) {
                var s = 0.0
                for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
                out[c * 4 + r] = s
            }
        }
    }
}
