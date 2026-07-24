# shared: `SnapshotStore` + `StatusCache`

Deep-dive reference for the two on-disk persistence layers in `:shared` that let
out-of-process surfaces (home-screen widgets, Quick Settings tiles, Wear
complications) and cold-start UI see vehicle state without reaching the live
in-memory `AppViewModel`.

Files:
- `shared/src/main/java/com/bloo/bluelink/data/SnapshotStore.kt`
- `shared/src/main/java/com/bloo/bluelink/data/StatusCache.kt`

Both live in package `com.bloo.bluelink.data`.

---

## 1. Purpose

Bloo's phone process holds the authoritative live vehicle state in
`AppViewModel`. But several surfaces cannot reach that in-memory state:

- **Home-screen widgets** (`BlooWidget`), **Quick Settings tiles**
  (`tiles/BlooTileService`), and their command workers run in *separate
  processes* / short-lived contexts and never see the ViewModel.
- The **watch's standalone / relay command path** (`WearCommandRunner`) may run
  without the phone UI ever having been opened.
- On a **cold start**, the UI needs something to show before the first network
  call returns.

The two classes here solve two distinct slices of that problem:

- **`SnapshotStore`** (`SnapshotStore.kt`) — a *small, flattened, command-capable*
  projection of each vehicle's latest state (`VehicleSnapshot`), plus which VIN
  is "selected" for glanceable surfaces. This is the thing widgets/tiles read
  to render and to rebuild a command-capable `Vehicle`. It is a live `Flow` so a
  Compose UI can react to cross-process writes. (`SnapshotStore.kt:15-19`,
  `127-135`.)
- **`StatusCache`** (`StatusCache.kt`) — the *full, raw* last-fetched
  `VehicleStatus` + `GeoLocation` + reverse-geocoded place name + fetch
  timestamp, keyed by VIN, so the phone UI can render stale-but-useful detail
  immediately at cold start until a fresh fetch lands. (`StatusCache.kt:33-37`.)

Rule of thumb: **`SnapshotStore` = the compact cross-process projection that
surfaces render and act on; `StatusCache` = the fat cold-start cache the main UI
warms itself from.**

---

## 2. Public surface

### `SnapshotStore.kt`

#### `data class VehicleSnapshot` — `SnapshotStore.kt:20-75`
`@Serializable`. The flattened per-vehicle projection persisted to disk. Full
field list in §4. Public members:

- **`fun toVehicle(): Vehicle`** — `SnapshotStore.kt:65-74`. Rebuilds a
  command-capable `Vehicle` (from `Models.kt:62`) out of the snapshot, copying
  `vin, regId, name, model, generation, brandIndicator, isEv, odometer`. Used by
  widgets/tiles that hold only a snapshot but need to issue a command (commands
  operate on a `Vehicle`). Note: `hasBattery`, `percent`, location, etc. are
  **not** carried into `Vehicle` — `Vehicle` is the command-addressing struct,
  not the state struct.

#### `val VehicleSnapshot.isDriving: Boolean` — `SnapshotStore.kt:77-80`
Extension property. `get() = (speedMph ?: 0.0) > 0.0`. The snapshot-based
equivalent of `AppViewModel.isDriving()`, for out-of-process command runners
that only ever hold a `VehicleSnapshot`. Used to gate "car rejects climate
commands while driving." Mirrors `VehicleStatus.isDriving` (`Models.kt:121`).

#### `fun VehicleSnapshot.merged(status: VehicleStatus): VehicleSnapshot` — `SnapshotStore.kt:82-106`
Extension. Folds a freshly fetched `VehicleStatus` into an existing snapshot,
returning a new copy. Control flow in §3. Critically uses `hasBattery` (not
`isEv`) when computing `percent`/`rangeMi`.

#### `class SnapshotStore(private val context: Context)` — `SnapshotStore.kt:136`
The store. Members:

