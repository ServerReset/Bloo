# Bloo

I wanted an app for my Hyundai that felt like Google built it. So I made one.

Bloo is a third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their connected-car services (US, Canada, and Hyundai Europe). Built with Jetpack Compose and Material 3 Expressive for phones, foldables, and tablets. No simulated data -- every screen talks to live servers.

## Supported brands

| Region | Brands | Login |
|--------|--------|-------|
| US | Hyundai (Blue Link), Genesis (Connected Services), Kia (Connect) | Email + PIN, or one-time code for Kia |
| Canada | Hyundai, Genesis, Kia | Email + PIN, then a one-time code |
| Europe | Hyundai (Bluelink) | Email + PIN |

- **Gen5W** head units (pre-2023) have no trip history through the API, and climate is limited to temperature and defrost; seat heat and custom durations are rejected server-side.
- **CCNC** head units (2023+) support everything: trips, seat heat, full climate duration.
- **Kia** does not expose the head-unit generation, so all features work across model years.

Multiple accounts can be signed in at once. Credentials are stored encrypted on-device (AES-256 via the Android Keystore).

## Features

### Car control
- **Lock / Unlock**, **Remote climate** (start/stop, temperature, defrost, seat heat, presets), **Charging** (start/stop, AC/DC limits)
- **Live location** on a map, **full status** (doors, windows, trunk, hood, tyres, 12V battery, fuel, charge, range), **trip history**, **weather** at the car and at home
- **On-device AI**: Gemini Nano summaries and natural-language car commands

### Safety and security
- **App lock**: biometric unlock with an optional 4-8 digit PIN backup, and a re-lock timing of Off / Screen off / Immediate
- **AutoLock**: locks the car automatically when your phone disconnects from it and you walk away, with a Bluetooth/activity-recognition trigger and a time-based alarm fallback
- Commands run through one serialized gate (Blue Link rate-limits overlapping requests), and climate-start is blocked while the car is driving

### Experience
- Adaptive layouts for foldable open/closed states, dual-column expanded views, and tablets
- Drag-to-reorder pebbles per car, custom colour palettes, theme and font choices, and an animated Aurora background
- **Drive sync**: settings, presets, and photos sync across your devices via Google Drive
- **Self-update**: checks GitHub Releases on launch, on refresh, and periodically in the background, then downloads and hands the APK to the system installer (optionally silently, via Shizuku)

## Building

```bash
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/`.

Requires Android Studio Meerkat or newer (AGP 9.1, Kotlin 2.2.20). The build needs JDK 17.

## Installing

Every push publishes a rolling [GitHub Release](../../releases) tagged `build-<run number>` with the phone APK (`Bloo.apk`) attached as a direct download -- a plain public file, no GitHub sign-in and no zip step.

To install:

1. Download `Bloo.apk` from the [latest release](../../releases)
2. Open the downloaded file from your notification shade or the Downloads app
3. If Android shows **"Blocked by Play Protect"**, tap **"More details"**, then **"Install anyway"**, and confirm with your biometrics or device password

The warning appears because Bloo is not signed with Google Play Store keys. It cannot be published on the Play Store (it costs money I do not have, and Hyundai would not approve an unofficial app), so it ships as a signed APK from GitHub Releases instead. The app is open source and collects no data; credentials never leave the device except to the brand's own servers.

Once installed, updates are automatic: the app checks on cold start and on every pull-to-refresh, plus a periodic background check, and surfaces an in-app tile that downloads the new build and hands it to the installer. The steps above are only needed for the very first install.

## Architecture

| Module | Purpose |
|--------|---------|
| `:shared` | API clients (BlueLink US/CA/EU, Kia US), models, repositories, session/credential/snapshot stores, shared formatting |
| `:app` | Phone, foldable, and tablet UI (Compose + Material 3 Expressive), workers, notifications, AutoLock |
| `:uicommon` | Foundation-only shared Compose components (morph button, segmented control, slider, text animations) |
| `:hidden-api-stub` | Compile-only stubs for the reflection used by the Shizuku silent-install path |

Auth tokens refresh automatically on 401/403; a second failure returns to the sign-in screen. A single process-wide mutex serializes every car request.

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia. Remote commands physically actuate your vehicle. Use responsibly.
