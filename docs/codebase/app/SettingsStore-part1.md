# SettingsStore — Part 1 (prefs / appearance / tiles), lines 1–800

**File:** `app/src/main/java/com/bloo/bluelink/data/SettingsStore.kt`
**Scope of this doc:** module-scope declarations, top-level types, and the
`SettingsStore` class members through line ~800 (appearance, notifications,
alert bookkeeping, per-car identity/seat/section config, AI, shortcuts, Quick
Settings tiles, and the start of home-screen widgets). The Drive-sync engine,
climate/palette JSON, backup/restore, and weather live in the same class below
line 800 and are covered in a sibling doc; they are referenced here only where
part-1 code calls into them (`editTracked`, `performDriveSync`, `csv`).

---

## 1. Purpose

`SettingsStore` is the app's single typed façade over a Jetpack **Preferences
DataStore** file named `"bloo_settings"` (`SettingsStore.kt:39-42`). It holds
everything that is *not* session/credential data: appearance/theme, per-car
config (plate, powertrain, seats, section order, photo, service intervals),
notification thresholds, transient alert bookkeeping, on-device AI toggles,
app-icon shortcut selection, Quick Settings tile assignments, home-screen widget
config, Google Drive backup/sync, climate presets, and custom color palettes.

Design in one sentence: it is a **thin typed wrapper around a flat
string/boolean key-value bag** persisted to disk (`SettingsStore.kt:108-128`).
There is no schema/migration framework — every getter reads the current value or
a hardcoded default (absent key == "never set"), and every setter writes through
`editTracked` (line 1314), which also records which keys changed so Drive sync
can compute a "dirty" set. Structured data is JSON-encoded (kotlinx.serialization)
into a single string value. Per-car/per-tile/per-widget data interpolates the
VIN / index / widgetId directly into the key name (e.g. `"plate_$vin"`,
`"tile_0_vin"`, `"widget_${id}_actions"`) because Preferences DataStore has only
a flat namespace.

It exists separately from the session store deliberately so that **sign-out
keeps appearance/config** (`SettingsStore.kt:109`).

---

## 2. Module-scope declarations (lines 1–106)

### `Context.settingsDataStore` — `SettingsStore.kt:39-42`
```kotlin
private val Context.settingsDataStore by preferencesDataStore(
    name = "bloo_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
```
Extension-property delegate creating the single process-wide `DataStore<Preferences>`.
The `ReplaceFileCorruptionHandler` resets a damaged file (interrupted write /
power loss) to empty prefs instead of rethrowing `IOException` out of every read —
important because `appearance` is collected eagerly at launch and a throw there
crashed startup (`SettingsStore.kt:36-38`).

### `driveSyncMutex` — `SettingsStore.kt:49`
```kotlin
private val driveSyncMutex = Mutex()
```
Module-scope (NOT per-instance) mutex serializing `performDriveSync()`. Rationale
(`SettingsStore.kt:44-48`): the periodic worker, the auto-sync-on-refresh
collector, and a watch-requested sync can all fire nearly simultaneously, and
**`SettingsStore` is instantiated fresh at every call site (not a singleton)**,
so a per-instance lock would serialize nothing. Same pattern as
`BlueLinkGate.statusMutex`.

### `DRIVE_IO_TIMEOUT_MS = 20_000L` — `SettingsStore.kt:54`
Per-step cap for each Drive I/O op in `performDriveSync()`; a stalled
SAF/DocumentsProvider call previously held `driveSyncMutex` indefinitely.

---

## 3. Top-level public types (lines 56–106)

### `data class SeatConfig` — `SettingsStore.kt:64-79`
Which seat heat/cool functions a specific car actually has (user-configured;
the API has no reliable flags). The US remote-start climate command addresses
**four seat positions only** — driver, front passenger, rear-left, rear-right.
Fields (all `Boolean`, defaults shown):

| Field | Default |
|---|---|
| `driverHeat` | `true` |
| `driverCool` | `false` |
| `passHeat` | `true` |
| `passCool` | `false` |
| `rearLeftHeat` | `false` |
| `rearLeftCool` | `false` |
| `rearRightHeat` | `false` |
| `rearRightCool` | `false` |
| `steeringWheel` | `false` (heated wheel; no API flag) |

