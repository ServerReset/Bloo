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
import androidx.wear.protolayout.material.Colors
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
import com.bloo.wear.WearSettingsStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Bloo Wear OS Tile — shows charge level as an edge arc, car name, range, and
 * lock + climate chips. Colors come from the app's active palette so the tile
 * always matches the phone app theme.
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
        val accentArgb = runBlocking { resolveAccent(ctx, snapshot?.vin) }
        val device = params.deviceConfiguration
        val layout = if (snapshot == null) emptyLayout(ctx, device) else carLayout(ctx, device, snapshot, accentArgb)
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    /** Read the app's active palette accent as an ARGB int. Falls back to brand indigo. */
    private suspend fun resolveAccent(ctx: android.content.Context, vin: String?): Int =
        runCatching {
            val payload = WearSettingsStore(ctx).flow.first()
            vin?.let { payload?.carColors?.get(it)?.primary }
                ?: payload?.colors?.primary
                ?: CLR_ACCENT_DEFAULT
        }.getOrElse { CLR_ACCENT_DEFAULT }

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
        accentArgb: Int,
    ): LayoutElementBuilders.LayoutElement {
        val screenDp = device.screenWidthDp
        // Tight spacing on small watches so chips don't overflow the edge layout's
        // inner circle. screenWidthDp == screenHeightDp for round watches.
        val isSmall = screenDp < 193
        val isTiny  = screenDp < 182

        val locked   = snap.locked   == true
        val charging = snap.charging == true
        val climate  = snap.climateOn == true
        val pct      = snap.percent ?: 0
        val pctText  = "${snap.percent ?: "—"}%"
        val rngText  = snap.rangeMi?.let { "$it mi" } ?: ""

        // Arc: accent for normal charge; semantic red/amber for warnings.
        val arcArgb = when {
            charging -> CLR_CHARGE
            pct < 15 -> CLR_ERR
            pct < 30 -> CLR_WARN
            else     -> accentArgb
        }

        // Chip palette keyed off the app's accent. Active state = accent fill,
        // inactive state = dark surface so the difference is immediately clear.
        val palette = Colors(accentArgb, CLR_WHITE, CLR_SURFACE, CLR_DIM)

        val arc = CircularProgressIndicator.Builder()
            .setProgress(pct.coerceIn(0, 100) / 100f)
            .setCircularProgressIndicatorColors(
                ProgressIndicatorColors.progressIndicatorColors(
                    Colors(arcArgb, CLR_WHITE, CLR_TRACK, CLR_WHITE)
                )
            )
            .build()

        val statusLine = when {
            charging -> "Charging" + if (rngText.isNotEmpty()) " · $rngText" else ""
            climate  -> "Climate on"
            else     -> if (locked) "Locked" else "Unlocked"
        }
        val statusColor = if (charging) CLR_CHARGE else CLR_DIM

        // Shrink the big % number on tiny watches so the column still fits.
        val pctTypography = if (isTiny) Typography.TYPOGRAPHY_DISPLAY2 else Typography.TYPOGRAPHY_DISPLAY1

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
                    .setTypography(pctTypography)
                    .setColor(argb(CLR_WHITE))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, statusLine)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(statusColor))
                    .setMaxLines(1)
                    .build()
            )
            .build()

        // Lock: accent chip = active (locked → "Unlock"), secondary = idle (unlocked → "Lock").
        val lockAction = if (locked) WearAction.UNLOCK else WearAction.LOCK
        val lockLabel  = if (locked) "Unlock" else "Lock"
        val lockChip = Chip.Builder(ctx, cmd(lockAction), device)
            .setPrimaryLabelContent(lockLabel)
            .setChipColors(
                if (locked) ChipColors.primaryChipColors(palette)
                else        ChipColors.secondaryChipColors(palette)
            )
            .setWidth(DimensionBuilders.expand())
            .build()

        // Climate: accent chip = active (running → "Stop"), secondary = idle (off → "Climate").
        // Keep labels ≤ 10 chars so they never truncate on any watch size.
        val climateAction = if (climate) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
        val climateLabel  = if (climate) "Stop" else "Climate"
        val climateChip = Chip.Builder(ctx, cmd(climateAction), device)
            .setPrimaryLabelContent(climateLabel)
            .setChipColors(
                if (climate) ChipColors.primaryChipColors(palette)
                else         ChipColors.secondaryChipColors(palette)
            )
            .setWidth(DimensionBuilders.expand())
            .build()

        // Tighten the gap between chips on smaller screens so the column fits
        // inside the EdgeContentLayout's inner circle without overflow.
        val gap = if (isTiny) 1f else if (isSmall) 2f else 4f

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(centerCol)
            .addContent(spacer(gap))
            .addContent(lockChip)
            .addContent(spacer(gap))
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

    private fun openChip(ctx: android.content.Context, device: DeviceParameters) =
        CompactChip.Builder(
            ctx, "Open Bloo",
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

    private fun cmd(action: String): Clickable = Clickable.Builder()
        .setId(CMD_PREFIX + action)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private fun spacer(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(dp))
            .build()

    private companion object {
        const val RES_VERSION    = "3"
        const val CMD_PREFIX     = "cmd:"
        const val FRESHNESS_MS   = 10L * 60L * 1000L

        const val CLR_WHITE          = 0xFFFFFFFF.toInt()
        const val CLR_DIM            = 0xFFAAAAAA.toInt()
        const val CLR_CHARGE         = 0xFF2EBD59.toInt()
        const val CLR_WARN           = 0xFFF5A623.toInt()
        const val CLR_ERR            = 0xFFE5484D.toInt()
        const val CLR_TRACK          = 0xFF3C3C3C.toInt()
        const val CLR_SURFACE        = 0xFF1A1B20.toInt()
        const val CLR_ACCENT_DEFAULT = 0xFF7B83EB.toInt()  // Bloo brand indigo fallback
    }
}
