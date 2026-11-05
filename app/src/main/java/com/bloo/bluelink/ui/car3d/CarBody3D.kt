package com.bloo.bluelink.ui.car3d

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Which 3D car to show. The IONIQ 5 has its own model; any other car gets a stand-in of its
 * body type (tools/model/generic.py), with the same hinged parts so doors, frunk, tailgate and
 * the charging cable work the same way.
 */
enum class CarBody(
    val label: String,
    val glb: String,
    /** Effect anchors written by the model tools; null = the IONIQ 5's, built in. */
    val anchors: String?,
) {
    IONIQ5("IONIQ 5", "models/car.glb", null),
    SEDAN("Sedan", "models/car_sedan.glb", "models/car_sedan-anchors.json"),
    CROSSOVER("Crossover", "models/car_crossover.glb", "models/car_crossover-anchors.json"),
    SUV("SUV", "models/car_suv.glb", "models/car_suv-anchors.json");

    companion object {
        /** The body type for a Bluelink model name ("IONIQ 5", "Kona Electric", "Santa Fe"…). */
        fun forModel(model: String?): CarBody {
            val m = model.orEmpty().uppercase().replace("-", " ")
            return when {
                Regex("IONIQ\\s*5").containsMatchIn(m) -> IONIQ5
                listOf("PALISADE", "SANTA FE", "SANTAFE", "IONIQ 9", "IONIQ9", "SANTA CRUZ", "XCIENT").any { it in m } -> SUV
                listOf("ELANTRA", "SONATA", "IONIQ 6", "IONIQ6", "ACCENT", "AZERA", "GENESIS").any { it in m } -> SEDAN
                else -> CROSSOVER // Kona, Venue, Tucson, Nexo, IONIQ (hatch) and anything new
            }
        }

        private const val PREFS = "car_look"

        /** The user's choice in Settings, or null for automatic. */
        fun override(context: Context): CarBody? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("body", null)?.let { n -> entries.firstOrNull { it.name == n } }

        fun setOverride(context: Context, body: CarBody?) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("body", body?.name).apply()

        private val shapes = HashMap<CarBody, CarShape>()

        fun shape(context: Context, body: CarBody): CarShape = synchronized(shapes) {
            shapes.getOrPut(body) {
                body.anchors?.let { path ->
                    runCatching { CarShape.parse(context.assets.open(path).use { it.readBytes().decodeToString() }) }
                        .onFailure { android.util.Log.w("OpenBlue", "Couldn't read $path", it) }.getOrNull()
                } ?: CarShape.IONIQ5
            }
        }
    }
}

/** Paint colours to choose from for the 3D car (linear RGB, as the model's materials take it). */
enum class CarPaint(val label: String, val rgb: FloatArray, val swatch: Long) {
    WHITE("White", floatArrayOf(0.78f, 0.78f, 0.75f), 0xFFE9E9E4),
    SILVER("Silver", floatArrayOf(0.42f, 0.43f, 0.44f), 0xFFAEB1B4),
    GREY("Grey", floatArrayOf(0.12f, 0.125f, 0.13f), 0xFF5E6266),
    BLACK("Black", floatArrayOf(0.012f, 0.012f, 0.014f), 0xFF17181A),
    BLUE("Blue", floatArrayOf(0.02f, 0.07f, 0.22f), 0xFF234C86),
    RED("Red", floatArrayOf(0.35f, 0.012f, 0.012f), 0xFFA3202A),
    GREEN("Green", floatArrayOf(0.05f, 0.11f, 0.07f), 0xFF3D5B47),
    SAND("Sand", floatArrayOf(0.42f, 0.36f, 0.26f), 0xFFB8A88A);

    companion object {
        private const val PREFS = "car_look"
        fun current(context: Context): CarPaint =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("paint", null)?.let { n -> entries.firstOrNull { it.name == n } } ?: WHITE
        fun set(context: Context, paint: CarPaint) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("paint", paint.name).apply()
    }
}

/**
 * The points on a car model the effects drawn over it need, in glTF axes (+X driver's side,
 * +Y up, +Z front): the charge port and cable, the bonnet's height along the car, where the
 * windshield starts (the cowl intake) and ends, and the car's size for framing it.
 */
class CarShape(
    val halfLength: Double,
    val halfWidth: Double,
    val height: Double,
    val nose: Double,
    val chargePort: DoubleArray,
    val chargePortNormal: DoubleArray,
    /** Relative to the port. */
    val cable: List<DoubleArray>,
    /** (z along the car, height of the bonnet's top), from the nose back. */
    val hood: List<Pair<Double, Double>>,
    val cowl: Pair<Double, Double>,
    /** Where defrost air ends up the inside of the windshield. */
    val defrostEnd: Pair<Double, Double>,
) {
    fun hoodAt(z: Double): Double {
        if (z >= hood.first().first) return hood.first().second
        for (i in 0 until hood.size - 1) {
            val (z0, y0) = hood[i]
            val (z1, y1) = hood[i + 1]
            if (z <= z0 && z >= z1) return y0 + (y1 - y0) * (z0 - z) / (z0 - z1)
        }
        return hood.last().second
    }

    companion object {
        private fun v(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)

        /** tools/model/rig.py's anchors for the IONIQ 5 model. */
        val IONIQ5 = CarShape(
            halfLength = 2.355, halfWidth = 0.98, height = 1.55, nose = 2.35,
            chargePort = v(-0.851, 0.96, -1.67),
            chargePortNormal = v(-0.991, 0.0, -0.133),
            cable = listOf(
                v(-0.188, 0.0, -0.025), v(-0.241, -0.002, -0.027), v(-0.307, -0.06, -0.041), v(-0.357, -0.257, -0.077),
                v(-0.416, -0.5, -0.136), v(-0.496, -0.736, -0.214), v(-0.614, -0.92, -0.333), v(-0.787, -0.958, -0.507),
                v(-1.041, -0.944, -0.75), v(-1.488, -0.945, -1.151), v(-1.883, -0.944, -1.5),
            ),
            hood = listOf(
                2.25 to 0.757, 2.2 to 0.794, 2.1 to 0.85, 2.0 to 0.894, 1.9 to 0.928, 1.8 to 0.953,
                1.7 to 0.973, 1.6 to 0.99, 1.5 to 1.003, 1.4 to 1.013,
            ),
            cowl = 1.31 to 0.975,
            defrostEnd = 0.45 to 1.39,
        )

        /** tools/model/generic.py's anchors file. */
        fun parse(text: String): CarShape {
            val o = Json.parseToJsonElement(text).jsonObject
            fun d(k: String) = o[k]!!.jsonPrimitive.double
            fun vec(e: kotlinx.serialization.json.JsonElement) = e.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            fun pair(k: String) = vec(o[k]!!).let { it[0] to it[1] }
            val cowl = pair("cowl")
            val top = pair("windshieldTop")
            // 85 % of the way up the windshield, just inside it.
            val f = 0.85
            val defrost = (cowl.first - f * (cowl.first - top.first)) to (cowl.second + f * (top.second - cowl.second) - 0.04)
            return CarShape(
                halfLength = d("length") / 2, halfWidth = d("width") / 2 + 0.03, height = d("height"), nose = d("nose"),
                chargePort = vec(o["chargePort"]!!),
                chargePortNormal = vec(o["chargePortNormal"]!!),
                cable = (o["cablePath"] as JsonArray).map { vec(it) },
                hood = (o["hood"] as JsonArray).map { vec(it).let { p -> p[0] to p[1] } },
                cowl = cowl,
                defrostEnd = defrost,
            )
        }
    }
}
