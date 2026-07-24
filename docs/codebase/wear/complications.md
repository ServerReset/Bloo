# Wear OS Complications (`com.bloo.wear.complication`)

> Deep-dive reference for the entire `wear/.../complication/` package: 4 complication
> data sources (Charge, Climate, Lock, and their shared `ToggleStateComplication` base),
> the per-slot car pin store (`ComplicationCarStore`), the picker config Activity
> (`ComplicationConfigActivity`), the refresh nudger (`ComplicationLink`), and the tap
> broadcast handler (`ComplicationTapReceiver`).
>
> Files:
> - `ChargeComplication.kt`
> - `ClimateComplication.kt`
> - `LockComplication.kt`
> - `ToggleStateComplication.kt`
> - `ComplicationCarStore.kt`
> - `ComplicationConfigActivity.kt`
> - `ComplicationLink.kt`
> - `ComplicationTapReceiver.kt`
>
> (The task named "all 8"; there are 7 source files. `ClimateComplication` and
> `LockComplication` are the two concrete `ToggleStateComplication` subclasses, so the
> user-facing complication *types* number four: Charge, Climate, Lock, plus the abstract
> Toggle base — hence the "8" tally counting base + 3 concrete + 4 support files.)

---

## 1. Purpose

This package implements Bloo's **watch-face complications** — the small glanceable
widgets a user places into a slot on their Wear OS watch face. Three concrete
complications exist:

- **Charge/Fuel level** (`ChargeComplication`) — battery/fuel percentage, optional range,
  a bolt while charging. Tapping opens the Bloo app to that car.
- **Lock** (`LockComplication`) — padlock icon + Locked/Unlocked text. Tapping toggles
  lock/unlock on the car.
- **Climate** (`ClimateComplication`) — snowflake icon + On/Off. Tapping starts/stops
  climate.

All of them render **entirely from the phone-synced [`SnapshotStore`]** (no network at
render time). They are Wear OS **system services** (`SuspendingComplicationDataSourceService`)
declared in the manifest and driven by the watch-face host process — this app never
starts, binds, or holds state in them. Each override is a stateless answer to one
system-issued request.

Supporting infrastructure in the package:

- `ComplicationCarStore` — a tiny DataStore mapping each complication *instance* (slot) to
  a VIN, so different slots can show different cars.
- `ComplicationConfigActivity` — the picker-launched Activity that lets the user choose
  which car a slot shows.
- `ComplicationLink` — nudges all three complications to re-render after a command/sync.
- `ComplicationTapReceiver` — the `BroadcastReceiver` that a toggle-complication tap fires
  into, relays the command via `WearComms`, then requests a refresh.

---

## 2. Public surface

### `ChargeComplication : SuspendingComplicationDataSourceService`
`ChargeComplication.kt:54`

Charge/fuel level complication. Supports `SHORT_TEXT` and `RANGED_VALUE`.

- **`override fun getPreviewData(type: ComplicationType): ComplicationData?`** —
  `ChargeComplication.kt:59`. Synchronous, instant, no suspension. Returns fake but
  plausible preview data for the complication *picker* UI:
  `buildData(type, pct = 82, rangeMi = 210, isEv = true, charging = true)`.
- **`override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData?`** —
  `ChargeComplication.kt:70`. Live render for one slot. Resolves the car via
  `resolveComplicationCar(applicationContext, "ChargeComplication", request.complicationInstanceId)`;
  returns `null` (clears slot) if no car. Reads the metric preference from
  `WearLocalStore(this).flow.first().unitSystem == "metric"` (wrapped in `runCatching`,
  default `false`). Then `buildData(request.complicationType, snap.percent, snap.rangeMi,
  snap.hasBattery, snap.charging == true, snap.vin, metric)`.
- **`override fun onComplicationDeactivated(complicationInstanceId: Int)`** —
  `ChargeComplication.kt:81`. Calls `clearComplicationConfig(applicationContext,
  "ChargeComplication", complicationInstanceId)` to GC the per-slot VIN pin.

### `ToggleStateComplication : SuspendingComplicationDataSourceService` (abstract)
`ToggleStateComplication.kt:23`

Shared base for on/off toggle complications. Renders a state-reflecting icon + text as
`SHORT_TEXT` or `MONOCHROMATIC_IMAGE`; toggles `action` on tap.

Abstract members subclasses must supply:
- **`protected abstract val dataSourceName: String`** (`:26`) — key passed to
  `resolveComplicationCar` / `clearComplicationConfig`.
