# Bloo

I wanted an app for my Hyundai that felt like Google built it. So I made one.

Bloo is a third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services. Built with Jetpack Compose and Material 3 Expressive for phone, foldable, tablet, and Wear OS. No simulated data -- every screen talks to live servers.

## Supported brands

| Brand   | Service             | Login       | Gen5W (pre-CCNC) | CCNC (gen 3+, 2023+) |
|---------|---------------------|-------------|-------------------|----------------------|
| Hyundai | Blue Link           | Email + PIN | No trips, limited climate | All features |
| Genesis | Genesis Connected   | Email + PIN | No trips, limited climate | All features |
| Kia     | Kia Connect         | OTP only    | All features | All features (no gen field) |

- **Gen5W** (pre-2023, generation code 1-2): The older head unit. Trip history is not available through the API. Climate controls are limited to temperature and defrost only. Seat heat and custom duration values are rejected by the server.
- **CCNC** (2023+, generation code 3+, Connected Car Navigation Cockpit): The newer head unit. Full API support for all features including trip history, seat heat, and climate duration.
- **Kia**: Does not expose the head unit generation, so all features work across all model years. One-time code sign-in instead of PIN.

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

APKs are output to `app/build/outputs/apk/` and `wear/build/outputs/apk/`.

Requires Android Studio Meerkat or newer (AGP 9.1, Kotlin 2.2.20).

## Installing

CI builds are available as zip artifacts from GitHub Actions. Each run produces a zip file containing both the phone and watch APKs.

To install:

1. Download the zip artifact for your platform from the latest [Actions run](../../actions/workflows/android.yml)
2. Unzip the file on your phone or computer
3. Open the APK file on your Android phone
4. Tap **"More details"** to expand the Play Protect warning, then tap **"Install without scanning"** and confirm with your biometrics or device password

The warning appears because Bloo is not signed with Google Play Store keys. I cannot publish on the Play Store because it costs money I do not have, and Hyundai would not approve an unofficial app on their platform. The app is safe, open source, and does not collect any data. All credentials are stored encrypted on-device.

When a new version is available, the Updates section in Settings will show a CI build number. Tap "Check now" to find the latest build, then follow the steps above to download and install the updated APK from the Actions page.

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
