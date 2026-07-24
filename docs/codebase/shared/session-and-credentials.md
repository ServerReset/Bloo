# SessionStore + CredentialStore

Deep-dive reference for the two per-brand local persistence stores in `:shared`.

- `SessionStore.kt` — `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\SessionStore.kt`
- `CredentialStore.kt` — `C:\Users\AdiPerets\Bloo\shared\src\main\java\com\bloo\bluelink\data\CredentialStore.kt`

Both live in package `com.bloo.bluelink.data` and share the `Brand` enum
(`Brand.kt`, same package) as their per-account namespace key.

---

## 1. Purpose

These two classes are the on-device persistence layer for **who is signed in**
and **how to re-authenticate them**, keyed **one entry per brand** so a Hyundai,
a Genesis, and a Kia account can all be signed in simultaneously.

- **`SessionStore`** persists the *live session* — the tokens and the service
  PIN needed to actually issue remote commands. It is the "logged-in state"
  source of truth. The service PIN is sent as a header on **every** remote
  command, which is why it is stored locally on-device (SessionStore.kt:22-25).
  Backed by **DataStore Preferences** (unencrypted; see Gotchas).

- **`CredentialStore`** persists the *raw sign-in credentials* (email, password,
  PIN) so the app can silently re-authenticate when a session token expires
  without asking the user to retype anything (CredentialStore.kt:8, 18-20).
  Backed by **`EncryptedSharedPreferences`** (AES-256 at rest).

The split reflects sensitivity and lifecycle: a session token is short-lived and
refreshable, whereas the credential set is long-lived secret material and gets
the stronger at-rest encryption. Both classes independently implement the same
two design themes: **per-brand namespacing** and **one-shot legacy migration**
from an older single-account key scheme.

---

## 2. Public surface

### SessionStore.kt

- **`class SessionStore(private val context: Context)`** (SessionStore.kt:26) —
  the store. Holds only a `Context`; all state lives in the DataStore file
  `bloo_session`.

- **`data class Session(...)`** (SessionStore.kt:28-36) — the persisted session
  payload. Fields documented in §4.

- **`suspend fun save(session: Session)`** (SessionStore.kt:54-65) — writes all
  of `session`'s fields under that brand's namespaced keys in one DataStore
  transaction, and registers the brand in the CSV brand set. Optional fields
  (`refreshToken`, `deviceId`) are only written when non-null so an update that
  omits them does not clobber the stored value with null.

- **`suspend fun load(brand: Brand): Session?`** (SessionStore.kt:74-81) — runs
  `migrateLegacy()` then reads back one brand's session. Returns `null` if any
  of the three **required** fields (access token, username, pin) is missing.
  `refresh` and `device` are read as nullable and passed straight through.

- **`suspend fun loggedInBrands(): List<Brand>`** (SessionStore.kt:88-92) — runs
  `migrateLegacy()` then parses the CSV `brands` value into `Brand` values,
  dropping any name that no longer maps to a known constant.

- **`suspend fun updateAccessToken(brand: Brand, access: String, refresh: String?)`**
  (SessionStore.kt:95-100) — rewrites just the access token (and refresh token
  if non-null) for a brand in one transaction. Used after a token refresh.

- **`suspend fun updatePin(brand: Brand, pin: String)`** (SessionStore.kt:103-105)
  — rewrites just the stored service PIN for a brand.

- **`suspend fun clear(brand: Brand)`** (SessionStore.kt:112-118) — removes every
  namespaced field for a brand and drops it from the CSV set; if that empties the
  set, removes the `brands` key entirely rather than storing `""`.

- **`suspend fun clearAll()`** (SessionStore.kt:121-123) — `p.clear()` on the
  whole DataStore; every brand signed out.

All public methods are `suspend`.

### CredentialStore.kt

- **`data class Credentials(...)`** (CredentialStore.kt:9-14) — top-level (not
  nested). Fields in §4.

- **`class CredentialStore(context: Context)`** (CredentialStore.kt:21) — the
  store. `context` is used only to build the `MasterKey` / prefs lazily; it is
  not retained as a property.

- **`fun save(credentials: Credentials)`** (CredentialStore.kt:47-57) — runs
  `migrateLegacy()`, then writes `<BRAND>_email/_password/_pin` plus adds the
  brand name to the `brands` `Set<String>`, all in one `apply()` batch.

