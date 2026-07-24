# app: Quick Settings tiles (service / activity / worker / runner)

Deep-dive reference for Bloo's Android **Quick Settings (QS) tile** feature: the
12 configurable QS tiles that let a user lock/unlock, toggle climate, toggle
charge, or open the app for a specific vehicle straight from the notification
shade.

Files covered:

- `app/src/main/java/com/bloo/bluelink/tiles/BlooTileService.kt` — the tile
  service (abstract base + 12 concrete subclasses + companion helpers).
- `app/src/main/java/com/bloo/bluelink/tiles/TileActionActivity.kt` — the
  transparent "open, send, close" activity used by open-and-close mode.
- `app/src/main/java/com/bloo/bluelink/tiles/TileCommandWorker.kt` — the
  WorkManager worker that outlives the short-lived tile service.
- `app/src/main/java/com/bloo/bluelink/data/TileCommandRunner.kt` — the shared
  command executor (locking, dispatch, optimistic snapshot update).

---

## 1. Purpose

Android QS tiles are backed by a `TileService`, which the OS **creates and
destroys aggressively** — a fresh instance every time the shade opens, torn down
when it closes. Two consequences drive the whole design of this unit:

1. **State must be re-read on demand.** There is no long-lived object holding the
   tile's assigned car/command; every render reads it from `SettingsStore` and
   the cached snapshot from `SnapshotStore`.
2. **Work must escape the service's lifecycle.** A network command started in the
   service's own coroutine scope would be cancelled the instant the service is
   destroyed after `onClick` — this was the literal cause of tile taps "doing
   nothing" (`TileCommandWorker.kt:13-20`). So the actual command is handed to
   **WorkManager**, which gives it a process-lifetime home.

Android also requires **one manifest-declared `TileService` class per tile**; a
single service cannot expose multiple independent tiles. To offer 12 configurable
slots from one implementation, the code declares 12 trivial subclasses
(`BlooTile1`..`BlooTile12`), each carrying only an `index`
(`BlooTileService.kt:301-316`).

The user configures each slot elsewhere (Settings UI): a **VIN + command**, an
optional custom **label**, and — for the climate command — a **climate target**.
Two global preferences shape behaviour: `tileBackground` (silent WorkManager run
vs. open-and-close activity) and `tileLiveRefresh` (throttled status pull on
render).

---

## 2. Public surface

### `BlooTileService.kt`

#### `abstract class BlooTileService : TileService()` (`:32`)

- **`abstract val index: Int`** (`:36`) — which of the 12 pool slots this concrete
  subclass backs. Used to look up per-tile config/label/climate-target in
  `SettingsStore`, and as the PendingIntent request code / `EXTRA_INDEX`.

- **`override fun onStartListening()`** (`:51-54`) — system callback fired when
  the tile becomes visible in the shade. Launches `render()` on `scope`. This is
  the only cue that `qsTile` is safe to read/mutate.

- **`override fun onClick()`** (`:142-155`) — system callback on tap. Re-reads
  config fresh from `SettingsStore(ctx).tileConfig(index)` on `scope`, then
  branches into exactly one of three outcomes (see §3).

The 12 concrete subclasses (`:305-316`), each `class BlooTileN : BlooTileService()
{ override val index = N-1 }`:

- `BlooTile1` (index 0) … `BlooTile12` (index 11).

#### `companion object` (`:221-298`)

- **`fun classFor(index: Int): Class<out BlooTileService>?`** (`:236`) — the
  concrete tile class backing pool slot `index`, or null if out of range
  (`TILE_CLASSES.getOrNull(index)`).

- **`fun iconResFor(cmd: String, unlocked: Boolean): Int`** (`:242-251`) — the
  drawable resource id shown for a command + lock state. Shared by the live tile
  render **and** the in-app preview/add flow so they always match. Mapping:
  - `"doors"` → `ic_shortcut_unlock` if `unlocked` else `ic_shortcut_lock`
  - `"lock"` → `ic_shortcut_lock`
  - `"unlock"` → `ic_shortcut_unlock`
  - `"climate"` → `ic_shortcut_climate`
  - `"charge"` → `ic_widget_bolt`
  - else → `ic_shortcut_car`

