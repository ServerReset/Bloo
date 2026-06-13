# Bloo

An Android app for controlling **Hyundai**, **Genesis**, and **Kia** vehicles through their US telematics services. Built with Jetpack Compose and Material 3 Expressive.

No simulated data. Every screen talks to live servers. If you are not signed in with a real account, the app shows errors rather than fake values.

CI builds the APK on every push. Download the **`bloo-debug-apk`** artifact from the latest Actions run.

---

## Supported vehicles

| Brand | Service | Auth |
|-------|---------|------|
| Hyundai | Blue Link (`api.telematics.hyundaiusa.com`) | Email + password + service PIN |
| Genesis | Genesis Connected (`api.genesis.telematics.hyundaiusa.com`) | Email + password + service PIN |
| Kia | Kia Connect (`api.owners.kia.com`) | Email + password + one-time code (no PIN) |

Multiple brands can be signed in at the same time. Credentials are stored encrypted on-device (Jetpack DataStore + Security Crypto) and never logged.

---

## Feature matrix

The table below shows what works for each combination of brand and powertrain. Features not listed here work the same for all brands and all powertrain types.

| Feature | Hyundai | Genesis | Kia |
|---------|---------|---------|-----|
| Vehicle status | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV |
| Lock / Unlock | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV |
| Remote climate | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV |
| Seat heat/cool (climate) | Gas · Hybrid · PHEV · Gen3 EV | Gas · Hybrid · PHEV · Gen3 EV | Gas · Hybrid · PHEV · EV |
| GPS location | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV |
| Start/stop charging | PHEV · EV | PHEV · EV | PHEV · EV |
| Set charge limits | PHEV · EV | PHEV · EV | PHEV · EV |
| Open/close charge port | PHEV · EV | PHEV · EV | — |
| Trip history | EV (Gen3+) | EV (Gen3+) | — |
| Digital Key 1 (BLE/NFC) | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | — |
| Digital Key 2 (UWB) via wallet | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV | Gas · Hybrid · PHEV · EV |
| Features on Demand store | Gen3+ only | Gen3+ only | Gas · Hybrid · PHEV · EV |

**Gen5W note:** Hyundai and Genesis vehicles with Gen5W head units (generation reported as `"2"`) do not support seat heat via remote climate on EVs (the climate module rejects the payload). Seat controls are hidden for those vehicles. Trip history is also suppressed for Gen5W because the head unit does not serve the trips endpoint.

---

## Vehicle status

Bloo shows everything the telematics API reports for your vehicle. What appears depends on what your car actually sends.

**Always shown (all brands, all powertrains)**
- Lock state and individual door, window, trunk and hood open/closed
- Engine on/off, accessory power, air conditioning, defrost
- Air temperature setting
- Odometer
- Live GPS location (embedded in the status payload — no separate API call)
- Last updated timestamp

**EV and PHEV only**
- Drive battery percentage and charge state
- Plug type (DC fast / AC / portable) and plug status
- Estimated electric range
- Charge time remaining (for current plug type, plus AC and DC projections)
- AC and DC charge target limits

**Gas, hybrid and PHEV**
- Fuel level percentage and estimated range

**Diagnostics (shown only when the API reports the field)**
- Per-tire pressure warnings
- 12V battery health
- Fuel level and low-fuel light
- Washer and brake fluid
- Key fob battery
- Steering wheel heater, rear window heater, mirror heater
- Per-seat heater and vent state

---

## Remote commands

### Lock and unlock
Works for all brands, all powertrains.

### Remote climate
Works for all brands, all powertrains.

- Temperature: 62–82 °F
- Run time: 10–30 minutes
- Defrost on/off
- Steering wheel heat on/off (requires enabling the option in Settings for your car)
- Per-seat heat and cooling levels (requires configuring each seat in Settings; only seats your car actually has appear in the live status)

**Seat heat on EVs:** Seat heat works in remote climate for Gen3 EVs across all brands. Gen5W Hyundai and Genesis EVs do not support it — seat controls are hidden for those vehicles. Kia EVs support it normally.

**Climate presets:** Save named configurations. One tap applies all sliders and fires the climate command. The active preset pill morphs to a rounded button while running and reverts automatically when the live status drifts.

### Charging (EV and PHEV only)
- Start and stop charging (all brands)
- Set AC and DC charge limits independently, 50–100% (all brands)
- Open and close the charge port (Hyundai and Genesis only; Kia has no endpoint for this)

### Find my car
Live GPS with a one-tap **Open in Maps** button. Location comes from the status payload so it is always current without an extra API call.

---

## Digital Car Key

The Car info pebble shows quick links based on what your brand and device support.

**Digital Key 1** (BLE/NFC — Hyundai and Genesis only)
- Deep-links directly to the key management screen in the respective Digital Car Key app
- Falls back to the Play Store if the app is not installed
- Kia does not have a Digital Key 1 app in the US

**Digital Key 2** (UWB — all brands)
- On Samsung devices: opens Samsung Wallet
- On all other Android devices: opens Google Wallet

---

## Trip history

For Hyundai and Genesis EVs with Gen3+ head units, Bloo shows a history of recent drives with:
- Distance and duration (driving vs. idle minutes)
- Average and peak speed
- Energy used and regenerated (kWh)
- Odometer at start

Trip history is not available for Kia (no endpoint), Gen5W Hyundai/Genesis head units, or non-EV vehicles.

---

## App experience

