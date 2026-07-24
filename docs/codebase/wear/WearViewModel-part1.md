# WearViewModel — Part 1: init / collectors / garage / auth (lines 1–950)

File: `C:\Users\AdiPerets\Bloo\wear\src\main\java\com\bloo\wear\WearViewModel.kt`

This document covers the top of the file: the top-level enums/data classes, the free helper functions, the ViewModel's fields and source-of-truth model, the `init` block's DataStore/Data-Layer collectors, `bootstrap()`, connection detection, sign in/out, garage loading, lazy status/trips fetch, the phone-relay command entry points defined in this range, geocoding, and the climate-draft plumbing up to `applyChargeLimits`. The command dispatcher `command()`, `toWearCommand`, `flip`, `mark`, `publish`, and `buildCarView` (lines 1556–1856) are cross-referenced here but documented in detail in Part 2.

---

## 1. Purpose

`WearViewModel` (`wear:1856`, class declared at `WearViewModel.kt:276`) is the **single ViewModel for the entire Wear OS app**. Every user-visible piece of state — car list, per-car climate drafts, charge-limit sliders, saved presets, phone-synced settings, PIN lock, update banner — flows through one `MutableStateFlow<WearUi>` (`_ui`, `WearViewModel.kt:337`) exposed read-only as `ui` (`WearViewModel.kt:338`). Compose screens collect `ui` and never touch the ViewModel's private mutable state directly.

The defining architectural quirk (documented at `WearViewModel.kt:244-275`) is **dual command execution**:

- **Relayed** — a command is serialized into a `WearCommand` and sent to the phone over the Wearable Data Layer (`WearComms.send`). The phone owns the real BlueLink/Kia network session and talks to the car. The watch gets no ack that the phone's *own* API call succeeded — only that the SEND reached the phone. Out-of-band failures arrive later via `WearCommandEvents.results` (wired in `init`, `WearViewModel.kt:435-450`) and trigger a corrective status re-fetch.
- **Standalone** — when no phone is reachable, the watch has its **own** signed-in BlueLink session (`repoFor`/`repos`) and calls the vehicle API directly, exactly like the phone would.

Both paths apply the same optimistic local-state update before any network confirmation, then call `publish()` immediately so the UI reacts instantly. All command logic funnels through the private `command()` helper (`WearViewModel.kt:1602`).

The lines-1–950 portion is the *setup and inbound-data* half: it wires all the collectors that feed `_ui`, bootstraps state on launch, loads the garage, handles login/logout, and defines the command entry points (`toggleLock`, `flashLights`, `hornAndLights`, `toggleClimate`, `toggleCharge`, `applyPreset`) plus geocoding.

---

## 2. Public surface (within lines 1–950 + closely-tied members)

### Top-level (file-scope) declarations

- **`enum class WearScreen { Loading, SignedOut, Ready }`** — `WearViewModel.kt:50`. The top-level screen the watch shows: initial data load, no logged-in account, or the normal car-list/detail UI.

- **`data class CarView(...)`** — `WearViewModel.kt:62-118`. A fully-resolved per-car view merging live status with the phone snapshot. See §4.

- **`data class ClimateDraft(...)`** — `WearViewModel.kt:121-133`. Editable climate settings for one car (heat-only seats, 0–3). See §4.

- **`data class ChargeLimitDraft(val ac: Int? = null, val dc: Int? = null)`** — `WearViewModel.kt:137`. Unsaved AC/DC charge-limit slider values; `null` means "show the car's actual current limit" (sliders only override once dragged). See §4.

- **`data class WearUi(...)`** — `WearViewModel.kt:146-207`. The single immutable snapshot of everything the watch UI renders, exposed via `WearViewModel.ui`. See §4. Member helper functions:
  - `fun draftFor(vin: String): ClimateDraft` — `WearViewModel.kt:199`. Returns `climateDrafts[vin]` or a fresh `ClimateDraft()`.
  - `fun chargeDraftFor(vin: String): ChargeLimitDraft` — `WearViewModel.kt:201`. Returns `chargeLimitDrafts[vin]` or a fresh `ChargeLimitDraft()`.
  - `fun pebbleOrderFor(vin: String): List<String>` — `WearViewModel.kt:205-206`. Effective pebble order: `pebbleOverride[vin]` (pending local change) wins, else `settings?.pebbleOrders?.get(vin)`, else `WearPebbles.DEFAULT_ORDER`.

- **`val seatStepLabels = listOf("Off", "Low", "Med", "High")`** — `WearViewModel.kt:242`. Public display labels for the 0–3 seat-heat steps.

### File-private free helpers