- **`fun canRequestAdd(): Boolean`** (`:254`) — whether the OS can prompt to add a
  tile directly (API 33 / TIRAMISU+).

- **`fun requestAddToQuickSettings(context, index, label, iconRes, onResult): Boolean`**
  (`:263-282`) — asks the system (`StatusBarManager.requestAddTileService`) to add
  the tile backing `index` to the shade, previewing `label` + `iconRes` in the OS
  dialog. Returns false when unavailable (older OS, no class, no
  `StatusBarManager`, or the call throws). `onResult` receives a
  `StatusBarManager.TILE_ADD_REQUEST_*` code (default no-op). Uses
  `context.mainExecutor` as the result callback executor.

- **`fun requestUpdates(context: Context)`** (`:293-297`) — asks the system to
  repaint all of Bloo's active tiles. Calls the inherited static
  `TileService.requestListeningState(...)` for each of the 12 classes; each is
  wrapped in `runCatching` because most tiles won't currently be added/visible and
  those failures are expected.

### `TileActionActivity.kt`

#### `class TileActionActivity : FragmentActivity()` (`:21`)

- **`override fun onCreate(savedInstanceState: Bundle?)`** (`:34-51`) — reads
  `EXTRA_VIN`/`EXTRA_CMD`/`EXTRA_INDEX`, acks with a toast, enqueues the command
  on WorkManager, then finishes. See §3 for control flow.

#### `companion object` (`:61-65`)

- **`const val EXTRA_VIN = "vin"`** (`:62`)
- **`const val EXTRA_CMD = "cmd"`** (`:63`)
- **`const val EXTRA_INDEX = "index"`** (`:64`)

### `TileCommandWorker.kt`

#### `class TileCommandWorker(ctx, params) : CoroutineWorker(ctx, params)` (`:21`)

- **`override suspend fun doWork(): Result`** (`:23-37`) — the worker body (see §3).

#### `companion object` (`:39-54`)

- **`const val KEY_VIN = "vin"`** (`:40`)
- **`const val KEY_CMD = "cmd"`** (`:41`)
- **`const val KEY_TARGET = "target"`** (`:42`)
- **`const val CMD_REFRESH = "__refresh"`** (`:43`) — sentinel command meaning "do a
  status refresh, not a vehicle command".
- **`fun enqueue(ctx, vin, cmd, target)`** (`:45-50`) — builds a
  `OneTimeWorkRequest` with `KEY_VIN/KEY_CMD/KEY_TARGET` input data and enqueues it
  (no constraints, no unique-work policy).
- **`fun enqueueRefresh(ctx, vin)`** (`:53`) — convenience: `enqueue(ctx, vin,
  CMD_REFRESH, "default")`.

### `TileCommandRunner.kt`

#### `object TileCommandRunner` (`:14`)

- **`data class Result(val ok: Boolean, val message: String)`** (`:18`) — outcome of
  a single tile command: success flag + a short human-readable status/error
  message for a toast or log line.

- **`suspend fun run(ctx, vin, cmd, climateTarget): Result`** (`:77-117`) — the
  single end-to-end command executor. Shared by background mode
  (`TileCommandWorker`) and open-and-close mode (via the worker too). See §3 for
  the full order of operations.