**Multi-car navigation**
- Opens straight to the last car you viewed
- Swipe left and right to switch cars (statuses are cached so switching is instant)
- On tablets and foldables: multi-car grid, tap any car to expand it to a dual-column full-screen view
- Drag-and-drop to reorder the pebble sections; pin any pebble to a fixed hotspot in the dual-column layout

**Pull to refresh**
- Full-screen animated aurora background during refresh
- Dice-roll haptic while pulling; slot-machine settle on completion
- Floating controls (car name, settings, page dots) stay in place and do not move with the pull

**Quick access**
- **Quick Settings tiles:** assign any of four tiles to a car and action (lock, unlock, start climate, stop climate). Tiles run the command silently in the background or open Bloo.
- **App-icon shortcuts:** long-press the Bloo icon for one-tap lock, unlock, or start climate.
- **Home-screen widget** (Jetpack Glance): live status at a glance with one-tap actions.

---

## Appearance

**Theme:** System / Light / Dark / AMOLED pure black

**Material 3 Expressive:** vibrant palette, pill-shaped controls that morph to rounded rectangles on press, bold display type, Material You dynamic color on Android 12+

**Font:** System / Atkinson Hyperlegible / Poppins (both bundled under SIL Open Font License)

**Customization**
- UI scale slider (0.85–1.3×) for text and layout density
- Vibrancy slider to dial the palette intensity
- Dynamic color toggle
- Haptics follow your system vibration intensity

---

## On-device AI (optional)

Powered by Gemini Nano (ML Kit GenAI). Runs fully on-device. Hidden automatically on devices that do not support it.

- **AI summary pebble:** one-tap plain-language summary of the current vehicle status
- **Settings search:** ask questions like "is the car locked" or "what is the odometer" and get a direct answer from the live status

---

## Settings

**App-wide**
- Theme, font, scale, vibrancy, haptics
- Biometric / fingerprint lock with configurable re-engagement timing
- Links open in-app (Chrome Custom Tab) or system browser

**Per car**
- License plate (your reference only)
- Service history tracking (last service mileage, interval)
- Seat heat and cooling capability per position (driver, passenger, rear-left, rear-right)
- Steering wheel heat capable
- Ventilated seats toggle (affects whether seat controls offer cooling levels)
- Car photo (from gallery or URL)
- Section visibility (show or hide any pebble)
- Section order (drag to reorder, persisted per car)

**Accounts**
- View and remove stored credentials per brand
- Activity log: copy-pasteable HTTP request/response lines at BASIC level (no auth data logged)

---

## How it works

| Concern | File |
|---------|------|
| Hyundai / Genesis API | `app/src/main/java/com/bloo/bluelink/data/BlueLinkApi.kt` |
| Kia API | `app/src/main/java/com/bloo/bluelink/data/KiaUsaApi.kt` |
| Response models | `app/src/main/java/com/bloo/bluelink/data/Models.kt` |
| Session / token store | `app/src/main/java/com/bloo/bluelink/data/SessionStore.kt` |
| Repository | `app/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt` |
| UI (Compose) | `app/src/main/java/com/bloo/bluelink/ui/` |

**Hyundai / Genesis auth flow**
1. `POST /v2/ac/oauth/token` with email + password → `access_token` / `refresh_token`
2. `GET /ac/v2/enrollment/details/{email}` → enrolled vehicles
3. Commands send `access_token`, registration ID, VIN and service PIN as headers
4. On 401/403 the token is refreshed automatically; a second failure returns to the sign-in screen

**Kia auth flow**
1. `POST /apigw/v1/prof/authUser` with email + password → one-time code challenge (email or SMS)
2. User enters the code → session token + `rmtoken` for silent re-auth
3. Commands send `sid` + `vinkey` + `clientid` + `secretkey` as headers (no PIN)

---

## Capability detection

The US telematics APIs carry no feature or capability flags. Bloo infers capability from live data:

- Seat controls appear only for positions the car reports in `seatHeaterVentState`
- Seat heat and cooling availability is configured by the user in Settings (the API cannot report whether a seat cools vs only heats)
- Charge limits and charge port button appear only when `hasBattery` is true (EV or PHEV)
- Trip history is shown only for cars whose head unit serves the trips endpoint
- Status rows in the diagnostics pebble render only when the field is present in the API response

---

## Building

CI builds automatically on every push via GitHub Actions (`.github/workflows/android.yml`). Download the `bloo-debug-apk` artifact from the Actions run.

Locally:

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/Bloo-0.1-debug.apk`

---

## Fonts and licensing

Bundled under the SIL Open Font License (license texts in `app/src/main/assets/licenses/`):

- **Atkinson Hyperlegible** — Braille Institute
- **Poppins** — Google Fonts

---

## Credits

Reverse-engineered telematics knowledge from these community projects:

- [Hacksore/bluelinky](https://github.com/Hacksore/bluelinky)
- [schmidtwmark/BetterBlueKit](https://github.com/schmidtwmark/BetterBlueKit)
- [andyfase/egmp-bluelink-scriptable](https://github.com/andyfase/egmp-bluelink-scriptable)
- [nelwyn2/BlueBridge](https://github.com/nelwyn2/BlueBridge)
- [RustyDust/bluelink_refresh_token](https://github.com/RustyDust/bluelink_refresh_token)

---

## Disclaimer

Unofficial app. Not affiliated with or endorsed by Hyundai, Genesis, or Kia. Use at your own risk. Remote commands physically actuate your vehicle.
