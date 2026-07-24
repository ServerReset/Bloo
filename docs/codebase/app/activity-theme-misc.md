# app: MainActivity + Shortcuts + ui (Theme / Haptics / GlassChrome / Fireworks / BiometricAuth)

Deep-dive reference for the phone app's "shell" layer: the single Activity that hosts
Compose, the dynamic app-icon shortcut builder, and the small `ui` support package
(theming/color resolution, the haptic vocabulary, frosted-glass chrome modifiers,
the celebratory sound, and the biometric-prompt wrapper).

Files covered:
- `app/src/main/java/com/bloo/bluelink/MainActivity.kt`
- `app/src/main/java/com/bloo/bluelink/Shortcuts.kt`
- `app/src/main/java/com/bloo/bluelink/ui/Theme.kt`
- `app/src/main/java/com/bloo/bluelink/ui/Haptics.kt`
- `app/src/main/java/com/bloo/bluelink/ui/GlassChrome.kt`
- `app/src/main/java/com/bloo/bluelink/ui/Fireworks.kt`
- `app/src/main/java/com/bloo/bluelink/ui/BiometricAuth.kt`

---

## 1. Purpose

This unit is the thin Android/OS-facing shell around the app's real logic, plus the
purely-presentational primitives the whole Compose tree leans on.

- **`MainActivity`** — the app's *single* Activity (`FragmentActivity`, needed so the
  AndroidX BiometricPrompt can attach as a fragment). It hosts the entire Compose UI
  (`BlooApp`), performs one-time-per-launch process setup (edge-to-edge bars,
  scheduling all background WorkManager jobs, registering a screen-off receiver for
  app-lock timing), and routes app-icon shortcut intents into `AppViewModel`. All
  screen/business logic lives elsewhere (`AppViewModel` + the Compose tree); this class
  is deliberately plumbing (`MainActivity.kt:26-33`).
- **`Shortcuts`** — builds the dynamic app-icon long-press shortcut set (per-car
  quick actions + one "Open the OEM app" per brand) and encodes each as an `ACTION`
  intent that `MainActivity` parses back.
- **`ui/Theme.kt`** — the entire color/typography/shape/motion theming system:
  enums for the user's appearance choices, the hand-tuned Expressive light/dark
  palettes, hue-rotation recoloring for palettes (built-in and custom), AMOLED and
  vibrancy transforms, the `BlooTheme` root wrapper, and non-Compose resolvers
  (`blooColorScheme`, `resolveWidgetAccent`) so widgets/tiles/watch compute the exact
  same colors.
- **`ui/Haptics.kt`** — a tuned haptic "vocabulary": named effects (tick/click/heavy/
  toggle/dice/slot/loading/success/fireworks) built from `VibrationEffect` composition
  primitives on API 31+ with waveform fallbacks below.
- **`ui/GlassChrome.kt`** — the frosted-chrome look shared by all floating UI:
  a shared alpha constant, a gradient "rim" border modifier, and an ambient-halo
  shadow modifier. (Real hardware-blur "liquid glass" was removed in favor of this.)
- **`ui/Fireworks.kt`** — plays a short celebratory sound for the first-run confetti.
- **`ui/BiometricAuth.kt`** — a `Context`→`FragmentActivity` finder and a wrapper that
  shows the system biometric prompt and routes success/error to callbacks (used by the
  app-lock screen).

---

## 2. Public surface

### MainActivity.kt

- **`class MainActivity : FragmentActivity()`** (`MainActivity.kt:34`) — the single
  Activity. Extends `FragmentActivity` (not `ComponentActivity`) specifically so
  AndroidX `BiometricPrompt` can host itself. No public members beyond the overridden
  lifecycle callbacks:
  - **`override fun onCreate(savedInstanceState: Bundle?)`** (`:60`) — one-time
    per-window setup, then `setContent`. See §3 for step-by-step.
  - **`override fun onStop()`** (`:112`) — records `backgroundedAt =
    System.currentTimeMillis()` for the app-lock timer.
  - **`override fun onStart()`** (`:125`) — on every foreground *except* the first,
    calls `viewModel.maybeRelock(backgroundedAt, screenOffWhileAway)`; then clears
    `firstStart` and `screenOffWhileAway`.
  - **`override fun onDestroy()`** (`:136`) — `unregisterReceiver(screenReceiver)`
    (wrapped in `runCatching`), then `super`.
  - **`override fun onNewIntent(intent: Intent)`** (`:145`) — `setIntent(intent)` then
    `handleShortcutIntent(intent)`.