Computed property `val any: Boolean` (`SettingsStore.kt:76-78`) — true if any of
the eight seat heat/cool flags is set (steering wheel NOT included in `any`).

### `enum class Powertrain { GAS, HYBRID, PHEV, EV }` — `SettingsStore.kt:82`
User-confirmed powertrain. The US API only exposes EV vs gas; the user
disambiguates hybrid/PHEV during setup. Stored via `.name`, read with
`valueOf` + `runCatching`.

### `enum class LockTiming(val label: String)` — `SettingsStore.kt:85-91`
When the biometric app-lock re-engages after leaving foreground.
Constants + labels: `OFF("Off")`, `IMMEDIATE("Immediate")`,
`AFTER_1_MIN("1 min")`, `AFTER_5_MIN("5 min")`, `AFTER_10_MIN("10 min")`.

### `val DEFAULT_SECTIONS: List<String>` — `SettingsStore.kt:100`
```
["summary","update","controls","charge","climate","ai","info","location","weather","trips","diagnostics"]
```
Default order of reorderable detail-pebble sections. Comment (`85-99`): "climate"
is deliberately ahead of "ai" — pre-heat/cool is the primary "glance and go"
action; AI summary is passive/network-dependent. **The watch tile order mirrors
this list.**

### `val HIDEABLE_SECTIONS: List<String>` — `SettingsStore.kt:103`
```
["charge","climate","location","weather","trips","info","diagnostics","ai"]
```
Sections the user may hide; the others ("summary","update","controls") are essential.

### `const val TILE_COUNT = 12` — `SettingsStore.kt:106`
Number of configurable Quick Settings tiles (room for ~two per car).

---

## 4. `class SettingsStore(private val context: Context)` — line 129

Single constructor param `context: Context`. All state lives in the DataStore
file; the class itself is effectively stateless (see §7) apart from a few lazily
constructed `Json`/serializer fields defined below line 800.

### 4.1 `private object Keys` — `SettingsStore.kt:136-170`
Holds only the strongly-typed, non-interpolated preference keys (the ones that
are the same app-wide). Per-car/tile/widget keys are built ad hoc with string
interpolation elsewhere. All are `stringPreferencesKey` unless noted:

`THEME("theme_mode")`, `FONT("font_choice")`, `DYNAMIC("dynamic_color")`,
`PALETTE("color_palette")`, `CUSTOM_PALETTES("custom_palettes")`,
`ACTIVE_CUSTOM_PALETTE_ID("active_custom_palette_id")`,
`CAR_PALETTE_IDS("car_palette_ids")`, `WEATHER_LAT/LON/LABEL`,
`BIOMETRIC("biometric_lock")`, `LOCK_TIMING("lock_timing")`,
`FLIPPED("columns_flipped")`, `LINKS_IN_APP("links_in_app")`,
`UI_SCALE("ui_scale")`, `VIBRANCY("vibrancy")`, `HAPTICS("haptics_enabled")`,
`PEBBLE_OUTLINE("pebble_outline")`, `AURORA("aurora_background")`,
`AURORA_MOTION`, `AURORA_COLOR_MODE`, `AURORA_CUSTOM_COLOR`,
`UNIT_SYSTEM("unit_system")`, `LAST_VIN("last_vehicle_vin")`,
`ORDER("vehicle_order")`, `SETTINGS_MODE("settings_mode")`,
`WATCH_PIN_ENABLED("watch_pin_lock_enabled")`,
`WATCH_PIN_TIMING("watch_pin_lock_timing")`.
Plus `const val DEFAULT_CLIMATE_PRESET_PREFIX = "default_climate_preset_"`
(`SettingsStore.kt:169`) — a prefix, not a full key, used to build per-VIN keys.

Note the **watch PIN keys are a backup mirror only** (`163-167`): the phone
never reads or acts on them and never pushes them back to the watch.

---

## 5. Appearance

### 5.1 `data class Appearance` — `SettingsStore.kt:172-222`
The full decoded appearance snapshot. Fields with defaults & encodings:

