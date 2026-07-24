# AppViewModel — Part 2: status, AI, commands config (lines 800–1600)

File: `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\ui\AppViewModel.kt`
Class: `class AppViewModel(app: Application) : AndroidViewModel(app)` (declared at `AppViewModel.kt:267`).

This document covers the **middle slice** of `AppViewModel` — roughly `AppViewModel.kt:804`–`1600`. That slice contains: local-config re-hydration, the Drive auto-sync bootstrap, the whole status-fetch machinery (`ensureStatus`/`refreshStatus`/`loadStatus`), snapshot persistence, self-update download/install, on-device AI (Gemini Nano) summaries + Q&A, and the setters that configure app-icon shortcuts and Quick Settings tiles. The surrounding parts (auth, `UiState` definition, remote commands, weather, settings/Drive setters) are documented elsewhere; where a function in this slice calls into them, the collaborator is noted.

---

## 1. Purpose

`AppViewModel` is the single `AndroidViewModel` behind the entire phone UI. It owns one `MutableStateFlow<UiState>` (`_state`, `AppViewModel.kt:295`) and every screen composes off `state` (`AppViewModel.kt:296`). This slice is the part that:

1. **Turns a signed-in account into a live, self-refreshing garage.** After `loadGarageInner` (earlier in the file) populates `vehicles`, this slice runs the status fetches (`ensureStatus`/`loadStatus`), reverse-geocodes locations, checks alerts, persists a disk cache + snapshots for widgets/tiles/watch, and re-hydrates local per-car config after a settings import.
2. **Keeps the app up to date** without the Play Store — `checkForUpdate`, `downloadUpdateInBackground`, `installDownloadedUpdate`.
3. **Bootstraps bidirectional Google Drive settings sync** exactly once per ViewModel (`bootstrapDriveSync`).
4. **Runs on-device Gemini Nano** to summarize a car's state or answer free-form questions (`autoSummarize`, `summarizeCar`, `askAi`, `carText`, `summaryPrompt`).
5. **Persists UI configuration** for shortcuts, Quick Settings tiles, pebbles, seats, and per-car metadata, with the standard "update `_state` optimistically, persist to `SettingsStore` async, poke the external surface (widget/tile/watch)" pattern.

The overarching reason it all lives in one ViewModel: **all vehicle-status and command traffic for one account must be serialized process-wide** (Blue Link 502s on overlapping requests), so a single owner holds `statusMutex` and de-dupes in-flight VINs.

---

## 2. Public surface (functions in this slice)

Signatures below are the real declarations. "Optimistic" means `_state` is updated before the async persist completes.

### Garage navigation & status

- **`fun selectIndex(index: Int)`** (`AppViewModel.kt:901`) — Swipe/select a car. Gets `vehicles.getOrNull(index)` (returns silently if out of range), sets `currentIndex`, persists `settingsStore.setLastVehicleVin(v.vin)` async, and calls `ensureStatus(v)` (lazy: only fetches if not already fetched this session).
- **`fun expand(index: Int)`** (`AppViewModel.kt:910`) — Large-screen only. Sets both `expandedIndex = index` and `currentIndex = index` together (comment at 909: keeps the two indices agreeing about "current").
- **`fun collapse()`** (`AppViewModel.kt:912`) — `expandedIndex = null`; back to the grid.
- **`fun handleShortcut(vin: String, cmd: String)`** (`AppViewModel.kt:918`) — Entry point for an app-icon long-press shortcut (or "open app + run" tile). Stashes `pendingShortcut = vin to cmd` and calls `tryRunPendingShortcut()`.
- **`fun refreshStatus(v: Vehicle)`** (`AppViewModel.kt:976`) — User-triggered pull-to-refresh: `loadStatus(v, refresh = true, …, surfaceErrors = true)` (shows the spinner, surfaces errors, logs success), then piggybacks `checkForUpdate()` (internally debounced).
- **`fun reorderVehicles(order: List<Vehicle>)`** (`AppViewModel.kt:1093`) — Persist a new car display order (drag-and-drop). Preserves the *selected car* across the reorder (not the selected index — see §8), then async writes `settingsStore.setVehicleOrder(order.map{it.vin})` and re-persists snapshots in the new order.

### Per-car metadata setters (optimistic `_state` + async `SettingsStore` write)

