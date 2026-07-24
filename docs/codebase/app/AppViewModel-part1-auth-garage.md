# AppViewModel — Part 1: State model, Auth, Init, Garage load

**File:** `app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt`
**Scope of this doc:** lines 1–800 (top-level constants, `Screen`, `UiState`, `KiaOtpUi`, the `AppViewModel` fields/init block, the whole Auth section, biometric lock, and the garage-load pipeline through `refreshLocalCarConfig`). The file is 2360 lines total; the commands/settings/weather/AI half is covered in a sibling doc. Where a symbol defined later (e.g. `launchBusy`, `runCommand`, `ensureStatus`, `loadStatus`) is called from this range, it is described only as a collaborator here.

---

## 1. Purpose

`AppViewModel` is the single `AndroidViewModel` behind Bloo's phone UI. It is the one source of truth for everything the Compose UI renders: which screen is showing, the signed-in accounts, the list of vehicles, every car's live status/location/weather/AI summary, and every user preference that has an in-memory mirror. It owns:

- **App-wide navigation** via the `Screen` sealed interface held in `UiState.screen`.
- **Multi-brand authentication** — Blue Link (Hyundai/Genesis) synchronous login, and Kia's two-step OTP dance. Any mix of brands can be signed in simultaneously (one `VehicleRepository` per brand in `repos`).
- **Cold-start auto-login** and **biometric app-lock** gating.
- **Garage loading** — merging vehicles across all signed-in brands, folding in per-VIN local config from `SettingsStore`, deciding the landing screen (onboarding / car-setup / garage / empty), and kicking status prefetches.
- A fan-out of **init-block collectors** that mirror phone state to a paired Wear OS watch.

Everything downstream (per-car commands, climate presets, settings setters, weather, on-device AI) hangs off the same `_state` `MutableStateFlow`. This part of the file establishes the state shape those later methods mutate.

---

## 2. Public surface (within lines 1–800)

### Top-level types

- **`sealed interface Screen`** (63–74) — the navigation enum. Members:
  - `data object Login` — the login form.
  - `data object Empty` — no vehicles enrolled, or still loading the first time.
  - `data object Onboarding` — first-run welcome wizard (feature setup before reaching the app).
  - `data class CarSetup(val vins: List<String>)` — feature-setup wizard for newly-detected cars after first run.
  - `data object Garage` — the main car carousel/grid.
  - `data object Settings`.

- **`data class UiState(...)`** (76–253) — the entire UI state snapshot. Every field is documented field-by-field in §4. It also exposes read-only derived helpers (public methods on the data class):
  - `statusFor(v: Vehicle): VehicleStatus?` (191) — `statuses[v.vin]`.
  - `fetchedAt(v: Vehicle): Long?` (193) — `lastFetched[v.vin]`.
  - `isPending(vin, action): Boolean` (195) — `"$vin:$action" in pending`.
  - `isPebbleExpanded(vin, section): Boolean` (197) — a pebble is expanded unless its `"$vin:$section"` key is in `collapsedPebbles` (absent = expanded).
  - `isPebbleHidden(vin, section): Boolean` (199) — key in `hiddenPebbles`.
  - `hotspotFor(vin): String?` (201) — `hotspotSections[vin]`.
  - `isShortcutEnabled(vin, cmd): Boolean` (203) — `shortcutSet?.contains("${cmd}_$vin") ?: true` (null set = all shortcuts on).
  - `seatConfigFor(v): SeatConfig` (206) — `seatConfigs[v.vin] ?: SeatConfig()`.
  - `sectionsFor(v): List<String>` (208) — `sectionOrders[v.vin] ?: DEFAULT_SECTIONS`.
  - `powertrainOf(v): Powertrain` (211) — user override in `powertrains`, else `EV` if `v.isEv` else `GAS`. **This is the "effective powertrain" used everywhere instead of raw `isEv`.**
  - `hasBattery(v): Boolean` (215) — effective powertrain is `EV` or `PHEV`.
  - `hasFuel(v): Boolean` (218) — effective powertrain is not `EV`.
  - `drivingLabel(v): String?` (225–237) — header badge. Returns `null` if charging (definitely parked). Otherwise: speed>0 → `"Driving"`; `engine==true` → `"Running"`; `engine==false` → `"Parked"`; else `null` (genuinely unknown).
  - `isDriving(v): Boolean` (241–244) — speed from location fix, falling back to `status.vehicleLocation.speed.value`, `> 0`. Used to make climate read-only while moving.
  - `powertrainLabel(v): String` (247–252) — `"Gas"`/`"Hybrid"`/`"PHEV"`/`"EV"`.

