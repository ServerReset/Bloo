package com.bloo.bluelink.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ioScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * The watch's Tile: one car at a glance in the carousel -- name, charge, lock state -- and a tap
 * anywhere opens the app for the quick actions.
 */
class BlooTileService : TileService() {
    private val scope = ioScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                val snapshot = runCatching { SnapshotStore(applicationContext).current() }.getOrNull()
                val car = snapshot?.vehicles?.let { all -> all.firstOrNull { it.vin == snapshot.selectedVin } ?: all.firstOrNull() }
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    // Refresh about once a minute while it is on screen.
                    .setFreshnessIntervalMillis(60_000)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layoutFor(car)))
                    .build()
                completer.set(tile)
            }
            "BlooTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "BlooTileResources"
        }

    private fun layoutFor(car: VehicleSnapshot?): LayoutElementBuilders.LayoutElement {
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearMainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
            LayoutElementBuilders.Text.Builder()
                .setText(value)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(size))
                        .setColor(argb(color))
                        .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }
                        .build(),
                )
                .setMaxLines(1)
                .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (car == null) {
            column.addContent(text("Bloo", 22f, WHITE, bold = true))
            column.addContent(text("Open to set up", 14f, MUTED))
        } else {
            column.addContent(text(car.name, 14f, MUTED))
            column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            column.addContent(text(car.percent?.let { "$it%" } ?: car.locked?.let { if (it) "Locked" else "Unlocked" } ?: "—", 40f, if (car.charging == true) GREEN else WHITE, bold = true))
            val line = listOfNotNull(
                car.locked?.let { if (it) "Locked" else "Unlocked" }.takeIf { car.percent != null },
                if (car.charging == true) "Charging" else null,
                car.rangeMi?.let { "$it mi" },
            ).joinToString(" · ")
            if (line.isNotBlank()) column.addContent(text(line, 13f, MUTED))
        }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(column.build())
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB8B8C4.toInt()
        val GREEN = BlooColors.chargeGreen
    }
}
