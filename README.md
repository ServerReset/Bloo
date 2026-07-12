# Bloo

I wanted an app for my Hyundai that felt like Google built it. So I made one.

Bloo is a third-party Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services. Built with Jetpack Compose and Material 3 Expressive for both phone and Wear OS. No simulated data -- every screen talks to live servers.

## Supported brands and generations

| Brand   | Service             | Login             | Gen2 | Gen3 | Gen5W |
|---------|---------------------|-------------------|------|------|-------|
| Hyundai | Blue Link           | Email + PIN       | Full | Full | Limited |
| Genesis | Genesis Connected   | Email + PIN       | Full | Full | N/A |
| Kia     | Kia Connect         | OTP (phone only)  | Full | Full | N/A |

Gen5W (CCNC) vehicles have limited climate controls and no trip history through the Blue Link API.

## Features by category

### Remote commands
- Lock, unlock, start climate, stop climate, start charge, stop charge
- Set charge targets (AC and DC limits) independently for EVs and PHEVs

### Vehicle status
- Battery percentage, range estimate, charging status
- Door, window, trunk, and hood open state
- Tyre pressure warnings and individual tyre checks
- 12V battery state of charge and health
- Fuel level, low fuel warning, washer fluid, brake fluid, key fob battery
- Steering wheel heat, mirror heat, rear defrost status
- Seat heater states for all positions
- Odometer reading

### Climate controls
- Temperature setpoint with tap-and-drag slider
- Defrost toggle, steering wheel heat toggle
- Per-seat heat level (off, low, med, high) for front and rear
- Smart climate: reads weather at car and auto-sets a comfortable temperature
- Saved presets: save, name, reorder, and apply climate presets

### Location and navigation
- Live GPS location from the vehicle status API (no separate find-my-car call)
- One-tap open in Google Maps on the phone or watch
- Address geocoding for readable location names
- Location box on large home-screen widgets

### Trip history
- Recent drive distance, duration, idle time, energy consumption
- Regenerated energy for EVs and PHEVs
- Efficiency in mi/kWh

### Weather
- Current conditions at the car's location and at home
- Temperature, feels like, humidity, wind speed
- High and low for the day

### On-device AI
- Gemini Nano plain-language status summary per car
- Natural-language search over Settings and car data
- Optional, gated by device support

### Home-screen widget
- 8 auto-adapting layout tiers for 1x1 through 5x5 placements
- Dynamic button sizing and 2x2 grids on medium widgets
- Optional car photo background with dark scrim
- Optional location map box on large widgets
- Info mode or controls-only mode for small placements
- Pill shape option for extreme corner rounding
- Background command execution without opening the app

### Quick Settings tiles
- Up to 12 configurable tiles per car
- Lock, unlock, climate on/off, charge on/off
- Run in background or open the app on tap

### App shortcuts
- Long-press the app icon for one-tap commands
- Per-car lock, unlock, climate, open

### Wear OS app
- All phone features available on the watch
- 6 poolable Wear OS Tiles (one per car)
- Watch-face complications for charge, lock, and climate
- Phone-paired or standalone operation
- Synced theme, font scale, and settings from the phone
- Crown and bezel scroll control

## Building

```bash
# Phone APK
./gradlew :app:assembleDebug

# Wear OS APK
./gradlew :wear:assembleDebug

# Both
./gradlew assembleDebug
```

Output:
- Phone: `app/build/outputs/apk/debug/Bloo-0.1-debug.apk`
- Watch: `wear/build/outputs/apk/debug/Bloo-Wear.apk`

Requires Android Studio Meerkat or newer (AGP 9.1, Kotlin 2.2.20).

## Architecture

| Module | Purpose |
|--------|---------|
| `:shared` | API clients, models, data stores, WearSync protocol |
| `:app` | Phone app UI, widget, Quick Settings tiles, Wear bridge |
| `:wear` | Wear OS app, tiles, complications |
| `:uicommon` | Shared compose components (slider, segmented control, WiggleText) |

Auth tokens are refreshed automatically on 401 or 403. A second failure routes back to the sign-in screen.

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia. Remote commands physically actuate your vehicle. Use responsibly.
