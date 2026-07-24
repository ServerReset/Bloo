# app: widget workers + config + receiver + action

Deep dive of the home-screen widget's off-UI machinery: the WorkManager workers
that run commands and refresh in the background, the auth-gate activity, the
config/edit activity, the enums that model widget actions and info fields, and
the Glance receiver that hosts the widget and cleans up on delete.

Files covered (all under `app/src/main/java/com/bloo/bluelink/widget/`):

- `WidgetCommandWorker.kt` — runs a single tapped command in the background + `WidgetActionCallback` (Glance in-place callback).
- `WidgetRefreshWorker.kt` — periodic 15-min all-surfaces refresh.
- `WidgetAction.kt` — enum of assignable widget buttons.
- `WidgetInfoField.kt` — enum of info-mode stats.
- `WidgetConfigActivity.kt` — Compose setup/edit screen.
- `WidgetAuthActivity.kt` — transparent biometric gate.
- `BlooWidgetReceiver.kt` — Glance receiver + delete cleanup.

The Glance UI itself (`BlooWidget.kt`) is a separate unit; it is the *caller* of
almost everything here, and is referenced where relevant.

---

## 1. Purpose

Home-screen widgets are Glance (RemoteViews) surfaces. Glance composition runs in
a very constrained context: no arbitrary coroutines, no activity, no UI dialogs.
This unit is everything a widget tap needs that Glance itself can't do:

- **Run a command without opening the app.** A widget button (lock/unlock/climate/
  charge/refresh/location) must hit the live BlueLink/Kia telematics API. That's a
  network round-trip far longer than a Glance callback should block, so it is
  offloaded to `WidgetCommandWorker` (a `CoroutineWorker`). `WidgetActionCallback`
  is the Glance entry point that dispatches it.
- **Gate sensitive actions behind biometrics.** Widgets can't show a biometric
  prompt. `WidgetAuthActivity` is a transparent activity launched only when an
  action needs auth, shows the system prompt, then hands off to the same worker.
- **Keep every surface fresh while the app is closed.** `WidgetRefreshWorker`
  polls the server-cached status every 15 min and fans it out to widgets, watch,
  and the Quick Settings tile.
- **Configure a widget.** `WidgetConfigActivity` is the OS-launched configuration
  activity (and the in-app setup screen) that writes per-widget preferences.
- **Model the choices.** `WidgetAction` and `WidgetInfoField` are the enums that
  define which buttons and which info stats a widget can carry.
- **Clean up.** `BlooWidgetReceiver` hosts `BlooWidget` and wipes a widget's
  stored config when it's removed.

---

## 2. Public surface

### `WidgetCommandWorker.kt`

**`class WidgetCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker`**
(WidgetCommandWorker.kt:30) — a `CoroutineWorker` that executes one widget button
command.

- **`override suspend fun doWork(): Result`** (:40) — WorkManager entry point.
  Unpacks the input data, resolves the `WidgetAction`, runs `execute`, and in a
  `finally` clears the pending-spinner flag and forces a widget redraw. On success
  fans the fresh snapshot out to watch and QS tile. Returns `Result.failure()` if
  `vin`, `actionKey`, or the resolved action is missing; otherwise `Result.success()`.

- **`companion object`** (:249):
  - `const val KEY_WIDGET_ID = "widget_id"` (:252)
  - `const val KEY_VIN = "vin"` (:253)
  - `const val KEY_ACTION = "action"` (:254)
  - `const val KEY_WEAR_ACTION = "wear_action"` (:255)
  - **`suspend fun dispatch(ctx, widgetId: Int, vin: String, action: WidgetAction)`**
    (:262) — the shared "run this now" entry. Optimistically flips the snapshot,
    marks the button pending, redraws the widget, then enqueues the worker. Called
    by `WidgetActionCallback` (no-auth path) and `WidgetAuthActivity` (after auth).
  - **`fun enqueue(ctx, widgetId, vin, action, wearAction: String? = action.wearAction)`**
    (:288) — builds the `workDataOf(...)` payload and enqueues a
    `OneTimeWorkRequest` as unique work `"widget_cmd_$widgetId"` with
    `ExistingWorkPolicy.KEEP`.