- **`data class KiaOtpUi(val challenge: KiaAuth.OtpRequired, val sentTo: String? = null)`** (259–262) — a pending Kia OTP challenge shown over the login form. `sentTo` is `"EMAIL"`/`"SMS"` once chosen, `null` while still choosing.

### Class `AppViewModel(app: Application) : AndroidViewModel(app)` (267)

Public properties/StateFlows:
- `val state: StateFlow<UiState>` (296) — the read-only view of `_state`.
- `val logs: StateFlow<List<String>>` (319) — mirrors `AppLog.lines` (copy-pasteable activity log for Settings).
- `val appearance: StateFlow<SettingsStore.Appearance>` (321–326) — `settingsStore.appearance` `stateIn` viewModelScope, `SharingStarted.Eagerly`, initial `Appearance()`.
- `val notifications: StateFlow<SettingsStore.NotificationPrefs>` (328–333) — same pattern for notification prefs.

Public functions in this range (auth/lock/garage; command/settings/AI setters are in the sibling doc):
- **Notification-pref setters** (354–358): `setNotifyService(Boolean)`, `setNotifyDoor(Boolean)`, `setDoorOpenMinutes(Int)`, `setNotifyRunning(Boolean)`, `setRunningMinutes(Int)` — each fires a coroutine writing one field to `SettingsStore`; UI updates via the `notifications` StateFlow mirror, so none touch `_state`.
- **`login(username, password, pin, brand: Brand)`** (480–496) — validate → Kia branch → BlueLink branch. See §3.
- **`kiaSendOtp(notifyType: String)`** (528–535) — send the Kia OTP to `"EMAIL"`/`"SMS"`.
- **`kiaVerifyOtp(code: String)`** (538–551) — verify the code and finish sign-in.
- **`kiaCancelOtp()`** (555–558) — drop stashed creds + clear the challenge UI.
- **`beginAddAccount()`** (573) — `_state.copy(addingAccount = true)` to show login over a loaded garage.
- **`cancelAddAccount()`** (574–579) — clears `addingAccount`; if `lockedToLogin`, re-locks instead of returning to the garage.
- **`logout(brand: Brand)`** (591–605) — best-effort server logout, clear creds, drop repo; last account → fresh `UiState(Login)`, else reload garage.
- **`updatePin(brand, pin)`** (608–616) — fix a wrong/locked PIN without re-login; writes both `store` and `credentialStore`.
- **`unlocked()`** (619–622) — dismiss the lock overlay; loads garage if empty and not already loading.
- **`lockToLogin()`** (626) — from the lock overlay, back out to login; sets `addingAccount=true, lockedToLogin=true`.
- **`setLockTiming(value: LockTiming)`** (633–635) — persist re-lock delay (fire-and-forget).
- **`maybeRelock(backgroundedAtMs, screenOff)`** (642–662) — re-engage lock on foreground per `LockTiming`; also nudges "data may be stale" if backgrounded past `STALE_STATUS_MS`.
- **`canUseBiometrics(): Boolean`** (669–672) — `BiometricManager … BIOMETRIC_WEAK == BIOMETRIC_SUCCESS`.
- **`setBiometricLock(enabled: Boolean)`** (677–679) — persist the lock toggle only.
- **`loadGarage()`** (685) — public entry: `launchBusy { loadGarageInternal() }`.
- **`selectIndex(index: Int)`** (901–906) — swipe to a car; updates index, persists last VIN, lazily `ensureStatus`.
- **`expand(index)`** (910) / **`collapse()`** (912) — large-screen full-screen expand/back.
- **`handleShortcut(vin, cmd)`** (918–921) — queue + run an app-icon shortcut (drains via `tryRunPendingShortcut`).

(Public functions defined after line 800 — `refreshStatus`, `reorderVehicles`, `finishOnboarding`, `finishCarSetup`, `dismissSettingsHint/Coach`, `setPowertrain`, the update/AI/settings/weather/command families — are in the sibling doc. `finishOnboarding`/`finishCarSetup`/`setPowertrain` are noted below because they consume state this range defines.)

---

## 3. Internal structure & control flow

### Private fields & repo plumbing (269–316)

