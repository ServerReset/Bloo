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
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.vehicleStateLabel
import com.bloo.wear.R
import com.bloo.wear.TILE_CHIP_ACTIONS
import com.bloo.wear.WearComms
import com.bloo.bluelink.data.formatDistance
import com.bloo.wear.WearLocalStore
import com.bloo.wear.WearSettingsStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Bloo Wear OS Tile — charge arc, car name, big %, status line, lock + climate chips.
 * Uses the same icon drawables as the phone widget. Colors come from the synced
 * WearColorRoles so the tile always matches the phone app theme.
 *
 * Concrete [poolIndex]ed subclasses ([BlooTile1]..[BlooTile4]) form a fixed pool —
 * mirroring the phone's BlooTile1..12 Quick Settings pool — so a user with
 * multiple cars can add one Tile per car to their watch face, each pinned
 * independently via Settings.
 */
abstract class BlooTileService : TileService() {

    protected abstract val poolIndex: Int

    // ProtoLayout Tiles are NOT Compose: the system (Wear OS Tiles surface,
    // rendered outside this process's own UI) calls back into this Service on
    // its own binder thread whenever it wants a fresh layout, and expects a
    // ListenableFuture back rather than a suspend function. `executor` is a
    // single dedicated thread used to run the (blocking, via runBlocking)
    // buildTile() work off the caller's thread; `tileScope` is a separate
    // coroutine scope (IO dispatcher) used only for the fire-and-forget network
    // relay of a tap-triggered command, which must NOT block tile rendering.
    private val executor = Executors.newSingleThreadExecutor()
    private val tileScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    // The system can destroy/recreate this Service between tile requests (it's
    // not kept warm), so both the executor and the coroutine scope must be
    // torn down here to avoid leaking a thread / dangling coroutines each time.
    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
        executor.shutdown()
    }

    /** Resource ID strings referenced in the tile layout. */
    private object Img {
        const val LOCK    = "img_lock"
        const val UNLOCK  = "img_unlock"
        const val CLIMATE = "img_climate"
        const val BOLT    = "img_bolt"
    }

    // Called by the Tiles system (the framework's TileRenderer/Tile Manager,
    // running out-of-process) whenever the tile needs to (re-)render: on first
    // pin, on a freshness-interval timeout (see FRESHNESS_MS/FRESHNESS_CHARGING_MS
    // below), when the user taps something on the tile (state carries the
    // clicked element's id via `currentState.lastClickableId`), or on a push
    // from refreshWearGlanceables(). The actual layout building happens
    // synchronously inside buildTile() on `executor` so this call returns
    // immediately with a Future the system awaits.
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(Callable { buildTile(requestParams) }, executor)

    // Called once (and cached by the system) to map the string ids used by
    // Img.* inside the layout to actual Android drawable resources. This is
    // ProtoLayout's resource-indirection: the layout tree returned by
    // onTileRequest only ever references resources by string id, never by
    // resource id directly, because the layout is serialized and rendered in a
    // separate process/surface that doesn't have this app's resource table.
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

    /** Wraps a drawable resource id into the ProtoLayout ImageResource type
     *  required by [onTileResourcesRequest]'s id->resource map. */
    private fun imgRes(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()

    /** Bundles everything [buildTile] resolves from disk/DataStore in one pass,
     *  so the rest of the layout-building code below doesn't need to re-read
     *  or thread multiple separate values through. */
    private data class TileResult(
        val car: VehicleSnapshot?,
        val roles: WearColorRoles,
        val actions: List<String>,
        val metric: Boolean,
    )

    /**
     * Synchronously builds the full [TileBuilders.Tile] returned to the Tiles
     * system for this request. Runs on `executor` (see [onTileRequest]), and
     * internally uses [runBlocking] to await the DataStore reads/writes below
     * since ProtoLayout's TileService callback contract is synchronous (a
     * Future, not a suspend fun) despite reading suspend-based stores.
     */
    private fun buildTile(params: RequestBuilders.TileRequest): TileBuilders.Tile {
        val ctx = applicationContext
        val device = params.deviceConfiguration
        // Non-null only when this request was triggered by the user tapping a
        // clickable element on the previously-rendered tile (a chip/button built
        // via cmd()); on a freshness-timeout or push-triggered refresh this is
        // whatever id was last recorded by the SYSTEM, not necessarily a fresh
        // tap -- see the dedupe check further down before treating it as one.
        val clickId = params.currentState.lastClickableId
        try {

        // One pass off the disk: read the snapshot once, apply the optional command's
        // optimistic update (WearComms.send writes back to the store), re-read so the
        // rendered car reflects it, then resolve theme roles for that same car.
        val result = runBlocking {
            val store = SnapshotStore(ctx)
            val localStore = WearLocalStore(ctx)
            val local = runCatching { localStore.flow.first() }.getOrNull()
            val actions = local?.tileActions ?: listOf("lock", "climate")
            val tileVin = local?.tileCarVins?.getOrNull(poolIndex)

            // The Tile shows the car pinned to this pool slot if it still exists,
            // else the app/widget's selected car (so an unconfigured/new slot is
            // never blank).
            fun pick(d: SnapshotStore.SnapshotData): VehicleSnapshot? =
                tileVin?.let { v -> d.vehicles.firstOrNull { it.vin == v } } ?: d.selected

            var data = store.current()
            var car = pick(data)
            // The system persists tile State (incl. lastClickableId) and re-delivers
            // it on EVERY later onTileRequest - freshness refreshes, push refreshes -
            // not just taps. Without dedupe, one tap on Unlock kept re-sending the
            // unlock command to the car on every background render. Ids carry a
            // per-render nonce (see cmd()), so "same id as last handled" means this
            // exact tap was already executed. Keyed by this tile's own poolIndex --
            // each pool tile has its own independently-persisted lastClickableId.
            if (clickId?.startsWith(CMD_PREFIX) == true && clickId != localStore.tileLastClick(poolIndex)) {
                localStore.setTileLastClick(poolIndex, clickId)
                val action = clickId.removePrefix(CMD_PREFIX).substringBefore(':')
                val c = car
                if (c != null) {
                    // Apply the optimistic snapshot flip synchronously so the
                    // re-read just below actually reflects it, then relay the
                    // slower network half in the background so the tile still
                    // renders immediately.
                    val resolved = WearComms.applyOptimistic(ctx, WearCommand(c.vin, action))
                    tileScope.launch {
                        runCatching { WearComms.relayCommand(ctx, resolved) }
                    }
                }
                data = store.current()
                car = pick(data)
            }
            val metric = local?.unitSystem == "metric"
            TileResult(car, resolveRoles(ctx, car?.vin), actions, metric)
        }
        val snapshot = result.car
        val roles = result.roles
        val actions = result.actions
        val metric = result.metric

        val nonce = System.currentTimeMillis().toString(36)
        val layout = if (snapshot == null) emptyLayout(ctx, device) else carLayout(ctx, device, snapshot, roles, actions, nonce, metric)

        // Refresh faster while charging (percent moves quickly) than when idle.
        val freshness = if (snapshot?.charging == true) FRESHNESS_CHARGING_MS else FRESHNESS_MS

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(freshness)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        } catch (t: Throwable) {
            // A corrupt/failed DataStore read or a layout-builder error must never
            // crash the tile — returning a failed future gives the user a broken,
            // blank tile. Fall back to the safe "Open Bloo" layout instead.
            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(emptyLayout(ctx, device)))
                .build()
        }
    }

    /** Resolve the full color roles from the phone-synced settings. Falls back to dark defaults.
     *  Mechanism: reads the first emitted value from WearSettingsStore's flow
     *  (a DataStore-backed Flow synced over the Data Layer from the phone),
     *  preferring a per-car color override (`carColors[vin]`, set when the
     *  phone assigns a distinct theme to that specific vehicle) over the
     *  global `colors` payload; if neither exists, or the whole read throws
     *  (no sync has happened yet, corrupt store, etc. -- via runCatching),
     *  falls back to the hardcoded [DEFAULT_ROLES] dark palette so the tile
     *  always has *something* coherent to render. */
    private suspend fun resolveRoles(ctx: android.content.Context, vin: String?): WearColorRoles =
        runCatching {
            val payload = WearSettingsStore(ctx).flow.first()
            vin?.let { payload?.carColors?.get(it) }
                ?: payload?.colors
                ?: DEFAULT_ROLES
        }.getOrElse { DEFAULT_ROLES }

    // ── Empty / not-configured layout ────────────────────────────────────────

    /** The layout shown when no car snapshot is available for this tile's
     *  pool slot (fresh install, no phone sync yet, or the pinned car's VIN no
     *  longer exists and there's no fallback selected car either). Just a
     *  short message plus a single chip that opens the app -- deliberately
     *  minimal since there's no vehicle data to build a real layout from. */
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

    /**
     * Builds the full glanceable layout for a resolved car snapshot: a
     * progress arc (charge/fuel %) wrapping the tile edge, a center column
     * (car name / big percentage / status line), and 1-2 action chips below.
     * Pure function of its arguments -- everything it needs (snapshot, theme
     * roles, chosen actions, a per-render nonce for click-id uniqueness, and
     * the metric/imperial unit preference) is passed in rather than read from
     * disk here, so this can't accidentally do blocking I/O on the render path.
     */
    private fun carLayout(
        ctx: android.content.Context,
        device: DeviceParameters,
        snap: VehicleSnapshot,
        roles: WearColorRoles,
        actions: List<String>,
        nonce: String,
        metric: Boolean = false,
    ): LayoutElementBuilders.LayoutElement {
        val screenDp = device.screenWidthDp
        val isSmall  = screenDp < 193
        val isTiny   = screenDp < 182

        val locked   = snap.locked   == true
        val charging = snap.charging == true
        val climate  = snap.climateOn == true
        val pct      = snap.percent ?: 0
        val pctText  = "${snap.percent ?: "—"}%"
        val rngText  = snap.rangeMi?.let { formatDistance(it, metric) } ?: ""
        // Clarify what the big % means for this car (and double as the brand footer).
        // hasBattery (not the raw isEv) so a manually-corrected PHEV on the phone
        // (misreported as gas by the API) still reads "Battery" here too.
        val secondaryLabel = if (snap.hasBattery) "Battery" else "Fuel"

        val hasPct = snap.percent != null

        // Arc color: neutral track when unknown, semantic for warnings, accent otherwise.
        val arcArgb = when {
            !hasPct  -> CLR_TRACK
            charging -> CLR_CHARGE
            pct < 15 -> roles.error
            pct < 30 -> CLR_WARN
            else     -> roles.primary
        }

        val arc = CircularProgressIndicator.Builder()
            .setProgress(pct.coerceIn(0, 100) / 100f)
            .setCircularProgressIndicatorColors(
                ProgressIndicatorColors.progressIndicatorColors(
                    Colors(arcArgb, CLR_WHITE, CLR_TRACK, CLR_WHITE)
                )
            )
            .build()

        // Shared with the phone widget/tiles so "what's this car doing" always
        // agrees on the same priority order (driving > charging > climate > lock);
        // the range suffix on "Charging" is a wear-tile-specific addition.
        val baseStatus = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        val statusLine = if (charging && rngText.isNotEmpty()) "$baseStatus · $rngText" else baseStatus
        // Mirrors vehicleStateLabel's own priority order exactly (driving > charging >
        // climate > locked > unlocked > unknown) so the color never disagrees with
        // which state the text actually settled on.
        val statusArgb = when {
            snap.engineOn == true -> roles.primary
            charging -> CLR_CHARGE
            climate  -> roles.tertiary
            snap.locked == true  -> CLR_DIM
            snap.locked == false -> CLR_UNLOCKED
            else -> CLR_DIM
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

        // The user's chosen actions (1–2 of lock/climate/charge) as circular icon
        // buttons. Icon-only, so they can never truncate like text chips did.
        // Drop "charge" for a car with no chargeable battery -- otherwise a
        // gas-only car with charge configured as a chip shows a button that can
        // only ever fail.
        val chosen = actions.filter { it in TILE_CHIP_ACTIONS && (it != "charge" || snap.hasBattery) }
            .distinct().take(2)
            .ifEmpty { listOf("lock", "climate") }
        val btnSize = when {
            chosen.size == 1 -> ButtonDefaults.LARGE_SIZE
            isTiny           -> DimensionBuilders.dp(44f)
            else             -> ButtonDefaults.DEFAULT_SIZE
        }
        val chipGap = if (isTiny) 6f else 12f

        val chipRowBuilder = LayoutElementBuilders.Row.Builder().setHeight(DimensionBuilders.wrap())
        chosen.forEachIndexed { i, action ->
            if (i > 0) chipRowBuilder.addContent(spacer(chipGap))
            chipRowBuilder.addContent(actionButton(ctx, action, snap, roles, btnSize, nonce))
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

    /** Chip click id: "cmd:<action>:<nonce>". The nonce makes each render's ids
     *  unique so buildTile's last-handled dedupe can tell a fresh tap from the
     *  system replaying a stale lastClickableId on a background refresh. */
    private fun cmd(action: String, nonce: String): Clickable = Clickable.Builder()
        .setId(CMD_PREFIX + action + ":" + nonce)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    /** Build one state-reflecting circular action button (lock / climate / charge).
     *  Icon-only, so it can never truncate like the old text chips. Colour encodes
     *  state: filled-accent when "on", muted surface when off. */
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
                // Color reflects the climate button's OWN state only — it used to also
                // check `charging` first and would borrow the charge button's green
                // whenever both happened to be true at once, which read as "this button
                // controls charging" rather than climate.
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
        const val RES_VERSION  = "4"
        const val CMD_PREFIX   = "cmd:"
        const val FRESHNESS_MS = 10L * 60L * 1000L
        const val FRESHNESS_CHARGING_MS = 90L * 1000L

        const val CLR_WHITE = 0xFFFFFFFF.toInt()
        const val CLR_DIM   = 0xFFAAAAAA.toInt()
        // These re-declared the same hex values shared/BlooColors.kt already
        // centralizes (stored there as raw Int for exactly this kind of
        // non-Compose use) -- referencing it directly means they can't
        // silently drift out of sync the way chargerLabel's text already had.
        const val CLR_CHARGE = com.bloo.bluelink.data.BlooColors.chargeGreen
        const val CLR_WARN   = com.bloo.bluelink.data.BlooColors.warn
        const val CLR_TRACK  = 0xFF3C3C3C.toInt()
        // Matches the phone widget's unlockedRed (BlooWidget.kt) so "unlocked" reads
        // as the same semantic red everywhere instead of drifting to a neutral gray.
        const val CLR_UNLOCKED = com.bloo.bluelink.data.BlooColors.heat

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
            // Matches BlooColors.heat -- missed in the earlier pass that
            // pointed CLR_UNLOCKED (this same file, two lines up from here in
            // the companion object) at the same constant.
            error                 = com.bloo.bluelink.data.BlooColors.heat,
            onError               = 0xFF690005.toInt(),
            errorContainer        = 0xFF93000A.toInt(),
            onErrorContainer      = 0xFFFFDAD6.toInt(),
        )
    }
}

class BlooTile1 : BlooTileService() { override val poolIndex = 0 }
class BlooTile2 : BlooTileService() { override val poolIndex = 1 }
class BlooTile3 : BlooTileService() { override val poolIndex = 2 }
class BlooTile4 : BlooTileService() { override val poolIndex = 3 }

/** Nudge every pool Tile and the watch-face complications to re-read the latest
 *  snapshot. The single source of truth for "which glanceable surfaces exist" -
 *  called from the ViewModel (app open) and WearListenerService (phone push,
 *  app closed). Must target the CONCRETE tile classes: the updater matches by
 *  exact ComponentName, so the abstract BlooTileService would match nothing. */
fun refreshWearGlanceables(context: android.content.Context) {
    val updater = runCatching { TileService.getUpdater(context) }.getOrNull()
    listOf(BlooTile1::class.java, BlooTile2::class.java, BlooTile3::class.java, BlooTile4::class.java)
        .forEach { cls -> runCatching { updater?.requestUpdate(cls) } }
    com.bloo.wear.complication.ComplicationLink.requestUpdate(context)
}
