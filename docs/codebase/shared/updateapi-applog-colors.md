# shared: `UpdateApi` + `AppLog` + `BlooColors`

Deep-dive reference for three small, independent utility units in the `:shared`
module, all in package `com.bloo.bluelink.data`:

- `UpdateApi.kt` — the app's self-update channel (GitHub Releases).
- `AppLog.kt` — an in-memory ring-buffer log surfaced in Settings.
- `BlooColors.kt` — semantic ARGB color constants shared across all surfaces.

They share a package and module but are otherwise unrelated; they are documented
together because each is too small to warrant its own file. Line citations use
`file:line`.

---

## 1. Purpose

### `UpdateApi`
Bloo is **not on the Play Store**; it self-updates from GitHub Releases
(`UpdateApi.kt:31-41`). Every ordinary push to the app's branch triggers CI
(`android.yml`) which publishes a rolling **pre-release** tagged `build-<run
number>` with the raw phone and watch APKs attached as **public** release assets
(no auth, no zip needed). `UpdateApi` is the client that:
1. queries GitHub's Releases REST API for the newest such release,
2. normalizes it into a `WorkflowRun`, and
3. streams the chosen APK to a local file with progress reporting.

`BuildConfig.BUILD_RUN_NUMBER` (baked in at CI build time) identifies the
currently-installed build; callers (`UpdateChecker`, `WearViewModel`) compare it
against `WorkflowRun.runNumber` to decide whether an update exists. The header
comment (`UpdateApi.kt:31-41`) notes this used to read **Actions artifacts**,
which required a browser + manual unzip — the switch to release assets removed
that friction.

### `AppLog`
A lightweight, in-memory, process-wide log (`AppLog.kt:10-14`) shown in Settings
so the user can copy/paste recent activity (network calls, commands, errors).
Bounded to a ring buffer of the last 500 lines. It is a diagnostics aid, not
persisted anywhere.

### `BlooColors`
A single source of truth for **semantic** color constants (`BlooColors.kt:3-10`)
used across every surface: phone app, watch app, Glance widget, QS tiles, and
watch complications. Stored as packed ARGB `Int` so that non-Compose callers
(Glance, Protolayout, `android.graphics.Color`) can use them directly; Compose
callers wrap with `Color(BlooColors.chargeGreen)`.

---

## 2. Public surface

### `UpdateApi.kt`

**`data class WorkflowRun`** (`UpdateApi.kt:19-29`) — a completed GitHub build
normalized to what the update flow needs. Public constructor properties:
- `runNumber: Int` — the build number parsed from the `build-<N>` tag.
- `htmlUrl: String` — the release's web page URL.
- `displayTitle: String? = null` — the release `name`.
- `phoneApkUrl: String? = null` — direct public download URL for `Bloo.apk`;
  null only for a stale release predating this field or if the asset upload
  failed.
- `wearApkUrl: String? = null` — same for `Bloo-Wear.apk`.
- `releaseNotes: String? = null` — the extracted changelog portion of the
  release markdown body (patch notes shown in the update tile); null for a
  release with no body.

**`object UpdateApi`** (`UpdateApi.kt:42`) — the singleton with these public
members:
- `const val DEFAULT_BRANCH = "claude/great-faraday-QuX3x"` (`UpdateApi.kt:50`)
  — the branch new builds land on. Referenced by `UpdateChecker`/`WearViewModel`.
- `suspend fun fetchLatestSuccessfulRun(branch: String): WorkflowRun?`
  (`UpdateApi.kt:92`) — queries GitHub for the newest `build-` pre-release and
  returns it as a `WorkflowRun`, or `null` on **any** failure. Runs on
  `Dispatchers.IO`. **`branch` is unused** (see §8).
- `suspend fun downloadApk(url: String, destination: File, onProgress: (Float) -> Unit): Boolean`
  (`UpdateApi.kt:167`) — streams `url` to `destination`, calling `onProgress`
  with a 0f–1f fraction as bytes arrive; returns `true` only once the file is
  fully written. Runs on `Dispatchers.IO`.

### `AppLog.kt`

**`object AppLog`** (`AppLog.kt:14`):
- `val lines: StateFlow<List<String>>` (`AppLog.kt:25`) — read-only view of the
  current log lines (newest at the end). Collected by the Settings screen.
