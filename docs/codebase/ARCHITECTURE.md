# Bloo — Architecture (capstone)

> The system-level map. Per-file deep-dives live alongside this file under `docs/codebase/`; see [README.md](README.md) for the index. This document is hand-written from a full read of `:shared` plus the phone/watch command paths, and is cross-checked against the per-file docs.

## 1. What Bloo is

An unofficial Android app that remotely controls **real** Hyundai (Blue Link), Genesis (Genesis Connected) and Kia (Kia Connect) US vehicles over their live telematics APIs — lock/unlock, remote climate, charging, charge limits, horn/lights, GPS location, status, EV trips. It runs on phone, foldable, tablet, and **Wear OS** (a full watch mirror with tiles + complications), plus home-screen **widgets** and **Quick Settings tiles**. It is not on the Play Store; it self-updates from GitHub Releases.

There is **no mock/simulated path** — every call hits production OEM servers, so command correctness is safety-relevant.

## 2. Module layout

```
:shared    Domain core — API clients, models, repositories, session/credential
           stores, the phone<->watch wire protocol (WearSync), snapshot/status
           caches, shared formatting. Pure Kotlin + a little Android (DataStore,
           EncryptedSharedPreferences). Depended on by :app and :wear.
:uicommon  Shared Jetpack Compose widgets (sliders, segmented control, wiggle
           text, drop shadow, temp color, weather glyphs). Depended on by :app
           and :wear.
:app       Phone/tablet: the main Compose UI (AppViewModel + Screens.kt), the
           home-screen widget (Glance), Quick Settings tiles, WorkManager jobs
           (alerts/drive-sync/update), on-device AI (Gemini Nano), and the
           phone side of the watch bridge.
:wear      Wear OS app: its own ViewModel + Compose UI, ProtoLayout tiles,
           watch-face complications, and the watch side of the bridge. Can run
           STANDALONE (talks to the car backend directly) or RELAY through the
           phone.
```

Two backends, one shape: Hyundai and Genesis share `BlueLinkApi` (same request/path structure, different base URL + OAuth creds via `Brand`). Kia US is a completely different backend (`KiaUsaApi`, OTP-gated login, session `sid` + `vinkey`). Both are hidden behind the `VehicleRepository` interface so the UI layers don't care which brand a car is.

## 3. The layered call graph

```
                 ┌─────────────────────────────────────────────┐
  Phone UI       │ AppViewModel (StateFlow<UiState>)            │
  Watch UI       │ WearViewModel (StateFlow<WearUiState>)       │
  Widget/Tile    │ TileCommandRunner / WearCommandRunner        │  (out-of-process)
                 └───────────────┬─────────────────────────────┘
                                 │ all car calls funnel through
                                 ▼
                    BlueLinkGate.statusMutex  (process-wide serialization)
                                 │
                                 ▼
                     VehicleRepository (interface)
                    ┌────────────┴────────────┐
          BlueLinkRepository            KiaRepository
          (Hyundai/Genesis)             (Kia US, OTP + rmtoken re-auth)
                    │                          │
               BlueLinkApi                 KiaUsaApi
                    │                          │
                    ▼                          ▼
        api.telematics.hyundaiusa.com   api.owners.kia.com
```

Persistence stores (all `:shared`, all read from multiple processes):
- **SessionStore** (DataStore) — per-brand tokens + PIN + deviceId. Survives restart; source of "who's logged in".
- **CredentialStore** (EncryptedSharedPreferences, AES-256) — per-brand email/password/PIN for silent re-auth after token expiry.
- **SnapshotStore** (DataStore) — compact `VehicleSnapshot` per car (lock/charge/climate/percent/range/location). The **only** state the out-of-process surfaces (widget, QS tile, watch relay, complications) can see; they can't reach the in-memory ViewModel.
- **StatusCache** (DataStore) — last full `VehicleStatus`/`GeoLocation`/place-name per VIN, so the phone UI shows stale-but-useful data at cold start.
- **SettingsStore** (DataStore) — everything user-configurable (theme, units, per-car config, tiles, presets, Drive-sync wiring, dirty-key tracking).

## 4. The two invariants everything depends on

These are the load-bearing rules. Most of the confirmed bugs in `REVIEW.md` are a surface that forgot one of them.

### 4.1 Serialize every car request through `BlueLinkGate.statusMutex`
The OEM backends reject overlapping requests for the same account with `502 "a previous request is pending"`. So **every** status fetch and command — from the phone UI, the background `AlertWorker`, the QS tile, the widget worker, the watch (standalone) — must run inside `BlueLinkGate.statusMutex.withLock { }`. It's a single process-wide `Mutex` in `:shared`. A path that does car I/O outside this lock is a latent 502 / duplicate-command bug.