### Shortcuts.kt

- **`object Shortcuts`** (`Shortcuts.kt:13`) — app-icon shortcut factory.
  - **`const val ACTION = "com.bloo.bluelink.action.SHORTCUT"`** (`:15`) — the intent
    action `MainActivity.handleShortcutIntent` matches on.
  - **`const val EXTRA_VIN = "vin"`** (`:16`), **`const val EXTRA_CMD = "cmd"`** (`:17`)
    — intent-extra keys.
  - **`val ACTIONS = listOf("doors", "climate", "open")`** (`:20`) — selectable per-car
    shortcut commands, in priority order.
  - **`fun actionLabel(cmd: String): String`** (`:24`) — human label for the *in-app*
    shortcut picker: `"doors"→"Lock / unlock"`, `"climate"→"Climate"`, `"open"→"Open"`,
    else `cmd` with first char upper-cased. Distinct from `label` (OS-facing).
  - **`fun refresh(context, vehicles: List<Vehicle>, enabled: Set<String>? = null)`**
    (`:48`) — rebuilds and registers the whole dynamic shortcut set. See §3.

### Theme.kt

- **`enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }`** (`:38`).
- **`enum class FontChoice { SYSTEM, ATKINSON, GOOGLE_SANS }`** (`:45`).
- **`enum class ColorPalette(val label: String, val swatch: Color, internal val hue: Float)`**
  (`:53`) — built-in palettes; see §4.
- **`@Serializable data class CustomPaletteData(...)`** (`:67`) — user-authored palette;
  see §4.
- **`fun buttonContainer(): Color`** `@Composable` (`:303`) — the default outline-less
  button fill. Delegates to `com.bloo.uicommon.BlooColors.buttonContainer(
  surfaceContainerHighest, onSurface)` so the watch renders identically.
- **`fun blooColorScheme(context, dark, themeMode, dynamicColor, colorPalette,
  customPalette, vibrancy): ColorScheme`** (`:322`) — resolves the final Material 3
  `ColorScheme` *outside* composition. Used by `BlooTheme` and by Wear sync. See §3.
- **`fun BlooTheme(themeMode=SYSTEM, fontChoice=SYSTEM, dynamicColor=true,
  colorPalette=BLUE, customPalette=null, uiScale=1f, vibrancy=1f, content)`**
  `@Composable` `@OptIn(ExperimentalMaterial3ExpressiveApi)` (`:408`) — root theme
  wrapper; wraps `MaterialExpressiveTheme` + provides `LocalDensity` (scaled) and
  `LocalReduceMotion`. See §3/§5.
- **`val LocalReduceMotion = staticCompositionLocalOf { false }`** (`:456`) — true when
  the user disabled animations (Accessibility). Read across the app to skip animation.
- **`internal fun CarThemeOverride(paletteId, customPalettes, themeMode, vibrancy,
  content)`** `@Composable` (`:465`) — overrides *only* the color scheme for a per-car
  custom palette, inheriting typography/shapes/motion from the surrounding `BlooTheme`.
  Renders `content` unchanged when `paletteId` is null/unknown.
- **`internal fun ColorScheme.applyCustomPalette(p: CustomPaletteData): ColorScheme`**
  (`:106`) — recolor accent groups from a custom palette. See §3.
- **`internal fun ColorScheme.applyPalette(palette: ColorPalette): ColorScheme`** (`:131`)
  — recolor accent roles to a built-in palette hue.
- **`internal fun resolveWidgetAccent(context, appearance, vin: String? = null): Color`**
  (`:153`) — resolve just the accent `Color` outside Compose (widgets/tiles/
  notifications), mirroring `BlooTheme`'s logic. See §3.

  (Private-but-notable helpers: `Color.rotateHue`, `Color.extractHue`, `Color.saturate`,
  `variableFont`, `fontFamilyFor`, `expressiveTypography`; the `LightExpressive`/
  `DarkExpressive` schemes; `ExpressiveShapes`. See §3/§4.)

### Haptics.kt

