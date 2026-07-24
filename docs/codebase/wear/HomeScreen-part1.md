# wear/HomeScreen.kt — Part 1 (lines 1–1050): structure & cards A

File: `C:\Users\AdiPerets\Bloo\wear\src\main\java\com\bloo\wear\ui\HomeScreen.kt`
Package: `com.bloo.wear.ui`
Scope of this doc: lines 1–1050 — the root screen scaffold (`HomeScreen`), the
per-car vertical column (`CarColumn`), the tile dispatch (`TileContent`), the
floating chrome overlays, and the first cluster of "cards" (Alerts, Summary,
Climate, Smart Climate, and the header of Comfort). The remaining cards
(Comfort body, Presets, Charge, Limits, Location, Weather, Info, Diagnostics,
AI, Assist, More) and `TileReorderScreen` continue past line 1050 and are
covered in a sibling doc; they are referenced here only where lines 1–1050 call
them.

---

## 1. Purpose

This file is the **entire Wear OS home UI** for the Bloo watch app. It renders,
per paired vehicle, a horizontally-swipeable pager (one page per car) where each
page is a vertically-scrolling, wrap-around ("infinite") stack of **tiles**
(a.k.a. cards): Summary, Climate, Charge, Location, Diagnostics, etc. It is a
pure Jetpack Compose (Wear Compose Material3) view layer: it holds only
UI-ephemeral state (scroll positions, pager state, delete-confirm arming,
rotary-scroll accumulators) and delegates every real action and every piece of
durable state to a `WearViewModel` (`vm`) and reads a snapshot of app state from
a `WearUi` (`ui`) passed in by the caller.

Why it exists: the watch is a thin remote control head for the phone-hosted
telematics stack. `HomeScreen` is what the user actually looks at and touches;
all vehicle data arrives passively from the phone over the Wear Data Layer and
is surfaced here as `ui.cars` (a `List<CarView>`), while user intent (lock,
climate, charge, presets, refresh, update install) flows back out through `vm`
method calls.

---

## 2. Public surface (within lines 1–1050)

Two declarations in this range are public (rest are `private`/`internal`):

### `HomeScreen` — `HomeScreen.kt:199`
```kotlin
@Composable
fun HomeScreen(
    vm: WearViewModel,
    ui: WearUi,
    onSettings: () -> Unit,
    onTrips: (String) -> Unit,
    onReorder: (String) -> Unit = {},
)
```
Root composable of the watch app.
- If `ui.cars.isEmpty()` (`:201`): renders a centered "No cars yet" placeholder
  (a `DirectionsCar` icon at 40dp + 0.4α, a bold "No cars yet" title, and
  "Open Bloo on your phone to sign in.") and **returns early** (`:228`) before
  any pager state is created.
- Otherwise builds the horizontal car pager and its overlays (see §3).
- `onSettings` / `onTrips(vin)` / `onReorder(vin)` are navigation callbacks
  threaded down to the `MoreCard`. `onReorder` defaults to a no-op.

### `MessageSnackbar` — `HomeScreen.kt:555`
```kotlin
@Composable
internal fun BoxScope.MessageSnackbar(message: String?, onDismiss: () -> Unit)
```
`internal` (not `private`) extension on `BoxScope`. Auto-dismissing status
snackbar pinned to bottom-center. See §3 for behavior. It is `internal` rather
than `private` because it is reused elsewhere in the module.

Everything else in lines 1–1050 is `private` (screen-internal composables /
helpers) or a `private` extension property.

---

## 3. Internal structure & control flow

### 3.1 `CarView.alertCount` — `HomeScreen.kt:152`
```kotlin
private val CarView.alertCount: Int
```
Sum of active warnings for a car: `doorsOpen.size + windowsOpen.size` plus 1
each for `trunkOpen`, `hoodOpen`, `tireWarning`, `lowFuel`, `washerLow`,
`brakeLow`, `keyFobLow`. Used to (a) decide whether the synthetic alerts tile is
shown, (b) draw the badge on the Summary charge ring, and (c) the "Alerts N
open" row in MoreCard.

### 3.2 `TILE_ALERTS` — `HomeScreen.kt:149`
```kotlin
private const val TILE_ALERTS = "alerts"
```
Synthetic tile key. **Not** part of the user-orderable pebble/tile set — it's
injected at the front of the visible tile list only when `alertCount > 0`.

### 3.3 `HomeScreen` control flow — `:199`–`:292`
1. Early-out empty case (`:201`–`:229`).
2. `count = ui.cars.size` (`:230`).
3. `listStates` (`:233`): `remember { mutableStateMapOf<String, ScalingLazyListState>() }`
   — per-VIN scroll positions. **Deliberately declared OUTSIDE the `key()`
   block below** so a VIN-list refresh doesn't wipe scroll positions.
4. `LaunchedEffect(ui.cars)` (`:234`): `listStates.keys.retainAll(...)` evicts
   scroll state for cars that no longer exist, so the map can't grow unbounded.
5. `key(ui.cars.map { it.vin })` (`:240`): the whole pager subtree is keyed on
   the **list of VINs**. If the set/order of cars changes, the `HorizontalPager`
   + its `rememberPagerState` are thrown away and rebuilt rather than trying to
   reconcile stale page indices against a different VIN list.
