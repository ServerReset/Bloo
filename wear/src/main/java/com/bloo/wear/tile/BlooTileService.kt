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
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearCommand
import com.bloo.wear.R
import com.bloo.wear.WearComms
import com.bloo.wear.WearSettingsStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Bloo Wear OS Tile — charge arc, car name, big %, status line, lock + climate chips.
 * Uses the same icon drawables as the phone widget. Colors come from the synced
 * WearColorRoles so the tile always matches the phone app theme.
 */
class BlooTileService : TileService() {

    private val executor = Executors.newSingleThreadExecutor()

    /** Resource ID strings referenced in the tile layout. */
    private object Img {
        const val LOCK    = "img_lock"
        const val UNLOCK  = "img_unlock"
        const val CLIMATE = "img_climate"
        const val BOLT    = "img_bolt"
        const val REFRESH = "img_refresh"
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(Callable { buildTile(requestParams) }, executor)

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RES_VERSION)
                .addIdToImageMapping(Img.LOCK,    imgRes(R.drawable.ic_shortcut_lock))
                .addIdToImageMapping(Img.UNLOCK,  imgRes(R.drawable.ic_shortcut_unlock))
                .addIdToImageMapping(Img.CLIMATE, imgRes(R.drawable.ic_shortcut_climate))
                .addIdToImageMapping(Img.BOLT,    imgRes(R.drawable.ic_widget_bolt))
                .addIdToImageMapping(Img.REFRESH, imgRes(R.drawable.ic_widget_refresh))
                .build()
        )

    private fun imgRes(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()

    private fun buildTile(params: RequestBuilders.TileRequest): TileBuilders.Tile {
        val ctx = applicationContext
        val clickId = params.currentState.lastClickableId

        // Handle a chip tap: apply optimistic update via WearComms (which also relays to phone).
        if (clickId.startsWith(CMD_PREFIX)) {
            val action = clickId.removePrefix(CMD_PREFIX)
            runBlocking {
                SnapshotStore(ctx).current().selected?.let { sel ->
                    runCatching { WearComms.send(ctx, WearCommand(sel.vin, action)) }
                }
            }
        }

        val snapshot = runBlocking { SnapshotStore(ctx).current().selected }
        val roles    = runBlocking { resolveRoles(ctx, snapshot?.vin) }
        val device   = params.deviceConfiguration
        val layout   = if (snapshot == null) emptyLayout(ctx, device) else carLayout(ctx, device, snapshot, roles)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    /** Resolve the full color roles from the phone-synced settings. Falls back to dark defaults. */
    private suspend fun resolveRoles(ctx: android.content.Context, vin: String?): WearColorRoles =
        runCatching {
            val payload = WearSettingsStore(ctx).flow.first()
            vin?.let { payload?.carColors?.get(it) }
                ?: payload?.colors
                ?: DEFAULT_ROLES
        }.getOrElse { DEFAULT_ROLES }

    // ── Empty / not-configured layout ────────────────────────────────────────

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

    // ── Main car layout ───────────────────────────────────────────────────────

    private fun carLayout(
        ctx: android.content.Context,
        device: DeviceParameters,
        snap: VehicleSnapshot,
        roles: WearColorRoles,
    ): LayoutElementBuilders.LayoutElement {
        val screenDp = device.screenWidthDp
        val isSmall  = screenDp < 193
        val isTiny   = screenDp < 182

        val locked   = snap.locked   == true
        val charging = snap.charging == true
        val climate  = snap.climateOn == true
        val pct      = snap.percent ?: 0
        val pctText  = "${snap.percent ?: "—"}%"
        val rngText  = snap.rangeMi?.let { "$it mi" } ?: ""

        // Arc color: semantic for warnings, accent otherwise.
        val arcArgb = when {
            charging -> CLR_CHARGE
            pct < 15 -> roles.error
            pct < 30 -> CLR_WARN
            else     -> roles.primary
        }

        // Active chip: primary fill. Inactive: surfaceContainerHigh with dimmed text.
        val activePalette   = Colors(roles.primary, roles.onPrimary, roles.surfaceContainer, roles.onSurface)
        val inactivePalette = Colors(roles.surfaceContainerHigh, roles.onSurfaceVariant, roles.surfaceContainer, roles.onSurface)
        val chargePalette   = Colors(CLR_CHARGE, CLR_WHITE, roles.surfaceContainer, roles.onSurface)

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
            locked   -> "Locked"
            else     -> "Unlocked"
        }
        val statusArgb = when {
            charging -> CLR_CHARGE
            climate  -> roles.tertiary
            else     -> CLR_DIM
        }

        // Percentage typography: shrink for tiny watches.
        val pctTypo = if (isTiny) Typography.TYPOGRAPHY_DISPLAY2 else Typography.TYPOGRAPHY_DISPLAY1

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
                    .setTypography(pctTypo)
                    .setColor(argb(CLR_WHITE))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, statusLine)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(statusArgb))
                    .setMaxLines(1)
                    .build()
            )
            .build()

        // Lock chip: accent when locked (tap to unlock), inactive surface when unlocked (tap to lock).
        val lockImg    = if (locked) Img.LOCK else Img.UNLOCK
        val lockLabel  = if (locked) "Unlock" else "Lock"
        val lockColors = if (locked) ChipColors.primaryChipColors(activePalette)
                         else        ChipColors.secondaryChipColors(inactivePalette)
        val lockAction = if (locked) WearAction.UNLOCK else WearAction.LOCK

        // Climate chip: active when climate is on (charge palette when charging, tertiary for climate).
        val climateImg    = Img.CLIMATE
        val climateLabel  = if (climate) "Stop" else "Climate"
        val climateColors = when {
            charging -> ChipColors.primaryChipColors(chargePalette)
            climate  -> ChipColors.primaryChipColors(Colors(roles.tertiary, roles.onTertiary, roles.surfaceContainer, roles.onSurface))
            else     -> ChipColors.secondaryChipColors(inactivePalette)
        }
        val climateAction = if (climate) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON

        // Chip width: fill available inner-circle width split evenly. Minimum 52dp.
        val chipGap = if (isTiny) 2f else 4f
        val innerW  = screenDp * 0.76f - chipGap
        val chipW   = (innerW / 2).coerceIn(52f, 84f)

        val lockChip = Chip.Builder(ctx, cmd(lockAction), device)
            .setIconContent(lockImg)
            .setPrimaryLabelContent(lockLabel)
            .setChipColors(lockColors)
            .setWidth(DimensionBuilders.dp(chipW))
            .build()

        val climateChip = Chip.Builder(ctx, cmd(climateAction), device)
            .setIconContent(climateImg)
            .setPrimaryLabelContent(climateLabel)
            .setChipColors(climateColors)
            .setWidth(DimensionBuilders.dp(chipW))
            .build()

        val chipRow = LayoutElementBuilders.Row.Builder()
            .addContent(lockChip)
            .addContent(spacer(chipGap))
            .addContent(climateChip)
            .setHeight(DimensionBuilders.wrap())
            .build()

        val gap = if (isTiny) 2f else if (isSmall) 3f else 5f

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(centerCol)
            .addContent(spacer(gap))
            .addContent(chipRow)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            .setWidth(DimensionBuilders.dp(dp))
            .setHeight(DimensionBuilders.dp(dp))
            .build()

    private companion object {
        const val RES_VERSION  = "4"
        const val CMD_PREFIX   = "cmd:"
        const val FRESHNESS_MS = 10L * 60L * 1000L

        const val CLR_WHITE = 0xFFFFFFFF.toInt()
        const val CLR_DIM   = 0xFFAAAAAA.toInt()
        const val CLR_CHARGE = 0xFF2EBD59.toInt()
        const val CLR_WARN   = 0xFFF5A623.toInt()
        const val CLR_TRACK  = 0xFF3C3C3C.toInt()

        /** Fallback roles when no phone sync has occurred yet. */
        val DEFAULT_ROLES = WearColorRoles(
            primary               = 0xFF7B83EB.toInt(),
            onPrimary             = 0xFF000000.toInt(),
            primaryContainer      = 0xFF3A3F7A.toInt(),
            onPrimaryContainer    = 0xFFFFFFFF.toInt(),
            secondary             = 0xFF9B83EB.toInt(),
            onSecondary           = 0xFF000000.toInt(),
            secondaryContainer    = 0xFF4A3F7A.toInt(),
            onSecondaryContainer  = 0xFFFFFFFF.toInt(),
            tertiary              = 0xFF4CD9E0.toInt(),
            onTertiary            = 0xFF003B3E.toInt(),
            tertiaryContainer     = 0xFF004F53.toInt(),
            onTertiaryContainer   = 0xFF6FF6FF.toInt(),
            background            = 0xFF111318.toInt(),
            onBackground          = 0xFFE2E2E9.toInt(),
            onSurface             = 0xFFE2E2E9.toInt(),
            onSurfaceVariant      = 0xFFAAAAAA.toInt(),
            surfaceContainerLow   = 0xFF1A1B20.toInt(),
            surfaceContainer      = 0xFF1F2026.toInt(),
            surfaceContainerHigh  = 0xFF262730.toInt(),
            outline               = 0xFF8E8E9A.toInt(),
            outlineVariant        = 0xFF44474F.toInt(),
            error                 = 0xFFE5484D.toInt(),
            onError               = 0xFF690005.toInt(),
            errorContainer        = 0xFF93000A.toInt(),
            onErrorContainer      = 0xFFFFDAD6.toInt(),
        )
    }
}
