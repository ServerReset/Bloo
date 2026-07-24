# app: BlooWidget (Glance widget rendering)

**File:** `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\widget\BlooWidget.kt` (972 lines)

---

## 1. Purpose

`BlooWidget` is the single Glance `GlanceAppWidget` that renders **every** Bloo
home-screen widget instance, at every size from a 1×1 tile to a 5×5 panel. One
class, one `provideGlance` entry point, and a family of size-specific "tier"
composables that each pick their own layout from the current pixel size.

It exists to give the user an at-a-glance battery/fuel readout plus **chunky,
state-reactive action buttons** (lock, climate, charge, refresh, locate, open)
directly on the launcher, without opening the app. Two per-widget options layer
on top: a **full-bleed, genuinely-blurred car photo background** and a **live
location/map box** on large sizes.

**Mechanical context — how Glance works (from the class doc, `:62`):** Glance
composables are NOT normal Jetpack Compose. When `provideGlance` runs, Glance
walks the composition tree and translates each node into an Android
`RemoteViews` tree — the cross-process view hierarchy inflated and drawn by the
*launcher* process. This app's process is not running while the widget sits
idle. Consequences that shape this entire file:

- No custom Canvas, no live blur/gradient primitive, no arbitrary animation,
  only the layout primitives that have a `RemoteViews` equivalent, and a hard
  limit on nested-view count.
- Every render is a fresh one-shot conversion; there is no persistent Composer
  holding state between updates. Anything that must persist between widget
  updates (pending-action flag, cached bitmaps, chosen config) lives in
  `SettingsStore`/`SnapshotStore` or the in-process caches in this file, and is
  **re-read at the top of every `provideGlance` call**.
- Clicks bake a `PendingIntent` into the RemoteViews — either
  `actionStartActivity` (open app / biometric gate) or `actionRunCallback`
  (silent background action that briefly wakes the process). See §6 Click
  routing.

---

## 2. Public surface

The class is intentionally lean at the top level; nearly everything is private.

### `class BlooWidget : GlanceAppWidget()` (`:102`)
The widget provider. Registered in the manifest (elsewhere) against the widget
receiver.

- **`override val sizeMode = SizeMode.Exact`** (`:110`) — Makes Glance
  re-invoke `provideGlance` (and thus the whole composition) separately for
  **every concrete pixel size** the widget is resized to, rather than a few
  size buckets. This is what lets each tier composable pick its layout purely
  from `LocalSize.current` instead of guessing a bucket.

- **`override suspend fun provideGlance(context: Context, id: GlanceId)`**
  (`:163`) — Glance's entry point for rendering one instance. Called on
  placement, on `updateAll()`/`update()` from the workers, and on every size
  change. Runs in two phases (see §3 for the step-by-step): (1) plain suspend
  Kotlin that reads saved config + latest cached snapshot from disk (never a
  live network call), resolves theme/accent, decodes/caches bitmaps; (2) the
  `provideContent { ... }` Glance composition that dispatches to one tier
  composable via `Ctx`.

### `companion object` (`:959`)
- **`bitmapCache`** (`:961`) — a private `android.util.LruCache<String, Bitmap>`
  bounded to **6 MiB**, sized by `value.byteCount` (`sizeOf` override). Static,
  so it survives across `provideGlance` calls (and across widget instances)
  within the same process lifetime. Backs both `decodeCached` and
  `blurredCached`.
- **`CLIMATE_KEYS = setOf("climate", "climate_on", "climate_off")`** (`:968`)
- **`LOCK_KEYS = setOf("doors", "lock", "unlock")`** (`:969`)
- **`CHARGE_KEYS = setOf("charge", "start_charge", "stop_charge")`** (`:970`)
  These group actions **by the state they visually react to**, not by exact
  key, so a toggle "climate" button AND explicit "climate_on"/"climate_off"
  buttons all light up teal together when climate is active.

Everything else (`Theme`, `Ctx`, all tier composables, shared pieces, click
routing, bitmap/blur helpers) is `private`. Documented in §3.

---

## 3. Internal structure

### 3a. Private data holders

**`private class Theme`** (`:113`) — palette + semantic state colors resolved
once per render off the app theme. Seven `ColorProvider` fields: `accent`,
`onAccent`, `charge`, `unlocked`, `climate`, `pending`, `tile`. See §4.