- **`fun load(brand: Brand): Credentials?`** (CredentialStore.kt:65-72) — runs
  `migrateLegacy()`, returns `null` if any of email/password/pin is missing.

- **`fun loadAll(): List<Credentials>`** (CredentialStore.kt:81-86) — runs
  `migrateLegacy()`, maps every brand name in the `brands` set back to a `Brand`
  (skipping unknown names via `runCatching`) then to `Credentials` via `load`.

- **`fun updatePin(brand: Brand, pin: String)`** (CredentialStore.kt:89-91) —
  overwrites just `<BRAND>_pin`, leaving email/password untouched.

- **`fun clear(brand: Brand)`** (CredentialStore.kt:94-101) — removes one brand's
  three keys and drops the brand from the `brands` set (in one `apply()`).

- **`fun clearAll()`** (CredentialStore.kt:104-106) — `prefs.edit().clear()`.

None of `CredentialStore`'s methods are `suspend` — SharedPreferences reads/writes
are synchronous (`apply()` commits async to disk but the in-memory map is updated
immediately).

---

## 3. Internal structure

### SessionStore private members

- **`Context.dataStore`** (SessionStore.kt:16-19) — file-level `by
  preferencesDataStore(name = "bloo_session", corruptionHandler =
  ReplaceFileCorruptionHandler { emptyPreferences() })`. This is a top-level
  delegate, so **all** `SessionStore` instances in the process share the one
  DataStore singleton for that file (important — see §5). The corruption handler
  resets a damaged file to empty prefs (= signed out) rather than rethrowing on
  every read.

- **`fun key(brand: Brand, field: String): Preferences.Key<String>`**
  (SessionStore.kt:40) — `stringPreferencesKey("${brand.name}_$field")`, e.g.
  `key(KIA, "access")` → `"KIA_access"`. This is the namespacing primitive.

- **`val brandsKey = stringPreferencesKey("brands")`** (SessionStore.kt:44) — the
  key for the comma-joined list of logged-in brand names. DataStore Preferences
  is used here without a native string-set type, so the set is hand-rolled as
  CSV (contrast CredentialStore, which uses `putStringSet`).

- **`private suspend fun migrateLegacy()`** (SessionStore.kt:126-159) — one-shot
  migration; control flow below.

**`save` control flow** (SessionStore.kt:54-65): open one `edit{}`; write
`access`, `username`, `pin` unconditionally; write `refresh`/`device` only if
non-null; read the current `brands` CSV → split on `,` → drop blanks → to
`MutableSet` → add this brand's name → re-join with `,` and store.

**`clear` control flow** (SessionStore.kt:112-118): open one `edit{}`; `remove`
each of the five field keys for the brand; rebuild the CSV set filtering out
blanks **and** the cleared brand; if empty `remove(brandsKey)` else store the
re-joined CSV.

**`migrateLegacy` control flow** (SessionStore.kt:146-158):
1. Cheap outer read: if `stringPreferencesKey("access_token")` is absent, return
   immediately (already migrated, or never on the old scheme).
2. Otherwise open one `edit{}`. Re-read the legacy access token **from the
   transaction's own snapshot `e`**; if absent, `return@edit`.
3. `val brand = Brand.fromName(e[...("brand")])` — legacy brand name → `Brand`,
   defaulting to `HYUNDAI` when absent/unknown.
4. Copy each present legacy field (`access_token`→`access`,
   `refresh_token`→`refresh`, `username`→`username`, `pin`→`pin`) to its
   brand-namespaced key.
5. Seed `brandsKey` with just that one brand's name (legacy sessions were
   single-account).
6. `remove` all five legacy keys so `access_token` is gone and the method is a
   no-op next time.

The critical detail (SessionStore.kt:130-145 comment): step 2 onward re-reads
from `e`, **not** the outer snapshot `p` from step 1. An earlier version copied
the remaining legacy fields from the stale outer read even inside `edit{}`; that
let a second `migrateLegacy()` racing a concurrent `updateAccessToken()` re-write
a stale legacy-derived token over a freshly-updated one. Reading from `e` inside
the transaction closes that race.

### CredentialStore private members