- `private fun seatLevelOf(step: Int): SeatLevel` — `WearViewModel.kt:211-216`. Maps the watch's 0–3 heat step to `SeatLevel` (`1→LOW_HEAT`, `2→MED_HEAT`, `3→HIGH_HEAT`, else `OFF`). Watch has heat-only, no cooling.
- `private fun ClimateDraft.toRequest(tempF: Int = this.tempF, defrost: Boolean = this.defrost): ClimateRequest` — `WearViewModel.kt:220-230`. Builds a `ClimateRequest` from the draft; `tempF`/`defrost` overridable (smart climate supplies a computed temp).
- `private fun seatStepOf(level: SeatLevel): Int` — `WearViewModel.kt:234-239`. Inverse of `seatLevelOf`; cooling levels collapse to `0` (off).

### ViewModel public API defined in lines 1–950

Constructor: **`class WearViewModel(app: Application) : AndroidViewModel(app)`** — `WearViewModel.kt:276`.

- **`val ui: StateFlow<WearUi>`** — `WearViewModel.kt:338`. Read-only projection of `_ui`. The only thing screens observe.
- **`fun refreshConnection()`** — `WearViewModel.kt:546-553`. Re-checks phone reachability over the Data Layer and updates `WearUi.phoneConnected`.
- **`fun login(brand: Brand, email: String, password: String, pin: String)`** — `WearViewModel.kt:567-593`. Standalone sign-in to a BlueLink/Genesis account. Kia refused.
- **`fun signOutAll()`** — `WearViewModel.kt:599-617`. Logs out of every brand, wipes all in-memory & on-disk vehicle state, resets to `SignedOut`.
- **`fun onCarShown(vin: String)`** — `WearViewModel.kt:665-679`. Called when a car page becomes visible; persists the selected VIN and fetches status once per session.
- **`fun refreshAll()`** — `WearViewModel.kt:683-692`. Forces a fresh status fetch for every car + a connection recheck.
- **`fun resync()`** — `WearViewModel.kt:695-716`. Re-pulls snapshots/sessions/settings the phone published.
- **`fun syncDrive()`** — `WearViewModel.kt:721-749`. Asks the phone to run a Drive sync; shows busy until reply or timeout.
- **`fun refreshStatus(vin: String, surface: Boolean = true)`** — `WearViewModel.kt:761-767`. Relay-only status refresh (no standalone fallback of its own).
- **`fun loadTrips(vin: String)`** — `WearViewModel.kt:770-787`. Lazily fetches recent EV trips (standalone, mutex-serialized).
- **`fun requestAiSummary(vin: String)`** — `WearViewModel.kt:795-811`. Phone-only relay to generate an AI summary; result lands in `ui.extras.ai[vin]`.
- **`fun setWeatherFromDeviceLocation()`** — `WearViewModel.kt:817-826`. Relay: ask phone to set home weather from its GPS.
- **`fun toggleLock(vin: String)`** — `WearViewModel.kt:838-841`. Lock/unlock toggle via `command()`.
- **`fun flashLights(vin: String)`** — `WearViewModel.kt:848-855`. Hyundai/Genesis-only flash lights, explicit `FLASH_LIGHTS`, `successMessage = "Lights flashed"`.
- **`fun hornAndLights(vin: String)`** — `WearViewModel.kt:857-861`. Explicit `HORN_AND_LIGHTS`, `successMessage = "Horn & lights sent"`.
- **`fun toggleClimate(vin: String)`** — `WearViewModel.kt:872-888`. Climate on/off toggle.
- **`fun toggleCharge(vin: String)`** — `WearViewModel.kt:896-904`. Charge start/stop toggle.
- **`fun applyPreset(vin: String, preset: ClimatePreset)`** — `WearViewModel.kt:908-962`. Apply a saved preset (start climate with its exact settings + seed sliders).
- **`fun ensurePlaceName(vin: String, lat: Double, lon: Double)`** — `WearViewModel.kt:970-990`. Reverse-geocode a car's coords once per session.
- **`fun applyChargeLimits(vin: String)`** — `WearViewModel.kt:1030-1069`. Push AC/DC charge-limit sliders to the car.

Other public members below line 950 (drafts setters, presets, PIN, update, aurora/AI toggles, tile config) are outside this unit's focus; documented in Part 2. `currentBuildNumber` (`WearViewModel.kt:1227`) and the setters `setClimateTemp`…`setDcLimit` (`WearViewModel.kt:1171-1180`) are part of the same class surface.

---

## 3. Internal structure

### Source-of-truth fields (`WearViewModel.kt:278-338`)

