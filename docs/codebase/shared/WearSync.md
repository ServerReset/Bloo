# WearSync — phone ↔ watch wire protocol

**File:** `shared/src/main/java/com/bloo/bluelink/data/WearSync.kt` (537 lines)
**Package:** `com.bloo.bluelink.data`

---

## 1. Purpose

`WearSync.kt` defines the **entire wire protocol** that the phone app (`:app`) and the
watch app (`:wear`) use to talk over the Google **Wearable Data Layer**. It lives in
`:shared` so both modules link the exact same constants, DTOs, and JSON codec — there is
one source of truth for the format, and the two apps can never disagree about it at
compile time (only at runtime if versions skew, which the code is explicitly built to
tolerate).

The unit is purely **data + (de)serialization**. It contains no Data Layer I/O itself: it
never touches `PutDataMapRequest`, `MessageClient`, coroutines, or Android APIs. It only
provides:

1. **Path constants** — the string keys under which DataItems and Messages are published
   (`WearSync.PATH_*`).
2. **DataMap key constants** — `KEY_PAYLOAD`, `KEY_TIMESTAMP`.
3. A configured `kotlinx.serialization` **`Json` instance** tuned for cross-version safety.
4. **`encodeX` / `decodeX` function pairs** — one per payload type — each `decode` wrapped
   in `runCatching` with a safe fallback so a malformed / version-skewed payload never
   crashes the receiver.
5. The **`@Serializable` data classes** and the `WearAction` verb constants that make up
   every payload body.

The actual Data Layer plumbing lives in the collaborators: `:app`'s `WearBridge.kt`
(phone publisher) + `WearPhoneService.kt` (phone listener), and `:wear`'s `WearComms.kt`,
`WearStateWriter.kt`, `WearListenerService.kt`, `WearViewModel.kt`, `WearCommandEvents.kt`.

There are **two conceptual channels** (comment, WearSync.kt:15–21):

- **State channel** — `PATH_STATE` (and the other DataItem paths) — phone publishes a
  `WearStatePayload` (the same compact `VehicleSnapshot` list widgets/QS tiles use) as a
  **DataItem**; the watch mirrors it so its tiles render instantly.
- **Command / sync channel** — `PATH_COMMAND`, `PATH_SYNC_REQUEST` — the watch sends a
  `WearCommand` as a **Message**; the phone executes it with its already-authenticated
  repository (token refresh, PIN, etc. all live on the phone).

Because everything is plain JSON, neither side needs Play-Services Wearable types to
(de)serialize, and the format is identical to what the phone already persists in
`SnapshotStore` (comment WearSync.kt:22–25).

---

## 2. Public surface

Three top-level public declarations plus a large set of `@Serializable` data classes.

### 2.1 `object WearSync` (WearSync.kt:27–223)

The protocol namespace. All members are public.

#### Path constants — DataItem paths (phone → watch unless noted)

| Constant | Value | Direction | Meaning |
|---|---|---|---|
| `PATH_STATE` (30) | `/bloo/state` | phone → watch | Snapshot of every car (`WearStatePayload`). |
| `PATH_AUTH` (35) | `/bloo/auth` | phone → watch | Session tokens for standalone watch operation. Sent separately + rarely so it isn't re-published on every refresh. |
| `PATH_SETTINGS` (40) | `/bloo/settings` | phone → watch | Appearance + preferences (theme colours, temp unit, UI scale). |
| `PATH_PRESETS` (43) | `/bloo/presets` | phone → watch | Saved climate presets keyed by VIN. |
| `PATH_CLIMATE` (48) | `/bloo/climate` | **bidirectional** | Live climate draft (slider positions, active preset) keyed by VIN. Either side writes; both mirror. |
| `PATH_EXTRAS` (52) | `/bloo/extras` | phone → watch | Weather, car photo URLs, AI summaries. |
| `PATH_PEBBLE_ORDER` (57) | `/bloo/pebble_order` | watch → phone | A car's reordered pebble order; phone persists as section order and re-publishes via `PATH_SETTINGS`. |
| `PATH_LOCAL` (79) | `/bloo/local` | watch → phone | Local display prefs (font/UI scale). |
| `PATH_AI_TOGGLE` (84) | `/bloo/ai_toggle` | watch → phone | AI summaries on/off. Separate path so it can't race with `PATH_LOCAL`'s uiScale echo. |
| `PATH_AURORA_TOGGLE` (88) | `/bloo/aurora_toggle` | watch → phone | Aurora background on/off. Own path for same reason. |

