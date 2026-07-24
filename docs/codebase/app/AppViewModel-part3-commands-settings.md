# AppViewModel — Part 3: Commands, Settings, Drive Sync & Weather

File: `app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt`
Covered range: **lines 1600–2360** (the tail of the class), inside
`class AppViewModel(app: Application) : AndroidViewModel(app)`.

This part covers, in file order: the tail of the Quick-Settings-tile setters,
per-car pebble/hotspot/section-order setters, EV-trip loading, the whole climate
draft + preset subsystem, `locate` + reverse-geocode, the **command engine**
(`runCommand` and every remote command that routes through it), Settings/nav,
appearance setters, settings import/export, Google-Drive auto-sync, weather, and
the shared busy/error plumbing (`launchBusy`, `reportError`, `reportInfo`).

> Context that lives in Parts 1–2 (same file): `UiState` (lines 76–253),
> `Screen`/`KiaOtpUi` sealed types, the `init {}` collectors, auth (`login`,
> `loginKia`, …), `loadGarageInner`, `loadStatus`, `ensureStatus`,
> `refreshStatus`, `checkForUpdate`, AI (`autoSummarize`, `summarizeCar`, `askAi`).
> This part references those but does not re-document them.

---

## 1. Purpose

This is the **write side** of the ViewModel: the functions the phone UI (and,
indirectly via `handleShortcut`/tiles/widgets, the OS) call to *change* something —
fire a remote vehicle command, flip a per-car preference, edit climate presets,
turn on Drive sync, look up weather. Almost every function here follows one of a
small number of stereotyped shapes:

1. **Optimistic-`_state`-then-persist setters** — mutate `_state` synchronously
   for instant UI feedback, then `viewModelScope.launch { settingsStore.setX(...) }`
   to persist; some also poke the watch / tiles / widget afterward.
2. **DataStore-only appearance setters** — write one field to `SettingsStore` and
   return; the UI updates because `appearance`/`notifications` (Part 1) are
   `StateFlow`s mirroring that DataStore. No `_state` touch.
3. **Remote commands** — one-liners delegating to `runCommand` (lines 1898–1957),
   the single serialized, optimistic, spinner-tracked command engine.

The unifying invariant across all three: **`statusMutex` serializes every call
that talks to the vehicle backend** (Blue Link 502s on overlapping requests for
one account), and **`_state` is the only source of truth the Compose UI observes.**

---

## 2. Public surface (this range)

All are members of `AppViewModel`. `fun x() = viewModelScope.launch { … }` returns
a `Job` but callers ignore it. "Optimistic" below means it writes `_state` before
the async persist completes.

### Quick-Settings-tile setters (tail)
- **`fun setTileClimateTarget(index: Int, target: String)`** (1595–1605) — sets what
  the climate tile at `index` runs: `"default"`, `"smart"`, or a preset id.
  Copies `tileClimateTargets` list, replaces the slot if in-bounds, persists via
  `settingsStore.setTileClimateTarget`, then `BlooTileService.requestUpdates`.
- **`fun setTileBackground(value: Boolean)`** (1615–1621) — global toggle: tiles run
  the command in the background vs. open the app. `_state` + persist + tile poke.
- **`fun setTileLiveRefresh(value: Boolean)`** (1624–1630) — global toggle: tapping a
  tile also kicks a throttled status refresh. Same three-step shape.

> Note: `setTileAssignment`/`setTileLabel` sit just above line 1600 (Part 2) and
> share the identical "update list slot → persist → `BlooTileService.requestUpdates`"
> pattern. The three tile setters in this range read the same way.

### Per-car layout setters
- **`fun setHotspot(v: Vehicle, section: String?)`** (1633–1640) — pin (or clear with
  `null`) a pebble to the dual-column "hot spot". Mutates `hotspotSections` map
  (remove on null), persists `settingsStore.setHotspot`.