- **`protected abstract val title: String`** (`:28`) — `SHORT_TEXT` title line.
- **`protected abstract val action: String`** (`:30`) — the `WearAction` string fired on
  tap.
- **`protected abstract fun stateOf(snap: VehicleSnapshot): Boolean?`** (`:33`) — live
  on/off (null = unknown).
- **`protected abstract fun iconRes(on: Boolean): Int`** (`:34`).
- **`protected abstract fun text(on: Boolean): String`** (`:35`).
- **`protected abstract fun description(on: Boolean): String`** (`:36`).

Final overrides (subclasses cannot change):
- **`final override fun getPreviewData(type)`** (`:38`) — `build(type, on = true, vin = null)`.
- **`final override suspend fun onComplicationRequest(request)`** (`:41`) — resolves the
  car (null → return null), then `build(request.complicationType, stateOf(snap), snap.vin)`.
- **`final override fun onComplicationDeactivated(complicationInstanceId)`** (`:47`) —
  `clearComplicationConfig(applicationContext, dataSourceName, complicationInstanceId)`.

### `ClimateComplication : ToggleStateComplication`
`ClimateComplication.kt:11`

- `dataSourceName = "ClimateComplication"`
- `title = "Climate"`
- `action = WearAction.TOGGLE_CLIMATE` (`"toggle_climate"`)
- `stateOf(snap) = snap.climateOn`
- `iconRes(on) = R.drawable.ic_shortcut_climate` (same icon on/off)
- `text(on) = if (on) "On" else "Off"`
- `description(on) = if (on) "Climate on" else "Climate off"`

### `LockComplication : ToggleStateComplication`
`LockComplication.kt:11`

- `dataSourceName = "LockComplication"`
- `title = "Lock"`
- `action = WearAction.TOGGLE_LOCK` (`"toggle_lock"`)
- `stateOf(snap) = snap.locked`
- `iconRes(on) = if (on) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock`
- `text(on) = if (on) "Locked" else "Unlocked"`
- `description(on) = if (on) "Locked" else "Unlocked"`

### `ComplicationCarStore(private val context: Context)`
`ComplicationCarStore.kt:31`

Wraps a preferences DataStore mapping `(dataSource, instanceId)` → VIN.
- **`suspend fun vinFor(dataSource: String, instanceId: Int): String?`** (`:36`) — reads
  the pinned VIN, or null if unset.
- **`suspend fun setVin(dataSource: String, instanceId: Int, vin: String)`** (`:39`) —
  persists the pin.
- **`suspend fun clear(dataSource: String, instanceId: Int)`** (`:43`) — removes the pin.

### Top-level functions in `ComplicationCarStore.kt`
- **`fun clearComplicationConfig(context: Context, dataSource: String, instanceId: Int)`** —
  `:54`. Fire-and-forget clear of a slot's pin on `complicationCleanupScope` (IO), wrapped
  in `runCatching`.
- **`suspend fun resolveComplicationCar(context: Context, dataSource: String, instanceId: Int): VehicleSnapshot?`** —
  `:64`. Resolves the car a request should show (see §3). Returns null on any failure or
  no cars.

### `ComplicationConfigActivity : ComponentActivity`
`ComplicationConfigActivity.kt:64`

Launched by the complication picker for a configurable complication. Lets the user pick
which car this slot shows.
- **`override fun onCreate(savedInstanceState: Bundle?)`** (`:66`) — sets result to
  `RESULT_CANCELED` up front, parses extras, renders the Compose picker UI (see §3).
- Private: `choose(...)` (`:175`), `followSelected(...)` (`:191`).
- `companion object` holds two extra-key constants (`:206`, `:208`).

### `object ComplicationLink`
`ComplicationLink.kt:12`

- **`fun requestUpdate(context: Context)`** (`:13`) — for each of `ChargeComplication`,
  `LockComplication`, `ClimateComplication`, calls
  `ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, cls)).requestUpdateAll()`,
  each wrapped in `runCatching`.

### `ComplicationTapReceiver : BroadcastReceiver`
`ComplicationTapReceiver.kt:22`

- **`override fun onReceive(context: Context, intent: Intent)`** (`:26`) — handles a toggle
  complication tap (see §3).
