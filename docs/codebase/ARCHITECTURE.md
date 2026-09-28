# Bloo — Architecture (capstone)

> The system-level map. Per-file deep-dives live alongside this file under `docs/codebase/`; see [README.md](README.md) for the index. Hand-written from a full read of `:shared` and the phone UI.

## 1. What Bloo is

An unofficial Android app that remotely controls **real** Hyundai, Genesis and Kia vehicles over their live connected-car APIs — lock/unlock, remote climate, charging and charge limits, GPS location, status, EV trips, weather — plus an on-device AI layer (Gemini Nano summaries and natural-language commands) and a walk-away **AutoLock**. It runs on phone, foldable (including flip-phone cover screens) and tablet. It is not on the Play Store; it self-updates from GitHub Releases.

There is **no mock/simulated path** — every call hits production OEM servers, so command correctness is safety-relevant.

## 2. Module layout

```
:shared          Domain core — API clients, models, repositories, session/credential/
                 snapshot/status stores, shared formatting. Pure Kotlin + a little Android
                 (DataStore, EncryptedSharedPreferences).
:uicommon        Foundation-only shared Jetpack Compose components (morph button core,
                 segmented control, slider, text animations, drop shadow). No Material, no
                 app state — colors/sounds are passed in as parameters.
:app             Phone/foldable/tablet UI (Compose + Material 3 Expressive), the ViewModel,
                 WorkManager jobs (alerts/drive-sync/update), notifications, and AutoLock.
:hidden-api-stub Compile-only stubs for the reflection the Shizuku silent-install path uses.
```

Three backends, one shape: US and Canada Hyundai/Genesis share `BlueLinkApi` (same request/path structure, different base URL + creds via `Brand`); Europe Hyundai uses `EuRepository`; Kia US is a different backend (`KiaUsaApi`, OTP-gated login, `sid` + `vinkey` session). All are hidden behind the `VehicleRepository` interface so the UI does not care which brand a car is.

## 3. The layered call graph

```
                 ┌───────────────────────────────────────────┐
  Phone UI       │ AppViewModel (StateFlow<UiState>)          │
                 └───────────────┬───────────────────────────┘
                                 │ all car calls funnel through
                                 ▼
                    BlueLinkGate.statusMutex  (process-wide serialization)
                                 │
                                 ▼
                     VehicleRepository (interface)
               ┌─────────────────┼──────────────────┐
     BlueLinkRepository       EuRepository       KiaRepository
     (Hyundai/Genesis US+CA)  (Hyundai EU)       (Kia US)
               │                 │                  │
          BlueLinkApi         EuApi            KiaUsaApi
```

Persistence stores (all `:shared`):
- **SessionStore** (DataStore) — per-brand tokens + PIN + deviceId. Survives restart; source of "who's logged in". **Not encrypted.**
- **CredentialStore** (EncryptedSharedPreferences, AES-256) — per-brand email/password/PIN for silent re-auth after token expiry, plus the app-lock PIN record and lockout state.
- **SnapshotStore** (DataStore) — compact `VehicleSnapshot` per car (lock/charge/climate/percent/range/location). Fed to the home-screen surfaces and the background workers.
- **StatusCache** (DataStore) — last full `VehicleStatus`/`GeoLocation`/place-name per VIN, so the UI shows stale-but-useful data at cold start.
- **SettingsStore** (DataStore) — everything user-configurable (theme, units, per-car config, presets, Drive-sync wiring, dirty-key tracking).

## 4. The load-bearing invariants

Nearly every real bug found in this codebase has been a surface that forgot one of these.

### 4.1 Serialize every car request through `BlueLinkGate.statusMutex`
The OEM backends reject overlapping requests for the same account with `502 "a previous request is pending"`. So **every** status fetch and command — phone UI, the background `AlertWorker`, AutoLock's own evaluations — must run inside `BlueLinkGate.statusMutex.withLock { }`. It is a single process-wide `Mutex` in `:shared`. A path that does car I/O outside this lock is a latent 502 / duplicate-command bug.