**`private class Ctx`** (`:124`) — "everything one render needs, so tier
composables stay short." Constructed once in `provideContent` and threaded into
every tier so no tier touches `Context`/DataStore. See §4 for every field.

### 3b. `provideGlance` control flow (`:163`–`:313`)

**Phase 1 — suspend prelude (`:164`–`:211`), plain Kotlin, no composition:**
1. `settings = SettingsStore(context)` and
   `widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)` (`:164`–`:165`).
2. `cfg = settings.widgetConfig(widgetId)` — a `Pair<vin, List<actionKey>>?`.
3. `snap` — look up the `VehicleSnapshot` whose `vin == cfg.first` from
   `SnapshotStore(context).current().vehicles` (`:167`–`:169`). Null if no
   config or vin not found → triggers `SetupTile`.
4. `actions` — map the stored key strings through `WidgetAction.fromKey`,
   dropping unknowns (`:170`).
5. `appearance = settings.appearance.first()` — collects one value from the
   appearance `Flow` (`:171`).
6. Per-widget flags: `requireAuth`, `photoBgOn`, `showLocation` (`:173`–`:175`).
7. `photoPath = snap?.let { settings.imageUrl(it.vin) }`; `photo` decoded only
   if the path starts with `/` (a local file), via `decodeCached` (`:176`–`:177`).
8. `map` — only if `showLocation`; decoded from
   `cacheDir/widget_map_$widgetId.png` at `maxPx = 512` (`:178`).
9. `address` — only if `showLocation`, from `settings.widgetLocationAddress` (`:179`).
10. `pending = settings.widgetPendingAction(widgetId)` — the key of the action
    currently in flight, or null (`:180`).
11. `photoBgActive = photoBgOn && photo != null` (`:181`) — the real gate; a
    photo option with no decodable bitmap does nothing.
12. `pillShape = settings.widgetPillShape(widgetId)` (`:182`).
13. **Color resolution (`:184`–`:203`):** `accentColor = resolveWidgetAccent(...)`
    (per-vin accent from the UI module). `onAccent` = near-black `0xFF20232A`
    if the accent's `luminance() > 0.5f`, else `Color.White`. `glassy(c)` lowers
    alpha to `0.62f` **only when `photoBgActive`**, so buttons/pills read as
    frosted glass over the photo. `Theme` is built: `accent`/`charge`
    (`BlooColors.chargeGreen`)/`unlocked` (`BlooColors.heat`)/`climate`
    (`BlooColors.climateTeal`) all wrapped in `glassy`; `pending` is a fixed
    muted grey `(0.55,0.55,0.60,0.55)`; `tile` a very faint
    `(0.5,0.5,0.55,0.13)`.
14. `layoutMode = settings.widgetLayoutMode(widgetId)` — `"info"` or
    `"controls"` (`:205`).
15. `infoFields` — mapped from stored keys; **no `ifEmpty{DEFAULTS}` fallback**
    because `SettingsStore.widgetInfoFields` itself already distinguishes "never
    configured" (returns DEFAULTS) from "all deselected" (returns empty, which
    must stay empty) (`:206`–`:210`).
16. `bgAlphaLevel = settings.widgetBackgroundAlpha(widgetId)` — 0 (opaque) … 9
    (transparent) (`:211`).

**Phase 2 — `provideContent { GlanceTheme { ... } }` (`:213`–`:312`):**
1. Read `w`/`h` from `LocalSize.current` (`:215`–`:216`).
2. **Corner radius (`:227`–`:234`):** `pillEligible = pillShape && minOf(w,h) < 180.dp`.
   `corner` = `999.dp` if pill-eligible; else 16/22/28 dp scaling with size.
   `pillPad = 8.dp` when pill-eligible else `0.dp`.
3. `bgAlpha = 1f - bgAlphaLevel/9f` → 1.0 opaque … ~0.1 transparent (`:235`).
4. `themeBg` = pure black `0xFF000000` for AMOLED theme mode, else
   `GlanceTheme.colors.widgetBackground` (`:236`–`:237`).
