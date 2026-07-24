# Wear Tile — `BlooTileService`

**File:** `wear/src/main/java/com/bloo/wear/tile/BlooTileService.kt`
**Package:** `com.bloo.wear.tile`

---

## 1. Purpose

`BlooTileService` is the Wear OS **Tile** provider for Bloo — the glanceable
surface a user pins to their watch tile carousel. Each tile renders one
vehicle's at-a-glance state: a **charge/fuel arc** wrapping the edge, a
**center column** (car name, big battery/fuel percentage, status line), a
**range primary label**, a **Battery/Fuel secondary label**, and **1–2 circular
action buttons** (lock / climate / charge). Tapping the center opens the watch
app; tapping an action button fires the corresponding remote command against the
real vehicle.

It exists as an **abstract base** with four concrete `poolIndex`ed subclasses
(`BlooTile1`..`BlooTile4`, lines 579–582), forming a fixed pool of four tile
types mirroring the phone's `BlooTile1..12` Quick Settings pool. This lets a
user with multiple cars add one Tile per car to their watch face, each pinned
independently to a car via Settings (file docblock, lines 47–56).

The unit is **ProtoLayout Tiles, not Compose** — the layout is a serialized
element tree rendered out-of-process by the system's Tile renderer.

The file also exposes one top-level function, `refreshWearGlanceables`
(line 589), the single entry point other code uses to nudge all four tiles plus
the watch-face complications to re-render.

---

## 2. Public surface

### `abstract class BlooTileService : TileService()` (line 57)

The base Tile service. Extends `androidx.wear.tiles.TileService`.

- **`protected abstract val poolIndex: Int`** (line 59) — which pool slot this
  concrete tile owns (0–3). Used to (a) pick the pinned car VIN from
  `tileCarVins[poolIndex]` and (b) namespace the per-tile last-click dedupe key.

- **`override fun onDestroy()`** (lines 77–81) — cancels `tileScope` and shuts
  down `executor`. The system does not keep the service warm; it may
  destroy/recreate between requests, so both the thread and coroutine scope are
  torn down here to avoid leaking one per lifecycle.

- **`override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile>`**
  (lines 99–102) — the primary framework callback. Submits `buildTile(params)`
  to `executor` via `Futures.submit(Callable { ... }, executor)` and returns the
  Future immediately. Called by the out-of-process Tiles system on: first pin,
  freshness-interval timeout, user tap (id carried in
  `currentState.lastClickableId`), or a push from `refreshWearGlanceables`.

- **`override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources>`**
  (lines 110–121) — returns an immediate Future mapping the four string image
  ids (`Img.LOCK`, `Img.UNLOCK`, `Img.CLIMATE`, `Img.BOLT`) to Android drawable
  resources, versioned `RES_VERSION`. This is ProtoLayout's resource
  indirection: the layout tree only references resources by string id because it
  is rendered in a separate process without this app's resource table.

### Concrete subclasses (lines 579–582)

- **`class BlooTile1 : BlooTileService()`** — `override val poolIndex = 0`
- **`class BlooTile2 : BlooTileService()`** — `override val poolIndex = 1`
- **`class BlooTile3 : BlooTileService()`** — `override val poolIndex = 2`
- **`class BlooTile4 : BlooTileService()`** — `override val poolIndex = 3`

These are the classes registered in the manifest as actual tile providers.

### Top-level function

- **`fun refreshWearGlanceables(context: android.content.Context)`**
  (lines 589–594) — nudges every pool tile and the watch-face complications to
  re-read the latest snapshot. Gets the `TileService.getUpdater(context)`
  (wrapped in `runCatching`), then calls `updater?.requestUpdate(cls)` for each
  of the four **concrete** classes (each wrapped in `runCatching`), and finally
  `ComplicationLink.requestUpdate(context)`. **Must** target concrete classes —
  the updater matches by exact `ComponentName`, so the abstract base would match
  nothing. This is the single source of truth for "which glanceable surfaces
  exist," called from the app ViewModel (app open) and `WearListenerService`
  (phone push, app closed).