- **`fun ackText(cmd: String, snap: VehicleSnapshot?): String`** (`:200-207`) — the
  short "doing it" toast text shown immediately on tap, computed from the same
  toggle-against-last-known-snapshot logic `run` uses so the toast direction and
  the dispatched command agree. Mapping:
  - `"doors"` → `"Unlocking…"` if `snap?.locked == true` else `"Locking…"`
  - `"lock"` → `"Locking…"`; `"unlock"` → `"Unlocking…"`
  - `"climate"` → `"Stopping climate…"` if `climateOn == true` else `"Starting climate…"`
  - `"charge"` → `"Stopping charge…"` if `charging == true` else `"Starting charge…"`
  - else → `"Sending…"`
  A null `snap` defaults every toggle to its "starting" phrasing (via `== true`
  short-circuiting to false).

- **`fun optimistic(snap: VehicleSnapshot, cmd: String): VehicleSnapshot`**
  (`:214-224`) — the snapshot a tile command is expected to produce, for instant
  feedback. Maps the tile vocabulary onto `WearAction` string constants and
  delegates to `WearCommandRunner.optimistic(snap, action)` rather than
  re-deriving the flips (it used to be a byte-for-byte duplicate). Mapping:
  `"doors"→TOGGLE_LOCK`, `"lock"→LOCK`, `"unlock"→UNLOCK`, `"charge"→TOGGLE_CHARGE`,
  `"climate"→TOGGLE_CLIMATE`; anything else returns `snap` unchanged.

Private members are listed in §3.

---

## 3. Internal structure & control flow

### `BlooTileService.render()` (private suspend, `:64-90`)

1. `val tile = qsTile ?: return` — bail if the OS hasn't given us a tile yet.
2. Read `SettingsStore(ctx).tileConfig(index)` wrapped in `runCatching`
   (`:67`). If null (unassigned, or store read failed):
   - `state = STATE_INACTIVE`, `label = tileLabel(index)` or `"Bloo tile N"`,
     `icon = ic_shortcut_car`, and on API 29+ `subtitle = "Unassigned"`; push with
     `updateTile()` and return (`:68-75`).
3. Destructure `(vin, cmd)` from config (`:76`).
4. Read the matching cached snapshot: `SnapshotStore(ctx).current().vehicles
   .firstOrNull { it.vin == vin }` (runCatching) (`:77-79`), and the custom label
   (`:80`).
5. Paint: `state = STATE_ACTIVE` iff `isActiveState(cmd, snap)` else
   `STATE_INACTIVE`; `icon = iconFor(cmd, snap)`; `label = custom ?:
   defaultLabel(cmd, snap)`; on API 29+ `subtitle = snap?.name ?: "Car"`; then
   `updateTile()` (`:82-86`).
6. `maybeLiveRefresh(ctx, vin)` (`:89`).

Every store read is `runCatching`-wrapped so a transient failure degrades to a
sane fallback rather than a half-drawn tile or a host-process crash.

### `BlooTileService.maybeLiveRefresh(ctx, vin)` (private suspend, `:93-100`)

- Return early if `SettingsStore.tileLiveRefresh()` is off.
- Throttle: if `now - tileRefreshedAt(vin) < LIVE_REFRESH_THROTTLE_MS`
  (60,000 ms) return.
- Otherwise record `setTileRefreshedAt(vin, now)` and
  `TileCommandWorker.enqueueRefresh(ctx, vin)`.

### `BlooTileService.isActiveState(cmd, snap)` (private, `:107-113`)

Whether the tile reads "on" (filled/white). `"doors"/"lock"` → `locked == false`
(unlocked is the noteworthy state); `"unlock"` → `locked == true`; `"climate"` →
`climateOn == true`; `"charge"` → `charging == true`; else false.

### `BlooTileService.iconFor(cmd, snap)` (private, `:115-116`)

Delegates to `iconResFor(cmd, unlocked = snap?.locked == false)`.

### `BlooTileService.defaultLabel(cmd, snap)` (private, `:118-131`)

Derives a label when the user set no custom one: `"doors"` → `"Locked"` /
`"Unlocked"` / `"Lock / unlock"` (for locked true / false / unknown-null);
`"lock"`→`"Lock"`, `"unlock"`→`"Unlock"`; `"climate"` → `"Climate on"` if on else
`"Climate"`; `"charge"` → `"Charging"` if charging else `"Charge"`; `"open"` →
`"Open"`; else the command with its first char upper-cased.

