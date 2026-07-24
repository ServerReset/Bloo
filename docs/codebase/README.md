# Bloo — Codebase Knowledge Base

A durable, micrometer-level reference for the entire Bloo codebase. Start with **[ARCHITECTURE.md](ARCHITECTURE.md)** (the system map: modules, call graph, invariants, flows, concurrency), then drop into the per-file deep-dives below.

Each per-file doc follows the same 8-part structure: Purpose · Public surface · Internal structure · Data & types · State & concurrency · Collaborators & data flow · Invariants & assumptions · Gotchas & sharp edges.

Generated from a full read of every one of the 91 Kotlin files (giant files sliced). Companion documents in the repo root: **[REVIEW.md](../../REVIEW.md)** (verified bug review) and **[FIX_PROMPT.md](../../FIX_PROMPT.md)** (actionable fix plan).

> Two load-bearing invariants underpin everything (see ARCHITECTURE §4): **(1)** every car request runs inside `BlueLinkGate.statusMutex`; **(2)** climate-start is gated on `isDriving` on every out-of-process path. Watch the encoding traps: `plugType` 0=DC/1=AC vs `batteryPlugin` 0=unplugged/1=DC/2=AC; `hasBattery` (user override) not raw `isEv` drives percent/range.

---

## `:shared` — domain core (API clients, models, repositories, stores, wire protocol)

| Doc | Covers |
|---|---|
| [shared/BlueLinkApi.md](shared/BlueLinkApi.md) | Hyundai/Genesis Blue Link US client — auth, vehicles, status, location, trips, and the full command set. Stateless; brand-parameterized. |
| [shared/KiaUsaApi.md](shared/KiaUsaApi.md) | Kia Connect US client — OTP-gated login, `sid`+`vinkey` sessions, JSON-tree status parsing, Kia's own seat/temp encodings. |
| [shared/Models.md](shared/Models.md) | Every API DTO + flattened domain type (`Vehicle`, `VehicleStatus`, `EvStatus`, `SeatLevel`, `ClimateRequest`…) and the status-interpretation helpers (`percentFor`, `rangeMiFor`, `targetForCurrentPlug`). |
| [shared/repositories-and-brand.md](shared/repositories-and-brand.md) | `VehicleRepository` interface, `BlueLinkRepository` + `KiaRepository` (auth-refresh retry, session gating), `repositoryFor()`, and the `Brand` enum (per-brand URLs/creds/links). |
| [shared/session-and-credentials.md](shared/session-and-credentials.md) | `SessionStore` (per-brand tokens/PIN/deviceId, DataStore) and `CredentialStore` (AES-256 EncryptedSharedPreferences), plus their legacy-migration schemes. |
| [shared/WearSync.md](shared/WearSync.md) | The phone↔watch wire protocol: every Data Layer path (state/auth/settings/presets/climate/extras + message/result paths), all payload DTOs, defensive encode/decode. |
| [shared/WearCommandRunner-and-gate.md](shared/WearCommandRunner-and-gate.md) | `WearCommandRunner` (shared command execution for watch/tile/widget → snapshot) and `BlueLinkGate` (the process-wide `statusMutex`). |
| [shared/snapshot-and-cache.md](shared/snapshot-and-cache.md) | `SnapshotStore` (`VehicleSnapshot` — the only state out-of-process surfaces see) and `StatusCache` (last full status for instant cold-start). |
| [shared/FormatUtils.md](shared/FormatUtils.md) | Shared formatting/units/smart-climate: `vehicleStateLabel`, `smartClimateTargetF`, temp/speed/distance formatters, the shared range/TTL constants. |
| [shared/updateapi-applog-colors.md](shared/updateapi-applog-colors.md) | `UpdateApi` (GitHub-Releases self-update + APK download), `AppLog` (in-memory ring-buffer log), `BlooColors` (shared ARGB constants). |

## `:app` — phone/tablet (UI, widgets, tiles, workers, bridge)