- `companion object`:
  - `const val ACTION_TAP = "com.bloo.wear.COMPLICATION_TAP"` (`:56`)
  - `const val EXTRA_VIN = "vin"` (`:57`)
  - `const val EXTRA_ACTION = "action"` (`:58`)
  - **`fun pendingIntent(context: Context, vin: String, action: String): PendingIntent`**
    (`:60`) — builds the broadcast `PendingIntent` a toggle complication's tap fires.

---

## 3. Internal structure & control flow

### `ChargeComplication.buildData(...)`  (`ChargeComplication.kt:98`)

Signature: `buildData(type, pct: Int?, rangeMi: Int?, isEv: Boolean, charging: Boolean,
vin: String? = null, metric: Boolean = false): ComplicationData?`

Step by step:
1. `label` = `"Battery"` if `isEv` else `"Fuel"` (`:107`). Note the param is named `isEv`
   but at the live call site it's fed `snap.hasBattery` — the manual powertrain override,
   not raw `isEv` (matches the KEY DOMAIN FACT).
2. `text` = `"$pct%"` or `"—%"` if null (`:108`).
3. `descText` (accessibility) = label + (`" $pct%"` or `" level unknown"`) + `", charging"`
   if charging (`:109`).
4. Builds `desc`, `plainText`, and `rangeTitle` (only if `rangeMi != null`, formatted via
   `formatDistance(it, metric)`) as `PlainComplicationText` (`:114-116`).
5. `tap = openAppIntent(vin)` (`:117`).
6. `bolt` = a `MonochromaticImage` of `R.drawable.ic_widget_bolt` only when `charging`,
   else null (`:118-120`).
7. Branch on `type` (`:122`):
   - `SHORT_TEXT` → `ShortTextComplicationData.Builder(plainText, desc)`, sets title
     (rangeTitle) if present, monochromatic image (bolt) if present, tap action.
   - `RANGED_VALUE` → `RangedValueComplicationData.Builder(value = (pct ?: 0).coerceIn(0,100).toFloat(),
     min = 0f, max = 100f, contentDescription = desc)`, then `setText(plainText)`, optional
     title, optional bolt, tap action. The `?: 0` + `coerceIn` guards against null/out-of-range
     percentages crashing the builder.
   - else → `null` (unsupported type; system leaves the slot unrendered).

### `ChargeComplication.openAppIntent(vin)`  (`ChargeComplication.kt:157`)

Builds a `PendingIntent` launching `MainActivity` with `FLAG_ACTIVITY_NEW_TASK or
FLAG_ACTIVITY_CLEAR_TOP`; puts `"vin"` extra if non-null. Request code =
`(vin ?: "").hashCode()` so per-car complications get distinct, non-colliding intents.
Flags: `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` (immutable required for cross-process
PendingIntents; update-current refreshes extras).

### `ToggleStateComplication.build(type, on: Boolean?, vin: String?)`  (`ToggleStateComplication.kt:51`)

The heart of the toggle rendering, with an important tri-state:
1. `known = on != null`, `isOn = on == true` (`:58-59`). Crucially, a null `on` (not yet
   synced) is **not** collapsed into "off" — a comment (`:52-57`) explains an unsynced lock
   used to render "Unlocked" which a user could mistake for a real reading.
2. `image` = `MonochromaticImage` of `iconRes(isOn)` when known, else `iconRes(false)`
   (`:60-62`).
3. `desc` = `description(isOn)` when known, else `"State unknown"` (`:63`).
4. `tap` = `vin?.let { ComplicationTapReceiver.pendingIntent(this, it, action) }` — null if
   no VIN, so an unresolved car has no tap action (`:64`).
5. Branch on `type` (`:65`):
   - `SHORT_TEXT` → `ShortTextComplicationData.Builder(text=…, desc)` where text is
     `text(isOn)` when known else `"—"`; sets title (the static `title`), the image, and
     the tap if present.
   - `MONOCHROMATIC_IMAGE` → `MonochromaticImageComplicationData.Builder(image, desc)` +
     optional tap.
   - else → null.

### `resolveComplicationCar(...)`  (`ComplicationCarStore.kt:64`)

1. `data = runCatching { SnapshotStore(context).current() }.getOrNull() ?: return null`
   (`:72`) — the DataStore read can throw on corrupt prefs; an uncaught throw out of
   `onComplicationRequest` would crash the data-source process, so it degrades to null.
2. `vin = runCatching { ComplicationCarStore(context).vinFor(dataSource, instanceId) }.getOrNull()`
   (`:73`).