**`class WidgetActionCallback : ActionCallback`** (WidgetCommandWorker.kt:317) —
Glance in-place action callback for no-auth taps (app never opens).

- **`override suspend fun onAction(context, glanceId: GlanceId, parameters: ActionParameters)`**
  (:324) — reads `KEY_WIDGET`/`KEY_VIN`/`KEY_ACTION` from the parameters and calls
  `WidgetCommandWorker.dispatch`. Any missing key silently no-ops (`?: return`).
- **`companion object`** (:331):
  - `val KEY_WIDGET = ActionParameters.Key<Int>("bloo_widget_id")` (:332)
  - `val KEY_VIN = ActionParameters.Key<String>("bloo_vin")` (:333)
  - `val KEY_ACTION = ActionParameters.Key<String>("bloo_action")` (:334)

### `WidgetRefreshWorker.kt`

**`class WidgetRefreshWorker(ctx, params) : CoroutineWorker`** (WidgetRefreshWorker.kt:26)
— periodic all-surfaces refresh.

- **`override suspend fun doWork(): Result`** (:37) — refreshes the server-cached
  snapshot for all vehicles (`vin = ""`, `force = false`), then fans out to
  widgets, watch state, watch auth, watch settings, and QS tile. Each step in its
  own `runCatching`; always returns `Result.success()`.
- **`companion object`** (:53):
  - `private const val NAME = "bloo_widget_refresh"` (:54)
  - **`fun schedule(context: Context)`** (:58) — enqueues a 15-min periodic work
    with `NetworkType.CONNECTED` constraint, unique-periodic under `NAME` with
    `ExistingPeriodicWorkPolicy.KEEP`.
  - **`fun cancel(context: Context)`** (:78) — `cancelUniqueWork(NAME)`.

### `WidgetAction.kt`

**`enum class WidgetAction(key, label, icon, requiresAuth, kind, wearAction)`**
(WidgetAction.kt:10) — see §4 for the full field/entry table.

- **nested `enum class Kind { COMMAND, REFRESH, LOCATION, OPEN }`** (:37).
- **`companion object`** (:39):
  - `val ALL = entries` (:41) — declaration order, for the config picker.
  - `fun fromKey(key: String?): WidgetAction?` (:45) — lookup by persisted key, null if unknown.
  - `val DEFAULTS = listOf(DOORS, CLIMATE, REFRESH, LOCATION)` (:48).

### `WidgetInfoField.kt`

**`enum class WidgetInfoField(val key: String, val label: String)`** (WidgetInfoField.kt:11)
— see §4.

- **`companion object`** (:18):
  - `val ALL = entries` (:20).
  - `fun fromKey(key: String): WidgetInfoField?` (:24) — note: non-nullable `key` param (unlike `WidgetAction.fromKey`).
  - `val DEFAULTS = listOf(NAME, PERCENT, RANGE, LOCK)` (:28).

### `WidgetConfigActivity.kt`

**`class WidgetConfigActivity : FragmentActivity()`** (WidgetConfigActivity.kt:68) —
OS configuration activity + in-app setup screen.

- **`override fun onCreate(savedInstanceState: Bundle?)`** (:70) — reads the widget
  id from `EXTRA_APPWIDGET_ID`, finishes if invalid, pre-sets `RESULT_CANCELED`,
  then sets a `BlooTheme` Compose content hosting `WidgetConfigScreen`.
- **`private fun finishWith(widgetId: Int)`** (:99) — force-redraw all widgets,
  set `RESULT_OK` with the widget id, `finish()`.

**`@Composable private fun WidgetConfigScreen(widgetId: Int, onDone: () -> Unit, onCancel: () -> Unit)`**
(:109) — the whole editor UI. Renders (top to bottom): title + subtitle; an
empty-state ("No cars yet") with a Close button; a **Car** section (one
`MorphButton` per `VehicleSnapshot`); a **Buttons** section (two-column
`MorphChip` grid over `WidgetAction.ALL`, max 4 selected); an **Info fields**
section (`MorphChip` grid over `WidgetInfoField.ALL`, unlimited); an **Options**
section (require-auth, photo-bg, show-location, pill-shape chips); a background
transparency `AnimatedSlider` (0..9); a **Layout** `MorphSegmented` (info/controls);
and Save/Cancel buttons. Save persists every field to `SettingsStore` then calls
`onDone`.

