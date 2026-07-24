# shared: Repositories + Brand

Deep-dive reference for the repository layer and brand registry in the `:shared`
module. Three files:

- `shared/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt` — the
  `VehicleRepository` contract, the `repositoryFor(...)` factory, and the
  Hyundai/Genesis implementation `BlueLinkRepository`.
- `shared/src/main/java/com/bloo/bluelink/data/KiaRepository.kt` — the Kia US
  (Kia Connect) implementation `KiaRepository`.
- `shared/src/main/java/com/bloo/bluelink/data/Brand.kt` — the `Brand` enum plus
  brand-derived extension properties (`links`, `brand`, `supportsConnectedStore`,
  `supportsHornLights`) and the `BrandLinks` data class.

---

## 1. Purpose

This unit is the **boundary between the app's domain/UI layers and the live
telematics backends**. It answers "for a signed-in brand, do X to this vehicle"
(status, location, lock/unlock, climate, charging, horn/lights) without callers
needing to know which OEM backend serves that brand or how its auth works.

Two OEM backends are abstracted behind one interface:

- **Hyundai and Genesis US** share the "Hyundai-shaped" telematics backend
  (`api.telematics.hyundaiusa.com` / `api.genesis.telematics.hyundaiusa.com`),
  served by `BlueLinkApi` and wrapped by `BlueLinkRepository`
  (`BlueLinkRepository.kt:44`).
- **Kia US** ("Kia Connect", `api.owners.kia.com`) is a completely different
  backend served by `KiaUsaApi` and wrapped by `KiaRepository`
  (`KiaRepository.kt:13`). Kia needs an OTP round-trip to sign in and keys
  commands by a session-specific "vinkey" rather than a service PIN.

`Brand.kt` is the **single source of truth for per-brand constants**: OAuth
client credentials, hosts/base URLs, brand codes, human labels, and all the
external OEM links (companion app package, owner portal, dealer locator, manuals,
service scheduler, roadside phone, connected-store URL). Because Genesis US runs
on the same Hyundai-shaped backend, only the per-brand values in the enum differ;
the request/path structure is shared (`Brand.kt:3-13`).

The unit exists so that: (a) brand differences are localized (one enum entry, one
repository subclass), (b) the rest of the app programs against `VehicleRepository`
and `Vehicle` regardless of brand, and (c) the token-refresh-and-retry policy is
implemented once per backend in a private `withSession` helper.

**All data is live** — these hit real production endpoints; nothing is simulated
(`BlueLinkRepository.kt:42`, `Brand.kt:11`).

---

## 2. Public surface

### 2.1 `interface VehicleRepository` (`BlueLinkRepository.kt:9-30`)

The brand-agnostic vehicle-operations contract. All operations are `suspend`.
Sign-in is deliberately **not** on this interface because it is brand-specific
(Kia needs OTP) — login lives on the concrete types (`BlueLinkRepository.login`,
`KiaRepository.startLogin/sendOtp/verifyOtp`) (`BlueLinkRepository.kt:6-7`).

Members:

- `suspend fun logout()` — clears the persisted session for this brand.
- `suspend fun vehicles(): List<Vehicle>` — the account's garage.
- `suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus?` — vehicle
  status; `refresh=true` requests a live wake/poll, `false` reads the
  server-cached status.
- `suspend fun location(v: Vehicle): GeoLocation?` — GPS coordinates.
- `suspend fun trips(v: Vehicle): List<EvTrip> = emptyList()` — recent EV trips.
  **Default returns empty**; the backends without an equivalent (Kia US) inherit
  the default (`BlueLinkRepository.kt:15-16`).
- `suspend fun lock(v: Vehicle)` / `suspend fun unlock(v: Vehicle)`.
- `suspend fun startClimate(v: Vehicle, req: ClimateRequest)` /
  `suspend fun stopClimate(v: Vehicle)`.
- `suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int)` —
  set AC/DC charge target percentages.
- `suspend fun startCharge(v: Vehicle)` / `suspend fun stopCharge(v: Vehicle)`.
- `val supportsHornLights: Boolean get() = false` — capability flag; **default
  false** (`BlueLinkRepository.kt:27`).
- `suspend fun flashLights(v: Vehicle) {}` / `suspend fun hornAndLights(v: Vehicle) {}`
  — **default no-op** bodies; only overridden where the backend supports them
  (`BlueLinkRepository.kt:25-29`).

