# app: Background Workers + Update Checker

Deep-dive reference for the `:app` background-work unit: the three periodic
WorkManager jobs (`DriveSyncWorker`, `AlertWorker`, `UpdateCheckWorker`), the
update-check orchestration (`UpdateChecker`), and the DataStore that backs the
update checker's debounce/snooze/notify bookkeeping (`UpdateStore`).

Files covered:

- `app/src/main/java/com/bloo/bluelink/work/DriveSyncWorker.kt`
- `app/src/main/java/com/bloo/bluelink/work/AlertWorker.kt`
- `app/src/main/java/com/bloo/bluelink/work/UpdateCheckWorker.kt`
- `app/src/main/java/com/bloo/bluelink/update/UpdateChecker.kt`
- `app/src/main/java/com/bloo/bluelink/data/UpdateStore.kt`

> Note: `WidgetRefreshWorker` is also registered from `MainActivity` alongside
> these three (`MainActivity.kt:74`) but lives in a different unit — it is
> referenced here only as a collaborator (the 15-minute widget-refresh job that
> `AlertWorker` deliberately front-runs).

---

## 1. Purpose

Bloo is a phone "hub" that fans data out to widgets, Quick Settings tiles, and a
Wear OS watch. Much of its value depends on things happening **while the app is
closed**. This unit is the set of background jobs that keep the hub alive when
no `AppViewModel` is running to do the work reactively:

- **`DriveSyncWorker`** (every 2h) — runs the Google Drive settings-sync pass in
  the background so a settings change made on another device propagates here (and
  vice versa) even if the app is never foregrounded. Previously sync only ran
  when the app was open and a status refresh settled.
- **`AlertWorker`** (every 30 min) — refreshes each signed-in car's status and
  posts service-due / door-open notifications when the app is closed. Doubles as
  a general data-refresh that fans out to widgets/tiles/watch.
- **`UpdateCheckWorker`** (every ~12h) — Bloo isn't on the Play Store; it
  self-updates from GitHub Actions builds. This job checks for a newer CI build
  while the app is closed and posts a "update available" notification. The
  in-app equivalent (`AppViewModel.checkForUpdate`) covers the app-is-open case.
- **`UpdateChecker`** — the shared orchestration of "is there a newer build than
  what's installed," with debounce and snooze gates. Used by both the worker and
  the in-app foreground check.
- **`UpdateStore`** — a small preferences DataStore holding the update checker's
  `lastCheckedAt`, `snoozeUntil`, and `lastNotifiedRun` bookkeeping.

All three workers are registered from `MainActivity.onCreate` (`MainActivity.kt:71,77,81`)
on every app launch, each via `enqueueUniquePeriodicWork(..., KEEP, ...)` so
re-registration is idempotent.

---

## 2. Public surface

### DriveSyncWorker.kt

- **`class DriveSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params)`** (`:29`)
  Periodic Drive settings-sync job.
  - **`override suspend fun doWork(): Result`** (`:57`) — the WorkManager entry
    point. Cheap early exit if Drive sync isn't configured, runs the sync, pushes
    imported settings to watch+widget, retries on error, else succeeds. See §3.
  - **`companion object`**
    - **`fun schedule(context: Context)`** (`:93`) — registers the 2-hour periodic
      job under unique name `"bloo_drive_sync"` with `NetworkType.CONNECTED`
      constraint and explicit 1-minute exponential backoff, policy `KEEP`.

### AlertWorker.kt

- **`class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)`** (`:24`)
  Periodic status-poll + alert-notification job.
  - **`override suspend fun doWork(): Result`** (`:67`) — early-exits unless a
    notify-worthy alert type is enabled, then iterates logged-in brands/vehicles,
    fetches status under the app-wide gate, evaluates+posts alerts, and fans out
    to all surfaces. Always returns `Result.success()`. See §3.
  - **`companion object`**
    - **`fun schedule(context: Context)`** (`:107`) — registers the 30-minute
      periodic job under unique name `"bloo_alerts"`, **no** constraints, policy
      `KEEP`.

### UpdateCheckWorker.kt

