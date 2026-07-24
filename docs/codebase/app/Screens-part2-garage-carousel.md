# Screens.kt — Part 2: Garage screen, car carousel/grid, cover-screen tiles, hero & pebble plumbing (lines ~2455–5210)

**File:** `C:\Users\AdiPerets\Bloo\app\src\main\java\com\bloo\bluelink\ui\Screens.kt`
**Range covered:** ~2455–5210 (focus 2700–5200). All identifiers below are `private` unless noted; this is a single-file UI module, so "public surface" here means "the composables/functions this cluster exposes to the rest of the file".

---

## 1. Purpose

This unit is the **main phone garage UI** of Bloo: the screen you see after unlock/onboarding that lets you swipe between your cars and drill into each car's status and controls. It contains three *fundamentally different* layouts, chosen by screen size, plus all the shared building blocks they compose from:

- **`GarageScreen`** (2543) — the dispatcher + the "normal phone" and "wide/tablet grid" layouts, including the car-switching pagers and the expand-one-car-to-fullscreen gesture.
- **`CompactGarage`/`CompactCar`/`CompactMainTile`** (2963/3092/3531) — the **folding-phone cover-screen** layout: horizontal swipe = cars, vertical swipe = section "tiles" (pebbles), with camera-cutout avoidance and an edge-trace refresh gesture.
- **`ExpandedCar`** (4692) — the wide dual-column detail view (critical info column + reorderable pebble column, with a pinnable "hot spot").
- **`VehicleDetailContent`** (4612) — the single-column per-car detail view (phones, and each column of the grid).
- **Shared chrome/building blocks:** `HeroHeader`/`HeroVisual`/`ChargeFuelBar` (hero card + battery/fuel readout), `UpdateAvailableTile` (self-update surface), `PagerDots`/`VerticalPagerDots` (page indicators with hold/scrub-to-refresh), `FloatingIcon`, `StatusBarScrim`, `ReorderColumn` (drag-to-reorder), `PebbleList`/`SinglePebble` (the pebble dispatcher), `PrimaryActions` (lock + horn/lights), `HotspotSlot`, `Refreshable` (pull-to-refresh wrapper), and misc helpers.

It exists because Bloo runs on candybar phones, tablets, **and** flip-phone cover screens, all with the same domain model but radically different interaction affordances; this file centralizes all three so they share `UiState`, `AppViewModel`, theming, and the pebble catalog.

---

## 2. Public surface (every composable/function/property in range)

### Sizing / layout helpers (2455–2510)

- **`const MIN_CARD_DP = 320`** (2457) — minimum comfortable width for one car column; `perPage` is `widthDp / MIN_CARD_DP`.
- **`const COVER_SCREEN_HEIGHT_DP = 570`, `const COVER_SCREEN_WIDTH_DP = 600`** (2467–2468) — thresholds. Below *both* → compact cover-screen layout; `>= COVER_SCREEN_WIDTH_DP` width → `large` (grid/expand). One shared cutoff (previously GarageScreen used 570 and LockOverlay 440, causing per-device inconsistency).
- **`@Composable isCompactCoverScreen(): Boolean`** (2473) — `screenWidthDp < 600 && screenHeightDp < 570`. Width checked separately so a wide-but-short tablet-in-landscape doesn't false-positive.
- **`@Composable coverScaled(base: Dp, refWidthDp: Float = 280f): Dp`** (2485) — scales a reference spacing by `(widthDp / refWidthDp)` clamped to `0.6f..1.4f`, so tiny cover screens don't lose proportionally more to fixed insets. Used pervasively in cover-screen paddings.
- **`enum CameraEdge { TOP, BOTTOM, LEFT, RIGHT }`** (2495) — which edge a display cutout is flush against.
- **`cameraEdgeOf(rect: android.graphics.Rect?, viewWidthPx: Int, viewHeightPx: Int): CameraEdge?`** (2501) — computes each edge's margin (`rect.top`, `viewHeight - rect.bottom`, `rect.left`, `viewWidth - rect.right`) and returns the edge with the **smallest** margin; `null` for a null rect. Pure function (not composable).