### 2.2 `fun repositoryFor(...)` (`BlueLinkRepository.kt:36-38`)

```kotlin
fun repositoryFor(brand: Brand, store: SessionStore, credentials: CredentialStore): VehicleRepository
```

Factory that returns the correct implementation for a brand:

- `Brand.KIA` → `KiaRepository(KiaUsaApi(), store, credentials)`.
- everything else (Hyundai, Genesis) → `BlueLinkRepository(BlueLinkApi(brand), store, brand)`.

Note `credentials` is only actually used by the Kia path; the Hyundai/Genesis
path never receives it (it re-authenticates via refresh token, not stored
credentials).

### 2.3 `class BlueLinkRepository` (`BlueLinkRepository.kt:44-159`)

```kotlin
class BlueLinkRepository(
    private val api: BlueLinkApi,
    private val store: SessionStore,
    private val brand: Brand,
) : VehicleRepository
```

Coordinates one brand's `BlueLinkApi` client with its persisted `SessionStore`
session, retrying once on an auth failure by refreshing the access token
(`BlueLinkRepository.kt:40-43`).

Public/overridden members beyond the interface:

- `suspend fun login(username: String, password: String, pin: String)`
  (`BlueLinkRepository.kt:56-67`) — authenticates via `api.login(username,
  password)` and persists a `SessionStore.Session` for this brand containing the
  returned `accessToken`/`refreshToken` plus the caller-supplied `username` and
  `pin`. **Critically: the PIN is never sent to or validated by the login call.**
  It is only remembered locally so it can be attached as a header on later
  commands (`BlueLinkRepository.kt:50-55`).
- `override suspend fun logout() = store.clear(brand)` (`:69`).
- `override suspend fun vehicles()` (`:74-76`) — calls `api.vehicles(accessToken,
  username)` and stamps each returned `Vehicle` with this repo's brand code via
  `it.copy(brandIndicator = brand.code)`. Necessary because the raw API response
  doesn't carry the brand, and downstream layers need it to route later calls and
  disambiguate vehicles across brands (`:71-73`).
- `override suspend fun status(v, refresh)` (`:78-80`) — `api.status(accessToken,
  username, pin, v, refresh)`.
- `override suspend fun location(v)` (`:82-84`) — `api.location(...)`.
- `override suspend fun trips(v)` (`:86-88`) — `api.tripDetails(...)`. (Overrides
  the interface default, so Hyundai/Genesis get real trips.)
- `override suspend fun lock/unlock` (`:90-96`).
- `override suspend fun startClimate/stopClimate` (`:98-104`).
- `override suspend fun setChargeTargets/startCharge/stopCharge` (`:106-126`).
- `override val supportsHornLights: Boolean get() = true` (`:114`).
- `override suspend fun flashLights/hornAndLights` (`:116-122`) — `api.flashLights` /
  `api.hornAndLights`.

Every override delegates through the private `withSession` helper (see §3).

### 2.4 `class KiaRepository` (`KiaRepository.kt:13-196`)

```kotlin
class KiaRepository(
    private val api: KiaUsaApi,
    private val store: SessionStore,
    private val credentialStore: CredentialStore,
) : VehicleRepository
```

Kia US implementation. Public members beyond the interface (the three-step OTP
sign-in):

- `suspend fun startLogin(username, password, pin): KiaAuth`
  (`KiaRepository.kt:37-45`) — Step 1. Reuses the stored `deviceId` if present
  (so a previously-issued rmtoken stays valid), else mints a new one via
  `KiaUsaApi.newDeviceId()`; stashes it in `pendingDeviceId`. Loads any stored
  `refreshToken` (rmtoken) and calls `api.authUser(username, password, deviceId,
  rmtoken, pin.ifBlank { null })`. If the result is `KiaAuth.LoggedIn`, it saves
  the session immediately and returns; otherwise returns `KiaAuth.OtpRequired`
  for the caller to solve.
- `suspend fun sendOtp(challenge: KiaAuth.OtpRequired, notifyType: String)`
  (`:48-51`) — Step 2a. Delivers the one-time code to `"EMAIL"` or `"SMS"`
  (`notifyType`) via `api.sendOtp(challenge.otpKey, notifyType, challenge.xid,
  deviceId)`, using `pendingDeviceId` (minting+storing one if somehow absent).
