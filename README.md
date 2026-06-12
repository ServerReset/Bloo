# Bloo

A polished Android app (Jetpack Compose) that controls **Hyundai** vehicles
through the **Blue Link** telematics service, talking directly to Hyundai's
real US servers (`api.telematics.hyundaiusa.com`).

There is **no simulated or mock data anywhere** - every screen is backed by a
live request to Hyundai. If you are not signed in with a real Blue Link
account, the app shows errors rather than fake values.

## Features

### Car status
- Live vehicle status: lock state, individual doors, trunk and hood open/closed,
  climate, engine, range, odometer, 12V battery health, EV charge % and
  charging state
- **Comprehensive diagnostics** card: per-tire pressure warnings, fuel level /
  low-fuel light, washer fluid, brake fluid, key-fob battery, steering-wheel /
  rear-window / mirror heaters, plug state and time-to-full
- Prominent **charge/fuel bar** showing percentage and estimated range
- EV **trip history** showing distance, energy used, regen, drive time and idle
  time for recent trips (EV models)

### Remote commands
- Remote **lock / unlock**
- Remote **climate** with sliders for target temperature, run time, defrost,
  steering-wheel heat, and per-seat heating/cooling
- **Climate presets** - save named climate configurations and start them with
  one tap; each preset pill starts the climate command immediately with its
  saved settings; last-used settings are remembered between app opens
- **Set EV charge limits** (separate AC and DC target state-of-charge)
- **Find my car** - live GPS location embedded in the status payload, with
  one-tap "Open in Maps"

### App experience
- Sign in with your Blue Link email, password and service PIN; credentials are
  stored **encrypted** (Jetpack Security) and remembered
- **Fingerprint / biometric lock** (optional) that gates the app on launch
- Opens straight to the **last car** you viewed with no chooser screen; swipe
  to switch cars; switching is **instant** because statuses are cached
- **Responsive layout**: a swipe carousel on phones; on tablets and foldables a
  multi-car grid with any car expandable to full screen
- Drag-and-drop pebble reordering so you can put the sections you use most at
  the top
- Pin any pebble to a persistent hotspot in the dual-column layout
- A copy-pasteable **activity log** and credential management in **Settings**
- **Material 3 Expressive** look: vibrant palette, pill-shaped controls that
  morph to rounded rectangles on press, bold display type, and Material You
  dynamic color on Android 12+
- **Appearance settings**: theme (System / Light / Dark / **AMOLED** pure
  black), dynamic-color toggle, and font choice (System /
  **Atkinson Hyperlegible** / Poppins)
- Expressive haptics: slider notches, pull-to-refresh dice-roll, data-refresh
  slot-machine settle (follows your system vibration intensity)
- Pull-to-refresh with a full-screen animated aurora background

### Fonts and licensing

Fonts are bundled (no network needed) under the SIL Open Font License; the
license texts ship in `app/src/main/assets/licenses/`:

- **Atkinson Hyperlegible** (Braille Institute) - a high-legibility typeface
- **Poppins** - used for the geometric sans option (Google's Product Sans is
  proprietary and cannot be redistributed)

The UI uses **Material 3 Expressive** (`material3` 1.4.x): `MaterialExpressiveTheme`
with an expressive `MotionScheme`, Expressive components
(`ContainedLoadingIndicator`, `LoadingIndicator`, `ButtonGroup`,
`SplitButtonLayout`, `HorizontalFloatingToolbar`), spring-based motion with
overshoot, shape morphing, and an emphasized type scale.

### Capability detection

The US Blue Link API has no feature or capability flags. Following the same
approach as the community libraries, Bloo infers capability from live data: a
seat control appears only for seats the car reports in `seatHeaterVentState`,
charge limits only appear for EVs, and a status row is rendered only when the
field is present (never a fabricated value). EV trip history is shown only for
EV and PHEV models.

One thing the API cannot tell us is whether a seat can cool vs only heat.
Rather than guess, that is a per-car toggle ("Ventilated seats") that widens
the seat slider to include cooling levels. Charge start/stop control is still
omitted - the only community endpoint for it points at a different API base
and an identifier the US enrollment flow does not return.

## How it works

The endpoints, OAuth client credentials and request headers are taken from
the community reverse-engineering efforts referenced below. The relevant code:

| Concern             | File |
|---------------------|------|
| API endpoints       | `app/src/main/java/com/bloo/bluelink/data/BlueLinkApi.kt` |
| Response models     | `app/src/main/java/com/bloo/bluelink/data/Models.kt` |
| Session/token store | `app/src/main/java/com/bloo/bluelink/data/SessionStore.kt` |
| Repository/refresh  | `app/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt` |
| UI (Compose)        | `app/src/main/java/com/bloo/bluelink/ui/` |

Authentication flow:

1. `POST /v2/ac/oauth/token` with email + password returns `access_token` /
   `refresh_token`
2. `GET /ac/v2/enrollment/details/{email}` returns enrolled vehicles
3. Commands (`/ac/v2/rcs/...`) send the access token, registration id, VIN
   and service PIN as headers

Tokens and the service PIN are stored on-device only (Jetpack DataStore).

## Region and scope

This targets **Hyundai, US region**. Kia and the EU/CA regions use different
hosts and credentials and are not wired up yet.

## Building

CI builds the APK automatically on every push via GitHub Actions
(`.github/workflows/android.yml`); download the `bloo-debug-apk` artifact
from the Actions run.

Locally:

```bash
./gradlew assembleDebug
```

## Credits and references

Reverse-engineered Blue Link knowledge comes from these community projects:

- [Hacksore/bluelinky](https://github.com/Hacksore/bluelinky)
- [schmidtwmark/BetterBlueKit](https://github.com/schmidtwmark/BetterBlueKit)
- [schmidtwmark/BetterBlue](https://github.com/schmidtwmark/BetterBlue)
- [andyfase/egmp-bluelink-scriptable](https://github.com/andyfase/egmp-bluelink-scriptable)
- [nelwyn2/BlueBridge](https://github.com/nelwyn2/BlueBridge)
- [JFerretti/egmp-alternate-app](https://github.com/JFerretti/egmp-alternate-app)
- [RustyDust/bluelink_refresh_token](https://github.com/RustyDust/bluelink_refresh_token)

## Disclaimer

This is an unofficial app, not affiliated with or endorsed by Hyundai. Use
your own account at your own risk; remote commands physically actuate your
vehicle.