### 4.2 Gate climate-start on "is driving"
The car rejects remote climate-start while moving. The phone UI disables the Start button while driving; **every out-of-process start path** (QS tile, watch standalone, launcher shortcut) must apply the same `snap.isDriving` / `status.isDriving` gate before calling `startClimate`, or the command is silently rejected by the car with no explanation. `isDriving` = last-known `vehicleLocation.speed > 0`.

### 4.3 (corollary) Toggle direction must be decided from serialized state
`TOGGLE_LOCK/CLIMATE/CHARGE` decide direction by re-reading the `SnapshotStore`. If a caller optimistically flips the snapshot *before* the toggle re-reads it — or reads it outside the mutex — the toggle inverts. This is why optimistic writes and the direction read must be ordered carefully (see `WearCommandRunner.resolveToggle`'s own docstring).

## 5. Encodings you must not mix up

| Concept | Encoding |
|---|---|
| `plugType` (charge targets, `targetSOClist`) | **0 = DC fast, 1 = AC** |
| `EvStatus.batteryPlugin` (what's plugged in now) | **0 = unplugged, 1 = DC fast, 2 = AC** (different!) |
| `SeatLevel.apiValue` | 0 = off, 3/4/5 = low/med/high **cool**, 6/7/8 = low/med/high **heat** |
| Kia seat (`seatSettings`) | `heatVentType` 1 = heat, 2 = cool, 0 = off; + level/step |
| Temperature over the API | always °F **string**, even for metric users (convert at display) |
| Kia temp out of 62–82 °F | clamps to sentinel strings `"LOW"`/`"HIGH"` |
| `hasBattery` vs `isEv` | `hasBattery` = user's manual powertrain override (EV *or* PHEV); drives `percentFor`/`rangeMiFor`. `isEv` = raw API flag (pure EV only). |

## 6. End-to-end flows (the ones worth knowing cold)

### 6.1 Cold start (phone)
`MainActivity.onCreate` → `AppViewModel.init` launches: watch-mirror collectors, AI-support probe, update check, `StatusCache` restore (instant stale data), and cold-start auto-login (`store.loggedInBrands()` → build repos → `loadGarage()`). `loadGarageInner` fetches vehicles from every signed-in brand (each under the mutex), writes snapshots, loads per-car settings, picks the screen (Onboarding / CarSetup / Garage), then `ensureStatus` for the current car and prefetches the rest.

### 6.2 A remote command (phone)
`AppViewModel.lock(v)` → `runCommand(vin,"doors", …optimistic…) { repoFor(v).lock(v) }`. `runCommand`: mark pending → apply optimistic status patch + persist snapshot → `statusMutex.withLock { repoFor(v).lock(v) }` → on success confirm + republish snapshot to watch/widget/tiles + auto-AI; on failure surface message + schedule a corrective `refreshStatus`; always hold the control ≥ `MIN_COMMAND_LOCK_MS` to block double-taps. Repo adds one auth-refresh retry on 401/403.

### 6.3 A command from the watch
`WearViewModel.command(vin, action)` tries **relay first**: `WearComms.send` a `WearCommand` message to the phone. If relayed, apply optimistic local flip and return (no ack channel for the phone's actual execution). If no phone reachable, fall back to **standalone**: `statusMutex.withLock { block(v, repoFor(brand), status) }` — the block talks to the car directly using the session mirrored from the phone. Phone side: `WearPhoneService`/`WearListenerService` receives the message and runs `WearCommandRunner.execute` (which itself takes the mutex, gates driving, dispatches, and writes the snapshot).

### 6.4 The phone↔watch wire (`WearSync`)
JSON over the Wearable Data Layer. **DataItems** (phone→watch, sticky): `/bloo/state` (snapshots), `/bloo/auth` (sessions, for standalone), `/bloo/settings`, `/bloo/presets`, `/bloo/extras` (weather/photos/AI); **bidirectional**: `/bloo/climate` (live draft). **Messages** (transient): `/bloo/command`, `/bloo/sync_request`, and result channels (`/bloo/command_result`, `/bloo/sync_result`, `/bloo/ai_result`). Watch→phone toggles for AI/aurora/local prefs/pebble order have their own paths. Every decode is defensive (`runCatching` → default) because phone and watch update independently and may be schema-skewed.

### 6.5 Drive sync (settings backup/merge)
`SettingsStore.performDriveSync` (serialized by its own `driveSyncMutex`): download the file → if remote is newer, merge it in (protecting locally-dirty keys) → upload our settings with a fresh timestamp and verify the write. Field-level merge via a "dirty keys" set so an un-uploaded local edit isn't clobbered by an incoming remote. Device-local keys (the Drive URI itself, sync bookkeeping) are never exported/imported. Photos are downscaled and base64-embedded.

## 7. Concurrency model at a glance

- **`BlueLinkGate.statusMutex`** — the one process-wide car-request lock (§4.1).
- **`SettingsStore.driveSyncMutex`** — serializes Drive sync passes.
- **`WearPhoneService.extrasMutex`** — meant to serialize the extras read-modify-write (see bug H12: the write escapes it).
- **StateFlows** — `AppViewModel._state` / `WearViewModel._ui`, updated via `.update { }` CAS loops (so update lambdas must be pure + cheap; blocking I/O inside them is a bug — see M9).
- **DataStore** — every store's edits are transactional per-file; safe across processes.
- **WorkManager** — `AlertWorker` (periodic status→alerts), `DriveSyncWorker` (periodic sync), `UpdateCheckWorker`, `WidgetRefreshWorker`, `WidgetCommandWorker`, `TileCommandWorker`.

## 8. Where to look for X

| I want to… | Go to |
|---|---|
| understand a car command end-to-end | `AppViewModel.runCommand` → `VehicleRepository` → `BlueLinkApi`/`KiaUsaApi` |
| add/inspect an API endpoint | `shared/BlueLinkApi.md`, `shared/KiaUsaApi.md` |
| understand what the watch can see offline | `shared/snapshot-and-cache.md`, `shared/WearSync.md` |
| trace a widget/tile tap | `app/widget-workers-and-config.md`, `app/qs-tiles.md` → `TileCommandRunner`/`WidgetCommandWorker` |
| change theming/units | `app/activity-theme-misc.md` (Theme.kt), `shared/FormatUtils.md` |
| understand phone↔watch sync | `shared/WearSync.md`, `app/wear-bridge-phone-side.md`, `wear/comms-and-listener.md` |
| settings persistence / Drive backup | `app/SettingsStore-part1.md`, `app/SettingsStore-part2-drivesync.md` |

See [README.md](README.md) for the full index of all 37 per-file deep-dive docs.

---

## 9. Sharp edges that bite across the whole codebase

A distilled list of the non-obvious rules the per-file docs surfaced — the things that cause bugs when forgotten:

1. **`rdo/off` LOCKS, `rdo/on` UNLOCKS** (BlueLinkApi) — inverted from intuition.
2. **The "unlocked" state is the highlighted/filled one** on tiles/complications (red = `BlooColors.heat`) — inverse of intuition, but consistent across phone tile, wear tile, and complication.
3. **`statusMutex` is non-reentrant and global** — any code holding it that calls `WearCommandRunner.execute`/`refresh` (which re-acquire) deadlocks. The gate wraps *leaf* calls only.
4. **`SessionStore` is NOT encrypted** (plain DataStore) — tokens + service PIN sit in cleartext, even though the same PIN is AES-encrypted in `CredentialStore`.
5. **`editTracked` vs plain `edit`** in SettingsStore is load-bearing: `mergeSettingsJson`/`clearDirtyKeys` must use plain `edit` or Drive sync never converges.
6. **Appearance booleans are stored as `"true"`/`"false"` STRINGS**; notification/tile booleans use native boolean keys. Same-named string vs boolean keys are *different keys*.
7. **`useFahrenheit` is derived** (`unitSystem != "metric"`), never stored independently — a garbage unitSystem reads as Fahrenheit.
8. **Relay-path optimistic flips never revert** — the watch relay confirms only that the message reached the phone, not that the car command worked; failures arrive out-of-band and only trigger a corrective refresh.
9. **PendingIntent identity ignores extras** — widget/tile/complication/alert intents must encode a unique `data` URI (e.g. `bloo://widget/$id/$action`) or they collapse into one intent firing the last action.
10. **The 12 QS tiles / 4 wear tiles are hard-coded classes** (`BlooTile1..12` / `BlooTile1..4`), each needing a matching manifest entry — adding a slot means editing both.
11. **Phone and watch MUST share `applicationId` + the checked-in debug key** or the Wearable Data Layer silently unpairs.
12. **`CarAlerts.evaluate` skips door/running checks when `status == null`** — a failed poll must not reset the open/running timer.
13. **`reservChargeInfos.level(1)` = AC, `level(0)` = DC** — opposite index order to `batteryPlugin` (0=unplugged/1=DC/2=AC).
14. **The Gen5W trips gate** `brand != KIA && (generation.toIntOrNull() ?: 3) < 3` is duplicated at 3 sites and must stay in agreement.
15. **Draw-phase reads only**: pager offset and pull distance must be read inside `graphicsLayer{}`/`offset{}` lambdas, never in composition, or the whole car card recomposes every drag frame.
