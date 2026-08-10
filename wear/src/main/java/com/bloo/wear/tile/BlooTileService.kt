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
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ButtonDefaults
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
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.vehicleStateLabel
import com.bloo.wear.R
import com.bloo.wear.TILE_CHIP_ACTIONS
import com.bloo.wear.WearComms
import com.bloo.wear.WearLocalStore
import com.bloo.wear.WearSettingsStore
import com.bloo.wear.complication.ComplicationLink
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Glanceable Bloo Wear Tile — a charge/fuel arc, the car name + big percentage,
 * a live status line, and one-or-two icon action buttons (lock / climate /
 * charge). Colours come from the phone-synced [WearColorRoles] so the tile
 * always matches the app theme; icons reuse the phone widget's own drawables.
 *
 * [BlooTile1]..[BlooTile4] form a fixed pool of concrete providers — one per
 * watch-face slot, each independently pinnable to a car in Settings (persisted
 * as `tile_car_vin_$poolIndex`). This mirrors the phone's BlooTile1..12 Quick
 * Settings pool: a multi-car user drops one Tile per car onto their face.
 */
abstract class BlooTileService : TileService() {

    /** Which pool slot (0-based) this concrete Tile owns; selects the pinned VIN
     *  and its own dedupe key. */
    protected abstract val poolIndex: Int

    // ProtoLayout Tiles are NOT Compose. The Tiles system (rendered out of this
    // process) calls onTileRequest on its own binder thread and expects a
    // ListenableFuture back, not a suspend function. `executor` is a single
    // dedicated thread that runs the (blocking, via runBlocking) buildTile()
    // work off the caller's thread. The fire-and-forget command relay is
    // deliberately NOT launched on any service-scoped coroutine — see
    // [TileCommandRelay].
    private val executor = Executors.newSingleThreadExecutor()

    // The system can destroy/recreate this Service between requests (it is not
    // kept warm), so the executor is torn down here to avoid leaking a thread
    // each time. The command relay intentionally outlives the Service (it runs
    // on [TileCommandRelay]) and is therefore NOT cancelled here — cancelling it
    // in onDestroy is exactly the dropped-command bug the scope split avoids.
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    /** String ids the layout uses to reference image resources (mapped in
     *  [onTileResourcesRequest]). */
    private object Img {
        const val LOCK = "img_lock"
        const val UNLOCK = "img_unlock"
        const val CLIMATE = "img_climate"
        const val BOLT = "img_bolt"
    }

    // Called by the Tiles system whenever the tile needs to (re-)render: first
    // pin, freshness-interval timeout, a tap (the clicked id arrives via
    // currentState.lastClickableId), or a push from refreshWearGlanceables().
    // Layout building runs synchronously on `executor` so this returns
    // immediately with a Future the system awaits.
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(Callable { buildTile(requestParams) }, executor)