- `store = SessionStore(app)` (269) — brand-namespaced session tokens.
- `settingsStore = SettingsStore(app)` (270) — all persisted prefs (DataStore).
- `credentialStore = CredentialStore(app)` (271) — per-brand saved credentials for auto-login.
- `snapshotStore = SnapshotStore(app)` (272) — lightweight per-VIN snapshots (feed widgets/tiles/watch).
- `statusCache = StatusCache(app)` (273) — on-disk last-known status/location.
- `ai = com.bloo.bluelink.data.Ai(app)` (274) — Gemini Nano wrapper.
- `repos = mutableMapOf<Brand, VehicleRepository>()` (276) — one repo per active brand.
- `repoFor(brand)` (278–279) — `getOrPut` lazily creates via `repositoryFor(brand, store, credentialStore)`.
- `kiaRepo()` (281) — `repoFor(Brand.KIA) as KiaRepository`.
- `brandOf(v)` (283–284) — `Brand.fromIndicator(v.brandIndicator)`.
- `repoFor(v)` (286) — `repoFor(brandOf(v))`.
- `@Volatile loadingGarage` (288–289) — re-entrancy guard for garage load.
- `@Volatile pendingShortcut: Pair<String,String>?` (292–293) — a shortcut awaiting garage load.
- `_state = MutableStateFlow(UiState())` (295) — the mutable backing store.
- `statusMutex = BlueLinkGate.statusMutex` (303) — process-wide status/command serializer (shared with the background worker; Blue Link 502s on overlapping requests).
- `statusInFlight = mutableSetOf<String>()` (306) — VINs with a status request queued/running (de-dupe).
- `surfaceInFlight = mutableSetOf<String>()` (309) — subset whose call used `surfaceErrors=true` (drives spinner + settle haptic).
- `sessionFetched = Collections.synchronizedSet(...)` (312) — VINs fetched from the network this session (cache restore does not count).
- `driveSyncBootstrapped = AtomicBoolean(false)` (316) — guards `bootstrapDriveSync` to fire once per ViewModel.

### `checkAlerts(v, status)` (343–347) — suspend, private

Runs `CarAlerts.evaluate(settingsStore, v, status)`, posts every alert via `Notifications.post(...)`, and additionally surfaces the **first** alert's text as an in-app snackbar (`message`). Called after every successful status load (from `loadStatus`, outside this range).

### `persistCache()` (361–366) — private

Snapshots the current `statuses/locations/placeNames/lastFetched` from `_state.value`, then launches a coroutine to `statusCache.save(...)`.

### `init { }` (368–466)

Launches independent, long-lived `viewModelScope` coroutines. Ordering doesn't matter — each only reads `settingsStore`/`_state` and writes to the watch bridge (one-directional phone→watch), except the two data-loading launches at the end. In order:

