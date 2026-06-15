package com.bloo.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Bloo Wear OS Tile — shows charge level as an edge arc, car name, range, and
 * lock + climate chips. Lock/Unlock and Climate On/Off are executed via the
 * shared [WearCommandRunner] on next tile request (LoadAction).
 */
class BlooTileService : TileService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(Callable { buildTile(requestParams) }, executor)

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun buildTile(params: RequestBuilders.TileRequest): TileBuilders.Tile {
        val ctx = applicationContext
        val clickId = params.currentState.lastClickableId
        if (clickId.startsWith(CMD_PREFIX)) {
            val action = clickId.removePrefix(CMD_PREFIX)
            runBlocking {
                SnapshotStore(ctx).current().selected?.let { sel ->
                    runCatching { WearCommandRunner.execute(ctx, WearCommand(sel.vin, action)) }
                }
            }
        }
        val snapshot = runBlocking { SnapshotStore(ctx).current().selected }
        val device = params.deviceConfiguration
        val layout = if (snapshot == null) emptyLayout(ctx, device) else carLayout(ctx, device, snapshot)
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    private fun emptyLayout(
        ctx: android.content.Context,
        device: DeviceParameters,
    ): LayoutElementBuilders.LayoutElement =
        PrimaryLayout.Builder(device)
            .setContent(
                Text.Builder(ctx, "Open Bloo to get started")
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(argb(CLR_DIM))
                    .setMaxLines(2)
                    .build()
            )
            .setPrimaryChipContent(openChip(ctx, device))
            .build()

    private fun carLayout(
        ctx: android.content.Context,
        device: DeviceParameters,
        snap: VehicleSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        val locked = snap.locked == true
        val charging = snap.charging == true
        val climate = snap.climateOn == true
        val pct = snap.percent ?: 0
        val pctText = "${snap.percent ?: "—"}%"
        val rngText = snap.rangeMi?.let { "$it mi" } ?: ""

        // Charge arc around the edge.
        val arcColor = when {
            charging -> CLR_CHARGE
            pct < 15 -> CLR_ERR
            pct < 30 -> CLR_WARN
            else -> CLR_BLUE
        }
        val arc = CircularProgressIndicator.Builder()
            .setProgress(pct.coerceIn(0, 100) / 100f)
            .setCircularProgressIndicatorColors(
                ProgressIndicatorColors.progressIndicatorColors(argb(arcColor), argb(CLR_TRACK))
            )
            .build()

        // Centre column: name · pct · range · status line.
        val statusLine = when {
            charging -> "Charging" + if (snap.rangeMi != null) " · $rngText" else ""
            climate -> "Climate on"
            else -> if (locked) "Locked" else "Unlocked"
        }

        val centerCol = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(ctx, snap.name)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(CLR_DIM))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, pctText)
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                    .setColor(argb(CLR_WHITE))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, statusLine)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(if (charging) CLR_CHARGE else CLR_DIM))
                    .setMaxLines(1)
                    .build()
            )
            .build()

        // Lock / Unlock chip.
        val lockAction = if (locked) WearAction.UNLOCK else WearAction.LOCK
        val lockLabel = if (locked) "Unlock" else "Lock"
        val lockChip = Chip.Builder(
            ctx,
            Clickable.Builder()
                .setId(CMD_PREFIX + lockAction)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build(),
            device,
        )
            .setPrimaryLabelContent(lockLabel)
            .setChipColors(
                if (locked) {
                    ChipColors.primaryChipColors(
                        androidx.wear.protolayout.material.Colors.DEFAULT
                    )
                } else {
                    ChipColors.secondaryChipColors(
                        androidx.wear.protolayout.material.Colors.DEFAULT
                    )
                }
            )
            .setWidth(DimensionBuilders.expand())
            .build()

        // Climate On / Off chip.
        val climateAction = if (climate) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
        val climateLabel = if (climate) "Stop climate" else "Start climate"
        val climateChip = Chip.Builder(
            ctx,
            Clickable.Builder()
                .setId(CMD_PREFIX + climateAction)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build(),
            device,
        )
            .setPrimaryLabelContent(climateLabel)
            .setChipColors(
                if (climate) {
                    ChipColors.primaryChipColors(
                        androidx.wear.protolayout.material.Colors.DEFAULT
                    )
                } else {
                    ChipColors.secondaryChipColors(
                        androidx.wear.protolayout.material.Colors.DEFAULT
                    )
                }
            )
            .setWidth(DimensionBuilders.expand())
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(centerCol)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(4f))
                    .build()
            )
            .addContent(lockChip)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(4f))
                    .build()
            )
            .addContent(climateChip)
            .build()

        return EdgeContentLayout.Builder(device)
            .setEdgeContent(arc)
            .setPrimaryLabelTextContent(
                Text.Builder(ctx, rngText.ifBlank { snap.name })
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(CLR_DIM))
                    .setMaxLines(1)
                    .build()
            )
            .setContent(content)
            .setSecondaryLabelTextContent(
                Text.Builder(ctx, "Bloo")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(CLR_DIM))
                    .setMaxLines(1)
                    .build()
            )
            .build()
    }

    private fun openChip(
        ctx: android.content.Context,
        device: DeviceParameters,
    ): LayoutElementBuilders.LayoutElement =
        CompactChip.Builder(
            ctx,
            "Open Bloo",
            Clickable.Builder()
                .setId("open")
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(ctx.packageName)
                                .setClassName("com.bloo.wear.MainActivity")
                                .build()
                        )
                        .build()
                )
                .build(),
            device,
        ).build()

    private companion object {
        const val RES_VERSION = "2"
        const val CMD_PREFIX = "cmd:"
        const val FRESHNESS_MS = 10L * 60L * 1000L  // 10 min

        // Tile palette (ARGB).
        const val CLR_WHITE = 0xFFFFFFFF.toInt()
        const val CLR_DIM = 0xFFAAAAAA.toInt()
        const val CLR_BLUE = 0xFFADC6FF.toInt()
        const val CLR_CHARGE = 0xFF2EBD59.toInt()
        const val CLR_WARN = 0xFFF5A623.toInt()
        const val CLR_ERR = 0xFFE5484D.toInt()
        const val CLR_TRACK = 0xFF3C3C3C.toInt()
    }
}
