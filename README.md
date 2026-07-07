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
- **Remote climate** — temperature, defrost, steering wheel heat, seat heat/cooling, duration presets
- **Charging** — start/stop charge, set AC and DC limits independently (EV/PHEV)
- **GPS location** — live map with one-tap open-in-maps
- **Vehicle status** — doors, windows, trunk, hood, tyre pressure, 12V battery, fuel, charge state, range
- **Trip history** — distance, time, energy and regen breakdown (Hyundai/Genesis Gen3+ EVs)
- **Weather** — current conditions at car and home
- **On-device AI** — Gemini Nano plain-language status summary and natural-language search (optional)
- **Quick Settings tiles** — lock, unlock, start/stop climate from the notification shade
- **App-icon shortcuts** — long-press the app icon for one-tap commands
- **Home-screen widget** — live status at a glance (Jetpack Glance)

### Wear OS app
- **All phone features on your watch** — lock, climate, charge, location, weather, diagnostics
- **Wear OS Tiles** — up to 4 poolable Tiles (one per car) with charge arc, lock/climate/charge chips
- **Watch-face complications** — charge %, lock state, climate state with tap-to-toggle
- **Phone-paired or standalone** — commands relay through the phone when nearby, or run directly on Wi-Fi/cell watches
- **Synced theme** — colour scheme, temperature unit, and text scale mirror the phone app
- **Font scale** — independent watch text-size slider (0.8×–1.4×), synced back to phone
- **Crown/bezel scroll** — rotary input for tile-by-tile navigation, reorderable sections
- **Smart climate** — reads weather at the car and auto-picks a comfortable setpoint
- **Climate presets** — save, rename, reorder, and apply named presets from the watch
- **Tile reorder** — drag-and-drop pebble groups, order synced to the phone per car
- **Haptic feedback** — distinct haptics for taps, toggles, ticks, and errors
- **Offline-friendly** — all data cached locally, tiles render from disk with fine-grained freshness

## Download

CI builds debug APKs on every push.

- **Phone:** grab the `bloo-debug-apk` artifact from the latest [Actions run](../../actions)
- **Watch:** grab the `bloo-wear-debug-apk` artifact — or the combined `wear-apks` artifact

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
# Pair the watch via `adb pair`, then:
adb -s <watch-serial> install wear/build/outputs/apk/debug/Bloo-Wear.apk

# For emulator:
# 1. Download a Wear OS system image (API 34, android-wear arm64-v8a)
# 2. Create an AVD with a round or square Wear device profile
# 3. Install and launch as above
```

## Architecture

| Layer                 | Module      | Notes                                      |
|-----------------------|-------------|--------------------------------------------|
| Hyundai/Genesis/Kia API | `:shared` | OkHttp + kotlinx.serialization             |
| Phone UI + Widget     | `:app`      | Jetpack Compose Material 3, Hilt DI, GSON  |
| Wear UI + Tiles       | `:wear`     | Wear Compose Material 3, Coil, ProtoLayout |
| Shared compose utils  | `:uicommon` | AnimatedSlider, WiggleText, weather icons  |

### Wear Data Layer

Phone → watch state sync uses Wearable Data Layer over 9 paths:

| Path                  | Direction | Content                        |
|-----------------------|-----------|--------------------------------|
| `/bloo/state`         | phone → watch | All-car snapshot          |
| `/bloo/auth`          | phone → watch | Session tokens            |
| `/bloo/settings`      | phone → watch | Theme, units, pebble order |
| `/bloo/presets`       | phone → watch | Saved climate presets     |
| `/bloo/climate`       | bidirectional | Live climate draft       |
| `/bloo/extras`        | phone → watch | Weather, images, AI       |
| `/bloo/pebble_order`  | watch → phone | Reordered sections        |
| `/bloo/local`         | watch → phone | Font scale                |
| `/bloo/command`       | watch → phone | Command relay             |
| `/bloo/sync_request`  | watch → phone | Refresh request           |
| `/bloo/command_result`| phone → watch | Command outcome           |

Auth tokens are refreshed automatically on 401/403. A second failure routes
back to the sign-in screen.

## Project structure

```
Bloo/
├── app/          Phone app (Compose Material 3, Hilt, GSON)
│   └── src/main/
│       └── java/com/bloo/bluelink/
│           ├── data/         API repos, stores, notifications
│           ├── tiles/        Quick Settings tiles
│           ├── ui/           Screens, theme, biometrics
│           ├── update/       Self-update from GitHub Actions
│           ├── wear/         Phone-side Wear bridge
│           └── widget/       Home-screen widget (Glance)
├── shared/       Shared library (API, models, WearSync)
│   └── src/main/java/com/bloo/bluelink/data/
│       ├── BlueLinkApi.kt        Hyundai/Genesis API client
│       ├── KiaUsaApi.kt          Kia API client
│       ├── Models.kt             Response models + enums
│       ├── BlueLinkRepository.kt Auth + command orchestration
│       ├── SessionStore.kt       Encrypted token persistence
│       ├── WearSync.kt           Wire protocol (9 Data Layer paths)
│       └── ...
├── wear/         Wear OS app (Compose for Wear, Tiles, Complications)
│   └── src/main/java/com/bloo/wear/
│       ├── tile/             BlooTile1..4, ProtoLayout rendering
│       ├── complication/     Charge/Lock/Climate watch-face complications
│       ├── ui/               Screens, components, theme
│       ├── WearViewModel.kt  Central state, commands, sync
│       ├── WearComms.kt      Data Layer send/receive
│       └── ...
├── uicommon/     Shared compose primitives (slider, weather icons, animations)
└── .github/      CI (builds phone + watch APKs on every push)
```

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia.
Remote commands physically actuate your vehicle — use responsibly.
