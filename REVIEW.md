# Bloo — Deep Code Review

**Repository:** `ServerReset/Bloo` (branch `claude/great-faraday-QuX3x`, HEAD `b4c4831`)
**Scope:** 91 Kotlin files, ~34,600 LOC across `:app`, `:shared`, `:wear`, `:uicommon`
**Focus (as requested):** correctness / crash bugs, dead code & framework extraction, Compose/Android quality. Security flagged only in passing.
**Date:** 2026-07-23

---

## How this review was run

1. The `:shared` domain core (all 17 files: API clients, models, repositories, session/credential stores, sync, caches) was read line-by-line by hand to build a domain model and a set of invariants.
2. A 36-unit fan-out then reviewed **every file** line-by-line (giant files sliced with overlap — `Screens.kt` alone in 8 slices), each reviewer seeded with the domain invariants so cross-file/contract bugs would surface.
3. **Every candidate finding was then handed to an independent adversarial verifier** that re-read the actual source (and grepped cross-file usage for "unused"/"dead" claims) and was told to *refute* it if it could. 124 agents ran; **10 findings were refuted** and are listed at the bottom so they're not chased.
4. Findings below are only those that survived verification, with the verifier's severity adjustments applied.

**Bottom line:** the codebase is unusually clean for its size and has clearly been through heavy prior AI passes (many comments narrate already-fixed bugs). No unconditional data-loss or crash-on-launch bug survived. The real risks cluster in **out-of-process command paths** (widget / QS tile / launcher shortcut / watch tile) where the app-wide "serialize every car request" and "gate climate while driving" invariants are enforced inconsistently, and in a handful of **money/security-path correctness bugs** (wrong-car voice/search command, start/stop inversion, shortcut re-fire).

Severity key: **HIGH** = user-visible wrong behavior, wrong car command, or a stuck/again-and-again failure · **MEDIUM** = real defect with narrower trigger or non-destructive impact · **LOW** = cosmetic / dead code / latent.

---

## HIGH severity

### H1 — Settings-search sends a command to the WRONG car (substring name match)
`app/.../ui/Screens.kt:10117` · correctness · CONFIRMED
The search resolves the target vehicle with `state.vehicles.firstOrNull { v -> v.name.isNotBlank() && v.name.lowercase() in submittedQuery.lowercase() }` — an unbounded substring test, no word boundaries, no longest-match, returns the first car in list order. The resolved vehicle feeds `TileCommandRunner.run` (…:10124) which dispatches a real lock/climate/charge command.
**Failure:** cars named "Ioniq" and "Ioniq 5" (Ioniq first). "lock my Ioniq 5" → `"ioniq" in "lock my ioniq 5"` is true → command sent to **Ioniq**, not Ioniq 5. Same for EV6/"Kia EV6", Niro/"Niro EV".
**Fix:** tokenize to whole-word matches; when several names match, prefer the longest; treat remaining ambiguity as "which car?" instead of list-order-first.

### H2 — Settings-search "stop climate"/"stop charging" does the OPPOSITE
`app/.../ui/Screens.kt:9709` (and 9711 for charge) · correctness · CONFIRMED
`parseVehicleCommand` encodes stop and start into the *same* `cmd` (`"climate"`/`"charge"`); the only difference is a human `label` that is never passed downstream (`TileCommandRunner.run(ctx, vin, command.cmd, command.climateTarget)`). The runner then decides direction by re-reading the snapshot's on/off flag.
**Failure:** climate is OFF, user types "stop the climate" → runner sees off → **starts** climate on the real car. "start climate" while on → stops it. Same inversion for charge.
**Fix:** give stop/start distinct commands the runner acts on directionally (e.g. `climate_off` vs `climate_on`), or thread an explicit start/stop flag through `ParsedVehicleCommand` → `TileCommandRunner.run`.

### H3 — Launcher shortcut re-fires a car command on every Activity recreation
`app/.../MainActivity.kt:90` · correctness · CONFIRMED
`onCreate` calls `handleShortcutIntent(intent)` unconditionally, with no `savedInstanceState == null` guard, and the manifest declares no `configChanges` for `MainActivity`. `getIntent()` still returns the original `ACTION_SHORTCUT` intent across recreation, and it's never cleared.
**Failure:** launch via the "Doors" shortcut (lock/unlock fires); rotate the phone or toggle dark mode mid-load → `onCreate` re-runs → `handleShortcut(vin,"doors")` fires again. For toggle actions this **duplicates or inverts** a real lock/unlock command.
**Fix:** `if (savedInstanceState == null) handleShortcutIntent(intent)`, and neutralize the intent after handling (`setIntent(Intent())`).

