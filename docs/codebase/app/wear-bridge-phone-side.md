# app: Wear Bridge (phone side)

Deep-dive reference for the phone half of the phone↔watch sync.

**Files covered**
- `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\wear\WearBridge.kt` (324 lines)
- `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\wear\WearPhoneService.kt` (318 lines)

Package: `com.bloo.bluelink.wear`.

The wire protocol constants and payload types (`WearSync`, `WearAction`, `WearStatePayload`, etc.) live in `:shared` at `com.bloo.bluelink.data.WearSync` and are referenced heavily below; where an encoding detail matters it is cited to that file.

---

## 1. Purpose

This unit is the **phone-side endpoint of the Wearable Data Layer sync** between the Bloo phone app and the Wear OS companion.

There are two responsibilities, split across the two files:

- **`WearBridge`** (an `object`) is the **outbound** side: it *publishes* phone state to the watch — car snapshots, signed-in sessions, resolved theme/settings, climate presets, the live climate draft, and "extras" (weather / car photos / AI summaries). It is also the single fan-out point that, after any state change, refreshes *every* downstream surface (home-screen widget, Quick-Settings tiles, and the watch). It additionally owns the phone-triggered Google Drive settings sync (`driveSync`).
- **`WearPhoneService`** (a `WearableListenerService`) is the **inbound** side: it *receives* the watch's messages and data items even when the phone app UI is not running (the system binds it on demand when Data Layer traffic arrives on a `/bloo` path). It runs the watch's car commands (via `WearCommandRunner`), handles the AI-summary and weather-from-device-location pseudo-commands itself, replies to the watch, and persists data items the watch writes (climate draft, presets, pebble order, local backup, AI/aurora toggles).

Why it exists: the watch operates as a thin remote. Actual commands are executed with the phone's stored sessions (the same stored-session pattern the QS tiles use — see file header, `WearBridge.kt:30-34`) so the watch never needs credentials to, e.g., lock a door. When the watch *does* need to act standalone on its own Wi‑Fi/cell, `publishAuth` mirrors the sessions to it. This bridge is the seam that keeps phone and watch consistent while allowing them to be built/updated independently.

---

## 2. Public surface

### `object WearBridge` (`WearBridge.kt:51`)

Stateless singleton. Holds only a private `scope` (see §5). All members are top-level functions on the object.

| Member | Signature | What it does |
|---|---|---|
| `publish` | `fun publish(context: Context)` (`:56`) | Fire-and-forget. Grabs `applicationContext`, launches a coroutine on `scope`, and runs `publishNow` then `publishAuth`, each wrapped in its own `runCatching`. Used as the cheap "just mirror everything to the watch now" entry point. |
| `publishNow` | `suspend fun publishNow(context: Context)` (`:65`) | Reads on-disk snapshots via `SnapshotStore(context).current()`, builds a `WearStatePayload` (vehicles, selectedVin, `producedAt = System.currentTimeMillis()`), writes it to Data Layer path `WearSync.PATH_STATE` with keys `KEY_PAYLOAD` (encoded state) + `KEY_TIMESTAMP` (the producedAt). `.setUrgent()`. Awaits the put. |
| `refreshAllSurfaces` | `suspend fun refreshAllSurfaces(context: Context)` (`:84`) | The single fan-out point after a data change. Runs (each in `runCatching`): `publishNow`; read `SettingsStore.appearance.first()` then `publishSettingsNow`; `BlooWidget().updateAll(context)`; `BlooTileService.requestUpdates(context)`. |
| `publishAuth` | `suspend fun publishAuth(context: Context)` (`:99`) | Loads every logged-in brand's `SessionStore.Session`, maps each to a `WearSessionDto`, wraps in `WearAuthBundle`, writes to `WearSync.PATH_AUTH` (payload only, **no timestamp** — see §8). `.setUrgent()`. |
| `publishSettings` | `fun publishSettings(context: Context, appearance: SettingsStore.Appearance)` (`:124`) | Fire-and-forget wrapper: launches `publishSettingsNow` on `scope`. |
| `publishSettingsNow` | `suspend fun publishSettingsNow(context: Context, appearance: SettingsStore.Appearance)` (`:137`) | Resolves the phone theme + preferences into a flat `WearSettingsPayload` of literal ARGB ints/primitives and writes it to `WearSync.PATH_SETTINGS`. Full control flow in §3. |
| `publishPresets` | `fun publishPresets(context: Context, byVin: Map<String, List<ClimatePreset>>)` (`:244`) | Fire-and-forget. Writes `WearPresets(byVin)` to `WearSync.PATH_PRESETS`. `.setUrgent()`. |
| `publishClimate` | `fun publishClimate(context: Context, state: WearClimateState)` (`:261`) | Fire-and-forget. Writes the live climate draft to `WearSync.PATH_CLIMATE`, **with** `KEY_TIMESTAMP = System.currentTimeMillis()` (bidirectional channel — see §8). `.setUrgent()`. |
| `publishExtras` | `fun publishExtras(context: Context, extras: WearExtras)` (`:275`) | Fire-and-forget. Writes `WearExtras` (weather / car photos / AI summaries) to `WearSync.PATH_EXTRAS`. `.setUrgent()`. |
| `driveSync` | `suspend fun driveSync(context: Context): SettingsStore.DriveSyncOutcome?` (`:295`) | Triggers a Drive sync. Returns `null` if sync isn't configured on this phone; otherwise the `DriveSyncOutcome`. Control flow in §3. |

