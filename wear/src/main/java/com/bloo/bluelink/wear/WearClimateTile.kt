package com.bloo.bluelink.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.ioScope
import com.google.common.util.concurrent.ListenableFuture

/** One row on the climate tile: what it says, the control key the app runs when it is pressed, and whether it is lit. */
internal data class ClimateTileRow(val key: String, val title: String, val subtitle: String?, val active: Boolean)

/**
 * Which rows the climate tile shows, and the only decision on it. While the climate runs, a single "Stop climate" row (the
 * one thing worth doing). Otherwise smart climate, then the phone's presets for this car in the order the phone keeps them,
 * as many as fit on a round face. The keys are the face's own control keys, so a press runs exactly what a press on the face
 * would, through the same PIN gate.
 */
internal object WearClimateTileRows {
    const val MAX_ROWS = 3

    fun rows(car: VehicleSnapshot, presets: List<ClimatePreset>, detail: (ClimateRequest) -> String): List<ClimateTileRow> {
        // An old reading cannot say the climate still runs (a timed session ends by itself), so it offers the starts.
        if (car.climateOn == true && !car.isStale()) return listOf(ClimateTileRow("climate_off", CarLabels.STOP_CLIMATE, "Running", active = true))
        val rows = mutableListOf(
            ClimateTileRow(WearFaceControls.CLIMATE_SMART_KEY, CarLabels.SMART_CLIMATE, "Matches the weather", active = false),
        )
        presets.take(MAX_ROWS - 1).forEach {
            rows += ClimateTileRow(WearFaceControls.PRESET_PREFIX + it.id, it.name, detail(it.request).ifBlank { null }, active = false)
        }
        return rows
    }
}

/**
 * A second tile in the watch's carousel, for the one thing worth a tile of its own: starting the car's climate. Smart
 * climate and the phone's first presets are rows you press; while it runs, one row stops it. A tile's press can only launch
 * the app, so each row launches it with its control key, and the app runs it through the same PIN gate as the face.
 */
class BlooClimateTileService : TileService() {
    private val scope = ioScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        answerTile(scope, "BlooClimateTile", fallback = { tileOf(messageLayout("Open Bloo")) }) { buildTile(requestParams) }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        WearAccent.load(this)
        val sealed = runCatching { WearBootGate.isLocked(applicationContext) }.getOrDefault(true)
        val snapshot = if (sealed) null else runCatching { SnapshotStore(applicationContext).current() }.getOrNull()
        val car = snapshot?.let { WearTileCar.pick(applicationContext, it.vehicles) ?: it.selected }
            ?: return tileOf(messageLayout(if (sealed) "Enter your PIN in Bloo" else "Add a car in Bloo on your phone"))
        WearPresets.ensureLoaded(applicationContext)
        val rows = WearClimateTileRows.rows(car, WearPresets.presets.value[car.vin].orEmpty(), WearPresets::detail)
        val device = requestParams.deviceConfiguration
        val side = minOf(device.screenWidthDp, device.screenHeightDp).toFloat().takeIf { it > 0f } ?: ReferenceFace.value
        return tileOf(listLayout(car, rows, side))
    }

    private fun tileOf(layout: LayoutElement): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()

    // The rows are text on pills; the tile uses no images, so its resources are empty.
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "BlooClimateTileResources"
        }

    private fun messageLayout(line: String): LayoutElement =
        tileBox(
            tileColumn(
                tileText("Bloo", 22f, WHITE, bold = true),
                tileText(line, 13f, MUTED),
                modifiers = tileModifiers(description = "Bloo. $line."),
            ),
            modifiers = tileModifiers(clickableId = "open", onClick = tileLaunch(null)),
        )

    private fun listLayout(car: VehicleSnapshot, rows: List<ClimateTileRow>, side: Float): LayoutElement {
        val scale = side / ReferenceFace.value
        // Narrower than the display: on a round face the lower rows' corners would otherwise be cut by the bezel.
        val rowWidth = side * ROW_WIDTH_FRACTION
        val rowHeight = ROW_HEIGHT * scale
        val children = buildList {
            add(tileText("Climate", 12f, MUTED, bold = true))
            add(tileSpace(height = 6f * scale))
            rows.forEachIndexed { index, row ->
                if (index > 0) add(tileSpace(height = ROW_GAP * scale))
                add(rowElement(index, row, rowWidth, rowHeight, car))
            }
        }
        return tileBox(
            tileColumn(*children.toTypedArray()),
            modifiers = tileModifiers(clickableId = "open", onClick = tileLaunch(null)),
        )
    }

    private fun rowElement(index: Int, row: ClimateTileRow, width: Float, height: Float, car: VehicleSnapshot): LayoutElement {
        val text = buildList {
            add(tileText(row.title, 13f, WHITE, bold = true))
            row.subtitle?.let { add(tileText(it, 10f, if (row.active) WHITE else MUTED)) }
        }
        return tileBox(
            tileColumn(*text.toTypedArray()),
            width = androidx.wear.protolayout.DimensionBuilders.dp(width),
            height = androidx.wear.protolayout.DimensionBuilders.dp(height),
            modifiers = tileModifiers(
                background = if (row.active) ACTIVE else BUTTON,
                corner = tileCorner(height / 2f),
                border = RIM,
                clickableId = "climate_$index",
                onClick = tileLaunch(row.key),
                description = listOfNotNull(row.title, row.subtitle, car.name).joinToString(". "),
            ),
        )
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_MS = 120_000L
        const val ROW_HEIGHT = 46f
        const val ROW_GAP = 6f
        const val ROW_WIDTH_FRACTION = 0.64f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB8B8C4.toInt()
        const val RIM = 0x33FFFFFF
        const val BUTTON = 0xFF2A2A33.toInt()
        /** The colour of what is on: the phone's accent once it has sent one, else the brand's (as the main tile). */
        val ACTIVE: Int get() = WearAccent.accent.value?.primary ?: BlooColors.brandAccent
    }
}
