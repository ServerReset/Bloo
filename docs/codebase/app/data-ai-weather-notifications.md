# app: data — Ai / Weather / Notifications / ClimateSync / Alert

Deep-dive reference for five loosely-related support files in the `:app` module's
`com.bloo.bluelink.data` package. They are grouped here because none is large enough
for its own doc, and together they cover the app's "ancillary services": on-device AI
summarization, weather fetching, local alert notifications and their action buttons,
and the phone-side mirror of the watch's live climate draft.

Files covered:
- `app/src/main/java/com/bloo/bluelink/data/Ai.kt`
- `app/src/main/java/com/bloo/bluelink/data/WeatherApi.kt`
- `app/src/main/java/com/bloo/bluelink/data/Notifications.kt`
- `app/src/main/java/com/bloo/bluelink/data/ClimateSyncStore.kt`
- `app/src/main/java/com/bloo/bluelink/data/AlertActionReceiver.kt`

---

## 1. Purpose

| File | Purpose |
|------|---------|
| `Ai.kt` | Thin isolation wrapper around **on-device Gemini Nano** via Google ML Kit GenAI summarization. Turns a car's status blurb into a three-bullet summary, downloading the model on demand. |
| `WeatherApi.kt` | Free, key-less current-conditions fetch from **Open-Meteo**, normalized into a `Weather` model plus a coarse `WeatherCode` enum for the UI. Used for a "home" location and each car's live position. |
| `Notifications.kt` | Posts Bloo's **local alerts** (service due, door left open, car left running) via a single shared channel, with optional action buttons (`Notifications`), and the pure **rules engine** that decides which alerts fire (`CarAlerts`). |
| `ClimateSyncStore.kt` | The phone's **DataStore-backed copy** of the live climate draft the watch publishes (two-way climate sync). |
| `AlertActionReceiver.kt` | The `BroadcastReceiver` that handles an **action-button tap** on a Bloo alert (e.g. "Lock", "Turn off"), runs the remote command, and posts a follow-up. |

None of these is on the vehicle-command hot path except `AlertActionReceiver`, which
delegates to `WearCommandRunner` (and thus the process-wide `BlueLinkGate` lock).

---

## 2. Public surface

### 2.1 `class Ai(context: Context)` — `Ai.kt:22`

Constructor takes any `Context`; immediately stores `context.applicationContext`
(`Ai.kt:27`) so the instance and its lazily-built summarizer can outlive a
short-lived Activity/Fragment.

- **`suspend fun isSupported(): Boolean`** — `Ai.kt:60`
  Returns `true` if Gemini Nano summarization is AVAILABLE, DOWNLOADABLE, or
  DOWNLOADING (i.e. anything other than `FeatureStatus.UNAVAILABLE`). Wrapped in
  `runCatching { … }.getOrDefault(false)` so any exception (device/OS lacks ML Kit
  GenAI) is swallowed and reported as unsupported.
- **`suspend fun summarize(text: String): String`** — `Ai.kt:78`
  Ensures the model is downloaded/ready (`ensureFeatureReady`), pads `text` up to
  the 400-char floor (`padToMinimum`), builds one `SummarizationRequest`, runs
  inference, and returns `result.summary`. **Failures are NOT caught** — they
  propagate so the caller can show a real error rather than a silently-empty summary.

Everything else in `Ai` is private (see §3).

### 2.2 `data class Weather(...)` — `WeatherApi.kt:16`

Public model of current conditions. Fields in §4. Public members:

- **`fun toWear(): WearWeather`** — `WeatherApi.kt:30` — projects to the wire type
  `WearWeather` (same shape) for mirroring to the watch.
