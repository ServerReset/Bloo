# wear: local/synced stores + event buses + MainActivity

Deep-dive reference for the watch app's persistence and event-plumbing spine plus
its single Activity entry point. Covers six files:

- `wear/src/main/java/com/bloo/wear/WearLocalStore.kt`
- `wear/src/main/java/com/bloo/wear/WearSyncedStore.kt`
- `wear/src/main/java/com/bloo/wear/WearAiEvents.kt`
- `wear/src/main/java/com/bloo/wear/WearCommandEvents.kt`
- `wear/src/main/java/com/bloo/wear/WearSyncEvents.kt`
- `wear/src/main/java/com/bloo/wear/MainActivity.kt`

---

## 1. Purpose

This unit is the watch app's **local persistence layer** (settings that live only
on the watch), its **synced persistence layer** (the phone→watch payloads mirrored
to disk), the **in-process event buses** that carry one-shot async results from the
Wear Data Layer listener service back to a live ViewModel, and the **single
Activity** that hosts the whole Compose UI.

Two distinct kinds of state live here:

1. **Watch-only local state** (`WearLocalStore` / `WearLocalSettings`): font scale,
   unit system, glanceable-Tile chip choices, per-pool-slot pinned-car VINs,
   update-check debounce, and the watch's own PIN lock (salted SHA-256, never
   leaves the watch). This is authored on the watch and only its non-secret subset
   is mirrored *up* to the phone for backup.
2. **Phone-synced state** (`WearSyncedStore`): the settings/climate/presets/extras
   JSON payloads the phone pushes down the Wear Data Layer, cached to disk so tiles
   and complications can render without the phone being reachable.

The three `Wear*Events` objects are ephemeral in-process `SharedFlow` bridges: the
`WearListenerService` receives Data Layer messages even with no UI open, and these
buses hand the resulting `Wear*Result` to a live `WearViewModel` so a "fire and
forget" tap (Summarize / Sync now / a relayed lock-climate-charge command) turns
into a real busy → success/failure transition instead of a stuck spinner or a
never-reverted optimistic toggle.

`MainActivity` wires phone-synced UI scale × local font scale into `LocalDensity`,
requests notification permission once, refreshes phone reachability on resume, and
drives the PIN re-lock timing on background/foreground transitions.

---

## 2. Public surface

### WearLocalStore.kt

**`object WearTiles`** (`WearLocalStore.kt:30-51`) — the catalogue of watch-only
Tile identifiers.
- `const val` string ids: `SUMMARY="summary"`, `CLIMATE="climate"`,
  `COMFORT="comfort"`, `PRESETS="presets"`, `CHARGE="charge"`, `LIMITS="limits"`,
  `LOCATION="location"`, `WEATHER="weather"`, `INFO="info"`,
  `DIAGNOSTICS="diagnostics"`, `ASSIST="assist"`, `MORE="more"`,
  `SMART_CLIMATE="smart_climate"`, `AI="ai"` (`:31-45`).
- `val DEFAULT_ORDER: List<String>` (`:47-50`) — default tile display order before
  any user reorder: SUMMARY, CHARGE, LIMITS, AI, CLIMATE, SMART_CLIMATE, COMFORT,
  PRESETS, LOCATION, WEATHER, INFO, DIAGNOSTICS, ASSIST, MORE.

**`object WearPebbles`** (`WearLocalStore.kt:60-130`) — bridges the phone's detail
"pebbles" (its `DEFAULT_SECTIONS`) to the watch's tiles; one pebble can own several
tiles, and reorder happens per-pebble so phone and watch stay in lock-step.
- `val DEFAULT_ORDER: List<String>` (`:62-65`) — pebble order mirroring the app's
  `DEFAULT_SECTIONS`: summary, controls, charge, climate, ai, info, location,
  weather, trips, diagnostics.
- `val LABELS: Map<String,String>` (`:89-100`) — human labels per pebble key.
- `fun reorderable(order: List<String>): List<String>` (`:103-104`) — normalizes
  then strips `"summary"` (pinned first, not user-movable).
- `fun normalize(order: List<String>): List<String>` (`:108-113`) — cleans an
  incoming order: keeps only keys in `DEFAULT_ORDER`, de-dupes, forces `"summary"`
  first, and appends any missing defaults so a newly-added pebble never disappears.
