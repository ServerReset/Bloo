# `shared`: `Models.kt` — API + domain data types

**File:** `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\Models.kt`
**Package:** `com.bloo.bluelink.data`
**Length:** 538 lines. Pure declarations — no top-level state, no coroutines, no I/O.

---

## 1. Purpose

`Models.kt` is the single-file catalog of **all data types for the Hyundai Blue Link US telematics API** plus the flattened **domain types** the rest of the app consumes. It is the wire-format contract: `kotlinx.serialization` `@Serializable` data classes whose `@SerialName`/field names mirror the exact JSON returned by `api.telematics.hyundaiusa.com`, reverse-engineered from the community projects named in the header comment (`Hacksore/bluelinky`, `schmidtwmark/BetterBlueKit`, `andyfase/egmp-bluelink-scriptable`, `hyundai_kia_connect_api`) (Models.kt:6-17).

Two distinct kinds of type live here:

1. **Serializable API DTOs** — decoded directly from real HTTP responses (`TokenResponse`, `EnrollmentResponse`, `VehicleStatus`, `EvStatus`, `EvTrip`, …). Every field is nullable / defaulted so `ignoreUnknownKeys` + partial payloads never crash the parser.
2. **Domain/UI types** — hand-built, not decoded from the wire (`Vehicle`, `SeatCapability`, `GeoLocation`), produced by mapping functions elsewhere (`VehicleDetails.toVehicle()` in BlueLinkApi.kt:472).

It also hosts a cluster of **extension-function status helpers** (`percentFor`, `rangeMiFor`, `targetForCurrentPlug`, `openLabels`, `coordString`) that encapsulate the app's canonical interpretation of ambiguous API encodings so UI, snapshots, cache, and AI layers all read the data the same way.

The file is Hyundai/Genesis-specific. Kia has its own parallel model set (see `KiaRepository.kt`, which maps `KiaVehicleSummary.toVehicle()` into the *same* shared `Vehicle` type — KiaRepository.kt:138).

---

## 2. Public surface (every declaration)

### Serializable API DTOs