---

## 3. Internal structure

### Fields (lines 69–72)

- **`private val executor = Executors.newSingleThreadExecutor()`** — single
  dedicated thread running the blocking `buildTile()` off the framework's binder
  thread.
- **`private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`**
  — separate scope used only for the fire-and-forget network relay of a
  tap-triggered command, which must NOT block tile rendering.

### `private object Img` (lines 84–89)

String constants for the four layout resource ids: `LOCK="img_lock"`,
`UNLOCK="img_unlock"`, `CLIMATE="img_climate"`, `BOLT="img_bolt"`.

### `private fun imgRes(resId: Int): ResourceBuilders.ImageResource` (lines 125–132)

Wraps a drawable resource id into a ProtoLayout `ImageResource` (via
`AndroidImageResourceByResId`), for the id→resource map in
`onTileResourcesRequest`.

### `private fun buildTile(params): TileBuilders.Tile` (lines 151–233)

The core synchronous builder. Runs on `executor`. Control flow:

1. Resolve `ctx = applicationContext`, `device = params.deviceConfiguration`,
   `clickId = params.currentState.lastClickableId` (line 159). `clickId` is only
   a *fresh* tap sometimes — the system persists and replays it on every later
   request, so it needs the dedupe check below.
2. Wraps everything from step 3 onward in a `try` (line 160) whose `catch`
   (lines 223–232) returns a safe "Open Bloo" `emptyLayout` tile so a corrupt
   DataStore read or layout error never crashes the tile.
3. **`runBlocking { ... }`** (lines 165–206) does one disk pass, producing a
   `TileResult`:
   - Constructs `SnapshotStore(ctx)` and `WearLocalStore(ctx)`.
   - `local = runCatching { localStore.flow.first() }.getOrNull()`.
   - `actions = local?.tileActions ?: listOf("lock", "climate")`.
   - `tileVin = local?.tileCarVins?.getOrNull(poolIndex)` — the car pinned to
     this slot (nullable).
   - Local fun `pick(d)` (lines 175–176): the car whose `vin == tileVin` if it
     still exists, else `d.selected` (so an unconfigured/new slot is never
     blank).
   - `data = store.current()`, `car = pick(data)`.
   - **Dedupe + command execution** (lines 187–203): if `clickId` starts with
     `CMD_PREFIX` **and** `clickId != localStore.tileLastClick(poolIndex)`:
     - `localStore.setTileLastClick(poolIndex, clickId)` records it.
     - `action = clickId.removePrefix(CMD_PREFIX).substringBefore(':')` extracts
       the action name (dropping the nonce suffix).
     - If `car != null`: `WearComms.applyOptimistic(ctx, WearCommand(c.vin, action))`
       flips the snapshot in-store synchronously and returns a resolved command;
       then `tileScope.launch { runCatching { WearComms.relayCommand(ctx, resolved) } }`
       relays the slower network half in the background.
     - Re-reads `data = store.current()`, `car = pick(data)` so the render
       reflects the optimistic flip.
   - `metric = local?.unitSystem == "metric"`.
   - Returns `TileResult(car, resolveRoles(ctx, car?.vin), actions, metric)`.
4. Destructures the result into `snapshot`, `roles`, `actions`, `metric`.
5. `nonce = System.currentTimeMillis().toString(36)` (line 212) — a per-render
   nonce making this render's click ids unique.
6. `layout =` `emptyLayout(...)` if `snapshot == null` else
   `carLayout(ctx, device, snapshot, roles, actions, nonce, metric)` (line 213).
7. `freshness = if (snapshot?.charging == true) FRESHNESS_CHARGING_MS else FRESHNESS_MS`
   (line 216) — refresh faster while charging.
8. Builds and returns the `Tile` with `RES_VERSION`, the freshness interval, and
   `Timeline.fromLayoutElement(layout)` (lines 218–222).

### `private suspend fun resolveRoles(ctx, vin: String?): WearColorRoles` (lines 244–250)

