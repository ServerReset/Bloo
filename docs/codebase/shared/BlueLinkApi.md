# `BlueLinkApi` — Hyundai/Genesis Blue Link US telematics client

**File:** `shared/src/main/java/com/bloo/bluelink/data/BlueLinkApi.kt`
**Package:** `com.bloo.bluelink.data`

---

## 1. Purpose

`BlueLinkApi` is the thin, stateless HTTP client that talks to the **real**
Hyundai Blue Link US telematics backend (`https://api.telematics.hyundaiusa.com`)
and, by swapping the `Brand`, the Genesis backend
(`https://api.genesis.telematics.hyundaiusa.com`). There is **no mock/simulated
path** — every method issues a live network call to production servers
(`BlueLinkApi.kt:14-20`).

Its responsibilities are narrow and single-purpose:

- Build correctly-shaped OkHttp requests (URL, method, headers, body) for each
  Blue Link endpoint: OAuth token exchange, enrollment (vehicle list), status,
  location, EV trip history, and the command set (lock/unlock, horn/lights,
  climate start/stop, charge start/stop, charge-target SOC).
- Serialize request bodies and deserialize response bodies with a lenient
  `kotlinx.serialization` `Json` instance.
- Normalize all failures into a single exception type, `BlueLinkException`
  (`BlueLinkApi.kt:488-492`), carrying an optional HTTP status `code`.
- Run every call on `Dispatchers.IO`.

It knows **nothing** about sessions, token refresh, retry-on-auth-failure,
persistence, or the app's process-wide status serialization
(`BlueLinkGate.statusMutex`). Those concerns live one layer up in
`BlueLinkRepository` (`BlueLinkRepository.kt:44`), which owns a `BlueLinkApi`
instance and supplies the `accessToken`/`username`/`pin` on every call. Kia US
runs on a completely different backend and uses `KiaUsaApi`/`KiaRepository`
instead (`Brand.kt:39-52`).

This file also defines two top-level members outside the class: the
`VehicleDetails.toVehicle()` mapper (`BlueLinkApi.kt:472-481`) and the
`BlueLinkException` class (`BlueLinkApi.kt:488-492`).

---

## 2. Public surface

### `class BlueLinkApi(private val brand: Brand = Brand.HYUNDAI)` — `BlueLinkApi.kt:21`

Constructed with a `Brand` (defaults to `HYUNDAI`). One instance can be pointed
at either Hyundai or Genesis; the brand alone selects the base URL, host, and
OAuth credentials (`BlueLinkApi.kt:23-30`). Genesis and Hyundai share the exact
same request/path structure — only the per-brand values change (`Brand.kt:1-13`).

#### Companion constants — `BlueLinkApi.kt:32-40`

- `const val UA_OKHTTP = "okhttp/3.12.0"` — User-Agent for most authenticated
  calls.
- `const val UA_POSTMAN = "PostmanRuntime/7.26.10"` — User-Agent used
  **only** for the OAuth token endpoints (`login`/`refresh`), which the real
  backend expects to see from Postman-shaped clients per the reverse-engineered
  reference clients.

#### Auth methods

**`suspend fun login(username: String, password: String): TokenResponse`** — `BlueLinkApi.kt:82-100`
POSTs a JSON body `{"username":…, "password":…}` to `$baseUrl/v2/ac/oauth/token`
with headers `Content-Type: application/json`, `client_id`, `client_secret`, and
`User-Agent: UA_POSTMAN`. Decodes the raw response into `TokenResponse`. Any
failure is normalized to `BlueLinkException` by `execute`.

**`suspend fun refresh(refreshToken: String): TokenResponse`** — `BlueLinkApi.kt:105-122`
Same endpoint family; POSTs `{"refresh_token":…}` to
`$baseUrl/v2/ac/oauth/token/refresh` with the same header set (`UA_POSTMAN`).
Returns a new `TokenResponse` without requiring the password. Note `TokenResponse.refreshToken`
can be null when the backend reuses the existing refresh token (`Models.kt:20-28`).

#### Vehicle enumeration