**`@Composable private fun SectionLabel(text: String)`** (:326) — bold primary-colored
label + 6dp spacer.

### `WidgetAuthActivity.kt`

**`class WidgetAuthActivity : FragmentActivity()`** (WidgetAuthActivity.kt:21) —
transparent biometric gate.

- **`override fun onCreate(savedInstanceState: Bundle?)`** (:34) — parses widget/
  vin/action from the intent, checks device auth capability, reads per-widget
  `requireAuth`, then either prompts or runs directly.
- **`private fun promptThenRun(action, vin, authenticators: Int)`** (:61) — shows a
  `BiometricPrompt`; on success calls `run`, on any error calls `finishNoAnim`.
- **`private fun run(action: WidgetAction, vin: String)`** (:89) — OPEN launches the
  app; else `dispatch` to the worker; always finishes in a `finally`.
- **`private fun finishNoAnim()`** (:108) — `finishAndRemoveTask()` +
  `overridePendingTransition(0, 0)`.
- **`private fun openApp(vin: String)`** (:116) — builds the `Shortcuts`-based
  MainActivity intent targeting `vin`.
- **`companion object`** (:126): `ACTION_RUN`, `EXTRA_WIDGET_ID`, `EXTRA_VIN`, `EXTRA_ACTION`.

### `BlooWidgetReceiver.kt`

**`class BlooWidgetReceiver : GlanceAppWidgetReceiver()`** (BlooWidgetReceiver.kt:12).

- **`override val glanceAppWidget: GlanceAppWidget = BlooWidget()`** (:14).
- **`override fun onDeleted(context, appWidgetIds: IntArray)`** (:16) — for each
  removed widget id, `clearWidgetConfig(it)` on `Dispatchers.IO`.

---

## 3. Internal structure & control flow

### `WidgetCommandWorker.doWork` (WidgetCommandWorker.kt:40-65)

1. Read `widgetId` (default `-1`), `vin`, `actionKey` from `inputData`. Missing
   `vin`/`actionKey` → `Result.failure()` (:42-43).
2. `WidgetAction.fromKey(actionKey)` → `Result.failure()` if unknown (:44).
3. `wearAction = inputData.getString(KEY_WEAR_ACTION) ?: action.wearAction` (:47) —
   this is the concrete verb the dispatcher already resolved from the pre-flip
   snapshot (e.g. `TOGGLE_LOCK` → `LOCK`); falls back to the action's own default
   verb for enqueues that didn't resolve.
4. `try { execute(...) } finally { ... }` (:50-60). The `finally` runs inside
   `withContext(NonCancellable)` and does two `runCatching` cleanups: clear the
   pending action (`setWidgetPendingAction(widgetId, null)`) and `BlooWidget().updateAll(ctx)`.
5. After the try/finally (only reached if not cancelled): `WearBridge.publishNow(ctx)`
   and `BlooTileService.requestUpdates(ctx)` (:62-63), then `Result.success()`.

### `WidgetCommandWorker.execute` (WidgetCommandWorker.kt:80-142)

Switches on `action.kind`:

- **COMMAND** (:82-112): if `wearAction != null`, call
  `WearCommandRunner.execute(ctx, WearCommand(vin, wearAction))`.
  - On failure (`!result.ok`): log, then **revert the optimistic flip** — read the
    snapshot, find the vehicle by vin, and write
    `optimistic(it, inverse(wearAction))` back to the store (:87-92). Then show a
    `Toast` on `Dispatchers.Main` (:98-104), and `return` early (skips the refresh).
  - On success: log ok.
  - Whether or not there was a wearAction, then `delay(4000)` and
    `WearCommandRunner.refresh(ctx, vin)` (a forcing, car-waking refresh) so the
    widget shows real post-command state (:109-111).
