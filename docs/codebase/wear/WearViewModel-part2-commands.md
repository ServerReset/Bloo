# WearViewModel — Part 2: Commands, Climate, Sync & Update (lines 950–1856)

File: `C:\Users\AdiPerets\Bloo\wear\src\main\java\com\bloo\wear\WearViewModel.kt`

This document covers the **command dispatch, climate/preset/charge-limit logic, smart climate, the `toWearCommand` builder, the `command` relay-vs-standalone dispatcher, the optimistic `flip`, `mark` pending bookkeeping, `publish`, and `buildCarView` merge** — i.e. the second half of `WearViewModel`, roughly lines 950–1856. It assumes Part 1 (init/collectors/bootstrap/sign-in/loading/PIN/settings) is documented separately, but references those pieces where they interact.

---

## 1. Purpose

`WearViewModel` is the **single** ViewModel of the Wear OS app (`WearViewModel.kt:276`). Everything the watch UI renders flows through one `MutableStateFlow<WearUi>` (`_ui`, `WearViewModel.kt:337-338`). This second half is the part that **executes user commands on a car** and **keeps climate/charge draft state in sync with the phone**, plus the app self-update flow and the `WearUi` projection builder.

The defining architectural quirk (documented at `WearViewModel.kt:244-275`) is that almost every command runs one of two ways:

- **Relayed**: serialize the command into a `WearCommand` and send it to the phone over the Wearable Data Layer (`WearComms.send`). The phone owns the real BlueLink/Kia session and talks to the car. The watch gets **no ack** that the phone's own API call succeeded — only that the *send* succeeded. Async failures arrive out-of-band via `WearCommandEvents.results` (wired in `init`, `WearViewModel.kt:435-450`) and trigger a corrective refresh, not a revert.
- **Standalone**: when no phone is reachable, the watch has its own signed-in BlueLink session (`repoFor`, `WearViewModel.kt:344-345`) and calls the vehicle API directly.

Both paths apply the **same optimistic local-state update** before any network confirmation: the relay path patches `snapshots`/`statuses` via `WearCommandRunner.optimistic` inside `command`; the standalone path patches `statuses` via `flip` after its suspend block returns successfully. Either way `publish()` fires immediately so the UI reacts instantly.

All command logic funnels through the private `command` helper (`WearViewModel.kt:1602`).

---

## 2. Public surface (in this line range)

### Command / action functions

- **`applyPreset(vin: String, preset: ClimatePreset)`** — `WearViewModel.kt:908`. Applies a saved climate preset: seeds sliders + active highlight up front via `updateDraft` (so the relay path, which never runs the block, still shows the preset as active), then dispatches through `command` with an **explicit** `CLIMATE_ON` `WearCommand` carrying the preset's full settings. `onFailure` restores the captured `previousDraft`. Standalone block enforces the driving gate then calls `repo.startClimate(v, preset.request)` and `flip`s `airCtrlOn = true`.

- **`ensurePlaceName(vin: String, lat: Double, lon: Double)`** — `WearViewModel.kt:970`. Reverse-geocode a car's coords to a place name, once per car per session. Guards on `geocoded`/`placeNames` membership and `Geocoder.isPresent()`. On any non-success outcome it **releases** the `geocoded` guard (so a thrown geocoder hiccup doesn't permanently disable geocoding for that VIN). On success writes `placeNames` and calls `publish()`.