- **`class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)`** (`:24`)
  Periodic self-update check job.
  - **`override suspend fun doWork(): Result`** (`:53`) — delegates to
    `UpdateChecker.checkPhone(force=false)`, and if an update is available and
    newer than the last-notified build, records+posts a notification. See §3.
  - **`companion object`**
    - **`fun schedule(context: Context)`** (`:85`) — registers the 12-hour
      periodic job under unique name `"bloo_update_check"` with
      `NetworkType.CONNECTED` constraint, policy `KEEP`.
    - `private const val NAME = "bloo_update_check"` (`:74`)
    - `private const val NOTIF_ID = 90210` (`:75`) — fixed notification id,
      distinct from per-VIN alert ids so a second update notification replaces
      the first rather than stacking.

### UpdateChecker.kt

- **`data class UpdateInfo(val run: WorkflowRun)`** (`:12`) — wrapper for a
  discovered newer CI build.
- **`sealed class UpdateCheckResult`** (`:15`) — result of a check attempt:
  - **`data class Available(val info: UpdateInfo) : UpdateCheckResult()`** (`:17`)
  - **`data object UpToDate : UpdateCheckResult()`** (`:19`)
  - **`data class Failed(val error: String?) : UpdateCheckResult()`** (`:21`)
- **`object UpdateChecker`** (`:32`)
  - **`suspend fun checkPhone(context: Context, force: Boolean = false): UpdateCheckResult`** (`:62`)
    — the core "is there a newer build" logic with debounce/snooze gates. See §3.
  - **`suspend fun snooze(context: Context)`** (`:87`) — stamps
    `now + UPDATE_SNOOZE_MS` into `UpdateStore.setSnoozeUntil`; makes future
    non-forced checks short-circuit to `UpToDate` until that time passes.

### UpdateStore.kt

- **`class UpdateStore(private val context: Context)`** (`:28`) — thin
  suspend accessor over a per-app preferences DataStore named `"bloo_update"`.
  - **`suspend fun lastCheckedAt(): Long`** (`:34`) — last check timestamp, `0L` default.
  - **`suspend fun setLastCheckedAt(millis: Long)`** (`:37`)
  - **`suspend fun snoozeUntil(): Long`** (`:41`) — active snooze deadline, `0L` default.
  - **`suspend fun setSnoozeUntil(millis: Long)`** (`:44`)
  - **`suspend fun lastNotifiedRun(): Int`** (`:51`) — CI run number last notified about, `0` default.
  - **`suspend fun setLastNotifiedRun(runNumber: Int)`** (`:53`)

---

## 3. Internal structure & control flow

### DriveSyncWorker.doWork() (`:57–83`)

1. `store = SettingsStore(applicationContext)` (`:59`).
2. **Early exit** (`:60`): `if (store.syncUri() == null) return Result.success()`.
   Sync not configured → no-op tick. The periodic job stays registered so it's
   ready the moment sync *is* configured; each tick is cheap until then.
3. `outcome = runCatching { store.performDriveSync() }.getOrNull()` (`:61`). Any
   thrown exception is swallowed to `null` and treated the same as a null-outcome
   below (silently succeed this run, no retry — because the failure mode is
   unknown and retry may not help).
4. **Imported-settings fan-out** (`:62–69`): if `outcome?.imported == true`,
   `runCatching { WearBridge.publishSettingsNow(ctx, store.appearance.first()) }`
   and `runCatching { BlooWidget().updateAll(ctx) }`. Each is independently
   wrapped so one failing doesn't skip the other. This is needed because the
   worker can run with the app process dead — no live ViewModel to pick up the
   DataStore change reactively.
5. **Error → retry** (`:70–81`): if `outcome?.error != null`, log
   `"⚠ Background Drive sync: ${outcome.error}"` and return `Result.retry()`
   (uses the 1-minute exponential backoff set in `schedule`, so transient failures
   recover in minutes rather than waiting up to 2h for the next periodic tick).
6. Else `return Result.success()` (`:82`).

### DriveSyncWorker.schedule() (`:93–119`)

- Builds a `PeriodicWorkRequestBuilder<DriveSyncWorker>(2, TimeUnit.HOURS)` (`:94`).
- Constraint `setRequiredNetworkType(NetworkType.CONNECTED)` (`:102–104`) — "any
  network," deliberately the loosest possible; the Wi-Fi-only *preference* is
  re-checked inside `performDriveSync()` each run, not at the WorkManager level.
- `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)` (`:108`) —
  explicit so a transient failure retries within a minute, not the platform
  default's much longer first step.
- `enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)` (`:114`).

### AlertWorker.doWork() (`:67–94`)

