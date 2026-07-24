# FormatUtils.kt — Deep Dive

**File:** `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\FormatUtils.kt`
**Package:** `com.bloo.bluelink.data`
**Module:** `:shared` (domain/API/data layer, consumed by `:app`, `:wear`, `:uicommon`)

---

## 1. Purpose

`FormatUtils.kt` is a **flat file of top-level (package-level) pure functions and constants** — there is no wrapping object or class. It is the single, canonical home for:

- **Formatting/labeling helpers** — turning raw telematics/weather/timestamp data into human-facing strings (durations, dates, distances, speeds, temperatures, weather, charge-plug labels, relative "x min ago" labels, vehicle-state one-liners, place names).
- **Unit conversions** — °C↔°F, mi↔km, kph↔mph, efficiency ratios.
- **Shared domain constants** — the ranges/defaults/thresholds that phone, wear, tiles, and widgets must all agree on (climate temp range, charge-limit range, staleness window, update-snooze window, default climate request).
- **Smart-climate logic** — `smartClimateTargetF`, the one-tap "smart" target temperature computation.
- **Small utilities** — email masking for logs, GMT offset for auth headers.

**Why it exists (the recurring theme in the doc comments):** almost every symbol here was *previously duplicated* across phone (`:app`) and watch (`:wear`) — and in several cases the copies had **already silently drifted** (see §8). This file is a deliberate de-duplication: one definition so a fix or value change can't land on one surface and miss the other. The file's own comments repeatedly cite the "hand-copied constant" footgun class as the reason a symbol was hoisted here even when the copies still happen to agree today.

Everything here is **stateless and pure** (deterministic given inputs, except the two that read the system clock/timezone: `gmtOffsetHours` and `relativeLabel`). No coroutines, no locks, no I/O.

---

## 2. Public surface

Every top-level declaration in the file (all are `public` by Kotlin default). Ordered as they appear.

### Functions

- **`fun formatPlaceName(a: android.location.Address): String?`** — (`FormatUtils.kt:11-15`)
  Builds `"Springfield, IL"` from a reverse-geocode `Address`. Takes `listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea)`, applies `.distinct()`, `joinToString(", ")`, and `.ifBlank { a.getAddressLine(0) }`. Returns `null` only if the fallback address line is null. The `.distinct()` is load-bearing (see §8).

- **`fun fmtMinutes(min: Int): String`** — (`FormatUtils.kt:21`)
  `"1h 20m"` for `min >= 60` (`"${min / 60}h ${min % 60}m"`), else `"$min min"` (e.g. `"45 min"`). Integer division/remainder.

- **`fun ambientFahrenheit(tempC: Double): Int`** — (`FormatUtils.kt:32`)
  `(tempC * 9.0 / 5.0 + 32.0).roundToInt()`. Converts a live weather °C reading to a whole-degree °F for use as the `ambientF` input to smart-climate. **Uses `roundToInt`, not truncation** — deliberately, because inconsistent truncate-vs-round at the old 7 call sites could flip the smart target near a whole-degree boundary (§8).

- **`fun smartClimateTargetF(ambientF: Int): Int`** — (`FormatUtils.kt:54-69`)
  Returns a one-tap "smart" climate setpoint clamped into `CLIMATE_TEMP_RANGE_F`. See §3 for the branch logic.

- **`fun chargerLabel(plugin: Int?): String?`** — (`FormatUtils.kt:107-111`)
  Label for `EvStatus.batteryPlugin`: `1 -> "DC fast"`, `2 -> "AC (level 2)"`, else `null`. **Note the encoding is `batteryPlugin` (0=unplugged, 1=DC, 2=AC), NOT `plugType`** (§4).

- **`fun tripDate(raw: String?, includeWeekday: Boolean = true): String`** — (`FormatUtils.kt:120-142`)
  Parses two timestamp shapes → `"Mon Jun 1 · 6:22 PM"`. See §3.

- **`fun maskEmail(email: String): String`** — (`FormatUtils.kt:147-158`)
  `"jane.doe@gmail.com"` → `"j***@gmail.com"`. Malformed input (`at <= 0`) → `"***"`. See §3.

- **`fun gmtOffsetHours(): String`** — (`FormatUtils.kt:164-172`)
  Current GMT offset in whole hours as a string (e.g. `"-5"`/`"-4"`). Reads `TimeZone.getDefault().getOffset(System.currentTimeMillis())` (DST-aware) and integer-divides by `3_600_000`. Sent as an auth header by **both** brand API clients.