    // Called once (system-cached) to map the Img.* string ids to real drawables.
    // ProtoLayout serializes the layout tree and renders it in a separate
    // surface with no access to this app's resource table, so images are always
    // referenced by string id, never resource id.
    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RES_VERSION)
                .addIdToImageMapping(Img.LOCK, imgRes(R.drawable.ic_shortcut_lock))
                .addIdToImageMapping(Img.UNLOCK, imgRes(R.drawable.ic_shortcut_unlock))
                .addIdToImageMapping(Img.CLIMATE, imgRes(R.drawable.ic_shortcut_climate))
                .addIdToImageMapping(Img.BOLT, imgRes(R.drawable.ic_widget_bolt))
                .build()
        )

    /** Wrap a drawable resource id into the ProtoLayout ImageResource type the
     *  id→resource map requires. */
    private fun imgRes(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()

    /** Everything [buildTile] resolves off disk in a single pass, so the layout
     *  code never re-reads or threads separate values through. */
    private data class TileData(
        val car: VehicleSnapshot?,
        val roles: WearColorRoles,
        val actions: List<String>,
        val metric: Boolean,
    )

    /**
     * Synchronously build the [TileBuilders.Tile] returned for this request.
     * Runs on `executor` and uses [runBlocking] to await the DataStore
     * reads/writes, since the TileService callback contract is a Future (not a
     * suspend fun) even though the backing stores are suspend-based.
     */
    private fun buildTile(params: RequestBuilders.TileRequest): TileBuilders.Tile {
        val ctx = applicationContext
        val device = params.deviceConfiguration
        // Non-null only when the previously-rendered tile had a clickable tapped
        // (an id built by cmd()). On a freshness/push refresh this is whatever id
        // the SYSTEM last recorded — not necessarily a fresh tap — hence the
        // dedupe below before treating it as one.
        val clickId = params.currentState.lastClickableId
        try {
            // One disk pass: read the snapshot, apply the tapped command's
            // optimistic flip (which writes back to the store), re-read so the
            // rendered car reflects it, then resolve theme roles for that car.
            val result = runBlocking {
                val store = SnapshotStore(ctx)
                val localStore = WearLocalStore(ctx)
                val local = runCatching { localStore.flow.first() }.getOrNull()
                val actions = local?.tileActions ?: listOf("lock", "climate")
                val tileVin = local?.tileCarVins?.getOrNull(poolIndex)

                // Show the car pinned to this slot if it still exists, else the
                // app/widget's selected car — so an unconfigured or stale slot is
                // never blank.
                fun pick(d: SnapshotStore.SnapshotData): VehicleSnapshot? =
                    tileVin?.let { v -> d.vehicles.firstOrNull { it.vin == v } } ?: d.selected

                var data = store.current()
                var car = pick(data)

                // The system persists Tile State (incl. lastClickableId) and
                // re-delivers it on EVERY later onTileRequest — freshness and push
                // refreshes, not just taps. Without dedupe, one Unlock tap
                // re-sent unlock on every background render. Ids carry a
                // per-render nonce (see cmd()), so "same id as last handled for
                // THIS slot" means this exact tap already ran. Keyed by poolIndex
                // because each pool tile has its own persisted lastClickableId.
                if (clickId?.startsWith(CMD_PREFIX) == true &&
                    clickId != localStore.tileLastClick(poolIndex)
                ) {
                    localStore.setTileLastClick(poolIndex, clickId)
                    val action = clickId.removePrefix(CMD_PREFIX).substringBefore(':')
                    val c = car
                    if (c != null) {
                        // Apply the optimistic flip synchronously so the re-read
                        // below reflects it (applyOptimistic also resolves toggle
                        // vocab before flipping), then relay the slow network half
                        // in the background so the tile still renders immediately.
                        val resolved = WearComms.applyOptimistic(ctx, WearCommand(c.vin, action))
                        // Relay on the process-lifetime scope, NOT a service
                        // scope: the system can tear this Service down the moment
                        // buildTile returns, cancelling an in-flight relay and
                        // silently dropping the user's command mid-send.
                        TileCommandRelay.scope.launch {
                            runCatching { WearComms.relayCommand(ctx, resolved) }
                        }
                    }
                    data = store.current()
                    car = pick(data)
                }
                val metric = local?.unitSystem == "metric"
                TileData(car, resolveRoles(ctx, car?.vin), actions, metric)
            }

            val car = result.car
            val nonce = System.currentTimeMillis().toString(36)
            val layout = if (car == null) {
                emptyLayout(ctx, device)
            } else {
                carLayout(ctx, device, car, result.roles, result.actions, nonce, result.metric)
            }

            // Refresh faster while charging — the percentage moves quickly.
            val freshness = if (car?.charging == true) FRESHNESS_CHARGING_MS else FRESHNESS_MS

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(freshness)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        } catch (t: Throwable) {
            // A corrupt/failed store read or a layout-builder error must never
            // crash the tile (a failed future = a blank, broken tile). Fall back
            // to the safe "Open Bloo" layout.
            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(emptyLayout(ctx, device)))
                .build()
        }
    }

    /**
     * Resolve the full colour roles from the phone-synced settings, preferring a
     * per-car override (`carColors[vin]`) over the global `colors`. Falls back to
     * [DEFAULT_ROLES] if nothing is synced yet or the read throws, so the tile
     * always has a coherent palette to render.
     */
    private suspend fun resolveRoles(ctx: android.content.Context, vin: String?): WearColorRoles =
        runCatching {
            val payload = WearSettingsStore(ctx).flow.first()
            vin?.let { payload?.carColors?.get(it) }
                ?: payload?.colors
                ?: DEFAULT_ROLES
        }.getOrElse { DEFAULT_ROLES }

    // ── Empty / not-configured layout ──────────────────────────────────────────

    /** Shown when no snapshot is available for this slot (fresh install, no sync
     *  yet, or the pinned VIN is gone with no selected fallback): a short message
     *  plus one chip that opens the app. */
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

    // ── Main car layout ─────────────────────────────────────────────────────────

    /**
     * The full glanceable layout for a resolved snapshot: a charge/fuel arc
     * wrapping the edge, a tappable centre column (name / big % / status line),
     * and 1–2 icon action buttons. A pure function of its arguments — everything
     * it needs is passed in rather than read from disk here, so it can't
     * accidentally block on I/O on the render path.
     */
    private fun carLayout(
        ctx: android.content.Context,
        device: DeviceParameters,
        snap: VehicleSnapshot,
        roles: WearColorRoles,
        actions: List<String>,
        nonce: String,
        metric: Boolean,
    ): LayoutElementBuilders.LayoutElement {
        val screenDp = device.screenWidthDp
        val isSmall = screenDp < 193
        val isTiny = screenDp < 182

        val locked = snap.locked == true
        val charging = snap.charging == true
        val climate = snap.climateOn == true
        val pct = snap.percent ?: 0
        val pctText = "${snap.percent ?: "—"}%"
        val rngText = snap.rangeMi?.let { formatDistance(it, metric) } ?: ""
        // Clarify what the big % means, doubling as the footer. hasBattery (not
        // raw isEv) so a manually-corrected PHEV still reads "Battery".
        val secondaryLabel = if (snap.hasBattery) "Battery" else "Fuel"

        // Arc colour: neutral track when unknown, semantic for low/warning,
        // charge-green while charging, accent otherwise.
        // Shared bands (com.bloo.bluelink.data.chargeTier) rather than this tile's own
        // `< 15` / `< 30`, which disagreed with the widget's inclusive thresholds at
        // exactly 15% and 30%. UNKNOWN keeps this surface's own answer: the track
        // colour, i.e. no visible arc.
        val arcArgb = when (com.bloo.bluelink.data.chargeTier(snap.percent, charging)) {
            com.bloo.bluelink.data.ChargeTier.UNKNOWN -> CLR_TRACK
            com.bloo.bluelink.data.ChargeTier.CHARGING -> CLR_CHARGE
            com.bloo.bluelink.data.ChargeTier.CRITICAL -> roles.error
            com.bloo.bluelink.data.ChargeTier.LOW -> CLR_WARN
            com.bloo.bluelink.data.ChargeTier.NORMAL -> roles.primary
        }

        val arc = CircularProgressIndicator.Builder()
            .setProgress(pct.coerceIn(0, 100) / 100f)
            .setCircularProgressIndicatorColors(
                ProgressIndicatorColors.progressIndicatorColors(
                    Colors(arcArgb, CLR_WHITE, CLR_TRACK, CLR_WHITE)
                )
            )
            .build()

        // Shared with the phone so state priority (driving > charging > climate >
        // lock) always agrees; the range suffix on "Charging" is wear-specific.
        val baseStatus = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        val statusLine = if (charging && rngText.isNotEmpty()) "$baseStatus · $rngText" else baseStatus
        // Mirror vehicleStateLabel's own priority order exactly so the colour
        // never disagrees with which state the text settled on.
        val statusArgb = when {
            snap.engineOn == true -> roles.primary
            charging -> CLR_CHARGE
            climate -> roles.tertiary
            snap.locked == true -> CLR_DIM
            snap.locked == false -> CLR_UNLOCKED
            else -> CLR_DIM
        }

        // Shrink the big percentage on tiny watches.
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

        // The chosen actions (1–2 of lock/climate/charge) as icon-only circular
        // buttons — icon-only so they can never truncate. Drop "charge" for a car
        // with no chargeable battery, so a gas-only car never shows a button that
        // can only ever fail.
        val chosen = actions
            .filter { it in TILE_CHIP_ACTIONS && (it != "charge" || snap.hasBattery) }
            .distinct()
            .take(2)
            .ifEmpty { listOf("lock", "climate") }
        val btnSize = when {
            chosen.size == 1 -> ButtonDefaults.LARGE_SIZE
            isTiny -> DimensionBuilders.dp(44f)
            else -> ButtonDefaults.DEFAULT_SIZE
        }
        val chipGap = if (isTiny) 6f else 12f

        val chipRowBuilder = LayoutElementBuilders.Row.Builder().setHeight(DimensionBuilders.wrap())
        chosen.forEachIndexed { i, action ->
            if (i > 0) chipRowBuilder.addContent(spacer(chipGap))
            chipRowBuilder.addContent(actionButton(ctx, action, snap, roles, btnSize, nonce))
        }
        val chipRow = chipRowBuilder.build()

        val gap = if (isTiny) 2f else if (isSmall) 3f else 5f

        // Tapping the centre (name/%/status) opens the app; the chips own their
        // own taps.
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
            .apply {
                // The centre column already shows snap.name, so only add the
                // primary label when there's a distinct range to show — otherwise
                // the name rendered twice. The primary-label slot is optional.
                if (rngText.isNotBlank()) {
                    setPrimaryLabelTextContent(
                        Text.Builder(ctx, rngText)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(CLR_DIM))
                            .setMaxLines(1)
                            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                            .build()
                    )
                }
            }
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

    /** A click that launches the watch app. */
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

    /** A command chip's click id: "cmd:<action>:<nonce>". The nonce makes each
     *  render's ids unique so buildTile's dedupe can tell a fresh tap from the
     *  system replaying a stale lastClickableId on a background refresh. */
    private fun cmd(action: String, nonce: String): Clickable =
        Clickable.Builder()
            .setId(CMD_PREFIX + action + ":" + nonce)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

    /** One state-reflecting circular action button. Icon-only (never truncates);
     *  colour encodes state — filled accent when "on", muted surface when off. */
    private fun actionButton(
        ctx: android.content.Context,
        action: String,
        snap: VehicleSnapshot,
        roles: WearColorRoles,
        size: DimensionBuilders.DpProp,
        nonce: String,
    ): LayoutElementBuilders.LayoutElement {
        val locked = snap.locked == true
        val charging = snap.charging == true
        val climate = snap.climateOn == true
        val offColors = ButtonColors(roles.surfaceContainerHigh, roles.onSurfaceVariant)

        val img: String
        val colors: ButtonColors
        val act: String
        val desc: String
        when (action) {
            "charge" -> {
                img = Img.BOLT
                colors = if (charging) ButtonColors(CLR_CHARGE, CLR_WHITE) else offColors
                act = if (charging) WearAction.CHARGE_OFF else WearAction.CHARGE_ON
                desc = if (charging) "Stop charging" else "Start charging"
            }
            "climate" -> {
                // Colour reflects the climate button's OWN state only — it must
                // not borrow the charge button's green when both are on, which
                // would read as "this controls charging" rather than climate.
                img = Img.CLIMATE
                colors = if (climate) ButtonColors(roles.tertiary, roles.onTertiary) else offColors
                act = if (climate) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
                desc = if (climate) "Turn climate off" else "Turn climate on"
            }
            else -> { // lock — unlocked is the highlighted (filled-primary) state
                img = if (locked) Img.LOCK else Img.UNLOCK
                colors = if (locked) offColors else ButtonColors(roles.primary, roles.onPrimary)
                act = if (locked) WearAction.UNLOCK else WearAction.LOCK
                desc = if (locked) "Unlock" else "Lock"
            }
        }
        return Button.Builder(ctx, cmd(act, nonce))
            .setButtonColors(colors)
            .setIconContent(img)
            .setContentDescription(desc)
            .setSize(size)
            .build()
    }

    private fun spacer(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.dp(dp))
            .setHeight(DimensionBuilders.dp(dp))
            .build()

    private companion object {
        const val RES_VERSION = "4"
        const val CMD_PREFIX = "cmd:"
        const val FRESHNESS_MS = 10L * 60L * 1000L
        const val FRESHNESS_CHARGING_MS = 90L * 1000L

        const val CLR_WHITE = 0xFFFFFFFF.toInt()
        const val CLR_DIM = 0xFFAAAAAA.toInt()
        // Reuse :shared/BlooColors (stored there as raw Int for exactly this kind
        // of non-Compose use) so the tile's semantic colours can't silently drift
        // from the phone's.
        const val CLR_CHARGE = BlooColors.chargeGreen
        const val CLR_WARN = BlooColors.warn
        const val CLR_TRACK = 0xFF3C3C3C.toInt()
        // Matches the phone widget's unlockedRed so "unlocked" reads as the same
        // semantic red everywhere.
        const val CLR_UNLOCKED = BlooColors.heat

        /** Fallback roles for before any phone sync has occurred. */
        val DEFAULT_ROLES = WearColorRoles(
            primary = BlooColors.brandAccent,
            onPrimary = 0xFF000000.toInt(),
            primaryContainer = 0xFF3A3F7A.toInt(),
            onPrimaryContainer = 0xFFFFFFFF.toInt(),
            secondary = 0xFF9B83EB.toInt(),
            onSecondary = 0xFF000000.toInt(),
            secondaryContainer = 0xFF4A3F7A.toInt(),
            onSecondaryContainer = 0xFFFFFFFF.toInt(),
            tertiary = 0xFF4CD9E0.toInt(),
            onTertiary = 0xFF003B3E.toInt(),
            tertiaryContainer = 0xFF004F53.toInt(),
            onTertiaryContainer = 0xFF6FF6FF.toInt(),
            background = 0xFF111318.toInt(),
            onBackground = 0xFFE2E2E9.toInt(),
            onSurface = 0xFFE2E2E9.toInt(),
            onSurfaceVariant = 0xFFAAAAAA.toInt(),
            surfaceContainerLow = 0xFF1A1B20.toInt(),
            surfaceContainer = 0xFF1F2026.toInt(),
            surfaceContainerHigh = 0xFF262730.toInt(),
            outline = 0xFF8E8E9A.toInt(),
            outlineVariant = 0xFF44474F.toInt(),
            error = BlooColors.heat,
            onError = 0xFF690005.toInt(),
            errorContainer = 0xFF93000A.toInt(),
            onErrorContainer = 0xFFFFDAD6.toInt(),
        )
    }
}

