# app: CarWidget (Glance widget rendering)

**Files:** `app/src/main/java/com/bloo/bluelink/widget/`
`CarWidget.kt` (~3140 lines) plus five files it was deliberately split out of,
each independently unit-testable with no Glance/Android dependency:
`WidgetGrid.kt` (101), `WidgetTier.kt` (149), `WidgetLayout.kt` (300),
`WidgetScale.kt` (724), `WidgetConfig.kt` (291), `WidgetConfigActivity.kt`
(466), plus `WidgetActions.kt` (185, click callbacks/workers), `WidgetMap.kt`,
`WidgetPhoto.kt`, `ChargeRing.kt`, `CarWidgetReceiver.kt`.

**Supersedes:** `widget-BlooWidget.md`, which documented an earlier
single-file `BlooWidget`/`BlooWidgetReceiver` implementation (1×1–5×5, six
size buckets). That class no longer exists — the widget was rebuilt around a
continuous, tested sizing model covering every grid shape from 2×1 through
7×7. Other docs in this tree (`widget-workers-and-config.md`,
`AppViewModel-part*.md`, `wear-bridge-phone-side.md`,
`snapshot-and-cache.md`, `build-and-manifests.md`) still name-check
`BlooWidget`/`BlooWidgetReceiver` in prose; read `CarWidget`/`CarWidgetReceiver`
for those references until they're swept.

---

## 1. Purpose

`CarWidget` is the single Glance `GlanceAppWidget` that renders every Bloo
home-screen widget instance, at every size the manifest allows — nominally a
2-to-7-column, 1-to-7-row launcher grid (`car_widget_info.xml`:
`minWidth/Height=40dp`, `maxResizeWidth/Height=640dp`,
`resizeMode="horizontal|vertical"`), but really any exact pixel size a host
measures it at, since `SizeMode.Exact` re-invokes the whole composition per
size rather than snapping to buckets.

It shows a battery/fuel readout, chunky state-reactive action buttons (lock,
climate, charge, refresh, locate, open, …), and progressively more modules —
header, status ring or bar-hero, info stat rows, a location map thumbnail,
footer — as the measured size allows. What appears is the intersection of
"what fits" (a **size tier**, §3) and "what the user asked for"
(`WidgetConfig`, edited in `WidgetConfigActivity`, §5).

## 2. Why it's split across six files

The widget used to be one file, and its worst bugs were never about *what*
to draw — they were about *how much room* a slot actually had, an arithmetic
question that lived buried inside 3000 lines of Compose and could only be
checked by running the launcher. Four separate reservation bugs (button
height cap, ring room, bar-hero minimum, name/stat pairing) were each found
by re-deriving the formulas outside the codebase, and each time the fix
could only be re-verified the same way — by simulating again. Pulling the
size math into files with **no Glance or Android import** turns that
external simulation into a permanent CI-run JVM test against the *real*
functions, not a hand-copied model of them:

| File | Owns | Depends on |
|---|---|---|
| `WidgetGrid.kt` | The launcher **grid model** — cols/rows ↔ dp, via the standard Android `70n − 30` cell formula. A classification the manifest and every launcher agree on, not the render canvas. | nothing |
| `WidgetTier.kt` | `WidgetTier` enum (18 tiers) + `tierFor(DpSize)` — which tier a measured size lands on. Pure `when` on width/height/aspect. | nothing |
| `WidgetScale.kt` | `Scale` — continuous sizing **primitives**: font sizes, paddings, ring/bar dimensions, button geometry, a `Frame` bundling per-render facts, and the `fittedSp`/`overflows` horizontal-fit model shared by every shrink-to-fit text helper. | `WidgetTier` (frame only) |
| `WidgetLayout.kt` | `WidgetLayout` — the **decision layer** above `Scale`: one `*Plan` function per tier family (`tallPlan`, `squarePlan`, `wideBarPlan`, `ringHeroPlan`, `mediumWideBarPlan`), each backed by a private per-tier `*Spec` so the composables and `WidgetLayoutTest`'s sweep call the identical function. | `WidgetScale` |
| `WidgetConfig.kt` | The per-widget settings **data class** + its string-constant "enums" (theme, priority, corner, button-labels, …) and derived helpers (`effectiveCorner`, `safeTextScale`). | nothing |
| `WidgetConfigActivity.kt` | The Compose (regular, not Glance) settings screen that edits a `WidgetConfig` and saves it via `WidgetConfigStore`. | `WidgetConfig` |

