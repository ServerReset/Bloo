# Bloo

A third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services. Built with Jetpack Compose and Material 3 Expressive.

No simulated data — every screen talks to live servers.

## Supported brands

| Brand | Service |
|-------|---------|
| Hyundai | Blue Link |
| Genesis | Genesis Connected |
| Kia | Kia Connect (OTP login, no PIN) |

Multiple brands can be signed in at the same time. Credentials are stored encrypted on-device and never logged.

## Features

- **Lock / Unlock** — all brands, all powertrains
- **Remote climate** — temperature, defrost, steering wheel heat, seat heat/cooling, duration presets
- **Charging** — start/stop charge, set AC and DC limits independently (EV/PHEV)
- **GPS location** — live map with one-tap open-in-maps
- **Vehicle status** — doors, windows, trunk, hood, tyre pressure, 12V battery, fuel, charge state, range
- **Trip history** — distance, time, energy and regen breakdown (Hyundai/Genesis Gen3+ EVs)
- **Weather** — local conditions tile plus weather at the car's location
- **On-device AI** — Gemini Nano plain-language status summary and natural-language search (optional)
- **Quick Settings tiles** — lock, unlock, start/stop climate from the notification shade
- **App-icon shortcuts** — long-press the app icon for one-tap commands
- **Home-screen widget** — live status at a glance (Jetpack Glance)

## Download

CI builds a debug APK on every push. Grab the **`bloo-debug-apk`** artifact from the latest [Actions run](../../actions).

## Building locally

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/Bloo-0.1-debug.apk`

Requires Android Studio Meerkat or newer (AGP 9.1, compileSdk 36).

## How it works

| Layer | File |
|-------|------|
| Hyundai/Genesis API | `data/BlueLinkApi.kt` |
| Kia API | `data/KiaUsaApi.kt` |
| Response models | `data/Models.kt` |
| Session / token store | `data/SessionStore.kt` |
| Repository | `data/BlueLinkRepository.kt` |
| UI (Compose) | `ui/` |

Auth tokens are refreshed automatically on 401/403. A second failure routes back to the sign-in screen.

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia. Remote commands physically actuate your vehicle — use responsibly.