- **`TokenResponse`** (Models.kt:22-28) — oauth token endpoint body (login and refresh share this shape). Fields: `accessToken` (`@SerialName("access_token")`), `refreshToken` (`@SerialName("refresh_token")`, nullable — a refresh response may reuse the old token and omit it), `expiresIn` (`@SerialName("expires_in")`, String), `tokenType` (`@SerialName("token_type")`).
- **`EnrollmentResponse`** (Models.kt:34-37) — `enrolledVehicleDetails: List<EnrolledVehicle>` defaulting to `emptyList()`. Body from enrollment/details endpoint (all cars on the account).
- **`EnrolledVehicle`** (Models.kt:42-45) — single field `vehicleDetails: VehicleDetails`. A redundant API wrapper layer, unwrapped by `toVehicle()`.
- **`VehicleDetails`** (Models.kt:47-59) — raw per-vehicle record: `vin`, `regid`, `nickName?`, `modelName?`, `modelYear?`, `vehicleGeneration?`, `brandIndicator?`, `enrollmentDate?`, `evStatus?`, `odometer?` (all except `vin`/`regid` nullable).
- **`VehicleStatusResponse`** (Models.kt:77-80) — `vehicleStatus: VehicleStatus?` (null when the car never reported a status).
- **`VehicleStatus`** (Models.kt:82-122) — the central status payload; see §4.
- **`WindowOpen`** (Models.kt:125-134) — `frontLeft/frontRight/backLeft/backRight: Int?`; computed `anyOpen: Boolean` = any position `== 1`.
- **`SeatHeaterVentState`** (Models.kt:137-143) — `flSeatHeatState/frSeatHeatState/rlSeatHeatState/rrSeatHeatState: Int?`. Used to infer which seats physically exist.
- **`TirePressure`** (Models.kt:147-150) — single `all: Int?` combined status.
- **`DoorOpen`** (Models.kt:153-162) — `frontLeft/frontRight/backLeft/backRight: Int?`; computed `anyOpen`.
- **`TirePressureLamp`** (Models.kt:168-190) — 10 nullable Int fields capturing two naming conventions; merged via computed getters `all`, `frontLeft`, `frontRight`, `rearLeft`, `rearRight`, and `hasWarning: Boolean`.
- **`Dte`** (Models.kt:195-199) — distance-to-empty: `value: Double?`, `unit: Int?`.
- **`TempValue`** (Models.kt:204-208) — climate setpoint: `value: String?` (quoted numeric string on the wire), `unit: Int?`.
- **`Battery12V`** (Models.kt:210-228) — `batSoc: Int?`, `batState: Int?`; computed `health: String?`.
- **`EvStatus`** (Models.kt:230-247) — EV battery/charge block; computed `pluggedInLabel`. See §4.
- **`RemainTime2`** (Models.kt:253-258) — `atc/etc1/etc3: TimeValue?` charge-time estimates.
- **`TimeValue`** (Models.kt:262-266) — `value: Double?`, `unit: Int?`.
- **`ReservChargeInfos`** (Models.kt:269-279) — `targetSOClist: List<TargetSOC>`; method `level(plugType: Int): Int?`.
- **`TargetSOC`** (Models.kt:284-288) — `plugType: Int?` (0=DC, 1=AC), `targetSOClevel: Int?`.
- **`DrvDistance`** (Models.kt:293-296) — `rangeByFuel: RangeByFuel?`.
- **`RangeByFuel`** (Models.kt:300-303) — `totalAvailableRange: Dte?`.
- **`ClimateRequest`** (Models.kt:351-361) — full climate-start request; see §4.
- **`ClimatePreset`** (Models.kt:364-369) — `id`, `name`, `request: ClimateRequest`.
- **`VehicleLocationResponse`** (Models.kt:376-381) — findMyCar body: `coord: Coord?`, `head: Double?` (compass heading, only present here), `speed: Speed?`.
- **`VehicleLocation`** (Models.kt:384-389) — embedded-in-status location: `coord: Coord?`, `time: String?`, `speed: Speed?` (no `head`).
- **`Coord`** (Models.kt:393-398) — `lat/lon/alt: Double?` (`alt` captured but unused in UI).
- **`Speed`** (Models.kt:403-407) — `value: Double?`, `unit: Int?`.
- **`GeoLocation`** (Models.kt:410-416) — UI-facing: `latitude: Double`, `longitude: Double` (non-null), `speed: Double?`.
- **`TripMeasure`** (Models.kt:477-481) — `value: Double?`, `unit: Int?`.
- **`EvTrip`** (Models.kt:488-532) — one drive from evTripDetails; see §4.
- **`EvTripDetailsResponse`** (Models.kt:534-537) — `tripdetails: List<EvTrip>`.

### Domain / non-serializable types

- **`Vehicle`** (Models.kt:62-71) — flattened car: `vin`, `regId`, `name`, `model`, `generation`, `brandIndicator`, `isEv: Boolean`, `odometer: String?`. **Not `@Serializable`** — built by mapping functions.
- **`SeatCapability`** (Models.kt:341-348) — `frontLeft/frontRight/rearLeft/rearRight: Boolean = false`; computed `any: Boolean`. Not serializable.

### Enum

- **`SeatLevel`** (Models.kt:309-338) — `@Serializable enum` with `(apiValue: Int, label: String)`. See §4.

### Public extension helpers

- **`VehicleStatus.percentFor(hasBattery: Boolean): Int?`** (Models.kt:425-426) — headline % : `evStatus?.batteryStatus` when `hasBattery`, else `fuelLevel`.
- **`VehicleStatus.rangeMiFor(hasBattery: Boolean): Int?`** (Models.kt:433-436) — headline range in miles; EV battery range or DTE fallback.
- **`EvStatus.targetForCurrentPlug(): Int?`** (Models.kt:446-450) — charge-limit target for the currently connected charger.
- **`DoorOpen.openLabels(): List<String>`** (Models.kt:466).
- **`WindowOpen.openLabels(): List<String>`** (Models.kt:468).
- **`GeoLocation.coordString(decimals: Int = 5): String`** (Models.kt:471-472) — `"lat, lon"` formatted.