- **`fun setVehicleImage(vin: String, url: String)`** (`AppViewModel.kt:1170`) — Set/clear (blank = clear) a custom car photo URL. Trims on set.
- **`fun setLicensePlate(vin: String, plate: String)`** (`AppViewModel.kt:1179`) — Set/clear plate; **also re-persists snapshots immediately** so the watch Info tile updates without waiting for a status refresh (comment 1186–1188).
- **`fun setLastServiceMiles(vin: String, miles: Int?)`** (`AppViewModel.kt:1192`) — Set/clear last-service odometer; re-persists snapshots.
- **`fun setServiceIntervalMiles(vin: String, miles: Int?)`** (`AppViewModel.kt:1203`) — Set/clear service interval; re-persists snapshots.
- **`fun setSeatFlag(v: Vehicle, field: String, value: Boolean)`** (`AppViewModel.kt:1222`) — Toggle one seat/steering capability flag. `field` is a short code mapped to a `SeatConfig` property (see §4). Unrecognized code = no-op (`else -> current`).
- **`fun setPowertrain(v: Vehicle, value: Powertrain)`** (`AppViewModel.kt:1294`) — User's manual powertrain override. Optimistic; **re-persists snapshots immediately** because the watch Charge/Fuel tile derives `hasBattery` from the synced snapshot (comment 1296–1298).

### Pebble (detail-section) config

- **`fun togglePebble(v: Vehicle, section: String)`** (`AppViewModel.kt:1241`) — Open/close a pebble. Computes `collapsedNow = key !in collapsedPebbles`, patches `collapsedPebbles`, persists `settingsStore.setSectionCollapsed(vin, section, collapsedNow)`.
- **`fun setSectionHidden(v: Vehicle, section: String, hidden: Boolean)`** (`AppViewModel.kt:1253`) — Show/hide a non-essential pebble. Persists, **then explicitly** `WearBridge.publishSettings(...)` because `hiddenSections` isn't part of `Appearance` and so isn't covered by the `appearance.collect` mirror in `init` (comment 1260–1262).
- **`fun setHotspot(v: Vehicle, section: String?)`** (`AppViewModel.kt:1633`, just past the slice) — Pin/clear the dual-column hot-spot pebble.
- **`fun setSectionOrder(v: Vehicle, order: List<String>)`** (`AppViewModel.kt:1643`) — Persist drag-and-drop pebble order.

### Onboarding / hints

- **`fun finishOnboarding()`** (`AppViewModel.kt:1268`) — Mark onboarding seen + every current VIN configured (async), then set screen to `Garage` (or `Empty` if no vehicles) and clear `garageLoadError`.
- **`fun finishCarSetup(vins: List<String>)`** (`AppViewModel.kt:1280`) — Mark the given VINs configured (async), same screen transition.
- **`fun dismissSettingsHint()`** (`AppViewModel.kt:1289`) — Clear the post-onboarding nudge (in-memory only).
- **`fun dismissSettingsCoach()`** (`AppViewModel.kt:1292`) — Clear the back-arrow coach mark (in-memory only).

### Self-update (GitHub Releases; not on Play Store)

- **`fun dismissUpdate()`** (`AppViewModel.kt:1309`) — "Not now": sets `updateTileDismissed = true` in memory only (leaves `updateAvailable` intact).
- **`fun snoozeUpdate()`** (`AppViewModel.kt:1313`) — "Remind me in a few days": sets `updateTileDismissed = true` and persists a snooze via `UpdateChecker.snooze(...)` that outlasts the checker's normal debounce.
- **`fun downloadUpdateInBackground()`** (`AppViewModel.kt:1349`) — First tap of the update tile's primary button. Reads `updateAvailable.run.phoneApkUrl`; if null, snackbar "No direct download…". If already downloading or ready, no-op. Otherwise sets `updateDownloading=true, updateDownloadProgress=0f`, launches `UpdateApi.downloadApk(url, dest){progress-> …}`, then clears downloading and sets `updateApkReady = ok`; failure → snackbar.
- **`fun installDownloadedUpdate()`** (`AppViewModel.kt:1372`) — Second tap. If `!updateApkReady`, no-op. If the cached APK file is gone, resets `updateApkReady=false` + snackbar. Else `launchApkInstaller(dest)`; failure → snackbar pointing at downloads folder.
- **`val currentBuildNumber: Int`** (`AppViewModel.kt:1385`) — `get() = BuildConfig.BUILD_RUN_NUMBER` (0 = local build).

### On-device AI (Gemini Nano)