Reads `WearSettingsStore(ctx).flow.first()` (DataStore-backed, synced from the
phone over the Data Layer). Prefers a **per-car** override
`payload?.carColors?.get(vin)`, then the global `payload?.colors`, then
`DEFAULT_ROLES`. The whole thing is in `runCatching { ... }.getOrElse { DEFAULT_ROLES }`,
so any read failure (no sync yet, corrupt store) also falls back to defaults.

### `private fun emptyLayout(ctx, device): LayoutElement` (lines 259–273)

`PrimaryLayout` with body text "Open Bloo to get started" (`TYPOGRAPHY_BODY1`,
`CLR_DIM`, max 2 lines, ellipsized) and a single primary chip
`openChip(ctx, device)`. Shown when no snapshot is available.

### `private fun carLayout(ctx, device, snap, roles, actions, nonce, metric=false): LayoutElement` (lines 286–437)

The full car glanceable. Pure function of its arguments (no disk I/O). Steps:

- **Screen sizing** (lines 295–297): `screenDp = device.screenWidthDp`,
  `isSmall = screenDp < 193`, `isTiny = screenDp < 182`.
- **State extraction** (lines 299–310): `locked/charging/climate` from the
  `== true` comparison on nullable Booleans; `pct = snap.percent ?: 0`;
  `pctText = "${snap.percent ?: "—"}%"`; `rngText = snap.rangeMi?.let { formatDistance(it, metric) } ?: ""`;
  `secondaryLabel = if (snap.hasBattery) "Battery" else "Fuel"`;
  `hasPct = snap.percent != null`.
- **Arc color** (`arcArgb`, lines 313–319): `!hasPct → CLR_TRACK`;
  `charging → CLR_CHARGE`; `pct < 15 → roles.error`; `pct < 30 → CLR_WARN`;
  else `roles.primary`.
- **Arc** (lines 321–328): `CircularProgressIndicator` with
  `progress = pct.coerceIn(0,100)/100f` and
  `Colors(arcArgb, CLR_WHITE, CLR_TRACK, CLR_WHITE)`.
- **Status line** (lines 333–334): `baseStatus = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)`;
  `statusLine = if (charging && rngText.isNotEmpty()) "$baseStatus · $rngText" else baseStatus`.
- **Status color** (`statusArgb`, lines 338–345), mirroring `vehicleStateLabel`'s
  priority exactly: `engineOn==true → roles.primary`; `charging → CLR_CHARGE`;
  `climate → roles.tertiary`; `locked==true → CLR_DIM`;
  `locked==false → CLR_UNLOCKED`; else `CLR_DIM`.
- **Percentage typography** (line 348): `TYPOGRAPHY_DISPLAY2` if `isTiny` else
  `TYPOGRAPHY_DISPLAY1`.
- **`centerCol`** (lines 350–375): a `Column` of three `Text`s — name
  (`CAPTION2`, `CLR_DIM`), `pctText` (`pctTypo`, `CLR_WHITE`), `statusLine`
  (`CAPTION1`, `statusArgb`) — each 1 line, ellipsized.
- **`chosen` actions** (lines 382–384):
  `actions.filter { it in TILE_CHIP_ACTIONS && (it != "charge" || snap.hasBattery) }.distinct().take(2).ifEmpty { listOf("lock", "climate") }`
  — filters to known chip actions, drops `charge` for a car with no chargeable
  battery, caps at 2, defaults to lock+climate if empty.
- **Button sizing** (lines 385–390): 1 action → `ButtonDefaults.LARGE_SIZE`;
  else `isTiny` → `dp(44f)`; else `ButtonDefaults.DEFAULT_SIZE`.
  `chipGap = if (isTiny) 6f else 12f`.
- **`chipRow`** (lines 392–397): a `Row` (wrap height) of
  `actionButton(...)` per chosen action, with a `spacer(chipGap)` between them.