Stores instantiated up front:
- `ctx` (`get()` for `getApplication<Application>()`), `sessionStore = SessionStore(ctx)`, `credentialStore = CredentialStore(ctx)`, `snapshotStore = SnapshotStore(ctx)`, `statusCache = StatusCache(ctx)`, `localStore = WearLocalStore(ctx)`.
- `repos = mutableMapOf<Brand, VehicleRepository>()` — brand→repo cache for the standalone path.

Nine `@Volatile` mutable fields are the **real** source of truth; `_ui` is a derived projection rebuilt by `publish()`/`buildCarView` whenever one changes (comment at `WearViewModel.kt:286-293`). They are plain `var`s (not StateFlows) because they're written from both main-thread command functions and background coroutines and read synchronously by `buildCarView` on every `publish()`. `@Volatile` gives safe cross-thread publication for what are simple map-replacement writes, without a full Mutex:
- `vehicles: List<Vehicle>` (`297`) — garage list.
- `statuses: Map<String, VehicleStatus>` (`302`) — rich live status per VIN; preferred over snapshots.
- `snapshots: Map<String, VehicleSnapshot>` (`306`) — phone's lightweight always-available view; the fallback.
- `fetchedAt: Map<String, Long>` (`310`) — wall-clock ms each VIN's status was fetched.
- `trips: Map<String, List<EvTrip>>` (`312`).
- `placeNames: Map<String, String>` (`316`) — reverse-geocoded names, session-only, never persisted.
- `pendingCounts: Map<String, Int>` (`324`) — **ref-counted** pending keys (see §8).
- `pending: Set<String> get() = pendingCounts.keys` (`328`) — private read-only view; only the keys matter to the UI.

Session-scoped `mutableSetOf`s (not volatile — accessed from coroutines on `viewModelScope`):
- `sessionFetched` (`332`) — cars whose status was already fetched this session (avoids re-hitting the rate-limited network on page-back).
- `tripsFetched` (`333`) — cars whose trips were fetched.
- `geocoded` (`335`) — coords already attempted to reverse-geocode this session.

`repoFor(brand)` (`344-345`): `repos.getOrPut(brand) { repositoryFor(brand, sessionStore, credentialStore) }` — one repository per brand for the ViewModel's life so session/token state persists across calls.

### `init` block collectors (`WearViewModel.kt:347-499`)

Eight `viewModelScope.launch { … collect … }` coroutines plus two direct calls. Each mirrors an on-disk store or an event channel into `_ui` or the volatile fields:

1. **Settings collector** (`360-385`) — `WearSettingsStore(ctx).flow.collect`. The most intricate: reconciles the incoming phone-synced `WearSettingsPayload` against two local optimistic-override maps (`pebbleOverride`, `settingsOverride`).
   - `stillPending` (`365-367`): keeps a pebble override only while the incoming synced order (after `WearPebbles.normalize`) does **not** match the override. Once they match, the phone has caught up and the override is dropped.
   - `stillPendingSettings` (`368-376`): same logic per settings key (`"aiEnabled"`/`"auroraEnabled"`/`"auroraColorMode"`), comparing the incoming value against the overridden value.
   - `effectiveSettings` (`377-381`): substitutes any still-pending override back over the (possibly stale) synced value.
   - Writes `u.copy(settings = effectiveSettings, pebbleOverride = stillPending, settingsOverride = stillPendingSettings)`.

2. **Presets collector** (`391-393`) — `WearPresetsStore(ctx).flow.collect { p -> _ui.update { it.copy(presets = p.byVin) } }`. Mirrors persisted presets into `ui.presets` so both watch writes and phone syncs surface identically.

3. **Extras collector** (`394-408`) — `WearExtrasStore(ctx).flow.collect`. Copies `extras` into `_ui`, and clears `aiBusy` **only** when a *new* summary for the awaited VIN actually landed (`gotNewSummary = busyVin != null && e.ai[busyVin] != null && e.ai[busyVin] != u.extras.ai[busyVin]`, `404`). Prevents an unrelated weather/photo push on the same channel from killing the spinner.

4. **Local settings collector** (`414-416`) — `localStore.flow.collect { s -> _ui.update { it.copy(localSettings = s) } }`. `WearLocalSettings` (font scale, unit system, PIN config) is device-only, never synced FROM the phone.

5. **Drive-sync results** (`422-426`) — `WearSyncEvents.results.collect`. Clears `driveSyncBusy` and sets a message (`r.message ?: if (r.ok) "Settings synced" else "Sync failed"`). Races `syncDrive`'s 15s timeout.

6. **AI results** (`427-434`) — `WearAiEvents.results.collect`. Only updates if `it.aiBusy == r.vin` (guards against stale/duplicate replies), then clears `aiBusy` and sets `r.message`.

