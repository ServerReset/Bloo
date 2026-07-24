# SettingsStore — Part 2: Drive sync, import/export/merge, per-car climate & palettes

**File:** `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\data\SettingsStore.kt`
**Focus of this doc:** lines 800–1571 — the Google-Drive bidirectional sync engine, the
dirty-key tracking that makes it a field-level merge, the portable JSON export / import /
merge (including embedded per-car photos), the per-car climate settings & presets, the
custom-colour-palette storage & sharing, and the weather-location setters. Earlier lines
(appearance/notification/tile/section helpers) are covered in Part 1; they are referenced
here where the sync engine depends on them (`editTracked`, `DEVICE_LOCAL_KEYS`, the `img_$vin`
key convention, etc.).

---

## 1. Purpose

`SettingsStore` is a thin, strongly-typed wrapper around a single Jetpack
`DataStore<Preferences>` instance (`Context.settingsDataStore`, declared file-scoped at
`SettingsStore.kt:39-42`) — a flat string/boolean key-value bag persisted to
`bloo_settings.preferences_pb`. It holds **everything that must survive sign-out**:
appearance, notifications, per-car config, tiles, widgets, climate presets, custom
palettes. Account credentials deliberately live in a *separate* store and are never
touched here (`exportSettingsJson` doc, `SettingsStore.kt:1341`).

The Part-2 range is the machinery that makes those flat prefs **portable and
multi-device**:

- **`performDriveSync()`** (`967-1077`) — one full bidirectional pass against a
  user-granted Drive `content://` URI: download → import-if-newer → upload → record
  timestamp. This is the *single* home of the sync logic; it was previously duplicated
  between the phone's auto-sync collector and the watch's "Sync now" path, which let a
  bug (the device's own Drive URI leaking into the portable export) exist in two copies
  (`955-966`).
- **Dirty-key tracking** (`editTracked` at `1314-1329`, `dirtyKeys`/`clearDirtyKeys`) —
  records which keys this device changed locally since its last successful sync so the
  merge is **field-level** ("protect what I changed, take everything else from remote")
  rather than whole-file last-write-wins.
- **`exportSettingsJson` / `importSettingsJson` / `mergeSettingsJson`** (`1352-1528`) —
  the portable backup format (manual export/import) and the automatic-sync merge, which
  share a format but differ crucially in whether they route through `editTracked`.
- **Per-car photo embedding** (`encodeSyncPhotos` / `downscaledJpegBytes` /
  `applySyncPhotos`, `1425-1491`) — because an `img_$vin` pref is a *local file path*
  that means nothing on another device, photos are downscaled and base64-embedded as a
  separate top-level `photos` object.
- **Per-car climate + presets** (`1157-1206`) and **custom palettes** (`1208-1279`) —
  JSON-encoded structured blobs stored under single string keys.
- **Weather location** setters (`1530-1570`).

---

## 2. Public surface (Part-2 range)

Everything below is a member of `class SettingsStore(private val context: Context)`.
All the data-reading/writing methods are `suspend` because DataStore reads
(`.data.first()`) and writes (`.edit{}`) are suspend calls.

### Drive-sync configuration accessors

- **`suspend fun syncUri(): String?`** (`901-902`) — the Drive backup `content://` URI, or
  `null` when unset/blank. Read from key `sync_uri`.
- **`suspend fun setSyncUri(uri: String?)`** (`904-909`) — writes `sync_uri`; a null/blank
  URI removes the key (disables sync).
- **`suspend fun lastSyncMs(): Long`** (`912-914`) — ms timestamp of the last *successful*
  bidirectional sync (key `sync_last_ms`), `0L` if never.
- **`suspend fun setLastSyncMs(ms: Long)`** (`916-918`).
- **`suspend fun lastSyncError(): String?`** (`924-926`) — persisted last sync error string
  (key `sync_last_error`). Persisted (not just returned) so a background-worker failure —
  which has no live ViewModel/`UiState.syncError` — still surfaces in Settings on next open
  (`920-923`).
- **`suspend fun setLastSyncError(error: String?)`** (`927-929`) — null removes the key.
- **`suspend fun syncWifiOnly(): Boolean`** (`932-934`) — Wi-Fi-only sync gate (key
  `sync_wifi`), default **true**.
- **`suspend fun setSyncWifiOnly(value: Boolean)`** (`936-938`) — stored as `"true"`/`"false"`
  string (note: string key, read with `toBooleanStrictOrNull()`).

### The sync pass

- **`data class DriveSyncOutcome`** (`941-953`) — result of one pass (see §4).
- **`suspend fun performDriveSync(): DriveSyncOutcome`** (`967-1077`) — the full
  bidirectional pass, serialized process-wide by `driveSyncMutex`. Detailed control flow in §3.

### Import / export / merge

- **`suspend fun exportSettingsJson(): String`** (`1352-1378`) — serialize every non-device-local
  pref (plus embedded photos) to the portable backup JSON. Used both for manual export and as
  the body uploaded by `performDriveSync`.
- **`suspend fun importSettingsJson(json: String): String?`** (`1391-1423`) — restore a manual
  backup, overwriting matching keys. Returns an **error message on failure, null on success**.
  Routes through `editTracked` (a manual restore is a deliberate local change to be re-uploaded).
- **`exportPalettesJson` / `importPalettesJson`** — see palettes below.

### Per-car climate (`1157-1206`)

- **`suspend fun savedClimate(vin: String): ClimateRequest?`** (`1163-1166`) — last-used climate
  settings for a car (key `climate_$vin`), decoded from JSON; `null` if absent or undecodable.
- **`suspend fun saveClimate(vin: String, req: ClimateRequest)`** (`1168-1172`).
- **`suspend fun climatePresets(vin: String): List<ClimatePreset>`** (`1175-1178`) — decoded list
  (key `climate_presets_$vin`); empty list on absent/corrupt.
- **`suspend fun saveClimatePreset(vin: String, preset: ClimatePreset)`** (`1185-1192`) —
  insert-or-replace by `preset.id`; rewrites the whole array.
- **`suspend fun deleteClimatePreset(vin: String, id: String)`** (`1194-1199`) — filter out by id,
  rewrite whole array.
- **`suspend fun setClimatePresets(vin: String, presets: List<ClimatePreset>)`** (`1202-1206`) —
  persist a full reordered list.

### Custom colour palettes (`1208-1279`)

- **`suspend fun saveCustomPalette(palette: CustomPaletteData)`** (`1219-1224`) — insert-or-replace
  by `id` (key `custom_palettes`, `Keys.CUSTOM_PALETTES`).
- **`suspend fun deleteCustomPalette(id: String)`** (`1227-1233`) — remove by id; also clears the
  active-palette id (`Keys.ACTIVE_CUSTOM_PALETTE_ID`) if it matched, in the same edit.
- **`suspend fun setActiveCustomPaletteId(id: String?)`** (`1236-1241`) — null removes the key
  (revert to a built-in palette).
- **`suspend fun setCarPaletteId(vin: String, paletteId: String?)`** (`1246-1255`) — set/clear a
  per-car palette override in the `car_palette_ids` VIN→id map; null clears (uses global);
  emptying the map removes the key.
- **`suspend fun clearAllCarPaletteIds()`** (`1258-1260`) — remove `car_palette_ids` entirely.
- **`suspend fun exportPalettesJson(): String`** (`1263-1264`) — returns the raw stored
  `custom_palettes` JSON, or `"[]"` if none.
- **`suspend fun importPalettesJson(json: String): String?`** (`1270-1279`) — merge parsed palettes
  (new ids only) into the saved set; returns `"Invalid palette file"` on parse failure, null on
  success.

### Widget location (in-range but part of the widget group)

- **`suspend fun widgetLocationAddress(widgetId: Int): String?`** (`1101-1102`) and
  **`setWidgetLocationAddress(widgetId, address)`** (`1104-1109`) — the car's last-known address
  shown in the widget location box (key `widget_<id>_addr`).

### Dual-column hot-spot & powertrain (in-range)

- **`suspend fun hotspot(vin: String): String?`** (`1113-1114`) / **`setHotspot(vin, section)`**
  (`1116-1121`) — the pebble section pinned under the car-info column (key `hotspot_$vin`).
- **`suspend fun powertrain(vin: String): Powertrain?`** (`1129-1131`) / **`setPowertrain(vin, value)`**
  (`1133-1135`) — the user-confirmed powertrain override (key `ptrain_$vin`), `null` = unconfirmed.

### Remaining global appearance setters (in-range)

- **`setThemeMode`** (`1141-1143`), **`setFontChoice`** (`1145-1147`), **`setDynamicColor`**
  (`1149-1151`), **`setColorPalette`** (`1153-1155`) — each stores the enum `.name` (or
  `"true"/"false"` for dynamic colour) under its `Keys.*` entry; decoding is centralized in the
  `appearance` Flow (Part 1).

### Weather (`1530-1570`)

- **`suspend fun setWeatherLocation(lat: Double?, lon: Double?, label: String?)`** (`1533-1545`) —
  set or clear (`null` lat/lon clears all three `Keys.WEATHER_*` keys); a blank label removes the
  label key even when coords are present.
- **`suspend fun setWeatherFromDeviceLocation(): Boolean`** (`1553-1570`) — read this device's
  last-known GPS/network/passive fix, reverse-geocode a label, and store it. Returns **false** when
  no location is available (e.g. permission never granted).

---

## 3. Internal structure & control flow

### File-scope declarations (`36-54`)

- **`Context.settingsDataStore`** (`39-42`) — the single delegated DataStore, name
  `bloo_settings`, with a `ReplaceFileCorruptionHandler { emptyPreferences() }`. Rationale
  (`36-38`): a settings file damaged by an interrupted write / power loss must reset to empty
  prefs, not rethrow `IOException` out of every read — the `appearance` Flow is collected eagerly
  at launch, so a rethrow crashed the app on startup.
- **`driveSyncMutex = Mutex()`** (`49`) — **module-scope, not per-instance.** `SettingsStore` is
  instantiated fresh at each call site (not a singleton), so a per-instance lock would serialize
  nothing. Same pattern as `BlueLinkGate.statusMutex`. Serializes the periodic worker, the
  auto-sync-on-refresh collector, and a watch-requested sync process-wide.
- **`DRIVE_IO_TIMEOUT_MS = 20_000L`** (`54`) — each Drive I/O step in `performDriveSync` is capped
  at 20 s. Before this, a stalled SAF/DocumentsProvider call could hold `driveSyncMutex`
  indefinitely (`51-53`).

### `performDriveSync()` — step by step (`967-1077`)

Entire body runs inside `driveSyncMutex.withLock { … }`.

1. **URI gate** (`972`): `val uri = syncUri() ?: return@withLock DriveSyncOutcome(ran=false, …,
   syncedAtMs=lastSyncMs())`. No URI configured → not a failure, `ran=false`.
2. **Wi-Fi gate** (`973-981`): if `syncWifiOnly()`, query `ConnectivityManager` active-network
   capabilities for `TRANSPORT_WIFI`. Not on Wi-Fi → log, `return@withLock` with `ran=false`.
3. **File modified time** (`982-995`): parse the URI; if it's a `DocumentsContract` document URI,
   query `COLUMN_LAST_MODIFIED`; keep the value only if `> 0`. Cursor is `use{}`-closed. Wrapped in
   `runCatching{}.getOrNull()` — providers that don't expose it yield `null`.
4. **Download** (`996-1016`): `remoteContent` =
   `runCatching { withDriveRetry { withTimeout(DRIVE_IO_TIMEOUT_MS) { openInputStream(parsed)?.bufferedReader()?.readText() } } }`.
   On failure, `downloadError` is set to a friendly message (distinguishing
   `TimeoutCancellationException` → "Timed out reading the Drive file"). Result is `getOrNull()`.
5. **Split content** (`1017-1018`): the file body format is `"<timestamp>\n<json>"`. `remoteJson` =
   substring after the first `\n` (empty string default if no newline); `remoteTs` = the file's real
   last-modified (step 3) **or**, as fallback, the leading line parsed as `Long`, else `0L`.
6. **Import-if-newer** (`1019-1027`): only if `remoteTs > lastSyncMs() && remoteJson != null`:
   - read `protectedKeys = dirtyKeys()` **before** touching anything (`1024`) — order matters so a
     merge can't accidentally protect keys it's about to import itself;
   - `imported = mergeSettingsJson(remoteJson, protect = protectedKeys)`.
7. **Upload** (`1028-1052`): `now = System.currentTimeMillis()`; `body = "$now\n${exportSettingsJson()}"`.
   `uploaded` = `runCatching { withDriveRetry { withTimeout { … } } }`:
   - open output stream with mode `"wt"` (truncate), write `body.toByteArray()`, or `error(...)` if
     the stream is null;
   - **read-back verify** (`1043-1044`): re-open the input stream and compare the full text to
     `body`; `error("Upload didn't verify …")` if it differs. Rationale (`1036-1042`): some document
     providers silently truncate/drop a buffered write under low storage or interrupted upload;
     without verify this reported success, advanced `lastSyncMs`, and cleared the dirty set for data
     never actually saved. On failure `uploadError` is set (timeout-aware) and logged; `getOrElse { false }`.
8. **Record success** (`1053-1062`): **only if `uploaded`** — `setLastSyncMs(now)` and
   `clearDirtyKeys()`. Not bumping on total failure avoids the UI showing "Last synced just now"
   next to "Sync failed" (`1053-1056`).
9. **Compute error** (`1063`): `error = uploadError ?: downloadError?.takeIf { remoteContent == null }`
   — a download error only counts if there was genuinely nothing to read; an upload error always
   counts.
10. **Persist error** (`1068`): `setLastSyncError(error)` (survives to next app open for the
    background-worker case).
11. **Return** (`1069-1076`): `syncedAtMs = if (uploaded) now else lastSyncMs()` — matches what was
    persisted, so a caller (AppViewModel) that copies it straight into UI state can't show "synced
    just now" beside a failure.

### `withDriveRetry` (`1085-1097`)

Runs `block()` once; on throw, retries once after `delay(1000)`. Special-cases
`CancellationException`: a real coroutine-job cancellation is **rethrown immediately** (not
swallowed into a pointless retry), but a `TimeoutCancellationException` (our own
`DRIVE_IO_TIMEOUT_MS` firing) **is** retried. Generic `Exception` → one delayed retry. This absorbs a
single transient blip (Drive app waking, dropped packet) which matters because the very first pass
right after the user enables sync is often the only one that runs (`1003-1007`).

### `editTracked` (`1314-1329`) — dirty-key recording

Wraps DataStore's `edit{}`:
1. snapshot `before = HashMap(prefs.asMap())`;
2. run the caller's `mutate(prefs)`;
3. `after = prefs.asMap()`;
4. compute `touched`: any key whose value changed (`before[k] != v`), **plus** any key present
   before but absent after (removals) — the second loop (`1321`) compares by `k.name`;
5. `touched.removeAll(DEVICE_LOCAL_KEYS)` (`1322`) — device-local wiring never counts as dirty;
6. if anything remains, append the names to the CSV under `sync_dirty_keys` (read the existing set
   *from the same `prefs` snapshot*, union, re-join).

`dirtyKeys()` (`1331-1333`) reads `sync_dirty_keys` as a CSV set. `clearDirtyKeys()` (`1335-1337`)
removes the key via a **plain** `edit{}` (not `editTracked`) so clearing the dirty set doesn't
itself re-dirty anything.

### `exportSettingsJson` (`1352-1378`)

1. read all prefs (`.first()`);
2. `entries = buildJsonObject { … }` — for each pref, **skip** keys in `DEVICE_LOCAL_KEYS`
   (`1362`), else emit as a typed `JsonPrimitive` (`Boolean`/`String`/else `toString()`);
3. `photos = encodeSyncPhotos(prefs)`;
4. `root` = `{ "_format":"bloo-settings", "_version":BACKUP_VERSION, "prefs":entries,
   ["photos":{…}] }` — photos only added when non-empty;
5. pretty-printed with `backupJson`.

### `importSettingsJson` (`1391-1423`) vs `mergeSettingsJson` (`1502-1528`)

Both parse `root`, require `_format == "bloo-settings"`, and refuse a `_version` newer than
`BACKUP_VERSION`. Both decode each `prefs` primitive with the same three-way rule: JSON string →
`stringPreferencesKey`; bare JSON boolean → `booleanPreferencesKey`; anything else (a bare number)
→ coerce back to a string pref (every numeric pref in this file is stored as a string). Both skip
`DEVICE_LOCAL_KEYS`.

Two crucial differences:
- **`importSettingsJson` routes through `editTracked`** (`1403`) — a manual restore is a deliberate
  local change, so if Drive sync is configured the restored values are what the next sync pushes out.
  It does **not** honour a `protect` set (a manual restore intentionally overwrites).
- **`mergeSettingsJson` uses a plain `context.settingsDataStore.edit{}`** (`1515`) — accepting a
  value *from* remote must NOT re-mark it dirty, or it would never finish converging (`1493-1501`).
  It **does** honour `protect`: keys in `protect` are skipped (`1517`), keeping the local value.
  On a newer remote format it logs and returns `false` (skips the import half; the upload half of
  the pass still runs) rather than misapplying an unknown format (`1506-1512`).

`importSettingsJson` returns a user-facing error string for: unparseable JSON
("Invalid settings file"), wrong `_format` ("Not a Bloo settings backup"), a too-new version
("This backup was made with a newer version of Bloo — update the app first"), or missing `prefs`
("Settings file has no data"). `mergeSettingsJson` returns a `Boolean` (whether anything applied)
and never surfaces messages to the user.

### Photo embedding

- **`encodeSyncPhotos(prefs)`** (`1436-1443`): for each key starting `img_`, take the value only if
  it starts with `/` (a **local file path**, not a remote URL — a URL loads the same on any device
  so needs no embedding); `downscaledJpegBytes(path)` → `vin to Base64(NO_WRAP)`. A corrupt/missing
  file for one car is skipped (whole-export doesn't fail).
- **`downscaledJpegBytes(path)`** (`1448-1465`): bounds-only decode first
  (`inJustDecodeBounds=true`), pick `inSampleSize` by doubling while `longest/sample >
  SYNCED_PHOTO_MAX_DIM*2`, decode at that sample, then `createScaledBitmap` to exactly
  `SYNCED_PHOTO_MAX_DIM` (640) longest edge if still larger, JPEG-compress at quality **78**. Never
  fully decodes the original at full resolution.
- **`applySyncPhotos(photos, protect)`** (`1478-1491`): writes each embedded photo to
  `filesDir/cars/car_<vin>_synced.jpg` (fixed per-vin filename so repeated syncs overwrite in place,
  no orphan accumulation). Skips any vin whose `img_$vin` is in `protect`. Returns a `vin → absolute
  path` map for the caller to fold into whatever edit block it's already running — so photo paths
  land in the **same DataStore transaction** as the rest of the import/merge.

Both `importSettingsJson` (`1420`) and `mergeSettingsJson` (`1525`) apply the returned photo paths
by writing `img_$vin = path` inside their edit block. `importSettingsJson` calls
`applySyncPhotos(root["photos"], …)` with the default empty `protect`; `mergeSettingsJson` passes
the same `protect` set it received.

### Climate & palette helpers

- Two dedicated `Json { ignoreUnknownKeys = true }` instances: `climateJson` (`1159`) with
  `presetListSerializer` (`1160`), and `paletteJson` (`1210`) with `paletteListSerializer` (`1211`)
  plus `carPaletteSerializer` (`1243`, a `MapSerializer<String,String>`). Reads always
  `runCatching{ decode }.getOrElse{ empty }` so corrupt JSON degrades to empty rather than throwing.
- `readCustomPalettes()` (`1213-1216`) is the private shared read used by
  `saveCustomPalette`/`deleteCustomPalette`/`importPalettesJson`.
- Insert-or-replace pattern (`saveClimatePreset` `1185-1192`, `saveCustomPalette` `1219-1224`):
  read whole list → replace-by-id-or-append → re-encode whole array → write. There is no
  partial in-JSON update.

---

## 4. Data & types

### `data class DriveSyncOutcome` (`941-953`)
Result of one `performDriveSync` pass.
- `ran: Boolean` — false when sync isn't configured or was skipped (Wi-Fi-only, not on Wi-Fi).
- `imported: Boolean` — a newer remote file was found and merged in.
- `uploaded: Boolean` — this device's settings were successfully uploaded (and verified).
- `syncedAtMs: Long` — the timestamp this pass recorded as last-sync (`now` iff uploaded, else the
  prior `lastSyncMs()`).
- `error: String? = null` — user-facing reason for partial failure; null on full success or when
  simply not configured.

### `ClimateRequest` (defined in `shared/.../Models.kt:352-361`, `@Serializable`)
Serialized under `climate_$vin`.
- `tempF: Int`, `defrost: Boolean`, `durationMinutes: Int`,
  `steeringWheelHeat: Boolean = false`,
  `seatFrontLeft/FrontRight/RearLeft/RearRight: SeatLevel = SeatLevel.OFF`.
- `SeatLevel.apiValue` encoding (per domain facts): 0=off, 3-5=cool, 6-8=heat; crosses the wear
  wire as ints. Stored here as its serialized enum form inside the JSON blob.

### `ClimatePreset` (`Models.kt:365-369`, `@Serializable`)
Serialized as a list under `climate_presets_$vin`.
- `id: String` (identity used for insert-or-replace / delete), `name: String`,
  `request: ClimateRequest`.

### `CustomPaletteData` (defined in `app/.../ui/Theme.kt:68-74`, `@Serializable`)
Serialized as a list under `custom_palettes`.
- `id: String`, `name: String`, `primaryArgb: Int`, `secondaryArgb: Int? = null`,
  `tertiaryArgb: Int? = null`. Colours stored as packed ARGB ints.

### `Powertrain` enum (`82`)
`GAS, HYBRID, PHEV, EV`. Stored as `.name` under `ptrain_$vin`; `null` return = user hasn't confirmed
(US API only distinguishes EV vs gas).

### Constants & key sets used in-range
- `BACKUP_VERSION = 1` (`1291`) — bump only when the format stops being purely additive; older
  clients refuse a higher version.
- `DEVICE_LOCAL_KEYS = {"sync_uri","sync_last_ms","sync_last_error","sync_wifi","sync_dirty_keys"}`
  (`1300`) — never exported, never imported, never dirty-tracked.
- `SYNCED_PHOTO_MAX_DIM = 640` (`1429`) — longest-edge cap for embedded photos.
- `backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }` (`1283`).

### Key-name conventions (Part-2 range)
`sync_uri`, `sync_last_ms`, `sync_last_error`, `sync_wifi`, `sync_dirty_keys`; per-car `climate_$vin`,
`climate_presets_$vin`, `ptrain_$vin`, `hotspot_$vin`, `img_$vin`; global `custom_palettes`
(`Keys.CUSTOM_PALETTES`), `active_custom_palette_id`, `car_palette_ids`, `weather_lat/lon/label`;
per-widget `widget_<id>_addr`. Backup JSON top level: `_format`="bloo-settings", `_version`,
`prefs` (object), `photos` (object, optional). The Drive file body is `"<ts>\n<exportJson>"`.

---

## 5. State & concurrency

- **All persisted state is in one `DataStore<Preferences>`** — no in-memory caches, no companion
  singletons. Every read is `context.settingsDataStore.data.first()` (one-shot) or a `.map{}` Flow;
  every write is `editTracked{}` or (for non-tracked merge/dirty-clear) a plain `.edit{}`.
- **`driveSyncMutex`** (module-scope `Mutex`) serializes `performDriveSync` process-wide across all
  three trigger paths. `SettingsStore` is *not* a singleton, so the mutex deliberately lives at file
  scope rather than as an instance field.
- **Timeouts, not just try/catch**: each Drive I/O step is wrapped in `withTimeout(DRIVE_IO_TIMEOUT_MS)`
  so a stalled provider can't hold the mutex forever.
- **DataStore transactionality**: `edit{}` runs the mutation block against the *current* prefs
  snapshot passed in, so read-modify-write inside one block (e.g. `setCarPaletteId`, the CSV set
  toggles, `editTracked`'s own dirty-set union) is atomic against concurrent writers. Note
  `setCarPaletteId` (`1247`) and `saveClimatePreset`/`saveCustomPalette` read the *old* value with
  `.first()` **outside** the edit block, then write inside — these are not fully atomic
  read-modify-write, but collisions are unlikely for these user-driven single-writer flows.
- **Recomposition/refresh**: any `editTracked`/`edit` write flips the DataStore file, which re-emits
  the `appearance` and `notifications` Flows (Part 1) — so a `mergeSettingsJson` import from Drive
  effectively pushes another device's changes into live UI without any extra plumbing (`224-231`).
- **Dispatcher/scope**: none is imposed here — these are `suspend` functions run on whatever scope
  the caller uses (the periodic `WorkManager` worker, the refresh collector, `WearPhoneService`,
  or a ViewModel scope). DataStore handles its own IO dispatching internally.

---

## 6. Collaborators & data flow

- **Callers of `performDriveSync()`**: the periodic sync `WorkManager` worker, the
  auto-sync-on-refresh collector, and the watch's "Sync now" request (relayed via
  `WearPhoneService`). `AppViewModel` copies `DriveSyncOutcome` fields into `UiState`
  (`syncError`, last-synced time).
- **Drive**: reads/writes the file at `syncUri()` through `ContentResolver`
  (`openInputStream`/`openOutputStream("wt")`) and `DocumentsContract` (last-modified query) — a
  Storage Access Framework `content://` URI the app was granted persistent permission for.
- **`AppLog.log(...)`** — sync progress/skips/failures.
- **Filesystem**: `applySyncPhotos` writes to `context.filesDir/cars/car_<vin>_synced.jpg`; the same
  directory the photo-crop screen saves to. `img_$vin` prefs point at these paths.
- **Wear data layer**: this file doesn't touch it directly, but the settings it stores are what
  `WearSync` serializes onto the phone↔watch wire (settings/climate/presets/extras DataItem paths);
  `setWatchPinLock` (Part 1) is written *from* the watch via `WearPhoneService`.
- **Types from `:shared`**: `ClimateRequest`, `ClimatePreset` (and their `SeatLevel`). From `:app`
  UI: `CustomPaletteData`, `ColorPalette`, `FontChoice`, `ThemeMode`, `WidgetInfoField`.
- **Data in**: remote Drive JSON (untrusted, parsed defensively); a user-picked backup file for
  `importSettingsJson`; palette JSON for `importPalettesJson`; device GPS for
  `setWeatherFromDeviceLocation`.
- **Data out**: `exportSettingsJson` string (uploaded to Drive or shared as a manual backup);
  `exportPalettesJson` string.

---

## 7. Invariants & assumptions

- **`performDriveSync` always runs under `driveSyncMutex`** — no code path enters the download/merge/
  upload sequence without the lock; correctness of the merge depends on this.
- **`dirtyKeys()` must be read before the merge writes** (`1024`) so the protect set reflects
  pre-pass local state.
- **Drive file body format is exactly `"<ts>\n<json>"`** — `substringBefore/After('\n')`. A file with
  no newline yields `remoteJson=""` and `remoteTs` from the (missing) leading line → falls back to
  the real last-modified time or `0L`.
- **`lastSyncMs` and `clearDirtyKeys` advance only on verified upload success** — never on a total
  failure. This is what keeps the field-level merge safe: an unshipped local change stays "dirty"
  until it genuinely lands.
- **`img_$vin` values starting with `/` are local file paths; anything else is a remote URL.**
  `encodeSyncPhotos` embeds only the former.
- **Numeric prefs are stored as strings.** `importSettingsJson`/`mergeSettingsJson` coerce a bare
  JSON number back to a string pref (`1417`, `1522`); reading a numeric pref back always uses
  `toIntOrNull`/`toLongOrNull`/`toFloatOrNull`.
- **`DEVICE_LOCAL_KEYS` are never portable** — enforced on export (skip), import (reject), and dirty
  tracking (removeAll). Older exports that still contained them are defensively rejected on import.
- **`_format`/`_version` gating** — a version `> BACKUP_VERSION` is refused (import) or skipped
  (merge); the format string must equal `"bloo-settings"`.
- **Preset/palette identity is `id`** — insert-or-replace and delete all key off it; duplicate ids
  would collide.

## 8. Gotchas & sharp edges

- **The whole class is not a singleton.** This is why `driveSyncMutex`, `DRIVE_IO_TIMEOUT_MS`, and
  the corruption handler are all file-scope. A future refactor that made `SettingsStore` a singleton
  and moved the mutex to an instance field would silently break serialization — leave the mutex at
  module scope.
- **`editTracked` vs plain `edit`** is the single most important distinction in this file.
  `mergeSettingsJson` and `clearDirtyKeys` use plain `edit{}` on purpose. If someone "tidies up" by
  making `mergeSettingsJson` use `editTracked`, every remote value would immediately re-mark itself
  dirty and the sync would never converge (`1493-1501`, `1313`).
- **Read-back verify on upload** (`1043-1044`) — trusting `close()` was insufficient because some
  document providers silently truncate; do not remove this check.
- **`downloadError` only counts if `remoteContent == null`** (`1063`). A download that succeeds but a
  merge that no-ops is not an error; an upload error always dominates.
- **`syncedAtMs` deliberately reports the OLD time on failure** (`1074`) — so the UI can't show
  "synced just now" beside "sync failed". Any change to how AppViewModel consumes this must preserve
  that.
- **Photos are a separate top-level object**, not folded into `prefs` (`1339-1350`). An `img_$vin`
  path synced as a raw string meant nothing on a second device, so the photo silently never appeared
  anywhere but the origin device. The base64 embedding + fixed filename (`car_<vin>_synced.jpg`)
  fixes both the "doesn't appear" and "orphan accumulation" problems.
- **`TimeoutCancellationException` is retried but real cancellation is rethrown** (`withDriveRetry`,
  `1087-1093`). Confusing at a glance because both are `CancellationException` subtypes — the
  ordering of the `is` check matters.
- **Wi-Fi gate uses the *active* network only** — a device with Wi-Fi connected but routing over
  cellular could read as not-on-Wi-Fi; this is intentional conservatism.
- **`saveClimatePreset`/`saveCustomPalette`/`setCarPaletteId` do a read-outside/write-inside**
  sequence (not a single atomic RMW). Fine for single-writer user flows; a concurrent writer to the
  same list key could lose an update. The CSV-set toggles and `editTracked`'s dirty union, by
  contrast, read the value *inside* the edit block and are safe.
- **`importPalettesJson` merges new ids only** (`1274`) — an incoming palette whose id already exists
  is dropped, not updated. Sharing a re-edited palette under the same id won't overwrite the local
  copy.
- **`setWeatherFromDeviceLocation` returns false, never throws**, on missing permission/fix — the
  whole location+geocode chain is `runCatching`-wrapped; the label degrades to `"My location"`.
- **Corruption handler resets to `emptyPreferences()`** — a corrupt settings file silently reverts
  every preference to defaults on next launch (better than crashing, but the user loses local
  settings unless Drive sync restores them).
