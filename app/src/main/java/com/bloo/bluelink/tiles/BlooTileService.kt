package com.bloo.bluelink.tiles

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.Shortcuts
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * A configurable Quick Settings tile. Each tile is assigned a car + action in
 * Settings, with an optional custom name. The tile reflects the car's live state
 * (locked/unlocked, climate on/off, charging) with matching icons, and tapping it
 * toggles that state — either silently in the background or via a quick
 * open-send-close surface, per the user's preference.
 */
abstract class BlooTileService : TileService() {

    /** Which of the 12 pool slots (see [TILE_CLASSES]) this concrete subclass backs;
     *  used to look up its per-tile config/label in [SettingsStore]. */
    abstract val index: Int

    // TileService instances are recreated/torn down by the system frequently (any time the
    // shade opens/closes), so this scope is tied to *this instance's* lifetime, not a
    // singleton -- SupervisorJob keeps one failed child coroutine from cancelling the rest.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The while-visible store observer; see [onStartListening]. */
    private var watchJob: Job? = null

    /**
     * Called by the system whenever this tile becomes visible in the Quick Settings shade
     * (shade pulled down, or the tile scrolled into view) -- this is the TileService
     * lifecycle's cue that [qsTile] is now safe to read/mutate. There's no matching
     * "refresh periodically while visible" hook, so a coroutine is kicked off once here to
     * paint current state; [onStopListening] (not overridden) is the mirror-image call when
     * the shade closes, at which point further `qsTile` access would be invalid.
     */
    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
        // Repaint while visible, instead of relying on someone to poke us.
        //
        // Nothing can poke us from outside: TileService.requestListeningState is only
        // honoured for tiles declaring META_DATA_ACTIVE_TILE, and none of Bloo's twelve do.
        // There used to be a requestUpdates() helper pretending otherwise, with thirteen
        // no-op call sites; it is deleted (see the tombstone in the companion object). In
        // background mode -- which deliberately does not collapse the shade -- its absence
        // meant the user tapped a tile and then watched it sit unchanged while the command
        // ran.
        //
        // Observing the store is the fix rather than declaring the tiles active, because
        // an ACTIVE tile stops getting onStartListening when the shade opens and only
        // gets it on click or request. That would have traded "never repaints after a
        // command" for "never repaints on shade open" -- a different bug, and one needing
        // a device to evaluate. This keeps the shade-open read AND reacts to changes,
        // whatever their source: the command worker's optimistic write, a background
        // poller landing mid-shade, or a sync import.
        //
        // Cancelled in onStopListening, because render() touches qsTile and that is only
        // valid between onStartListening and onStopListening.
        watchJob = scope.launch {
            SnapshotStore(applicationContext).payload
                .drop(1) // the launch above already painted the current value
                .collect { runCatching { render() } }
        }
    }

    /**
     * The shade closed (or this tile scrolled away): stop observing, since [qsTile] is no
     * longer safe to touch. Previously not overridden at all.
     */
    override fun onStopListening() {
        watchJob?.cancel()
        watchJob = null
        super.onStopListening()
    }

    /**
     * The system tears these instances down constantly, and [scope] is tied to THIS
     * instance -- but nothing cancelled it, so every teardown leaked its children.
     */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Reads this tile's assigned car+command from settings and the latest cached vehicle
     * snapshot, then paints [qsTile]'s state/icon/label/subtitle to match and pushes it with
     * [Tile.updateTile]. If no car/command has been assigned yet, shows a generic inactive
     * "unassigned" tile instead. Every settings/store read is wrapped in `runCatching` so a
     * transient failure (e.g. store not yet initialized) degrades to a sane fallback rather
     * than leaving the tile in a broken half-drawn state or crashing the host process.
     */
    private suspend fun render() {
        val tile = qsTile ?: return
        val ctx = applicationContext
        val cfg = runCatching { SettingsStore(ctx).tileConfig(index) }.getOrNull()
        if (cfg == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = runCatching { SettingsStore(ctx).tileLabel(index) }.getOrNull() ?: "Bloo tile ${index + 1}"
            tile.icon = Icon.createWithResource(ctx, R.drawable.ic_shortcut_car)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Unassigned"
            tile.updateTile()
            return
        }
        val (vin, cmd) = cfg
        val snap = runCatching {
            SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
        }.getOrNull()
        val custom = runCatching { SettingsStore(ctx).tileLabel(index) }.getOrNull()

        tile.state = if (isActiveState(cmd, snap)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(ctx, iconFor(cmd, snap))
        tile.label = custom ?: defaultLabel(cmd, snap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = snap?.name ?: "Car"
        tile.updateTile()

        // Optional: kick a throttled live refresh so the shown state is current.
        maybeLiveRefresh(ctx, vin)
    }

    /** If live refresh is on and this car hasn't refreshed recently, queue one. */
    private suspend fun maybeLiveRefresh(ctx: Context, vin: String) {
        val settings = SettingsStore(ctx)
        if (!settings.tileLiveRefresh()) return
        val now = System.currentTimeMillis()
        if (now - settings.tileRefreshedAt(vin) < LIVE_REFRESH_THROTTLE_MS) return
        settings.setTileRefreshedAt(vin, now)
        TileCommandWorker.enqueueRefresh(ctx, vin)
    }

    /**
     * Whether the tile reads as "on" (filled/white background) given the car's
     * state. For lock/unlock the *unlocked* car is the noteworthy state, so the
     * tile lights up white when unlocked and is plain ("none") when locked.
     */
    private fun isActiveState(cmd: String, snap: VehicleSnapshot?): Boolean = when (cmd) {
        "doors", "lock" -> snap?.locked == false
        "unlock" -> snap?.locked == true
        "climate" -> snap?.climateOn == true
        "charge" -> snap?.charging == true
        else -> false
    }

    private fun iconFor(cmd: String, snap: VehicleSnapshot?): Int =
        iconResFor(cmd, unlocked = snap?.locked == false)

    private fun defaultLabel(cmd: String, snap: VehicleSnapshot?): String = when (cmd) {
        // Known state → state label; unknown → neutral "Lock / unlock".
        "doors" -> when (snap?.locked) {
            true -> "Locked"
            false -> "Unlocked"
            else -> "Lock / unlock"
        }
        "lock" -> "Lock"
        "unlock" -> "Unlock"
        "climate" -> if (snap?.climateOn == true) "Climate on" else "Climate"
        "charge" -> if (snap?.charging == true) "Charging" else "Charge"
        "open" -> "Open"
        else -> cmd.replaceFirstChar { it.uppercase() }
    }

    /**
     * Called by the system when the user taps this tile in the shade. Re-reads the tile's
     * config fresh (rather than trusting whatever [render] last painted) since the tap could
     * follow a stale render. Three mutually exclusive outcomes based on this tile's config:
     * an unassigned tile or an "open" action just opens the app; otherwise, depending on the
     * user's global "run in background" preference, either fires the command silently via
     * WorkManager ([runBackground]) or opens a brief transparent activity that visibly runs
     * it ([launchActionActivity]).
     */
    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        scope.launch {
            val cfg = SettingsStore(ctx).tileConfig(index)
            if (cfg == null) { openApp(null, null); return@launch }
            val (vin, cmd) = cfg
            when {
                cmd == "open" -> openApp(vin, cmd)
                SettingsStore(ctx).tileBackground() -> runBackground(ctx, vin, cmd)
                else -> launchActionActivity(vin, cmd)
            }
        }
    }

    /**
     * Background mode: ack instantly with a toast, then run the command via
     * WorkManager so it survives this service being destroyed (the cause of taps
     * "doing nothing"). The worker refreshes the tiles when it's done.
     */
    private suspend fun runBackground(ctx: Context, vin: String, cmd: String) {
        val snap = runCatching {
            SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
        }.getOrNull()
        Toast.makeText(ctx, TileCommandRunner.ackText(cmd, snap), Toast.LENGTH_SHORT).show()
        val target = SettingsStore(ctx).tileClimateTarget(index)
        TileCommandWorker.enqueue(ctx, vin, cmd, target)
    }

    /** Open Bloo and let it run the command (reuses the shortcut routing). */
    private fun openApp(vin: String?, cmd: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (vin != null && cmd != null) {
                action = Shortcuts.ACTION
                putExtra(Shortcuts.EXTRA_VIN, vin)
                putExtra(Shortcuts.EXTRA_CMD, cmd)
            }
        }
        collapseAndStart(intent)
    }

    /** Open-and-close mode: a transparent activity runs the command then finishes. */
    private fun launchActionActivity(vin: String, cmd: String) {
        val intent = Intent(this, TileActionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TileActionActivity.EXTRA_VIN, vin)
            putExtra(TileActionActivity.EXTRA_CMD, cmd)
            putExtra(TileActionActivity.EXTRA_INDEX, index)
        }
        collapseAndStart(intent)
    }

    /**
     * Starts [intent] while collapsing the Quick Settings shade so the activity is actually
     * visible to the user instead of appearing behind it. API 34+ deprecated passing a raw
     * Intent to `startActivityAndCollapse` in favor of a PendingIntent (keyed by [index] so
     * each tile slot gets its own distinct PendingIntent, otherwise FLAG_UPDATE_CURRENT would
     * make them collide); older OS versions still use the deprecated raw-Intent overload.
     * If the device is currently locked ([isLocked], a TileService property), the launch is
     * deferred until after [unlockAndRun] gets the user through the lock screen -- firing an
     * activity intent while locked would otherwise silently fail.
     */
    private fun collapseAndStart(intent: Intent) {
        val run = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, index, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(run) else run()
    }

    companion object {
        /** Don't kick a live refresh for the same car more than once per minute. */
        private const val LIVE_REFRESH_THROTTLE_MS = 60_000L

        /** The pool of concrete tile services, indexed 0..TILE_COUNT-1. */
        private val TILE_CLASSES: List<Class<out BlooTileService>> = listOf(
            BlooTile1::class.java, BlooTile2::class.java,
            BlooTile3::class.java, BlooTile4::class.java,
            BlooTile5::class.java, BlooTile6::class.java,
            BlooTile7::class.java, BlooTile8::class.java,
            BlooTile9::class.java, BlooTile10::class.java,
            BlooTile11::class.java, BlooTile12::class.java,
        )

        /** The concrete tile service class backing pool slot [index] (0-based). */
        fun classFor(index: Int): Class<out BlooTileService>? = TILE_CLASSES.getOrNull(index)

        /**
         * The drawable shown in the QS shade for an action + lock state. Shared by
         * the live tile render and the in-app preview/add flow so they always match.
         */
        fun iconResFor(cmd: String, unlocked: Boolean): Int = when (cmd) {
            // Only claim "open padlock" when we actually know it's unlocked; a closed
            // padlock otherwise (locked, or state not yet synced).
            "doors" -> if (unlocked) R.drawable.ic_shortcut_unlock else R.drawable.ic_shortcut_lock
            "lock" -> R.drawable.ic_shortcut_lock
            "unlock" -> R.drawable.ic_shortcut_unlock
            "climate" -> R.drawable.ic_shortcut_climate
            "charge" -> R.drawable.ic_widget_bolt
            else -> R.drawable.ic_shortcut_car
        }

        /** Whether the system can prompt to add a tile straight to the shade (API 33+). */
        fun canRequestAdd(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /**
         * Ask the system to add the tile backing [index] directly to the user's
         * Quick Settings shade, previewing the given [label] and [iconRes] in the OS
         * dialog (so the tile's name/properties are shown before it's added). Returns
         * false when unavailable (older OS / no service) so the caller can fall back
         * to guidance. [onResult] receives a StatusBarManager.TILE_ADD_REQUEST_* code.
         */
        fun requestAddToQuickSettings(
            context: Context,
            index: Int,
            label: String,
            iconRes: Int,
            onResult: (Int) -> Unit = {},
        ): Boolean {
            if (!canRequestAdd()) return false
            val cls = classFor(index) ?: return false
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: return false
            return runCatching {
                sbm.requestAddTileService(
                    ComponentName(context, cls),
                    label,
                    Icon.createWithResource(context, iconRes),
                    context.mainExecutor,
                ) { result -> onResult(result) }
                true
            }.getOrDefault(false)
        }

        // requestUpdates() was deleted here, along with all thirteen of its call sites.
        //
        // It never worked. TileService.requestListeningState is documented in AOSP as
        // applying "only to tiles that have META_DATA_ACTIVE_TILE defined as true on their
        // TileService Manifest declaration, and will do nothing otherwise" -- and none of
        // Bloo's twelve declare it (every entry in AndroidManifest checked).
        //
        // It was previously left in place on the reasoning that it "costs nothing, is
        // harmless, and becomes correct the moment any tile is declared active". Both
        // halves of that turned out to be wrong. It is not free: each call looped all
        // twelve classes issuing a StatusBarManager binder transaction that the system
        // then discards, so thirteen call sites -- several on paths that run on every
        // state change -- meant a steady trickle of IPC doing nothing. And it can never
        // become correct, because declaring a tile ACTIVE is a change this app must not
        // make: AOSP says the system binds active tiles "only when a click needs to
        // occur", so they stop being refreshed when the shade opens. Bloo's process is
        // usually dead, so active tiles would show stale state to anyone who just pulls
        // down the shade -- strictly worse than today.
        //
        // What actually repaints a visible tile is the store observer in onStartListening,
        // which needs no cooperation from the system. See that method.
    }
}

// One trivial concrete subclass per pool slot: Android requires each Quick Settings tile to
// be backed by its own manifest-declared TileService class (a single service can't expose
// multiple independent tiles), so this is the boilerplate needed to offer 12 configurable
// tile slots via one shared implementation above.
class BlooTile1 : BlooTileService() { override val index = 0 }
class BlooTile2 : BlooTileService() { override val index = 1 }
class BlooTile3 : BlooTileService() { override val index = 2 }
class BlooTile4 : BlooTileService() { override val index = 3 }
class BlooTile5 : BlooTileService() { override val index = 4 }
class BlooTile6 : BlooTileService() { override val index = 5 }
class BlooTile7 : BlooTileService() { override val index = 6 }
class BlooTile8 : BlooTileService() { override val index = 7 }
class BlooTile9 : BlooTileService() { override val index = 8 }
class BlooTile10 : BlooTileService() { override val index = 9 }
class BlooTile11 : BlooTileService() { override val index = 10 }
class BlooTile12 : BlooTileService() { override val index = 11 }