- **`fun relativeLabel(ms: Long?): String`** — (`FormatUtils.kt:175-190`)
  `"just now"` / `"x min ago"` / `"x hr ago"` / `"x day ago"` from a wall-clock ms timestamp. `null` or `<= 0` → `""`. See §3.

- **`fun degLabel(valueF: String, fahrenheit: Boolean): String`** — (`FormatUtils.kt:196-204`)
  Renders a climate setpoint the **API reports as a °F string** in the user's chosen unit. `valueF.toDoubleOrNull() ?: return "$valueF°"` (non-numeric passes through with a bare degree). Fahrenheit: `"${f.toLong()}°F"`; Celsius: `"${((f - 32) * 5 / 9.0).toLong()}°C"`. Uses `.toLong()` (truncation).

- **`fun weatherLabel(code: Int): String`** — (`FormatUtils.kt:211-222`)
  WMO weather-code integer → label. Groups (see §4). Unlisted codes → `"—"`.

- **`fun weatherTemp(tempC: Double, fahrenheit: Boolean): String`** — (`FormatUtils.kt:228-229`)
  Weather temperature (arrives as **Celsius**) → `"NN°F"` or `"NN°C"`. Both branches **truncate** via `.toInt()`.

- **`fun formatDistance(mi: Number, metric: Boolean): String`** — (`FormatUtils.kt:236-237`)
  Distance in **miles** → `"NN km"` (`* 1.609`, truncated) or `"NN mi"` (truncated). Note input type is `Number` (accepts Int/Double/etc.).

- **`fun formatSpeed(kph: Double, metric: Boolean): String`** — (`FormatUtils.kt:243-244`)
  Speed in **km/h** input → `"NN km/h"` (passthrough) or `"NN mph"` (`/ 1.609`). **Input unit differs from `formatDistance`** — km/h here, miles there (§8).