- `suspend fun verifyOtp(username, password, pin, code, challenge)` (`:54-61`) —
  Step 2b. Calls `api.verifyOtpAndComplete(...)` with the code + challenge
  `otpKey`/`xid` + deviceId, saves the returned session, and clears
  `pendingDeviceId`.

All the `VehicleRepository` overrides (`logout`, `vehicles`, `status`,
`location`, `lock`, `unlock`, `startClimate`, `stopClimate`, `setChargeTargets`,
`startCharge`, `stopCharge`) are implemented (`:82-130`). `trips`,
`supportsHornLights`, `flashLights`, `hornAndLights` are **not** overridden — Kia
inherits the interface defaults (empty trips, `supportsHornLights=false`, no-op
horn/lights), because the Kia US API has no equivalent endpoints
(`BlueLinkRepository.kt:26`, `Brand.kt:152-156`).

### 2.5 `enum class Brand` (`Brand.kt:14-72`)

Public entries and per-entry fields — see §4. Public members:

- `val usesOtpLogin: Boolean get() = this == KIA` (`Brand.kt:55`) — true when
  sign-in uses a one-time code and no service PIN (Kia US only).
- companion `fun fromName(name: String?): Brand` (`Brand.kt:62-63`) — looks up an
  entry by exact `name` (e.g. `"KIA"`); **falls back to `HYUNDAI`** if `name` is
  null or unmatched, so legacy/blank stored values never throw.
- companion `fun fromIndicator(indicator: String?): Brand` (`Brand.kt:66-70`) —
  maps a vehicle brand indicator to a `Brand`: `"G"`→GENESIS, `"K"`→KIA (both
  case-insensitive), everything else→HYUNDAI.

### 2.6 Brand-derived top-level extensions (`Brand.kt`)

- `val Vehicle.brand: Brand` (`Brand.kt:75`) — `Brand.fromIndicator(brandIndicator)`.
- `val Brand.links: BrandLinks` (`Brand.kt:105-140`) — returns the `BrandLinks`
  record for each brand (see §4). Single source of truth for OEM apps/sites/phone
  numbers (`Brand.kt:77-83`).
- `val Vehicle.supportsConnectedStore: Boolean` (`Brand.kt:149-150`) —
  `brand == Brand.KIA || (generation.trim().toIntOrNull() ?: 0) >= 3`. True for
  ccNC-era head units (Hyundai/Genesis report generation 3+; older Gen5W report
  2). Kia doesn't expose a generation, so Kia is always eligible and the store
  page gates by VIN itself (`Brand.kt:142-148`).
- `val Vehicle.supportsHornLights: Boolean` (`Brand.kt:155-156`) —
  `brand != Brand.KIA`. Horn & Lights / Flash Lights (`rcs/rhl/light`,
  `rcs/rhl/hnl`) exist on the Hyundai/Genesis US telematics API but not Kia's
  (`Brand.kt:152-154`). **Note:** this is the *vehicle*-level capability check,
  distinct from the *repository*-level `VehicleRepository.supportsHornLights`
  (§2.1) — both encode the same fact but serve different callers.

### 2.7 `data class BrandLinks` (`Brand.kt:84-103`)

See §4.

---

## 3. Internal structure

### 3.1 `BlueLinkRepository.withSession` (`BlueLinkRepository.kt:142-158`)

```kotlin
private suspend fun <T> withSession(block: suspend (SessionStore.Session) -> T): T
```

The single point through which every operation runs. Control flow:

1. Load the persisted session for this brand: `store.load(brand)`. If null →
   `throw BlueLinkException("Not logged in")` (`:143`).
2. `try { block(session) }` — run the operation with the loaded session (`:145`).
3. `catch (e: BlueLinkException)` (`:146`): decide whether this is a refreshable
   auth failure.
   - `isAuth = e.code == 401 || e.code == 403` (`:148`).
   - If `isAuth && refreshToken != null` (`:149`):
     - `api.refresh(refreshToken)` → new access/refresh token pair (`:150`).
     - `store.updateAccessToken(brand, refreshed.accessToken, refreshed.refreshToken)`
       persists it (`:151`).
     - `store.load(brand)` reloads the now-updated session; if null → rethrow the
       original `e` (`:152`).
     - `block(updated)` — **retry exactly once** with the fresh session (`:153`).
   - Else (non-auth error, or no refresh token) → `throw e` (`:155`).
