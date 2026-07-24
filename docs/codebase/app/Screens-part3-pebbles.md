# Screens.kt — Part 3: The detail "pebble" composables (lines ~6086–8200)

File: `app/src/main/java/com/bloo/bluelink/ui/Screens.kt`

This document covers the pebble subsystem: the shared collapsible-card
framework (`Pebble` / `PebbleShell` and its two header controls) plus every
individual detail pebble (Trips, Car info, Diagnostics, Climate, Charge, Fuel,
Location, Weather) and the leaf widgets they compose (`TripRow`, `PresetPill`,
`ChargeLimitPill`, `SeatControl`, `WeatherStripe`, `CarMap`, `OwnerLinks`,
`LinkButton`). It also documents the small colour-picker / palette-editor block
(lines 5219–5658) that sits just above, since it is inside the assigned range,
and the `open*/dial` intent helpers (8115–8139).

---

## 1. Purpose

A "pebble" is the app's unit of on-screen surface: a rounded card with a
tap-to-toggle header (icon + title + one-line summary + a right-side expand
control) that animates open to reveal a detail body. Each vehicle's detail
screen is a reorderable, collapsible stack of these pebbles, one per functional
area. This unit is the **catalogue of pebble bodies** — the code that renders
what's *inside* each section — plus the single shared shell (`PebbleShell`) that
gives them all identical chrome, expand/collapse motion, drag-to-reorder
plumbing, and accessibility structure.

Why a shared shell: with 8+ pebbles per car, a hand-rolled card per section
would drift in corner radius, header height, heading semantics, and animation
feel. Everything funnels through `Pebble`/`PebbleShell` so the update tile
(non-vehicle) and every vehicle section look and behave identically
(`PebbleShell` doc, 6116–6122).

The pebbles are dispatched from two call sites, both in earlier parts of this
file: the normal per-car list (`SinglePebble`, 5102–5132) and the cover-screen
vertical pager (3281–3288). Both pass the same `(v, status, state, vm,
Modifier)` shape.

---

## 2. Public surface

Everything in this range is `private` to the file (the pebbles are internal
building blocks). The genuinely reusable/exported item is `MorphButton`
(`fun`, 6696 — documented in Part 2 but referenced heavily here). `ToggleRow`
(10740) is also non-private. The list below is the pebble surface as it appears
in this range.

### Shell framework

- **`Pebble(v, section, title, icon, state, vm, dragHandle=Modifier, summary=null, containerColor=surfaceVariant, headerAction=null, content)`** (6086) — the vehicle+section-keyed entry point. Derives `expanded` from `LocalForceExpanded.current || state.isPebbleExpanded(v.vin, section)` (6099–6100) and `onToggle` from `vm.togglePebble(v, section)` (6103), then delegates to `PebbleShell`. Every detail pebble below calls this.
- **`PebbleShell(expanded, onToggle, icon, title, vm, dragHandle=Modifier, summary=null, containerColor=surfaceVariant, headerAction=null, forceExpanded=false, content)`** (6124) — the actual card. Renders the drop-shadowed `Card` with animated corner radius, optional outline, the collapsed header row (icon / weighted title+summary column / expand control), and the `AnimatedVisibility`-wrapped body. Takes `expanded`/`onToggle` directly so non-vehicle callers (the update tile) reuse it.
- **`SplitExpandButton(action, expanded, onToggle)`** (6365) — right-side control for pebbles that also have an action: a connected two-segment pill (left = action button with label+icon, right = chevron nub). Handles the icon bounce (`bounceIcon`) and spin (`spinning`) animations.
- **`MorphExpandButton(expanded, onToggle)`** (6516) — right-side control for pebbles with no action: a single 50dp pill that morphs to a 14dp rounded square when expanded, with a rotating chevron.

### Pebble bodies