1. **Appearance→watch** (379–383): `settingsStore.appearance.collect { WearBridge.publishSettings(app, a) }`.
2. **Section-order→watch** (386–392): on `_state.sectionOrders` change (distinct), re-publish settings (which carry each car's pebble order) using the current appearance.
3. **Climate-presets→watch** (394–398): on `_state.climatePresets` change, `WearBridge.publishPresets`.
4. **Watch climate draft→state** (400–403): collects `ClimateSyncStore(app).flow`, folding `s.byVin` into `_state.climateSync` (the inbound half of two-way climate sync).
5. **Weather/photos/AI→watch** (405–416): maps `_state` to a `WearExtras(homeWeather, carWeather, images, ai)`, distinct, publishes via `WearBridge.publishExtras`.
6. **Gemini Nano probe** (418–429): `ai.isSupported()` once; if true, sets `aiSupported=true` and loads `aiEnabled`/`aiAuto` from settings.
7. **`checkForUpdate()`** (433) — cold-start build-channel check (defined later; internally debounced).
8. **Cache restore** (436–448): `statusCache.load()`; if non-empty, merges cached maps **under** any existing live maps (`cached + it` puts cached first so live values win on key collision). Shows stale-but-useful data before the network returns.
9. **Cold-start auto-login** (453–465): `store.loggedInBrands()`; if empty, return. Else eagerly build each brand's repo (`brands.forEach { repoFor(it) }`), load `accounts` from `credentialStore.loadAll()`, compute `locked = appearance.biometricLock && canUseBiometrics()`, set `locked` if so, then `loadGarage()`. The garage loads **regardless** of lock so it renders (blurred) behind the overlay.

### Auth section (468–679)

**`login`** (480–496): field validation first — blank username/password, or blank PIN when `!brand.usesOtpLogin`, → snackbar "Email, password and PIN are all required" and return. `Brand.KIA` → `loginKia(...)` and return. Otherwise `launchBusy { (repoFor(brand) as BlueLinkRepository).login(...); credentialStore.save(...); AppLog.log(...); update accounts + addingAccount=false; loadGarageInternal() }`.

**Kia OTP dance** (498–569):
- `kiaPending: Credentials?` (501) — creds held between steps, persisted only after full sign-in.
- `loginKia(username, password, pin)` (511–525): `launchBusy` → `kiaRepo().startLogin(...)`:
  - `KiaAuth.LoggedIn` → clear `kiaPending`, `finishKiaLogin(...)`.
  - `KiaAuth.OtpRequired` → stash creds in `kiaPending` (NOT persisted), log, set `_state.kiaOtp = KiaOtpUi(auth)`.
- `kiaSendOtp(notifyType)` (528–535): reads `_state.kiaOtp` (return if null), `launchBusy { kiaRepo().sendOtp(challenge, notifyType); log; kiaOtp = otp.copy(sentTo = notifyType) }`.
- `kiaVerifyOtp(code)` (538–551): needs both `_state.kiaOtp` and `kiaPending`; blank code → snackbar; `launchBusy { kiaRepo().verifyOtp(email, password, pin, code.trim(), challenge); clear kiaPending + kiaOtp; finishKiaLogin(creds) }`.
- `kiaCancelOtp()` (555–558): drop `kiaPending`, clear `kiaOtp`.
- `finishKiaLogin(creds)` (564–569) — suspend: `credentialStore.save(creds)`, log, update accounts + `addingAccount=false`, `loadGarageInternal()`.

**Add-account/logout/PIN** (571–616): `beginAddAccount`/`cancelAddAccount` (with the `lockedToLogin` re-lock branch), `logout(brand)` (best-effort server logout in `runCatching`; always clears creds + drops repo; last account resets to `UiState(Login)`), `updatePin(brand, pin)` (writes both `store.updatePin` and `credentialStore.updatePin`).

**Lock flow** (618–679): `unlocked()`, `lockToLogin()`, `setLockTiming`, `maybeRelock`, `canUseBiometrics`, `setBiometricLock`. `maybeRelock` control flow (642–662): early-return if already locked; in a coroutine, read appearance, return if `!biometricLock || !canUseBiometrics()`, compute `elapsed = now - backgroundedAtMs`, decide `shouldLock` by `LockTiming` (`OFF`→false, `IMMEDIATE`→true, `AFTER_1_MIN`→≥60_000, `AFTER_5_MIN`→≥300_000, `AFTER_10_MIN`→≥600_000), set `locked=true` if so. Separately (synchronously, outside the coroutine), if backgrounded past `STALE_STATUS_MS` and any `lastFetched` value is stale, `reportInfo("Data may be stale — pull down to refresh")`. **Note:** `screenOff` is a parameter but is not read in the body.

### Garage load (681–792)

- **`loadGarage()`** (685) — `launchBusy { loadGarageInternal() }`.
- **`loadGarageInternal()`** (694–702) — suspend re-entrancy gate: if `loadingGarage` return; set true; `try { loadGarageInner() } finally { loadingGarage = false }`. All callers funnel through this one gate so a login finishing and a pull-to-refresh landing together can't run two overlapping brand fetches.
- **`loadGarageInner()`** (704–792) — the heavy lifter:
  1. `var lastError` (711). `fetched = repos.values.flatMap { r -> runCatching { statusMutex.withLock { r.vehicles() } }.getOrElse { … log ⚠, lastError = msg, emptyList() } }` (712–719). One brand failing doesn't hide the others; the error is tracked separately from "genuinely zero vehicles".
  2. If `fetched.isEmpty()` → `_state.copy(vehicles = emptyList(), screen = Screen.Empty, garageLoadError = lastError)` and return (720–723). `garageLoadError` distinguishes a real network/API failure from "not signed in / no vehicles".
  3. `vehicles = applyOrder(fetched, settingsStore.vehicleOrder())` (724).
  4. `snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, null) })` (725) — placeholder snapshots with no live status yet.
  5. Fold in per-VIN local config from `settingsStore` (726–733): `seatConfigs`, `powertrains` (only VINs with an override), `sectionOrders`, `imageUrls`, `licensePlates` (non-blank only), `lastServiceMiles`, `serviceIntervalMiles`, `climatePresets`.
  6. `firstRun = !settingsStore.onboardingSeen()` (734); `unconfiguredVins = vehicles.filter { !isCarConfigured(vin) }` (735).
  7. `collapsed`: on first run **all pebbles start expanded** (emptySet), else built from `settingsStore.collapsedSections` (737–738). `hidden` from `hiddenSections` (739). `hotspots` from `hotspot(vin)` (740).
  8. Tile config from `0 until TILE_COUNT`: `tileConfigs`, `tileLabels`, `tileClimateTargets`, plus `tileBackground`, `tileLiveRefresh`, `shortcutSet` (741–746).
  9. `index = vehicles.indexOfFirst { it.vin == lastVehicleVin() }`, clamped to 0 if not found (747–748).
  10. `screen`: `firstRun` → `Onboarding`; else `unconfiguredVins.isNotEmpty()` → `CarSetup(unconfiguredVins)`; else `Garage` (749–753).
  11. Single `_state.update { copy(...) }` folding all of the above, clearing `garageLoadError = null` (754–778).
  12. `bootstrapDriveSync()` (781) — one-time Drive-sync collector.
  13. `Shortcuts.refresh(app, vehicles, shortcutSet)` (783).
  14. `tryRunPendingShortcut()` (785) — run any shortcut queued before the garage loaded.
  15. `ensureStatus(vehicles[index])` for the current car first (788), then a coroutine prefetching every other car (789–791).

- **`refreshLocalCarConfig()`** (804–827) — suspend. Re-reads the exact same per-VIN local config as steps 5 above (seat/powertrain/photo/plate/service/section/presets) for the currently-loaded vehicles and folds it into `_state`, **no network**. Called after a settings import/restore so an already-composed UI (mid-onboarding, open Settings) reflects the restore immediately instead of waiting for an unrelated full reload. Returns early if `vehicles.isEmpty()`.

### Supporting helpers touched by this range

- `applyOrder(vehicles, order)` (1161–1167) — re-sorts fetched vehicles by saved VIN order; unknown VINs skipped (`mapNotNull`), new cars appended (`rest`); empty order returns as-is.
- `snapshotOf(v, status)` (1121–1153) — builds a `VehicleSnapshot` using the **effective** powertrain: `hasBattery` decides whether `percentFor`/`rangeMiFor` read battery vs fuel. `fetchedAt = now` if status non-null else `0L`.
- `tryRunPendingShortcut()` (923–948), `bootstrapDriveSync()` (844–893), `ensureStatus`/`loadStatus`/`checkForUpdate`/`launchBusy` — described in the sibling doc; called from this range.

---

## 4. Data & types (field-by-field)

### `UiState` (76–190) — every field with default

| Field | Type | Default | Meaning / encoding |
|---|---|---|---|
| `screen` | `Screen` | `Screen.Login` | current nav destination |
| `locked` | `Boolean` | `false` | biometric lock overlay showing (real app blurred behind) |
| `loading` | `Boolean` | `false` | app-wide spinner (`launchBusy`) |
| `refreshing` | `Boolean` | `false` | user-triggered refresh in flight (spinner + settle haptic) |
| `vehicles` | `List<Vehicle>` | `[]` | all cars across all brands, in user order |
| `currentIndex` | `Int` | `0` | selected car in the carousel |
| `expandedIndex` | `Int?` | `null` | large-screen full-screen car; null = grid |
| `statuses` | `Map<String,VehicleStatus>` | `{}` | live status keyed by VIN |
| `lastFetched` | `Map<String,Long>` | `{}` | wall-clock ms of last server pull, by VIN |
| `locations` | `Map<String,GeoLocation>` | `{}` | last-known GPS by VIN |
| `trips` | `Map<String,List<EvTrip>>` | `{}` | recent EV trips (lazy) |
| `climatePresets` | `Map<String,List<ClimatePreset>>` | `{}` | user-named presets by VIN |
| `climateSync` | `Map<String,ClimateSync>` | `{}` | live climate draft mirrored from watch (two-way) |
| `seatConfigs` | `Map<String,SeatConfig>` | `{}` | per-car seat capability flags |
| `powertrains` | `Map<String,Powertrain>` | `{}` | user powertrain **override** by VIN |
| `sectionOrders` | `Map<String,List<String>>` | `{}` | pebble order by VIN |
| `imageUrls` | `Map<String,String>` | `{}` | custom car photo URL by VIN |
| `placeNames` | `Map<String,String>` | `{}` | reverse-geocoded place name by VIN |
| `homeWeather` | `Weather?` | `null` | weather at configured home |
| `carWeather` | `Map<String,Weather>` | `{}` | weather at each car's location |
| `licensePlates` | `Map<String,String>` | `{}` | plate by VIN |
| `lastServiceMiles` | `Map<String,Int>` | `{}` | odometer at last service |
| `serviceIntervalMiles` | `Map<String,Int>` | `{}` | service interval |
| `pending` | `Set<String>` | `{}` | in-flight commands keyed `"vin:action"` |
| `collapsedPebbles` | `Set<String>` | `{}` | collapsed pebbles `"vin:section"`; absent = expanded |
| `hiddenPebbles` | `Set<String>` | `{}` | hidden pebbles `"vin:section"` |
| `hotspotSections` | `Map<String,String>` | `{}` | per-VIN pebble pinned to the dual-column hot spot |
| `tileConfigs` | `List<Pair<String,String>?>` | `List(12){null}` | QS-tile index → (vin, command) or null |
| `tileLabels` | `List<String?>` | `List(12){null}` | per-tile custom name |
| `tileClimateTargets` | `List<String>` | `List(12){"default"}` | per-tile climate target: `"default"`/`"smart"`/preset id |
| `tileBackground` | `Boolean` | `false` | tiles run command in background vs open app |
| `tileLiveRefresh` | `Boolean` | `false` | tiles kick a throttled refresh when shown |
| `shortcutSet` | `Set<String>?` | `null` | enabled app-icon shortcut ids `"cmd_vin"`; null = show all |
| `aiSupported` | `Boolean` | `false` | Gemini Nano present on device |
| `aiEnabled` | `Boolean` | `false` | user opted in to AI |
| `aiAuto` | `Boolean` | `false` | run summaries automatically on open/refresh/command |
| `aiSummaries` | `Map<String,String>` | `{}` | produced summaries by VIN |
| `aiBusy` | `Set<String>` | `{}` | VINs being summarized, plus `"search"` for the query box |
| `aiSearchReply` | `String?` | `null` | free-form AI answer card |
| `showSettingsCoach` | `Boolean` | `false` | coach mark on Settings (points at back arrow) |
| `showSettingsHint` | `Boolean` | `false` | post-onboarding "check out Settings" nudge |
| `accounts` | `List<Credentials>` | `[]` | all signed-in accounts (one per brand) |
| `addingAccount` | `Boolean` | `false` | login form shown over a loaded garage |
| `lockedToLogin` | `Boolean` | `false` | user backed out of biometric prompt to login; Cancel must re-lock |
| `kiaOtp` | `KiaOtpUi?` | `null` | pending Kia OTP challenge |
| `message` | `String?` | `null` | snackbar text |
| `messageType` | `String` | `"error"` | `"error"`/`"success"`/`"info"` — snackbar colour |
| `updateAvailable` | `update.UpdateInfo?` | `null` | newer CI build found |
| `updateTileDismissed` | `Boolean` | `false` | update tile hidden for this build (a different build clears it) |
| `updateDownloading` | `Boolean` | `false` | update APK downloading in-app |
| `updateDownloadProgress` | `Float?` | `null` | 0–1 while downloading, else null |
| `updateApkReady` | `Boolean` | `false` | APK downloaded and cached, ready to install |
| `settingsMode` | `String` | `"simple"` | `"simple"`/`"advanced"` |
| `defaultClimatePresets` | `Map<String,String>` | `{}` | per-VIN default preset id for one-tap Start |
| `syncUri` | `String?` | `null` | Drive `content://` for auto-backup |
| `lastSyncMs` | `Long` | `0L` | last Drive sync time (ms) |
| `syncWifiOnly` | `Boolean` | `true` | Wi-Fi-only sync |
| `syncError` | `String?` | `null` | reason last sync didn't fully succeed; null if OK / not run |
| `garageLoadError` | `String?` | `null` | set when the empty garage came from a real failure, not zero cars |

### `KiaOtpUi` (259–262)
- `challenge: KiaAuth.OtpRequired` — the server challenge (carries `hasEmail`/`hasSms`).
- `sentTo: String?` = null — `"EMAIL"`/`"SMS"` once a destination is chosen.

### Constants
- `WEATHER_TTL_MS = 15 * 60 * 1000L` (61) — weather freshness (15 min).
- `MIN_COMMAND_LOCK_MS = 3000L` (265) — minimum control-lock after firing a command (double-tap guard; used by `runCommand` in the sibling range).

### Referenced domain encodings (defined elsewhere, load-bearing here)
- `Powertrain` enum: `GAS`, `HYBRID`, `PHEV`, `EV` (used in `powertrainOf`/`hasBattery`/`hasFuel`/`powertrainLabel`).
- `Brand.usesOtpLogin` gates whether the PIN field is required at login (481).
- `Brand.fromIndicator(brandIndicator)` maps a vehicle's indicator to its brand (284).
- `LockTiming`: `OFF`, `IMMEDIATE`, `AFTER_1_MIN`, `AFTER_5_MIN`, `AFTER_10_MIN` (648–654).
- `KiaAuth`: sealed with `LoggedIn` and `OtpRequired(hasEmail, hasSms)` (513–521).

---

## 5. State & concurrency

- **Single source of truth:** `_state: MutableStateFlow<UiState>` (295), exposed read-only as `state` (296). Every mutation goes through `_state.update { it.copy(...) }` (atomic read-modify-write) or, in a couple of reset paths, `_state.value = UiState(...)` (599). Compose recomposes on each new emission.
- **Derived StateFlows:** `appearance` and `notifications` are `settingsStore` flows `stateIn(viewModelScope, SharingStarted.Eagerly, <default>)` (321–333). `logs` is `AppLog.lines`. These are the reason the many settings setters never touch `_state`: they write to `SettingsStore`'s DataStore, the flow re-emits, and the UI updates automatically.
- **Scope:** everything runs in `viewModelScope`, cancelled together when the ViewModel clears. The init collectors are long-lived `collect` loops that never complete.
- **Dispatchers:** most mutations run on the default (main-safe) dispatcher; blocking work is pushed to `Dispatchers.IO` — `reverseGeocode` (1801), `performDriveSync` (886), file I/O in export/import. Repo calls are suspend and internally choose their own IO.
- **Locks:**
  - `statusMutex` (`BlueLinkGate.statusMutex`) serializes **all** `vehicles()`/`status()`/command calls process-wide (shared with the background worker). Every garage fetch (713), status load (1039), and command (1922) is wrapped in `statusMutex.withLock { … }`.
  - `synchronized(statusInFlight)` guards the `statusInFlight`/`surfaceInFlight` sets (`loadStatus`, sibling range).
  - `loadingGarage` is `@Volatile` and checked/set only inside `loadGarageInternal` — the single gate for garage loads.
  - `driveSyncBootstrapped` is an `AtomicBoolean` (`compareAndSet(false,true)`) ensuring the Drive-sync collector starts exactly once.
  - `sessionFetched` is a `Collections.synchronizedSet`.
- **Recomposition triggers from this range:** login/logout, lock/unlock, garage load folding everything at once (754), account list changes, and every optimistic map update.

---

## 6. Collaborators & data flow

**Stores (DataStore/disk):** `SessionStore` (tokens, `loggedInBrands`, `updatePin`), `SettingsStore` (all prefs + `vehicleOrder`, per-VIN config, `performDriveSync`, `exportSettingsJson`/`importSettingsJson`), `CredentialStore` (`save`/`loadAll`/`clear`/`updatePin` per brand), `SnapshotStore` (`saveVehicles`), `StatusCache` (`load`/`save`).

**Repositories:** `VehicleRepository` per brand via `repositoryFor(brand, store, credentialStore)`; concrete `BlueLinkRepository` (`.login`) and `KiaRepository` (`.startLogin`/`.sendOtp`/`.verifyOtp`). Repo calls (`vehicles`, `status`, `trips`, commands) are the network boundary; all funnel through `statusMutex`.

**Wear Data Layer (phone→watch, via `com.bloo.bluelink.wear.WearBridge`):**
- `publishSettings(app, appearance)` — appearance + pebble order (init collectors 1&2, and explicit republishes elsewhere).
- `publishPresets(app, presets)` — climate presets (collector 3).
- `publishExtras(app, WearExtras(...))` — weather/photos/AI (collector 5).
- Inbound: `ClimateSyncStore(app).flow` → `_state.climateSync` (collector 4). Paths: state/auth/settings/presets/climate/extras (DataItems) + command/sync_request/results (Messages).

**System integration:** `Notifications.post(...)` (alerts), `BiometricManager` (`canUseBiometrics`), `Shortcuts.refresh(...)` (app-icon long-press), `Geocoder` (reverse/forward), `BlooWidget().updateAll`, `BlooTileService.requestUpdates`, `UpdateChecker`/`UpdateApi` (self-update from GitHub Releases), `Ai` (Gemini Nano).

**Data in:** repo network payloads → `statuses`/`locations`; `SettingsStore` reads → per-VIN config; `CredentialStore.loadAll()` → `accounts`; `StatusCache.load()` → cached maps at startup; watch climate draft → `climateSync`.
**Data out:** `_state` → Compose UI; `SnapshotStore` → widgets/tiles/watch; `WearBridge` → watch; `Notifications` → system tray; `Shortcuts`/`BlooWidget`/`BlooTileService` → launcher/home-screen/QS.

**Who calls this unit:** the Compose root observes `state`; `MainActivity`/lifecycle calls `maybeRelock`; intent handlers call `handleShortcut`; the login screen calls `login`/`kia*`; the biometric prompt callbacks call `unlocked`/`lockToLogin`.

---

## 7. Invariants & assumptions

- **`statusMutex` must wrap every status/command/vehicles call** — overlapping requests for one account 502 with "a previous request is pending". This is process-wide (shared with the worker), not per-instance.
- **`loadGarageInternal` is the only path to `loadGarageInner`** — the `loadingGarage` guard assumes no one calls the inner directly.
- **`kiaPending` is set iff an OTP challenge is outstanding** — `kiaVerifyOtp` requires both `kiaOtp` and `kiaPending` non-null; the creds are persisted only after successful verify, so an abandoned OTP flow leaves nothing on disk.
- **`_state` copies are immutable snapshots** — mutations must go through `copy`; helpers like `powertrainOf`/`hasBattery` are pure derivations over the current state.
- **Pebble collapse semantics are inverted:** absence in `collapsedPebbles` means expanded (see `isPebbleExpanded`). First-run forces expanded regardless of stored state (737).
- **`shortcutSet == null` means "all shortcuts enabled"** (203) — a distinct meaning from an empty set (none).
- **Effective powertrain (`powertrainOf`) — not raw `isEv` — drives battery/fuel decisions** (`snapshotOf`, `carText`, commands' `electric(v)`). A user-marked PHEV reads battery %, and its EV endpoints are used even though the API reports gas.
- **`garageLoadError` non-null ⟹ empty vehicles came from a real failure**, not "no cars"; cleared on the next successful load (776) and by `finishOnboarding`/`finishCarSetup`.
- **Cache-restore merge order matters:** `cached + it.statuses` (etc.) puts cached entries first so live values win on collision (444). `sessionFetched` deliberately excludes cache restores so `ensureStatus` still pulls a live update.
- **Biometrics use `BIOMETRIC_WEAK`** (not STRONG) so more authenticators (incl. some face-only) qualify (669–672); the lock overlay is meaningless without enrolled biometrics, so `locked` is only ever set when `canUseBiometrics()`.

---

## 8. Gotchas & sharp edges

- **`maybeRelock`'s `screenOff` parameter is unused** (642). The signature accepts it but the body only uses `backgroundedAtMs` and the `LockTiming` setting. Future callers shouldn't assume screen-off state changes behaviour.
- **The garage loads even when locked** (463–464). The overlay only gates interaction; the real (blurred) UI is fully populated behind it. `unlocked()` re-triggers `loadGarage()` only if `vehicles` is empty and no load is running (621) — the belt-and-suspenders path for a lock that outlived an empty state.
- **`lockedToLogin` back-navigation trap:** backing out of the biometric prompt to the login form sets `lockedToLogin=true` (626); `cancelAddAccount` then re-locks rather than silently dropping the user into the garage (577). Miss this and Cancel becomes an auth bypass.
- **Kia OTP branch is non-deterministic:** whether `startLogin` returns `LoggedIn` or `OtpRequired` depends on the account/server and isn't knowable in advance (508–509). Both must be handled every time.
- **Auto-login only fires when `store.loggedInBrands()` is non-empty** (454–455) — it's the one-time cold-start path, entirely separate from interactive `login()`. Repos are eagerly created here so later `repoFor` calls just reuse them.
- **First-run forces all pebbles expanded** (736–738) ignoring any stored `collapsedSections` — do not "fix" this to read stored state on first run; it's intentional onboarding behaviour.
- **`applyOrder` never drops cars:** unknown saved VINs are skipped, newly-appeared cars are appended (not dropped), so a car added to the account since the last saved order still shows up (1160–1167).
- **`snapshotOf(_, null)` vs `snapshotOf(_, status)`:** a null status means a placeholder snapshot — `fetchedAt` is deliberately left `0L` ("unknown"), not `now` (1146–1147). The garage load saves placeholders first (725), then real status arrives and re-persists.
- **`logout` server call is best-effort** (`runCatching`, 593) — a failed remote logout must never block clearing local creds and dropping the repo, or a broken server would trap the user signed-in forever.
- **`bootstrapDriveSync` is guarded because it used to leak collectors:** it was once spliced into `loadStatus` (runs per status fetch), so every refresh started a new permanent `refreshing` collector that each did a full Drive download+merge+upload; a long session piled up unbounded collectors all firing at once (844–893 doc comment). The `AtomicBoolean.compareAndSet` guard fixes that — never call it from a per-fetch path.
- **`checkForUpdate`'s `sameBuild` logic** (sibling range, but relevant to init line 433): a *different* build re-surfaces a dismissed tile; re-finding the *same* build does not un-dismiss it, and a stale downloaded APK is only considered ready if it matches the current build's `run.runNumber`.
- **Init collectors are strictly one-directional (phone→watch) except collector 4** (the inbound watch climate draft). They're safe in any order because they only read `settingsStore`/`_state` and write to the bridge — but that also means they never complete and live for the ViewModel's lifetime.