- **`fun tempF(): Double`** — `WeatherApi.kt:37` — `tempC * 9 / 5 + 32`.
- **`fun feelsLikeF(): Double`** — `WeatherApi.kt:38`.
- **`fun highF(): Double?`** — `WeatherApi.kt:41` — null-propagating (null in → null out).
- **`fun lowF(): Double?`** — `WeatherApi.kt:42` — null-propagating.
- **`fun tempLabel(fahrenheit: Boolean): String`** — `WeatherApi.kt:45` — e.g. `"72°F"` / `"22°C"` (rounded via `toInt()`, **truncates toward zero**, not round-half).
- **`fun feelsLikeLabel(fahrenheit: Boolean): String`** — `WeatherApi.kt:50` — degree glyph only, **no unit suffix** (compact UI next to a labelled primary temp).
- **`fun highLowLabel(fahrenheit: Boolean): String?`** — `WeatherApi.kt:58` — `"H:hi°  L:lo°"` (two spaces between), or `null` if either bound is missing.
- **`val condition: WeatherCode`** — `WeatherApi.kt:65` — computed getter delegating to `WeatherCode.from(code)`.

### 2.3 `enum class WeatherCode(val label: String)` — `WeatherApi.kt:72`

Coarse UI bucket over raw WMO codes. Members and labels in §4.

- **`fun toCode(): Int`** — `WeatherApi.kt:85` — a representative WMO int for each bucket; round-trips through `from` (`UNKNOWN -> -1`).
- **`companion object.from(code: Int): WeatherCode`** — `WeatherApi.kt:100` — buckets a raw WMO code; unlisted codes (incl. negatives) → `UNKNOWN`.

### 2.4 `object WeatherApi` — `WeatherApi.kt:119`

- **`suspend fun fetch(lat: Double, lon: Double): Weather?`** — `WeatherApi.kt:180`
  Fetches current conditions on `Dispatchers.IO`, returning `null` on any failure
  (network, non-2xx, empty body, malformed JSON, or missing `current`/temperature).
  Details in §3.

### 2.5 `object Notifications` — `Notifications.kt:18`

- **`data class Action(val label: String, val vin: String, val wearAction: String)`** — `Notifications.kt:24` — a tappable action button on an alert.
- **`fun hasPermission(context: Context): Boolean`** — `Notifications.kt:57` — `true` on < API 33; on 33+ defers to `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)`.
- **`fun post(context: Context, id: Int, title: String, text: String, actions: List<Action> = emptyList())`** — `Notifications.kt:92` — builds and posts one alert with optional action row. Details in §3.

### 2.6 `object CarAlerts` — `Notifications.kt:139`

- **`data class Alert(val id: Int, val title: String, val text: String, val actions: List<Notifications.Action> = emptyList())`** — `Notifications.kt:140`.
- **`suspend fun evaluate(settings: SettingsStore, v: Vehicle, status: VehicleStatus?): List<Alert>`** — `Notifications.kt:162` — the fire-once rules engine; returns only the alerts that fire *this* call. Details in §3.

### 2.7 `class ClimateSyncStore(private val context: Context)` — `ClimateSyncStore.kt:25`

- **`val flow: Flow<WearClimateState>`** — `ClimateSyncStore.kt:29` — maps the stored `"payload"` string through `WearSync.decodeClimate(...)`.
- **`suspend fun save(raw: String)`** — `ClimateSyncStore.kt:31` — writes the raw JSON payload under the `"payload"` key.

### 2.8 `class AlertActionReceiver : BroadcastReceiver()` — `AlertActionReceiver.kt:18`

- **`override fun onReceive(context: Context, intent: Intent)`** — `AlertActionReceiver.kt:51` — runs the tapped action's remote command async. Details in §3.
- **`companion object`** — `AlertActionReceiver.kt:81` — intent contract constants:
  - `ACTION_RUN = "com.bloo.bluelink.ALERT_ACTION"`
  - `EXTRA_VIN = "vin"`, `EXTRA_ACTION = "action"`, `EXTRA_NOTIF_ID = "notif_id"`, `EXTRA_LABEL = "label"`

---

## 3. Internal structure & control flow

### 3.1 `Ai` internals

- **`app`** (`Ai.kt:27`) — application context.
- **`direct`** (`Ai.kt:31`) — `Executor { it.run() }`, a same-thread executor used
  only to bridge `ListenableFuture` callbacks inline. No thread pool.
- **`companion object.MIN_ARTICLE_CHARS = 400`** (`Ai.kt:35`) — ML Kit ARTICLE minimum input length.
- **`summarizer: Summarizer by lazy`** (`Ai.kt:41`) — built via
  `Summarization.getClient(...)` with `InputType.ARTICLE`, `OutputType.THREE_BULLETS`,
  `Language.ENGLISH`. Lazy so constructing `Ai` is cheap; the ML Kit client and its
  resources spin up only on first summarization.