- **`TripsPebble(v, state, vm, dragHandle)`** (6560) — recent drives list. Hidden entirely for Gen5W (gen < 3) non-Kia cars. Lazily loads trips once per car.
- **`TripRow(trip: EvTrip, metric=false)`** (6588) — one trip: date + distance, a "pace" line (drive/idle minutes, avg/max speed), and (EV) an energy line (kWh used / regen).
- **`InfoPebble(v, status, state, vm, dragHandle)`** (6620) — combined status + service + owner-links readout. Summary is "Locked"/"Unlocked".
- **`OwnerLinks(v, context, inApp)`** (6715) — brand-specific owner/service/digital-key destination buttons in flow rows.
- **`LinkButton(label, icon, onClick)`** (6796) — a compact secondary-container `MorphButton` used by `OwnerLinks`.
- **`DiagnosticsPebble(v, status, state, vm, dragHandle)`** (6816) — tire/12V/fluids/heaters/plug checks, with a warning header action when any check reports a problem.
- **`ClimatePebble(v, status, seats, state, vm, dragHandle)`** (6956) — the most stateful pebble: temp/duration/defrost/steering-heat/4 seat levels, presets, smart-climate, two-way watch sync. Detailed in §3.
- **`SeatControl(label, level, canCool, canHeat, onChange)`** (7347) — a colour-tinted slider over the allowed `SeatLevel` range for one seat.
- **`seatTint(level): Color`** (7381, `@Composable`) — interpolated blue-for-cool / red-for-heat colour by intensity.
- **`ClimatePresetSection(presets, activeId, fahrenheit, onStart, onDelete, onReorder)`** (7394) — animated reorderable list of `PresetPill`s.
- **`presetDetail(req, fahrenheit): String`** (7453) — compact "79° · Defrost · Heat" summary of a `ClimateRequest`.
- **`PresetPill(name, detail, active, onStart, onDelete, dragHandle=Modifier)`** (7477) — two-segment split button (apply / delete-with-confirm) for one saved preset.
- **`ChargeLimitPill(label, limit, pending, enabled, icon=Bolt, onValueChange, onApply)`** (7606) — split pill + slider for one charge-limit target.
- **`ChargePebble(v, status, enabled, state, vm, dragHandle)`** (7722) — EV energy: start/stop charge header action + separate AC/DC limit pills.
- **`FuelPebble(v, status, state, vm, dragHandle)`** (7803) — gas/hybrid energy: fuel % + range only. Reuses the "charge" section slot.
- **`VibrancySlider(appearance, vm)`** (7855) — 5-stop saturation slider (shared with settings search). Not a pebble but lives in this range.
- **`LocationPebble(v, state, vm, dragHandle)`** (7874) — map + coordinates + car-weather + "Open in maps", with a "Locate" header action.
- **`weatherIcon(code, isDay): ImageVector`** (7928) / **`weatherTint(code, isDay): Color`** (7932, `@Composable`) — thin delegates to `uicommon`.
- **`WeatherStripe(weather, fahrenheit, caption)`** (7940) — compact one-line weather readout used inside `LocationPebble`.
- **`WeatherPebble(v, state, vm, dragHandle)`** (7971) — home-location weather with a "Refresh" spinning header action.
- **`CarMap(location, modifier=Modifier)`** (8060) — key-free OpenStreetMap tile map centred on the car with a centre pin.

### Free helpers / constants

- **`MutedContentAlpha = 0.7f`** (6330), **`ControlHeight = 76.dp`** (6333), **`PebbleHeaderHeight = ControlHeight`** (6336), **`PebbleCornerCollapsed = 38.dp`** (6337), **`PebbleCornerExpanded = 20.dp`** (6338).
- **`PebbleHeaderAction`** (data-ish class, 6340) — see §4.
- **`DiagRow(label, value, indent=false)`** (data class, 6813).
- **`warn(v: Int)`** (6916) → "OK"/"Warning"; **`yesNo(v: Boolean)`** (6917); **`onOff(v: Int)`** (6918) → 0="Off" else "On".
- **`tripDate`** (6615), **`chargerLabel`** (7829), **`fmtMinutes`** (7831), **`degLabel`** (7837) — one-line delegates to `com.bloo.bluelink.data`.
- **`VibrancySteps`** (7847) = `[0f,0.5f,1f,1.6f,2.5f]`, **`VibrancyLabels`** (7848), **`vibrancyIndexFor(v)`** (7849).
- **`openUrl(context, url, inApp)`** (8115), **`openApp(context, packages, fallbackUrl, inApp)`** (8126), **`dial(context, number)`** (8135).

### Palette / colour-picker block (5219–5658, above the pebbles but in range)

