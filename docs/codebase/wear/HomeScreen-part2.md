# HomeScreen.kt — Part 2 (cards B + actions + reorder), lines 1050–2080

File: `C:\Users\AdiPerets\Bloo\wear\src\main\java\com\bloo\wear\ui\HomeScreen.kt`
(total 2081 lines). This document covers the **second half** of the file:
the mid/lower stack of per-tile "cards" (`ComfortCard` tail → `MoreCard`), the
shared bits (`seatLabel`, `CurvedIndicator`), and the full-screen
`TileReorderScreen`. Part 1 covers `HomeScreen`, `CarColumn`, `TileContent`,
`CurvedDotIndicator`, `CarNameOverlay`, `MessageSnackbar`, `AlertsCard`,
`SummaryCard`, `ClimateCard`, `SmartClimateCard`, and the head of `ComfortCard`.

---

## 1. Purpose

This unit is the **card catalog + reorder editor** of the Wear OS home surface.

- Every composable named `*Card` here is one **vertically-scrollable tile** in a
  car's `CarColumn` (Part 1). `TileContent` (Part 1, `HomeScreen.kt:617`)
  dispatches a tile-key string (`WearTiles.*` / `TILE_ALERTS`) to exactly one of
  these composables. Each card is `private` and renders a single
  `SectionCard(...)` (defined in `Components.kt`, same package) — the shared
  "uppercase bold primary-tinted header + content column" chrome.
- The cards split into two behavioral families:
  - **Read-only status cards** — `ChargeCard`, `WeatherCard`, `InfoCard`,
    `DiagnosticsCard`, `AiCard`, `LocationCard` (mostly): render fields off the
    car's last-reported `CarView` snapshot; recompose only when a fresh status
    push replaces `car`.
  - **Action/draft cards** — `ComfortCard`, `PresetsCard`, `LimitsCard`,
    `AssistCard`, `MoreCard`: emit commands to `WearViewModel` (`vm.*`) or write
    into per-car in-memory *drafts* (`ui.draftFor` / `ui.chargeDraftFor`).
- `TileReorderScreen` is a **separate full-screen route** (public), navigated to
  from `MoreCard`'s "Reorder tiles" button via the `onReorder(vin)` callback. It
  is a drag-to-reorder editor for the car's pebble groups, mirrored to the phone.

Why it exists: the watch replicates the phone's per-car control surface as a ring
of self-contained cards so each remote command (lock, climate, charge, horn, AI
summary, etc.) is reachable in one bezel/crown detent, and so the card order can
be personalized per car and kept in lock-step with the phone.

---

## 2. Public surface

Almost everything in this range is `private`. The only **public** declaration in
the unit is:

### `TileReorderScreen` — `HomeScreen.kt:1882`
```kotlin
@Composable
fun TileReorderScreen(vm: WearViewModel, ui: WearUi, vin: String)
```
Full-screen drag-to-reorder editor for one car's **pebble groups** (not
individual tiles — a pebble can own several tiles, and they move as a unit).
Renders a `ScalingLazyColumn` with a header (`"Reorder tiles"` + hint) and one
draggable `Card` row per pebble key. On drop (or a TalkBack Move up/down action)
it commits the new order to the view model and forces a Tile widget redraw.
Summary is pinned first and is **not** in the reorderable list. Details in §3.

All other declarations below are `private` (or `internal` in Part 1) to this
file; documented here because `TileContent` (Part 1) is the only caller and this
is the reference for what each tile renders.

---

## 3. Internal structure — card by card

Cards appear here in file order. For each: signature, what it renders, control
flow. All are `@Composable private fun`, most using the expression-body form
`... = SectionCard("Title", Icon) { ... }`.

### `ComfortCard(vm, ui, car)` — `HomeScreen.kt:1058` (tail: header comment starts at line 1050)
`SectionCard("Comfort", Icons.Filled.AirlineSeatReclineNormal)`. Steering-wheel
heat + per-seat heat/vent level controls. Everything reads/writes the per-car
draft `d = ui.draftFor(car.vin)`.

Control flow:
1. `val d = ui.draftFor(car.vin)`; `val seats = ui.settings?.seatConfigs?.get(car.vin) ?: WearSeatConfig()` (`:1059-1060`). The `WearSeatConfig()` default (driver + passenger heat only) matches the phone's default when the car hasn't synced its capability yet.
2. Helper caption `"Applied when you start climate"` under the header (`:1065`) — these settings are **not** sent on their own; they ride along the next climate start.
3. If `seats.steeringWheel` (`:1067`): a `MorphButton` (`vm.toggleSteering`), label `"Steering heat on"`/`"Steering heat"`, `activeColor = WearColors.heat`, `pending = false`.
4. `showRearRow = seats.rearLeftHeat || seats.rearRightHeat` (`:1084`). If rear rows exist **and** any front row exists, render a `"Front"` subheader (`:1085`).
5. Front sliders (each gated on its capability flag): `"Driver seat"` (`vm.setSeatDriver`, `:1090`) and `"Passenger"` (`vm.setSeatPassenger`, `:1093`). Range `0..3` step `1`, label from `seatStepLabels[d.seatDriver]` etc., `accent = WearColors.heat`.
6. If `showRearRow`: `"Rear"` subheader then `"Rear left"` (`vm.setSeatRearLeft`) / `"Rear right"` (`vm.setSeatRearRight`) sliders, same `0..3` shape (`:1095-1104`).