- **`gap`** (line 399): `2f`/`3f`/`5f` for tiny/small/normal.
- **`centerTappable`** (lines 402–409): the `centerCol` wrapped in a `Box` whose
  modifier sets `openClickable(ctx)` — tapping name/%/status opens the app.
- **`content`** (lines 411–415): `Column` of `centerTappable`, `spacer(gap)`,
  `chipRow`.
- **Return** (lines 417–436): `EdgeContentLayout` with `arc` as edge content,
  a **primary label** of `rngText.ifBlank { snap.name }` (`CAPTION1`, `CLR_DIM`),
  `content` in the center, and a **secondary label** of `secondaryLabel`
  (`CAPTION2`, `CLR_DIM`).

### `private fun openChip(ctx, device)` (lines 441–442)

A `CompactChip` labeled "Open Bloo" using `openClickable(ctx)`.

### `private fun openClickable(ctx): Clickable` (lines 445–458)

`Clickable` id `"open"` whose `LaunchAction` targets
`AndroidActivity(packageName = ctx.packageName, className = "com.bloo.wear.MainActivity")`.

### `private fun cmd(action: String, nonce: String): Clickable` (lines 463–466)

A chip click id `"cmd:<action>:<nonce>"` with a `LoadAction` (which re-requests
the tile). The nonce makes each render's ids unique so `buildTile`'s dedupe can
tell a fresh tap from a replayed stale `lastClickableId`.

### `private fun actionButton(ctx, action, snap, roles, size, nonce): LayoutElement` (lines 471–518)

Builds one state-reflecting circular icon `Button`. `offColors =
ButtonColors(roles.surfaceContainerHigh, roles.onSurfaceVariant)`. `when (action)`:

- **`"charge"`** (lines 489–494): icon `BOLT`; if `charging` →
  `ButtonColors(CLR_CHARGE, CLR_WHITE)` + `WearAction.CHARGE_OFF` + "Stop charging",
  else `offColors` + `WearAction.CHARGE_ON` + "Start charging".
- **`"climate"`** (lines 495–504): icon `CLIMATE`; if `climate` →
  `ButtonColors(roles.tertiary, roles.onTertiary)` + `WearAction.CLIMATE_OFF` +
  "Turn climate off", else `offColors` + `WearAction.CLIMATE_ON` + "Turn climate on".
- **`else` (lock)** (lines 505–510): icon `LOCK` if locked else `UNLOCK`; if
  locked → `offColors` + `WearAction.UNLOCK` + "Unlock", else
  `ButtonColors(roles.primary, roles.onPrimary)` + `WearAction.LOCK` + "Lock".
  Note unlocked is the highlighted (filled-primary) state.

Returns `Button.Builder(ctx, cmd(act, nonce)).setButtonColors(colors).setIconContent(img).setContentDescription(desc).setSize(size)`.

### `private fun spacer(dp: Float): LayoutElement` (lines 520–524)

A square `Spacer` of `dp` × `dp`.

---

## 4. Data & types

### `private data class TileResult` (lines 137–142)

Bundles everything `buildTile` resolves from disk in one pass:

- `car: VehicleSnapshot?` — resolved car (pinned-VIN car or fallback selected),
  possibly null.
- `roles: WearColorRoles` — resolved theme (per-car / global / default).
- `actions: List<String>` — chosen tile chip actions from local store.
- `metric: Boolean` — true when `unitSystem == "metric"`.

### `private object Img` (lines 84–89)

String resource ids: `LOCK`, `UNLOCK`, `CLIMATE`, `BOLT` — see §3.

### `private companion object` constants (lines 526–575)

- `RES_VERSION = "4"` — resources version (must be bumped when the id→drawable
  map changes so the system re-requests resources).
- `CMD_PREFIX = "cmd:"` — command click-id prefix.
- `FRESHNESS_MS = 10L*60L*1000L` (10 min) — idle refresh interval.
- `FRESHNESS_CHARGING_MS = 90L*1000L` (90 s) — refresh interval while charging.
- `CLR_WHITE = 0xFFFFFFFF`, `CLR_DIM = 0xFFAAAAAA` — plain ARGB ints.
- `CLR_CHARGE = BlooColors.chargeGreen`, `CLR_WARN = BlooColors.warn` — pulled
  from the shared `BlooColors` (raw Int store) so they can't drift.