### H4 — `AppLog` formats timestamps OUTSIDE its own `synchronized` block
`shared/.../data/AppLog.kt:38` · concurrency · CONFIRMED
`val line = "${timestamp.format(Date())}  $message"` runs *before* the `synchronized(this)` block (only the list mutation is inside it), directly contradicting the class's own comment. `SimpleDateFormat` is not thread-safe.
**Failure:** two threads (network callback + command thread) log at the same instant → corrupted timestamp string, or an `ArrayIndexOutOfBoundsException`/`NumberFormatException` thrown out of the logging call, crashing whatever background op was merely trying to log.
**Fix:** move the `val line = …` inside `synchronized(this)`, or use a `ThreadLocal<SimpleDateFormat>` / `DateTimeFormatter`.

### H5 — Manual pull-to-refresh silently swallowed behind a background fetch
`app/.../ui/AppViewModel.kt:1030` · correctness/concurrency · CONFIRMED (found independently + by fan-out)
`loadStatus` de-dupes purely by VIN (`if (!statusInFlight.add(v.vin)) return`) with no distinction between a silent background fetch (`refresh=false`) and a user pull-to-refresh (`refresh=true`). The early return sits *before* `refreshing=true` is set.
**Failure:** cold start fires `ensureStatus` (refresh=false) for every car, serialized behind `statusMutex`. User pulls to refresh a car still queued → the manual call returns instantly: no spinner, and the queued fetch is `refresh=false` (server-cached), so the user never gets the live poll they asked for.
**Fix:** key the in-flight set on `(vin, refresh)`, or upgrade an in-flight `refresh=false` to `refresh=true` rather than dropping the caller.

### H6 — Sticky `messageType`: genuine errors render in the success/info snackbar colour
`app/.../ui/AppViewModel.kt` (`messageType`, error emitters at 346, 1077, 1941) · correctness · CONFIRMED
`messageType` (default `"error"`, drives snackbar colour) is only ever *set* by `reportError`/`reportInfo`/`importSettings`, and only reset to `"error"` by `reportError`. Every other error emitter (`runCommand` failure, `loadStatus` catch, `checkAlerts`, login/sync errors) sets `message` without touching `messageType`; `clearMessage` nulls only `message`.
**Failure:** `maybeRelock` shows an info snackbar ("Data may be stale…") → `messageType="info"`. User taps Lock, it fails → "Command failed" is shown in the **neutral info colour**, so a real failure looks benign and the user believes the door locked.
**Fix:** reset `messageType="error"` wherever an error/alert message is set (or route all errors through `reportError`; simplest: `clearMessage` also resets the type).

### H7 — Weather pebble wind speed hardcoded to km/h, ignores unit preference
`app/.../ui/Screens.kt:8047` · correctness · CONFIRMED
`"${w.windKph.toInt()} km/h"` — literal `km/h`, no conversion — while every other distance/speed readout routes through `formatSpeed(kph, metric)`. The app's audience is US (imperial) Blue Link / Kia Connect owners.
**Failure:** imperial user, `windKph=32` → shows "32 km/h" instead of "19 mph".
**Fix:** `StatusRow("Wind", formatSpeed(w.windKph, metric))`, deriving `metric` from `unitSystem == "metric"` as `FuelPebble` does.

### H8 — Second consecutive wrong PIN leaves the watch PIN pad stuck
`wear/.../ui/PinLockScreen.kt:175` · correctness · CONFIRMED
The reject/clear/shake behaviour is driven by `LaunchedEffect(error)` keyed on the error **string**. Both callers set the identical `"Wrong PIN"` on repeat failures and never reset `error` to null between attempts. Compose `MutableState` uses structural equality, so re-assigning the same value doesn't restart the effect; meanwhile `onDigit` deliberately leaves the 4 dots filled after submit.
**Failure:** first wrong PIN clears+shakes; second identical wrong PIN → effect doesn't re-run → buffer stays at length 4, no haptic, no shake, and `if (buffer.length < PIN_LENGTH)` blocks all further digits. User is stuck until they manually backspace 4 times. Affects both the unlock gate and the Change/Remove-PIN confirmation.
**Fix:** don't key on the message text — bump a monotonic attempt/nonce counter and key the effect on that, or clear the buffer unconditionally on a length-4 failed submit.

### H9 — Background "engine left running" alert never fires unless another alert is on
`app/.../work/AlertWorker.kt:71` · correctness · CONFIRMED
The early-exit gate checks only `prefs.service` and `prefs.doorOpen`, but `CarAlerts.evaluate` also handles `prefs.running`. If the user disables service + door alerts but keeps the running/engine-left-on alert, `doWork()` returns immediately and never logs in, fetches status, or evaluates.
**Failure:** user keeps only "Engine/climate left running" on, leaves the car remote-started, closes the app → the alert never fires while the app is closed (which is the entire point of the background worker).
**Fix:** `if (!prefs.service && !prefs.doorOpen && !prefs.running) return Result.success()`.

