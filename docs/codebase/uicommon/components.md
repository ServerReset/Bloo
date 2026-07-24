# uicommon — Shared Compose Components

**Module:** `:uicommon`
**Package:** `com.bloo.uicommon`
**Files covered:**
- `AnimatedSlider.kt`
- `MorphSegmented.kt`
- `WiggleText.kt`
- `DropShadow.kt`
- `TempColor.kt`
- `WeatherUtils.kt`
- `BlooColors.kt`
- `BlooMotion.kt`

---

## 1. Purpose

`:uicommon` is the **platform-neutral Compose component library** shared between the phone app (`:app`) and the Wear OS app (`:wear`). Its reason for existing is stated in nearly every file header: the custom controls, color math, motion constants, and utility mappings must live in **exactly one place** so the phone and watch render and behave identically, and so bugs/tuning happen once.

The critical design constraint that shapes the entire module: **it must stay neutral to `compose.material3` (phone) vs `wear.compose.material3` (watch)**. These are two different Material libraries with incompatible types. Every component here therefore takes all color / typography / haptic / motion context as **explicit parameters** rather than reading a `MaterialTheme`, `LocalContentColor`, or a haptic feedback provider. Callers on each platform wire in their own theme values.

The module contains two flavors of code:
1. **Custom hand-drawn interactive controls** — `AnimatedSlider` and `MorphSegmented`. These re-implement slider and segmented-button behavior from raw pointer input + `Canvas`/`graphicsLayer` because the Material versions can't be shared across the two Material libraries and don't give the exact bespoke look/feel Bloo wants.
2. **Pure utility functions/constants** — text animation (`WiggleText`/`AnimatedValue`), a real drop-shadow modifier (`dropShadow`), temperature→color mapping (`tempColor`), WMO weather-code→icon/tint mapping (`weatherIcon`/`weatherTint`), color derivation helpers (`BlooColors` object), and a shared spring damping constant (`SoftDamping`).

---

## 2. Public surface

### AnimatedSlider.kt

#### `fun AnimatedSlider(...)` — `AnimatedSlider.kt:53-339`
```kotlin
@Composable
fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    inactiveColor: Color,
    dotOnActive: Color,
    dotOnInactive: Color,
    reduceMotion: Boolean,
    onStepTick: () -> Unit,
    onSettle: () -> Unit,
)
```
Bloo's fully custom hand-drawn slider (track + thumb + step dots), shared phone/watch. Renders a full-width `Box` of fixed height `thumbH` (44.dp) containing a `Canvas` that draws four things every frame from a single `frac` derived from an internal `Animatable`: an inactive (remaining) track segment, an active (traveled) track segment, optional step dots, and a tall pill-shaped thumb.

Parameters:
- `value` — the logical (caller-owned, stepped) current value; used for semantics and to re-sync the visual `Animatable` when idle.
- `onValueChange(Float)` — called with the clamped value continuously during a drag (free-flow) and once with the final target on settle/tap.
- `valueRange` — the closed float range the slider spans.
- `steps` — number of *intermediate* stops (see `snapToStep` semantics: `steps + 1` increments; `0` = no quantization).
- `accent` — color of the active track segment **and** the thumb.
- `inactiveColor` — color of the remaining (right-of-thumb) track.
- `dotOnActive` / `dotOnInactive` — step-dot tint for dots left-of-thumb (already passed) vs right-of-thumb (still ahead).
- `reduceMotion` — when true, the settle spring is replaced by an instant `snapTo` (no bounce) and the motion-blur still applies but the thumb jumps.
- `onStepTick()` — fired each time a drag crosses a step boundary (per-notch haptic).
- `onSettle()` — fired once when a drag is released / a tap commits.

#### `fun snapToStep(...)` — `AnimatedSlider.kt:351-356`
```kotlin
fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float
```
Top-level (public) quantizer. Rounds `v` to the nearest of `steps` evenly-spaced increments across `range`. With `steps` intermediate stops, the range is divided into `steps + 1` equal increments (1 step → 2 valid positions: start & end). `steps <= 0` returns a plain clamp to the range. Algorithm: `range.start + round((v - range.start) / inc) * inc`, then clamp. This is the same "round to nearest multiple" technique used by `MorphSegmented.indexFor`.

### MorphSegmented.kt

#### `data class SegmentOption` — `MorphSegmented.kt:63`
```kotlin
data class SegmentOption(val key: String, val label: String, val icon: ImageVector? = null)
```
One option in a `MorphSegmented`. `icon` is optional and defaults null (the watch build typically omits icons).