5. Root `Box(fillMaxSize().cornerRadius(corner))` (`:238`). Inside, in order:
   - **Photo layer (`:248`–`:266`):** if `photoBgActive`, draw
     `blurredCached(photo!!, photoPath!!)` cropped full-bleed, then a black
     scrim `Box` at `scrimAlpha = 0.46f * bgAlpha` (only if `> 0.01f`).
   - **Tint layer (`:274`–`:279`):** if `!photoBgActive && bgAlphaLevel > 0`,
     draw a base tint `Color(0.12,0.13,0.16, bgAlpha*0.85f)` plus a 2 dp top rim
     `Color(1,1,1, bgAlpha*0.22f)` — a deliberately honest flat tint (Glance
     can't fake distinct glass looks; see §8).
   - **`base` modifier (`:280`–`:286`):** `fillMaxSize()`, conditionally
     `.background(themeBg)` **only when neither photo nor tint layer already
     painted** (i.e. `!(photoBgActive || bgAlphaLevel > 0)`), then
     `.cornerRadius(corner).padding(pillPad)`.
   - **Content dispatch (`:288`–`:309`):** if `snap == null` →
     `SetupTile(base, configIntent(...))`. Else build `metric` (from
     `appearance.unitSystem == "metric"`) and `c = Ctx(...)`, then the **tier
     dispatcher** `when` block (§3c). After the tier, if `pending != null`,
     overlay a 16 dp `CircularProgressIndicator` (accent-colored) top-end with
     10 dp padding — the "action in flight" spinner drawn on top of any tier.

### 3c. The tier dispatcher (`:293`–`:303`)

Ordered `when` on `w`/`h`. First match wins, so order matters:

| Condition | Tier |
|---|---|
| `w < 70.dp \|\| (w < 80.dp && h < 80.dp)` | `ControlsTile` if `layoutMode=="controls"` else `InfoTile` (`:294`–`:295`) |
| `h < 70.dp` | `ControlsTile` (`:296`) |
| `h < 110.dp` | `ShortWideTile` (`:297`) |
| `w < 110.dp` | `TallNarrowTile` (`:298`) |
| `w < 220.dp && h < 130.dp` | `SquareTile` (`:299`) |
| `w < 220.dp` | `MediumTallTile` (`:300`) |
| `h < 190.dp` | `WideTile` (`:301`) |
| else | `LargeTile` (`:302`) |

Note the tiniest tier is the only one that hard-swaps between a controls and an
info layout at the dispatcher level; other tiers decide internally based on
`c.layoutMode`.

### 3d. Tier composables

- **`InfoTile(c, base)`** (`:319`) — smallest 1×1 info layout. Centered
  `Column` of up to 3 lines, each gated on `infoFields` membership: `NAME`
  (`name.take(6)`, 8sp), `PERCENT` (`"$it%"` or `"—"`, bold 20sp), `LOCK` (a
  5 dp `stateColor` dot). Whole tile clickable → `openIntent`. Range/model never
  fit here regardless of selection.

- **`ControlsTile(c, base)`** (`:347`) — controls-only fill for the tiniest
  controls-mode tile. **Returns nothing if `c.actions.isEmpty()`** (caller still
  paints background/spinner around whatever it returns). Takes first 4 actions
  (defensive coerce; config caps at 4), renders a `ButtonGrid` with
  `cols = take.size.coerceAtMost(2)`, no labels, 18 dp icons.

- **`SetupTile(base, intent)`** (`:360`) — shown for **any** size when
  `snap == null`. Centered car icon + "Tap to set up" text; whole box clickable
  → the passed `configIntent`.

- **`ShortWideTile(c, base)`** (`:374`) — `h < 110.dp`, wide. If
  `layoutMode=="controls"` and actions present, **fully replaces** info with a
  4-col `ButtonGrid` (24 dp icons) and returns. Otherwise a left-aligned
  info `Row`/`Column`: name (`take(12)`, 9sp), percent (bold 20sp), range.

- **`TallNarrowTile(c, base)`** (`:405`) — 1 column wide, any height (1×2 …
  1×5). `narrow = width < 90.dp`. Controls mode: 1-column `ButtonGrid` taking
  4/3/2 actions by height (`>=260`/`>=180`/else), labels shown at `h>=220.dp`,
  icons 26 or 20 dp. Info mode: centered `Column` gated on `infoFields` — NAME
  (bold, 10/12sp), PERCENT (uses `VerticalNumber` when narrow, else 26sp),
  RANGE. Extra rows only when tall: `StateChip` at `h>=200.dp` (+ LOCK field),
  model line at `h>=280.dp` (+ MODEL field, non-blank model).

- **`MediumTallTile(c, w, h, base)`** (`:476`) — `w < 220`, `h >= 130`. Uses
  `splitName` for a 2-line name, then percent (34sp), range, `StateChip`, and in
  controls mode a `ButtonGrid` (2 cols if ≥3 actions) filling remaining height
  via `defaultWeight()`.

- **`SquareTile(c, w, base)`** (`:502`) — roughly square, `<220×130`. Name,
  a vertically-centered percent (30sp) + range side by side, `StateChip`, and
  optional 2-col button block below via `defaultWeight()`.

- **`WideTile(c, w, h, base)`** (`:534`) — wide but under `LargeTile` height
  (`h < 190`). `showButtons = actions.isNotEmpty() && layoutMode=="controls"`.
  Left info column sizes to content (`fillMaxHeight` when buttons present,
  else `fillMaxSize` to use the whole width — fixes an old dead-gap bug, `:538`).
  Name + `StateChip` on one row, percent (34sp), range with
  `"range"`/`"left"` suffix depending on `hasBattery`. Right button column
  (`cols = 2` when ≥3 actions and `h>=150`) only in controls mode.

- **`LargeTile(c, w, h, base)`** (`:577`) — 3×3 and up. Priorities: `wantMap`
  when `showLocation && (map != null || address != null)`; else `wantPhoto`
  when `photo != null && !onPhoto && w>=240.dp`. `sideW = w*0.44f` (≥340) else
  `w*0.38f`. Font/columns scale: `tall = h>=250`, `pctSize` 46/40/34sp by
  height, `footerCols` = 2 when tall & ≥3 actions. Layout: header row (name
  `take(16)` 18sp + `StateChip`), hero row (percent + range + "Battery"/"Fuel"
  label; address text under it when `wantMap`), and on the right either a
  `LocationBox` or a photo `Box` (`w*0.34f`). Footer `ButtonGrid` in controls
  mode with labels at `h>=290.dp`. **This tier ignores `infoFields` entirely —
  it always shows everything** (per `WidgetInfoField` doc).

### 3e. Shared composables

- **`VerticalNumber(text, color)`** (`:457`) — renders a short string as a
  vertical stack (one 18sp bold char per line). Used on narrow widgets where
  horizontal percent text would clip.

- **`LocationBox(c, modifier)`** (`:634`) — the location panel. Three states:
  map bitmap present → crop-fill image; else `address != null` → location icon +
  address (2 lines); else placeholder icon + "Tap to locate". Uses
  `GlanceTheme.colors.onSurfaceVariant` tint and `c.theme.tile` background.

- **`ButtonGrid(c, actions, cols, showLabel, iconSize, modifier)`** (`:660`) —
  a grid of chunky buttons that FILLS `modifier`'s box. Returns early if empty.
  Computes `rows = ceil(actions.size / cols)`; builds `Column` of `Row`s, each
  row `fillMaxWidth().defaultWeight()` (so rows grow to fill height), each cell
  `fillMaxHeight().defaultWeight()`. Cells beyond `actions.size` render an empty
  `Box` to preserve grid alignment.

- **`ChunkyButton(c, action, showLabel, iconSize, modifier)`** (`:682`) — one
  state-colored button filling its cell. Calls `actionVisual` for
  icon/bg/fg/label, draws `Box.background(vis.bg).cornerRadius(18.dp)
  .clickable(clickFor(...))`. With label: icon + text column (icon
  `contentDescription = null` because the Text repeats the words). Without
  label: icon only, `contentDescription = action.label`.

- **`StateChip(c)`** (`:711`) — the rounded status pill next to the car name.
  Label from `vehicleStateLabel(engineOn, charging, climateOn, locked)`.
  Background priority: **charging > unlocked > climate-on > accent default**
  (same priority as `stateColor`/`actionVisual`). `fg` = `onAccent` when the bg
  is the plain accent, else white. `cornerRadius(999.dp)` for a true pill.

- **`onBg(c)`** (`:729`) — `Color.White` over a photo, else
  `GlanceTheme.colors.onSurface`.
- **`onBgV(c)`** (`:732`) — `Color(0xFFE2E2E6)` over a photo, else
  `GlanceTheme.colors.onSurfaceVariant`. (The dimmer "variant" text color.)

### 3f. State → visuals

- **`private class ActionVisual(iconRes, bg, fg, label)`** (`:736`).

- **`actionVisual(action, snap, pending, theme): ActionVisual`** (`:748`) —
  resolves one button's appearance from live state:
  - `isPending = pending == action.key`, `isClimateActive`/`isChargeActive`/
    `isUnlocked` computed against the `*_KEYS` sets and snapshot booleans.
  - **icon priority (`:753`):** pending (refresh icon) > climate-active > lock
    keys (lock vs unlock icon by `snap.locked`) > `action.icon`.
  - **bg priority (`:759`):** pending (muted) > charge > unlocked > climate >
    accent.
  - `fg` = `onAccent` when bg is accent, else white.
  - `label`: for `"doors"`, "Lock"/"Unlock"/"Doors" by `snap.locked`
    (true/false/null); else `action.label.take(8)`.
  - **Pending overrides everything** — a button in flight always shows the
    spinner icon + muted color regardless of car state.

- **`stateColor(snap, theme): ColorProvider`** (`:778`) — same
  charging>unlocked>climate>accent priority, for `InfoTile`'s 5 dp dot. Kept in
  lockstep with `actionVisual`/`StateChip` so the widget never disagrees about
  "which state matters most" across sizes.

### 3g. Click routing

- **`clickFor(ctx, c, action): Action`** (`:793`) — the routing switch:
  - `OPEN` kind → `actionStartActivity(openIntent)`.
  - `action.requiresAuth && c.requireAuth` → `actionStartActivity(authIntent)`
    (transparent biometric gate).
  - else → `actionRunCallback<WidgetActionCallback>` with params
    `KEY_WIDGET`/`KEY_VIN`/`KEY_ACTION` — silent background run, app never opens.

- **`openIntent(ctx, vin): Intent`** (`:809`) — targets `MainActivity` with
  `Shortcuts.ACTION`, data `bloo://widget/open/$vin`, extras
  `EXTRA_VIN`/`EXTRA_CMD="open"`, flags `NEW_TASK|CLEAR_TOP`. Read by
  `MainActivity.handleShortcutIntent`, shared with launcher shortcuts.

- **`authIntent(ctx, widgetId, vin, action): Intent`** (`:818`) — targets
  `WidgetAuthActivity` with `ACTION_RUN`, **unique data URI**
  `bloo://widget/$widgetId/${action.key}` (crucial — see §8), extras
  `EXTRA_WIDGET_ID`/`EXTRA_VIN`/`EXTRA_ACTION`, flag `NEW_TASK`.

- **`configIntent(context, widgetId): Intent`** (`:833`) — targets
  `WidgetConfigActivity` with data `bloo://widget/config/$widgetId` and
  `EXTRA_APPWIDGET_ID`. Used by `SetupTile`.

### 3h. Bitmap + blur helpers

- **`decodeCached(path, maxPx = 400): Bitmap?`** (`:843`) — decode a
  file-backed bitmap downsampled so the longest edge ≤ `maxPx`, memoized by
  `"$path:${lastModified}:$maxPx"`. Returns null if the file doesn't exist.
  Uses `inJustDecodeBounds` to read dimensions, doubles `inSampleSize` until the
  longest edge fits, decodes, caches. Full-size photos handed to RemoteViews
  throw "exceeds maximum bitmap memory usage" and blank the widget — hence
  always scale.

- **`blurredCached(source, path): Bitmap`** (`:881`) — a soft real blur of the
  already-downsampled photo, memoized by `"blur:$path:${lastModified}"`. Copies
  to a mutable `ARGB_8888`, computes `radius = (maxOf(w,h) /
  BLUR_RADIUS_DIVISOR).coerceIn(3, 9)`, runs **2** `boxBlurInPlace` passes.
  Falls back to `source` on failure. Cheap because it runs once per photo change
  on a ~400px bitmap and gets cached.

- **`BLUR_RADIUS_DIVISOR = 45`** (`:901`) — blur radius scales with image size
  but is clamped 3–9; tuned by eye for the ~400px source.

- **`boxBlurInPlace(bmp, radius)`** (`:908`) — one full box-blur pass:
  horizontal pass into a temp array, then vertical pass back into `pixels`, then
  `setPixels`. Returns early if `radius < 1`.

- **`boxBlurPass(src, dst, w, h, radius, alongRows)`** (`:926`) — one
  directional pass using per-channel prefix sums (`prefA/R/G/B`, length
  `inner+1`, index 0 always empty-window so no reset between lines). O(w*h), not
  O(w*h*radius). Edge windows shrink (average of real neighbors only — standard
  box-blur edge behavior, avoids border darkening). Bit-twiddling extracts
  A/R/G/B channels and recomposes them.

- **`splitName(name): Pair<String, String>`** (`:466`) — splits a name into two
  lines at a space (found via `indexOf(' ', 4)` with sanity bounds) or mid-way
  if no space; single line (`x to ""`) when `length <= 8`.

---

## 4. Data & types

### `class Theme` (`:113`) — all `ColorProvider`, built once per render
| Field | Source |
|---|---|
| `accent` | `glassy(accentColor)` from `resolveWidgetAccent` |
| `onAccent` | near-black `0xFF20232A` if accent luminance > 0.5, else white |
| `charge` | `glassy(BlooColors.chargeGreen)` |
| `unlocked` | `glassy(BlooColors.heat)` (red — used for the UNLOCKED state) |
| `climate` | `glassy(BlooColors.climateTeal)` |
| `pending` | fixed muted grey `Color(0.55, 0.55, 0.60, 0.55)` |
| `tile` | very faint `Color(0.5, 0.5, 0.55, 0.13)` (map/photo box bg) |

`glassy(c)` = `c.copy(alpha = 0.62f)` when `photoBgActive`, else `c` unchanged.

### `class Ctx` (`:124`) — the per-render bundle threaded to every tier
| Field | Type | Meaning |
|---|---|---|
| `widgetId` | `Int` | AppWidget id |
| `snap` | `VehicleSnapshot` | cached vehicle status (non-null in tiers; null → SetupTile) |
| `actions` | `List<WidgetAction>` | configured buttons (already decoded) |
| `theme` | `Theme` | resolved colors |
| `pending` | `String?` | key of the action in flight, or null |
| `requireAuth` | `Boolean` | this widget gates actions behind biometrics |
| `onPhoto` | `Boolean` | `photoBgActive` — a photo background is actually drawn |
| `showLocation` | `Boolean` | location box enabled |
| `map` | `Bitmap?` | decoded map tile (≤512px) |
| `photo` | `Bitmap?` | decoded car photo (≤400px) |
| `address` | `String?` | last-known location address |
| `layoutMode` | `String` | `"info"` (data) or `"controls"` (buttons) |
| `metric` | `Boolean` (default false) | units — passed to `formatDistance` |
| `infoFields` | `List<WidgetInfoField>` (default `DEFAULTS`) | which info stats to show; **LargeTile ignores this** |

### `class ActionVisual(iconRes: Int, bg: ColorProvider, fg: ColorProvider, label: String)` (`:736`)
Return value of `actionVisual`. Pure display data for one button.

### `enum class WidgetAction` (external, `WidgetAction.kt`) — referenced heavily
Fields: `key: String`, `label: String`, `icon: Int` (drawable res),
`requiresAuth: Boolean`, `kind: Kind`, `wearAction: String?`. Entries: `DOORS`,
`LOCK`, `UNLOCK`, `CLIMATE`, `CLIMATE_ON`, `CLIMATE_OFF`, `CHARGE`, `REFRESH`
(no auth), `LOCATION`, `OPEN` (no auth). `Kind { COMMAND, REFRESH, LOCATION,
OPEN }`. `fromKey(key)` → entry or null. `DEFAULTS = [DOORS, CLIMATE, REFRESH,
LOCATION]`. Most COMMAND actions carry a `WearAction` (e.g. `DOORS` →
`TOGGLE_LOCK`).

### `enum class WidgetInfoField` (external, `WidgetInfoField.kt`)
`(key, label)` pairs: `NAME`, `PERCENT`, `RANGE`, `LOCK`, `MODEL`.
`fromKey(key)` → entry or null. `DEFAULTS = [NAME, PERCENT, RANGE, LOCK]`.

**Encodings from the domain (relevant when reading `snap`):** `snap.percent`
and `snap.rangeMi` are the powertrain-override-aware values;
`snap.hasBattery` drives the "Battery"/"Fuel" and "range"/"left" labels.
Booleans `charging`, `locked`, `climateOn`, `engineOn` are nullable
tri-states (`true`/`false`/`null` = unknown), and the code checks `== true` /
`== false` explicitly.

---

## 5. State & concurrency

- **No in-process mutable state of its own.** Every render is one-shot (§1).
  All persisted state lives in `SettingsStore` (per-widget config, flags,
  pending-action key, addresses) and `SnapshotStore` (vehicle snapshots), both
  DataStore-backed and read fresh at the top of `provideGlance`.
- The suspend prelude reads DataStore via `.first()` on the appearance Flow and
  direct suspend accessors; there is no explicit dispatcher pin here — Glance
  runs `provideGlance` on its own worker coroutine.
- **`bitmapCache`** (companion `LruCache`) is the only cross-render/cross-widget
  in-process state. `LruCache` is internally synchronized; keys embed
  path+lastModified (+maxPx / "blur:" prefix) so a changed file forces a fresh
  decode/blur. Bounded to 6 MiB so it can never pin more than a few MB.
- **Recomposition/redraw triggers:** there is no live recomposition. A redraw
  happens only when Glance re-invokes `provideGlance` — on placement, size
  change (thanks to `SizeMode.Exact`), or an explicit `update`/`updateAll` from
  `WidgetCommandWorker`/`WidgetActionCallback` after a command completes or the
  pending flag flips.
- **Pending spinner:** `pending` (a `String?` action key) is read once per
  render; the top-end `CircularProgressIndicator` and each button's pending
  visual are derived from it. The worker sets/clears
  `settings.widgetPendingAction` and calls `update`, causing a re-render.

---

## 6. Collaborators & data flow

**Reads from (inbound):**
- `SettingsStore` — `widgetConfig`, `widgetRequireAuth`, `widgetPhotoBackground`,
  `widgetShowLocation`, `imageUrl(vin)`, `widgetLocationAddress`,
  `widgetPendingAction`, `widgetPillShape`, `appearance` (Flow),
  `widgetLayoutMode`, `widgetInfoFields`, `widgetBackgroundAlpha`.
- `SnapshotStore.current().vehicles` — the cached `VehicleSnapshot` list.
- `GlanceAppWidgetManager.getAppWidgetId(id)` — GlanceId → int widget id.
- Disk cache files: `cacheDir/widget_map_$widgetId.png`, and the car photo file
  at `settings.imageUrl(vin)` (local path).
- `resolveWidgetAccent(context, appearance, vin)` (UI module), `BlooColors.*`,
  `formatDistance`, `vehicleStateLabel` (data module).

**Writes / dispatches (outbound):**
- `actionStartActivity(openIntent)` → `MainActivity` (via `Shortcuts` routing,
  `handleShortcutIntent`).
- `actionStartActivity(authIntent)` → `WidgetAuthActivity` (biometric gate).
- `actionStartActivity(configIntent)` → `WidgetConfigActivity`.
- `actionRunCallback<WidgetActionCallback>` → `WidgetActionCallback.onAction`
  (`WidgetCommandWorker.kt:317`), which forwards to
  `WidgetCommandWorker.dispatch(context, widgetId, vin, action)`. Params travel
  as `ActionParameters` keys `bloo_widget_id`/`bloo_vin`/`bloo_action`.
- Produces a `RemoteViews` tree (the real output) consumed by the launcher.

**Called by:** the Glance runtime (`provideGlance`); indirectly by
`WidgetCommandWorker`/callbacks that trigger widget updates.

**Channels:** DataStore (SettingsStore/SnapshotStore), disk cache (bitmaps),
PendingIntents/Intents (activities), Glance ActionCallback → WorkManager
(`WidgetCommandWorker`), Wear Data Layer indirectly (via `WidgetAction.wearAction`
handled downstream in the worker, not here).

---

## 7. Invariants & assumptions

- **A widget render must never make a live network call.** It reads only cached
  snapshot + config from disk (`:158` comment, phase 1).
- **`snap` non-null in every tier composable.** `provideGlance` routes null to
  `SetupTile` before constructing `Ctx`; tiers freely dereference `c.snap`.
- **`photoPath!!`/`photo!!` in the photo layer are safe** because they are only
  reached when `photoBgActive == true`, which requires both non-null (`:181`,
  `:250`).
- **A widget has at most 4 configured actions** (enforced by
  `WidgetConfigActivity`); the `take(4)`/`coerceAtMost` calls are defensive.
- **Bitmaps handed to RemoteViews must be downsampled** or the widget blanks
  with "exceeds maximum bitmap memory usage" (`:842`). `decodeCached` guarantees
  ≤ maxPx.
- **State-priority order is identical everywhere** (charging > unlocked >
  climate-on > accent) across `actionVisual`, `stateColor`, and `StateChip`, so
  size never changes which state "wins."
- **`bitmapCache` keys must embed `lastModified`** so a changed photo/map file
  is not served stale from cache.
- **`infoFields` empty is meaningful** ("all deselected") and must not be
  replaced by DEFAULTS here — the fallback lives in `SettingsStore` (`:206`).
- Box-blur assumes `ARGB_8888`; `blurredCached` copies to that config first.
- `authIntent`'s data URI must be unique per widget+action (see §8).

## 8. Gotchas & sharp edges

- **PendingIntent filterEquals ignores extras (`:821`).** `authIntent` MUST set
  a unique `data` URI (`bloo://widget/$widgetId/${action.key}`); without it,
  every button on a widget collapses into one PendingIntent that always fires
  the last-cached action. (`openIntent` similarly encodes the vin in its data.)
- **`ControlsTile` returns nothing when no actions are configured** (`:348`) —
  it does NOT draw an empty box; the caller still paints the background and the
  pending spinner around the empty result.
- **`glassy()` alpha baking (`:194`):** state colors get `alpha 0.62f` baked in
  *once* only when over a photo, so every call site (`ChunkyButton`,
  `StateChip`, …) gets the frosted look for free without per-site alpha logic.
  Combined with the stronger `0.46f` scrim (`:262`, raised from an old `0.30f`)
  this is what lets buttons stay translucent and still legible over the blurred
  photo.
- **The blur is a real per-pixel box blur, not RenderScript/RenderEffect**
  (`:858` doc). Glance content is built off the main render pipeline and
  RenderEffect needs a live View/RenderNode, so neither is available. The
  fixed-*pixel*-radius approach (vs an older scale-down/scale-up trick) decouples
  blur amount from source resolution/JPEG compression, avoiding blotchy /
  over-mushy / block-edge-showing artifacts. Radius/pass count were tuned down
  over time (divisor 30→45, clamp 4–14 → 3–9, three passes → two).
- **Pill shape gating fixed (`:227`):** now `minOf(w,h) < 180.dp` (one narrow
  dimension), previously required BOTH under 180, which silently excluded long
  strips — the exact shape a stadium/pill reads best on. Large-in-both tiles
  still fall back to normal corners so a near-circular radius doesn't clip
  content.
- **`cornerRadius(999.dp)` on `StateChip` (`:722`)** clamps to a true pill at
  the chip's actual short height; an old fixed `9.dp` only looked right at one
  tier's text size.
- **`base` background is conditional (`:280`).** If the photo layer or tint
  layer already painted, `base` does NOT add `themeBg`, avoiding a double fill
  (and letting the photo/tint show through).
- **Tint layers are deliberately flat (`:267` doc).** Glance has no
  blur/gradient primitive and a hard nested-view budget, so faking distinct
  "Liquid"/"Frosted" looks would only produce two slightly different flat tints.
  The code chooses one honest tint (base fill + 2 dp top rim) instead.
- **AMOLED theme uses pure `0xFF000000` background** (`:236`), bypassing the
  usual `GlanceTheme.colors.widgetBackground`.
- **`LargeTile` deliberately ignores `infoFields`** (`:140` / `WidgetInfoField`
  doc) — at 3×3+ there's room for everything, so a picker there would be config
  for no visual gain.
- **`ShortWideTile`/`TallNarrowTile`/tiniest tier fully SWAP to controls**
  rather than appending a button strip (`:376` comment) — otherwise controls
  mode would only ever *add* to those small tiers instead of changing what the
  widget is for.
- **Icon `contentDescription` asymmetry in `ChunkyButton` (`:694` vs `:699`):**
  when a label is shown the icon's description is `null` (the Text repeats the
  words — a non-null description was a redundant screen-reader announcement);
  label-less buttons carry `action.label` as the description.
- **`decodeCached` uses power-of-two `inSampleSize`** (`sample *= 2`), so the
  decoded longest edge can be well under `maxPx` (not exactly it).