### H10 — `clearWidgetConfig` leaves alpha/pill/layout keys behind on widget-id reuse
`app/.../data/SettingsStore.kt:816` · lifecycle · CONFIRMED (one verifier upgraded MEDIUM→HIGH)
The removed-suffix list omits three per-widget keys written elsewhere in the same file: `widget_<id>_alpha`, `widget_<id>_pill`, `widget_<id>_layout`. Android recycles widget ids. (The list also carries dead `lat`/`lon` entries never written anywhere.)
**Failure:** user deletes a widget configured with alpha=9 (near-invisible), pill shape, controls layout. Android reassigns that id to a new widget → it renders nearly invisible / wrong shape / wrong layout with no config change.
**Fix:** add `alpha`, `pill`, `layout` to the suffix list, drop `lat`/`lon`; better, define the suffixes once as a shared constant used by both setters and `clearWidgetConfig`.

### H11 — Quick-Settings tile taps can duplicate or invert a car command (no unique work)
`app/.../tiles/TileCommandWorker.kt:49` · concurrency · CONFIRMED
`enqueue()` uses `WorkManager.enqueue(req)` with no unique-work name / `ExistingWorkPolicy`, so double-taps run concurrently. In `TileCommandRunner.run` the toggle direction is read from the snapshot *before* `statusMutex` is taken, and the optimistic flip is written *after* the lock is released — so the mutex serializes only the network dispatch, not the read→decide→write.
**Failure:** double-tap a "doors" tile → W1 reads locked=false, sends lock, writes locked=true; W2 (scheduling-dependent) reads locked=true and sends **UNLOCK** — car ends up unlocked after two lock taps. Or both read stale and send duplicate locks.
**Fix:** `enqueueUniqueWork` per `(vin,cmd)` with `APPEND_OR_REPLACE`/`KEEP`, and move the snapshot read + optimistic write inside the `statusMutex` critical section so direction and flip are atomic with dispatch.

### H12 — `extrasMutex` doesn't actually serialize the write (watch extras lost)
`app/.../wear/WearPhoneService.kt:287` · concurrency · CONFIRMED (verifier kept HIGH)
The mutex guards the read half, but the write half is `WearBridge.publishExtras`, which is fire-and-forget (`scope.launch{ … putDataItem … }`) and returns immediately. The actual Data Layer write runs *after* `withLock` releases, so two concurrent extras patches (AI summary + weather) race last-writer-wins.
**Failure:** AI-summary request A reads extras, schedules write W_A, releases lock; weather request B reads the still-stale extras, schedules W_B; W_A and W_B race → the AI summary or the fresh weather is silently dropped from the watch — exactly what the mutex comment claims to prevent.
**Fix:** make `publishExtras` a `suspend` that awaits `putDataItem`, and call it inside `extrasMutex.withLock`.

### H13 — Watch tile command relay can be cancelled by service teardown (PLAUSIBLE)
`wear/.../tile/BlooTileService.kt:79` · lifecycle · PLAUSIBLE (real anti-pattern; worst case runtime-dependent)
The tap's network command is launched fire-and-forget on `tileScope`, a service-scoped scope that `onDestroy` cancels — and the class comment itself says the Tiles system freely destroys the service between requests. The optimistic snapshot flip is applied and re-rendered synchronously, so the tile shows the new state regardless.
**Failure:** tap Unlock→Lock; tile renders "locked"; system tears the service down within ~1s → `tileScope.cancel()` cancels the still-running relay before it reaches the backend. Car stays unlocked but the tile shows locked. (Verifier notes the blocking `Tasks.await` sends may complete if cancellation lands mid-send, so loss is narrower than certain — but the pattern is wrong.)
**Fix:** relay on a process/application-scoped `SupervisorScope` or hand off to WorkManager; only cancel UI/render scopes in `onDestroy`.

---

## MEDIUM severity

### M1 — `statusMutex` held across reverse-geocode + alert checks (serializes unrelated slow work)
`app/.../ui/AppViewModel.kt:1066` · concurrency · CONFIRMED (found independently + by fan-out)
Inside `loadStatus`'s `statusMutex.withLock`, after the status call the same critical section awaits `checkAlerts` (DataStore read) and `reverseGeocode` (blocking `Geocoder`, can take seconds/hang). The mutex only needs to cover the `status()` network call.
**Failure:** a garage of 3 cars prefetches sequentially; each holds the account-wide mutex through its geocode, so every other car's fetch and the background poller stall behind it — initial load/refresh several times slower than necessary.
**Fix:** capture `s`/`statusLoc` inside the lock, release it, then run `checkAlerts`/`reverseGeocode` and update `placeNames` separately.

### M2 — Watch toggle direction read outside the mutex → concurrent toggles invert
`shared/.../data/WearCommandRunner.kt:27` · concurrency · CONFIRMED (borders HIGH)
`snap` is read from `SnapshotStore` before `BlueLinkGate.statusMutex` is acquired; the lock body never re-reads the store. Its own `resolveToggle` docstring warns that direction must be decided from a serialized read.
**Failure:** car locked; two overlapping `TOGGLE_LOCK` (watch double-tap, or watch racing phone). Both read locked=true; A unlocks; B still holds stale locked=true and sends UNLOCK again instead of re-locking.
**Fix:** move the `store.current()` read + vehicle lookup inside `statusMutex.withLock`.