`CarWidget.kt` itself keeps everything that genuinely needs Glance: the
`GlanceAppWidget` entry points, the 18 tier composables (which slots, in
which order, with which spacers — *structure*), and the shared render
modules (`HeaderRow`, `RingImage`, `InfoStack`, `ActionButtons`, `FitText`/
`FitLine`, …). Composables decide the render tree; `Scale`/`WidgetLayout`
decide the space budget it renders into. That split is deliberate and load-
bearing — see `WidgetLayout.kt`'s own doc comment.

## 3. The grid model and the tier system

**`WidgetGrid`** (`WidgetGrid.kt:45`) answers the question a user is
actually asking when they drag a widget's resize handles: "how many columns
and rows." `nominalSize(cols, rows)` gives the standard Android cell-pitch dp
size (`70n − 30` per axis — a 70dp cell pitch minus a fixed 30dp inset, the
same formula Android's own widget design guidance has used since App Widgets
shipped); `gridFor(DpSize)` rounds a real measurement back to the nearest
`(cols, rows)`. It's a **classification** layer, not the render canvas —
real rendering always uses the continuous, exact measured `DpSize` through
`Scale`/`WidgetLayout`, never a fixed dp-per-cell assumption (which would
either waste real launcher space rounding down, or overflow it rounding up).

**`tierFor(DpSize)`** (`WidgetTier.kt:88`) then picks one of 18
`WidgetTier` values from the *exact* measured size — largest-first `when` on
width/height/aspect, e.g. `w>=300 && h>=300` splits into `XL_WIDE`/
`XL_TALL`/`XL_SQUARE` by aspect ratio, down through `LARGE_*`/`MEDIUM_*`
bands, then two grid-driven strip tiers (`BANNER` — any tile shorter than
one compact cell, at any width from `WidgetGrid.MIN_COLS` up; `RAIL` — the
narrow-tall mirror), two lopsided-but-roomy `COMPACT_WIDE(_NARROW)`/
`COMPACT_TALL(_NARROW)` bands, a square catch-all `COMPACT_SQUARE`, and
finally `MICRO`/`MICRO_TINY` for tiles at or near the manifest's absolute
40dp floor (icon-only — no size configurable from a real launcher's picker
should ever land here; `WidgetTierTest` sweeps the whole 2–7×1–7 grid to
prove it).

`Content` (`CarWidget.kt:349`) dispatches on `tierFor(size)` to one
composable per tier:

| Tier | Composable (`CarWidget.kt:`) |
|---|---|
| `MICRO_TINY` / `MICRO` | `MicroTinyLayout` (464) / `MicroLayout` (491) |
| `BANNER` / `RAIL` | `BannerLayout` (528) / `RailLayout` (646) |
| `COMPACT_SQUARE` | `CompactSquareLayout` (726) |
| `COMPACT_WIDE_NARROW` / `COMPACT_WIDE` | `CompactWideNarrowLayout` (791) / `CompactWideLayout` (856) |
| `COMPACT_TALL_NARROW` / `COMPACT_TALL` | `CompactTallNarrowLayout` (915) / `CompactTallLayout` (981) |
| `MEDIUM_SQUARE` / `MEDIUM_WIDE` / `MEDIUM_TALL` | `MediumSquareLayout` (1058) / `MediumWideLayout` (1143) / `MediumTallLayout` (1230) |
| `LARGE_WIDE` / `LARGE_SQUARE` / `LARGE_TALL` | `LargeWideLayout` (1295) / `LargeSquareLayout` (1373) / `LargeTallLayout` (1446) |
| `XL_WIDE` / `XL_TALL` / `XL_SQUARE` | `XlWideLayout` (1493) / `XlTallLayout` (1545) / `XlSquareLayout` (1608) |