**`suspend fun vehicles(accessToken: String, username: String): List<Vehicle>`** — `BlueLinkApi.kt:132-145`
GETs `$baseUrl/ac/v2/enrollment/details/$username` (the username is in the URL
**path**, not just the auth token). Headers: `access_token`, `client_id`,
`Host`, `User-Agent: UA_OKHTTP`, a hardcoded `payloadGenerated` timestamp
(`"20200226171938"`), and `includeNonConnectedVehicles: "Y"` (so cars without an
active subscription still appear). Decodes `EnrollmentResponse`, then maps each
`enrolledVehicleDetails[i].vehicleDetails` through `toVehicle()`.

#### Read commands

**`suspend fun status(token, username, pin, v: Vehicle, refresh: Boolean): VehicleStatus?`** — `BlueLinkApi.kt:155-162`
GETs `/ac/v2/rcs/rvs/vehicleStatus` via `baseRequest`, adding a `REFRESH` header
set to `refresh.toString()`. `refresh=true` forces a **live poll** of the car
(slower, rate-limited); `refresh=false` returns the server's last cached status.
Returns `VehicleStatusResponse.vehicleStatus`, which is **null** when the car has
never reported a status (`Models.kt:77-80`).

**`suspend fun location(token, username, pin, v: Vehicle): GeoLocation?`** — `BlueLinkApi.kt:169-179`
GETs the dedicated (rate-limited) `/ac/v2/rcs/rfc/findMyCar` endpoint via
`baseRequest`. Reads `coord.lat`/`coord.lon`; returns **null** if either is
missing (a partial fix isn't usable), otherwise wraps into
`GeoLocation(lat, lon, speed?.value)`.

**`suspend fun tripDetails(token, username, pin, v: Vehicle): List<EvTrip>`** — `BlueLinkApi.kt:186-193`
GETs `/ac/v2/ts/alerts/maintenance/evTripDetails` via `baseRequest`, adding a
`userId` header equal to `username`. Returns `EvTripDetailsResponse.tripdetails`.
Cars whose head unit doesn't report trips return an empty list; callers treat any
failure here as "no trips" (`BlueLinkApi.kt:181-185`).

#### Write commands (all return the raw response body `String`)

**`suspend fun lock(token, username, pin, v)`** — `BlueLinkApi.kt:197-198`
Delegates to `formCommand("/ac/v2/rcs/rdo/off", …)`. Counterintuitively,
`rdo/off` **locks** ("remote door operation, off = secured").

**`suspend fun unlock(token, username, pin, v)`** — `BlueLinkApi.kt:201-202`
Delegates to `formCommand("/ac/v2/rcs/rdo/on", …)` — `on` unlocks.

**`suspend fun flashLights(token, username, pin, v)`** — `BlueLinkApi.kt:207-208`
`jsonCommand("/ac/v2/rcs/rhl/light", …)`. Flashes hazards only. Hyundai/Genesis
only — Kia has no equivalent.

**`suspend fun hornAndLights(token, username, pin, v)`** — `BlueLinkApi.kt:211-212`
`jsonCommand("/ac/v2/rcs/rhl/hnl", …)`. Flash + horn.

**`suspend fun stopClimate(token, username, pin, v): String`** — `BlueLinkApi.kt:214-223`
Picks the path by powertrain: pure EVs use `/ac/v2/evc/fatc/stop` (no engine to
cancel); ICE/PHEV use `/ac/v2/rcs/rsc/stop` (cancels remote engine start). POSTs
an **empty body** (`ByteArray(0).toRequestBody(null)`). Uses `v.isEv`, which is
true only for pure EVs (PHEVs are false — `BlueLinkApi.kt:216-217`).

**`suspend fun startCharge(token, username, pin, v): String`** — `BlueLinkApi.kt:226-231`
POSTs empty body to `/ac/v2/evc/charge/start`.

**`suspend fun stopCharge(token, username, pin, v): String`** — `BlueLinkApi.kt:234-239`
POSTs empty body to `/ac/v2/evc/charge/stop`.

**`suspend fun startClimate(token, username, pin, v, req: ClimateRequest): String`** — `BlueLinkApi.kt:242-299`
The most complex method. Builds a generation- and powertrain-specific JSON body
(details in §3) and POSTs it. Uses `callWithRetry` (one retry on transient 5xx),
unlike the other commands which use plain `call`. Temperature is Fahrenheit for
US vehicles.

**`suspend fun setChargeTargets(token, username, pin, v, acPercent: Int, dcPercent: Int): String`** — `BlueLinkApi.kt:306-329`
POSTs a `targetSOClist` JSON array to `/ac/v2/evc/charge/targetsoc/set`. **Both**
AC and DC targets must always be sent together — the endpoint takes the full
list, so you cannot update one plug type's target without re-sending the other's
current value (`BlueLinkApi.kt:301-305`). The array holds two objects:
`{plugType:0, targetSOClevel:dcPercent}` (DC fast) and
`{plugType:1, targetSOClevel:acPercent}` (AC).

### Top-level (outside the class)

**`private fun VehicleDetails.toVehicle(): Vehicle`** — `BlueLinkApi.kt:472-481`
Extension mapper flattening the nested API model into the UI-facing `Vehicle`
(see §3 for fallback logic).

**`class BlueLinkException(message, cause: Throwable? = null, val code: Int? = null): Exception`** — `BlueLinkApi.kt:488-492`
The single exception type every public method can throw. `code` holds the HTTP
status when the failure came from a server response (null for pure network/parse
failures), letting callers distinguish e.g. a 401 expired session from a generic
error.

---

## 3. Internal structure

### Shared infrastructure fields

- `baseUrl`/`host`/`clientId`/`clientSecret` — `BlueLinkApi.kt:27-30`, all
  `get()` delegating to `brand`, so the instance is fully brand-parameterized.
- `json` — `BlueLinkApi.kt:42-52` — the shared `Json` config:
  `ignoreUnknownKeys = true` (API adds fields faster than models track them),
  `coerceInputValues = true` (best-effort on type mismatches, e.g. number where
  string modeled), `isLenient = true`.
- `client` — `BlueLinkApi.kt:54-64` — one `OkHttpClient`: 30s connect timeout,
  60s read timeout, and an `HttpLoggingInterceptor` at `Level.BASIC` (request
  line + response line only, **no bodies**) piping to `AppLog.log`. BASIC level
  is deliberate so the password in the auth body is never written to the log
  (`BlueLinkApi.kt:58-59`).
- `jsonMedia = "application/json"`, `formMedia = "application/x-www-form-urlencoded"`
  — `BlueLinkApi.kt:70-71`. Two content types; lock/unlock require form-encoded,
  most others JSON. Sending the wrong one gets the body rejected.

### `baseRequest(path, token, username, pin, v)` — `BlueLinkApi.kt:369-396`

Central helper building the common authenticated header set for every command.
Returns a `Request.Builder` with URL and headers set but **no HTTP method or
body** — the caller attaches those and calls `.build()`. Key headers:

- `access_token` **and** `accessToken` — the token appears under two names; some
  endpoints (findMyCar, fatc) validate `accessToken` even though `rdo` doesn't
  (`BlueLinkApi.kt:374-377`).
- `client_id` and `clientSecret` (secret sent as a header on every command).
- `Host: host`, `User-Agent: UA_OKHTTP`.
- Vehicle identity: `registrationId: v.regId`, `gen: v.generation`, `vin: v.vin`,
  `APPCLOUD-VIN: v.vin`.
- `username`, `blueLinkServicePin: pin`.
- `offset: gmtOffsetHours()` — current GMT offset in whole hours (DST-aware),
  sourced from `FormatUtils.gmtOffsetHours()` (`FormatUtils.kt:164-172`).
- Constant routing/locale headers: `accept`, `accept-language`, `Language: "0"`,
  `language: "0"`, `to: "ISS"`, `encryptFlag: "false"`, `from: "SPA"`.
- `brandIndicator: v.brandIndicator.ifBlank { brand.code }` — uses the vehicle's
  own indicator, falling back to the brand's code letter if blank.

### `formCommand` / `jsonCommand` — `BlueLinkApi.kt:334-360`

Two shared bodies for the simple commands, both wrapping `baseRequest`:

- `formCommand` — POSTs `userName=$username&vin=${v.vin}` with `formMedia`.
  Used by lock/unlock.
- `jsonCommand` — POSTs `{"userName":…, "vin":…}` with `jsonMedia`. Used by
  horn/lights, which reject the form-encoded body lock/unlock uses.

Note the field name is **`userName`** (capital N) in these bodies, distinct from
the lowercase `username` header in `baseRequest`.

### `startClimate` body construction — `BlueLinkApi.kt:242-299`

Control flow, step by step:

1. Define local `seatInfo()` — `BlueLinkApi.kt:251-256` — builds a JSON object
   with `drvSeatHeatState`/`astSeatHeatState`/`rlSeatHeatState`/`rrSeatHeatState`
   from the four `SeatLevel.apiValue` fields on `req`.
2. `val isEv = v.isEv`; `val gen3 = v.generation.trim() == "3"` (`BlueLinkApi.kt:257-258`).
3. Path: EVs → `/ac/v2/evc/fatc/start`; ICE/PHEV → `/ac/v2/rcs/rsc/start`
   (`BlueLinkApi.kt:259`).
4. Build the payload:
   - **EV branch** (`BlueLinkApi.kt:263-275`): minimal body — `airCtrl:1`,
     `airTemp:{value:"<tempF>", unit:1}` (value is a **string**),
     `defrost:<bool>`, `heating1:<0|1>` (steering-wheel heat). Only if `gen3`,
     additionally `igniOnDuration:<durationMinutes>` and
     `seatHeaterVentInfo:<seatInfo()>`. Newer head units (Gen5W) 502 if the body
     is bloated, so EV bodies are kept minimal and seat/duration are gen-3-only
     (`BlueLinkApi.kt:245-250, 271`).
   - **ICE/PHEV branch** (`BlueLinkApi.kt:276-289`): fuller body — `Ims:0`,
     `airCtrl:1`, `airTemp:{unit:1, value:"<tempF>"}`, `defrost`, `heating1`,
     `igniOnDuration`, `seatHeaterVentInfo`, plus `username` and `vin` (the last
     three plus `Ims` are ICE-only fields).
5. POST via `baseRequest(path, …)` and call `callWithRetry(request)`
   (`BlueLinkApi.kt:293-298`).

### `VehicleDetails.toVehicle()` — `BlueLinkApi.kt:472-481`

Flattens and fills fallbacks:
- `vin = vin`, `regId = regid`.
- `name` = `nickName` (if non-blank) → `modelName` → last 6 VIN chars.
- `model` = `"$modelYear $modelName"` joined (non-null parts), or `"Hyundai"`
  if blank.
- `generation` = `vehicleGeneration ?: "2"` (2 is the most common in sample data).
- `brandIndicator` = `brandIndicator ?: ""`.
- `isEv` = `evStatus.equals("E", ignoreCase = true)` — a single-char code
  ("E" = electric, case-insensitive).
- `odometer = odometer`.

### Plumbing: `call`, `callWithRetry`, `friendlyError`, `execute`

**`private fun call(request): String`** — `BlueLinkApi.kt:423-433`
Executes synchronously (must be off main thread — all callers route through
`execute`'s `Dispatchers.IO`). Uses `.use { }` so the response body is always
closed even on exception. Reads body text; on non-2xx, extracts a friendly
message via `friendlyError`, logs `ERROR <code> <method> <path>: <message>`
(never headers/body verbatim, so tokens/PINs in request headers aren't logged —
`BlueLinkApi.kt:416-422`), and throws `BlueLinkException(message, code=resp.code)`.
On success returns the raw body string.

**`private suspend fun callWithRetry(request): String`** — `BlueLinkApi.kt:404-414`
Wraps `call`; catches `BlueLinkException`. If the code is a transient 5xx
(`code in 500..599`), logs a retry line, `delay(1500)`, and calls once more.
Non-transient (or null-code) errors rethrow immediately. Only `startClimate`
uses this, because Hyundai occasionally 502s a valid HVAC call.

**`private fun friendlyError(code, body): String`** — `BlueLinkApi.kt:436-446`
Parses `body` as JSON, pulls `errorMessage` (fallback `errorSubMessage`) as a
`JsonPrimitive.content`, wrapped in `runCatching`. Returns that message if
non-blank, else `"Request failed (HTTP $code)"`.

**`private suspend fun <T> execute(block): T`** — `BlueLinkApi.kt:455-463`
Runs `block` inside `withContext(Dispatchers.IO)`. A `BlueLinkException` thrown
deeper (e.g. by `call`) rethrows unchanged so its `code`/message survive; any
**other** `Exception` (IOException, SerializationException…) is wrapped into
`BlueLinkException(e.message ?: "Network error", e)` (no code). Every public
method wraps its work in `execute`, giving callers exactly one exception type.

---

## 4. Data & types

### Defined in this file

**`BlueLinkException(message: String, cause: Throwable? = null, val code: Int? = null)`** — `BlueLinkApi.kt:488-492`
Extends `Exception(message, cause)`. `code` = HTTP status when server-sourced,
null for pure network/parse failures.

### Consumed models (defined in `Models.kt`, relevant to this client)

- **`TokenResponse`** — `Models.kt:22-28` — `@Serializable`; `accessToken`
  (`@SerialName("access_token")`), `refreshToken` (`refresh_token`, nullable),
  `expiresIn` (`expires_in`, nullable String), `tokenType` (`token_type`,
  nullable). Login and refresh share this shape.
- **`EnrollmentResponse`** — `Models.kt:35-37` — `enrolledVehicleDetails: List<EnrolledVehicle> = emptyList()`.
- **`EnrolledVehicle`** — `Models.kt:43-45` — one wrapper layer: `vehicleDetails: VehicleDetails`.
- **`VehicleDetails`** — `Models.kt:48-59` — raw API vehicle: `vin`, `regid`,
  `nickName?`, `modelName?`, `modelYear?`, `vehicleGeneration?`, `brandIndicator?`,
  `enrollmentDate?`, `evStatus?` (single-char code), `odometer?`.
- **`Vehicle`** — `Models.kt:62-71` — flattened UI model (NOT `@Serializable`):
  `vin`, `regId`, `name`, `model`, `generation`, `brandIndicator`, `isEv: Boolean`,
  `odometer? = null`.
- **`VehicleStatusResponse`** — `Models.kt:78-80` — `vehicleStatus: VehicleStatus? = null`.
- **`VehicleStatus`** — `Models.kt:83-122` — large status payload (door/engine/
  climate/EV/diagnostics). Field-by-field detail lives in the `Models.md`
  deep-dive; relevant here only as the return type of `status()`. Note the
  computed `isDriving: Boolean` = `(vehicleLocation?.speed?.value ?: 0.0) > 0.0`
  (`Models.kt:121`).
- **`VehicleLocationResponse`** — `Models.kt:377-381` — `coord: Coord?`,
  `head: Double?` (compass heading), `speed: Speed?`. Returned by findMyCar.
- **`Coord`** — `Models.kt:394-398` — `lat?`, `lon?`, `alt?`.
- **`Speed`** — `Models.kt:404-407` — `value: Double?`, `unit: Int?`.
- **`GeoLocation`** — `Models.kt:411-416` — UI-facing: `latitude`, `longitude`
  (both non-null), `speed: Double? = null` (>0 implies moving).
- **`EvTrip`** / **`EvTripDetailsResponse`** — `Models.kt:489-537` — trip
  history rows; `EvTripDetailsResponse.tripdetails: List<EvTrip> = emptyList()`.
- **`ClimateRequest`** — `Models.kt:352-361` — `@Serializable`: `tempF: Int`,
  `defrost: Boolean`, `durationMinutes: Int`, `steeringWheelHeat: Boolean = false`,
  and four `SeatLevel` fields (`seatFrontLeft/Right`, `seatRearLeft/Right`, each
  defaulting to `SeatLevel.OFF`).
- **`SeatLevel`** enum — `Models.kt:310-338` — `apiValue: Int` encoding used in
  the climate body: `OFF=0`, `LOW_COOL=3`/`MED_COOL=4`/`HIGH_COOL=5`,
  `LOW_HEAT=6`/`MED_HEAT=7`/`HIGH_HEAT=8`. Crosses the wear wire as ints.

### Encodings this client hard-codes

- **`plugType`** in `setChargeTargets`: `0 = DC fast`, `1 = AC`
  (`BlueLinkApi.kt:301, 314, 319`). This matches `TargetSOC`/`ReservChargeInfos`
  but is a **different scheme** from `EvStatus.batteryPlugin` (0 unplugged,
  1 DC, 2 AC — `Models.kt:239-247`).
- **`airTemp.unit: 1`** and **`airCtrl: 1`** in the climate body
  (`BlueLinkApi.kt:264-267, 278-282`). `airTemp.value` is sent as a **quoted
  string** (`req.tempF.toString()`).
- **`heating1`** = steering-wheel heat as `1`/`0` (`BlueLinkApi.kt:270, 284`).
- **`Ims: 0`** — ICE-only field (`BlueLinkApi.kt:277`).
- **`REFRESH`** header stringified boolean (`BlueLinkApi.kt:159`).

### `Brand` enum (from `Brand.kt`, selects endpoints)

`HYUNDAI` (code "H", `api.telematics.hyundaiusa.com`), `GENESIS` (code "G",
`api.genesis.telematics.hyundaiusa.com`) — both served by this client. `KIA`
(code "K") is served elsewhere. Each carries `code`, `baseUrl`, `host`,
`clientId`, `clientSecret`, `label` (`Brand.kt:14-52`).

---

## 5. State & concurrency

- **Stateless per request.** `BlueLinkApi` holds no mutable state. Its only
  fields are the immutable `brand`, derived config getters, the shared `json`
  config, one reusable `OkHttpClient`, and two `MediaType` constants. Every
  method takes its `token`/`username`/`pin`/`Vehicle` as parameters — nothing is
  cached between calls.
- **Dispatcher:** every public method funnels through `execute`, which runs on
  `withContext(Dispatchers.IO)` (`BlueLinkApi.kt:455`). `call` executes the
  OkHttp request synchronously inside that IO context.
- **No StateFlow/DataStore/remember** in this file. The one flow-bearing
  collaborator is `AppLog` (`AppLog.kt:14-49`), which exposes a `StateFlow<List<String>>`
  the Settings screen collects; this client only *writes* to it via `AppLog.log`.
  `AppLog.log` is internally `synchronized(this)` and bounded to a 500-line ring
  buffer.
- **No locks held here.** The process-wide `BlueLinkGate.statusMutex` that
  serializes all status/command calls (the backend 502s on overlapping requests
  for one account) is enforced **above** this class, in the repository layer, not
  inside `BlueLinkApi`. This client makes no attempt to serialize concurrent
  callers itself.
- **OkHttp reuse:** the single `OkHttpClient` (with its connection pool and
  dispatcher) is shared across all calls on the instance, which is the intended
  OkHttp usage pattern.

---

## 6. Collaborators & data flow

**Called by:** `BlueLinkRepository` (`BlueLinkRepository.kt:44-150`), which owns
a `BlueLinkApi` (built via `repositoryFor(...)` → `BlueLinkApi(brand)` at
`BlueLinkRepository.kt:38`). The repository:
- Passes session fields (`s.accessToken`, `s.username`, `s.pin`) into every call
  (`BlueLinkRepository.kt:57-150`).
- Adds the token-refresh-and-retry-on-auth-failure logic and (via
  `withSession`) the higher-level session gating. `login`/`refresh` feed the
  `SessionStore`.
- Stamps `brandIndicator = brand.code` onto returned vehicles
  (`BlueLinkRepository.kt:75`).

**Calls out to:**
- `okhttp3` (`OkHttpClient`, `Request`, `RequestBody`, `HttpLoggingInterceptor`).
- `kotlinx.serialization` `Json` for encode/decode.
- `AppLog.log(...)` (`AppLog.kt`) — HTTP log lines + error/retry lines.
- `gmtOffsetHours()` (`FormatUtils.kt:164-172`) — for the `offset` header.
- `Brand` (`Brand.kt`) — for base URL / host / credentials / code.
- Model types in `Models.kt` for (de)serialization.

**Data in:** `username`, `password`, `pin`, `accessToken`/`refreshToken`,
`Vehicle`, `ClimateRequest`, charge percentages — all as function args.
**Data out:** `TokenResponse`, `List<Vehicle>`, `VehicleStatus?`, `GeoLocation?`,
`List<EvTrip>`, and raw `String` command responses — all as return values; plus
side-effect log lines to `AppLog`. **No DataStore, no Wear Data Layer paths, no
intents, no WorkManager** are touched directly by this file.

---

## 7. Invariants & assumptions

- **Must run off the main thread.** `call` executes synchronously; the guarantee
  it runs on IO comes entirely from every caller going through `execute`
  (`BlueLinkApi.kt:416-422, 455`). Calling `call` outside `execute` would block
  the calling thread and could NetworkOnMainThreadException.
- **`brandIndicator` is only "H" or "G" here.** This client is for
  Hyundai/Genesis; Kia vehicles must not be routed through it (they use
  `KiaUsaApi`).
- **`v.isEv` is true only for pure EVs** — PHEVs report false, which is why
  `stopClimate`/`startClimate` pick the ICE path for PHEVs (`BlueLinkApi.kt:216-217`).
- **EV climate body must stay minimal on modern head units.** Sending
  Ims/username/vin/seat info to a Gen5W EV yields a 502 "could not complete your
  request" (`BlueLinkApi.kt:245-250`). Seat heat + duration are only honored on
  gen-3 EVs.
- **`setChargeTargets` must always send both plug targets.** The endpoint
  replaces the whole list; omitting one wipes/ignores it (`BlueLinkApi.kt:301-305`).
- **Correct content type per endpoint.** Lock/unlock require form-urlencoded;
  the rest require JSON — mismatch = rejected body (`BlueLinkApi.kt:66-71`).
- **`login`/`refresh` require `UA_POSTMAN`; everything else uses `UA_OKHTTP`.**
- **Non-null coordinate assumption in `location`:** returns null unless both lat
  and lon are present.
- **`status` can legitimately return null** (car never reported) — callers must
  handle it.
- **`friendlyError` assumes the error body may or may not be JSON** — it is fully
  `runCatching`-guarded and always yields a non-null string.
- **`code in 500..599` retry only** — `callWithRetry` will not retry a 4xx or a
  null-code (network/parse) failure.

---

## 8. Gotchas & sharp edges

- **`rdo/off` locks, `rdo/on` unlocks** — the endpoint naming is inverted from
  intuition ("off = secured") (`BlueLinkApi.kt:196-202`).
- **Token sent under two header names** (`access_token` and `accessToken`) and
  the secret as `clientSecret` on every command — some endpoints (findMyCar,
  fatc) validate the extra names even though `rdo` doesn't (`BlueLinkApi.kt:374-379`).
- **Body field `userName` (capital N) vs header `username` (lowercase)** — both
  appear on the same request in `formCommand`/`jsonCommand` (`BlueLinkApi.kt:337, 352`).
- **`airTemp.value` is a quoted string, not a number** (`req.tempF.toString()`) —
  and the EV vs ICE bodies order `value`/`unit` differently (cosmetic, but
  mirrors the reference client byte-for-byte) (`BlueLinkApi.kt:265-268, 279-282`).
- **Only `startClimate` retries** (`callWithRetry`); every other command uses
  plain `call` and fails on the first 5xx. HVAC is singled out because Hyundai
  502s valid HVAC calls occasionally (`BlueLinkApi.kt:296-298, 400-403`).
- **`generation` gate is a trimmed string equality** (`v.generation.trim() == "3"`)
  — not a numeric comparison; a `"3 "` with whitespace is handled by `trim()`,
  but `"4"`, `"5"` etc. take the non-gen3 path (no seat/duration on EV)
  (`BlueLinkApi.kt:258`).
- **Empty-body POSTs use `ByteArray(0).toRequestBody(null)`** for
  stop-climate/charge start/stop — null media type is intentional
  (`BlueLinkApi.kt:220, 228, 236`).
- **Logging is deliberately BASIC-level** so bodies (which contain the password
  on login) never hit the log; `call`'s error log also prints only path + a
  parsed message, never raw headers/body, keeping tokens/PINs out of `AppLog`
  (`BlueLinkApi.kt:58-59, 416-422`).
- **`payloadGenerated` is a hardcoded 2020 timestamp** on the vehicles call
  (`"20200226171938"`) — a fixed magic value carried over from the reference
  clients, not a live timestamp (`BlueLinkApi.kt:140`).
- **`toVehicle()` generation default is `"2"`** — a car with no reported
  generation is assumed Gen5W-era, which affects the climate-body branch
  downstream (`BlueLinkApi.kt:478`, `Brand.kt:149-150`).
- **Genesis credentials are community-derived** and centralized in `Brand.kt`
  for one-line correction if Genesis rotates them (`Brand.kt:8-13`).
- **`plugType` numbering trap:** DC=0/AC=1 here in `setChargeTargets`, but
  `EvStatus.batteryPlugin` uses 0=unplugged/1=DC/2=AC — a different scheme.
  Mixing them up would set the wrong charger's target (`BlueLinkApi.kt:301`,
  `Models.kt:239-247, 273-283`).