/** Process-lifetime scope for the fire-and-forget tile command relay. A
 *  BlooTileService instance can be destroyed the instant its buildTile()
 *  returns, so relaying on the service's own scope would let onDestroy cancel
 *  the send mid-flight and silently drop the tap. Tied to the app process
 *  instead, so the relay always completes; SupervisorJob keeps one failed relay
 *  from cancelling later ones; IO dispatcher because relayCommand is
 *  network-bound. */
private object TileCommandRelay {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class BlooTile1 : BlooTileService() { override val poolIndex = 0 }
class BlooTile2 : BlooTileService() { override val poolIndex = 1 }
class BlooTile3 : BlooTileService() { override val poolIndex = 2 }
class BlooTile4 : BlooTileService() { override val poolIndex = 3 }

/**
 * Nudge every pool Tile and the watch-face complications to re-read the latest
 * snapshot. The single source of truth for "which glanceable surfaces exist" —
 * called from the ViewModel (app open) and WearListenerService (phone push,
 * app closed). Must target the CONCRETE tile classes: the updater matches by
 * exact ComponentName, so the abstract BlooTileService would match nothing.
 */
fun refreshWearGlanceables(context: android.content.Context) {
    val updater = runCatching { TileService.getUpdater(context) }.getOrNull()
    listOf(
        BlooTile1::class.java,
        BlooTile2::class.java,
        BlooTile3::class.java,
        BlooTile4::class.java,
    ).forEach { cls -> runCatching { updater?.requestUpdate(cls) } }
    ComplicationLink.requestUpdate(context)
}