- **`PaletteSwatch(palette: ColorPalette, selected, onClick)`** (5219) — round seed-colour swatch with selection ring + check; `RadioButton` semantics.
- **`CustomPaletteSwatch(palette: CustomPaletteData, selected, onClick, onEdit)`** (5274) — like above but for user palettes, scales 1.12× when selected and has an "Edit palette" `IconButton`.
- **`ColorPickerCanvas(color, onColorChange, modifier=Modifier)`** (5369) — HSV canvas picker (sat×value square + hue bar) with an accessible hex text field.
- **`PaletteEditorDialog(editing: CustomPaletteData?, onSave, onDelete, onDismiss)`** (5521) — create/edit dialog with name + up to three `ColorPickerCanvas`es and a tap-twice-to-confirm delete.
- **`GroupIconAction(icon, contentDescription, enabled, onClick)`** (data class, 5661) and **`connectedGroupShape(index, count, cornerPercent, smallCorner=12.dp)`** (5680) — the connected-button-group shape helper.

---

## 3. Internal structure & control flow

### 3.1 `PebbleShell` (6124–6327)

Steps:

1. `corner` animates between `PebbleCornerCollapsed` (38dp) and `PebbleCornerExpanded` (20dp) via a `SoftDamping`, `StiffnessLow` spring (6139). Collapsed = pill-soft; expanded = tighter square. `pebbleShape = RoundedCornerShape(corner)`.
2. Reads `fillHeight = LocalPebbleFillHeight.current` (6144) and `pebbleOutline = vm.appearance.collectAsState().value.pebbleOutline` (6150–6151).
3. Outer `Box` fills width, and fills height only when `fillHeight` (6152).
4. `Card` gets `dropShadow(pebbleShape, 12dp, offsetY 4dp)` (6157) and, when `pebbleOutline`, a **dedicated bold border** (`outline.copy(alpha=0.55f)`, 6167) — deliberately not `frostedRim`, whose 0.10–0.24 alpha is invisible against a flat dark pebble (6158–6164).
5. **Two rendering branches** inside the `Card`'s `Column`:
   - **`fillHeight` (cover-screen tile) branch** (6188–6239): no header row at all — it wasted ~76dp of a tiny screen. When `expanded`, the body is a `verticalScroll` column inside a `BoxWithConstraints` that forces `heightIn(min = maxHeight)` so short content centres (`spacedBy(8.dp, CenterVertically)`) rather than clinging to the top. A 26dp floating icon badge (top-start, 8dp inset) identifies the tile instead of a header. Uses `LocalCoverScrollState.current ?: rememberScrollState()` (6201) and `fadingEdges` (6216). Extra 34dp top padding keeps content clear of the badge (6222).
   - **normal branch** (6240–6322): a header `Row` at `heightIn(min = PebbleHeaderHeight)` (76dp). The whole row is `clickable` to toggle (unless `forceExpanded`) with `haptics.tick()` on collapse vs `haptics.click()` on expand (6250), and `.then(dragHandle)` for long-press reorder. Icon (20dp) + weighted column (bold `titleMedium` title with `semantics { heading() }`, plus an `AnimatedContent` summary that fades+slides on change, 1 line, muted). Right side (only when `!forceExpanded`): `SplitExpandButton` if `headerAction != null`, else `MorphExpandButton`. The body follows in an `AnimatedVisibility` (fadeIn 180ms + expandVertically spring / fadeOut 130ms + shrinkVertically 160ms) padded 16/16/16/4 with `spacedBy(8.dp)`.

**Deliberately no `animateContentSize` on the body Column** (6176–6184): the inner `AnimatedVisibility` already springs the height delta; wrapping a second spring on top made expand/collapse rubber-band because each inner frame is a "size changed" event the outer spring re-chases.

### 3.2 `SplitExpandButton` (6365–6508)

A connected two-Surface pill (3dp gap, `IntrinsicSize.Min` height). `morphed = action.active || leftPressed || expanded` drives the outer corner between 50dp (pill) and 16dp (6379–6385); inner corner fixed 6dp. Left Surface = the action: colours branch `isWarning → errorContainer`, `active → activeContainer ?: primary`, else `buttonContainer()` (6389–6402). It shows a `LoadingIndicator` when `action.pending && !bouncing`, else the icon. Right Surface = chevron nub with a rotating `KeyboardArrowDown`.