4. Any exception from the retried `block(updated)` propagates unchanged — **there
   is no second retry**, so a persistent auth failure surfaces immediately rather
   than looping (`:138-140`).

Only `BlueLinkException` is caught; any other exception type bypasses the
refresh logic entirely and propagates.

### 3.2 `KiaRepository.withSession` (`KiaRepository.kt:172-195`)

```kotlin
private suspend fun <T> withSession(block: suspend (KiaSession) -> T): T
```

Same shape as Hyundai's but re-authenticates via stored **credentials + rmtoken**
(silent, no OTP) instead of a simple token refresh:

1. `store.load(Brand.KIA)` → the generic `Session`; null → `throw
   BlueLinkException("Not logged in")` (`:173`).
2. `try { block(stored.toKia()) }` — convert the generic session to a `KiaSession`
   and run (`:175`).
3. `catch (e: BlueLinkException)`:
   - If `e.code != 401 && e.code != 403` → `throw e` (not an auth failure) (`:177`).
   - `credentialStore.load(Brand.KIA)`; null → `throw BlueLinkException("Kia
     session expired — please sign in again")` (`:178-179`). (Silent re-auth needs
     the stored email/password.)
   - Logs via `AppLog.log("Kia session expired; re-authenticating with stored
     rmtoken")` (`:180`).
   - `api.authUser(creds.email, creds.password, stored.deviceId ?:
     newDeviceId(), stored.refreshToken /* rmtoken */, stored.pin.ifBlank { null })`
     (`:181-184`).
   - If the result is **not** `KiaAuth.LoggedIn` (i.e. even the rmtoken expired
     and a fresh OTP round-trip is needed) → `throw BlueLinkException("Kia session
     expired — please sign out and sign in again")` (`:185-188`).
   - `save(auth.session, creds.email, stored.pin)` persists the fresh session
     (`:189`).
   - `store.load(Brand.KIA)` reload; null → rethrow original `e` (`:190`).
   - `fetchSummaries(fresh.toKia())` — **re-fetch vinkeys**, because they're bound
     to the session that just changed (`:191-192`).
   - `block(fresh.toKia())` — retry once (`:193`).

### 3.3 `KiaRepository` private helpers

- `save(session: KiaSession, username: String, pin: String)` (`:67-78`) —
  persists a Kia session as a generic `SessionStore.Session` tagged `Brand.KIA`:
  `accessToken = session.sid`, `refreshToken = session.rmtoken`,
  `deviceId = session.deviceId`, plus `username`/`pin`. This is the "encode Kia
  into the shared store" direction (`:63-66`).
- `KiaVehicleSummary.toVehicle()` (`:138-146`) — maps the Kia-specific summary
  onto the cross-brand `Vehicle`: `vin=id`, `regId=key` (the vinkey), `name`,
  `model`, `generation=""` (Kia exposes no head-unit generation),
  `brandIndicator=Brand.KIA.code` (`"K"`), `isEv`. The blank generation is
  intentional and interacts with `supportsConnectedStore` (Kia stays eligible)
  (`:134-137`).
- `fetchSummaries(s: KiaSession): List<KiaVehicleSummary>` (`:152-156`) — calls
  `api.vehicles(s)` and **replaces the entire `summaries` cache**: `clear()` then
  repopulate keyed by `it.id`. Replace-not-merge so a car removed from the account
  disappears from the cache instead of lingering stale (`:148-151`).
- `summaryFor(s: KiaSession, v: Vehicle): KiaVehicleSummary` (`:159-162`) —
  returns the cached summary for `v.vin`, else `fetchSummaries(s).firstOrNull {
  it.id == v.vin }`, else `throw BlueLinkException("Vehicle not found on this Kia
  account")`. The vinkey lookup used by every command.
- `SessionStore.Session.toKia()` (`:164-165`) — the reverse of `save`: builds a
  `KiaSession(sid = accessToken, rmtoken = refreshToken, deviceId = deviceId ?:
  newDeviceId(), pin = pin)`. Note it mints a fresh deviceId if `deviceId` is null
  — a fallback that should rarely trigger since `save` always writes one.

### 3.4 Per-operation flow differences (Kia)

