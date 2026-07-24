# Deep Dive: `WearCommandRunner` + `BlueLinkGate`

**Unit:** `shared: WearCommandRunner + BlueLinkGate`
**Files:**
- `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\WearCommandRunner.kt` (172 lines)
- `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\BlueLinkGate.kt` (18 lines)

Both live in package `com.bloo.bluelink.data`.

---

## 1. Purpose

This unit is the **out-of-process command execution engine** and the **process-wide serialization primitive** that protects the Blue Link / Kia Connect backend from overlapping requests.

- **`BlueLinkGate`** (`BlueLinkGate.kt:11`) is a tiny singleton exposing one shared `Mutex` (`statusMutex`, line 17). Every vehicle-status fetch and every command dispatch across the *entire process* — foreground ViewModel, background `AlertWorker`, Quick-Settings tiles, the home-screen widget, and the watch relay/standalone paths — must run inside `statusMutex.withLock { }`. The backend rejects overlapping requests on one account with "a previous request is pending" (it 502s), so this mutex funnels all such traffic into a single in-flight-at-a-time FIFO queue.

- **`WearCommandRunner`** (`WearCommandRunner.kt:13`) is a stateless `object` that takes a `WearCommand` (the flat, serializable command shape the watch sends over the Wear Data Layer) and executes it end-to-end against the real car backend: it looks up the target vehicle's current `VehicleSnapshot`, builds a brand-specific `VehicleRepository` and a `ClimateRequest`, dispatches the correct repository call for the command's `action` **inside the gate lock**, and folds the resulting confirmed state back into the on-disk `SnapshotStore`. It exists in `:shared` precisely so the **phone** (relaying the watch's command) and the **watch** (running standalone on its own network connection) share exactly one implementation — the same stored-session pattern the Quick-Settings tiles use (`WearCommandRunner.kt:6-12`).

`WearCommandRunner` also carries three small pure helper functions — `resolveToggle`, `inverse`, `optimistic` — that let callers do instant optimistic UI without desyncing from `execute`'s toggle-direction logic.

---

## 2. Public surface

### `object BlueLinkGate` (`BlueLinkGate.kt:11`)

- **`val statusMutex: Mutex`** (`BlueLinkGate.kt:17`) — a single shared `kotlinx.coroutines.sync.Mutex`. Callers wrap any vehicle-status/fetch/command call in `statusMutex.withLock { ... }`. Kotlin's `Mutex` queues suspended coroutines fairly (FIFO), so concurrent callers wait their turn rather than racing the API. This is the *only* member.

### `object WearCommandRunner` (`WearCommandRunner.kt:13`)

- **`suspend fun execute(context: Context, command: WearCommand): WearCommandResult`** (`WearCommandRunner.kt:25`)
  The primary entry point. Executes one `WearCommand` end-to-end and returns a `WearCommandResult` (never throws — all exceptions during dispatch are caught and converted to a failed result). See §3 for full control flow.
  - Returns `WearCommandResult(vin, action, ok = false, message = "Car not found")` if no snapshot matches `command.vin` (`:28`).
  - Returns `WearCommandResult(..., message = "Unknown action")` for any `action` not in the `when` (`:84`).
  - On success returns `WearCommandResult(vin, action, ok = true)` (`:88`).
  - On any thrown exception returns `WearCommandResult(..., ok = false, message = e.message ?: "Command failed")` (`:91`).

- **`fun resolveToggle(snap: VehicleSnapshot, action: String): String`** (`WearCommandRunner.kt:104`)
  Pure. Resolves a `TOGGLE_*` verb into its explicit direction by reading `snap`. `TOGGLE_LOCK` → `UNLOCK` if `snap.locked == true` else `LOCK`; `TOGGLE_CLIMATE` → `CLIMATE_OFF`/`CLIMATE_ON`; `TOGGLE_CHARGE` → `CHARGE_OFF`/`CHARGE_ON`. Any non-toggle action passes through unchanged (`else -> action`, `:111`).
  **Contract (from the doc comment `:96-103`):** any caller that persists an `optimistic()` snapshot *before* the command runs MUST call `resolveToggle` first, because `execute` decides toggle direction by re-reading the store — an already-flipped snapshot would invert the command.

- **`fun inverse(action: String): String`** (`WearCommandRunner.kt:117`)
  Pure. Returns the verb whose `optimistic()` write undoes `action`'s — used to revert a failed command's optimistic flip. `LOCK↔UNLOCK`, `CLIMATE_ON↔CLIMATE_OFF`, `CHARGE_ON↔CHARGE_OFF`. Every `TOGGLE_*` maps to itself via the `else` branch (`:124`) — a second flip restores the original state.

- **`fun optimistic(snap: VehicleSnapshot, action: String): VehicleSnapshot`** (`WearCommandRunner.kt:128`)
  Pure. Returns the snapshot a command is expected to produce, for instant optimistic UI. `TOGGLE_*` verbs flip the relevant nullable field with a `?: false` default (e.g. `snap.copy(locked = !(snap.locked ?: false))`, `:129`); the explicit `LOCK`/`UNLOCK`/`CLIMATE_ON`/`CLIMATE_OFF`/`CHARGE_ON`/`CHARGE_OFF` verbs set the field to a fixed boolean. Any other action (including momentary ones like `FLASH_LIGHTS`, `SET_CHARGE_LIMITS`, `REFRESH`) returns `snap` unchanged (`:138`).

- **`suspend fun refresh(context: Context, vin: String, force: Boolean = true)`** (`WearCommandRunner.kt:147`)
  Re-fetches car status and folds it into snapshots. Blank `vin` → refresh *all* vehicles; otherwise only the matching one. `force = true` wakes the car for a live pull (on-demand); `force = false` reads the server's last-known status (cheap, for frequent background polls that won't drain the car's 12 V battery). See §3 for control flow. Returns `Unit`; failures per-vehicle are swallowed by `runCatching` (`:160`).

---

## 3. Internal structure & control flow

`WearCommandRunner` has **no private members** — everything is either the four public functions or inlined logic. The interesting flow is entirely inside `execute` and `refresh`.

### `execute(context, command)` step by step (`WearCommandRunner.kt:25-94`)

1. **Load store & snapshot** (`:26-28`): construct a `SnapshotStore(context)`; call `store.current()` (a one-shot DataStore read); `firstOrNull { it.vin == command.vin }`. If null → early return `"Car not found"`. Note this read happens **outside** the lock.
2. **Build the command `Vehicle`** (`:29`): `snap.toVehicle()` reconstructs a command-capable `Vehicle` from the snapshot's identity fields (vin, regId, name, model, generation, brandIndicator, isEv, odometer).
3. **Build the brand repository** (`:30-33`): `repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(context), CredentialStore(context))`. `Brand.fromIndicator` maps `"G"→GENESIS`, `"K"→KIA`, else `HYUNDAI` (`Brand.kt:66-69`). Constructed outside the lock.
4. **Build `ClimateRequest`** (`:34-43`): copies `tempF`, `defrost`, `durationMinutes`, `steeringWheelHeat` straight from the command; converts each seat int via `SeatLevel.fromApi(...)` (int → enum, unknown → `OFF`). Built unconditionally even for non-climate actions.
5. **Enter the gate lock** (`:49`): `BlueLinkGate.statusMutex.withLock { ... }`. Everything below is serialized process-wide. The comment (`:44-48`) explains *why*: this was the one command path that skipped the gate, so a resent watch command (e.g. after a slow BLE ack) could fire the same command twice concurrently or race a phone-UI command.
6. **`runCatching { }`** (`:50`) wraps the dispatch so any thrown exception is caught.
7. **Dispatch `when (command.action)`** (`:51-85`) — each branch calls the repo and computes the optimistic-but-now-confirmed `updated` snapshot:
   - `TOGGLE_LOCK` (`:52-54`): re-reads `snap.locked`; if `== true` calls `repo.unlock(v)` → `copy(locked=false)`, else `repo.lock(v)` → `copy(locked=true)`.
   - `LOCK`/`UNLOCK` (`:55-56`): direct call + copy.
   - `TOGGLE_CLIMATE` (`:57-67`): if `climateOn == true` → `repo.stopClimate(v)` + `copy(climateOn=false)`. Else — **guarded by `if (snap.isDriving) error("Can't start climate while driving")`** (`:65`) — `repo.startClimate(v, climate)` + `copy(climateOn=true)`.
   - `CLIMATE_ON` (`:68-71`): same driving guard, then `startClimate`.
   - `CLIMATE_OFF` (`:72`): `stopClimate` + `copy(climateOn=false)`.
   - `TOGGLE_CHARGE` (`:73-75`): if `charging == true` → `stopCharge` + `copy(charging=false)`, else `startCharge` + `copy(charging=true)`.
   - `CHARGE_ON`/`CHARGE_OFF` (`:76-77`): direct.
   - `SET_CHARGE_LIMITS` (`:78`): `repo.setChargeTargets(v, command.acLimit, command.dcLimit)`; returns `snap` unchanged (no stateful field to flip).
   - `FLASH_LIGHTS` / `HORN_AND_LIGHTS` (`:82-83`): momentary — call repo, return `snap` unchanged.
   - `else` (`:84`): `return@withLock WearCommandResult(..., "Unknown action")` — exits the lock block directly without persisting. (This includes `REFRESH`, `AI_SUMMARY`, `DRIVE_SYNC`, `WEATHER_DEVICE_LOCATION`, which are phone-side, not car commands.)
8. **Persist & log** (`:86-88`): `store.updateVehicle(updated)` writes the confirmed snapshot back; `AppLog.log("${command.action} → ${v.name}")`; return `WearCommandResult(vin, action, ok = true)`.
9. **`getOrElse { e -> ... }`** (`:89-92`): on any exception, logs `"Command failed (${action}): ${e.message}"` and returns a failed `WearCommandResult`. The `error(...)` driving-guard throws `IllegalStateException` whose message becomes the result's `message`.

### `refresh(context, vin, force)` step by step (`WearCommandRunner.kt:147-170`)

1. **Load store** (`:148`) and compute `targets` (`:149-151`): all vehicles if `vin.isBlank()`, else the single matching one. Read happens outside the lock.
2. **Enter gate lock** (`:152`).
3. **Per-brand repo cache** (`:158`): `val reposByBrand = mutableMapOf<Brand, VehicleRepository>()`. The comment (`:153-157`) explains the fix: constructing a fresh `KiaRepository` per vehicle discarded its account-wide vehicle-list cache, so "refresh all" on N Kia cars fired N redundant full-account list calls. Reusing one repo per brand collapses that to one.
4. **Loop `targets.forEach`** (`:159-168`), each wrapped in `runCatching` (`:160`) so one car's failure doesn't abort the rest:
   - `snap.toVehicle()`, `Brand.fromIndicator(...)`.
   - `reposByBrand.getOrPut(brand) { repositoryFor(brand, SessionStore(context), CredentialStore(context)) }` (`:163-165`).
   - `repo.status(v, refresh = force)?.let { store.updateVehicle(snap.merged(it)) }` (`:166`) — a null status (fetch returned nothing) leaves that snapshot untouched.

---

## 4. Data & types

**No data classes or enums are defined in either file.** They consume types defined elsewhere (all in `com.bloo.bluelink.data`). The relevant ones:

### `WearCommand` (`WearSync.kt:280-297`, `@Serializable`)
The flat wire command. Fields:
- `vin: String`, `action: String` (a `WearAction` constant string).
- `tempF: Int = DEFAULT_CLIMATE_TEMP_F` — climate target °F.
- `durationMinutes: Int = DEFAULT_CLIMATE_DURATION_MIN`.
- `defrost: Boolean = false`, `steeringWheelHeat: Boolean = false`.
- `seatFrontLeft/Right`, `seatRearLeft/Right: Int = 0` — **`SeatLevel.apiValue` ints, kept flat for the wire** (`:284-285`). `0` = off.
- `acLimit: Int = 80`, `dcLimit: Int = 90` — targets for `SET_CHARGE_LIMITS`.

### `WearCommandResult` (`WearSync.kt:300-306`, `@Serializable`)
The phone's reply: `vin: String`, `action: String`, `ok: Boolean`, `message: String? = null`.

### `object WearAction` (`WearSync.kt:243-277`) — string constants
Car-command verbs used by `execute`: `TOGGLE_LOCK="toggle_lock"`, `LOCK`, `UNLOCK`, `TOGGLE_CLIMATE`, `CLIMATE_ON`, `CLIMATE_OFF`, `TOGGLE_CHARGE`, `CHARGE_ON`, `CHARGE_OFF`, `FLASH_LIGHTS`, `HORN_AND_LIGHTS`, `SET_CHARGE_LIMITS`. Note `FLASH_LIGHTS`/`HORN_AND_LIGHTS` are **Hyundai/Genesis only** — Kia's US API has neither (`:254-255`). Phone-side (not handled by `execute`, fall through to "Unknown action"): `REFRESH`, `AI_SUMMARY`, `DRIVE_SYNC`, `WEATHER_DEVICE_LOCATION`.

### `SeatLevel` (`Models.kt:310-338`, `@Serializable` enum)
`(apiValue: Int, label: String)`: `HIGH_COOL(5)`, `MED_COOL(4)`, `LOW_COOL(3)`, `OFF(0)`, `LOW_HEAT(6)`, `MED_HEAT(7)`, `HIGH_HEAT(8)`. `isCool = apiValue in 3..5`, `isHeat = apiValue in 6..8`. **`fromApi(value: Int?)` returns the entry whose `apiValue == value`, else `OFF`** (`:336`) — this is what `execute` uses to decode the four seat ints. Encoding matches the domain fact: 0=off, 3-5=cool, 6-8=heat, crossing the wire as ints.

### `ClimateRequest` (`Models.kt:352-361`, `@Serializable`)
`tempF: Int`, `defrost: Boolean`, `durationMinutes: Int`, `steeringWheelHeat: Boolean = false`, and four `seat*: SeatLevel = SeatLevel.OFF`. `execute` builds this fresh from `WearCommand`, converting seat ints back to enums.

### `VehicleSnapshot` (`SnapshotStore.kt:21-75`) — the persisted per-car state
Key fields consumed here: `vin`, `name`, `brandIndicator: String = "H"` (drives `Brand.fromIndicator`), the three nullable command-state booleans `locked/charging/climateOn` (all `Boolean? = null`), and `speedMph: Double? = null`. Also `hasBattery: Boolean = isEv` (the user's manual powertrain override), used by `merged`.
- **`fun toVehicle()`** (`:65-74`) reconstructs a command-capable `Vehicle`.
- **`val VehicleSnapshot.isDriving`** (extension, `:80`): `(speedMph ?: 0.0) > 0.0` — the snapshot-based equivalent of `AppViewModel.isDriving()`; this is the `snap.isDriving` the climate guard reads.
- **`fun VehicleSnapshot.merged(status: VehicleStatus)`** (`:83-106`): folds a freshly fetched `VehicleStatus` into the snapshot. Uses `status.percentFor(hasBattery)`/`rangeMiFor(hasBattery)` (the override, **not** raw `isEv`), and coalesces `doorLock→locked`, `evStatus?.batteryCharge→charging`, `airCtrlOn→climateOn`, `engine→engineOn`, location `coord/speed`, `dateTime→updated`, and sets `fetchedAt = System.currentTimeMillis()`. This is what `refresh` calls.

### `Brand.fromIndicator` (`Brand.kt:66-69`)
`"G"→GENESIS`, `"K"→KIA`, else `HYUNDAI` (case-insensitive).

---

## 5. State & concurrency

- **`WearCommandRunner` holds no state** — it is a pure `object` with no fields. All state lives in the DataStore-backed `SnapshotStore` it constructs on each call. Multiple concurrent `execute`/`refresh` invocations each create their own `SnapshotStore`/`SessionStore`/`CredentialStore` instances (cheap wrappers around a process-wide DataStore).
- **The one concurrency primitive is `BlueLinkGate.statusMutex`** — a single shared `Mutex` (`BlueLinkGate.kt:17`). Both `execute` and `refresh` do their repository work inside `statusMutex.withLock { }`. This is FIFO-fair and process-wide, shared with `AlertWorker`, the foreground ViewModel, tiles, and the widget. It guarantees at most one status/command call in flight per process.
- **Dispatcher/scope:** neither function switches dispatchers. Both are plain `suspend` functions that run on whatever coroutine context the caller provides. The repository calls (`repo.status/lock/startClimate/...`) are themselves `suspend` and handle their own IO. Callers are responsible for launching these off the main thread.
- **Read-outside-lock, write-inside-lock:** in both functions the initial `store.current()` snapshot read happens *before* acquiring the lock (`:26-28`, `:148-151`); only the repo dispatch + `store.updateVehicle` happen inside. This means the snapshot `execute` reads for toggle-direction can in principle be slightly stale relative to a concurrent writer, but the lock ensures the *commands* don't overlap.
- **Recomposition:** no Compose here. Writes go through `SnapshotStore.updateVehicle`, which edits the DataStore; any UI collecting `SnapshotStore.payload` (`:147`) re-emits and recomposes, even cross-process.

---

## 6. Collaborators & data flow

**Inbound (who calls this unit):**
- The **phone's Wear relay** — receives a `WearCommand` over Wear Data Layer message path `PATH_COMMAND` (`/bloo/command`, `WearSync.kt:60`) and calls `WearCommandRunner.execute`, replying over `PATH_COMMAND_RESULT` (`/bloo/command_result`, `:66`) with the `WearCommandResult`.
- The **watch standalone path** — runs `execute` directly against its own connection.
- The **home-screen widget** and **Quick-Settings tiles** — call `execute` (commands) and `refresh` (status pulls). The `optimistic`/`resolveToggle`/`inverse` helpers exist for these surfaces to flip UI instantly and revert on failure.
- Background polling / sync — calls `refresh(context, vin, force=false)` for cheap last-known pulls.

**Outbound (what this unit calls):**
- `SnapshotStore(context).current()` / `.updateVehicle(...)` — the DataStore-backed per-car state blob (single JSON string under one preferences key, `SnapshotStore.kt:141`).
- `repositoryFor(brand, SessionStore, CredentialStore)` (`BlueLinkRepository.kt:36`) → a `VehicleRepository` (`interface` at `:9`) — `HyundaiRepository`/`GenesisRepository`/`KiaRepository`. Methods used: `status(v, refresh)` (`:12`), `lock`/`unlock` (`:17-18`), `startClimate(v, req)`/`stopClimate` (`:19-20`), `setChargeTargets(v, ac, dc)` (`:21`), `startCharge`/`stopCharge` (`:22-23`), `flashLights`/`hornAndLights` (`:28-29`, default no-op — Kia inherits the empty bodies).
- `SessionStore`/`CredentialStore` (per-brand, namespaced) — read by the repository for auth.
- `Brand.fromIndicator` (`Brand.kt:66`), `SeatLevel.fromApi` (`Models.kt:336`), `VehicleSnapshot.toVehicle/isDriving/merged`.
- `AppLog.log(...)` — the in-app log ring.
- `BlueLinkGate.statusMutex` — the shared gate.

**Data in:** a `WearCommand` (deserialized from the Wear Data Layer) + the current `SnapshotStore` contents + brand session/credentials.
**Data out:** a `WearCommandResult` (returned, and pushed back over `PATH_COMMAND_RESULT`), plus a mutated `VehicleSnapshot` written to DataStore (which re-emits to every observer), plus an `AppLog` line.

---

## 7. Invariants & assumptions

1. **The gate must be held for every real backend call.** `execute` and `refresh` both assume they hold `statusMutex` while calling any `repo.*` method; the whole point is that no other component is mid-request for the same account. Callers must NOT hold the lock themselves before calling `execute`/`refresh` (these acquire it internally) — doing so would deadlock (`Mutex` is non-reentrant).
2. **Toggle direction is decided by re-reading the store at execute time** (`:52-54`, `:57-58`, `:73-74`). This is the load-bearing contract behind `resolveToggle`'s doc comment (`:96-103`): if a caller optimistically flips the snapshot *before* calling `execute` without first resolving the toggle to an explicit verb, the command inverts.
3. **`snap.locked/climateOn/charging` are nullable and treated as false-ish.** A `null` in `TOGGLE_*` is read via `== true` (so null → treated as "not on", sends the ON/LOCK direction). `optimistic` uses `?: false`.
4. **`command.vin` must match a snapshot** already present in the store, else "Car not found". `execute` never fetches a fresh vehicle list — it operates only on already-persisted snapshots.
5. **`brandIndicator` must be `"H"`/`"G"`/`"K"`** (anything else silently means Hyundai). Default snapshot value is `"H"` (`SnapshotStore.kt:34`).
6. **Seat ints on the wire are valid `SeatLevel.apiValue`s** (0, 3-8); an out-of-range int decodes to `OFF` (`fromApi` fallback), silently.
7. **`isDriving` relies on `speedMph` being freshly populated** by a prior `merged()`. If `speedMph` is null/stale-zero, the driving guard passes (climate is allowed). The comment (`SnapshotStore.kt:43-50`) notes out-of-process runners only ever see the snapshot, never live location.
8. **`refresh` assumes one repo per brand can serve all that brand's vehicles** — true because the account-wide list call covers every car on the account (`:153-157`).
9. **`repo.flashLights`/`hornAndLights` are safe to call on Kia** — they're default no-ops on the interface (`BlueLinkRepository.kt:28-29`). But callers should gate on `Vehicle.supportsHornLights` per the `WearAction` doc (`WearSync.kt:254-255`); `execute` itself does *not* gate — it just calls the (possibly no-op) method.

---

## 8. Gotchas & sharp edges

- **`execute` never throws.** The outer `runCatching { }.getOrElse { }` (`:50-92`) converts every exception (including the deliberate `error("Can't start climate while driving")`) into a failed `WearCommandResult`. Callers must inspect `result.ok`, not rely on try/catch.
- **The driving guard uses `error()`, so its message flows to the user via the result.** `error("Can't start climate while driving")` (`:65`, `:69`) throws `IllegalStateException("Can't start climate while driving")`; `getOrElse` puts that string into `result.message`. This is intentional — it's a user-facing reason.
- **Unknown/phone-side actions early-return inside the lock without writing.** `REFRESH`, `AI_SUMMARY`, `DRIVE_SYNC`, `WEATHER_DEVICE_LOCATION` all hit the `else` branch (`:84`) → `"Unknown action"`. `execute` is only for *car* commands; the phone-side verbs are dispatched elsewhere before reaching here.
- **`ClimateRequest` is built even for non-climate actions** (`:34-43`). Wasteful but harmless; keeps the code flat.
- **The read-then-lock ordering means the toggle snapshot read is outside the lock** (`:26-28`), but the *command* still can't overlap. The window is: read snapshot → (another command completes & writes) → acquire lock → dispatch based on possibly-stale snapshot. In practice the phone/watch paths resolve toggles to explicit verbs first, sidestepping this.
- **`merged()` must use `hasBattery`, not `isEv`.** The comment at `SnapshotStore.kt:84-89` documents a real bug that was fixed: refreshing a PHEV the API misreports as gas would clobber `percent`/`rangeMi` with *fuel* data. Since `refresh` calls `merged`, any change to snapshot construction that drops the `hasBattery` override reintroduces this on the watch's refresh path.
- **`refresh` swallows per-vehicle failures** (`runCatching` with no `getOrElse`, `:160`). "Refresh all" is best-effort: a single failing car is silently skipped, others still update. No error surfaces to the caller.
- **The per-brand repo cache in `refresh` is the fix for N redundant Kia list calls** (`:153-157`). If a future refactor moves `repositoryFor` back inside the loop, Kia "refresh all" regresses to N full-account fetches.
- **`BlueLinkGate.statusMutex` is a global singleton `Mutex` — non-reentrant.** Any code path that acquires it and then transitively calls `execute`/`refresh` (which re-acquire) will deadlock. The gate must wrap leaf calls only.
- **`FLASH_LIGHTS`/`HORN_AND_LIGHTS` silently no-op on Kia** rather than erroring, because the interface default bodies are empty (`BlueLinkRepository.kt:28-29`). A watch that sends these to a Kia gets `ok = true` with nothing happening.