- **`val payload: Flow<SnapshotData>`** — `SnapshotStore.kt:147-149`. Live stream;
  re-emits on every underlying DataStore file change (including cross-process
  writes), decoding each emission into `SnapshotData`.
- **`suspend fun current(): SnapshotData`** — `SnapshotStore.kt:154`. One-shot
  read (`.data.first()`) for callers that don't need to keep observing.
- **`suspend fun saveVehicles(vehicles: List<VehicleSnapshot>)`** —
  `SnapshotStore.kt:160-170`. Replaces the whole vehicle list (e.g. after a full
  account refresh); preserves selection intelligently (§3).
- **`suspend fun updateVehicle(snapshot: VehicleSnapshot)`** —
  `SnapshotStore.kt:173-182`. Replaces a single vehicle's snapshot by VIN match,
  leaving `selectedVin` untouched.
- **`suspend fun setSelected(vin: String)`** — `SnapshotStore.kt:186-194`. Changes
  the selected VIN without touching vehicle data.
- **`suspend fun selectNext(): VehicleSnapshot?`** — `SnapshotStore.kt:197-211`.
  Advances selection to the next car (looping), returns the newly selected
  snapshot (or `null` if list empty).
- **`data class SnapshotData(vehicles, selectedVin)`** — nested public type,
  `SnapshotStore.kt:228-238`. See §4.

(`private fun decode(raw: String?)`, `private object Keys`, and the private
`json` field are covered in §3.)

### `StatusCache.kt`

#### `class StatusCache(private val context: Context)` — `StatusCache.kt:38`
- **`data class Cached(statuses, locations, placeNames, fetched)`** — nested
  public type, `StatusCache.kt:46-51`. The decoded, in-memory view. See §4.
- **`suspend fun load(): Cached`** — `StatusCache.kt:61-66`. One-shot read + decode
  with empty fallback (§3).
- **`suspend fun save(statuses, locations, placeNames, fetched)`** —
  `StatusCache.kt:74-86`. Serializes all four maps into one JSON blob and writes
  wholesale in a single atomic edit. **No per-VIN partial update** — callers pass
  the full merged state.

`CachePayload` (the wire type) is `private`; see §4.

---

## 3. Internal structure & control flow

### `SnapshotStore`

**DataStore declaration** — `SnapshotStore.kt:122-125`:
```
private val Context.snapshotDataStore by preferencesDataStore(
    name = "bloo_snapshots",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
```
A Preferences DataStore extension property on `Context`, file name
`bloo_snapshots`. The corruption handler resets a damaged file (interrupted
write / power loss) to empty prefs instead of throwing out of every read — vital
because the widget, tiles, and complications all read this and would otherwise
crash on a corrupt file.

**Private members inside the class:**
- `json = Json { ignoreUnknownKeys = true }` — `SnapshotStore.kt:138`. Note: does
  **not** set `encodeDefaults` (unlike `StatusCache`); default-valued fields may
  be omitted from the written JSON, which is fine because every field either has
  a default or is required.
- `object Keys { val PAYLOAD = stringPreferencesKey("payload") }` —
  `SnapshotStore.kt:140-142`. The single string key everything is stored under.

**The read-modify-write pattern:** every mutator (`saveVehicles`,
`updateVehicle`, `setSelected`, `selectNext`) follows the same shape inside
`snapshotDataStore.edit { prefs -> ... }`:
1. `decode(prefs[Keys.PAYLOAD])` → current `SnapshotData`.
2. Compute the new list / selection.
3. `prefs[Keys.PAYLOAD] = json.encodeToString(SnapshotPayload.serializer(), SnapshotPayload(...))`.

The `edit` block is transactional (single file + mutex), so concurrent writers
from different processes don't stomp each other. Note that the whole
`SnapshotPayload` (vehicle list + selection) is re-encoded on every write — it's
one atomic blob, never per-field.