- **`fun setSectionOrder(v: Vehicle, order: List<String>)`** (1643–1646) — persist a
  new drag-and-drop pebble order for a car. `_state.sectionOrders` + persist.
  (The `init` collector on `sectionOrders` re-publishes settings to the watch.)

### EV trips
- **`fun loadTrips(v: Vehicle)`** (1649–1669) — lazily fetch recent EV trips once per
  session. Guarded by `v.vin in trips || isPending(vin,"trips")`. Adds
  `"$vin:trips"` to `pending`, calls `repoFor(v).trips(v)` in `runCatching`,
  filters to trips with `distance != null && distance > 0`, and **only caches on
  success** (a failure leaves the VIN absent so it can retry). See §8.

### Climate draft + presets
- **`suspend fun loadSavedClimate(v: Vehicle): ClimateRequest?`** (1672) — returns the
  last-used climate settings for a car (`settingsStore.savedClimate(vin)`), null if
  never saved. The only `suspend` public function in this range.
- **`fun saveClimateDebounced(v, req: ClimateRequest, activePresetId: String?)`**
  (1683–1690) — 400ms-debounced persist + watch-mirror of the live climate draft.
  Cancels the previous per-VIN job in `climateSaveJobs`, then delays 400ms, calls
  `settingsStore.saveClimate` and `publishClimateState`. Lives in `viewModelScope`
  (not a composition-scoped effect) on purpose — see §8.
- **`fun saveClimatePreset(v, name: String, req: ClimateRequest)`** (1702–1712) —
  create a new named preset with a `System.currentTimeMillis().toString()` id and
  `name.trim().ifBlank { "Preset" }`; optimistic append to `climatePresets[vin]`,
  persist `settingsStore.saveClimatePreset`.
- **`fun deleteClimatePreset(v, id: String)`** (1715–1721) — remove preset by id
  (filter), optimistic + persist.
- **`fun reorderClimatePresets(v, ordered: List<ClimatePreset>)`** (1724–1727) —
  persist a new preset order, optimistic + persist.