| Doc | Covers |
|---|---|
| [app/AppViewModel-part1-auth-garage.md](app/AppViewModel-part1-auth-garage.md) | `UiState`/`Screen`, init collectors, auth/login + Kia OTP, biometric lock, garage load. |
| [app/AppViewModel-part2-status-ai.md](app/AppViewModel-part2-status-ai.md) | Drive-sync bootstrap, `ensureStatus`/`loadStatus`, snapshot building, AI summaries, tile/shortcut config. |
| [app/AppViewModel-part3-commands-settings.md](app/AppViewModel-part3-commands-settings.md) | `locate`, `runCommand` + all lock/climate/charge commands, settings setters, import/export/Drive sync, weather. |
| [app/SettingsStore-part1.md](app/SettingsStore-part1.md) | Prefs/appearance/notifications/tiles/per-car config accessors + the `appearance` Flow. |
| [app/SettingsStore-part2-drivesync.md](app/SettingsStore-part2-drivesync.md) | `performDriveSync`, `editTracked`/dirty-key tracking, import/export/merge JSON, embedded photos, climate presets, custom palettes. |
| [app/Screens-part1-root-login-onboarding.md](app/Screens-part1-root-login-onboarding.md) | `BlooApp` root, nav, login screen, onboarding + car-setup wizard. |
| [app/Screens-part2-garage-carousel.md](app/Screens-part2-garage-carousel.md) | Garage screen, car carousel/grid, hero tile, compact/cover-screen tiles, pebble-list plumbing. |
| [app/Screens-part3-pebbles.md](app/Screens-part3-pebbles.md) | The detail "pebble" composables: charge, fuel, climate, location/map, trips, diagnostics, info, weather. |
| [app/Screens-part4-settings-search.md](app/Screens-part4-settings-search.md) | Settings screen sections, AI search, and `parseVehicleCommand` (natural-language → car command). **Home of two HIGH bugs — see its Gotchas.** |
| [app/widget-BlooWidget.md](app/widget-BlooWidget.md) | The Glance home-screen widget: tier dispatcher, each tile tier, blur cache, color/background resolution. |
| [app/widget-workers-and-config.md](app/widget-workers-and-config.md) | `WidgetCommandWorker`, `WidgetRefreshWorker`, `WidgetAction`, config/auth activities, receiver. |
| [app/qs-tiles.md](app/qs-tiles.md) | Quick Settings tiles: `BlooTileService`, `TileActionActivity`, `TileCommandWorker`, and `TileCommandRunner`. |
| [app/wear-bridge-phone-side.md](app/wear-bridge-phone-side.md) | `WearBridge` (publish to watch) + `WearPhoneService`/listener (receive watch commands, run them). |
| [app/workers-and-update.md](app/workers-and-update.md) | `AlertWorker`, `DriveSyncWorker`, `UpdateCheckWorker`, `UpdateChecker`, `UpdateStore`. |
| [app/data-ai-weather-notifications.md](app/data-ai-weather-notifications.md) | `Ai` (Gemini Nano), `WeatherApi` (Open-Meteo), `Notifications` + `CarAlerts`, `ClimateSyncStore`, `AlertActionReceiver`. |
| [app/activity-theme-misc.md](app/activity-theme-misc.md) | `MainActivity`, `Shortcuts`, `Theme` (M3 color/vibrancy + `resolveWidgetAccent`), `Haptics`, `GlassChrome`, `Fireworks`, `BiometricAuth`. |

## `:wear` — Wear OS (app, tiles, complications)

| Doc | Covers |
|---|---|
| [wear/WearViewModel-part1.md](wear/WearViewModel-part1.md) | UI state, init collectors wiring the Data Layer stores, bootstrap, garage load, login/PIN. |
| [wear/WearViewModel-part2-commands.md](wear/WearViewModel-part2-commands.md) | `command()` relay-vs-standalone dispatch, toggle/preset/smart climate, `toWearCommand`, `buildCarView`, update check. |
| [wear/HomeScreen-part1.md](wear/HomeScreen-part1.md) | Watch home structure + first set of cards. |
| [wear/HomeScreen-part2.md](wear/HomeScreen-part2.md) | Weather/info/diagnostics cards, action buttons, horn/lights, trips. |
| [wear/tile-BlooTileService.md](wear/tile-BlooTileService.md) | The ProtoLayout watch tile: layout, tap handling, command relay. |
| [wear/comms-and-listener.md](wear/comms-and-listener.md) | `WearComms` (send/relay/requestSync), `WearRemote`, `WearListenerService`, `WearStateWriter`, `WearImage`, `WearNotifications`. |
| [wear/stores-events-activity.md](wear/stores-events-activity.md) | Watch-local + synced DataStores, the AI/command/sync event buses, `MainActivity`. |
| [wear/ui-screens.md](wear/ui-screens.md) | `Components`, Login/PinLock/Settings/Trips screens, `WatchApp` root, wear haptics/theme. |
| [wear/complications.md](wear/complications.md) | All 8 watch-face complications (charge/climate/lock/toggle-state) + their car store, config, link, tap receiver. |

## `:uicommon` & build

| Doc | Covers |
|---|---|
| [uicommon/components.md](uicommon/components.md) | Shared Compose: `AnimatedSlider`, `MorphSegmented`, `WiggleText`, `DropShadow`, `TempColor`, `WeatherUtils`, `BlooColors`, `BlooMotion`. |
| [build-and-manifests.md](build-and-manifests.md) | Module/Gradle structure, SDK levels, signing, every manifest component + permission, widget/tile/complication registrations, the CI build+release pipeline. |

---

*Docs reflect source at commit `b4c4831` (branch `claude/great-faraday-QuX3x`). If a line reference has drifted, re-anchor by the named symbol.*