7. **Command results** (`435-450`) — `WearCommandEvents.results.collect`. On `!r.ok`: sets a failure message and calls `refreshStatus(r.vin, surface = false)`. This is the relay path's only failure feedback (no revert; a fresh fetch is correct for every action including FLASH_LIGHTS/HORN_AND_LIGHTS which have no state to invert).

8. **Climate store** (`451-453`) — `WearClimateStore(ctx).flow.collect { remote -> mergeRemoteClimate(remote) }`.

9. **Snapshot store** (`454-495`) — `snapshotStore.payload.collect`. Live-collects snapshot pushes (from `WearListenerService` and standalone command results). Control flow:
   - If `data.vehicles.isEmpty()` (`461-476`): a genuine zero-vehicle assertion. If local state still has anything (`snapshots.isNotEmpty() || vehicles.isNotEmpty()`), clear `snapshots`/`vehicles`/`statuses`/`trips` and `publish()`; then `return@collect`. Repeated empty pushes are cheap no-ops.
   - Otherwise: `snapshots = data.vehicles.associateBy { it.vin }` (`477`). Then fold each snapshot's core fields into any held `statuses` entry (`481-491`) — because `buildCarView` prefers status over snapshot, a fresh push would otherwise stay masked for lock/climate/charge. It copies `doorLock`, `airCtrlOn`, and `evStatus.batteryCharge` from the snapshot into the existing status.
   - If `vehicles.isEmpty() && sessionStore.loggedInBrands().isNotEmpty()` → `loadGarage()`, else `publish()` (`492-493`).

10. **Direct calls**: `viewModelScope.launch { runUpdateCheck(force = false) }` (`497`) — cold-start update check. `bootstrap()` (`498`).

### `bootstrap()` (`WearViewModel.kt:501-536`)