- **REFRESH** (:114-117): `WearCommandRunner.refresh(ctx, vin)` only, no command.
- **LOCATION** (:119-138): forcing refresh (in `runCatching`), read snapshot's
  `lat`/`lon`, and if both non-null and not `(0.0, 0.0)`: reverse-geocode into a
  human address (composed from `thoroughfare` / `subThoroughfare` / `locality`,
  falling back to `getAddressLine(0)`), persist via `setWidgetLocationAddress`, and
  `downloadAndCacheMapTile`.
- **OPEN** (:140): no-op here — OPEN never reaches the worker (it launches the app
  directly from the Glance click router).

### `geocode` (WidgetCommandWorker.kt:149-176)

Reverse-geocode with a hard 6-second timeout. Returns null for `(0,0)` or when no
geocoder is present. On API 33+ (`TIRAMISU`) uses the non-blocking
`getFromLocation(lat, lon, 1, GeocodeListener)` wrapped in
`suspendCancellableCoroutine` inside `withTimeoutOrNull(6000)`; the listener
resumes with `addresses.firstOrNull()` on success or `null` on error, guarded by
`cont.isActive`. Pre-33 uses the deprecated blocking overload on `Dispatchers.IO`
inside `withTimeoutOrNull(6000)`.

### `downloadAndCacheMapTile` (WidgetCommandWorker.kt:180-247)

Runs on `Dispatchers.IO`, all inside `runCatching`. Web-Mercator/Slippy-Map math
at `zoom = 15`:

1. `n = 1 shl zoom`; compute fractional tile coords `xFull`/`yFull` from lon/lat.
2. Integer tile `xt`/`yt`; fractional offsets `xOff`/`yOff` within the tile.
3. Pick the top-left mosaic tile `x0`/`y0`: if the car is in the right/bottom half
   (`> 0.5`) use its own tile, else the one left/above; `coerceAtLeast(0)` so we
   never request tile `-1`.
4. Create a 512×512 ARGB_8888 bitmap + canvas.
5. Loop `dy`/`dx` in `0..1` (a 2×2 mosaic). Each tile fetched in its own
   `runCatching` so one failure just leaves a gap. Skip tiles beyond `n-1`. HTTP
   GET `https://tile.openstreetmap.org/$zoom/$tx/$ty.png` with a `Bloo/1.0` UA and
   5s connect/read timeouts; decode, draw at `(dx*256, dy*256)`, recycle.
6. Draw the pin at `((xFull - x0) * 256, (yFull - y0) * 256)`: a `#CC1A73E8` circle
   radius 14 + a white circle radius 7.
7. Compress the stitched bitmap as PNG (quality 85) to
   `cacheDir/widget_map_$widgetId.png`, recycle.

### `WidgetCommandWorker.dispatch` (WidgetCommandWorker.kt:262-281)

1. `resolved = action.wearAction`; `wa = action.wearAction`.
2. If `wa != null` (in `runCatching`): read the snapshot for `vin`,
   `resolveToggle(snap, wa)` → `resolved`, and write `optimistic(snap, resolved)`
   back. **Resolve happens on the PRE-flip snapshot** — critical (see §8).
3. `setWidgetPendingAction(widgetId, action.key)`, `BlooWidget().updateAll(ctx)`.
4. `enqueue(ctx, widgetId, vin, action, resolved)` — passes the resolved verb.

### `WidgetConfigScreen` load/save flow

**Load** (`LaunchedEffect(Unit)`, :125-150): reads `cars` from `SnapshotStore`, and
every per-widget option from `SettingsStore`. `existing = widgetConfig(widgetId)`:
if present, pick a still-valid vin (falling back to first car) and, only if the
saved actions are non-empty, replace the DEFAULTS-seeded `actions`. Info fields are
loaded **unconditionally** (`clear()` + `addAll`) — see §8. Sets `loaded = true`.

**Save** (Save button onClick, :300-315): guard `selectedVin ?: return`; in a
coroutine persist config, info fields, require-auth, photo-bg, show-location,
pill-shape, layout-mode, background-alpha; then `onDone()` (which calls `finishWith`).