Every tier composable is built from the same shared modules — `HeaderRow`
(1671), `FooterRow` (1713), `InfoStack` (1882), `RingImage` (2787),
`ActionButtons`/`ActionButton` (2192/2386), `FitText`/`FitLine` (2089/2136) —
so 18 layouts stay one maintained set of building blocks instead of 18
independent implementations. What changes tier to tier is composition and
proportion (via `Scale`/`WidgetLayout`), never reinvented per-tier logic.

**RemoteViews cannot clip overflowing content.** This is the single fact
behind nearly every bug this widget has ever had: an under-budgeted
reservation doesn't get truncated at the tile edge, it silently renders
*outside* it or vanishes. Every budget in `Scale`/`WidgetLayout` exists to
make that structurally impossible rather than something caught by eye —
which is also why `FitText`/`FitLine` exist: any text slot that COULD
overflow shrinks (and, as a last resort, wraps or stacks one character per
line) to the room it's actually given instead.

## 4. `CarWidget` class structure

- **`override val sizeMode = SizeMode.Exact`** — recompose per exact size,
  not a handful of `Responsive` buckets; this is what lets `tierFor` work
  directly off `LocalSize.current`.
- **`provideGlance(context, id)`** — two phases, matching `GlanceAppWidget`'s
  own guidance ("load initial data before `provideContent`, observe your
  sources of data *within* the composition"):
  1. Suspend prelude: resolve `appWidgetId`, load `WidgetConfig` from
     `WidgetConfigStore`, pick the car (a pinned widget — `config.vin` set —
     never silently swaps cars; a "follow" widget — `vin == null` — tracks
     `data.selected`), resolve `WidgetTheme`, compute staleness, and
     pre-fetch the two things composables can't (`WidgetMap.render` for the
     location thumbnail, `WidgetPhoto.decodeCached`+`blurredCached` for the
     photo background) — both cold-path, only re-run when the car identity
     changes.
  2. `provideContent { }`: `collectAsState` on `SnapshotStore.payload`
     *inside* the composition — deliberately, not just read once — because
     `update()`/`updateAll()` only calls `session.updateGlance()` if a
     session is already running, which re-reads Glance *state*, not the
     suspend body above. Without the live collect, a second `updateAll()`
     inside the same session (e.g. the real status landing seconds after an
     optimistic command write) is silently dropped — confirmed as a real
     "I sent a command and it never updated" bug.
- **`private data class Render`** — the per-render bundle threaded to every
  composable: `car`, `config`, `theme`, `metric`, `multiCar`, `stale`,
  `mapBitmap`, `photoBitmap`, plus two derived helpers: `hasSwitcher` (does
  `HeaderRow` draw the car-switcher pill) and `frame(size)`/`pillCorner(size)`
  (builds the `Scale.Frame` every budget needs — one definition, so the
  padding `Content` actually draws and the padding every tier's budget
  subtracts can't disagree, which they did for all 18 tiers before this
  existed).
- **`Content(render)`** (289) — resolves corner shape, draws the
  photo/scrim or themed background, a whole-card tap target (`openAction`,
  under everything else so empty space is tappable too — an earlier version
  only made specific inner elements clickable), then the tier `when` block.
- **`onCompositionError`** (120) — replaces Glance's bare "Can't show
  content" with a themed `RemoteViews` panel (plain, non-composable, since
  composition has already failed) that opens the app on tap.