**`saveVehicles` selection logic** — `SnapshotStore.kt:163-164`:
```
val selected = existing.selectedVin?.takeIf { sel -> vehicles.any { it.vin == sel } }
    ?: vehicles.firstOrNull()?.vin
```
Keep the prior `selectedVin` iff that car is still in the new list; otherwise
fall back to the first vehicle's VIN (or `null` if the new list is empty). So
there's always a valid selection whenever the list is non-empty.

**`selectNext` control flow** — `SnapshotStore.kt:197-211`:
1. `result` starts `null`.
2. Inside `edit`: if `existing.vehicles.isEmpty()` → `return@edit` (leaves store
   unchanged, returns `null`).
3. `idx = indexOfFirst { it.vin == selectedVin }` — **note: returns `-1` if the
   selected VIN isn't found** (or none selected).
4. `next = vehicles[(idx + 1).mod(vehicles.size)]`. Using Kotlin's `.mod` (always
   non-negative) means `idx = -1` → `(-1+1).mod(n) = 0` → first vehicle, and the
   last index wraps to 0. So it loops correctly and handles the not-found case by
   landing on the first car.
5. Writes the new payload with `next.vin` selected, sets `result = next`, returns
   it.

**`decode(raw: String?)`** — `SnapshotStore.kt:219-224`:
```
val payload = raw?.let {
    runCatching { json.decodeFromString(SnapshotPayload.serializer(), it) }.getOrNull()
} ?: SnapshotPayload()
return SnapshotData(payload.vehicles, payload.selectedVin)
```
Null raw (nothing saved yet) **or** a parse failure (corrupt/incompatible —
belt-and-suspenders alongside the DataStore-level corruption handler) both fall
back to an empty `SnapshotPayload()`. Every caller expects to read even before
anything was ever written, so this never throws.

**`merged(status)` control flow** — `SnapshotStore.kt:83-105`:
1. `pct = status.percentFor(hasBattery)` — `Models.kt:425`, returns
   `evStatus?.batteryStatus` when `hasBattery` else `fuelLevel`.
2. `range = status.rangeMiFor(hasBattery)` — `Models.kt:433`, EV battery range
   (`evStatus.drvDistance.first().rangeByFuel.totalAvailableRange.value`) when
   `hasBattery`, else falls back to `dte.value`; `.toInt()`.
3. Returns `copy(...)` where each field uses the freshly fetched value **or the
   old value if the new one is null** (`?: existingField`), so a status that
   omits a field never wipes prior data:
   - `percent = pct ?: percent`, `rangeMi = range ?: rangeMi`
   - `locked = status.doorLock ?: locked`
   - `charging = status.evStatus?.batteryCharge ?: charging`
   - `climateOn = status.airCtrlOn ?: climateOn`
   - `engineOn = status.engine ?: engineOn`
   - `lat = status.vehicleLocation?.coord?.lat ?: lat`
   - `lon = status.vehicleLocation?.coord?.lon ?: lon`
   - `speedMph = status.vehicleLocation?.speed?.value ?: speedMph`
   - `updated = status.dateTime ?: updated`
   - `fetchedAt = System.currentTimeMillis()` — **always** stamped, because
     `merged()` folds a status we *just* fetched, so the data is current now.

### `StatusCache`

**DataStore declaration** — `StatusCache.kt:16-19`: `preferencesDataStore(name =
"bloo_status_cache", corruptionHandler = ...emptyPreferences())`. Same corruption
strategy; this cache is read at cold start before any network call returns.

**Private members:**
- `json = Json { ignoreUnknownKeys = true; encodeDefaults = true }` —
  `StatusCache.kt:43`. **`encodeDefaults = true` here** (unlike `SnapshotStore`):
  every field is always written, so a reader on an older app version (or after a
  rollback) still finds all keys present. `ignoreUnknownKeys` lets old files
  survive future field additions.
- `key = stringPreferencesKey("payload")` — `StatusCache.kt:44`.