6. Inside the key block:
   - `carPager = rememberPagerState(initialPage = 0) { count }` (`:241`).
   - `activeCarIndex by derivedStateOf { carPager.settledPage }` (`:243`) —
     **settledPage, not currentPage**, so it updates only once a swipe fully
     settles, not mid-drag.
   - `lastShownVin` guard (`:245`) + `LaunchedEffect(activeCarIndex)` (`:246`)
     calls `vm.onCarShown(vin)` exactly once per car per "session of being
     scrolled to" — swiping back and forth over the same car doesn't re-fire it.
7. The `Box(fillMaxSize)` (`:254`) contains:
   - `HorizontalPager` with `beyondViewportPageCount = 1` (`:257`) — keeps the
     two neighbor pages pre-composed ("warm").
   - Per page (`:259`–`:285`):
     - `pageOff` (`:261`) = `derivedStateOf { abs((page - currentPage) + currentPageOffsetFraction).coerceIn(0f,1f) }` — 0 = fully centered/settled, 1 = fully off screen.
     - `car = ui.cars[page]` (`:267`).
     - `active` (`:269`) = `page == carPager.settledPage && !carPager.isScrollInProgress` — true only for the settled page with no scroll in progress. Threaded into `CarColumn` so only that page claims rotary focus.
     - `carRoles = ui.settings?.carColors?.get(car.vin)` (`:270`).
     - `body` (`:271`): a `Box(fillMaxSize).graphicsLayer { alpha = 1f - pageOff*0.28f; scaleX/scaleY = 1f - pageOff*0.03f }` wrapping `CarColumn(...)` — cheap fade + subtle shrink as a page recedes.
     - If `carRoles != null` (`:282`), wrap `body()` in a per-car `MaterialTheme(colorScheme = schemeFrom(carRoles))` (memoized via `remember(carRoles)`) so each car can carry its own accent color without a global theme override; else render `body()` directly.
   - `CurvedIndicator(count, carPager.currentPage, anchor = 90f)` (`:287`) — per-car dots along the bottom bezel, driven by `currentPage` (live, mid-drag).
   - `MessageSnackbar(ui.message, onDismiss = { vm.dismissMessage() })` (`:289`) — one instance for the whole screen, above all pages.

### 3.4 `visibleTiles(ui, car): List<String>` — `HomeScreen.kt:295`
Resolves the ordered list of tile keys actually shown for one car:
1. If `car.alertCount > 0`, prepend `TILE_ALERTS` (`:298`).
2. `hidden = ui.settings?.hiddenSections?.get(car.vin).orEmpty()` (`:302`).
3. Iterate `WearPebbles.tilesFor(ui.pebbleOrderFor(car.vin), hidden)` — pebble
   order mirrored from the phone; one pebble may expand into several tiles;
   phone-hidden pebbles dropped (`:303`).
4. Per key, a `when` (`:304`–`:313`) decides visibility:
   - `PRESETS` → always `true` (so you can save the first preset from the watch).
   - `CHARGE`, `LIMITS` → `car.hasBattery`.
   - `LOCATION` → `car.lat != null && car.lon != null`.
   - `WEATHER`, `SMART_CLIMATE` → `ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null`.
   - `DIAGNOSTICS` → `car.hasLiveStatus`.
   - `AI` → `ui.settings?.aiEnabled == true`.
   - else → `true` (summary, lock, climate, comfort, info, assist, more).
5. Returns the accumulated `ArrayList<String>`.

### 3.5 `CarColumn` — `HomeScreen.kt:326`
```kotlin
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarColumn(
    vm, ui, car,
    listStates: MutableMap<String, ScalingLazyListState>,
    onSettings, onTrips, onReorder,
    active: Boolean = true,
)
```
One car's tile stack as a wrap-around `ScalingLazyColumn` with rotary snap. Key
mechanics, in order:

- `scope = rememberCoroutineScope()` (`:337`), `focusRequester = remember { FocusRequester() }` (`:338`), `round = LocalConfiguration.current.isScreenRound` (`:339`).
- **Narrow keying** for `tiles` (`:345`–`:362`): reads only the specific fields
  `visibleTiles()` consumes (`hiddenForCar`, `pebbleOrder`, `hasCarWeather`,
  `hasHomeWeather`, `aiEnabled`, plus `car.vin/alertCount/hasBattery/lat/lon/hasLiveStatus`)
  and `remember(...)`s on those. Rationale (comment `:341`): keying on the whole
  `ui.settings`/`ui.extras` recomputed this for every on-screen page on any
  unrelated push.
- `tileCount = tiles.size`; `infinite = tileCount > 1` (`:363`–`:364`).
- `cycles = if (infinite) 24 else 1`; `total = tileCount * cycles` (`:369`–`:370`).
  The virtual list is `total` items long; the wrap guardian only lets the user
  drift `tileCount*2` from center, so 24 cycles is plenty (down from a former 200).
- `summaryIdx = tiles.indexOf(SUMMARY).coerceAtLeast(0)` (`:371`).
- `initialIndex = if (infinite) (cycles/2)*tileCount + summaryIdx else summaryIdx` (`:372`)
  — start centered in the virtual list on the Summary tile.
- `state = listStates.getOrPut(car.vin) { ScalingLazyListState(initialIndex) }` (`:374`)
  — scroll state is per-VIN and persisted in the caller's map.
