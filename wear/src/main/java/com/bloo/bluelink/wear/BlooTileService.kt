package com.bloo.bluelink.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
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
        // The phone's accent, as the face has it (the tile runs without the face having opened).
        WearAccent.load(applicationContext)
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
                tileText("Bloo", 22f, INK, bold = true),
                tileText(line, 13f, NAME),
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
        // The hub is the face's own: the same radius the quarters meet at.
        val hubSize = side * (2f * HubRadius / REFERENCE_SIDE)
        // The hub's place: down to the middle of the grid, and across the middle of the display.
        val hubPlace = tileColumn(
            tileSpace(height = inset + nameBand + grid / 2f - hubSize / 2f),
            tileRow(tileSpace(width = side / 2f - hubSize / 2f), hub(car, hubSize), width = expand(), height = wrap()),
            width = expand(),
            height = expand(),
        )
        // The name header, then the grid of quarters under it, and the hub where they meet.
        // The name is a header across the top, inside the ring, centred like the rest of the tile's text.
        val face = tileColumn(
            tileSpace(height = inset),
            tileBox(tileText(car.name, 12f, NAME, bold = true), width = expand(), height = dp(nameBand)),
            tileBox(gridColumn, width = expand(), height = dp(grid)),
            width = expand(),
            height = expand(),
        )
        return tileBox(
            *listOfNotNull(
                if (car.percent != null) edgeRing(car, side, scale) else null,
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
        // The face's fills: the quiet surface for an empty quarter, the accent for a lit one, the raised surface for the rest.
        val fill = when {
            action == null -> SURFACE
            action.active -> ACTIVE
            else -> SURFACE_HIGH
        }
        val ink = if (action?.active == true) ACTIVE_INK else INK
        if (action == null) return tileBox(width = dp(size), height = dp(size), modifiers = tileModifiers(background = fill, corner = corner))
        // The glyph over its word, as on the face (where the word runs along the outer edge): white on the lit fill.
        val content = buildList {
            if (action.glyph in GLYPHS) {
                add(tileImage(action.glyph, 26f, ink))
                add(tileSpace(height = 2f))
            }
            add(tileText(action.label, 11f, ink, bold = true))
        }
        return tileBox(
            tileColumn(*content.toTypedArray()),
            width = dp(size),
            height = dp(size),
            modifiers = tileModifiers(
                background = fill, corner = corner,
                clickableId = "act_$index", onClick = tileLaunch(action.key, index), description = action.label,
            ),
        )
    }

    /** The value where the quarters meet. Its own clickable: it sits over the quarters, so a tap on it opens the app. */
    private fun hub(car: VehicleSnapshot, size: Float): LayoutElement {
        val stale = if (car.isStale()) car.updatedLabel() else null
        // An old reading says so (in red, as the face does) instead of passing as current; a car that is charging says how long is left.
        val state = carStateLine(car)
        val line = if (stale != null) tileText(stale, 10f, STALE, bold = true)
        else state.takeIf { it.isNotBlank() }?.let { tileText(it, 10f, INK) }
        return tileBox(
            tileColumn(
                // 24sp fits a percentage; a word ("Unlocked") would be cut off in the hub, so it is smaller.
                tileText(car.bigValue(), if (car.bigValue().length > 4) 16f else 28f, if (car.percent != null) tint(car) else INK, bold = true),
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
        com.bloo.bluelink.data.BlooColors.levelColor(car.percent, car.charging == true, INK)

    /**
     * The car's charge as the face draws it: a thin track round the display's edge, the charge in the car's colour from the top, and a short
     * mark where the car's charge limit sits. A car with no battery gets no ring.
     */
    private fun edgeRing(car: VehicleSnapshot, side: Float, scale: Float): LayoutElement {
        val pct = (car.percent ?: 0).coerceIn(0, 100)
        val stroke = RingStroke.value * scale
        val limit = car.chargeLimitPct?.takeIf { it in 1..99 }
        val arcs = buildList {
            add(ringArc(0f, 360f, RING_TRACK, stroke, round = false))
            if (pct > 0) add(ringArc(0f, 360f * pct / 100f, tint(car), stroke, round = true))
            limit?.let { add(ringArc(360f * it / 100f - LIMIT_MARK_DEG / 2f, LIMIT_MARK_DEG, RING_MARK, stroke, round = false)) }
        }
        // Inset by half the stroke, so the whole ring sits inside the display's edge, as on the face.
        return tileBox(
            *arcs.toTypedArray(),
            width = dp(side - stroke),
            height = dp(side - stroke),
        )
    }

    /** One arc of the ring, from [startDeg] (0 at the top, clockwise) over [sweepDeg]. */
    private fun ringArc(startDeg: Float, sweepDeg: Float, color: Int, thickness: Float, round: Boolean): LayoutElementBuilders.Arc =
        LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(startDeg))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(degrees(sweepDeg))
                    .setThickness(dp(thickness))
                    .setColor(argb(color))
                    .setStrokeCap(if (round) LayoutElementBuilders.STROKE_CAP_ROUND else LayoutElementBuilders.STROKE_CAP_BUTT)
                    .build(),
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "3"
        /** The face's own colours: its surfaces (the watch's surface container and the raised one above it), its ink and its name's tone. */
        const val SURFACE = 0xFF1C1C22.toInt()
        const val SURFACE_HIGH = 0xFF2B2930.toInt()
        const val INK = 0xFFE6E1E5.toInt()
        const val NAME = 0xFFCAC4D0.toInt()
        const val RING_MARK = 0xFFE6E1E5.toInt()
        /** The limit mark's width, in degrees of the ring. */
        const val LIMIT_MARK_DEG = 2.5f
        /** The face's lit ink: the accent's own on-colour, once the phone has sent it. */
        val ACTIVE_INK: Int get() = WearAccent.accent.value?.onPrimary ?: 0xFFFFFFFF.toInt()
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
        /** The face's track: its ink at 16%. */
        const val RING_TRACK = 0x29E6E1E5
        const val TILE_GAP = 6f
        /** The band at the bottom the car's name sits in, in dp at 227dp; the quarters keep clear of it. */
        const val NAME_BAND = 16f
        /** The size the tile was drawn for (dp); every size scales by the display's real one over this. */
        val REFERENCE_SIDE = ReferenceFace.value
        /** The hub's share of the display: larger than the face's, so the value reads at a glance. */
        const val STALE = 0xFFFF6B6B.toInt()
        const val HUB_ID = "refresh"
        /** The colour of what is on or in motion (see [WearAccent.active]). */
        val ACTIVE: Int get() = WearAccent.active
    }
}