### Computed instance members (public API on the types above)

- `VehicleStatus.isDriving: Boolean` (Models.kt:121) — `(vehicleLocation?.speed?.value ?: 0.0) > 0.0`.
- `WindowOpen.anyOpen` / `DoorOpen.anyOpen` (Models.kt:132, 160).
- `TirePressureLamp.{all, frontLeft, frontRight, rearLeft, rearRight, hasWarning}` (Models.kt:181-189).
- `Battery12V.health` (Models.kt:220-227).
- `EvStatus.pluggedInLabel` (Models.kt:240-246).
- `ReservChargeInfos.level(plugType)` (Models.kt:277-278).
- `SeatCapability.any` (Models.kt:347).
- `SeatLevel.{isCool, isHeat}` + companion members (see §4).
- `EvTrip.{driveMinutes, idleMinutes, usedKwh, regenKwh}` (Models.kt:507-531).

---

## 3. Internal structure

The file has exactly one private declaration:

- **`private fun openPositions(fl, fr, bl, br: Int?): List<String>`** (Models.kt:458-463) — the shared engine behind both `DoorOpen.openLabels()` and `WindowOpen.openLabels()`. Both door and window state use the identical `0=closed / 1=open` per-position encoding, so this maps the four raw `Int?` flags to human labels (`"front-left"`, `"front-right"`, `"rear-left"`, `"rear-right"`) via `listOfNotNull`, emitting a name only when the flag `== 1`. Note the naming translation: the wire calls rear positions `backLeft`/`backRight`, but the labels say `"rear-left"`/`"rear-right"`. Closed **and** unknown/null both produce no entry (not a "closed" label).

Everything else is public. There are no init blocks, secondary constructors, or private backing fields. State is entirely per-instance immutable `val`s; all "logic" is in computed-property getters and extension functions.

Control flow of the non-trivial functions:

- **`rangeMiFor`** (Models.kt:433-436): (1) reach the deeply nested EV range: `evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value`. (2) If `hasBattery` is false, discard it (`if (hasBattery) batteryRange else null`). (3) Elvis-fallback to `dte?.value`. (4) `.toInt()` (truncates). Net: non-EV or missing-EV-range cars still show DTE rather than a blank.
- **`targetForCurrentPlug`** (Models.kt:446-450): `when (batteryPlugin)` — `1` (DC) → `reservChargeInfos?.level(0)`; `2` (AC) → `reservChargeInfos?.level(1)`; else null. This is the crossover point of the two plug-numbering schemes (see §8).
- **`ReservChargeInfos.level`** (Models.kt:277-278): linear search `targetSOClist.firstOrNull { it.plugType == plugType }?.targetSOClevel`. Explicitly a scan, not index lookup — the API guarantees neither order nor fixed index.
- **`Battery12V.health`** (Models.kt:220-227): guard `batSoc == null → null`; `batState == 0 → "Needs attention"`; then SoC bands `>=75 "Good"`, `>=50 "Fair"`, else `"Low"`.
- **`EvTrip.idleMinutes`** (Models.kt:514-519): needs both `duration.value` and `mileagetime.value` (returns null if either missing), computes `(total - driving)/60`, `.toInt()`, `.coerceAtLeast(0)`.

---

## 4. Data & types — field by field with encodings

