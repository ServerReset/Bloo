# Fix Prompt — Bloo (for an AI coding agent)

You are fixing verified bugs in **Bloo**, a Kotlin/Jetpack Compose Android app that remotely controls **real** Hyundai/Genesis (Blue Link) and Kia (Kia Connect) US vehicles. Modules: `:app` (phone), `:shared` (domain/API/data), `:wear` (Wear OS), `:uicommon` (shared Compose). Because commands hit real cars, **correctness of command dispatch and of the "which car / which direction" decision is safety-critical.**

Every item below was **confirmed against source at commit `b4c4831`** by an adversarial verification pass. Line numbers are 1-indexed and current as of that commit — re-locate by the described symbol/logic if a number has drifted.

## Rules of engagement

1. **Do exactly the fixes listed.** Do not refactor unrelated code, restyle, or "improve" things not in scope. If you spot something new, note it in a `FIX_NOTES.md` at the end — don't act on it.
2. **Preserve behavior comments.** This codebase documents *why* code is shaped as it is; keep comments accurate — if you change logic a comment describes, update the comment.
3. **Match surrounding style** (naming, formatting, comment density). No new dependencies.
4. **Some items say "confirm intent first"** — for those, implement the safe interpretation given and leave a `// TODO(review):` note rather than guessing at product intent.
5. **After each phase, build.** `./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin` (or `assembleDebug`). Do not proceed to the next phase with a broken build.
6. **Do not touch the refuted items** listed at the end — they were investigated and found to be non-bugs.
7. Work in **phases**, committing after each with the message shown. Keep commits scoped to one phase.

---

## PHASE 1 — Safety-critical command correctness (HIGH)

### 1.1 Wrong-car command from Settings search (substring match)
**File:** `app/src/main/java/com/bloo/bluelink/ui/Screens.kt` ~line 10117
Replace the substring name resolution `state.vehicles.firstOrNull { v -> v.name.isNotBlank() && v.name.lowercase() in submittedQuery.lowercase() }` with whole-word/token matching that prefers the **longest** matching name. If two names match equally, do **not** silently pick one — set the parsed command to require disambiguation (surface "Which car?" / no dispatch). A car command must never be sent to a car the user didn't name.

### 1.2 "stop climate" / "stop charging" inverted in Settings search
**File:** `app/.../ui/Screens.kt` ~lines 9709–9712 (`parseVehicleCommand`) and the call site ~10124.
Stop and start currently collapse to the same `cmd` (`"climate"`/`"charge"`); the runner then decides direction from the snapshot, ignoring the user's words. Introduce explicit directional commands (e.g. `"climate_off"`/`"climate_on"`, `"charge_stop"`/`"charge_start"`), thread them through `ParsedVehicleCommand` → `TileCommandRunner.run`, and handle them in `TileCommandRunner` (`app/.../data/TileCommandRunner.kt`) as explicit non-toggling actions (it already has `"lock"`/`"unlock"` as explicit variants — mirror that). Toggle-by-snapshot must only happen when the user's phrasing was itself a toggle.

### 1.3 Launcher shortcut re-fires on Activity recreation
**File:** `app/src/main/java/com/bloo/bluelink/MainActivity.kt` ~line 90.
Guard the launch-intent handling: `if (savedInstanceState == null) handleShortcutIntent(intent)`. After handling a shortcut intent, neutralize it so a later recreate can't replay it: `setIntent(Intent())` (or clear the action). The `onNewIntent` path already `setIntent`s — keep it; only the `onCreate` path is unguarded.

### 1.4 AppLog timestamp formatting outside the lock (thread-unsafe)
**File:** `shared/src/main/java/com/bloo/bluelink/data/AppLog.kt` ~line 38.
Move `val line = "${timestamp.format(Date())}  $message"` **inside** the existing `synchronized(this) { … }` block (currently only the list mutation is inside it). Update the class comment if needed. (Alternative: switch to a `ThreadLocal<SimpleDateFormat>` or `java.time.DateTimeFormatter` and drop the lock requirement — either is acceptable; the in-lock move is minimal.)

