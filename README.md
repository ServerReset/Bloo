# Bloo

A third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia**
vehicles through their US telematics services. Built with Jetpack Compose and
Material 3 Expressive for both phone and Wear OS.

No simulated data — every screen talks to live servers.

## Supported brands

| Brand   | Service             | Login             |
|---------|---------------------|-------------------|
| Hyundai | Blue Link           | Email + PIN       |
| Genesis | Genesis Connected   | Email + PIN       |
| Kia     | Kia Connect         | OTP (phone only)  |

Multiple brands can be signed in at the same time. Credentials are stored
encrypted on-device and never logged.

## Features

### Phone app
- **Lock / Unlock** — all brands, all powertrains
- **Remote climate** — temperature, defrost, steering wheel heat, seat heat/cooling, presets
- **Charging** — start/stop, set AC and DC limits independently (EV/PHEV)
- **GPS location** — live map with one-tap open-in-maps
- **Vehicle status** — doors, windows, trunk, hood, tyres, 12V battery, fuel, charge, range
- **Trip history** — distance, time, energy and regen breakdown (Hyundai/Genesis Gen3+ EVs)
- **Weather** — current conditions at car and home
- **On-device AI** — Gemini Nano plain-language status summary and natural-language search
- **Quick Settings tiles** — lock, unlock, start/stop climate from the notification shade
- **App-icon shortcuts** — long-press the app icon for one-tap commands

#### Home-screen widget (Jetpack Glance)

Auto-adapting widget with 7 size tiers that match your home-screen grid slot:

| Tier        | Triggers                          | Shows                                |
|-------------|-----------------------------------|--------------------------------------|
| Tiny        | `w < 80dp && h < 80dp`           | Percent + coloured state dot         |
| Compact     | `h < 65dp`                       | Name + percent + inline action icons |
| NarrowTall  | portrait, `w < 155dp`            | Name + percent + bar + state + buttons |
| WideRow     | `w > h × 2`                      | Name + percent + range + state + buttons |
| Square      | aspect ratio 0.62–1.45           | Centred ring gauge + name + actions  |
| Portrait    | `h > w × 1.2`                    | Full photo/map + scrim + state + actions |
| Landscape   | default                          | Full photo/map + scrim + state + actions |

Four manual style overrides available in widget settings:
- **Auto** — picks the best tier automatically (default)
- **Stats** — grid of up to 4 user-choosen metrics (battery/range/lock/climate)
- **Photo** — full-bleed car photo with name/percent overlay + action buttons
- **Map** — last known location map (requires a Location action to have run)

Widget buttons support **Lock / Unlock / Climate / Charge / Refresh / Location / Open app**,
each gated behind biometric authentication (optional, per-widget setting).

### Wear OS app
- **All phone features on your watch** — lock, climate, charge, location, weather, diagnostics
- **Wear OS Tiles** — up to 4 poolable Tiles (one per car) with charge arc, action chips
- **Watch-face complications** — charge %/arc, lock state, climate state, all tap-to-toggle
- **Phone-paired or standalone** — commands relay through the phone when nearby, or run directly
- **Synced theme** — colour scheme, temp unit, font scale mirror the phone app
- **Crown/bezel scroll** — rotary input for tile-by-tile navigation
- **Smart climate** — reads weather at the car and auto-picks a comfortable setpoint
- **Climate presets** — save, rename, reorder, apply named presets from the watch
- **Tile reorder** — drag-and-drop pebble groups, order synced per car
- **Haptic feedback** — distinct haptics for taps, toggles, ticks, errors
- **Offline-friendly** — all data cached locally, tiles render from disk

#### Wear OS Tiles

4 poolable glanceable tiles (`BlooTile1`–`BlooTile4`), each configurable to show a
specific car or follow the selected car. Each tile renders:
- Circular charge/fuel arc with progress indicator
- Car name
- Large percentage
- Status line (Driving / Charging / Climate on / Locked)
- Up to 2 user-configurable action chips (Lock / Climate / Charge)

Tiles refresh every 10 minutes, or every 90 seconds while charging.

#### Watch-face complications

| Complication         | Types                         | Tap action          |
|----------------------|-------------------------------|----------------------|
| **ChargeComplication** | SHORT_TEXT, RANGED_VALUE     | Opens the app        |
| **LockComplication**   | SHORT_TEXT, MONOCHROMATIC_IMAGE | Toggles lock      |
| **ClimateComplication** | SHORT_TEXT, MONOCHROMATIC_IMAGE | Toggles climate   |

Each complication is configurable per watch-face slot (choose which car it
shows) via the watch-face complication picker. Tap actions on Lock/Climate
run the command optimistically and update immediately.

Complications refresh every 5 minutes by default, plus on-demand after any
command via `ComplicationLink.requestUpdate()`.

## Download

CI builds debug APKs on every push.

- **Phone:** grab the `bloo-debug-apk` artifact from the latest [Actions run](../../actions)
- **Watch:** grab the `bloo-wear-debug-apk` artifact — or `wear-apks`

The phone and watch apps must share the same `applicationId` and signing key
for the Wearable Data Layer to pair them. Debug builds use a checked-in debug
keystore so they work out of the box.