- `fun log(message: String)` (`AppLog.kt:37`) — appends a timestamped line
  (`HH:mm:ss  <message>`) to the log, trimming to the last `MAX_LINES` (500).
  Thread-safe.
- `fun clear()` (`AppLog.kt:46`) — resets the log to empty (e.g. the Settings
  "clear" button).

### `BlooColors.kt`

**`object BlooColors`** (`BlooColors.kt:10`) — eight `const val ... : Int` ARGB
constants (`BlooColors.kt:16-23`):
- `chargeGreen = 0xFF2EBD59.toInt()` — battery/charge indicator, "good" state.
- `chargeGreenDark = 0xFF1B8A41.toInt()` — darker variant for dark backgrounds.
- `heat = 0xFFE5484D.toInt()` — heating indicator / hot-temp warning (red).
- `cool = 0xFF2E78FF.toInt()` — cooling indicator / cold temp (blue).
- `tempMid = 0xFF66BB6A.toInt()` — mid-range cabin/outside temperature (green).
- `tempHot = 0xFFFF5722.toInt()` — high-temperature alert (orange-red).
- `climateTeal = 0xFF5DA3A3.toInt()` — neutral climate-control accent (teal).
- `warn = 0xFFF5A623.toInt()` — generic warning/caution color (amber).

---

## 3. Internal structure

### `UpdateApi` private members

Constants (`UpdateApi.kt:44-47`):
- `OWNER = "ServerReset"`, `REPO = "Bloo"` — the GitHub repo coordinates.
- `PHONE_ASSET_NAME = "Bloo.apk"`, `WEAR_ASSET_NAME = "Bloo-Wear.apk"` — the
  exact asset filenames matched to pick the two APKs out of a release's assets.