### 1.5 QS tile taps can duplicate/invert commands (no unique work + read/write outside mutex)
**Files:** `app/src/main/java/com/bloo/bluelink/tiles/TileCommandWorker.kt` ~line 49; `app/.../data/TileCommandRunner.kt` (snapshot read ~78, optimistic write ~108, mutex ~87).
Two changes:
- In `TileCommandWorker.enqueue`, use `enqueueUniqueWork` per `(vin, cmd)` with `ExistingWorkPolicy.APPEND_OR_REPLACE` (or `KEEP`) so same-car taps serialize instead of running concurrently.
- In `TileCommandRunner.run`, move the snapshot read (`SnapshotStore(ctx).current()...`) and the optimistic `updateVehicle` write **inside** the `BlueLinkGate.statusMutex.withLock { }` block so the direction decision and the flip are atomic with the network dispatch.

### 1.6 Watch tile command relay cancelled by service teardown
**File:** `wear/src/main/java/com/bloo/wear/tile/BlooTileService.kt` ~line 79 (`tileScope.cancel()`), relay launch ~197.
Do not relay the network command on the service-lifecycle `tileScope`. Launch it on a process/application-scoped `SupervisorScope` (or hand off to WorkManager) so it survives `onDestroy`. Only cancel UI/render-owning scopes in `onDestroy`. **Confirm** there isn't already an app-scope you should reuse before adding one.

**Commit:** `fix: harden car-command dispatch paths (wrong-car search, start/stop inversion, shortcut replay, tile concurrency, log thread-safety)`

---

## PHASE 2 — Command-path concurrency & state (HIGH/MEDIUM)

### 2.1 Manual refresh swallowed behind background fetch
**File:** `app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt` ~line 1030 (`loadStatus`).
The in-flight de-dupe keys on VIN only. Either key on `(vin, refresh)`, or when a `refresh=true, surfaceErrors=true` request arrives while only a `refresh=false` fetch is in flight, **upgrade** it: set `surfaceInFlight`, set `refreshing=true`, and ensure a live (`REFRESH=true`) fetch actually runs for that VIN rather than returning early. A user pull-to-refresh must always produce a spinner and a live poll.

### 2.2 statusMutex held across geocode/alerts
**File:** `app/.../ui/AppViewModel.kt` ~lines 1039–1071 (`loadStatus`).
Only `repoFor(v).status(...)` needs to be inside `statusMutex.withLock`. Capture the returned status + derived `statusLoc` inside the lock, **exit the lock**, then run `checkAlerts(v, s)` and `reverseGeocode(loc)` and the `placeNames` update outside it. Keep `persistCache`/`persistSnapshots` as they are (already off-lock via `viewModelScope.launch` where applicable).

### 2.3 Watch toggle direction read outside the mutex
**File:** `shared/src/main/java/com/bloo/bluelink/data/WearCommandRunner.kt` ~lines 26–27 vs 49.
Move the `SnapshotStore(context).current()` read + target-vehicle lookup **inside** the `BlueLinkGate.statusMutex.withLock { }` block, so the toggle-direction decision reads state serialized against other command paths. Then build repo/climate and dispatch inside the lock as today.

### 2.4 Null status marks VIN sessionFetched (kills retry-on-view)
**File:** `app/.../ui/AppViewModel.kt` ~line 1072.
Only run `sessionFetched.add(v.vin)` when a non-null status was actually received — move it inside the `?.let { s -> … }` block (or gate on `s != null`). Do not add it on the null-return path.

### 2.5 Standalone watch garage fetch outside the mutex
**File:** `wear/src/main/java/com/bloo/wear/WearViewModel.kt` ~line 644 (`loadGarage` standalone fallback).
Wrap the `repoFor(b).vehicles()` call in `BlueLinkGate.statusMutex.withLock { }`, matching the standalone command path and `loadTrips` in the same file.

### 2.6 Blocking Keystore read inside CAS retry lambda
**File:** `wear/.../WearViewModel.kt` ~line 586 (`login()` onSuccess).
Resolve the account list off-main into a `val` **before** `_ui.update { }`, then reference the `val` inside the lambda — mirror the pattern `bootstrap()` (~lines 526–531) already uses: `val emails = withContext(Dispatchers.IO) { runCatching { credentialStore.loadAll().map { it.email } }.getOrDefault(emptyList()) }`.