- **`fun setAiEnabled(value: Boolean)`** (`AppViewModel.kt:1389`) — Optimistic `_state.aiEnabled`, persist `settingsStore.setAiEnabled`, **then** explicit `WearBridge.publishSettings` (not in `Appearance`, comment 1392–1395).
- **`fun setAiAuto(value: Boolean)`** (`AppViewModel.kt:1404`) — Optimistic `_state.aiAuto`, persist async. (No watch republish.)
- **`fun summarizeCar(v: Vehicle)`** (`AppViewModel.kt:1445`) — Manual "Summarize" tap. Requires a status already present (else snackbar "Refresh … first"); guards against a second tap via `v.vin in aiBusy`; runs `ai.summarize(summaryPrompt(v, status))` in `viewModelScope`; success writes into `aiSummaries[vin]`, failure logs + snackbar. Always clears the busy flag.
- **`fun askAi(query: String)`** (`AppViewModel.kt:1471`) — Free-form Q&A across *all* cars. No-op if `!aiEnabled` or blank. Marks `"search"` busy, builds a data blob from `carText` for every vehicle, calls `ai.summarize("Answer this question using only the data below…")`, writes result to `aiSearchReply`.
- **`fun clearAiReply()`** (`AppViewModel.kt:1486`) — `aiSearchReply = null`.

### Shortcuts & Quick Settings tiles

- **`fun setShortcutEnabled(vin: String, cmd: String, enabled: Boolean)`** (`AppViewModel.kt:1553`) — Toggle whether a car+action app-icon shortcut is shown. Computes the full universe of `"${action}_${vin}"` ids from `Shortcuts.ACTIONS × vehicles`; `current = shortcutSet ?: universe` (null means "all enabled"); adds/removes the id; optimistic `_state`; persists + `Shortcuts.refresh(...)`.
- **`fun setTileAssignment(index: Int, vin: String?, cmd: String?)`** (`AppViewModel.kt:1567`) — Assign/clear a QS tile (index within bounds). `vin!=null && cmd!=null → vin to cmd`, else `null`. Persists `settingsStore.setTileConfig` + `BlooTileService.requestUpdates`.
- **`fun setTileLabel(index: Int, label: String?)`** (`AppViewModel.kt:1582`) — Set/clear a tile's custom name (trimmed, blank→null). Persists + tile refresh.
- **`fun setTileClimateTarget(index: Int, target: String)`** (`AppViewModel.kt:1595`) — Set what the climate tile runs: `"default"`, `"smart"`, or a preset id. Persists + tile refresh.

(`setTileBackground`/`setTileLiveRefresh` at 1615/1624 are just past the slice but follow the identical pattern.)

### Public helpers used by this slice

- **`fun loadCarWeather(v: Vehicle, force: Boolean = false)`** (`AppViewModel.kt:2228`) — called from `locate`; documented in the weather part.

---

## 3. Internal structure & control flow

### `refreshLocalCarConfig()` — `AppViewModel.kt:804`

`private suspend fun`. Re-reads *local-only* per-car config for the currently-loaded `vehicles` and folds it into `_state` — no network. Reads, for each vehicle: `seatConfig`, `powertrain` (nullable→`mapNotNull`), `sectionOrder`, `imageUrl` (nullable), `licensePlate` (filtered to non-blank), `lastServiceMiles`/`serviceIntervalMiles` (nullable), `climatePresets`. Then one `_state.update` copying all eight maps in.

Why it exists (comment 794–803): a restored/imported settings backup writes only to `settingsStore`; without this call the already-composed UI (mid-onboarding, an open Settings screen) keeps showing pre-import values until some unrelated event triggers a full `loadGarageInner`. Called from `importSettings` (2063), `importSettingsAndSync` (2160), and `runDriveSyncNow` (2330). It's a strict subset of the local reads `loadGarageInner` does.

### `bootstrapDriveSync()` — `AppViewModel.kt:844`

`private fun`. **Guarded to run exactly once per ViewModel** via `driveSyncBootstrapped.compareAndSet(false, true)` (`AppViewModel.kt:845`) — early-returns on any subsequent call. Called from the tail of `loadGarageInner` (`AppViewModel.kt:781`), which itself can run multiple times (re-login, logout-of-one-brand).

Two independent coroutines launched:

1. **Restore coroutine (847–876):** reads `syncUri()`, `lastSyncMs()`, `lastSyncError()` from settings. If a `syncUri` exists, does a **proactive permission check** (858–868): scans `contentResolver.persistedUriPermissions` for a matching uri that still has both read+write; if the check succeeds and the grant is gone, sets `lastError = "Lost access to the Drive file — set up sync again"` and persists it. The `runCatching{…}.getOrDefault(true)` means "assume fine if the check itself throws" (comment 863). Then reads `syncWifiOnly()`, `settingsMode()`, and builds `defaultPresets` = per-VIN `defaultClimatePreset(vin) ?: "smart"`. One `_state.update` folds `syncUri, lastSyncMs, syncError, syncWifiOnly, settingsMode, defaultClimatePresets` in.