### `BlooTileService.onClick()` branch logic (`:142-155`)

Launched on `scope`; re-reads `tileConfig(index)`:
- null config → `openApp(null, null)` (just opens the app).
- `cmd == "open"` → `openApp(vin, cmd)`.
- else if `SettingsStore(ctx).tileBackground()` → `runBackground(ctx, vin, cmd)`.
- else → `launchActionActivity(vin, cmd)`.

### `BlooTileService.runBackground(ctx, vin, cmd)` (private suspend, `:162-169`)

Background/silent mode. Reads the snapshot (for toast wording), shows
`Toast(TileCommandRunner.ackText(cmd, snap))`, reads `tileClimateTarget(index)`,
then `TileCommandWorker.enqueue(ctx, vin, cmd, target)`. The worker survives the
service's teardown and refreshes tiles when done.

### `BlooTileService.openApp(vin?, cmd?)` (private, `:172-182`)

Builds a `MainActivity` intent with `FLAG_ACTIVITY_NEW_TASK |
FLAG_ACTIVITY_CLEAR_TOP`; if both `vin` and `cmd` are non-null, sets
`action = Shortcuts.ACTION` and `EXTRA_VIN`/`EXTRA_CMD` extras (reusing the
app-shortcut routing). Then `collapseAndStart(intent)`.

### `BlooTileService.launchActionActivity(vin, cmd)` (private, `:185-193`)

Open-and-close mode. Builds a `TileActionActivity` intent
(`FLAG_ACTIVITY_NEW_TASK`) with `EXTRA_VIN`/`EXTRA_CMD`/`EXTRA_INDEX`, then
`collapseAndStart(intent)`.

### `BlooTileService.collapseAndStart(intent)` (private, `:205-219`)