- **`fun formatTripDistance(mi: Double, metric: Boolean): String`** — (`FormatUtils.kt:251-252`)
  Trip distance in **miles** → `"%.1f km"` (`* 1.609`) or `"%.1f mi"`. Keeps **one decimal** (unlike `formatDistance`'s whole-number truncation).

- **`fun formatEfficiency(mi: Double, kwh: Double, metric: Boolean): String`** — (`FormatUtils.kt:259-262`)
  Efficiency → `"%.1f km/kWh"` or `"%.1f mi/kWh"`. Converts the **distance component** to km first when metric (`(mi * 1.609) / kwh`) so the ratio itself is correct, not just relabelled.

- **`fun vehicleStateLabel(engineOn: Boolean?, charging: Boolean?, climateOn: Boolean?, locked: Boolean?): String`** — (`FormatUtils.kt:276-294`)
  The canonical priority-ordered vehicle-state one-liner. See §3.

### Constants / vals

- **`val CLIMATE_TEMP_RANGE_F = 62..82`** — (`FormatUtils.kt:41`) `IntRange`. The Fahrenheit range every supported car will accept as a climate target. Matches the temp slider (was previously `60..85` in six other places).
- **`val CHARGE_LIMIT_RANGE = 50..100`** — (`FormatUtils.kt:76`) `IntRange`. Valid AC/DC charge-limit percentage.
- **`val STALE_STATUS_MS = 15L * 60 * 1000L`** — (`FormatUtils.kt:86`) `Long` = 900,000 ms = 15 min. Cached-status staleness window.
- **`val UPDATE_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000L`** — (`FormatUtils.kt:92`) `Long` = 259,200,000 ms = 3 days. Update-snooze duration.
- **`const val DEFAULT_CLIMATE_TEMP_F = 72`** — (`FormatUtils.kt:101`) `Int`. Default climate setpoint.
- **`const val DEFAULT_CLIMATE_DURATION_MIN = 10`** — (`FormatUtils.kt:102`) `Int`. Default climate run duration (minutes).

Note: the two `DEFAULT_CLIMATE_*` are `const val` (compile-time inlined into callers); the ranges and `*_MS` are plain `val` (`IntRange`/`Long` objects can't be `const`).

---

## 3. Internal structure & control flow

There are **no private helpers** — the file is entirely top-level public symbols. Control flow for the non-trivial functions:

### `smartClimateTargetF(ambientF)` (`:54-69`)
1. `val min = CLIMATE_TEMP_RANGE_F.first` (62), `val max = CLIMATE_TEMP_RANGE_F.last` (82) — reads the range instead of hardcoding, so it tracks any future change to `CLIMATE_TEMP_RANGE_F`.
2. `when` branches, checked **top-to-bottom in order of extremity**:
   - `ambientF >= 90` → `min` (62): "really hot → max cold."
   - `ambientF >= 70` → `(ambientF - 10).coerceIn(min, max)`: warm, gentle −10 offset.
   - `ambientF <= 40` → `max` (82): "really cold → max hot."
   - `else` (41–69) → `(ambientF + 10).coerceIn(min, max)`: cool, gentle +10 offset.
3. The extreme branches short-circuit **before** the offset branches. The whole point (per the comment `:43-53`): a flat "ambient − 10" on a 100°F day = 90 → clamped **up** to 82 (the *least* aggressive cooling), the opposite of "smart." Jumping straight to `min` fixes that.

### `tripDate(raw, includeWeekday)` (`:120-142`)
1. `if (raw.isNullOrBlank()) return "Trip"`.
2. `val trimmed = raw.substringBefore('.').trim()` — drops fractional seconds (feed precision varies: `".0"` vs `".000000"`).
3. Builds `outFormat` = `SimpleDateFormat("EEE MMM d · h:mm a" | "MMM d · h:mm a", Locale.US)` depending on `includeWeekday`.
4. Loops over `arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")` (T-separated ISO first, then space variant). Each parse is wrapped in `runCatching { ... }.getOrNull()` so a non-matching pattern's `ParseException` is swallowed and the loop continues. Returns `outFormat.format(parsed)` on first success.
5. Fallback (neither matched): `trimmed.take(16).replace('T', ' ')` — best-effort raw rendering.

### `maskEmail(email)` (`:147-158`)
1. `val at = email.indexOf('@')`.
2. `if (at <= 0) return "***"` — `-1` (no `@`) and `0` (leading `@`, no local part) are both malformed; same fully-redacted fallback rather than crashing on bad substring math.
3. Else `"${email.first()}***${email.substring(at)}"` — first char + `***` + everything from `@` onward (fixed `***` regardless of local-part length).

### `relativeLabel(ms)` (`:175-190`)
1. `if (ms == null || ms <= 0) return ""` — no timestamp recorded yet.
2. `val d = System.currentTimeMillis() - ms`.
3. `when` on `d`: `< 60_000` → `"just now"`; `< 3_600_000` → `"${d / 60_000} min ago"`; `< 86_400_000` → `"${d / 3_600_000} hr ago"`; else `"${d / 86_400_000} day ago"`. Integer division (truncates).

### `vehicleStateLabel(...)` (`:276-294`)
`when` evaluated top-to-bottom (this IS the priority encoding): `engineOn == true → "Driving"`, `charging == true → "Charging"`, `climateOn == true → "Climate on"`, `locked == true → "Locked"`, `locked == false → "Unlocked"`, else `"—"`. Every check is `== true`, so a `null` (unknown) never wins a branch by accident. Priority: **driving > charging > climate > lock**. A car towing while plugged in reports "Driving." Final `"—"` = lock state itself unknown and nothing else applies.

### `gmtOffsetHours()` (`:164-172`)
`TimeZone.getDefault().getOffset(System.currentTimeMillis())` returns raw offset + active DST for the current instant (DST-aware). `offsetMs / 3_600_000` integer-divides to whole hours (the granularity both brands' auth headers expect). Truncates sub-hour offsets toward zero (see §8).

---

## 4. Data & types

**No data classes, enums, or sealed types are defined in this file.** It references types by contract only:

- **`android.location.Address`** — input to `formatPlaceName`; fields used: `locality`, `subAdminArea`, `adminArea`, `getAddressLine(0)`.
- **`EvStatus.batteryPlugin: Int?`** — referenced by `chargerLabel` (only in the doc comment; the function takes a bare `Int?`). Encoding per the label mapping: `1 = DC (→ "DC fast")`, `2 = AC (→ "AC (level 2)")`, anything else (incl. `0`=unplugged, `null`) → `null`. **Critical:** this is the `batteryPlugin` scheme (0=unplugged,1=DC,2=AC), which is DIFFERENT from `plugType` (0=DC fast, 1=AC). Do not conflate.
- **WMO weather codes** (`weatherLabel`, `:211-222`): `0`→Clear; `1,2`→Partly cloudy; `3`→Cloudy; `45,48`→Fog; `51,53,55,56,57`→Drizzle; `61,63,65,66,67`→Rain; `71,73,75,77,85,86`→Snow; `80,81,82`→Showers; `95,96,99`→Thunderstorm; else→`—`.

**Constant values & encodings** (defaults/ranges are in §2):
- `CLIMATE_TEMP_RANGE_F = 62..82` (car-accepted climate target range, matches slider).
- `CHARGE_LIMIT_RANGE = 50..100` (charge-limit % range).
- `STALE_STATUS_MS = 900_000L`; `UPDATE_SNOOZE_MS = 259_200_000L`.
- `DEFAULT_CLIMATE_TEMP_F = 72`; `DEFAULT_CLIMATE_DURATION_MIN = 10`.

**Conversion factors:** distance uses a fixed `1 mi = 1.609 km` throughout (`formatDistance`, `formatSpeed`, `formatTripDistance`, `formatEfficiency`). Temperature: °C→°F = `c*9/5+32`, °F→°C = `(f-32)*5/9`.

**Rounding/truncation policy (varies per function, deliberately):**
- `ambientFahrenheit` — **rounds** (`roundToInt`).
- `weatherTemp`, `formatDistance`, `formatSpeed`, `gmtOffsetHours`, `relativeLabel`, `fmtMinutes` — **truncate** (`toInt`/integer division).
- `degLabel` — **truncates** via `toLong`.
- `formatTripDistance`, `formatEfficiency` — keep **one decimal** (`%.1f`).

---

## 5. State & concurrency

**None.** Every symbol is a stateless pure function or an immutable top-level constant. No `StateFlow`, `DataStore`, `remember`, coroutine scope, dispatcher, or lock is involved. Thread-safe by construction.

Two functions are **impure only in that they read ambient system state at call time**:
- `gmtOffsetHours()` — reads `TimeZone.getDefault()` and `System.currentTimeMillis()`.
- `relativeLabel(ms)` — reads `System.currentTimeMillis()`.

Both are still thread-safe (no shared mutable state). Note `SimpleDateFormat` instances in `tripDate` are created **locally per call** — this side-steps `SimpleDateFormat`'s well-known non-thread-safety (no shared static formatter to race on).

This file is unrelated to `BlueLinkGate.statusMutex`; it performs no network or command work.

---

## 6. Collaborators & data flow

**Consumers (who calls this, per doc comments):**
- **Phone (`:app`):** `ClimatePebble` (×4 uses of ambient/range/default logic), `ChargePebble` slider, `TileCommandRunner`, `AppViewModel` (auto-refresh/staleness, shortcut climate defaults), `UpdateChecker`, phone widget, phone Quick Settings tiles, phone UI "pull to refresh" hint, the phone log viewer (`maskEmail` via `AppLog`).
- **Watch (`:wear`):** `smartClimate()`, `HomeScreen` + its preview, `setAcLimit`/`setDcLimit` clamps, two `SliderRow`s, `WearViewModel` (staleness, update snooze, `ClimateDraft` defaults), `WearCommand` wire/default values, wear tile, wear complications.
- **Both brand API clients** call `gmtOffsetHours()` for an auth header.

**Data channels:**
- **Function calls only** — this file exposes no DataStore keys, no Wear Data Layer paths, no intents, no WorkManager. It is a pure utility layer that callers invoke and embed the results into their own channels.
- Results flow *outward* into: Compose UI text (phone/uicommon), Wear ProtoLayout text (tiles/complications), auth headers (API clients), and the `WearSync` wire (e.g. `ClimateDraft`/`WearCommand` default values seeded from `DEFAULT_CLIMATE_*`).
- Data flows *inward* as plain parameters: `Address` (from geocoder), Int/Double/String telematics + weather readings, timestamps in ms, email strings.

**The constants are the coordination surface:** `CLIMATE_TEMP_RANGE_F`, `CHARGE_LIMIT_RANGE`, `STALE_STATUS_MS`, `UPDATE_SNOOZE_MS`, `DEFAULT_CLIMATE_*` are the single source of truth that phone/wear/tiles/widgets read so they can't drift.

---

## 7. Invariants & assumptions

- **`ambientFahrenheit` and `smartClimateTargetF` must use the same rounding** (round, not truncate) as every other ambient-derivation path — the whole reason `ambientFahrenheit` exists is to guarantee this. Callers must route °C→°F for climate through `ambientFahrenheit`, not inline math.
- **`smartClimateTargetF` output is always within `CLIMATE_TEMP_RANGE_F`** (the extreme branches return the exact endpoints; the offset branches `coerceIn`). Callers can rely on the result being a car-acceptable setpoint.
- **`CLIMATE_TEMP_RANGE_F` must equal the actual temp slider's range** (62..82). If the car's accepted range ever changes, change it here once; `smartClimateTargetF` reads `.first`/`.last` and stays correct automatically.
- **`chargerLabel` expects the `batteryPlugin` encoding** (1=DC, 2=AC), NOT `plugType`. Passing a `plugType` value would mislabel.
- **`degLabel` assumes the API reports climate temps as °F strings**, always — even for metric-preference cars. This is the single conversion point; callers must pass the raw API °F string.
- **`weatherTemp`/`formatDistance`/`formatSpeed`/`formatTripDistance`/`formatEfficiency` assume specific input units** — weather temp in °C, distance in miles, **speed in km/h**, trip distance in miles, efficiency distance in miles. Mixing these up is silent and wrong.
- **`gmtOffsetHours` assumes whole-hour offsets are acceptable** to the brand APIs; integer division truncates sub-hour zones (e.g. India +5:30) toward zero — a known limitation the comment flags as a future fix point (`:160-163`).
- **`relativeLabel`/`STALE_STATUS_MS` assume `ms` is a wall-clock (epoch) timestamp**, comparable to `System.currentTimeMillis()`. A monotonic/uptime value would produce garbage.
- **`tripDate` assumes the feed uses one of the two known shapes** (`'T'`-separated or space-separated `yyyy-MM-dd HH:mm:ss`, optionally with fractional seconds). Anything else falls back to truncated raw text.
- **`vehicleStateLabel` priority order is intentional and load-bearing:** driving > charging > climate > lock. Reordering the `when` changes user-visible behavior.

---

## 8. Gotchas & sharp edges

- **`formatPlaceName`'s `.distinct()` is a bug fix, not decoration** (`:6-10, :13`). The watch's old copy omitted it, so when `locality == adminArea` it rendered `"Springfield, Springfield"`. Keep `.distinct()`.
- **`ambientFahrenheit` rounds, but the old inline call sites disagreed** (`:24-31`): 3 of 7 truncated (`.toInt()`), 4 rounded. Near a whole-degree boundary the *same* weather reading could resolve to different `ambientF` → different smart target → different "Cool"/"Heat" label depending on entry point. This function exists to kill that.
- **The flat-offset-clamps-wrong footgun** (`:43-53`): the reason `smartClimateTargetF` has explicit extreme branches. `100°F − 10 = 90`, clamped **up** to 82 = the *least* aggressive cooling — backwards. Don't "simplify" it back to a single clamped offset.
- **`CLIMATE_TEMP_RANGE_F` was previously `60..85` in six places** while the slider used `62..82` (`:35-40`). A "smart" target clamped to the wider range could ask the car for an unsupported temperature. This file uses the slider's real range.
- **`chargerLabel` string had already drifted** — phone said `"AC (level 2)"`, watch said `"AC"` for the same wire value (`:104-106`). Canonicalized to `"AC (level 2)"`.
- **`tripDate` needs BOTH date patterns.** The feed has been seen with both `'T'` and plain-space separators; the phone's old space-only parser silently fell back to raw text on a `'T'` timestamp (`:113-119`). The `runCatching` loop is how it tolerates the mismatch.
- **`formatSpeed` input is km/h, `formatDistance` input is miles** (`:239-242`). Easy to swap by mistake — the doc comment explicitly calls this out. `formatSpeed` metric = passthrough, imperial = `/1.609`; `formatDistance` metric = `*1.609`, imperial = passthrough. Opposite directions.
- **`formatEfficiency` converts the ratio, not just the label** (`:254-258`): `(mi*1.609)/kwh` for metric. A relabel-only version would report the imperial number with a km label — wrong.
- **Truncation vs rounding is inconsistent across the file by design** — see §4. `ambientFahrenheit` rounds (correctness-critical); display formatters mostly truncate; trip/efficiency keep one decimal. Don't "normalize" these without understanding each rationale.
- **`gmtOffsetHours` truncates sub-hour timezones** (`:160-163, :164-172`). Half-hour offset zones (India, Newfoundland, etc.) lose the fractional part. Flagged as a deliberate single-point-of-fix location for both API clients.
- **`maskEmail` fallback covers both no-`@` and leading-`@`** via one `at <= 0` guard (`:150-153`) — avoids a substring crash on malformed input, at the cost of not distinguishing the two cases (both → `"***"`).
- **`DEFAULT_CLIMATE_*` are `const val`** and thus **inlined into every caller at compile time** (`:101-102`). Changing them requires recompiling all consumers (across `:app` and `:wear`) — not just relinking — to take effect. The ranges/`*_MS` are plain `val`, referenced at runtime.
- **`degLabel` uses `.toLong()`**, `weatherTemp` uses `.toInt()` — different truncation types for the same conceptual "drop the fraction" (`:203, :229`). Both truncate; just note the type difference if refactoring.
- **`vehicleStateLabel` maps `null` == false everywhere** via `== true` (`:281-293`); an unknown state never wins a branch. Only when `locked` is explicitly `false` do you get `"Unlocked"`; `null` lock with nothing else true yields `"—"`.