### M3 — App-icon "climate" shortcut starts climate with no `isDriving` gate
`app/.../ui/AppViewModel.kt:940` · correctness · CONFIRMED
`tryRunPendingShortcut` calls `startClimate` directly with no driving check, unlike the in-app control (disabled while driving) and the QS-tile path (hard error while driving). `startClimate`/`runCommand` don't gate either.
**Failure:** tap the Climate launcher shortcut while driving → command dispatched, car rejects it, spurious "command failed" surfaces and a serialized request slot is wasted.
**Fix:** guard the shortcut climate branch with `if (!_state.value.isDriving(v))`, matching the in-app read-only gate.

### M4 — Null status marks a VIN `sessionFetched`, defeating retry-on-view
`app/.../ui/AppViewModel.kt:1072` · correctness · CONFIRMED
`sessionFetched.add(v.vin)` runs unconditionally after the `withLock` block, even when `status()` returned null. `ensureStatus` skips any VIN already in `sessionFetched`, contradicting the documented "a car that failed to load gets another chance when you view it" (which only holds for the throw path).
**Failure:** a sleeping car returns null at startup → marked fetched → swiping to it never auto-retries; user must manually pull-to-refresh.
**Fix:** only add to `sessionFetched` when a non-null status was received (move the add inside the `?.let`).

### M5 — AI-support flag lost after logging out of the last account
`app/.../ui/AppViewModel.kt:599` · lifecycle · CONFIRMED
`aiSupported` is probed once in `init`. `logout()` of the final account replaces the whole state with a fresh `UiState`, resetting it to false, and no path re-probes on re-login.
**Failure:** sign out of the only account, sign back in same session → AI summary UI and toggle silently vanish on a Gemini-Nano-capable device until app restart.
**Fix:** preserve `aiSupported` across the logout reset, or re-probe on login / garage entry.

### M6 — Drive-sync `clearDirtyKeys()` can wipe an edit made mid-upload (data loss)
`app/.../data/SettingsStore.kt:1061` · concurrency · CONFIRMED
The upload body is snapshotted (`exportSettingsJson`) before a multi-second write+verify; on success `clearDirtyKeys()` removes the *entire* dirty set. Ordinary setters (`editTracked`) don't take `driveSyncMutex`, so a key marked dirty *after* the snapshot but *before* the clear loses its protection.
**Failure:** user toggles setting K=V during an in-flight upload. V isn't in the uploaded body, but K's dirty flag is cleared. A later sync finds a newer remote file (written by another device), imports it, K is no longer protected, and V is overwritten with the old value — silent revert.
**Fix:** capture the exact key set reflected in the exported body and clear only those (set-difference), not the whole set.

### M7 — `locate()` updates status but never `lastFetched`
`app/.../ui/AppViewModel.kt:1760` · correctness · CONFIRMED
`locate()` writes fresh status into `state.statuses` and persists, but never sets `lastFetched`, `sessionFetched`, or calls `checkAlerts` (unlike `loadStatus`).
**Failure:** after a successful Locate the card's "updated X ago" still shows the old time, and `maybeRelock`'s stale check can flag the car stale and nudge "pull to refresh" right after fresh data arrived.
**Fix:** in the status-success branch, also set `lastFetched + (vin to now)` (and consider `sessionFetched`/`checkAlerts`).

### M8 — Standalone watch garage fetch runs outside the mutex
`wear/.../WearViewModel.kt:644` · concurrency · CONFIRMED
`loadGarage()`'s standalone fallback calls `repoFor(b).vehicles()` with only `runCatching`, no `BlueLinkGate.statusMutex.withLock` — unlike the standalone command path and `loadTrips` in the same file.
**Failure:** a standalone command awaiting the car API overlaps a `resync()`-triggered `vehicles()` for the same account → backend 502 / "previous request pending".
**Fix:** wrap the standalone `vehicles()` fetch in `statusMutex.withLock`.

### M9 — Blocking Keystore read inside a `_ui.update` CAS retry lambda (Main thread)
`wear/.../WearViewModel.kt:586` · concurrency · CONFIRMED
`login()`'s onSuccess calls `credentialStore.loadAll()` *inside* the `_ui.update{}` lambda, which re-runs under CAS contention — re-invoking blocking `EncryptedSharedPreferences`/Keystore crypto on Main each retry. `bootstrap()` already fixed exactly this pattern by resolving the value off-main first.
**Failure:** a concurrent collector loses the CAS race right after sign-in → the update lambda re-runs `loadAll()` again, blocking Keystore decryption on Main → jank / potential ANR.
**Fix:** resolve the email list in `withContext(Dispatchers.IO)` into a `val` before `_ui.update`, matching `bootstrap()`.