- **`class Haptics(context: Context)`** (`Haptics.kt:18`) — the haptic engine.
  - **`@Volatile var enabled: Boolean = true`** (`:29-30`) — master gate; mutable
    from any thread.
  - Public effect methods (each plays a distinct pattern; API 31+ uses composition
    primitives, else waveform/one-shot fallback):
    - **`fun tick()`** (`:78`) — light crisp step (slider notches, list ticks).
    - **`fun click()`** (`:83`) — standard confirm (taps, expand/collapse).
    - **`fun heavy()`** (`:88`) — weighty confirm (lock/unlock landed, command sent).
    - **`fun toggleOn()`** (`:96`) — quick rise into a click.
    - **`fun toggleOff()`** (`:104`) — click falling away.
    - **`fun diceRoll()`** (`:115`) — tumbling burst landing on a knock (pull-to-refresh
      release).
    - **`fun slotSettle()`** (`:133`) — decelerating ticks that spin down (refreshed
      numbers rolling into place).
    - **`fun loadingSweep()`** (`:168`) — short soft→strong rise, looped by the UI
      while loading.
    - **`fun success()`** (`:178`) — rise then crisp click.
    - **`fun fireworks()`** (`:186`) — a boom scattering into crackling pops.
- **`val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }`** (`:228`) — lets any
  composable reach the engine; defaults to `null` (no-op / disabled).

### GlassChrome.kt

- **`fun glassContainerAlpha(frosted: Float = 0.74f): Float`** (`:28`) — the single alpha
  constant for frosted-chrome base fills (identity function returning its default;
  every call site shares this constant so changing the default restrengthens all frosted
  surfaces at once). Raised from `0.62`.
- **`fun Modifier.frostedRim(shape: Shape): Modifier`** `@Composable` (`:43`) — a 1dp
  border painted with a *vertical gradient* of `onSurface` alpha (top 0.24, middle 0.10,
  bottom 0.16) to read as a lit card edge.
- **`fun Modifier.ambientRing(shape: Shape): Modifier`** `@Composable` (`:77`) — a soft
  symmetric dark halo (`dropShadow` with `Color.Black` α0.30, blur 10dp, zero offset) to
  darken *all* sides against unpredictable photo backgrounds. Chain before
  `dropShadow`/`frostedRim`.

### Fireworks.kt

- **`object Fireworks`** (`Fireworks.kt:14`).
  - **`fun playSound(context: Context)`** (`:16`) — plays `res/raw/celebrate` if bundled,
    else the default notification ringtone. All in `runCatching`. See §3.

### BiometricAuth.kt

- **`fun Context.findFragmentActivity(): FragmentActivity?`** (`:11`) — walks the
  `ContextWrapper` `baseContext` chain to the hosting `FragmentActivity`, or `null`.
- **`fun showBiometricPrompt(activity, title, subtitle, onSuccess: () -> Unit,
  onError: (String) -> Unit)`** (`:21`) — builds and shows a `BiometricPrompt`, routing
  `onAuthenticationSucceeded`→`onSuccess()` and `onAuthenticationError(code, str)`→
  `onError(str.toString())`. See §3.

---

## 3. Internal structure & control flow

### MainActivity.onCreate (`:60-108`)
1. `super.onCreate`.
2. `enableEdgeToEdge` with fully transparent status + nav bar styles
   (`SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)`) so the app's gradient
   shows through and content draws behind the bars (`:64-67`).
3. On API 29+ (`Q`): `window.isNavigationBarContrastEnforced = false` (`:68-70`) — stop
   the OS auto-scrimming the nav bar.
4. Schedule all four background workers (each `schedule()` is idempotent/unique-work
   keyed, so re-calling on every cold start creates no duplicates) — order:
   `AlertWorker.schedule`, `WidgetRefreshWorker.schedule`, `DriveSyncWorker.schedule`,
   `UpdateCheckWorker.schedule` (`:71-81`), all on `applicationContext`.