### 4.2 Gate climate-start on "is driving"
The car rejects remote climate-start while moving. The phone UI disables the Start button while driving, and every other start path (launcher shortcut, AutoLock) must apply the same `isDriving` gate before calling `startClimate`, or the command is silently rejected. `isDriving` = last-known `vehicleLocation.speed > 0`.

### 4.3 Toggle direction is decided from serialized state
`TOGGLE_LOCK/CLIMATE/CHARGE` decide direction by re-reading the `SnapshotStore`. If a caller optimistically flips the snapshot *before* the toggle re-reads it — or reads it outside the mutex — the toggle inverts. Optimistic writes and the direction read must be ordered carefully.

### 4.4 AutoLock never crashes the app
AutoLock is a foreground service plus a manifest Bluetooth receiver plus an alarm fallback. `startForeground` can throw `SecurityException`; it is always guarded. The pending "walk-away" record is written on both the real and debug paths (shared `AutoLockTrigger`), so the alarm fallback can never be armed as a no-op.

## 5. Encodings you must not mix up

| Concept | Encoding |
|---|---|
| `plugType` (charge targets, `targetSOClist`) | **0 = DC fast, 1 = AC** |
| `EvStatus.batteryPlugin` (what's plugged in now) | **0 = unplugged, 1 = DC fast, 2 = AC** (different!) |
| `SeatLevel.apiValue` | 0 = off, 3/4/5 = low/med/high **cool**, 6/7/8 = low/med/high **heat** |
| Kia seat (`seatSettings`) | `heatVentType` 1 = heat, 2 = cool, 0 = off; + level/step |
| Temperature over the API | always °F **string**, even for metric users (convert at display) |
| `hasBattery` vs `isEv` | `hasBattery` = user's manual powertrain override (EV *or* PHEV); drives `percentFor`/`rangeMiFor`. `isEv` = raw API flag (pure EV only). |

## 6. End-to-end flows

### 6.1 Cold start
`MainActivity.onCreate` → construct `AppViewModel` (before `setContent`, so its init block is off the first-frame path) → `AppViewModel.init` launches: AI-support probe, update check, `StatusCache` restore (instant stale data), Drive-sync bootstrap, and cold-start auto-login (`SessionStore.loggedInBrands()` → build repos + load credentials in parallel → `loadGarage()`). The **lock decision runs first** (it needs only the appearance and the PIN record), and the garage load is deferred behind the lock screen. `loadGarageInner` publishes the **cached garage from `SnapshotStore` before the network round trip**, then fetches vehicles from every signed-in brand (each under the mutex), loads per-car settings, and picks the screen (Onboarding / CarSetup / Garage). The warm-up thread in `BlooApplication` pre-loads every DataStore, the credential crypto, and OkHttp's class graph off the critical path.

### 6.2 A remote command
`AppViewModel.lock(v)` → `runCommand(vin,"doors", …optimistic…) { repoFor(v).lock(v) }`. `runCommand`: mark pending → apply optimistic status patch + persist snapshot → `statusMutex.withLock { repoFor(v).lock(v) }` → on success confirm and auto-summarize; on failure surface a message + schedule a corrective `refreshStatus`; always hold the control for at least `MIN_COMMAND_LOCK_MS` to block double-taps. Repos add one auth-refresh retry on 401/403.

### 6.3 AutoLock (walk-away auto-lock)
`AutoLockService` (foreground) watches for the phone disconnecting from the car over Bluetooth and for driving-activity transitions. On disconnect it records a pending walk-away and arms an alarm at a deadline; a confirmed walk (activity recognition) within the grace window locks immediately, and the alarm is the fallback if confirmation never arrives. It runs in **dry run** unless the user opts in, and can play the bundled lock sound. All evaluation happens off the UI thread and inside the shared gate.

### 6.4 Drive sync (settings backup/merge)
`SettingsStore.performDriveSync` (serialized by its own `driveSyncMutex`): download the file → if the remote is newer, merge it in (protecting locally-dirty keys) → upload our settings with a fresh timestamp and verify the write. Field-level merge is driven by a **dirty-keys** set so an un-uploaded local edit is not clobbered by an incoming remote. Device-local keys (the Drive URI, sync bookkeeping) are never exported/imported. Photos are downscaled and base64-embedded.

## 7. Concurrency model at a glance

- **`BlueLinkGate.statusMutex`** — the one process-wide car-request lock (§4.1).
- **`SettingsStore.driveSyncMutex`** — serializes Drive sync passes.
- **StateFlows** — `AppViewModel._state`, updated via `.update { }` CAS loops, so update lambdas must be pure + cheap; blocking I/O inside them is a bug. `currentIndex` is its own flow so finishing a swipe does not invalidate the car pages.
- **DataStore** — every store's edits are transactional per-file.
- **WorkManager** — `AlertWorker` (periodic status→alerts), `MainToMainSyncWorker` (periodic Drive sync), `UpdateCheckWorker`.
- **Startup warm-up thread** — `BlooApplication` loads stores/crypto/OkHttp/DataStores off the main thread.

## 8. Where to look for X

| I want to… | Go to |
|---|---|
| understand a car command end-to-end | `AppViewModel.runCommand` → `VehicleRepository` → `BlueLinkApi`/`KiaUsaApi` |
| add/inspect an API endpoint | `shared/BlueLinkApi.md`, `shared/KiaUsaApi.md` |
| change theming/units | `app/activity-theme-misc.md` (Theme.kt), `shared/FormatUtils.md` |
| settings persistence / Drive backup | `app/SettingsStore-part1.md`, `app/SettingsStore-part2-drivesync.md` |
| the UI shell / screens | `app/Screens-part1-root-login-onboarding.md` … `part4-settings-search.md` |
| the button/pebble design system | `uicommon/components.md`, `app/Screens-part3-pebbles.md` |

See [README.md](README.md) for the full index of per-file deep-dive docs.

---

## 9. Sharp edges that bite across the whole codebase

1. **`rdo/off` LOCKS, `rdo/on` UNLOCKS** (BlueLinkApi) — inverted from intuition.
2. **The "unlocked" state is the highlighted/filled one** in the UI (red = `BlooColors.heat`) — inverse of intuition.
3. **`statusMutex` is non-reentrant and global** — the gate wraps *leaf* calls only; a caller already holding it must not call a path that re-acquires it.
4. **`SessionStore` is NOT encrypted** (plain DataStore) — tokens + service PIN sit in cleartext, while the same PIN is AES-encrypted in `CredentialStore`.
5. **`editTracked` vs plain `edit`** in SettingsStore is load-bearing: `mergeSettingsJson`/`clearDirtyKeys` must use plain `edit` or Drive sync never converges.
6. **Appearance booleans are stored as `"true"`/`"false"` STRINGS**; notification booleans use native boolean keys. Same-named string vs boolean keys are *different keys*.
7. **`useFahrenheit` is derived** (`unitSystem != "metric"`), never stored independently.
8. **`reservChargeInfos.level(1)` = AC, `level(0)` = DC** — opposite index order to `batteryPlugin`.
9. **The Gen5W trips gate** `brand != KIA && (generation.toIntOrNull() ?: 3) < 3` must stay in agreement wherever it appears.
10. **Draw-phase reads only**: pager offset and pull distance must be read inside `graphicsLayer{}`/`offset{}` lambdas, never in composition, or the whole car card recomposes every drag frame.
11. **Pager pages are keyed by raw virtual index**, never the modulo real item — real-index keying crashed ("Key already used").
12. **`EncryptedSharedPreferences` re-decrypts every read** — the credential stores cache the decrypted account list and the warm-up thread pre-reads every DataStore.
