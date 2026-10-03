# Bloo — Codebase Knowledge Base

A durable reference for the Bloo codebase. Start with **[ARCHITECTURE.md](ARCHITECTURE.md)** (the system map: modules, call graph, invariants, flows, concurrency), then drop into the per-file deep-dives below.

Each per-file doc follows a consistent structure: Purpose · Public surface · Internal structure · Data & types · State & concurrency · Collaborators & data flow · Invariants & assumptions · Gotchas & sharp edges.

> **Note:** the per-file deep-dives were written when the app still had a Wear OS companion, a home-screen widget, Quick Settings tiles, and the flip-phone cover-screen layout (`CompactGarage`, `CoverTile`, `CoverHero`, `coverScaled`, `isCompactCoverScreen`). Those were removed; where a deep-dive still describes any of them, treat it as historical. The top-level map in [ARCHITECTURE.md](ARCHITECTURE.md) reflects the current app.

> Two load-bearing invariants underpin everything (see ARCHITECTURE §4): **(1)** every car request runs inside `BlueLinkGate.statusMutex`; **(2)** climate-start is gated on `isDriving` on every path. Watch the encoding traps: `plugType` 0=DC/1=AC vs `batteryPlugin` 0=unplugged/1=DC/2=AC; `hasBattery` (user override) not raw `isEv` drives percent/range.

---

## `:shared` — domain core (API clients, models, repositories, stores)

| Doc | Covers |
|---|---|
| [shared/BlueLinkApi.md](shared/BlueLinkApi.md) | Hyundai/Genesis Blue Link US+Canada client — auth, vehicles, status, location, trips, and the full command set. Stateless; brand-parameterized. |
| [shared/KiaUsaApi.md](shared/KiaUsaApi.md) | Kia Connect US client — OTP-gated login, `sid`+`vinkey` sessions, JSON-tree status parsing, Kia's own seat/temp encodings. |
| [shared/Models.md](shared/Models.md) | Every API DTO + flattened domain type (`Vehicle`, `VehicleStatus`, `EvStatus`, `SeatLevel`, `ClimateRequest`…) and the status-interpretation helpers (`percentFor`, `rangeMiFor`, `targetForCurrentPlug`). |
| [shared/repositories-and-brand.md](shared/repositories-and-brand.md) | `VehicleRepository` interface, `BlueLinkRepository` + `KiaRepository` + `EuRepository` (auth-refresh retry, session gating), `repositoryFor()`, and the `Brand` enum (per-brand URLs/creds/links). |
| [shared/session-and-credentials.md](shared/session-and-credentials.md) | `SessionStore` (per-brand tokens/PIN/deviceId, DataStore) and `CredentialStore` (AES-256 EncryptedSharedPreferences, app-lock PIN), plus their legacy-migration schemes. |
| [shared/CarCommandRunner-and-gate.md](shared/CarCommandRunner-and-gate.md) | `BlueLinkGate` (the process-wide `statusMutex`) and the shared command-runner core. |
| [shared/snapshot-and-cache.md](shared/snapshot-and-cache.md) | `SnapshotStore` (`VehicleSnapshot`) and `StatusCache` (last full status for instant cold-start). |
| [shared/FormatUtils.md](shared/FormatUtils.md) | Shared formatting/units/smart-climate: `vehicleStateLabel`, `smartClimateTargetF`, temp/speed/distance formatters, the shared range/TTL constants. |
| [shared/updateapi-applog-colors.md](shared/updateapi-applog-colors.md) | `UpdateApi` (GitHub-Releases self-update + APK download), `AppLog` (in-memory ring-buffer log), `BlooColors` (shared ARGB constants). |

## `:app` — phone/foldable/tablet (UI, ViewModel, AutoLock, workers)

| Doc | Covers |
|---|---|
| [app/AppViewModel-part1-auth-garage.md](app/AppViewModel-part1-auth-garage.md) | `UiState`/`Screen`, init collectors, auth/login + Kia OTP, biometric app-lock, garage load. |
| [app/AppViewModel-part2-status-ai.md](app/AppViewModel-part2-status-ai.md) | Drive-sync bootstrap, `ensureStatus`/`loadStatus`, snapshot building, AI summaries, shortcut config. |
| [app/AppViewModel-part3-commands-settings.md](app/AppViewModel-part3-commands-settings.md) | `locate`, `runCommand` + all lock/climate/charge commands, settings setters, import/export/Drive sync, weather. |
| [app/SettingsStore-part1.md](app/SettingsStore-part1.md) | Prefs/appearance/notifications/per-car config accessors + the `appearance` Flow. |
| [app/SettingsStore-part2-drivesync.md](app/SettingsStore-part2-drivesync.md) | `performDriveSync`, `editTracked`/dirty-key tracking, import/export/merge JSON, embedded photos, climate presets, custom palettes. |
| [app/Screens-part1-root-login-onboarding.md](app/Screens-part1-root-login-onboarding.md) | `BlooApp` root, nav, login screen, onboarding + car-setup wizard. |
| [app/Screens-part2-garage-carousel.md](app/Screens-part2-garage-carousel.md) | Garage screen, car carousel/grid, hero tile, pebble-list plumbing. (The cover-screen sections are historical, see the Note above.) |
| [app/Screens-part3-pebbles.md](app/Screens-part3-pebbles.md) | The detail "pebble" composables: charge, fuel, climate, location/map, trips, diagnostics, info, weather. |
| [app/Screens-part4-settings-search.md](app/Screens-part4-settings-search.md) | Settings screen sections, AI search, and `parseVehicleCommand` (natural-language → car command). |
| [app/workers-and-update.md](app/workers-and-update.md) | `AlertWorker`, `MainToMainSyncWorker`, `UpdateCheckWorker`, `UpdateChecker`, `UpdateStore`. |
| [app/data-ai-weather-notifications.md](app/data-ai-weather-notifications.md) | `Ai` (Gemini Nano), `WeatherApi` (Open-Meteo), `Notifications` + `CarAlerts`, `ClimateSyncStore`, `AlertActionReceiver`. |
| [app/activity-theme-misc.md](app/activity-theme-misc.md) | `MainActivity`, `Shortcuts`, `Theme` (M3 color/vibrancy), `Haptics`, `GlassChrome`, `Fireworks`, `BiometricAuth`. |

## `:uicommon` & build

| Doc | Covers |
|---|---|
| [uicommon/components.md](uicommon/components.md) | Shared Compose: `AnimatedSlider`, `MorphSegmented`, `MorphButtonCore`, `WiggleText`, `DropShadow`, `TempColor`, `WeatherUtils`, `BlooColors`, `BlooMotion`. |
| [build-and-manifests.md](build-and-manifests.md) | Module/Gradle structure, SDK levels, signing, every manifest component + permission, the CI build+release pipeline. |

---

*Docs reflect source on branch `claude/great-faraday-QuX3x`. If a line reference has drifted, re-anchor by the named symbol.*