- `CLR_TRACK = 0xFF3C3C3C` — arc track color.
- `CLR_UNLOCKED = BlooColors.heat` — the "unlocked" red, matching the phone
  widget's `unlockedRed`.
- `DEFAULT_ROLES: WearColorRoles` (lines 546–575) — the full hardcoded dark
  palette fallback (primary `0xFF7B83EB`, tertiary `0xFF4CD9E0`,
  `error = BlooColors.heat`, etc.), used when no phone sync has happened.

All colors are raw ARGB `Int` (ProtoLayout is not Compose; `argb(...)` wraps
them into `ColorProp`).

### Encoding notes

- Nullable Booleans (`snap.locked/charging/climateOn/engineOn`) are read with
  explicit `== true` / `== false` so `null` (unknown) is treated distinctly.
- `snap.hasBattery` (the manual powertrain override, not raw `isEv`) drives the
  Battery/Fuel label and whether `charge` is offered.
- Click id format: `"cmd:<action>:<nonce>"`; the action is parsed with
  `removePrefix("cmd:").substringBefore(':')`.

---

## 5. State & concurrency

- **No long-lived in-process state** beyond `executor` and `tileScope`. The
  service is not kept warm; all vehicle/theme state lives in DataStores read
  fresh each request.
- **`executor`** — single dedicated thread; runs the blocking `buildTile`.
  `onTileRequest` returns a `Future` on it immediately so the framework binder
  thread is never blocked.
- **`tileScope`** — `SupervisorJob() + Dispatchers.IO`; used only for the
  fire-and-forget `relayCommand` network call after a tap. A failing relay won't
  cancel siblings (SupervisorJob) and is additionally wrapped in `runCatching`.
- **`runBlocking`** inside `buildTile` bridges the synchronous TileService
  contract to the suspend-based DataStore reads (`SnapshotStore.current()`,
  `WearLocalStore.flow.first()`, `WearSettingsStore.flow.first()`,
  `tileLastClick`/`setTileLastClick`, `WearComms.applyOptimistic`).
- **Recomposition equivalent:** the tile "recomposes" when the system calls
  `onTileRequest` — on pin, freshness timeout, tap, or a
  `refreshWearGlanceables` push.
- **Cleanup:** `onDestroy` cancels `tileScope` and shuts down `executor`.
- **Dedupe state** is persisted in `WearLocalStore` per `poolIndex`
  (`tileLastClick(poolIndex)`), not in memory, because the service may be
  recreated between the tap and the next render.

---

## 6. Collaborators & data flow

**Reads (in):**
- `SnapshotStore(ctx).current()` → `SnapshotData` (`.vehicles`, `.selected`) —
  vehicle snapshots.
- `WearLocalStore(ctx).flow.first()` → local config: `tileActions`,
  `tileCarVins`, `unitSystem`; plus `tileLastClick(poolIndex)` for dedupe.
- `WearSettingsStore(ctx).flow.first()` → phone-synced `carColors` / `colors`
  (`WearColorRoles`).
- `params.currentState.lastClickableId` — the clicked element id (from system).
- `params.deviceConfiguration` — screen size.
- `R.drawable.ic_shortcut_lock/unlock/climate`, `ic_widget_bolt` — icons.

**Writes / actions (out):**
- `WearLocalStore.setTileLastClick(poolIndex, clickId)` — persists the handled
  tap id.
- `WearComms.applyOptimistic(ctx, WearCommand(vin, action))` — optimistic flip
  written back into `SnapshotStore`.
- `WearComms.relayCommand(ctx, resolved)` — relays the command over the Wear
  Data Layer to the phone (background on `tileScope`).
- `ActionBuilders.LaunchAction` → launches `com.bloo.wear.MainActivity`.
- Returns a `TileBuilders.Tile` to the Tiles framework.