| Field | Type | Default | Encoding / notes |
|---|---|---|---|
| `themeMode` | `ThemeMode` | `SYSTEM` | enum `.name` |
| `fontChoice` | `FontChoice` | `SYSTEM` | enum `.name` |
| `dynamicColor` | `Boolean` | `true` | stored as `"true"/"false"` string |
| `colorPalette` | `ColorPalette` | `BLUE` | enum `.name`; used when dynamic off & no custom palette |
| `customPalettes` | `List<CustomPaletteData>` | `emptyList()` | JSON list |
| `activeCustomPaletteId` | `String?` | `null` | raw string; null = built-in palette |
| `carCustomPaletteIds` | `Map<String,String>` | `emptyMap()` | JSON map VIN→paletteId; absent VIN = use global |
| `weatherLat` | `Double?` | `null` | `toString`/`toDoubleOrNull` |
| `weatherLon` | `Double?` | `null` | same |
| `weatherLabel` | `String?` | `null` | raw string |
| `useFahrenheit` | `Boolean` | `true` | **derived** from `unitSystem != "metric"` |
| `biometricLock` | `Boolean` | `false` | `"true"/"false"` string |
| `lockTiming` | `LockTiming` | `IMMEDIATE` | enum `.name` |
| `columnsFlipped` | `Boolean` | `false` | pebbles left / controls right in wide view |
| `linksInApp` | `Boolean` | `true` | in-app browser tab vs system browser |
| `uiScale` | `Float` | `1f` | range 0.85–1.3, `toString`/`toFloatOrNull` |
| `vibrancy` | `Float` | `1f` | range 0.5–1.6 |
| `auroraBackground` | `Boolean` | `false` | aurora gradient vs solid surface |
| `auroraMotion` | `String` | `"static"` | one of `off/static/motion` |
| `auroraColorMode` | `String` | `"complementary"` | one of `complementary/material/custom` |
| `auroraCustomColor` | `String?` | `null` | hex, only used when mode = "custom" |
| `unitSystem` | `String` | `"imperial"` | `imperial` or `metric` |
| `hapticsEnabled` | `Boolean` | `true` | `"true"/"false"` string |
| `pebbleOutline` | `Boolean` | `false` | hairline rim on pebbles/hero (off by default; see `213-216`) |
| `watchPinLockEnabled` | `Boolean` | `false` | backup mirror from watch |
| `watchPinLockTiming` | `String` | `"immediate"` | backup mirror from watch |

### 5.2 `val appearance: Flow<Appearance>` — `SettingsStore.kt:232-273`
Reactive view of every appearance pref at once: `context.settingsDataStore.data
.map { prefs -> Appearance(...) }`. Re-emits a freshly decoded snapshot each time
the underlying file changes (any `editTracked` write on this device, or a
Drive-sync merge). **Every field decode is defensive** — absent or unparseable
values fall back to the field default (enum reads use `runCatching { valueOf }
.getOrNull() ?: default`, JSON via `runCatching{}.getOrElse{ empty }`, booleans
via `toBooleanStrictOrNull() ?: default`). Rationale: this flow is collected
eagerly near launch, so a decode failure must never crash startup
(`SettingsStore.kt:230-231`). Two local helpers are built once per emission:
`palJson = Json { ignoreUnknownKeys = true }` and `palSer =
ListSerializer(CustomPaletteData.serializer())` (`233-234`).

`useFahrenheit` (`268`) is derived: `(prefs[UNIT_SYSTEM] ?: "imperial") != "metric"`.

### 5.3 Simple appearance setters — `SettingsStore.kt:285-457`
Each writes one `Keys.*` value through `editTracked` (persist + mark dirty).
Comment (`275-283`): booleans are stored as `.toString()` (`"true"/"false"`)
because a `booleanPreferencesKey` and a `stringPreferencesKey` of the same name
are *different keys*, and the file mixes both conventions by field vintage; the
read side always uses `toBooleanStrictOrNull()`.