- `vehicles()` (`:92-94`) — always re-fetches via `fetchSummaries` (repopulating
  the cache) so the returned list reflects the current garage (`:89-91`).
- `status(v, refresh)` (`:100-104`) — resolves `summaryFor`, and when
  `refresh=true` calls `api.forceRefresh(s, summary)` first (a separate call
  telling the car to phone home with live data) before `api.status(s, summary)`.
  Without refresh, only reads the server-cached status, avoiding the wake call for
  background polls (`:96-99`).
- `location(v)` (`:106-112`) — **Kia has no separate location endpoint.** GPS
  rides along in `status`: reads `api.status(...)?.vehicleLocation?.coord`, and
  returns `GeoLocation(lat, lon)` only if both `lat` and `lon` are non-null, else
  null (`:107`).

### 3.5 `Brand` internals

Aside from the two companion lookup functions (§2.5), `Brand` has no complex
logic. `BrandLinks.playStoreUrl` (`Brand.kt:100-102`) is a computed property that
builds `https://play.google.com/store/apps/details?id=$appPackage` on the fly
rather than storing a redundant field (`:100-101`).

---

## 4. Data & types

### 4.1 `enum class Brand` (`Brand.kt:14-72`)

Constructor fields (all `val String`): `code`, `baseUrl`, `host`, `clientId`,
`clientSecret`, `label`.

| Entry | code | baseUrl / host | clientId | label |
|---|---|---|---|---|
| `HYUNDAI` | `"H"` | `https://api.telematics.hyundaiusa.com` | `m66129Bb-em93-SPAHYN-bZ91-am4540zp19920` | `Hyundai` |
| `GENESIS` | `"G"` | `https://api.genesis.telematics.hyundaiusa.com` | `3020afa2-30ff-412a-aa51-d28fbe901e10` | `Genesis` |
| `KIA` | `"K"` | `https://api.owners.kia.com` | `SPACL716-APL` | `Kia` |

- `code` is the one-letter brand indicator stamped onto `Vehicle.brandIndicator`
  and decoded by `fromIndicator`.
- `clientSecret` values: HYUNDAI `v558o935-6nne-423i-baa8`, GENESIS
  `KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV`, KIA
  `sydnat-9kykci-Kuhtep-h5nK` (`Brand.kt:27,35,50`). These are real production
  OAuth client credentials from community reverse-engineering; Genesis creds in
  particular are community-derived and can be corrected here in one place if
  Genesis rotates them (`Brand.kt:9-13`).
- `KiaUsaApi` reads its endpoint + client creds from the `KIA` entry
  (`Brand.kt:39-44`, and `KiaUsaApi.kt:73-77`: `BASE = Brand.KIA.host`, `API =
  "${Brand.KIA.baseUrl}/apigw/v1/"`, `CLIENT_ID`/`SECRET_KEY` from the entry).

### 4.2 `data class BrandLinks` (`Brand.kt:84-103`)

Fields (all `String`):

- `appPackage` — Play Store package of the official companion app.
- `appName` — short app name, used as "`<name>` app".
- `ownersUrl` — owner portal.
- `dealerLabel` — UI label ("Find a dealer" vs Genesis "Find a retailer").
- `dealerUrl` — dealer/retailer locator.
- `manualsUrl` — manuals/resources.
- `serviceScheduleUrl` — online service-appointment scheduler.
- `roadsidePhone` — 24/7 roadside line, **digits only**.
- `storeUrl` — connected-car content store (Features on Demand).
- `val playStoreUrl: String` (computed) — Play listing URL from `appPackage`.

Concrete per-brand values live in `val Brand.links` (`Brand.kt:105-140`). Key
packages: Hyundai `com.stationdm.bluelink` / appName `Bluelink`, Genesis
`com.stationdm.genesis` / `Genesis`, Kia `com.myuvo.link` / `Kia Access`. Roadside
numbers: Hyundai `8002437766`, Genesis `8443409741`, Kia `8003334542`. URLs
verified June 2026 (`Brand.kt:82`).

### 4.3 Types this unit uses but defines elsewhere