Two animations live here:
- **Bounce** (`bounceIcon`, used by Location's "Locate"): on click, `bounceY` springs to −9f then back; `translationY` applied via `graphicsLayer` (6438–6443, 6461).
- **Spin** (`spinning`, used by Climate's "Start" while on and Weather's "Refresh"): a `LaunchedEffect(action.spinning)` (6411–6428) does one 850ms spin-up then loops 600ms full turns; on stop it eases to the next whole 360° and `snapTo(0f)`. The spin is applied through a **`graphicsLayer { rotationZ = spinAngle.value }` lambda, not `rotate()`** (6471–6475) — `rotate()` reads the `Animatable` at composition and would recompose the button every frame for as long as climate is on; the lambda defers the read to the draw phase.

Accessibility: the chevron nub carries `stateDescription = if (expanded) "Expanded" else "Collapsed"` (6494) while the icon's own `contentDescription` is the *next* action ("Expand"/"Collapse") — so TalkBack announces both current state and the tap outcome. Icon-only action Surfaces get `action.contentDescription` as their semantics label (6452–6456).

### 3.3 `ClimatePebble` (6956–7344) — the complex one

**Local editable state**, all `remember(v.vin)`-keyed so switching cars resets to that car's values (6966–6974): `tempF` (Int, `DEFAULT_CLIMATE_TEMP_F`), `duration` (Int, `DEFAULT_CLIMATE_DURATION_MIN`), `defrost`, `steeringHeat` (Bool), `driver/passenger/rearLeft/rearRight` (`SeatLevel.OFF`), and a `settingsLoaded` gate. `activePresetId` (String?) is also `remember(v.vin)`.

`currentReq` (6991–7000) is a `ClimateRequest` built fresh each composition from all eight local values. `applyPreset` (7012–7021) copies a request's fields back into local state.

**Three sync effects:**
1. **Restore** (`LaunchedEffect(v.vin)`, 6977–6989): `vm.loadSavedClimate(v)` → copy into locals, then `settingsLoaded = true`.
2. **Preset match tracking** (`LaunchedEffect(currentReq, activePresetId, presets)`, 7022–7025): if the active preset's `request != currentReq`, clear `activePresetId` — so the highlight only ever marks an exact live match.
3. **Watch inbound** (`LaunchedEffect(remoteClimate)`, 7030–7041): `remoteClimate = state.climateSync[v.vin]`; when it changes, snap all locals to it, converting seat ints via `SeatLevel.fromApi(...)` and adopting `r.activePresetId`.
4. **Persist/publish** (`LaunchedEffect(currentReq, activePresetId)`, 7050–7052): `if (settingsLoaded) vm.saveClimateDebounced(v, currentReq, activePresetId)`. **The 400ms debounce lives in the ViewModel (viewModelScope), not here** (7042–7049) — an effect-side `delay` was cancelled whenever the pebble left composition within 400ms (tile swipe / car switch / collapse), silently reverting the change.

**Derived flags** (7054–7066): `climateOn = status?.airCtrlOn == true`; `driving = state.isDriving(v)`; `weather = state.carWeather[v.vin] ?: state.homeWeather`; `simpleMode = state.settingsMode != "advanced"`; `expanded = LocalForceExpanded.current || state.isPebbleExpanded(v.vin, "climate")` (recomputed here to match `Pebble`'s own so the header Start button and the body agree on "expanded").

**Header action = context-sensitive Start/Stop** (7075–7115). Label/summary: `On · driving` / `On` / `Stop` / `Start`. `enabled = !driving`, `pending`, `active = climateOn`, `spinning = climateOn`. Click logic:
- `climateOn` → `vm.stopClimate(v); activePresetId = null`.
- else `expanded` → `startClimate()` (exactly the visible sliders; no smart/preset second-guessing).
- else `simpleMode && weather != null` → compute `smartTarget = smartClimateTargetF(ambientFahrenheit(weather.tempC))`, set `tempF`, clear defrost + preset, `vm.startClimate(v, currentReq.copy(...))`.
- else (advanced, collapsed): if a `defaultClimatePresets[v.vin]` matches an existing preset, apply+start it; else if weather present, smart target; else `startClimate()`.

**Body** (7116–7343):
- If `driving`: read-only. When `climateOn`, show the car's current setpoint/defrost/steering/seat state as `StatusRow`s and `return@Pebble`; else a "can't start while driving" note (7117–7139).
- `ClimatePresetSection(...)` (7141–7161): `onStart` toggles off if tapping the running preset, else applies+starts+marks active; `onDelete` clears active if it was the deleted one then `vm.deleteClimatePreset`; `onReorder → vm.reorderClimatePresets`.
- **Smart climate** button (7168–7195): visible when `weather != null`. Label "Cool to X"/"Heat to X" (threshold 70°F). Disabled when `pending || climateOn`.
- **Controls** (7197 on): an `AnimatedVisibility` set-temperature readout shown while `climateOn`; an optional advanced-mode `showClimateChoice` dialog (7212–7239) offering smart + each preset.
- **Temperature slider** (7244–7273): `tempColor = uicommon.tempColor(tempF, range.start, range.end)`. In Fahrenheit the slider runs directly over `CLIMATE_TEMP_RANGE_F` (`steps=19`); in Celsius it drives a 17–28°C slider (`steps=10`) but keeps `tempF` canonical, converting on each edge (`tempC = round((tempF-32)*5/9)`; back = `round(tempC*9/5+32)`).
- **Run time** slider 1–10 min (`steps=8`, 7275–7281).
- `ToggleRow("Defrost")`, and `ToggleRow("Steering wheel heat")` only if `seats.steeringWheel` (7283–7286).
- **Seats** (7288–7303): `isGen5W = v.brand != KIA && (generation < 3)`; the seat block is shown only if `seats.any && !(isGen5W && v.isEv)`. Each seat gets a `SeatControl` if it has any heat/cool capability.
- **Save** (7305–7342): "Save as preset" opens an `AlertDialog` that calls `vm.saveClimatePreset(v, name.trim(), currentReq)`.

### 3.4 `ChargePebble` (7722–7796)

`ev = status?.evStatus`; `charging = ev?.batteryCharge == true`; `plugged = (ev?.batteryPlugin != null && ev.batteryPlugin != 0) || charging` (7723–7725). Summary: "Charging" / "Plugged in · idle" / "Not plugged in".

**Separate AC/DC limit state** (7745–7757): `acLimit` seeded 80, `dcLimit` seeded 90 (both `remember(v.vin)`), `limitsSeeded` gate. A `LaunchedEffect(v.vin, ev?.reservChargeInfos)` reads the car's real targets — `reservChargeInfos.level(1)` = AC, `.level(0)` = DC — and seeds once. The two different defaults matter (7734–7744): both pills' "Set" sends **both** values together via `vm.setChargeLimits(v, acLimit, dcLimit)`, so if DC were left at a wrong seed (the old bug: both defaulted 80), tapping "Set" on the AC pill would silently lower the DC target.

Header action: Start/Stop charge, `enabled = plugged`, `active = charging`, `activeContainer = ChargeGreen`, `activeContent = White`. Body shows the `chargerLabel(ev?.batteryPlugin)` when plugged, then the two `ChargeLimitPill`s. (Charge-port toggle lives in the controls pebble, not here — 7794.)

### 3.5 `ChargeLimitPill` (7606–7715)

Split pill (left = label + `RollingNumber` current value; right = "Set" nub) plus an `AnimatedSlider` below. Left-half tap **bumps the limit by 10, wrapping 100→50** (7644–7647) for keyboard-free stepping; slider snaps to nearest 10 over `CHARGE_LIMIT_RANGE` (`steps=4`, 7707–7712). Right nub calls `onApply` with `haptics.heavy()`, disabled while `pending` (shows `LoadingIndicator`). The left half carries merged semantics: `contentDescription = "$label, $limit percent"` and a custom `onClick(label="Increase by 10 percent")` action (7657–7664) because the stepper behaviour was otherwise invisible to TalkBack.

### 3.6 `CarMap` (8060–8110)

Zoom fixed 15, tile size 256px. Uses `BoxWithConstraints` to get the box's pixel size, then Web-Mercator math (7072–7077 style): `xTileF/yTileF` from lat/lon, `originX/originY` = the world-pixel of the box top-left so the car lands dead-centre. Iterates the covering tile grid `firstX..lastX × firstY..lastY`, skipping out-of-range `ty`, horizontally wrapping `wrappedX = ((tx % span) + span) % span`, and draws each tile as a Coil `AsyncImage` from `https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png` with a `User-Agent` header at its pixel offset. A centre `LocationOn` pin is offset `y = -20.dp` so its tip points at the car. Chosen over static-map render services that "painted blank" (8053–8058).

---

## 4. Data & types defined here

### `PebbleHeaderAction` (class, 6340–6357)

The header's action-button descriptor.
- `label: String` — button text; empty = icon-only.
- `icon: ImageVector`.
- `onClick: () -> Unit`.
- `enabled: Boolean = true`, `pending: Boolean = false` (shows a spinner + disables), `active: Boolean = false` (toggled-on fill).
- `spinning: Boolean = false` — continuous icon rotation (climate-on, weather-refresh).
- `bounceIcon: Boolean = false` — one-shot icon bounce on tap (locate).
- `activeContainer: Color? = null`, `activeContent: Color? = null` — override the active fill/content (Charge uses `ChargeGreen`/White).
- `isWarning: Boolean = false` — error-container styling (Diagnostics).
- `contentDescription: String? = null` — explicit TalkBack label for empty-`label` actions (a bare icon Surface otherwise announces only "Button").

### `DiagRow` (data class, 6813)

`label: String`, `value: String`, `indent: Boolean = false`. `indent` rows render with a bullet + start padding (per-tire lines under "Tire pressure"). The diag summary counts only non-indented rows (`rows.count { !it.indent }`, 6868).

### `GroupIconAction` (data class, 5661)

`icon: ImageVector`, `contentDescription: String`, `enabled: Boolean`, `onClick: () -> Unit` — one segment of a connected icon-button group (used by StateControl's horn/lights).

### Encodings referenced (defined in `:shared`, not here)

- **`batteryPlugin`**: 0 = unplugged, 1 = DC, 2 = AC. `plugged` in both `InfoPebble` (6635) and `ChargePebble` (7725) treats any non-zero as plugged; `chargerLabel(plugin)` maps it to a human label.
- **`reservChargeInfos.level(0)` = DC target, `.level(1)` = AC target** (7750–7751) — note this `plugType`-style scheme (0=DC, 1=AC) is the *opposite* index order to `batteryPlugin`.
- **`SeatLevel.apiValue`**: 0=off, 3–5=cool, 6–8=heat. `seatTint` (7381) lerps cool over `(apiValue-3)/2` and heat over `(apiValue-6)/2`. Seat levels cross the wear wire as ints; `SeatLevel.fromApi(int)` (7036–7039) rebuilds them from `climateSync`.
- **`percentFor(hasBattery)` / `rangeMiFor(hasBattery)`** (6656–6659, 6835) honour the user's manual powertrain override (`state.hasBattery(v)`), not raw `isEv`.
- **Temperature**: `airTemp.value` / climate setpoints are °F strings from the API; `degLabel(valueF, fahrenheit)` renders them in the chosen unit (7837).

### Constants

`CLIMATE_TEMP_RANGE_F`, `CHARGE_LIMIT_RANGE`, `DEFAULT_CLIMATE_TEMP_F`, `DEFAULT_CLIMATE_DURATION_MIN` are imported from `com.bloo.bluelink.data` (301–304). `VibrancySteps=[0,0.5,1,1.6,2.5]` with labels ending "Best Buy TV" (7847–7848). `ChargeGreen` (4164) from `BlooColors`.

---

## 5. State & concurrency

- **Expansion state** is not local: `Pebble` reads `state.isPebbleExpanded(v.vin, section)` (a `UiState` accessor over ViewModel state) and toggles via `vm.togglePebble(v, section)`. `LocalForceExpanded` (a composition local) forces every pebble open (used by the cover screen).
- **Appearance** (`pebbleOutline`, `useFahrenheit`, `unitSystem`, `linksInApp`, `weatherLat/Lon`, etc.) comes from `vm.appearance.collectAsState()` — a `StateFlow<SettingsStore.Appearance>` (DataStore-backed).
- **ClimatePebble local UI state** is `remember(v.vin)`-keyed `mutableStateOf`/`mutableIntStateOf`; it is the source of truth for the sliders. It is reconciled with three external inputs (saved settings, `state.climateSync`, presets) via the `LaunchedEffect`s in §3.3. The **debounce runs in `viewModelScope`**, not in a composition-scoped effect, precisely so leaving composition can't drop a pending save.
- **Animations**: `animateDpAsState` (corners), `animateColorAsState` (backgrounds), `animateFloatAsState` (chevron rotation), and imperative `Animatable`s in `SplitExpandButton` (`bounceY`, `spinAngle`) driven by `LaunchedEffect(action.spinning)` and a `rememberCoroutineScope()` bounce launch. Spin is read in the **draw phase** via a `graphicsLayer` lambda to avoid per-frame recomposition (6471–6475).
- **Lazy loads on first composition per car**: `TripsPebble` → `LaunchedEffect(v.vin){ vm.loadTrips(v) }` (6569); `LocationPebble` → `LaunchedEffect(loc.lat, loc.lon){ vm.loadCarWeather(v) }` (7906); `WeatherPebble` → `LaunchedEffect(weatherLat, weatherLon){ vm.loadHomeWeather() }` (7979). The VM throttles weather to a 15-minute TTL.
- **Weather spinner** (`WeatherPebble`, 7976–7991): `weatherSpinning`/`spinStartedAt` locals; a `LaunchedEffect(state.homeWeather?.fetchedAt)` clears the spinner but enforces a 900ms minimum so a cached/instant response still animates.
- **CropScreen** (8148+, just past 8200) decodes the picked bitmap on `Dispatchers.IO` inside a `LaunchedEffect`, with EXIF-orientation correction and downsampling to ≤2200px.
- All command dispatch (`startClimate`, `stopClimate`, `startCharge`, `setChargeLimits`, `locate`, …) goes through `vm`, which routes through `BlueLinkGate.statusMutex` in `:shared` so overlapping vehicle calls never hit the backend concurrently.

---

## 6. Collaborators & data flow

**Inbound (what the pebbles read):**
- `Vehicle v` — `vin`, `brand`, `generation`, `odometer`, `isEv`, `supportsHornLights`, `supportsConnectedStore`, `brand.links`, `brand.label`.
- `VehicleStatus? status` — `airCtrlOn`, `doorLock`, `doorOpen/windowOpen.openLabels()`, `trunkOpen`, `hoodOpen`, `engine`, `acc`, `airTemp`, `defrost`, `steerWheelHeat`, `sideMirrorHeat`, `sideBackWindowHeat`, `seatHeaterVentState`, `battery.batSoc`, `evStatus` (`batteryCharge`, `batteryPlugin`, `batteryStatus`, `reservChargeInfos`, `remainTime2.atc`, `pluggedInLabel`, `targetForCurrentPlug()`), `tirePressureLamp`/`tirePressure`, `fuelLevel`, `dte`, `lowFuelLight`, `washerFluidStatus`, `breakOilStatus`, `smartKeyBatteryWarning`, plus `percentFor/rangeMiFor`.
- `UiState state` — maps keyed by VIN: `trips`, `locations`, `placeNames`, `licensePlates`, `lastServiceMiles`, `serviceIntervalMiles`, `climatePresets`, `defaultClimatePresets`, `climateSync`, `carWeather`; scalars `homeWeather`, `refreshing`, `settingsMode`; and accessors `isPending(vin, key)`, `isPebbleExpanded`, `isDriving`, `hasBattery`, `fetchedAt`, `seatConfigFor`.
- Composition locals: `LocalForceExpanded`, `LocalPebbleFillHeight`, `LocalCoverScrollState`, `LocalHaptics`, `LocalContext`, `LocalDensity`, `LocalContentColor`.

**Outbound (what the pebbles call on `vm`):**
`togglePebble`, `loadTrips`, `loadSavedClimate`, `saveClimateDebounced`, `startClimate`, `stopClimate`, `saveClimatePreset`, `deleteClimatePreset`, `reorderClimatePresets`, `startCharge`, `stopCharge`, `setChargeLimits`, `locate`, `loadCarWeather`, `loadHomeWeather`, `setVibrancySoon`, plus `flashLights`/`hornAndLights` (from the StateControl block above at 5206–5207).

**Wear Data Layer**: `state.climateSync[v.vin]` is the inbound half of the phone↔watch climate wire (`ClimateRequest`-shaped, seat levels as ints); `saveClimateDebounced` publishes the outbound half. Seat levels cross as ints via `SeatLevel.apiValue`/`fromApi`.

**Intents / external**: `LocationPebble`'s "Open in maps" fires a `geo:` `ACTION_VIEW` (7910–7920); `OwnerLinks` uses `openUrl` (Custom Tab when `inApp`, else `ACTION_VIEW`), `openApp` (launch by package, else fallback URL), and `dial` (`ACTION_DIAL tel:`). `CarMap` fetches OSM tiles over HTTPS via Coil.

**Callers**: `SinglePebble` (5102) and the cover-screen pager (3281–3288). `PresetPill`/`ChargeLimitPill`/`SeatControl` are only used inside their owning pebbles. `PebbleShell` is also reused by the update tile.

---

## 7. Invariants & assumptions

- **`Pebble` is always called inside a `Column`/`ColumnScope`** — its `content` is `@Composable ColumnScope.() -> Unit` and the body applies `spacedBy(8.dp)`.
- **`section` string keys are stable and unique per pebble** — expansion, reorder, and pending state are all keyed on `(v.vin, section)`. `FuelPebble` deliberately reuses `"charge"` (7814) so a car that flips EV↔gas keeps its slot/collapse state.
- **`ClimatePebble` local state must be `remember(v.vin)`-keyed** — otherwise a car switch would carry over the previous car's temp/seat settings.
- **`settingsLoaded` must gate the persist effect** (7051) — otherwise the restore effect's writes would immediately re-save (and republish) the values it just loaded.
- **Both AC and DC charge limits must be seeded before "Set"** (7734–7757) because `setChargeLimits` sends both together; the seeds (80/90) match the watch/shared defaults.
- **`reservChargeInfos.level(1)`=AC, `level(0)`=DC** — the opposite index order from `batteryPlugin`. Getting these backwards silently sets the wrong target.
- **Gen5W check**: `v.brand != Brand.KIA && (v.generation.trim().toIntOrNull() ?: 3) < 3`. Kia reports no generation, so the `?: 3` fallback keeps Kia out of the Gen5W branch (trips pebble hidden, seat block behaviour, digital-key variant). This exact expression is duplicated at 6565, 6752, and 7288 — they must stay in agreement.
- **`plugged` is non-zero `batteryPlugin` OR `batteryCharge`** — a car can report charging without a plug value, so both are checked (6635, 7725).
- **`weather` non-null gates smart climate** — the header Start falls back to preset or plain `startClimate()` when no weather is available.
- **`CarMap` assumes a bounded parent** — it uses `BoxWithConstraints` and needs a real height (callers give it `.height(220.dp)`).

---

## 8. Gotchas & sharp edges

- **No `animateContentSize` on the pebble body** (6176–6184): two nested springs (the outer size animation + the inner `AnimatedVisibility`) rubber-banded; only the inner one is used.
- **`spinning` uses a `graphicsLayer` lambda, not `rotate()`** (6471–6475): `rotate()` reads the `Animatable` in composition and would recompose the button every frame indefinitely while climate is on. The lambda defers the read to draw.
- **Climate debounce lives in the ViewModel, not a `LaunchedEffect`** (7042–7052): an effect-scoped `delay` is cancelled when the pebble leaves composition within the window (tile swipe, car switch, collapse), silently reverting the user's last change.
- **The header Start button recomputes `expanded` itself** (7066) rather than trusting a passed-in flag, so its "if expanded, start exactly what's shown; else smart/preset" logic always matches what's actually on screen.
- **Tapping the running preset stops climate** (7146–7154) — it toggles rather than restarts.
- **Destructive taps require a second confirm with a 4s auto-reset**: `PresetPill` delete (7513–7519), `PaletteEditorDialog` delete (5544–5550), climate preset delete nub. This is a deliberate cross-app pattern (also Sign out, watch preset delete) — a single mis-tap beside the frequently-hit Apply half used to silently drop saved work.
- **Preset delete is animated by the parent, not the pill**: `ClimatePresetSection` tracks `deletingIds` and delays 240ms (matching the shrink) before calling `onDelete`, so the item's exit animation completes before it's removed (7434–7442).
- **`ColorPickerCanvas` HSV state is seeded once and never re-synced from `color`** (5374–5389) — each canvas drag computes HSV straight from touch position; the hex field only overwrites canvas state on commit (Done/focus-loss, 5410–5425), never mid-edit. `sat` is floored at 0.05 and `value` at 0.3 so a picked colour is never invisible-dark or fully grey.
- **The two picker Canvases have no TalkBack path at all** — the hex `OutlinedTextField` (5505) is the *only* accessible way for a screen-reader user to choose a colour; its `semantics` descriptions explicitly point there.
- **`CustomPaletteSwatch` edit button** was a bare 10dp clickable icon under the touch-target minimum with no button role; it's now a 28dp `IconButton` (5334–5347).
- **`ChargeLimitPill` left-tap wraps 100→50** (7646), not clamped — quick cycling, but a user expecting a clamp at 100 gets 50.
- **Celsius temp slider is lossy**: it snaps to whole °C (17–28) while `tempF` stays canonical, so round-tripping through Celsius can shift the underlying °F by a degree (7261–7272).
- **`InfoPebble` odometer parsing** strips commas and parses as Double→Int (6627); a non-numeric odometer silently yields no odometer/service rows.
- **`CarMap` uses raw `tile.openstreetmap.org`** with a custom `User-Agent` (8092) — this depends on OSM's tile-usage policy tolerance; there is no API key and no local caching beyond Coil's.
- **`Pebble` outline uses a dedicated bold border, not `frostedRim`** (6158–6167): frostedRim's low alpha is invisible on a flat pebble, so the setting appeared to do nothing.
- **Diagnostics summary counts only non-indented rows** (6868) so per-tire detail lines don't inflate the "N checks" count.