### `VehicleStatus` (Models.kt:82-122)
The main status snapshot. All fields nullable.
- `doorLock: Boolean?` — true = locked.
- `airCtrlOn: Boolean?` — climate/HVAC running.
- `engine: Boolean?`, `acc: Boolean?` (accessory power), `trunkOpen`, `hoodOpen`, `defrost: Boolean?`.
- `doorOpen: DoorOpen?`, `windowOpen: WindowOpen?`.
- `tirePressureLamp: TirePressureLamp?`.
- `dte: Dte?` — distance-to-empty (fuel/combined).
- `airTemp: TempValue?` — climate setpoint.
- `battery: Battery12V?` — 12V accessory battery.
- `evStatus: EvStatus?` — present on EVs/PHEVs.
- `dateTime: String?` — when the car reported.
- `vehicleLocation: VehicleLocation?` — **last-known GPS bundled free with status** (no rate-limited findMyCar call needed; this is how the official app shows location) (Models.kt:99-101).
- Climate sub-features: `steerWheelHeat: Int?`, `sideBackWindowHeat: Int?`, `sideMirrorHeat: Int?`, `seatHeaterVentState: SeatHeaterVentState?`.
- Diagnostics: `lowFuelLight`, `washerFluidStatus`, `breakOilStatus`, `smartKeyBatteryWarning: Boolean?`, `fuelLevel: Int?`, `tirePressure: TirePressure?`.
- Computed `isDriving` (Models.kt:121): true when embedded speed > 0. Comment notes the phone's `AppViewModel.isDriving()` layers live GPS on top of this; the bare version is what the **watch's standalone command path** uses to apply the "car rejects climate while driving" gate.

### `EvStatus` (Models.kt:230-247)
- `batteryCharge: Boolean?` — actively charging.
- `batteryStatus: Int?` — **state-of-charge %** (this is the EV headline percent used by `percentFor`).
- `batteryPlugin: Int?` — **0 = unplugged, 1 = fast (DC), 2 = portable/AC** (Models.kt:239).
- `drvDistance: List<DrvDistance>` — range list; only `first()` read in practice.
- `remainTime2: RemainTime2?`, `reservChargeInfos: ReservChargeInfos?`.
- `pluggedInLabel: String?` (Models.kt:240-246): maps `batteryPlugin` 0→"Not plugged in", 1→"Plugged in (DC fast)", 2→"Plugged in (AC)", else null.

### `TirePressureLamp` (Models.kt:168-190)
Captures two generation-specific naming conventions for the same data and merges via getters with `?:` preference (older `…LampAll/FL/FR/RL/RR` names win over newer `…WarningLamp…`). `hasWarning` = any of the five merged values is non-null and non-zero.

### `Battery12V` (Models.kt:210-228)
`batSoc: Int?`, `batState: Int?`. **`batSignalReferenceValue` is deliberately NOT declared** (comment Models.kt:214-217): some CCNC head units return it as an object `{"batWarning":65}` rather than a number, which would break parsing; it's unused, so `ignoreUnknownKeys` skips it regardless of shape.

### `SeatLevel` enum (Models.kt:309-338)
`@Serializable`, `(apiValue: Int, label: String)`. Entries & apiValues: `HIGH_COOL(5)`, `MED_COOL(4)`, `LOW_COOL(3)`, `OFF(0)`, `LOW_HEAT(6)`, `MED_HEAT(7)`, `HIGH_HEAT(8)`.
- `isCool: Boolean` = `apiValue in 3..5`; `isHeat` = `apiValue in 6..8` (Models.kt:319-320).
- Companion:
  - `ventilatedRange` (Models.kt:324): full 7-item list (cool→off→heat).
  - `heatOnlyRange` (Models.kt:327): `[OFF, LOW_HEAT, MED_HEAT, HIGH_HEAT]`.
  - `rangeFor(canCool, canHeat): List<SeatLevel>` (Models.kt:330-334): `buildList` — cool triplet only if `canCool`, always `OFF`, heat triplet only if `canHeat`.
  - `fromApi(value: Int?): SeatLevel` (Models.kt:336): reverse lookup by `apiValue`, **defaulting to `OFF`** for unknown/null. Note the encoding gap: apiValues 1 and 2 map to nothing and fall to `OFF`.

The `apiValue` scheme (0=off, 3-5=cool, 6-8=heat) matches the domain fact that these ints cross the phone↔watch wire (WearSync `climate` path) as raw ints, so `fromApi`/`apiValue` are the (de)serializers for that transport.