#### `fun MorphSegmented(...)` — `MorphSegmented.kt:84-412`
```kotlin
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    containerColor: Color,
    indicatorColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    textStyle: TextStyle,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = if (options.any { it.icon != null }) 48.dp else 44.dp,
    borderColor: Color? = null,
)
```
Bloo's full-width segmented selector, shared phone/watch. Renders a tonal rounded track (`RoundedCornerShape(16.dp)`, filled `containerColor`, optional hairline `borderColor` rim) containing a sliding highlight pill (`indicatorColor`, `RoundedCornerShape(14.dp)`) beneath a `Row` of equal-width segment labels/icons. You can **drag** the highlight and it springs to wherever you release, or **tap** a segment to jump. The active segment's text is bold/tinted `selectedTextColor`; the rest render `unselectedTextColor` at 88% font size and Normal weight.

Parameters:
- `options` — segments in display order; each becomes one equal-width pill.
- `selectedKey` — currently committed selection, matched against `SegmentOption.key`; drives resting highlight position.
- `onSelect(String)` — called with the newly chosen key on tap or drag-release.
- `containerColor` — track background.
- `indicatorColor` — sliding-highlight fill.
- `selectedTextColor` / `unselectedTextColor` — foreground for active vs inactive text/icon (animated between via `animateColorAsState`).
- `textStyle` — base text style; color and weight are overridden per-segment.
- `onTick()` — haptic callback, fired each time the selection actually changes.
- `modifier` — outer modifier (applied to the track `Box`).
- `trackHeight` — track height; defaults 48.dp if any option has an icon, else 44.dp.
- `borderColor` — hairline rim color, or null for borderless (default; keeps un-updated callers visually unchanged).

### WiggleText.kt

#### `fun WiggleText(...)` — `WiggleText.kt:35-88`
```kotlin
@Composable
fun WiggleText(text: String, style: TextStyle, maxLines: Int = 1, reduceMotion: Boolean = false)
```
Renders `text` as a `BasicText` normally — **except** when the displayed number is exactly **67**, in which case each character becomes its own `BasicText` inside a `Row` and bounces up/down in a travelling sine wave (an easter egg). Callers must pre-resolve `Color.Unspecified` and merge `fontWeight` into `style` (this function passes `style` straight through, and `BasicText` does not consult `LocalContentColor`). `reduceMotion=true` forces the plain non-wiggling path.

#### `fun AnimatedValue(...)` — `WiggleText.kt:94-116`
```kotlin
@Composable
fun AnimatedValue(value: String, style: TextStyle, maxLines: Int = 1, reduceMotion: Boolean = false, modifier: Modifier = Modifier)
```
Wraps `WiggleText` in an `AnimatedContent` so value changes animate with a fade + vertical slide. The "67 bounce" continues to work inside the transition because the content lambda is `WiggleText`. Under `reduceMotion` the transition collapses to a 1ms cross-fade. `modifier` is forwarded to `AnimatedContent` so callers can size it (e.g. `Modifier.weight` in a label/value row).

### DropShadow.kt

#### `fun Modifier.dropShadow(...)` — `DropShadow.kt:37-67`
```kotlin
fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.38f),
    blurRadius: Dp = 14.dp,
    offsetY: Dp = 5.dp,
    offsetX: Dp = 0.dp,
): Modifier
```
A **real** drop shadow — an offset, blurred, dark silhouette of `shape` drawn behind the composable via a native `BlurMaskFilter` — as opposed to Material3 `shadowElevation` (which reads as barely-there). Pure Compose graphics APIs, no Material dependency, so it works on both phone and watch. Uses `drawWithCache` so the `Paint`/`Path`/`BlurMaskFilter` are rebuilt only when size (or read values) change, not every frame.

### TempColor.kt

#### `fun tempColor(...)` — `TempColor.kt:22-34`
```kotlin
@Composable
fun tempColor(tempF: Int, rangeStart: Float = 62f, rangeEnd: Float = 82f): Color
```
Maps a climate setpoint (default 62–82°F) to a blue → green → warm-red accent for the temperature slider's fill/label color. Interpolates cool→mid for the lower half of the range and mid→warm for the upper half, then springs to the target via `animateColorAsState`. Constants come from `com.bloo.bluelink.data.BlooColors` (cool/tempMid/tempHot) — **not** the local `com.bloo.uicommon.BlooColors` object.

### WeatherUtils.kt

#### `fun weatherIcon(...)` — `WeatherUtils.kt:20-30`
```kotlin
fun weatherIcon(code: Int, isDay: Boolean): ImageVector
```
Maps a **WMO weather interpretation code** + day/night flag to a Material `ImageVector`. Shared so phone/watch pick the same icon.