- `setHapticsEnabled(Boolean)` → `HAPTICS`
- `setPebbleOutline(Boolean)` → `PEBBLE_OUTLINE`
- `setBiometricLock(Boolean)` → `BIOMETRIC` (param named `enabled`)
- `setLockTiming(LockTiming)` → `LOCK_TIMING = value.name` (read back with fallback to `IMMEDIATE`)
- `setWatchPinLock(enabled: Boolean, timing: String)` → writes `WATCH_PIN_ENABLED` + `WATCH_PIN_TIMING`. Called from **WearPhoneService** when the watch pushes a change; never from phone UI (`304-306`).
- `setColumnsFlipped(Boolean)` → `FLIPPED`
- `setUiScale(Float)` → `UI_SCALE`
- `setVibrancy(Float)` → `VIBRANCY`
- `setLinksInApp(Boolean)` → `LINKS_IN_APP`
- `setAuroraBackground(Boolean)` → `AURORA`
- `setAuroraMotion(String)` → validates against `{off,static,motion}`, else `"static"` (`440-442`)
- `setAuroraColorMode(String)` → validates against `{complementary,material,custom}`, else `"complementary"` (`444-446`)
- `setAuroraCustomColor(String?)` → removes key if null/blank, else stores raw (`448-453`)
- `setUnitSystem(String)` → validates against `{imperial,metric}`, else `"imperial"` (`455-457`)

The three "aurora"/unit setters validate at *write* time so the `appearance`
Flow never has to re-validate garbage on every read (`435-439`).

The remaining global appearance setters live below 800 but belong to this group:
`setThemeMode` (`1141`), `setFontChoice` (`1145`), `setDynamicColor` (`1149`),
`setColorPalette` (`1153`) — each stores enum `.name` (or `"true"/"false"`).

---

## 6. Notifications — `SettingsStore.kt:318-376`

### `data class NotificationPrefs` — `SettingsStore.kt:326-332`
App-wide (not per-car) toggles/thresholds:

| Field | Type | Default | Gate |
|---|---|---|---|
| `service` | `Boolean` | `true` | persistent foreground-service notification |
| `doorOpen` | `Boolean` | `true` | "door left open" alert |
| `doorOpenMinutes` | `Int` | `5` | threshold for door alert |
| `running` | `Boolean` | `true` | "engine left running" alert |
| `runningMinutes` | `Int` | `10` | threshold for running alert |

**Keys are inline literals, not in `Keys`:** `notify_service`, `notify_door`,
`notify_door_min`, `notify_running`, `notify_running_min`. Booleans use
`booleanPreferencesKey` here (native boolean prefs); the minute thresholds use
`stringPreferencesKey` + `toIntOrNull`.

### `suspend fun notificationPrefs(): NotificationPrefs` — `SettingsStore.kt:337-346`
One-shot read via `data.first()`. Used where a caller needs current values once
(e.g. deciding whether to schedule a check).

### `val notifications: Flow<NotificationPrefs>` — `SettingsStore.kt:350-358`
Reactive equivalent for live-updating Settings UI. Same decode as above.

### Setters — `SettingsStore.kt:363-376`
`setNotifyService(Boolean)`, `setNotifyDoor(Boolean)`, `setDoorOpenMinutes(Int)`,
`setNotifyRunning(Boolean)`, `setRunningMinutes(Int)`. Each is a single-expression
`=` body ending in `.let {}` purely to discard `editTracked`'s `Unit` return so
the expression-body form compiles (`360-362`). Minutes stored as strings.

---

## 7. Transient alert bookkeeping (per-car) — `SettingsStore.kt:378-417`

Mechanism (`378-387`): **AlertWorker** (`work/AlertWorker.kt`) stamps
`door_since_$vin` / `engine_since_$vin` with `System.currentTimeMillis()` when it
first observes the condition, then on each check reads it back, compares elapsed
time vs the configured `*Minutes` threshold, and — if the threshold is crossed
AND the per-condition `alertFired(key)` flag isn't set — fires the notification
and sets `alertFired`. The `*Since` value is cleared (key removed) the moment the
condition stops, so the next spell times from zero.

- `doorOpenSince(vin): Long?` — reads `door_since_$vin` via `toLongOrNull` (`388-389`)
- `setDoorOpenSince(vin, value: Long?)` — null removes key, else stores `toString` (`391-396`)
- `engineOnSince(vin): Long?` — reads `engine_since_$vin` (`398-399`)
- `setEngineOnSince(vin, value: Long?)` — null removes, else store (`401-406`)
- `alertFired(key: String): Boolean` — reads `booleanPreferencesKey("alert_$key")` default false; `key` is caller-defined, typically `"door_$vin"`/`"running_$vin"` (`408-413`)
- `setAlertFired(key, value: Boolean)` — writes `alert_$key` (`415-417`)