### M10 — Watch "last updated" timestamp frozen (never advances)
`wear/.../WearViewModel.kt:1816` · correctness · CONFIRMED
`fetchedAt` is written exactly once (in `bootstrap()` from the on-disk cache) and read in `buildCarView`; no snapshot-collector/flip/relay path ever updates it. (Verifier notes `StatusCache.save()` is never called on the watch, so in practice the map is empty and the label renders blank / the stale indicator is dead — either way the timestamp never tracks fresh data.)
**Fix:** set `fetchedAt + (vin to now)` on each real data arrival before `publish()`, or derive it from the snapshot/status payload.

### M11 — Widget `h<70` tier ignores layout mode → info widget shows buttons or blank
`app/.../widget/BlooWidget.kt:296` · correctness · CONFIRMED
The `h < 70.dp` branch routes to `ControlsTile` unconditionally, unlike the tier above which honours `c.layoutMode`. `ControlsTile` early-returns (renders nothing) when `c.actions` is empty.
**Failure:** a short-wide info-mode widget shows the button grid instead of data; if it also has no actions configured, it renders completely blank.
**Fix:** `h < 70.dp -> if (c.layoutMode == "controls") ControlsTile(c, base) else InfoTile(c, base)`.

### M12 — `ControlsTile` ignores its `base` modifier → transparent/unrounded widget
`app/.../widget/BlooWidget.kt:352` · correctness · CONFIRMED
`ControlsTile` never references `base` (the only source of the solid background / corner radius / pill padding in the default opaque case) and builds a fresh modifier instead.
**Failure:** a default opaque controls-mode widget at the tiny/short tiers loses its `themeBg` fill and rounding — buttons float on the wallpaper with square corners.
**Fix:** pass `base.padding(4.dp)` to `ButtonGrid`, or wrap it in a Box using `base`.

### M13 — Widget accent ignores `themeMode` override
`app/.../ui/Theme.kt:158` · correctness · CONFIRMED
`resolveWidgetAccent` derives dark/light purely from system `uiMode`, while `BlooTheme` honours `appearance.themeMode` (LIGHT/DARK/AMOLED/SYSTEM). Light and dark primaries differ materially.
**Failure:** user forces DARK while the system is light → app shows the dark-blue accent, widget/tile/notification show the light-mode dark-blue accent — inconsistent, despite the doc claiming "always consistent".
**Fix:** pass `appearance.themeMode` and compute `isDark` the way `BlooTheme` does.

### M14 — Widget accent omits vibrancy saturation
`app/.../ui/Theme.kt:179` · correctness · CONFIRMED
`resolveWidgetAccent` returns the raw primary with no `saturate(vibrancy)`, while `blooColorScheme` applies it when `vibrancy != 1f`.
**Failure:** with vibrancy 1.6 or 0.5, the widget/tile accent visibly differs from the app.
**Fix:** thread `vibrancy` in and apply the same `saturate(vibrancy)` on every return path.

### M15 — Periodic widget refresh worker never cancelled when the last widget is removed
`app/.../widget/BlooWidgetReceiver.kt:16` · lifecycle · CONFIRMED
The receiver overrides only `onDeleted`, never `onDisabled`, and never calls the existing `WidgetRefreshWorker.cancel`. The 15-min poll (network + fan-out) runs forever after all widgets are removed. (Also: `onDeleted` writes on a bare `CoroutineScope(Dispatchers.IO)` with no `goAsync()`, so the clear can be dropped on process death.)
**Fix:** override `onDisabled` → `WidgetRefreshWorker.cancel(context)`; use `goAsync()`/WorkManager for the `onDeleted` writes.

### M16 — Watch temperature-unit rule diverges between cards
`wear/.../ui/HomeScreen.kt:957` (vs `1443`) · correctness · CONFIRMED
Climate/SmartClimate/Info cards use `unitSystem != "metric" || useFahrenheit != false`; `WeatherCard` uses only `useFahrenheit != false`. Because the watch's own `localSettings.unitSystem` is never synced *from* the phone, changing units on the phone drives them apart.
**Failure:** switch units to metric on the phone → phone pushes `useFahrenheit=false` (WeatherCard shows °C) but the watch-local `unitSystem` stays "imperial" (Climate/Info show °F) — same page, mixed units.
**Fix:** one source of truth for temperature units across all four cards (a shared helper).

### M17 — Unsynced lock complication renders as an OPEN padlock (icon-only faces)
`wear/.../complication/ToggleStateComplication.kt:61` · correctness · CONFIRMED
The `on==null` (not-yet-synced) fix only covers the SHORT_TEXT path ("—"); the icon falls back to `iconRes(false)`, which for the lock complication is the open padlock. The MONOCHROMATIC_IMAGE branch is image-only with no text escape hatch.
**Failure:** on an icon-only watch face, an unsynced lock complication shows the open-padlock glyph — indistinguishable from a confirmed "Unlocked" reading.
**Fix:** use a distinct neutral/unknown glyph for `!known` (both branches), or return null for MONOCHROMATIC_IMAGE when state is unknown.