#### `fun weatherTint(...)` — `WeatherUtils.kt:37-45`
```kotlin
fun weatherTint(code: Int, isDay: Boolean, neutralColor: Color): Color
```
Condition-appropriate accent color for a weather icon. Mostly static ARGB constants; callers pass `neutralColor` (typically their theme's `onSurfaceVariant`) which is returned for overcast/fog/unknown codes.

### BlooColors.kt

#### `object BlooColors` — `BlooColors.kt:12-50`
Shared color-derivation utilities (phone & watch apply the same M3-expressive treatment, so derived colors match). **Note:** this is `com.bloo.uicommon.BlooColors`, distinct from `com.bloo.bluelink.data.BlooColors` used by `tempColor`.

- `fun buttonContainer(surface: Color, onSurface: Color): Color` — `BlooColors.kt:20-24`. Default button fill for `MorphButton`. Pushes `surface` toward `onSurface` — 18% in dark themes (`surface.luminance() < 0.5f`), 20% in light — so a borderless button always contrasts its background.
- `fun onAccent(accent: Color): Color` — `BlooColors.kt:31-32`. Foreground (text/icon) color on top of `accent`: `Color(0xFF383838)` (dark gray) when `accent.luminance() > 0.5f`, else `Color.White`.
- `fun accentMuted(accent: Color): Color` — `BlooColors.kt:35-41`. A muted version of `accent` for secondary surfaces (e.g. inactive chips). Converts to HSV, multiplies saturation by 0.55 (clamped to 0.1–0.5) and value by 0.55 (floored at 0.18), converts back.
- `private fun Color.toArgbInt(): Int` — `BlooColors.kt:43-49`. Packs a Compose `Color`'s a/r/g/b (each `* 255`) into a single ARGB int for the `android.graphics.Color` HSV calls.

### BlooMotion.kt

#### `const val SoftDamping = 0.82f` — `BlooMotion.kt:11`
App-wide standard spring damping ratio, slightly overdamped so motions feel settled without oscillation. The framework's `Spring.DampingRatioMediumBouncy` (0.5) is used where a bouncier feel is intentional (e.g. watch morph-button).

---

## 3. Internal structure

### AnimatedSlider — internal helpers & control flow

The composable holds several `remember`ed state values (see §5), then defines three local functions and wires them into a pointer-input gesture handler plus a `Canvas`.

**Local dimension constants** (`AnimatedSlider.kt:98-104`): `trackThickness=14.dp`, `thumbW=6.dp`, `thumbH=44.dp`, `gap=6.dp`, `dotR=2.5.dp`, `edgePad=14.dp`, and `edgePadPx` (edgePad in px via density).

**`rawForX(x: Float): Float`** (`:111-115`) — converts a raw touch x (px, relative to control) into a value on `valueRange`, accounting for `edgePad` insets on both sides so usable travel excludes padding. `travel = (widthPx - 2*edgePadPx).coerceAtLeast(1f)`; `frac = (x - edgePadPx)/travel`; returns `valueRange.start + frac*span`. **Not** step-snapped.

**`trackTo(x: Float)`** (`:124-138`) — called on every pointer-move during a drag:
1. `raw = rawForX(x)`.
2. Computes an `overshoot` of 4.5% of span; the *visual* value is `raw` clamped to `[start - overshoot, end + overshoot]` so dragging past an edge feels elastic. Applies it via `scope.launch { anim.snapTo(visual) }`.
3. Clamps `raw` to the real range → `clamped`.
4. Snaps `clamped` to a step (`s`); if `steps > 0 && s != prevStep`, fires `onStepTick()` and updates `prevStep` (per-notch haptic, independent of the anim's smoothing).
5. Calls `onValueChange(clamped)` — free-flow during drag; the snapped step is applied on settle.

**`settleTo(target: Float)`** (`:145-168`) — called once on drag-end or plain tap with the already-step-snapped target:
1. `prevStep = target`; `settling = true`.
2. **`onValueChange(target)` then `onSettle()`** — order matters (see §8): callers track "last value seen" in `onValueChange` and read it in `onSettle`.
3. Cancels any in-flight `settleJob` so rapid taps/drags don't leave two springs writing `anim`.
4. Launches a new `settleJob`: if `reduceMotion`, `anim.snapTo(target)`; else `anim.animateTo(target, spring(dampingRatio=0.7f, stiffness=StiffnessLow))`. Then `settling = false`.

**`settleBlur`** (`:174-178`) — `animateFloatAsState`, target `4f` while `settling` else `0f`; spec `snap()` while settling (instant jump to blur) else a spring fade-back. Mirrors `MorphSegmented`'s motion-blur trick — the thumb's post-release wobble gets a soft motion-blur.

**Gesture handler** (`pointerInput(valueRange, steps)`, `:196-240`): a standard `awaitEachGesture` tap-vs-drag state machine (see §8 for the shared pattern):
- `awaitFirstDown(requireUnconsumed=false)`, read `touchSlop`, `claimed=false`.
- Loop `awaitPointerEvent`; find the change for `down.id` (break if gone).
- If `!change.pressed`: if never `claimed`, consume & treat as a tap — `settleTo(snapToStep(rawForX(down.position.x), ...))`. Break.
- If `!claimed`: compute `dx`,`dy`. If `dx > slop && dx >= dy` → `claimed=true`, `dragging=true`, consume, `trackTo(change.position.x)`. Else if `dy > slop` → break (cede to ancestor scroll, without consuming).
- Else (`claimed`) if `change.positionChanged()` → `trackTo(change.position.x)`, consume.
- After loop, if `claimed`: `dragging=false`, `settleTo(snapToStep(anim.value, ...))` — commit whichever step the final visual value quantizes to.

**Semantics** (`:246-259`): `.progressSemantics(value, valueRange, steps)` publishes the **logical** `value` (not `anim.value`, to avoid per-frame recomposition and to announce the stepped value). `.semantics { setProgress { target -> settleTo(snapToStep(target, ...)); true } }` makes it *adjustable* for TalkBack (which computes its own step size from `progressSemantics` and intercepts the raw drag).

**Canvas draw** (`:267-337`), all from `frac = (anim.value - start)/span` (span floored at 0.001):
- `thumbX = padPx + travel*frac` (travel = `size.width - 2*padPx`), `cy = height/2`.
- `cut = halfThumb + gapPx` — half-gap kept clear on each side of the thumb.
- **Inactive track**: from `inStart = (thumbX+cut).coerceAtMost(width)` to right edge; only if `inStart < width`.
- **Active track**: from `0` to `acEnd = (thumbX-cut).coerceAtLeast(0)`; only if `acEnd > 0`.
- **Step dots** (if `steps > 0`): `n = steps + 2` dots (steps + 2 endpoints), evenly spaced; skip any dot within `cut` px of `thumbX`; tint `dotOnActive` if `x <= thumbX` else `dotOnInactive`.
- **Thumb**: a `thumbW`-wide, full-height rounded rect centered on `thumbX`, corner radius = half width (pill).

### MorphSegmented — internal helpers & control flow

`selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)` (`:107`) — falls back to index 0 for a stale/unknown key rather than a crashing `-1`.

`rememberUpdatedState` wraps `selectedKey`/`onSelect`/`onTick` (`:121-123`) into `currentSelectedKey`/`currentOnSelect`/`currentOnTick` so the long-lived gesture coroutine reads live values without relaunching (see §8).

Layout: outer `Box` (`fillMaxWidth`, clipped to `trackShape` = `RoundedCornerShape(16.dp)`, `background(containerColor)`, optional `border`) → `BoxWithConstraints(padding(trackPad=4.dp).height(trackHeight))`. `BoxWithConstraints` exposes `maxWidth` needed to compute pixel widths for the gesture math.

Pixel geometry (`:136-149`): `n = options.size`; `segWidth = (maxWidth - gap*(n-1))/n`; `stepPx = (segWidth+gap).toPx()` (the "pitch"); `maxXPx = (segWidth*(n-1) + gap*(n-1)).toPx()` (furthest left-edge position = left edge of last segment); `segWidthPx`, `gapPx` converted once.

State: `dragXPx: Float?` (`:154`, non-null while dragging, indicator's left-edge target), `pendingIndex: Int?` (`:166`, just-picked index held as resting target until the real prop catches up). `restingXPx = (segWidthPx + gapPx) * (pendingIndex ?: selectedIndex)` (`:170`). `LaunchedEffect(selectedIndex)` clears `pendingIndex` when the prop catches up (`:167-169`).

`indicatorXPx = remember { Animatable(restingXPx) }` (`:179`) drives `graphicsLayer.translationX` (draw-phase only, no relayout). `targetXPx = dragXPx ?: restingXPx`. `LaunchedEffect(targetXPx, dragXPx != null)` (`:181-193`): while dragging → `snapTo` (1:1 follow); on release → `animateTo` with `spring(DampingRatioLowBouncy, StiffnessMedium)`.

**`offsetFor(touchXPx): Float`** (`:198`) = `(touchXPx - segWidthPx/2).coerceIn(0f, maxXPx)` — centers the indicator on the finger (its rendered position is its left edge).

**`indexFor(offsetXPx): Int`** (`:205-206`) = `(offsetXPx / stepPx).roundToInt().coerceIn(0, n-1)`. Expects an already-offset X so a segment's centre lands on an exact multiple of `stepPx` (rounding boundary = segment centre, not trailing edge).

`visualIndex = dragXPx?.let { indexFor(it) } ?: selectedIndex` (`:211`) — which segment reads selected *while dragging* (live position, not committed prop).

`motionBlurX` (`:221-225`) — `animateFloatAsState`, `6f` while `isMoving` (`dragXPx != null`) with `snap()`, else spring fade to `0f`.

Draw order: the highlight `Box` (`:232-239`, `width(segWidth)`, `fillMaxHeight`, `graphicsLayer{translationX=indicatorXPx.value}`, optional `blur`, `background(indicatorColor, RoundedCornerShape(14.dp))`) is declared **before** the `Row`, so it draws underneath — labels stay legible on top.

`Row` (`:243-409`, `fillMaxSize`, `horizontalArrangement = spacedBy(gap)`) hosts the single `pointerInput(n, stepPx)` gesture detector and one `Box` per option. Gesture handler mirrors `AnimatedSlider`'s tap-vs-drag machine, but wrapped in a `try { ... } finally { dragXPx = null }` (`:269-327`) so a mid-drag re-key/cancel clears the drag state:
- Tap (release while `!claimed`): `idx = indexFor(offsetFor(down.position.x))`; if `options[idx].key != currentSelectedKey` → `currentOnTick()` + `currentOnSelect(...)`.
- Drag confirmed (`dx > slop && dx >= dy`): consume, `dragXPx = offsetFor(x)`; subsequent moves update `dragXPx`.
- Vertical (`dy > slop` before claim): break without consuming (cede to scroll).
- On claimed release: `idx = indexFor(x)`; `pendingIndex = idx`; fire tick+select if changed.

Each option `Box` (`:345-407`): `width(segWidth)`, `fillMaxHeight`, clipped `RoundedCornerShape(14.dp)`, **semantics-only** click (`role = Role.Tab`, `selected = isSelected`, `onClick` fires tick+select) — additive accessibility, not a real `clickable` (which would fight the parent drag). Inside: a centered `Row` with the optional icon (`Image` + `rememberVectorPainter`, tinted `fg`, sized 16.dp selected / 14.dp not, plus a `Spacer` of 6/4.dp) and a `BasicText` label. `fg` is `animateColorAsState` between `selectedTextColor`/`unselectedTextColor`.

### WiggleText — internal structure

`isSixSeven = text.filter { it.isDigit() }.toIntOrNull() == 67` (`:47`). If `!isSixSeven || reduceMotion`: a single `BasicText` with `TextOverflow.Ellipsis` and returns early. Otherwise: `rememberInfiniteTransition` → `phase` animates `0f → 2π` over 620ms, `RepeatMode.Restart`, `LinearEasing` (a free-running radian clock). `amplitude = (style.fontSize.value * 0.22f).dp.toPx()`. A `Row` of one `BasicText` per char, each offset `translationY = sin(phase + i*1.1f) * amplitude` — the `i*1.1f` phase offset makes the bounce ripple left-to-right.

`AnimatedValue` wraps `WiggleText` in `AnimatedContent(targetState = value)` with a transition spec that is a 1ms cross-fade under `reduceMotion`, else `(fadeIn(200) + slideInVertically(200){-it/3}) togetherWith (fadeOut(150) + slideOutVertically(150){it/3})`.

### DropShadow — internal structure

`drawWithCache` block runs when size/reads change:
1. Build a framework `Paint` (`Paint().asFrameworkPaint()`), set `color = color.toArgb()`, `isAntiAlias = true`, and if `blurRadius > 0.dp` set `maskFilter = BlurMaskFilter(blurRadius.toPx(), NORMAL)`.
2. `shape.createOutline(size, layoutDirection, this)` → build an android `Path` from the outline (`Rectangle`→`addRect`, `Rounded`→`addRoundRect`, `Generic`→its `.path`), `.asAndroidPath()`.
3. Precompute `offXPx`/`offYPx`.
4. `onDrawBehind { drawIntoCanvas { save; translate(offX,offY); nativeCanvas.drawPath(path, paint); restore } }`.

---

## 4. Data & types

- **`SegmentOption`** (`MorphSegmented.kt:63`) — `data class`. Fields: `key: String` (identity, matched against `selectedKey`), `label: String` (display text + a11y contentDescription), `icon: ImageVector? = null` (optional leading icon; null on watch). No serialization here (pure UI model).
- **`object BlooColors`** (`BlooColors.kt`) — `com.bloo.uicommon` namespace; utility functions only, no fields.
- **`SoftDamping`** (`BlooMotion.kt:11`) — top-level `const val Float = 0.82f`.

No enums or sealed types are defined in this unit. There are no `@Serializable` types; nothing here crosses the Wear wire directly (this module renders; the wire lives elsewhere).

**Encoding conventions in the utility mappings:**
- **WMO weather codes** (`WeatherUtils.kt`): `0`=clear (day→WbSunny/`0xFFFFB300`, night→Nightlight/`0xFFB0BEC5`); `1,2`=partly cloudy (WbCloudy/`0xFF90A4AE`); `3`=overcast (Cloud); `45,48`=fog (BlurOn); `51,53,55,56,57`=drizzle (Grain); `61,63,65,66,67,80,81,82`=rain (Umbrella); `71,73,75,77,85,86`=snow (AcUnit/`0xFF81D4FA`); `95,96,99`=thunderstorm (Thunderstorm/`0xFF9575CD`); else Cloud. Tint: drizzle+rain share `0xFF4FC3F7`; codes `3,45,48` and `else` return the caller-supplied `neutralColor`.
- **Temperature range** (`TempColor.kt`): default `62f..82f`, `t` clamped `0..1`, split at `0.5` (cool→mid, mid→warm).
- **Slider steps** (`snapToStep`): `steps` = intermediate stops; `steps + 1` = increments; `steps + 2` = dots drawn (including endpoints).

---

## 5. State & concurrency

Everything here is **UI-thread / Compose-only**. There is no `StateFlow`, `DataStore`, `WorkManager`, coroutine `Dispatchers`, or lock in this module. "Concurrency" here means Compose coroutines launched into the composition's `rememberCoroutineScope()` and animation drivers.

**AnimatedSlider** holds:
- `scope = rememberCoroutineScope()` (`:67`) — used to launch `anim.snapTo`/`animateTo`.
- `widthPx: mutableFloatStateOf(0f)` (`:72`) — set by `.onSizeChanged` (only known after first layout).
- `anim = remember { Animatable(value) }` (`:78`) — the single source of truth for the *rendered* thumb position. Read in the `Canvas` draw scope (draw-phase, so drag/settle repaints **without recomposition**).
- `dragging: Boolean` (`:79`), `settling: Boolean` (`:91`), `prevStep: Float` (`:80`), `settleJob: Job?` (`:92`).
- `LaunchedEffect(value)` (`:94-96`) re-syncs `anim` to the logical `value` only when idle: `if (!dragging && !settling && !anim.isRunning && anim.value != value) anim.snapTo(value)`. The `settling` flag exists specifically to close a scheduling race between `settleTo`'s synchronous `onValueChange` (which recomposes and re-triggers this effect) and the not-yet-started bounce coroutine (see §8).

**MorphSegmented** holds: `currentSelectedKey/currentOnSelect/currentOnTick` via `rememberUpdatedState`; `dragXPx: Float?`; `pendingIndex: Int?`; `indicatorXPx = remember { Animatable(restingXPx) }`; plus derived `animateColorAsState`/`animateFloatAsState`. Two `LaunchedEffect`s: one keyed on `selectedIndex` (clears `pendingIndex`), one keyed on `(targetXPx, dragXPx != null)` (snap vs spring the indicator). The gesture coroutine is launched once (keyed only on `n`/`stepPx`) and loops internally.

**WiggleText** uses `rememberInfiniteTransition`/`animateFloat` for `phase` (only allocated on the 67 path). **tempColor** uses `animateColorAsState`. **settleBlur**/**motionBlurX** use `animateFloatAsState`.

**Recomposition triggers**: Both custom controls deliberately push per-frame motion into draw-phase (`Canvas` reading `anim.value`; `graphicsLayer.translationX = indicatorXPx.value`) rather than composition, so dragging does **not** recompose per frame. Recomposition happens on prop changes (`value`/`selectedKey`), `dragXPx`/`pendingIndex` flips, size changes, and animated-color/blur updates.

---

## 6. Collaborators & data flow

**Callers (inbound):** `:app` and `:wear` build their screens/widgets on these components. `AnimatedSlider` is wrapped by `Screens.kt`'s own `AnimatedSlider` wrapper on phone (referenced in the `onSettle` ordering comment, `AnimatedSlider.kt:151`). `MorphButton` (phone) / its wear twin consume `BlooColors.buttonContainer`/`onAccent`. `tempColor` feeds the temperature slider's fill/label. `weatherIcon`/`weatherTint` feed weather chips on both platforms. `dropShadow` is used by floating chrome (e.g. a FAB fading in via `AnimatedVisibility`).

**Dependencies (outbound):**
- `TempColor.kt` imports **`com.bloo.bluelink.data.BlooColors`** (the `:shared` module's palette object) for its `cool`/`tempMid`/`tempHot` int constants — a cross-module dependency. This is **not** the local `com.bloo.uicommon.BlooColors`.
- `BlooColors.kt` calls `android.graphics.Color.colorToHSV` / `HSVToColor` and `Color.luminance()` / `lerp`.
- `DropShadow.kt` calls into `android.graphics.BlurMaskFilter` and `nativeCanvas`.
- `WeatherUtils.kt` uses `androidx.compose.material.icons.Icons.Filled.*` vectors.

**Data channels:** All data enters and leaves via **function-call parameters and callbacks only** — no DataStore, no Wear Data Layer paths, no intents, no WorkManager touched in this module. Callbacks out: `onValueChange`/`onStepTick`/`onSettle` (slider), `onSelect`/`onTick` (segmented). These callbacks are where callers wire haptics and commit state (which, upstream, may trigger the Wear sync / telematics commands — but that is entirely the caller's concern).

---

## 7. Invariants & assumptions

1. **Callers pre-resolve style** for `WiggleText`/`AnimatedValue`: `Color.Unspecified` must be replaced and `fontWeight` merged into `style` before calling — `BasicText` does not read `LocalContentColor`, so an unspecified color renders black (`WiggleText.kt:34`, and the `MorphSegmented` `BasicText` note at `:391-402`).
2. **`snapToStep` semantics**: `steps` is *intermediate* stops; callers must pass the same `valueRange`/`steps` to the slider and to any external `snapToStep` call for values to agree.
3. **`MorphSegmented` never sees a `-1` index** — `selectedIndex` is `coerceAtLeast(0)`, so an unknown `selectedKey` silently falls back to option 0 rather than crashing the pixel math.
4. **`options` non-empty**: `segWidth = (maxWidth - gap*(n-1))/n` divides by `n = options.size`; an empty list divides by zero.
5. **`widthPx` known before gesture math** in the slider: `rawForX` floors travel at `1f`, so a pre-layout tap (widthPx=0) won't divide by zero but will produce a degenerate fraction. In practice gestures arrive after layout.
6. **Draw-phase reads are intentional**: `anim.value` is read only inside `Canvas`, and `indicatorXPx.value` only inside `graphicsLayer`, to avoid per-frame recomposition. Moving those reads up into the composable body would reintroduce per-frame recompose.
7. **`onValueChange` precedes `onSettle`** (slider) — callers depend on this ordering to read a current value inside `onSettle`.
8. **`luminance() < 0.5f` = "dark"** is the shared cutoff used by `buttonContainer` and `onAccent`, assumed to match the app's M3-expressive theming.
9. **Weather codes are WMO codes** (Open-Meteo style), not any brand-specific scheme.
10. **The gesture handler owns all touch** in both controls; child `semantics { onClick/setProgress }` are additive a11y actions that bypass touch dispatch (a real `clickable`/`selectable` would fight the custom drag).

---

## 8. Gotchas & sharp edges

- **The `settling` flag closes a real race (slider, `AnimatedSlider.kt:81-96,145-168`).** `settleTo` calls `onValueChange(target)` synchronously *before* launching the bounce spring. That recomposition re-triggers `LaunchedEffect(value)`, which — if it ran before the launched coroutine actually started animating — would `snapTo(value)` straight to target, and the spring would then animate target→target (a no-op that reads as the bounce snapping partway). `anim.isRunning` can't detect this window (it doesn't flip true until the coroutine starts). `settling`, set synchronously inside `settleTo` before any suspension, guards the effect.
- **`settleJob?.cancel()` before relaunch (slider `:156`)** prevents two competing settle springs both writing `anim` on rapid taps/drags.
- **Overshoot is visual-only (slider `:127-137`)**: the rendered thumb may travel 4.5% past an edge, but `onValueChange` always receives the clamped-to-range value. The elastic feel never leaks into the committed value.
- **`progressSemantics` reads the logical `value`, not `anim.value` (slider `:246`)** — deliberately, to avoid invalidating composition every animation frame and to announce the stepped value to assistive tech.
- **`setProgress` is required for TalkBack adjustability (slider `:254-259`)** — `progressSemantics` alone is read-only; touch-exploration intercepts the raw drag the `pointerInput` needs, so without `setProgress` a screen-reader user could hear but not change the value.
- **`rememberUpdatedState` is load-bearing in `MorphSegmented` (`:121-123`).** The gesture coroutine is launched once (keyed on `n`/`stepPx`) and loops forever across recompositions. A plain closure over `selectedKey`/`onSelect`/`onTick` would freeze at their first values ("stops working after a couple taps, can't reselect the original option"). `rememberUpdatedState` keeps reads live without relaunching (interrupting) an in-progress gesture.
- **`pendingIndex` prevents a visible snap-back (`MorphSegmented.kt:166-170,316-319`).** On drag release `dragXPx` clears immediately, but the new `selectedIndex` only arrives after the caller's state round-trips. Without `pendingIndex`, `restingXPx` briefly reflected the *old* index and the indicator jerked backward then forward ("jumpy when you let go"). `pendingIndex` holds the just-picked index as the resting target until `LaunchedEffect(selectedIndex)` clears it.
- **`try/finally { dragXPx = null }` in `MorphSegmented`'s gesture (`:269-327`).** A width change mid-drag (rotation, compact-layout switch) re-keys the `pointerInput` and cancels the coroutine outright; without the `finally`, `dragXPx` would only clear at the natural loop end, freezing the indicator at its last dragged position forever.
- **`graphicsLayer.translationX` not `Modifier.offset` (`MorphSegmented.kt:170-179,236`).** `offset()` moves the *layout* position, forcing a full relayout on every finger-pixel during a drag ("junky when dragging"). `graphicsLayer` translation is a pure draw-phase transform — same visual position, no relayout.
- **`indexFor` rounds at segment *centre*, not trailing edge (`MorphSegmented.kt:198-206`).** Because `offsetFor` subtracts half a segment first, a segment's own centre lands on an exact multiple of `stepPx`. A naive scheme rounded a back-third tap up into the next segment.
- **`BasicText` ignores `LocalContentColor` (`MorphSegmented.kt:391-402`, `WiggleText.kt:34,53`).** The animated `fg` color must be baked into the `TextStyle.copy(color = fg, ...)`; otherwise the label silently rendered at the style default (unspecified → black) regardless of theme/selection. Unselected labels also render at `fontSize * 0.88f`, Normal weight, to keep the control visually quiet.
- **`WiggleText` overflow is `Ellipsis`, not the `BasicText` default `Clip` (`WiggleText.kt:48-53`).** A long status string routed through `AnimatedValue` would otherwise hard-clip instead of trailing off with "…". The wiggle branch (`Row` of per-char `BasicText`s) uses `maxLines = 1` per char and has no ellipsis (it's numeric-only anyway).
- **The "67" easter egg (`WiggleText.kt:47`)** matches any string whose digits parse to 67 ("67", "67°", "67F"), but deliberately not multi-number strings like "6-7" (filtering digits from "6-7" → "67" would falsely trigger; but `toIntOrNull` on the *filtered* string does still collapse "6-7"→"67" — the comment claims the filter guards this, which holds only for strings with non-digit separators the filter removes, so "6-7" *would* actually match; treat this as an intentional easter egg, low-risk). Non-numeric leftovers give `null ≠ 67`.
- **`dropShadow` uses `drawWithCache`, not `drawBehind` (`DropShadow.kt:26-36`).** `drawBehind` rebuilt the native `BlurMaskFilter`/`Paint`/`Path` every frame — a per-frame native allocation that showed up as the shadow glitching/popping instead of fading smoothly with `AnimatedVisibility`. `drawWithCache` rebuilds only when size (or read values) change and reuses the objects across redraws.
- **Two different `BlooColors` (`TempColor.kt:10` vs `BlooColors.kt:12`).** `tempColor` uses `com.bloo.bluelink.data.BlooColors` (the `:shared` palette with `cool`/`tempMid`/`tempHot` int constants); the utility object in this module is `com.bloo.uicommon.BlooColors`. They are unrelated — a real footgun when editing/import-completing. The comment at `TempColor.kt:12-20` notes the phone/watch previously had drifted one-off hex triples; these were reconciled to the `:shared` canonical constants.
- **`reduceMotion` is honored inconsistently by design**: the slider still applies `settleBlur` under `reduceMotion` (only the bounce spring becomes a `snapTo`), whereas `WiggleText`/`AnimatedValue` fully collapse.
- **`SoftDamping = 0.82f` is defined but not used within this module** — it's the shared constant callers (`:app`/`:wear`) reach for; the local controls hardcode their own damping (slider settle 0.7, segmented `DampingRatioLowBouncy`, blurs 0.6).
