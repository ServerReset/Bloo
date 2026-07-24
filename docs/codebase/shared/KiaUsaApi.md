# KiaUsaApi — Kia Connect (Kia US) telematics client

**File:** `shared/src/main/java/com/bloo/bluelink/data/KiaUsaApi.kt`
**Package:** `com.bloo.bluelink.data`

---

## 1. Purpose

`KiaUsaApi` is the low-level HTTP client for the **Kia US "Kia Connect"** telematics
backend (`api.owners.kia.com`). It is a completely separate backend from the
Hyundai/Genesis US telematics API (`api.telematics.hyundaiusa.com`) that
`BlueLinkApi` talks to — different host, different auth model, different request
shapes — so it gets its own client class. `Brand.KIA` routes to
`KiaUsaApi` + `KiaRepository`, whereas Hyundai/Genesis route to `BlueLinkApi` +
`BlueLinkRepository` (see `Brand.kt:39-52`, `KiaRepository.kt:1-12`).

It faithfully mirrors the community `hyundai_kia_connect_api` project's
`KiaUvoApiUSA` implementation (comments at `KiaUsaApi.kt:66-68`, `326`). The
defining traits of this backend:

- **OTP-gated login.** Username/password may return a one-time-code challenge
  (email or SMS) that must be solved before a session is issued.
- **Session model:** a login yields a `sid` (session id, sent as the `sid`
  header) plus a reusable `rmtoken` (remember-me token) that allows *silent*
  re-authentication (no OTP) when the session later expires. The `rmtoken` is
  **bound to a stable `deviceId`** which must be persisted and reused across
  logins.
- **Commands are keyed by `sid` + `vinkey`** (a session-specific per-vehicle
  key), not by a service PIN like Hyundai/Genesis.

This class is stateless with respect to sessions: every method takes a
`KiaSession` (and usually a `KiaVehicleSummary`) as an argument. Session
persistence, expiry-driven re-auth, and vinkey caching all live one layer up in
`KiaRepository`.

`KiaUsaApi` is a **pure transport layer** — it builds requests, parses
responses into shared domain models (`VehicleStatus` etc. from `Models.kt`), and
throws `BlueLinkException` on failure. It holds no vehicle state and performs no
persistence.

---

## 2. Public surface

### Top-level types (file scope, outside the class)

**`data class KiaSession`** (`KiaUsaApi.kt:35-40`)
```kotlin
data class KiaSession(
    val sid: String,
    val rmtoken: String?,
    val deviceId: String,
    val pin: String?,
)
```
Represents a Kia US session. `sid` = session token (sent as the `sid` header);
`rmtoken` = re-auth token for silent re-login (nullable — may not have been
issued); `deviceId` = the stable UUID the `rmtoken` is bound to (must not change
across logins); `pin` = the service PIN (carried through, though Kia's command
flow does not actually use a PIN the way Hyundai/Genesis does).

**`data class KiaVehicleSummary`** (`KiaUsaApi.kt:43-49`)
```kotlin
data class KiaVehicleSummary(
    val id: String,      // vehicleIdentifier (the VIN); our Vehicle.vin
    val name: String,    // nickname / model name / last 6 of id
    val model: String,   // modelName, else "Kia"
    val key: String,     // "vinkey" (vehicleKey) — session-specific, sent as vinkey header
    val isEv: Boolean,   // fuelType == 4
)
```
One car on the account. `key` (the "vinkey") is **session-specific and refreshed
on every login** (comment at `:42`) — this is why `KiaRepository` re-fetches
summaries after each re-auth.

**`sealed interface KiaAuth`** (`KiaUsaApi.kt:52-62`) — the outcome of a login
attempt:
- **`KiaAuth.LoggedIn(val session: KiaSession)`** — ready session; done.
- **`KiaAuth.OtpRequired(otpKey, xid, email, sms, hasEmail, hasSms)`** — an OTP
  challenge to solve. `otpKey`/`xid` are opaque handles carried into the OTP
  steps; `email`/`sms` are (masked) destinations; `hasEmail`/`hasSms` say which
  channels are available.

### `class KiaUsaApi` (`KiaUsaApi.kt:70`)