1. `store = SessionStore(applicationContext)`, `settings = SettingsStore(applicationContext)`,
   `prefs = settings.notificationPrefs()` (`:68–70`).
2. **Early exit** (`:71`): `if (!prefs.service && !prefs.doorOpen) return Result.success()`.
   Note: the "running" (engine-left-on) alert type is *not* checked here, so a
   "running"-only preference is only evaluated as a side effect of service or
   door-open also being enabled (see §8).
3. **Per-brand loop** (`:73`): over `store.loggedInBrands()`.
   - `repo = runCatching { repositoryFor(brand, store, CredentialStore(applicationContext)) }.getOrNull() ?: continue` (`:74`)
     — a brand whose repo can't be built (bad/missing credentials) is skipped, not
     fatal to the whole run.
   - `vehicles = runCatching { BlueLinkGate.statusMutex.withLock { repo.vehicles() } }.getOrElse { emptyList() }` (`:77–78`)
     — the vehicle-list fetch takes the **app-wide status gate** and degrades to
     an empty list on failure.
   - **Per-vehicle loop** (`:79`):
     - `status = runCatching { BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) } }.getOrNull()` (`:80–82`)
       — `refresh = false` accepts a cached/last-known status rather than forcing
       a fresh poll of the car; again under the gate; degrades to `null`.
     - `runCatching { CarAlerts.evaluate(settings, v, status).forEach { Notifications.post(applicationContext, it.id, it.title, it.text, it.actions) } }` (`:83–87`)
       — evaluate against the (possibly null) status and post each returned alert.
4. **Fan-out** (`:92`): `WearBridge.refreshAllSurfaces(applicationContext)` once,
   unconditionally, after all brands/vehicles. The 30-min poll doubles as a data
   refresh so widgets/tiles/watch don't wait for the 15-min `WidgetRefreshWorker`.
5. Always `return Result.success()` (`:93`). No retry path.

### AlertWorker.schedule() (`:107–114`)

- `PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES).build()` (`:108`).
  **No constraints** — offline fetches are per-item `runCatching`-swallowed and
  simply yield no alerts, so there's no need to gate the schedule on connectivity.
- `enqueueUniquePeriodicWork("bloo_alerts", KEEP, request)` (`:109–113`).

### UpdateCheckWorker.doWork() (`:53–71`)

1. `result = UpdateChecker.checkPhone(ctx, force = false)` (`:55`) — subject to
   the checker's own debounce/snooze, so a recent foreground check or an active
   snooze makes this tick a no-op even after 12h.
2. `if (result !is UpdateCheckResult.Available) return Result.success()` (`:56`) —
   up-to-date or failed → post nothing (a failed check quietly waits for the next tick).
3. `run = result.info.run` (`:57`); `store = UpdateStore(ctx)` (`:58`).
4. **De-dup guard** (`:61`): `if (run.runNumber <= store.lastNotifiedRun()) return Result.success()`
   — don't re-notify about a build (or older) we already notified about.
5. `store.setLastNotifiedRun(run.runNumber)` (`:62`) — recorded **before** posting,
   so a crash between recording and posting under-notifies rather than
   over-notifies on retry (safer direction for a "don't nag" guard).
6. `Notifications.post(ctx, id = NOTIF_ID, title = "Bloo update available", text = ...)` (`:63–69`)
   — body prefers `run.displayTitle?.takeIf { it.isNotBlank() }` else `"Build #${run.runNumber}"`,
   suffixed with `" — open Bloo to download and install."`.
7. `return Result.success()` (`:70`).

### UpdateCheckWorker.schedule() (`:85–94`)

- `PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)` with
  `NetworkType.CONNECTED` (`:86–88`); `enqueueUniquePeriodicWork(NAME, KEEP, request)` (`:89`).

### UpdateChecker.checkPhone() (`:62–77`)

Order of early-return gates, each skipping the network call:

1. `if (BuildConfig.BUILD_RUN_NUMBER <= 0) return UpToDate` (`:63`) — a local/debug
   build not stamped by CI has no baseline; never flag it as outdated.
2. `store = UpdateStore(context)`; `now = System.currentTimeMillis()` (`:64–65`).
3. **Debounce** (`:67`): `if (!force && now - store.lastCheckedAt() < 60_000L) return UpToDate`.
   `force` bypasses this (user-initiated "check now" always runs).