Defines a `run` lambda that collapses the shade while starting the activity:
- API 34 (UPSIDE_DOWN_CAKE)+: wraps `intent` in a `PendingIntent.getActivity`
  keyed by `index` (request code) with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`,
  then `startActivityAndCollapse(pi)`. The per-tile request code prevents distinct
  tiles from colliding under `FLAG_UPDATE_CURRENT`.
- older: the deprecated `startActivityAndCollapse(intent)` raw-Intent overload.

Then: if `isLocked` (a `TileService` property) → `unlockAndRun(run)` (defers the
launch until the user clears the lock screen); else `run()` immediately. Firing an
activity intent while the device is locked would otherwise silently fail.

### `TileActionActivity.onCreate` flow (`:34-51`)

1. Read `EXTRA_VIN`/`EXTRA_CMD`; if either null → `finishNoAnim()` and return
   (`:36-38`) — launched without a valid config.
2. Capture `applicationContext` and `EXTRA_INDEX` (default 0) (`:39-40`).
3. On `lifecycleScope`: on `Dispatchers.IO` read the snapshot (for toast wording)
   and `tileClimateTarget(index)`; show `Toast(ackText(cmd, snap))` on the main
   thread; `TileCommandWorker.enqueue(appCtx, vin, cmd, target)`; then
   `finishNoAnim()` (`:42-50`).

Because the command is **enqueued** (not run inline) before finishing, it keeps
running though the activity is destroyed the moment `finishNoAnim()` returns.

### `TileActionActivity.finishNoAnim()` (private, `:55-59`)

`finishAndRemoveTask()` + `overridePendingTransition(0, 0)` so this invisible
activity never flashes on top of what the user was viewing.

### `TileCommandWorker.doWork()` flow (`:23-37`)

1. Read `KEY_VIN` / `KEY_CMD` from `inputData`; either missing → `Result.failure()`
   (`:24-25`).
2. If `cmd == CMD_REFRESH` (`:26-30`): `runCatching { WearCommandRunner
   .refresh(applicationContext, vin); AppLog.log("Tile refresh for $vin") }`,
   logging a `⚠` line on failure. (`refresh` defaults `force = true`, so this is a
   live pull that wakes the car.)
3. Else (`:31-34`): read `KEY_TARGET` (default `"default"`), then
   `TileCommandRunner.run(applicationContext, vin, cmd, target)`. (Note: the
   worker ignores `run`'s returned `Result` — feedback already went out as the tap
   toast; `run` itself logs success/failure.)
4. Always `runCatching { BlooTileService.requestUpdates(applicationContext) }`
   (`:35`) so visible tiles repaint with the new state.
5. Return `Result.success()` (`:36`).

### `TileCommandRunner.run` order of operations (`:77-117`)

1. Look up `SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }`;
   null → `Result(false, "Car not found")` before any network work (`:78-79`).
2. `val v = snap.toVehicle()` (`:80`).
3. Build a **fresh** repo per call:
   `repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(ctx),
   CredentialStore(ctx))` (`:81`) — not cached, so it always uses the latest
   signed-in session.
4. Enter `BlueLinkGate.statusMutex.withLock { runCatching { when(cmd) {…} } }`
   (`:87-101`):
   - `"doors"` → toggle off `snap.locked == true`: `unlock`/`lock` (`:90-92`).
   - `"lock"` → `repo.lock(v)`; `"unlock"` → `repo.unlock(v)` (`:93-94`).
   - `"charge"` → toggle off `snap.charging == true`: `stopCharge`/`startCharge`
     (`:95-97`).
   - `"climate"` → `runClimate(ctx, repo, v, snap, climateTarget)` (`:98`).
   - else → `"Done"` (`:99`).
5. `.fold` (`:102-116`), **outside** the lock:
   - success → `AppLog.log(msg)`, best-effort `runCatching { SnapshotStore(ctx)
     .updateVehicle(optimistic(snap, cmd)) }`, `Result(true, msg)` (`:103-110`).
   - failure → `err = e.message ?: "Command failed"`, `AppLog.log("⚠ $err (${cmd} →
     ${v.name})")`, `Result(false, err)` (`:111-115`).

### `TileCommandRunner.runClimate` (private suspend, `:152-188`)

1. If `snap.climateOn == true` → `repo.stopClimate(v)`; return `"Stopping climate"`
   (target ignored) (`:159`).
2. If `snap.isDriving` → `error("Can't start climate while driving")` (`:166`) —
   the car rejects remote climate-start while moving; mirrors the phone UI's
   `AppViewModel.isDriving()` gate.
3. Resolve the request (`:167-185`):
   - `"smart"`: needs `snap.lat`/`snap.lon` (else `error("No location for smart
     climate")`), fetches `WeatherApi.fetch(lat, lon)` (else `error("No weather for
     smart climate")`), builds `ClimateRequest(tempF =
     smartClimateTargetF(ambientFahrenheit(w.tempC)), defrost = false,
     durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)`.
   - any non-`"default"` id: looks up
     `SettingsStore(ctx).climatePresets(v.vin).firstOrNull { it.id == target }` and
     uses `preset.request`; else `error("Preset unavailable")`.
   - `"default"` / fallthrough: `ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F,
     defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)`.
4. `repo.startClimate(v, req)`; return `"Starting climate"` (`:186-187`).

---

## 4. Data & types

Types **defined** in this unit:

- **`TileCommandRunner.Result`** (`TileCommandRunner.kt:18`): `data class Result(val
  ok: Boolean, val message: String)`. `ok` = command succeeded; `message` =
  human-readable status (success wording like `"Locking Foo"`) or error text.

There are **no enums or sealed types** defined in this unit. Commands are plain
**string tokens**, not an enum. The closed vocabulary observed across the unit:

| token | meaning | render icon | active-when |
|-------|---------|-------------|-------------|
| `"doors"` | toggle lock/unlock | unlock if unlocked else lock | `locked == false` |
| `"lock"` | force lock | lock | `locked == false` |
| `"unlock"` | force unlock | unlock | `locked == true` |
| `"climate"` | toggle climate (honours target) | climate | `climateOn == true` |
| `"charge"` | toggle charge | bolt | `charging == true` |
| `"open"` | open the app (no command) | car | (never active) |

The **climate target** string (`SettingsStore.tileClimateTarget`, default
`"default"`): `"default"` = fixed default request; `"smart"` = weather-computed;
any other value = a preset id looked up in `climatePresets(vin)`.

`CMD_REFRESH = "__refresh"` (`TileCommandWorker.kt:43`) is an internal sentinel
that is **not** a tile command — the worker routes it to
`WearCommandRunner.refresh` instead of `TileCommandRunner.run`.

Persistence keys (in `SettingsStore`, per index/vin): `tile_{index}_vin`,
`tile_{index}_cmd`, `tile_{index}_label`, `tile_{index}_climate` (all
`stringPreferencesKey`); globals `tile_background`, `tile_live_refresh`
(`booleanPreferencesKey`, default false); throttle `tile_refreshed_{vin}` (string
epoch-ms). A tile is either fully configured (both vin+cmd non-blank) or
unassigned — `tileConfig` returns null if either key is blank
(`SettingsStore.kt:721-736`).

WorkManager input `Data` keys: `KEY_VIN`/`KEY_CMD`/`KEY_TARGET` = `"vin"`/`"cmd"`/
`"target"`.

Intent extras: `TileActionActivity.EXTRA_VIN/EXTRA_CMD/EXTRA_INDEX` =
`"vin"`/`"cmd"`/`"index"`; `openApp` uses `Shortcuts.ACTION` +
`Shortcuts.EXTRA_VIN`/`EXTRA_CMD`.

---

## 5. State & concurrency

- **`BlooTileService.scope`** (`:41`): `CoroutineScope(SupervisorJob() +
  Dispatchers.Main)`, tied to *this service instance's* lifetime (not a
  singleton), because the OS recreates the service per shade open. `SupervisorJob`
  keeps one failed child from cancelling siblings. `render` and the `onClick`
  handler both `scope.launch`. **Not cancelled explicitly** in this file
  (no `onDestroy` override) — see gotchas.
- Stores (`SettingsStore`, `SnapshotStore`) are DataStore-backed; their suspend
  reads (`.data.first()`) run within the launched coroutine. `render` runs on the
  Main dispatcher but the store reads suspend rather than block the thread.
- **`TileActionActivity`** uses `lifecycleScope` and explicitly hops to
  `Dispatchers.IO` for the two store reads, hopping back to main for the toast.
- **`TileCommandWorker`** is a `CoroutineWorker`; `doWork` runs on WorkManager's
  default dispatcher and outlives any UI component. This is the durable execution
  home.
