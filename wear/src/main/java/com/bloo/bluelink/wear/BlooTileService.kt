package com.bloo.bluelink.wear

import com.bloo.bluelink.wear.TilePalette.WHITE
import com.bloo.bluelink.wear.TilePalette.MUTED
import com.bloo.bluelink.wear.TilePalette.RIM
import com.bloo.bluelink.wear.TilePalette.BUTTON
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.chargeLeftLabel
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import com.bloo.bluelink.ioScope
import com.google.common.util.concurrent.ListenableFuture

/**
 * The watch's Tile: the car in the carousel, and -- the part that makes it a widget rather than a
 * card -- the actions worth doing without opening the app.
 *
 * Three things it does now that it did not:
 *
 *  - It draws the car's charge as a RING, the same mechanic as the app's hero, so the tile and the
 *    app read as one product. Protolayout has the primitive (Arc + ArcLine), so this is the platform
 *    drawing it rather than a picture.
 *  - It carries up to three action buttons. A tile's button cannot run code -- a click can only be an
 *    Action -- so each one launches the app with the action in its extras, and the app runs it
 *    exactly as a tap on the watch face does.
 * *
 * It also has a content description, so the glance is one spoken sentence instead of four fragments.
 */
class BlooTileService : TileService() {
    private val scope = ioScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        answerTile(scope, "BlooTile", fallback = { tileOf(layoutFor(null)) }) { buildTile(requestParams) }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val snapshot = loadTileSnapshot(this)
                // The display's real size: the quarters are sized from it (not stretched to fill), so they are equal squares.
                val device = requestParams.deviceConfiguration
                val side = minOf(device.screenWidthDp, device.screenHeightDp).toFloat().takeIf { it > 0f } ?: REFERENCE_SIDE
                val car = snapshot?.let { WearTileCar.pick(applicationContext, it.vehicles) ?: it.selected }
                // Whatever goes wrong while drawing, the request is answered: an unset completer leaves the tile
                // spinning for good, so a failure draws the plain "open Bloo" layout instead.
                val layout = runCatching { layoutFor(car, side) }.getOrElse { layoutFor(null) }
                return tileOf(layout)
    }

    private fun tileOf(layout: LayoutElement): TileBuilders.Tile {
        val freshness = runCatching {
            60_000L * com.bloo.bluelink.data.DeviceStatus.policy(applicationContext).pollIntervalMultiplier
        }.getOrDefault(60_000L)
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Refresh about once a minute while it is on screen.
            .setFreshnessIntervalMillis(freshness)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
            // The face's glyphs, as drawables the tile can draw (a tile cannot use the app's Compose icons).
            GLYPHS.forEach { (name, res) ->
                resources.addIdToImageMapping(
                    name,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build())
                        .build(),
                )
            }
            completer.set(resources.build())
            "BlooTileResources"
        }

    private fun layoutFor(car: VehicleSnapshot?, side: Float = REFERENCE_SIDE): LayoutElement {
        // The whole tile opens the app. The quarters and the hub are clickables of their own inside it, and the system
        // routes the innermost hit, so a tap on "Charge" charges and a tap on the hub or anywhere else opens Bloo.
        val open = tileModifiers(clickableId = "open", onClick = tileLaunch(null))
        if (car != null) return faceLayout(car, open, side)

        // No car: say why.
        val line = "Add a car in Bloo on your phone"
        return tileBox(
            tileColumn(
                tileText("Bloo", 22f, WHITE, bold = true),
                tileText(line, 13f, MUTED),
                width = expand(),
                modifiers = tileModifiers(description = "Bloo. $line."),
            ),
            modifiers = open,
        )
    }

    /**
     * The tile as the face in the app: the charge ring round the edge, the four chosen controls as quarters inside it, the value in a hub
     * where they meet, and the car's name along the bottom. Pressing a quarter runs that control (through the app, as a tap on the face
     * does); pressing the hub opens the app.
     */
    private fun faceLayout(car: VehicleSnapshot, open: ModifiersBuilders.Modifiers, side: Float): LayoutElement {
        // The presets a quarter may start live in prefs the app fills; the tile can run without the app having opened.
        WearPresets.ensureLoaded(applicationContext)
        val slots = WearTileActions.forSlots(car, WearFacePrefs.load(applicationContext))
        // Sized from the display's side: the ring's inset at the edge, the name's band at the bottom, and the quarters square in what is
        // left. The hub sits where the quarters meet, the middle of that square.
        val scale = side / REFERENCE_SIDE
        val inset = RING_INSET * scale
        val nameBand = NAME_BAND * scale
        val gap = TILE_GAP * scale
        val grid = side - 2 * inset - nameBand
        val q = (grid - gap) / 2f
        val gridColumn = tileColumn(
            tileRow(
                quarter(0, slots[0], q, scale), tileSpace(width = gap), quarter(1, slots[1], q, scale),
                width = dp(grid), height = dp(q),
            ),
            tileSpace(height = gap),
            tileRow(
                quarter(2, slots[2], q, scale), tileSpace(width = gap), quarter(3, slots[3], q, scale),
                width = dp(grid), height = dp(q),
            ),
            width = dp(grid),
            height = dp(grid),
        )
        val hubSize = side * HUB_FRACTION
        // The hub's place: down to the middle of the grid, and across the middle of the display.
        val hubPlace = tileColumn(
            tileSpace(height = inset + grid / 2f - hubSize / 2f),
            tileRow(tileSpace(width = side / 2f - hubSize / 2f), hub(car, hubSize), width = expand(), height = wrap()),
            width = expand(),
            height = expand(),
        )
        // The grid, then the car's name in the band under it, on the face's own centre line.
        val face = tileColumn(
            tileSpace(height = inset),
            tileBox(gridColumn, width = expand(), height = dp(grid)),
            tileBox(tileText(car.name, 11f, MUTED, bold = true), width = expand(), height = dp(nameBand)),
            width = expand(),
            height = expand(),
        )
        return tileBox(
            *listOfNotNull(
                if (car.percent != null) edgeRing(car) else null,
                face,
                hubPlace,
            ).toTypedArray(),
            modifiers = open,
        )
    }

    /**
     * One quarter, as a disc quarter: the OUTER corner (on the display's edge) rounded by the tile's whole width, the inner
     * corner (at the hub) a soft curve, the other two nearly square. [index]: 0 top-left, 1 top-right, 2 bottom-left,
     * 3 bottom-right. A control the car cannot do is a quiet, inert quarter.
     */
    private fun quarter(index: Int, action: WearTileAction?, size: Float, scale: Float): LayoutElement {
        // The outer corner is as round as the quarter is wide (a quarter disc); the inner one, at the hub, a soft curve.
        val outer = size
        val inner = 18f * scale
        val flat = 4f * scale
        val corner = when (index) {
            0 -> tileCorners(outer, flat, inner, flat)
            1 -> tileCorners(flat, outer, flat, inner)
            2 -> tileCorners(flat, inner, flat, outer)
            else -> tileCorners(inner, flat, outer, flat)
        }
        val fill = when {
            action == null -> QUIET
            action.active -> ACTIVE
            else -> BUTTON
        }
        if (action == null) return tileBox(width = dp(size), height = dp(size), modifiers = tileModifiers(background = fill, corner = corner, border = RIM))
        // The glyph over its word, as on the face (where the word runs along the outer edge): white on the lit fill.
        val content = buildList {
            if (action.glyph in GLYPHS) {
                add(tileImage(action.glyph, 26f, WHITE))
                add(tileSpace(height = 2f))
            }
            add(tileText(action.label, 11f, WHITE, bold = true))
        }
        return tileBox(
            tileColumn(*content.toTypedArray()),
            width = dp(size),
            height = dp(size),
            modifiers = tileModifiers(
                background = fill, corner = corner, border = RIM,
                clickableId = "act_$index", onClick = tileLaunch(action.key, index), description = action.label,
            ),
        )
    }

    /** The value where the quarters meet. Its own clickable: it sits over the quarters, so a tap on it opens the app. */
    private fun hub(car: VehicleSnapshot, size: Float): LayoutElement {
        val stale = if (car.isStale()) car.updatedLabel() else null
        // An old reading says so (in red, as the face does) instead of passing as current; a car that is charging says how long is left.
        val line = if (stale != null) tileText(stale, 10f, STALE, bold = true)
        else car.chargeLeftLabel()?.let { tileText("$it left", 12f, tint(car), bold = true) }
        return tileBox(
            tileColumn(
                // 24sp fits a percentage; a word ("Unlocked") would be cut off in the hub, so it is smaller.
                tileText(car.bigValue(), if (car.bigValue().length > 4) 16f else 28f, if (car.percent != null) tint(car) else WHITE, bold = true),
                *listOfNotNull(line).toTypedArray(),
            ),
            width = dp(size),
            height = dp(size),
            modifiers = tileModifiers(
                background = BLACK, corner = tileCorner(size / 2),
                clickableId = HUB_ID, onClick = tileLaunch(null),
                description = "Open Bloo. ${car.name}, ${car.bigValue()}" + (car.updatedLabel()?.let { ", updated $it" } ?: ""),
            ),
        )
    }

    private fun tint(car: VehicleSnapshot): Int =
        com.bloo.bluelink.data.BlooColors.levelColor(car.percent, car.charging == true, WHITE)

    /**
     * The car's charge as a ring round the display's edge, split as the phone's charge bar is: the fill (green, blue once it has
     * reached its limit), a track up to the charge limit and a dimmer one past it, with a gap between each -- or one track
     * for a car without a limit. A car with no battery gets no ring, because a ring with nothing to say is a decoration.
     */
    private fun edgeRing(car: VehicleSnapshot): LayoutElement {
        val pct = (car.percent ?: 0).coerceIn(0, 100)
        val limit = car.chargeLimitPct?.takeIf { it in 1..99 }
        val stuck = limit != null && pct >= limit
        val fill = if (stuck) BlooColors.chargeBlue else BlooColors.chargeGreen
        val filledEnd = if (pct > 0) maxOf(360f * pct / 100f, 2 * RING_GAP) else 0f
        val ring = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
        // Each segment runs from one boundary to the next, less the gap; the spacer after it is that gap.
        fun segment(from: Float, to: Float, color: Int) {
            val length = to - from - RING_GAP
            if (length <= 0f) return
            ring.addContent(arcLine(length, color))
            ring.addContent(arcLine(RING_GAP, CLEAR))
        }
        segment(0f, filledEnd, fill)
        if (limit == null || stuck) {
            segment(filledEnd, 360f, if (limit != null) RING_DIM else RING_TRACK)
        } else {
            val limitAt = (360f * limit / 100f).coerceIn(filledEnd, 360f)
            segment(filledEnd, limitAt, RING_TRACK)
            segment(limitAt, 360f, RING_DIM)
        }
        return ring.build()
    }

    private fun arcLine(sweepDegrees: Float, color: Int): LayoutElementBuilders.ArcLine =
        LayoutElementBuilders.ArcLine.Builder()
            .setLength(degrees(sweepDegrees))
            .setThickness(dp(8f))
            .setColor(argb(color))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .build()

    private companion object {
        const val RESOURCES_VERSION = "3"
        val GLYPHS = mapOf(
            "lock" to R.drawable.tile_lock, "unlock" to R.drawable.tile_unlock, "climate" to R.drawable.tile_climate,
            "bolt" to R.drawable.tile_bolt, "flash" to R.drawable.tile_flash, "refresh" to R.drawable.tile_refresh,
            "close" to R.drawable.tile_close, "horn" to R.drawable.tile_horn,
        )
        const val BLACK = 0xFF000000.toInt()
        /** The ring's inset from the display's edge, and the gaps between quarters, matching the face (dp at 227dp). */
        const val RING_INSET = 13f
        /** The gap between two segments of the ring, in degrees (the round caps stand out past it, so it reads as a small space). */
        const val RING_GAP = 7f
        /** A gap in the ring is an arc nothing is drawn in. */
        const val CLEAR = 0x00000000
        const val RING_TRACK = 0x38FFFFFF
        const val RING_DIM = 0x17FFFFFF
        const val TILE_GAP = 6f
        /** The band at the bottom the car's name sits in, in dp at 227dp; the quarters keep clear of it. */
        const val NAME_BAND = 16f
        /** The size the tile was drawn for (dp); every size scales by the display's real one over this. */
        val REFERENCE_SIDE = ReferenceFace.value
        /** The hub's share of the display: larger than the face's, so the value reads at a glance. */
        const val HUB_FRACTION = 0.44f
        const val STALE = 0xFFFF6B6B.toInt()
        const val HUB_ID = "refresh"
        const val QUIET = 0xFF1A1A20.toInt()
        /** The colour of what is on or in motion (see [WearAccent.active]). */
        val ACTIVE: Int get() = WearAccent.active
    }
}