4. **Snooze** (`:69`): `if (!force && now < store.snoozeUntil()) return UpToDate`.
5. `branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }` (`:71`)
   — falls back to `UpdateApi.DEFAULT_BRANCH` (`"claude/great-faraday-QuX3x"`,
   `UpdateApi.kt:50`) if this build recorded no branch.
6. `run = UpdateApi.fetchLatestSuccessfulRun(branch)` (`:72`).
7. `store.setLastCheckedAt(now)` (`:73`) — recorded **unconditionally**,
   regardless of fetch success, so the 1-minute debounce applies even after a
   failure (prevents tight retry loops).
8. `if (run == null) return Failed("Could not reach GitHub (rate limited or offline)")` (`:74`).
9. `if (run.runNumber <= BuildConfig.BUILD_RUN_NUMBER) return UpToDate` (`:75`).
10. `return Available(UpdateInfo(run))` (`:76`) — strictly-greater run number is a
    real update.

### UpdateChecker.snooze() (`:87–89`)

`UpdateStore(context).setSnoozeUntil(System.currentTimeMillis() + UPDATE_SNOOZE_MS)`.
`UPDATE_SNOOZE_MS = 3 days = 259,200,000 ms` (`FormatUtils.kt:92`). Does not
cancel/reschedule anything — purely a timestamp future non-forced checks compare against.

---

## 4. Data & types

### DriveSyncWorker
No own types. Consumes `SettingsStore.DriveSyncOutcome` (`SettingsStore.kt:941`):
- `ran: Boolean` — false when sync not configured or skipped (Wi-Fi-only, not on Wi-Fi).
- `imported: Boolean` — true if a newer remote file was found and imported.
- `uploaded: Boolean` — true if this device's settings uploaded successfully.
- `syncedAtMs: Long` — timestamp recorded as last-sync (unchanged if `!ran`).
- `error: String? = null` — user-facing failure reason, or null (not-configured is not a failure).

The worker only reads `.imported` and `.error`.

### AlertWorker
No own types. Consumes: `SettingsStore.notificationPrefs()` (reads `.service`,
`.doorOpen`), `SessionStore.loggedInBrands()` (a brand collection), the repo's
`Vehicle` list, `VehicleStatus?`, and `CarAlerts.evaluate(...)`'s returned
`List<Alert>` where each `Alert` has `.id`, `.title`, `.text`, `.actions`.

### UpdateCheckWorker
Constants `NAME` and `NOTIF_ID = 90210` (§2). No data classes.

### UpdateChecker.kt

- **`UpdateInfo(run: WorkflowRun)`** — single field wrapping the discovered run.
- **`UpdateCheckResult`** sealed class with three cases: `Available(info: UpdateInfo)`,
  `UpToDate` (data object, singleton), `Failed(error: String?)`.
- **`WorkflowRun`** (defined in `shared/.../UpdateApi.kt:19`, consumed here):
  `runNumber: Int`, `htmlUrl: String`, `displayTitle: String? = null`,
  `phoneApkUrl: String? = null`, `wearApkUrl: String? = null`,
  `releaseNotes: String? = null`. The worker/checker read `runNumber` and
  `displayTitle`.

### UpdateStore.kt — DataStore keys

DataStore name: `"bloo_update"` (`:16`). Preference keys (`:30–32`):
- `keyLastCheckedAt = longPreferencesKey("last_checked_at")` — default `0L`.
- `keySnoozeUntil = longPreferencesKey("snooze_until")` — default `0L`.
- `keyLastNotifiedRun = intPreferencesKey("last_notified_run")` — default `0`.

All getters read via `context.updateDataStore.data.first()[key] ?: default`;
all setters via `context.updateDataStore.edit { it[key] = value }`.

---

## 5. State & concurrency

- **Workers hold no instance state.** Each `doWork()` constructs fresh
  `SettingsStore` / `SessionStore` / `CredentialStore` / `UpdateStore` from
  `applicationContext`. `CoroutineWorker.doWork` runs on WorkManager's coroutine
  dispatcher (`Dispatchers.Default`-backed by default); the stores/repos push
  their own blocking I/O to `Dispatchers.IO` internally.
- **`UpdateStore` state** lives entirely in the `updateDataStore` preferences
  DataStore, a `Context` extension delegate (`:15`) → one instance per process.
  A `ReplaceFileCorruptionHandler { emptyPreferences() }` (`:17`) resets a
  file damaged by an interrupted write / power loss to empty prefs instead of
  rethrowing out of every read. All accessors are `suspend`.