5. Register `screenReceiver` for `ACTION_SCREEN_OFF` via
   `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (`:82-87`).
6. `handleShortcutIntent(intent)` — route the launching intent (`:90`).
7. `setContent { ... }` (`:91-107`): collects `viewModel.appearance` as Compose state;
   computes `activeCustom` = the custom palette matching `activeCustomPaletteId`
   **only when `!dynamicColor`** (else `null`); calls `BlooTheme(...)` with all
   appearance fields, wrapping `BlooApp(viewModel)`. Because `appearance` is collected as
   state, the whole tree recomposes live when settings change.

Note: notification permission is deliberately requested from the onboarding screen on a
button tap, not here (`:88-89`).

### MainActivity app-lock timing
- `backgroundedAt` (`:41`) set in `onStop`; `screenOffWhileAway` (`:42`) set by
  `screenReceiver` (`:44-48`) when `ACTION_SCREEN_OFF` arrives; `firstStart` (`:43`)
  gates the cold-start case. In `onStart`, cold start defers to the ViewModel's own init
  logic; warm resumes call `viewModel.maybeRelock(backgroundedAt, screenOffWhileAway)`.
  Both flags reset each cycle (`:131-132`).

### MainActivity.handleShortcutIntent (`:152-157`)
Returns early unless `intent.action == Shortcuts.ACTION`; extracts `EXTRA_VIN` and
`EXTRA_CMD` (returns if either missing); calls `viewModel.handleShortcut(vin, cmd)`.

### Shortcuts.refresh (`:48-64`)
Whole body wrapped in `runCatching` (shortcut APIs can throw; never crash).
1. `max = getMaxShortcutCountPerActivity(context).coerceAtLeast(4)`.
2. Build an `ArrayList<ShortcutInfoCompat>`:
   - For each **distinct brand** (`vehicles.distinctBy { it.brand }`) add
     `oemShortcut(context, v)` **first**, so the "Open OEM app" entries are never the ones
     dropped when a launcher caps the count (`:53-56`).
   - For every vehicle × every `cmd` in `ACTIONS`, add `carShortcut` **iff** `enabled ==
     null` (show all) *or* `id(cmd, v.vin)` is in `enabled` (`:57-61`).
3. `setDynamicShortcuts(context, items.take(max))` — truncate to the launcher's cap.

Helpers:
- **`label(cmd, name): Pair<String,String>`** (`:32-36`) — OS `(shortLabel, longLabel)`:
  `"doors"→("Doors","Lock or unlock $name")`, `"climate"→("Climate","Climate · $name")`,
  else `(name.take(10), "Open $name")`.
- **`id(cmd, vin) = "${cmd}_$vin"`** (`:41`) — stable id; must match the ids built inline
  in `carShortcut` (`"${cmd}_${v.vin}"`, `:74`) and the `enabled` set keys.
- **`carShortcut(context, v, cmd)`** (`:67-75`) — picks icon (`ic_shortcut_lock` /
  `ic_shortcut_climate` / else `ic_shortcut_car`), delegates to `shortcut(...)`.
- **`oemShortcut(context, v)`** (`:81-84`) — id `"bluelink_${v.brand.name}"`, label
  `v.brand.links.appName`, cmd `"bluelink"`; the vin is carried in extras but unused
  (any car of the brand would do — it just opens the OEM app).
- **`shortcut(context, id, vin, cmd, shortLabel, longLabel, icon)`** (`:89-110`) — builds
  the `Intent(context, MainActivity::class.java)` with `action=ACTION`, extras vin+cmd,
  `FLAG_ACTIVITY_CLEAR_TOP`; wraps in `ShortcutInfoCompat.Builder`.

### Theme color pipeline
- **`Color.rotateHue(degrees)`** (`:84-90`) — HSV hue shift, wrap `((h+d)%360+360)%360`
  (handles Kotlin's negative `%`); re-applies original alpha (`HSVToColor` drops alpha).
  Early-returns `this` when `degrees == 0f`.
- **`Color.extractHue()`** (`:94-98`) — the HSV hue channel (0..360).
- **`Color.saturate(factor)`** (`:309-315`) — scale HSV saturation, `coerceIn(0,1)`;
  early-return when `factor == 1f`.
- **`applyCustomPalette(p)`** (`:106-128`) — `primaryDelta = userPrimaryHue -
  BasePaletteHue`; secondary/tertiary each rotate by `(userOverrideHue - existingRoleHue)`
  if the override int is non-null, else by `primaryDelta` (preserving expressive offset).
  Rotates all four groups' primary/secondary/tertiary + their containers + `on*` roles.
  ARGB→Color uses `Color(int.toLong() and 0xFFFFFFFFL)` to avoid sign extension.
- **`applyPalette(palette)`** (`:131-143`) — single `delta = palette.hue - BasePaletteHue`;
  early-return `this` when 0; rotates the same accent roles.
- **`blooColorScheme(...)`** (`:322-393`) — three stages:
  1. **Base** selection by priority (`:337-344`): `canDynamic = dynamicColor && SDK>=S`;
     dynamic wins, else custom palette recolors Expressive, else built-in enum palette
     recolors Expressive; each picks light/dark per `dark`.
  2. **AMOLED** (`:349-362`): if `themeMode == AMOLED`, override only the surface tiers to
     black/near-black (`background`/`surface`/`surfaceContainerLowest`=`#000000`, then
     `#0A0A0A`, `#101012`, `#161618`, `#1D1D1F` up the container ladder), leaving accents
     and text untouched.
  3. **Vibrancy** (`:368-392`): if `vibrancy == 1f` return `amoled` unchanged; else
     `.saturate(vibrancy)` every *tinted/semantic* role (primary/secondary/tertiary +
     containers + on-roles, surfaceVariant/onSurfaceVariant, outline/outlineVariant,
     error family). Neutral background/surface/onSurface roles are intentionally excluded.