### `class WearPhoneService : WearableListenerService()` (`WearPhoneService.kt:52`)

Manifest-declared bound service. Public/override surface:

| Member | Signature | What it does |
|---|---|---|
| `onMessageReceived` | `override fun onMessageReceived(event: MessageEvent)` (`:82`) | Routes one-shot watch messages by `event.path`. Handles `PATH_COMMAND` and `PATH_SYNC_REQUEST`. Detail in §3. |
| `onDataChanged` | `override fun onDataChanged(events: DataEventBuffer)` (`:175`) | Handles data items the watch writes (climate draft, presets, pebble order, local backup, AI/aurora toggles). Detail in §3. |
| `onDestroy` | `override fun onDestroy()` (`:254`) | Cancels `scope` so in-flight coroutines are torn down when the system reclaims the idle service, then calls `super.onDestroy()`. |

Private helpers `runAiSummary` and `setWeatherFromDeviceLocation` are documented in §3.

---

## 3. Internal structure & control flow

### `WearBridge.publishSettingsNow` (`:137-209`)

The most involved publish. Steps:

1. **Resolve dark mode** (`:138-142`): `when (appearance.themeMode)` — `LIGHT`→false, `DARK`/`AMOLED`→true, `SYSTEM`→`isSystemDark(context)`.
2. **Pick custom palette** (`:143-145`): if `!appearance.dynamicColor`, find the palette whose `id == appearance.activeCustomPaletteId`; else `null`.
3. **Resolve the main scheme** (`:146-154`): call `blooColorScheme(...)` with context, dark, themeMode, dynamicColor, colorPalette, the custom palette, vibrancy. Returns a Material 3 `ColorScheme`.
4. **Per-car color overrides** (`:157-165`): for each `(vin, paletteId)` in `appearance.carCustomPaletteIds`, find the matching custom palette (skip if absent), build a *car-specific* scheme with `dynamicColor = false` and that palette, then map `vin -> rolesOf(carScheme)`. Result is a `Map<String, WearColorRoles>`.
5. **Per-car layout + comfort** (`:169-186`): `vins` = current snapshot VINs. Builds three maps keyed by VIN: `pebbleOrders` (`store.sectionOrder(vin)`), `hiddenSections` (`store.hiddenSections(vin)`), and `seatConfigs` (`store.seatConfig(vin)` translated field-by-field into `WearSeatConfig`).
6. **Assemble `WearSettingsPayload`** (`:188-204`): dark, useFahrenheit, unitSystem, uiScale, `colors = rolesOf(s)`, carColors, pebbleOrders, hiddenSections, seatConfigs, aiEnabled, auroraEnabled, auroraColorMode, auroraCustomColor, hapticsEnabled, settingsMode.
7. **Write** (`:205-208`): put to `WearSync.PATH_SETTINGS` (payload only, no timestamp), `.setUrgent()`, await.

### `WearBridge.rolesOf` (private, `:211-237`)

