package com.bloo.bluelink.wear

import com.bloo.bluelink.wear.TilePalette.WHITE
import com.bloo.bluelink.wear.TilePalette.MUTED
import com.bloo.bluelink.wear.TilePalette.RIM
import com.bloo.bluelink.wear.TilePalette.BUTTON
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
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
import com.bloo.bluelink.data.privatePrefs
import com.bloo.bluelink.data.updatedLabel
import com.bloo.bluelink.ioScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
 *    through the same PIN gate as a tap on the watch face. That is deliberate: there is exactly one
 *    place where a command is authorised, and the tile is not it.
 *  - It says what it means. A sealed watch used to read "Open to set up", which is the wrong sentence
 *    for someone who has already set it up and is merely locked.
 *
 * It also has a content description, so the glance is one spoken sentence instead of four fragments.
 */
class BlooTileService : TileService() {
    private val scope = ioScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        answerTile(scope, "BlooTile", fallback = { tileOf(layoutFor(null)) }) { buildTile(requestParams) }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val (sealed, snapshot) = loadTileSnapshot(this)
                // The hub is the refresh button: a press reloads the tile with this id, and a refresh is asked for as it does.
                // Not more than once in HUB_REFRESH_GAP_MS: the tile keeps reporting the last pressed id on later reloads (its own
                // freshness updates, the redraw a refresh causes), and each would otherwise start another refresh.
                if (!sealed && requestParams.currentState.lastClickableId == HUB_ID && hubRefreshDue()) startHubRefresh()
                // While one runs the tile SAYS so (see [faceLayout]) and is asked for again soon, so the answer lands quickly.
                val refreshing = !sealed && System.currentTimeMillis() < tilePrefs().getLong(REFRESHING_UNTIL, 0L)
                // The display's real size: the quarters are sized from it (not stretched to fill), so they are equal squares.
                val device = requestParams.deviceConfiguration
                val side = minOf(device.screenWidthDp, device.screenHeightDp).toFloat().takeIf { it > 0f } ?: REFERENCE_SIDE
                val car = snapshot?.let { WearTileCar.pick(applicationContext, it.vehicles) ?: it.selected }
                // Whatever goes wrong while drawing, the request is answered: an unset completer leaves the tile
                // spinning for good, so a failure draws the plain "open Bloo" layout instead.
                val layout = runCatching { layoutFor(car, sealed, refreshing, side) }.getOrElse { layoutFor(null, sealed) }
                return tileOf(layout, refreshing)
    }

    private fun tileOf(layout: LayoutElement, refreshing: Boolean = false): TileBuilders.Tile {
        val freshness = if (refreshing) 2_000L else runCatching {
            60_000L * com.bloo.bluelink.data.DeviceStatus.policy(applicationContext).pollIntervalMultiplier
        }.getOrDefault(60_000L)
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Refresh about once a minute while it is on screen.
            .setFreshnessIntervalMillis(freshness)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    private fun tilePrefs() = applicationContext.privatePrefs("bloo_watch_tile")

    /**
     * Starts the refresh the hub asked for, without waiting for it: the tile answers at once showing "Updating..." (the flag
     * below), and when the refresh ends the flag is cleared and the tile is asked for again, so the new numbers replace it.
     * The flag also lapses by itself after [REFRESH_SHOW_MS], so a refresh that never reports cannot leave it stuck.
     */
    private fun startHubRefresh() {
        val app = applicationContext
        tilePrefs().edit().putLong(REFRESHING_UNTIL, System.currentTimeMillis() + REFRESH_SHOW_MS).apply()
        val repo = WearSnapshotRepository(app)
        runCatching { repo.refresh() }
        scope.launch {
            kotlinx.coroutines.withTimeoutOrNull(REFRESH_SHOW_MS) { repo.refreshing.first { !it } }
            tilePrefs().edit().putLong(REFRESHING_UNTIL, 0L).apply()
            runCatching { TileService.getUpdater(app).requestUpdate(BlooTileService::class.java) }
        }
    }

    /** True when the hub's refresh has not run in the last [HUB_REFRESH_GAP_MS] (and records that it is running now). */
    private fun hubRefreshDue(): Boolean {
        val prefs = tilePrefs()
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("hub_refreshed_ms", 0L) < HUB_REFRESH_GAP_MS) return false
        prefs.edit().putLong("hub_refreshed_ms", now).apply()
        return true
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

    private fun layoutFor(car: VehicleSnapshot?, sealed: Boolean = false, refreshing: Boolean = false, side: Float = REFERENCE_SIDE): LayoutElement {
        // The whole tile opens the app. The quarters and the hub are clickables of their own inside it, and the system
        // routes the innermost hit, so a tap on "Charge" charges and a tap anywhere else opens Bloo.
        val open = tileModifiers(clickableId = "open", onClick = tileLaunch(null))
        if (car != null) return faceLayout(car, open, refreshing, side)

        // No car: say why. A sealed watch has been set up and is only waiting for its PIN; "add a car" read as not set up.
        val line = if (sealed) "Enter your PIN in Bloo" else "Add a car in Bloo on your phone"
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
     * The tile as the face in the app: the charge ring round the edge, the four chosen controls as quarters inside it,
     * and the value in a hub where they meet. Pressing a quarter runs that control (through the app, so the PIN gate
     * applies); pressing the hub refreshes. Each quarter is a disc quarter, from per-corner radii.
     */
    private fun faceLayout(car: VehicleSnapshot, open: ModifiersBuilders.Modifiers, refreshing: Boolean, side: Float): LayoutElement {
        // The presets a quarter may start live in prefs the app fills; the tile can run without the app having opened.
        WearPresets.ensureLoaded(applicationContext)
        val slots = WearTileActions.forSlots(car, WearFacePrefs.load(applicationContext))
        // Everything is sized from the display's side, so each quarter is the same square whatever the watch: the grid is the
        // display less the ring's inset, and a quarter is half of it less half a gap.
        val scale = side / REFERENCE_SIDE
        val gap = TILE_GAP * scale
        val grid = side - 2 * RING_INSET * scale
        val q = (grid - gap) / 2f
        val gridColumn = tileColumn(
            tileRow(
                quarter(0, slots[0], refreshing, q, scale), tileSpace(width = gap), quarter(1, slots[1], refreshing, q, scale),
                width = dp(grid), height = dp(q),
            ),
            tileSpace(height = gap),
            tileRow(
                quarter(2, slots[2], refreshing, q, scale), tileSpace(width = gap), quarter(3, slots[3], refreshing, q, scale),
                width = dp(grid), height = dp(q),
            ),
            width = dp(grid),
            height = dp(grid),
        )
        return tileBox(
            *listOfNotNull(
                // While refreshing, the ring is four bright accent arcs -- it reads as "working" at a glance, not as a charge.
                if (refreshing) refreshRing() else if (car.percent != null) edgeRing(car) else null,
                gridColumn,
                hub(car, refreshing, side * HUB_FRACTION),
            ).toTypedArray(),
            modifiers = open,
        )
    }

    /**
     * One quarter, as a disc quarter: the OUTER corner (on the display's edge) rounded by the tile's whole width, the inner
     * corner (at the hub) a soft curve, the other two nearly square. [index]: 0 top-left, 1 top-right, 2 bottom-left,
     * 3 bottom-right. A control the car cannot do is a quiet, inert quarter.
     */
    private fun quarter(index: Int, action: WearTileAction?, refreshing: Boolean, size: Float, scale: Float): LayoutElement {
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
            action == null || refreshing -> QUIET
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

    /** The value where the quarters meet; pressing it refreshes (the tile reloads with this id, and a refresh is asked for). */
    private fun hub(car: VehicleSnapshot, refreshing: Boolean, size: Float): LayoutElement {
        val stale = if (car.isStale()) car.updatedLabel() else null
        if (refreshing) {
            // The press is answered at once: the refresh glyph and "Updating..." in the accent, until the new numbers land.
            return tileBox(
                tileColumn(
                    tileImage("refresh", 22f, ACTIVE),
                    tileSpace(height = 2f),
                    tileText("Updating…", 12f, ACTIVE, bold = true),
                ),
                width = dp(size),
                height = dp(size),
                modifiers = tileModifiers(
                    background = BLACK, corner = tileCorner(size / 2),
                    clickableId = HUB_ID, onClick = ActionBuilders.LoadAction.Builder().build(),
                    description = "Updating ${car.name}",
                ),
            )
        }
        return tileBox(
            tileColumn(
                // 24sp fits a percentage; a word ("Unlocked") would be cut off in the hub, so it is smaller.
                tileText(car.bigValue(), if (car.bigValue().length > 4) 16f else 28f, if (car.percent != null) tint(car) else WHITE, bold = true),
                // An old reading says so (in red, as the face does) instead of passing as current; otherwise the name.
                // A car that is charging says how long is left, in the level's colour, in place of the name.
                if (stale != null) tileText(stale, 10f, STALE, bold = true)
                else car.chargeLeftLabel()?.let { tileText("$it left", 12f, tint(car), bold = true) }
                    ?: tileText(car.name, 13f, WHITE, bold = true),
            ),
            width = dp(size),
            height = dp(size),
            modifiers = tileModifiers(
                background = BLACK, corner = tileCorner(size / 2),
                clickableId = HUB_ID, onClick = ActionBuilders.LoadAction.Builder().build(),
                description = "Refresh. ${car.name}, ${car.bigValue()}" + (car.updatedLabel()?.let { ", updated $it" } ?: ""),
            ),
        )
    }

    /** The ring's colour from the level, the shared [BlooColors.levelColor] rule, as the watch face uses. */
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

    /** The ring while the hub's refresh runs: four accent arcs with gaps. */
    private fun refreshRing(): LayoutElement {
        val ring = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
        repeat(4) {
            ring.addContent(arcLine(90f - RING_GAP, ACTIVE))
            ring.addContent(arcLine(RING_GAP, CLEAR))
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
        const val HUB_REFRESH_GAP_MS = 30_000L
        const val REFRESH_SHOW_MS = 12_000L
        const val REFRESHING_UNTIL = "hub_refreshing_until"
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