- **`private fun padToMinimum(text: String): String`** (`Ai.kt:90`):
  1. If `text.length >= 400`, return unchanged.
  2. Otherwise append `'\n' + text` repeatedly until length ≥ 400.
  Repetition (not filler characters) is deliberate: the model summarizes whatever it
  sees, so repeating **true** statements can't introduce anything false, whereas junk
  filler could leak into or skew the summary.

- **`private suspend fun ensureFeatureReady()`** (`Ai.kt:115`):
  1. `checkFeatureStatus().await()`.
  2. If `DOWNLOADABLE` or `DOWNLOADING`, call `downloadFeature(DownloadCallback)` and
     suspend on `suspendCancellableCoroutine`.
  3. `onDownloadStarted`/`onDownloadProgress` are ignored (no UI hook).
     `onDownloadCompleted` → `cont.resume(Unit)`; `onDownloadFailed(e)` →
     `cont.resumeWithException(e)`. Both guarded by `cont.isActive`.
  4. If already `AVAILABLE` (or `UNAVAILABLE`), returns immediately without downloading.

- **`private suspend fun <T> Task<T>.await(): T`** (`Ai.kt:138`) — adapts a Play
  Services `Task` via `addOnSuccessListener`/`addOnFailureListener`, each guarded by
  `cont.isActive`.
- **`private suspend fun <T> ListenableFuture<T>.await(): T`** (`Ai.kt:155`) — adapts
  Guava's `ListenableFuture`: `addListener({ … get() … }, direct)`, catches any
  throwable from `get()` into `resumeWithException`, and `invokeOnCancellation { cancel(false) }`
  propagates coroutine cancellation to the future **without** interrupting an
  in-progress task (the `false` argument).

### 3.2 `WeatherApi.fetch` control flow (`WeatherApi.kt:180`)

Runs inside `withContext(Dispatchers.IO)` because it uses OkHttp's **synchronous**
`execute()`. Whole body wrapped in `runCatching { … }.getOrNull()`:

1. Build the GET URL with query params requesting the `current` block
   (`temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,is_day,weather_code`)
   and a one-day `daily` block (`temperature_2m_max,temperature_2m_min`), with
   `wind_speed_unit=kmh`, `forecast_days=1`, `timezone=auto` (`WeatherApi.kt:182-187`).
   `timezone=auto` means the "day" for high/low matches the **location's** calendar
   day, not the device's.
2. `client.newCall(request).execute().use { resp -> … }` — `.use{}` guarantees the
   body/connection close on every exit path, including the early `return@use null`s.
3. `if (!resp.isSuccessful) return@use null`.
4. `val body = resp.body?.string() ?: return@use null`.
5. `json.decodeFromString(Response.serializer(), body)`.
6. `val c = parsed.current ?: return@use null`; `val temp = c.temperature ?: return@use null`.
7. Build `Weather` with defaults for the soft-optional fields (see §4/§8).

Every failure path collapses to `null`, so callers only distinguish "got a `Weather`"
vs. "didn't", never *why*.

- **`private val json`** (`WeatherApi.kt:123`) — `Json { ignoreUnknownKeys = true; isLenient = true }`.
- **`private val client`** (`WeatherApi.kt:129`) — dedicated `OkHttpClient` (not shared
  with other API callers), 15s connect / 20s read timeouts.

### 3.3 `Notifications` internals