- **`WidgetTheme`** (3035, private data class at file scope) — the
  fully-resolved per-render color set: accent/onAccent (from
  `resolveWidgetAccent`, or a `WidgetConfig.accent` override), semantic
  charge/unlocked/climate colors, background/surface colors (following the
  app's real theme unless `WidgetConfig.theme` overrides it), and
  `textScale`. Resolved once per `provideGlance`, not the vanilla
  `GlanceTheme` default (wallpaper-derived Material You, no relation to
  Bloo's branding).

## 5. Settings (`WidgetConfig` / `WidgetConfigActivity`)

`WidgetConfig` (data class, `WidgetConfig.kt:18`) is saved per widget
instance via `WidgetConfigStore` and edited in `WidgetConfigActivity`'s
Compose screen (simple mode + an advanced-mode reveal). Fields, each a
string "enum" backed by companion-object constants rather than a Kotlin
`enum class` (so an unrecognized saved value from a future app version falls
through to a sane default branch instead of crashing deserialization):

- `actions: List<String>` — configured `WidgetAction` keys (Controls chips).
- `infoFields: List<String>` — which stats show (advanced mode).
- `showRing` / `showMap` / `photoBackground` — toggles.
- `priority: String` (`PRIORITY_INFO` / `PRIORITY_CONTROLS`) — which wins on
  sizes too small for both.
- `corner: String` (`CORNER_SHARP`/`SOFT`/`ROUND`/`PILL`) + legacy
  `pillShape: Boolean`, reconciled via `effectiveCorner`.
- `backgroundOpacity: Float`, `textScale: Float` — clamped by
  `safeBackgroundOpacity`/`safeTextScale`.
- `showHeader` / `showFooter`, `accent: String?`, `theme: String`
  (`THEME_AUTO`/`LIGHT`/`DARK`).
- **`buttonLabels: String`** (`BUTTON_LABELS_AUTO`/`ALWAYS`/`OFF`) — added
  this pass. AUTO keeps the original all-or-nothing room check
  (`ActionButtons`: label only when every configured button's own longest
  label fits); ALWAYS forces every button to name itself regardless of room;
  OFF forces icons only. ALWAYS is safe on any tile because `ActionButton`'s
  label render (`CarWidget.kt`, inside `ActionButton`) goes through
  `FitLine` rather than a bare `Text` — it shrinks (and stacks as a last
  resort) to whatever width it's actually handed, so forcing it on can't
  reintroduce the overflow bug the AUTO room-check existed to prevent.

## 6. Click routing

`WidgetActions.kt` defines `WidgetKeys` (typed `ActionParameters` keys) and
the Glance `ActionCallback`s. `CarWidget.openAction(context)` (3019) routes
the whole-card tap to `MainActivity`. Per-button routing inside
`ActionButton` resolves `WidgetAction.Kind`: `NAV` → `openAction`;
`REFRESH` → `actionRunCallback<WidgetRefreshAction>`; everything else →
`actionRunCallback<WidgetCommandAction>` with `WidgetKeys.VIN`/`ACTION`
parameters, which resolves the toggle direction from the current snapshot,
writes an optimistic result + repaints (`CarWidget().updateAll`), then runs
the real command in a `WidgetCommandWorker` that reverts on failure.
**PendingIntent `filterEquals` ignores extras** — every intent that needs to
stay distinct per widget/action encodes it in the `data` URI instead
(`bloo://widget/$id/$action`), or every button on a widget collapses into
one PendingIntent that always fires the last-cached action.

## 7. Invariants worth knowing before touching this package

- **RemoteViews never clips.** Any new module must reserve its own room
  through `Scale`/`WidgetLayout`, not assume overflow is visually harmless.
- **`tierFor` must route every size in the 2–7×1–7 grid to a real layout,
  never `MICRO`/`MICRO_TINY`.** `WidgetTierTest` sweeps this; two real bugs
  (2×1/3×1 falling to icon-only, a 70–79dp sliver regressing on growth) were
  found exactly this way, not by inspection.
- **A budget function and the composable it feeds must be the SAME
  function**, not a hand-copied mirror — `WidgetScaleTest`/`WidgetLayoutTest`
  sweep the real `Scale`/`WidgetLayout` calls specifically so the two can't
  drift the way the old inline-per-tier constants once did.
- **A generic "is there room" gate can be looser than what the specific
  content drawn in that slot needs.** `Scale.ringHero`'s 24dp floor (tuned
  for a circle) let `BarHero` (~35dp `heroSpIn` need) pass the generic check
  and then render nothing — fixed by giving `BarHero` its own three-way
  fallback (hero number → small stat line → nothing only when truly no
  room) rather than trusting the generic gate.
- **A widget render must never make a live network call** — only cached
  `SnapshotStore`/`WidgetConfigStore` reads and pre-fetched bitmaps.