- `fun tilesFor(pebbleOrder: List<String>, hiddenPebbles: Set<String> = emptySet()): List<String>`
  (`:120-129`) — expands a pebble order to the flat tile order, skipping pebbles in
  `hiddenPebbles`, always appending `TAIL` (ASSIST, MORE), then `.distinct()`.

**`object WearTilePool`** (`WearLocalStore.kt:134-136`) — `const val SIZE = 4`; the
pool of concrete Tile services the user can add to the watch face (mirrors the
phone's BlooTile1..12 QS pool).

**`val PIN_LOCK_TIMINGS: Set<String>`** (`WearLocalStore.kt:139`) — valid
`pinLockTiming` values: `{"off","immediate","1min","5min","10min"}` (same
vocabulary as the phone's LockTiming).

**`data class WearLocalSettings`** (`WearLocalStore.kt:141-161`) — see §4.

**`val TILE_CHIP_ACTIONS: List<String>`** (`WearLocalStore.kt:164`) — the canonical
ordered set of Tile chip actions: `["lock","climate","charge"]`.

**`class WearLocalStore(private val context: Context)`** (`WearLocalStore.kt:166-357`)
- `val flow: Flow<WearLocalSettings>` (`:190-217`) — reactive fully-populated
  settings snapshot derived from the DataStore prefs flow (defaults/coercion/
  validation applied per field).
- `suspend fun setFontScale(f: Float)` (`:222-224`) — persists font scale clamped to
  `[0.8, 1.4]`.
- `suspend fun setUnitSystem(value: String)` (`:229-231`) — persists the string
  verbatim (no validation on write).
- `suspend fun setUpdateLastCheckedAt(millis: Long)` (`:235-237`).
- `suspend fun setUpdateSnoozeUntil(millis: Long)` (`:241-243`).
- `suspend fun tileLastClick(poolIndex: Int): String?` (`:261-262`) — reads the
  last-handled clickable id for one pool slot (one-shot `first()`).
- `suspend fun setTileLastClick(poolIndex: Int, id: String)` (`:265-267`).
- `suspend fun setTileActions(actions: List<String>)` (`:275-278`) — filters to
  `TILE_CHIP_ACTIONS`, de-dupes, caps at 2, non-empty fallback `["lock"]`, stores
  comma-joined.
- `suspend fun setTileCarVin(index: Int, vin: String?)` (`:281-286`) — pins/clears
  slot; also clears the legacy key when `index == 0`.
- `suspend fun setPinLockEnabled(enabled: Boolean)` (`:298-300`).
- `suspend fun setPinLockTiming(value: String)` (`:305-307`) — falls back to
  `"immediate"` for any value outside `PIN_LOCK_TIMINGS`.
- `suspend fun setPin(rawPin: String)` (`:310-317`) — generates a fresh 16-byte
  salt, stores salt (Base64 NO_WRAP) + hash, arms the lock (`pinLockEnabled=true`).
- `suspend fun clearPin()` (`:320-326`) — removes salt+hash, disarms the lock.
- `suspend fun verifyPin(rawPin: String): Boolean` (`:337-343`) — reads salt+hash
  fresh, returns false on no-PIN / decode-fail / mismatch (indistinguishable).
- `private fun hashPin(pin: String, salt: ByteArray): String` (`:351-356`) — see §3.
- Private preference keys `:168-181` and `keyTileCarVin(index)` `:170`,
  `keyTileLastClick(poolIndex)` `:256`.

### WearSyncedStore.kt

**`class WearSyncedStore<T>`** (`WearSyncedStore.kt:43-61`) — generic single-key
JSON DataStore wrapper.
- `private constructor(store: DataStore<Preferences>, decode: (String?) -> T)`.
- `val flow: Flow<T>` (`:49`) — maps the store's data through `decode(prefs[key])`.
- `suspend fun save(raw: String)` (`:51-53`) — writes the raw JSON to the single
  `"payload"` key.
- `companion object` factories (`:55-60`):
  `settings(ctx) → WearSyncedStore<WearSettingsPayload?>` (`WearSync::decodeSettings`),
  `climate(ctx) → WearSyncedStore<WearClimateState>` (`WearSync::decodeClimate`),
  `presets(ctx) → WearSyncedStore<WearPresets>` (`WearSync::decodePresets`),
  `extras(ctx) → WearSyncedStore<WearExtras>` (`WearSync::decodeExtras`).

**Backward-compatible factory functions** (all `@Suppress("FunctionName")`, capitalised
to mimic the old class names) — `WearSyncedStore.kt:64-78`:
- `fun WearSettingsStore(context): WearSyncedStore<WearSettingsPayload?>` (`:65-66`).
- `fun WearClimateStore(context): WearSyncedStore<WearClimateState>` (`:69-70`).
- `fun WearPresetsStore(context): WearSyncedStore<WearPresets>` (`:73-74`).
- `fun WearExtrasStore(context): WearSyncedStore<WearExtras>` (`:77-78`).

### WearAiEvents.kt / WearCommandEvents.kt / WearSyncEvents.kt

Three structurally identical `object` event buses:

**`object WearAiEvents`** (`WearAiEvents.kt:14-19`)
- `private val _results = MutableSharedFlow<WearAiResult>(extraBufferCapacity = 4)`.
- `val results = _results.asSharedFlow()` — read-only exposure.
- `suspend fun emit(result: WearAiResult)`.

**`object WearCommandEvents`** (`WearCommandEvents.kt:19-24`)
- `private val _results = MutableSharedFlow<WearCommandResult>(extraBufferCapacity = 1)`.
- `val results = _results.asSharedFlow()`; `suspend fun emit(result: WearCommandResult)`.

**`object WearSyncEvents`** (`WearSyncEvents.kt:15-20`)
- `private val _results = MutableSharedFlow<WearSyncResult>(extraBufferCapacity = 1)`.
- `val results = _results.asSharedFlow()`; `suspend fun emit(result: WearSyncResult)`.

### MainActivity.kt

**`class MainActivity : ComponentActivity`** (`MainActivity.kt:20-81`)
- `private val viewModel: WearViewModel by viewModels()` (`:22`).
- `private val notifPermission` (`:24-25`) — `RequestPermission` launcher,
  best-effort (empty callback).
- `override fun onCreate(savedInstanceState: Bundle?)` (`:35-60`) — requests
  POST_NOTIFICATIONS on API ≥ TIRAMISU if not granted; `setContent { ... }` hosts
  `BlooWearTheme` → `CompositionLocalProvider(LocalDensity)` → `WatchApp(viewModel)`.
- `override fun onResume()` (`:62-66`) — `viewModel.refreshConnection()`.
- `override fun onStop()` (`:68-71`) — records `backgroundedAt = System.currentTimeMillis()`.
- `override fun onStart()` (`:73-80`) — on the first start after create, only flips
  `firstStart=false`; subsequent starts call `viewModel.maybeRelock(backgroundedAt)`.
- Private mutable fields: `backgroundedAt: Long = 0L` (`:32`), `firstStart: Boolean = true` (`:33`).

---

## 3. Internal structure & control flow

### `WearLocalStore.flow` mapping (`WearLocalStore.kt:190-217`)

Each emission of `context.wearLocalStore.data` runs the `map` block:
1. `fontScale` = `(prefs[keyFontScale] ?: 1f).coerceIn(0.8f, 1.4f)` (`:191`).
2. `actions` = `prefs[keyTileActions]?.split(",")?.filter { it in TILE_CHIP_ACTIONS }?.takeIf { it.isNotEmpty() } ?: listOf("lock","climate")` (`:192-196`).
3. `tileCarVins` (`:202-205`) — for each slot `i in 0 until SIZE`, read
   `keyTileCarVin(i)` (blank→null); if slot 0 is null, fall back to the legacy
   single-VIN key `keyTileCarVinLegacy`.
4. Builds `WearLocalSettings` with `unitSystem ?: "imperial"`, `updateLastCheckedAt`/
   `updateSnoozeUntil ?: 0L`, `pinLockEnabled ?: false`,
   `pinLockTiming` validated against `PIN_LOCK_TIMINGS` else `"immediate"`, and
   `hasPin = prefs[keyPinHash] != null` (`:206-216`).

### `WearPebbles.normalize` (`WearLocalStore.kt:108-113`)
1. `known` = incoming filtered to known keys, de-duped.
2. `merged` = `["summary"] + known(minus summary) + DEFAULT_ORDER(missing, minus summary)`.
3. Return `merged.distinct()`. Summary always ends up index 0; unknown keys dropped;
   never-before-seen defaults appended so nothing silently vanishes.

### `WearPebbles.tilesFor` (`WearLocalStore.kt:120-129`)
1. `src = normalize(pebbleOrder)` — filtering by `hiddenPebbles` happens *after*
   normalize so a hidden pebble isn't mistaken for a new/unknown pebble and
   re-appended.
2. For each pebble not in `hiddenPebbles`, append `TO_TILES[p].orEmpty()`.
3. Append `TAIL` (ASSIST, MORE); return `.distinct()`.

`private val TO_TILES` (`:68-84`) maps each pebble → its tiles: `summary→[SUMMARY]`,
`controls→[]` (empty — the Lock tile was dropped), `charge→[CHARGE,LIMITS]`,
`ai→[AI]`, `climate→[CLIMATE,SMART_CLIMATE,COMFORT,PRESETS]`, `info→[INFO]`,
`location→[LOCATION]`, `weather→[WEATHER]`, `trips→[]` (Trips lives in More tile),
`diagnostics→[DIAGNOSTICS]`. `private val TAIL = [ASSIST, MORE]` (`:87`).

### `hashPin` (`WearLocalStore.kt:351-356`)
`MessageDigest.getInstance("SHA-256")`, `update(salt)` first, then
`digest(pin.toByteArray(UTF-8))` — i.e. **SHA-256(salt ‖ pin)**. Result joined as
lowercase two-hex-digit-per-byte string. `verifyPin` recomputes and string-compares.

### `WearSyncedStore` mechanics
The single stored key is `stringPreferencesKey("payload")` (`:47`). `flow` maps the
raw JSON string (or null) through the injected `decode` fn — all four decoders live
in `WearSync` (`shared/.../WearSync.kt:152-174`) and each swallows parse failures
via `runCatching{...}.getOrNull()`, returning the type default (or null for
settings). `save` overwrites the whole payload key.

### `MainActivity.onCreate` density math (`:45-59`)
`phoneScale = ui.settings?.uiScale ?: 1f`; `localScale = ui.localSettings.fontScale`;
`effectiveScale = phoneScale * localScale`. Provides a new `Density(density.density,
density.fontScale * effectiveScale)` so **only font scaling** compounds the two
scales — pixel density is untouched.

---

## 4. Data & types

### `WearLocalSettings` (`WearLocalStore.kt:141-161`) — persisted-locally settings snapshot
| field | type | default | notes |
|---|---|---|---|
| `fontScale` | `Float` | `1f` | clamped `[0.8,1.4]` on read and write |
| `unitSystem` | `String` | `"imperial"` | `"imperial"`/`"metric"`; unvalidated on write, falls back to imperial on read |
| `tileActions` | `List<String>` | `["lock","climate"]` | subset of `TILE_CHIP_ACTIONS`, max 2 |
| `tileCarVins` | `List<String?>` | `List(SIZE){null}` | per pool-slot pinned VIN; null=follow selected car; sized `WearTilePool.SIZE`(=4) |
| `updateLastCheckedAt` | `Long` | `0L` | update-check debounce timestamp (ms) |
| `updateSnoozeUntil` | `Long` | `0L` | suppress update reminders until (ms) |
| `pinLockEnabled` | `Boolean` | `false` | only meaningful when `hasPin` true |
| `pinLockTiming` | `String` | `"immediate"` | one of `PIN_LOCK_TIMINGS` |
| `hasPin` | `Boolean` | `false` | derived as `keyPinHash != null`; the hash itself is never exposed via this flow |

No enums/sealed types are defined in this unit. `WearTiles`/`WearPebbles`/
`WearTilePool` are `object`s of `const val` string ids and `List`/`Map`/`Set`
literals (see §2). `PIN_LOCK_TIMINGS` and `TILE_CHIP_ACTIONS` are top-level
collection vals.

### Types this unit references but defines elsewhere (`shared/.../WearSync.kt`)
- `WearSettingsPayload` (`:398`) — phone settings incl. `uiScale` used by MainActivity.
- `WearClimateState` (`:511`), `WearPresets` (`:489`), `WearExtras` (`:531`) — synced payloads.
- `WearCommandResult(vin: String, action: String, ok: Boolean, message: String? = null)` (`:301-306`).
- `WearSyncResult(ok: Boolean, message: String? = null)` (`:310-313`).
- `WearAiResult(vin: String, ok: Boolean, message: String? = null)` (`:317-321`).

### DataStore file names & keys
Five separate Preferences DataStore files, each with a
`ReplaceFileCorruptionHandler { emptyPreferences() }`:
- `bloo_wear_local` (`WearLocalStore.kt:22-25`) — keys: `font_scale`, `tile_actions`,
  `tile_car_vin_$i`, `unit_system`, `update_last_checked_at`, `update_snooze_until`,
  `pin_lock_enabled`, `pin_lock_timing`, `pin_salt`, `pin_hash`, legacy `tile_car_vin`,
  `tile_last_click_$poolIndex`.
- `bloo_wear_settings`, `bloo_wear_climate`, `bloo_wear_presets`, `bloo_wear_extras`
  (`WearSyncedStore.kt:29-32`) — each holds a single `"payload"` string key.

---

## 5. State & concurrency

- **DataStore-backed reactive state**: both stores expose `Flow`s derived from
  `store.data.map { ... }`. Writes are `suspend fun`s using `store.edit { }`, which
  DataStore serializes internally on its own single-writer dispatcher; each edit
  triggers a fresh emission to all `flow` collectors. Collectors (chiefly
  `WearViewModel`) stay in sync without polling.
- **One-shot reads**: `tileLastClick` and `verifyPin` use `store.data.first()` to
  read the current snapshot once rather than subscribing.
- **Event buses**: `MutableSharedFlow` with **no replay** and small
  `extraBufferCapacity` (4 for AI, 1 for command/sync). `emit` is a `suspend` that,
  with buffer space, completes without suspending; with a full buffer and no
  collector it would suspend the emitter. There is no `SharedFlow` replay, so a
  result emitted while no collector is subscribed is **dropped** (buffer aside).
- **Process/threading**: `WearListenerService` runs in the same process (no
  `android:process` override) as the ViewModel, so these in-memory flows are a valid
  bridge — no cross-process/disk round-trip needed (`WearSyncEvents.kt:11-14`).
- **MainActivity state**: `backgroundedAt`/`firstStart` are plain mutable fields
  touched only on the main thread via lifecycle callbacks. `ui` is
  `viewModel.ui.collectAsState()` — recomposition of `BlooWearTheme`/`WatchApp`
  triggers whenever the ViewModel's `ui` StateFlow emits. `LocalDensity` is
  re-provided each composition from the current density × `effectiveScale`.

---

## 6. Collaborators & data flow

- **`WearViewModel`** — primary collector of `WearLocalStore.flow` and all synced
  stores' flows; also the subscriber to `WearAiEvents`/`WearCommandEvents`/
  `WearSyncEvents.results`. Calls the `set*`/`setPin`/`verifyPin`/`clearPin`/
  `maybeRelock`/`refreshConnection` methods. `MainActivity` owns the ViewModel via
  `by viewModels()`.
- **`WearListenerService`** — the Wear Data Layer listener; on receiving a phone's
  relayed result message it calls `WearAiEvents.emit` / `WearCommandEvents.emit` /
  `WearSyncEvents.emit`. It also `save()`s incoming DataItem payloads into the
  synced stores.
- **`WearSync`** (`shared/.../WearSync.kt`) — provides the `decode*` functions
  injected into `WearSyncedStore`, and defines all the `Wear*Result`/`Wear*` payload
  data classes.
- **`BlooTileService` (BlooTile1..4)** — reads/writes `tileLastClick`/
  `setTileLastClick` per pool slot for tap dedupe, and reads `tileCarVins`/
  `tileActions`.
- **Wear Data Layer paths** (context, wired by the listener/comms, not this unit):
  DataItem paths state/auth/settings/presets/climate/extras; Message paths
  command/sync_request/results. This unit is the disk landing zone (synced stores)
  and the in-process fan-out (event buses) for those.
- **Data leaving upward**: the non-secret subset of local settings (PIN
  enabled+timing, but **never** the hash/salt) is mirrored to the phone via
  `WearLocalPayload` for settings backup (`WearLocalStore.kt:290-293`).
- **`MainActivity` → Android**: `RequestPermission` for POST_NOTIFICATIONS;
  `setContent` hosting the Compose tree.

---

## 7. Invariants & assumptions

- **Single DataStore per file per process**: the delegate properties MUST be
  top-level (`WearSyncedStore.kt:19-32`, `WearLocalStore.kt:22-25`). Holding them in
  instances crashes with "There are multiple DataStores active for the same file".
- **Corruption never throws**: all five stores use
  `ReplaceFileCorruptionHandler { emptyPreferences() }` because tiles/complications
  read them off the UI thread's render path and must not throw on a torn file.
- `WearTilePool.SIZE == 4` bounds the `tileCarVins` list and the valid `poolIndex`
  range for `tileLastClick`/`setTileLastClick`/`setTileCarVin`.
- `WearPebbles.normalize` guarantees `"summary"` is always present and first;
  `reorderable` assumes summary is pinned and excludes it.
- `hasPin` (flow) is derived from the hash key's presence, so `pinLockEnabled=true`
  is only actionable when a hash actually exists; `setPinLockEnabled(true)` with no
  PIN is a no-op gate.
- `verifyPin` assumes salt was stored Base64 NO_WRAP by `setPin`; a decode failure
  is treated as verification failure.
- `firstStart` assumes cold-start locking is already decided by the ViewModel's own
  init, so the first `onStart` after create must NOT call `maybeRelock`.
- Event buses assume the listener service and the ViewModel share one process.

---

## 8. Gotchas & sharp edges

- **Legacy single-VIN migration** (`WearLocalStore.kt:180-181, 202-205, 281-286`):
  slot 0 falls back to the pre-pool `tile_car_vin` key until slot 0 is explicitly
  written; `setTileCarVin(0, …)` clears the legacy key so the fallback stops.
- **Tile tap dedupe** (`WearLocalStore.kt:245-267`): `tile_last_click` is keyed
  **per pool slot** on purpose. A single shared key let a background render of one
  pinned tile look like a fresh tap because a *different* tile was tapped more
  recently, re-firing a stale command. Tile State (incl. `lastClickableId`) is
  persisted by the system and re-delivered on every `onTileRequest` (including
  freshness/push refreshes), so `BlooTileService` MUST dedupe; clickable ids carry a
  per-render nonce so equality means "this exact tap already handled".
- **`setTileActions` caps at 2** (`:275-278`) — the glanceable Tile has room for only
  two chips even though `TILE_CHIP_ACTIONS` lists three; never stores empty (falls
  back to `["lock"]`).
- **`controls` and `trips` pebbles own no tiles** (`:75, 82`): kept in the pebble
  map (not deleted) because only pebble *order* is persisted; deleting would risk a
  normalize mismatch. The old Lock tile was dropped as a near-duplicate of
  SummaryCard's hero row; Trips lives inside the More tile.
- **`hiddenPebbles` filtering is after normalize** (`:120-129`) — filtering before
  normalize would let normalize re-append a hidden pebble as if it were a new unknown
  one.
- **PIN security posture** (`:288-343`): salted SHA-256 only, never plaintext, never
  leaves the watch; **no attempt-counting or lockout anywhere** — every `verifyPin`
  is an independent unrate-limited comparison; any repeated-failure UX is the
  ViewModel's job. `verifyPin` collapses "no PIN set", "salt decode failed", and
  "wrong PIN" into a single `false`.
- **Salt is per-`setPin` random** (`:311, 345-350`): a fresh 16-byte
  `SecureRandom` salt each time defeats precomputed rainbow tables; same PIN → a
  different hash on every device/reset.
- **`WearSyncedStore` swallows all decode errors** (via the injected `WearSync`
  decoders' `runCatching`): a malformed synced payload silently becomes the type
  default (or `null` for settings) rather than surfacing an error — tiles keep
  rendering stale/default data.
- **Density combines *scales*, not densities** (`MainActivity.kt:50-55`):
  `phoneScale * localScale` is applied to `fontScale` only; both a phone-side
  `uiScale` and a local `fontScale` compound — a large value in both multiplies.
- **Backward-compat factory functions are capitalised** to look like constructors
  (`WearSyncedStore.kt:63-78`), each `@Suppress("FunctionName")`; old call sites
  `WearSettingsStore(ctx)` etc. still compile unchanged after the refactor to the
  generic `WearSyncedStore<T>`.
- **Notification permission request is best-effort** (`MainActivity.kt:43`) wrapped
  in `runCatching` with an empty result callback — a denial is silently tolerated.