- `SessionStore.Session` (`SessionStore.kt:28-35`) — the persisted per-brand
  session: `accessToken`, `refreshToken` (nullable), `username`, `pin`, `brand`,
  `deviceId: String? = null`. Persisted namespaced by brand in a DataStore
  (`save`/`load`/`updateAccessToken`/`clear` at `SessionStore.kt:54,74,95,112`).
  Optional fields (refreshToken, deviceId) are only written when present.
- `Credentials` (`CredentialStore.kt:9-11`) — `email`, `password` (+ more);
  loaded per-brand via `CredentialStore.load(Brand)` for Kia silent re-auth.
- `KiaSession` (`KiaUsaApi.kt:35-40`) — `sid: String`, `rmtoken: String?`,
  `deviceId: String`, `pin: String?`. The deviceId **must stay stable across
  logins** because the rmtoken is bound to it (`KiaUsaApi.kt:33`).
- `KiaVehicleSummary` (`KiaUsaApi.kt:43-49`) — `id`, `name`, `model`,
  `key` (the session-specific "vinkey", refreshed on login), `isEv`.
- `KiaAuth` (`KiaUsaApi.kt:52-62`) — sealed interface:
  - `LoggedIn(session: KiaSession)`.
  - `OtpRequired(otpKey: String, xid: String, email: String?, sms: String?,
    hasEmail: Boolean, hasSms: Boolean)`.
- `Vehicle` (`Models.kt:62-69`) — cross-brand model: `vin`, `regId`, `name`,
  `model`, `generation: String`, `brandIndicator: String`, `isEv: Boolean`.
- `ClimateRequest` (`Models.kt:352`), `GeoLocation(lat, lon)` (`Models.kt:411`),
  `EvTrip` (`Models.kt:489`), `VehicleStatus` (`Models.kt:83`) — request/response
  domain types passed through untouched by this unit.
- `BlueLinkException` — carries an HTTP `code` used by both `withSession`
  variants to detect 401/403 auth failures.

Note on encodings relevant to callers of this layer (from KEY DOMAIN FACTS, not
defined in these three files but flowing through `status`/`ClimateRequest`):
`plugType` 0=DC fast / 1=AC; `batteryPlugin` 0=unplugged / 1=DC / 2=AC (different
scheme); `SeatLevel.apiValue` 0=off, 3-5=cool, 6-8=heat.

---

## 5. State & concurrency

- **`BlueLinkRepository` is effectively stateless** — it holds only its injected
  `api`, `store`, `brand`. All mutable state lives in the `SessionStore`
  (DataStore-backed, persisted).

- **`KiaRepository` holds two pieces of in-memory state:**
  - `summaries: java.util.concurrent.ConcurrentHashMap<String, KiaVehicleSummary>`
    (`KiaRepository.kt:25`) — session-specific vinkeys + EV flags keyed by vehicle
    id. **A `ConcurrentHashMap` (not a plain `HashMap`) is used deliberately:** the
    single repository instance is cached and reused across the app's lifetime (the
    `repos` map in `AppViewModel`/`WearViewModel`), and not every path that
    reads/mutates it runs under the same lock — e.g. a background garage load can
    race a user-triggered command — which risked a `ConcurrentModificationException`
    with a plain map (`:19-24`).
  - `pendingDeviceId: String?` (`:28`) — carries the device id between the
    password step (`startLogin`) and the OTP steps (`sendOtp`/`verifyOtp`).
    Cleared to null at the end of `verifyOtp` (`:60`). This is plain mutable state,
    **not** thread-safe, but the OTP flow is a sequential user-driven wizard so
    concurrent mutation isn't expected.

- **Dispatchers/scopes:** neither repository switches dispatchers itself. The
  `withContext(Dispatchers.IO)` boundary lives inside the API clients
  (`KiaUsaApi` uses `withContext(Dispatchers.IO)` per call, e.g.
  `KiaUsaApi.kt:188`). Repository methods are `suspend` and simply call the API on
  whatever context the caller provides.

- **No StateFlow / remember / recomposition here.** This is a plain data/domain
  layer; there are no Compose or Flow constructs. Recomposition happens in the
  ViewModels that consume these results, not in this unit.

- **Process-wide command serialization** is handled by `BlueLinkGate.statusMutex`
  (per KEY DOMAIN FACTS) — a lock **outside** this unit that the callers (view
  models) hold around status/command calls because the backend 502s on overlapping
  requests for one account. This unit does not acquire that mutex itself; it
  assumes callers serialize appropriately (see §7).