---

## 8. Misc mode / default-climate / per-car identity — `SettingsStore.kt:459-535`

- `settingsMode(): String` — reads `SETTINGS_MODE`, default `"simple"` (values `"simple"`/`"advanced"`) (`460-461`)
- `setSettingsMode(String)` — writes raw (`463-465`)
- `defaultClimatePreset(vin): String?` — reads `default_climate_preset_$vin` (via `DEFAULT_CLIMATE_PRESET_PREFIX`), `takeIf { isNotBlank() }` (`468-469`)
- `setDefaultClimatePreset(vin, id: String?)` — null/blank removes key, else stores (`471-476`)
- `licensePlate(vin): String` — reads `plate_$vin`, default `""` (`480-481`)
- `setLicensePlate(vin, value)` — blank removes key, else stores `value.trim()` (`483-488`)
- `lastServiceMiles(vin): Int?` — reads `svc_last_$vin` via `toIntOrNull` (`490-491`)
- `setLastServiceMiles(vin, value: Int?)` — null removes, else `toString` (`493-498`)
- `serviceIntervalMiles(vin): Int?` — reads `svc_interval_$vin` (`500-501`)
- `setServiceIntervalMiles(vin, value: Int?)` — null removes, else store (`503-508`)
- `lastVehicleVin(): String?` — reads `LAST_VIN` (`510-511`)
- `setLastVehicleVin(vin)` — writes `LAST_VIN` (`513-515`)
- `vehicleOrder(): List<String>` — reads `ORDER`, splits on `"\n"`, drops blanks, default empty (`518-520`)
- `setVehicleOrder(order)` — joins on `"\n"` (`522-524`) — **newline-delimited**, unlike section/tile lists which use commas
- `imageUrl(vin): String?` — reads `img_$vin`, `takeIf { isNotBlank() }`; empty = default gradient (`527-528`)
- `setImageUrl(vin, url)` — blank removes, else `url.trim()` (`530-535`)

The service-history fields exist because the API has no service-history fields
(`478`).

---

## 9. Per-car seat capability — `SettingsStore.kt:537-577`

### `suspend fun seatConfig(vin): SeatConfig` — `SettingsStore.kt:553-572`
Reads eight per-seat flags plus steering wheel, each under its own short key:
`seat_dh/dc/ph/pc/rlh/rlc/rrh/rrc/sw_$vin`. Local helper
`fun b(key): Boolean? = p[booleanPreferencesKey(key)]` (`555`).

**Migration mechanism (`539-552`, `561-571`):** earlier builds stored *grouped
axle* flags — `seat_fh` (front heat), `seat_fc` (front cool), `seat_rh` (rear
heat), `seat_rc` (rear cool). Each new per-seat key is looked up first; if absent
it falls back to the matching old grouped flag, then to a hardcoded default. So
an old `front-heat=true` transparently becomes both `driverHeat` and `passHeat`
on first read — **no explicit migration step or version bump**. Fallback chains:

- `driverHeat = b(dh) ?: oldFrontHeat ?: true`
- `driverCool = b(dc) ?: oldFrontCool ?: false`
- `passHeat = b(ph) ?: oldFrontHeat ?: true`
- `passCool = b(pc) ?: oldFrontCool ?: false`
- `rearLeftHeat = b(rlh) ?: oldRearHeat ?: false`
- `rearLeftCool = b(rlc) ?: oldRearCool ?: false`
- `rearRightHeat = b(rrh) ?: oldRearHeat ?: false`
- `rearRightCool = b(rrc) ?: oldRearCool ?: false`
- `steeringWheel = b(sw) ?: false`

### `suspend fun setSeatFlag(vin, field: String, value: Boolean)` — `SettingsStore.kt:575-577`
Writes `seat_${field}_$vin` as a boolean pref. `field` ∈ {dh,dc,ph,pc,rlh,rlc,rrh,rrc}
(the setter only writes *new* per-seat keys — old grouped keys are read-only legacy).

---

## 10. First-run onboarding — `SettingsStore.kt:579-594`
- `onboardingSeen(): Boolean` — `onboarding_seen`, default false (`581-582`)
- `setOnboardingSeen()` — sets `onboarding_seen = true` (write-only true; `584-586`)
- `isCarConfigured(vin): Boolean` — `car_configured_$vin`, default false (`589-590`)
- `setCarConfigured(vin)` — sets `car_configured_$vin = true` (`592-594`)