3. Return `vin?.let { v -> data.vehicles.firstOrNull { it.vin == v } } ?: data.selected`
   (`:74`). I.e. the pinned car **if it still exists**, otherwise the globally-selected car
   (`SnapshotData.selected`, which itself falls back to the first vehicle — `SnapshotStore.kt:236`).

### `ComplicationConfigActivity.onCreate`  (`ComplicationConfigActivity.kt:66`)

1. `setResult(RESULT_CANCELED)` immediately, so a back-out reports cancel (`:68`).
2. Parse `complicationId` from `EXTRA_COMPLICATION_ID` (default `-1`) (`:70`).
3. Parse `component: ComponentName?` from `EXTRA_PROVIDER_COMPONENT` — SDK 33+ uses the
   typed `getParcelableExtra(..., ComponentName::class.java)`; older uses the deprecated
   overload (`:75-80`).
4. If `complicationId == -1 || component == null` → `finish(); return` (`:81`).
5. `dataSource = component.shortClassName.substringAfterLast('.')` — the bare class name
   (e.g. `"ChargeComplication"`), which is exactly the key used throughout the store
   (`:82`).
6. `setContent {}` Compose UI:
   - Remembered state: `cars: List<VehicleSnapshot>`, `loaded: Boolean`,
     `settings: WearSettingsPayload?`, `currentVin: String?` (`:85-92`).
   - `LaunchedEffect(Unit)` (`:93`) loads cars from `SnapshotStore(applicationContext).current().vehicles`,
     `settings` from `WearSettingsStore(applicationContext).flow.first()`, and `currentVin`
     from `ComplicationCarStore(...).vinFor(dataSource, complicationId)` — each `runCatching`
     with defaults. Sets `loaded = true`.
   - Wrapped in `BlooWearTheme(settings)`; renders a `ScalingLazyColumn` with a header
     "Show which car?" (`:105`).
   - If `!loaded` → spinner + "Loading cars…" (`:106-126`).
   - Else if `cars.isEmpty()` → "No cars yet -- sign in on your phone first…" (`:127-136`).
   - If `cars.isNotEmpty()` → a "Follow selected" `MorphButton` (`Icons.Filled.MyLocation`,
     `active = currentVin == null`) that calls `followSelected(...)` (`:137-153`).
   - `items(cars, key = { it.vin })` → one `MorphButton` per car (`Icons.Filled.DirectionsCar`,
     `active = car.vin == currentVin`) calling `choose(..., car.vin)` (`:154-163`).

### `choose(...)` / `followSelected(...)`  (`ComplicationConfigActivity.kt:175`, `:191`)

Both run on `lifecycleScope`:
- `choose` → `ComplicationCarStore(applicationContext).setVin(dataSource, complicationId, vin)`,
  then `runCatching { ComplicationDataSourceUpdateRequester.create(applicationContext,
  component).requestUpdate(complicationId) }`, then `setResult(RESULT_OK); finish()`.
- `followSelected` → same, but `.clear(dataSource, complicationId)` instead of setVin.

`runCatching` around the requester because it can fail if the data source was
unregistered; the pin is already saved and applies on next natural refresh regardless.

### `ComplicationTapReceiver.onReceive`  (`ComplicationTapReceiver.kt:26`)

1. Ignore if `intent.action != ACTION_TAP` (`:27`).
2. Extract `vin` and `action` (return if either missing) (`:28-29`).
3. `ctx = context.applicationContext`; `pending = goAsync()` to extend process lifetime
   (`:30-31`).
4. On `scope` (IO): inside `try/finally`, `withTimeoutOrNull(9_000) { runCatching {
   WearComms.send(ctx, WearCommand(vin, action)) } }` (`:45-47`), then
   `ComplicationLink.requestUpdate(ctx)` (`:48`); `finally { pending.finish() }` (`:49-51`).

The 9 s cap is deliberate — `goAsync()` grants roughly 10 s; `WearComms.send` can chain a
10 s phone-relay attempt plus an unbounded standalone car-API call, which could exceed the
budget and get the process reclaimed before `finish()` runs. Capping guarantees `finish()`
and a best-effort refresh always run.

### `ComplicationTapReceiver.pendingIntent(...)`  (`:60`)