### `ClimateRequest` (Models.kt:351-361)
`tempF: Int`, `defrost: Boolean`, `durationMinutes: Int`, `steeringWheelHeat: Boolean = false`, and four `SeatLevel` fields (`seatFrontLeft/FrontRight/RearLeft/RearRight`), each defaulting to `SeatLevel.OFF`. Fully `@Serializable` — travels the wire and is embedded in `ClimatePreset`.

### `EvTrip` (Models.kt:488-532)
Energy figures are **watt-hours**; times **seconds**; speeds **mph** (Models.kt:485-486). Fields: `startdate: String?`, and Doubles `distance`, `totalused`, `drivetrain`, `climate`, `accessories`, `batterycare`, `regen`; plus `TripMeasure?` fields `odometer`, `mileagetime`, `duration`, `avgspeed`, `maxspeed`. Computed:
- `driveMinutes: Int?` — `mileagetime.value / 60` truncated (partial minute dropped) (Models.kt:507).
- `idleMinutes: Int?` — see §3, clamped ≥0.
- `usedKwh: Double?` — `Math.round(totalused/100.0)/10.0` = kWh to one decimal, done in Long arithmetic to avoid FP accumulation (Models.kt:521-527).
- `regenKwh: Double?` — same mechanism on `regen` (Models.kt:531).

### `Vehicle` (Models.kt:62-71) — how it's built
Not serializable; produced by `VehicleDetails.toVehicle()` (BlueLinkApi.kt:472-481): `name` = non-blank `nickName` ?: `modelName` ?: last 6 of VIN; `model` = `"$modelYear $modelName"` trimmed, blank → `"Hyundai"`; `generation` = `vehicleGeneration ?: "2"`; `brandIndicator` = `?: ""`; `isEv` = `evStatus.equals("E", ignoreCase = true)`. Kia builds the same type via `KiaVehicleSummary.toVehicle()` (KiaRepository.kt:138); the snapshot layer also has a `toVehicle()` (SnapshotStore.kt:65).

---

## 5. State & concurrency

**None held here.** Every type is an immutable data class (`val`-only) or an enum. There is no `StateFlow`, `DataStore`, `remember`, mutable field, lock, dispatcher, or coroutine anywhere in the file. Computed properties are pure functions of the instance's fields, recomputed on every access (no caching) — cheap, allocation-light list scans at worst.

Because instances are immutable, they are freely shareable across threads. This matters given the domain fact that `BlueLinkGate.statusMutex` serializes all status/command calls: the DTOs decoded under that mutex can be handed to UI/snapshot/wear threads without synchronization. Recomposition is driven by whoever holds these in a `State`/`StateFlow` elsewhere; this file contributes no recomposition triggers of its own.

---

## 6. Collaborators & data flow

**Producers (data in):**
- `BlueLinkApi.kt` decodes JSON responses into these DTOs (`EnrollmentResponse`, `VehicleStatusResponse`, `VehicleLocationResponse`, `EvTripDetailsResponse`, `TokenResponse`) and maps `VehicleDetails.toVehicle()` (BlueLinkApi.kt:144, 472).
- `KiaRepository.kt` maps its own summaries into the shared `Vehicle` (KiaRepository.kt:93, 138).
- `SnapshotStore.kt` reconstructs a `Vehicle` and holds `hasBattery` (the powertrain override consumed by `percentFor`/`rangeMiFor`) (SnapshotStore.kt:65; comment Models.kt:424).

**Consumers (data out):**
- UI/AppViewModel: `percentFor`, `rangeMiFor`, `isDriving`, `openLabels`, `SeatLevel.rangeFor`, `pluggedInLabel`, `Battery12V.health`, `targetForCurrentPlug`, `coordString`.
- `WearCommandRunner.kt` uses `snap.toVehicle()` and the driving gate (WearCommandRunner.kt:29, 161) — the watch's standalone command path referenced in the `isDriving` comment.
- `ClimateRequest`/`ClimatePreset`/`SeatLevel` are serialized across the **WearSync wire** (`climate`/`presets` DataItem paths, `command` message path) — `SeatLevel.apiValue`/`fromApi` are the int (de)serializers for that hop, matching the domain fact that seat levels cross the wear wire as ints.