**Commit:** `fix: enforce status-mutex + de-dupe invariants across phone/watch command paths`

---

## PHASE 3 — Snackbar/state correctness & data integrity (HIGH/MEDIUM)

### 3.1 Sticky messageType (errors shown as success/info)
**File:** `app/.../ui/AppViewModel.kt` (`UiState.messageType`; error emitters ~346, ~1077, ~1941; `clearMessage` ~2277).
Make error/alert messages reset the type. Cleanest: bundle message+type (add a helper `private fun setError(msg: String)` that sets `message=msg, messageType="error"`, and route `runCommand` catch / `loadStatus` catch / `checkAlerts` / login+sync error paths through it). Also have `clearMessage()` reset `messageType` to `"error"`. Verify `reportInfo`/`importSettings` success still set their non-error types.

### 3.2 Drive-sync clearDirtyKeys wipes edits made mid-upload
**File:** `app/src/main/java/com/bloo/bluelink/data/SettingsStore.kt` ~line 1029 (body snapshot) and ~1061 (`clearDirtyKeys()`).
Capture the exact set of keys reflected in the uploaded body (the keys present in the `exportSettingsJson()` snapshot taken at ~1029), and on success clear **only those** from the dirty set (set-difference), instead of `clearDirtyKeys()` wiping everything. A key marked dirty after the snapshot must retain its dirty flag so a later remote import can't overwrite the un-uploaded edit.

### 3.3 locate() doesn't update lastFetched
**File:** `app/.../ui/AppViewModel.kt` ~line 1760 (`locate()` status-success branch).
When `locate()` writes the fresh status into `state.statuses`, also set `lastFetched + (v.vin to System.currentTimeMillis())`. Consider also `sessionFetched.add(v.vin)` and `checkAlerts(v, st)` to match `loadStatus`, but the timestamp is the required fix.

### 3.4 AI-support flag lost on last-account logout
**File:** `app/.../ui/AppViewModel.kt` ~line 599 (`logout` last-account branch).
Don't discard `aiSupported` when replacing state on last-account logout: preserve it into the fresh `UiState` (e.g. `UiState(screen = Screen.Login, aiSupported = _state.value.aiSupported, aiEnabled = …)`), **or** re-probe `ai.isSupported()` on login/garage entry. Preserving is simpler.

### 3.5 Background "engine running" alert never fires
**File:** `app/src/main/java/com/bloo/bluelink/work/AlertWorker.kt` ~line 71.
Add `prefs.running` to the early-exit guard: `if (!prefs.service && !prefs.doorOpen && !prefs.running) return Result.success()`.

**Commit:** `fix: message-type reset, drive-sync dirty-key retention, locate timestamp, AI probe persistence, running-alert gate`

---

## PHASE 4 — Widget / tile / complication correctness (MEDIUM)

### 4.1 clearWidgetConfig misses alpha/pill/layout
**File:** `app/.../data/SettingsStore.kt` ~line 816.
Add `"alpha"`, `"pill"`, `"layout"` to the removed-suffix list; remove the dead `"lat"`/`"lon"` entries (never written). Preferably define the per-widget suffix set as one `private val WIDGET_KEY_SUFFIXES` constant referenced by both the setters and `clearWidgetConfig` so they can't drift.