2. **Auto-sync collector (883–892):** collects `_state.map { it.refreshing }.distinctUntilChanged()`. **When `refreshing` transitions to `false`** (i.e. `!wasRefreshing`), it runs `settingsStore.performDriveSync()` on `Dispatchers.IO`; if `outcome.ran`, folds `lastSyncMs = outcome.syncedAtMs` and `syncError = outcome.error` into state. This is a download-then-upload merge pass fired whenever a refresh *settles*.

The long doc comment (829–843) explains the critical bug this fixed: this collector used to be inline in `loadStatus`, so every status fetch spawned a *new permanent* refreshing-collector, each doing a full Drive download+merge+upload; a long session accumulated unbounded collectors that all fired on every later refresh, racing concurrent writes to the same Drive file. The `AtomicBoolean` guard + single collector is the fix.

### `tryRunPendingShortcut()` — `AppViewModel.kt:923`

`private fun`. Pops `pendingShortcut` (returns if null). Finds the vehicle by VIN (returns if not loaded yet — leaving `pendingShortcut` set for a later retry after garage load; note it's already been cleared to null at 926 before the vehicle lookup, so if the vehicle isn't found the request is *dropped*, not retried — see §8 gotcha). Clears `pendingShortcut = null`, selects the car via `selectIndex(idx)`, forces `screen = Garage, expandedIndex = null` (comment 929–933: a widget/shortcut tap always means "look at this car"), then dispatches on `cmd`:

- `"doors"` → toggle: `if (status?.doorLock == true) unlock(v) else lock(v)`
- `"climate"` → toggle: if `airCtrlOn == true` `stopClimate(v)` else `startClimate(v, ClimateRequest(tempF=DEFAULT_CLIMATE_TEMP_F, defrost=false, durationMinutes=DEFAULT_CLIMATE_DURATION_MIN))`
- `"lock"`/`"unlock"`/`"locate"` → direct call
- `"bluelink"` → `openOemApp(v)`
- `"open"` → falls through (car already selected)

The toggle direction is read from the current `SnapshotStore`-derived `_state.statusFor(v)` (matches the domain fact "toggle commands decide direction by re-reading the SnapshotStore").

### `openOemApp(v)` — `AppViewModel.kt:951`

`private fun`. Resolves `brandOf(v).links.appPackage`; tries `packageManager.getLaunchIntentForPackage(...)`, else falls back to an `ACTION_VIEW` intent at `links.playStoreUrl`. Wrapped in `runCatching`; adds `FLAG_ACTIVITY_NEW_TASK`.

### `ensureStatus(v)` — `AppViewModel.kt:969`