- **`UpdateChecker` is a stateless `object`**; its only mutable state is what it
  reads/writes through `UpdateStore`. No locks of its own.
- **The app-wide status gate** is the critical concurrency point: `AlertWorker`
  wraps every `repo.vehicles()` and `repo.status(...)` in
  `BlueLinkGate.statusMutex.withLock { ... }` (`AlertWorker.kt:77,81`) — the same
  process-wide mutex the foreground app takes — so a background poll can never
  race a live app session and trigger the backend's 502-on-overlapping-requests.
- **`performDriveSync` serialization** is handled inside `SettingsStore` by its
  own `driveSyncMutex` (`SettingsStore.kt:44,967`), not by the worker; the worker
  just calls `performDriveSync()`.
- **No recomposition** here — these are background workers, not Compose. The
  worker instead *drives* recomposition on other surfaces indirectly by pushing
  DataStore/Wear-DataItem changes (`WearBridge.publishSettingsNow`,
  `refreshAllSurfaces`, `BlooWidget().updateAll`).

---

## 6. Collaborators & data flow

Registered by: **`MainActivity.onCreate`** calls `AlertWorker.schedule` (`:71`),
`DriveSyncWorker.schedule` (`:77`), `UpdateCheckWorker.schedule` (`:81`) on every
launch. Also `WidgetRefreshWorker.schedule` (`:74`, separate unit).

**DriveSyncWorker** →
- `SettingsStore.syncUri()`, `SettingsStore.performDriveSync()`, `store.appearance.first()`.
- `WearBridge.publishSettingsNow(ctx, appearance)` → pushes appearance settings
  over the Wear Data Layer (`settings` DataItem path).
- `BlooWidget().updateAll(ctx)` (Glance) → refreshes home-screen widgets.
- `AppLog.log(...)` for the error path.
- Returns to WorkManager: `success`/`retry`.

**AlertWorker** →
- `SessionStore.loggedInBrands()`, `CredentialStore`, `repositoryFor(brand, ...)`.
- `BlueLinkGate.statusMutex` (shared lock) guarding `repo.vehicles()` / `repo.status(v, refresh=false)`.
- `SettingsStore.notificationPrefs()`.
- `CarAlerts.evaluate(settings, v, status)` (`Notifications.kt:162`) → produces `List<Alert>`.
- `Notifications.post(ctx, id, title, text, actions)` (`Notifications.kt:92`) → system notifications.
- `WearBridge.refreshAllSurfaces(ctx)` (`WearBridge.kt:84`) → fans out to watch,
  widgets, tiles.

**UpdateCheckWorker** →
- `UpdateChecker.checkPhone(ctx, force=false)`.
- `UpdateStore.lastNotifiedRun()` / `setLastNotifiedRun(...)`.
- `Notifications.post(...)` with fixed id `90210`.

**UpdateChecker** →
- `BuildConfig.BUILD_RUN_NUMBER`, `BuildConfig.BUILD_BRANCH` (baked in at CI build
  time — `app/build.gradle.kts:23,29` from `GITHUB_RUN_NUMBER` / `GITHUB_REF_NAME`).
- `UpdateStore` (lastCheckedAt, snoozeUntil).
- `UpdateApi.fetchLatestSuccessfulRun(branch)` (`shared/.../UpdateApi.kt:92`) →
  `WorkflowRun?`; `UpdateApi.DEFAULT_BRANCH`.
- `UPDATE_SNOOZE_MS` (`FormatUtils.kt:92`).
- Also called in-app by `AppViewModel.checkForUpdate` (cold start + every refresh)
  — the app-is-open counterpart.

**UpdateStore** → the `"bloo_update"` preferences DataStore only.

Data leaving this unit: system notifications, Wear DataItems, Glance widget
updates, `AppLog` entries. Data entering: DataStore reads, `BuildConfig`
constants, GitHub API responses (via `UpdateApi`), live telematics status (via repos).

---

## 7. Invariants & assumptions

- **`enqueueUniquePeriodicWork` + `KEEP`** across all three: re-calling `schedule`
  on every app start must NOT reset the periodic timer or cancel an in-flight run.
  This is the reason `KEEP` (not `REPLACE`/`UPDATE`) is used everywhere.
