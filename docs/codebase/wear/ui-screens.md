# Wear UI Screens & Shared Components

**Unit:** `wear` module — the `com.bloo.wear.ui` package's screen composables and shared
Compose building blocks.

**Files covered (all under `C:\Users\AdiPerets\Bloo\wear\src\main\java\com\bloo\wear\ui\`):**

| File | Role |
|------|------|
| `Components.kt` | Shared, reusable Wear Compose widgets (cards, rings, sliders, buttons, map thumb, text-input helper, formatters). |
| `WatchApp.kt` | The app's root composable + nav host + aurora background + haptics gating. |
| `LoginScreen.kt` | On-watch sign-in form (used standalone / no phone). |
| `PinLockScreen.kt` | 4-digit PIN pad, lock gate, and Set/Change/Remove/Disable PIN management overlay. |
| `SettingsScreen.kt` | The full settings list. |
| `TripsScreen.kt` | Recent-trips list for one car. |
| `WearHaptics.kt` | Semantic haptic vocabulary (`tick`/`click`/`reject`). |
| `WearTheme.kt` | Wear M3 `ColorScheme` construction from phone-synced roles + reduce-motion local. |

---

## 1. Purpose

This unit is the **entire visible surface of the Wear OS app** below the tile/complication
layer, plus the shared widget kit those screens are built from. It renders `WearViewModel.ui`
(a single `StateFlow<WearUi>`) reactively and wires every control's `onClick`/`onSelect`/
`onSettle` back to a `vm.setXxx(...)`/command call. It contains almost no business logic —
verification, persistence, and telematics all live in `WearViewModel` / `WearLocalStore` /
`:shared`. The design intent, repeated in the file comments, is **cross-platform visual
consistency**: nearly every widget is a thin wrapper over a shared `:uicommon` implementation
so the phone and watch never drift to two independently-tuned animations/colors for the same
concept.

`HomeScreen` and `TileReorderScreen` are referenced (navigated to from `WatchApp`) but are
**not** in this unit — they live in other files in the same package.

---

## 2. Public surface

### `WearTheme.kt`

- **`val LocalReduceMotion = staticCompositionLocalOf { false }`** (`WearTheme.kt:17`) — a
  composition local; `true` when the user disabled animations in Accessibility. Consumed by
  `AnimatedValue`/`AnimatedSlider` wrappers in `Components.kt`.
- **`object WearColors`** (`:20`) — brand accents from shared `BlooColors`:
  `chargeGreen`, `heat`, `cool` (each `Color(BlooColors.<int>)`).
- **`fun schemeFrom(c: WearColorRoles): ColorScheme`** (`:27`) — builds a Wear M3
  `ColorScheme` by mapping each of ~25 `WearColorRoles` int fields through `Color(...)`.
- **`@Composable fun BlooWearTheme(settings: WearSettingsPayload?, content: @Composable () -> Unit)`**
  (`:61`) — top-level theme wrapper. If `settings?.colors` is non-null, paints with
  `schemeFrom(...)`; otherwise falls back to the framework default expressive watch scheme.
  Also computes `reduceMotion` once from `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` and
  provides it via `LocalReduceMotion`.

### `WearHaptics.kt` — three extension functions on `HapticFeedback`

- **`HapticFeedback.tick()`** (`:18`) → `SegmentTick` — slider step / keypad digit / segmented tick.
- **`HapticFeedback.click()`** (`:19`) → `Confirm` — button taps, slider settle.
- **`HapticFeedback.reject()`** (`:20`) → `Reject` — wrong PIN.

### `WatchApp.kt`

- **`@Composable fun WatchApp(vm: WearViewModel)`** (`:140`) — the true root. Collects
  `vm.ui` as state, swaps in `NoOpHapticFeedback` app-wide if haptics off, and gates the whole
  app behind `PinLockScreen` when `ui.pinLocked`. Public entry point.
- All other symbols in this file are `private` (see §3).

### `Components.kt` — the shared widget kit (all public unless noted)

- **`@Composable fun roundSafeHorizontalPadding(flat: Dp = 14.dp, round: Dp = 22.dp): Dp`**
  (`:98`) — returns `round` on a round screen, else `flat`. Reads `LocalConfiguration.isScreenRound`.
- **`@Composable fun SectionCard(title: String?, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit)`**
  (`:107`) — the canonical card: rounded `Box` (NOT a Wear `Card`) with a faint outline rim,
  animated size, and an optional uppercase/bold/primary header (with `heading()` semantics)
  above `content()`.
- **`@Composable fun rememberWearTextInput(label: String, onResult: (String) -> Unit): () -> Unit`**
  (`:198`) — returns a lambda that launches the system RemoteInput overlay; the typed text
  comes back through the activity-result callback and invokes `onResult` if non-blank.
- **`@Composable fun ChargeRing(percent: Int?, modifier: Modifier = Modifier, size: Dp = 88.dp, charging: Boolean = false)`**
  (`:234`) — a `CircularProgressIndicator` ring with the centered percent value; animated fill
  (800ms) and color (400ms); green while charging, error-red under 15%, else primary.
- **`@Composable fun MapThumbnail(lat: Double, lon: Double, modifier: Modifier = Modifier)`**
  (`:278`) — a 116dp OSM slippy-map thumbnail (zoom 15) with a marker dot; loading spinner and
  tap-to-retry error state.
- **`fun relativeLabel(ms: Long?): String`** (`:383`) — delegates to `bluelink.data.relativeLabel`.
- **`fun fmtMinutes(min: Int): String`** (`:386`) — "1h 20m" / "45 min"; delegates to shared.
- **`@Composable fun StatusRow(label: String, value: String, valueColor: Color? = null)`**
  (`:391`) — label→value row; value cell is an `AnimatedValue` (crossfade), both sides truncate.
- **`@Composable fun SliderRow(label, valueLabel, value: Int, min, max, step: Int, accent: Color? = null, onSettle: (() -> Unit)? = null, onValue: (Int) -> Unit)`**
  (`:421`) — labelled slider row; converts int min/max/step to the `AnimatedSlider`'s float API.
- **`@Composable fun tempColor(tempF: Int): Color`** (`:461`) — delegates to `uicommon.tempColor`.
- **`@Composable fun AnimatedSlider(value: Float, onValueChange, valueRange, steps: Int = 0, accent, onSettle: (() -> Unit)? = null)`**
  (`:469`) — thin wrapper over `uicommon.AnimatedSlider`, injecting watch colors, reduce-motion,
  and haptics (`tick` on step, `click` on settle).
- **`@Composable fun MorphButton(label, icon, active, activeColor, pending, onClick, modifier, secondaryLabel: String? = null, enabled: Boolean = true, toggled: Boolean? = null)`**
  (`:504`) — the app's pill↔rounded-square morphing button; the single button primitive.
- **`typealias WearSegmentOption = com.bloo.uicommon.SegmentOption`** (`:615`).
- **`@Composable fun MorphSegmented(options: List<WearSegmentOption>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)`**
  (`:624`) — full-width segmented selector; wrapper over `uicommon.MorphSegmented`.
- **`fun weatherLabel(code: Int): String`** (`:652`), **`fun weatherIcon(code: Int, isDay: Boolean): ImageVector`** (`:654`),
  **`fun weatherTemp(tempC: Double, fahrenheit: Boolean): String`** (`:657`) — weather-code helpers, delegate to shared.
- **`@Composable fun AnimatedValue(value: String, style, color: Color = Color.Unspecified, fontWeight: FontWeight? = null, maxLines: Int = 1, modifier)`**
  (`:678`) — resolves color/weight then delegates crossfade to `uicommon.AnimatedValue`.
- **`private const val KEY = "bloo_input"`** (`:214`) — RemoteInput slot key.

### `LoginScreen.kt`

- **`@Composable fun LoginScreen(vm: WearViewModel, ui: WearUi)`** (`:69`) — the sign-in form.
  `FieldRow` and `InfoCallout` are private (see §3).

### `PinLockScreen.kt`

- **`@Composable fun PinEntryScreen(title: String, onSubmit: (String) -> Unit, modifier, subtitle: String? = null, error: String? = null, onCancel: (() -> Unit)? = null)`**
  (`:163`) — reusable 4-digit pad; auto-submits on the 4th digit and keeps dots filled while
  verifying.
- **`@Composable fun PinLockScreen(vm: WearViewModel)`** (`:278`) — full-screen lock gate;
  wraps `PinEntryScreen` and forwards to `vm.submitPin`.
- **`enum class PinFlowMode { SET, CHANGE, REMOVE, DISABLE }`** (`:296`) — public.
- **`@Composable fun PinManagementOverlay(vm: WearViewModel, mode: PinFlowMode, onDone: () -> Unit)`**
  (`:308`) — drives the multi-step set/change/remove/disable flow.
- `PinDots`, `PinKey`, `PinKeypad`, and `enum PinFlowStep` are private (see §3, §4).

### `SettingsScreen.kt`

- **`@Composable fun SettingsScreen(vm: WearViewModel, ui: WearUi, onAddAccount: () -> Unit)`**
  (`:78`) — the whole settings list. `SettingSection` is private.

### `TripsScreen.kt`

- **`@Composable fun TripsScreen(vm: WearViewModel, ui: WearUi, vin: String)`** (`:66`) — recent
  trips list for one car. `tripDate` is a private file-level helper.

---

## 3. Internal structure & control flow

### `WatchApp.kt` private members

- **`private fun Color.hueShifted(degrees: Float): Color`** (`:48`) — round-trips the color
  through `android.graphics.Color.colorToHSV` / `HSVToColor`, adding `degrees` mod 360 to the
  hue. Used by the aurora background for "complementary" (180° shift).
- **`@Composable private fun WearAuroraBackground(colors: WearColorRoles?, colorMode: String, customHex: String?, modifier)`**
  (`:67`) — a diagonal `primary→transparent→tertiary` linear gradient over the base surface.
  Resolves the two gradient colors from `colorMode`:
  - `"custom"`: `customColor ?: themePrimary` for primary; `customColor?.hueShifted(180f) ?: themeTertiary` for tertiary.
  - `"complementary"`: primary = `base.hueShifted(180f)`; tertiary = theme tertiary (only the primary blob recolors — matches phone).
  - else (`"material"`): theme primary/tertiary.
  A **hand-ticked breathing loop** (`LaunchedEffect(Unit)`, `delay(80)` ≈ 12fps) drives
  `breathe` between 0.55 and 1.0 on a 9000ms triangle wave; the gradient alphas are
  `0.32f*breathe` and `0.26f*breathe`. This deliberately avoids Compose's animation clock to
  cap the redraw rate (battery/heat on the always-on home backdrop). See §8.
- **`private object NoOpHapticFeedback : HapticFeedback`** (`:123`) — swallows every
  `performHapticFeedback` call.
- **`@Composable private fun WatchAppContent(vm, ui)`** (`:167`) — the unlocked app.
  Control flow:
  1. `auroraOn = ui.settings?.auroraEnabled == true`.
  2. Outer `Box(fillMaxSize)`; if aurora on, paint `WearAuroraBackground` first (behind).
  3. `AppScaffold(containerColor = if (auroraOn) Transparent else background)` — transparent so
     the gradient shows through.
  4. `when (ui.screen)`:
     - `WearScreen.Loading` → centered spinner + "Loading…" + "Open Bloo on your phone…" +
       a "Sync from phone" `MorphButton` (`vm.resync()`).
     - `WearScreen.SignedOut` → `key(ui.accounts.size) { LoginScreen(vm, ui) }` — keying on
       account count forces a fresh LoginScreen when the account set changes.
     - `WearScreen.Ready` → `rememberSwipeDismissableNavController()` +
       `SwipeDismissableNavHost(startDestination = "home")` with routes:
       `home` → `HomeScreen(vm, ui, onSettings, onTrips, onReorder)`;
       `settings` → `SettingsScreen(vm, ui, onAddAccount = nav→login)`;
       `login` → `LoginScreen`;
       `trips/{vin}` → `TripsScreen(vm, ui, vin ?: "")`;
       `reorder/{vin}` → `TileReorderScreen(vm, ui, vin ?: "")`.

`WatchApp` itself (`:140`): collects `ui`, computes `hapticsEnabled = ui.settings?.hapticsEnabled ?: true`,
`remember`s `effectiveHaptics` (base vs NoOp) keyed on `(hapticsEnabled, baseHaptics)`, provides
it via `CompositionLocalProvider(LocalHapticFeedback ...)`, then branches
`if (ui.pinLocked) PinLockScreen(vm) else WatchAppContent(vm, ui)`.

### `Components.kt` internals

- **`SectionCard`** is deliberately a `Box` + `clip` + `background(surfaceContainerLow)` +
  `border` + `animateContentSize`, NOT a Wear `Card` (see §8). Header row only rendered when
  `title != null`; icon only when non-null.
- **`ChargeRing`** clamps `(percent ?: 0).coerceIn(0,100)/100f`; the color's null-percent case
  defaults to `100` ("not critical"); center value shows `"$it%"` or `"—"`.
- **`MapThumbnail`** control flow:
  1. Round lat/lon to ~11m: `latKey=(lat*10000).toInt()`, `lonKey=(lon*10000).toInt()`.
  2. `remember(latKey, lonKey) { ... }` computes the slippy-map tile at zoom 15 (Web Mercator:
     `n=2^15`, `xf=(lon+180)/360*n`, `yf=(1 - ln(tan(latRad)+1/cos(latRad))/PI)/2*n`), returning
     `Triple(url, mx, my)` where `mx/my` are the fractional in-tile marker position.
  3. `retryKey` `remember(url)`-scoped counter (resets when tile URL changes). Error tap
     `retryKey++`; `loadUrl` appends `?retry=N` to bust Coil's cache without changing the tile.
  4. Renders via `rememberAsyncImagePainter(model=loadUrl, imageLoader=WearImage.loader(context))`;
     inspects `AsyncImagePainter.State` for Error/Loading. Marker drawn on a `Canvas` at
     `Offset(mx*width, my*height)`.
- **`MorphButton`** — see §5 for its animation state. Emits a Wear `Button` with animated
  `RoundedCornerShape(percent = pct)`, animated `bg`, press-scale via `graphicsLayer`,
  optional `Role.Switch` semantics when `toggled != null`, `AnimatedContent` for the label,
  optional `secondaryLabel`, spinner icon when `pending`. `enabled = enabled && !pending`.

### `LoginScreen.kt` internals

- **`@Composable private fun FieldRow(label, value, icon, onClick, masked: Boolean = false)`**
  (`:221`) — a `MorphButton` where `label = value.ifBlank { label }` and `secondaryLabel = label`
  when a value exists. When `masked`, the outer `Box` sets a merged `contentDescription`
  (`label` or `"$label, entered"`) so TalkBack doesn't read "bullet, bullet…".
- **`@Composable private fun InfoCallout(text: String)`** (`:249`) — centered onSurfaceVariant text.

`LoginScreen` flow: holds `brand/email/password/pin` as local state; three
`rememberWearTextInput` lambdas feed the locals. If `ui.busy`, renders only a "Signing in…"
spinner and **returns early**. Otherwise a focusable `ScalingLazyColumn` (rotary → `scrollBy`)
with: title, `MorphSegmented` brand picker (Hyundai/Genesis/Kia), then either an `InfoCallout`
(Kia) or three `FieldRow`s (else), a "Sign in" `MorphButton` (hidden for Kia), an
`AnimatedVisibility`-wrapped error `Text` bound to `ui.message`, and a bottom tip. Sign-in calls
`vm.login(brand, email, password, pin)` with no local validation.

### `PinLockScreen.kt` internals

- **`private const val PIN_LENGTH = 4`** (`:48`).
- **`@Composable private fun PinDots(filled: Int, showError: Boolean)`** (`:54`) — a row of
  `PIN_LENGTH` dots; each animates color (error / primary if `i<filled` / surfaceContainerHigh)
  over `tween(150)` and size (11dp filled / 9dp) via a bouncy spring.
- **`@Composable private fun PinKey(label: String, onClick: () -> Unit)`** (`:84`) — a 48dp round
  key; tracks its own `MutableInteractionSource` pressed state and drives scale (0.88 pressed),
  bg (primary pressed / surfaceContainerHigh), content, and border color off it. Backspace key
  (`"⌫"`) gets a `contentDescription = "Backspace"`. Uses `indication = null` (custom animation
  replaces the ripple).
- **`@Composable private fun PinKeypad(onDigit, onBackspace)`** (`:133`) — 3×4 grid
  `[[1,2,3],[4,5,6],[7,8,9],["",0,⌫]]`; the empty string renders a same-sized invisible `Spacer`
  to keep 0/⌫ column-aligned. `⌫` routes to `onBackspace`, everything else to `onDigit(key)`.
- **`private enum class PinFlowStep { CONFIRM_CURRENT, ENTER_NEW, CONFIRM_NEW, REMOVING, DISABLING }`** (`:298`).

**`PinEntryScreen` flow** (`:163`): `buffer` state; `LaunchedEffect(error)` — on non-null error,
fires `haptics.reject()`, clears `buffer`, sets `showErrorTint`, plays a left-right shake via an
`Animatable shakeX` stepping through `[-8,8,-5,5,0]`, then clears the tint. `LaunchedEffect(Unit)`
runs a one-shot entrance alpha fade (0→1 over 220ms). Digit taps append to `buffer` (max
`PIN_LENGTH`) with a `tick`; when the 4th digit lands it calls `onSubmit(next)` **without
clearing** (dots stay filled = "verifying"). Optional `Cancel` text.

**`PinManagementOverlay` flow** (`:308`): `step` starts at `ENTER_NEW` for `SET` else
`CONFIRM_CURRENT`. `key(step) { when(step) ... }` — keying forces a new `PinEntryScreen` per step
so its entrance fade replays.
- `CONFIRM_CURRENT` → `vm.verifyPinForManagement(pin){ ok -> if(ok) step=REMOVING/DISABLING/ENTER_NEW else error="Wrong PIN" }`.
- `ENTER_NEW` → stash `firstEntry=pin`, `step=CONFIRM_NEW`.
- `CONFIRM_NEW` → if `pin==firstEntry` call `vm.setPin(pin){onDone()}` else set
  `error="Didn't match — try again"` + `step=ENTER_NEW` (deliberately **no** `error` param on
  this step's screen — see §8).
- `REMOVING` → `LaunchedEffect(Unit){ vm.clearPin(onDone) }`.
- `DISABLING` → `LaunchedEffect(Unit){ vm.setPinLockEnabled(false, onDone) }`.

### `SettingsScreen.kt` internals

- **`@Composable private fun SettingSection(title, content)`** (`:534`) — just
  `SectionCard(title, content = content)`.

`SettingsScreen` flow: focusable rotary-scroll `ScalingLazyColumn` inside an outer `Box`. Local
state: `pinFlow: PinFlowMode?`, `confirmSignOut: Boolean` (auto-reset after 4000ms via a
`LaunchedEffect(confirmSignOut)`). `advanced = ui.settings?.settingsMode == "advanced"` gates
three sections (Aurora, Watch text size, Tile chips). Sections in order: Settings header,
Accounts, Add account, Appearance (units segmented + text-scale readout + "Use phone's
location"), PIN lock, AI Summaries, Aurora (advanced), Watch text size (advanced), Tile chips
(advanced), per-slot Tile car pinning (only when `ui.cars.size > 1`), Tile order (info only),
Sync (resync + drive), Phone status, Refresh all cars, Sign out, version footer. Below the list:
`MessageSnackbar(ui.message){ vm.dismissMessage() }` and, when set,
`PinManagementOverlay(vm, mode, onDone = { pinFlow = null })`.

### `TripsScreen.kt` internals

- **`private fun tripDate(raw: String?): String`** (`:50`) — `bluelink.data.tripDate(raw, includeWeekday=false)`.

`TripsScreen` flow: `LaunchedEffect(vin){ vm.loadTrips(vin) }` fires once per distinct VIN.
`trips = ui.trips[vin]` (nullable — key absent = not-yet-loaded). `loading = "$vin:trips" in ui.pending`.
If `trips == null && loading` → first-load spinner, return early. Otherwise a focusable
`ScalingLazyColumn`: header, then if `trips.isNullOrEmpty()` a rich empty state distinguishing
`failed = vin in ui.tripsErrors` ("Couldn't load trips" + WifiOff + Retry) from "No trips yet"
(Route icon); else one `SectionCard` per trip (`key = index`) with conditionally-rendered
`StatusRow`s (distance, drive, idle>0, used kWh, regen>0, efficiency only when distance & kWh>0).

---

## 4. Data & types

### `PinFlowMode` (public enum, `PinLockScreen.kt:296`)
`SET`, `CHANGE`, `REMOVE`, `DISABLE` — which PIN-management flow the overlay runs.

### `PinFlowStep` (private enum, `PinLockScreen.kt:298`)
`CONFIRM_CURRENT`, `ENTER_NEW`, `CONFIRM_NEW`, `REMOVING`, `DISABLING` — internal step state
machine of `PinManagementOverlay`.

### `WearSegmentOption` (typealias, `Components.kt:615`)
`= com.bloo.uicommon.SegmentOption`. Constructed as `WearSegmentOption(key, label)`; watch
options carry no icon.

### `WearColors` (object, `WearTheme.kt:20`)
`chargeGreen`, `heat`, `cool` — `Color` wrappers over `BlooColors` ints. Only `chargeGreen` is
consumed in this unit (`ChargeRing`).

### Consumed data types (defined elsewhere, read here)
- **`WearUi`** (`WearViewModel.kt:146`) — the single UI state. Fields read by this unit:
  `screen: WearScreen` (Loading/SignedOut/Ready), `cars: List<CarView>`,
  `trips: Map<String, List<EvTrip>>` (note: the `TripsScreen` doc comment says
  `Map<String, List<Trip>?>`, but the actual type is non-nullable-value `Map`; `ui.trips[vin]`
  returns `List<EvTrip>?` because the **key may be absent**), `pending: Set<String>`
  (membership keys like `"$vin:trips"`, `"$vin:refresh"`), `busy: Boolean`, `message: String?`,
  `resyncBusy`, `driveSyncBusy: Boolean`, `accounts: List<String>` (emails),
  `phoneConnected: Boolean`, `settings: WearSettingsPayload?` (phone-synced),
  `localSettings: WearLocalSettings`, `pinLocked: Boolean`, `tripsErrors: Set<String>` (VINs).
- **`WearLocalSettings`** (`WearLocalStore.kt:141`) — watch-only settings. Fields read here:
  `fontScale: Float = 1f` (coerced 0.8..1.4 on read), `unitSystem: String = "imperial"`
  (`"metric"` toggles), `tileActions: List<String> = ["lock","climate"]`,
  `tileCarVins: List<String?> = List(WearTilePool.SIZE){null}` (null = "follow selected"),
  `pinLockEnabled: Boolean = false`, `pinLockTiming: String = "immediate"`,
  `hasPin: Boolean = false`.
- **`WearSettingsPayload`** (`:shared`) — phone-synced. Fields read here: `settingsMode`
  (`"advanced"`), `uiScale: Float?`, `aiEnabled: Boolean?`, `auroraEnabled`,
  `auroraColorMode: String?` (`complementary`/`material`/`custom`), `auroraCustomColor: String?`,
  `hapticsEnabled: Boolean?`, `colors: WearColorRoles?`.
- **`WearColorRoles`** (`:shared`) — ~25 int color roles, mapped by `schemeFrom` and read by the
  aurora background (`primary`, `tertiary`, `background`).
- **`EvTrip`** (`:shared`) — trip record. Fields read: `startdate: String?`, `distance` (nullable),
  `driveMinutes: Int?`, `idleMinutes: Int?`, `usedKwh` (nullable), `regenKwh` (nullable).
- **`Brand`** (`:shared`) — `HYUNDAI`, `GENESIS`, `KIA`; `Brand.valueOf(key)` on select.

### Encodings established / relied on in this unit
- **`pinLockTiming`** string keys: `"off"`, `"immediate"`, `"1min"`, `"5min"`, `"10min"`
  (segmented labels Off/Now/1m/5m/10m).
- **`unitSystem`**: `"imperial"` / `"metric"`.
- **`tileActions`** keys: `"lock"`, `"climate"`, `"charge"` (max 2, `distinct().takeLast(2)`).
- **`auroraColorMode`**: `"complementary"` / `"material"` / `"custom"`; `"custom"` is coerced to
  `"complementary"` for the watch segmented display (no hex keyboard).
- **Font-scale slider mapping**: displayed `0.8..1.4×`; slider is int steps `0..12` via
  `((draft-0.8f)/0.05f).roundToInt()` and back `0.8f + step*0.05f`.
- **RemoteInput slot key**: `KEY = "bloo_input"`.
- **`pending` membership keys**: `"$vin:trips"`, `"$vin:refresh"`.

---

## 5. State & concurrency

- **Single source of truth:** `WatchApp` collects `vm.ui: StateFlow<WearUi>` via
  `collectAsState()` (`WatchApp.kt:141`). Every screen receives `ui` by parameter and
  recomposes when it changes. No screen owns durable domain state.
- **Local Compose state (`remember { mutableStateOf }`):**
  - `LoginScreen`: `brand`, `email`, `password`, `pin`.
  - `PinEntryScreen`: `buffer`, `showErrorTint`, `Animatable shakeX`, `Animatable entranceAlpha`.
  - `PinLockScreen`: `error`. `PinManagementOverlay`: `step`, `firstEntry`, `error`.
  - `SettingsScreen`: `pinFlow`, `confirmSignOut`; `draft` (text-scale, keyed on
    `ui.localSettings.fontScale`).
  - `MapThumbnail`: `retryKey` (keyed on `url`).
  - `WearAuroraBackground`: `breathe` (`mutableFloatStateOf`).
- **Coroutine scopes:** each scrollable screen has `rememberCoroutineScope()` used only inside
  `onRotaryScrollEvent` to `scope.launch { state.scrollBy(...) }`. `LaunchedEffect`s run on the
  composition's scope: focus requests (`runCatching { focusRequester.requestFocus() }`),
  `vm.loadTrips`, `confirmSignOut` auto-reset (`delay(4000)`), the aurora breathing loop
  (`delay(80)` forever), PIN error shake, and entrance fades.
- **Dispatcher:** all effects here run on `Dispatchers.Main` (the composition dispatcher);
  actual persistence/IO happens inside `WearViewModel`/`WearLocalStore` off-screen.
- **Reduce-motion / haptics gating:** `LocalReduceMotion` (static local, set once in
  `BlooWearTheme`) and `LocalHapticFeedback` (swapped to `NoOpHapticFeedback` in `WatchApp`) are
  composition locals — flipping the haptics setting recomposes `WatchApp` and re-provides.
- **Recomposition triggers:** any `WearUi` field change; local state writes; and the
  independently-animated values in `MorphButton` (`pct`, `pressScale`, `bg`), `PinKey`
  (`scale`, `bg`, `border`), `PinDots` (`color`, `size`), `ChargeRing` (`animatedProgress`,
  `animatedColor`).
- **`onSettle` pattern:** `SliderRow`/`AnimatedSlider` fire `onValueChange` on every drag tick
  but `onSettle` only once on release — used for text-scale so the DataStore write + Wear Data
  Layer push happen once, not per tick (see §8).

---

## 6. Collaborators & data flow

**Downstream (this unit → `WearViewModel`), function calls:**
`login(brand,email,password,pin)`, `submitPin(pin){ok,lockoutMessage->}`,
`verifyPinForManagement(pin){ok->}`, `setPin(pin){onDone}`, `clearPin(onDone)`,
`setPinLockEnabled(enabled, onDone?)`, `setPinLockTiming(value)`, `setUnitSystem(value)`,
`setWeatherFromDeviceLocation()`, `setAiEnabled(bool)`, `setAuroraEnabled(bool)`,
`setAuroraColorMode(mode)`, `setFontScale(scale)`, `setTileActions(list)`,
`setTileCarVin(index, vin?)`, `resync()`, `syncDrive()`, `refreshAll()`, `signOutAll()`,
`loadTrips(vin)`, `dismissMessage()`.

**Upstream (state in):** everything via the `WearUi` parameter (§4), itself a `StateFlow`
combining phone-synced `WearSettingsPayload` and local `WearLocalSettings`.

**PIN persistence path:** `PinLockScreen` → `vm.submitPin` → `WearLocalStore.verifyPin` (salted
hash compare) → flips `pinLocked`. `setPin`/`clearPin`/`setPinLockEnabled` are async DataStore
writes whose completion callbacks drive `onDone`.

**Wear Data Layer / phone sync:** several `vm.setXxx` calls both write local DataStore AND push
over the Wearable Data Layer to the phone (explicitly noted for `setFontScale`; aurora/AI toggles
sync back the same way). Text input arrives via the **system RemoteInput overlay** (activity
result), not the Data Layer.

**External services:** `MapThumbnail` fetches OSM tiles over HTTPS through Coil
(`WearImage.loader`). `WatchApp` hosts navigation via `SwipeDismissableNavHost`.

**Called by:** `WatchApp` is the module entry composable (set as content by the wear activity,
wrapped in `BlooWearTheme`). `HomeScreen`/`TileReorderScreen` (other files) are siblings reached
through the same nav host. Shared widgets in `Components.kt` are called by every screen including
`HomeScreen`.

**`:uicommon` delegation:** `AnimatedSlider`, `AnimatedValue`, `MorphSegmented`, `tempColor`,
`weatherIcon`, `SegmentOption`, `BlooColors.buttonContainer`, `SoftDamping`. **`:shared`
(`bluelink.data`):** `relativeLabel`, `fmtMinutes`, `weatherLabel`, `weatherTemp`, `tripDate`,
`formatTripDistance`, `formatEfficiency`, `Brand`, `WearColorRoles`, `WearSettingsPayload`,
`BlooColors`, `EvTrip`.

---

## 7. Invariants & assumptions

- `WearUi` is never null in composables (always supplied by the collected StateFlow).
- `ui.trips[vin]` distinguishes three states: absent key (`null`) = not loaded; empty list =
  loaded-but-empty; non-empty = data. The empty-vs-failed distinction relies on
  `vin in ui.tripsErrors`.
- `PIN_LENGTH == 4` is assumed everywhere PIN dots/buffer are handled; `PinEntryScreen`
  auto-submits exactly when `buffer.length == PIN_LENGTH`.
- Rotary scroll only works because each `ScalingLazyColumn` explicitly claims focus via
  `.focusRequester(fr).focusable()` + `LaunchedEffect(Unit){ requestFocus() }`; without it a
  crown turn does nothing until a tap focuses something.
- Font-scale round-trip assumes the `0.8..1.4` range maps cleanly to int steps `0..12` at 0.05
  granularity; `WearLocalStore` coerces on read as a backstop.
- `tileActions` never exceeds 2 entries (`distinct().takeLast(2)`); the Tile only renders 2.
- `tileCarVins` index → slot mapping: a `null` slot means "follow selected car", NOT the
  same-index car (BlooTileService resolves it). Per-slot cards only appear when `ui.cars.size > 1`,
  bounded by `WearTilePool.SIZE`.
- `SliderRow` guards `step` with `coerceAtLeast(1)` to avoid a divide-by-zero
  `ArithmeticException` during composition.
- `ChargeRing` assumes percent may be null or out of `0..100`; both are handled (`—` label /
  `coerceIn`), so a bad snapshot never flashes red or overdraws the ring.
- The aurora Box relies on `AppScaffold(containerColor = Transparent)` when aurora is on — a
  non-transparent scaffold would paint over the gradient in the same Box.
- Kia sign-in cannot be completed on the watch (one-time code); `LoginScreen` assumes this and
  hides the fields + Sign-in button for `Brand.KIA`.
- `MapThumbnail` assumes GPS jitter is below ~11m; it keys the tile on rounded coordinates so a
  stationary car doesn't re-fetch tiles every poll.

---

## 8. Gotchas & sharp edges

- **`SectionCard` is a `Box`, not a `Card`** (`Components.kt:107-142`). Wear Compose Material3's
  `Card` has no non-interactive overload — every `Card` requires `onClick` — so a plain
  `Card(onClick={})` made every section a focusable "double-tap does nothing" TalkBack stop. The
  Box reproduces the flat-tonal look with `surfaceContainerLow` + a 0.18α outline rim + a
  bouncy `animateContentSize` (the slowest/bounciest spring in the file: `SoftDamping` +
  `StiffnessMediumLow`) so row-count changes animate like everything else.
- **Aurora is hand-ticked at ~12fps** (`WatchApp.kt:92-102`), deliberately bypassing Compose's
  animation clock (which would redraw the full-screen gradient up to 120×/sec) — a real battery
  and heat concern on the always-visible home backdrop.
- **`CONFIRM_NEW` intentionally has no `error` param** (`PinLockScreen.kt:350-357`). On a
  mismatch it sets `error` and switches `step=ENTER_NEW` in the same event, so Compose
  recomposes straight to `ENTER_NEW` — the confirm screen never gets a frame to show the error.
  The "Didn't match" message surfaces on the screen the user bounces back to.
- **`REMOVING`/`DISABLING` fire from `LaunchedEffect(Unit)`, calling `onDone` from the write's
  completion callback** (`:376`, `:384`) — an earlier version called `onDone()` synchronously,
  closing the overlay before the async DataStore write landed, briefly flashing the stale
  "Lock: On" toggle.
- **Disabling the lock requires the current PIN** (`DISABLE` → `CONFIRM_CURRENT`, `:384`,
  Settings `:209`). Turning "Lock: On" off is equivalent to removing the PIN (watch never locks
  again); requiring the PIN closes the window where someone holding the watch could disable the
  lock in two taps. It does NOT clear the stored PIN, so re-enabling needs no reset.
- **PIN pad keeps dots filled after the 4th digit** (`:245`) — leaving them filled reads as
  "verifying" during a cold DataStore read; a wrong PIN clears+shakes via the `error` effect and
  a correct one unmounts the screen, so there's no stale-filled state either way.
- **`PinManagementOverlay` uses `key(step)`** (`:323`) so each step gets a fresh
  `PinEntryScreen`, replaying its entrance fade instead of a hard pop.
- **`confirmSignOut` auto-resets after 4s** (`SettingsScreen.kt:96`) so a stale "tap again" can't
  sign you out much later.
- **Text-scale slider commits `onSettle` only** (`SettingsScreen.kt:338,351`) — `setFontScale`
  does both a DataStore write and a Wear Data Layer push; firing per drag tick raced dozens of
  near-simultaneous writes/IPC sends with no ordering guarantee. The `draft` is keyed on
  `ui.localSettings.fontScale` so an external sync resets it.
- **`MapThumbnail` retry uses `?retry=N`** (`:322`) to bust Coil's URL-derived cache key without
  changing the tile fetched — otherwise Coil just re-serves the cached failure.
- **Masked FieldRows override `contentDescription`** (`LoginScreen.kt:229-233`) because
  `MorphButton`'s label is a raw `Text` node — without it TalkBack reads "bullet, bullet…".
- **`MorphButton.toggled` must stay `null` for non-toggles** (`Components.kt:523`) — passing it
  makes the button announce a `Role.Switch` + on/off state, which is wrong for plain actions
  ("Settings", "Refresh") and pickers.
- **`SignedOut` is wrapped in `key(ui.accounts.size)`** (`WatchApp.kt:219`) so the LoginScreen is
  recreated when the account set changes.
- **`ui.trips` type mismatch in the doc comment** — `TripsScreen`'s KDoc calls it
  `Map<String, List<Trip>?>`, but the real `WearUi.trips` is `Map<String, List<EvTrip>>`; the
  nullability the code relies on comes from **absent keys**, not null values. Harmless but a
  future editor should not trust the comment's type.
- **`roundSafeHorizontalPadding` was retrofitted** — Settings, Login, Trips, reorder, and the
  PIN screen originally used a flat inset regardless of screen shape, sitting text too close to a
  round bezel. Every scrollable screen now calls it for `contentPadding`; `PinEntryScreen` uses
  a tighter `flat = 12.dp` override.
- **`WearHaptics` exists because everything used `TextHandleMove`** — before this vocabulary,
  plain taps and toggles felt identical; `tick`/`click`/`reject` map to distinct
  `HapticFeedbackType`s.
