package com.bloo.bluelink.wear

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.first
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearSettingsPayload
import com.bloo.bluelink.ui.ThemeMode
import com.bloo.bluelink.ui.blooColorScheme
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAuthBundle
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandResult
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearSessionDto
import com.bloo.bluelink.data.WearStatePayload
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The phone half of the watch sync. It mirrors car snapshots + sessions to the
 * Wearable Data Layer and runs the commands the watch forwards (delegating to the
 * shared [WearCommandRunner], the same stored-session pattern the Quick-Settings
 * tiles use) — so the watch never needs the credentials to lock a door.
 */
object WearBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget publish of the current snapshots (and auth) to a paired watch. */
    fun publish(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { publishNow(app) }
            runCatching { publishAuth(app) }
        }
    }

    /** Publish the on-disk snapshots as a Data Layer item (phone → watch). */
    suspend fun publishNow(context: Context) {
        val data = SnapshotStore(context).current()
        val payload = WearStatePayload(
            vehicles = data.vehicles,
            selectedVin = data.selectedVin,
            producedAt = System.currentTimeMillis(),
        )
        val request = PutDataMapRequest.create(WearSync.PATH_STATE).apply {
            dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeState(payload))
            // A changing timestamp guarantees the item is treated as updated even
            // when the car states are byte-identical to the previous push.
            dataMap.putLong(WearSync.KEY_TIMESTAMP, payload.producedAt)
        }.asPutDataRequest().setUrgent()
        runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
    }

    /** Fan the latest snapshot out to every downstream surface (home widget, QS
     *  tiles, and the watch) after a data change - the single place that knows
     *  which surfaces exist, called from the workers/services that mutate state. */
    suspend fun refreshAllSurfaces(context: Context) {
        runCatching { publishNow(context) }
        runCatching {
            val appearance = com.bloo.bluelink.data.SettingsStore(context).appearance.first()
            publishSettingsNow(context, appearance)
        }
        runCatching { com.bloo.bluelink.widget.BlooWidget().updateAll(context) }
        runCatching { com.bloo.bluelink.tiles.BlooTileService.requestUpdates(context) }
    }

    /**
     * Publish the signed-in sessions so the watch can operate standalone on its
     * own Wi-Fi/cell. Sent as a separate item with no timestamp, so the Data
     * Layer only re-transmits it when the tokens actually change.
     */
    suspend fun publishAuth(context: Context) {
        val sessionStore = SessionStore(context)
        val sessions = sessionStore.loggedInBrands().mapNotNull { brand ->
            sessionStore.load(brand)?.let { s ->
                WearSessionDto(
                    brand = s.brand.name,
                    accessToken = s.accessToken,
                    refreshToken = s.refreshToken,
                    username = s.username,
                    pin = s.pin,
                    deviceId = s.deviceId,
                )
            }
        }
        val request = PutDataMapRequest.create(WearSync.PATH_AUTH).apply {
            dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeAuth(WearAuthBundle(sessions)))
        }.asPutDataRequest().setUrgent()
        runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
    }

    /**
     * Mirror the phone's appearance + preferences to the watch: the *resolved*
     * Material 3 role colours (so the watch theme matches exactly), the
     * temperature unit and the UI scale. Republished whenever they change.
     */
    fun publishSettings(context: Context, appearance: SettingsStore.Appearance) {
        val app = context.applicationContext
        scope.launch { runCatching { publishSettingsNow(app, appearance) } }
    }

    suspend fun publishSettingsNow(context: Context, appearance: SettingsStore.Appearance) {
        val dark = when (appearance.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> isSystemDark(context)
        }
        val custom = if (!appearance.dynamicColor) {
            appearance.customPalettes.find { it.id == appearance.activeCustomPaletteId }
        } else null
        val s = blooColorScheme(
            context = context,
            dark = dark,
            themeMode = appearance.themeMode,
            dynamicColor = appearance.dynamicColor,
            colorPalette = appearance.colorPalette,
            customPalette = custom,
            vibrancy = appearance.vibrancy,
        )
        // Per-car custom-palette overrides resolved to colours, so each car page
        // on the watch can wear its own theme like the phone.
        val carColors = appearance.carCustomPaletteIds.mapNotNull { (vin, paletteId) ->
            val palette = appearance.customPalettes.find { it.id == paletteId } ?: return@mapNotNull null
            val carScheme = blooColorScheme(
                context = context, dark = dark, themeMode = appearance.themeMode,
                dynamicColor = false, colorPalette = appearance.colorPalette,
                customPalette = palette, vibrancy = appearance.vibrancy,
            )
            vin to rolesOf(carScheme)
        }.toMap()

        // Each car's pebble order (and which pebbles the user hid) so the watch
        // lays its tiles out to match and drops what the phone hides.
        val store = SettingsStore(context)
        val vins = SnapshotStore(context).current().vehicles.map { it.vin }
        val pebbleOrders = vins.associateWith { store.sectionOrder(it) }
        val hiddenSections = vins.associateWith { store.hiddenSections(it) }

        val payload = WearSettingsPayload(
            dark = dark,
            useFahrenheit = appearance.useFahrenheit,
            unitSystem = appearance.unitSystem,
            uiScale = appearance.uiScale,
            colors = rolesOf(s),
            carColors = carColors,
            pebbleOrders = pebbleOrders,
            hiddenSections = hiddenSections,
            aiEnabled = store.aiEnabled(),
            auroraEnabled = appearance.auroraBackground,
            auroraColorMode = appearance.auroraColorMode,
            auroraCustomColor = appearance.auroraCustomColor,
        )
        val request = PutDataMapRequest.create(WearSync.PATH_SETTINGS).apply {
            dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeSettings(payload))
        }.asPutDataRequest().setUrgent()
        runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
    }

    private fun rolesOf(s: androidx.compose.material3.ColorScheme) = WearColorRoles(
        primary = s.primary.toArgb(),
        onPrimary = s.onPrimary.toArgb(),
        primaryContainer = s.primaryContainer.toArgb(),
        onPrimaryContainer = s.onPrimaryContainer.toArgb(),
        secondary = s.secondary.toArgb(),
        onSecondary = s.onSecondary.toArgb(),
        secondaryContainer = s.secondaryContainer.toArgb(),
        onSecondaryContainer = s.onSecondaryContainer.toArgb(),
        tertiary = s.tertiary.toArgb(),
        onTertiary = s.onTertiary.toArgb(),
        tertiaryContainer = s.tertiaryContainer.toArgb(),
        onTertiaryContainer = s.onTertiaryContainer.toArgb(),
        background = s.background.toArgb(),
        onBackground = s.onBackground.toArgb(),
        onSurface = s.onSurface.toArgb(),
        onSurfaceVariant = s.onSurfaceVariant.toArgb(),
        surfaceContainerLow = s.surfaceContainerLow.toArgb(),
        surfaceContainer = s.surfaceContainer.toArgb(),
        surfaceContainerHigh = s.surfaceContainerHigh.toArgb(),
        outline = s.outline.toArgb(),
        outlineVariant = s.outlineVariant.toArgb(),
        error = s.error.toArgb(),
        onError = s.onError.toArgb(),
        errorContainer = s.errorContainer.toArgb(),
        onErrorContainer = s.onErrorContainer.toArgb(),
    )

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Mirror saved climate presets (keyed by VIN) to the watch. */
    fun publishPresets(context: Context, byVin: Map<String, List<com.bloo.bluelink.data.ClimatePreset>>) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_PRESETS).apply {
                    dataMap.putString(
                        WearSync.KEY_PAYLOAD,
                        WearSync.encodePresets(com.bloo.bluelink.data.WearPresets(byVin)),
                    )
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(request))
            }
        }
    }

    /** Mirror the phone's live climate draft (sliders + active preset) to the
     *  watch over the shared bidirectional climate channel. */
    fun publishClimate(context: Context, state: com.bloo.bluelink.data.WearClimateState) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_CLIMATE).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeClimate(state))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(request))
            }
        }
    }

    /** Mirror weather / car photos / AI summaries to the watch. */
    fun publishExtras(context: Context, extras: com.bloo.bluelink.data.WearExtras) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_EXTRAS).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeExtras(extras))
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(request))
            }
        }
    }

    suspend fun execute(context: Context, command: WearCommand): WearCommandResult =
        WearCommandRunner.execute(context, command)

    suspend fun refresh(context: Context, vin: String) =
        WearCommandRunner.refresh(context, vin)

    /** Trigger a Drive sync: download settings from Drive, import them, and re-publish
     *  to the watch. Called when the watch requests a Drive sync. Returns the
     *  outcome so the caller can report back to the watch what happened; null
     *  means sync isn't configured on this phone at all.
     *
     *  The download/compare/import/upload sequence itself lives in
     *  [SettingsStore.performDriveSync] — shared with the phone's own
     *  auto-sync-on-refresh collector, so there's exactly one implementation. */
    suspend fun driveSync(context: Context): SettingsStore.DriveSyncOutcome? {
        return runCatching {
            val store = SettingsStore(context)
            if (store.syncUri() == null) {
                com.bloo.bluelink.data.AppLog.log("⚠ Drive sync: not configured")
                return@runCatching null
            }
            com.bloo.bluelink.data.AppLog.log("Drive sync: starting")
            val outcome = store.performDriveSync()
            val appearance = store.appearance.first()
            publishSettingsNow(context, appearance)
            updateAllSurfaces(context)
            outcome
        }.getOrElse { e ->
            // Fall back to the real last-known sync time (not 0L/"never") on a
            // genuinely unexpected exception -- performDriveSync already
            // catches everything it reasonably can internally, so reaching
            // here is rare, but a stray uncaught exception here shouldn't
            // regress "Last synced" to blank when a real prior sync exists.
            val lastKnown = runCatching { SettingsStore(context).lastSyncMs() }.getOrDefault(0L)
            SettingsStore.DriveSyncOutcome(ran = true, imported = false, uploaded = false, syncedAtMs = lastKnown, error = e.message ?: "Sync failed")
        }
    }

    /** Update phone widgets and watch tiles (no state re-fetch). */
    private suspend fun updateAllSurfaces(context: Context) {
        runCatching { com.bloo.bluelink.widget.BlooWidget().updateAll(context) }
        runCatching { com.bloo.bluelink.tiles.BlooTileService.requestUpdates(context) }
    }
}
