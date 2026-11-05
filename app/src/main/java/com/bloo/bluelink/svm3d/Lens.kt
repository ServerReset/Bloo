package com.bloo.bluelink.svm3d

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Geometry of the car's four surround-view cameras.
 *
 * Car frame: X to the right, Y forward, Z up, in metres, origin on the ground under the middle of
 * the car. The cameras are fisheye lenses (Kannala-Brandt model) whose poses were solved by
 * matching each camera's view of the ground to the car's own stitched top view (tools/svm3d).
 */
class SvmCamera(
    val name: String,
    val x: Double, val y: Double, val z: Double,
    val yaw: Double, val pitch: Double, val roll: Double,
    val f: Double, val cx: Double, val cy: Double,
    val k1: Double, val k2: Double, val aspect: Double,
) {
    /** Camera-to-car rotation, row-major; columns are the camera's right, down and forward axes. */
    val r: DoubleArray = rotation(yaw, pitch, roll)
    val position = doubleArrayOf(x, y, z)

    /**
     * Car-frame point to fisheye pixel. Returns false if it's outside the lens's useful range.
     * [out] receives u, v.
     */
    fun project(px: Double, py: Double, pz: Double, out: DoubleArray): Boolean {
        val dx = px - x; val dy = py - y; val dz = pz - z
        // Pc = R^T * d
        val cxv = r[0] * dx + r[3] * dy + r[6] * dz
        val cyv = r[1] * dx + r[4] * dy + r[7] * dz
        val czv = r[2] * dx + r[5] * dy + r[8] * dz
        val rho = sqrt(cxv * cxv + cyv * cyv) + 1e-9
        val th = atan2(rho, czv)
        val rr = f * (th + k1 * th * th * th + k2 * th * th * th * th * th)
        out[0] = cx + rr * cxv / rho
        out[1] = cy + aspect * rr * cyv / rho
        return th < MAX_THETA
    }

    companion object {
        const val MAX_THETA = 100.0 * Math.PI / 180.0

        fun rotation(yaw: Double, pitch: Double, roll: Double): DoubleArray {
            val f = doubleArrayOf(sin(yaw) * cos(pitch), cos(yaw) * cos(pitch), sin(pitch))
            // right = f x up
            var rx = f[1]; var ry = -f[0]; var rz = 0.0
            val n = sqrt(rx * rx + ry * ry + rz * rz)
            rx /= n; ry /= n; rz /= n
            // down = f x right
            val dx = f[1] * rz - f[2] * ry
            val dy = f[2] * rx - f[0] * rz
            val dz = f[0] * ry - f[1] * rx
            val c = cos(roll); val s = sin(roll)
            val r2 = doubleArrayOf(c * rx + s * dx, c * ry + s * dy, c * rz + s * dz)
            val d2 = doubleArrayOf(-s * rx + c * dx, -s * ry + c * dy, -s * rz + c * dz)
            return doubleArrayOf(
                r2[0], d2[0], f[0],
                r2[1], d2[1], f[1],
                r2[2], d2[2], f[2],
            )
        }
    }
}

/** The calibrated cameras of this IONIQ 5 (2027). */
object SvmCalibration {
    private const val F = 244.758979
    private const val CX = 481.527977
    private const val CY = 353.917785
    private const val K1 = 0.045343
    private const val K2 = -0.00849
    private const val ASP = 0.997689

    val FRONT = SvmCamera("front", 0.017671, 2.288166, 0.761237, 0.013279, -0.023804, -0.014806, F, CX, CY, K1, K2, ASP)
    val REAR = SvmCamera("rear", -0.00664, -2.39178, 0.716708, 3.150202, -0.35439, 0.012947, F, CX, CY, K1, K2, ASP)
    val LEFT = SvmCamera("left", -1.050037, 0.631521, 1.067092, -1.908542, -1.027386, 0.424651, F, CX, CY, K1, K2, ASP)
    val RIGHT = SvmCamera("right", 1.054931, 0.576321, 1.088584, 2.086966, -1.029079, -0.303032, F, CX, CY, K1, K2, ASP)
    val ALL = listOf(FRONT, REAR, LEFT, RIGHT)

    /** The fisheye frames these were solved for. */
    const val FRAME_WIDTH = 960
    const val FRAME_HEIGHT = 720

    /** IONIQ 5 body. */
    const val CAR_LENGTH = 4.655
    const val CAR_WIDTH = 1.89

    /** Middle of each camera's share of the surroundings, as a bearing from the car (degrees). */
    val SECTOR = mapOf("front" to 0.0, "right" to 90.0, "rear" to 180.0, "left" to -90.0)
}

/**
 * A square, ordinary (pinhole) view cut out of a fisheye, looking along [yaw], [pitch] (radians,
 * car frame), [n] pixels across a 100° field of view. Depth models want pictures like this.
 */
class CropView(val camera: SvmCamera, val yaw: Double, val pitch: Double, val n: Int = 518) {
    val fp = (n / 2.0) / tan(Math.toRadians(50.0))
    private val r = SvmCamera.rotation(yaw, pitch, 0.0)
    val axis = doubleArrayOf(r[2], r[5], r[8])
    val right = doubleArrayOf(r[0], r[3], r[6])
    val down = doubleArrayOf(r[1], r[4], r[7])
    val centerAzimuthDeg = Math.toDegrees(yaw)

    /** Unit ray through pixel (row i, column j), into [out]. */
    fun ray(i: Int, j: Int, out: DoubleArray) {
        val a = (j + 0.5 - n / 2.0) / fp
        val b = (i + 0.5 - n / 2.0) / fp
        val x = r[0] * a + r[1] * b + r[2]
        val y = r[3] * a + r[4] * b + r[5]
        val z = r[6] * a + r[7] * b + r[8]
        val l = sqrt(x * x + y * y + z * z)
        out[0] = x / l; out[1] = y / l; out[2] = z / l
    }

    /**
     * Pixel (row, column) that unit direction [d] falls on, or null if it's behind or outside.
     */
    fun pixelOf(dx: Double, dy: Double, dz: Double, out: DoubleArray): Boolean {
        val zc = dx * axis[0] + dy * axis[1] + dz * axis[2]
        if (zc < 0.1) return false
        out[1] = fp * (dx * right[0] + dy * right[1] + dz * right[2]) / zc + n / 2.0
        out[0] = fp * (dx * down[0] + dy * down[1] + dz * down[2]) / zc + n / 2.0
        return out[0] >= 0 && out[0] < n - 1 && out[1] >= 0 && out[1] < n - 1
    }
}
