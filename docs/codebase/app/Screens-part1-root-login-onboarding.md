# Screens.kt — Part 1: App root, navigation, login, onboarding, car-setup wizard

**File:** `app/src/main/java/com/bloo/bluelink/ui/Screens.kt`
**Scope of this doc:** lines 1–2700 (the app-root shell, top-level navigation, login/OTP, onboarding wizard, car-setup wizard, the shared aurora backdrop, the biometric lock overlay, the empty state, and the beginning of `GarageScreen`). Lines 2700+ (the full garage layouts, pebbles, settings, dialogs) are covered in later parts.

This is a single 10,973-line file holding essentially all of the phone app's Compose UI. This document is a reference for the "entry" screens a user hits before reaching the garage.

---

## 1. Purpose

`Screens.kt` is the entire phone-app Compose surface for Bloo. The top of the file (this doc's scope) is the **app root and pre-garage flows**:

- `BlooApp(vm)` — the single root composable, wired to `AppViewModel`. Owns the snackbar, the haptics engine, the edge-to-edge gradient background, the biometric-lock blur overlay, and the `AnimatedContent` that cross-fades between the six top-level `Screen` states.
- The **login** flow (`LoginScreen`, `KiaOtpDialog`) — multi-brand sign-in (Hyundai/Genesis via PIN, Kia via OTP dialog).
- The **first-run onboarding** wizard (`OnboardingScreen` + its per-step pages).
- The **new-car detection** wizard (`CarSetupWizardScreen` → `CarFeatureWizard` + its per-feature pages).
- Shared decorative/utility composables used across these screens: `AuroraBackground`, `FireworksOverlay`, `GlassAlertDialog`, `LockOverlay`, `EmptyScreen`.

Everything here is stateless-by-default: real state lives in `AppViewModel`, collected via `collectAsState()`, and every user action calls back into `vm`. The screens hold only ephemeral UI state (`pageIndex`, typed-but-unsubmitted form fields, animation flags).

---

## 2. Public surface

Almost everything in this file is `private`. The only truly public entry point in scope is:

### `fun BlooApp(vm: AppViewModel)` — line 372–571
`@Composable`. Root composable for the whole phone app. Parameter: the shared `AppViewModel`.

What it does / renders (outside-in):
1. Collects `state = vm.state.collectAsState()` (a `UiState`) and `appearance = vm.appearance.collectAsState()` (a `SettingsStore.Appearance`) — line 374–375.
2. Creates `snackbar` (`SnackbarHostState`, remembered), `scope` (`rememberCoroutineScope`), grabs `LocalClipboardManager` and `LocalContext` — line 376–379.
3. Builds a single `Haptics(context.applicationContext)` (remembered) and sets `haptics.enabled = appearance.hapticsEnabled` **every recomposition** — line 382–383.
4. **Loading-sweep haptic loop** (line 391–401): `busy = state.loading || state.pending.isNotEmpty()`. A `LaunchedEffect(busy)` that, when busy, uses `lifecycleOwner.repeatOnLifecycle(STARTED)` to loop `haptics.loadingSweep()` every 560 ms. Gated on STARTED so a backgrounded activity does not keep buzzing in the pocket.
5. **Snackbar dispatch** (line 403–408): `LaunchedEffect(state.message)` — if non-null, `scope.launch { snackbar.showSnackbar(it) }` then `vm.clearMessage()`.
6. `CompositionLocalProvider(LocalHaptics provides haptics)` wraps everything so descendants can use the shared engine — line 410.
7. **Lock blur/alpha** (line 418–427): `lockBlur` animates 0.dp↔22.dp and `lockAlpha` animates 0f↔1f over 450 ms tween, driven by `state.locked`.
8. Nested `Box`es: outer full-size box; an inner box with `.blur(lockBlur)`; inside that a box painting a full-bleed `Brush.verticalGradient(surfaceContainerHigh, surface, surfaceContainerLow)` behind the transparent system bars — line 428–442.
9. `Scaffold(containerColor = Transparent, snackbarHost = {...})` — line 443. The snackbar host is **hand-rolled** (not M3's `Snackbar()`): a `Surface` with shape/color driven by `state.messageType` ("success"→primaryContainer, "info"→secondaryContainer, else→errorContainer, line 450–454), a swipe-to-dismiss horizontal drag (line 475–493), an `ErrorOutline` icon, a `SelectionContainer` for the message text, a copy-to-clipboard `IconButton`, and a dismiss `IconButton`. Explicit `semantics { liveRegion = LiveRegionMode.Polite }` (line 470) because the hand-rolled surface loses M3's automatic live-region announcement for TalkBack.
10. **Navigation** (line 519–558): computes `target = if (state.addingAccount) Screen.Login else state.screen`, then `AnimatedContent(targetState = target)` with a transition where Settings slides in from the right (`sign = +1`) and everything else from the left (`sign = -1`). The `when (screen)` dispatch:
    - `Screen.Login` → `Box(padding) { LoginScreen(loading, onLogin = vm::login, onCancel = if accounts non-empty vm::cancelAddAccount else null) }` plus `state.kiaOtp?.let { KiaOtpDialog(...) }`.
    - `Screen.Empty` → `Box(padding) { EmptyScreen(vm) }`.
    - `Screen.Onboarding` → `OnboardingScreen(vm)`.
    - `is Screen.CarSetup` → `CarSetupWizardScreen(vm, screen.vins)`.
    - `Screen.Garage` → full-bleed `Box`; if `appearance.auroraBackground` draws `AuroraBackground(...)` behind, then `GarageScreen(state, vm)`.
    - `Screen.Settings` → `SettingsScreen(vm)`.
11. **Lock overlay** (line 562–567): drawn last, on top of everything; if `lockAlpha > 0.01f`, `Box(fillMaxSize().alpha(lockAlpha)) { LockOverlay(vm) }`.

Everything else in scope is `private` (composables, data classes, enums, helper funcs) and documented in §3/§4.

---

## 3. Internal structure (private composables & helpers)

### Onboarding wizard model + screen

- **`private enum class WizardStepKind { POWERTRAIN, SEATS, STEERING }`** — line 575. The three per-car feature steps.
- **`private data class WizardPage(val kind: WizardStepKind, val vin: String? = null)`** — line 577–580.
- **`private fun buildSetupPages(vehicles: List<Vehicle>): List<WizardPage>`** — line 589–595. Flattens vehicles into one linear list: for each vehicle, POWERTRAIN then SEATS then STEERING. Drives the single `HorizontalPager`-style flow in `CarFeatureWizard`.
- **`private enum class OnboardingStepKind { INTRO, SETUP, CAR, CRASH_COURSE }`** — line 597.
- **`private data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)`** — line 599.
- **`private fun buildOnboardingSteps(vehicles, preConfiguredVins: Set<String> = emptySet()): List<OnboardingStep>`** — line 617–625. Order: INTRO, SETUP, then one CAR step per vehicle **whose vin is not in `preConfiguredVins`**, then CRASH_COURSE. `preConfiguredVins` lets a restored backup skip cars it already configured.

- **`private fun OnboardingScreen(vm: AppViewModel)`** — line 640–808. `@Composable`.
  - Local state: `preConfiguredVins` (`mutableStateOf<Set<String>>`), `pageIndex` (`mutableIntStateOf(0)`), `canBio = remember { vm.canUseBiometrics() }`.
  - `LaunchedEffect(state.powertrains.keys, pageIndex)`: while `pageIndex <= 1`, freeze `preConfiguredVins = state.powertrains.keys.toSet()`. This snapshots which cars a restored backup already configured, frozen once the user leaves the SETUP step (index 1), so a later live edit on a CAR page (which also mutates `state.powertrains`) can't retroactively shrink the step list underneath the user — line 653–657.
  - `steps = remember(state.vehicles, preConfiguredVins) { buildOnboardingSteps(...) }`. A `LaunchedEffect(steps)` clamps `pageIndex` to `steps.lastIndex` if it overflowed — line 658–659.
  - `goNext()`: if not last, `haptics?.click()` + `pageIndex++`; else `vm.finishOnboarding()` — line 664–671.
  - `goBack()`: if `pageIndex > 0`, `haptics?.click()` + `pageIndex--` — line 672–677. `BackHandler { goBack() }` (line 678) means the system back gesture steps back a page and bottoms out on page 0 — **the user can never exit onboarding via back**.
  - `LaunchedEffect(isLast)`: on the last page, `Fireworks.playSound(context)` + `haptics?.fireworks()` — line 680–685.
  - Renders: `AuroraBackground` full-bleed; `FireworksOverlay` if last; a `Column` with a top animated progress bar + "N/total" counter (line 700–727), an `AnimatedContent(targetState = pageIndex)` sliding horizontally by direction (line 730–763) that dispatches `step.kind` to `OnboardingIntroPage` / `OnboardingSetupPage` / `OnboardingCarPage` / `OnboardingCrashCoursePage`, and a Back/Next footer (line 766–805) with an `OutlinedCard` "Back" (only when `pageIndex > 0`) and a `MorphButton` whose label is "Enter Bloo" (last) / "Get started" (page 0) / "Next".

- **`private fun OnboardingIntroPage()`** — line 817–859. Welcome text + three feature-highlight `Surface` cards (Live status, Remote climate, Multiple cars). No per-item entrance animation (the outer `AnimatedContent` already animates the page).
- **`private fun OnboardingSetupPage(vm, state: UiState, context, canBio: Boolean)`** — line 872–999. Three optional setup cards, all skippable (Next always works):
  1. **Notifications** (only on `SDK_INT >= TIRAMISU`): `notifGranted` state seeded from `Notifications.hasPermission(context)`; a `rememberLauncherForActivityResult(RequestPermission())` requests `POST_NOTIFICATIONS`.
  2. **Fingerprint lock** (only if `canBio`): `bioEnabled` state; tapping runs `showBiometricPrompt(...)` on the host `FragmentActivity`, and on success calls `vm.setBiometricLock(true)`.
  3. **Sync across devices**: `showDriveDialog` state; `driveSaveLauncher` (`CreateDocument("application/json")`) → `vm.setSyncUri(it)`; `driveOpenLauncher` (`OpenDocument()`) → `vm.importSettingsAndSync(context, it)`. `syncEnabled = state.syncUri != null`. Shows `DriveSyncSetupDialog` when `showDriveDialog`.
- **`private fun OnboardingSetupCard(icon, title, body, done: Boolean, content: @Composable () -> Unit)`** — line 1008–1046. A solid `surfaceContainerHigh` card: circular icon chip (tinted `primaryContainer` + CheckCircle when `done`), title, body, then `content()` (a MorphButton or a "done" status row).
- **`private fun OnboardingCarPage(vehicle: Vehicle?, state, sc: SeatConfig, vm)`** — line 1053–1123. Per-car setup on one screen (returns early if `vehicle == null`). Sections: Powertrain (`PowertrainPicker(current = state.powertrainOf(vehicle)) { vm.setPowertrain(vehicle, pt) }`), Seats (iterates `SeatPositions`, each a `WizardSeatRow` wired to `vm.setSeatFlag(vehicle, key, value)`), and Extras (a steering-wheel-heat toggle `Surface` → `vm.setSeatFlag(vehicle, "sw", !sc.steeringWheel)`).
- **`private fun OnboardingCrashCoursePage()`** — line 1127–1168. "You're all set" + four tip cards (swipe between cars, tap/hold pebbles, hold to refresh, tune in Settings).

### Car-setup wizard (new-car detection)

- **`private fun CarSetupWizardScreen(vm, vins: List<String>)`** — line 1175–1185. `@Composable`. Shown when new cars are detected after onboarding. `BackHandler {}` **swallows back entirely** (mandatory flow). Filters `state.vehicles` to those in `vins`, builds pages via `buildSetupPages`, and delegates to `CarFeatureWizard(vm, pages, onComplete = { vm.finishCarSetup(vins) })`.
- **`private fun CarFeatureWizard(vm, pages: List<WizardPage>, onComplete: () -> Unit)`** — line 1197–1317. The reusable button-driven wizard shell. Only local state is `pageIndex`. Returns early if `pages.isEmpty()`. `goNext()`/`goBack()` mutate `pageIndex`; last-page Next calls `onComplete()`. Renders: vertical gradient background, top progress bar, `AnimatedContent(targetState = pageIndex)` sliding by direction that dispatches `pg.kind` to `WizardPowertrainPage` / `WizardSeatsPage` / `WizardSteeringPage`, and a Back/Next footer (last-page label "Done"). Note: **no `BackHandler` of its own** — when hosted by `CarSetupWizardScreen`, back is swallowed at that level.
- **`private fun WizardPowertrainPage(vehicle: Vehicle?, state, vm)`** — line 1327–1384. Lists all four `Powertrain.entries` as selectable `Surface` rows (emoji icon + label + description). Selection is driven straight off `state.powertrainOf(vehicle)` (no local pending); tapping calls `vm.setPowertrain(vehicle, pt)`. Descriptions: GAS "Combustion engine only", HYBRID "Gas + small electric motor (no plug)", PHEV "Gas + large battery you can charge", EV "Battery-only, no fuel tank".
- **`private fun WizardSeatsPage(vehicle: Vehicle?, seats: SeatConfig, vm)`** — line 1393–1435. Iterates `SeatPositions`, each a `WizardSeatRow` wired to `vm.setSeatFlag`.
- **`private data class SeatPosition(label, heatKey, coolKey, heat: (SeatConfig)->Boolean, cool: (SeatConfig)->Boolean)`** — line 1440–1446.
- **`private val SeatPositions`** — line 1448–1453. The single source of the seat matrix (used in all three places seats are configured): Driver ("dh"/"dc" → driverHeat/driverCool), Front passenger ("ph"/"pc" → passHeat/passCool), Rear left ("rlh"/"rlc" → rearLeftHeat/rearLeftCool), Rear right ("rrh"/"rrc" → rearRightHeat/rearRightCool).
- **`private fun WizardSeatRow(label, heat, cool, onHeat, onCool)`** — line 1455–1473. Label + two `WizardToggleChip`s ("Heat" and "Cool ❄️").
- **`private fun WizardToggleChip(label, selected, onClick)`** — line 1475–1492. Pill-shaped selectable `Surface`.
- **`private fun WizardSteeringPage(vehicle: Vehicle?, seats: SeatConfig, vm)`** — line 1497–1539. Single "Heated steering wheel" toggle → `vm.setSeatFlag(vehicle, "sw", it)`.
- **`private fun WizardFeatureToggle(title, body, checked, onChecked)`** — line 1542–1566. A `toggleable` row with `Role.Switch`; the inner `Switch` has `clearAndSetSemantics {}` so TalkBack sees one toggle, not two focus stops.

### Fireworks

- **`private class Burst(x, y, start, life, hue, count, maxR)`** — line 1568.
- **`private fun FireworksOverlay(modifier)`** — line 1584–1618. Remembers 7 randomized `Burst`s; drives a single `Animatable t` 0→1 over 2600 ms; on `Canvas`, each burst reads its local progress `(t.value - b.start)/b.life`, visible only in (0,1), placing `b.count` particles evenly on a circle of radius `local * b.maxR * height`, alpha `1-local`, with a `local² * height * 0.06` downward gravity drift. Runs once, never loops.

### Login

- **`private val FieldShape = RoundedCornerShape(18.dp)`** — line 1622. Shared field shape.
- **`private fun LoginScreen(loading: Boolean, onLogin: (String, String, String, Brand) -> Unit, onCancel: (() -> Unit)? = null)`** — line 1640–1863.
  - Local state: `email`, `password`, `pin`, `brand` (default `Brand.HYUNDAI`), all `mutableStateOf`. Nothing persists until `onLogin` fires, so switching brands mid-entry preserves typed values.
  - `shortScreen = cfg.screenHeightDp < 520` selects hero height (96 vs 160 dp) and wordmark type scale.
  - Brand-specific copy recomputed each recomposition: `brandSubtitle`, `emailLabel` (line 1656–1665).
  - `formVisible` flips true one frame after composition (`LaunchedEffect(Unit)`) to trigger the slide-up-and-fade entrance — line 1668–1669.
  - `if (onCancel != null) BackHandler { onCancel() }` — line 1671.
  - Renders: `AuroraBackground` full-bleed, a scrolling `Column` with a "Bloo" wordmark hero + crossfading subtitle, then an `AnimatedVisibility(formVisible)` form: "Sign in with" + `MorphSegmented` over `Brand.entries` (→ `brand = Brand.valueOf(key)`), email field, password field, and a PIN field wrapped in `AnimatedVisibility(!brand.usesOtpLogin)` (shown only for Hyundai/Genesis; Kia uses OTP). The sign-in CTA `MorphButton` calls `onLogin(email, password, pin, brand)`, shows a `LoadingIndicator()` while `loading`, and cross-fades its "Sign in to {brand.label}" text. Optional Cancel button (when `onCancel != null`). "Forgot password?" `MorphTextButton` opens the brand's portal URL via `Intent.ACTION_VIEW` (Hyundai/Genesis/Kia URLs at line 1836–1840). Closing privacy note reads "Credentials are sent directly to {brand.label}'s telematics servers and stored encrypted on this device."
- **`private fun KiaOtpDialog(otp: KiaOtpUi, loading: Boolean, vm: AppViewModel)`** — line 1869–1925. `AlertDialog` shown over the login form during a Kia OTP challenge.
  - `code` state, keyed on `otp.sentTo` (resets when a code is (re)sent).
  - When `otp.sentTo == null` (choose destination): title "Verify it's you", buttons for "Email" (`vm.kiaSendOtp("EMAIL")`, shown if `otp.challenge.hasEmail`) and "Text message" (`vm.kiaSendOtp("SMS")`, if `otp.challenge.hasSms`), each appending the masked address/number if present.
  - When `otp.sentTo != null` (enter code): title "Enter your code", a code `OutlinedTextField`, and a "Verify" confirm button (`vm.kiaVerifyOtp(code)`, enabled when `!loading && code.isNotBlank()`).
  - Dismiss button "Cancel" → `vm.kiaCancelOtp`; `onDismissRequest` also cancels unless `loading`.

### Shared dialog + backdrop + lock + empty

- **`private fun GlassAlertDialog(onDismissRequest, icon, title, text: @Composable ColumnScope.() -> Unit, buttons: @Composable ColumnScope.() -> Unit)`** — line 1943–2007. The shared "important pop-up" shell used by update-available and Drive-sync-setup. Uses `Dialog(...)` (own platform window) with a `SideEffect` that reflectively calls `View.setForceDarkAllowed(decorView, false)` on API 29+ to stop Android's Force-Dark heuristic from re-inverting explicitly-colored text (reflection because the method isn't in every compileSdk stub). Renders a frosted `Surface` (icon chip, headline, scrollable `text` slot capped at 360 dp, stacked `buttons`).
- **`private fun triangleWave(elapsedMs: Long, periodMs: Long): Float`** — line 2014–2017. Triangle wave in [0,1]: rises for `periodMs`, falls for `periodMs`, repeats.
- **`private fun AuroraBackground(modifier, appearance: SettingsStore.Appearance? = null, refreshing: Boolean = false)`** — line 2044–2220. The animated gradient-blob backdrop behind login/onboarding/(optional) garage. Detailed in §5/§8.
- **`private fun LockOverlay(vm)`** — line 2231–2313. The biometric lock overlay. `authenticate()` runs `showBiometricPrompt(...)` on the host activity → `vm.unlocked()` on success. `LaunchedEffect(Unit) { authenticate() }` prompts on first show. A dimmed scrim (`Color.Black alpha 0.45`) that swallows taps (no-ripple `clickable`). A floating back arrow → `vm.lockToLogin()`. Centered fingerprint icon + "Bloo is locked" + a white "Unlock" `MorphButton`. `compact = isCompactCoverScreen()` scales sizes down for cover screens.
- **`private fun EmptyScreen(vm)`** — line 2318–2452. Shown for `Screen.Empty`. Distinguishes three causes (line 2328–2345): `state.accounts.isEmpty()` → CloudOff "Not signed in"; `accounts non-empty && state.garageLoadError != null` (`loadFailed`) → WifiOff "Couldn't load your vehicles" (+ the error string); else → DirectionsCar "No vehicles found". Fade+slide-up entrance via two `Animatable`s. Header row ("Bloo" + `FloatingIcon` Refresh→`vm.loadGarage()` + Settings→`vm.openSettings()`). Primary action is "Open Settings" (`vm.openSettings()`) when not signed in, else "Try again"/"Reload" (`vm.loadGarage()`). Plus an "Account Settings" `MorphTextButton`.

### Garage layout helpers (start of the garage section)

- **`private const val MIN_CARD_DP = 320`** — line 2457. Minimum comfortable column width before adding another car column.
- **`private const val COVER_SCREEN_HEIGHT_DP = 570`**, **`COVER_SCREEN_WIDTH_DP = 600`** — line 2467–2468. Shared cover-screen thresholds (previously GarageScreen and LockOverlay each had their own, causing inconsistent layouts on one device).
- **`private fun isCompactCoverScreen(): Boolean`** — line 2472–2476. True when `screenWidthDp < 600 && screenHeightDp < 570` (both checked so a wide-short tablet-landscape doesn't false-positive).
- **`private fun coverScaled(base: Dp, refWidthDp: Float = 280f): Dp`** — line 2484–2489. Scales a reference spacing value by `(screenWidthDp / refWidthDp)` clamped to [0.6, 1.4].
- **`private enum class CameraEdge { TOP, BOTTOM, LEFT, RIGHT }`** — line 2495.
- **`private fun cameraEdgeOf(rect: Rect?, viewWidthPx, viewHeightPx): CameraEdge?`** — line 2501–2510. Returns the edge a cutout is flush against (smallest margin), or null.
- **`private fun GarageScreen(state: UiState, vm: AppViewModel)`** — line 2542+ (extends beyond this doc's scope). Top-level garage dispatcher. In scope here (line 2543–2748): returns early if no vehicles; sets up stale-data warning (`LaunchedEffect(currentVehicle?.vin, currentFetchedAt)` at line 2551 waits 25 s then `vm.reportError(...)`, cancellable when fresh data lands), the one-time post-onboarding settings hint (line 2564), a settle haptic on refresh completion (line 2574), pull-to-refresh overlay state (`pullFractionState`, `dotsAlpha`, `refreshShift`), layout selection (`large`, `compact`, `perPage`, `canExpand`, `singleLarge`, `expandedIdx`), the cover-screen hint, and the expanded/collapsed `HorizontalPager`s with infinite-wrap virtual paging. Full detail in a later part.

---

## 4. Data & types defined here (in scope)

- **`WizardStepKind`** (enum, line 575): `POWERTRAIN`, `SEATS`, `STEERING`.
- **`WizardPage`** (data class, line 577): `kind: WizardStepKind`, `vin: String? = null`.
- **`OnboardingStepKind`** (enum, line 597): `INTRO`, `SETUP`, `CAR`, `CRASH_COURSE`.
- **`OnboardingStep`** (data class, line 599): `kind: OnboardingStepKind`, `vin: String? = null`.
- **`SeatPosition`** (data class, line 1440): `label: String`, `heatKey: String`, `coolKey: String`, `heat: (SeatConfig)->Boolean`, `cool: (SeatConfig)->Boolean`. The `heatKey`/`coolKey` strings ("dh","dc","ph","pc","rlh","rlc","rrh","rrc","sw") are the exact keys passed to `vm.setSeatFlag(...)` — the persisted flag identifiers.
- **`Burst`** (class, line 1568): `x, y` (fractions 0–1 of width/height), `start` (delay fraction of the shared 0–1 clock), `life` (fraction), `hue` (0–360), `count: Int` (particles), `maxR` (max radius as a fraction of height).
- **`CameraEdge`** (enum, line 2495): `TOP`, `BOTTOM`, `LEFT`, `RIGHT`.

Types **referenced but defined elsewhere** (imports at line 298–345 and other Screens parts / `:shared`): `AppViewModel`, `UiState`, `Screen` (sealed; variants `Login`, `Empty`, `Onboarding`, `CarSetup(vins)`, `Garage`, `Settings`), `Brand` (enum HYUNDAI/GENESIS/KIA with `.label`, `.usesOtpLogin`), `Powertrain` (GAS/HYBRID/PHEV/EV), `SeatConfig`, `Vehicle`, `SettingsStore.Appearance`, `KiaOtpUi`/`KiaOtpChallenge`, `Haptics`, `Fireworks`, `MorphButton`/`MorphSegmented`/`MorphTextButton`/`SegmentOption`/`PowertrainPicker`/`FloatingIcon`/`StatusBarScrim`/`PagerDots`/`BackdropHost`/`CarThemeOverride`/`ExpandedCar`/`CompactGarage`/`SettingsScreen`/`DriveSyncSetupDialog` (later parts of Screens.kt or sibling UI files).

Module-level mutable state: **`private val coldStartIntroPlayed = mutableSetOf<Any>()`** — line 352. A process-global set of `ReorderColumn.introKey`s that have already played their cold-start intro, keyed per-vehicle (not one global flag) so a prefetched off-screen pager neighbour can't "use up" the intro before the visible page composes. (Used later in the file, but declared here.)

Key domain encodings relevant here (from KEY DOMAIN FACTS + this code): `Powertrain` is the user's manual override consumed via `state.powertrainOf(vehicle)`; `SeatConfig` boolean flags are toggled by string key through `vm.setSeatFlag`. `SeatLevel.apiValue` and plugType/batteryPlugin schemes are not touched in this range.

---

## 5. State & concurrency

- **Source of truth:** `AppViewModel.state` (`StateFlow<UiState>`) and `AppViewModel.appearance` (`StateFlow<SettingsStore.Appearance>`), collected with `collectAsState()`. Every top-level screen re-collects `vm.state` locally (`OnboardingScreen`, `CarSetupWizardScreen`, `CarFeatureWizard`, `EmptyScreen`, `GarageScreen`); `BlooApp` collects both once and passes `state`/`appearance` down where cheap (e.g. it reuses `appearance` for the Garage branch rather than re-subscribing — line 548–554).
- **Local UI state** is `remember`/`mutableStateOf`/`mutableIntStateOf`/`mutableFloatStateOf` and never persisted until a `vm` callback fires:
  - Login: `email`, `password`, `pin`, `brand`, `formVisible`.
  - Onboarding: `pageIndex`, `preConfiguredVins`, `canBio`; per-card `notifGranted`, `bioEnabled`, `showDriveDialog`.
  - Wizard: `pageIndex` only.
  - Garage (in scope): `sessionStartMs`, `wasRefreshing`, `pullFractionState`, `coverHintShown` (`rememberSaveable`), various derived/animated values.
- **Coroutines / effects:** `LaunchedEffect` for one-shot and keyed side effects; `rememberCoroutineScope()` in `BlooApp` for launching snackbar shows and swipe-dismiss animations. The busy haptic loop uses `repeatOnLifecycle(STARTED)` so it pauses when backgrounded. The stale-data warning `delay(25_000)` in `GarageScreen` is intentionally inside a keyed `LaunchedEffect(currentVehicle?.vin, currentFetchedAt)` so fresh data cancels it. The aurora ambient drift loops `while (true) { ...; delay(80) }` (≈12 fps, deliberately not riding the frame clock — see §8).
- **Animations:** `animateDpAsState`/`animateFloatAsState` for lock blur/alpha, progress bars, `dotsAlpha`, `refreshShift`; `Animatable` for fireworks `t`, aurora `explosion`, EmptyScreen entrance, and snackbar swipe offset. `AnimatedContent`/`AnimatedVisibility` for all screen/page/label transitions.
- **Dispatchers:** the composables themselves don't switch dispatchers; `withContext`/`Dispatchers` are imported for use lower in the file. Sensor callbacks in `AuroraBackground` run on the sensor thread and write to Compose `mutableFloatStateOf` (`tiltX`/`tiltY`) read in the draw phase.
- **No explicit locks here.** Process-wide serialization of vehicle commands is `BlueLinkGate.statusMutex` inside `AppViewModel`/data layer, not in this UI.

---

## 6. Collaborators & data flow

**Calls into `AppViewModel` (data leaving the UI):**
- Auth: `vm.login(email, password, pin, brand)`, `vm.cancelAddAccount()`, `vm.kiaSendOtp("EMAIL"|"SMS")`, `vm.kiaVerifyOtp(code)`, `vm.kiaCancelOtp()`.
- Onboarding/setup: `vm.finishOnboarding()`, `vm.finishCarSetup(vins)`, `vm.setPowertrain(vehicle, pt)`, `vm.setSeatFlag(vehicle, key, bool)`, `vm.setBiometricLock(true)`, `vm.setSyncUri(uri)`, `vm.importSettingsAndSync(context, uri)`, `vm.canUseBiometrics()`.
- Lock: `vm.unlocked()`, `vm.lockToLogin()`.
- Navigation/garage: `vm.openSettings()`, `vm.loadGarage()`, `vm.selectIndex(i)`, `vm.expand(i)`, `vm.collapse()`, `vm.refreshStatus(vehicle)`, `vm.reportError(msg)`, `vm.reportInfo(msg)`, `vm.dismissSettingsHint()`, `vm.clearMessage()`.
- Reads: `vm.state`, `vm.appearance`, and derived `state.powertrainOf(vehicle)`, `state.fetchedAt(vehicle)`.

**Data entering the UI (via `UiState`):** `screen`, `addingAccount`, `accounts`, `vehicles`, `currentIndex`, `expandedIndex`, `loading`, `refreshing`, `pending`, `message`/`messageType`, `kiaOtp` (`KiaOtpUi`), `powertrains`, `seatConfigs`, `garageLoadError`, `syncUri`, `showSettingsHint`. Via `appearance`: `hapticsEnabled`, `auroraBackground`, `auroraMotion`, `auroraColorMode`, `auroraCustomColor`, `themeMode`, `vibrancy`, `columnsFlipped`, `carCustomPaletteIds`, `customPalettes`.

**Android platform channels:**
- Activity-result launchers: `RequestPermission` (POST_NOTIFICATIONS), `CreateDocument`/`OpenDocument` (Drive sync JSON) — the sync URI ultimately flows to `SettingsStore`/Drive backup.
- Intents: `Intent(ACTION_VIEW, forgotUrl)` opens the brand password portal in a browser.
- Biometrics: `showBiometricPrompt(activity, ...)` via `context.findFragmentActivity()`.
- Sensors: `SensorManager` accelerometer (`TYPE_ACCELEROMETER`, `SENSOR_DELAY_UI`) for aurora motion mode.
- `Notifications.hasPermission(context)`, `Fireworks.playSound(context)`.

**Composition locals:** `LocalHaptics` (provided by `BlooApp`, consumed everywhere), `LocalPullFraction` (provided by `GarageScreen`).

Not directly touched in scope: WearSync (phone↔watch), WorkManager, and the vehicle telematics APIs — those live in the data/view-model layers.

---

## 7. Invariants & assumptions

- **`buildOnboardingSteps` always yields INTRO at index 0 and SETUP at index 1** before any CAR step. `OnboardingScreen` relies on this: `preConfiguredVins` freezes while `pageIndex <= 1`, i.e. through the SETUP step, before the first CAR page (line 655–657).
- **`OnboardingScreen` can never be exited by back** — `BackHandler` bottoms out at page 0 (line 678). `CarSetupWizardScreen` fully swallows back (`BackHandler {}`, line 1177) — it's a mandatory flow; it only leaves once `vm.finishCarSetup(vins)` navigates away (every car in `vins` must be configured, per the doc comment).
- Wizard pages assume `vehicle != null` and **return early** if it's null (line 1060, 1333, 1399, 1503) — the caller looks up the vehicle by vin from `state.vehicles` and passes null if not found.
- The powertrain/seat wizard pages have **no local pending state**; highlight and toggle state read straight from `state` (`state.powertrainOf(vehicle)`, the `SeatConfig`), so a tap only reflects after the view model emits. This assumes `vm.setPowertrain`/`vm.setSeatFlag` update `state` promptly.
- `pageIndex` is clamped to `steps.lastIndex` after the step list changes (line 659), assuming the list can only shrink under it in edge cases.
- Snackbar dispatch assumes `state.message` is cleared exactly once per emission (`vm.clearMessage()` right after showing) so the `LaunchedEffect(state.message)` doesn't re-fire.
- The busy haptic loop assumes `busy` accurately reflects in-flight work (`state.loading || state.pending.isNotEmpty()`).
- `AuroraBackground` assumes the accelerometer may be absent (`sensor != null` guard, line 2105) and that hex parsing may fail (`runCatching`, falls back to theme colors).
- `GarageScreen` assumes `state.currentIndex` may be out of range and always `.coerceIn`s it (line 2548, 2660, 2729).
- The stale-data warning assumes `currentFetchedAt < sessionStartMs` (only warn about data that was already old when the session began, not data fetched this session) and that a fresh fetch changes `currentFetchedAt`, restarting/cancelling the effect (line 2551–2560).

---

## 8. Gotchas & sharp edges

- **`haptics.enabled` is set on every recomposition of `BlooApp`** (line 383) rather than in an effect — cheap, and keeps it in sync with `appearance.hapticsEnabled` without an extra effect.
- **Busy haptic loop is lifecycle-gated** (`repeatOnLifecycle(STARTED)`, line 395). Without the gate, a backgrounded activity keeps its composition (and `LaunchedEffect`s) alive, so a slow command kept vibrating the phone in the user's pocket. The `if (!busy) return@LaunchedEffect` (line 394) avoids entering `repeatOnLifecycle` at all when idle.
- **Hand-rolled snackbar loses M3's auto live-region** — must set `semantics { liveRegion = Polite }` (line 470) or TalkBack never announces command results; a copy button and dismiss button are added because the swipe-to-dismiss drag has no TalkBack equivalent (line 507–514).
- **`preConfiguredVins` freeze window** (line 655–657): editing a CAR page updates `state.powertrains`, which would otherwise re-run `buildOnboardingSteps` and remove the very step the user is on. Freezing it once past the SETUP step prevents the step list shrinking underfoot.
- **`OnboardingSetupPage` cards float text on solid surfaces, not directly on the Aurora** — a moving colourful backdrop is a poor contrast surface for plain text (comment at line 861–871, 1001–1007). Same reasoning behind the many "solid surface" choices.
- **`GlassAlertDialog` reflectively disables Force Dark** on its own dialog window (line 1963–1976). `Dialog()` opens a platform window that doesn't inherit the Activity's `forceDarkAllowed=false`, so on API 29+ Android re-inverts already-dark explicitly-colored text (title/body rendered near-black on near-black). Reflection is used because `setForceDarkAllowed` isn't a resolvable method against every compileSdk stub, even though it's a real on-device public API.
- **Aurora ambient drift is hand-ticked at ~12 fps (`delay(80)`), not on the Compose animation clock** (line 2171–2199). Under a heavy 90 dp blur, riding the frame clock forced a full-screen blur redraw every vsync (up to 120×/s) for no visible benefit — a real, sustained GPU/heat cost whenever this background is on screen (which is most of the time). The blur was also reduced from 120 dp to 90 dp because 120 dp smoothed the blobs into a static wash.
- **Aurora "motion" mode uses a fast-EMA-minus-slow-EMA of the accelerometer** (line 2088–2101). Raw values bake in however the phone is generally held (gravity puts `values[1]` near ±9.8 when upright), which pinned the blobs off-center and saturated tilt. Subtracting a slow-moving baseline isolates *deliberate* tilt and re-centers if you settle into a new hold. Both modes now run the ambient drift; motion mode adds tilt on top (previously motion froze the drift, so a still phone showed a dead frame). Switching away from motion resets `tiltX/tiltY` (line 2108–2112) so blobs don't stick at a stale offset.
- **Aurora `explosion` pulse guarantees a grow-then-shrink** (line 2155–2162): a fast refresh flipped `refreshing` back to false before the spring visibly moved. Animating to peak, holding `delay(220)`, then shrinking guarantees the "grow" half is seen regardless of refresh speed.
- **Expanded-pager transform reads the continuous offset ONLY inside `graphicsLayer {}`** (draw phase), never as a plain val (line 2673–2699). Reading it in composable scope subscribed the whole page subtree to recompose on every drag frame — the real cause of swipe jank. A prior "snap bounce" spring off a discretized settled boolean was also removed because it lagged the transform behind the drag; the raw offset now drives scale/alpha directly. Blur and rotationZ tilt were tried and removed (read as worse than plain fade/scale).
- **Infinite wrap-around paging** (expanded pager line 2658–2662, collapsed pager line 2727–2733): start in the middle of a `count * 1000` (or `pageCount * 1000`) virtual range and map back with `((page % n) + n) % n`. The same technique is reused by the cover-screen tile pager. Only `pager.settledPage` pushes into `state.currentIndex` (one direction); an external change (widget/shortcut tap) must jump the pager the other way — the code note at line 2739–2748 flags this and (beyond this doc's range) handles it by snapping.
- **`FireworksOverlay` never loops** — once the single `Animatable t` reaches 1, all bursts are permanently done (line 1570–1582). Suitable for a one-shot celebration only.
- **Cover-screen thresholds were unified** into `COVER_SCREEN_HEIGHT_DP`/`_WIDTH_DP` (line 2461–2468) because GarageScreen and LockOverlay previously used different cutoffs (570 vs 440), so one physical device could get compact UI on one screen and full UI on the other. `isCompactCoverScreen` checks **both** width and height so a wide-short tablet-landscape doesn't false-positive as a cover screen.
- **`EmptyScreen` splits three previously-conflated causes** (not-signed-in vs load-failed vs no-vehicles, line 2323–2345) because a real network/API failure used to look identical to a silent sign-out.
- **Login preserves typed fields across brand switches** because email/password/pin are independent `mutableStateOf` and only `brand` drives the copy/validation shape (line 1645–1648, 1624–1638 comment). The PIN field visibility is `!brand.usesOtpLogin` (Kia hides it and uses the OTP dialog instead).