- `virtualList = remember(total) { List(total) { it } }` (`:377`) — because
  `ScalingLazyListScope` only has `items(List<T>)`, not `items(count)`; the key
  lambda needs unique ints.
- **Reorder-snap effect** (`:382`–`:395`): `initialised` starts `false`, keyed on
  `car.vin`. `LaunchedEffect(tiles)` animates back to `(cycles/2)*tileCount + summaryIdx`
  whenever `tiles` changes — but skips the very first composition (`if (initialised)`),
  because the state was just initialized there. Uses `animateScrollToItem` (not
  a snap) so it matches the rest of the screen's animated motion language. Keyed
  on **this car's** `tiles` (structurally compared), not the whole pebble map —
  otherwise reordering one car snapped every on-screen car back to Summary.
- **Focus claim** (`:399`–`:406`): `LaunchedEffect(car.vin, active)` — returns
  immediately if `!active`; else `delay(60)` then up to 5 attempts (each guarded
  by `runCatching`) to `focusRequester.requestFocus()`, 40ms apart. Only the
  settled (active) page owns crown/bezel.
- **Rotary state**: `rotaryJob: Job?` (`:408`), `rotaryTargetIdx: Int = -1` (`:409`),
  `rotaryAccumPx: Float = 0f` (`:411`), `rotaryStepPx = 24.dp.toPx()` (`:412`).
- `centerItemIndex` (`:414`–`:424`): `derivedStateOf { state.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index ?: 0 }`.
  Comment (`:415`–`:421`) documents a fixed bug: under the default
  `ItemCenter` anchor, `ScalingLazyListItemInfo.offset` is already center-line
  relative (0 == centered); a prior version added `it.size/2` again, double-
  counting and picking the tile ~half an item-height off center.
- `centerTile = if (tileCount > 0) tiles[centerItemIndex % tileCount] else ""` (`:425`).
- **Idle wrap guardian** (`:435`–`:446`): `LaunchedEffect(tileCount, total)` (keyed
  on the counts it closes over, NOT `Unit` — a shrunken/grown tile set otherwise
  judged boundaries with stale counts). While `infinite`, `snapshotFlow { centerItemIndex to state.isScrollInProgress }`; when **not scrolling and no rotary job active**, if `idx < tileCount*2` or `idx > total - tileCount*2`, silently `scrollToItem((cycles/2)*tileCount + phase)` where `phase = idx % tileCount`.
- **The `ScalingLazyColumn`** (`:449`–`:508`):
  - `.onRotaryScrollEvent` handler (`:452`–`:484`): see §3.5.1.
  - `.focusRequester(focusRequester).focusable(active)` (`:485`–`:486`).
  - `scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f)` (`:489`)
    — **no** built-in shrink/fade; tiles render flat. Comment explains a prior
    focus-zoom effect read as "tiles receding with dead space."
  - `contentPadding = PaddingValues(horizontal = if (round) 22.dp else 12.dp, vertical = 60.dp)` (`:492`).
  - `verticalArrangement = Arrangement.spacedBy(4.dp)` (`:499`) — tighter than the 12dp default.
  - `items(items = virtualList, key = { it })` (`:501`) → `TileContent(tiles[i % tileCount], vm, ui, car, onSettings, onTrips, onReorder)` (`:506`).
- After the column, inside the same outer `Box`:
  - `CurvedDotIndicator(total = tileCount, activeIndex = if (tileCount>0) centerItemIndex % tileCount else 0)` (`:512`).
  - `CarNameOverlay(name = car.name, visible = centerTile.isNotEmpty() && centerTile != SUMMARY, phoneConnected = ui.phoneConnected)` (`:518`).
  - `TopClockScrim()` (`:527`).

