package com.bloo.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
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
 * The Bloo Wear OS Tile — a glanceable card outside the app showing the selected
 * car's charge and lock state, with a Lock/Unlock action (run via the shared
 * command runner) and a chip to open the app. Tapping Lock fires a LoadAction;
 * the next [onTileRequest] reads the clicked id, runs the command, and re-renders.
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
        // A tapped Lock/Unlock chip arrives here as the last clickable id.
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
                Text.Builder(ctx, "Open Bloo on your phone")
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(argb(0xFFE2E2E9.toInt()))
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
        val pct = snap.percent?.let { "$it%" } ?: "—"
        val rng = snap.rangeMi?.let { " · $it mi" } ?: ""
        val lockLabel = if (snap.locked == true) "Lock: Locked" else "Tap to lock"

        val lockChip = Chip.Builder(
            ctx,
            Clickable.Builder()
                .setId(CMD_PREFIX + WearAction.TOGGLE_LOCK)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build(),
            device,
        ).setPrimaryLabelContent(lockLabel).build()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(ctx, snap.name)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, pct + rng)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(argb(0xFFADC6FF.toInt()))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(lockChip)
            .build()

        return PrimaryLayout.Builder(device)
            .setContent(content)
            .setPrimaryChipContent(openChip(ctx, device))
            .build()
    }

    private fun openChip(
        ctx: android.content.Context,
        device: DeviceParameters,
    ): LayoutElementBuilders.LayoutElement =
        CompactChip.Builder(
            ctx,
            "Open",
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
        const val RES_VERSION = "1"
        const val CMD_PREFIX = "cmd:"
        const val FRESHNESS_MS = 15L * 60L * 1000L
    }
}
