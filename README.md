# Bloo

A basic Android app (Jetpack Compose) that controls **Hyundai** vehicles
through the **Blue Link** telematics service, talking directly to Hyundai's
real US servers (`api.telematics.hyundaiusa.com`).

There is **no simulated/mock data anywhere** — every screen is backed by a
live request to Hyundai. If you are not logged in with a real Blue Link
account, the app shows errors rather than fake values.

## Features

- Sign in with your Blue Link email, password and service PIN
- **Swipe left/right** between all the cars on your account
- Live vehicle status: lock state, individual doors / trunk / hood, climate,
  engine, range, odometer, 12V battery, EV charge % and charging state
- **Comprehensive diagnostics** card: per-tire pressure warnings, fuel level /
  low-fuel, washer & brake fluid, key-fob battery, steering-wheel / rear-window
  / mirror heaters, plug state and time-to-full
- Remote **lock / unlock**
- Remote **climate** with sliders: target temperature, run time, defrost,
  steering-wheel heat, and **per-seat heating/cooling**
- **Set EV charge limits** (separate AC and DC target SOC)
- **Find my car** — live GPS location with one-tap "Open in Maps"

### Capability detection

The US Blue Link API has **no feature/capability flags**. Following the same
approach as the community libraries, Bloo *infers* capability from the live
data: a seat control is shown only for seats the car actually reports in
`seatHeaterVentState`, charge limits only appear for EVs, and a status row is
rendered only when the field is present (never a fabricated value).

One thing the API genuinely can't tell us is whether a seat can *cool* vs only
*heat*. Rather than guess, that's a per-car toggle ("Ventilated seats") that
widens the seat slider to include cooling levels. Charge start/stop *control*
is still omitted — the only community endpoint for it points at a different API
base and an id the US enrollment flow doesn't return.

## How it works

The endpoints, OAuth client credentials and request headers are taken from
the community reverse-engineering efforts referenced below. The relevant code:

| Concern            | File |
|--------------------|------|
| API endpoints      | `app/src/main/java/com/bloo/bluelink/data/BlueLinkApi.kt` |
| Response models    | `app/src/main/java/com/bloo/bluelink/data/Models.kt` |
| Session/token store| `app/src/main/java/com/bloo/bluelink/data/SessionStore.kt` |
| Repository/refresh | `app/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt` |
| UI (Compose)       | `app/src/main/java/com/bloo/bluelink/ui/` |

Authentication flow:

1. `POST /v2/ac/oauth/token` with email + password → `access_token` /
   `refresh_token`.
2. `GET /ac/v2/enrollment/details/{email}` → enrolled vehicles.
3. Commands (`/ac/v2/rcs/...`) send the access token, registration id, VIN
   and service PIN as headers.

Tokens and the service PIN are stored on-device only (Jetpack DataStore).

## Region / scope

This first version targets **Hyundai, US region**. Kia and the EU/CA regions
use different hosts and credentials and are not wired up yet.

## Building

CI builds the APK automatically on every push via GitHub Actions
(`.github/workflows/android.yml`); download the `bloo-debug-apk` artifact
from the run.

Locally:

```bash
./gradlew assembleDebug
```

## Credits / references

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