#### 3.5.1 Rotary handler control flow — `:452`–`:484`
1. If `!active`, return `false` (don't consume — inactive page).
2. `rotaryAccumPx += e.verticalScrollPixels` (`:460`). Accumulate travel rather
   than stepping per raw event: a low-res bezel emits one big event per detent,
   a high-res crown streams many small deltas — stepping each streamed event flew
   across the whole ring.
3. If `abs(rotaryAccumPx) < rotaryStepPx` (24dp), return `true` (consumed but no
   step yet) (`:461`).
4. `dir = if (rotaryAccumPx > 0) 1 else -1`; **reset** `rotaryAccumPx = 0f`
   (reset, not carry, so one oversized detent can't double-step) (`:462`–`:463`).
5. `base = if (rotaryTargetIdx >= 0) rotaryTargetIdx else centerItemIndex` (`:465`)
   — chase the in-flight target if a scroll is already animating.
6. `newTarget = if (infinite) base+dir else (base+dir).coerceIn(0, maxIdx)` (`:466`).
7. **Immediate boundary remap** (`:473`–`:476`): if `infinite` and `newTarget`
   is within `tileCount*2` of either end, remap to `(cycles/2)*tileCount + phase`
   where `phase = ((newTarget % tileCount) + tileCount) % tileCount` (the double-
   mod handles negative `newTarget`). This fires **before** the scroll settles,
   because the idle guardian only fires when `isScrollInProgress` goes false —
   which a long continuous roll never does, so the user used to get stuck.
8. `rotaryTargetIdx = newTarget`; cancel any prior `rotaryJob`; launch a new one
   that `state.animateScrollToItem(newTarget)` then resets `rotaryTargetIdx = -1`
   (`:477`–`:482`).
9. Return `true`.

### 3.6 `TopClockScrim` — `HomeScreen.kt:534`
`private fun BoxScope.TopClockScrim()`. A top-center, full-width, 34dp-tall box
with a vertical gradient (`background @ 0.5α → Transparent`) blurred 14dp with
`BlurredEdgeTreatment.Unbounded`. It's a soft scrim behind Wear's system clock
(`TimeText`), matching the phone's `StatusBarScrim`.

### 3.7 `MessageSnackbar` — `HomeScreen.kt:555`
- `if (message != null)` → `LaunchedEffect(message) { delay(3500); onDismiss() }`
  (`:557`) — auto-dismiss after 3.5s.
- `isError` (`:562`): `message` contains (case-insensitive) any of "fail",
  "error", "couldn't", "can't", "denied".
- `AnimatedVisibility(visible = message != null, ...)` at bottom-center + 24dp
  bottom padding, slide-in/out vertically + fade (`:567`).
- Content: a rounded (16dp) `Box`, error-tinted (`errorContainer` vs
  `surfaceContainer`), with a real `dropShadow` (`:581`), `semantics {
  liveRegion = Polite; contentDescription = "$message. Double tap to dismiss." }`
  (`:588`–`:591`), `clickable(onClickLabel = "Dismiss") { onDismiss() }`, and a
  2-line ellipsized `Text` in `onErrorContainer`/`onSurface`.
- Lives at HomeScreen level (not inside `CarColumn`) so pre-composed neighbor
  pages don't each spin up their own copy + timer.

### 3.8 `TileContent` — `HomeScreen.kt:617`
```kotlin
@Composable
private fun TileContent(key, vm, ui, car, onSettings, onTrips, onReorder)
```
Exhaustive `when(key)` dispatch from a tile key to its card composable
(`:626`–`:642`):
`TILE_ALERTS`→`AlertsCard(car)`, `SUMMARY`→`SummaryCard`, `CLIMATE`→`ClimateCard`,
`SMART_CLIMATE`→`SmartClimateCard`, `COMFORT`→`ComfortCard`, `PRESETS`→`PresetsCard`,
`CHARGE`→`ChargeCard`, `LIMITS`→`LimitsCard`, `LOCATION`→`LocationCard`,
`WEATHER`→`WeatherCard(ui, car)`, `INFO`→`InfoCard(car, ui)`,
`DIAGNOSTICS`→`DiagnosticsCard(car)`, `AI`→`AiCard`, `ASSIST`→`AssistCard(car)`,
`MORE`→`MoreCard(...)`. **Not** remembered/cached — it's re-invoked for every
virtual index, so the same key (e.g. "summary") is composed at many virtual
indices simultaneously across the wrapped list.

### 3.9 `CurvedDotIndicator` — `HomeScreen.kt:660`
`private fun CurvedDotIndicator(total: Int, activeIndex: Int)`. Tile-progress
dots along the **right** arc (anchor 0° = 3 o'clock). One dot per tile.
- Returns if `total <= 1` (`:661`).
- `shown = min(total, 12)` — cap at 12 dots (`:662`).
- `active`: if `total <= shown`, `activeIndex.coerceIn(0, shown-1)` (1:1); else
  proportionally map: `((activeIndex.toFloat()/(total-1))*(shown-1)).roundToInt().coerceIn(...)` (`:663`–`:670`).
- `CurvedLayout(anchor=0f, anchorType=Center) { curvedRow { repeat(shown) { i -> ... } } }`.
  Each dot: `animateDpAsState(if isOn 7.dp else 4.dp, tween(150))` size and
  `animateColorAsState(primary vs outlineVariant, tween(150))` color, clipped to
  a circle (`:674`–`:684`).

### 3.10 `CarNameOverlay` — `HomeScreen.kt:700`
`private fun BoxScope.CarNameOverlay(name, visible, phoneConnected = true)`. A
small pill naming the current car, below the system clock (top-center + 26dp).
- `AnimatedVisibility(visible, ...)` fade + vertical slide from top (`:701`).
- Rounded-50 pill with real `dropShadow`, `surfaceContainer` background (`:707`).
- `Row`: bold `labelMedium` ellipsized name in `onBackground` color; **if
  `!phoneConnected`**, a 6dp `tertiary@0.7α` dot with `semantics {
  contentDescription = "Phone disconnected, running standalone" }` (`:729`–`:741`)
  — the only phone-disconnect cue when scrolled away from Summary.
- Comment (`:688`–`:698`): this used to also be a long-press-to-refresh gesture
  with an escalating-haptic hold ring; removed because it duplicated MoreCard's
  Refresh button and had no accessible (TalkBack/switch) equivalent.

### 3.11 `AlertsCard` — `HomeScreen.kt:757`
`private fun AlertsCard(car: CarView)`.
- Local `fun openSummary(items)` (`:761`): if `items.size == 1` → the single
  name, else `"${items.size} open"`.
- `warnings = buildList { ... }` (`:767`–`:777`): appends `(label, value)` pairs
  for each true flag: `Doors`/`Windows` (via `openSummary`), `Trunk`/`Hood`
  ("Open"), `Tires` ("Check"), `Fuel`/`Washer fluid`/`Brake fluid` ("Low"),
  `Key fob` ("Low battery").
- `if (warnings.isEmpty()) return` (`:778`) — renders nothing when empty.
- `AnimatedVisibility(visible = true, enter = fadeIn(tween 300) + slideInVertically(tween 300){-it/4})`
  wrapping a headerless `SectionCard(null)` (`:780`–`:781`): an error-tinted
  Warning icon + "ALERTS" label row, then a `StatusRow(label, value, valueColor = errColor)` per warning.

### 3.12 `SummaryCard` — `HomeScreen.kt:809`
`private fun SummaryCard(vm, ui, car) = SectionCard(null) { ... }`. The
always-first, headerless "hero" card.
- `alertCount = car.alertCount` (`:810`).
- `isStale = car.fetchedAt != null && System.currentTimeMillis() - car.fetchedAt > STALE_STATUS_MS` (`:811`)
  — plain derived boolean, **not** `remember`ed; only re-evaluates when
  something else triggers recomposition (not a ticking clock).
- Left: a `Box(TopEnd)` with `ChargeRing(car.percent, size=60.dp, charging=car.charging==true)` and, if `alertCount>0`, an error-circle badge (`:815`–`:840`) showing `"$alertCount"` (or "!" if >9), with its own `semantics { contentDescription = "$alertCount alert(s)" }`.
- Right `Column` (`:842`):
  - `metric = ui.localSettings.unitSystem == "metric"` (`:843`).
  - `AnimatedValue(value = car.rangeMi?.let { formatDistance(it, metric) } ?: "—", titleMedium bold)` (`:844`).
  - `Text(if (car.hasBattery) "Battery" else "Fuel", labelSmall)` (`:849`).
  - Activity line (`:850`–`:859`), mutually exclusive `when`:
    - `car.engineOn` → "Driving" (tertiary).
    - `car.charging == true && timeToFullMin != null && > 0` → `"${fmtMinutes(...)} to full"` (chargeGreen).
    - `car.charging == true` → "Charging" (chargeGreen).
    - `car.pluggedIn == true` → "Plugged in" (onSurfaceVariant).
  - `rel = relativeLabel(car.fetchedAt)`; if non-blank, a freshness `Text` colored `error@0.75α` when `isStale` else `onSurfaceVariant` (`:860`–`:868`).
  - `if (!ui.phoneConnected)` → "Standalone" (tertiary@0.8α) (`:869`).
- `car.battery12v?.let { v12 -> ... }` (`:879`): a `StatusRow("12V battery", "$v12%")` colored `error` if `v12 < 20`.
- Quick actions row (`:888`–`:914`): two `MorphButton`s at `weight(1f)`:
  - Lock: label "Locked"/"Unlocked", icon Lock/LockOpen, `active = !locked`
    (unlocked is the highlighted/noteworthy state), `pending = "${vin}:doors" in ui.pending`, `onClick = vm.toggleLock(vin)`, `toggled = locked`.
  - Climate: label "Climate", `active/toggled = car.climateOn == true`,
    `pending = "${vin}:climate" in ui.pending`, `onClick = vm.toggleClimate(vin)`.

### 3.13 `ClimateCard` — `HomeScreen.kt:933`
`private fun ClimateCard(vm, ui, car) = SectionCard("Climate", Icons.Filled.Thermostat) { ... }`.
Manual climate. **All editable values come from `ui.draftFor(car.vin)`** (an
in-memory per-car draft in `WearViewModel`), not the car's reported state.
- `d = ui.draftFor(car.vin)` (`:934`).
- Helper text "Pick your own temperature" (`:939`) — parallels SmartClimate's line.
- Start/stop `MorphButton` (`:945`): label "Climate on"/"Start climate",
  `active/toggled = car.climateOn == true` (the actual reported state, not the
  draft), `pending = "${vin}:climate" in ui.pending`, `onClick = vm.toggleClimate(vin)`.
- `fahrenheit = ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false` (`:957`).
- `SliderRow("Temp", degLabel(d.tempF.toString(), fahrenheit), d.tempF, 62, 82, 2, accent = tempColor(d.tempF)) { vm.setClimateTemp(vin, it) }` (`:958`)
  — **2°F steps** (11 stops vs 21), because the round face can't fit 21 slider dots.
- `SliderRow("Run", "${d.duration} min", d.duration, 1, 10, 1) { vm.setClimateDuration(vin, it) }` (`:959`).
- Defrost `MorphButton` (`:961`): label "Defrost on"/"Defrost", `active = d.defrost`, `pending = false`, `onClick = vm.toggleDefrost(vin)`.

### 3.14 `SmartClimateCard` — `HomeScreen.kt:997`
`private fun SmartClimateCard(vm, ui, car) = SectionCard("Smart Climate", ...) { ... }`.
One-tap climate that derives a target from ambient weather.
- `fahrenheit` computed as in ClimateCard (`:998`).
- `weather: WearWeather? = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather` (`:999`)
  — prefer this car's own weather; fall back to phone/home weather. Both arrive
  asynchronously; either can be null for a while after start.
- `ambientF = weather?.let { ambientFahrenheit(it.tempC) }` (`:1000`) — convert to
  °F so the `>= 70` threshold and `smartClimateTargetF` (both °F-based) work
  regardless of the display-unit preference.
- `label` (`:1001`–`:1008`): if `ambientF != null`: `action = if (ambientF >= 70) "Cool" else "Heat"`; if `car.climateOn == true` → "Smart climate on", else `"$action to ~${degLabel(smartClimateTargetF(ambientF).toString(), fahrenheit)}"`. Else "No weather data".
- `MorphButton` (`:1009`): `active = car.climateOn == true`, `pending = "${vin}:climate" in ui.pending`, **`enabled = weather != null`** (disabled, not spinning, when no weather — "no weather yet" isn't an in-flight request), `onClick = { if (weather != null) vm.smartClimate(vin) }`.
- `currentWeather = weather` captured into a local (`:1024`); if `ambientF != null && currentWeather != null` (guarding on `weather` itself, not just `ambientF`, to avoid a future NPE), render `"Ambient: ${weatherTemp(currentWeather.tempC, fahrenheit)} · adjusts ±10°${if (fahrenheit) "F" else "C"}"` (2-line ellipsized) (`:1025`–`:1033`).

### 3.15 `ComfortCard` (header only, `:1057`–`~1065` within range)
`private fun ComfortCard(vm, ui, car) = SectionCard("Comfort", Icons.Filled.AirlineSeatReclineNormal) { ... }`
begins at `:1057`. Within lines 1–1050 only its doc-comment (`:1037`–`:1056`)
and header/first lines are in scope. Like `ClimateCard`, every control reads and
writes `ui.draftFor(car.vin)`; row visibility is gated by
`ui.settings?.seatConfigs?.get(car.vin)` (defaulting to `WearSeatConfig()` =
driver + passenger heat) and settings are "Applied when you start climate"
rather than sent immediately. Full body is documented in the part-2 doc.

---

## 4. Data & types

This file defines **no** data classes / enums / sealed interfaces in this range.
It consumes types from `com.bloo.bluelink.data` and `com.bloo.wear`:

- **`CarView`** (`com.bloo.wear.CarView`): the per-car UI snapshot the phone
  pushes down. Fields read in lines 1–1050 include: `vin`, `name`, `brand`
  (`Brand`), `percent: Int?`, `rangeMi: Int?`, `hasBattery: Boolean`,
  `charging: Boolean?`, `pluggedIn: Boolean?`, `engineOn: Boolean`,
  `climateOn: Boolean?`, `locked: Boolean?`, `timeToFullMin: Int?`,
  `fetchedAt: Long?`, `battery12v: Int?`, `lat: Double?`, `lon: Double?`,
  `hasLiveStatus: Boolean`, and the alert flags used by `alertCount`
  (`doorsOpen: List<String>`, `windowsOpen: List<String>`, `trunkOpen`,
  `hoodOpen`, `tireWarning`, `lowFuel`, `washerLow`, `brakeLow`, `keyFobLow`).
- **`WearUi`** (`com.bloo.wear.WearUi`): the whole-app UI state. Fields read
  here: `cars: List<CarView>`, `settings?` (`.carColors: Map<vin,roles>`,
  `.hiddenSections: Map<vin,Set<String>>`, `.aiEnabled`, `.useFahrenheit`,
  `.seatConfigs`), `localSettings.unitSystem` ("metric"/other),
  `extras.carWeather: Map<vin, WearWeather>`, `extras.homeWeather: WearWeather?`,
  `pending: Set<String>` (keys like `"$vin:doors"`, `":climate"`, `":charge"`,
  `":refresh"`), `message: String?`, `phoneConnected: Boolean`,
  `presets: Map<vin, List<...>>`, `aiBusy`, `updateRun`, `updateDownloading`.
  Plus methods: `pebbleOrderFor(vin)`, `draftFor(vin)`, `chargeDraftFor(vin)`.
- **`WearWeather`** (`com.bloo.bluelink.data.WearWeather`): `tempC`, `feelsLikeC`,
  `highC?`, `lowC?`, `humidity?`, `windKph`, `code`, `isDay`. (Full field use in
  WeatherCard is past line 1050 but `tempC` is read in SmartClimateCard `:1000/:1028`.)
- **`Brand`** enum, **`SeatLevel`** (`.apiValue`, `.fromApi`, `.label`), and the
  `degLabel`/`formatDistance`/`formatSpeed`/`links` helpers from `bluelink.data`.
- Constants from `com.bloo.bluelink.data`: `STALE_STATUS_MS` (`:811`),
  `ambientFahrenheit`/`smartClimateTargetF` (`:1000`,`:1004`), `CHARGE_LIMIT_RANGE`
  (used past 1050).

Local synthetic key: `TILE_ALERTS = "alerts"` (`:149`). All other tile keys are
`WearTiles.*` string constants (`SUMMARY`, `CLIMATE`, `SMART_CLIMATE`, `COMFORT`,
`PRESETS`, `CHARGE`, `LIMITS`, `LOCATION`, `WEATHER`, `INFO`, `DIAGNOSTICS`, `AI`,
`ASSIST`, `MORE`).

Encoding notes relevant to this range:
- `SeatLevel.apiValue`: 0=off, 3–5=cool, 6–8=heat (crosses the wear wire as
  ints). `seatLabel(v)` (`:1837`) treats 0 as "no row" (null).
- `pending` set membership is by string key `"$vin:$action"`.

---

## 5. State & concurrency

- **No StateFlow/DataStore here.** All durable/observable app state lives in
  `WearViewModel` and is delivered as an immutable `WearUi` snapshot on each
  recomposition; this file only reads it.
- **Ephemeral Compose state**:
  - `listStates` — `mutableStateMapOf<String, ScalingLazyListState>()` in
    `HomeScreen`, one scroll state per VIN, survives VIN-list refresh (kept
    outside the `key()` block), pruned by `retainAll` (`:237`).
  - `carPager` — `rememberPagerState` (rebuilt when VIN list changes).
  - `activeCarIndex` / `pageOff` / `centerItemIndex` — `derivedStateOf` computed
    from pager/list layout info (recompute only when their inputs change).
  - `lastShownVin`, `initialised`, `rotaryJob`, `rotaryTargetIdx`,
    `rotaryAccumPx` — `remember { mutableStateOf/... }` per composable.
- **Coroutines/dispatchers**: `rememberCoroutineScope()` in `CarColumn` (`:337`)
  launches rotary scroll animations (`state.animateScrollToItem`) on the
  composition scope (main). `LaunchedEffect`s drive: scroll-state eviction, the
  `onCarShown` call, reorder-snap animation, focus-request retry loop, the idle
  wrap guardian (`snapshotFlow(...).collect { ... }`), and the snackbar
  auto-dismiss `delay(3500)`.
- **No explicit locks** in this file. Serialization of vehicle
  commands/status is enforced downstream in `BlueLinkGate.statusMutex`
  (process-wide, on the phone side); the watch just fires `vm.*` intents.
- **Recomposition triggers**: a new `WearUi` from the caller (any car/settings/
  extras change), pager scroll (`currentPage`/`currentPageOffsetFraction`/
  `settledPage`/`isScrollInProgress`), list scroll (`layoutInfo`), and local
  state flips (rotary target, delete-confirm arming, etc.).

---

## 6. Collaborators & data flow

**Reads from (`ui`, function calls):**
- `WearUi` snapshot (see §4) — the phone→watch state, ultimately delivered over
  the Wear Data Layer (`WearSync` DataItem paths state/auth/settings/presets/
  climate/extras) and turned into `WearUi` by `WearViewModel`.
- `ui.pebbleOrderFor(vin)`, `ui.draftFor(vin)`, `ui.chargeDraftFor(vin)`.
- `WearPebbles.tilesFor(order, hidden)` (`:303`) — expands pebble order to tile keys.
- `WearTiles.*` constants for dispatch.
- `schemeFrom(carRoles)` (`:283`) — builds a per-car `ColorScheme`.

**Writes to / commands (`vm` calls in lines 1–1050):**
- `vm.onCarShown(vin)` (`:250`), `vm.dismissMessage()` (`:289`).
- `vm.toggleLock(vin)` (`:900`), `vm.toggleClimate(vin)` (`:910`,`:951`).
- `vm.setClimateTemp(vin, °F)` (`:958`), `vm.setClimateDuration(vin, min)` (`:959`),
  `vm.toggleDefrost(vin)` (`:967`).
- `vm.smartClimate(vin)` (`:1020`).
- (Past line 1050, still dispatched from cards in this file: `vm.applyPreset`,
  `vm.saveCurrentAsPreset`, `vm.deletePreset`, `vm.toggleCharge`, `vm.setAcLimit`/
  `setDcLimit`/`applyChargeLimits`, `vm.refreshStatus`, `vm.ensurePlaceName`,
  `vm.flashLights`/`hornAndLights`, `vm.requestAiSummary`, `vm.downloadAndInstallUpdate`/
  `snoozeUpdate`, `vm.savePebbleOrder`/`refreshTileWidgets`, and the seat setters.)

**Out to the phone / OS (via `WearRemote`, past line 1050):**
`WearRemote.openOnPhone(context, url)` and `WearRemote.dialOnPhone(context, phone)`
hand maps/service/owner/roadside actions to the phone (the watch has no browser
or dialer).

**Called by:** the watch app's top-level nav host (not in this file) composes
`HomeScreen(vm, ui, onSettings, onTrips, onReorder)`; `onSettings`/`onTrips`/
`onReorder` route to Settings, the Trips screen, and `TileReorderScreen`.

**UI building blocks (shared, defined in Components.kt or elsewhere in file):**
`SectionCard`, `MorphButton`, `SliderRow`, `StatusRow`, `ChargeRing`,
`AnimatedValue`, `MapThumbnail`, `rememberWearTextInput`, `WearColors`,
`fmtMinutes`, `relativeLabel`, `tempColor`, `weatherIcon`/`weatherLabel`/
`weatherTemp`, `seatStepLabels`, `dropShadow` (uicommon), `roundSafeHorizontalPadding`.

---

## 7. Invariants & assumptions

- `ui.cars.isEmpty()` is handled before any pager state exists — no page indexing
  happens on an empty list (`:201`,`:228`).
- The `key(ui.cars.map { it.vin })` boundary assumes the pager/pager-state can be
  discarded whenever the VIN set changes; `listStates` must stay outside it to
  preserve scroll positions. `retainAll` (`:237`) is what keeps `listStates` from
  leaking removed VINs.
- `page` is always a valid index into `ui.cars` inside the pager (pager count ==
  `ui.cars.size`, both inside the same `key` block) — `ui.cars[page]` (`:267`) is
  assumed non-throwing.
- `active` is true for at most one page at a time (`settledPage && !isScrollInProgress`);
  the focus/rotary logic assumes exactly the settled page owns crown/bezel input,
  so pre-composed neighbors must pass `active=false`.
- `centerItemIndex` relies on the **default** `ScalingLazyListAnchorType.ItemCenter`
  never being overridden — `it.offset` being center-relative is load-bearing
  (`:415`–`:421`). Overriding the anchor would silently reintroduce the
  double-counting focus bug.
- Wrap-around math assumes `cycles = 24` and the guardians keep the user within
  `tileCount*2` of center; the phase formula `((newTarget % tileCount) + tileCount) % tileCount`
  assumes it must cope with negative `newTarget`.
- `visibleTiles`/`CarColumn` tile keying assumes `visibleTiles()` reads only the
  fields listed in the `remember(...)` key set (`:350`–`:362`) — adding a new
  data dependency to `visibleTiles` without adding it to the key list would leave
  the tile list stale.
- `SummaryCard.isStale` is not a live clock: it only refreshes on some other
  recomposition (`:806`,`:811`).
- Climate/Comfort sliders assume `ui.draftFor(vin)` reflects the user's
  in-progress choice and is the correct source for slider positions (not
  `car.*` reported state), while `car.climateOn` governs button styling/labels.
- SmartClimate assumes weather may be null indefinitely (never an in-flight
  request) → `enabled = weather != null`, never `pending`-spun for missing weather.

---

## 8. Gotchas & sharp edges

1. **`settledPage` vs `currentPage`.** `activeCarIndex`/`active` use `settledPage`
   (post-swipe) for focus + `onCarShown`, but `CurvedIndicator` uses `currentPage`
   (live, mid-drag) so the per-car dots track the finger immediately (`:243`,
   `:269`, `:287`, `:1848`). Mixing these up would either make dots lag or fire
   `onCarShown` mid-drag.
2. **`listStates` placement.** It MUST be outside `key(ui.cars.map { it.vin })`,
   or every VIN-list refresh wipes all scroll positions (`:232`–`:233`).
3. **`onCarShown` de-dupe.** `lastShownVin` (`:245`) prevents re-firing on
   back-and-forth swipes; a side effect (analytics/refresh ping) is assumed
   idempotent per "session of being viewed," not per settle.
4. **Rotary accumulation reset-not-carry.** `rotaryAccumPx = 0f` on step (`:463`)
   deliberately drops leftover travel so one oversized bezel detent can't
   double-step. Carrying it would make low-res bezels overshoot.
5. **Immediate boundary remap in the rotary handler** (`:473`) exists because the
   idle guardian (`:435`) only fires when `isScrollInProgress` becomes false — a
   continuous roll never idles, so without the immediate remap the user got stuck
   at index 0/`total-1` and had to reverse direction to unstick.
6. **`centerItemIndex` double-count bug** (fixed): do NOT add `it.size/2` to
   `it.offset`; the library already bakes in half the item size under the default
   anchor (`:415`–`:421`).
7. **Reorder-snap keyed on `tiles`, not the pebble map** (`:386`): keying on the
   whole `pebbleOverride` map made reordering one car snap every on-screen car
   back to Summary.
8. **Idle wrap guardian keyed on `(tileCount, total)`, not `Unit`** (`:435`): a
   `Unit`-keyed effect judged boundaries with stale counts as the tile set grew/
   shrank async (weather/alerts arriving), dead-ending or teleporting to the
   wrong tile.
9. **`TileContent` is intentionally not memoized** (`:606`–`:615`): it's called
   once per virtual index so the same key composes at many indices at once for
   the wrap-around illusion. Caching it would break the endless scroll.
10. **`scalingParams(edgeScale=1f, edgeAlpha=1f)`** (`:489`) disables Wear's
    default edge shrink/fade on purpose — a prior focus-zoom read as tiles
    receding with dead gaps.
11. **`MessageSnackbar` is hoisted to HomeScreen** (`:549`–`:553`), not per-page,
    so neighbor pages don't each start their own 3.5s dismiss timer.
12. **Lock button highlights the UNLOCKED state** (`active = !locked`, `:897`) —
    intentional: the unlocked car is the noteworthy state, matching the phone
    tile/complication.
13. **SmartClimate: `enabled = weather != null` (not `pending`)** (`:1019`) — a
    missing weather reading is passive, not in-flight; spinning read as "stuck
    loading forever."
14. **Accessibility is deliberate and load-bearing**: the alert badge (`:827`),
    the standalone/disconnected dot (`:739`), and the snackbar liveRegion +
    dismiss label (`:588`) all carry `semantics` because they'd otherwise be
    invisible/unlabelled to TalkBack. The removed long-press-refresh gesture on
    `CarNameOverlay` (`:688`–`:698`) was dropped partly because it had no
    accessible equivalent.
15. **`degLabel`/Fahrenheit resolution** appears twice with the same expression
    (`ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false`,
    `:957`,`:998`): display-unit only; the smart-climate math is always done in °F
    regardless.