- **`BlueLinkGate.statusMutex` is the sole cross-process serialization** for
  status/command calls; `AlertWorker` assumes taking it is sufficient to avoid
  502s. It must be the *same* mutex instance the foreground app uses.
- **`refresh = false`** in `AlertWorker` assumes a cached/last-known status is
  acceptable for alert evaluation — it does not force a live poll of the car.
- **`CarAlerts.evaluate` treats `null` status as "poll failed, don't guess"** —
  the worker relies on this to safely pass through swallowed-failure nulls.
- **`UpdateChecker` treats `BUILD_RUN_NUMBER <= 0` as a local build** and never
  flags it outdated; assumes CI always stamps a positive run number.
- **`lastNotifiedRun` de-dup** assumes run numbers are monotonically increasing on
  the branch (a strictly-greater run = a genuinely newer build).
- **`setLastCheckedAt(now)` is recorded unconditionally** (even on fetch failure)
  — the debounce invariant depends on this to avoid tight retry loops.
- **DriveSyncWorker assumes the app process may be dead**, hence the explicit
  watch/widget fan-out after an import (a live ViewModel would otherwise do it).
- **`syncUri() == null` ⇔ sync not configured** — the DriveSyncWorker early-exit
  invariant. The Wi-Fi-only check is *not* here; it lives in `performDriveSync`.

---

## 8. Gotchas & sharp edges

- **AlertWorker's "running" alert type is unreachable on its own.** The early
  exit at `AlertWorker.kt:71` only checks `prefs.service` and `prefs.doorOpen`.
  `CarAlerts.evaluate` also handles the engine-left-on "running" alert, but if a
  user enabled *only* "running," the worker returns before the loop and never
  evaluates it — "running" is effectively a side effect of service or door-open
  being enabled (documented at `:33–37`).
- **AlertWorker never retries.** Every failure is per-item swallowed
  (`runCatching`/`getOrElse`/`getOrNull`); one bad car or brand never fails the
  run, and there's no `Result.retry()` path — it just runs again next tick.
- **DriveSyncWorker swallows sync exceptions to a null outcome**, which is treated
  as success-without-retry. Only a *reported* `outcome.error` (a structured
  failure from `performDriveSync`) triggers `Result.retry()`. A thrown exception
  does not retry.
- **The two fan-out `runCatching`s in DriveSyncWorker are independent** (`:67,68`)
  so a `publishSettingsNow` failure doesn't skip the widget update, and vice versa.
- **`NOTIF_ID = 90210` is a fixed, arbitrary id** deliberately distinct from
  per-VIN alert ids (`AlertWorker`'s notifications use per-alert ids). A second
  update notification *replaces* the first rather than stacking.
- **`setLastNotifiedRun` before `Notifications.post`** (`UpdateCheckWorker.kt:62`
  before `:63`) is intentional: under-notify-on-crash beats over-notify (nag).
- **The 12h worker cadence is a ceiling, not a floor.** `checkPhone(force=false)`
  applies its own 1-minute debounce and snooze window, so a periodic tick can be
  a complete no-op even though 12h elapsed (a recent foreground check or an active
  "remind me in 3 days" snooze suppresses it).
- **`force=true` bypasses debounce AND snooze** but not the `BUILD_RUN_NUMBER <= 0`
  guard — a local build can never be flagged outdated even by a forced check.
- **Snooze does nothing active.** `UpdateChecker.snooze` only writes a future
  timestamp; it cancels/reschedules no work. The suppression is entirely by the
  `now < snoozeUntil()` comparison in `checkPhone`.
- **`DEFAULT_BRANCH` is a hardcoded ephemeral-looking branch name**
  (`"claude/great-faraday-QuX3x"`, `UpdateApi.kt:50`). A build with a blank
  `BUILD_BRANCH` (e.g. local build) falls back to checking *that* branch's runs.
- **`DriveSyncWorker`'s network constraint is deliberately loose** (`CONNECTED`,
  any network) so the worker is scheduled as often as intended; the Wi-Fi-only
  user preference is honored one layer deeper, inside `performDriveSync()`.
- **`UpdateStore` corruption resets silently to empty prefs** — a corrupted file
  means `lastNotifiedRun`/`snoozeUntil` reset to defaults, which can cause one
  re-notify or lose an active snooze. Chosen over crashing on every read.