#### Companion (`KiaUsaApi.kt:72-84`)
- `val BASE = Brand.KIA.host` → `"api.owners.kia.com"`
- `val API = "${Brand.KIA.baseUrl}/apigw/v1/"` → `"https://api.owners.kia.com/apigw/v1/"`
- `private val CLIENT_ID = Brand.KIA.clientId` → `"SPACL716-APL"`
- `private val SECRET_KEY = Brand.KIA.clientSecret` → `"sydnat-9kykci-Kuhtep-h5nK"`
- `private const val USER_AGENT = "KIAPrimo_iOS/37 CFNetwork/1335.0.3.4 Darwin/21.6.0"`
  — spoofs a real iOS Kia Connect build; the API keys some behavior off a
  recognized UA (`:78-80`).
- **`fun newDeviceId(): String`** (`:83`) — `UUID.randomUUID().toString().uppercase(Locale.US)`.
  Produces a fresh stable device id; caller must persist it (the rmtoken binds
  to it).

#### Auth methods

**`suspend fun authUser(username, password, deviceId, rmtoken: String?, pin: String?): KiaAuth`**
(`:145-185`)
Step 1 of login. POSTs to `prof/authUser`. Returns `KiaAuth.LoggedIn` if the
response carries a `sid` header (silent re-auth succeeded or no OTP needed), or
`KiaAuth.OtpRequired` if the payload carries an `otpKey`. Throws
`BlueLinkException` otherwise. If `rmtoken` is supplied it is sent as the
`rmtoken` header to attempt silent re-auth.

**`suspend fun sendOtp(otpKey, notifyType: String, xid, deviceId)`** (`:188-196`)
Step 2a. POSTs empty `{}` to `cmm/sendOTP` with `otpkey`/`notifytype`/`xid`
headers. `notifyType` is `"EMAIL"` or `"SMS"`. Returns `Unit`; throws on failure
(via `call`).

**`suspend fun verifyOtpAndComplete(username, password, otpCode, otpKey, xid, deviceId, pin: String?): KiaSession`**
(`:199-229`)
Step 2b. Two-request flow: (1) POST `otp` to `cmm/verifyOTP` → yields interim
`sid` + `rmtoken` from response headers; (2) POST credentials again to
`prof/authUser` (with interim sid + rmtoken headers) → yields the final `sid`.
Returns a `KiaSession(finalSid, rmtoken, deviceId, pin)`.

#### Vehicles

**`suspend fun vehicles(session: KiaSession): List<KiaVehicleSummary>`** (`:238-254`)
GET `ownr/gvl` (get vehicle list). Walks `payload.vehicleSummary`, dropping any
entry with no `vehicleIdentifier` (via `mapNotNull`). `fuelType == 4` ⇒ pure EV.

#### Status / location

**`suspend fun status(session, vehicle): VehicleStatus?`** (`:269-294`)
POSTs to `cmm/gvi` (get vehicle info) opting into `location`+`vehicleStatus`,
opting out of weather/functionalCards. Reads `payload.vehicleInfoList[0]`
(returns `null` if empty/missing), parses via `parseStatus`, and for EVs merges
in AC/DC charge targets from a best-effort second call (`chargeTargets`).

**`suspend fun forceRefresh(session, vehicle)`** (`:314-320`)
POST `{requestType:0}` to `rems/rvs` — tells the car to phone home with fresh
data (async; returns once accepted). Returns `Unit`.

#### Commands

- **`suspend fun lock(session, v)`** (`:417`) → GET `rems/door/lock`
- **`suspend fun unlock(session, v)`** (`:418`) → GET `rems/door/unlock`
- **`suspend fun stopClimate(session, v)`** (`:419`) → GET `rems/stop`
- **`suspend fun stopCharge(session, v)`** (`:420`) → GET `evc/cancel`
- **`suspend fun startCharge(session, v)`** (`:425-427`) → POST `evc/charge`
  with `{chargeRatio:100}`. `chargeRatio` is fixed at 100; the actual AC/DC
  charge *limits* are set separately via `setChargeTargets`.
- **`suspend fun setChargeTargets(session, v, ac: Int, dc: Int)`** (`:434-444`) →
  POST `evc/sts` with a `targetSOClist` containing **both** targets (plugType
  0=DC with `dc`, plugType 1=AC with `ac`). Both must be sent together — the
  endpoint has no single-target update.
- **`suspend fun startClimate(session, v, req: ClimateRequest)`** (`:454-485`) →
  POST `rems/start` with a `remoteClimate` body. See §3 for the temp-sentinel
  and seat logic.

All command methods return `Unit` and throw `BlueLinkException` on any failure.

---