---

## 11. Per-car section order & visibility — `SettingsStore.kt:596-681`

### `suspend fun sectionOrder(vin): List<String>` — `SettingsStore.kt:617-637`
Returns the pebble render order for `vin`, reconciled against `DEFAULT_SECTIONS`
so app updates that add a new section never silently drop it. **Control flow:**
1. Read `sections_$vin`, split on `","`, drop blanks → `saved` (nullable).
2. `valid = saved.filter { it in DEFAULT_SECTIONS }` (drops renamed/removed).
3. If `valid` empty → `return DEFAULT_SECTIONS` as-is.
4. `result = valid.toMutableList()`; `missing = DEFAULT_SECTIONS - result`.
5. Partition `missing` into `lead` (== "summary" or "controls") and `trail`.
6. `lead.reversed().forEach { result.add(0, it) }` — pin lead sections to the
   front in DEFAULT_SECTIONS order (reversed so repeated index-0 inserts end up
   ordered correctly).
7. For each `section` in `trail`: find its index in `DEFAULT_SECTIONS`, take the
   *last* DEFAULT_SECTIONS entry before it that is present in `result` as
   `predecessor`, and insert right after it (`indexOf(predecessor)+1`), or at end
   if none. So a new "ai" lands right after "charge", not appended.

### `suspend fun setSectionOrder(vin, order)` — `SettingsStore.kt:639-641`
Joins on `","` → `sections_$vin`.

### `private fun csv(p: Preferences, key): Set<String>` — `SettingsStore.kt:649-650`
Shared helper: reads `key` as a comma-separated string, splits, drops blank
segments, `toSet()`, default `emptySet()`. So a stored `""` decodes to empty set,
not a set with one blank element. Used for all "set of section names" prefs.

### Collapsed / hidden sets — `SettingsStore.kt:652-681`
- `collapsedSections(vin): Set<String>` — `csv(.., "collapsed_$vin")` (`652-653`)
- `setSectionCollapsed(vin, section, collapsed: Boolean)` — read-modify-write
  **inside one `editTracked` block** using the block's live prefs (`it`), not a
  stale snapshot, so a concurrent write to the same key isn't lost (`655-667`).
- `hiddenSections(vin): Set<String>` — `csv(.., "hidden_$vin")` (`669-670`)
- `setSectionHidden(vin, section, hidden: Boolean)` — same read-modify-write
  pattern (`675-681`).

Semantic distinction (`672-674`): a **hidden** section is fully removed from
view; a **collapsed** section is still shown but closed by default. The two sets
are independent.

---

## 12. On-device AI — `SettingsStore.kt:683-698`
- `aiEnabled(): Boolean` — `ai_enabled`, default false (`685-686`)
- `setAiEnabled(Boolean)` — writes `ai_enabled` (`688-690`)
- `aiAuto(): Boolean` — `ai_auto`, default false; when on, summaries run
  automatically on open/refresh/command vs only on tap (`692-694`)
- `setAiAuto(Boolean)` — writes `ai_auto` (`696-698`)

---

## 13. App-icon shortcut selection — `SettingsStore.kt:700-710`
- `enabledShortcuts(): Set<String>?` — reads `enabled_shortcuts`; **returns null
  when the key is absent** (never customised → show all), else splits CSV to set
  (`703-706`). The nullability is meaningful: null ≠ empty set.
- `setEnabledShortcuts(ids: Set<String>)` — joins CSV (`708-710`). Shortcut ids
  look like `"cmd_vin"`.

---

## 14. Quick Settings tiles — `SettingsStore.kt:712-785`

There are `TILE_COUNT` (12) fixed tile slots; each slot `index` owns several
`tile_${index}_*` keys.

### `suspend fun tileConfig(index): Pair<String,String>?` — `SettingsStore.kt:721-726`
Returns `(vin, command)` or null. **Both** `tile_${index}_vin` and
`tile_${index}_cmd` must be present and non-blank; if either is missing the tile
is treated as fully unassigned (never half-configured; `714-720`).