- **`CHANNEL = "bloo_alerts"`** (`Notifications.kt:19`), **`ACCENT = 0xFF7B83EB.toInt()`** (`Notifications.kt:21`, Bloo's accent for the small icon tint).
- **`private fun ensureChannel(context: Context)`** (`Notifications.kt:37`):
  no-op below API 26; on 26+ reads the channel by id and only creates it if missing
  (`NotificationChannel(CHANNEL, "Car alerts", IMPORTANCE_DEFAULT)` with a description).
  The existence check avoids needless re-declaration; note that re-creating an existing
  channel would **not** reset the user's per-channel settings anyway.

- **`post(...)` control flow** (`Notifications.kt:92`):
  1. `if (!hasPermission(context)) return` — cheap no-op when denied.
  2. `ensureChannel(context)`.
  3. Build content `PendingIntent` from `packageManager.getLaunchIntentForPackage(packageName)`
     (nullable → `pi` is null if no launch intent), with flags
     `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
  4. Build `NotificationCompat.Builder`: `ic_stat_bloo` small icon, `ACCENT` color,
     title/text, `BigTextStyle` (no truncation), `setAutoCancel(true)`,
     `CATEGORY_REMINDER`, `VISIBILITY_PUBLIC`, and `setContentIntent(pi)` only if `pi != null`.
  5. For each `Action` (indexed `i`): build an `Intent` targeting `AlertActionReceiver`
     with `action = ACTION_RUN`, a **unique data URI** `bloo://alert/$id/${a.wearAction}`,
     and the four extras (VIN, action id, notif id, label). Wrap in a broadcast
     `PendingIntent` with request code **`id * 16 + i`** and flags
     `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`; `builder.addAction(0, a.label, actionPi)`
     (icon `0` = none).
  6. `runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }`
     — swallows a `SecurityException` from a permission revoked in the TOCTOU window
     between the `hasPermission` check and `notify`.

### 3.4 `CarAlerts.evaluate` control flow (`Notifications.kt:162`)

Reads `prefs = settings.notificationPrefs()` once, accumulates into `out`. Three
independent, gated checks, each using a per-check per-VIN "fired" flag
(`alertFired(key)` / `setAlertFired(key, …)`):

**Service (`prefs.service`, `Notifications.kt:166`)**
- `odo` = `v.odometer` with commas stripped, trimmed, `toDoubleOrNull()?.toInt()`.
- `last = settings.lastServiceMiles(v.vin)`, `interval = settings.serviceIntervalMiles(v.vin)`.
- `due = if (last != null && interval != null) last + interval else null`.
- key `"service_${v.vin}"`. If `due != null && odo != null && odo >= due` and not
  already fired → emit `Alert(serviceId(v), …)` and set fired. Else → clear the flag
  (so a future crossing re-fires).

**Door open (`prefs.doorOpen && status != null`, `Notifications.kt:195`)**
- Skipped entirely when `status == null` (a failed poll ≠ doors closed — see §8).
- `open = status.doorOpen?.anyOpen == true || status.trunkOpen == true || status.hoodOpen == true`.
- key `"door_${v.vin}"`, `now = System.currentTimeMillis()`.
- If `open`: read `since = settings.doorOpenSince(v.vin)`. If null → stamp `now`
  (records when the open state *began*). Else if `now - since > prefs.doorOpenMinutes * 60_000L`
  and not fired → emit `Alert(doorId(v), …, actions = listOf(Action("Lock", v.vin, WearAction.LOCK)))`
  and set fired.
- If not open → `setDoorOpenSince(v.vin, null)` and clear fired flag.

**Running (`prefs.running && status != null`, `Notifications.kt:226`)**
- `on = status.engine == true || status.airCtrlOn == true` (covers remote start /
  climate, and engine on supported cars).
- key `"running_${v.vin}"`. Same "stamp on first observation, don't reset while true"
  pattern via `engineOnSince` / `setEngineOnSince`, threshold `prefs.runningMinutes * 60_000L`.
- Emits `Alert(runningId(v), …, actions = listOf(Action("Turn off", v.vin, WearAction.CLIMATE_OFF)))`.
- Off → clear clock and flag.

**Stable notification ids** (`Notifications.kt:259-261`): `serviceId = ("svc"+vin).hashCode()`,
`doorId = ("door"+vin).hashCode()`, `runningId = ("run"+vin).hashCode()` — one distinct id
per (kind, VIN) so the three alert types never overwrite each other for one car.

### 3.5 `AlertActionReceiver.onReceive` control flow (`AlertActionReceiver.kt:51`)

1. `if (intent.action != ACTION_RUN) return` (defensive — public entry point).
2. Extract `vin` (`?: return`), `action` (`?: return`), `notifId`
   (`getIntExtra(EXTRA_NOTIF_ID, -1)`), `label` (`?: "Command"`). `ctx = context.applicationContext`.
3. `val pending = goAsync()` — keeps the receiver alive past the synchronous return
   so the network coroutine can finish before the process is reclaimed.
4. `CoroutineScope(Dispatchers.IO).launch { try { … } finally { pending.finish() } }`
   — a fresh receiver-scoped scope (a `BroadcastReceiver` has no lifecycle scope):
   a. If `notifId != -1`, `runCatching { NotificationManagerCompat.from(ctx).cancel(notifId) }`
      immediately, so the tap feels responsive (before the network call).
   b. `result = runCatching { WearCommandRunner.execute(ctx, WearCommand(vin, action)) }.getOrNull()`.
   c. `ok = result?.ok == true`; title `"$label sent"` / `"$label failed"`; text is a
      generic success line, or on failure prefers `result?.message` then falls back to
      `"Couldn't reach the car. Try again from the app."`.
   d. If `notifId != -1`, `Notifications.post(ctx, notifId, title, text)` — **reuses the
      same id** so the follow-up replaces the (already-cancelled) alert rather than stacking.
5. `finally { pending.finish() }` always releases the `goAsync` hold.

---

## 4. Data & types

### 4.1 `Weather` (`WeatherApi.kt:16`)

| Field | Type | Notes |
|-------|------|-------|
| `tempC` | `Double` | Canonical stored temperature, always **Celsius**. |
| `feelsLikeC` | `Double` | Apparent temp (falls back to `tempC` at fetch time when API omits it). |
| `highC` | `Double?` | Day high; nullable — Open-Meteo's `daily` block can be absent. |
| `lowC` | `Double?` | Day low; nullable. |
| `windKph` | `Double` | Wind in km/h (API pre-converts). |
| `humidity` | `Int?` | Relative humidity %; nullable. |
| `isDay` | `Boolean` | Day/night flag. |
| `code` | `Int` | Raw WMO weather interpretation code. |
| `fetchedAt` | `Long = System.currentTimeMillis()` | Wall-clock millis when constructed. |

Temperatures kept in Celsius so the canonical value never drifts; unit chosen at
display time by the `*F()`/`*Label()` helpers.

### 4.2 `WeatherCode` (`WeatherApi.kt:72`)

Values with labels: `CLEAR("Clear")`, `PARTLY_CLOUDY("Partly cloudy")`,
`CLOUDY("Cloudy")`, `FOG("Fog")`, `DRIZZLE("Drizzle")`, `RAIN("Rain")`, `SNOW("Snow")`,
`SHOWERS("Showers")`, `THUNDERSTORM("Thunderstorm")`, `UNKNOWN("—")`.

`from(code)` mapping (`WeatherApi.kt:100`):
`0→CLEAR`; `1,2→PARTLY_CLOUDY`; `3→CLOUDY`; `45,48→FOG`; `51,53,55,56,57→DRIZZLE`;
`61,63,65,66,67→RAIN`; `71,73,75,77,85,86→SNOW`; `80,81,82→SHOWERS`;
`95,96,99→THUNDERSTORM`; anything else → `UNKNOWN`.
`toCode()` representative ints: `CLEAR 0`, `PARTLY_CLOUDY 1`, `CLOUDY 3`, `FOG 45`,
`DRIZZLE 51`, `RAIN 61`, `SHOWERS 80`, `SNOW 71`, `THUNDERSTORM 95`, `UNKNOWN -1`.

### 4.3 Open-Meteo private DTOs (`WeatherApi.kt:134-154`)

- `Response(current: Current? = null, daily: Daily? = null)`.
- `Current`: `@SerialName("temperature_2m") temperature: Double?`,
  `@SerialName("apparent_temperature") apparent: Double?`,
  `@SerialName("relative_humidity_2m") humidity: Int?`,
  `@SerialName("wind_speed_10m") windKph: Double?`,
  `@SerialName("is_day") isDay: Int?`,
  `@SerialName("weather_code") weatherCode: Int?` (all nullable, default null).
- `Daily`: `@SerialName("temperature_2m_max") max: List<Double>?`,
  `@SerialName("temperature_2m_min") min: List<Double>?`. Only `firstOrNull()` of each
  is read (single-day forecast).

Field fallbacks at construction (`WeatherApi.kt:197-206`): `feelsLikeC = c.apparent ?: temp`;
`highC = parsed.daily?.max?.firstOrNull()` (may stay null); `lowC` similar;
`windKph = c.windKph ?: 0.0`; `humidity = c.humidity` (may stay null);
`isDay = (c.isDay ?: 1) == 1` (missing → **daytime**); `code = c.weatherCode ?: -1`
(missing → `-1` → `UNKNOWN`).

### 4.4 `Notifications.Action` (`Notifications.kt:24`)

`Action(label: String, vin: String, wearAction: String)` — button text, target VIN,
and a `WearAction.*` string constant (e.g. `WearAction.LOCK = "lock"`,
`WearAction.CLIMATE_OFF = "climate_off"`) that flows across the alert intent into
`AlertActionReceiver` and on to `WearCommandRunner`.

### 4.5 `CarAlerts.Alert` (`Notifications.kt:140`)

`Alert(id: Int, title: String, text: String, actions: List<Notifications.Action> = emptyList())`.
`id` is a stable per-(kind,VIN) hash from §3.4; consumed by `Notifications.post(id=…)`.

### 4.6 Collaborator types (defined elsewhere, referenced here)

- `WearWeather` (`shared … WearSync.kt:518`) — same shape as `Weather` minus
  `fetchedAt`; defaults `windKph=0.0`, `isDay=true`, `code=-1`. `Weather.toWear()`
  projects into it.
- `WearClimateState(byVin: Map<String, ClimateSync> = emptyMap())` (`WearSync.kt:511`)
  — decoded by `ClimateSyncStore.flow`.
- `WearCommand`, `WearCommandResult(vin, action, ok, message?)` (`WearSync.kt:281`, `:301`).
- `SettingsStore.NotificationPrefs(service=true, doorOpen=true, doorOpenMinutes=5, running=true, runningMinutes=10)` (`SettingsStore.kt:326`).

---

## 5. State & concurrency

- **`Ai`** — Holds `app`, `direct`, and the `by lazy` `summarizer` (thread-safe lazy
  init by default). All async work is bridged into coroutines via
  `suspendCancellableCoroutine`; there is no shared mutable state and no explicit lock.
  `summarize`/`isSupported`/`ensureFeatureReady` are `suspend` and can run on any
  dispatcher the caller provides; ML Kit does its own threading.
- **`WeatherApi`** — Stateless `object`. `json` and `client` are immutable singletons.
  Network runs on `Dispatchers.IO` (synchronous OkHttp `execute()`). No stored state.
- **`Notifications` / `CarAlerts`** — Stateless `object`s. `CarAlerts.evaluate` is
  `suspend`; all its persistent state lives in `SettingsStore` (DataStore), read/written
  via `alertFired`/`setAlertFired`/`doorOpenSince`/`engineOnSince`/etc. It holds no
  in-memory state between calls — the "fire once" behavior is entirely DataStore-backed,
  which is what makes it safe to run from a stateless WorkManager worker.
- **`ClimateSyncStore`** — Preferences DataStore named `"bloo_climate_sync"`
  (`ClimateSyncStore.kt:15`) with a `ReplaceFileCorruptionHandler { emptyPreferences() }`
  so a torn write resets to empty instead of throwing on every read. Single string key
  `"payload"`. `flow` is a cold `Flow` mapped to `WearClimateState`; `save` uses
  `edit {}` (DataStore's serialized single-writer). No manual locks.
- **`AlertActionReceiver`** — Creates a `CoroutineScope(Dispatchers.IO)` per receive
  (no lifecycle scope exists for a receiver). `goAsync()`/`pending.finish()` bound the
  process's aliveness window. The actual command runs through `WearCommandRunner`, which
  serializes vehicle calls under the process-wide `BlueLinkGate.statusMutex`.

---

## 6. Collaborators & data flow

```
ML Kit GenAI (Gemini Nano)  <── Ai ──>  (caller: AppViewModel / WearSyncEvents AI_SUMMARY path)
Open-Meteo HTTP  <── WeatherApi.fetch ──> Weather ── toWear() ──> WearWeather ──> WearExtras (to watch)
SettingsStore (DataStore) <──> CarAlerts.evaluate ──> List<Alert> ──> Notifications.post ──> Android NotificationManager
                                                                          │ action buttons carry intent extras
                                                                          ▼
watch DataItem "climate" ──> WearPhoneService.onDataChanged ──> ClimateSyncStore.save(raw)
ClimateSyncStore.flow ──> AppViewModel (UI)
notification action tap ──> AlertActionReceiver.onReceive ──> WearCommandRunner.execute (BlueLinkGate) ──> Notifications.post (follow-up)
```

- **`Ai`** is called by the phone's AI-summary path (produces the `WearExtras.ai`
  per-VIN summary map mirrored to the watch for the `AI_SUMMARY` action).
- **`WeatherApi.fetch`** is called for the home location and each car's position; results
  become `Weather` in the UI and `WearWeather` (via `toWear()`) inside `WearExtras`
  (`homeWeather` / `carWeather`) pushed over the Wear Data Layer.
- **`CarAlerts.evaluate`** is driven by `com.bloo.bluelink.work.AlertWorker` (WorkManager,
  on a timer), reads `SettingsStore` and a car's latest `VehicleStatus`, and returns
  `Alert`s the worker posts via `Notifications.post`.
- **`Notifications.post`** targets `AlertActionReceiver` for each action button; the
  intent contract is the four `EXTRA_*` constants + the unique `bloo://alert/$id/$action` URI.
- **`ClimateSyncStore`** — `WearPhoneService.onDataChanged` calls `save(raw)` when the
  watch publishes a climate DataItem; `AppViewModel` observes `flow`.
- **`AlertActionReceiver`** — delegates to `WearCommandRunner.execute(ctx, WearCommand(vin, action))`
  (in `:shared`), then re-posts via `Notifications.post`.

Data leaving the device: HTTPS GET to `api.open-meteo.com` (lat/lon only, no key);
text sent to on-device ML Kit (stays on device). Nothing else leaves here.

---

## 7. Invariants & assumptions

- **`Ai.summarize`** assumes `ensureFeatureReady` leaves the model AVAILABLE; it does
  not re-check status before `runInference`. If the status is `UNAVAILABLE`,
  `ensureFeatureReady` returns without downloading and `runInference` will throw
  (propagated by design).
- ML Kit's ARTICLE summarizer requires **≥ 400 input chars** — `padToMinimum` guarantees
  this. Padding by repetition assumes repeating true facts cannot make the summary wrong.
- **`WeatherApi.fetch`** assumes Open-Meteo returns `temperature_2m` inside `current`
  whenever the response is usable; absence of either → `null`. Assumes `wind_speed_unit=kmh`
  is honored so `windKph` is genuinely km/h. `daily` arrays are read positionally
  (`firstOrNull`), assuming index 0 is "today" (guaranteed by `forecast_days=1`).
- Temperature rounding via `toInt()` **truncates toward zero** (e.g. -0.5°C → `0°`,
  72.9°F → `72°F`), not round-half-to-even.
- **`CarAlerts.evaluate`** assumes it is called repeatedly on a timer; the fire-once
  logic is meaningless for a single call. It assumes `SettingsStore` reads/writes are
  durable across worker invocations. `status == null` MUST be treated as "unknown", not
  "closed/off" (door and running checks are skipped) — otherwise flaky polls would
  perpetually reset the open/running timer.
- Odometer string may contain thousands separators; only `,` is stripped
  (`Notifications.kt:170`) — a locale using `.` as a grouping separator would misparse.
- **Notification ids** rely on `String.hashCode()` distinctness across the three kind
  prefixes and per VIN; a collision (astronomically unlikely) would let two alerts
  overwrite each other.
- **`post`** action `PendingIntent` distinctness relies on both the unique data URI
  **and** the `id * 16 + i` request code; this assumes fewer than 16 actions per alert
  (`i` in `0..15`) to avoid request-code overlap with the next id. In practice ≤ 2.
- **`AlertActionReceiver`** assumes `goAsync()` + `pending.finish()` in `finally` always
  runs; the coroutine must not out-live the process reclaim window (network I/O within
  the OS broadcast time budget). It assumes `WearCommandRunner.execute` never throws out
  of the `runCatching` (it returns a failed result instead).
- **`ClimateSyncStore`** assumes `WearSync.decodeClimate(null)` returns a sensible empty
  `WearClimateState` (it does) so the first emission before any `save` is valid.

---

## 8. Gotchas & sharp edges

- **`isSupported` swallows, `summarize` does not** (`Ai.kt:60` vs `:78`). Deliberate:
  a support probe should never crash the UI, but a real summarize failure should surface
  to the user rather than yield an empty/misleading summary.
- **Padding by repetition, not filler** (`Ai.kt:90`). Filler characters risk leaking into
  the model output; repeating the same true text cannot introduce false statements. Note
  the model *does* see duplicated content, but for a 3-bullet factual summary that's benign.
- **`ListenableFuture.await` runs the callback on the `direct` (same-thread) executor**
  (`Ai.kt:155`) and calls the blocking `get()` — safe only because the listener fires
  after the future is done. `invokeOnCancellation { cancel(false) }` does **not** interrupt
  an in-progress task.
- **All `await` helpers guard with `cont.isActive`** — a stale second resume from ML Kit /
  Play Services would otherwise crash with "already resumed".
- **`WeatherApi.fetch` returns bare `null` for every failure** — callers cannot tell a
  network error from a bad location from a malformed response. This is intentional
  (weather is nice-to-have) but means no retry/diagnostic distinction is possible upstream.
- **`isDay` defaults to daytime** when the API omits `is_day` (`WeatherApi.kt:204`) — a
  missing flag shows a day icon, never accidentally a night one.
- **`feelsLikeLabel` has no unit suffix** (`WeatherApi.kt:50`) — only the degree glyph.
  Using it standalone (without a nearby unit-labelled temp) would be ambiguous.
- **`highLowLabel` is all-or-nothing** — returns `null` unless *both* high and low exist,
  and uses **two spaces** between the H and L segments (`"H:$hi°  L:$lo°"`).
- **`toInt()` truncation** across all label helpers can make a value read one degree lower
  than a rounded display elsewhere. Be consistent if comparing.
- **`ensureChannel` intentionally does not re-create an existing channel** (`Notifications.kt:40`)
  — but even if it did, Android would not reset user-modified importance/sound; the check
  is mainly to skip wasted work.
- **`post` is a no-op when permission is denied**, and the final `notify` is wrapped in
  `runCatching` for the TOCTOU revoke race (`Notifications.kt:93`, `:131`). A denied user
  silently gets no alerts — nothing logs this here.
- **Unique data URI is load-bearing for action buttons** (`Notifications.kt:117`). Extras
  are NOT part of `PendingIntent` identity — only action/data/component are — so without
  `bloo://alert/$id/$action`, two actions (or two alerts) could silently share one intent
  and fire the wrong command. The `id * 16 + i` request code is a second safety net.
- **`CarAlerts` skips door/running checks entirely on `status == null`** (`Notifications.kt:195`,
  `:226`) — the single most important correctness note here. Treating a failed poll as
  "closed/off" would reset the timer every flaky tick and indefinitely defer a real alert.
  The service check has no such guard (it depends on the persisted odometer, not live status).
- **Service check clears its fired flag whenever it is not currently due** (`Notifications.kt:186`),
  including when due-ness is simply unknowable (missing `last`/`interval`/`odo`). So editing
  the interval/last-service downward can re-arm the alert.
- **`AlertActionReceiver` cancels the alert *before* the network call** (`AlertActionReceiver.kt:63`)
  for perceived responsiveness — the user sees the notification vanish instantly, then a
  success/failure follow-up appears under the **same id** (`:74`). If the command fails,
  the "…failed" notification is the only remaining trace; the original alert is gone.
- **Follow-up failure text prefers the command's own `message`** (`AlertActionReceiver.kt:71`)
  — e.g. the car refusing to lock while a door is physically open surfaces the backend's
  reason, which is exactly why the follow-up exists (see the class KDoc).
- **`ClimateSyncStore` uses a corruption handler** (`ClimateSyncStore.kt:15`) because
  `AppViewModel` collects `flow` at init — an uncaught corruption exception there would
  break app startup, so it resets to `emptyPreferences()` instead.
