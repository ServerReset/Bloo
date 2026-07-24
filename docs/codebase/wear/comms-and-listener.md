# wear: comms + remote + listener + state writer

Deep-dive reference for the Wear OS module's **phone↔watch wire layer**: the six
files that send commands/state across the Wearable Data Layer, receive the
phone's pushes/results, fold them into the watch's on-disk stores, open things on
the phone, load map tiles, and post native watch notifications.

Files covered:
- `wear/src/main/java/com/bloo/wear/WearComms.kt` — the watch's outbound link to the car (relay-or-standalone).
- `wear/src/main/java/com/bloo/wear/WearRemote.kt` — open URL / dial on the paired phone.
- `wear/src/main/java/com/bloo/wear/WearListenerService.kt` — inbound Data Layer receiver (system-managed service).
- `wear/src/main/java/com/bloo/wear/WearStateWriter.kt` — fold received payloads into on-disk stores.
- `wear/src/main/java/com/bloo/wear/WearImage.kt` — Coil image loader with a proper User-Agent for OSM tiles.
- `wear/src/main/java/com/bloo/wear/WearNotifications.kt` — native watch-side notifications.

Closely-related event buses (defined in sibling files, referenced heavily here):
`WearCommandEvents`, `WearSyncEvents`, `WearAiEvents` — in-process `SharedFlow`
buses the listener emits onto. The cross-module wire protocol (paths, encoders,
data classes) lives in `shared` at `com.bloo.bluelink.data.WearSync` /
`WearCommandRunner`.

---

## 1. Purpose

This unit is the **watch side of the WearSync wire protocol**. The watch is a
Wear OS app that can control real Hyundai/Genesis/Kia vehicles. It has two ways
to reach the car:

1. **Relay through a connected phone** (preferred) — the phone holds the tested,
   authenticated Blue Link / Kia Connect session and serializes calls behind
   `BlueLinkGate.statusMutex`. The watch just forwards a command and lets the
   phone execute it.
2. **Standalone** (fallback) — when no phone is reachable, the watch runs the
   command itself using a session that was previously synced down to it
   (standalone-on-Wi-Fi/cell path).

`WearComms` is the outbound object that chooses between those two per call and
also publishes watch-originated state (climate drafts, presets, pebble order,
local settings, toggles) back to the phone. `WearListenerService` is the inbound
counterpart: a system-instantiated `WearableListenerService` that receives the
phone's pushes and command results even when the watch UI is closed, and
persists them via `WearStateWriter`. `WearRemote`, `WearImage`, and
`WearNotifications` are small support objects (open-on-phone, map-tile image
loader, native notifications).

**Two transports, deliberately chosen per call** (`WearComms.kt:27-52`):
- **MessageClient** (`Wearable.getMessageClient`) — one-shot byte-array message
  to a specific currently-connected node. Requires a live connection *now*;
  "phone wasn't there" is a meaningful, actionable outcome. Used for commands
  and sync requests.
- **DataClient** (`Wearable.getDataClient`) — publishes a versioned `DataItem`
  keyed by a path; the system syncs the latest value to every node when next
  connected, and each node's `WearableListenerService` is notified. Used for
  "latest known value" state (climate/presets/pebble/local/toggles) and for
  `pullLatest` (reading back existing DataItems on launch).

---

## 2. Public surface

### `object WearComms` (`WearComms.kt:54`)

All functions are `suspend` and run their network/disk work inside
`withContext(Dispatchers.IO)`.

- **`suspend fun phoneNodeId(context: Context): String?`** (`:64`)
  Returns the id of a connected phone node, or `null` if none reachable.
  Queries `Wearable.getNodeClient(context).connectedNodes` (10s `Tasks.await`
  timeout), prefers a node flagged `isNearby` (direct Bluetooth, lowest
  latency), else the first reachable node. Wrapped in `runCatching{}.getOrNull()`
  so any failure/timeout returns `null` rather than throwing. Every caller
  treats `null` as "no phone — go standalone".

- **`suspend fun send(context: Context, command: WearCommand)`** (`:76`)
  Full command path: `applyOptimistic` (resolve + local flip) then
  `relayCommand` (network). Split so a caller needing only the local flip to
  land can await just `applyOptimistic`.

