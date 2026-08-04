# Europe (Hyundai Bluelink EU, CCS2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Europe as a third region so a European Hyundai owner can sign in and control an E-GMP (CCS2) car, mirroring the existing Canada backend pattern.

**Architecture:** New `EuApi` (CCS2 client: device registration, OAuth2, control-token, stamp-signed requests) + `EuRepository : VehicleRepository`, selected by a new `Brand.HYUNDAI_EU` in `repositoryFor()`. A "Europe" segment is added to the login region picker. Nothing above the `VehicleRepository` interface changes.

**Tech Stack:** Kotlin, okhttp 4.12, kotlinx-serialization-json, AndroidX Security (existing). Reference: Apache-2.0 `hyundai_kia_connect_api` (`HyundaiBlueLinkApiEU`, CCS2 variant).

**Verification note:** The Android build/live verification is run by the repo owner against a real EU Hyundai account + Ioniq/E-GMP car (`./gradlew assembleDebug`, then sign in). A small set of opaque constants (Hyundai EU `client_secret`, stamp CFB seed) are centralized and marked `FILL-FROM-SOURCE`; they must be confirmed from the current reference project or a captured app session before login will succeed. Fabricated values are deliberately NOT used.

---

## File structure

| File | Responsibility | Action |
|------|----------------|--------|
| `shared/.../data/Brand.kt` | Add `HYUNDAI_EU`, `isEurope`, EU `BrandLinks`, `fromIndicator` | Modify |
| `shared/.../data/EuStamp.kt` | Pure stamp-header generator (CFB XOR) | Create |
| `shared/.../data/EuApi.kt` | CCS2 client: auth, control-token, endpoints, response mapping | Create |
| `shared/.../data/EuRepository.kt` | `VehicleRepository` impl over `EuApi` | Create |
| `shared/.../data/BlueLinkRepository.kt` | Wire `repositoryFor()` for Europe | Modify |
| `app/.../ui/Screens.kt` | Add "Europe" region segment + Hyundai-only brand list + login copy | Modify |
| `shared/src/test/.../EuStampTest.kt` | Unit test for stamp generation | Create |

---

## Task 1: `Brand.HYUNDAI_EU`

**Files:** Modify `shared/src/main/java/com/bloo/bluelink/data/Brand.kt`

- [ ] **Step 1: Add the enum entry** after `KIA_CA` (before the `;`):

```kotlin
    /**
     * Hyundai Bluelink Europe on the CCAPI ("CCS2") platform, served by
     * [EuApi]/[EuRepository]. Kia Connect EU and Genesis EU ride the SAME
     * backend family (different host + client only) — like the three Canada
     * brands share [CanadaApi] — so they can later be added as sibling entries
     * here with no new API code. Only Hyundai EU is shipped/verified for now.
     *
     * clientSecret / the stamp CFB seed (see [EuStamp]) are community-derived
     * and rotate with Hyundai's app; correct them here / in [EuStamp] in one
     * place if sign-in starts failing.
     */
    HYUNDAI_EU(
        code = "HEU",
        baseUrl = "https://prd.eu-ccapi.hyundai.com:8080",
        host = "prd.eu-ccapi.hyundai.com",
        clientId = "6d477c38-3ca4-4cf3-9557-2a1929a94654",
        clientSecret = "FILL-FROM-SOURCE", // hyundai_kia_connect_api const.py, Hyundai EU
        label = "Hyundai (Europe)",
    );
```

- [ ] **Step 2: Add the `isEurope` helper** next to `isCanada`:

```kotlin
    /** True for the Europe (CCAPI/CCS2) brands, served by [EuApi]/[EuRepository]. */
    val isEurope: Boolean get() = this == HYUNDAI_EU
```

- [ ] **Step 3: Route `fromIndicator`** — add the 3-letter EU code check alongside the Canada ones (before the single-letter US checks):

```kotlin
            indicator == HYUNDAI_EU.code -> HYUNDAI_EU
```

- [ ] **Step 4: Add the EU `BrandLinks` branch** in `val Brand.links` `when` (compiler will demand it). EU owner portals + roadside for France/EU:

```kotlin
        Brand.HYUNDAI_EU -> BrandLinks(
            appPackage = "com.hyundai.bluelink.eu",
            appName = "Bluelink",
            ownersUrl = "https://www.hyundai.com/eu/en/owners.html",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.hyundai.com/eu/en/find-a-dealer.html",
            manualsUrl = "https://www.hyundai.com/eu/en/owners/e-manual.html",
            serviceScheduleUrl = "https://www.hyundai.com/eu/en/owners/book-a-service.html",
            roadsidePhone = "",
            storeUrl = "https://www.hyundai.com/eu/en/owners.html",
        )
```

- [ ] **Step 5: Update `supportsConnectedStore` / `isGen5W` / `supportsHornLights`** to treat EU like Canada (excluded), by adding `&& !brand.isEurope` to the non-Kia/non-Canada guards. EU CCS2 has no US "Features on Demand"/horn-lights parity confirmed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/java/com/bloo/bluelink/data/Brand.kt
git commit -m "Brand: add Hyundai Bluelink Europe (CCS2) entry"
```

---

## Task 2: `EuStamp` (pure stamp generator) — TDD

**Files:** Create `shared/.../data/EuStamp.kt`, `shared/src/test/.../EuStampTest.kt`

The EU `Stamp` header = base64( appId-bytes XOR cfb-bytes ), where the raw is
`"$appId:$timestampMillis"` and `cfb` is a fixed base64 seed. Pure + deterministic.

- [ ] **Step 1: Failing test** (`EuStampTest.kt`):

```kotlin
package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