## 3. Internal structure & control flow

### Header builders

**`rfc1123Date(): String`** (`:104-107`) — formats "now" as an RFC 1123 GMT date
string (`"EEE, dd MMM yyyy HH:mm:ss 'GMT'"`, `Locale.US`, `GMT` timezone). This
is the exact format HTTP's own `Date` header uses; Kia expects it in the custom
`date` header on every request.

**`Request.Builder.apiHeaders(deviceId): Request.Builder`** (`:113-134`) —
extension adding the full baseline header set every call needs regardless of
endpoint: content-type/accept negotiation, `apptype=L`, `appversion=7.22.0`,
`clientid=CLIENT_ID`, `clientuuid=uuid5FromDns(deviceId)`, `from=SPA`,
`Host=BASE`, `language=0`, `offset=gmtOffsetHours()`, `ostype=iOS`,
`osversion=15.8.5`, `phonebrand=iPhone`, `secretkey=SECRET_KEY`, `to=APIGW`,
`tokentype=A`, the spoofed `User-Agent`, a fresh `date`, and `deviceid`.
Note `clientuuid` is a **deterministic v5 UUID derived from `deviceId`**, while
`deviceid` is the raw device id.

**`Request.Builder.authedHeaders(session, vehicle): Request.Builder`** (`:139-140`) —
`apiHeaders(session.deviceId)` plus `sid` (session) and `vinkey` (vehicle.key).
Used by every authenticated call (status, commands).

### `authUser` control flow (`:145-185`)
1. Build JSON body: `deviceKey=deviceId`, `deviceType=2`,
   `userCredential={userId, password}`, `tncFlag=1`.
2. POST to `prof/authUser` with `apiHeaders`, optionally adding the `rmtoken`
   header if a token was supplied.
3. `raw(req).use { ... }` — read body text and the `sid` response header.
4. **If `sid != null`** → silent auth / no-OTP path succeeded. Compute
   `freshRmtoken = resp.header("rmtoken") ?: rmtoken` — prefer a server-rotated
   rmtoken, falling back to the caller's. Return `KiaAuth.LoggedIn`.
5. **Else** parse the body, read `payload.otpKey`. If present, return
   `KiaAuth.OtpRequired` filling `xid` from the `xid` header, and
   `email`/`sms`/`hasEmail`/`hasSms` from the payload (`phone`→sms,
   `hasPhone`→hasSms).
6. **Else** throw `BlueLinkException(friendly(code, text), code)`.

Uses `raw()` directly (not `call()`) because success is signaled by the presence
of the `sid` header, not by the JSON `status.statusCode` — and because an OTP
challenge is a *non-error* outcome that `call()` would otherwise reject.

### `verifyOtpAndComplete` control flow (`:199-229`)
1. POST `{otp: otpCode}` to `cmm/verifyOTP` with `otpkey`/`xid` headers, using
   `raw()`. Read `sid` + `rmtoken` response headers; if either is null throw
   `"Invalid code — please try again."` (code = resp.code). → `(interimSid, rmtoken)`.
2. POST credentials again to `prof/authUser` with `sid=interimSid` +
   `rmtoken=rmtoken` headers (note: body here omits `tncFlag`, unlike
   `authUser`). Read the `sid` response header → `finalSid` (throw `friendly`
   error if absent).
3. Return `KiaSession(finalSid, rmtoken, deviceId, pin)`. The rmtoken kept is the
   one from step 1's verifyOTP response.

### `status` control flow (`:269-294`)
1. Build the `cmm/gvi` request body with three sub-objects: `vehicleConfigReq`
   (mostly opting out — only `maintenance`/`vehicle` on), `vehicleInfoReq`
   (opting into `dtc`/`enrollment`/`location`/`vehicleStatus`, out of
   `drivingActivty`/`functionalCards`/`weather`), and `vinKey` as a
   single-element array.
2. POST to `cmm/gvi` with `authedHeaders`; run via `call()`.
3. Extract `payload.vehicleInfoList[0]`; return `null` if empty/missing.
4. `parseStatus(info)` → `VehicleStatus`.
5. If `parsed.evStatus == null`, return as-is. Otherwise fetch
   `chargeTargets(session, vehicle)` inside `runCatching{}.getOrNull()`; if
   non-null, `parsed.copy(evStatus = ev.copy(reservChargeInfos = targets))`.