**Channels:** function-call decoding (kotlinx.serialization from HTTP bodies), then plain in-memory object passing. Nothing here touches DataStore, intents, or WorkManager directly; those layers consume/persist these types.

---

## 7. Invariants & assumptions

1. **Nullable-everywhere for API DTOs**: every wire field is `?`/defaulted so `ignoreUnknownKeys` + missing keys never throw. Non-null fields are confined to hand-built domain types (`Vehicle.vin/regId/...`, `GeoLocation.latitude/longitude`).
2. **Plug encodings are two different schemes** and must not be conflated: `EvStatus.batteryPlugin` uses 0=unplugged/1=DC/2=AC; `TargetSOC.plugType` uses 0=DC/1=AC. `targetForCurrentPlug` is the only place they cross.
3. **`percentFor`/`rangeMiFor` require the caller's `hasBattery`**, not `isEv` — the status payload has no notion of a PHEV manually tracked as electric (Models.kt:420-424). Callers must pass the powertrain override.
4. **`drvDistance` first-entry only**: EV range reads assume index 0 is the primary energy source (Models.kt:290-292).
5. **Position flags are `1`==open**; any other value (including 0 and null) is treated as not-open.
6. **`SeatLevel.fromApi` never returns null** — unknown apiValues (including 1, 2) collapse to `OFF`. Downstream code can assume a valid `SeatLevel` always exists.
7. `TempValue.value` is a **String**, not a Double, because the API quotes it — callers must parse.
8. `EvTrip` energy = watt-hours, times = seconds, speeds = mph. Conversions live in the computed getters; raw fields are unconverted.
9. `isDriving` assumes `vehicleLocation.speed.value` is authoritative for the "moving" gate absent a live GPS reading.

## 8. Gotchas & sharp edges

- **The dual plug numbering** (repeated in comments Models.kt:239, 282-283, 438-445) is the single biggest footgun. `batteryPlugin` 1/2 (DC/AC) must be remapped to `plugType` 0/1 (DC/AC) via `targetForCurrentPlug`. Get this backwards and you show the AC limit for a DC charger.
- **`batSignalReferenceValue` is intentionally absent** (Models.kt:214-217). Do not add it as `Int?` — some head units send it as an object and it would break decoding. Leave it to `ignoreUnknownKeys`.
- **`fromApi` swallows apiValues 1 and 2** — there is no seat level for those; they silently become `OFF`. Not a bug, but surprising.
- **`rangeMiFor` truncates** via `.toInt()` and **falls back to DTE** even for `hasBattery` cars when the EV range field is empty — deliberate, so the UI shows *something* (Models.kt:429-432).
- **`usedKwh`/`regenKwh` use a roundabout `Math.round(x/100.0)/10.0`** to get one-decimal kWh entirely in Long arithmetic, avoiding FP accumulation (Models.kt:521-527). Don't "simplify" to `x/1000.0` — you'd change the rounding.
- **`driveMinutes` drops partial minutes** (integer division after `/60`); `idleMinutes` clamps to ≥0 to absorb server-side independent-rounding disagreement between `duration` and `mileagetime` (Models.kt:505-518).
- **`openLabels` renames back→rear**: wire fields `backLeft/backRight` surface as `"rear-left"/"rear-right"` labels; only `==1` positions appear, closed/unknown are omitted (not labeled "closed") (Models.kt:458-468).
- **`TirePressureLamp` getters prefer the non-`Warning` variant** (`tirePressureLampAll ?: tirePressureWarningLampAll`); if a future generation only sends the `Warning*` keys they still resolve, but mixed payloads take the older key first.
- **`Vehicle` is not `@Serializable`** while nearly everything around it is — it's a computed domain object, so don't try to decode it from the wire.
- **`VehicleLocation` lacks `head`** (compass heading); only the rate-limited `VehicleLocationResponse` (findMyCar) carries it (Models.kt:376-389). If you need heading you must pay the rate-limited call.
- **`Coord.alt` is captured but never surfaced** in UI (Models.kt:392-393).