---

## 6. Collaborators & data flow

### Calls out to:

- **`BlueLinkApi`** (`BlueLinkRepository`) — `login`, `refresh`, `vehicles`,
  `status`, `location`, `tripDetails`, `lock`, `unlock`, `startClimate`,
  `stopClimate`, `setChargeTargets`, `startCharge`, `stopCharge`, `flashLights`,
  `hornAndLights`. All take `(accessToken, username, pin, vehicle, …)`.
- **`KiaUsaApi`** (`KiaRepository`) — `authUser`, `sendOtp`,
  `verifyOtpAndComplete`, `newDeviceId` (companion), `vehicles`, `forceRefresh`,
  `status`, `lock`, `unlock`, `startClimate`, `stopClimate`, `setChargeTargets`,
  `startCharge`, `stopCharge`. Keyed by `(KiaSession, KiaVehicleSummary)`.
- **`SessionStore`** (both) — `load(brand)`, `save(Session)`, `clear(brand)`;
  `BlueLinkRepository` also `updateAccessToken(brand, access, refresh)`. Persisted
  via DataStore, namespaced per brand.
- **`CredentialStore`** (`KiaRepository` only) — `load(Brand.KIA)` for silent
  re-auth.
- **`AppLog.log(...)`** (`KiaRepository`) — one diagnostic line on Kia re-auth.
- **`Brand` / `Brand.links` / `Vehicle` extensions** (`Brand.kt`) — consumed by
  UI, widgets, QS tiles, OEM-app launcher, owner-link and app-shortcut code
  (`Brand.kt:77-83`), and by `KiaUsaApi` for endpoint/credential config.

### Called by:

- `repositoryFor(...)` is invoked by the view models (`AppViewModel`,
  `WearViewModel`), which cache the returned `VehicleRepository` in a per-brand
  `repos` map (`KiaRepository.kt:20-22`).
- The view models call the `VehicleRepository` methods to drive the phone UI,
  widgets, QuickSettings tiles, and the Wear bridge.

### Data channels:

- **In:** username/password/pin (login), `Vehicle`, `ClimateRequest`, charge
  percentages, `refresh` flag — all via function-call arguments. Persisted tokens
  and device id in via `SessionStore`/`CredentialStore` (DataStore /
  SharedPreferences).
- **Out:** `List<Vehicle>`, `VehicleStatus?`, `GeoLocation?`, `List<EvTrip>`,
  `KiaAuth`, and side-effect commands (no return) — all via return values /
  exceptions. Persisted session writes out via `SessionStore.save` /
  `updateAccessToken` / `clear`.
- This unit does **not** touch the Wear Data Layer, intents, or WorkManager
  directly — those are downstream of the view models that consume it.

---

## 7. Invariants & assumptions

1. **Login required first.** `withSession` (both) throws
   `"Not logged in"`/`"Vehicle not found…"` if no session/summary exists. Callers
   must have signed in for the brand.
2. **PIN is a header credential, not a login secret.** `BlueLinkRepository.login`
   never validates the PIN with the backend; it only stores it for later command
   headers (`BlueLinkRepository.kt:50-55`). A wrong PIN is discovered at command
   time, not login time.
3. **`BlueLinkException.code` reliably carries the HTTP status.** Both
   `withSession` variants branch on `code == 401 || code == 403`; a
   mis-populated code silently disables the refresh/re-auth path.
4. **Single retry, no loop.** Each `withSession` retries `block` at most once.
   The retried call's result/exception is final (`BlueLinkRepository.kt:138-140`).
5. **Kia deviceId stability.** The rmtoken is bound to the deviceId, so the same
   deviceId must be reused across logins. `startLogin` and the re-auth path both
   reuse `store.load(...).deviceId`; `save` always persists it (`KiaRepository.kt:38-39,
   67-78, 181-184`; `KiaUsaApi.kt:33`).
6. **Kia vinkeys are session-scoped.** After any re-authentication the vinkeys
   must be refreshed before commands; `withSession` calls `fetchSummaries` before
   the retry (`:191-192`), and `summaryFor` fetches on cache miss.
7. **Genesis == Hyundai backend shape.** Genesis is served by
   `BlueLinkRepository`/`BlueLinkApi` with only per-brand enum values differing
   (`Brand.kt:3-13`); `repositoryFor` sends everything except KIA down the
   BlueLink path (`BlueLinkRepository.kt:36-38`).