#### Path constants — Message paths

| Constant | Value | Direction | Meaning |
|---|---|---|---|
| `PATH_COMMAND` (60) | `/bloo/command` | watch → phone | "Run this command" (`WearCommand`). |
| `PATH_SYNC_REQUEST` (63) | `/bloo/sync_request` | watch → phone | "Push me fresh state" (optionally refreshing). |
| `PATH_COMMAND_RESULT` (66) | `/bloo/command_result` | phone → watch | Result of an executed command (`WearCommandResult`). |
| `PATH_SYNC_RESULT` (70) | `/bloo/sync_result` | phone → watch | Result of a watch-requested Drive sync (`WearSyncResult`). |
| `PATH_AI_RESULT` (75) | `/bloo/ai_result` | phone → watch | Result of a watch-requested AI summary (`WearAiResult`). Sent on **both** success and failure so the watch's busy spinner always resolves. |

#### DataMap / message keys

- `KEY_PAYLOAD = "payload"` (91) — DataMap/message key holding the JSON body string.
- `KEY_TIMESTAMP = "ts"` (96) — DataMap key holding a monotonic timestamp so **identical
  state still publishes as a *changed* DataItem** (the Data Layer dedupes byte-identical
  items otherwise). This is the critical anti-dedup trick.

#### `val json: Json` (98–109)

The shared codec. Configured with:
- `ignoreUnknownKeys = true` — one side can add a field without breaking the other's
  decode; phone and watch are built/updated independently and won't always run the same
  version.
- `encodeDefaults = true` — payloads always include **every** field (even ones at default)
  so a stale receiver that doesn't know a new field still gets a fully-populated JSON
  shape for the fields it does understand.

#### Encode/decode function pairs (118–222)