### `chargeTargets` (private, `:300-311`)
GET `evc/gts`. Reads `payload.targetSOClist`, maps each entry to
`TargetSOC(plugType, targetSOClevel)` — **dropping entries with `level == 0`**
("not reported yet" per the community client). Returns `null` when the list is
absent or all-empty.

### `parseStatus` (private, `:327-410`)
Maps `cmm/gvi`'s `vehicleInfoList[0]` onto the shared `VehicleStatus`. Field
paths follow `KiaUvoApiUSA._update_vehicle_properties`. Structure:
- Navigates to `lastVehicleInfo.vehicleStatusRpt.vehicleStatus` (`vs`), then
  sub-objects `climate`, `climate.heatingAccessory` (`heat`), `doorStatus`
  (`doors`), `seatHeaterVentState` (`seats`), `evStatus` (`ev`), and
  `lastVehicleInfo.location` (`location`).
- `lat`/`lon` read from `location.coord.lat`/`.lon`.
- **Local `fun window(key, evKey)`** (`:339-340`): ICE cars report windows under
  `vs.windowOpen.<key>`, EVs under `ev.windowStatus.<evKey>` — tries the former,
  falls back to the latter.
- **`evStatus`** built only if `ev != null` (see §4 for fields). Range read from
  `ev.drvDistance[0].rangeByFuel.totalAvailableRange`, falling back to
  `evModeRange`; charge-time estimates from `ev.remainChargeTime[0]`
  (`timeInterval`→atc, `etc1`, `etc3`), each wrapped as `TimeValue(value, 1)`;
  `RemainTime2` is nulled if all three estimates are absent.
- Returns a fully populated `VehicleStatus` (doorLock/airCtrlOn/engine/defrost
  as `flag()`; doors/windows as ints; tire pressure; airTemp; 12V battery;
  comfort heaters; seat states; diagnostics; fuelLevel; dte; dateTime from
  `syncDate.utc`; evStatus; vehicleLocation only if both lat and lon present).