- **`fun publishClimateState(vin: String, presetId: String?, req: ClimateRequest)`**
  (1734–1752) — mirror this car's live climate draft + active preset to the watch.
  Builds a `ClimateSync`, **skips the write if `climateSync[vin] == cs`** (loop
  breaker so watch→phone state doesn't echo back), else updates `_state.climateSync`
  and calls `WearBridge.publishClimate`. Seat levels cross the wire as
  `SeatLevel.apiValue` ints (see §4/§6).

### Location
- **`fun locate(v: Vehicle)`** (1754–1793) — routes through `runCommand` with action
  `"locate"`, message `"Location updated"`, `optimistic = null`. The block does a
  status refresh (GPS rides along for free), falls back to the rate-limited
  `repoFor(v).location(v)` only if the status carried no coords, reverse-geocodes,
  and loads car weather. Detailed control flow in §3.

### Remote commands (all delegate to `runCommand`)
- **`fun lock(v)`** (1839) — action `"doors"`, optimistic `doorLock = true`, calls
  `repoFor(v).lock(v)`.
- **`fun unlock(v)`** (1840) — action `"doors"`, optimistic `doorLock = false`,
  `repoFor(v).unlock(v)`.
- **`fun flashLights(v)`** (1847) — action `"hornLights"`, `optimistic = null`.
- **`fun hornAndLights(v)`** (1848) — action `"hornLights"`, `optimistic = null`.
- **`fun stopClimate(v)`** (1852–1853) — action `"climate"`, optimistic
  `airCtrlOn = false`, `repoFor(v).stopClimate(v)`.
- **`fun startClimate(v, req: ClimateRequest)`** (1858–1861) — action `"climate"`,
  optimistic `airCtrlOn = true`, message `"Climate on (${req.tempF}°F)"`,
  `repoFor(v).startClimate(v, req)`.
- **`fun startCharge(v)`** (1872–1875) — action `"charge"`, optimistic
  `evStatus = evStatus?.copy(batteryCharge = true)`, calls
  `repoFor(v).startCharge(electric(v))`.
- **`fun stopCharge(v)`** (1879–1882) — action `"charge"`, optimistic
  `batteryCharge = false`, `repoFor(v).stopCharge(electric(v))`.
- **`fun setChargeLimits(v, acPercent: Int, dcPercent: Int)`** (1889–1892) — action
  `"chargeLimit"` (distinct key from `"charge"`), `optimistic = null`,
  `repoFor(v).setChargeTargets(electric(v), acPercent, dcPercent)`.

### Settings / navigation
- **`fun openSettings()`** (1963) — `screen = Screen.Settings`.
- **`fun closeSettings()`** (1964–1973) — back to `Garage` (or `Empty` if no
  vehicles), clears `expandedIndex` and `showSettingsCoach`.

### Appearance / preference setters (DataStore-only unless noted)
- **`fun setThemeMode(mode: ThemeMode)`** (1989–1992) — persist + best-effort
  `BlooWidget().updateAll` (widget isn't reactive to the DataStore Flow).
- **`fun setFontChoice(choice: FontChoice)`** (1993).
- **`fun setDynamicColor(enabled: Boolean)`** (1999–2002) — persist; **if enabling,
  `settingsStore.clearAllCarPaletteIds()`** so stale per-car fixed palettes don't
  resurrect later.
- **`fun setColorPalette(palette: ColorPalette)`** (2003).
- **`fun saveCustomPalette(palette: CustomPaletteData)`** (2004).
- **`fun deleteCustomPalette(id: String)`** (2005).
- **`fun setActiveCustomPaletteId(id: String?)`** (2006).
- **`fun setCarPaletteId(vin: String, paletteId: String?)`** (2007).
- **`fun setColumnsFlipped(flipped: Boolean)`** (2238).
- **`fun setLinksInApp(value: Boolean)`** (2241).
- **`fun setUiScaleSoon(value: Float)`** (2248–2249) — deferred slider commit
  (recomposes the whole app via `LocalDensity`); in `viewModelScope` so closing
  Settings mid-animation can't drop it.
- **`fun setVibrancySoon(value: Float)`** (2250–2251) — same rationale (colorScheme).
- **`fun setHapticsEnabled(value: Boolean)`** (2252).
- **`fun setPebbleOutline(value: Boolean)`** (2260).
- **`fun setAuroraBackground(value: Boolean)`** (2262).
- **`fun setAuroraMotion(value: String)`** (2264).
- **`fun setAuroraColorMode(value: String)`** (2266).
- **`fun setAuroraCustomColor(value: String?)`** (2268).
- **`fun setUnitSystem(value: String)`** (2271) — imperial vs metric.
- **`fun setSettingsMode(mode: String)`** (2291–2301) — `"simple"`/`"advanced"`;
  optimistic `_state.settingsMode` + persist + **explicit** `WearBridge.publishSettings`
  (settingsMode isn't part of `Appearance`).
- **`fun setDefaultClimatePreset(vin: String, id: String?)`** (2306–2308) — persist
  only; read back in `bootstrapDriveSync` into `defaultClimatePresets`.

### Import / export / Drive sync
- **`fun exportSettings(context: Context)`** (2016–2044) — writes
  `settingsStore.exportSettingsJson()` to a cache file, shares it as a real
  `application/json` file via `FileProvider` + `ACTION_SEND` chooser. See §3.
- **`fun importSettings(context, uri: Uri)`** (2047–2070) — read the picked JSON,
  `settingsStore.importSettingsJson(json)`, set snackbar (success/error type),
  and on success `refreshLocalCarConfig()` + push settings to watch.
- **`fun setSyncUri(uri: Uri)`** (2073–2097) — take a **persistable** R/W URI grant;
  refuse to enable sync if the grant fails; persist, update `_state.syncUri`,
  then `runDriveSyncNow()`.
- **`fun clearSyncUri()`** (2100–2108) — disable auto-sync; also clears in-memory
  and persisted `lastSyncError`.
- **`fun importSettingsAndSync(context, uri: Uri)`** (2111–2166) — "join an existing
  sync" flow. Import (import failure treated as "nothing to import", not an error),
  take persistable grant, enable sync, `refreshLocalCarConfig` if imported,
  `runDriveSyncNow()`. See §3/§8.
- **`fun setSyncWifiOnly(wifiOnly: Boolean)`** (2169–2173) — persist + `_state`.
- **`fun retryDriveSync()`** (2313–2315) — manual "Sync now"; launches `runDriveSyncNow`.

### Weather
- **`fun clearWeatherLocation()`** (2179–2182) — clear saved home lat/lon/label and
  drop `homeWeather`.
- **`fun setWeatherPlace(query: String)`** (2185–2201) — forward-geocode a place name,
  save as weather location, `loadHomeWeather(force = true)`.
- **`fun useDeviceLocationForWeather()`** (2204–2211) — use device last-known location.
- **`fun loadHomeWeather(force: Boolean = false)`** (2214–2225) — fetch weather for
  the configured home location, TTL-gated by `WEATHER_TTL_MS` unless `force`.
- **`fun loadCarWeather(v: Vehicle, force: Boolean = false)`** (2228–2235) — fetch
  weather at a car's last-known location; same TTL gate keyed per-VIN.

### Log / message / error plumbing
- **`fun clearLogs()`** (2275) — `AppLog.clear()`.
- **`fun clearMessage()`** (2277) — `message = null`.
- **`fun reportError(msg: String)`** (2280–2283) — log + `message`, `messageType="error"`.
- **`fun reportInfo(msg: String)`** (2286–2288) — `message`, `messageType="info"`.

### Private helpers (this range)
- **`private fun electric(v: Vehicle): Vehicle`** (1832–1833) — returns `v.copy(isEv=true)`
  if `hasBattery(v)`, else `v`. Forces EV endpoints for user-declared PHEVs.
- **`private fun runCommand(vin, action, success, optimistic, block)`** (1898–1957) —
  the command engine. See §3.
- **`private suspend fun reverseGeocode(loc: GeoLocation): String?`** (1801–1807) —
  lat/lon → place name via `Geocoder` on `Dispatchers.IO`, `runCatching`→null.
- **`private suspend fun runDriveSyncNow()`** (2327–2333) — one `performDriveSync`
  pass now, fold outcome into `_state`; `refreshLocalCarConfig` if it imported.
- **`private fun launchBusy(block: suspend () -> Unit)`** (2345–2358) — the app-wide
  loading spinner wrapper: `loading=true` + clear message, run block, catch→snackbar,
  `finally { loading=false }`.
- **`private val climateSaveJobs = mutableMapOf<String, Job>()`** (1674) — per-VIN
  debounce job map for `saveClimateDebounced`.

---

## 3. Internal structure & control flow

### `runCommand` (1898–1957) — the command engine

Signature:
```kotlin
private fun runCommand(
    vin: String,
    action: String,
    success: String,
    optimistic: ((VehicleStatus) -> VehicleStatus)?,
    block: suspend () -> Unit,
)
```
`key = "$vin:$action"`. Launches in `viewModelScope`:

1. `startedAt = System.currentTimeMillis()`.
2. Add `key` to `pending`, clear `message` (1908).
3. **Optimistic pre-apply** (1911–1918): if `optimistic != null` and a status
   exists for `vin`, replace `statuses[vin]` with `optimistic(current)`, then
   `persistSnapshots()` so the widget reflects the expected outcome *before* the
   network round-trip. If no status yet, leaves it unchanged.
4. `try`: `statusMutex.withLock { block() }` (1922) — serialized against status
   fetches and every other command process-wide.
5. On success: `AppLog.log(success)`; **re-apply/confirm** the optimistic patch
   (1925–1932, in case the status arrived between steps 3 and 5); `persistSnapshots()`;
   best-effort `BlooTileService.requestUpdates`; `autoSummarize` for the car.
6. `catch (e: Exception)` (1938–1945): log `"⚠ $msg"`, set `message = msg`, and
   **schedule a `refreshStatus(v)` to roll back the optimistic flip** with real data.
7. `finally` (1946–1955): enforce **`MIN_COMMAND_LOCK_MS` (3000ms)** minimum lock —
   if `elapsed < MIN_COMMAND_LOCK_MS`, `delay` the remainder before removing `key`
   from `pending`. Blocks a fast double-tap that would 502 the backend.

Note the failure rollback is *not* a direct state restore — it fires a fresh
`refreshStatus`, which re-fetches and overwrites the optimistic status with reality.
The `pending` key is still cleared in `finally` regardless.

### `locate` (1754–1793)

Wrapped in `runCommand(vin, "locate", "Location updated", optimistic = null)`. Block:
1. `repoFor(v).status(v, refresh = true)` — a status refresh; write status into
   `_state` (1759–1761).
2. Extract `statusLoc` from `s.vehicleLocation.coord` (lat/lon both non-null →
   `GeoLocation(lat, lon, speed)`), else null.
3. `hadCached = locations[vin] != null`.
4. `loc = statusLoc ?: try { repoFor(v).location(v) } catch (BlueLinkException) { if (hadCached) null else throw }`
   — only calls the rate-limited `findMyCar`/`location` if status had no GPS; if
   that fails but we already show a fix, swallow it.
5. Three-way `when` (1776–1792):
   - `loc != null` → update `locations`, `reverseGeocode` → `placeNames`,
     `loadCarWeather(force=true)`, `persistCache()`.
   - `hadCached` → snackbar "Showing last-known location — a live locate is over
     today's limit."
   - else → `throw BlueLinkException(...)` (bubbles to `runCommand`'s catch → snackbar).

### `exportSettings` (2016–2044)

1. `json = settingsStore.exportSettingsJson()`.
2. On `Dispatchers.IO`: mkdir `cacheDir/exports`, write `bloo_settings_backup.json`,
   `FileProvider.getUriForFile(ctx, "${packageName}.fileprovider", file)`.
3. If uri null → snackbar "Couldn't prepare the backup file", return.
4. Build `ACTION_SEND` intent (`application/json`, `EXTRA_STREAM`, subject,
   `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`), start a chooser.
   Deliberately a **file** share, not `EXTRA_TEXT` (Drive/Files/email reject text).

### `importSettingsAndSync` (2111–2166)

The subtle one. Reads the picked file; `importError = json?.let { importSettingsJson(it) }`.
Crucially it **does not surface import failure as an error** — the common case here
is picking a fresh empty Drive file to use as the sync target, so a failed import is
logged and treated as "nothing to import yet." `imported = json != null && importError == null`.
Then take persistable grant (refuse sync if it fails, but still honestly report a
successful import), persist `syncUri`, `refreshLocalCarConfig()` if imported, and
`runDriveSyncNow()` so the device's real settings reach the file immediately.

### `runDriveSyncNow` (2327–2333)

`outcome = withContext(Dispatchers.IO) { settingsStore.performDriveSync() }`; if
`outcome.ran`, `refreshLocalCarConfig()` when `outcome.imported`, then fold
`lastSyncMs`/`syncError` into `_state`. This is the *active* counterpart to the
*passive* refreshing-transition collector installed in `bootstrapDriveSync` (Part 2);
both call the single `performDriveSync` implementation.

---

## 4. Data & types

No data class / enum / sealed type is **defined** in this range. Types used here:

- **`ClimateRequest`** (`com.bloo.bluelink.data`) — fields referenced:
  `tempF: Int` (°F), `durationMinutes: Int`, `defrost: Boolean`,
  `steeringWheelHeat`, and four `SeatLevel` seats: `seatFrontLeft`,
  `seatFrontRight`, `seatRearLeft`, `seatRearRight` (each read via `.apiValue`).
  Defaults `DEFAULT_CLIMATE_TEMP_F` / `DEFAULT_CLIMATE_DURATION_MIN` are used by the
  shortcut path (Part 2, line 940) not this range.
- **`ClimatePreset`** — `id: String` (timestamp-based, created at 1704),
  `name: String`, `request: ClimateRequest`.
- **`ClimateSync`** (`com.bloo.bluelink.data`) — built at 1735–1745 with:
  `activePresetId: String?`, `tempF`, `durationMinutes`, `defrost`,
  `steering` (= `req.steeringWheelHeat`), and four seat ints
  `seatFrontLeft/Right`, `seatRearLeft/Right` = each seat's `SeatLevel.apiValue`.
  **`SeatLevel.apiValue` encoding (domain fact): 0=off, 3–5=cool, 6–8=heat; crosses
  the wear wire as ints.**
- **`WearClimateState(merged: Map<String, ClimateSync>)`** — wrapper passed to
  `WearBridge.publishClimate` (1749–1751).
- **`GeoLocation(latitude, longitude, speed?)`** — constructed from status coords in
  `locate`; `speed` from `vehicleLocation.speed.value`.
- **`VehicleStatus`** — patched by optimistic lambdas: `doorLock`, `airCtrlOn`,
  and nested `evStatus?.copy(batteryCharge = …)`. The nested copy is null-safe: if
  `evStatus` is null the patch is a no-op (1871 comment).
- **`Weather`** — TTL-gated by `fetchedAt` against `WEATHER_TTL_MS`.
- **`ThemeMode`, `FontChoice`, `ColorPalette`, `CustomPaletteData`** — appearance
  enums/data classes defined elsewhere in `com.bloo.bluelink.ui`; passed straight
  through to `SettingsStore`.

Module-level constant (top of file, but governs this range):
- **`private const val WEATHER_TTL_MS = 15 * 60 * 1000L`** (line 61) — 15 min.
- **`private const val MIN_COMMAND_LOCK_MS = 3000L`** (line 265) — command double-tap lock.

Tile encodings: `tileClimateTargets` slots hold `"default"`/`"smart"`/preset-id
strings; `tileConfigs` are `Pair<vin, cmd>?`. `TILE_COUNT` drives list sizing (Part 2).

---

## 5. State & concurrency

- **`_state: MutableStateFlow<UiState>`** is the single observable source of truth;
  every UI-visible change here goes through `_state.update { it.copy(...) }`. Reads
  use `_state.value`.
- **DataStore** via `settingsStore` (`SettingsStore`) is the durable store; appearance
  setters rely on the `appearance`/`notifications` `StateFlow`s (Part 1) re-emitting
  after a write, so they never touch `_state`.
- **Scope/dispatcher**: everything launches in `viewModelScope` (cancelled on
  `onCleared`). Blocking work is pushed to `Dispatchers.IO`:
  `reverseGeocode` (1801), forward-geocode in `setWeatherPlace` (2188),
  file read/write in import/export (2018/2048/2112), `performDriveSync` (2328).
- **`statusMutex`** (`BlueLinkGate.statusMutex`, process-wide, shared with the
  background worker) wraps **every backend call**: `runCommand`'s `block` (1922) and
  `locate`'s status/location calls run inside it. Guarantees strictly sequential
  telematics traffic.
- **`climateSaveJobs: Map<vin, Job>`** — per-VIN debounce; `saveClimateDebounced`
  cancels the prior job before launching a new 400ms-delayed one.
- **`MIN_COMMAND_LOCK_MS` self-throttle** in `runCommand.finally` keeps a control's
  `pending` key set ≥3s so a double-tap can't overlap requests.
- **Recomposition triggers**: any `_state.copy` of a field the UI reads (`pending`,
  `statuses`, `climatePresets`, `syncUri`, `homeWeather`, `message`, …). The
  `pending` set is keyed `"vin:action"` so each control shows its own spinner
  independently (`UiState.isPending`).

---

## 6. Collaborators & data flow

**Calls out to:**
- `repoFor(v): VehicleRepository` → `lock/unlock/flashLights/hornAndLights/
  startClimate/stopClimate/startCharge/stopCharge/setChargeTargets/status/location/trips`.
  (`electric(v)` passed to the three EV-only charge endpoints.)
- `SettingsStore` — dozens of `setX`/getters (tiles, hotspot, section order, climate
  presets, appearance, sync uri, weather location, `exportSettingsJson`,
  `importSettingsJson`, `performDriveSync`, `savedClimate`, `saveClimate`).
- `WearBridge` — `publishClimate` (1749), `publishSettings` (2068, 2299), and the
  `init`-block collectors mirror presets/settings automatically.
  **Wear Data Layer paths involved:** `climate` (via `publishClimate`) and `settings`
  (via `publishSettings`); presets flow on `presets` from the `init` collector.
- `BlooTileService.requestUpdates` (QS tiles) — after tile setters and successful
  commands.
- `BlooWidget().updateAll` — after theme change (1991) and inside `persistSnapshots`.
- `Notifications` / `CarAlerts` — via `checkAlerts` (Part 2), reached from `loadStatus`.
- `Geocoder` — reverse (locate) and forward (`setWeatherPlace`) geocoding.
- `WeatherApi.fetch(lat, lon)` — home + per-car weather.
- `FileProvider` + `Intent` (`ACTION_SEND`, `ACTION_VIEW`) — export share, APK install
  (APK install is Part 2's `launchApkInstaller`).
- `contentResolver.takePersistableUriPermission` / `openInputStream` — Drive sync grants
  and import reads.
- `AppLog.log` — activity log surfaced in Settings.

**Called by:** the Compose UI (garage card controls, Settings screens, climate
pebble, weather pebble), plus indirect entry points: `handleShortcut` /
`tryRunPendingShortcut` (Part 2) call `lock/unlock/startClimate/stopClimate/locate/
openOemApp`; QS tiles and widgets ultimately drive commands via those.

**Data in:** `Vehicle`, `ClimateRequest`, user text (place query, preset name), picked
`Uri`s, JSON backups. **Data out:** `_state` (→ UI), DataStore (→ durable),
snapshots (→ widget/watch/tiles via `persistSnapshots`), Drive file (via
`performDriveSync`), system notifications, share/install intents.

---

## 7. Invariants & assumptions

- **`statusMutex` must wrap every backend call** — commands and locate both hold it;
  breaking this reintroduces the "a previous request is pending" 502.
- **Optimistic patches assume a status already exists** for the VIN; if not, the patch
  is skipped (both pre-apply at 1913 and confirm at 1926 guard on `statuses[vin] != null`).
- **`evStatus?.copy` null-safety**: `startCharge`/`stopCharge` patches are safe no-ops
  when `evStatus` is null.
- **Charge/limit endpoints require `electric(v)`** — a user-declared PHEV that the API
  reports as gas (`isEv=false`) would hit the wrong ICE endpoint without the forced
  `copy(isEv=true)`.
- **`publishClimateState` idempotence** — the `climateSync[vin] == cs` early-return is
  what prevents an infinite phone↔watch echo loop; equality on `ClimateSync` must be
  meaningful (data class).
- **Drive sync requires a *persisted* URI grant** — both `setSyncUri` and
  `importSettingsAndSync` refuse to enable sync if `takePersistableUriPermission`
  fails, because a session-only grant would silently break after process death.
- **`loadTrips` gating**: presence of the VIN key in `trips` is what blocks a re-fetch;
  therefore it must only be inserted on success (see §8).
- **Preset ids are timestamp strings** — assumed unique enough that two presets never
  collide even with identical names.
- **`MIN_COMMAND_LOCK_MS = 3000`** — the minimum a control stays disabled; assumes 3s
  is enough spacing for the backend.
- **`_state.value.vehicles`** is assumed populated when commands fire (the UI only
  shows controls once the garage loaded); `runCommand`'s auto-summarize/rollback use
  `vehicles.firstOrNull { it.vin == vin }` defensively.

---

## 8. Gotchas & sharp edges

- **`runCommand` failure "rollback" is a refresh, not a restore.** On error it
  schedules `refreshStatus(v)` (a nested `viewModelScope.launch`) rather than undoing
  the optimistic `_state` edit — reality overwrites the guess. If the refresh itself
  fails, the optimistic (possibly wrong) status can linger until the next successful
  fetch. (1942–1945)
- **The optimistic patch is applied twice** (pre-network at 1911, re-confirmed at
  1925) to cover the case where a real status arrives *between* the two points; the
  second application re-derives from `statuses.getValue(vin)`.
- **`MIN_COMMAND_LOCK_MS` delay lives in `finally`** — even an instant network success
  keeps the control disabled ≥3s. This is intentional anti-double-tap, but means the
  spinner visibly lingers after fast commands.
- **`loadTrips` deliberately does NOT cache `emptyList()` on failure.** A transient
  network blip that returned nothing used to stick the car at "no trips" for the whole
  session with no retry, because the VIN key's mere presence gates re-fetch. Now only
  a non-null successful fetch is stored, and it's filtered to `distance > 0`. (1653–1667)
- **`saveClimateDebounced` runs in `viewModelScope`, not a `LaunchedEffect`.** A
  composition-scoped debounce got cancelled when the ClimatePebble left composition
  (car switch, collapse, cover-screen swipe), silently dropping the last 400ms of edits
  and reverting sliders to the stale persisted value. (1676–1690)
- **`publishClimateState` loop-breaker** — without the `== cs` guard, state received
  *from* the watch would echo straight back and ping-pong. (1746)
- **`importSettingsAndSync` swallows import errors on purpose.** The common "join sync"
  file is a fresh/empty Drive file; treating its failed import as an error read as
  "something broke." It logs and continues; the next successful push overwrites the
  file. Contrast with `importSettings` (2047), which *does* surface the error as a
  red snackbar. (2115–2131)
- **`setDynamicColor(true)` wipes all per-car palette ids** so switching back to a
  fixed palette later doesn't resurrect a stale per-car choice. (1999–2002)
- **`setThemeMode` / `persistSnapshots` nudge the widget explicitly** because the
  home-screen widget renders with its own non-reactive theme resolution and won't
  repaint from the DataStore Flow alone. Wrapped in `runCatching` — a missing widget
  instance isn't an error. (1991)
- **Several setters must publish to the watch *explicitly*** because their field isn't
  part of `Appearance` (so the `init` `appearance.collect` mirror doesn't cover them):
  `setSettingsMode` (2299), `setSectionHidden`/`setAiEnabled` (Part 2). Miss this and
  the watch silently diverges until an unrelated push.
- **`locate` prefers status-carried GPS over `findMyCar`.** `findMyCar`/`location` is
  the rate-limited call that throws "exceeded the daily remote service request limit";
  the code only falls back to it when status had no coords, and even then suppresses
  the error if a cached fix exists. (1768–1792)
- **`exportSettings` shares a real file, not `EXTRA_TEXT`.** Text-only shares are
  silently rejected by Drive/Files/email, which would defeat "Restore". (2010–2015)
- **`runDriveSyncNow` vs the passive collector** — `setSyncUri`/`importSettingsAndSync`
  call `runDriveSyncNow()` immediately because the passive refreshing-transition
  collector (Part 2's `bootstrapDriveSync`) only fires on the *next* data refresh,
  which might never happen in a session that backgrounds right after setup — leaving a
  second device to find an empty file. (2317–2326)
- **`launchBusy` is the only thing standing between a thrown command and a stuck
  spinner** — its `finally` always clears `loading`. Note commands use per-action
  `pending` instead of `launchBusy`; auth/garage-load use `launchBusy`.