8. **`brandIndicator` is always set on returned vehicles.** BlueLink stamps
   `brand.code` (`BlueLinkRepository.kt:75`); Kia's `toVehicle` sets
   `Brand.KIA.code` (`KiaRepository.kt:144`). `fromIndicator` and `Vehicle.brand`
   rely on this to route later calls.
9. **Blank generation for Kia is intentional**, not "unknown/old":
   `supportsConnectedStore` treats Kia as always-eligible regardless of generation
   (`Brand.kt:134-137, 149-150`).
10. **Caller serializes overlapping calls** via `BlueLinkGate.statusMutex` — this
    unit does not, and the shared account backend 502s on overlapping requests.
11. **`fromName` never throws** on legacy/blank stored brand values (falls back to
    HYUNDAI) (`Brand.kt:60-63`).

---

## 8. Gotchas & sharp edges

- **Two `supportsHornLights` with the same name.** One is a
  `VehicleRepository` member (`BlueLinkRepository.kt:27`, overridden `true` at
  `:114`); the other is a `Vehicle` extension (`Brand.kt:155-156`,
  `brand != Brand.KIA`). They encode the same capability but are consumed by
  different call sites — don't confuse them, and keep them in sync if brand support
  changes.

- **`credentials` param of `repositoryFor` is Kia-only.** The Hyundai/Genesis path
  ignores it. Passing a null-ish or wrong CredentialStore only breaks Kia silent
  re-auth, and only at token-expiry time — not at construction.

- **BlueLink refreshes via refresh token; Kia re-authenticates via stored
  credentials.** These are fundamentally different recovery strategies. Kia's needs
  `CredentialStore` to contain the email/password; if credentials were never saved,
  a 401 turns into `"Kia session expired — please sign in again"` even though a
  valid rmtoken might exist (`KiaRepository.kt:178-179`). The rmtoken alone isn't
  used without credentials in this path because `authUser` needs email/password
  arguments.

- **Two distinct "session expired" Kia messages.** `"…please sign in again"` =
  no stored credentials to attempt silent re-auth (`:179`); `"…please sign out and
  sign in again"` = re-auth attempted but the rmtoken itself expired, needing a
  fresh OTP round-trip (`:187`). The wording difference is meaningful to whoever
  reads the exception.

- **Kia `location` silently returns null** whenever status lacks coords or status
  itself is null — there is no dedicated location endpoint, so a car that hasn't
  reported GPS yields null location even though other status fields may be present
  (`KiaRepository.kt:106-112`).

- **`status(refresh=false)` returns stale server-cached data by design.** For
  Kia this deliberately skips `forceRefresh` to avoid waking the car on background
  polls (`:96-99`); callers wanting live data must pass `refresh=true`.

- **`fetchSummaries` clears then repopulates** — a car removed from the account
  vanishes from the cache; but this also means an in-flight `summaryFor` on a
  concurrent thread could momentarily miss during the `clear()`/repopulate window.
  The `ConcurrentHashMap` prevents a crash but does not make clear+repopulate
  atomic; a racing lookup can fall through to its own `fetchSummaries`. Acceptable
  because summaries are idempotently re-fetchable.

- **`toKia()` mints a deviceId on null** (`:165`) as a fallback. If this ever
  fires it breaks rmtoken binding (a new deviceId invalidates the old rmtoken),
  forcing a full re-login. In practice `save` always writes a deviceId, so it's a
  guard rather than a live path.

- **PIN nullability convention:** Kia passes `pin.ifBlank { null }` to the API
  (`:42, 57, 183`) — a blank PIN becomes null, so a user without a PIN doesn't send
  an empty-string header.

- **Real production credentials live in `Brand.kt`.** The OAuth client secrets are
  hardcoded (community-derived, especially Genesis). Rotating a brand's creds is a
  one-line edit here (`Brand.kt:11-13`), but this also means the secrets ship in the
  APK — expected for this class of reverse-engineered telematics client.

- **`fromName` vs `fromIndicator` take different inputs.** `fromName` matches the
  enum `name` (`"KIA"`); `fromIndicator` matches the one-letter `code` (`"K"`).
  Passing an indicator to `fromName` (or vice versa) silently yields HYUNDAI.