### `suspend fun setTileConfig(index, vin: String?, cmd: String?)` — `SettingsStore.kt:730-737`
If either arg is null/blank, **removes both** keys (unassign); else writes both.
Enforces the "no half-configured tile" invariant.

### Other tile fields
- `tileLabel(index): String?` — `tile_${index}_label`, `takeIf isNotBlank`; null → derive from state (`740-741`)
- `setTileLabel(index, label: String?)` — null/blank removes, else `label.trim()` (`743-748`)
- `tileClimateTarget(index): String` — `tile_${index}_climate`, default `"default"`; value is `"default"`, `"smart"`, or a preset id (`751-753`)
- `setTileClimateTarget(index, target: String?)` — null/blank removes, else store raw (`755-760`)
- `tileBackground(): Boolean` — `tile_background`, default false; true = run command in background, false = open app (`763-764`)
- `setTileBackground(Boolean)` (`766-768`)
- `tileLiveRefresh(): Boolean` — `tile_live_refresh`, default false; true = tile kicks a throttled status refresh when visible (`772-773`)
- `setTileLiveRefresh(Boolean)` (`775-777`)
- `tileRefreshedAt(vin): Long` — `tile_refreshed_$vin` via `toLongOrNull`, default `0L`; epoch-ms throttle stamp keyed by VIN (not tile index) (`780-781`)
- `setTileRefreshedAt(vin, value: Long)` — stores `toString` (`783-785`)

---

## 15. Home-screen widgets (start) — `SettingsStore.kt:787-800+`

Widgets are keyed by Android's `AppWidgetManager`-assigned `widgetId` (Int), so
each placed widget instance has its own `widget_<id>_*` keys — unlike tiles
(fixed 12 slots), an arbitrary number of widgets can exist (`787-797`).

### `suspend fun widgetConfig(widgetId): Pair<String,List<String>>?` — `SettingsStore.kt:798-804`
Returns `(vin, actions)` or null. Only `widget_${id}_vin` is required to count as
configured; a missing `widget_${id}_actions` key just means an empty action list
(not unconfigured — different from tiles). Actions parsed from CSV.

*(Widget setters `setWidgetConfig`, `clearWidgetConfig`, and the remaining
`widget_*` fields continue past line 800 and are covered in the sibling doc; they
follow the same key-suffix + editTracked patterns.)*

---

## 16. State & concurrency

- **No in-memory mutable state on the instance** for part-1 members — every
  read hits `context.settingsDataStore.data.first()` and every write goes through
  `editTracked` → `DataStore.edit {}`. The class is created fresh per call site
  (not a singleton), which is why `driveSyncMutex` is at module scope (§2).
- **Flows:** `appearance` (232) and `notifications` (350) are cold `map`
  transforms over `settingsDataStore.data`. They re-emit on *any* file change,
  including a Drive-sync merge, so a remote import propagates to live UI.
  Recomposition is driven by collectors (e.g. `collectAsState`) upstream.
- **Dispatcher:** all reads/writes are `suspend`; DataStore runs its I/O on its
  own internal single-threaded dispatcher. Callers must invoke from a coroutine.
- **Atomicity:** `DataStore.edit {}` runs the mutation against the *current*
  prefs snapshot serially, giving read-modify-write safety for the CSV set
  toggles (`setSectionCollapsed`/`setSectionHidden`) that read and write the same
  key in one block.
- **Dirty tracking:** `editTracked` (1314, below scope) diffs before/after key
  values inside the same `edit {}` and appends changed key names to
  `sync_dirty_keys` (excluding `DEVICE_LOCAL_KEYS`). Every part-1 setter uses
  `editTracked`, so every part-1 write is a candidate for Drive upload.

---

## 17. Collaborators & data flow

- **`editTracked`** (1314) — the write path for essentially every part-1 setter;
  persists + records dirty keys for Drive sync.
- **`ThemeMode`, `FontChoice`, `ColorPalette`, `CustomPaletteData`** (from
  `com.bloo.bluelink.ui`) — enums/data used by `Appearance` (imports 13–16).
- **`WidgetInfoField`** (`com.bloo.bluelink.widget`) — used by widget info fields
  below scope (import 17).
- **AlertWorker** (`work/AlertWorker.kt`) — the sole consumer of the alert
  bookkeeping getters/setters in §7.
