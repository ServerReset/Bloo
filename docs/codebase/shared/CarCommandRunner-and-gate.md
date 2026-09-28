# Deep Dive: `CarCommandRunner` + `BlueLinkGate`

**Unit:** `shared: CarCommandRunner + BlueLinkGate`
**Files:**
- `shared/src/main/java/com/bloo/bluelink/data/CarCommandRunner.kt`
- `shared/src/main/java/com/bloo/bluelink/data/BlueLinkGate.kt`

Both live in package `com.bloo.bluelink.data`.

---

## 1. Purpose

This unit is the **background/out-of-UI command execution engine** and the **process-wide serialization primitive** that protects the Blue Link / Kia Connect backend from overlapping requests.

- **`BlueLinkGate`** is a tiny singleton exposing one shared `Mutex` (`statusMutex`). Every vehicle-status fetch and every command dispatch across the *entire process* — the foreground ViewModel, the background `AlertWorker` and charge workers, and AutoLock — must run inside `statusMutex.withLock { }`. The backend rejects overlapping requests on one account with "a previous request is pending" (it 502s), so this mutex funnels all such traffic into a single in-flight-at-a-time FIFO queue. It is **non-reentrant and global**: the gate wraps leaf calls only, and a caller already holding it must not call a path that re-acquires it.

- **`CarCommandRunner`** is a stateless `object` that takes a `CarCommand` (the flat, serializable command shape the notification actions and background workers send) and executes it end-to-end against the real car backend: it looks up the target vehicle's current `VehicleSnapshot`, builds a brand-specific `VehicleRepository` and a `ClimateRequest`, dispatches the correct repository call for the command's `action` **inside the gate lock**, and folds the resulting confirmed state back into the on-disk `SnapshotStore`. It lives in `:shared` precisely so every bare-`Context` command path (notification action buttons, AutoLock, the climate auto-extend worker) shares exactly one implementation.

It also carries pure helpers — `resolveToggle`, `stateFor`, `withState`, `optimistic` — so callers can do instant optimistic UI without desyncing from `execute`'s toggle-direction logic, and a `refresh()` that re-fetches every car under the same lock.

---

## 2. Public surface

### `object BlueLinkGate`
- **`val statusMutex: Mutex`** — a single shared `kotlinx.coroutines.sync.Mutex`. Kotlin's `Mutex` queues suspended coroutines fairly (FIFO), so concurrent callers wait their turn rather than racing the API. This is the only member.

### `object CarCommandRunner`
- **`suspend fun execute(context: Context, command: CarCommand): CarCommandResult`** — runs one command under the gate and writes the confirmed snapshot back.
- **`fun resolveToggle(snap: VehicleSnapshot, action: String): String`** — maps a `TOGGLE_*` action to the concrete action to send, from the serialized snapshot.
- **`fun stateFor(snap, action): Boolean?` / `fun withState(snap, action, value): VehicleSnapshot`** — read and write the one boolean a toggle controls, so a failed command can restore exactly what was there before (rather than inventing `false` for a field the car has never reported).
- **`fun optimistic(snap, action): VehicleSnapshot`** — the instant on-device flip applied while the request is in flight.
- **`suspend fun refresh(context: Context, ...)`** — re-fetch every car's status under the gate and merge into the snapshot.

---

## 3. Invariants & gotchas

1. **Toggle direction is decided INSIDE the lock.** `execute` reads the snapshot after acquiring `statusMutex`; if that read happened before the lock, two overlapping `TOGGLE_*` commands would both observe the pre-toggle state and the second would invert the first.
2. **`optimistic()` writes an absolute value, not an inverse.** The old `inverse()` helper invented a definite `false` for a never-reported field; `stateFor`/`withState` replace it so a failed command restores the previous value.
3. **`statusMutex` is non-reentrant.** Do not call `CarCommandRunner.execute`/`refresh` while already holding it.
4. **Climate optimism is brand-gated** on `Brand.reportsClimateState`: Europe never reports `airCtrlOn`, so an optimistic climate flip there could never be confirmed and would stick wrong.
