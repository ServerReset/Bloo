# Europe support — Hyundai Bluelink EU (CCS2)

**Date:** 2026-08-04
**Status:** Approved design, implementation in progress
**Branch:** `feature/europe-hyundai-bluelink`

## Goal

Add **Europe** as a third region in Bloo, alongside US and Canada, so a European
Hyundai owner can sign in and control their car. First release targets **Hyundai
Bluelink Europe on the CCS2 protocol** (E-GMP / 2023+ cars such as Ioniq 5/6),
verified live against a real European Hyundai account and vehicle.

Kia Connect EU and Genesis EU share the same backend platform ("CCAPI") and are
deliberately **out of scope for code** in this release, but the design is
parameterised by brand so they later reduce to a single `Brand` entry + host —
exactly the way Canada already serves three brands from one `CanadaApi`.

## Non-goals (YAGNI)

- No Kia EU / Genesis EU code shipped now (untestable without those cars).
- No legacy CCS1 protocol (pre-2023 EU cars) — CCS2 only for now.
- No UI redesign of the login screen beyond adding the region/brand option.
- No changes to screens, widgets, tiles, or the Wear app: they consume
  `VehicleRepository` and are backend-agnostic.

## Guiding principle

The project's rule holds: **no simulated data — every screen talks to live
servers.** Only code paths verifiable against the real test car (Hyundai EU,
CCS2) are shipped as "supported". Anything unverifiable is either omitted or
clearly documented as untested.

## Architecture

Bloo already isolates each backend family behind the
[`VehicleRepository`](../../../shared/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt)
interface, selected per brand by `repositoryFor()`:

| Region / brand        | API client   | Repository          |
|-----------------------|--------------|---------------------|
| Hyundai/Genesis US    | `BlueLinkApi`| `BlueLinkRepository`|
| Kia US                | `KiaUsaApi`  | `KiaRepository`     |
| Hyundai/Genesis/Kia CA| `CanadaApi`  | `CanadaRepository`  |
| **Hyundai EU (new)**  | **`EuApi`**  | **`EuRepository`**  |

Europe mirrors the **Canada** shape: one API client parameterised by `Brand`,
one repository. Nothing above the repository interface changes.

### Components

1. **`Brand.HYUNDAI_EU`** — new enum entry in
   [`Brand.kt`](../../../shared/src/main/java/com/bloo/bluelink/data/Brand.kt):
   `code = "HEU"` (region-distinct, like the 3-letter Canada codes so
   `fromIndicator` re-derives the backend for a saved vehicle), EU CCAPI host,
   Hyundai EU OAuth client id/secret. New `isEurope` helper mirroring `isCanada`.
   Adding the entry forces the exhaustive `when` blocks (BrandLinks, login copy)
   to be completed — compiler-enforced.

2. **`EuApi`** (new, `shared/.../data/EuApi.kt`) — the CCS2 client. Owns:
   - Device registration (obtain a `deviceId`).
   - OAuth2 sign-in (email + password → authorization code → access/refresh token).
   - Control-token (PIN) acquisition for commands.
   - `Stamp` request signing (see below).
   - CCS2 endpoints: `carstatus/latest`, and `control/*` for lock/unlock,
     climate, charge.
   - Mapping CCS2 JSON responses to Bloo's existing domain models
     (`VehicleStatus`, `GeoLocation`, `EvTrip`).

3. **`EuRepository : VehicleRepository`** (new) — coordinates `EuApi` with the
   persisted `SessionStore`/`CredentialStore`, refreshing tokens on auth failure,
   exactly like `CanadaRepository`. Implements the full interface: `vehicles`,
   `status`, `location`, `trips`, `lock`, `unlock`, `startClimate`,
   `stopClimate`, `setChargeTargets`, `startCharge`, `stopCharge`, `logout`.

4. **`repositoryFor()` wiring** — add `brand.isEurope -> EuRepository(EuApi(brand), …)`.

5. **Login UI** — add a third `MorphSegmented` region segment "Europe" in
   [`Screens.kt`](../../../app/src/main/java/com/bloo/bluelink/ui/Screens.kt)
   (~line 1871). Selecting Europe offers only Hyundai. Login form is
   email + password + PIN — identical to Hyundai US, no OTP dialog.

## Authentication flow (EU CCS2)

Ported and verified from the Apache-2.0 community project
`hyundai_kia_connect_api` (`HyundaiBlueLinkApiEU`, CCS2 variant). Sequence:

1. **Device registration** — POST a generated device UUID, receive a `deviceId`.
   (New vs US/CA, which need no device step.)
2. **OAuth2** — cookie bootstrap → `authorize` → `signin` (email+password) →
   extract `code` from the redirect → exchange `code` for `access_token` +
   `refresh_token` (Basic auth with the Hyundai EU client credentials).
3. **Control token** — commands require a separate PIN-derived token
   (~10 min TTL), the EU analogue of Canada's `pAuth`. Acquired with the user's
   PIN on demand and cached until it expires.
4. **CCS2 endpoints** — status via `…/ccs2/carstatus/latest`; commands via
   `…/ccs2/control/*`.

The PIN is stored the same encrypted way as every other brand
([`CredentialStore`](../../../shared/src/main/java/com/bloo/bluelink/data/CredentialStore.kt),
AES-256) and attached to the control-token request, never logged (okhttp stays
at `BASIC` level, as the other APIs already do).

## Request signing — the `Stamp` header

Every EU request carries a `Stamp` header (anti-abuse), computed from the
Hyundai EU `appId` XOR a rotating base64 "CFB" seed plus a timestamp.

**Decision: generate the stamp on-device** from an embedded CFB constant — no
new network dependency, no third-party host. (Rejected: fetching a
community-hosted stamp list, which would add a non-official host — precisely
what Bloo's clean network profile avoids.)

The CFB seed + `appId` are the maintenance point: if Hyundai rotates its app,
they are updated in **one place**, documented inline the same way `Brand.kt`
already documents rotatable `clientSecret` values. The stamp computation is
pure logic and is **unit-tested** against known input/output vectors.

## Testing & verification

- **Unit tests** (`shared/src/test`) for the pure, deterministic logic:
  - `Stamp` generation (known appId + CFB + fixed timestamp → expected stamp).
  - CCS2 response → `VehicleStatus`/`GeoLocation`/`EvTrip` mapping, from captured
    sample JSON.
- **Live verification** (done by the repo owner running the app against the real
  Ioniq/E-GMP car), screen by screen: sign-in, vehicle list, status (doors,
  battery, range, charge), location/map, lock/unlock, remote climate
  (pre-conditioning), charge start/stop + AC/DC limits, trips (if the car
  exposes them). This is the "no simulated data" gate.

## Risks & open items

- **Exact EU constants** (Hyundai EU `client_id`, `client_secret`, `appId`, CFB
  seed, host `prd.eu-ccapi.hyundai.com:8080`, service device id): sourced from
  the community lib and must be confirmed current at build time. Isolated in
  `Brand.kt` / `EuApi` for a one-line fix if rotated.
- **Stamp rotation**: if Hyundai changes its app signing, the CFB seed needs an
  update. Documented and unit-tested so a mismatch is obvious.
- **CCS2 only**: pre-2023 EU cars (legacy protocol) are unsupported for now;
  the region picker still offers Europe, but such cars may not return status.
  A follow-up can add the legacy path if needed.

## Git / workflow

- Work on `feature/europe-hyundai-bluelink`, atomic commits per logical step.
- Not pushed until the owner asks; a PR into the main branch is offered at the end.