- **`applyChargeLimits(vin: String)`** — `WearViewModel.kt:1030`. Pushes AC/DC charge-limit sliders to the car. Computes `ac`/`dc` from **what LimitsCard displays** (this car's draft, else its actual current limit `car?.acLimit`/`dcLimit`, else `80`/`90`). Clears the draft optimistically **before** the network call; `onFailure` restores `previousDraft`. Dispatches via `command` with explicit `SET_CHARGE_LIMITS`; standalone block calls `repo.setChargeTargets(v, ac, dc)`.

- **`saveCurrentAsPreset(vin: String, name: String)`** — `WearViewModel.kt:1107`. Builds a `ClimatePreset` (random UUID id, trimmed name defaulting to `"Preset"`, `request` from the draft) and appends it, unless an identical `request` already exists (dedup). Updates `ui.presets` then `persistAndPublishPresets`.

- **`deletePreset(vin: String, id: String)`** — `WearViewModel.kt:1126`. Removes the preset by id (no-op filter if absent), updates `ui.presets`, then `persistAndPublishPresets`.

- **Climate slider/toggle setters** (`WearViewModel.kt:1171-1180`), all funnel through `updateDraft`/`updateChargeDraft`, clamp, and clear `activePresetId` (except the charge ones):
  - `setClimateTemp(vin, value)` — clamps to `62..82`.
  - `setClimateDuration(vin, value)` — clamps to `1..10`.
  - `toggleDefrost(vin)`, `toggleSteering(vin)`.
  - `setSeatDriver/Passenger/RearLeft/RearRight(vin, step)` — each clamps to `0..3`.
  - `setAcLimit(vin, value)`, `setDcLimit(vin, value)` — clamp to `CHARGE_LIMIT_RANGE`; use `updateChargeDraft` (no phone push, no `activePresetId` clear).

- **`dismissMessage()`** — `WearViewModel.kt:1189`. Clears `ui.message`.

### App self-update

- **`snoozeUpdate()`** — `WearViewModel.kt:1195`. Clears `ui.updateRun` and persists a snooze `now + UPDATE_SNOOZE_MS`.
- **`currentBuildNumber: Int`** (property) — `WearViewModel.kt:1227`. Returns `BuildConfig.BUILD_RUN_NUMBER`.
- **`downloadAndInstallUpdate()`** — `WearViewModel.kt:1234`. Downloads `Bloo-Wear.apk` to `cacheDir/apk/` and hands it to the system installer via a `FileProvider` URI + `ACTION_VIEW` intent. If the run has no `wearApkUrl`, opens the release page on the phone instead (`WearRemote.openOnPhone`). Guards re-entry via `ui.updateDownloading`.

### Local settings (font/units) — in this range

- **`setFontScale(scale: Float)`** — `WearViewModel.kt:1271`. Clamps to `0.8f..1.4f`, persists, re-reads `localStore`, pushes the whole local-settings bundle to the phone.
- **`setUnitSystem(value: String)`** — `WearViewModel.kt:1281`. Persists, re-reads, pushes bundle.

### Smart climate

- **`smartClimate(vin: String)`** — `WearViewModel.kt:1498`. See §3 for the full flow. Reads weather (car weather, else home weather; message + return if neither), computes `targetF` via shared `smartClimateTargetF`, decides on/off **here** using the same `statuses`-then-`snapshots` priority as `buildCarView`, and dispatches an explicit `CLIMATE_OFF` or `CLIMATE_ON` command.

> Note: `toggleLock`, `flashLights`, `hornAndLights`, `toggleClimate`, `toggleCharge` are defined just before line 950 (`WearViewModel.kt:838-904`) and also funnel through `command`; they belong to the same command family and are documented in §6 for context.

---

## 3. Internal structure & control flow

### `command(...)` — the shared dispatcher (`WearViewModel.kt:1602-1664`)

Signature:
```kotlin
private fun command(
    vin: String,
    action: String,
    explicit: WearCommand? = null,
    successMessage: String? = null,
    onFailure: (() -> Unit)? = null,
    block: suspend (Vehicle, VehicleRepository, VehicleStatus?) -> Unit,
)
```

Step by step:

1. `val v = vehicles.firstOrNull { it.vin == vin } ?: return` (`:1615`) — bail if the VIN isn't in the garage.
2. `mark("$vin:$action") { ... }` (`:1616`) — wraps the whole thing in the pending refcount + `publish` bracket (see §5).
3. Build `wearCommand = explicit ?: toWearCommand(vin, action)` (`:1617`).
4. `val relayed = runCatching { WearComms.send(ctx, wearCommand) }.isSuccess` (`:1618`).
5. **Relay branch** (`:1619-1646`): if the send succeeded,
   - Log `"Watch: $action relayed to phone"`.
   - If a snapshot exists for the VIN, compute `newSnap = WearCommandRunner.optimistic(currentSnap, wearCommand.action)`, write it into `snapshots`.
   - Then fold the inferred new state into `statuses[vin]` too (`:1625-1641`), via a `when` on `wearCommand.action`:
     - `TOGGLE_LOCK`/`LOCK`/`UNLOCK` → `s.copy(doorLock = newSnap.locked)`.
     - `TOGGLE_CLIMATE`/`CLIMATE_ON`/`CLIMATE_OFF` → `s.copy(airCtrlOn = newSnap.climateOn)`.
     - `TOGGLE_CHARGE`/`CHARGE_ON`/`CHARGE_OFF` → `s.copy(evStatus = (s.evStatus ?: EvStatus()).copy(batteryCharge = newSnap.charging ?: false))`.
     - `else -> s` (no state change; e.g. FLASH_LIGHTS, HORN_AND_LIGHTS, SET_CHARGE_LIMITS, REFRESH).
   - `publish()`, show `successMessage` if set, `sessionFetched.remove(vin)` (force real re-fetch on next `onCarShown`), `requestWidgetUpdates()`.
6. **Standalone branch** (`:1647-1662`): if the send failed,
   - `runCatching { BlueLinkGate.statusMutex.withLock { block(v, repoFor(v.brand), statuses[vin]) } }` — the block is serialized process-wide against the status mutex and receives the vehicle, its brand's repository, and the last-known status.
   - `onSuccess`: log, `publish()`, `successMessage`, `sessionFetched.remove(vin)`, `refreshStatus(vin, surface = false)` (pull the real post-command state), `requestWidgetUpdates()`.
   - `onFailure`: log warning, set `ui.message = e.message ?: "Command failed"`, invoke `onFailure?.invoke()` (caller's rollback).

Key asymmetry: `onFailure` **only** fires on the standalone failure branch — the relay branch has no ack channel (documented at `:1607-1612`).

### `toWearCommand(vin, action)` (`WearViewModel.kt:1678-1700`)

Used only when the caller didn't pass `explicit`. Maps the three generic action strings to their TOGGLE_* actions; anything else → `REFRESH`:
- `"doors" -> TOGGLE_LOCK`
- `"climate" -> TOGGLE_CLIMATE`
- `"charge" -> TOGGLE_CHARGE`
- `else -> REFRESH`

Crucially it **always carries the full current `ClimateDraft`** (temp, duration, defrost, steering, all four seats via `seatLevelOf(...).apiValue`) on *every* command regardless of action — so a relayed climate toggle that turns climate ON gives the phone complete settings instead of wire-protocol defaults (`:1679-1681` comment). This is exactly why callers like `flashLights`/`applyChargeLimits` must pass `explicit`: a bare unrecognized string would silently relay a `REFRESH`.

### `flip(vin, change)` (`WearViewModel.kt:1712-1715`)

The standalone path's optimistic-update primitive. `val cur = statuses[vin] ?: VehicleStatus()`, then `statuses = statuses + (vin to change(cur))`. Called by each command block **after** its repository call returns successfully. Does **not** call `publish()` itself — it relies on `command`'s `publish()` right after the block returns.

### `mark(key, block)` (`WearViewModel.kt:1738-1748`)

Runs `block` fire-and-forget in `viewModelScope` while marking `key` pending. Uses a **reference count** in `pendingCounts` (not a plain Set):
- On entry: `pendingCounts = pendingCounts + (key to (pendingCounts[key] ?: 0) + 1)`, then `publish()`.
- Launch coroutine; `try { block() } finally { ... decrement ... publish() }`.
- Decrement: `remaining = (pendingCounts[key] ?: 1) - 1`; if `<= 0` remove key else write back.

The refcount fixes a bug where two overlapping same-key calls collapsed to one Set entry and the first completion cleared it while the second was still running (see the `pendingCounts` field comment, `WearViewModel.kt:317-324`).

### `publish(screen)` (`WearViewModel.kt:1761-1770`)

The **only** place `WearUi.cars`/`trips`/`pending` are updated. Rebuilds from the `@Volatile` source-of-truth fields:
```kotlin
cur.copy(
    screen = screen ?: cur.screen,
    cars = vehicles.map { buildCarView(it) },
    trips = trips,
    pending = pending,
)
```
Every mutation of the underlying fields elsewhere must call this (directly or via `mark`/`command`). `screen = null` leaves the screen untouched.

### `buildCarView(v)` (`WearViewModel.kt:1787-1855`)

Merges `statuses[vin]` (rich live) with `snapshots[vin]` (phone lightweight) into one `CarView`. General rule per field: `s?.field ?: snap?.field` — **live `statuses` wins**, snapshot is fallback. See §4 for the encoded fields and §8 for the `hasBattery` inversion.

Local computations inside:
- `hasBattery = snap?.hasBattery ?: v.isEv` (`:1793`) — **inverts** the normal preference: phone's manually-corrected powertrain over raw `isEv`.
- `ev = s?.evStatus`, `coord = s?.vehicleLocation?.coord`, `lamp = s?.tirePressureLamp`, `seats = s?.seatHeaterVentState`.
- `gen = v.generation.trim().toIntOrNull() ?: 3` and `gen5w = v.brand != Brand.KIA && gen < 3` → drives `tripsSupported = !gen5w` (`:1831`).
- `percent = s?.percentFor(hasBattery) ?: snap?.percent`, `rangeMi = s?.rangeMiFor(hasBattery) ?: snap?.rangeMi` — use the **powertrain-aware** helpers, not raw `isEv`.
- `pluggedIn = ev?.batteryPlugin?.let { it != 0 }` — batteryPlugin `0=unplugged`.
- `chargerLabel = chargerLabel(ev?.batteryPlugin)`.
- `acLimit = ev?.reservChargeInfos?.level(1)`, `dcLimit = ev?.reservChargeInfos?.level(0)` — note **level(1)=AC, level(0)=DC**.
- Tire lamp booleans: `(lamp?.frontLeft ?: 0) != 0` etc.
- Seat/steer/mirror/defrost heat as ints/bools from the respective status subfields.
- `hasLiveStatus = s != null`.
- `licensePlate`/`lastServiceMiles`/`serviceIntervalMiles` come only from `snap` (phone-entered).

### Preset persistence helpers

- **`persistAndPublishPresets(byVin)`** (`WearViewModel.kt:1140-1146`, private) — shared tail of save/delete. Wraps the map in `WearPresets`, launches a coroutine that independently `WearPresetsStore(ctx).save(WearSync.encodePresets(wp))` and `WearComms.publishPresets(ctx, wp)`. Either can fail without affecting the other; `ui.presets` was already updated by the caller.

### Draft mutators

- **`updateDraft(vin, f)`** (`WearViewModel.kt:1078-1081`, private) — applies `f` to the current (or fresh default) `ClimateDraft`, writes it into `WearUi.climateDrafts`, then **immediately** calls `publishClimateDrafts()` so every slider/toggle change mirrors to the phone in near-real-time.
- **`mergeRemoteClimate(remote)`** (`WearViewModel.kt:1084-1103`, private) — mirrors the phone's climate drafts in **without** re-publishing (no echo loop). Maps each `ClimateSync` back to a `ClimateDraft`, converting API seat values via `seatStepOf(SeatLevel.fromApi(...))`.
- **`publishClimateDrafts()`** (`WearViewModel.kt:1149-1164`, private) — maps every `ClimateDraft` to a `ClimateSync` (seats → `seatLevelOf(step).apiValue`) and pushes the whole map via `WearComms.publishClimate`.
- **`updateChargeDraft(vin, f)`** (`WearViewModel.kt:1185-1187`, private) — mirrors `updateDraft` but **without** a phone push (charge sliders are local until Apply).

### Reverse geocoding

- **`reverseGeocode(lat, lon)`** (`WearViewModel.kt:992-1017`, private suspend) — on API 33+ (`TIRAMISU`) uses the non-blocking `Geocoder.getFromLocation(..., GeocodeListener)` wrapped in `suspendCancellableCoroutine` + `withTimeoutOrNull(6000)`; resumes with `formatPlaceName(address)` or null. On older APIs uses the deprecated blocking overload on `Dispatchers.IO` with the same 6s timeout. `formatPlaceName` is the shared (phone) formatter that includes `.distinct()` to avoid `"Springfield, Springfield"`.

### Update check (`runUpdateCheck` is defined here but also called from init)

- **`runUpdateCheck(force: Boolean): Boolean`** (`WearViewModel.kt:1206-1224`, private suspend) — the single update-check path (cold-start only, no manual "Check now"):
  1. `if (BuildConfig.BUILD_RUN_NUMBER <= 0) return false` (dev/unstamped build).
  2. Read `localStore.flow.first()`.
  3. Debounce: if `!force && now - settings.updateLastCheckedAt < UPDATE_CHECK_INTERVAL_MS (12h)` return false.
  4. Snooze: if `!force && now < settings.updateSnoozeUntil` return false.
  5. Resolve `branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }`.
  6. `run = fetchLatestSuccessfulRun(branch)` or return false (only consumes the 12h window on a **successful** fetch — `setUpdateLastCheckedAt(now)` runs after the fetch succeeds).
  7. If `run.runNumber > BUILD_RUN_NUMBER`: set `ui.updateRun = run`, return true; else false.

---

## 4. Data & types (defined in this file)

These are top-level in the file (not strictly in 950–1856, but they are the payload types the command layer produces/consumes; documented here for reference completeness):

### `CarView` (`WearViewModel.kt:62-118`)
The fully-resolved per-car view merging live status + snapshot. ~60 fields. Notable ones the command layer sets via `buildCarView`:
- `hasBattery: Boolean` — powertrain (snapshot-corrected, see §8).
- `percent: Int?`, `rangeMi: Int?` — powertrain-aware.
- `locked`, `climateOn`, `charging`, `pluggedIn: Boolean?`.
- `acLimit`, `dcLimit: Int?` — from `reservChargeInfos.level(1)`/`level(0)`.
- `chargerLabel: String?`, `timeToFullMin: Int?`.
- `hasLiveStatus: Boolean` — `statuses[vin] != null`.
- `licensePlate`, `lastServiceMiles`, `serviceIntervalMiles` — phone-only (from snapshot).
- Plus doors/windows/trunk/hood/tire/12V/fuel/location/seat-heat fields.

### `ClimateDraft` (`WearViewModel.kt:121-133`)
Editable climate settings for one car. Fields + defaults:
- `tempF: Int = DEFAULT_CLIMATE_TEMP_F`
- `duration: Int = DEFAULT_CLIMATE_DURATION_MIN`
- `defrost: Boolean = false`
- `steering: Boolean = false`
- `seatDriver/seatPassenger/seatRearLeft/seatRearRight: Int = 0` — **watch heat steps: 0=off, 1=low, 2=med, 3=high** (heat only, no cooling).
- `activePresetId: String? = null` — which saved preset is applied; cleared once any control drifts.

### `ChargeLimitDraft` (`WearViewModel.kt:137`)
`data class ChargeLimitDraft(val ac: Int? = null, val dc: Int? = null)`. `null` = "show the car's actual current limit" — sliders only override once dragged.

### `WearUi` (`WearViewModel.kt:146-207`)
The single immutable render snapshot. Fields relevant here: `cars`, `trips`, `pending: Set<String>`, `busy`, `message: String?`, `presets`, `extras`, `aiBusy`, `driveSyncBusy`, `climateDrafts: Map<String, ClimateDraft>`, `chargeLimitDrafts: Map<String, ChargeLimitDraft>`, `settings`, `localSettings`, `pebbleOverride`, `settingsOverride`, `updateRun: WorkflowRun?`, `updateDownloading`, `resyncBusy`, `tripsErrors`. Helper methods: `draftFor(vin)`, `chargeDraftFor(vin)`, `pebbleOrderFor(vin)`.

### Seat-level mapping helpers (file-level, `WearViewModel.kt:211-242`)
- `seatLevelOf(step: Int): SeatLevel` — `1→LOW_HEAT, 2→MED_HEAT, 3→HIGH_HEAT, else→OFF`.
- `ClimateDraft.toRequest(tempF, defrost)` — builds a `ClimateRequest` from the draft; `tempF`/`defrost` default to the draft's own but overridable (smart climate supplies a computed temp).
- `seatStepOf(level: SeatLevel): Int` — inverse: `LOW_HEAT→1, MED_HEAT→2, HIGH_HEAT→3, else→0`. **Cooling collapses to 0** (watch is heat-only).
- `val seatStepLabels = listOf("Off", "Low", "Med", "High")` — public display labels.

**Encoding facts the command layer relies on** (from domain, used here):
- `SeatLevel.apiValue`: `0=off, 3-5=cool, 6-8=heat`; crosses the wear wire as ints. Command builders always send `seatLevelOf(step).apiValue`.
- `batteryPlugin`: `0=unplugged, 1=DC, 2=AC` — used for `pluggedIn`/`chargerLabel`.
- `reservChargeInfos.level(1)=AC`, `level(0)=DC`.

---

## 5. State & concurrency

- **Source of truth**: the `@Volatile` fields (`vehicles`, `statuses`, `snapshots`, `fetchedAt`, `trips`, `placeNames`, `pendingCounts`, `WearViewModel.kt:296-324`). They are plain `var`s (not StateFlows) because they're written from both main-thread command functions and background coroutines and read synchronously by `buildCarView` on every `publish()`. `@Volatile` gives safe publication for simple whole-map-replacement writes without a Mutex. Writes are **replace-whole-map** (`statuses = statuses + (vin to ...)`) — not in-place mutation.
- **Derived projection**: `_ui: MutableStateFlow<WearUi>` (`:337`), exposed read-only as `ui` (`:338`). `publish()` is the only rebuilder of `cars`/`trips`/`pending`. Compose screens collect `ui`.
- **Scope/dispatcher**: everything launches on `viewModelScope` (default `Dispatchers.Main.immediate`). Suspend network work inside command blocks runs under `BlueLinkGate.statusMutex.withLock` (`:1649`) — the **process-wide serializer** that prevents overlapping vehicle-status/command calls (backend 502s otherwise). `loadTrips` also holds it (`:775`). Keystore/credential IO and legacy geocoding are pushed to `Dispatchers.IO`.
- **Pending bookkeeping**: `pendingCounts` is a refcounted map; `pending` (`:328`) exposes only the keys. `mark` increments/decrements around the block in a `finally`.
- **PIN cooldown**: `pinFailCount`/`pinLockedUntilMs` (`WearViewModel.kt:1297-1298`) are plain in-memory vars (reset on process death — accepted, it's a brute-force slower not a hard boundary).
- **Recomposition triggers**: any `_ui.update { }` (optimistic draft edits, messages, busy flags) plus every `publish()` (car/pending rebuild). `updateDraft` triggers both a `_ui.update` and a phone push coroutine.

---

## 6. Collaborators & data flow

**Calls out to:**
- `WearComms.send(ctx, WearCommand)` — the relay send (`:1618`).
- `WearComms.publishClimate/publishPresets/publishAiToggle/publishAuroraToggle/publishPebbleOrder/publishLocalSettings/requestSync/relayToPhone/phoneNodeId/pullLatest` — Data Layer pushes/queries.
- `WearCommandRunner.optimistic(snapshot, action)` — relay-path optimistic snapshot patch (`:1623`).
- `repoFor(brand)` → `VehicleRepository`: `lock/unlock/startClimate/stopClimate/startCharge/stopCharge/setChargeTargets/flashLights/hornAndLights/trips/vehicles/login/logout` — standalone-path car API.
- `BlueLinkGate.statusMutex` — serialization.
- `UpdateApi.fetchLatestSuccessfulRun/downloadApk`, `WearRemote.openOnPhone`, `refreshWearGlanceables(ctx)` (via `requestWidgetUpdates`, `:1719`).
- On-disk stores: `WearLocalStore`, `WearPresetsStore`, `SnapshotStore`, `StatusCache`, `SessionStore`, `CredentialStore`.
- Shared pure helpers: `smartClimateTargetF`, `ambientFahrenheit`, `chargerLabel`, `formatPlaceName`, `percentFor`/`rangeMiFor`.

**Called by (UI):** Compose screens invoke `applyPreset`, `applyChargeLimits`, `saveCurrentAsPreset`, `deletePreset`, the slider setters, `smartClimate`, `snoozeUpdate`, `downloadAndInstallUpdate`, `setFontScale`, `setUnitSystem`, `dismissMessage`, `ensurePlaceName` (Location card), plus the toggle commands just above the range.

**Data channels in:** `WearCommandEvents.results` (relay failure notices → refresh), `WearClimateStore.flow` → `mergeRemoteClimate`, `snapshotStore.payload`, `WearSyncEvents`/`WearAiEvents.results` (init collectors). **Out:** Wearable Data Layer message/data paths (command/sync_request/climate/presets/settings), FileProvider install intent, WorkManager-free glanceable refresh.

---

## 7. Invariants & assumptions

- `vehicles.firstOrNull { it.vin == vin }` must succeed for a command to run — `command` and `smartClimate` early-`return` otherwise (`:1615`).
- The standalone `block` runs **only** while holding `BlueLinkGate.statusMutex`; blocks assume no other status/command call for the account overlaps.
- `flip` is only correct if called **after** the repository call succeeds — it optimistically records the intended new state assuming success.
- `onFailure` fires **only** on the standalone failure branch; callers that make optimistic UI edits before `command` (e.g. `applyPreset` seeding sliders, `applyChargeLimits` clearing the draft) rely on this to roll back — but a *relayed* failure will NOT trigger it (only a corrective refresh via `WearCommandEvents`).
- The driving gate (`if (st?.isDriving == true) error(...)`) is enforced **only on the standalone path** in `toggleClimate` (`:881`), `applyPreset` (`:958`), and `smartClimate` (`:1549`). Relayed commands rely on the phone's own repository to enforce it.
- Seat step range is `0..3`; charge sliders clamp to `CHARGE_LIMIT_RANGE`; temp `62..82`; duration `1..10`; font scale `0.8..1.4`.
- `runUpdateCheck` assumes `BUILD_RUN_NUMBER > 0` means a real CI build; `updateLastCheckedAt` is only advanced on a successful fetch.
- `buildCarView` assumes `statuses` is the authoritative source when present, EXCEPT `hasBattery` where snapshot wins.
- `publish()` must be called after any `@Volatile` field write for the UI to reflect it.

---

## 8. Gotchas & sharp edges

- **Relay path has no execution ack.** `WearComms.send` succeeding means the message reached the phone, NOT that the phone's BlueLink/Kia call worked (`:1583-1586`, `:1607-1612`). Optimistic flips are applied on send success. A genuine phone-side failure arrives later via `WearCommandEvents.results` and triggers `refreshStatus(r.vin, surface=false)` — there is **no revert**, because FLASH_LIGHTS/HORN have no state to invert and a fresh fetch is uniformly correct.

- **`explicit` is mandatory for anything not doors/climate/charge.** `toWearCommand`'s `else` branch returns a `REFRESH`. `flashLights`/`hornAndLights`/`applyChargeLimits`/`applyPreset`/`smartClimate` all pass `explicit` precisely because their action strings would otherwise relay a no-op REFRESH (see `:846-847`, `:1051-1053`, `:934-936`).

- **`toWearCommand` always carries the full climate draft** on every relayed command, even `REFRESH`/`TOGGLE_LOCK`. This is deliberate: a relayed climate-ON must arrive with complete seat/steering/duration settings, not wire defaults (`:1679-1681`).

- **Optimistic snapshot AND status must both be patched** in the relay branch (`:1621-1642`). `buildCarView` prefers `statuses` over `snapshots`, so patching only the snapshot would leave the change masked behind stale live status.

- **`applyPreset` seeds sliders up front, outside the command block** (`:918-930`), because the relay path never runs the block — otherwise a preset would start on the car but the UI would never highlight it as active with a phone connected.

- **`applyChargeLimits` sends what LimitsCard displays, not a fixed default** (`:1032-1039`). The old `draft ?: 80/90` fallback silently dropped an untouched DC limit from a displayed 100 to 90. Now it falls back to `car?.acLimit`/`dcLimit` first.

- **`smartClimate` decides on/off outside the block** (`:1508-1510`) using the same `statuses[vin]?.airCtrlOn ?: snapshots[vin]?.climateOn ?: false` priority `buildCarView` uses — because the relay path never runs the block, so a computed `targetF` inside it would be silently discarded and the phone would get a generic stale-temp toggle.

- **`ensurePlaceName` releases its guard on ANY non-success** (`:982-988`), not just a clean null. Previously a thrown geocoder exception left the VIN stuck in `geocoded` forever, permanently disabling place-name resolution for that car for the session.

- **`toggleCharge`'s `flip` was added after the fact** (`:899-903`): unlike lock/climate it originally never flipped local state, so on the standalone path the button lagged until the follow-up `refreshStatus` round-trip.

- **`saveCurrentAsPreset` dedups by `request` equality** (`:1112`) — tapping save twice with unchanged settings is a silent no-op, not a duplicate.

- **`persistAndPublishPresets` persist and publish are independent** (`:1140-1146`) — the on-disk save survives a process restart even if the phone push fails, and `ui.presets` is already updated before either runs.

- **`updateChargeDraft` deliberately does NOT push to the phone** (`:1182-1187`), unlike `updateDraft` — charge sliders are local-only until `applyChargeLimits`. Climate drafts sync live on every keystroke.

- **`downloadAndInstallUpdate` falls back to the phone** only when `run.wearApkUrl == null` (old release predating wear assets, or a failed upload) — it opens the release page on the phone via `WearRemote.openOnPhone` (`:1236-1240`).

- **`runUpdateCheck` compares against the WATCH's own `BUILD_RUN_NUMBER`, not the phone's** (`:1204-1224`), on the same GitHub Actions endpoint, entirely independent of any phone connection. It only consumes the 12h debounce window on a successful fetch.

- **Seat cooling is unrepresentable on the watch.** `seatStepOf` collapses `COOL` levels to `0` (off), so a preset created on the phone with seat cooling round-trips to the watch as "off" and back.