`private fun`. The **once-per-session** fetch gate: `if (v.vin in sessionFetched) return`. Otherwise `loadStatus(v, refresh = false, errorMessage = "Couldn't load status", surfaceErrors = false)` — a silent background load (`refresh=false` → cached last-known status, doesn't burn remote quota; `surfaceErrors=false` → no spinner, no error toast, no settle haptic). Called from `loadGarageInner` for the current car then all others, and from `selectIndex`.

### `checkForUpdate()` — `AppViewModel.kt:991`

`private fun`. Launches `UpdateChecker.checkPhone(...)` (self-debounced/snoozed). Branches:
- `Available` → folds `updateAvailable = result.info`, and crucially recomputes `sameBuild = it.updateAvailable?.run?.runNumber == result.info.run.runNumber`, keeping `updateApkReady`/`updateTileDismissed` **only if same build** (a *different* build re-surfaces the tile and invalidates a stale cached APK; comment 997–1002).
- `Failed` → `Unit`, silent, retried next time.
- `UpToDate` → clears `updateAvailable=null, updateApkReady=false, updateTileDismissed=false`.

Called from `init` (cold start, 433) and every `refreshStatus` (986).

### `loadStatus(...)` — `AppViewModel.kt:1022` (the core fetch)

`private fun loadStatus(v, refresh, errorMessage, logSuccess=null, surfaceErrors=true)`. Step by step:

1. **De-dupe & in-flight bookkeeping (1029–1032):** under `synchronized(statusInFlight)`, `if (!statusInFlight.add(v.vin)) return` — a VIN already queued/running is skipped. If `surfaceErrors`, also add to `surfaceInFlight`.
2. **Spinner (1036):** only if `surfaceErrors`, set `refreshing = true`. Silent background fetches never touch `refreshing` (so no settle haptic fires — comment 1033–1035).
3. **Launch + `statusMutex.withLock` (1037–1039):** the entire network call is inside the process-wide mutex.
4. **Fetch + fold (1040–1070):** `repoFor(v).status(v, refresh)`. If non-null:
   - Extract `statusLoc` from `s.vehicleLocation?.coord` (only if both `lat` & `lon` non-null), carrying `speed?.value`. Comment 1041–1043: the status payload carries free GPS, avoiding the rate-limited `findMyCar`.
   - `_state.update`: merge `statuses[vin]=s`, `lastFetched[vin]=now`, and `locations[vin]=statusLoc` **only if non-null** (else keep old location).
   - `persistSnapshots()`, `persistCache()`, `checkAlerts(v, s)`, `autoSummarize(v)`.
   - `statusLoc?.let{ reverseGeocode(it)?.let{ place -> update placeNames } }`.
5. **Session mark + log (1072–1073):** `sessionFetched.add(v.vin)`; `logSuccess?.let{AppLog.log(it)}`.
6. **Catch (1074–1077):** log `"⚠ ${v.name}: $msg"`; only surface a snackbar `"${v.name}: $msg"` if `surfaceErrors`.
7. **Finally (1078–1088):** under `synchronized(statusInFlight)`, remove the VIN from both sets and capture `noMoreSurface = surfaceInFlight.isEmpty()`. Only clear `refreshing=false` if `noMoreSurface` — so a background fetch finishing after a user refresh can't prematurely stop the spinner/settle haptic (comment 1079–1081).

### `persistCache()` — `AppViewModel.kt:361`

`private fun`. Snapshots `_state.value` then launches `statusCache.save(statuses, locations, placeNames, lastFetched)`. Writes the live maps to disk so a cold start shows stale-but-useful data.

### `persistSnapshots(vehicles = _state.value.vehicles)` — `AppViewModel.kt:1110`

`private suspend fun`. Builds `VehicleSnapshot`s via `snapshotOf` and `snapshotStore.saveVehicles(...)`, then fans out to three external surfaces: `WearBridge.publish(...)` (watch), `BlooWidget().updateAll(...)` (home-screen widgets, in `runCatching`), `BlooTileService.requestUpdates(...)` (QS tiles).

### `snapshotOf(v, status)` — `AppViewModel.kt:1121`

`private fun → VehicleSnapshot`. Uses the **effective powertrain**: `hasBattery = _state.value.hasBattery(v)`, then `percent = status?.percentFor(hasBattery)` and `range = status?.rangeMiFor(hasBattery)` — honoring the user's manual override (a PHEV reads battery %, not fuel %; comment 1122). Fields laid out in §4. Key nuance: `fetchedAt = if (status != null) System.currentTimeMillis() else 0L` — a non-null status is fresh data; null means a placeholder snapshot with unknown fetch time (comment 1145–1147). Plate/service fields are read from `_state.value` maps, not the status.

### `applyOrder(vehicles, order)` — `AppViewModel.kt:1161`

`private fun`. Re-sorts fetched vehicles to the saved VIN order. Empty order → return as-is. Ordered = `order.mapNotNull{byVin[it]}` (drops VINs no longer present); `rest` = vehicles not in `order` (newly-added cars) appended at the end. So new cars never disappear.

### AI helpers

- **`autoSummarize(v)`** (`AppViewModel.kt:1414`, `private fun`) — Silent auto-summary. Early return unless `aiSupported && aiEnabled && aiAuto`; return if `v.vin in aiBusy` or no status. Marks busy, runs `ai.summarize(summaryPrompt(v, status))` in `viewModelScope`, folds result into `aiSummaries` or (on failure) just logs and clears busy — never surfaces a toast (comment 1409–1413). Called from `loadStatus` (1064) and after a successful command (1937).
- **`summaryPrompt(v, status)`** (`AppViewModel.kt:1494`, `private fun → String`) — `"${v.name} vehicle status:\n" + carText(v, status)`.
- **`carText(v, status)`** (`AppViewModel.kt:1501`, `private fun → String`) — Builds a compact natural-language description ordered by importance. If status null → `"…No live status has been fetched yet…"`. Otherwise appends, in order: doors locked/unlocked; **if `hasBattery(v)`** charging + time-to-full (reads `status.evStatus?.remainTime2?.atc?.value?.toInt()?.takeIf{it>0}` and formats with `fmtTimeToFull`) else "not charging"; `drivingLabel`; engine on/off; battery % (`evStatus?.batteryStatus`); fuel % **if `hasFuel(v)`**; `rangeMiFor(hasBattery)`; climate on/off; 12V starter battery (`battery?.batSoc`); odometer; last-known place; then warnings (tire pressure `tirePressureLamp?.hasWarning`, washer fluid, brake fluid, smart-key battery). Joined with spaces.
- **`fmtTimeToFull(minutes)`** (`AppViewModel.kt:1544`, `private fun → String`) — `<60` → `"$minutes minutes"`; else `"$h hour(s)"` optionally `" $m minutes"`.

---

## 4. Data & types

No new top-level types are declared in this slice (`UiState`, `Screen`, `KiaOtpUi` are all defined earlier, `AppViewModel.kt:63`–262). This slice *reads and writes* these fields; encodings worth pinning down:

### `SeatConfig` field codes (`setSeatFlag`, `AppViewModel.kt:1224–1234`)
`"dh"`=driverHeat, `"dc"`=driverCool, `"ph"`=passHeat, `"pc"`=passCool, `"rlh"`=rearLeftHeat, `"rlc"`=rearLeftCool, `"rrh"`=rearRightHeat, `"rrc"`=rearRightCool, `"sw"`=steeringWheel. Any other code → no change. These are *user-declared trim capabilities*, not API-reported (comment 1218–1221).

### `VehicleSnapshot` (built in `snapshotOf`, `AppViewModel.kt:1126–1152`)
Fields set: `vin, name, model, isEv` (raw), `hasBattery` (effective), `regId, generation, brandIndicator`, `percent = status?.percentFor(hasBattery)`, `rangeMi = status?.rangeMiFor(hasBattery)`, `locked = status?.doorLock`, `charging = status?.evStatus?.batteryCharge`, `climateOn = status?.airCtrlOn`, `engineOn = status?.engine`, `lat/lon = status?.vehicleLocation?.coord?.{lat,lon}`, `speedMph = status?.vehicleLocation?.speed?.value`, `updated = status?.dateTime`, `fetchedAt = now if status!=null else 0L`, `odometer = v.odometer`, `licensePlate/lastServiceMiles/serviceIntervalMiles` from `_state.value` maps.

### `ClimateSync` mirror (`publishClimateState`, `AppViewModel.kt:1735`, just past slice) — seat levels cross as ints via `SeatLevel.apiValue` (0=off, 3–5=cool, 6–8=heat).

### Tile config triple (in `UiState`, referenced by setters here)
`tileConfigs: List<Pair<String,String>?>` = (vin, command); `tileLabels: List<String?>`; `tileClimateTargets: List<String>` where each is `"default"`/`"smart"`/preset-id. All indexed by tile slot `0..TILE_COUNT-1`.

### Shortcut id encoding
Shortcut ids are `"${cmd}_${vin}"` (e.g. `setShortcutEnabled` builds `"${it}_${v.vin}"` at 1555, universe from `Shortcuts.ACTIONS`). `shortcutSet == null` means "all enabled" (`UiState.isShortcutEnabled` returns `?: true`).

### Update tile flags interplay (`checkForUpdate`, `downloadUpdateInBackground`, `installDownloadedUpdate`)
`updateAvailable: UpdateInfo?` (drives the tile), `updateTileDismissed`, `updateDownloading`, `updateDownloadProgress: Float?` (0–1 or null), `updateApkReady`. `run.runNumber` is the GitHub Actions build number; `run.phoneApkUrl` the direct APK download. `sameBuild` comparison keys off `run.runNumber`.

---

## 5. State & concurrency

- **Single source of truth:** `_state: MutableStateFlow<UiState>` (`AppViewModel.kt:295`), exposed as `state` (296). Every setter here uses `_state.update { it.copy(...) }` (atomic read-modify-write) — safe under concurrent coroutines.
- **Scope:** everything launches in `viewModelScope`; cancelled when the ViewModel clears. IO-bound work is wrapped in `withContext(Dispatchers.IO)` (`reverseGeocode` 1801, `performDriveSync` 886/2328, exports/imports).
- **`statusMutex`** (`AppViewModel.kt:303`) = `BlueLinkGate.statusMutex`, a **process-wide** `Mutex` shared with the background worker. Held around every `r.vehicles()`, `repoFor(v).status(...)`, and every command `block()`. This is the hard serialization guaranteeing no overlapping Blue Link requests.
- **`statusInFlight` / `surfaceInFlight`** (306/309) — plain `mutableSetOf`, guarded by `synchronized(statusInFlight)` (both add at 1029–1032 and remove at 1082–1086). De-dupes concurrent fetches of the same VIN and tracks whether any *user-visible* fetch is still running (drives `refreshing`).
- **`sessionFetched`** (312) — `Collections.synchronizedSet`; gates `ensureStatus` (network fetch happens at most once per VIN per session; cache restore doesn't add to it).
- **`driveSyncBootstrapped`** (316) — `AtomicBoolean`; `compareAndSet(false,true)` ensures exactly one Drive-sync collector.
- **`loadingGarage`** (`@Volatile`, 289) — re-entrancy guard for garage loads (checked in `loadGarageInternal`).
- **`pendingShortcut`** (`@Volatile`, 293) — a queued shortcut awaiting garage load.
- **`climateSaveJobs`** (1674) — per-VIN debounce jobs (400ms) for climate persistence.
- **`repos`** (276) — `mutableMap<Brand, VehicleRepository>`, lazily populated by `repoFor`; **not synchronized** (see §8).
- **Recomposition triggers:** any `_state` emission recomposes observers of `state`. The `init` collectors (`appearance`, `sectionOrders`, `climatePresets`, `ClimateSyncStore`, `WearExtras`, `refreshing`) are long-lived; the `refreshing`-transition collector in `bootstrapDriveSync` is the only one that fires Drive I/O.

---

## 6. Collaborators & data flow

**Repositories:** `repoFor(v)` → `VehicleRepository.{status, vehicles, trips, location, lock, unlock, startClimate, …}`. Data in: `VehicleStatus`, `GeoLocation`, `List<EvTrip>`. All serialized by `statusMutex`.

**`SettingsStore` (DataStore):** every setter in this slice reads/writes it — `seatConfig`, `powertrain`, `sectionOrder`, `imageUrl`, `licensePlate`, service miles, `climatePresets`, tile config/label/climate-target, `enabledShortcuts`, `defaultClimatePreset`, `syncUri`, `lastSyncMs`, `lastSyncError`, `syncWifiOnly`, `settingsMode`, and `performDriveSync()`. The `appearance` StateFlow (321) mirrors `settingsStore.appearance`, so most appearance setters need no `_state` write.

**`StatusCache`** (`persistCache`) and **`SnapshotStore`** (`persistSnapshots`/`snapshotOf`) — on-disk persistence surviving process death.

**Watch (Wear Data Layer) via `WearBridge`:** `publish()` (snapshots), `publishSettings()` (explicitly called by `setSectionHidden`, `setAiEnabled`, and `setSettingsMode` because those fields aren't in `Appearance`), plus `init` mirrors for extras/presets/climate. Snapshot fields cross the wire; the watch derives `hasBattery` from `VehicleSnapshot.hasBattery`.

**Home-screen widgets:** `BlooWidget().updateAll(...)` in `persistSnapshots` and `setThemeMode`.

**QS tiles:** `BlooTileService.requestUpdates(...)` from `persistSnapshots`, tile setters, and successful commands. Tiles read their own state independently of `state`, hence the explicit poke.

**App-icon shortcuts:** `Shortcuts.refresh(getApplication(), vehicles, shortcutSet)` from `loadGarageInner` and `setShortcutEnabled`. Inbound: `handleShortcut` → `tryRunPendingShortcut`.

**Self-update:** `UpdateChecker.checkPhone/snooze`, `UpdateApi.downloadApk`, `FileProvider` + `ACTION_VIEW` package-installer intent. APK lives at `cacheDir/apk/Bloo.apk` (`apkCacheFile`, 1322).

**AI:** `com.bloo.bluelink.data.Ai` (274) — `ai.isSupported()` (probed once in `init`, 419) and `ai.summarize(prompt)`.

**Geocoder:** `reverseGeocode` (1801) turns a `GeoLocation` into a place name on `Dispatchers.IO`, `runCatching`→null on failure.

**Alerts/Notifications:** `checkAlerts` (343) → `CarAlerts.evaluate` → `Notifications.post(...)` + first alert as snackbar. Called from `loadStatus`.

---

## 7. Invariants & assumptions

1. **`statusMutex` wraps every network call** that hits Blue Link (status, vehicles, commands). Overlapping calls for one account → 502. This is non-negotiable and shared with the background worker.
2. **`statusInFlight`/`surfaceInFlight` mutations happen only inside `synchronized(statusInFlight)`.** The finally block relies on this to correctly compute `noMoreSurface`.
3. **`refreshing` is only set true when `surfaceErrors=true`** and only cleared when `surfaceInFlight` is empty — a background fetch must never clear a user refresh's spinner.
4. **`bootstrapDriveSync` starts its collector at most once** (AtomicBoolean). It is only called *after* `vehicles` is populated so `defaultClimatePresets` can be built.
5. **`ensureStatus` fetches a VIN over the network at most once per session** (`sessionFetched`). A manual `refreshStatus` bypasses this (it calls `loadStatus` directly, and `loadStatus` unconditionally re-adds to `sessionFetched`).
6. **`snapshotOf` uses the *effective* powertrain** (`_state.value.hasBattery(v)`), never raw `isEv`, for percent/range — matches the `percentFor/rangeMiFor` domain contract.
7. **Snapshot `fetchedAt==0L` ⇔ placeholder** (no live status); non-zero ⇔ live data.
8. **`applyOrder` never drops a vehicle** — unknown-VIN entries in `order` are skipped, new vehicles appended.
9. **`setSectionHidden`, `setAiEnabled`, `setSettingsMode` must republish to the watch explicitly** because those fields aren't part of `Appearance` (the auto-mirror only covers `Appearance`).
10. **Update-tile continuity keys off `run.runNumber`**: the cached APK and dismissed flag survive only across identical build numbers.
11. `downloadUpdateInBackground` assumes `updateAvailable.run.phoneApkUrl` may legitimately be null (older builds without a direct APK asset).

---

## 8. Gotchas & sharp edges

- **`bootstrapDriveSync` was a serious latent leak.** The long comment (829–843) documents the pre-fix behavior: inline in `loadStatus`, it spawned a fresh permanent `refreshing` collector on *every* status fetch, each doing a full Drive round-trip, all firing simultaneously on later refreshes and racing writes to one file. If you ever move this logic, keep the single-collector + `AtomicBoolean` invariant.
- **`tryRunPendingShortcut` drops the request if the vehicle isn't loaded.** It sets `pendingShortcut = null` (line 926) *after* the null-check but the vehicle lookup at 925 returns early **without** re-queueing — so the retry path relies on `handleShortcut`/`loadGarageInner`'s explicit re-call at 785. In practice `loadGarageInner` calls `tryRunPendingShortcut()` after vehicles load, and `handleShortcut` re-sets `pendingShortcut` each tap, so a genuinely-early tap is handled by the post-load call; but note that a single stale call whose VIN never appears silently no-ops.
- **`loadStatus` silent path deliberately skips the spinner and settle haptic.** Any refactor that always sets `refreshing` would fire the settle haptic on background prefetches (all the non-current cars at garage load) — jarring.
- **`locations` is never cleared by a status fetch that lacks GPS.** `loadStatus` only *adds* `statusLoc` when non-null (1055–1057), preserving the last known fix. Same in `locate` (1758–1792), which prefers the free GPS in a status refresh and only falls back to the rate-limited `findMyCar`, and even then keeps a cached fix rather than throwing if the daily locate limit is hit.
- **`checkForUpdate`'s `sameBuild` logic is subtle:** finding the *same* build again does NOT un-dismiss the tile or invalidate a downloaded APK; finding a *different* build re-surfaces the tile (clears `updateTileDismissed`) and marks the cached APK stale (`updateApkReady && sameBuild` = false). `UpToDate` clears everything.
- **`installDownloadedUpdate` re-checks the file exists** (1375) because the cache dir can be reclaimed by the OS under storage pressure between download and install; it degrades gracefully to "tap Update to fetch again".
- **`autoSummarize` vs `summarizeCar`:** auto is fully silent (no toast, no "refresh first" nudge); manual surfaces both. Both share the `aiBusy` guard so the two can't double-run for one VIN, and a manual tap while auto is mid-flight is ignored.
- **`carText` gates battery/fuel lines on the *effective* powertrain** (`hasBattery`/`hasFuel`), so a user-marked PHEV reports battery, and a pure EV omits fuel — the AI prompt reflects the override, not raw `isEv`.
- **`reorderVehicles` preserves the selected car, not the index** (comment 1096–1099): dragging a car above the selected one used to silently swap which car the detail view showed because this was the one place mutating `vehicles` without also fixing `currentIndex`.
- **`repos` map is a plain `mutableMap` mutated from coroutines** (via `repoFor`/`getOrPut`, and `logout` removes from it). There's no synchronization; it relies on garage loads and logins not racing (which `loadingGarage` mostly guarantees for loads), but a login `repoFor` and a concurrent `loadGarageInner` iterating `repos.values` are not formally protected.
- **Several setters re-persist snapshots immediately** (`setLicensePlate`, `setLastServiceMiles`, `setServiceIntervalMiles`, `setPowertrain`) specifically so the *watch* Info/Charge tile updates without waiting for the next status refresh — a non-obvious coupling to the Wear wire.
- **`setShortcutEnabled` materializes the full "universe" of shortcut ids** on every toggle, because `shortcutSet == null` semantically means "all on"; the first toggle must expand null into the concrete set before removing one entry, or disabling one would appear to enable everything.