**`load()`** — `StatusCache.kt:61-66`: read `data.first()[key]` → `runCatching {
decode } .getOrNull() ?: CachePayload()` → unpack into `Cached`. Same null/parse
fallback philosophy as `SnapshotStore.decode`.

**`save(...)`** — `StatusCache.kt:74-86`: `statusCacheStore.edit { it[key] =
json.encodeToString(CachePayload.serializer(), CachePayload(...)) }`. Wholesale
replace; the four maps passed in ARE the complete new state.

---

## 4. Data & types

### `VehicleSnapshot` — `SnapshotStore.kt:20-63`
`@Serializable data class`. Fields (name : type = default):

| Field | Type | Default | Meaning / encoding |
|---|---|---|---|
| `vin` | `String` | — | Vehicle identifier; the primary key everywhere. |
| `name` | `String` | — | User-facing car name. |
| `model` | `String` | — | Model label. |
| `isEv` | `Boolean` | — | Raw EV flag from the API. |
| `hasBattery` | `Boolean` | `= isEv` | User's **manual powertrain override**. A PHEV the API misreports as gas still needs the Charge tile. Defaults to `isEv` so snapshots built without an override (e.g. watch's standalone fetch) behave as before. Drives `percentFor`/`rangeMiFor`. (`:26-31`) |
| `regId` | `String` | `""` | Registration id, needed for commands. |
| `generation` | `String` | `"2"` | API generation; affects endpoint shapes. |
| `brandIndicator` | `String` | `"H"` | Brand code (H = Hyundai etc.). |
| `percent` | `Int?` | `null` | Headline charge/fuel %. |
| `rangeMi` | `Int?` | `null` | Headline range in miles. |
| `locked` | `Boolean?` | `null` | Door lock state. |
| `charging` | `Boolean?` | `null` | EV charging in progress. |
| `climateOn` | `Boolean?` | `null` | Climate/HVAC running. |
| `engineOn` | `Boolean?` | `null` | Engine running. |
| `lat` | `Double?` | `null` | Last-known latitude. |
| `lon` | `Double?` | `null` | Last-known longitude. |
| `speedMph` | `Double?` | `null` | mph from last status `vehicleLocation.speed`. Powers `isDriving` gate for out-of-process runners. (`:43-50`) |
| `updated` | `String?` | `null` | The car's own `dateTime` string from the status. |
| `fetchedAt` | `Long` | `0L` | Wall-clock ms when this snapshot last got fresh car data; `0` = unknown. Lets glanceable surfaces flag stale data. (`:52-55`) |
| `odometer` | `String?` | `null` | Odometer reading. |
| `licensePlate` | `String?` | `null` | User-entered plate, mirrored so e.g. watch Info tile can show it. (`:57-59`) |
| `lastServiceMiles` | `Int?` | `null` | Service-due tracking (phone Settings). |
| `serviceIntervalMiles` | `Int?` | `null` | Service interval. |

**Two distinct timestamps:** `updated` = the car's self-reported time (a
String); `fetchedAt` = when *we* fetched (epoch ms). Don't conflate them —
staleness detection uses `fetchedAt`.

### `SnapshotPayload` — `SnapshotStore.kt:112-116` (private)
`@Serializable`. The exact on-disk shape, one JSON blob under one key:
- `vehicles: List<VehicleSnapshot> = emptyList()`
- `selectedVin: String? = null`

Kept as one blob (not one DataStore entry per field) so a read/write is always a
single atomic operation over the whole list + selection.

### `SnapshotStore.SnapshotData` — `SnapshotStore.kt:228-238` (public nested)
Decoded view:
- `vehicles: List<VehicleSnapshot>`
- `selectedVin: String?`
- **`val selected: VehicleSnapshot?`** — `get() = vehicles.firstOrNull { it.vin
  == selectedVin } ?: vehicles.firstOrNull()`. The selected car, or the first
  vehicle if the recorded selection matches no known VIN (car removed from
  account since selection saved), or `null` if no vehicles. (`:236-237`)