### `WidgetAuthActivity.onCreate` decision (WidgetAuthActivity.kt:34-54)

`widgetId` from `EXTRA_WIDGET_ID` (default -1); `vin`, `action` from intent. Bail
(`finish()`) if vin or action null. Compute `canAuth` for `BIOMETRIC_WEAK OR
DEVICE_CREDENTIAL`. In a `lifecycleScope` coroutine read `requireAuth` per widget;
if `action.requiresAuth && canAuth && requireAuth` → `promptThenRun`, else `run`.

---

## 4. Data & types

### `WidgetAction` enum (WidgetAction.kt:10-50)

Fields: `key: String` (persisted id), `label: String` (UI), `icon: Int` (drawable
res), `requiresAuth: Boolean` (gate behind biometrics), `kind: Kind`,
`wearAction: String? = null` (the `WearAction` verb sent to the car).

| entry | key | label | requiresAuth | kind | wearAction |
|---|---|---|---|---|---|
| DOORS | doors | Doors | true | COMMAND | `WearAction.TOGGLE_LOCK` |
| LOCK | lock | Lock | true | COMMAND | `WearAction.LOCK` |
| UNLOCK | unlock | Unlock | true | COMMAND | `WearAction.UNLOCK` |
| CLIMATE | climate | Climate | true | COMMAND | `WearAction.TOGGLE_CLIMATE` |
| CLIMATE_ON | climate_on | Climate on | true | COMMAND | `WearAction.CLIMATE_ON` |
| CLIMATE_OFF | climate_off | Climate off | true | COMMAND | `WearAction.CLIMATE_OFF` |
| CHARGE | charge | Charge | true | COMMAND | `WearAction.TOGGLE_CHARGE` |
| REFRESH | refresh | Refresh | false | REFRESH | (null) |
| LOCATION | location | Location | true | LOCATION | (null) |
| OPEN | open | Open app | false | OPEN | (null) |

`Kind` (:37): `COMMAND` (sends a car command, needs response), `REFRESH` (re-fetch
+ redraw only), `LOCATION` (fetch + display last location), `OPEN` (launch app,
bypasses auth/worker).

### `WidgetInfoField` enum (WidgetInfoField.kt:11-30)

Fields: `key: String`, `label: String`.

| entry | key | label |
|---|---|---|
| NAME | name | Car name |
| PERCENT | percent | Battery/fuel % |
| RANGE | range | Range |
| LOCK | lock | Lock status |
| MODEL | model | Model |

