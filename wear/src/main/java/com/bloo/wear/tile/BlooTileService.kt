package com.bloo.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
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
import com.bloo.wear.TILE_CHIP_ACTIONS
import com.bloo.wear.WearComms
import com.bloo.wear.WearLocalStore
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

        // One pass off the disk: read the snapshot once, apply the optional command's
        // optimistic update (WearComms.send writes back to the store), re-read so the
        // rendered car reflects it, then resolve theme roles for that same car.
        val result = runBlocking {
            val store = SnapshotStore(ctx)
            var data = store.current()
            if (clickId.startsWith(CMD_PREFIX)) {
                val action = clickId.removePrefix(CMD_PREFIX)
                data.selected?.let { sel ->
                    runCatching { WearComms.send(ctx, WearCommand(sel.vin, action)) }
                }
                data = store.current()
            }
            val sel = data.selected
            val roles = resolveRoles(ctx, sel?.vin)
            val actions = runCatching { WearLocalStore(ctx).flow.first().tileActions }
                .getOrElse { listOf("lock", "climate") }
            Triple(sel, roles, actions)
        }
        val snapshot = result.first
        val roles = result.second
        val actions = result.third

        val device = params.deviceConfiguration
        val layout = if (snapshot == null) emptyLayout(ctx, device) else carLayout(ctx, device, snapshot, roles, actions)

        // Refresh faster while charging (percent moves quickly) than when idle.
        val freshness = if (snapshot?.charging == true) FRESHNESS_CHARGING_MS else FRESHNESS_MS

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(freshness)
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
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
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
        actions: List<String>,
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
        // Clarify what the big % means for this car (and double as the brand footer).
        val secondaryLabel = if (snap.isEv) "Battery" else "Fuel"

        val hasPct = snap.percent != null

        // Arc color: neutral track when unknown, semantic for warnings, accent otherwise.
        val arcArgb = when {
            !hasPct  -> CLR_TRACK
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
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, pctText)
                    .setTypography(pctTypo)
                    .setColor(argb(CLR_WHITE))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .addContent(
                Text.Builder(ctx, statusLine)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(statusArgb))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .build()

        // The user's chosen chips (1–2 of lock/climate/charge), split evenly across
        // the inner-circle width. A single chip is allowed to grow wider.
        val chosen = actions.filter { it in TILE_CHIP_ACTIONS }.distinct().take(2)
            .ifEmpty { listOf("lock", "climate") }
        val chipGap = if (isTiny) 2f else 4f
        val innerW  = screenDp * 0.76f - chipGap
        val chipW   = (innerW / chosen.size).coerceIn(52f, if (chosen.size == 1) 150f else 84f)

        val chipRowBuilder = LayoutElementBuilders.Row.Builder().setHeight(DimensionBuilders.wrap())
        chosen.forEachIndexed { i, action ->
            if (i > 0) chipRowBuilder.addContent(spacer(chipGap))
            chipRowBuilder.addContent(
                actionChip(ctx, device, action, snap, roles, activePalette, inactivePalette, chargePalette, chipW),
            )
        }
        val chipRow = chipRowBuilder.build()

        val gap = if (isTiny) 2f else if (isSmall) 3f else 5f

        // The centre (name/%/status) opens the app on tap; the chips own their own taps.
        val centerTappable = LayoutElementBuilders.Box.Builder()
            .addContent(centerCol)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openClickable(ctx))
                    .build()
            )
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(centerTappable)
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
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .setContent(content)
            .setSecondaryLabelTextContent(
                Text.Builder(ctx, secondaryLabel)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(CLR_DIM))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun openChip(ctx: android.content.Context, device: DeviceParameters) =
        CompactChip.Builder(ctx, "Open Bloo", openClickable(ctx), device).build()

    /** A click action that launches the watch app. */
    private fun openClickable(ctx: android.content.Context): Clickable =
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
            .build()

    private fun cmd(action: String): Clickable = Clickable.Builder()
        .setId(CMD_PREFIX + action)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    /** Build one state-reflecting action chip (lock / climate / charge). */
    private fun actionChip(
        ctx: android.content.Context,
        device: DeviceParameters,
        action: String,
        snap: VehicleSnapshot,
        roles: WearColorRoles,
        activePalette: Colors,
        inactivePalette: Colors,
        chargePalette: Colors,
        chipW: Float,
    ): LayoutElementBuilders.LayoutElement {
        val locked = snap.locked == true
        val charging = snap.charging == true
        val climate = snap.climateOn == true
        val tertiaryPalette = Colors(roles.tertiary, roles.onTertiary, roles.surfaceContainer, roles.onSurface)

        val img: String
        val label: String
        val colors: ChipColors
        val act: String
        when (action) {
            "charge" -> {
                img = Img.BOLT
                label = if (charging) "Stop" else "Charge"
                colors = if (charging) ChipColors.primaryChipColors(chargePalette)
                         else ChipColors.secondaryChipColors(inactivePalette)
                act = if (charging) WearAction.CHARGE_OFF else WearAction.CHARGE_ON
            }
            "climate" -> {
                img = Img.CLIMATE
                label = if (climate) "Stop" else "Climate"
                colors = when {
                    charging -> ChipColors.primaryChipColors(chargePalette)
                    climate -> ChipColors.primaryChipColors(tertiaryPalette)
                    else -> ChipColors.secondaryChipColors(inactivePalette)
                }
                act = if (climate) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
            }
            else -> { // lock — unlocked is the highlighted state
                img = if (locked) Img.LOCK else Img.UNLOCK
                label = if (locked) "Unlock" else "Lock"
                colors = if (locked) ChipColors.secondaryChipColors(inactivePalette)
                         else ChipColors.primaryChipColors(activePalette)
                act = if (locked) WearAction.UNLOCK else WearAction.LOCK
            }
        }
        return Chip.Builder(ctx, cmd(act), device)
            .setIconContent(img)
            .setPrimaryLabelContent(label)
            .setChipColors(colors)
            .setWidth(DimensionBuilders.dp(chipW))
            .build()
    }

    private fun spacer(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.dp(dp))
            .setHeight(DimensionBuilders.dp(dp))
            .build()

    private companion object {
        const val RES_VERSION  = "4"
        const val CMD_PREFIX   = "cmd:"
        const val FRESHNESS_MS = 10L * 60L * 1000L
        const val FRESHNESS_CHARGING_MS = 90L * 1000L

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