`json` (`UpdateApi.kt:52`) — a `Json` configured with `ignoreUnknownKeys = true`
(drops the large unused portion of GitHub's payload) and `isLenient = true`.

`client` (`UpdateApi.kt:54-57`) — an `OkHttpClient` with a 15s connect timeout
and **20s** read timeout, tuned for the small JSON metadata response.

Private serialization DTOs:
- `@Serializable private data class ReleaseAsset` (`UpdateApi.kt:63-67`) — one
  attached file: `name` (default `""`) and `browserDownloadUrl` (JSON key
  `browser_download_url`, default `""`).
- `@Serializable private data class ReleaseResponse` (`UpdateApi.kt:72-80`) — the
  minimal slice of a GitHub Release: `tagName` (`tag_name`), `htmlUrl`
  (`html_url`), `name: String?`, `body: String?`, `draft: Boolean = false`,
  `assets: List<ReleaseAsset> = emptyList()`.

`CHANGELOG_MARKER = "### What's changed"` (`UpdateApi.kt:139`) — the literal
marker that CI's "Generate changelog" step writes between the install steps and
the real per-commit changelog.

`extractChangelog(body: String?): String?` (`UpdateApi.kt:141-155`) — extracts
just the changelog portion of the release body. Control flow:
1. Return `null` if `body` is null or blank (`:142`).
2. Find `CHANGELOG_MARKER`. If present (`marker >= 0`), take the substring
   **after** the marker; otherwise use the whole body (`:143-144`).
3. On the resulting text, line-by-line: `dropWhile` leading blank lines or a
   stray `## What's Changed` line (`:150`), `filterNot` lines starting with
   `**Full Changelog**` (`:151`) — both defensive against GitHub's own
   auto-generated artifacts from an older/hand-edited release.
4. Re-join with `\n`, `trim()`, and return `null` if the result is blank
   (`:152-154`).

`downloadClient` (`UpdateApi.kt:160-162`) — a **second** client derived from
`client` via `newBuilder()` (sharing the connection pool/dispatcher) but with a
**5-minute** read timeout, for the multi-MB APK download over slow connections.

**`fetchLatestSuccessfulRun` control flow** (`UpdateApi.kt:92-125`):
1. Wrapped in `withContext(Dispatchers.IO)` + `runCatching { ... }.getOrNull()`
   so any exception yields `null`.
2. Build URL `https://api.github.com/repos/ServerReset/Bloo/releases` with
   `per_page=5` (`:94-98`).
3. Build a GET with headers `Accept: application/vnd.github+json` and
   `X-GitHub-Api-Version: 2022-11-28` (`:99-104`).
4. Execute inside `.use { resp -> ... }` (auto-closes the response). Return
   `null` if not successful, or if the body is null (`:105-107`).
5. Decode the body as `List<ReleaseResponse>` (`:108`).
6. Pick `firstOrNull { !it.draft && it.tagName.startsWith("build-") }` — the
   newest non-draft rolling build release; return `null` if none (`:112`).
7. Parse the run number: `tagName.removePrefix("build-").toIntOrNull()`; return
   `null` if unparseable (`:113`).
8. Return `null` if `htmlUrl` is blank (`:114`).
9. Construct the `WorkflowRun`, matching `phoneApkUrl`/`wearApkUrl` by exact
   asset name and running the body through `extractChangelog` (`:115-122`).

**`downloadApk` control flow** (`UpdateApi.kt:167-197`):
1. `withContext(Dispatchers.IO)` + `runCatching { ... }.getOrDefault(false)`.
2. GET `url`, execute inside `.use`. Return `false` if unsuccessful or body null
   (`:171-173`).
3. Read `contentLength()` into `total`; create parent dirs (`:174-175`).
4. Write to a **temp file** `${destination.name}.tmp` in the same directory
   (`:180`).
5. Stream in 64 KiB chunks (`:183`); on each chunk, if `total > 0`, call
   `onProgress((written / total).coerceIn(0f, 1f))` (`:185-191`). If the server
   sends no Content-Length, progress stays at 0.
6. `tmp.renameTo(destination)` — the atomic-ish rename is the last expression, so
   its boolean result is the return value (`:194`).

### `AppLog` private members
- `MAX_LINES = 500` (`AppLog.kt:16`) — ring-buffer cap.
- `timestamp = SimpleDateFormat("HH:mm:ss", Locale.US)` (`AppLog.kt:20`) — one
  shared formatter, guarded by `synchronized(this)` because `SimpleDateFormat` is
  not thread-safe.
- `_lines = MutableStateFlow<List<String>>(emptyList())` (`AppLog.kt:24`) — the
  private backing flow; `lines` (`:25`) exposes it read-only via `asStateFlow()`.

### `BlooColors`
No private members; a flat set of `const val` ARGB `Int`s. The explanatory
comment (`BlooColors.kt:11-15`) documents the packed-ARGB layout and the
`0x...L.toInt()` narrowing trick (see §4).

---

## 4. Data & types

### `WorkflowRun` (`UpdateApi.kt:19-29`) — public, non-`@Serializable`
The normalized model handed to callers. All fields listed in §2. Note this is
**not** a serialization DTO — it is built by hand in `fetchLatestSuccessfulRun`,
not decoded. Nullable APK URLs are expected for older releases.

### `ReleaseAsset` (`UpdateApi.kt:63-67`) — private `@Serializable`
- `name: String = ""` — asset filename, matched against `PHONE_ASSET_NAME` /
  `WEAR_ASSET_NAME`.
- `browserDownloadUrl: String = ""` — JSON `browser_download_url`; the public,
  unauthenticated, unzipped download link.

### `ReleaseResponse` (`UpdateApi.kt:72-80`) — private `@Serializable`
- `tagName: String = ""` (`tag_name`) — e.g. `"build-142"`.
- `htmlUrl: String = ""` (`html_url`).
- `name: String? = null`.
- `body: String? = null` — the markdown body (install steps + changelog).
- `draft: Boolean = false`.
- `assets: List<ReleaseAsset> = emptyList()`.
All fields have defaults so a missing/partial payload still deserializes.

### `BlooColors` constants — ARGB `Int` encoding
Every constant is a packed 32-bit ARGB value: alpha in the top byte, then R, G,
B (`BlooColors.kt:11-15`). Written as an unsigned `Long` literal (`0xFF......L`
implied) and narrowed with `.toInt()` because Kotlin `Int` literals cannot
directly express values above `0x7FFFFFFF` (the sign bit). This bit layout is
exactly what `android.graphics.Color` and Compose `Color(Int)` expect, so no
conversion is needed at the call site. All eight constants use full opacity
(`0xFF` alpha).

No enums or sealed types are defined in this unit.

---

## 5. State & concurrency

### `UpdateApi`
Effectively stateless — an `object` holding only immutable config (constants and
two `OkHttpClient`s). Both public suspend functions run on `Dispatchers.IO`. No
shared mutable state, no locks. Concurrency safety comes from OkHttp (thread-safe
clients) and `runCatching`, which converts any thrown exception into a
`null`/`false` result rather than propagating. The two clients share OkHttp's
connection pool and dispatcher (`downloadClient` is built via
`client.newBuilder()`), differing only in read timeout.

### `AppLog`
Holds the log in a single `MutableStateFlow<List<String>>` (`_lines`). Mutation
happens **only** in `log()` and `clear()`.
- `log()` (`AppLog.kt:37-43`): formats the timestamp, then inside
  `synchronized(this)` computes `next = (_lines.value + line).takeLast(MAX_LINES)`
  and assigns `_lines.value = next`. A **brand-new immutable list** is built each
  call because `StateFlow` only notifies collectors on a *distinct* value
  instance — mutating in place would not emit. The `synchronized` block guards
  the whole read-modify-write and the non-thread-safe `SimpleDateFormat.format`.
- `clear()` (`AppLog.kt:46-48`): sets `_lines.value = emptyList()`. **Not**
  inside `synchronized` (see §8).

Recomposition: any Compose screen collecting `AppLog.lines` recomposes on every
new list emission.

### `BlooColors`
Compile-time constants only — no runtime state or concurrency concerns.

---

## 6. Collaborators & data flow

### `UpdateApi`
- **Callers:** `UpdateChecker` (phone) and `WearViewModel` (watch) call
  `fetchLatestSuccessfulRun(DEFAULT_BRANCH)` and `downloadApk(...)`. They compare
  `WorkflowRun.runNumber` against `BuildConfig.BUILD_RUN_NUMBER` to detect an
  update, render `releaseNotes` as patch notes in the update tile, and pick
  `phoneApkUrl` vs `wearApkUrl` for the download.
- **Outbound network:** GitHub REST — `GET /repos/ServerReset/Bloo/releases?per_page=5`
  and the asset `browser_download_url` for the APK.
- **Filesystem:** `downloadApk` writes to a `.tmp` file then renames to the caller-
  supplied `destination: File`; progress is streamed back via the `onProgress`
  callback (not a flow).
- **Upstream producer:** CI (`android.yml` — "Publish build as a GitHub Release",
  "Generate changelog", "Publish build" steps) creates the releases and the body
  format this code parses. `BuildConfig.BUILD_RUN_NUMBER` is baked in by the same
  CI build.

### `AppLog`
- **Producers:** anywhere in the app calling `AppLog.log(...)` — network layers,
  command dispatch, error handlers.
- **Consumers:** the Settings screen collects `AppLog.lines` and offers
  copy/paste + a clear button (which calls `AppLog.clear()`).
- Purely in-process; no persistence, network, DataStore, or Wear Data Layer
  involvement.

### `BlooColors`
- **Consumers:** phone Compose UI (wrapped `Color(...)`), watch Compose UI, Glance
  widgets, QS tiles, and Protolayout watch complications — the latter three use
  the raw `Int` directly. One-way dependency; `BlooColors` depends on nothing.

---

## 7. Invariants & assumptions

**`UpdateApi`:**
- Rolling build releases are tagged exactly `build-<integer>`; the integer suffix
  after `build-` must parse via `toIntOrNull` or the release is skipped
  (`:113`).
- The two APKs are attached with the **exact** filenames `Bloo.apk` and
  `Bloo-Wear.apk` — matching is by strict `==`, so a rename in CI silently yields
  `null` URLs (`:119-120`).
- Every build release is a **pre-release**, so the `/releases` list (which
  includes pre-releases) is used, **not** `/releases/latest` (which excludes
  them) (`:82-91`).
- The list is newest-first by creation date, so `firstOrNull` gets the latest.
- `per_page=5` is assumed enough to reach the newest non-draft `build-` release
  past any interleaved `vN` tagged releases or drafts.
- The changelog format: CI writes install steps, then the `### What's changed`
  marker, then a per-commit changelog. `extractChangelog` assumes the marker
  string is exactly `"### What's changed"` (case-sensitive).
- `downloadApk` assumes the caller's `destination.parentFile` is writable; it
  creates it if missing.

**`AppLog`:**
- `MAX_LINES` bounds memory; lines are FIFO (oldest dropped first).
- Collectors tolerate whole-list replacement on each append.

**`BlooColors`:**
- Callers expect full-opacity ARGB `Int` in Compose/`android.graphics.Color`
  layout; no assumption is made about color space beyond sRGB.

---

## 8. Gotchas & sharp edges

**`UpdateApi`:**
- **`branch` parameter is dead** (`UpdateApi.kt:82-91`). GitHub's release list
  has no server-side branch filter (a release ties to a tag, not a source
  branch), and the repo only pushes one branch at a time. It is kept in the
  signature purely so callers need no change — passing `DEFAULT_BRANCH` is
  cosmetic.
- **Silent failure everywhere.** Both public functions swallow *all* exceptions
  via `runCatching` and return `null`/`false`. A network error, a JSON change, or
  a renamed asset produces no log line and no exception — callers only see a null
  result. (Notably `UpdateApi` does **not** call `AppLog.log`.)
- **`extractChangelog` history** (`UpdateApi.kt:127-155`). CI originally used
  GitHub's `generate_release_notes`, which only lists merged PRs; since the repo
  pushes straight to a branch with no PRs, that produced an empty "What's
  changed" section (just a trailing compare-link). CI now writes its own
  per-commit `git log` after the marker. The update tile shows its own install-
  steps card, so the body before the marker (duplicate install steps + a raw
  unrendered `###` header) is deliberately dropped — only text after the marker
  is used. The `dropWhile`/`filterNot` (`:150-151`) defensively strip GitHub's
  legacy `## What's Changed` / `**Full Changelog**` artifacts from old releases.
- **Marker case mismatch.** `CHANGELOG_MARKER` is `"### What's changed"`
  (lowercase "changed"), while the defensive `dropWhile` strips
  `"## What's Changed"` (capital "Changed", two hashes) — these are intentionally
  *different* strings targeting the CI marker vs. GitHub's generated heading.
- **Temp-file-then-rename** (`UpdateApi.kt:176-194`). Downloads write to
  `<name>.tmp` then `renameTo(destination)` so a cancelled/failed download never
  leaves a truncated, uninstallable APK masquerading as a good one. The return
  value **is** `renameTo`'s boolean — if the rename fails (e.g. cross-device),
  `downloadApk` returns `false` even though bytes were written.
- **Progress can stay at 0.** If the server omits Content-Length, `total <= 0`
  and `onProgress` is never called with a real fraction (`:190`).
- **Two clients, one pool.** `downloadClient` derives from `client` via
  `newBuilder()` so they share the connection pool/dispatcher; only the read
  timeout differs (20s metadata vs. 5min APK).

**`AppLog`:**
- **`clear()` is not synchronized** (`AppLog.kt:46-48`) while `log()` is. A
  `clear()` racing a concurrent `log()` could be immediately overwritten by the
  `log()`'s read-modify-write (which captured `_lines.value` before the clear),
  effectively losing the clear. Low-impact for a diagnostics log but a genuine
  race.
- **O(n) per append.** Each `log()` allocates a full new list (`_lines.value +
  line`) then `takeLast(500)` — copying up to ~500 strings on every call. Fine at
  this scale, but not a true circular buffer despite the "ring buffer" wording.
- **Timestamp only, no date.** Format is `HH:mm:ss` (`:20`) — lines crossing
  midnight are ambiguous, and there is no absolute date anywhere.

**`BlooColors`:**
- **`.toInt()` narrowing is mandatory** (`BlooColors.kt:11-15`). Any value with
  the top (alpha) bit set exceeds `Int.MAX_VALUE` as a literal, so it must be
  written as a `Long` (`0xFF......`) and narrowed. Dropping `.toInt()` or writing
  a bare `Int` literal `> 0x7FFFFFFF` is a compile error.
- **Two greens, distinct roles.** `chargeGreen`/`chargeGreenDark` are the
  charge-state greens; `tempMid` is a *different* green for temperature — do not
  substitute one for the other.
- **Semantic, not raw palette.** Names encode meaning (`heat`, `cool`, `warn`),
  so reusing a constant for an unrelated purpose couples surfaces to the wrong
  semantic and breaks the single-source-of-truth intent.