- **`val prefs: SharedPreferences by lazy { ... }`** (CredentialStore.kt:26-37) —
  builds a `MasterKey` (`AES256_GCM` scheme) and an `EncryptedSharedPreferences`
  named `"bloo_credentials"` with key scheme `AES256_SIV` and value scheme
  `AES256_GCM`. Lazy so the (expensive) master-key generation/lookup and
  EncryptedSharedPreferences setup happen only on first credential touch, not at
  construction.

- **`fun brandSet(): Set<String>`** (CredentialStore.kt:111) —
  `prefs.getStringSet(KEY_BRANDS, emptySet()) ?: emptySet()`. Double fallback
  (default arg and `?:`) because `getStringSet` can return null.

- **`fun migrateLegacy()`** (CredentialStore.kt:122-132) — detect legacy by the
  presence of a bare `"email"` key; if absent, return. Otherwise read legacy
  `"brand"` (via `Brand.fromName`, so null/unknown → `HYUNDAI`), write the three
  fields under `<BRAND>_...` keys, seed `KEY_BRANDS` with that one brand, remove
  the four legacy keys (`email`, `password`, `pin`, `brand`), all in one
  `apply()`. Note it copies `password`/`pin` via `prefs.getString(..., null)`,
  which can write a null value (see Gotchas).

- **`companion object { const val KEY_BRANDS = "brands" }`** (CredentialStore.kt:134-138)
  — the string-set key holding brand names that currently have credentials.

---

## 4. Data & types

### `SessionStore.Session` (nested data class, SessionStore.kt:28-36)

| Field | Type | Default | Meaning / encoding |
|---|---|---|---|
| `accessToken` | `String` | — | live bearer/session token. For **Kia** this holds the `sid`. |
| `refreshToken` | `String?` | — | refresh token. For **Kia** this holds the `rmtoken`. Nullable; not all flows produce one. |
| `username` | `String` | — | account username/email. |
| `pin` | `String` | — | service PIN, sent as a header on every remote command. |
| `brand` | `Brand` | `Brand.HYUNDAI` | which account this session belongs to; drives every namespaced key. |
| `deviceId` | `String?` | `null` | **Kia US only** — the device id the `rmtoken` is bound to; must persist for refresh to work (SessionStore.kt:34). |

Persisted keys per brand: `<BRAND>_access`, `<BRAND>_refresh`, `<BRAND>_username`,
`<BRAND>_pin`, `<BRAND>_device`, plus the shared CSV key `brands`.

### `Credentials` (top-level data class, CredentialStore.kt:9-14)

| Field | Type | Default | Meaning |
|---|---|---|---|
| `email` | `String` | — | sign-in email. |
| `password` | `String` | — | sign-in password. |
| `pin` | `String` | — | service PIN. |
| `brand` | `Brand` | `Brand.HYUNDAI` | account brand. |

Persisted keys per brand: `<BRAND>_email`, `<BRAND>_password`, `<BRAND>_pin`,
plus the `Set<String>` key `brands` (`KEY_BRANDS`). Note there is **no** device
id or token here — CredentialStore stores only re-auth inputs, not session state.

### `Brand` (enum, `Brand.kt:14-72`, referenced but defined elsewhere)

Constants `HYUNDAI("H")`, `GENESIS("G")`, `KIA("K")`. Its `name` (`"HYUNDAI"`
etc.) is the namespace token both stores use. Relevant helpers these stores lean
on:
- `Brand.fromName(name: String?): Brand` (Brand.kt:62-63) — exact `name` match,
  falls back to `HYUNDAI` on null/unknown. Used by both migrations.
- `Brand.valueOf(name)` (stdlib) — used inside `runCatching` by `loggedInBrands`
  and `loadAll` to skip unknown names.
- `usesOtpLogin` (Kia) is why Kia credentials/sessions look different (sid/rmtoken
  in the token fields), but neither store special-cases it — the differing
  meanings are purely by convention at the call sites.

### Serialization details

- **SessionStore**: everything is a DataStore `String`. The brand set is CSV
  (`joinToString(",")` / `split(",")`), filtered for blanks on read.
- **CredentialStore**: strings via SharedPreferences `putString`; the brand set
  is a real `Set<String>` via `putStringSet`.

---

## 5. State & concurrency

### SessionStore