A single `viewModelScope.launch`, deliberately sequential (not racing separate coroutines) so the PIN gate resolves before any Ready screen with real data can render:
1. Read `localStore.flow.first()` (guarded by `runCatching`, `510`). If `pinLockEnabled == true && hasPin`, set `pinLocked = true` (`511-513`).
2. `runCatching { WearComms.pullLatest(ctx) }` (`514`) — pull phone data.
3. `refreshConnection()` (`515`).
4. Load snapshots from `snapshotStore.current()` (`516`), falling back to the existing `snapshots` on failure.
5. Load cached statuses + fetchedAt from `statusCache.load()` (`517-521`).
6. `val brands = sessionStore.loggedInBrands()`. If empty → `screen = SignedOut` (`523-524`). Else: load account emails **off the main thread** via `withContext(Dispatchers.IO) { credentialStore.loadAll().map { it.email } }` (`529-531`; Keystore/EncryptedSharedPreferences is disk+crypto IO, resolved before `update{}` so a lost CAS race can't re-run the blocking read), set `accounts`, then `loadGarage()` (`532-533`).

### `loadGarage()` (`WearViewModel.kt:632-662`)

`private suspend`. Populates `vehicles` and moves to `Ready`. Live per-car statuses are deliberately NOT fetched here (that's lazy via `onCarShown`).
- Companion mode: if `snapshots.isNotEmpty()`, `vehicles = snapshots.values.map { it.toVehicle() }` (`634`).
- Else (`636-658`): `runCatching { WearComms.requestSync(ctx, "", refresh = false) }` to request a push. Then if `WearComms.phoneNodeId(ctx) == null` (no phone at all → standalone): fetch the vehicle list per logged-in brand via `repoFor(b).vehicles()`, and if non-empty, build lightweight `VehicleSnapshot`s (copying `vin/name/model/isEv/regId/generation/brandIndicator`), save them via `snapshotStore.saveVehicles(snaps)`, and set `snapshots`. Returns `fetched`. If a phone IS reachable, returns `emptyList()` (the push will populate snapshots).
- `publish(WearScreen.Ready)` (`660`).

### Auth flow

- `login` (`567-593`): validation (blank fields → message; Kia → "Sign in on your phone" message and return). Then `busy = true`, `runCatching { repoFor(brand) as? BlueLinkRepository … repo.login(email.trim(), password, pin.trim()); credentialStore.save(...) }`. On success: log with `maskEmail`, set `Ready` + refreshed `accounts`, `loadGarage()`. On failure: clear busy, show error.
- `signOutAll` (`599-617`): per brand `repoFor(b).logout()` + `credentialStore.clear(b)`; then `repos.clear()`, clear session/trips sets, empty all volatile vehicle state, `snapshotStore.saveVehicles(emptyList())`, `requestWidgetUpdates()`, and set `SignedOut` with empty lists.

---

## 4. Data & types (field by field)

### `enum class WearScreen` (`WearViewModel.kt:50`)
`Loading`, `SignedOut`, `Ready`.

### `data class CarView` (`WearViewModel.kt:62-118`)
A fully resolved per-car view, built by `buildCarView` (`1787`). Fields:
- `vin: String`, `name: String`, `model: String`, `brand: Brand`.
- `hasBattery: Boolean` — prefers phone's manually-corrected powertrain over raw `isEv`.
- `percent: Int?`, `rangeMi: Int?` — via `percentFor(hasBattery)`/`rangeMiFor(hasBattery)` (respects the manual powertrain override), fallback `snap.percent`/`snap.rangeMi`.
- `locked: Boolean?`, `climateOn: Boolean?`, `charging: Boolean?`, `pluggedIn: Boolean?`.
- `chargerLabel: String?` — from `chargerLabel(ev?.batteryPlugin)`.
- `timeToFullMin: Int?`, `acLimit: Int?`, `dcLimit: Int?`, `fetchedAt: Long?`.
- `doorsOpen: List<String>`, `windowsOpen: List<String>` — from `openLabels()`.
- `trunkOpen: Boolean`, `hoodOpen: Boolean`, `tireWarning: Boolean`, `battery12v: Int?`.
- `lowFuel: Boolean`, `washerLow: Boolean`, `brakeLow: Boolean`, `keyFobLow: Boolean`, `odometer: String?`.
- `lat: Double?`, `lon: Double?`, `locationName: String?`.
- `tripsSupported: Boolean`, `engineOn: Boolean`, `accessoryOn: Boolean`, `defrostOn: Boolean`, `tempSetting: String?`.
- `tireAll: Int?`, `tireFl/Fr/Rl/Rr: Boolean` (per-corner warning: `(lamp?.corner ?: 0) != 0`).
- `steerHeat: Boolean`, `mirrorHeat: Boolean`, `rearDefrost: Boolean` (`(s?.field ?: 0) != 0`).
- `seatFl/Fr/Rl/Rr: Int?` — from `seatHeaterVentState`.
- `battery12vHealth: String?`, `fuelLevel: Int?`, `hasLiveStatus: Boolean` (`s != null`).
- `licensePlate: String?`, `lastServiceMiles: Int?`, `serviceIntervalMiles: Int?` — user-entered on phone Settings, synced from `snap`, never entered on the watch.

### `data class ClimateDraft` (`WearViewModel.kt:121-133`)
- `tempF: Int = DEFAULT_CLIMATE_TEMP_F` (`122`).
- `duration: Int = DEFAULT_CLIMATE_DURATION_MIN` (`123`).
- `defrost: Boolean = false`, `steering: Boolean = false`.
- `seatDriver: Int = 0`, `seatPassenger: Int = 0`, `seatRearLeft: Int = 0`, `seatRearRight: Int = 0` — **0 off, 1 low, 2 med, 3 high (heat only)** (`126`).
- `activePresetId: String? = null` — which saved preset is currently applied; cleared once a control drifts away from it.

Encoding note: seat steps 0–3 map to `SeatLevel` via `seatLevelOf` (heat only) and back via `seatStepOf` (cooling collapses to 0). `SeatLevel.apiValue` crosses the wear wire as ints (0=off, 3–5=cool, 6–8=heat per domain facts).

### `data class ChargeLimitDraft` (`WearViewModel.kt:137`)
- `ac: Int? = null`, `dc: Int? = null` — `null` = "show the car's actual current limit"; sliders only override once dragged.

### `data class WearUi` (`WearViewModel.kt:146-207`)
The immutable render snapshot. Fields and defaults:
- `screen: WearScreen = Loading`.
- `cars: List<CarView> = emptyList()`.
- `trips: Map<String, List<EvTrip>> = emptyMap()`.
- `pending: Set<String> = emptySet()` — busy keys.
- `busy: Boolean = false` — global busy (used by login).
- `message: String? = null` — snackbar/status text.
- `presets: Map<String, List<ClimatePreset>> = emptyMap()`.
- `extras: WearExtras = WearExtras()` — weather/photo/AI bundle.
- `aiBusy: String? = null` — VIN awaiting an AI summary (spinner).
- `driveSyncBusy: Boolean = false`.
- `accounts: List<String> = emptyList()` — signed-in emails.
- `phoneConnected: Boolean = false`.
- `climateDrafts: Map<String, ClimateDraft> = emptyMap()`.
- `chargeLimitDrafts: Map<String, ChargeLimitDraft> = emptyMap()` — per-VIN so dragging one car can't bleed onto another.
- `settings: WearSettingsPayload? = null` — phone-synced settings.
- `localSettings: WearLocalSettings = WearLocalSettings()`.
- `pebbleOverride: Map<String, List<String>> = emptyMap()` — optimistic per-car pebble orders held until the phone echoes back.
- `settingsOverride: Map<String, Any?> = emptyMap()` — optimistic overrides for `aiEnabled`/`auroraEnabled`/`auroraColorMode`, held until echo matches (prevents concurrent settings pushes from stomping a just-set toggle; comment `171-177`).
- `updateRun: WorkflowRun? = null` — a newer CI build if found (independent of phone).
- `updateDownloading: Boolean = false`, `pinLocked: Boolean = false`, `resyncBusy: Boolean = false`.
- `tripsErrors: Set<String> = emptySet()` — VINs whose last trip fetch failed (distinguishes "couldn't load" from "no trips").

Member helpers: `draftFor`, `chargeDraftFor`, `pebbleOrderFor` (see §2).

---

## 5. State & concurrency

- **Single StateFlow**: `_ui = MutableStateFlow(WearUi())` (`337`), exposed as `ui = _ui.asStateFlow()` (`338`). All UI observation goes through this. Mutations use `_ui.update { … }` (atomic CAS loop) — hence the recurring "resolve blocking/network reads BEFORE `update{}` so a lost CAS retry can't re-run them" pattern (`bootstrap` line 529, `refreshConnection` line 549).
- **Volatile source-of-truth** (`vehicles`, `statuses`, `snapshots`, `fetchedAt`, `trips`, `placeNames`, `pendingCounts`): written from main-thread commands and background collectors, read synchronously by `buildCarView` on every `publish()`. `@Volatile` gives safe publication for whole-map-replacement writes; no Mutex is used because writes are atomic reference swaps (`286-293`).
- **Scope/dispatcher**: everything launches on `viewModelScope` (default `Dispatchers.Main.immediate` on Android). Explicit `withContext(Dispatchers.IO)` is used only for the Keystore-backed credential read in `bootstrap` (`529`) and the legacy pre-Tiramisu geocoder path (`1010`).
- **Cross-account mutex**: standalone API calls that touch vehicle status are serialized process-wide by `BlueLinkGate.statusMutex.withLock { … }` — used in `loadTrips` (`775`) and `command`'s standalone branch (`1649`). The backend 502s on overlapping requests for one account.
- **Ref-counted pending** (`pendingCounts` + `mark`, §8): handles overlapping same-key in-flight calls so the busy state clears only when the last one finishes.
- **In-memory PIN cooldown**: `pinFailCount`/`pinLockedUntilMs` (`1297-1298`) reset on process death — an accepted tradeoff for a brute-force slowdown, not a hard boundary.
- **Recomputation triggers**: `publish()` is the single funnel that rebuilds `cars`/`trips`/`pending` from volatile fields into `_ui`. Every collector that mutates volatile fields must call `publish()` (directly, or via `mark`/`command`). Collectors that only touch `_ui`-resident fields (settings/presets/extras/localSettings) use `_ui.update` directly and don't call `publish()`.

---

## 6. Collaborators & data flow

### Stores (DataStore / EncryptedSharedPrefs backed)
- `SessionStore` — `loggedInBrands()`; per-brand session tokens (read for auth gating).
- `CredentialStore` — `save`/`clear`/`loadAll`; per-brand credentials, backed by Keystore/EncryptedSharedPreferences.
- `SnapshotStore` — `payload` (Flow, collected in init `460`), `current()`, `saveVehicles()`, `setSelected(vin)`. The phone's lightweight car view + the watch's persisted fallback.
- `StatusCache` — `load()` returns `{statuses, fetched}` (bootstrap `518-520`).
- `WearLocalStore` (`localStore`) — `flow`, `verifyPin`, `setPin`, `setUpdateSnoozeUntil`, `setUpdateLastCheckedAt`, `setFontScale`, `setUnitSystem`, `setTileActions`, `setTileCarVin`, etc. Device-only settings.
- `WearSettingsStore`, `WearPresetsStore`, `WearExtrasStore`, `WearClimateStore` — each exposes a `.flow` collected in `init`; `WearPresetsStore.save` used by preset persistence.

### Wear Data Layer via `WearComms`
- `WearComms.send(ctx, WearCommand)` — relay a command to the phone (in `command`).
- `WearComms.requestSync(ctx, vin, refresh)` — ask the phone to push fresh data (`loadGarage` 637, `resync` 706, `refreshStatus` 764).
- `WearComms.pullLatest(ctx)` — pull latest phone data (bootstrap, resync, syncDrive).
- `WearComms.phoneNodeId(ctx)` — node lookup (up to 10s); `null` = no phone. Drives standalone fallback and `phoneConnected`.
- `WearComms.relayToPhone(ctx, WearCommand)` — used by `requestAiSummary` (AI_SUMMARY) and `setWeatherFromDeviceLocation` (WEATHER_DEVICE_LOCATION).
- `WearComms.publishPresets/publishClimate/publishLocalSettings/publishAiToggle/publishAuroraToggle/publishPebbleOrder` — outbound settings/state pushes (mostly Part 2).
- Raw `MessageClient.sendMessage(node, WearSync.PATH_SYNC_REQUEST, WearSync.encodeCommand(cmd))` in `syncDrive` (`728-733`) with a 10s `Tasks.await` — a hand-rolled relay for DRIVE_SYNC.

### Event channels (in-process broadcast of Data-Layer replies)
- `WearSyncEvents.results`, `WearAiEvents.results`, `WearCommandEvents.results` — collected in `init`.

### Repositories (standalone path)
- `repositoryFor(brand, sessionStore, credentialStore)` → `VehicleRepository` (cached in `repos`). Concrete `BlueLinkRepository` for Hyundai/Genesis login. Methods used: `login`, `logout`, `vehicles()`, `trips(v)`, `lock`/`unlock`, `startClimate`/`stopClimate`, `startCharge`/`stopCharge`, `flashLights`, `hornAndLights`, `setChargeTargets`.

### Other
- `com.bloo.wear.tile.refreshWearGlanceables(ctx)` via `requestWidgetUpdates()` (`1719`) — redraws Tiles/complications.
- `Geocoder` (`ensurePlaceName`/`reverseGeocode`) + `com.bloo.bluelink.data.formatPlaceName`.
- `UpdateApi` / `WearRemote` / `FileProvider` for self-update (Part 2).
- `AppLog.log(...)` for diagnostics.

### Data in / out (this range)
- **In**: phone pushes (settings/presets/extras/climate/snapshot Data-Layer paths), event replies (sync/ai/command results), on-disk caches (status cache, snapshot store, local settings), and standalone repo fetches (vehicle list, trips, status via commands).
- **Out**: relayed `WearCommand`s to the phone, sync requests, published settings/presets/climate/pebble orders, `snapshotStore.setSelected`, widget refreshes, and `_ui` to Compose.

---

## 7. Invariants & assumptions

- **`_ui` is derived, never authoritative for cars/trips/pending**: `publish()` is the only writer of `WearUi.cars`/`trips`/`pending`. Any change to `vehicles`/`statuses`/`snapshots`/`trips`/`pendingCounts` must be followed by `publish()` or the UI won't reflect it (`1750-1770`).
- **`buildCarView` prefers `statuses` over `snapshots`** per field (`s?.field ?: snap?.field`). Therefore any fresh snapshot push must be folded into an existing status entry (init `481-491`) or it stays masked. Exception: `hasBattery` inverts the preference (`snap?.hasBattery ?: v.isEv`, `1793`).
- **Kia never signs in on the watch**: `login` refuses Kia (`572-574`); Kia only reaches the watch by syncing down from the phone (domain fact: Kia login needs phone-side captcha/2FA).
- **`repoFor` reuses one repo per brand** for the ViewModel's life so session/token state survives across calls (`342-345`).
- **Standalone status-touching calls must hold `BlueLinkGate.statusMutex`** to avoid overlapping-request 502s (`loadTrips` 775, `command` standalone 1649).
- **PIN gate must be resolved before Ready with real data**: `bootstrap` reads the PIN setting synchronously *first* (`510-513`), before the IPC calls that could flip the screen to Ready.
- **`onCarShown` fetches status at most once per session per VIN** (`sessionFetched` guard, `675`), and only for VINs actually in `vehicles` (`676`).
- **Blocking/network reads are resolved before `_ui.update{}`** so a lost CAS retry can't re-run them (bootstrap `529`, refreshConnection `549`).
- **Optimistic overrides only clear on an exact echo match** (`pebbleOverride`/`settingsOverride` in init). If the phone never receives a push, the override must be dropped manually (see `savePebbleOrder` `1481-1487` in Part 2) or it masks all future phone-side changes for the session.
- Climate-start is gated on `st?.isDriving` on all three standalone start sites (`toggleClimate` 881, `applyPreset` 958, `smartClimate` 1549) — but relayed climate-starts rely on the phone's own repository to enforce this.
- `plugType`/`batteryPlugin` encodings (domain): `pluggedIn = ev?.batteryPlugin?.let { it != 0 }` treats 0 as unplugged (`1811`).

---

## 8. Gotchas & sharp edges

- **Ref-counted `pendingCounts`, not a Set** (`311-328`, `mark` 1738-1748): two overlapping calls with the same key (e.g. a manual Refresh tap while `onCarShown`'s own refresh is still in flight) previously collapsed to one Set entry, so the *first* completion cleared the key while the *second* block was still running — the spinner vanished and the button became tappable mid-request. `mark` increments on entry, decrements in `finally`; the key leaves only when the count hits 0.
- **Relay path has no execution ack** (`244-275`, `435-450`, `1607-1612`): `WearComms.send` succeeding only means the message reached the phone, not that the phone's BlueLink call worked. A real failure arrives out-of-band via `WearCommandEvents.results` and triggers a corrective `refreshStatus` — never a hand-rolled revert. Consequently, `onFailure` callbacks (for optimistic-draft rollback) only fire on the *standalone* failure branch.
- **Extras collector must not clear `aiBusy` on unrelated pushes** (`394-408`): `extras` carries weather/photo on the same channel, so `aiBusy` is cleared only when a genuinely new summary for the awaited VIN appears; otherwise the real AI result (or failure) arriving later would be dropped by `WearAiEvents`' own "still waiting" guard.
- **Empty snapshot push reconciliation** (`461-476`): a genuine zero-vehicle push (cars removed / phone sign-out that bypassed the watch's `signOutAll`) clears local state — but only when something stale exists, so repeated empty pushes are no-ops.
- **`signOutAll` must also wipe snapshots on disk** (`607-613`): `loadGarage` rebuilds the garage from snapshots, so leaving the old account's snapshots would make a later sign-in to a *different* account show the previous owner's cars, states and locations.
- **`onCarShown` persists the selected VIN** (`665-674`): nothing else on the watch ever wrote `SnapshotStore.selected` (the watch deliberately ignores the phone's `persistState`), so "Follow selected" tiles/complications were stuck on car #1 forever. Now every page view writes the selection + refreshes widgets.
- **`refreshAll` really means all cars** (`683-692`): the old code cleared `sessionFetched` then only refreshed `vehicles.firstOrNull()`, silently refreshing just the first car. Now it seeds `sessionFetched` with every VIN and refreshes each.
- **`refreshStatus` has no standalone fallback** (`751-767`): it only ever relays a `requestSync`. A standalone watch still gets fresh data indirectly through each command's post-success `refreshStatus`/`flip`.
- **`applyChargeLimits` sends what LimitsCard DISPLAYS** (`1030-1069`): `ac = draft.ac ?: car?.acLimit ?: 80`, `dc = draft.dc ?: car?.dcLimit ?: 90`. The old `draft ?: 80/90` fallback silently changed an untouched slider (e.g. car at DC 100%, user edits only AC → DC dropped to 90). The draft is cleared optimistically up front and restored by `onFailure` on standalone failure.
- **Explicit `WearCommand` needed for non-toggle actions** (`848-861`, `937-948`, `1055-1060`): `command()` always relays first, and `toWearCommand` maps unknown action strings to a plain `REFRESH`. Without `explicit`, `flashLights`/`hornAndLights`/`applyChargeLimits`/preset-apply would have silently relayed a REFRESH instead of the real action.
- **`applyPreset` seeds sliders up front, not inside the block** (`908-930`): the relay path never runs the standalone `block`, so seeding inside it never happened with a phone connected — the preset started but the UI never showed it as active. `previousDraft` is captured so a standalone failure restores the exact pre-apply draft via `onFailure`.
- **`toggleCharge` needed a `flip` added after the fact** (`896-904`): unlike lock/climate it originally never flipped local state, so on the standalone path the button lagged on its old state until the follow-up `refreshStatus` landed.
- **`ensurePlaceName` releases its guard on ANY non-success** (`970-990`): a thrown geocoder exception used to leave `vin` stuck in `geocoded` forever, permanently disabling reverse-geocoding for that car for the session. Now `geocoded.remove(vin)` runs whenever the result is null/blank.
- **`reverseGeocode` API split** (`992-1017`): API 33+ uses the non-blocking `Geocoder.GeocodeListener` inside `suspendCancellableCoroutine` with a 6s `withTimeoutOrNull` (the legacy overload can hang); older APIs use the deprecated blocking overload on `Dispatchers.IO`. Both funnel through `formatPlaceName` (which `.distinct()`s to avoid "Springfield, Springfield").
- **`syncDrive` 15s safety net** (`745-746`): if the phone never replies, the `WearSyncEvents` collector never clears `driveSyncBusy`; the `delay(15_000)` then clears it with "Sync timed out". If the reply already arrived, this no-ops (`if (it.driveSyncBusy)`). `requestAiSummary` uses the same 15s pattern (`808-809`).
- **`bootstrap` runs sequentially by design** (`501-512` comment): it was previously a separate racing coroutine, which could render Ready with real vehicle data before `pinLocked` ever flipped true.