Every `encode*` is `json.encodeToString(T.serializer(), value): String`. Every `decode*`
wraps the parse in `runCatching` and falls back to a safe default. Two fallback flavours:
returns a **default instance** (the payload "always exists") vs. returns **null** ("we
have nothing yet").

| Encode | Decode | Signature of decode | Fallback on failure/null |
|---|---|---|---|
| `encodeState` (118) | `decodeState` (121) | `(raw: String?) -> WearStatePayload` | `WearStatePayload()` — **plus element-by-element recovery** (see §3). |
| `encodeAuth` (142) | `decodeAuth` (145) | `-> WearAuthBundle` | `WearAuthBundle()` |
| `encodeSettings` (149) | `decodeSettings` (152) | `-> WearSettingsPayload?` | **null** |
| `encodePresets` (155) | `decodePresets` (158) | `-> WearPresets` | `WearPresets()` |
| `encodeClimate` (162) | `decodeClimate` (165) | `-> WearClimateState` | `WearClimateState()` |
| `encodeExtras` (169) | `decodeExtras` (172) | `-> WearExtras` | `WearExtras()` |
| `encodePebbleOrder` (176) | `decodePebbleOrder` (179) | `-> WearPebbleOrder?` | **null** |
| `encodeLocal` (182) | `decodeLocal` (185) | `-> WearLocalPayload?` | **null** |
| `encodeCommand` (188) | `decodeCommand` (191) | `-> WearCommand?` | **null** |
| `encodeResult` (194) | `decodeResult` (197) | `-> WearCommandResult?` | **null** |
| `encodeSyncResult` (200) | `decodeSyncResult` (203) | `-> WearSyncResult?` | **null** |
| `encodeAiResult` (206) | `decodeAiResult` (209) | `-> WearAiResult?` | **null** |
| `encodeAiToggle` (212) | `decodeAiToggle` (215) | `-> WearAiTogglePayload?` | **null** |
| `encodeAuroraToggle` (218) | `decodeAuroraToggle` (221) | `-> WearAuroraTogglePayload?` | **null** |

The null-returning decodes are for "event / message" payloads where "nothing received"
is a distinct, meaningful state; the default-instance decodes are for mirrored-state
payloads that are always meaningfully present.

### 2.2 `object WearAction` (WearAction.kt logic at WearSync.kt:243–277)

Stable **command verb** string constants used as `WearCommand.action` values. Deliberately
plain strings, **not a Kotlin enum** (comment 234–242): they're serialized as free-form
text, so neither side needs matching enum ordinals/names to stay in lockstep across
independent updates; an unrecognized action string is simply ignored by the receiver
rather than failing the whole decode.

- **TOGGLE_\*** verbs ask the receiver to flip whatever the current state is; the explicit
  `_ON`/`_OFF`/`LOCK`/`UNLOCK` variants force a specific state. (Toggle direction is
  decided by re-reading the `SnapshotStore` on the phone — a documented domain fact.)

| Constant | Value | Notes |
|---|---|---|
| `TOGGLE_LOCK` (244) | `toggle_lock` | |
| `LOCK` (245) | `lock` | |
| `UNLOCK` (246) | `unlock` | |
| `TOGGLE_CLIMATE` (247) | `toggle_climate` | uses climate fields of `WearCommand`. |
| `CLIMATE_ON` (248) | `climate_on` | uses climate fields of `WearCommand`. |
| `CLIMATE_OFF` (249) | `climate_off` | |
| `TOGGLE_CHARGE` (250) | `toggle_charge` | |
| `CHARGE_ON` (251) | `charge_on` | |
| `CHARGE_OFF` (252) | `charge_off` | |
| `FLASH_LIGHTS` (256) | `flash_lights` | Hyundai/Genesis only (`Vehicle.supportsHornLights`); Kia US API has neither. |
| `HORN_AND_LIGHTS` (257) | `horn_and_lights` | Hyundai/Genesis only. |
| `SET_CHARGE_LIMITS` (260) | `set_charge_limits` | applies `WearCommand.acLimit`/`dcLimit`. |
| `REFRESH` (263) | `refresh` | re-fetch one car's status, or all cars when `WearCommand.vin` is blank. |
| `AI_SUMMARY` (266) | `ai_summary` | phone generates + pushes an AI summary. |
| `DRIVE_SYNC` (270) | `drive_sync` | phone imports latest settings from Google Drive and re-publishes to watch. |
| `WEATHER_DEVICE_LOCATION` (276) | `weather_device_location` | phone sets its home weather location from its own GPS; watch has no independent weather fetch. |

### 2.3 Top-level `@Serializable` data classes

All are documented field-by-field in §4.

- `WearStatePayload` (226)
- `WearCommand` (280)
- `WearCommandResult` (300)
- `WearSyncResult` (309)
- `WearAiResult` (316)
- `WearSessionDto` (325)
- `WearAuthBundle` (336)
- `WearColorRoles` (345)
- `WearSeatConfig` (383)
- `WearSettingsPayload` (397)
- `WearLocalPayload` (444)
- `WearAiTogglePayload` (464)
- `WearAuroraTogglePayload` (474)
- `WearPebbleOrder` (481)
- `WearPresets` (488)
- `ClimateSync` (496)
- `WearClimateState` (510)
- `WearWeather` (517)
- `WearExtras` (530)

---

## 3. Internal structure

There are **no private members**. The only non-trivial control flow is `decodeState`.

### `decodeState(raw: String?): WearStatePayload` (WearSync.kt:121–140) — step by step

1. If `raw == null` → return an empty `WearStatePayload()` (122).
2. **Fast path** (124): try to decode the whole payload with the generated serializer.
   `runCatching { ... }.getOrNull()?.let { return it }` — on clean success, return
   immediately.
3. **Element-by-element recovery** (129–139): reached only if the whole-payload decode
   threw (e.g. the phone published a schema the watch's older build doesn't have on one
   vehicle). Steps:
   - `json.parseToJsonElement(raw).jsonObject` — reparse to a generic tree.
   - `obj["vehicles"]?.jsonArray?.mapNotNull { el -> runCatching { decode one VehicleSnapshot }.getOrNull() }` — decode each vehicle **independently**; a malformed element yields null and is dropped by `mapNotNull`, so **every car that still parses is kept** instead of blanking all of them. `?: emptyList()` if `vehicles` is absent/not an array.
   - Rebuild `WearStatePayload` with the recovered `vehicles`, plus `selectedVin` read via
     `jsonPrimitive.contentOrNull` and `producedAt` via `jsonPrimitive.longOrNull ?: 0L`.
   - The whole recovery block is itself wrapped in `runCatching { ... }.getOrDefault(WearStatePayload())`, so even a totally malformed root JSON degrades to an empty payload rather than throwing.

All other decodes share the simple defensive shape described in the comment at 111–117:
`raw?.let { runCatching { decode }.getOrNull() } ?: fallback`.

---

## 4. Data & types

Defaults `DEFAULT_CLIMATE_TEMP_F = 72` and `DEFAULT_CLIMATE_DURATION_MIN = 10` come from
`shared/.../FormatUtils.kt:101–102`.

Seat ints on the wire are `SeatLevel.apiValue` (defined in `Models.kt:310`): **0 = off,
3/4/5 = low/med/high cool, 6/7/8 = low/med/high heat**. The wire keeps them flat ints so
neither side needs the enum. Note the watch collapses cooling to off (comment 493–495) —
the phone supports cool levels, the watch does not, so seat values coming from the watch
are effectively 0 or 6–8.

### `WearStatePayload` (226–232)
The full car list mirrored to the watch plus which is selected.
- `vehicles: List<VehicleSnapshot> = emptyList()` — the compact snapshots (defined in `SnapshotStore.kt:21`; shared with widgets/QS tiles).
- `selectedVin: String? = null`.
- `producedAt: Long = 0L` — server/phone wall-clock (ms) when this snapshot was produced.

### `WearCommand` (280–297)
A command the watch wants run for one car.
- `vin: String` — target car (blank = "all", for `REFRESH`).
- `action: String` — a `WearAction.*` value.
- `tempF: Int = DEFAULT_CLIMATE_TEMP_F` (72) — for `CLIMATE_ON`/`TOGGLE_CLIMATE`.
- `durationMinutes: Int = DEFAULT_CLIMATE_DURATION_MIN` (10).
- `defrost: Boolean = false`.
- `steeringWheelHeat: Boolean = false`.
- `seatFrontLeft/Right`, `seatRearLeft/Right: Int = 0` — `SeatLevel.apiValue` ints (0 = off).
- `acLimit: Int = 80`, `dcLimit: Int = 90` — targets for `SET_CHARGE_LIMITS`.

### `WearCommandResult` (300–306)
Phone's reply after attempting a `WearCommand`. `vin: String`, `action: String`,
`ok: Boolean`, `message: String? = null`.

### `WearSyncResult` (309–313)
Reply after a watch-requested Drive sync. `ok: Boolean`, `message: String? = null`.

### `WearAiResult` (316–321)
Reply after a watch-requested AI summary. `vin: String`, `ok: Boolean`,
`message: String? = null`.

### `WearSessionDto` (325–333)
One brand's session, mirrored to the watch for standalone operation. Maps 1:1 onto
`SessionStore.Session`.
- `brand: String`, `accessToken: String`, `refreshToken: String? = null`,
  `username: String`, `pin: String`, `deviceId: String? = null`.
- Per the domain facts: for **Kia**, `accessToken` holds the `sid`, `refreshToken` holds
  the `rmtoken`, and `deviceId` is the id the rmtoken binds to.

### `WearAuthBundle` (336–339)
`sessions: List<WearSessionDto> = emptyList()` — every signed-in brand's session so the
watch can authenticate standalone.

### `WearColorRoles` (345–372)
The phone's **resolved** Material 3 role colours as **packed ARGB `Int`s**, so the watch
mirrors the exact theme without re-running palette/vibrancy maths. All 25 fields are
non-nullable `Int` (no defaults): `primary`, `onPrimary`, `primaryContainer`,
`onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`,
`onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`,
`onTertiaryContainer`, `background`, `onBackground`, `onSurface`, `onSurfaceVariant`,
`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `outline`,
`outlineVariant`, `error`, `onError`, `errorContainer`, `onErrorContainer`.

### `WearSeatConfig` (383–394)
Which seats actually have heat/cool + whether the steering wheel is heated. Mirrors
`:app`'s `SeatConfig` (not visible from `:shared`). API can't reliably report this, so
it's user-confirmed once on the phone and synced here so the watch's Comfort card only
shows controls the car has. **Field-for-field identical shape and defaults** to `SeatConfig`
so an unsynced/old watch falls back to the same default the phone uses.
- `driverHeat = true`, `driverCool = false`, `passHeat = true`, `passCool = false`,
  `rearLeftHeat = false`, `rearLeftCool = false`, `rearRightHeat = false`,
  `rearRightCool = false`, `steeringWheel = false`. (Default = "driver + passenger heat
  only".)

### `WearSettingsPayload` (397–441)
Appearance + preferences mirrored to the watch.
- `dark: Boolean = true`.
- `useFahrenheit: Boolean = true`.
- `unitSystem: String = "imperial"`.
- `uiScale: Float = 1f`.
- `colors: WearColorRoles? = null` — the app-wide resolved palette.
- `carColors: Map<String, WearColorRoles> = emptyMap()` — per-VIN palette overrides.
- `pebbleOrders: Map<String, List<String>> = emptyMap()` — per-VIN detail-section order so
  the watch lays out tiles like the phone.
- `hiddenSections: Map<String, Set<String>> = emptyMap()` — per-VIN pebble keys hidden on
  the phone, so the watch drops matching tiles.
- `seatConfigs: Map<String, WearSeatConfig> = emptyMap()` — per-VIN seat/steering capability.
- `aiEnabled: Boolean = false` — whether on-device AI summaries are on.
- `auroraEnabled: Boolean = false` — aurora background on/off. One shared flag; flipping on
  either device changes both by design.
- `auroraColorMode: String = "complementary"` — `"complementary"`, `"material"`, or
  `"custom"`.
- `auroraCustomColor: String? = null` — hex, only used when mode is `"custom"`.
- `hapticsEnabled: Boolean = true` — app-wide haptics, mirrored so a user who disabled it
  on the phone isn't buzzed on every watch tap.
- `settingsMode: String = "simple"` — `"simple"` or `"advanced"`; mirrors phone Settings
  view mode. **One-way (phone → watch)** — the watch has no UI to change it (unlike
  `aiEnabled`/`auroraEnabled`).

### `WearLocalPayload` (444–461)
Watch-local display prefs synced **back to the phone** (watch → phone).
- `uiScale: Float = 1f`.
- `unitSystem: String? = null`.
- `watchPinLockEnabled: Boolean = false` — mirrored to the phone **purely for the phone's
  Drive/manual backup record**; the PIN code itself never leaves the watch, and the phone
  **never pushes this back down** (a stale phone echo must not clobber a security-lock
  setting the watch just changed).
- `watchPinLockTiming: String = "immediate"` — `"off"/"immediate"/"1min"/"5min"/"10min"`;
  same backup-record reason.

### `WearAiTogglePayload` (464–467)
Watch → phone "turn AI summaries on/off". `enabled: Boolean = false`.

### `WearAuroraTogglePayload` (474–478)
Watch → phone "turn aurora on/off", optionally also set colour mode.
- `enabled: Boolean = false`.
- `colorMode: String? = null` — **null when the push only changes `enabled`**, so the
  phone leaves its current colour mode alone (toggling off/on never resets an unrelated
  setting).

### `WearPebbleOrder` (481–485)
One car's reordered pebble order, watch → phone. `vin: String`,
`order: List<String> = emptyList()`.

### `WearPresets` (488–491)
Saved climate presets per VIN. `byVin: Map<String, List<ClimatePreset>> = emptyMap()`
(`ClimatePreset` from `Models.kt:365` — `id`, `name`, `ClimateRequest`).

### `ClimateSync` (496–507)
The live climate draft for one car, shared both ways. Seat values are `SeatLevel.apiValue`
(platform-neutral; watch collapses cool to off).
- `activePresetId: String? = null`.
- `tempF: Int = 72`, `durationMinutes: Int = 10`.
- `defrost: Boolean = false`, `steering: Boolean = false`.
- `seatFrontLeft/Right`, `seatRearLeft/Right: Int = 0`.

Note: `ClimateSync` uses `steering` where `WearCommand` uses `steeringWheelHeat` — the two
climate-bearing DTOs have **different field names** for the steering-wheel flag.

### `WearClimateState` (510–513)
Per-VIN live drafts. `byVin: Map<String, ClimateSync> = emptyMap()`.

### `WearWeather` (517–527)
Compact current-conditions snapshot. **Celsius** like the phone's Weather; the watch
converts using the synced unit.
- `tempC: Double`, `feelsLikeC: Double` (no defaults — required).
- `highC: Double? = null`, `lowC: Double? = null`.
- `windKph: Double = 0.0`, `humidity: Int? = null`, `isDay: Boolean = true`,
  `code: Int = -1` (weather code; -1 = unknown).

### `WearExtras` (530–536)
Richer per-car content for phone parity.
- `homeWeather: WearWeather? = null`.
- `carWeather: Map<String, WearWeather> = emptyMap()` (keyed by VIN).
- `images: Map<String, String> = emptyMap()` — VIN → car photo URL.
- `ai: Map<String, String> = emptyMap()` — VIN → AI summary text.

---

## 5. State & concurrency

`WearSync` is a **stateless `object`** (singleton). It holds exactly one piece of shared
state: `val json: Json` (98), which is an **immutable, thread-safe** configured serializer
created once. There is no `StateFlow`, `DataStore`, `remember`, mutex, coroutine scope, or
dispatcher in this file. All functions are pure: `String` in / DTO out (and vice versa),
with no side effects.

Concurrency safety is therefore trivial here — the singleton `Json` can be called from any
thread. Any threading/dispatching happens entirely in the collaborators (`WearBridge`,
`WearComms`, the listener services). The DTOs are all immutable `data class`es.

Note: none of this participates in the `BlueLinkGate.statusMutex` serialization — that lock
guards the actual telematics command/status calls the phone runs **after** decoding a
`WearCommand`, not the (de)serialization here.

---

## 6. Collaborators & data flow

`WearSync` is the shared vocabulary; the endpoints that use it:

**Phone side (`:app`)**
- `wear/WearBridge.kt` — the phone **publisher**. Builds `PutDataMapRequest.create(WearSync.PATH_*)`,
  puts `WearSync.KEY_PAYLOAD` = `WearSync.encodeX(...)` and (for state/climate)
  `WearSync.KEY_TIMESTAMP`. Confirmed uses: `PATH_STATE`+`encodeState`+timestamp (72–76),
  `PATH_AUTH`+`encodeAuth` (113–114), `PATH_SETTINGS`+`encodeSettings` (205–206),
  `PATH_PRESETS`+`encodePresets` (248–251), `PATH_CLIMATE`+`encodeClimate`+timestamp
  (265–267), `PATH_EXTRAS`+`encodeExtras` (279–280).
- `wear/WearPhoneService.kt` — the phone **listener**. Receives watch Messages
  (`PATH_COMMAND`, `PATH_SYNC_REQUEST`) and watch-authored DataItems
  (`PATH_CLIMATE`, `PATH_PEBBLE_ORDER`, `PATH_LOCAL`, `PATH_AI_TOGGLE`,
  `PATH_AURORA_TOGGLE`); decodes via `WearSync.decodeCommand` etc.; executes with the
  phone's authenticated repository; replies with `PATH_COMMAND_RESULT` / `PATH_SYNC_RESULT`
  / `PATH_AI_RESULT`.
- `data/ClimateSyncStore.kt` — backs the bidirectional `PATH_CLIMATE` drafts.
- `ui/Screens.kt` — references the protocol (e.g. presets/climate UI).

**Watch side (`:wear`)**
- `WearComms.kt` — watch's sender/receiver wrapper.
- `WearStateWriter.kt` — writes watch-authored DataItems.
- `WearListenerService.kt` — receives phone DataItems/Messages and decodes them.
- `WearViewModel.kt` — mirrors decoded state into the watch UI.
- `WearCommandEvents.kt` — command event bus.

**Data in / out (channels):** everything crosses the **Wearable Data Layer** — either as a
**DataItem** (mirrored state, deduped unless `KEY_TIMESTAMP` differs) or a **Message**
(fire-and-forget command/result). The JSON body always lives under `KEY_PAYLOAD`. The
format is deliberately identical to the phone's own `SnapshotStore` persistence, so
`VehicleSnapshot` flows straight through.

---

## 7. Invariants & assumptions

1. **Both modules link the same `WearSync`.** The shared `:shared` module guarantees
   identical constants and serializers at compile time; runtime skew (different app
   versions) is the only divergence, and the codec is built for it.
2. **`action` strings are the contract, not enum ordinals.** Adding a verb never breaks
   decode; an unknown verb is silently ignored (§2.2). Callers must compare against
   `WearAction.*` constants, never hardcode literals.
3. **`KEY_TIMESTAMP` must change when you want a re-publish of identical state.** The Data
   Layer dedupes byte-identical DataItems; publishers bump `ts` (state/climate) to force
   delivery. Forgetting it means an identical snapshot silently never arrives.
4. **Seat ints on the wire are `SeatLevel.apiValue`** (0 off, 3–5 cool, 6–8 heat). Any code
   reading `seat*` fields must map through `SeatLevel.fromApi` (`Models.kt:336`), which
   returns `OFF` for unknown values.
5. **`WearColorRoles` fields are packed ARGB Ints with no defaults** — the sender must
   supply all 25; they are already-resolved colours, not to be re-themed on the watch.
6. **`WearWeather.tempC`/`feelsLikeC` are Celsius and required.** The watch converts to the
   synced unit; it must not assume Fahrenheit.
7. **`settingsMode` is one-way (phone → watch)**; `watchPinLockEnabled`/timing are
   backup-only records the phone must never push back down (§4 / §8).
8. **`WearAuroraTogglePayload.colorMode == null` means "don't touch colour mode."** The
   phone must treat null as "leave unchanged," not as a value to write.
9. **`ignoreUnknownKeys` + `encodeDefaults` together** are load-bearing: senders emit full
   shapes, receivers tolerate extra keys. Neither may be dropped without breaking
   cross-version compatibility.
10. **`WearSessionDto` maps 1:1 onto `SessionStore.Session`**; the Kia field overloading
    (`accessToken`=sid, `refreshToken`=rmtoken, `deviceId` bound to rmtoken) must be
    preserved on both ends.

---

## 8. Gotchas & sharp edges

- **`decodeState` is the only resilient decoder.** It does per-vehicle salvage so one
  version-skewed car doesn't blank the whole list (WearSync.kt:129–139). Every other
  `decode*` is all-or-nothing: a single malformed field yields the fallback (default
  instance or null) for the *entire* payload. This asymmetry is intentional — state is the
  hot, always-present payload; the rest are smaller/rarer.
- **Two fallback conventions, easy to conflate.** Some decodes return a default instance
  (`WearAuthBundle()`, `WearPresets()`, `WearClimateState()`, `WearExtras()`,
  `WearStatePayload()`) meaning "the mirrored state, empty"; others return **null**
  (`decodeSettings`, `decodePebbleOrder`, `decodeLocal`, `decodeCommand`, `decodeResult`,
  `decodeSyncResult`, `decodeAiResult`, `decodeAiToggle`, `decodeAuroraToggle`) meaning
  "we have nothing / no event." Callers must handle the null cases; treating a null decode
  as an empty default would be wrong.
- **The timestamp anti-dedup trick is invisible in the DTOs.** `WearStatePayload.producedAt`
  is a semantic field; the *delivery-forcing* timestamp is the separate DataMap
  `KEY_TIMESTAMP`. They are different values with different jobs — don't conflate them.
- **Three toggle-able settings live on their own DataItem paths on purpose.** `aiEnabled`
  and `auroraEnabled` (and colour mode) also appear inside `WearSettingsPayload`
  (phone → watch), but the watch → phone toggles use dedicated paths (`PATH_AI_TOGGLE`,
  `PATH_AURORA_TOGGLE`) so a toggle can never be raced/overwritten by `PATH_LOCAL`'s
  `uiScale` echo (comments 81–88). Collapsing them into `PATH_LOCAL` would reintroduce the
  race.
- **Shared aurora/AI flags change *both* devices by design** (comments 419–421). Flipping on
  the watch flips the phone and vice-versa; they are meant to stay in lockstep, not diverge.
- **`watchPinLockEnabled` is a stale-echo footgun the code deliberately avoids.** The phone
  keeps a copy only for its backup file and must never push it back — a briefly-stale phone
  echo silently clobbering the watch's security lock is worse than a one-time manual
  re-enable after a backup restore (comments 447–457).
- **Field-name mismatch between the two climate DTOs.** `WearCommand.steeringWheelHeat`
  vs. `ClimateSync.steering` — code copying between them must map the field, not assume the
  same name.
- **`FLASH_LIGHTS`/`HORN_AND_LIGHTS` are Hyundai/Genesis only.** Sending them for a Kia will
  be rejected downstream (Kia US API has neither); the watch UI should gate on
  `Vehicle.supportsHornLights` (comment 254–255).
- **`REFRESH` with a blank `vin` means "all cars."** Not "no car" — a blank VIN is a valid,
  meaningful command (comment 262–263).
- **`WearAction` string values are frozen.** Because they cross the wire as literals and old
  builds compare against their own copies, renaming a value (even keeping the constant name)
  breaks compatibility with any peer running an older/newer version. Add new verbs; never
  repurpose existing values.
