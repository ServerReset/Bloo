package com.bloo.bluelink.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Renders the widget's location map thumbnail to a [Bitmap].
 *
 * Glance can't use Coil's async painters, so — like the charge ring — the map is
 * produced as a plain [Bitmap] here (in the widget's suspend provideGlance) and
 * shown via `Image(ImageProvider(bitmap))`. It fetches a single OpenStreetMap
 * "slippy map" tile at a fixed zoom, disk-caches it by z/x/y (so a car sitting
 * still never re-downloads), and draws a marker dot at the car's fractional
 * position within the tile.
 *
 * Uses the same Web-Mercator projection + OSM tile-usage-policy User-Agent the
 * watch's MapThumbnail proved out. All I/O is wrapped so a network failure just
 * yields null (the caller then skips the map module) rather than throwing into
 * the widget process.
 */
object WidgetMap {

    private const val ZOOM = 15
    private const val TILE_PX = 256
    // OSM's usage policy requires an identifying User-Agent; a default/missing one
    // gets a "blocked" tile back. Mirrors WearImage's fix.
    private const val USER_AGENT = "Bloo-Android/1.0 (https://claude.ai/code)"

    /**
     * @param sizePx square output edge in px (caller converts dp→px)
     * @param markerColor ARGB of the location dot
     * @return the map bitmap with a marker, or null if the tile couldn't be fetched
     */
    suspend fun render(
        context: Context,
        lat: Double,
        lon: Double,
        sizePx: Int,
        markerColor: Int,
    ): Bitmap? {
        val edge = sizePx.coerceIn(48, 1024)
        val n = (1 shl ZOOM).toDouble()
        val latRad = Math.toRadians(lat)
        val xf = (lon + 180.0) / 360.0 * n
        val yf = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val xt = xf.toInt()
        val yt = yf.toInt()
        val fracX = (xf - xt).toFloat()
        val fracY = (yf - yt).toFloat()

        val tile = fetchTile(context, xt, yt) ?: return null

        // Scale the 256px tile to the requested edge, then draw the marker.
        val out = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val src = android.graphics.Rect(0, 0, tile.width, tile.height)
        val dst = android.graphics.Rect(0, 0, edge, edge)
        canvas.drawBitmap(tile, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        val cx = fracX * edge
        val cy = fracY * edge
        val dotR = edge * 0.06f
        // White halo behind the coloured dot so it reads on any map background.
        canvas.drawCircle(cx, cy, dotR + edge * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        })
        canvas.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerColor })
        return out
    }

    /** Fetch a tile from disk cache, or download + cache it. Null on any failure. */
    private fun fetchTile(context: Context, x: Int, y: Int): Bitmap? {
        val cacheDir = File(context.cacheDir, "widget_map").also { it.mkdirs() }
        val file = File(cacheDir, "${ZOOM}_${x}_$y.png")
        if (file.exists()) {
            runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()?.let { return it }
        }
        return runCatching {
            val url = URL("https://tile.openstreetmap.org/$ZOOM/$x/$y.png")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                // Persist for next time, then decode. Write is best-effort.
                runCatching { file.writeBytes(bytes) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
}