### M18 — `AnimatedSlider` stuck blurred when a settle spring is interrupted by a new drag
`uicommon/.../AnimatedSlider.kt:166` · lifecycle · CONFIRMED
`settling = false` runs only if `animateTo` completes normally; a new drag's `snapTo` cancels the settle animation (shared `MutatorMutex`), so `animateTo` throws `CancellationException` and `settling` is never reset. While stuck true, the whole control renders blurred at 4dp and external value sync is blocked.
**Failure:** tap a step to start the bounce, then immediately drag before it finishes → the slider stays visibly blurred for the whole drag. (Self-heals on release.)
**Fix:** `try { anim.animateTo(...) } finally { settling = false }`.

### M19 — Temperature labels truncate instead of rounding (wrong for negatives)
`app/.../data/WeatherApi.kt:46` · correctness · CONFIRMED
`tempLabel`/`feelsLikeLabel`/`highLowLabel` convert via `Double.toInt()` (truncates toward zero) despite the KDoc saying "rounded".
**Failure:** −3.9 °C shows "−3°" (a degree warmer, wrong direction); −0.6 °C shows "0°", dropping the sign; 71.8 °F shows "71°".
**Fix:** `roundToInt()` on the converted value in all four helpers.

### M20 — Climate-choice dialog is dead code (unreachable)
`app/.../ui/Screens.kt:7212` · dead-code · CONFIRMED
`showClimateChoice` is only ever assigned `false`; there is no `= true` anywhere. The ~28-line `BlooDialog` block is compiled but unreachable — the header Start button handles the advanced/collapsed case inline instead.
**Fix:** delete the state + dialog, or wire the advanced-collapsed branch to open the chooser when multiple presets exist. **Confirm intended behaviour before deleting.**

### M21 — `CompactMainTile` computes `carIndex`/`carCount` but never uses them
`app/.../ui/Screens.kt:3535` · dead-code · CONFIRMED
Two vals assigned, never read; `carIndex = state.vehicles.indexOf(v)` is an O(n) scan on every recomposition of the full-screen cover tile.
**Fix:** delete both lines.

### M22 — Duplicate `androidx.core:core-ktx` at two versions
`app/build.gradle.kts:119` · duplication · CONFIRMED
`1.15.0` (line 100) and `1.13.1` (line 119) both declared. Gradle resolves to 1.15.0, so line 119 is dead/misleading config.
**Fix:** delete line 119.

### Framework-extraction / duplication (MEDIUM)

- **M23** `shared/.../Models.kt:324` — `SeatLevel.ventilatedRange`/`heatOnlyRange` are byte-for-byte what `rangeFor(true,true)`/`rangeFor(false,true)` build **and are unreferenced anywhere** (verified by repo-wide grep). Delete both, or replace bodies with `rangeFor(...)`.
- **M24** `wear/.../WearViewModel.kt:1529` — the full `CLIMATE_ON` `WearCommand` construction (+ seat `apiValue` mapping) and the `isDriving`-gate/`startClimate`/`flip` triplet are copy-pasted across `toggleClimate`, `applyPreset`, `smartClimate`, `toWearCommand`. Extract one command-builder + one `startClimateStandalone(v, repo, st, request)`.
- **M25** `wear/.../ui/LoginScreen.kt:109` — rotary-scroll + focusRequester + `LaunchedEffect(Unit){requestFocus}` + `ScalingLazyColumn` boilerplate (and the busy-spinner block) duplicated across LoginScreen / SettingsScreen / TripsScreen (and a drifted variant in WatchApp). Extract `RotaryScalingColumn` + `BusySpinner` into `Components.kt`.

---

## LOW severity (grouped)

