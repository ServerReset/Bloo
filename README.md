# Bloo

An Android app (Jetpack Compose + Material 3 Expressive) for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services.

**No simulated data.** Every screen talks to Hyundai's real servers (`api.telematics.hyundaiusa.com`). If you are not signed in with a real account, the app shows errors rather than fake values.

CI builds the APK automatically on every push. Download the **`bloo-debug-apk`** artifact from the latest Actions run.

---

## Features

### Vehicle status
- Lock state, individual door / window open or closed, trunk, hood
- Climate and defrost on/off, engine on/off, accessory power
- EV battery percentage and charging state with a prominent charge bar
- Estimated range and odometer
- 12V battery health
- Charge time remaining and charger type (AC/DC)
- Live GPS location embedded in the status payload
- **Diagnostics card**: per-tire pressure warnings, fuel level, low-fuel light, washer fluid, brake fluid, key-fob battery, steering-wheel heater, rear-window and mirror heaters, plug state and time-to-full

### Remote commands
- **Lock and unlock**
- **Climate start / stop** with sliders for temperature, run time, defrost, steering-wheel heat, and per-seat heating/cooling levels
- **Climate presets** - save named configurations; one tap loads all sliders and fires the climate command. The active preset pill morphs from a pill to a rounded button to show it is running. Preset pills revert to pill shape automatically when settings drift.
- **Set EV charge limits** (separate AC and DC target state-of-charge, 50-100%)
- **Start and stop charging**
- **Open and close the charge port** (Hyundai/Genesis EV)
- **Find my car** - live GPS with one-tap "Open in maps"

### Digital Car Key
- Quick links to the Hyundai Digital Car Key app, Google Wallet, and Samsung Wallet (shown only on Samsung devices)
- Links appear in the Car info pebble for Hyundai and Genesis vehicles

### App experience
- Sign in with your Blue Link / Kia Access email, password and service PIN; credentials are stored **encrypted** (Jetpack DataStore + Security Crypto) and remembered
- **Fingerprint / biometric lock** (optional) that gates the app on launch
- Opens straight to the **last car** you viewed with no chooser screen; swipe to switch cars; switching is **instant** because statuses are cached
- **Responsive layout**: a swipe carousel on phones; on tablets and foldables a multi-car grid with any car expandable to full screen
- Drag-and-drop pebble reordering so you can put the sections you use most at the top
- Pin any pebble to a persistent hot-spot in the dual-column layout
- A copy-pasteable **activity log** and credential management in **Settings**
- Pull-to-refresh with a full-screen animated aurora background; floating controls stay fixed while the content refreshes
- Tap-or-drag **sliders** throughout (not just drag)

### Appearance
- **Material 3 Expressive** look: vibrant palette, pill-shaped controls that morph to rounded rectangles on press, bold display type, Material You dynamic color on Android 12+
- Theme: System / Light / Dark / **AMOLED** pure black
- Dynamic-color toggle
- Font: System / **Atkinson Hyperlegible** / Poppins
- UI scale slider for text and layout density
- Vibrancy slider to dial the palette intensity
- **Expressive haptics**: slider notches, pull-to-refresh dice-roll, data-refresh slot-machine settle (follows your system vibration intensity)

### Quick access
- **Quick Settings tiles**: assign any tile to a car and action; tiles open Bloo or run the command silently in the background
- **App-icon shortcuts**: long-press the Bloo icon for one-tap lock, unlock or climate
- **Home-screen widget** (Jetpack Glance)

### On-device AI (Gemini Nano - optional)
- AI status summary pebble for each car, summarizing what the data says
- Ask the settings search box plain questions like "what is the odometer" or "is the car locked"
- Runs fully on-device; gated at runtime so unsupported devices simply hide it

---

## Supported brands

| Brand | Backend | Notes |
|-------|---------|-------|
| Hyundai | `api.telematics.hyundaiusa.com` | Full support |
| Genesis | `api.genesis.telematics.hyundaiusa.com` | Full support |
| Kia | `api.owners.kia.com` (Kia Connect) | Sign-in uses a one-time code; no service PIN |

Multiple brands can be signed in simultaneously (separate accounts).

---

## Capability detection

The US telematics APIs carry no feature or capability flags. Bloo infers capability from live data:

- Seat controls appear only for positions the car reports in `seatHeaterVentState`
- Charge limits and the charge-port button appear only for EVs and PHEVs
- Trip history is shown only for cars whose head unit serves the trips endpoint (not Gen5W)
- Status rows are rendered only when the field is present in the API response

**Seat heat via remote climate** works on ICE/PHEV cars and Gen3 EVs. Gen5W EVs reject the seat-heat payload in the climate start command. Bloo shows an informational note when a Gen5W EV user has configured seat controls.

One thing the API cannot report is whether a seat can cool vs only heat. That is a per-car toggle ("Ventilated seats") in Settings.

---

## How it works

| Concern | File |
|---------|------|
| Hyundai/Genesis API | `app/src/main/java/com/bloo/bluelink/data/BlueLinkApi.kt` |
| Kia API | `app/src/main/java/com/bloo/bluelink/data/KiaUsaApi.kt` |
| Response models | `app/src/main/java/com/bloo/bluelink/data/Models.kt` |
| Session / token store | `app/src/main/java/com/bloo/bluelink/data/SessionStore.kt` |
| Repository / refresh | `app/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt` |
| UI (Compose) | `app/src/main/java/com/bloo/bluelink/ui/` |

Authentication flow (Hyundai/Genesis):

1. `POST /v2/ac/oauth/token` with email + password returns `access_token` / `refresh_token`
2. `GET /ac/v2/enrollment/details/{email}` returns enrolled vehicles
3. Commands (`/ac/v2/rcs/...`, `/ac/v2/evc/...`) send the access token, registration ID, VIN and service PIN as headers

Tokens and the service PIN are stored on-device only (Jetpack DataStore + encrypted SharedPreferences). The activity log in Settings shows request and response lines at BASIC level (no auth bodies are logged).

---

## Building

CI builds automatically on every push via GitHub Actions (`.github/workflows/android.yml`). Download the `bloo-debug-apk` artifact from the Actions run.

Locally:

```bash
./gradlew assembleDebug
```

---

## Fonts and licensing

Fonts are bundled (no network needed) under the SIL Open Font License; the license texts ship in `app/src/main/assets/licenses/`:

- **Atkinson Hyperlegible** (Braille Institute)
- **Poppins** (Google Fonts)

---

## Credits and references

Reverse-engineered telematics knowledge comes from these community projects:

- [Hacksore/bluelinky](https://github.com/Hacksore/bluelinky)
- [schmidtwmark/BetterBlueKit](https://github.com/schmidtwmark/BetterBlueKit)
- [andyfase/egmp-bluelink-scriptable](https://github.com/andyfase/egmp-bluelink-scriptable)
- [nelwyn2/BlueBridge](https://github.com/nelwyn2/BlueBridge)
- [RustyDust/bluelink_refresh_token](https://github.com/RustyDust/bluelink_refresh_token)

---

## Disclaimer

This is an unofficial app, not affiliated with or endorsed by Hyundai, Genesis, or Kia. Use your own account at your own risk. Remote commands physically actuate your vehicle.