Note the gating source-of-truth change (comment `:1078-1083`): rows are gated on
the **synced `WearSeatConfig`**, not on live-status non-null `seatRl/seatRr`
(the car API can't reliably report seat-heat capability).

### `PresetsCard(vm, ui, car)` — `HomeScreen.kt:1138`
`SectionCard("Presets", Icons.Filled.Thermostat)`. List of saved climate presets
+ save/delete. This is the **most stateful** card in the unit.

Local state:
- `var confirmDeleteId by remember(car.vin) { mutableStateOf<String?>(null) }` (`:1140`) — which single preset (if any) is armed for a two-tap delete confirm. Keyed on `car.vin` so swiping to another car resets it.

Control flow:
1. `list = ui.presets[car.vin].orEmpty()` (`:1139`) — presets come from view-model state synced with the phone, not from the local draft.
2. `LaunchedEffect(confirmDeleteId)` (`:1144-1149`): if non-null, `delay(4000)` then reset to null — auto-disarm so a stale "tap again to confirm" can't fire against a later unrelated tap. Matches Settings' "Sign out" confirm pattern.
3. Empty state (`:1150-1157`): explainer text `"Save the current climate settings as a preset."`
4. `list.forEach { preset }` (`:1158`):
   - `isActive = ui.draftFor(car.vin).activePresetId == preset.id && car.climateOn == true` (`:1159`) — active means both the applied preset **and** climate actually on.
   - `confirming = confirmDeleteId == preset.id` (`:1160`).
   - `Row` containing: the preset `MorphButton` (`weight(1f)`) whose `onClick` (`:1172-1175`) clears `confirmDeleteId`, then toggles climate off if `isActive` (`vm.toggleClimate`) else applies (`vm.applyPreset(car.vin, preset)`); and a circular delete control.
   - The delete control (`:1180-1235`) animates:
     - `delBg` via `animateColorAsState(tween(120))` → `error` when confirming, else `errorContainer.copy(alpha=0.7f)`.
     - `delFg` via `animateColorAsState(tween(120))` → `onError` / `onErrorContainer`.
     - `delScale` via `animateFloatAsState(spring(MediumBouncy, StiffnessHigh))` → `0.88f` when pressed (press feedback), driven by a `MutableInteractionSource` + `collectIsPressedAsState`.
     - The 36.dp `Box` uses `clickable(indication = null)`: if `confirming` → `vm.deletePreset(car.vin, preset.id)` + reset; else arm `confirmDeleteId = preset.id`.
     - Icon is `Check` (`"Confirm delete"`) when confirming, else `Close` (`"Delete preset"`).
5. Save row (`:1239-1247`): `saveInput = rememberWearTextInput("Preset name") { name -> vm.saveCurrentAsPreset(car.vin, name) }` opens the watch text/voice entry UI; the "Save preset" `MorphButton`'s `onClick` is that returned lambda. `saveCurrentAsPreset` snapshots the **current draft**, not any listed preset.

### `ChargeCard(vm, ui, car)` — `HomeScreen.kt:1265`
`SectionCard("Charge", Icons.Filled.Bolt)`. EV charge status + start/stop. No
local state — pure reads off `car`.
- `metric = ui.localSettings.unitSystem == "metric"` (`:1266`).
- `MorphButton` (`:1267-1275`): label `"Charging — stop"`/`"Start charge"`, `active = car.charging == true`, `activeColor = WearColors.chargeGreen`, `pending = "${car.vin}:charge" in ui.pending`, `onClick = vm.toggleCharge(car.vin)`, `toggled` mirrors charging.
- Status rows, each conditional via `?.let`: `"Battery" $it%` (`car.percent`), `"Range" formatDistance(it, metric)` (`car.rangeMi`), `"Plug"` = `car.chargerLabel ?: (if pluggedIn "Plugged in" else "Unplugged")` (always shown), `"Time to full" fmtMinutes(it)` for `car.timeToFullMin?.takeIf { it > 0 }`.

### `LimitsCard(vm, ui, car)` — `HomeScreen.kt:1312`
`SectionCard("Charge limits", Icons.Filled.Bolt)`. AC/DC charge-limit sliders
with an explicit **Apply** step (all other slider cards commit on drag).

Reconciliation logic:
- `draft = ui.chargeDraftFor(car.vin)` — nullable `ac`/`dc` (null = "untouched", not zero).
- `ac = draft.ac ?: car.acLimit ?: 80`; `dc = draft.dc ?: car.dcLimit ?: 90` (`:1314-1315`) — prefer draft, then car's real reported limit, then a hardcoded 80/90 guess.
- `isDirty = (draft.ac != null && draft.ac != car.acLimit) || (draft.dc != null && draft.dc != car.dcLimit)` (`:1316-1317`) — a draft equal to the car's real value reads as **not** dirty.
- Header caption: `"Unsaved changes"` (tertiary) when dirty, else `"Adjust, then tap Apply"`.
- Two `SliderRow`s over `CHARGE_LIMIT_RANGE.first..last` step 10 → `vm.setAcLimit` / `vm.setDcLimit` (write into draft).
- `MorphButton("Apply limits")` → `vm.applyChargeLimits(car.vin)`, `pending = "${car.vin}:chargeLimit" in ui.pending`, **`enabled = isDirty`** so a stray tap can't push the 80/90 guess.

### `LocationCard(vm, ui, car)` — `HomeScreen.kt:1352`
`SectionCard("Location", Icons.Filled.LocationOn)`. Map thumbnail + resolved
place + refresh/open buttons.
1. `lat = car.lat; lon = car.lon`; `if (lat == null || lon == null) return@SectionCard` (`:1356-1358`) — explicit guard even though `visibleTiles()` (Part 1) already gates on non-null coords, so a future reordering can't render `0.0, 0.0`.
2. `LaunchedEffect(car.vin, car.lat, car.lon)` (`:1366-1368`): calls `vm.ensurePlaceName(car.vin, lat, lon)` — reverse-geocode. Keyed on the actual fix so it only re-fires on a coordinate change; `ensurePlaceName` is expected to cache/no-op internally.
3. `MapThumbnail(lat, lon)`; then place text = `car.locationName ?: "%.4f, %.4f".format(lat, lon)`.
4. If `car.engineOn`: `"Engine running"` (tertiary).
5. `relativeLabel(car.fetchedAt)` freshness line if non-blank.
6. `MorphButton("Locate")` → `vm.refreshStatus(car.vin)` (`pending = "${car.vin}:refresh" in ui.pending`).
7. `MorphButton("Open on phone")` → `WearRemote.openOnPhone(context, "https://www.google.com/maps/search/?api=1&query=$lat,$lon")` (`pending = false`).

### `WeatherCard(ui, car)` — `HomeScreen.kt:1420`
Note: **no `vm` parameter** (read-only). Structure is unusual — the header icon
is resolved *before* the `SectionCard` call so it can use the per-condition glyph.
1. `w = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather` (`:1429`) — same car-first/home-fallback lookup as `SmartClimateCard` so the two agree.
2. `headerIcon = w?.let { weatherIcon(it.code, it.isDay) } ?: Icons.Filled.WbSunny` (`:1430`).
3. `SectionCard("Weather", headerIcon) { ... }`:
   - If `w == null`: `"No weather data available"` then `return@SectionCard` (`:1435-1442`).
   - `f = ui.settings?.useFahrenheit != false` (`:1443`).
   - Row: condition icon (`weatherTint`), temp (`weatherTemp(w.tempC, f)`) + `weatherLabel(w.code)`, and an optional High/Low column (`w.highC`/`w.lowC`, only if either non-null, `weight(1f)` end-aligned).
   - `StatusRow("Feels", weatherTemp(w.feelsLikeC, f))`; `"Humidity" $it%` (`w.humidity?`); `"Wind"` via `formatSpeed(w.windKph.toDouble(), metric)` only when `w.windKph > 0`.

### `InfoCard(car, ui)` — `HomeScreen.kt:1468`
Note parameter order `(car, ui)`. `SectionCard("Info", Icons.Filled.DirectionsCar)`.
Flat status list with a roll-up row on top.
- `fahrenheit = ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false` (`:1469`).
- Roll-up: `openCount = doorsOpen.size + windowsOpen.size + (trunkOpen?1:0) + (hoodOpen?1:0)` (`:1474-1475`); row label `"Open"`/`"Closed up"`, value `"$openCount item(s)"`/`"All secure"`, error-tinted when >0.
- Rows: `"Engine"` On/Off; `"Set temp"` = `degLabel(car.tempSetting, fahrenheit)` (only if non-null); `"Climate"`, `"Defrost"` (`car.defrostOn`), `"Accessory"` (`car.accessoryOn`) On/Off.
- `"Doors"` / `"Windows"` labels: `"All closed"` / single name / `"N open"`, error-tinted when open.
- `"Trunk"`/`"Hood"` only when open. `"VIN"` = `car.vin.takeLast(6)`.
- Odometer: `odoInt = car.odometer?.replace(",", "")?.toDoubleOrNull()?.toInt()` (`:1507`) — de-comma the display string, parse as **Double** (some brands include decimals), then truncate. Row `"Odometer"` shows the raw display string. `"Plate"` from `car.licensePlate` if non-blank.
- Service-due (`:1519-1527`): only when `lastServiceMiles` **and** `serviceIntervalMiles` both non-null. `nextDue = lastSvc + interval`; `remaining = odoInt?.let { nextDue - it }`. Row `"Service due"` shows `"in <dist>"` (remaining, coerced ≥0) if odometer parsed, else `"at <dist>"` (absolute nextDue). Error-tinted when `remaining <= 0`.

### `DiagnosticsCard(car)` — `HomeScreen.kt:1531`
`SectionCard("Diagnostics", Icons.Filled.Build)`. Read-only. Roll-up + detail.
- `anyIndividualTire = tireFl || tireFr || tireRl || tireRr` (`:1538`) — whether the car reports per-wheel tire warnings vs. only the aggregate `tireWarning`.
- `issueCount` counts `true` among: `anyIndividualTire`, `tireWarning`, `battery12v != null && < 20`, `lowFuel`, `washerLow`, `brakeLow`, `keyFobLow` (`:1544-1548`).
- Roll-up row `"Needs attention" / "$issueCount to check"` vs `"Status" / "All normal"`, error-tinted when >0.
- Tire rows: `"Tire avg" "${tireAll} psi"` if `tireAll != null`; then per-wheel `"Tire FL/FR/RL/RR" "Check"` (err) when `anyIndividualTire`; else `"Tires" "Check pressure"` (err) if `tireWarning`; else `"Tires" "OK"` only if `tireAll == null` (avoids a redundant OK when avg row already shown).
- `"12V"` = `"$v12%$h"` where `h = " · $health"` if `battery12vHealth` present; err when `<20`.
- `"Fuel" "$it%"` (err if `lowFuel`); `"Washer"`/`"Brake fluid"`/`"Key fob"` `"Low"` rows only when set.
- `"Steering" "Heating"` (`car.steerHeat`, `WearColors.heat`); `"Mirrors" "Heating"` (`mirrorHeat`); `"Rear defrost" "On"` (`rearDefrost`).
- Per-seat: `seatLabel(car.seatFl/Fr/Rl/Rr)?.let { StatusRow("Seat FL", it) }` — `seatLabel` returns null for level 0 so no dead "Off" rows.
- `"Time to full"` (`timeToFullMin > 0`); `"Charger"` from `chargerLabel`.

### `AiCard(vm, ui, car)` — `HomeScreen.kt:1603`
`SectionCard("AI Summary", Icons.Filled.AutoAwesome)`. No local state.
- `summary = ui.extras.ai[car.vin]`; `busy = ui.aiBusy == car.vin` (`:1604-1605`) — `aiBusy` is a single shared VIN-or-null in the VM; comparing to `car.vin` scopes the busy state so only the matching car's card shows "thinking".
- Three-way content: summary text (if `summary != null && !busy`); `"Thinking…"` (if `busy`); else explainer `"A quick plain-English rundown…"`.
- `MorphButton`: label `"Summarizing…"`/`"Refresh"`/`"Summarize"` (busy / has-summary / neither), `pending = busy`, `onClick = vm.requestAiSummary(car.vin)`.

### `AssistCard(car)` — `HomeScreen.kt:1653`
`SectionCard("Assist", Icons.Filled.Call)`. Fully static — never recomposes once
`car.brand` is set (reads no live status).
- `links = car.brand.links` (per-brand phone numbers/URLs).
- Three buttons, all handed to the phone via `WearRemote` (watch has no dialer/browser): `"Roadside"` → `WearRemote.dialOnPhone(context, links.roadsidePhone)`; `"Schedule service"` → `openOnPhone(links.serviceScheduleUrl)`; `"Owner site"` → `openOnPhone(links.ownersUrl)`.

### `MoreCard(vm, ui, car, onSettings, onTrips, onReorder)` — `HomeScreen.kt:1717`
`SectionCard("More", Icons.Filled.Settings)`. Catch-all: alerts summary, refresh,
brand-gated horn/lights, trips, Settings, Reorder, and the update banner.
1. If `car.alertCount > 0`: `StatusRow("Alerts", "$alertCount open")` error-tinted.
2. `MorphButton("Refresh")` → `vm.refreshStatus(car.vin)` (`pending = "${car.vin}:refresh" in ui.pending`).
3. **Horn/lights**, only if `car.brand != Brand.KIA` (`:1738`) — Kia's US API has no such endpoint (`Vehicle.supportsHornLights`). A `Row` of two `weight(1f)` buttons sharing `hlPending = "${car.vin}:hornLights" in ui.pending`: `"Flash lights"` → `vm.flashLights(car.vin)`; `"Horn"` → `vm.hornAndLights(car.vin)`.
4. **Trips**, only if `car.hasBattery && car.tripsSupported` (`:1762`): `MorphButton("Trips")` → `onTrips(car.vin)`.
5. `MorphButton("Settings")` → `onSettings`. `MorphButton("Reorder tiles")` → `onReorder(car.vin)`.
6. **Update banner**, only if `ui.updateRun != null` (`:1797`):
   - `MorphButton` label `"Downloading…"`/`"Update available"` keyed on `ui.updateDownloading`, `active = true`, `pending = ui.updateDownloading`, `onClick = vm.downloadAndInstallUpdate()` (downloads the watch APK on-device and hands to the system installer — no phone needed).
   - `ui.updateRun.releaseNotes?.takeIf { it.isNotBlank() }` rendered `maxLines = 12` ellipsized (this card has no scroll of its own but sits in the outer `ScalingLazyColumn`).
   - `MorphButton("Remind me")` → `vm.snoozeUpdate()` — the only dismissal (scrolling past also works, since it's a passive row, not a dialog).

### `seatLabel(v: Int?): String?` — `HomeScreen.kt:1837`
```kotlin
private fun seatLabel(v: Int?): String? = v?.takeIf { it != 0 }?.let { SeatLevel.fromApi(it).label }
```
Maps a seat API level int to a display label, returning **null** for null or 0
(0 = off/unsupported → no row). Used by `DiagnosticsCard`.

### `CurvedIndicator(count, current, anchor)` — `HomeScreen.kt:1851`
```kotlin
@Composable private fun CurvedIndicator(count: Int, current: Int, anchor: Float)
```
One dot **per car** along the round-screen arc (contrast Part 1's
`CurvedDotIndicator`, one dot per tile). Early-returns if `count <= 1`. Uses
`CurvedLayout` + `curvedRow` + `curvedComposable`; `repeat(count)` draws a 7.dp
(selected) or 4.dp (unselected) circle, colored `primary`/`outlineVariant`.
`current` is driven by `carPager.currentPage` (live, mid-drag) so this tracks the
swipe immediately (Part 1 passes `anchor = 90f` to hug the bottom).

### `TileReorderScreen` — `HomeScreen.kt:1882` (public; see §2)
See §3 detail below.

---

## 3b. `TileReorderScreen` control flow (the non-trivial one)

State (`:1889-1900`):
- `synced = WearPebbles.reorderable(ui.pebbleOrderFor(vin))` — source-of-truth order derived fresh from the phone's pebble order every recomposition (Summary excluded — it's pinned).
- `var order by remember(vin) { mutableStateOf(synced) }` — the local, mutable, rendered/dragged copy. Kept separate from `synced` so an in-progress drag isn't clobbered by an incoming sync.
- `var draggingKey by remember { mutableStateOf<String?>(null) }` — the one row being long-press-dragged.
- `var offsetY by remember { mutableFloatStateOf(0f) }` — that row's cumulative drag distance in px (reset to 0 on start/end).
- `val heights = remember { mutableStateMapOf<String, Int>() }` — measured on-screen row heights by key (via `onSizeChanged`), needed for the "crossed half the neighbor" swap threshold.

Sync adoption (`:1903-1905`): `LaunchedEffect(synced) { if (draggingKey == null) order = synced }` — adopt incoming phone changes **only when not dragging**.

Scroll/focus: `rememberScalingLazyListState()`; rotary handler just
`scope.launch { state.scrollBy(e.verticalScrollPixels) }` (simpler than
`CarColumn`'s one-tile-per-detent snap — this list scrolls freely). Focus claimed
once via `LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }`.

`commit()` (`:1913-1919`): `vm.savePebbleOrder(vin, listOf("summary") + order)`
(re-pin Summary first, push to phone) then `vm.refreshTileWidgets()` (redraw the
glanceable Tile immediately rather than waiting for the phone echo or the Tile's
own poll).

List body (`ScalingLazyColumn`, `:1921`):
- Header item: `ListHeader { Text("Reorder tiles") }` + hint `"Long-press a row then drag to reorder"`.
- `items(order, key = { it })` — one row per pebble key:
  - `dragging = draggingKey == key`; `lift by animateFloatAsState(if dragging 1.04f else 1f)` — scale bump.
  - **Displacement slide-in** (`:1956-1970`): because `ScalingLazyColumn` has no `Modifier.animateItem()`, displaced (non-dragged) rows are animated manually. `prevIdx` (remembered per key) tracks the row's last index; `slideOffset = Animatable(0f)`. On `LaunchedEffect(idx)`: if not dragging and `idx != prevIdx`, `snapTo((prevIdx - idx) * rowH)` then `animateTo(0f, spring(SoftDamping, StiffnessMediumLow))` — slides the row in from its old slot. `rowH` falls back to 64 if unmeasured.
  - Row `Box` `graphicsLayer`: `translationY = if dragging offsetY else slideOffset.value`; `scaleX/scaleY = lift`; `zIndex(if dragging 1f else 0f)`; `onSizeChanged { heights[key] = it.height }`.
  - `Card` with animated `cardTint` (`animateColorAsState` → `surfaceContainerHigh` when dragging else `surfaceContainerLow`). **`contentColor` is explicitly set** to `onSurface` (`:1995`) because a mid-animation interpolated `cardTint` would miss `contentColorFor`'s exact-match lookup and fall back to an ambient color.
  - **Accessibility** (`:2005-2027`): `stateDescription = "Position ${cur+1} of ${order.size}"` (re-reads after each move since `order` is state); `customActions` = `"Move up"` (if `cur > 0`) and `"Move down"` (if `cur in 0 until lastIndex`), each mutating `order` (remove+insert) then `commit()`. This is the TalkBack-equivalent of dragging (the drag gesture itself has no a11y).
  - **Drag gesture** (`:2028-2056`): `pointerInput(key)` + `detectDragGesturesAfterLongPress`:
    - `onDragStart`: set `draggingKey = key`, `offsetY = 0f`, fire `HapticFeedbackType.LongPress`.
    - `onDragEnd`: clear dragging, `offsetY = 0f`, `commit()`.
    - `onDragCancel`: clear dragging, `offsetY = 0f` (**no commit** — cancel discards).
    - `onDrag`: `change.consume()`; `offsetY += dragAmount.y`. If dragging **down** past `nextH/2` (half the next row's measured height), swap `cur` with `cur+1` and subtract `nextH` from `offsetY`; symmetric for dragging **up** past `prevH/2`, adding `prevH`. Uses measured `heights`; skips if the neighbor height is 0/unknown.
  - Row content: `DragHandle` icon (`"Drag to reorder"`) + label `WearPebbles.LABELS[key] ?: key`.

---

## 4. Data & types

**No data classes / enums / sealed types are declared in this range.** Everything
is composables + the two file-private helpers (`seatLabel` here; the head-of-file
`alertCount` extension and `TILE_ALERTS` const are Part 1). The unit *consumes*
types defined elsewhere; encodings that matter for reading this code:

- `CarView` (`com.bloo.wear.CarView`) — the per-VIN status snapshot. Fields read here include: `charging: Boolean?`, `percent: Int?`, `rangeMi`, `chargerLabel: String?`, `pluggedIn: Boolean?`, `timeToFullMin: Int?`, `acLimit/dcLimit: Int?`, `lat/lon: Double?`, `locationName: String?`, `engineOn: Boolean`, `climateOn: Boolean?`, `defrostOn/accessoryOn: Boolean`, `tempSetting: String?`, `doorsOpen/windowsOpen: List<String>`, `trunkOpen/hoodOpen: Boolean`, `vin: String`, `odometer: String?` (display-formatted, e.g. `"12,345"`), `licensePlate: String?`, `lastServiceMiles/serviceIntervalMiles: Int?`, tire flags `tireFl/Fr/Rl/Rr: Boolean` + aggregate `tireWarning: Boolean` + `tireAll: Int?` (psi), `battery12v: Int?` + `battery12vHealth: String?`, `lowFuel/washerLow/brakeLow/keyFobLow: Boolean`, `fuelLevel: Int?`, `steerHeat/mirrorHeat/rearDefrost: Boolean`, `seatFl/Fr/Rl/Rr: Int?` (SeatLevel apiValue), `brand: Brand`, `hasBattery: Boolean`, `tripsSupported: Boolean`, `fetchedAt: Long?`.
- `SeatLevel.apiValue` encoding (per project facts): 0=off, 3–5=cool, 6–8=heat. `seatLabel` treats 0 as null (no row). `SeatLevel.fromApi(it).label` produces the display string.
- `WearSeatConfig` (`com.bloo.bluelink.data`) — synced seat capability flags: `steeringWheel`, `driverHeat`, `passHeat`, `rearLeftHeat`, `rearRightHeat`. Default ctor = driver + passenger heat only.
- `WearWeather` — `code: Int`, `isDay: Boolean`, `tempC`, `feelsLikeC`, `highC/lowC: Double?`, `humidity: Int?`, `windKph`. Fahrenheit conversion for logic goes through `com.bloo.bluelink.data.ambientFahrenheit` (SmartClimate, Part-1-adjacent); display via `weatherTemp`.
- Draft types (defined in `WearViewModel`/`WearUi`, not here): the climate draft `d` (fields `tempF`, `duration`, `defrost`, `steering`, `seatDriver/seatPassenger/seatRearLeft/seatRearRight`, `activePresetId`) and the **charge draft** with **nullable** `ac`/`dc` (null = untouched).
- `WearTiles.*` string keys and `WearPebbles.LABELS`/`reorderable`/`tilesFor` — tile identity + pebble grouping (Part 1 / `WearPebbles`).
- `Brand` enum — only `Brand.KIA` is compared here (horn/lights gate).

Ranges/constants used: `CHARGE_LIMIT_RANGE` (`com.bloo.bluelink.data`) for the
Limits sliders; the 80/90 AC/DC fallback guess; seat slider `0..3`; temp
fallback threshold 70°F in SmartClimate.

---

## 5. State & concurrency

- **Recomposition:** each card is a leaf reading `car` / `ui`. A fresh status push replaces the whole `CarView` for a VIN → the card recomposes. `ui.pending` (a set of `"$vin:$action"` strings) drives every `MorphButton`'s `pending` spinner. `ui.presets`, `ui.extras.*`, `ui.updateRun`, `ui.updateDownloading`, `ui.aiBusy`, `ui.settings`, `ui.localSettings` are all VM-held observable state; touching any recomposes the readers.
- **Drafts** are in-memory VM state (`ui.draftFor(vin)` / `ui.chargeDraftFor(vin)`); slider `onValueChange`/button callbacks write straight into them via `vm.set*` — no coroutine here, the VM owns any dispatch.
- **Local `remember` state** exists only in `PresetsCard` (`confirmDeleteId`, keyed on `car.vin`; plus the delete control's `MutableInteractionSource` and animation `State`s) and `TileReorderScreen` (`order`, `draggingKey`, `offsetY`, `heights`, per-row `prevIdx`/`slideOffset`).
- **Coroutines / scopes:** `TileReorderScreen` uses `rememberCoroutineScope()` for `scope.launch { state.scrollBy(...) }` on rotary. `LaunchedEffect`s: `PresetsCard` delete auto-disarm (`delay(4000)`), `LocationCard` `ensurePlaceName`, `TileReorderScreen` sync-adoption / focus / per-row slide animation. All run on the composition's effect scope (main dispatcher).
- **Animations:** `animateColorAsState`/`animateFloatAsState`/`animateDpAsState` (Presets delete, reorder tint/lift, CurvedIndicator dot size) and `Animatable` (reorder slide-in). No locks — everything is snapshot-state / single-threaded UI.
- No `StateFlow`/`DataStore`/`WorkManager` is touched directly in this range; those live behind the `vm.*` calls.

---

## 6. Collaborators & data flow

**Called by:** `TileContent` (Part 1, `HomeScreen.kt:626-642`) dispatches to every
`*Card` by tile key. `MoreCard`'s "Reorder tiles" button invokes the
`onReorder(vin)` callback that `HomeScreen` was handed → the host navigates to
`TileReorderScreen`.

**Calls into `WearViewModel` (`vm`):** commands `toggleSteering`,
`setSeatDriver/Passenger/RearLeft/RearRight`, `toggleClimate`, `applyPreset`,
`deletePreset`, `saveCurrentAsPreset`, `toggleCharge`, `setAcLimit/setDcLimit`,
`applyChargeLimits`, `refreshStatus`, `ensurePlaceName`, `requestAiSummary`,
`flashLights`, `hornAndLights`, `downloadAndInstallUpdate`, `snoozeUpdate`,
`savePebbleOrder`, `refreshTileWidgets`. Reads `ui.*` (see §5).

**Off-device hand-offs:** `WearRemote.openOnPhone(context, url)` and
`WearRemote.dialOnPhone(context, phone)` (Location "Open on phone"; Assist
roadside/service/owner) — the watch relays URLs/dials to the phone over the Wear
Data Layer (watch has no browser/dialer).

**Wear Data Layer flow (indirect):** presets, drafts, seat configs, weather,
AI summaries, pebble order, update-run all arrive from the phone via WearSync
DataItems and land in `WearUi`/`WearViewModel`; `savePebbleOrder` +
`refreshTileWidgets` push the reordered pebble order back down and force a Tile
redraw.

**Shared UI collaborators (same package / `uicommon`):** `SectionCard`,
`MorphButton`, `SliderRow`, `StatusRow`, `ChargeRing`, `MapThumbnail`,
`rememberWearTextInput` (Components.kt); `WearColors` (`heat`, `chargeGreen`);
formatting helpers `degLabel`, `formatDistance`, `formatSpeed`, `weatherIcon`,
`weatherLabel`, `weatherTemp`, `weatherTint`, `fmtMinutes`, `relativeLabel`,
`tempColor`, `seatStepLabels`, `roundSafeHorizontalPadding`, `SoftDamping`,
`dropShadow`.

---

## 7. Invariants & assumptions

- **`TileContent` only reaches a card when `visibleTiles()` (Part 1) allows it**, so cards assume their preconditions: `ChargeCard`/`LimitsCard` assume `car.hasBattery`; `LocationCard` assumes non-null lat/lon (still re-guards); `WeatherCard`/`SmartClimateCard` assume some weather exists (still handle null); `DiagnosticsCard` assumes `car.hasLiveStatus`; `AiCard` assumes `ui.settings?.aiEnabled == true`.
- `MoreCard` trips button assumes both `car.hasBattery && car.tripsSupported`; horn/lights assume `car.brand != Brand.KIA` (Kia US has no endpoint).
- `ui.draftFor(vin)` / `ui.chargeDraftFor(vin)` always return a non-null draft (never null) — cards read `d.field` unguarded.
- Charge-draft `ac`/`dc` **null means "untouched"**, not zero; `isDirty` and the `enabled` gate depend on this.
- `car.odometer` is a **display string** possibly containing commas/decimals; the service-due math must de-comma and `toDoubleOrNull` (not `toIntOrNull`) it. `remaining <= 0` means overdue.
- `ui.pending` keys follow the exact `"$vin:$action"` convention (`doors`, `climate`, `charge`, `chargeLimit`, `refresh`, `hornLights`). A typo silently disables the spinner.
- `TileReorderScreen`: `order` never contains `"summary"` (excluded by `WearPebbles.reorderable`); `commit()` re-prepends it. `synced` is adopted into `order` only while `draggingKey == null`. `heights[key]` may be missing (0/unmeasured) → swap logic no-ops for that neighbor and slide falls back to 64px.
- `AiCard` assumes `ui.aiBusy` is a single VIN-or-null (only one AI request in flight app-wide), scoping busy by `== car.vin`.
- `seatLabel` assumes API level 0 means off/unsupported.

---

## 8. Gotchas & sharp edges

- **Two-tap delete, not swipe (Presets):** `confirmDeleteId` arms a single preset; a 4-second `LaunchedEffect` auto-disarms so a stale "confirm" can't fire against a later tap. Tapping the preset **button** also clears it (`:1173`).
- **`WeatherCard` resolves its header icon *before* `SectionCard`** (`:1429-1431`) so the header glyph matches the current condition — the only card that computes header state outside the content slot. It has **no `vm`** param.
- **`InfoCard` parameter order is `(car, ui)`** — reversed from every other card's `(vm, ui, car)`. Easy to mis-call.
- **Limits `enabled = isDirty`** is load-bearing: the sliders show an 80/90 *guess* before any draft/real limit exists; without the gate, a stray "Apply" tap would push the guess to the car as if chosen. A draft dragged back to the car's real value correctly reads non-dirty.
- **Diagnostics tire-mode fork:** per-wheel rows vs. one aggregate row depend on `anyIndividualTire`; the `"Tires" "OK"` fallback only shows when `tireAll == null` too, to avoid a redundant OK beside the psi-average row.
- **Odometer parse is fragile by design** (`:1507`): `replace(",","")` then `toDoubleOrNull()?.toInt()` — if a brand's format defeats it, the service-due row degrades to `"at N mi"` instead of vanishing.
- **`SmartClimate`/`Weather` weather can be null for a while after launch** (arrives passively from the phone); SmartClimate's button is `enabled = weather != null` (disabled, **not** pending-spinner) so "no weather" doesn't read as "stuck loading."
- **`MoreCard` update banner has no scroll of its own** — release notes are `maxLines = 12` ellipsized; it relies on the outer `ScalingLazyColumn` for scroll. "Remind me" is the only dismiss; scrolling past also dismisses.
- **On-device self-update** (`vm.downloadAndInstallUpdate`) downloads the watch APK and hands to the system installer — no phone involved (the app is not on Play Store; it self-updates from GitHub Releases).
- **`TileReorderScreen` uses manual slide animation** because `ScalingLazyColumn` lacks `Modifier.animateItem()` (unlike `LazyColumn`). Displaced rows `snapTo` their old offset then `animateTo(0f)`.
- **`TileReorderScreen` `contentColor` is set explicitly** (`:1995`) — a mid-animation interpolated `cardTint` misses `contentColorFor`'s exact-match table and would fall back to an ambient color; pinning `onSurface` avoids a flicker of wrong text color during the drag-highlight crossfade.
- **`onDragCancel` does not `commit()`** (only `onDragEnd` does) — a cancelled drag discards the reorder; but note the `order` list was already mutated *during* `onDrag` swaps, so a cancel mid-drag leaves `order` at its last swapped state until the next `synced` adoption. (Adoption only happens once `draggingKey == null`, which cancel sets.)
- **Accessibility Move up/down commit immediately** and re-read `stateDescription` ("Position N of M") so TalkBack users get placement confirmation without re-navigating.
- **`Brand.KIA` horn/lights omission is intentional**, mirroring the official Hyundai/Genesis apps (Kia US telematics has no horn/lights endpoint).