### `CachePayload` — `StatusCache.kt:25-31` (private)
`@Serializable`. Wire format, one JSON string under one key. All maps keyed by
VIN, so one payload holds every vehicle at once. All default to empty so an
old/partial payload (or the corruption fallback's `emptyPreferences()`) still
parses:
- `statuses: Map<String, VehicleStatus> = emptyMap()`
- `locations: Map<String, GeoLocation> = emptyMap()`
- `placeNames: Map<String, String> = emptyMap()` — reverse-geocoded label per
  VIN, cached to avoid re-geocoding.
- `fetched: Map<String, Long> = emptyMap()` — epoch-millis fetch timestamp per
  VIN.

### `StatusCache.Cached` — `StatusCache.kt:46-51` (public nested)
Identical field set to `CachePayload` but as a plain (non-serializable) in-memory
carrier: `statuses`, `locations`, `placeNames`, `fetched` (same types, no
defaults).

### Referenced external types (defined in `Models.kt`, documented for context)
- **`Vehicle`** — `Models.kt:62-71`. Command-addressing struct: `vin, regId,
  name, model, generation, brandIndicator, isEv, odometer?`.
- **`VehicleStatus`** — `Models.kt:83-122`. The fat raw status (`doorLock`,
  `airCtrlOn`, `engine`, `evStatus`, `vehicleLocation`, `dateTime`, `fuelLevel`,
  `dte`, tire/seat/diagnostic sub-objects…). Has `val isDriving: Boolean` at
  `Models.kt:121`.
- **`EvStatus`** — `Models.kt:230-247`. `batteryCharge`, `batteryStatus`,
  `batteryPlugin` (0=unplugged, 1=DC fast, 2=AC per `pluggedInLabel`),
  `drvDistance`, etc. (Distinct from `plugType`/`ReservChargeInfos` numbering: 0=DC,
  1=AC — see `targetForCurrentPlug` at `Models.kt:446`.)
- **`GeoLocation`** — `Models.kt:411-416`. `latitude`, `longitude`, `speed?`
  (>0 ⇒ moving). Note the field is `latitude`/`longitude`, whereas the status's
  own embedded location uses `vehicleLocation.coord.lat/lon`.
- **`percentFor(hasBattery)`** — `Models.kt:425-426`.
- **`rangeMiFor(hasBattery)`** — `Models.kt:433-436`.

---

## 5. State & concurrency

- **All state is on-disk in Jetpack DataStore (Preferences).** Neither class
  holds mutable in-memory state; each is a thin stateless wrapper over a
  `Context`-scoped DataStore extension property. `SnapshotStore` and
  `StatusCache` instances can be created ad-hoc anywhere (e.g. `SnapshotStore` is
  constructed fresh inside `WearCommandRunner` at `WearCommandRunner.kt:26` and
  `:148`).
- **One key, one blob each.** `SnapshotStore` stores everything under
  `stringPreferencesKey("payload")` in file `bloo_snapshots`; `StatusCache` under
  `"payload"` in `bloo_status_cache`. Each write re-encodes the entire blob.
- **Transactionality/serialization** comes entirely from DataStore: `edit { }`
  runs as a single transaction backed by a per-file mutex, so concurrent writers
  (widget refresh while watch relay writes) are serialized and don't corrupt each
  other. This is the *only* locking — there is no explicit `Mutex` in either
  file, and this is unrelated to `BlueLinkGate.statusMutex` (that serializes
  network calls, not disk writes).
- **Dispatcher/scope:** all methods are `suspend`; DataStore does its own IO on
  `Dispatchers.IO` internally. Callers supply the coroutine scope.
- **Recomposition:** only `SnapshotStore.payload` is reactive — it's
  `snapshotDataStore.data.map { decode(...) }`, a cold `Flow` that DataStore
  re-emits on every file change (including cross-process writes). A Compose UI
  `collectAsState`-ing it recomposes on any write from any process.
  `StatusCache` exposes **no** Flow — `load()` is one-shot only.
- **Corruption handling:** both DataStores use
  `ReplaceFileCorruptionHandler { emptyPreferences() }` so a damaged file resets
  to empty rather than throwing; both decode paths additionally wrap parsing in
  `runCatching{}.getOrNull() ?: <empty payload>` as a second layer.

---

## 6. Collaborators & data flow

### `SnapshotStore`
**Writers (data in):**
- `AppViewModel` (phone UI) — populates snapshots after account/status refreshes
  (`saveVehicles`, `updateVehicle`, selection changes).
- `WearCommandRunner` — `WearCommandRunner.kt:86` `store.updateVehicle(updated)`
  after a command; `:166`
  `repo.status(v, refresh=force)?.let { store.updateVehicle(snap.merged(it)) }`
  during standalone/command-triggered refreshes. This is the exact path
  `merged()` was hardened for (using `hasBattery`, not `isEv`).
- Widget/tile command workers (`WidgetCommandWorker`, `TileActionActivity`,
  `TileCommandRunner`) — write back updated snapshots after issuing commands.

**Readers (data out):**
- `BlooWidget`, `tiles/BlooTileService`, `WidgetConfigActivity` — read `current()`
  / collect `payload` to render, and call `toVehicle()` to issue commands.
- `selectNext()` / `setSelected()` drive the widget/tile "cycle to next car"
  affordance.
- The Wear side ultimately consumes snapshot-derived data over WearSync, but the
  snapshot store itself is phone/shared-side; the watch has its own stores
  (`ComplicationCarStore` etc.).

**Channel:** function calls only, backed by the `bloo_snapshots` DataStore file
(shared across processes via the file itself). `toVehicle()` bridges snapshot →
command layer.

### `StatusCache`
**Writer:** `AppViewModel.kt:364` `statusCache.save(s.statuses, s.locations,
s.placeNames, s.lastFetched)` — the ViewModel dumps its full merged map state.
**Reader:** `AppViewModel.kt:437` `val cached = statusCache.load()` — cold-start
warm-up. Constructed at `AppViewModel.kt:273` `private val statusCache =
StatusCache(app)`.

`StatusCache` is effectively **phone-UI-private**: only `AppViewModel` reads and
writes it, for cold-start rendering. It does not participate in the widget/tile
cross-process story the way `SnapshotStore` does.

**Data enters** from `VehicleRepository` fetches (raw `VehicleStatus` +
`GeoLocation`) and geocoding (`placeNames`); **leaves** back into `AppViewModel`'s
in-memory state at startup.

---

## 7. Invariants & assumptions

- **VIN is the identity key** in both stores. `updateVehicle` matches by
  `it.vin == snapshot.vin`; if no snapshot with that VIN exists, `map` produces
  no match and the update is silently a no-op (the list is returned unchanged).
  `CachePayload` maps are VIN-keyed.
- **There is always a valid selection when the list is non-empty** — enforced by
  `saveVehicles` selection fallback and by `SnapshotData.selected`'s
  `?: vehicles.firstOrNull()`.
- **`merged()` must be called with the correct `hasBattery`** already set on the
  snapshot — it reads `this.hasBattery`, not `status.isEv`. The snapshot's
  `hasBattery` is the source of truth for EV-ness in percent/range.
- **Null-in-status means "no update", never "clear"** — every `merged()` field
  uses `newValue ?: oldValue`. Code relying on `merged()` to *clear* a field
  (e.g. mark climate off) will NOT work if the status simply omits the field;
  the status must report the explicit `false`/new value.
- **DataStore `edit` holds the file lock for the whole read-modify-write** — the
  decode-inside-edit pattern is required for correctness; decoding *outside* the
  edit and writing inside would reintroduce a lost-update race.
- **`selectNext` assumes `.mod` (floor-mod) semantics**, tolerating `idx == -1`
  (not found / nothing selected) by wrapping to index 0.
- Callers of `StatusCache.save` **must pass the complete merged state** — there
  is no partial/per-VIN write; a save with a subset of VINs deletes the rest.
- Both stores assume it's acceptable to silently discard a corrupt payload
  (reset to empty) rather than surface an error.

---

## 8. Gotchas & sharp edges

- **`isEv` vs `hasBattery` (the headline footgun).** The comment at
  `SnapshotStore.kt:84-89` documents a real prior bug: `merged()` originally
  reimplemented `percentFor`/`rangeMiFor` logic but with `isEv` instead of the
  user's manual `hasBattery` override. On a PHEV the API misreports as gas, every
  refresh through `WearCommandRunner.refresh` would clobber `percent`/`rangeMi`
  with **fuel** data instead of **battery** data. The fix routes through
  `status.percentFor(hasBattery)` / `rangeMiFor(hasBattery)`. Any new code that
  computes headline %/range must use `hasBattery`.
- **`hasBattery` defaults to `isEv`.** Snapshots built without a powertrain
  override (notably the watch's own standalone vehicle fetch, `:26-31`) behave
  exactly like the old `isEv`-only logic. Only the phone UI knows the manual
  override; if a snapshot is built somewhere that doesn't plumb it through, PHEV
  handling silently degrades.
- **Two timestamps, easy to mix up.** `updated` (String, the car's own
  `dateTime`) vs `fetchedAt` (Long epoch ms, when *we* fetched). Staleness
  badges must use `fetchedAt`; `0L` means unknown. `merged()` always refreshes
  `fetchedAt` but only conditionally refreshes `updated`.
- **`SnapshotStore.json` does NOT set `encodeDefaults`, but `StatusCache.json`
  does.** Divergent on purpose: `StatusCache` writes all keys so
  older-app-version readers (post-rollback) find every key present
  (`StatusCache.kt:42-43`); `SnapshotStore` relies on kotlinx defaults on decode,
  so omitting default-valued fields is harmless. Don't "unify" these without
  understanding the rollback-compat reasoning.
- **`GeoLocation.latitude/longitude` vs `vehicleLocation.coord.lat/lon`.** The
  cache stores `GeoLocation` (latitude/longitude field names), but `merged()`
  reads the *status's* embedded `vehicleLocation.coord.lat/lon` into the
  snapshot's `lat`/`lon`. Three different location representations coexist
  (snapshot lat/lon Doubles, `GeoLocation`, `VehicleStatus.vehicleLocation`);
  don't assume they're the same struct.
- **`updateVehicle` on an unknown VIN is a silent no-op** — it maps over the
  existing list and only replaces on VIN match. It will not *add* a new vehicle;
  use `saveVehicles` for that.
- **`selectNext` returns the newly selected snapshot, but returns `null` on an
  empty list** — callers must handle `null` (nothing to cycle to) distinctly
  from a real snapshot.
- **Belt-and-suspenders corruption handling is intentional** (both the DataStore
  `corruptionHandler` and the `runCatching` in decode). The DataStore handler
  covers file-level corruption; the `runCatching` covers a syntactically valid
  file with JSON that no longer decodes (schema drift). Both matter because these
  stores are read from crash-sensitive out-of-process surfaces
  (`SnapshotStore.kt:118-121`, `StatusCache.kt:13-15`).
- **Everything is one atomic blob per store.** There is no per-field or per-VIN
  DataStore entry; every write serializes and rewrites the whole payload. Fine at
  Bloo's scale (a handful of vehicles) but not a pattern to copy for large
  collections.
- **`StatusCache` has no reactive Flow.** Unlike `SnapshotStore.payload`, it's
  load-once. A surface wanting live status updates must use `SnapshotStore` or
  the ViewModel, not `StatusCache`.