### `GarageScreen` (2543)

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable private fun GarageScreen(state: UiState, vm: AppViewModel)
```
Top-level dispatcher. Returns immediately if `state.vehicles` is empty (2545). Selects a layout:
- `compact` → delegates to `CompactGarage(state, vm, appearance)` and `return`s (2627–2630).
- otherwise builds the normal/large layout with an `AnimatedContent(expandedIdx != null)` (2644) crossfade/scale between the **expanded single-car pager** and the **collapsed multi-car-per-page grid pager**.
Renders floating overlays: a Back button (`ArrowBack`, when `expandedByUser != null`), a Flip-columns button (`SwapHoriz`, when `expandedIdx != null`), and always a Settings button. Registers `BackHandler(enabled = expandedByUser != null)` → `vm.collapse()` (2640).

### Cover-screen composables

- **`@Composable CompactGarage(state, vm, appearance: SettingsStore.Appearance)`** (2963) — owns the car-switching `HorizontalPager`. Renders one `CompactCar` per page and the hoisted car-switch `PagerDots`.
- **`@Composable CompactCar(v: Vehicle, state, vm, dotsAlpha: Float)`** (3092) — one car's page: a `VerticalPager` of pebble tiles, camera-cutout avoidance, edge-trace refresh gesture, and the right-rail `VerticalPagerDots` scrubber.
- **`@Composable CompactMainTile(v, state, vm)`** (3531) — the always-present "main" tile: faded car photo, name row (with Refresh + Settings `FloatingIcon`s), `LastUpdatedLabel`, `ChargeFuelBar`, `PrimaryActions`.
- **`@Composable VerticalPagerDots(current, count, tiles: List<String>, onPageJump: suspend (Int) -> Unit, modifier)`** (3373) — vertical page indicator that expands into a long-press scrubber (14 dp drag/page). Includes TalkBack `customActions`.

### Chrome / indicators

- **`@Composable StatusBarScrim()`** (3632) — blurred top gradient behind the status bar (non-cover layouts only).
- **`@Composable FloatingIcon(icon, description, onClick, modifier, outerPadding: Dp = 12.dp)`** (3654) — translucent 48 dp circular glass icon button, press-scale spring to 0.88f.
- **`@Composable PagerDots(current, count, modifier, onRefresh: (() -> Unit)? = null)`** (3700) — horizontal dots with optional hold-1s-to-refresh ring; `null` drops the whole gesture.

### Hero / readout

- **`@Composable HeroHeader(v, status: VehicleStatus?, imageUrl: String?, hasBattery, hasFuel, vm, drivingLabel: String? = null, dragHandle: Modifier = Modifier, height: Dp = 150.dp, metric: Boolean = false)`** (3826) — the hero card: `HeroVisual` + `ChargeFuelBar`. Corner radius animates 24↔40 dp on charging.
- **`@Composable UpdateAvailableTile(state, vm, dragHandle: Modifier = Modifier)`** (3897) — the GitHub-Releases self-update pebble.
- **`@Composable HeroVisual(v, imageUrl: String?, height)`** (3994) — brand gradient fallback or the user's photo (File for `/`-prefixed paths, URL otherwise; `.png` → Fit + no clip = transparent-friendly, else Crop + rounded).
- **`@Composable ChargeFuelBar(status: VehicleStatus?, hasBattery, hasFuel, drivingLabel: String? = null, metric: Boolean = false)`** (4036) — percent + range headline, status line, gradient fill bar, target-SOC marker dot.

### Misc reused helpers

- **`val ChargeGreen`/`ChargeGreenDark`** (4164–4165) — `Color(BlooColors.chargeGreen/…Dark)`.
- **`val SoftDamping get() = com.bloo.uicommon.SoftDamping`** (4167).
- **`const AdvancedModeStiffness = 130f`** (4175).
- **`@Composable RollingNumber(text, style, fontWeight, color = Color.Unspecified)`** (4222) — number that slides up when growing / down when shrinking.
- **`@Composable rememberRelativeTime(millis: Long?): String?`** (4246) — self-ticking (30 s) "x min ago".
- **`@Composable LastUpdatedLabel(v, state, modifier = Modifier)`** (4264) — "Updated x ago" caption.
- **`Modifier.animatePlacement()`** (4289) — glides an item to its new placement when siblings reorder.
- **`@Composable <T> ReorderColumn(items, keyOf, onReorder, modifier, spacing = 12.dp, onDragMove?, onDragRelease?, staggerInOnColdStart = false, introKey = Unit, content)`** (4329) — drag-to-reorder column.
- **`@Composable AnimatedSlider(...)`** (4483), **`@Composable WiggleText(...)`** (4517), **`snapToStep(...)`** (4531) — thin wrappers over `com.bloo.uicommon` equivalents.
- **`Modifier.fadingEdges(scroll: ScrollState, length = 28.dp)`** (4539) — soft top/bottom fade of a scroll area.
- **`class HotSeatDrag`** (4577) + **`val LocalHotSeatDrag`** (4588) — cross-column pebble-drag state.
- **`@Composable BackdropHost(content)`** (4594) — trivial full-size Box host.

### Detail views & pebble plumbing

- **`@Composable VehicleDetailContent(v, state, vm, onExpand: (() -> Unit)? = null, reserveHeaderEnd = false, onNameHiddenChanged: ((Boolean, suspend () -> Unit) -> Unit)? = null, hideIndicator = false)`** (4612) — single-column per-car view.
- **`@Composable ExpandedCar(v, state, vm, flipped: Boolean)`** (4692) — wide dual-column view.
- **`sectionLabel(section: String): String`** (4793) — friendly label for a pebble id.
- **`@Composable HotspotSlot(v, hotspot: String?, state, vm)`** (4811) — the pinnable slot.
- **`val RefreshPullShift = 96.dp`** (4898).
- **`@Composable Refreshable(v, state, vm, hideIndicator = false, content)`** (4912) — pull-to-refresh wrapper.
- **`@Composable CarHeaderRow(v, state, onExpand: (() -> Unit)?, reserveEnd)`** (4968).
- **`@Composable CriticalContent(v, state, vm)`** (4996).
- **`@Composable ControlsPebble(v, state, vm, dragHandle)`** (5024).
- **`@Composable PebbleList(v, state, vm, exclude: Set<String> = emptySet())`** (5058).
- **`@Composable SinglePebble(section: String, v, state, vm, dragHandle)`** (5102) — the section→pebble dispatcher.
- **`@Composable AiPebble(v, state, vm, dragHandle)`** (5138).
- **`@Composable PrimaryActions(v, state, vm, contentPadding = PaddingValues(start = 26.dp, end = 8.dp))`** (5178).
- **`@Composable PaletteSwatch(palette, selected, onClick)`** (5219, extends slightly past 5200 — settings palette picker).

### CompositionLocals defined here (4181–4214)

- **`LocalForceExpanded: Boolean`** (`staticCompositionLocalOf { false }`) — cover-screen tiles render pebbles permanently open.
- **`LocalPebbleFillHeight: Boolean`** (`staticCompositionLocalOf { false }`) — pebble stretches to fill height + scrolls internally.
- **`LocalCoverScrollState: ScrollState?`** (`compositionLocalOf { null }`) — parent-supplied scroll state for a fill-height pebble.
- **`LocalCoverScrubbing: MutableState<Boolean>?`** (`staticCompositionLocalOf { null }`) — scrub-active flag shared to `CompactGarage`.
- **`LocalPullFraction: MutableState<Float>`** (`staticCompositionLocalOf { mutableStateOf(0f) }`) — live pull distance published by `Refreshable`.
- **`val CompactKnownTiles = setOf("climate","charge","location","weather","trips","info","diagnostics","ai")`** (4190) — tiles `CompactCar` can render.

---

## 3. Internal structure & control flow

### 3.1 `GarageScreen` layout selection (2543–2946)

1. **Stale-data watcher** (2551): `LaunchedEffect(currentVehicle?.vin, currentFetchedAt)` — if the current car's `fetchedAt` predates the session start *and* is older than `STALE_STATUS_MS` (15 min), it `delay(25_000)` then `vm.reportError("Data is over 15 min old...")`. The delay is deliberately cancellable: a fresh background fetch changes `currentFetchedAt`, restarts the effect, cancels the delay, and the toast never fires.
2. **Refresh-settle haptic** (2573): `wasRefreshing` tracks the edge; `haptics?.slotSettle()` fires when `refreshing` falls false.
3. **Pull-driven overlay motion** (2580–2600): `pullFractionState` (a local `MutableState<Float>`, provided down via `LocalPullFraction` at 2642) drives `dotsAlpha` (fade dots out during a pull/refresh) and `refreshShift` (slide overlays down by up to `RefreshPullShift = 96.dp`).
4. **Layout decision** (2602–2638):
   - `count = vehicles.size`, `widthDp`, `large = widthDp >= 600`, `compact = isCompactCoverScreen()`.
   - Cover-screen hint: `LaunchedEffect(compact, hasCameraCutout)` shows a one-per-session "Open your phone…" toast when a cutout is detected.
   - `if (compact) { CompactGarage(...); return }`.
   - `perPage = (widthDp / MIN_CARD_DP).coerceIn(1, count)`; `canExpand = large && count > 1`; `singleLarge = large && count == 1`.
   - `expandedByUser = state.expandedIndex?.takeIf { it in indices && canExpand }`; `expandedIdx = if (singleLarge) 0 else expandedByUser`.
5. **`AnimatedContent(expandedIdx != null)`** (2644): fade+scale (0.94) crossfade between expanded and collapsed layouts.

**Expanded branch** (2653–2720): a single-car `HorizontalPager` (`exPager`). Infinite wrap: `exVirtualCount = count*1000` (if `count>1`), start at `virtual/2 + expandedIdx`. `exReal(page) = ((page % count) + count) % count`. `snapshotFlow { exPager.settledPage }` → `vm.expand(exReal(it))`. Per-page transform (alpha/scaleX/scaleY) is computed **inside `graphicsLayer{}`** (draw phase only) from `(page - currentPage) + currentPageOffsetFraction` — reading it as a plain val used to recompose the whole page (CarThemeOverride + VehicleDetailContent + every pebble) each drag frame (the documented jank cause). Each page wraps `ExpandedCar` in `CarThemeOverride`. `StatusBarScrim()` + `PagerDots` on top.

**Collapsed branch** (2721–2917): multi-car-per-page grid pager.
- `pageCount = (count + perPage - 1) / perPage`; `loopMulti = pageCount > 1`; `virtualPageCount = pageCount*1000`.
- `initialBlock = currentIndex.coerceIn(0, count-1) / perPage`; pager starts at `virtual/2 + initialBlock`.
- `realBlock(vp) = ((vp % pageCount) + pageCount) % pageCount`.
- **Two `LaunchedEffect`s** (2734, 2749) — the crux of bidirectional sync:
  - `snapshotFlow { pager.settledPage }` → `vm.selectIndex((realBlock(page) * perPage).coerceIn(...))` (pager → state).
  - `LaunchedEffect(state.currentIndex)` → if the selected car's block differs from the displayed block, `pager.scrollToPage(currentPage + delta)` (state → pager, non-animated jump). This handles a widget/shortcut tap selecting a car while the pager sat on another block.
- Per-page `Row` of `perPage` `VehicleDetailContent`s (each weighted, theme-overridden), trailing `Spacer`s pad a partial final page. Same draw-phase-only alpha/scale transform.
- `onNameHiddenChanged` is wired only when `perPage == 1` — it hoists the floating car-name pill up to the parent Box (see §3.5). `hideIndicator = perPage > 1` (grid hides per-card spinners; a single shared `LoadingIndicator` is shown instead at 2831).
- Hoisted **car-name pill** (2842–2915) for `perPage == 1`: an `AnimatedVisibility` slide-in glass pill; corner radius is a **fixed `RoundedCornerShape(24.dp)`**, *not* a percent shape — see Gotchas.

### 3.2 `CompactGarage` (2963)

Empty guard: `if (count == 0) { EmptyScreen(vm); return }` — because `coerceIn(0, count-1)` with `count==0` throws (min>max) before the pager can gracefully handle emptiness (2971).
- Infinite-wrap car pager (`virtualCarCount = count*1000`), `realCar(vp) = ((vp % count)+count)%count`.
- Same two-way sync `LaunchedEffect`s as the grid (settledPage→selectIndex at 2984, currentIndex→scrollToPage at 2992).
- `scrubbing = remember { mutableStateOf(false) }` — provided down as `LocalCoverScrubbing` (3040) so the vertical scrubber can lock car-switch swipes: `HorizontalPager(userScrollEnabled = !scrubbing.value)`.
- `dotsAlpha` shared by both dot rows; fades to 0 during refresh.
- Per-page draw-phase graphicsLayer fade/scale (`pageOff` via `derivedStateOf`, 3017), wraps `CompactCar` in `CarThemeOverride` + `CompositionLocalProvider(LocalCoverScrubbing provides scrubbing)`.
- Car-switch `PagerDots` hoisted here (sibling of the pager, outside per-page transform) with `onRefresh = null` (cover screen refreshes via the edge-trace gesture, not the dots).

### 3.3 `CompactCar` (3092) — the densest function

Three overlapping concerns in one `Box`:

**(a) Tile list construction** (3101–3112): `state.sectionsFor(v)` mapped: `"summary" → "main"`; otherwise the section is kept only if it's in `CompactKnownTiles`, passes gates (`charge`⇒`hasBattery`, `ai`⇒`aiEnabled`, `trips`⇒`!isGen5W`), and is not `isPebbleHidden`. Finally, if `"main"` isn't present it's prepended (`listOf("main") + ordered`) so there's always a home tile. `isGen5W` (3094) = non-Kia with generation `< 3`.

**(b) Vertical tile pager** (3113–3292): infinite-wrap `vPager` (`virtualCount = tiles.size*1000`), `current = ((currentPage % size)+size)%size`. `tileScrollStates = remember { mutableMapOf<String, ScrollState>() }` keyed by **tile name** (survives paging recycle and reordering). `userScrollEnabled = coverScrubbing?.value != true`. Each page provides `LocalForceExpanded=true`, `LocalPebbleFillHeight=true`, `LocalCoverScrollState=tileScroll`, then applies `navigationBarsPadding()` + per-edge padding where the camera-edge side is grown via `maxOf(base, cameraClearance)`. `when (tile)` dispatches to `CompactMainTile` or the various `*Pebble` composables.

**(c) Camera cutout** (3129–3163): reads `view.rootWindowInsets.displayCutout.boundingRects.firstOrNull()` (API 28+), `cameraEdge = cameraEdgeOf(...)`, `cameraClearance` = pixels from the edge past the cutout + 12 dp, per-edge. `ringColor` = `onSurface @ 0.18`. A `Canvas` (3296) draws two decorative stroke circles around the hole (only if `cameraHole != null`).

**(d) Edge-trace refresh** (3165–3234): `edgeTraceProgress: Animatable(0f)`, `edgeTraceHolding: Boolean`, `dotsBounds: Rect?`. `LaunchedEffect(edgeTraceHolding)`: on hold, `snapTo(0f)` then `animateTo(1f, tween(1200))`; if still holding at completion → `vm.refreshStatus(v)`, then `edgeTraceHolding=false`. On release before completion → ease progress back to 0 over `tween(200)`. The gesture `pointerInput` lives on the **outer parent Box** (3213) so `VerticalPager`'s child drag recognizer gets first claim (leaf-to-root Main-pass dispatch); `awaitEachGesture` starts timing on down, bails if `dotsBounds.contains(down.position)` (so scrubbing the dots doesn't also arm refresh), and breaks the hold if the change is unpressed/consumed or exceeds `touchSlop`. The `edgeTraceProgress` arc is drawn by a separate sibling Canvas (3321) starting at -90° clockwise.
- `VerticalPagerDots` (3347) at CenterEnd publishes its bounds via `onGloballyPositioned { dotsBounds = it.boundsInParent() }` and `onPageJump` maps a target tile index to a `vPager.scrollToPage(currentPage + delta)`.

### 3.4 `VerticalPagerDots` (3373) — long-press scrubber

`scrubbing`, `scrubStartPage`, `scrubAccumY` state. `pxPerPage = 14.dp`. `scrubTargetPage = (scrubStartPage + round(scrubAccumY/pxPerPage)).coerceIn(0, count-1)` via `derivedStateOf`. `LaunchedEffect(scrubTargetPage, scrubbing)` → on each change while scrubbing: `haptics?.tick()` + `onPageJump(scrubTargetPage)` (the first firing doubles as a "scrub entered" tick). Gesture: `awaitLongPressOrCancellation` → set `scrubbing=true`, `coverScrubbing?.value=true`, then `verticalDrag` accumulates `(change.position - previousPosition).y`; the `finally` always clears both flags (never leaves the parent's car-switch disabled). The invisible gesture Box is `widthIn(min = 48.dp)` (touch target) while the visible Surface stays narrow. Semantics: `contentDescription` announces the showing tile + `customActions` "Go to X" per non-current tile (reuses the same `onPageJump`). Animated padding/corner/alpha morph between resting pill (100 dp corner, 0.7 alpha) and scrub mode (20 dp corner, 0.92 alpha), with per-dot `dotH` (28↔7) / `dotW` (10↔7) / color (primary/secondary/outlineVariant) animations.

### 3.5 Floating name pills (three variants)

There are **three** "which car am I looking at" pills, all glass:
1. Hoisted in `GarageScreen` for single-car-per-page (2842) — fixed 24 dp corner, has `AnimatedContent` for name + "n / total".
2. Inline in `VehicleDetailContent` (4652) when `onNameHiddenChanged == null` — `RoundedCornerShape(50)`, tap scrolls to top.
3. Inline in `ExpandedCar` (4769) at TopEnd — `RoundedCornerShape(50)`, watches `controlsScroll`.
All three appear when the car name/header scrolls out of view (`nameHidden` derivedStateOf over scroll position).

### 3.6 `ReorderColumn` (4329) — drag reorder

Local state: `order` (re-synced from `items` only while `draggingKey == null`, 4364), `draggingKey`, `offsetY`, `heights: mutableStateMapOf<Any,Int>`, `dropRipple`. `playIntro = remember(introKey) { staggerInOnColdStart && coldStartIntroPlayed.add(introKey) }` — consumed once per introKey (VIN) so navigating back never replays the stagger. Each item is wrapped in `key(k)`; the dragged item is excluded from `animatePlacement()` and manually translated by `offsetY` (draw-phase graphicsLayer). `detectDragGesturesAfterLongPress`: `onDrag` adds `dragAmount.y` to `offsetY`, reports `onDragMove(k, localToWindow(position))`, and swaps with the neighbor once `offsetY` passes half the neighbor's measured height (`heights[...]`), subtracting/adding that height so the dragged item stays visually continuous. `onDragEnd` calls `onDragRelease?.invoke(k)`; if not handled, `onReorder(order)`. Additive TalkBack `customActions` "Move up"/"Move down" reuse the same reorder+commit path (4421).

### 3.7 `PebbleList` / `SinglePebble` (5058 / 5102)

`PebbleList` filters `state.sectionsFor(v)` (drops excluded, hidden, `ai` unless `aiEnabled`, `update` unless `updateAvailable != null`), feeds `ReorderColumn`. `onReorder` (5070) **merges** the reordered *visible* subset back into the full section order (`allSections + DEFAULT_SECTIONS distinct`, dequeuing visible items in new order) so excluded/pinned/hidden sections keep their slots, then `vm.setSectionOrder(v, merged)`. `onDragMove`/`onDragRelease` (5083) wire the hot-seat pin: release-over-slot → `vm.setHotspot(v, key)`. `staggerInOnColdStart=true`, `introKey=v.vin`.

`SinglePebble` (5102) is the `when(section)` dispatcher → `HeroHeader`(summary) / `UpdateAvailableTile`(update) / `ControlsPebble` / `ClimatePebble` / `ChargePebble` or `FuelPebble` (charge, branch on `hasBattery`) / `LocationPebble` / `WeatherPebble` / `TripsPebble` / `InfoPebble` / `DiagnosticsPebble` / `AiPebble`; unknown → `Spacer`.

---

## 4. Data & types defined here

- **`enum CameraEdge { TOP, BOTTOM, LEFT, RIGHT }`** (2495) — screen edge a cutout is flush to. No wire encoding; internal only.
- **`class HotSeatDrag`** (4577):
  - `var section: String?` — the section id currently being dragged (null = not dragging).
  - `var pointer: Offset` — live finger position in **window** coords.
  - `var slotTopLeft: Offset`, `var slotSize: IntSize` — the hot-spot slot's window bounds, published by its `onGloballyPositioned`.
  - `val overSlot: Boolean` (computed) — true iff a section is being dragged, the slot has width, and `pointer` is inside `[slotTopLeft, slotTopLeft+slotSize]`.
- **Color/spacing constants:** `ChargeGreen`/`ChargeGreenDark` (from `BlooColors`), `SoftDamping`, `AdvancedModeStiffness=130f`, `MIN_CARD_DP=320`, `COVER_SCREEN_HEIGHT_DP=570`, `COVER_SCREEN_WIDTH_DP=600`, `RefreshPullShift=96.dp`, `CompactKnownTiles` set.

No data class / sealed hierarchy with wire serialization is defined *in this range*; the domain types (`Vehicle`, `VehicleStatus`, `UiState`) are consumed, not declared here.

### Encoding facts this code depends on (consumed from the domain)

- **`ChargeFuelBar` chargeType** (4045): `status.evStatus.batteryPlugin` → `1 → "DC"`, `2 → "AC"`, else null. (Matches the domain scheme: batteryPlugin 0=unplugged, 1=DC, 2=AC — note this is the *different* scheme from `plugType`.)
- **`status.percentFor(hasBattery)` / `rangeMiFor(hasBattery)`** (4038/4040) — use the user's manual powertrain override, not raw `isEv`.
- **`status.evStatus.targetForCurrentPlug()`** (4132) — the AC/DC charge-limit percent for the current plug; drives the target-SOC marker dot, only shown when non-null (plugged in).
- **`status.evStatus.remainTime2.atc.value`** (4044) — remaining charge minutes (`> 0` filter).
- **`v.supportsHornLights`** (5203) — Kia US has no equivalent endpoint, so Flash-lights/Horn-and-lights `groupActions` render only for Hyundai/Genesis.

---

## 5. State & concurrency

- **No StateFlow/DataStore is created here** beyond `vm.appearance.collectAsState()` (read in several places). All persistent state lives in `AppViewModel` (`UiState`, `SettingsStore.Appearance`); this file reads it and calls `vm.*` mutators.
- **Pager state** is `rememberPagerState`; the middle-of-virtual-range trick means `pager.currentPage` is a large int mapped by modulo — treat the raw page as opaque, always go through `realX()`.
- **Two-way pager⇄state sync** uses `snapshotFlow { pager.settledPage }.collect { vm.selectIndex(...) }` (pager→VM) and `LaunchedEffect(state.currentIndex)` doing `scrollToPage` (VM→pager). Both run in the composition's coroutine scope. `scrollToPage` (not `animateScrollTo`) is used for the VM→pager jump to avoid an animated fly-through across a huge virtual-page delta.
- **`coldStartIntroPlayed`** (line 352, module-level `mutableSetOf<Any>()`) — process-global set gating the one-time entrance stagger; `.add(introKey)` returns false on the second read. Not thread-safe, but only touched on the main/composition thread.
- **Draw-phase reads:** pager offset transforms and pull-indicator offset are computed **inside `graphicsLayer{}`/`offset{}` lambdas** so they run in draw/layout only, never recomposing content — this is the single most-repeated performance pattern in the unit (documented at 2673, 3023, 3549, 4940).
- **`Animatable` coroutines:** `edgeTraceProgress`, `PagerDots.expandProgress`, hero/main-tile entrance animators, `ReorderColumn.maxRippleScale`/`lift`/`intro` — all driven inside `LaunchedEffect`s; cancellation on key change is relied upon (e.g. the stale-data delay, the edge-trace release path).
- **Gesture flags with `finally` cleanup:** `VerticalPagerDots` and the edge-trace handler both clear their active flags in `finally` so a cancelled gesture never leaves car-switching disabled or a ring frozen.

---

## 6. Collaborators & data flow

**Reads (in):**
- `UiState`: `vehicles`, `currentIndex`, `expandedIndex`, `refreshing`, `loading`, `updateAvailable`, `updateDownloading`/`updateDownloadProgress`/`updateApkReady`/`updateTileDismissed`, `aiEnabled`/`aiBusy`/`aiSummaries`, `imageUrls`, `showSettingsHint`; and helper methods `statusFor(v)`, `fetchedAt(v)`, `hasBattery(v)`, `hasFuel(v)`, `drivingLabel(v)`, `powertrainLabel(v)`, `sectionsFor(v)`, `seatConfigFor(v)`, `hotspotFor(vin)`, `isPebbleHidden(vin, sec)`, `isPending(vin, cmd)`.
- `SettingsStore.Appearance` (via `vm.appearance`): `carCustomPaletteIds`, `customPalettes`, `themeMode`, `vibrancy`, `columnsFlipped`, `unitSystem`, `pebbleOutline`.
- Android: `LocalView.rootWindowInsets.displayCutout` (camera hole), `WindowInsets.statusBars/navigationBars`, `LocalConfiguration`, `LocalDensity`, `Build.VERSION`.

**Writes / calls (out) to `vm`:** `selectIndex`, `expand`, `collapse`, `refreshStatus(v)`, `setColumnsFlipped`, `openSettings`, `setSectionOrder(v, list)`, `setHotspot(v, sec?)`, `lock/unlock(v)`, `flashLights(v)`, `hornAndLights(v)`, `summarizeCar(v)`, `installDownloadedUpdate`, `downloadUpdateInBackground`, `dismissUpdate`, `snoozeUpdate`, `reportInfo/reportError`, `dismissSettingsHint`.
- `UpdateAvailableTile` also starts an `Intent(ACTION_VIEW, info.run.htmlUrl)` when no direct APK URL is available.

**Data-layer / self-update channel:** `UpdateAvailableTile` surfaces GitHub-Releases build metadata (`info.run.phoneApkUrl`, `runNumber`, `displayTitle`, `htmlUrl`, `releaseNotes`) — the app self-updates by downloading the phone APK asset directly (no Play Store).

**CompositionLocals (intra-file channels):** `LocalPullFraction` (Refreshable→GarageScreen overlays), `LocalCoverScrubbing` (scrubber→CompactGarage), `LocalForceExpanded`/`LocalPebbleFillHeight`/`LocalCoverScrollState` (CompactCar→pebbles), `LocalHotSeatDrag` (ExpandedCar→PebbleList/HotspotSlot), plus `LocalHaptics`/`LocalContentColor`/`LocalReduceMotion` consumed.

**Called by:** the top-level screen switch elsewhere in Screens.kt routes to `GarageScreen` (post-unlock). `CarThemeOverride`, the various `*Pebble` composables, `StateControl`, `Pebble`/`PebbleShell`, `MorphButton`/`MorphTextButton`, `RollingNumber`, and glass modifiers (`ambientRing`/`dropShadow`/`frostedRim`, `glassContainerAlpha`) live elsewhere in the file / `uicommon`.

---

## 7. Invariants & assumptions

- **`count > 0` for the pager branches.** `GarageScreen` returns early on empty (2545); `CompactGarage` guards `count == 0` → `EmptyScreen` (2971) precisely because `coerceIn(0, count-1)` throws when `count==0`.
- **Modulo mapping is the only valid page→index conversion.** Raw `pager.currentPage` is a huge virtual number; always use `exReal`/`realBlock`/`realCar`/`current`.
- **`realBlock` uses `pageCount`, `realCar`/tile `current` use `count`/`tiles.size`.** Mixing the divisor breaks wrap-around.
- **`tiles` always contains `"main"`** (guaranteed by the prepend at 3112) — the `when(tile)` has no fallback for a missing home tile.
- **`tileScrollStates` and `heights` are keyed by name/key, not index** — required so scroll offset and reorder swap heights survive paging recycle and reordering.
- **Edge-trace correctness depends on leaf-to-root dispatch:** the gesture must be on the *parent* of `VerticalPager`, relying on Compose's Main-pass leaf-first ordering so the pager claims real drags first (documented at 3201). A sibling Box would be ambiguous.
- **`onReorder` merge assumes `newVisible` is a permutation of `sections`** and that `sections ⊆ full` — visible items are dequeued in order back into their positions.
- **`ExpandedCar` scroll states are hoisted** (`controlsScroll`/`pebblesScroll`) so a scroll position follows its *content* across a column flip, not the physical column.
- **Camera coords assume display-pixel space** matching the edge-to-edge Canvas coordinate space (`boundingRects` are screen pixels, aligned with the fillMaxSize Canvas).
- **`StateControl` needs a content color set.** `CriticalContent` explicitly provides `LocalContentColor = onSurface` (5012) because the dual-column controls column isn't itself Surfaced and Compose's default is opaque black (invisible on the dark theme).

---

## 8. Gotchas & sharp edges

- **Single-car pill uses a fixed 24 dp corner, not `RoundedCornerShape(50)`** (2862): a percent shape would make `dropShadow`/`ambientRing`'s *cached* outline (rebuilt only when its `size` read changes) chase the pill's width mid-`animateContentSize`, showing a "square shadow that snaps right after a second". 24 dp = half the pill's fixed 48 dp height gives the identical resting shape without the corner depending on an animating width.
- **`hideIndicator = perPage > 1` only.** `state.refreshing` is one app-wide flag, not per-car; leaving indicators unhidden in the grid would light every visible card's spinner for a one-car refresh. But `pageCount == 1` (all cars fit one page, common on tablets) means `PagerDots` never renders either, so a shared `LoadingIndicator` (2831) is added for that gap. A prior fix meant for the grid once leaked into the single-car view and silently killed its refresh feedback (2798 comment).
- **`ExpandedCar` no longer hardcodes `hideIndicator = true`** (4723) — that grid-only flag once hid the pull spinner in the dual-column detail view too; it's a single car's own screen, so the real indicator shows.
- **No blur / no rotationZ on any car pager** (2690, 2772, 3023): `Modifier.blur(x.dp)` reconstructs its modifier node every drag frame (jitter); a secondary "snap-bounce" spring was also removed because it visibly lagged the transform behind the drag. Only cheap draw-phase fade/scale remain, and all three pagers were made consistent.
- **`Refreshable`'s indicator offset lives in `offset{}`** (4940) — the whole `distanceFraction` calc used to be a composition-phase read, recomposing the entire car card on every pull pixel; moving it to the layout-phase lambda made a live drag cost one indicator relayout per frame instead.
- **`PagerDots(onRefresh = null)` drops the *whole* gesture and fill-ring**, not just the action (3691 doc) — otherwise a stray tap-through while swiping started the ring filling for a frame, reading as a spurious refresh flicker. The cover screen passes `null` because its edge-trace gesture already owns refresh.
- **Both hold-to-refresh rings ease back on early release** (`PagerDots` at 3722, edge-trace at 3190) — a `LaunchedEffect(holding)` cancels the fill coroutine when the flag flips false, which used to leave the ring frozen at its partial fill.
- **`ChargeGreen`/`ChargeGreenDark` now delegate to `BlooColors`** (4161 comment) — they were a phone-only re-declaration that could silently diverge from the shared hex (as `chargerLabel`'s text once had).
- **`rememberRelativeTime` re-derives buckets that `relativeLabel()` already owns** and had drifted ("d ago" vs "day ago", 4249) — `now` only exists to force a 30 s recompute; `relativeLabel` reads the wall clock itself.
- **`ControlsPebble` and `HeroHeader` roll their own Surface/Card** (5024, 3855) instead of going through `Pebble()`, so they had each been missed by the shared shadow/rim + `pebbleOutline`-gated border treatment and had to re-implement it manually.
- **The `AiPebble` uses `tertiaryContainer`** (5144) — the one deliberate visually-distinct pebble; `UpdateAvailableTile` was corrected *away* from `primaryContainer` back to the default `surfaceVariant` so it fits the stack (3919 comment).
- **`UpdateAvailableTile` expand state is `rememberSaveable(info.run.runNumber)`** (3910) so a genuinely different build starts collapsed rather than inheriting the prior build's state.
- **`CompactCar` `dotsBounds` carve-out** (3220): without excluding the scrubber-dots' hit area, holding the dots to scrub also armed the edge-trace ring underneath (edge-trace starts timing on raw down regardless of what the touch lands on).
- **`VerticalPagerDots` / `PagerDots` / `PaletteSwatch` / `ReorderColumn` all add explicit TalkBack semantics** because their primary interaction is a raw pointer gesture (long-press, drag, scrub) that touch-exploration intercepts — the `customActions`/`onLongClick` reuse the *same* commit path as the gesture, not a parallel one.
- **Cover-screen `main` tile photo alpha is 0.5 with a top/bottom gradient scrim** (3568), raised from 0.22 which read as barely-there on the already-dark `surfaceContainer`.
- **`coverScaled` clamps to 0.6f..1.4f** — spacing nudges, never a dramatic re-layout at extreme widths.