class EuStampTest {
    @Test fun stampIsCfbXorAppIdColonTimestampBase64() {
        // cfb "AAAA..." (all-zero bytes) makes XOR a no-op, so the stamp is just
        // base64("<appId>:<ts>") — a deterministic vector independent of the real seed.
        val appId = "app"
        val zeroCfb = java.util.Base64.getEncoder().encodeToString(ByteArray(9))
        val stamp = EuStamp.generate(appId = appId, cfbBase64 = zeroCfb, timestampMillis = 5L)
        assertEquals(
            java.util.Base64.getEncoder().encodeToString("app:5".toByteArray()),
            stamp,
        )
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`EuStamp` unresolved): `./gradlew :shared:testDebugUnitTest --tests '*EuStampTest*'`

- [ ] **Step 3: Implement `EuStamp.kt`:**

```kotlin
package com.bloo.bluelink.data

import java.util.Base64

/**
 * Generates the `Stamp` header every Hyundai/Kia CCAPI (EU) request must carry.
 * Algorithm (from the Apache-2.0 hyundai_kia_connect_api reference): base64 of
 * ("$appId:$timestamp") XOR the decoded base64 [CFB] seed, byte-for-byte.
 *
 * [CFB] rotates when Hyundai ships a new app build; if EU sign-in starts
 * returning 4xx with a stamp error, refresh it from the reference project's
 * const.py (Hyundai EU) — it is the single maintenance point, mirroring the
 * rotatable clientSecret documented in [Brand].
 */
object EuStamp {
    // FILL-FROM-SOURCE: base64 CFB seed for the Hyundai EU app id below.
    const val CFB = "FILL-FROM-SOURCE"
    // FILL-FROM-SOURCE: the Hyundai EU appId the stamp is bound to.
    const val APP_ID = "FILL-FROM-SOURCE"

    fun generate(
        appId: String = APP_ID,
        cfbBase64: String = CFB,
        timestampMillis: Long,
    ): String {
        val cfb = Base64.getDecoder().decode(cfbBase64)
        val raw = "$appId:$timestampMillis".toByteArray()
        val out = ByteArray(raw.size) { i -> (cfb[i % cfb.size].toInt() xor raw[i].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(out)
    }
}
```

- [ ] **Step 4: Run — expect PASS** (with the zero-cfb vector, independent of the real seed).

- [ ] **Step 5: Commit**

```bash
git add shared/src/main/java/com/bloo/bluelink/data/EuStamp.kt shared/src/test/java/com/bloo/bluelink/data/EuStampTest.kt
git commit -m "EuStamp: CCAPI stamp-header generator + unit test"
```

---

## Task 3: `EuApi` (CCS2 client)

**Files:** Create `shared/.../data/EuApi.kt`

Mirrors `CanadaApi`'s shape: an okhttp client at `BASIC` log level, a `EuSession`
(access/refresh/deviceId + pin), a `EuVehicleSummary`, and suspend methods:
`register()`, `login()`, `refresh()`, `controlToken()`, `vehicles()`, `status()`,
`location()`, `lock()/unlock()`, `startClimate()/stopClimate()`,
`setChargeTargets()`, `startCharge()/stopCharge()`. Each request carries the
`Stamp` header from `EuStamp.generate(timestampMillis = <now>)` plus the
`ccsp-service-id`/`Authorization` headers the CCAPI expects. Command methods send
the control-token in the `Authorization` header (PIN-derived). Status parses the
CCS2 `.../ccs2/carstatus/latest` payload into `VehicleStatus` (best-effort field
mapping, verified live). Full method bodies written to compile against the exact
model types in `Models.kt`; opaque wire constants centralized at the top of the
file and marked `FILL-FROM-SOURCE` where not reliably known.

- [ ] **Step 1: Write `EuApi.kt`** (see repository source — large integration file).
- [ ] **Step 2: Commit**

```bash
git add shared/src/main/java/com/bloo/bluelink/data/EuApi.kt
git commit -m "EuApi: Hyundai EU CCS2 client (auth, control-token, endpoints)"
```

---

## Task 4: `EuRepository`

**Files:** Create `shared/.../data/EuRepository.kt`

Implements `VehicleRepository` over `EuApi`, caching vehicle summaries and the
control-token per VIN with 401 refresh, and silent session re-auth on
401/403 — structurally identical to `CanadaRepository`. Exposes `login()` for the
sign-in screen.

- [ ] **Step 1: Write `EuRepository.kt`.**
- [ ] **Step 2: Commit**

```bash
git add shared/src/main/java/com/bloo/bluelink/data/EuRepository.kt
git commit -m "EuRepository: VehicleRepository over EuApi"
```

---

## Task 5: Wire `repositoryFor()`

**Files:** Modify `shared/.../data/BlueLinkRepository.kt:36-40`

- [ ] **Step 1:** Add before the `else`:

```kotlin
    brand.isEurope -> EuRepository(EuApi(brand), store, brand, credentials)
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/main/java/com/bloo/bluelink/data/BlueLinkRepository.kt
git commit -m "repositoryFor: route Europe brands to EuRepository"
```

---

## Task 6: Login UI — "Europe" region

**Files:** Modify `app/.../ui/Screens.kt` (region picker ~1871; copy `when`s ~1785-1797; login dispatch in `AppViewModel`)

- [ ] **Step 1:** Add `SegmentOption("EU", "Europe", null)` to the region `MorphSegmented`, and on select set `brand = Brand.HYUNDAI_EU`.
- [ ] **Step 2:** Add the `region == "EU"` branch to `brandOptions` returning `listOf(Brand.HYUNDAI_EU)`.
- [ ] **Step 3:** Complete the exhaustive `when (brand)` for `brandSubtitle` (`"A better Bluelink · Europe"`) and `emailLabel` (`"Bluelink email"`).
- [ ] **Step 4:** In `AppViewModel.login`, route `brand.isEurope` to a `loginEurope()` that calls `(repoFor(brand) as EuRepository).login(...)` (email+password+pin, no OTP), mirroring the Hyundai US branch.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bloo/bluelink/ui/Screens.kt app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt
git commit -m "Login: add Europe region (Hyundai EU)"
```

---

## Task 7: Build & live verification (owner-run)

- [ ] `./gradlew assembleDebug :shared:testDebugUnitTest` — compiles + unit tests pass.
- [ ] Fill `EuStamp.CFB` / `EuStamp.APP_ID` / `Brand.HYUNDAI_EU.clientSecret` from source.
- [ ] Sign in with the real EU account; verify screen-by-screen (status, location, lock/unlock, climate, charge, trips). Fix wire-mapping against live responses.

---

## Self-review

- Spec coverage: region picker (T6), EuApi/EuRepository backend (T3/T4), Brand entry (T1), stamp on-device (T2), factory wiring (T5), verification (T7) — all mapped.
- Types: `VehicleRepository` methods, `SessionStore.Session`, `Credentials`, `Vehicle`, `VehicleStatus`, `ClimateRequest`, `BlueLinkException(code=)` match the signatures read from source.
- Placeholders: the only `FILL-FROM-SOURCE` markers are genuine account/app secrets that must not be fabricated; every code step is otherwise complete.