- **`suspend fun applyOptimistic(context: Context, command: WearCommand): WearCommand`** (`:97`)
  Resolves `TOGGLE_*` actions to an explicit `LOCK`/`UNLOCK`/etc. **from the
  pre-flip snapshot** via `WearCommandRunner.resolveToggle`, then writes the
  optimistic snapshot via `WearCommandRunner.optimistic` +
  `SnapshotStore.updateVehicle`. Returns the resolved command. Matches by
  `snap.vin == command.vin`; if no matching vehicle, returns the command
  unchanged. Whole body wrapped in `runCatching{}` (failures are swallowed —
  the returned `resolved` may still be the original toggle if lookup threw).

- **`suspend fun relayCommand(context: Context, resolved: WearCommand)`** (`:112`)
  Network half of `send`. If `phoneNodeId != null`, sends `resolved` to the
  phone via MessageClient on `WearSync.PATH_COMMAND` (payload =
  `WearSync.encodeCommand(resolved).toByteArray()`, 10s timeout). On send
  failure falls back to `runStandalone`. If no phone node, goes straight to
  `runStandalone`.

- **`suspend fun relayToPhone(context: Context, command: WearCommand): Boolean`** (`:164`)
  Relays a **phone-only** request (e.g. AI summary) — no optimistic flip, no
  standalone fallback (the watch can't fulfil it). Returns `true` only if the
  phone received it (`false` immediately when no node). Also uses
  `PATH_COMMAND`.

- **`suspend fun requestSync(context: Context, vin: String, refresh: Boolean): Boolean`** (`:195`)
  Ask for fresh data. Builds `WearCommand(vin, action = if (refresh) REFRESH else "")`.
  If a phone node exists, sends on `WearSync.PATH_SYNC_REQUEST`; if the send
  fails, falls back to `WearCommandRunner.refresh(context, vin, force = refresh)`.
  Return value semantics are subtle (see §8): returns `sent || refresh` in the
  phone-present branch, and `refresh` in the no-node branch — i.e. **`true` only
  means "the phone actually received the request"** when `refresh == false`.

- **`suspend fun publishClimate(context: Context, state: WearClimateState)`** (`:221`)
  DataClient write on `PATH_CLIMATE`: `KEY_PAYLOAD = encodeClimate(state)`,
  `KEY_TIMESTAMP = now`, `.setUrgent()`. Mirrors the watch's live climate draft
  to the phone.

- **`suspend fun publishPresets(context: Context, presets: WearPresets)`** (`:234`)
  DataClient write on `PATH_PRESETS` (`encodePresets`, urgent).

- **`suspend fun publishPebbleOrder(context: Context, vin: String, order: List<String>): Boolean`** (`:250`)
  Wraps `(vin, order)` in `WearPebbleOrder`, writes DataItem on
  `PATH_PEBBLE_ORDER` (`encodePebbleOrder`, urgent). Returns `.isSuccess` so the
  caller can drop its optimistic override if the phone will never echo the
  order.

- **`suspend fun publishLocalSettings(context, uiScale: Float, unitSystem: String? = null, pinLockEnabled: Boolean = false, pinLockTiming: String = "immediate")`** (`:266`)
  DataClient write on `PATH_LOCAL` of a `WearLocalPayload(uiScale, unitSystem,
  watchPinLockEnabled, watchPinLockTiming)`. Pushes the watch's display scale
  (and PIN-lock backup fields) so the phone's Settings → Text scale slider stays
  in sync.

- **`suspend fun publishAiToggle(context: Context, enabled: Boolean)`** (`:296`)
  DataClient write on `PATH_AI_TOGGLE` of `WearAiTogglePayload(enabled)`. Its
  **own path** (not `PATH_LOCAL`) so it can't race the uiScale echo.

- **`suspend fun publishAuroraToggle(context: Context, enabled: Boolean, colorMode: String? = null)`** (`:311`)
  DataClient write on `PATH_AURORA_TOGGLE` of `WearAuroraTogglePayload(enabled,
  colorMode)`; sets the phone's aurora colour mode when `colorMode` non-null.

- **`suspend fun pullLatest(context: Context)`** (`:328`)
  On launch, reads back **all** existing DataItems
  (`Wearable.getDataClient(context).dataItems`, 10s timeout) and dispatches each
  by `item.uri.path` to the matching `WearStateWriter.persist*`. Handles
  `PATH_STATE/AUTH/SETTINGS/PRESETS/CLIMATE/EXTRAS`. Skips items with a null
  `KEY_PAYLOAD`. **Releases the `DataItemBuffer` in a `finally`** (native
  Parcel-backed cursor; must be released or it leaks).

### `object WearRemote` (`WearRemote.kt:9`)

- **`fun openOnPhone(context: Context, url: String)`** (`:10`)
  Builds `Intent(ACTION_VIEW)` + `CATEGORY_BROWSABLE` with `Uri.parse(url)`,
  starts it on the phone via `RemoteActivityHelper(context).startRemoteActivity`.
  Adds a listener (on `MoreExecutors.directExecutor()`) that calls
  `future.get()`; on failure posts a `WearNotifications` "Couldn't open on phone"
  alert (id = `("open$url").hashCode()`). Non-suspend, fire-and-forget.

- **`fun dialOnPhone(context: Context, number: String)`** (`:33`)
  Same pattern with `Uri.parse("tel:$number")`; failure notification id =
  `("dial$number").hashCode()`, title "Couldn't open dialer on phone".

### `class WearListenerService : WearableListenerService()` (`WearListenerService.kt:38`)

System-instantiated; not called directly by app code.

- **`override fun onDestroy()`** (`:42`) — cancels `serviceScope`.
- **`override fun onDataChanged(events: DataEventBuffer)`** (`:53`) — batch of
  DataItem changes; see §3.
- **`override fun onMessageReceived(event: MessageEvent)`** (`:108`) — one-shot
  MessageClient messages (command/sync/AI **results**); see §3.

### `object WearStateWriter` (`WearStateWriter.kt:13`)

All `suspend`.
- **`persistState(context, raw)`** (`:15`) — `WearSync.decodeState(raw)`, then
  `SnapshotStore(context).saveVehicles(payload.vehicles)`. Uses `saveVehicles`
  (not a full overwrite) to **preserve the watch's own car selection**.
- **`persistSettings(context, raw)`** (`:22`) — `WearSettingsStore(context).save(raw)` (raw string stored as-is).
- **`persistPresets(context, raw)`** (`:26`) — `WearPresetsStore(context).save(raw)`.
- **`persistClimate(context, raw)`** (`:30`) — `WearClimateStore(context).save(raw)`.
- **`persistExtras(context, raw)`** (`:34`) — `WearExtrasStore(context).save(raw)`.
- **`persistAuth(context, raw)`** (`:38`) — `WearSync.decodeAuth(raw)` →
  `WearAuthBundle`; for each `s` in `bundle.sessions`, saves a
  `SessionStore.Session(accessToken, refreshToken, username, pin,
  brand = Brand.fromName(s.brand), deviceId)`. This is what makes standalone
  mode possible.

### `object WearImage` (`WearImage.kt:16`)

- **`fun loader(context: Context): ImageLoader`** (`:21`) — lazy, double-checked
  `@Volatile` singleton; builds on first call using `applicationContext`.
- **`private fun build(context): ImageLoader`** (`:26`) — Coil `ImageLoader` with
  an OkHttp interceptor injecting `User-Agent: Bloo-WearOS/0.1
  (https://claude.ai/code)`, a memory cache capped at 1% of RAM
  (`maxSizePercent(0.01)`), and a disk cache in `cacheDir/coil` capped at 2%
  (`maxSizePercent(0.02)`).

### `object WearNotifications` (`WearNotifications.kt:21`)

- **`fun hasPermission(context): Boolean`** (`:37`) — `true` below Android 13
  (TIRAMISU), else checks `POST_NOTIFICATIONS` granted.
- **`fun post(context, id: Int, title: String, text: String)`** (`:42`) — no-op
  if no permission; ensures the channel; builds a `NotificationCompat`
  (BigTextStyle, auto-cancel, `CATEGORY_STATUS`, accent color, tap opens
  `MainActivity`); notifies. Whole `notify` wrapped in `runCatching`.
- **`private fun ensureChannel(context)`** (`:25`) — creates the
  `"bloo_wear_alerts"` channel ("Car alerts", `IMPORTANCE_HIGH`) on API 26+ if
  absent.

---

## 3. Internal structure & control flow

### `WearComms.send` → optimistic + relay
`send` (`:76`) calls `applyOptimistic` and awaits it (the local snapshot flip
lands), then `relayCommand`. Callers that want to fire the network half in the
background (e.g. `BlooTileService` re-reading the store to render immediately)
call `applyOptimistic` themselves, await it, then launch `relayCommand`
separately — avoiding double-applying the optimistic update.

**`applyOptimistic` step-by-step** (`:97`):
1. `resolved = command`.
2. Load `SnapshotStore(context)`, find the vehicle where `vin == command.vin`.
3. `resolved = command.copy(action = WearCommandRunner.resolveToggle(snap, command.action))`
   — resolves `TOGGLE_*` against the **pre-flip** state.
4. `store.updateVehicle(WearCommandRunner.optimistic(snap, resolved.action))` —
   writes the expected post-command snapshot so UI reacts instantly.
5. Return `resolved`.
Order matters: resolve *before* flipping (§8).

**`relayCommand`** (`:112`): `phoneNodeId` → if present, MessageClient send on
`PATH_COMMAND`; `.onFailure { runStandalone }`. If null node → `runStandalone`.

**`runStandalone` (private)** (`:140`):
1. `WearCommandRunner.execute(context, command)` → `WearCommandResult`.
2. On failure (`!result.ok`): log; **revert the optimistic flip** by writing
   `WearCommandRunner.optimistic(it, WearCommandRunner.inverse(command.action))`
   back to the snapshot store (so a UI that jumped to "locked" flips back to
   "unlocked"); then `WearNotifications.post` with id
   `("cmd"+vin+action).hashCode()`.
3. On success: just log.

**`requestSync`** (`:195`): builds command with `REFRESH` or `""`; branches on
node presence; standalone fallback via `WearCommandRunner.refresh` when send
fails or no node. See §8 for the return-value semantics.

### `WearListenerService.onDataChanged` (`:53`)
1. `events.mapNotNull` — drop non-`TYPE_CHANGED` events; extract
   `(item.uri.path to raw)` where `raw = DataMapItem.fromDataItem(item).dataMap
   .getString(KEY_PAYLOAD)` (skip null payload). This runs synchronously on the
   binder thread — cheap.
2. If `updates.isEmpty()` return early.
3. `serviceScope.launch { ... }` — off the binder thread:
   - `var tileNeedsRefresh = false`.
   - For each `(path, raw)`, **wrapped individually in `runCatching`** (one bad
     payload must not skip the rest of the batch), dispatch:
     `PATH_STATE` → `persistState` + set `tileNeedsRefresh`;
     `PATH_AUTH` → `persistAuth`;
     `PATH_SETTINGS` → `persistSettings` + set `tileNeedsRefresh` (theme colors
     the Tile reads);
     `PATH_PRESETS`/`PATH_CLIMATE`/`PATH_EXTRAS` → matching `persist*`.
   - If `tileNeedsRefresh`, `runCatching { refreshWearGlanceables(applicationContext) }`
     to push a tile + complication refresh.

### `WearListenerService.onMessageReceived` (`:108`)
`raw = String(event.data ?: ByteArray(0))`. `when (event.path)`:
- `PATH_COMMAND_RESULT` → `serviceScope.launch { decodeResult ?:return;
  WearCommandEvents.emit(result); WearNotifications.post(... "Command
  succeeded/failed", result.message ?: "Done") }`.
- `PATH_SYNC_RESULT` → `decodeSyncResult`; `WearSyncEvents.emit`; notify
  "Drive sync complete/failed".
- `PATH_AI_RESULT` → `decodeAiResult`; `WearAiEvents.emit`; **notify only on
  failure** (`if (!result.ok)`) — success is already visible on the AI card.

Every branch does two things: emit on the in-process event bus (for a live
`WearViewModel` to react immediately) **and** post a notification as a backstop
for when the app is closed. Each branch guards with `?: return@launch` on decode
failure inside a `runCatching`.

---

## 4. Data & types

This unit **defines no data classes of its own** — every payload type lives in
`shared` (`com.bloo.bluelink.data.*`) and is (de)serialized through `WearSync`.
Types touched here, with the fields this code reads/writes:

- **`WearCommand`** (relayed on `PATH_COMMAND`/`PATH_SYNC_REQUEST`) — fields used
  here: `vin: String`, `action: String` (a `WearAction.*` verb; also `acLimit`/
  `dcLimit` etc. elsewhere). `action` is free-form text on the wire, not an
  enum, so unknown verbs are ignored rather than failing to decode
  (`WearSync.kt:234-259`).
- **`WearAction`** constants (`WearSync.kt:243`): `TOGGLE_LOCK/LOCK/UNLOCK`,
  `TOGGLE_CLIMATE/CLIMATE_ON/CLIMATE_OFF`, `TOGGLE_CHARGE/CHARGE_ON/CHARGE_OFF`,
  `FLASH_LIGHTS`, `HORN_AND_LIGHTS`, `REFRESH`, charge-limit apply, etc.
  `TOGGLE_*` = flip current; explicit variants force a state.
- **`WearCommandResult`** — has `vin`, `action`, `ok: Boolean`, `message:
  String?` (decoded on `PATH_COMMAND_RESULT`).
- **`WearSyncResult`** — `ok: Boolean`, `message: String?` (`PATH_SYNC_RESULT`).
- **`WearAiResult`** — `vin`, `ok: Boolean`, `message: String?` (`PATH_AI_RESULT`).
- **`WearClimateState`** — the live climate draft; `SeatLevel.apiValue` crosses
  the wire as ints (0=off, 3-5=cool, 6-8=heat). Encoded via `encodeClimate`.
- **`WearPresets`**, **`WearPebbleOrder(vin, order: List<String>)`**,
  **`WearLocalPayload(uiScale: Float, unitSystem: String?, watchPinLockEnabled:
  Boolean, watchPinLockTiming: String)`**, **`WearAiTogglePayload(enabled:
  Boolean)`**, **`WearAuroraTogglePayload(enabled: Boolean, colorMode:
  String?)`** — published DataItem payloads.
- **`WearStatePayload(vehicles, selectedVin, producedAt)`** — decoded by
  `persistState`; only `.vehicles` is used here.
- **`WearAuthBundle` / session entries** — decoded by `persistAuth`; each entry
  has `accessToken, refreshToken, username, pin, brand, deviceId`. (For Kia,
  `accessToken` = sid and `refreshToken` = rmtoken bound to `deviceId`.)

**Wire framing (all via `WearSync`):**
- DataItems put `KEY_PAYLOAD` (JSON string) + `KEY_TIMESTAMP` (ms) into a
  `PutDataMapRequest`, all with `.setUrgent()`.
- Messages send `encode*(...).toByteArray()`; the receiver does
  `String(event.data)` then `decode*`.
- All `decode*` use `runCatching{...}.getOrNull()` → **null on malformed JSON**,
  which every caller treats as "ignore".

**Path constants** (`WearSync.kt:30-88`): `PATH_STATE=/bloo/state`,
`PATH_AUTH=/bloo/auth`, `PATH_SETTINGS=/bloo/settings`,
`PATH_PRESETS=/bloo/presets`, `PATH_CLIMATE=/bloo/climate`,
`PATH_EXTRAS=/bloo/extras`, `PATH_PEBBLE_ORDER=/bloo/pebble_order`,
`PATH_COMMAND=/bloo/command`, `PATH_SYNC_REQUEST=/bloo/sync_request`,
`PATH_COMMAND_RESULT=/bloo/command_result`, `PATH_SYNC_RESULT=/bloo/sync_result`,
`PATH_AI_RESULT=/bloo/ai_result`, `PATH_LOCAL=/bloo/local`,
`PATH_AI_TOGGLE=/bloo/ai_toggle`, `PATH_AURORA_TOGGLE=/bloo/aurora_toggle`.

---

## 5. State & concurrency

- **`WearComms`** holds no state; every function hops to `Dispatchers.IO`.
  Blocking Play-Services `Task`s are bridged with `Tasks.await(task, 10,
  TimeUnit.SECONDS)`. State mutation is indirect: it reads/writes
  `SnapshotStore` (the DataStore-backed vehicle store the UI observes) inside
  `applyOptimistic`/`runStandalone`.
- **`WearListenerService`** owns `serviceScope = CoroutineScope(SupervisorJob() +
  Dispatchers.IO)` (`:40`), cancelled in `onDestroy` (`:44`). Binder callbacks
  do minimal synchronous work then `launch` disk I/O on `serviceScope`
  (`SupervisorJob` so one failing child doesn't cancel siblings). It also emits
  onto the three in-process `SharedFlow` buses (`WearCommandEvents` cap 1,
  `WearSyncEvents` cap 1, `WearAiEvents` cap 4 — all `extraBufferCapacity`, plain
  in-memory, same process as the ViewModel so no disk round-trip needed).
- **`WearImage`** — `@Volatile instance` with double-checked locking under
  `synchronized(this)` (`:22`). Single process-wide `ImageLoader`.
- **`WearNotifications`** — stateless; only touches the system
  `NotificationManager`.
- **Recomposition triggers**: none directly. UI updates flow from
  `WearStateWriter` writing to `SnapshotStore`/the `Wear*Store`s, which the
  ViewModel observes as flows.

---

## 6. Collaborators & data flow

**Outbound (`WearComms`):**
- Calls Play Services: `Wearable.getNodeClient/getMessageClient/getDataClient`.
- Calls `WearSync.encode*` to frame payloads; `WearCommandRunner.{resolveToggle,
  optimistic, inverse, execute, refresh}` for optimistic UI + standalone
  execution; `SnapshotStore.{current, updateVehicle, saveVehicles}`;
  `WearNotifications.post`; `AppLog.log`.
- Message paths out: `PATH_COMMAND` (send/relayToPhone), `PATH_SYNC_REQUEST`
  (requestSync). DataItem paths out: `PATH_CLIMATE, PATH_PRESETS,
  PATH_PEBBLE_ORDER, PATH_LOCAL, PATH_AI_TOGGLE, PATH_AURORA_TOGGLE`.
- Callers: `WearViewModel` (send, requestSync/resync, publish*), tile/QS/glance
  services (`applyOptimistic`+`relayCommand`), settings/AI screens.

**Inbound (`WearListenerService`):**
- Receives DataItem changes on `PATH_STATE/AUTH/SETTINGS/PRESETS/CLIMATE/EXTRAS`
  → dispatches to `WearStateWriter.persist*`.
- Receives messages on `PATH_COMMAND_RESULT/SYNC_RESULT/AI_RESULT` → decodes via
  `WearSync.decode*Result` → emits on `WearCommandEvents/WearSyncEvents/
  WearAiEvents` + `WearNotifications.post`.
- Calls `refreshWearGlanceables` (tile + complication refresh) on state/settings
  changes.

**`WearStateWriter`** fans into: `SnapshotStore.saveVehicles`, `SessionStore.save`,
and the raw-string stores `WearSettingsStore/WearPresetsStore/WearClimateStore/
WearExtrasStore`. It's called from both `WearListenerService.onDataChanged` (live
pushes) and `WearComms.pullLatest` (launch backfill).

**`WearRemote`** → `RemoteActivityHelper.startRemoteActivity` + `WearNotifications`.
**`WearImage`** → Coil + OkHttp; consumed by map/image composables.

---

## 7. Invariants & assumptions

- `phoneNodeId` returning non-null does **not** guarantee the send will succeed —
  the connection can drop mid-send, which is why `relayCommand` still has an
  `onFailure` → standalone path.
- `applyOptimistic` **must resolve `TOGGLE_*` before** writing the optimistic
  flip: the standalone executor (`WearCommandRunner.execute`) also decides toggle
  direction by re-reading the same `SnapshotStore`, so relaying a raw toggle
  after the flip would execute the opposite action (§8).
- `runStandalone`'s revert assumes `WearCommandRunner.inverse(action)` is the
  exact inverse of the optimistic flip, and that the vehicle still matches by
  `vin`.
- `pullLatest` assumes the `DataItemBuffer` **must** be `.release()`d (native
  cursor) — done in `finally`.
- `persistState` assumes `saveVehicles` preserves the watch's own `selectedVin`
  so a phone sync doesn't yank the watch's selection.
- All `decode*` tolerate malformed JSON by returning null; callers `?: return`.
- `onDataChanged`/`onMessageReceived` run on a **binder thread** — must stay
  fast; heavy work is moved to `serviceScope`.
- The event buses are same-process only (the listener has no `android:process`
  override) — a plain `SharedFlow` reaches the ViewModel without persistence.
- `WearNotifications.post` silently no-ops without `POST_NOTIFICATIONS` (Android
  13+); notification ids are deterministic hashes so repeats replace rather than
  stack.

---

## 8. Gotchas & sharp edges

- **`requestSync` return value is NOT "did we get data"** (`:176-213`). It's
  deliberately "did the **phone** receive the request". Phone-present branch
  returns `sent || refresh`; no-node branch returns `refresh`. So when `refresh
  == true`, the function returns `true` even if the phone was unreachable (the
  standalone fallback ran) — `WearViewModel.resync` relies on the
  `refresh == false` case to specifically say "bring your phone nearby to sync".
  `force = refresh` also matches the fallback's aggressiveness to what was asked;
  previously the `refresh == false` path had no fallback and silently dropped
  failed sends.
- **Toggle direction bug that this code prevents**: resolving `TOGGLE_*` *after*
  the optimistic flip made every standalone toggle do the opposite (tap Unlock →
  car re-locks). Resolution happens in `applyOptimistic` from the pre-flip snap,
  and the resolved (not raw) command is what gets relayed — so the phone also
  executes the direction the user actually saw (`:81-96`).
- **Optimistic revert on standalone failure** (`:144-149`): only `runStandalone`
  reverts. The phone-relay path does **not** revert locally on the watch — it
  relies on the phone echoing a corrected `PATH_STATE` / a `PATH_COMMAND_RESULT`
  that a live `WearViewModel` reverts off of.
- **`applyOptimistic` swallows lookup failures** (`:99-106` `runCatching`): if
  the store read throws, `resolved` stays the original (possibly a raw toggle) —
  an edge case where a subsequent standalone relay could misfire, but in
  practice the store read is reliable.
- **`.setUrgent()` on every publish** — climate especially, because a delayed
  batched sync would make the phone's mirrored slider visibly lag behind a
  finger the user is actively dragging (`:215-220`).
- **AI toggle / aurora toggle get their own paths**, not `PATH_LOCAL`, precisely
  so they can't race the uiScale echo on that shared path (`:294-295`).
- **`pullLatest` vs live `onDataChanged`**: `pullLatest` handles the same set of
  paths as `onDataChanged` **minus** the tile-refresh side effect — it's a
  cold-launch backfill so the UI isn't empty before the first live callback; it
  does **not** call `refreshWearGlanceables`.
- **`onDataChanged` wraps each item individually** in `runCatching` so a corrupt
  `PATH_STATE` write doesn't skip `PATH_SETTINGS`/`PATH_EXTRAS` in the same burst
  (`:64-93`).
- **`PATH_SETTINGS` triggers a tile refresh** because the Tile reads
  phone-synced theme colors (`resolveRoles()` in `BlooTileService`); without the
  push the Tile only picked up a theme change on its next freshness poll (up to
  10 min idle) (`:77-86`).
- **AI success is intentionally silent** (`onMessageReceived` `:143-159`) — the
  extras push already renders it on the AI card, so a success notification would
  be noise; only failures notify.
- **`WearRemote` used to silently no-op** when the phone was unreachable because
  the `startRemoteActivity` future was ignored; now a listener on
  `directExecutor()` calls `future.get()` and surfaces a failure notification
  (`WearRemote.kt:16-28`).
- **`WearImage` User-Agent is load-bearing**: OSM tile servers return a "blocked"
  403 tile to default/missing-UA clients per their usage policy; the explicit
  `Bloo-WearOS/0.1 (...)` UA is the documented fix (`WearImage.kt:10-15`). Caches
  are intentionally tiny (1% RAM / 2% disk) for a watch.
- **`persistAuth` is what enables standalone mode** — without a synced
  `SessionStore.Session` (Kia: sid as accessToken, rmtoken as refreshToken bound
  to deviceId), `WearCommandRunner.execute` can't authenticate on the watch.