- **State container:** Jetpack **DataStore Preferences**, file `bloo_session`,
  declared as a top-level `Context.dataStore` delegate (SessionStore.kt:16-19).
  Because the delegate is defined once at file scope, **every `SessionStore`
  instance in the process shares the same underlying DataStore singleton** — this
  is what makes the migration race real, and also what makes concurrent
  `edit{}` calls from different `SessionStore` instances safe against each other.
- **Reads:** `context.dataStore.data.first()` — takes the current snapshot from
  the flow. `first()` suspends until the first emission.
- **Writes:** `context.dataStore.edit { }` — atomic read-modify-write
  transaction; DataStore serializes concurrent `edit` blocks on one file and each
  block sees a consistent snapshot. All the multi-field operations (`save`,
  `clear`, `migrateLegacy`) rely on this single-transaction atomicity.
- **Dispatcher/scope:** none of its own — all methods are `suspend` and run on the
  caller's coroutine context; DataStore does its IO on its own internal
  dispatcher. Callers (`AppViewModel`, `WearCommandRunner`, `TileCommandRunner`,
  `AlertWorker`, `WearBridge`) invoke from their own coroutine scopes.
- **No StateFlow / no `remember`** — this is not a Compose or reactive component;
  it exposes point-in-time `suspend` reads, not observable flows.

### CredentialStore

- **State container:** `EncryptedSharedPreferences` (`bloo_credentials`), built
  **lazily** (CredentialStore.kt:26-37). Each `CredentialStore` instance builds
  its own `prefs` on first use, but they all point at the same OS prefs file.
- **Reads/writes:** synchronous SharedPreferences API; writes batched per method
  and committed with `apply()` (async disk flush, immediate in-memory update).
- **Dispatcher/scope:** none — plain blocking calls; not `suspend`. Callers wrap
  them in their own coroutine/dispatcher if they want off-main-thread behavior.
- **No StateFlow / no `remember`.**

Neither class holds a lock of its own. Concurrency safety comes from DataStore's
transaction serialization (SessionStore) and SharedPreferences' internal
synchronization (CredentialStore). Note: `BlueLinkGate.statusMutex` (the
process-wide command serializer) is unrelated to these stores and does not guard
them.

---

## 6. Collaborators & data flow

**Constructed / used by** (`SessionStore(...)` / `CredentialStore(...)`):
- `app/.../ui/AppViewModel.kt` (SessionStore at :269, CredentialStore at :271) —
  the phone UI's main holder. `repositoryFor(brand, store, credentialStore)`
  (AppViewModel.kt:279) hands both stores to the per-brand repository. AppViewModel
  drives `credentialStore.loadAll()` into UI state (`accounts`), `save`, `clear`,
  `updatePin`.
- `shared/.../data/WearCommandRunner.kt` — the watch-side command executor builds
  its own stores.
- `app/.../data/TileCommandRunner.kt` — QS-tile command executor.
- `app/.../work/AlertWorker.kt` — WorkManager background polling.
- `app/.../wear/WearBridge.kt` — phone↔watch bridge.
- `wear/.../WearViewModel.kt`, `wear/.../WearStateWriter.kt` — Wear app.

Several of these construct `SessionStore` **fresh** (the comment at
SessionStore.kt:132-134 calls this out), which is exactly why the migration must
be race-safe.

**Data in:** `Session` / `Credentials` objects from login and refresh flows
(repository / API layer), `Brand` selectors.
**Data out:** `Session?`, `Credentials?`, `List<Brand>`, `List<Credentials>`.
The repository reads `SessionStore.load(brand)` to get tokens+PIN for API calls,
and calls `updateAccessToken` after a refresh; `updatePin` when the user changes
their PIN.

**Channels:** pure Kotlin function calls plus two on-disk backing files
(`bloo_session` DataStore, `bloo_credentials` EncryptedSharedPreferences). No
Wear Data Layer paths, intents, or WorkManager are used *inside* these classes;
those live in the collaborators.

---

## 7. Invariants & assumptions

- **Required-field completeness:** `load`/`Session` assumes a valid session has
  non-null access token, username, and pin; any missing → `null`
  (SessionStore.kt:77-79). Same for `Credentials` email/password/pin
  (CredentialStore.kt:68-70).