Builds an explicit `Intent` to `ComplicationTapReceiver` with `action = ACTION_TAP`,
`data = Uri.parse("bloo://comp/$vin/$action")` (unique per vin+action so PendingIntents
don't collapse), and the vin/action extras. Returns `PendingIntent.getBroadcast` with
request code `(vin + action).hashCode()` and flags `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.

---

## 4. Data & types

This package defines **no** data classes/enums/sealed types of its own. All domain types
are imported from `:shared`:

- **`VehicleSnapshot`** (`com.bloo.bluelink.data`, `SnapshotStore.kt:21+`). Fields used here:
  `vin: String`, `name: String`, `percent: Int?` (default null), `rangeMi: Int?` (null),
  `locked: Boolean?` (null), `charging: Boolean?` (null), `climateOn: Boolean?` (null),
  `hasBattery: Boolean` (defaults to `isEv`). Nullability is central: null means "not yet
  synced / unknown", rendered distinctly (see §8).
- **`SnapshotStore.SnapshotData`** — `vehicles: List<VehicleSnapshot>`, `selectedVin: String?`,
  and derived `selected: VehicleSnapshot?` (selected VIN's snapshot, else first vehicle,
  else null — `SnapshotStore.kt:236`).
- **`WearAction`** (`WearSync.kt:243`, an `object` of `const val` strings). Used:
  `TOGGLE_LOCK = "toggle_lock"` (`:244`), `TOGGLE_CLIMATE = "toggle_climate"` (`:247`).
  `action` typed as `String`, not an enum.
- **`WearCommand`** (`WearSync.kt:302`, `vin` + action) — constructed in the tap receiver.
- **`WearSettingsPayload`** — carried into `BlooWearTheme` for theming; not otherwise
  inspected here.

**DataStore key encoding** (`ComplicationCarStore.kt:33`): `stringPreferencesKey("$dataSource:$instanceId")`
— e.g. `"ChargeComplication:1234567"`. `dataSource` is the bare class name so the three
Bloo complications never collide even if a face reuses a numeric slot id.

**ComplicationType support matrix:**
- Charge: `SHORT_TEXT`, `RANGED_VALUE` (else null).
- Toggle (Lock/Climate): `SHORT_TEXT`, `MONOCHROMATIC_IMAGE` (else null).

---

## 5. State & concurrency

- **No in-service state.** The three complication services are stateless — each override is
  a self-contained answer. This is required: the watch-face host owns their lifecycle.
- **`ComplicationCarStore`** is backed by `Context.complicationCarStore` — a
  `preferencesDataStore(name = "bloo_complication_cars", corruptionHandler =
  ReplaceFileCorruptionHandler { emptyPreferences() })` (`ComplicationCarStore.kt:20-23`).
  The corruption handler is deliberate: this store is read on every render and "must not
  throw".
- **`complicationCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`**
  (`ComplicationCarStore.kt:48`) — the fire-and-forget scope for `clearComplicationConfig`,
  independent of any service lifecycle.
- **`onComplicationRequest`** is a `suspend` fun — the system awaits it; DataStore reads
  (`SnapshotStore(...).current()`, `.first()` calls) happen on the DataStore's own IO
  dispatcher.
- **`ComplicationConfigActivity`** — Compose `remember`/`mutableStateOf` for
  `cars/loaded/settings/currentVin`; loads once in `LaunchedEffect(Unit)`;
  `choose`/`followSelected` run on `lifecycleScope`. Recomposition triggers on any of the
  four state vars changing (notably `loaded` flipping true, and `currentVin` driving the
  `active` highlight).
- **`ComplicationTapReceiver`** — `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
  (`:24`), a fresh scope per receiver instance. Uses `goAsync()` + `withTimeoutOrNull(9_000)`
  to bound work within the broadcast's extended lifetime; `pending.finish()` always runs in
  `finally`.

---

## 6. Collaborators & data flow

**Data in:**
- `SnapshotStore(context).current()` → the phone-synced vehicle snapshots. Populated by the
  WearSync layer over the Wearable Data Layer (state path). Complications read only; never
  write.
- `ComplicationCarStore` (own DataStore `bloo_complication_cars`) → per-slot VIN pins.
- `WearLocalStore(this).flow.first().unitSystem` → metric/imperial preference (Charge only).
- `WearSettingsStore(...).flow.first()` → `WearSettingsPayload` for theming the config
  Activity.

**Data out / actions:**
- Charge tap → `PendingIntent` launching `MainActivity` with a `"vin"` extra (intent).
- Toggle tap → broadcast `PendingIntent` → `ComplicationTapReceiver` → `WearComms.send(ctx,
  WearCommand(vin, action))`. `WearComms` relays to the phone over the Data Layer (command
  path) or runs standalone against the car API; it applies an optimistic snapshot update
  (per `WearCommandRunner`, which flips `snap.locked`/`snap.climateOn` — `WearCommandRunner.kt:129,132`).
- After a command, `ComplicationLink.requestUpdate(ctx)` →
  `ComplicationDataSourceUpdateRequester.requestUpdateAll()` on all three complication
  classes so the icon/label flips immediately.

**Who calls this package:**
- Wear OS complication host process → drives all three services' overrides.
- The complication picker → launches `ComplicationConfigActivity`.
- `ComplicationConfigActivity` → `ComplicationDataSourceUpdateRequester.requestUpdate(complicationId)`
  after a pin change.
- `ComplicationLink.requestUpdate` is called from elsewhere in the wear module (after a
  command or sync) to keep arcs/icons fresh — and from the tap receiver.

---

## 7. Invariants & assumptions

- `dataSource` key equals the complication service's **bare class name**
  (`substringAfterLast('.')` of `component.shortClassName`) — the config Activity and the
  services must agree on this string, or a slot's pin won't be found. Charge/Lock/Climate
  each hardcode their own name (`dataSourceName` / the `"ChargeComplication"` literal).
- `resolveComplicationCar` must never throw out of `onComplicationRequest` — an uncaught
  throw crashes the host-bound data-source process. Hence every read is `runCatching`.
- `getPreviewData` must return **without suspending** and **instantly** — no DataStore/network.
- A null `percent`/`locked`/`climateOn` means "not synced yet", not "0/off". This must be
  rendered as an explicit neutral ("—%", "—", "State unknown"), never as a false concrete
  reading.
- A `RANGED_VALUE` percent is coerced into `0..100`; the builder would otherwise reject an
  out-of-range value.
- PendingIntents must be `FLAG_IMMUTABLE` (cross-process handoff to the watch-face host).
- Distinct per-car / per-(vin,action) PendingIntents rely on unique request codes/data URIs
  so they don't collapse into one.
- `goAsync()` grants roughly 10 s; the tap work is capped at 9 s to stay inside it.

---

## 8. Gotchas & sharp edges

- **`isEv` param name is a trap.** `buildData`'s `isEv` param is fed `snap.hasBattery` at
  the live call site (`ChargeComplication.kt:74`) — the user's manual powertrain override,
  matching the domain rule that `percentFor`/`rangeMiFor` use `hasBattery`, not raw `isEv`.
  Only the preview passes literal `isEv = true`.
- **Tri-state rendering** (`ToggleStateComplication.kt:52-63`): the comment documents a real
  past bug — an unsynced lock rendered as a definite "Unlocked". Never collapse null into
  off. The icon for unknown falls back to `iconRes(false)` but the text/description make the
  unknown explicit.
- **No tap action when VIN is null** (`ToggleStateComplication.kt:64`): if the car can't be
  resolved, the toggle complication renders but does nothing on tap. Charge's tap, by
  contrast, still opens the app (with a null/empty vin extra).
- **`onComplicationRequest` returning null clears the slot** — so "no car configured / no
  cars synced" shows an empty slot, not stale data.
- **Pin GC**: `onComplicationDeactivated` → `clearComplicationConfig` prevents a removed
  slot's VIN pin from being inherited by a future slot reusing the same numeric
  `complicationInstanceId`. This is fire-and-forget on an IO scope; the DataStore write
  outlives the service callback.
- **Corruption handler is load-bearing** — without `ReplaceFileCorruptionHandler`, a
  power-loss-damaged prefs file would rethrow on every render and repeatedly crash the
  data-source process.
- **`requestUpdate` failures are swallowed** in the config Activity — the pin is already
  persisted and applies on the next natural refresh, so a failed immediate re-render is
  non-fatal.
- **9 s timeout may drop the command but still refreshes.** On a slow connection the actual
  command can be cut off by `withTimeoutOrNull`, yet `ComplicationLink.requestUpdate` still
  fires — so the complication may re-read a snapshot that reflects only the *optimistic*
  update `WearComms` applied, not a confirmed car response.
- **`ComplicationLink` hardcodes exactly three classes** (`ComplicationLink.kt:14-18`). A new
  complication type would silently not get refreshed unless added here.
- **Config Activity SDK branch**: pre-33 uses the deprecated `getParcelableExtra` overload;
  the typed overload is only safe on 33+.
