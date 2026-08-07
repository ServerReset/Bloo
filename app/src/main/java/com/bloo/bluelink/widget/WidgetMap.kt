package com.bloo.bluelink.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Renders the widget's location map thumbnail to a [Bitmap].
 *
 * Glance can't use Coil's async painters, so — like the charge ring — the map is
 * produced as a plain [Bitmap] here (in the widget's suspend provideGlance) and
 * shown via `Image(ImageProvider(bitmap))`. It fetches OpenStreetMap "slippy
 * map" tiles at a fixed zoom, disk-caches them by z/x/y (so a car sitting still
 * never re-downloads), and draws a window CENTRED on the car with a marker in
 * the middle.
 *
 * Centring is the whole point of the tile mosaic below. It used to draw one
 * whole tile and put the marker at the car's fractional position inside it,
 * which meant a car parked near a tile boundary sat against the edge of the
 * thumbnail with all of its surroundings on the wrong side -- and since the
 * layout shows this square through a wide, short slot with ContentScale.Crop,
 * a car near the tile's top or bottom edge was cropped off the widget
 * altogether. The map was then a picture of somewhere near the car rather than
 * of the car, with no marker in it at all.
 *
 * Uses the same Web-Mercator projection + OSM tile-usage-policy User-Agent the
 * watch's MapThumbnail proved out. All I/O is wrapped so a network failure just
 * yields null (the caller then skips the map module) rather than throwing into
 * the widget process.
 */
object WidgetMap {

    // Was 15 -- street level, close enough that the one-tile window around
    // the car showed a single road fragment with no surrounding context (a
    // house number's worth of street, not a neighbourhood). 13 covers
    // roughly 4x the ground per tile at the same pixel size, reading as
    // "which part of town" rather than "which specific driveway".
    private const val ZOOM = 13
    private val TILE_PX = com.bloo.bluelink.data.MapTiles.TILE_PX
    // OSM's usage policy requires an identifying User-Agent; a default/missing one
    // gets a "blocked" tile back. Built by MapTiles so all three surfaces send the
    // same shape -- the phone's was still the unversioned, contactless kind.
    private val USER_AGENT = com.bloo.bluelink.data.MapTiles.userAgent("Android")
    // Cap on cached tiles — a car driving around would otherwise accrete PNGs in
    // cacheDir forever. At ~12-40KB/tile this stays well under a couple MB.
    private const val MAX_CACHED_TILES = 40

    /**
     * @param sizePx square output edge in px (caller converts dp→px)
     * @param markerColor ARGB of the location dot
     * @return the map bitmap with a marker, or null if the tile couldn't be fetched
     *
     * Runs on [Dispatchers.IO]: Glance's provideGlance is suspend but doesn't
     * guarantee an IO dispatcher, and this does real network + file + bitmap I/O.
     */
    suspend fun render(
        context: Context,
        lat: Double,
        lon: Double,
        sizePx: Int,
        markerColor: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val edge = sizePx.coerceIn(48, 1024)
        val n = com.bloo.bluelink.data.MapTiles.span(ZOOM)
        val xf = com.bloo.bluelink.data.MapTiles.tileX(lon, ZOOM)
        val yf = com.bloo.bluelink.data.MapTiles.tileY(lat, ZOOM)
        val xt = xf.toInt()
        val yt = yf.toInt()

        // A one-tile-wide window of the world, centred on the car rather than
        // aligned to the tile grid. In global pixel coordinates at this zoom
        // the car is at (xf, yf) * TILE_PX, and the window is the TILE_PX box
        // around it -- which straddles a tile boundary unless the car happens
        // to sit dead centre of one, hence up to four tiles below.
        val left = xf * TILE_PX - TILE_PX / 2.0
        val top = yf * TILE_PX - TILE_PX / 2.0
        val scale = edge.toDouble() / TILE_PX

        val out = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var haveCentre = false
        for (ty in floorDiv(top)..floorDiv(top + TILE_PX - 1)) {
            // No vertical wrap: past the poles there is no tile to fetch, and
            // the window simply keeps whatever the neighbours drew.
            if (ty < 0 || ty >= n) continue
            for (tx in floorDiv(left)..floorDiv(left + TILE_PX - 1)) {
                // Longitude does wrap, so a car near the antimeridian still
                // gets the tiles on the far side of it rather than a gap.
                val wrapped = com.bloo.bluelink.data.MapTiles.wrapX(tx, ZOOM)
                val tile = fetchTile(context, wrapped, ty) ?: continue
                if (wrapped == xt && ty == yt) haveCentre = true
                val dl = ((tx * TILE_PX - left) * scale).toFloat()
                val dt = ((ty * TILE_PX - top) * scale).toFloat()
                val span = (TILE_PX * scale).toFloat()
                canvas.drawBitmap(tile, null, android.graphics.RectF(dl, dt, dl + span, dt + span), paint)
            }
        }
        // The neighbours are best-effort -- a missing one just leaves that
        // corner blank -- but without the tile the car is actually ON there is
        // no map here worth showing, and the caller's null path (skip the
        // module entirely) is the honest answer.
        if (!haveCentre) return@withContext null

        val cx = edge / 2f
        val cy = edge / 2f
        val dotR = edge * 0.06f
        // White halo behind the coloured dot so it reads on any map background.
        canvas.drawCircle(cx, cy, dotR + edge * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        })
        canvas.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerColor })
        out
    }

    /** Floor division into tile indices. The window's edges are doubles that
     *  go negative near the antimeridian, where `toInt()` truncates toward
     *  zero and would name the wrong tile. */
    private fun floorDiv(px: Double): Int = Math.floorDiv(Math.floor(px).toLong(), TILE_PX.toLong()).toInt()

    /** Fetch a tile from disk cache, or download + cache it. Null on any failure. */
    private fun fetchTile(context: Context, x: Int, y: Int): Bitmap? {
        val cacheDir = File(context.cacheDir, "widget_map").also { it.mkdirs() }
        val file = File(cacheDir, "${ZOOM}_${x}_$y.png")
        if (file.exists()) {
            // Touch on a cache hit so eviction below is genuinely LRU (last-used),
            // not just oldest-downloaded.
            runCatching { file.setLastModified(System.currentTimeMillis()) }
            runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()?.let { return it }
        }
        return runCatching {
            val url = URL(com.bloo.bluelink.data.MapTiles.tileUrl(ZOOM, x, y))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                // Persist for next time, then trim the cache. Both best-effort.
                runCatching { file.writeBytes(bytes); evictOldest(cacheDir) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    /** Keep the tile cache bounded: when it exceeds [MAX_CACHED_TILES], delete the
     *  least-recently-used files (oldest lastModified) down to the cap. */
    private fun evictOldest(cacheDir: File) {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= MAX_CACHED_TILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED_TILES)
            .forEach { runCatching { it.delete() } }
    }
}