## Building locally

```bash
# Phone APK
./gradlew :app:assembleDebug

# Watch APK
./gradlew :wear:assembleDebug

# Both
./gradlew assembleDebug
```

Output:
- Phone: `app/build/outputs/apk/debug/Bloo-0.1-debug.apk`
- Watch: `wear/build/outputs/apk/debug/Bloo-Wear.apk`

Requires Android Studio Meerkat or newer (AGP 9.1, Kotlin 2.2.20, compileSdk 36).

### Installing the watch APK

```bash
adb -s <watch-serial> install wear/build/outputs/apk/debug/Bloo-Wear.apk
```

For an emulator: create an AVD with a Wear OS system image (API 34,
android-wear arm64-v8a) and a round or square device profile.

## Architecture

| Layer                   | Module      | Notes                                    |
|-------------------------|-------------|------------------------------------------|
| Hyundai/Genesis/Kia API | `:shared`   | OkHttp + kotlinx.serialization           |
| Phone UI + Widget       | `:app`      | Compose Material 3, Hilt DI, GSON        |
| Wear UI + Tiles + Comps | `:wear`     | Wear Compose Material 3, Coil, ProtoLayout |
| Shared compose utils    | `:uicommon` | AnimatedSlider, WiggleText, weather icons |

### Wear Data Layer

Phone ↔ watch sync uses Wearable Data Layer over 11 paths:

| Path                   | Direction      | Content                        |
|------------------------|----------------|--------------------------------|
| `/bloo/state`          | phone → watch  | All-car snapshot               |
| `/bloo/auth`           | phone → watch  | Session tokens                 |
| `/bloo/settings`       | phone → watch  | Theme, units, pebble order     |
| `/bloo/presets`        | phone → watch  | Saved climate presets          |
| `/bloo/climate`        | bidirectional  | Live climate draft             |
| `/bloo/extras`         | phone → watch  | Weather, images, AI            |
| `/bloo/pebble_order`   | watch → phone  | Reordered sections             |
| `/bloo/local`          | watch → phone  | Font scale                     |
| `/bloo/command`        | watch → phone  | Command relay                  |
| `/bloo/sync_request`   | watch → phone  | Refresh request                |
| `/bloo/command_result` | phone → watch  | Command outcome                |

Auth tokens are refreshed automatically on 401/403. A second failure routes
back to the sign-in screen.

### Widget data flow

```
User taps widget button
        │
        ▼
WidgetAuthActivity (transparent, biometric gate)
        │
        ▼
Optimistic snapshot update (instant UI feedback)
        │
        ▼
WidgetCommandWorker (WorkManager background worker)
        │
        ▼
WearCommandRunner.execute() ──► Hyundai/Genesis/Kia API
        │
        ▼
SnapshotStore updated ──► BlooWidget.updateAll() ──► Widget re-renders
        │
        ▼
WearBridge.publishNow() ──► Watch tiles + complications update
```

A `WidgetRefreshWorker` runs every 15 minutes (server-cached, no car wake)
to keep the widget, tiles, and complications fresh even when the app is
closed.

## Project structure

```
Bloo/
├── app/          Phone app (Compose Material 3, Hilt, GSON)
│   └── src/main/java/com/bloo/bluelink/
│       ├── data/         API repos, encrypted stores, notifications
│       ├── tiles/        Quick Settings tiles (QS panel)
│       ├── ui/           Screens, theme, biometrics, AI
│       ├── update/       Self-update from GitHub Actions
│       ├── wear/         Phone-side Wear bridge + Data Layer
│       └── widget/       Glance home-screen widget
├── shared/       Shared library (API, models, WearSync)
│   └── src/main/java/com/bloo/bluelink/data/
│       ├── BlueLinkApi.kt         Hyundai/Genesis REST client
│       ├── KiaUsaApi.kt           Kia REST client
│       ├── Models.kt              Response models + enums
│       ├── BlueLinkRepository.kt  Auth + command orchestration
│       ├── SessionStore.kt        Encrypted token persistence
│       ├── WearSync.kt            Wire protocol (all Data Layer paths)
│       ├── WearCommandRunner.kt   Standalone command execution
│       └── ...
├── wear/         Wear OS app
│   └── src/main/java/com/bloo/wear/
│       ├── tile/             BlooTile1..4, ProtoLayout rendering
│       ├── complication/     Charge/Lock/Climate complications
│       ├── ui/               Screens (Login/Home/Settings/Trips/Reorder), components, theme
│       ├── WearViewModel.kt  Central state, commands, sync
│       ├── WearComms.kt      Data Layer send/receive
│       └── ...
├── uicommon/     Shared compose primitives
│   └── src/main/java/com/bloo/uicommon/
│       ├── AnimatedSlider.kt Custom hand-drawn slider
│       ├── WiggleText.kt     Scrolling text marquee
│       ├── BlooMotion.kt     Animation constants + soft damping
│       └── WeatherUtils.kt   Weather icon + tint mapping
└── .github/      CI workflow (builds both APKs on every push)
```

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia.
Remote commands physically actuate your vehicle — use responsibly.