### 4.2 extrasMutex doesn't serialize the write
**Files:** `app/src/main/java/com/bloo/bluelink/wear/WearPhoneService.kt` ~287, ~314; `app/.../wear/WearBridge.kt` (`publishExtras` ~275).
Make `WearBridge.publishExtras` a `suspend` function that awaits the `putDataItem` (don't fire-and-forget on its own scope), and call it **inside** `extrasMutex.withLock` in `WearPhoneService` so the read-modify-write completes atomically. Check other callers of `publishExtras` compile against the new signature.

### 4.3 Widget h<70 tier ignores layoutMode
**File:** `app/src/main/java/com/bloo/bluelink/widget/BlooWidget.kt` ~line 296.
Change the branch to honor layout mode like the tier above: `h < 70.dp -> if (c.layoutMode == "controls") ControlsTile(c, base) else InfoTile(c, base)`.

### 4.4 ControlsTile ignores its base modifier
**File:** `app/.../widget/BlooWidget.kt` ~line 352.
Pass `base` through so the widget keeps its background/corner/pill: give `ButtonGrid` `base.padding(4.dp)` (or wrap it in a Box using `base`). Verify opaque default controls-mode tiles regain their surface.

### 4.5 Widget accent ignores themeMode + vibrancy
**File:** `app/src/main/java/com/bloo/bluelink/ui/Theme.kt` (`resolveWidgetAccent` ~153–180).
Thread `appearance.themeMode` in and compute `isDark` the way `BlooTheme` does (LIGHT→false, DARK/AMOLED→true, SYSTEM→system), and apply the same `saturate(vibrancy)` to the returned primary on every branch that `blooColorScheme` applies. Update the callers (`BlooWidget`, tiles, notifications) to pass the needed appearance fields. Keep the doc comment's "mirrors BlooTheme" claim true after the fix.

### 4.6 Periodic widget worker never cancelled
**File:** `app/src/main/java/com/bloo/bluelink/widget/BlooWidgetReceiver.kt`.
Override `onDisabled(context)` and call `WidgetRefreshWorker.cancel(context)`. Also convert the `onDeleted` DataStore write off the bare `CoroutineScope(Dispatchers.IO)` to a `goAsync()`/`PendingResult` pattern (or WorkManager) so it isn't dropped on process death.

### 4.7 Unsynced lock complication shows open padlock
**File:** `wear/src/main/java/com/bloo/wear/complication/ToggleStateComplication.kt` ~line 61.
For the unknown (`!known`) state, use a distinct neutral/unknown glyph instead of `iconRes(false)` for **both** the SHORT_TEXT and MONOCHROMATIC_IMAGE branches — or return null for MONOCHROMATIC_IMAGE when state is unknown so the slot renders empty rather than a false "unlocked".

**Commit:** `fix: widget/tile/complication rendering + config-cleanup + extras write serialization`

---

## PHASE 5 — Unit/display correctness (HIGH/MEDIUM/LOW)

- **5.1 (HIGH)** `app/.../ui/Screens.kt:8047` — wind speed: replace `"${w.windKph.toInt()} km/h"` with `StatusRow("Wind", formatSpeed(w.windKph, metric))`, deriving `metric = appearance.unitSystem == "metric"` (see `FuelPebble`).
- **5.2 (MEDIUM)** `wear/.../ui/HomeScreen.kt` — unify the temperature-unit rule across `WeatherCard` (~1443) and Climate/SmartClimate/Info (~957/998/1469). Pick one source of truth (extract a `private fun useFahrenheit(ui): Boolean` and call it from all four). Confirm whether phone-changed units should drive the watch cards; implement the consistent rule and note the decision.
- **5.3 (MEDIUM)** `app/.../data/WeatherApi.kt:46` — use `roundToInt()` instead of `.toInt()` in `tempLabel`/`feelsLikeLabel`/`highLowLabel` (fixes negatives + matches the "rounded" KDoc).
- **5.4 (LOW)** `shared/.../FormatUtils.kt:188` — pluralize: `"${d/86_400_000} day${if (d/86_400_000 != 1L) "s" else ""} ago"`.
- **5.5 (LOW)** `wear/.../ui/HomeScreen.kt:1028` — SmartClimate "±10°": either always show "±10°F", or convert the magnitude (×5/9) before appending "°C".
- **5.6 (LOW)** `wear/.../ui/HomeScreen.kt:1545` — count a tire issue once: `(anyIndividualTire || car.tireWarning)` as a single term.
- **5.7 (LOW)** `app/.../data/SettingsStore.kt:260` — `coerceIn` uiScale (0.85..1.3) and vibrancy (0.5..1.6) on read so a corrupt/foreign backup can't lock the UI.

**Commit:** `fix: unit-aware display for wind/temp + range/label correctness`

---

## PHASE 6 — Dead code, duplication, docs, config (LOW/MEDIUM cleanup)

Only after Phases 1–5 build and are committed. These are low-risk but touch many files — keep them in their own commit.

**Dead code (delete):**
- `shared/.../KiaUsaApi.kt:495` — unreachable `1 ->` branch in `seatSettings()`.
- `app/.../update/UpdateChecker.kt:9` — unused `import kotlinx.coroutines.flow.first`.
- `app/.../ui/Screens.kt:2617` — unused `val likelyCoverScreen`.
- `app/.../ui/Screens.kt:3535–3536` — unused `carIndex`/`carCount` in `CompactMainTile`.
- `app/.../widget/BlooWidget.kt` — drop unused tile-size params: `SquareTile(w)` (~502, call site ~299), `MediumTallTile(w,h)` (~476, call site ~300), `WideTile(w)` (~534, call site ~301).
- `app/build.gradle.kts:119` — delete the duplicate `androidx.core:core-ktx:1.13.1`.
- `shared/.../Models.kt:324,327` — `SeatLevel.ventilatedRange`/`heatOnlyRange` are unused (verified) — delete both, or replace bodies with `rangeFor(true,true)`/`rangeFor(false,true)` if you prefer to keep the names.

**Dead code (confirm intent, don't blind-delete):**
- `app/.../ui/Screens.kt:7212` (`showClimateChoice` dialog, ~7062 decl) — unreachable. Either delete the state + dialog, **or** wire the advanced-collapsed Start branch to open it when multiple presets exist. Leave a `// TODO(review):` and implement the delete unless there's product intent to keep the chooser.

**Duplication / framework-extraction:**
- `wear/.../WearViewModel.kt` — extract one `CLIMATE_ON` `WearCommand` builder + one `startClimateStandalone(v, repo, st, request)` (enforcing the driving gate + flip once) and call them from `toggleClimate`/`applyPreset`/`smartClimate`/`toWearCommand`.
- `wear/.../ui/Components.kt` — extract `RotaryScalingColumn(state, content)` and `BusySpinner(caption)`; route LoginScreen/SettingsScreen/TripsScreen (and WatchApp's loading state) through them.
- `app/.../ui/Screens.kt` — extract `CarThumbnail(img, size, cornerRadius)` used by `CarSettingsCard` (~9451) and `CarTilesHeader` (~10298).
- `app/.../data/SettingsStore.kt:350` — extract `private fun decodeNotificationPrefs(prefs): NotificationPrefs` used by both `notificationPrefs()` and the `notifications` Flow.
- `shared/.../CredentialStore.kt` ↔ `SessionStore.kt` — consolidate the brand-set + legacy-migration bookkeeping into one shared helper (make both `save()` paths migrate-then-merge identically). This removes the latent asymmetry behind refuted item R4.
- `shared/.../KiaRepository.kt:106` — have `location()` avoid the wasted `evc/gts` charge-target leg (read `vehicleLocation` off an already-fetched status, or add a location-only path).

**Compose/Android quality:**
- `app/.../ui/Screens.kt:383` — move `haptics.enabled = appearance.hapticsEnabled` into `SideEffect { }`.
- `app/.../ui/Screens.kt:3719` — PagerDots: run the `delay(300)+animateTo(0)` linger **before** setting `holding=false` (or drive it from a non-keyed effect) so it isn't self-cancelled.
- `app/.../data/SettingsStore.kt:233` — reuse the existing `paletteJson`/`paletteListSerializer` fields in the `appearance` Flow instead of allocating per emission.
- `app/.../tiles/TileCommandWorker.kt:33` — surface terminal failures (don't always return `Result.success()`); at minimum a follow-up toast/notification for a failed command, and `Result.retry()` for transient errors.
- `app/.../widget/WidgetCommandWorker.kt:274` — only apply the optimistic flip / pending write when the work was actually enqueued (have `enqueue()` report acceptance).
- `app/.../widget/WidgetCommandWorker.kt:223` — wrap the tile fetch in try/finally (or `.use{}`) so `HttpURLConnection.disconnect()` always runs.

**Correctness (LOW):**
- `shared/.../BlueLinkApi.kt:476` — blank-model fallback: use a brand-derived name instead of literal `"Hyundai"` (fall back to `nickName`/VIN suffix, or `brand.label`).
- `shared/.../KiaUsaApi.kt:225` — in `verifyOtpAndComplete`'s finish block, also read `resp.header("rmtoken")` and prefer it over the earlier verifyOTP token when present (mirror `authUser` ~168); fix the now-false comment in `authUser`.
- `app/.../ui/Screens.kt:5128` — gate the "trips" pebble on `state.hasBattery(v)` (or exclude "trips" from `DEFAULT_SECTIONS` for non-battery cars) so it doesn't fire the EV-only endpoint for gas/PHEV/Kia.
- `app/.../ui/Screens.kt:10024` — only `add()` the odometer search entry when the parsed `odoInt` is non-null.
- `app/.../widget/BlooWidget.kt:235` — clamp `bgAlpha` `.coerceAtLeast(0.1f)` (or fix the comment to state the true 0.0 floor).
- `app/.../widget/WidgetCommandWorker.kt:130` — build the street portion (subThoroughfare + thoroughfare) explicitly so a house number isn't dropped when the street name is blank.
- `uicommon/.../MorphSegmented.kt:167` — clear `pendingIndex` after a settle completes (not only on `selectedIndex == pendingIndex`).
- `uicommon/.../WiggleText.kt:47` — trigger the "67" easter-egg only when the trimmed text is exactly "67" (optionally with a single trailing unit char), not on any digit-concatenation.
- `wear/.../WearViewModel.kt:1816` — advance `fetchedAt` on each real data arrival (snapshot collector / standalone command / refresh) before `publish()`, so "last updated" tracks fresh data.

**Docs/config (LOW):**
- `app/.../widget/BlooWidget.kt:906` — fix the `boxBlurPass` KDoc ("three passes" → "two").
- `app/build.gradle.kts` / `wear/build.gradle.kts` — note that `proguard-rules.pro` is inert (`isMinifyEnabled=false`); if minify may ever be enabled, add a generic `-keepclassmembers class **$$serializer { *; }` to the wear rules to match the app's. (Do not enable minify as part of this pass.)
- `app/build.gradle.kts:9` — verify `compileSdk = 37` is a **finalized** platform for CI; if it's still preview, switch to `compileSdkPreview` or pin to a finalized level. (Verify before changing.)

**Commit:** `chore: remove dead code, extract shared helpers, fix docs/config nits`

---

## Do NOT touch (verified non-bugs)

These were investigated and refuted; leave them as-is:
`Screens.kt:1207` CarFeatureWizard "soft-lock"; `FormatUtils.kt:259` formatEfficiency "divide-by-zero"; `CredentialStore.kt:127` migrate "null putString"; `SessionStore.kt:154` brandsKey "drop" (the *code asymmetry* is addressed as a Phase 6 cleanup, but the claimed bug doesn't manifest); `Screens.kt:9706` "lock/unlock not in runner vocabulary"; `WearPhoneService.kt:141` "sync reports failure with nothing to upload"; phone `tiles/BlooTileService.kt:41` "blocking reads on Main"; `WearViewModel.kt:518` "blocking reads on Main at bootstrap"; `WearViewModel.kt:1442` `setAuroraColorMode` "silent drop"; wear `tile/BlooTileService.kt:488` "no isDriving gate on relay" (the gate is enforced downstream in `WearCommandRunner.execute`).

---

## Verification checklist (run before declaring done)

- [ ] `./gradlew assembleDebug` succeeds for `:app` and `:wear`.
- [ ] Phase 1 items: trace each command path once by reading the code and confirm the "which car / which direction" decision can no longer be wrong (1.1, 1.2) and can't fire twice (1.3, 1.5).
- [ ] Grep confirms deleted symbols (Phase 6 dead code) have no remaining references.
- [ ] Every comment you touched still matches the code.
- [ ] `FIX_NOTES.md` lists anything you deferred or any new issue spotted (do not fix out-of-scope items).