- **`BlueLinkGate.statusMutex`** (`TileCommandRunner.kt:87`): the process-wide
  `Mutex` serializing *all* status/command traffic for an account.
  `run` takes it via `withLock` around only the command dispatch; the mutex is
  released before the `.fold` post-processing (logging + optimistic write) runs.
  `runClimate` is called *inside* that critical section and must **not** re-take
  the lock (it doesn't) — re-entrancy would deadlock (`Mutex` is non-reentrant).
- **Recomposition/repaint**: tiles are not Compose. State is pushed imperatively
  via `Tile.updateTile()` in `render`; repaints are triggered by the OS
  re-delivering `onStartListening` after `requestListeningState`
  (`requestUpdates`).
- Optimistic snapshot writes go through `SnapshotStore.updateVehicle`
  (DataStore), which is what widgets and the next `render` will observe.

---

## 6. Collaborators & data flow

Calls **out**:

- `SettingsStore` (`app/.../data/SettingsStore.kt`): `tileConfig`, `tileLabel`,
  `tileClimateTarget`, `tileBackground`, `tileLiveRefresh`, `tileRefreshedAt`,
  `setTileRefreshedAt`, `climatePresets`.
- `SnapshotStore`: `.current().vehicles` (read), `.updateVehicle(...)` (optimistic
  write).
- `TileCommandRunner.run` / `.ackText` / `.optimistic`.
- `TileCommandWorker.enqueue` / `.enqueueRefresh`.
- `WearCommandRunner.refresh(ctx, vin)` (shared, `shared/.../data/WearCommandRunner.kt:147`)
  and `WearCommandRunner.optimistic(snap, action)` (`:128`) — the latter via
  `WearAction` string consts (`shared/.../data/WearSync.kt:243`).
- `VehicleRepository` (per-brand, via `repositoryFor` + `Brand.fromIndicator`):
  `lock`, `unlock`, `startCharge`, `stopCharge`, `startClimate`, `stopClimate`.
- `SessionStore`, `CredentialStore` (per-brand session/creds for the repo).
- `WeatherApi.fetch`, `smartClimateTargetF`, `ambientFahrenheit`,
  `ClimateRequest`, `DEFAULT_CLIMATE_TEMP_F`, `DEFAULT_CLIMATE_DURATION_MIN`.
- `AppLog.log`.
- `MainActivity` + `Shortcuts` (open-app routing).
- Android: `TileService`/`Tile`, `StatusBarManager`, `PendingIntent`, `Toast`,
  `Icon`, `WorkManager`.

Called **by**:

- The OS (tile lifecycle: `onStartListening`, `onClick`).
- `TileCommandWorker.doWork` → `TileCommandRunner.run` and (for refresh) →
  `WearCommandRunner.refresh`, then → `BlooTileService.requestUpdates`.
- `TileActionActivity` and `BlooTileService.runBackground` → `TileCommandWorker.enqueue`.
- The in-app Settings UI (not in this unit) → `iconResFor`, `canRequestAdd`,
  `requestAddToQuickSettings`, and `classFor` for the add-tile / preview flow, and
  presumably `requestUpdates` after config changes.

Data channels: **DataStore** (SettingsStore/SnapshotStore), **WorkManager**
(input `Data`), **Intents** (extras to MainActivity / TileActionActivity),
**PendingIntent** (collapse-and-start on API 34+), **live telematics** (via
VehicleRepository behind `statusMutex`), **process-wide `Mutex`**.

Data **in**: tile config (vin/cmd/label/climate target) from settings, cached
`VehicleSnapshot` from SnapshotStore, tap events from the OS. Data **out**:
`Tile` visuals to the shade, toasts, an updated optimistic snapshot, live commands
to the car backend, and repaint requests to other tiles.

---

## 7. Invariants & assumptions

- **`qsTile` is only valid between `onStartListening` and `onStopListening`.**
  `render` guards with `qsTile ?: return`; `onClick` never touches `qsTile`.
- **`statusMutex` must be held while dispatching any command.** `run` is the *only*
  tile path to the backend, and it always takes the lock. `runClimate` assumes the
  lock is already held and must not re-take it (non-reentrant mutex).
- **A tile is never half-configured**: `tileConfig` returns null unless both
  `vin` and `cmd` are non-blank (`SettingsStore.kt:723-724`).
- **`snap.toVehicle()` / `v.brandIndicator` are valid** for a cached snapshot; a
  missing snapshot short-circuits to "Car not found" before any repo build.
- **PendingIntent request codes are unique per tile** — `index` is the request
  code, so `FLAG_UPDATE_CURRENT` won't collide across the 12 tiles.
- Toggle direction is derived from the **last-known** snapshot, not a fresh fetch;
  `ackText`, `run`, and `optimistic` all use the same rule so toast, command, and
  optimistic write agree.
- `tileBackground`/`tileLiveRefresh` default **false**; `tileClimateTarget`
  defaults **"default"**; `tileRefreshedAt` defaults **0** (so first render always
  passes the throttle if live refresh is on).
- The command vocabulary is a small closed set; `run`'s `else → "Done"` and
  `optimistic`'s `else → snap` are defensive no-ops.

---

## 8. Gotchas & sharp edges

- **The whole reason WorkManager exists here**: a `TileService` is destroyed almost
  immediately after `onClick`, cancelling any coroutine in its scope before the
  network call finishes — that was the "tile taps do nothing" bug
  (`TileCommandWorker.kt:13-20`, `BlooTileService.kt:157-161`). Never run the
  command inline in the service or the action activity; always enqueue.
- **Tile taps used to skip the mutex.** Before `run` took `statusMutex`, a tile tap
  racing a background status poll or another in-flight command could trigger
  BlueLink's 502-on-overlap. This was the last unprotected command path
  (`TileCommandRunner.kt:82-87`).
- **Climate-while-driving used to silently fail.** The car backend rejects remote
  climate-start while moving and returned no explanation; `runClimate` now throws
  `"Can't start climate while driving"` up front so the user sees a real message
  (`TileCommandRunner.kt:159-166`).
- **The mutex releases before logging + optimistic write.** `.fold` runs outside
  `withLock`, so those side-effects are deliberately outside the critical section.
- **Optimistic write is best-effort.** If `SnapshotStore.updateVehicle` fails, the
  command still counts as succeeded; the next real status poll corrects the
  snapshot (`TileCommandRunner.kt:106-108`).
- **The worker discards `run`'s `Result`.** No error toast surfaces from the
  background path after the initial ack; failures only reach `AppLog`. Feedback is
  the tap-time ack toast plus the eventual tile repaint.
- **`scope` is never explicitly cancelled** in `BlooTileService` (no `onDestroy`
  override) — it relies on the launched coroutines being short (a config read plus
  a fast handoff) and on the process/GC reclaiming the instance. The durable work
  lives in WorkManager, so a cancelled `scope` coroutine doesn't lose the command
  (the enqueue happens synchronously enough within `runBackground`, but note: if
  the service is torn down mid-`onClick` *before* `enqueue`, the tap is lost —
  which is exactly why `enqueue` is the very last thing before the coroutine ends).
- **API-level splits**: subtitle only set on API 29+ (Q); `startActivityAndCollapse`
  uses a `PendingIntent` on API 34+ (UPSIDE_DOWN_CAKE) and the deprecated raw
  Intent below; `requestAddToQuickSettings`/`canRequestAdd` require API 33+
  (TIRAMISU).
- **Locked-device deferral**: `collapseAndStart` defers the launch through
  `unlockAndRun` when `isLocked`, because starting an activity while locked
  silently fails. This applies to open-and-close and open-app modes (which start
  activities), not background mode (which just enqueues).
- **12 hard-coded subclasses.** The tile count is fixed by `TILE_CLASSES` /
  `BlooTile1..12` and each must have a matching `<service>` manifest entry; adding
  slots means editing both. `classFor`/`requestUpdates` iterate this list.
- **`iconResFor` is shared with the in-app preview** so the OS add-dialog preview
  and the live tile can never diverge — change icons in one place.
- **`enqueue` uses no unique-work policy**, so rapid repeated taps enqueue multiple
  workers; each independently takes `statusMutex`, so they serialize rather than
  overlap, but they do *each* run (potentially issuing duplicate commands).
- **`CMD_REFRESH` uses a live pull.** `WearCommandRunner.refresh` defaults
  `force = true`, so the live-refresh path (and any `enqueueRefresh`) wakes the car
  rather than reading the server's cached status — hence the 60s throttle in
  `maybeLiveRefresh` to limit 12V battery drain / rate-limit cost.