**Correctness / display**
- **L1** `shared/.../FormatUtils.kt:188` — `relativeLabel` always prints singular "day ago" ("3 day ago").
- **L2** `shared/.../BlueLinkApi.kt:476` — blank model falls back to literal "Hyundai" even for a Genesis car.
- **L3** `shared/.../KiaUsaApi.kt:225` — `verifyOtpAndComplete` ignores a rotated `rmtoken` on the finish response (contradicts `authUser`'s own comment); could force an OTP re-login if Kia rotates it.
- **L4** `app/.../ui/Screens.kt:5128` — the "trips" pebble isn't gated to EV/battery cars (`DEFAULT_SECTIONS` always includes it); fires the EV-only trip endpoint for gas/PHEV/Kia cars. Graceful empty state, so low impact.
- **L5** `app/.../ui/Screens.kt:10024` — odometer search entry is added even when the value fails to parse → a result card titled "Odometer · <car>" with an empty body.
- **L6** `app/.../widget/BlooWidget.kt:235` — `bgAlpha = 1f - level/9f` reaches 0.0 at level 9 (comment claims a 0.1 floor); the scrim/tint vanishes, hurting text legibility over photos. Clamp `.coerceAtLeast(0.1f)` or fix the comment.
- **L7** `app/.../widget/WidgetCommandWorker.kt:130` — geocoded address drops the house number when the street name is blank.
- **L8** `wear/.../ui/HomeScreen.kt:1028` — SmartClimate "±10°" hardcodes a Fahrenheit magnitude but labels it with the display unit (shows "±10°C" when the real swing is ~±5.6 °C).
- **L9** `wear/.../ui/HomeScreen.kt:1545` — DiagnosticsCard "N to check" double-counts a tire that has both a per-wheel flag and the aggregate warning.
- **L10** `uicommon/.../MorphSegmented.kt:167` — `pendingIndex` never clears when a controlled caller rejects the drag-selected key → indicator and bold label disagree until the next interaction.
- **L11** `uicommon/.../WiggleText.kt:47` — the "67" easter-egg triggers on any string whose digits concatenate to 67 ("6-7", "6.7", "ready 67"); the code contradicts its own comment.
- **L12** `wear/.../WearComms.kt:208` — `requestSync` returns `sent || refresh`, contradicting its "true only if the phone received it" contract (latent; current callers don't observe the difference). PLAUSIBLE.
- **L13** `app/.../data/SettingsStore.kt:260` — `uiScale`/`vibrancy` read & stored without range clamping; a corrupt/hand-edited/foreign backup with `ui_scale="10"` scales the whole UI 10× and can lock the user out of Settings. `coerceIn` on read.

**Compose / Android quality**
- **L14** `app/.../ui/Screens.kt:383` — `haptics.enabled = appearance.hapticsEnabled` mutated during composition; move to `SideEffect{}`.
- **L15** `app/.../ui/Screens.kt:3719` — PagerDots' post-refresh 300ms linger never runs (the `holding=false` write cancels its own `LaunchedEffect(holding)` before the delay). Do the delay+collapse before flipping `holding`.
- **L16** `app/.../data/SettingsStore.kt:233` — the `appearance` Flow allocates a fresh `Json` + `ListSerializer` on every emission; reuse the existing `paletteJson`/`paletteListSerializer` fields.
- **L17** `app/.../tiles/TileCommandWorker.kt:33` — `doWork` discards `TileCommandRunner.run`'s `Result` and always returns `success()`; genuine failures ("can't start climate while driving", network) show the optimistic ack toast and no error, no retry.
- **L18** `app/.../widget/WidgetCommandWorker.kt:274` — the optimistic snapshot flip is written even when `enqueue()` is dropped by `ExistingWorkPolicy.KEEP` (double-tap within ~4s) → widget shows a state no command was sent for until the next refresh corrects it.
- **L19** `app/.../widget/WidgetCommandWorker.kt:223` — `HttpURLConnection` not disconnected if `decodeStream` throws (leaks the connection on a corrupt map tile); use try/finally or `.use{}`.

**Dead code**
- **L20** `shared/.../KiaUsaApi.kt:495` — unreachable `1 ->` branch in `seatSettings()` (no `SeatLevel` yields apiValue 1; also duplicates the `8 ->` body).
- **L21** `app/.../update/UpdateChecker.kt:9` — unused `import kotlinx.coroutines.flow.first`.
- **L22** `app/.../widget/BlooWidget.kt:476,502,534` — `MediumTallTile(w,h)`, `SquareTile(w)`, `WideTile(w)` declare tile-size params never used in their bodies.
- **L23** `app/.../ui/Screens.kt:2617` — unused `val likelyCoverScreen = compact && hasCameraCutout`.

**Duplication**
- **L24** `shared/.../KiaRepository.kt:106` — `location()` runs a full `cmm/gvi` (+ `evc/gts` for EVs) just to extract lat/lon; callers that already fetched `status()` double-fetch and the charge-target leg is pure waste on the location path.
- **L25** `app/.../ui/Screens.kt:9451` & `10298` — the 44dp thumbnail-or-gradient-fallback Box is copy-pasted in `CarSettingsCard` and `CarTilesHeader`; extract `CarThumbnail(img, size, cornerRadius)`.
- **L26** `app/.../data/SettingsStore.kt:350` — `notificationPrefs()` and the `notifications` Flow duplicate the same 5-line decode; extract one `decodeNotificationPrefs(prefs)`.
- **L27** `shared/.../CredentialStore.kt` ↔ `SessionStore.kt` — legacy-migration + per-brand bookkeeping reimplemented twice with subtly different behaviour (CSV vs `putStringSet`, migrate-before-merge vs not). This asymmetry is the root of the (refuted-but-latent) R4 below; consolidate into one helper.

**Docs / config**
- **L28** `app/.../widget/BlooWidget.kt:906` — `boxBlurPass` KDoc says "three passes" but `blurredCached` runs `repeat(2)`.
- **L29** `app/build.gradle.kts:49` & `wear/build.gradle.kts:42` — `isMinifyEnabled=false` for release, so `proguard-rules.pro` never runs; the wear rules are narrower than the app's (no generic `**$$serializer` keep) and untested — a latent trap if minify is ever enabled.
- **L30** `app/build.gradle.kts:9` — `compileSdk = 37` (integer) requires a *finalized* API 37 platform in CI; if 37 is still preview at build time, AGP demands `compileSdkPreview` and the CI build fails (no APK published). PLAUSIBLE — verify against the SDK release calendar.

---

## Refuted findings (do NOT act on these — verified false positives)

These were raised by a reviewer and then **refuted** by an independent verifier that re-read the source. Listed so they aren't re-investigated.

- **R1** `Screens.kt:1207` — "CarFeatureWizard soft-locks on empty pages." Refuted: `Screen.CarSetup(vins)` is only constructed from the same vehicle list written to `state.vehicles` in the same atomic update, so `pages` is never empty when that screen shows.
- **R2** `FormatUtils.kt:259` — "`formatEfficiency` divides by zero → 'Infinity'." Refuted: the only caller (`TripsScreen.kt:178`) guards `if (k > 0)`.
- **R3** `CredentialStore.kt:127` — "migrateLegacy passes null password/pin to putString, dropping the account." Refuted: it's `EncryptedSharedPreferences`, which stores a null as a zero-length string (doesn't remove the key); and a legacy install missing password/pin was never a loadable credential anyway.
- **R4** `SessionStore.kt:154` — "migrateLegacy overwrites `brandsKey`, dropping a saved brand (Kia scenario)." Refuted: `KiaRepository.startLogin` calls `store.load(KIA)` (which migrates first) *before* saving, and cold-start `loggedInBrands()` migrates at launch. The code asymmetry is real (see L27) but the described bug doesn't manifest.
- **R5** `Screens.kt:9706` — "search emits `lock`/`unlock` but the runner only knows `doors`." Refuted: `TileCommandRunner.run` has explicit `"lock"`/`"unlock"` branches.
- **R6** `WearPhoneService.kt:141` — "Drive sync reports failure when nothing to upload." Refuted: `performDriveSync` uploads unconditionally on every run, so a successful sync always yields `uploaded=true`.
- **R7** `tiles/BlooTileService.kt:41` (phone) — "tile render/onClick do blocking disk reads on Main." Refuted: all reads are `suspend` DataStore reads, which do their IO off-main by design.
- **R8** `WearViewModel.kt:518` — "`statusCache.load()`/`snapshotStore.current()` block Main at bootstrap." Refuted: both are `suspend` DataStore reads (main-safe); only the non-suspend Keystore `loadAll()` needed the IO wrap.
- **R9** `WearViewModel.kt:1442` — "`setAuroraColorMode` silently drops the change when settings null." Refuted: the color-mode control is inside `AnimatedVisibility(visible = auroraEnabled)`, which requires non-null settings, so the `?: return` is unreachable via the UI.
- **R10** `tile/BlooTileService.kt:488` (wear) — "no `isDriving` gate before relaying `CLIMATE_ON`." Refuted: the relay converges on `WearCommandRunner.execute`, which enforces the driving gate immediately before `repo.startClimate` on both transports.

---

## Cross-cutting themes (fix the class, not just the instance)

1. **Out-of-process command paths under-enforce the two core invariants.** "Serialize every car request through `BlueLinkGate.statusMutex`" and "gate climate-start on `isDriving`" are correct in the phone UI but leak in the launcher shortcut (M3), the QS tile worker (H11), the watch standalone garage fetch (M8), and the watch toggle read (M2). Consider one funnel — a single `runCarCommand`/`refreshCar` that takes the lock, reads state, checks the driving gate, dispatches, and writes the optimistic flip — reused by *every* entry point (phone, tile, widget, watch, shortcut). Several bugs here are the same bug wearing different hats.

2. **Optimistic snapshot flips are applied before the command is guaranteed to run.** H11, H13, and L18 all show the UI committing to a new state that the backend may never receive (dropped by `KEEP`, cancelled with the service, etc.). Optimistic writes should be conditioned on the command actually being *accepted*/enqueued, and reconciled by a guaranteed follow-up refresh.

3. **`messageType` / snackbar styling isn't tied to the message.** H6 is a whole class: message and its type are set independently. Bundle them (a single `setMessage(text, type)` or a `Message(text, type)` object) so they can't desync.

4. **Temperature/speed unit handling is re-derived per surface.** H7, M16, L8 — the "which unit" decision and the conversion are duplicated and have already drifted between phone/watch/widget. Route every temp/speed/distance render through the shared `FormatUtils` helpers, and settle on one "is Fahrenheit / is metric" source of truth.

5. **Per-key bookkeeping lists drift from their setters.** H10 (widget config keys) and L27 (brand-set migration) are both "a list of keys maintained by hand next to the code that writes those keys." Define the key set once as a shared constant.

---

*Findings verified against source at commit `b4c4831`. Line numbers are 1-indexed and were re-checked by the verification pass; a few were corrected from the original reviewer's numbers during verification.*