**Called by:**
- The Wear Tiles system (out-of-process) → `onTileRequest` / `onTileResourcesRequest`.
- `refreshWearGlanceables(context)` → `TileService.getUpdater(...).requestUpdate(BlooTileN::class.java)`,
  invoked by the app ViewModel (app open) and `WearListenerService` (phone push).
- `refreshWearGlanceables` also calls `ComplicationLink.requestUpdate(context)`.

**Shared helpers:** `vehicleStateLabel(...)`, `formatDistance(...)`,
`WearAction.*`, `WearColorRoles`, `BlooColors.*`, `TILE_CHIP_ACTIONS`.

---

## 7. Invariants & assumptions

- `poolIndex` is stable per concrete class (0–3) and matches an index into
  `tileCarVins` (`getOrNull` guards out-of-range gracefully).
- The Tiles system persists and **replays** `lastClickableId` on every request,
  so a command must only execute when `clickId != tileLastClick(poolIndex)` —
  the nonce is what makes each fresh tap a new id.
- `RES_VERSION` ("4") must match between the Tile's `setResourcesVersion` and
  `onTileResourcesRequest`'s version, and must be bumped whenever the drawable
  mapping changes.
- The layout tree references images only by the four `Img.*` string ids, all of
  which must be present in the resources mapping.
- `applyOptimistic` must synchronously commit to `SnapshotStore` before the
  re-read at line 201, or the tile would render stale state.
- `carLayout` assumes it does no blocking I/O — everything is passed in.
- Nullable Booleans are three-valued (`true`/`false`/unknown); code relies on
  `== true`/`== false` to distinguish them.
- `pct.coerceIn(0,100)` guards the arc against out-of-range percentages.
- `refreshWearGlanceables` relies on the updater matching **exact concrete**
  `ComponentName`s — the abstract class would match nothing.

---

## 8. Gotchas & sharp edges

- **Tap replay dedupe (lines 180–203):** the original bug was that one Unlock tap
  kept re-sending unlock on every background refresh because the system replays
  `lastClickableId`. Fix: per-render nonce in the id + a persisted
  per-poolIndex last-handled id. Both parts are load-bearing.
- **This is ProtoLayout, not Compose** — no `remember`, no `StateFlow` in the UI;
  the "state" is whatever the DataStores hold at request time, and the service is
  cold between requests.
- **`runBlocking` on `executor` is intentional** — the TileService callback
  contract is a `Future`, but the stores are suspend-based; the blocking happens
  on a dedicated thread, off the binder thread.
- **Fail-safe catch (lines 223–232):** any exception in the build path yields an
  "Open Bloo" tile rather than a failed Future (which would render a blank/broken
  tile).
- **`charge` chip is dropped for gas-only cars** (line 382) so a car with no
  chargeable battery never shows a button that can only fail.
- **`climate` button color reflects only its own state** (comment lines 496–500):
  it previously checked `charging` first and would borrow the charge button's
  green when both were true, misleading the user. Do not reintroduce that.
- **Unlocked is the *highlighted* lock state** (line 507) — filled-primary — the
  inverse of the intuitive "locked = highlighted" expectation.
- **`hasBattery`, not `isEv`** drives Battery/Fuel labeling and the charge chip,
  so a manually-corrected PHEV (misreported as gas by the API) reads correctly.
- **`statusArgb` must mirror `vehicleStateLabel`'s priority exactly** (comment
  lines 335–337) or the color would disagree with the settled-on text.
- **Color constants reference `BlooColors`** (lines 538–543, 571) rather than
  re-declaring hex, specifically because an earlier local copy (`chargerLabel`)
  drifted out of sync.
- **Freshness is longer (10 min) when idle, short (90 s) when charging** — a
  tradeoff between battery/network and percentage freshness.
- **`refreshWearGlanceables` swallows all failures** via nested `runCatching` so a
  missing updater or one failing tile never breaks the others or the caller.