- **WearPhoneService** — calls `setWatchPinLock` (5.3) when the watch pushes a
  PIN change; the phone treats those keys as a backup mirror only.
- **Quick Settings tile service / widget providers** — consume §14/§15 getters.
- **Drive sync** (`performDriveSync`, `exportSettingsJson`, `mergeSettingsJson`,
  below scope) — reads the whole prefs bag; `img_$vin` values feed
  `encodeSyncPhotos`.
- **Channel:** everything crosses via DataStore (disk file). No intents /
  WorkManager / Wear Data Layer paths are touched *directly* in part-1 (those are
  callers). The `appearance`/`notifications` Flows are the reactive egress.

---

## 18. Invariants & assumptions

1. **Absent key == default == "never set".** No sentinel distinguishes "user
   explicitly chose the default" from "unset" (except `enabledShortcuts`, where
   null vs empty-set is deliberately meaningful, §13, and `widgetInfoFields`
   below scope where present-but-empty must stay empty).
2. **Booleans of the same name in string vs boolean key are DIFFERENT keys.**
   Appearance booleans use `stringPreferencesKey` + `"true"/"false"`; notification
   / alert / AI / onboarding / tile-flag booleans use `booleanPreferencesKey`.
   Mixing conventions per field vintage is intentional; read side must match.
3. **A tile is all-or-nothing** — both `_vin` and `_cmd` present & non-blank, or
   the tile is unassigned (§14).
4. **Seat migration is read-time and idempotent** — new per-seat keys shadow old
   grouped keys; writing only ever touches new keys (§9).
5. **`sectionOrder` always returns a complete, valid ordering** covering every
   `DEFAULT_SECTIONS` entry, even from stale/corrupt saved data (§11).
6. Enum reads assume `valueOf` may throw (renamed constant) and always
   `runCatching{}.getOrNull() ?: default`.
7. `appearance` must never throw — it is collected eagerly at launch; every
   decode is guarded (§5.2). The DataStore corruption handler backstops file-level
   corruption.
8. Delimiters are consistent per field: **vehicle order uses `"\n"`**; sections,
   tile actions, shortcut ids, widget actions, and CSV sets use `","`.

---

## 19. Gotchas & sharp edges

- **Fresh instance per call site.** `SettingsStore(context)` is cheap and created
  ad hoc; do NOT put per-instance mutable coordination state on it — it won't be
  shared. That is precisely why `driveSyncMutex` lives at module scope.
- **`.let {}` on notification setters** (`363-376`) exists only to discard
  `editTracked`'s `Unit` return so the `=`-body form compiles — not logic.
- **String-encoded booleans need `toBooleanStrictOrNull`.** Reading an
  appearance boolean with `toBoolean()` would treat any non-"true" string as
  false; the code uses `toBooleanStrictOrNull() ?: default` so a corrupt value
  falls back to the *field* default, not silently false.
- **`useFahrenheit` is derived, not stored.** It always tracks `unitSystem`; there
  is no independent `useFahrenheit` key. Anything not literally `"metric"` reads
  as Fahrenheit (so a garbage unitSystem value → Fahrenheit).
- **Aurora/unit setters validate at write time** and silently coerce unknown
  values to the default (`440-457`), so a value from a future app version is
  dropped rather than stored.
- **Section list uses commas; a section name must never contain a comma** — the
  CSV `csv()` split would corrupt it. Same for tile/widget action ids.
- **`vehicleOrder` uniquely uses newline delimiter** — don't assume comma when
  editing that key by hand.
- **Watch PIN keys are inert on the phone.** Writing them via `setWatchPinLock`
  affects only the backup mirror surfaced in `Appearance`; the phone never acts
  on or re-pushes them (`163-167`, `304-306`).
- **`onboardingSeen`/`setCarConfigured` are write-once-true** — no setter to
  reset them to false (would need a direct DataStore edit or reinstall).
- **Seat `field` string is unchecked** — `setSeatFlag(vin, "typo", true)` writes a
  junk key `seat_typo_$vin` that `seatConfig` will never read. Callers must pass
  exactly one of the 8 known suffixes.
- **`tileRefreshedAt` is keyed by VIN, not tile index** — the throttle is
  per-car, shared across all tiles pointing at the same car.