Maps a Compose `ColorScheme` to `WearColorRoles` by calling `.toArgb()` on 25 roles (primary/onPrimary/…/onErrorContainer). This is the flattening that lets the watch paint the exact same theme with no Compose theming code.

### `WearBridge.isSystemDark` (private, `:239-241`)

`(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES`.

### `WearBridge.driveSync` (`:295-317`)

Wrapped in `runCatching { … }.getOrElse { … }`:
- Build `SettingsStore`. If `store.syncUri() == null`, log "⚠ Drive sync: not configured" and `return@runCatching null`.
- Log "Drive sync: starting"; `outcome = store.performDriveSync()` (the shared implementation used by the phone's own auto-sync collector — one implementation, see `:293-294`).
- Re-publish settings (`publishSettingsNow` with fresh appearance) and call the private `updateAllSurfaces` (widgets+tiles, no state re-fetch).
- Return `outcome`.
- **On unexpected exception** (`:308-316`): fall back to a `DriveSyncOutcome(ran = true, imported = false, uploaded = false, syncedAtMs = <lastKnown>, error = e.message ?: "Sync failed")`, where `lastKnown` is `SettingsStore(context).lastSyncMs()` (defaulting to `0L`). This deliberately avoids regressing "Last synced" to blank/never when a real prior sync exists.

### `WearBridge.updateAllSurfaces` (private, `:320-323`)

`BlooWidget().updateAll(context)` + `BlooTileService.requestUpdates(context)`, each in `runCatching`. Distinct from the public `refreshAllSurfaces`: this one does **not** re-publish state or settings, it only nudges widgets/tiles.

### `WearPhoneService.onMessageReceived` (`:82-158`)

`when (event.path)`:

**`WearSync.PATH_COMMAND`** (`:84-125`):
1. `decodeCommand(String(event.data ?: ByteArray(0)))`; `?: return` (malformed → drop).
2. If `command.action == WearAction.AI_SUMMARY` (`:86-101`): launch → `runAiSummary(ctx, command.vin)`, then send the `WearAiResult` back to `event.sourceNodeId` on `WearSync.PATH_AI_RESULT`. `return`.
3. If `command.action == WearAction.WEATHER_DEVICE_LOCATION` (`:102-108`): launch → `runCatching { setWeatherFromDeviceLocation(applicationContext) }`. Fire-and-forget — no reply message (the watch's extras collector reacts when fresh weather publishes). `return`.
4. Otherwise (ordinary car command, `:109-124`): launch → `WearCommandRunner.execute(applicationContext, command)`; log ok/failure; send `WearCommandResult` back to `event.sourceNodeId` on `WearSync.PATH_COMMAND_RESULT`; then `WearBridge.refreshAllSurfaces(ctx)` to fan the refreshed state out to every other surface.

**`WearSync.PATH_SYNC_REQUEST`** (`:127-156`):
1. `decodeCommand(...)` (note: not null-guarded with early return — see the `command?.action` below).
2. launch → `when (command?.action)`:
   - `WearAction.REFRESH` (`:132-135`): `WearCommandRunner.refresh(ctx, command.vin)` then `WearBridge.refreshAllSurfaces(ctx)`. No dedicated result message.
   - `WearAction.DRIVE_SYNC` (`:136-152`): `outcome = WearBridge.driveSync(ctx)`; build `WearSyncResult` — if `outcome == null` → `ok=false, message="Drive sync isn't set up on this phone"`; else `ok = outcome.uploaded, message = outcome.error`. Send it on `WearSync.PATH_SYNC_RESULT` to `event.sourceNodeId`.
   - `else` (`:153`): `WearBridge.refreshAllSurfaces(ctx)`. This covers `null` (decode failure) and any unrecognized action — a plain refresh.

### `WearPhoneService.onDataChanged` (`:175-250`)

1. **Synchronous extraction** (`:176-187`): map the `DataEventBuffer` to a list of `(path, raw)` pairs. Skip non-`TYPE_CHANGED` events. Path allowlist: `PATH_CLIMATE`, `PATH_PRESETS`, `PATH_PEBBLE_ORDER`, `PATH_LOCAL`, `PATH_AI_TOGGLE`, `PATH_AURORA_TOGGLE`; anything else → dropped. Read `KEY_PAYLOAD` string; skip if null. (The buffer must not outlive the callback, so everything is pulled out before the coroutine — see `:169-171`.)
2. If empty, `return`.
3. **launch** (`:189-249`): `updates.forEach { (path, raw) -> runCatching { when (path) … } }`:
   - `PATH_CLIMATE` (`:193`): `ClimateSyncStore(applicationContext).save(raw)` (saves the raw string directly).
   - `PATH_PRESETS` (`:194-199`): `decodePresets(raw)`, for each `(vin, list)` call `store.setClimatePresets(vin, list)`.
   - `PATH_PEBBLE_ORDER` (`:200-211`): `decodePebbleOrder(raw) ?: return@forEach`; if `po.vin.isNotBlank() && po.order.isNotEmpty()`, `setSectionOrder(po.vin, po.order)`, then **mirror back** by re-publishing settings (`publishSettingsNow` with fresh appearance) so all devices + the watch's optimistic override align.
   - `PATH_LOCAL` (`:212-221`): `decodeLocal(raw) ?: return@forEach`; `setUiScale(payload.uiScale.coerceIn(0.8f, 1.4f))`; `payload.unitSystem?.let { setUnitSystem(it) }`; `setWatchPinLock(payload.watchPinLockEnabled, payload.watchPinLockTiming)`. **One-directional (watch→phone) backup only** — never pushed back down (see `:217-219`).
   - `PATH_AI_TOGGLE` (`:222-232`): `decodeAiToggle(raw) ?: return@forEach`; `setAiEnabled(payload.enabled)`; mirror back via `publishSettingsNow` (settle the watch's optimistic toggle).
   - `PATH_AURORA_TOGGLE` (`:233-245`): `decodeAuroraToggle(raw) ?: return@forEach`; `setAuroraBackground(payload.enabled)`; `payload.colorMode?.let { setAuroraColorMode(it) }` (null means only `enabled` changed); mirror back via `publishSettingsNow`.

### `WearPhoneService.runAiSummary` (private, `:263-291`)

Always returns a `WearAiResult` (never throws to caller) so the watch spinner resolves:
1. If `!SettingsStore(ctx).aiEnabled()` → `WearAiResult(vin, ok=false, "AI summaries are turned off in Settings")`.
2. If `!Ai(ctx).isSupported()` → `WearAiResult(vin, ok=false, "AI summaries aren't supported on this phone")`.
3. Find the snapshot with matching `vin`; if none → `WearAiResult(vin, ok=false, "Car not found")`.
4. Build a prompt string from name/locked/climateOn/percent/rangeMi and call `Ai(ctx).summarize(...)` in `runCatching`; on null → `WearAiResult(vin, ok=false, "Couldn't generate a summary")`.
5. **`extrasMutex.withLock`** (`:281-288`): read the current `PATH_EXTRAS` data item via `dataClient.getDataItems(Uri.parse("wear://*${WearSync.PATH_EXTRAS}"))`, decode first (or `WearExtras()`), `items?.release()`, `updated = existing.copy(ai = existing.ai + (vin to summary))`, `WearBridge.publishExtras(ctx, updated)`.
6. Log, return `WearAiResult(vin, ok=true)`.

### `WearPhoneService.setWeatherFromDeviceLocation` (private, `:298-317`)

1. `if (!store.setWeatherFromDeviceLocation())` → log "no device location available", return. (This call mutates the phone's home weather location from device GPS — mirrors the phone Settings "My location".)
2. Read fresh `appearance`, `lat`/`lon`; if either null, return.
3. `WeatherApi.fetch(lat, lon)` in `runCatching`; null → return.
4. **`extrasMutex.withLock`** (`:309-315`): same read-modify-write of `PATH_EXTRAS` as above, patching `homeWeather = weather.toWear()`.
5. Log.

---

## 4. Data & types

**No data classes/enums are *defined* in this unit.** All payload types are declared in `:shared`'s `WearSync.kt`. The types this unit constructs/consumes, with the encoding facts that matter:

- **`WearStatePayload`** (`WearSync.kt:227`): `vehicles: List<VehicleSnapshot> = emptyList()`, `selectedVin: String? = null`, `producedAt: Long = 0L` (ms wall-clock). Built in `publishNow`.
- **`WearSessionDto`** (`WearSync.kt:326`): `brand: String`, `accessToken: String`, `refreshToken: String? = null`, `username: String`, `pin: String`, `deviceId: String? = null`. Maps 1:1 onto `SessionStore.Session`. Note the domain fact: Kia stores its `sid` as `accessToken` and `rmtoken` as `refreshToken`, plus the `deviceId` the rmtoken binds to. Built in `publishAuth` from `sessionStore.load(brand)`.
- **`WearAuthBundle`** (`WearSync.kt:337`): `sessions: List<WearSessionDto> = emptyList()`.
- **`WearColorRoles`** (`WearSync.kt:346`): 25 `Int` ARGB fields (primary…onErrorContainer). Produced by `rolesOf`. Ints are packed ARGB from `Color.toArgb()`.
- **`WearSeatConfig`** (`WearSync.kt:384`): `driverHeat=true, driverCool=false, passHeat=true, passCool=false, rearLeftHeat, rearLeftCool, rearRightHeat, rearRightCool, steeringWheel` (defaults "driver + passenger heat only"). Built per-VIN in `publishSettingsNow`.
- **`WearSettingsPayload`** (constructed at `WearBridge.kt:188-204`): `dark, useFahrenheit, unitSystem, uiScale, colors: WearColorRoles, carColors: Map<String, WearColorRoles>, pebbleOrders, hiddenSections, seatConfigs: Map<String, WearSeatConfig>, aiEnabled, auroraEnabled, auroraColorMode, auroraCustomColor, hapticsEnabled, settingsMode`.
- **`WearCommand`** (`WearSync.kt:281`): `vin, action: String`, climate fields (`tempF=DEFAULT_CLIMATE_TEMP_F`, `durationMinutes`, `defrost=false`, `steeringWheelHeat=false`, `seatFrontLeft/Right`, `seatRearLeft/Right` — all `Int`, `0=off`, encoded as `SeatLevel.apiValue`), charge targets (`acLimit=80`, `dcLimit=90`). Decoded from incoming messages.
- **`WearCommandResult`** (`WearSync.kt:301`): `vin, action, ok: Boolean, message: String? = null`.
- **`WearSyncResult`** (`WearSync.kt:310`): `ok: Boolean, message: String? = null`. Built in the DRIVE_SYNC branch.
- **`WearAiResult`** (`WearSync.kt:317`): `vin, ok: Boolean, message: String? = null`. Returned by `runAiSummary`.
- **`WearExtras`** — read-modify-written in the AI-summary/weather flows; `ai` is a `Map<vin, summary>`, `homeWeather` holds `weather.toWear()`.
- **`WearAction`** (`WearSync.kt:243`): **string constants, not an enum** (`:234-242`) so neither side needs matching ordinals. Values referenced by this unit: `REFRESH="refresh"`, `AI_SUMMARY="ai_summary"`, `DRIVE_SYNC="drive_sync"`, `WEATHER_DEVICE_LOCATION="weather_device_location"`. Unrecognized actions are ignored.

**Data Layer keys/paths** (all from `WearSync`): `KEY_PAYLOAD="payload"`, `KEY_TIMESTAMP="ts"`; paths `PATH_STATE="/bloo/state"`, `PATH_AUTH="/bloo/auth"`, `PATH_SETTINGS="/bloo/settings"`, `PATH_PRESETS="/bloo/presets"`, `PATH_CLIMATE="/bloo/climate"`, `PATH_EXTRAS="/bloo/extras"`, `PATH_PEBBLE_ORDER="/bloo/pebble_order"`, `PATH_COMMAND="/bloo/command"`, `PATH_SYNC_REQUEST="/bloo/sync_request"`, `PATH_COMMAND_RESULT="/bloo/command_result"`, `PATH_SYNC_RESULT="/bloo/sync_result"`, `PATH_AI_RESULT="/bloo/ai_result"`, `PATH_LOCAL="/bloo/local"`, `PATH_AI_TOGGLE="/bloo/ai_toggle"`, `PATH_AURORA_TOGGLE="/bloo/aurora_toggle"`.

**Serialization** (`WearSync.kt:100-117`): kotlinx.serialization JSON with `ignoreUnknownKeys=true` and `encodeDefaults=true` (every field always on the wire, so a stale receiver still gets a fully-populated shape). Every `decode*` is wrapped in `runCatching` with a sensible fallback (empty/default instance or `null`) — a malformed/version-skewed payload never crashes the receiver.

---

## 5. State & concurrency

- **`WearBridge.scope`** (`:53`): `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Process-lifetime scope for the singleton — never cancelled. All fire-and-forget `publish*` wrappers launch here. `SupervisorJob` means one failing child doesn't cancel siblings.
- **`WearPhoneService.scope`** (`:54`): `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, **owned by the service instance** and cancelled in `onDestroy` (`:255`). Both binder callbacks hand work off to this scope immediately so they stay fast/non-blocking.
- **`WearPhoneService.extrasMutex`** (companion, `:63`): a `kotlinx.coroutines.sync.Mutex` guarding the `PATH_EXTRAS` read-modify-write in both `runAiSummary` and `setWeatherFromDeviceLocation`. Both read the current extras item, patch one field, and republish the whole thing; without the lock two near-simultaneous requests could both read the same stale snapshot and the second publish would silently drop the first's update (`:57-62`).
- **`Tasks.await(...)`** is used throughout to bridge the callback-based Play Services `Task` into the suspend functions by **blocking the calling coroutine's thread**. This is only safe because everything runs on `Dispatchers.IO`, never the main thread (`:41-45`).
- **No StateFlow / DataStore held here directly.** Persistent state lives in the injected stores (`SnapshotStore`, `SessionStore`, `SettingsStore`, `ClimateSyncStore`), each constructed on demand from `applicationContext`. This unit is otherwise stateless.
- **Recomposition**: N/A — no Compose here. The "surfaces" refreshed are Glance widgets (`updateAll`) and tiles (`requestUpdates`), which redraw off their own state.

---

## 6. Collaborators & data flow

**Outbound (WearBridge → …):**
- Writes Data Items via `Wearable.getDataClient(context).putDataItem(...)` on paths STATE/AUTH/SETTINGS/PRESETS/CLIMATE/EXTRAS. The Data Layer syncs to any reachable watch node automatically; with no watch it's a local no-op (`:36-42`).
- `refreshAllSurfaces` / `updateAllSurfaces` call `BlooWidget().updateAll(context)` (Glance home widget) and `BlooTileService.requestUpdates(context)` (QS tiles).
- Reads: `SnapshotStore.current()`, `SessionStore.loggedInBrands()/load()`, `SettingsStore.appearance/sectionOrder/hiddenSections/seatConfig/aiEnabled/settingsMode/syncUri/performDriveSync/lastSyncMs`, `blooColorScheme(...)`.

**Inbound (WearPhoneService ← watch):**
- `onMessageReceived` ← `MessageClient.sendMessage` from watch on `PATH_COMMAND` / `PATH_SYNC_REQUEST`. Replies via `getMessageClient(ctx).sendMessage(sourceNodeId, PATH_*_RESULT, …)`.
- `onDataChanged` ← watch's `putDataItem` on CLIMATE/PRESETS/PEBBLE_ORDER/LOCAL/AI_TOGGLE/AURORA_TOGGLE. Persists to `ClimateSyncStore`, `SettingsStore` (climate presets, section order, uiScale, unit system, watch-pin-lock backup, AI/aurora toggles).
- Delegates command execution to `WearCommandRunner.execute` / `.refresh` (runs against stored sessions — the watch never needs credentials).
- AI: `Ai(ctx).isSupported()/summarize(...)`; weather: `WeatherApi.fetch(lat, lon)` + `weather.toWear()`; logging: `AppLog.log`.

**Callers of WearBridge:** workers/services that mutate state (e.g. `WidgetRefreshWorker` calls `publishSettingsNow` defensively on every periodic refresh — `:132-136`), and the phone UI on settings/preset/climate changes. `WearPhoneService.onMessageReceived` calls `WearBridge.refreshAllSurfaces` and `WearBridge.driveSync`.

---

## 7. Invariants & assumptions

- **IO-thread only.** `Tasks.await` blocks the thread; all callers must be on `Dispatchers.IO` (both scopes are). Never invoke publish suspend fns on the main thread.
- **Changing content triggers the watch listener.** A byte-identical Data Item is coalesced/skipped by the Data Layer. `publishNow` (`:70,76`) and `publishClimate` (`:267`) therefore stamp a fresh `KEY_TIMESTAMP` on every push. `publishAuth`/`publishSettings`/`publishPresets`/`publishExtras` deliberately omit a timestamp so they only re-transmit when tokens/colors/data actually change.
- **`extrasMutex` must be held** around any read-modify-write of `PATH_EXTRAS` (both current call sites do).
- `WearSessionDto` maps 1:1 to `SessionStore.Session`; adding a session field means updating both.
- `WearSeatConfig` must stay field-for-field aligned with `:app`'s `SeatConfig` (same defaults) so an unsynced/old watch falls back correctly.
- The `PATH_LOCAL` sync is strictly one-directional (watch→phone backup); the phone never republishes it to reconfigure the watch (`:217-219`).
- `uiScale` from the watch is clamped to `0.8f..1.4f` before persisting (`:215`).
- Decode failures are non-fatal (drop the message / use defaults); the code never assumes phone and watch run the same schema version.
- The `DataEventBuffer` must be consumed synchronously inside `onDataChanged` — it does not outlive the callback (`:169-171`).
- `getDataItems` results must be `release()`d (both extras readers do — `:285,313`).
- Only `TYPE_CHANGED` events are handled; nothing here is cleared by item deletion (`:172-173,177`).

---

## 8. Gotchas & sharp edges

- **Timestamp presence is protocol, not decoration.** STATE and CLIMATE include `KEY_TIMESTAMP` *specifically* to force the watch's DataClient to see the item as changed even when the actual data is identical (e.g. a manual refresh that returns the same battery %). Removing it would make repeat pushes silently no-op on the watch. AUTH/SETTINGS/PRESETS/EXTRAS intentionally omit it so they don't waste a re-transmit when nothing changed (`:44-49`, `:96-98`).
- **`onMessageReceived` PATH_COMMAND null-guards with early `return`** (`:85`), but **PATH_SYNC_REQUEST does not** (`:128`) — it relies on `command?.action` and the `else` branch to treat a decode failure as a plain refresh. Two different error-handling shapes for the same decode.
- **AI_SUMMARY and WEATHER_DEVICE_LOCATION are pseudo-commands** that arrive on `PATH_COMMAND` but never touch the car — they're intercepted before `WearCommandRunner.execute`. AI_SUMMARY always sends a reply (the watch's only feedback for a disabled/unsupported/failed request — `:259-262`); WEATHER_DEVICE_LOCATION is fire-and-forget (the watch's extras collector reacts to the fresh publish — `:103-105`).
- **`driveSync` returns `null` vs. an outcome with `error`** are different signals: `null` = not configured at all (watch shows "Drive sync isn't set up"); a non-null outcome with `ok = outcome.uploaded` reports actual success/failure. Note `WearSyncResult.ok` maps to `uploaded`, not merely "ran".
- **`driveSync`'s getOrElse fallback preserves `lastSyncMs()`** rather than resetting to `0L`, precisely so a stray uncaught exception doesn't blank the watch's "Last synced" when a genuine prior sync exists (`:308-316`).
- **The mirror-back pattern** (PEBBLE_ORDER, AI_TOGGLE, AURORA_TOGGLE all re-publish settings after persisting) exists to settle the watch's *optimistic* local override on the phone-confirmed value. PATH_CLIMATE, PATH_PRESETS, and PATH_LOCAL deliberately do **not** mirror back.
- **`publishSettingsNow` rebuilds a full `ColorScheme` per car** with a custom palette (`:159-163`) — one `blooColorScheme` call per car-with-override, which can be several palette computations per push. Called on every periodic refresh, so it isn't free.
- **`applicationContext` is used everywhere in the service**, not the (short-lived, bound) service `Context`, so launched coroutines survive the service binding lifecycle correctly.
- **`WearAction` values are free-form strings, not enums** — an unknown action string decodes fine and is simply ignored, which is what lets phone/watch update independently (`:234-242`, `WearSync.kt`).
- **`onDataChanged` path allowlist is duplicated** — once as the extraction filter (`:180-183`) and again implicitly as the `when` arms (`:192-246`). The comment (`:172-173`) frames the filter as belt-and-suspenders against stragglers from other apps.