- **Brand name stability:** the CSV/set stores `Brand.name`. If an enum constant
  is renamed or removed, old stored names silently drop out (via `runCatching` /
  `mapNotNull`) rather than throwing — a deliberate forward-compat choice
  (SessionStore.kt:91, CredentialStore.kt:84).
- **Single DataStore file per process:** correctness of `save`/`clear`/migration
  relies on all `SessionStore` instances sharing one `bloo_session` DataStore
  (guaranteed by the top-level delegate) so transactions serialize.
- **Transaction snapshot freshness:** `migrateLegacy` assumes reading from the
  `edit{}` snapshot `e` (not the outer `p`) — this is load-bearing for the race
  fix (SessionStore.kt:147-153).
- **Optional fields must not be clobbered:** `save`/`updateAccessToken` assume
  that passing `null` for `refresh`/`device` means "keep existing," not "erase"
  — enforced by the `?.let` guards (SessionStore.kt:57, 60, 98).
- **Legacy detection keys:** migration assumes the *presence* of the bare
  unprefixed key (`access_token` for sessions, `email` for credentials) uniquely
  identifies an un-migrated old install, and that removing them makes the method
  idempotent.
- **Kia convention:** callers must treat `accessToken`=sid, `refreshToken`=rmtoken,
  and populate `deviceId` for Kia — the store does not enforce this.

---

## 8. Gotchas & sharp edges

- **SessionStore is NOT encrypted.** It uses plain DataStore Preferences while
  CredentialStore uses `EncryptedSharedPreferences`. So the **service PIN and
  tokens live in cleartext** in `bloo_session`, even though the same PIN is
  encrypted in `bloo_credentials`. Anyone reasoning about at-rest security must
  know the session file is the weaker one.

- **Two different "brands" set representations.** `SessionStore` uses a
  hand-rolled **CSV string** under `stringPreferencesKey("brands")`;
  `CredentialStore` uses a real **`Set<String>`** under `KEY_BRANDS = "brands"`.
  Same logical concept, same key name, but different files and different
  encodings — do not assume symmetry.

- **The migration race (the big one).** `SessionStore.migrateLegacy` re-reads
  from the transaction snapshot `e`, not the outer `p`. The extensive comment
  (SessionStore.kt:130-145) documents the historical bug: the old code copied
  remaining legacy fields from the stale outer read inside `edit{}`, so a second
  `migrateLegacy()` racing a concurrent `updateAccessToken()` could overwrite a
  freshly-refreshed token with a stale legacy-derived one. This is why every read
  after the guard uses `e`.

- **Migration runs on almost every read.** `load`, `loggedInBrands`, `loadAll`,
  and `CredentialStore.load/loadAll/save` all call `migrateLegacy()` first. It is
  cheap after the first run (SessionStore short-circuits on a single outer read;
  CredentialStore on a single `getString`), but it is not free — there is one
  extra read per call for the life of the app.

- **CredentialStore migration can write nulls.** At CredentialStore.kt:127-128 it
  does `putString("${brand}_password", prefs.getString("password", null))` — if
  the legacy password/pin were somehow absent, it writes a null value into the
  new key. `load` then treats that as "missing" and returns `null`, which is the
  safe outcome, but it is an untidy edge.

- **`clear` vs `clearAll` asymmetry.** `SessionStore.clear` carefully collapses an
  empty brand set to *removing* the key (SessionStore.kt:116) to avoid persisting
  `""`; `CredentialStore.clear` just stores the shrunken (possibly empty) set
  (CredentialStore.kt:99). Behaviourally fine, but different.

- **Refresh/device can only be added, never cleared, via the normal path.**
  Because of the `?.let` guards, once a `refresh`/`device` value is stored there
  is no `save`/`updateAccessToken` call that clears it back to null; only
  `clear(brand)` / `clearAll()` remove it. Intentional (avoids accidental
  clobbering) but means you cannot "downgrade" a session to token-only in place.

- **`CredentialStore` methods are blocking and not `suspend`** — calling them on
  the main thread does synchronous prefs IO. Callers are responsible for
  dispatching; the class gives no help.

- **Corruption resets to signed-out.** A power-loss-damaged `bloo_session` file
  silently becomes empty prefs (SessionStore.kt:11-19), i.e. a forced re-login —
  chosen deliberately over a crash loop, since every surface reads this file.