`DEFAULTS = [NAME, PERCENT, RANGE, LOCK]` (MODEL excluded, matching the pre-existing
fixed layout so upgrading doesn't change existing widgets).

### WorkManager Data payload (WidgetCommandWorker `enqueue`, :295-300)

`workDataOf` with `KEY_WIDGET_ID` (Int), `KEY_VIN` (String), `KEY_ACTION` (String,
`action.key`), `KEY_WEAR_ACTION` (String?, the resolved verb). Must be
primitive/String because WorkManager persists it — the enum can't be serialized
directly.

### SettingsStore per-widget keys (from `SettingsStore.kt`, referenced)

Namespaced `widget_${widgetId}_<suffix>`: `vin`, `actions` (comma-joined),
`info` (comma-joined), `pending`, `auth` (bool, default **true**), `photobg`
(bool, default false), `alpha` (string int, default 0, coerced 0..9), `loc` (bool),
`pill` (bool), `layout` (string, default `"info"`, validated to `info`/`controls`),
`addr`, `lat`, `lon`. `widgetConfig` returns null if the `vin` key is blank/absent
(a widget with no vin is "unconfigured"); an empty actions list is valid.
`clearWidgetConfig` removes every suffix under both string and boolean key types.

---

## 5. State & concurrency

- **`WidgetCommandWorker` / `WidgetRefreshWorker`** are `CoroutineWorker`s; `doWork`
  runs on WorkManager's coroutine dispatcher. The command worker's map-tile and
  geocode helpers explicitly hop to `Dispatchers.IO`; the failure toast hops to
  `Dispatchers.Main`. The `finally` cleanup wraps in `NonCancellable` so cancellation
  can't skip the spinner-clear (WidgetCommandWorker.kt:56).
- **Vehicle status serialization**: `WearCommandRunner.refresh` takes
  `BlueLinkGate.statusMutex.withLock` (process-wide) around all status pulls —
  overlapping status/command calls 502 the backend. The command worker's own
  `enqueueUniqueWork(..., KEEP)` (WidgetCommandWorker.kt:306) guarantees at most one
  command worker per widget id, so a second tap while one is in flight is dropped
  rather than raced.
- **Optimistic state** lives in `SnapshotStore` (a DataStore-backed snapshot). The
  dispatcher writes an optimistic flip; on command failure the worker writes the
  inverse to revert. `BlooWidget().updateAll(ctx)` forces Glance recomposition to
  re-read that store.
- **Per-widget config/pending flags** live in `SettingsStore` (Preferences
  DataStore). Reads use `.first()` (one-shot). `setWidget*` uses `editTracked`.
- **`WidgetConfigScreen` UI state**: all `remember { mutableStateOf(...) }` /
  `mutableStateListOf(...)`. `LaunchedEffect(Unit)` loads once; `loaded` gates the
  empty-state. Chip toggles mutate the `SnapshotStateList`s in place, triggering
  recomposition. Save runs in `rememberCoroutineScope().launch`.
- **`WidgetAuthActivity`** does its async reads/dispatch in `lifecycleScope`. The
  `BiometricPrompt` callback runs on `ContextCompat.getMainExecutor`.
- **`BlooWidgetReceiver.onDeleted`** launches a bare `CoroutineScope(Dispatchers.IO)`
  (fire-and-forget) — acceptable because the cleanup is idempotent and short.

---

## 6. Collaborators & data flow

**Inbound (who calls this unit):**
- `BlooWidget.clickFor` (BlooWidget.kt:793) routes taps: OPEN →
  `actionStartActivity(openIntent)`; auth-required on an auth-on widget →
  `actionStartActivity(authIntent → WidgetAuthActivity)`; everything else →
  `actionRunCallback<WidgetActionCallback>` with `KEY_WIDGET`/`KEY_VIN`/`KEY_ACTION`.
- `BlooWidget.SetupTile` (BlooWidget.kt:289) launches `WidgetConfigActivity` for an
  unconfigured widget; the OS also launches it as the declared configuration activity.
- The OS calls `BlooWidgetReceiver.onDeleted` when a widget is removed.
- `WidgetRefreshWorker.schedule` is invoked on app launch / when the first widget
  is added; `cancel` when the last widget is removed or on sign-out (callers outside
  this unit).

**Outbound (what this unit calls):**
- `WearCommandRunner.execute` / `.refresh` / `.resolveToggle` / `.optimistic` /
  `.inverse` (shared `data/WearCommandRunner.kt`) — the actual car-command + status
  logic.
- `SnapshotStore` — read current vehicles, `updateVehicle` for optimistic writes.
- `SettingsStore` — all per-widget prefs + pending flag + location address.
- `BlooWidget().updateAll(ctx)` — force Glance redraw.
- `WearBridge.publishNow/publishAuth/publishSettingsNow` — push to the watch over
  the Wear Data Layer.
- `BlooTileService.requestUpdates` — refresh the QS tile.
- `MainActivity` via `Shortcuts.ACTION` intents (OPEN path).
- `WidgetCommandWorker.dispatch` — the common entry for auth + no-auth paths.

**Channels:** WorkManager Data bundle (worker input), `ActionParameters` (Glance
callback), Intent extras + a unique `data` URI (auth/open/config activities),
DataStore (SettingsStore/SnapshotStore), cacheDir PNG file (map tile),
Wear Data Layer (WearBridge).

---

## 7. Invariants & assumptions

- **Toggle verbs must be resolved from the pre-flip snapshot.** `dispatch` resolves
  `TOGGLE_*` → concrete verb *before* writing the optimistic flip, and passes that
  resolved verb through to the worker. The worker re-reads the same `SnapshotStore`;
  if it saw a raw `TOGGLE_*` after the flip, every toggle would re-assert the
  current state (WidgetCommandWorker.kt:269-274; WearCommandRunner.kt:104-112 doc).
- **`KEY_WEAR_ACTION` fallback**: worker uses `action.wearAction` when the enqueue
  didn't resolve one — fine for non-toggle COMMANDs whose verb is fixed.
- **Pending flag is always cleared**, even on cancellation, via the `NonCancellable`
  finally — otherwise the whole-widget spinner overlay sticks forever.
- **At most one command worker per widget** (`enqueueUniqueWork KEEP`).
- **`refresh(vin = "", force = false)`** means "all known cars, server cache is
  fine" — cheap, no car wake; used by the periodic worker.
- **Config activity contract**: result defaults to `RESULT_CANCELED` up front so a
  back-out tears the widget down; `RESULT_OK` only on Save. `widgetConfig` counts a
  widget as configured only if it has a non-blank vin.
- **Location gating**: `(0.0, 0.0)` lat/lon is treated as "no location" everywhere
  (geocode returns null, tile skipped).
- **Auth is triple-gated**: `action.requiresAuth && canAuth && requireAuth` — any
  false runs unprompted. OPEN and REFRESH have `requiresAuth = false`.
- **Unique intent data URI per widget+action** (`bloo://widget/$widgetId/${action.key}`)
  is required because PendingIntent `filterEquals` ignores extras (BlooWidget.kt:824).

---

## 8. Gotchas & sharp edges

- **NonCancellable finally (WidgetCommandWorker.kt:53-56)**: without it, the first
  suspension after WorkManager cancels the worker throws immediately, leaving the
  pending spinner stuck on the widget forever. Deliberate.
- **4-second delay before post-command refresh (:109)**: the car needs time to
  actually act before polling, or the refresh reads stale state.
- **Optimistic revert on failure (:87-92)** uses `optimistic(it, inverse(wearAction))`,
  not a fresh server fetch — instant, but it undoes exactly the flip that was made
  (TOGGLE_* inverses to itself since a second flip restores the original).
- **Toast is the only failure feedback (:98-104)** — with no activity/UI context a
  reverted button is otherwise indistinguishable from a render glitch. Matches how
  in-app failures surface via `runCommand`/`AppViewModel`.
- **Info-fields load is unconditional (WidgetConfigActivity.kt:141-148)** while
  actions load is guarded by `isNotEmpty()`. `widgetInfoFields()` already returns
  DEFAULTS for a never-configured widget, so an empty result means the user
  deliberately deselected every chip. Guarding on `isNotEmpty()` (like actions
  does) would silently re-seed DEFAULTS and un-deselect. The two enums are handled
  asymmetrically on purpose.
- **Actions capped at 4** (`actions.size < 4`, :193); info fields uncapped.
- **Background-transparency slider is a no-op with a photo background** — the
  photo overrides the tint, so the label reads "Not used with a photo background"
  and the row dims to 0.4 alpha (WidgetConfigActivity.kt:255,279).
- **Pill shape silently no-ops above ~2×2 cells** — the config screen says so
  outright (WidgetConfigActivity.kt:246) because the rounding needs padding room
  only reserved at small sizes.
- **`WidgetInfoField.fromKey` takes non-null `String`**, `WidgetAction.fromKey`
  takes `String?` — subtle API asymmetry between the two otherwise-parallel enums.
- **Map tile is stitched from 4 independent tiles** each in its own `runCatching`,
  so an edge-of-world or failed tile leaves a gap instead of aborting the mosaic.
  Cached to `cacheDir` (evictable) as `widget_map_$widgetId.png`.
- **Legacy blocking geocoder can hang** — hence the API-33 non-blocking listener +
  6s timeout; a hang would stall the worker *and* the pending-spinner clear.
- **`WidgetActionCallback` missing-key path silently no-ops** — all three params
  are attached at compose time so this "shouldn't happen," but it fails safe rather
  than crashing the launcher process.
- **`WidgetAuthActivity.finishNoAnim` uses `finishAndRemoveTask` + zero transition**
  to avoid an opaque task-switch flash on the home screen for a transparent activity.