### `seatSettings(level: Int): JsonObject` (private, `:488-497`)
Translates a shared `SeatLevel.apiValue` into Kia's `{heatVentType, heatVentLevel,
heatVentStep}` triple. See §4 for the full mapping table.

### Command plumbing
- **`getCommand(path, session, v)`** (`:503-507`) — GET with `authedHeaders`, run
  via `call()`, discard result. Wrapped in `withContext(Dispatchers.IO)`.
- **`postCommand(path, session, v, body)`** (`:514-518`) — POST `body` with
  `authedHeaders`, run via `call()`. **Synchronous** — callers
  (`startCharge`/`setChargeTargets`/`startClimate`) supply their own
  `withContext(Dispatchers.IO)`.

### Response plumbing
- **`call(request): JsonElement`** (`:528-549`) — the standard path for calls
  whose success is JSON-based. See §8 for the two-layer error handling.
- **`raw(request): Response`** (`:551`) — `client.newCall(request).execute()`.
- **`friendly(code, body): String`** (`:553-559`) — best-effort extraction of a
  human message from `status.errorMessage` or top-level `errorMessage`; falls
  back to `"Kia request failed (HTTP $code)"`.
- **`parseJson(text, code): JsonElement`** (`:567-569`) — parse text to JSON,
  converting parse failure into `BlueLinkException(friendly(code, text), code)`
  so a WAF HTML page / gateway 5xx / truncated body never crashes the app.

### JSON tree helpers (`:581-606`)
Because Kia payloads are deeply nested and inconsistently shaped, the parser
walks the raw `JsonElement` tree with null-safe cast helpers rather than
`@Serializable` models:
- `JsonElement?.obj()` → `JsonObject?`
- `JsonElement?.str()` → `String?` — treats the literal string `"null"` as absent
- `JsonElement?.int()` / `.dbl()` / `.bool()` → primitive-or-null
- `JsonElement?.flag()` → `Boolean?` — tolerates both `true/false` and `0/1`
  (int → `v != 0`)
- `JsonElement?.path(vararg keys)` → descends objects by key; **numeric string
  keys index into arrays** (`"0"` → `getOrNull(0)`); returns null on any miss.

### `uuid5FromDns(name): String` (`:609-623`)
Computes an RFC 4122 v5 (name-based, SHA-1) UUID in the DNS namespace to match
the iOS app's `clientuuid`. Hard-codes the DNS namespace bytes
(`6ba7b810-9dad-11d1-80b4-00c04fd430c8`), SHA-1 hashes namespace + UTF-8 name,
sets the version nibble to 5 (`h[6]`) and the variant bits (`h[8]`), then formats
the first 16 bytes as a dashed UUID string.

---

## 4. Data & types

### `KiaSession` (`:35-40`)
| field | type | meaning |
|---|---|---|
| `sid` | `String` | session token → `sid` header |
| `rmtoken` | `String?` | silent-reauth token; bound to `deviceId` |
| `deviceId` | `String` | stable UUID; rmtoken binds to it; must persist |
| `pin` | `String?` | service PIN, carried through (unused by Kia commands) |

### `KiaVehicleSummary` (`:43-49`)
| field | type | source / encoding |
|---|---|---|
| `id` | `String` | `vehicleIdentifier` (the VIN) |
| `name` | `String` | `nickName` ?: `modelName` ?: `id.takeLast(6)` |
| `model` | `String` | `modelName` ?: `"Kia"` |
| `key` | `String` | `vehicleKey` (the "vinkey") ?: `""` — session-specific |
| `isEv` | `Boolean` | `fuelType == 4` |

### `KiaAuth` (sealed, `:52-62`)
- `LoggedIn(session: KiaSession)`
- `OtpRequired(otpKey: String, xid: String, email: String?, sms: String?, hasEmail: Boolean, hasSms: Boolean)`

### Shared models produced/consumed (defined in `Models.kt`)
`status`/`parseStatus` produce a **`VehicleStatus`** (`Models.kt:82-122`) and its
nested types. Kia-relevant encodings this file relies on:

- **`EvStatus`** (`Models.kt:230-247`): `batteryCharge: Boolean?`,
  `batteryStatus: Int?` (charge %), `batteryPlugin: Int?`
  (**0=unplugged, 1=DC fast, 2=AC** — different from `plugType`),
  `drvDistance: List<DrvDistance>`, `remainTime2: RemainTime2?`,
  `reservChargeInfos: ReservChargeInfos?` (merged in by `status`).
- **`RemainTime2`** (`Models.kt:253-258`): `atc` (current plug estimate),
  `etc1`/`etc3` (separate AC/DC estimates), each a `TimeValue`.
- **`TargetSOC`** (`Models.kt:284-288`): `plugType: Int?` (**0=DC fast, 1=AC**),
  `targetSOClevel: Int?`. Note this plug scheme differs from `batteryPlugin`.
- **`ReservChargeInfos`** (`Models.kt:270-279`): wraps `targetSOClist`; `.level(plugType)`
  linear-searches by plug type.

### `ClimateRequest` (consumed, `Models.kt:352-361`)
`tempF: Int`, `defrost: Boolean`, `durationMinutes: Int`, `steeringWheelHeat:
Boolean`, four `SeatLevel` fields (default `OFF`).

### `SeatLevel.apiValue` → Kia `seatSettings` mapping (`:488-497`)
`SeatLevel.apiValue` encoding (`Models.kt:310-317`): 0=off, 3=LOW_COOL, 4=MED_COOL,
5=HIGH_COOL, 6=LOW_HEAT, 7=MED_HEAT, 8=HIGH_HEAT. In Kia's body `heatVentType`
1=heat, 2=cool, 0=off:

| `apiValue` | meaning | heatVentType | heatVentLevel | heatVentStep |
|---|---|---|---|---|
| 8 (high heat) | heat | 1 | 4 | 1 |
| 7 (med heat) | heat | 1 | 3 | 2 |
| 6 (low heat) | heat | 1 | 2 | 3 |
| 5 (high cool) | cool | 2 | 4 | 1 |
| 4 (med cool) | cool | 2 | 3 | 2 |
| 3 (low cool) | cool | 2 | 2 | 3 |
| 1 | heat | 1 | 4 | 1 |
| else (0/off) | off | 0 | 1 | 0 |

Note: `apiValue == 1` is not a value any `SeatLevel` enum entry produces (the
enum has no 1 or 2); it is a defensive branch treated as max heat.

### `startClimate` body shape (`:462-483`)
`remoteClimate` object: `airTemp = {unit:1, value:tempValue}` (see §8 for
LOW/HIGH sentinels), `airCtrl=true`, `defrost=req.defrost`, `heatingAccessory`
(rearWindow/sideMirror = 1 if defrost else 0; steeringWheel & steeringWheelStep =
1 if `steeringWheelHeat` else 0), `ignitionOnDuration = {unit:4, value:durationMinutes}`,
and — **only if any seat ≠ OFF** — `heatVentSeat` with driver/passenger/rearLeft/rearRight
mapped via `seatSettings`.

---

## 5. State & concurrency

- **No mutable instance state.** The class holds only immutable configuration:
  `json` (a configured `Json` with `ignoreUnknownKeys`, `isLenient`,
  `coerceInputValues`), `jsonMedia` (`application/json;charset=utf-8`), and
  `client` (a single reused `OkHttpClient`, 30s connect / 60s read timeout, with
  a BASIC-level `HttpLoggingInterceptor` that routes lines to `AppLog.log`).
- All session/vehicle state is passed in per call; nothing is cached here.
- **Dispatchers:** the public `suspend` methods (`authUser`, `sendOtp`,
  `verifyOtpAndComplete`, `vehicles`, `status`, `forceRefresh`, `startCharge`,
  `setChargeTargets`, `startClimate`, plus `getCommand`) wrap their work in
  `withContext(Dispatchers.IO)`. The private `postCommand`, `chargeTargets`,
  `parseStatus`, `call`, `raw` are **synchronous** and rely on the caller's IO
  context. `startCharge`/`setChargeTargets`/`startClimate` each open their own
  `withContext(Dispatchers.IO)` around `postCommand`.
- **No locks in this class.** Process-wide serialization of vehicle
  status/command calls is enforced by `BlueLinkGate.statusMutex` at a higher
  layer (the backend 502s on overlapping requests for one account) — this client
  does not itself serialize.
- No StateFlow / DataStore / Compose state — this is a transport layer.

---

## 6. Collaborators & data flow

**Calls out to:**
- `Brand.KIA` (`Brand.kt:45-52`) for host/baseUrl/clientId/clientSecret.
- `gmtOffsetHours()` (`FormatUtils.kt:164`) for the `offset` header.
- `AppLog.log` for HTTP logging + error logging.
- `BlueLinkException` (shared) as the single error type it throws.
- OkHttp for transport; `kotlinx.serialization.json` for parsing;
  `java.security.MessageDigest` (SHA-1) for the v5 UUID.
- Produces shared domain models from `Models.kt` (`VehicleStatus`, `EvStatus`,
  `TargetSOC`, `ReservChargeInfos`, `RemainTime2`, `DrvDistance`, `RangeByFuel`,
  `Dte`, `TempValue`, `Battery12V`, `SeatHeaterVentState`, `DoorOpen`,
  `WindowOpen`, `TirePressure`, `TirePressureLamp`, `VehicleLocation`, `Coord`,
  `TimeValue`).

**Called by:** `KiaRepository` (`KiaRepository.kt`) is the sole caller. It:
- Persists/loads `KiaSession` via `SessionStore` (mapping `sid→accessToken`,
  `rmtoken→refreshToken`, plus `deviceId`; `toKia()`/`save()` at
  `KiaRepository.kt:67-78, 164-165`).
- Caches `KiaVehicleSummary` (vinkeys) in a `ConcurrentHashMap` keyed by VIN
  (`KiaRepository.kt:25`), re-fetching after every re-auth.
- Drives the OTP login UI flow (`startLogin`/`sendOtp`/`verifyOtp`).
- Handles session expiry: `withSession` catches `BlueLinkException` with
  `code == 401 || 403`, silently re-auths via `authUser(rmtoken)`, refreshes
  vinkeys, and retries once (`KiaRepository.kt:172-195`). This is the consumer of
  the 401 that `call()` synthesizes on `errorCode 1003/1005`.

**Data channels:** HTTP (headers + JSON bodies) only. No DataStore, Wear Data
Layer, intents, or WorkManager are touched by this file directly.

---

## 7. Invariants & assumptions

1. **`deviceId` stability.** The rmtoken is bound to the deviceId (`:33, 82`);
   the same deviceId must be passed on re-auth or silent re-auth fails.
   `KiaRepository.startLogin` reuses the stored deviceId for exactly this reason.
2. **`clientuuid` = v5-of-deviceId.** `apiHeaders` derives `clientuuid` from
   `deviceId` deterministically; the same deviceId always yields the same
   clientuuid, matching the iOS client.
3. **`sid` header presence = auth success** in `authUser`/`verifyOtpAndComplete`;
   these use `raw()` and inspect headers, not `call()`.
4. **`status.statusCode == 0` = success** on HTTP-200 responses. Any non-zero,
   non-null statusCode is an in-band error (`call`, `:538`).
5. **Session-expiry contract:** `statusCode==1 && errorType==1 && errorCode ∈
   {1003, 1005}` is mapped to HTTP **401** so `KiaRepository.withSession` can
   detect and re-auth. Changing this mapping breaks silent re-login.
6. **plug encodings differ by field** — `batteryPlugin` (0/1/2) vs `plugType`
   (0=DC, 1=AC). `setChargeTargets` and `chargeTargets` both use the `plugType`
   (0=DC/1=AC) scheme. Do not conflate.
7. `vehicleInfoList[0]` is the car's status; `parseStatus` reads only index 0.
8. `path()` numeric keys are array indices — relies on Kia sending arrays where
   the paths (`drvDistance."0"`, `remainChargeTime."0"`) expect them.
9. `chargeTargets` is best-effort: `status` must still return a valid
   `VehicleStatus` even if the charge-target call fails.

---

## 8. Gotchas & sharp edges

- **rmtoken rotation bug (fixed, documented at `:161-167`).** In `authUser`,
  `freshRmtoken = resp.header("rmtoken") ?: rmtoken` prefers a server-rotated
  rmtoken. The old code echoed back the caller-supplied token unconditionally,
  so a server-side rotation was never persisted — eventually the stale rmtoken
  invalidated and forced a full OTP re-login. Keep the `resp.header("rmtoken")`
  precedence.
- **Two-layer error handling in `call` (`:528-549`).** (1) Non-2xx HTTP →
  `BlueLinkException(friendly, code)`. (2) HTTP 200 with `status.statusCode != 0`
  → in-band error; the 1003/1005 case becomes a 401 (see §7.5), everything else
  a `BlueLinkException(errorMessage ?: "Kia request failed (error N)", resp.code)`.
  A **blank 200 body** parses as an empty object and is treated as success.
- **`str()` treats the literal `"null"` string as absent** (`:585`) — Kia
  inconsistently sends the string `"null"` instead of a JSON null for some fields.
- **`flag()` tolerates mixed boolean encodings** (`:591-592`) — `true/false` or
  `0/1`; needed because Kia is inconsistent across generations.
- **Temperature sentinels** (`:455-459`): Kia represents out-of-range setpoints
  as the literal strings `"LOW"` (< 62°F) and `"HIGH"` (> 82°F) rather than a
  number; the `airTemp.value` field is a **string** either way.
- **Seat block omitted when all seats OFF** (`:474`) — keeps the climate payload
  minimal; `anySeat` gates the whole `heatVentSeat` object.
- **`chargeTargets` skips `level == 0`** (`:308`) — 0 means "not reported yet",
  not "0% target". A car that reports only zeros yields `null` (no
  `reservChargeInfos` merged).
- **Charge limits live on a separate endpoint.** `cmm/gvi` omits `targetSOC`, so
  EV status requires the extra `evc/gts` round-trip merged into `EvStatus`
  (`:287-293`).
- **`startCharge`'s `chargeRatio=100` is not a limit** (`:424`) — it is only the
  on/off trigger; real limits are `setChargeTargets`.
- **`verifyOtpAndComplete` re-POSTs credentials** in a second `prof/authUser`
  call (with interim sid + rmtoken) to exchange for the *final* sid; the body
  there omits `tncFlag` (present only in the step-1 `authUser` body).
- **`postCommand` is synchronous** — unlike `getCommand` it does not wrap itself
  in `withContext(Dispatchers.IO)`; its three callers must (and do). A future
  caller forgetting this would run a blocking network call on the caller's
  dispatcher.
- **Window key mismatch by powertrain** (`:339-340`, `:374-379`) — ICE cars
  report windows under `windowOpen.{frontLeft…}`, EVs under
  `evStatus.windowStatus.{windowFL…}`; the local `window()` helper bridges both.
- **`friendly` double-parses** the body (`:554-557`) — parses once for
  `status.errorMessage`, then again for top-level `errorMessage`. Both parses are
  inside a single `runCatching`, so a malformed body just yields the HTTP-code
  fallback string.
- **Hardcoded production credentials** (`CLIENT_ID`/`SECRET_KEY` via `Brand.KIA`)
  and a **spoofed iOS User-Agent** are load-bearing: the API keys behavior off a
  recognized UA (`:78-80`).
