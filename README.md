# Bloo

I wanted an app for my Hyundai that felt like Google built it. So I made one.

Bloo is a third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services. Built with Jetpack Compose and Material 3 Expressive for phone, foldable, tablet, and Wear OS. No simulated data -- every screen talks to live servers.

## Supported brands

| Brand   | Service             | Login       | Gen2/Gen3          | Gen5W (CCNC)       |
|---------|---------------------|-------------|--------------------|---------------------|
| Hyundai | Blue Link           | Email + PIN | All features       | No trips, limited climate controls |
| Genesis | Genesis Connected   | Email + PIN | All features       | No trips, limited climate controls |
| Kia     | Kia Connect         | OTP only    | All features       | All features (OTT sign-in, no PIN) |

- **Gen2/Gen3** (2018-2022): Full status, location, climate, charging, trips, diagnostics
- **Gen5W** (2023+ CCNC head units): No trip history via the API, climate limited to temperature and defrost only (no seat heat or duration)
- **Kia**: No PIN needed; sign in with a one-time code sent to your phone or email

Multiple brands can be signed in at the same time. Credentials are stored encrypted on-device.

## Features

### Phone app (foldable and tablet supported)
- Auto-adapting layouts for flip-phone cover screens, foldable open/closed states, and tablets
- Camera cutout detection on cover screens with content flowing around the camera hole
- Dual-column expanded view with drag-to-reorder sections
- **Lock / Unlock, Remote climate, Charging** (start/stop, AC/DC limits), **GPS location** with live map, **Vehicle status** (doors, windows, trunk, hood, tyres, 12V battery, fuel, charge, range), **Trip history** (distance, time, energy), **Weather** at car and home, **On-device AI** (Gemini Nano summaries and search)
- **Home-screen widget** with 8 auto-adapting size tiers, dynamic button grids, optional car photo background, optional location map, pill shape mode, and info/controls layout preference
- **Quick Settings tiles** (up to 12 per car), **App shortcuts**, **Biometric auth**, **Custom colour palettes**, **Theme and font choices**

### Wear OS app
- All phone features mirrored on the watch with crown/bezel navigation
- 6 poolable Wear OS Tiles (one per car) with live charge arc and action chips
- Watch-face complications for charge, lock, and climate (tap to toggle)
- Phone-paired or standalone operation (Wi-Fi/cellular watch)
- Synced theme, font scale, and settings from the phone
- Smart climate (reads weather at the car location), climate presets

## Building

```bash
./gradlew assembleDebug
```

Phone APK: `app/build/outputs/apk/debug/Bloo-0.1-debug.apk`
Watch APK: `wear/build/outputs/apk/debug/Bloo-Wear.apk`

Requires Android Studio Meerkat or newer (AGP 9.1, Kotlin 2.2.20).

## Architecture

| Module | Purpose |
|--------|---------|
| `:shared` | API clients (BlueLinkApi, KiaUsaApi), Models, SessionStore, WearSync protocol |
| `:app` | Phone UI, widget (Glance), Quick Settings tiles, Wear bridge |
| `:wear` | Wear OS app, tiles (ProtoLayout), complications, data layer |
| `:uicommon` | Shared compose components (slider, segmented control, text animations) |

Auth tokens are refreshed automatically on 401/403. A second failure returns to the sign-in screen.

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia. Remote commands physically actuate your vehicle. Use responsibly.