- **`resolveWidgetAccent(context, appearance, vin=null)`** (`:153-180`) — non-Compose
  accent resolver mirroring the theme's priority, but returns just `.primary`. Order:
  reads dark from `context.resources.configuration.uiMode`; base = Dark/LightExpressive;
  (1) per-car custom palette (`appearance.carCustomPaletteIds[vin]`) highest priority;
  (2) global `activeCustomPaletteId`; (3) dynamic color (API 31+); (4) built-in
  `applyPalette(appearance.colorPalette)`.
- **`BlooTheme(...)`** (`:408-453`) — resolves `dark` from `themeMode` (AMOLED⇒dark,
  SYSTEM⇒`isSystemInDarkTheme()`); `scheme = blooColorScheme(...)`; builds a scaled
  `Density(density.density, density.fontScale * uiScale)` so the "make everything bigger"
  preference scales text-driven layout; reads
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` once inside `remember` → `reduceMotion`;
  wraps `MaterialExpressiveTheme(scheme, MotionScheme.expressive(),
  expressiveTypography(fontChoice), ExpressiveShapes)` and provides `LocalDensity`
  (scaled) + `LocalReduceMotion`.
- **`CarThemeOverride(...)`** (`:465-496`) — finds palette by id (renders `content()`
  unchanged and returns if null); resolves `dark`; base =
  Dark/LightExpressive.applyCustomPalette; applies vibrancy to accent roles only (no
  surfaceVariant/outline/error here, unlike `blooColorScheme`); wraps a plain
  `MaterialTheme(colorScheme = scheme)` so typography/shapes/motion inherit.
- **Typography** — `variableFont(resId, weight, axis)` (`:243-247`) registers one
  `Font` per weight with a `wght` `FontVariation`; `fontFamilyFor(choice)` (`:253-269`)
  builds the `FontFamily` (SYSTEM→Default; ATKINSON/GOOGLE_SANS register 5 weights each,
  400/500/600/700/800, so no faux-bolding); `expressiveTypography(choice)` (`:272-292`)
  copies the base `Typography()` applying the family and heavier display/headline weights.

### Haptics internals
- Constructor picks `vibrator` via `VibratorManager.defaultVibrator` on API 31+, else the
  deprecated `VIBRATOR_SERVICE` (`:22-27`).
- `composes` (`:33-40`) = API≥S AND `hasVibrator()` AND (in `runCatching`, default false)
  `areAllPrimitivesSupported(PRIMITIVE_TICK, PRIMITIVE_CLICK)`.
- `hasAmplitude` (`:44`) = `hasAmplitudeControl()`.
- `play(effect?)` (`:50-55`) — central gate: returns if `!enabled`, `effect==null`, no
  vibrator, or `!hasVibrator()`; the actual `v.vibrate(effect)` is in `runCatching`
  (a missed haptic never crashes).
- `oneShot(ms, amplitude)` (`:58-63`) — API≥O; passes `DEFAULT_AMPLITUDE` when
  `!hasAmplitude`.
- `waveform(timings, amplitudes)` (`:68-73`) — API≥O; drops amplitudes when
  `!hasAmplitude`; `-1` = play once.
- `composed { add(...) }` (`:211-215`) + `CompositionBuilder` (`:217-223`) — a small DSL:
  each effect declares primitives as `add(primitive, scale, delayMs)`; wraps the built
  sequence in one `play`.
- `slotSettle` and `fireworks` build compositions manually (loops with growing delay /
  shrinking scale); `fireworks` even uses `Math.random()` for jitter (`:186-204`).

### Fireworks.playSound (`:16-30`)
Uses `context.applicationContext`; `runCatching`: `getIdentifier("celebrate","raw",pkg)`;
if found, `MediaPlayer.create(...)` with an `OnCompletionListener { it.release() }` then
`start()`; else default `TYPE_NOTIFICATION` ringtone via `RingtoneManager`. No synthesis.

### BiometricAuth
- `findFragmentActivity()` (`:11-18`) — loop over `ContextWrapper.baseContext` until a
  `FragmentActivity` or `null`.
- `showBiometricPrompt(...)` (`:21-49`) — `ContextCompat.getMainExecutor(activity)`;
  builds `BiometricPrompt` with a callback forwarding success/error; `PromptInfo` with
  title/subtitle, negative button "Cancel", allowed authenticators `BIOMETRIC_WEAK`;
  `prompt.authenticate(info)`.

---

## 4. Data & types

- **`enum ThemeMode`** (`:38`): `SYSTEM, LIGHT, DARK, AMOLED`. AMOLED implies dark +
  black surfaces (§3 AMOLED stage). `DARK` and `AMOLED` both resolve `dark=true`.
- **`enum FontChoice`** (`:45`): `SYSTEM` (FontFamily.Default), `ATKINSON`
  (Atkinson Hyperlegible Next, variable font), `GOOGLE_SANS` (Google Sans Flex, OFL —
  the open sibling of Product Sans, ships legitimately).
- **`enum ColorPalette(label, swatch, internal hue: Float)`** (`:53-60`):
  | const | label | swatch ARGB | hue° |
  |---|---|---|---|
  | `BLUE` | "Bloo" | `0xFF005AC1` | 217 |
  | `VIOLET` | "Violet" | `0xFF7B4DFF` | 255 |
  | `TEAL` | "Teal" | `0xFF00696E` | 184 |
  | `GREEN` | "Forest" | `0xFF3A6A2E` | 107 |
  | `AMBER` | "Amber" | `0xFFB26A00` | 36 |
  | `ROSE` | "Rose" | `0xFFB02E55` | 338 |

  `hue` is the target HSV hue each palette rotates the Expressive accents to; `BLUE`'s
  217 equals `BasePaletteHue`, so `BLUE` is a no-op rotation.
- **`data class CustomPaletteData`** (`@Serializable`, `:67-74`): `id: String`,
  `name: String`, `primaryArgb: Int` (required), `secondaryArgb: Int? = null`,
  `tertiaryArgb: Int? = null`. Each `*Argb` is a packed Android ARGB int
  (`android.graphics.Color` encoding). Only primary is required; missing secondary/
  tertiary follow primary's hue delta (preserving the base scheme's relative offsets).
  Serialized (kotlinx.serialization) as part of `SettingsStore.Appearance`.
- **`private const val BasePaletteHue = 217f`** (`:77`) — the hue `LightExpressive` is
  authored around; all rotations are measured relative to it.
- **`LightExpressive` / `DarkExpressive`** (`:186-226`) — the hand-tuned vibrant M3
  base schemes (blue primary / violet secondary / teal tertiary) used whenever dynamic
  color is off/unavailable.
- **`ExpressiveShapes`** (`:230-236`) — `extraSmall = CutCornerShape(6.dp)` (the
  intentional mixed-geometry accent), then RoundedCornerShape 16/24/32/40 dp for
  small→extraLarge.
- Haptics has no data classes/enums beyond the private `CompositionBuilder` (`:217`).

**Encoding note (matches project domain facts):** ARGB conversions use
`Color(int.toLong() and 0xFFFFFFFFL)` (`:107,111,116`) to mask off sign extension when
widening a possibly-negative `Int` ARGB to `Long`.

---

## 5. State & concurrency

- **`MainActivity`** holds plain mutable fields (`backgroundedAt`, `screenOffWhileAway`,
  `firstStart`) touched only on the main thread (lifecycle callbacks + a
  main-thread `BroadcastReceiver`); no locking needed. `viewModel` is obtained via
  `by viewModels()` (Activity-scoped, survives config changes). The only reactive state
  is `viewModel.appearance` collected via `collectAsState()` inside `setContent` — its
  emissions drive full-tree recomposition and thus live theme changes.
- **`Haptics.enabled`** is **`@Volatile`** (`:29`) — the one cross-thread field; every
  effect reads it through `play`. `Haptics` itself is otherwise immutable after
  construction. It's provided to the tree via `LocalHaptics`
  (`staticCompositionLocalOf`, default `null`).
- **Theme** state: `blooColorScheme`/`applyPalette`/`applyCustomPalette`/`saturate`/
  `rotateHue` are pure functions (no state). `BlooTheme` uses `remember` only to read
  `ANIMATOR_DURATION_SCALE` once (so it isn't re-queried per recomposition) and provides
  `LocalDensity`/`LocalReduceMotion` via `CompositionLocalProvider`. Recomposition is
  driven purely by changed parameters.
- **Dispatchers/scopes:** none in this unit. `Fireworks` uses `MediaPlayer`/
  `RingtoneManager` on the calling thread; `showBiometricPrompt` explicitly uses the
  main executor. Background *work* is delegated to WorkManager via the four
  `*.schedule()` calls (each keyed as unique/periodic work — idempotent).
- `LocalReduceMotion` and `LocalHaptics` are `staticCompositionLocalOf` (changing them
  triggers full recomposition of readers, not just those reading the value — acceptable
  since they're set once near the root).

---

## 6. Collaborators & data flow

- **`MainActivity` → workers:** `AlertWorker`, `WidgetRefreshWorker`, `DriveSyncWorker`,
  `UpdateCheckWorker` (`.schedule(applicationContext)`). These keep widgets, the watch,
  QS tiles, Drive settings sync, and GitHub-release self-update checks running while the
  app is closed (`MainActivity.kt:71-81`).
- **`MainActivity` → `AppViewModel`:** `viewModel.appearance` (StateFlow of
  `SettingsStore.Appearance`) drives the theme; `viewModel.maybeRelock(...)` for app-lock;
  `viewModel.handleShortcut(vin, cmd)` for shortcut routing (`:129,156`).
- **`MainActivity` → Compose:** `setContent { BlooTheme(...) { BlooApp(viewModel) } }`.
- **Intents (Shortcuts ⇄ MainActivity):** `Shortcuts.shortcut` encodes
  `ACTION`/`EXTRA_VIN`/`EXTRA_CMD` into an intent targeting `MainActivity`;
  `handleShortcutIntent` decodes them back. `refresh` reads `Vehicle` fields
  (`vin`, `name`, `brand`) and `brand.links.appName` (from `com.bloo.bluelink.data`).
  `refresh` is called by whoever owns the vehicle list + shortcut-enable settings
  (VM/settings layer) — this unit only defines the builder.
- **Theme resolvers shared off-Compose:** `blooColorScheme` and `resolveWidgetAccent` are
  the single source of truth reused by widgets, QS tiles, notifications, and Wear sync
  (so the watch mirrors the exact resolved colors). `resolveWidgetAccent` reads
  `SettingsStore.Appearance` fields: `carCustomPaletteIds`, `customPalettes`,
  `activeCustomPaletteId`, `dynamicColor`, `colorPalette`.
- **`buttonContainer`** delegates to `com.bloo.uicommon.BlooColors.buttonContainer` (the
  shared :uicommon module) so phone and watch buttons match.
- **`GlassChrome`** depends on `com.bloo.uicommon.dropShadow`; consumed by search bar,
  floating buttons, pebbles, dialogs, widgets.
- **`Haptics`** — provided via `LocalHaptics`; consumed all over the Compose tree.
- **`BiometricAuth`** — `findFragmentActivity` + `showBiometricPrompt` used by the
  app-lock UI to authenticate; success/error routed to VM callbacks.
- **`Fireworks`** — invoked by the first-run confetti moment (paired with
  `Haptics.fireworks()`).

---

## 7. Invariants & assumptions

- **Single Activity, must be `FragmentActivity`** — AndroidX `BiometricPrompt` attaches
  as a fragment; a plain `ComponentActivity` would break app-lock.
- **`schedule()` idempotency** — every `*.schedule()` in `onCreate` is assumed to be
  unique-work/periodic-keyed so repeated cold starts never create duplicate jobs.
- **App-lock timing** — assumes `onStop` always precedes the next `onStart`
  (`backgroundedAt` set before it's read); `firstStart` guarantees cold start defers to
  the VM's init logic exactly once.
- **`onNewIntent` must `setIntent`** — otherwise a later config-change recreation would
  re-process the stale original intent (`:141-149`).
- **Shortcut id stability** — `id(cmd,vin)` (`"${cmd}_$vin"`) must match the inline id in
  `carShortcut` and the keys in the `enabled` set; drift silently hides shortcuts.
- **OEM shortcuts added first** so they survive `items.take(max)` truncation.
- **`BasePaletteHue` == `ColorPalette.BLUE.hue` (217)** — `BLUE` and any custom palette
  whose primary hue is 217 rotate by zero (`rotateHue` early-returns).
- **ARGB masking** — widening ARGB `Int`→`Long` must use `and 0xFFFFFFFFL` or the alpha
  byte sign-extends and corrupts the color.
- **Vibrancy excludes neutral roles** in `blooColorScheme` (background/surface/onSurface)
  so backgrounds are never tinted; only accents/semantics saturate.
- **AMOLED only touches surface tiers** — accents/text stay identical to the base scheme.
- **Dynamic color requires API ≥ S (31)**; `canDynamic` guards it (theme falls back to
  Expressive on older devices).
- **`ANIMATOR_DURATION_SCALE` read once** — `reduceMotion` won't react to the setting
  changing while the app is open (acceptable trade-off, avoids per-recomposition query).
- **Haptics `composes` requires PRIMITIVE_TICK + PRIMITIVE_CLICK support** on API 31+;
  otherwise the whole engine uses waveform/one-shot fallbacks.

---

## 8. Gotchas & sharp edges

- **`glassContainerAlpha` is an identity function**, not a lookup — its whole value is
  being *one shared constant* every frosted call site funnels through, so bumping the
  `0.74f` default restrengthens dialogs/search bar/widget/pebbles uniformly. It was
  raised from `0.62` because floating chrome sits over car photos with no blur behind it
  and washed out over bright patches (`GlassChrome.kt:19-28`).
- **`frostedRim` gradient is drawn uniformly around the whole outline** — Compose's
  `border` paints the vertical-gradient brush all the way around, so the top-bright/
  bottom-dim asymmetry is what fakes a physical top-lit highlight (`:44-49`).
- **`ambientRing` must be chained *before* `dropShadow`/`frostedRim`** — it's the
  all-sides dark halo compensating for `dropShadow`'s single-side (downward) offset and
  `frostedRim`'s white-in-dark-mode border washing out over light photos (`:65-78`).
- **`activeCustom` in `MainActivity` is computed only when `!dynamicColor`** (`:93-95`) —
  dynamic color takes precedence and the custom palette is dropped to `null` before it
  reaches `BlooTheme`; `blooColorScheme` also re-guards this via `canDynamic` priority.
- **`CarThemeOverride` vibrancy differs from `blooColorScheme`** — it saturates only
  primary/secondary/tertiary (+containers/on-roles), *not* surfaceVariant/outline/error
  (`Theme.kt:484-494` vs `:368-392`). A per-car override therefore won't punch up error/
  outline tones the way the global scheme does.
- **`rotateHue`/`saturate` early-return on the identity case** (`degrees==0f`,
  `factor==1f`) — an optimization, but means a `BLUE` palette or `vibrancy==1f` genuinely
  skips the HSV round-trip (no precision loss for the common path).
- **HSVToColor drops alpha** — both `rotateHue` and `saturate` must re-apply
  `(alpha*255).toInt()` explicitly (`:89,314`).
- **`Haptics.play` swallows every platform exception** — a missed haptic is never worth a
  crash; do not rely on haptics as a signal that anything succeeded (`:50-55`).
- **`fireworks()` uses `Math.random()`** for pop jitter (`:194`) — non-deterministic; each
  celebration feels slightly different by design.
- **`slotSettle`/`fireworks` build compositions manually** (not via the `composed` DSL)
  because they need loops with evolving delay/scale (`:133-162`, `:186-204`).
- **`Fireworks.playSound` needs `res/raw/celebrate`** to play the real clip; without it,
  it silently uses the default notification ringtone — no error, no synthesis
  (`Fireworks.kt:8-13`).
- **`oemShortcut` carries a vin it never acts on** — the "Open OEM app" shortcut just
  launches; the vin is dead payload chosen by `distinctBy { it.brand }` order
  (`Shortcuts.kt:77-84`).
- **`refresh` wraps everything in `runCatching`** — a launcher rejecting shortcuts is
  swallowed, so a failure to register shows up as "shortcuts silently missing," not an
  exception (`Shortcuts.kt:49`).
- **`UpdateCheckWorker`** is load-bearing: Bloo isn't on the Play Store and self-updates
  from GitHub Releases via this worker, presenting newer builds by notification when the
  app is closed (`MainActivity.kt:78-81`).
- **`showBiometricPrompt` only allows `BIOMETRIC_WEAK`** (`:46`) — class-2 biometrics
  (e.g. some face unlocks) are accepted; this is an app-lock convenience gate, not a
  cryptographic/keystore-bound authentication.
