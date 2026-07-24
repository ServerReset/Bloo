# Screens.kt — Part 4: Settings screen, AI search, natural-language command parser (lines ~8200–10973)

File: `app/src/main/java/com/bloo/bluelink/ui/Screens.kt`

This document covers the tail of `Screens.kt` (10973 lines total): the whole
**Settings** screen and its cards, the bottom-anchored **search** experience
(live settings filter + on-device **AI** answers + a natural-language **vehicle
command** path), the **Quick Settings tiles** configuration UI, and the small
shared leaf widgets (`SettingsCard`, `ToggleRow`, `MorphToggleTrack`,
`StatusRow`, `SecretRow`, `ChoiceRow`, `StepRow`, `SettingsSegmentedRow`,
`MorphSegmented`, `PowertrainPicker`, dialogs) that live in this range.

The single most important thing in this slice: **`parseVehicleCommand` +
`SettingsSearchResults` are a second, fully-live path that issues real commands
to real cars** (via `TileCommandRunner.run`). Typing "lock my Ioniq" into the
Settings search box and pressing enter locks a car. Sections 3, 7, and 8 below
document exactly how, and two real bugs in that path.

The range opens mid-`CropScreen` (the photo-crop `MorphButton` "Use photo"
handler, 8232–8277) — `CropScreen` itself begins earlier in the file and is a
photo-cropping helper, not part of this unit's focus. It is used by
`SettingsScreen` (9381–9391) to crop a picked car photo.

---

## 1. Purpose

This slice is **the Settings surface of the phone app**. It is one long
vertically-scrolling `Column` of `SettingsCard`s wrapping every user-facing
preference the app has (accounts, AI, shortcuts, cars, backup/sync, display,
font, links, logs, notifications, quick tiles, security, sounds, theme,
weather), plus a **floating, bottom-anchored search bar** hoisted *outside* that
scroll so it stays pinned to the screen bottom.

The search bar does three things at once (7213-style "answer box"):

1. **Filters settings by name/keyword** live as you type (no side effects).
2. **Answers plain-language data questions** ("what's my odometer") via on-device
   Gemini Nano when AI is enabled (`askAi`).
3. **Runs a small, conservative set of vehicle commands** ("lock my car", "start
   smart climate", "stop charging") on an explicit submit, through the same
   `TileCommandRunner` path the Quick Settings tiles use.

Two cross-cutting behaviours span the whole screen (documented in the
`SettingsScreen` KDoc, 8284–8306):

- **Simple vs. Advanced mode** (`state.settingsMode`, a `"simple"`/`"advanced"`
  string): power-user cards/sections are wrapped in a shared
  `AnimatedVisibility(visible = advanced, enter = advancedEnter, exit =
  advancedExit)` so toggling the mode reveals/tucks every advanced-only section
  in visual lockstep.
- **Two separate query states**: `query` (live, every keystroke, filter-only) is
  deliberately kept apart from `submittedQuery` (only set on an explicit
  submit/tap). A mis-typed partial query must never fire a command or an AI
  request — only a deliberate submission does. This split is a load-bearing
  safety invariant (see §7).

---

## 2. Public surface

Everything in this range is `private` to the file **except** four items that are
package/module-visible and reused elsewhere: `MorphSegmented` (9220),
`SettingsSegmentedRow` (9276), `ToggleRow` (10740), and the `SegmentOption`
typealias (10210). The rest are file-private building blocks.

### Screen entry points & car cards

- **`SettingsScreen(vm: AppViewModel)`** (8309, `@OptIn(ExperimentalMaterial3Api)`, `private`) — the entire Settings screen. Collects `vm.appearance`, `vm.notifications`, `vm.state`, `vm.logs`; hoists `query`/`searchFocused`/`submittedQuery`; wires the photo picker + crop; lays out the scrolling card column, the floating search overlay, the top chrome row (back arrow, "Settings" title pill that scrolls-to-top, Simple/Advanced segmented control), a first-run coach mark, and the crop dialog. See §3.1 for structure and §2.1 for the card inventory.
- **`CarSettingsCard(v, state, vm, expanded, dragging, dragHandle, onToggle, onPickPhoto, collapsible=true, showHandle=true)`** (9397, `private`) — one reorderable car entry. Collapsed header = drag handle (optional) + photo thumbnail + name + "`model · powertrainLabel`" + expand control. Expanded body is a stack of `SettingsGroup`s: **Powertrain** (always), **Climate features** (always; seat heat/cool + heated steering wheel), and — only when `state.settingsMode == "advanced"` — **Default climate start**, **Palette override** (only if `appearance.customPalettes.isNotEmpty()`), **Photo** (always), **Identity & service** (VIN/plate/service miles), and **Sections shown** (per-section pebble visibility toggles). With a single car it's rendered non-collapsible/handle-less and always expanded (8550–8557).
- **`SettingsGroup(title, content)`** (9665, `private`) — a titled, tonal (`surfaceColorAtElevation(6.dp)`) boxed sub-group used inside `CarSettingsCard`; its title carries `semantics { heading() }`.

### Search surface

- **`GlowySearchBar(query, focused, onQueryChange, onFocusChange, onSubmit)`** (9725, `private`) — the pill that morphs between a collapsed "Search" button (~40% width) and a full text field (100% width). Persistent identity (one `Surface`, animated width fraction + content cross-fade), a hand-ticked ~12fps glow halo, and an IME `Search` action that calls `onSubmit`. Trailing icon clears text if any, else calls `onFocusChange(false)`. See §5 for the glow throttling.
- **`SearchSuggestions(state, onPick)`** (9917, `private`) — example-query chips shown while the bar is focused but empty ("odometer for <car>", "battery level", "lock my <car>", "haptic feedback", and "start smart climate" iff any car `hasBattery`). Tapping a chip calls `onPick`, which both sets `query` and `submittedQuery` (the tap *is* the submit).
- **`SettingsSearchResults(query, submittedQuery, vm, state, appearance, notif)`** (9963, `private`) — the heart of search. Builds the searchable `SearchEntry` catalogue (app-wide + per-car), filters it by tokenised query, renders matches as cards, then renders (a) an **Action** card if `parseVehicleCommand(submittedQuery)` matched, and (b) an **AI answer** card if `state.aiEnabled`. See §3.2/§3.3.

### Command parsing / data types (see §4)

- **`SearchStopwords`** (9685, `private val`) — the filler-word set dropped during tokenisation.
- **`SearchEntry(title, haystack, content)`** (9690, `private class`) — one searchable settings row.
- **`ParsedVehicleCommand(cmd, climateTarget="default", label)`** (9696, `private class`) — a recognised command.
- **`parseVehicleCommand(query): ParsedVehicleCommand?`** (9703, `private fun`) — the natural-language recogniser. **This is where typed/spoken text becomes a car command.** See §3.3.

### Quick Settings tiles UI

- **`QuickTilesManager(state, vm)`** (10380, `private`) — per-car tile card list; one tonal `Card` per car containing a `CarTilesHeader`, the car's assigned `QuickTileCard`s, and an add/at-capacity affordance. `TILE_COUNT` = 12 total slots shared across all cars.
- **`CarTilesHeader(name, img, assignedCount, totalTiles)`** (10295, `private`) — per-car header: thumbnail/gradient bubble, name, "N of M tiles used", and an animated capacity bar.
- **`QuickTileCard(index, vin, state, vm)`** (10471, `private`) — one configured tile built on `PebbleShell`. Collapsed header is a live preview (icon/name/current state reading `state.statusFor`); its `PebbleHeaderAction` "Add" button calls `addTileToQuickSettings`. Expanded body edits the action (`MorphSegmented` over `TileActions`), a custom name, the climate target (Basic/Smart/preset), and a "Remove tile" button.
- **`AddTilePill(label, onClick)`** (10573, `private`) — outlined "add" pill.
- **`TileEmptyHint(text)`** (10361, `private`) — muted hint for empty/full states.
- **`addTileToQuickSettings(context, index, cmd, label, unlocked)`** (10428, `private fun`) — asks the OS (API 33+ `StatusBarManager.requestAddTileButton` via `BlooTileService.requestAddToQuickSettings`) to add the tile; falls back to a toast instructing manual add on older APIs. Toasts the add-result.
- **`TileActions`** (10198, `private val`) — `List<Triple<key,label,icon>>`: `doors`/`climate`/`charge`/`open`.
- **`tileActionLabel(cmd)`** (10206, `private fun`) — label for a tile action key, falls back to the key.
- **`tileSummary(cmd, climateTarget, presetName)`** (10449, `private fun`) — one-line summary string for a tile ("Lock / unlock", "Climate · Smart/Basic/<preset>", "Start / stop charge", "Opens the app").

### Segmented controls & pickers

- **`MorphSegmented(options, selectedKey, onSelect, modifier=Modifier, containerColor=null, trackHeight=null)`** (9220, **non-private**) — the phone's thin wrapper over `com.bloo.uicommon.MorphSegmented`, supplying M3 colours, `labelLarge` typography, haptic `tick`, a hairline `outline` rim, and a default track height (48dp if any option has an icon, else 44dp).
- **`SegmentOption`** (10210, `typealias` → `com.bloo.uicommon.SegmentOption`) — one segment (`key`, `label`, optional `icon`).
- **`PowertrainPicker(current, onSelect)`** (10255, `private`) — a 4-way `MorphSegmented` (Gas/Hybrid/PHEV/EV) with per-option icons; maps to/from `com.bloo.bluelink.data.Powertrain` by `.name`.
- **`SettingsSegmentedRow(label, options, selectedKey, onSelect)`** (10276, **non-private**) — a caption above a full-width `MorphSegmented`; the "choice between two equal alternatives" replacement for a switch (°C/°F, in-app/browser, etc.).

### Reusable leaf widgets

- **`SettingsCard(title, content)`** (10589, `private`) — the standard section card: bold `titleMedium` heading with `semantics { heading() }` (so TalkBack "headings" navigation can jump between the ~15 cards), then content, with `animateContentSize` (spring, `SoftDamping`/`AdvancedModeStiffness`).
- **`SecretRow(label, value)`** (10613, `private`) — masked value (bullets) with a Show/Hide toggle button.
- **`ChoiceRow(label, selected, onSelect)`** (10628, `private`) — a full-width single-select row: animated corner (14dp selected/pressed → 24dp), animated background (`primaryContainer` when selected), trailing check that scale-fades in. Used by the Font card.
- **`StatusRow(label, value)`** (10674, `private`) — a dimmed label + a right-aligned animated value cell (`com.bloo.uicommon.AnimatedValue`, colour pinned to full-strength `onSurface`). The workhorse read-only row across search results and pebbles.
- **`SectionLabel(text)`** (10701, `private`) — small bold group heading (used by the Car-info pebble; defined here).
- **`StepRow(label, value, valueColor=Unspecified)`** (10712, `private`) — label + a value that rolls (slide+fade `AnimatedContent`) when it changes (used for slider read-outs).
- **`ToggleRow(label, checked, onChange)`** (10740, **non-private**) — the app's one boolean toggle: a `toggleable` row (own `Role.Switch` semantics node) with a custom `MorphToggleTrack`. Label goes `Medium` weight when checked. Haptic `toggleOn`/`toggleOff`.
- **`MorphToggleTrack(checked)`** (10781, `private`) — the purely-visual pill track + spring-animated thumb (size 16→20dp, colour/offset animated); clears its own semantics so `ToggleRow` owns the one announced node.
- **`SeatConfigRow(label, heat, cool, onHeat, onCool)`** (10826, `private`) — one seat's Heat/Cool `MorphChip`s.
- **`CommandButton(label, icon, modifier, enabled, onClick)`** (10845, `private`) — a 64dp-tall icon+label `MorphButton` (used by the command UI elsewhere; defined here).

### Dialogs

- **`DriveSyncSetupDialog(onDismissRequest, onSaveToDrive, onOpenFromDrive)`** (10874, `private`) — shared Google-Drive-sync setup dialog (onboarding + Settings "Backup & sync"), built on `GlassAlertDialog` with two `DriveSyncChoiceRow` decision cards + a Cancel button.
- **`DriveSyncChoiceRow(icon, title, subtitle, onClick)`** (10913, `private`) — one `MorphButton` choice card inside that dialog.
- **`BlooDialog(onDismissRequest, title, text, confirmButton)`** (10933, `private`) — the app-wide glass-styled `AlertDialog` shell (themed title/text `Surface`s, scrollable content, spaced full-width confirm buttons, transparent container).

### 2.1 SettingsCard inventory (order top-to-bottom in the scroll)

Rendered inside the shared advanced-transition `Column` (8386–9199). "Adv" =
wrapped in / gated by advanced mode.

| Card (line) | Gating | What it toggles / does |
|---|---|---|
| **Accounts** (8391) | always | Per-brand credentials: email, masked password, Service PIN (`OutlinedTextField` + `vm.updatePin`; hidden for `usesOtpLogin` brands, i.e. Kia US), Update PIN, Sign out (tap-again-to-confirm, 4s auto-reset via `LaunchedEffect(confirmSignOut)`), "Add another account" (`vm.beginAddAccount`). |
| **AI** (8467) | shown iff `state.aiSupported`; not advanced-only | `ToggleRow` "On-device AI (Gemini Nano)" → `vm.setAiEnabled`. If enabled **and** advanced: `ToggleRow` "Summarize automatically" → `vm.setAiAuto`. |
| **App shortcuts** (8495) | Adv (whole card) | Expandable; per-vehicle launcher-icon shortcut toggles over `Shortcuts.ACTIONS` (`doors`/`climate`/`open`) via `vm.setShortcutEnabled`. |
| **Cars/Car** (8549) | always (each inner group self-gates) | Single car → one always-expanded `CarSettingsCard`; multiple → `ReorderColumn` (drag to reorder, `vm.reorderVehicles`) of collapsible `CarSettingsCard`s. Photo pick via `photoLauncher` → `cropUri`. |
| **Backup & sync** (8577) | Drive block always; Manual export/import is Adv | Icon-led Drive status; set up / change / disable auto-sync (`vm.setSyncUri`/`clearSyncUri`/`retryDriveSync`), Wi-Fi-only vs any-network segmented (`vm.setSyncWifiOnly`), last-sync/error lines. Adv: Export (`vm.exportSettings`) / Restore (import launcher → `vm.importSettings`). |
| **Display** (8729) | Units always; scale is Adv | Adv: "Text & layout scale" `AnimatedSlider` (0.8–1.3, deferred-commit `vm.setUiScaleSoon`). Always: Units imperial/metric (`vm.setUnitSystem`). |
| **Font** (8758) | Adv (whole card) | `ChoiceRow`s over `FontChoice` (System/Atkinson/Google Sans) → `vm.setFontChoice`. |
| **Links** (8774) | Adv (whole card) | In-app vs Browser (`vm.setLinksInApp`). |
| **Logs** (8789) | Adv (whole card) | Expandable activity log (`vm.logs`), Copy to clipboard, Clear (`vm.clearLogs`). |
| **Notifications** (8848) | always | Service due (`setNotifyService`), Door-left-open (`setNotifyDoor` + minutes 1–120 `setDoorOpenMinutes`), Car-running (`setNotifyRunning` + minutes `setRunningMinutes`). |
| **Quick tiles** (8900) | Adv (whole card) | Tap behaviour background/open (`setTileBackground`), live-refresh on/off (`setTileLiveRefresh`), then `QuickTilesManager`. |
| **Security** (8961) | always (content depends on `canBio`) | If a biometric is enrolled: fingerprint lock on/off (with `showBiometricPrompt` confirm before enabling) → `vm.setBiometricLock`; lock-timing segmented over `LockTiming.entries` → `vm.setLockTiming`. Else an info line. |
| **Sounds & vibration** (9005) | always | Haptic feedback (`vm.setHapticsEnabled`). |
| **Theme** (9010) | Appearance always; Aurora + dynamic-color/palettes/vibrancy/pebble-outline are Adv | Appearance System/Light/Dark/AMOLED (`setThemeMode`). Adv: Aurora background toggle + motion/colour-mode/custom-hex; dynamic color toggle; built-in `ColorPalette` swatches + custom palettes (`PaletteEditorDialog`, per-palette select); `VibrancySlider`; pebble outline. |
| **Weather** (9154) | always | Set a place by text (`vm.setWeatherPlace`) or use device location (permission launcher → `vm.useDeviceLocationForWeather`), Clear (`vm.clearWeatherLocation`). |

---

## 3. Internal structure

### 3.1 `SettingsScreen` layout (8309–9393)

State hoisted at the top (8310–8356): `appearance`/`notif`/`state`/`logs`
collected from the VM; `canBio = vm.canUseBiometrics()` (remembered);
`settingsScroll`/`settingsScope`; `pickTarget`/`cropUri` for photos; `query`,
`searchFocused`, `submittedQuery` for search.

- **`BackHandler` (8341–8348)** is layered: if `searchFocused || query.isNotEmpty()`,
  back **collapses/clears search** (`searchFocused=false; query=""`); otherwise
  `vm.closeSettings()` returns to the garage.
- **`LaunchedEffect(query.isBlank())` (8356)**: whenever the box becomes blank, it
  drops any stale AI reply (`vm.clearAiReply()`) and resets `submittedQuery=""`.
- Structure inside `BackdropHost` (8357): a width-capped (`widthIn(max=640.dp)`)
  centred `Box`, containing:
  1. The **scrolling `Column`** (8361) of cards. A top spacer clears the status
     bar/floating chrome (8370). The advanced-mode `run{}` block (8371) computes
     `advanced` and the shared `advancedEnter`/`advancedExit` transitions, then
     lays out all cards in a single `animateContentSize` `Column`. A bottom
     spacer (`bottomInset + 132.dp`, 9204) reserves room so scrolled content
     never hides behind the floating bar.
  2. The **floating bottom search `Column`** (9220), aligned `BottomCenter`,
     padded by `WindowInsets.navigationBars.union(WindowInsets.ime)` so it sits
     flush above whichever is taller (keyboard or nav bar) and tracks the real
     animated IME height. It contains, top-to-bottom: an `AnimatedVisibility`
     answer panel (visible when `searchFocused || query.isNotEmpty()`) that shows
     either `SettingsSearchResults` (query non-blank) or `SearchSuggestions`
     (focused+empty), then the `GlowySearchBar` itself. Reading order bottom-up
     is therefore [results][AI][bar].
  3. `StatusBarScrim()` (9289, skipped on a compact cover screen).
  4. The top chrome `Row` (9291): back `FloatingIcon`, "Settings" title pill
     (tap → `settingsScroll.animateScrollTo(0)`), and the Simple/Advanced
     `MorphSegmented` (`vm.setSettingsMode`).
  5. First-run coach mark (9348, `state.showSettingsCoach`, dismiss →
     `vm.dismissSettingsCoach()`).
  6. Crop dialog (9381): when `cropUri != null && pickTarget != null`, shows
     `CropScreen`; on save → `vm.setVehicleImage(target, path)`.

### 3.2 `SettingsSearchResults` flow (9963–10196)

1. **Tokenise** (9972): lower-case the *live* `query`, split on
   `[^a-z0-9%]+`, drop blanks and `SearchStopwords`.
2. **Build the entry catalogue** (9975–10066) with a local `add(title,
   keywords, content)` helper that stores a `SearchEntry` whose `haystack =
   "$title $keywords".lowercase()`:
   - **App-wide** entries (9981–10010): Haptic feedback, Text & layout scale,
     Colour vibrancy, Open links in app, Service/Door/Car-running alerts. Each
     `content` renders the *actual live control* (so search is a working editor,
     not a shortcut to the card).
   - **Per-car** entries (10013–10066), for every vehicle: License plate,
     Odometer (if present, formatted via `formatDistance`), VIN, Range
     (EV range-by-fuel or DTE), Battery + Charge limit (EV) *or* Fuel (non-EV),
     Last refreshed (`rememberRelativeTime`), Location
     (`placeNames`/`locations[...].coordString`), Powertrain picker, Last service
     miles field. Titles are suffixed "· <car name>".
3. **Filter** (10072): `if (tokens.isEmpty()) entries else entries.filter { e ->
   tokens.all { it in e.haystack } }` — an AND over tokens against the haystack.
4. **Render matches** (10083–10100): "No matches" card, else one drop-shadowed
   `Card` per result showing its title + `content()`.
5. **Command card** (10114–10155): see §3.3 — runs a recognised command.
6. **AI card** (10165–10195): if `state.aiEnabled`, a `LaunchedEffect(submittedQuery)`
   calls `vm.askAi(submittedQuery)` (or `clearAiReply` if blank); the card shows
   "Thinking…" while `"search" in state.aiBusy`, then `state.aiSearchReply`.

### 3.3 `parseVehicleCommand` → dispatch (9703–9715, 10114–10155) — the command path

**`parseVehicleCommand(query)` (9703)** lower-cases the query then matches a
`when` of regexes in **priority order** (first match wins), returning a
`ParsedVehicleCommand?`:

| # | Regex (against lower-cased query) | Result `cmd` | `climateTarget` | `label` |
|---|---|---|---|---|
| 1 | `\bunlock\b` | `"unlock"` | (default `"default"`) | "Unlocking" |
| 2 | `\block\b` | `"lock"` | default | "Locking" |
| 3 | `smart climate\|smart (ac\|a/c\|heat)` | `"climate"` | `"smart"` | "Starting smart climate for" |
| 4 | `stop (the )?(climate\|ac\|a/c\|heat)\|turn off (the )?(climate\|ac\|a/c\|heat)` | `"climate"` | **default** | "Stopping climate for" |
| 5 | `(start\|turn on\|run) (the )?(climate\|ac\|a/c\|heat)` | `"climate"` | `"default"` | "Starting climate for" |
| 6 | `stop (the )?charg` | `"charge"` | default | "Stopping charge for" |
| 7 | `(start\|begin) (the )?charg\|charge (it\|the car) now` | `"charge"` | default | "Starting charge for" |
| — | else | `null` | — | — |

Order is deliberate: **unlock is tested before the bare `\block\b`** so "unlock"
is not also captured by "lock" (9698–9702).

**Dispatch** (10114–10129), inside `SettingsSearchResults`:

1. `val command = remember(submittedQuery) { if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery) }` — **parses the SUBMITTED query, not the live one** (10114).
2. **Target resolution** (10117–10118):
   - `namedVehicle = state.vehicles.firstOrNull { v -> v.name.isNotBlank() && v.name.lowercase() in submittedQuery.lowercase() }` — the **first** car whose name is a **substring** of the submitted query.
   - `targetVehicle = namedVehicle ?: state.vehicles.singleOrNull()` — else the sole car if there's exactly one, else `null`.
3. **Execution** `LaunchedEffect(submittedQuery)` (10121–10129): if `targetVehicle != null`, sets `actionRunning=true`, calls
   `TileCommandRunner.run(ctx, targetVehicle.vin, command.cmd, command.climateTarget)` inside `runCatching`, stores `result?.message ?: "Command failed"` into `actionResult`, then `vm.refreshStatus(targetVehicle)`.
4. **UI** (10130–10154): an "Action" `Card` that reads: "Which car?…" (if no
   target), "<label> <car>…" while running, `actionResult` when done, else
   "<label> <car>".

**How the command actually reaches the car** — `TileCommandRunner.run` (in
`data/TileCommandRunner.kt:77`) is the shared execution path (also used by Quick
Settings tiles and, via the same mutex, the wear/phone command paths):

- Looks up the car's last-known `VehicleSnapshot` from `SnapshotStore`; "Car not
  found" if absent (no network touched).
- Builds a fresh `VehicleRepository` for the car's brand from the current
  session/credentials.
- Acquires `BlueLinkGate.statusMutex` (the app-wide lock; BlueLink 502s on
  overlapping requests for one account) and dispatches on `cmd`:
  - `"lock"`/`"unlock"` — explicit, non-toggling (`repo.lock`/`repo.unlock`).
  - `"doors"` — **toggles** against `snap.locked`.
  - `"charge"` — **toggles** against `snap.charging` (`stopCharge` if
    `charging == true`, else `startCharge`).
  - `"climate"` — delegates to `runClimate`, which **stops if `snap.climateOn ==
    true`, else starts** (resolving `smart`/preset/`default`; errors "Can't start
    climate while driving" if `snap.isDriving`).
  - else → "Done".
- On success writes an optimistic snapshot and returns `Result(true, msg)`; on
  failure returns `Result(false, err)`.

**The consequence** (critical, see §8b): `parseVehicleCommand` cases 4/6 ("stop
climate"/"stop charging") return the **same `cmd`** as their start counterparts
(`"climate"`/`"charge"`) and simply carry a "Stopping …" *label*. The label is
**never passed to the runner** — only `cmd` and `climateTarget` are (10124). The
runner then decides start-vs-stop purely by **toggling against the snapshot**.
So "stop charging" issued when the snapshot says the car isn't charging will
*start* charging; "stop climate" when climate reads off will *start* it. The
user-visible label ("Stopping charge for …") can be the opposite of what the
car actually does.

---

## 4. Data & types

- **`SearchStopwords: Set<String>`** (9685) — `for, the, of, show, me, what,
  whats, is, a, an, to, car, cars, my, s, setting, settings, get, in`. Dropped
  during tokenisation so "odometer for xyz" reduces to `[odometer, xyz]`.
- **`class SearchEntry(val title: String, val haystack: String, val content:
  @Composable () -> Unit)`** (9690) — one searchable settings/data row. `title`
  is displayed; `haystack` is the lower-cased "title + keywords" string matched
  against; `content` is the live control/readout to render when it matches.
- **`class ParsedVehicleCommand(val cmd: String, val climateTarget: String =
  "default", val label: String)`** (9696) — a recognised command.
  - `cmd` — maps directly onto `TileCommandRunner`'s vocabulary: `"lock"`,
    `"unlock"`, `"climate"`, `"charge"` (note: **not** `"doors"` — the parser
    emits the explicit lock/unlock, never the toggling `doors`).
  - `climateTarget` — `"smart"` (case 3), `"default"` (cases 4 & 5), or default
    `"default"` for the non-climate cases (unused by the runner for
    lock/unlock/charge).
  - `label` — a human-readable present-participle phrase for the UI card only;
    **carries the intended direction but is dropped before dispatch** (§8b).
- **`TileActions: List<Triple<String,String,ImageVector>>`** (10198) —
  `("doors","Lock / unlock",Lock)`, `("climate","Climate",Thermostat)`,
  `("charge","Charge",Bolt)`, `("open","Open",DirectionsCar)`. The tile-editor
  action vocabulary (distinct from the parser's, which uses `lock`/`unlock`).

External types referenced (not defined here): `Vehicle`, `UiState`,
`SettingsStore.Appearance`, `SettingsStore.NotificationPrefs`,
`com.bloo.bluelink.data.Powertrain`, `FontChoice`, `ThemeMode`, `LockTiming`,
`ColorPalette`, `CustomPaletteData`, `SegmentOption`. Constants:
`com.bloo.bluelink.data.TILE_COUNT` (=12), `HIDEABLE_SECTIONS`,
`Shortcuts.ACTIONS` (`["doors","climate","open"]`), `SeatPositions`.

---

## 5. State & concurrency

- **`SettingsScreen`** hoists `query`, `searchFocused`, `submittedQuery`,
  `pickTarget`, `cropUri` as `remember { mutableStateOf(...) }`. `appearance`,
  `notif`, `state`, `logs` are `collectAsState()` from the VM — any VM emission
  recomposes the screen.
- **`LaunchedEffect(query.isBlank())`** (8356) clears AI + `submittedQuery` when
  the box empties. Keyed on the *boolean*, so it re-runs only on the blank↔non-
  blank edge, not every keystroke.
- **`GlowySearchBar`** (9725): `focusRequester` + `LaunchedEffect(focused)`
  (9735) requests focus when focused. `glowPulse` is a `mutableFloatStateOf`
  driven by a **hand-ticked ~12fps loop** (`LaunchedEffect(expanded)`, 9742–9750:
  `while(true){…; delay(80)}`) using `triangleWave` — a deliberate perf choice
  to avoid an always-on 60fps blur-halo redraw while Settings is open. Width
  fraction and press scale use `animateFloatAsState`. `AnimatedContent` cross-
  fades collapsed↔expanded content. The text field intentionally has **no
  collapse-on-blur** (a spurious `isFocused=false` fires as it first composes,
  before `requestFocus` lands — closing is the explicit Close button's job,
  9850–9858).
- **`SettingsSearchResults`** — `parseVehicleCommand` result is
  `remember(submittedQuery)`; the command execution, AI request, and their
  per-run UI flags (`actionResult`, `actionRunning`, both
  `remember(submittedQuery)`) are all keyed on **`submittedQuery`**, so they run
  exactly once per deliberate submit and reset on the next. `askAi` launches a
  coroutine on `viewModelScope` that runs Gemini Nano over all cars' text and
  posts back into `aiBusy`/`aiSearchReply`.
- **`CarSettingsCard`** — `cardBg` `animateColorAsState` on `dragging`; the body
  is `AnimatedVisibility(expanded)` — deliberately **without** its own
  `animateContentSize` (the inner expand/shrink already animates the height; a
  second sprung `animateContentSize` fought it frame-by-frame, 9420–9427).
- Accounts card: `LaunchedEffect(confirmSignOut)` (8409) auto-resets the
  tap-again confirm after 4s.

---

## 6. Collaborators & data flow

**Reads from `UiState` (via `state`):** `accounts`, `aiSupported`, `aiEnabled`,
`aiAuto`, `aiBusy`, `aiSearchReply`, `vehicles`, `settingsMode`, `syncUri`,
`syncError`, `lastSyncMs`, `syncWifiOnly`, `showSettingsCoach`, `seatConfigs`,
`imageUrls`, `climatePresets`, `defaultClimatePresets`, `licensePlates`,
`lastServiceMiles`, `serviceIntervalMiles`, `tileConfigs`, `tileLabels`,
`tileClimateTargets`, `tileBackground`, `tileLiveRefresh`, `locations`,
`placeNames`; plus helpers `statusFor`, `powertrainOf`, `powertrainLabel`,
`hasBattery`, `isShortcutEnabled`, `isPebbleHidden`, `fetchedAt`. From
`appearance`: `uiScale`, `unitSystem`, `fontChoice`, `linksInApp`,
`hapticsEnabled`, `biometricLock`, `lockTiming`, `themeMode`, `auroraBackground`
/`auroraMotion`/`auroraColorMode`/`auroraCustomColor`, `dynamicColor`,
`colorPalette`, `activeCustomPaletteId`, `customPalettes`, `carCustomPaletteIds`,
`pebbleOutline`, `weatherLabel`. From `notif`: `service`, `doorOpen`,
`doorOpenMinutes`, `running`, `runningMinutes`.

**Calls on `AppViewModel`** (representative): `closeSettings`, `updatePin`,
`logout`, `beginAddAccount`, `setAiEnabled`, `setAiAuto`, `askAi`,
`clearAiReply`, `refreshStatus`, `setShortcutEnabled`, `reorderVehicles`,
`setVehicleImage`, `importSettings`/`importSettingsAndSync`/`exportSettings`,
`setSyncUri`/`clearSyncUri`/`retryDriveSync`/`setSyncWifiOnly`, `setUiScaleSoon`,
`setUnitSystem`, `setFontChoice`, `setLinksInApp`, `clearLogs`,
`setNotify*`/`setDoorOpenMinutes`/`setRunningMinutes`, `setTileBackground`,
`setTileLiveRefresh`, `setTileAssignment`/`setTileLabel`/`setTileClimateTarget`,
`setBiometricLock`/`setLockTiming`, `setHapticsEnabled`, `setThemeMode`/aurora
setters/`setDynamicColor`/`setColorPalette`/`setActiveCustomPaletteId`/`saveCustomPalette`
/`deleteCustomPalette`/`setCarPaletteId`/`setPebbleOutline`,
`setWeatherPlace`/`useDeviceLocationForWeather`/`clearWeatherLocation`,
`setPowertrain`, `setSeatFlag`, `setDefaultClimatePreset`, `setLicensePlate`,
`setLastServiceMiles`/`setServiceIntervalMiles`, `setSectionHidden`,
`dismissSettingsCoach`, `canUseBiometrics`.

**Command/AI flow OUT:**
- **Commands** bypass the VM for dispatch and go **directly** to
  `TileCommandRunner.run(ctx, vin, cmd, climateTarget)` (10124), then call
  `vm.refreshStatus` afterward. The runner mutates `SnapshotStore` (optimistic)
  and the VM's next status poll reconciles.
- **AI** flows through `vm.askAi(query)` → `viewModelScope` coroutine →
  `ai.summarize(...)` over all cars' `carText` → posts into
  `aiBusy`/`aiSearchReply`, which this screen reads back.

---

## 7. Invariants & assumptions

- **`submittedQuery` gates every side effect.** Live `query` only filters the
  on-screen list. The command card, `askAi`, `actionResult`/`actionRunning` are
  all `remember`/`LaunchedEffect`-keyed on `submittedQuery`. This is the safety
  boundary that stops mid-typing from firing a command or an AI request
  (8349–8353, 10109–10113, 10161–10164). Do not re-key any of these on `query`.
- **Parser precedence:** the first matching regex in `parseVehicleCommand` wins;
  `unlock` must precede `lock`.
- **Target direction:** commands run against **`targetVehicle`**, resolved as
  `namedVehicle ?: state.vehicles.singleOrNull()`. If multiple cars and none
  named, `targetVehicle == null` and the card asks "Which car?" instead of
  running anything (10143). With exactly one car, the name need not appear.
- **The runner decides start/stop by snapshot toggle** for `doors`/`charge`/
  `climate`; the parser's explicit `lock`/`unlock` are the only non-toggling
  door commands. So for climate/charge the *effective direction is a function of
  the last-known snapshot*, not of the words typed (see §8b).
- `TILE_COUNT` (12) tile slots are **shared across all cars**; `QuickTilesManager`
  partitions them by `tileConfigs[i].first == car.vin` and finds the first free
  global slot to add to.
- Photo paths starting with `/` are treated as local files
  (`java.io.File(...)`); anything else as a URL/model (e.g. 9457, 10304).

---

## 8. Gotchas & sharp edges

### 8a. KNOWN BUG — search resolves the target car by unbounded substring match (wrong-car risk) — ~line 10117

```kotlin
val namedVehicle = state.vehicles.firstOrNull { v ->
    v.name.isNotBlank() && v.name.lowercase() in submittedQuery.lowercase()
}
```

The car is chosen by the **first** vehicle whose name is a **substring** of the
submitted text — with **no word boundaries** and **first-in-list wins**.
Failure modes:

- If a car is named **"Ioniq"** and the user types **"lock my Ioniq 5"**,
  "ioniq" is a substring of the query, so the "Ioniq" car matches — even though
  the user meant a different car named "Ioniq 5". Whichever such car sits earlier
  in `state.vehicles` wins.
- Conversely a short/generic name that is a substring of another car's name (or
  of an unrelated word in the query) can be matched unintentionally.
- There is no disambiguation when two names both substring-match; the list order
  silently decides which real car receives the command.

Because this feeds `TileCommandRunner.run` directly, a wrong match sends a real
lock/unlock/climate/charge command to the **wrong vehicle**. This is a genuine
correctness hazard, not cosmetic.

### 8b. KNOWN BUG — "stop climate"/"stop charging" carry the same `cmd` as start; direction is dropped and the runner can invert it — ~lines 9709–9712

`parseVehicleCommand` cases 4 and 6:

```kotlin
Regex("stop (the )?(climate|ac|a/c|heat)|turn off ...").containsMatchIn(q) ->
    ParsedVehicleCommand("climate", label = "Stopping climate for")
...
Regex("stop (the )?charg").containsMatchIn(q) ->
    ParsedVehicleCommand("charge", label = "Stopping charge for")
```

These emit **`cmd = "climate"` / `cmd = "charge"`** — identical to their
start counterparts (cases 5 and 7). The only thing distinguishing "stop" from
"start" is the **`label`** string, which is used **purely for the UI card**
(10145–10149) and is **never passed to `TileCommandRunner.run`** — dispatch only
sends `command.cmd` and `command.climateTarget` (10124).

`TileCommandRunner` then re-reads the last-known snapshot and **toggles**:
`charge` does `stopCharge` iff `snap.charging == true` else `startCharge`
(`TileCommandRunner.kt:95-97`); `climate` does `stopClimate` iff `snap.climateOn
== true` else it *starts* (`runClimate`, `TileCommandRunner.kt:159`). So:

- "**stop charging**" while the snapshot says the car is **not** charging →
  the runner **starts** charging (the opposite of the words and of the
  "Stopping charge for …" label the user sees).
- "**stop climate**" while climate reads **off** in the snapshot → the runner
  **starts** climate.
- The inversion is worst when the snapshot is stale/optimistic, since the toggle
  keys off possibly-outdated state rather than a fresh fetch.

The intended direction the user typed is understood by the parser (it's in the
`label`) but is **thrown away** at the dispatch boundary; the runner's toggle
semantics are authoritative. A correct fix would require distinct stop-only
commands (or passing direction through to the runner) rather than reusing the
toggling `climate`/`charge` verbs.

### 8c. Other sharp edges

- **Explicit lock/unlock vs. toggling `doors`.** The parser emits `lock`/`unlock`
  (explicit, non-toggling in the runner), so door commands from search are *not*
  subject to the snapshot-toggle inversion that bites climate/charge — a
  deliberate asymmetry worth noting when comparing to the tile vocabulary
  (`TileActions` uses `doors`).
- **AI answers over ALL cars, commands over one.** `askAi` feeds the model every
  car's text (`AppViewModel.askAi`, 1475), so an AI answer may talk about a
  different car than the command card acted on for the same submitted query.
- **Search `content` renders live editable controls**, not read-only snapshots —
  editing a plate/service value inside a search result writes through to the VM
  immediately, same as the full card would.
- **`GlowySearchBar` never auto-collapses on blur** (9850–9858); only the Close
  button, the layered `BackHandler`, or clearing the query collapses it.
- **`MorphSegmented`'s default track height** depends on whether any option has
  an icon (48dp) or not (44dp) (10241) — mixing icon/no-icon options changes row
  height.
- The top-chrome Simple/Advanced control draws its glass ring/backdrop in a
  wrapper `Box` at **16.dp** corners to match `MorphSegmented`'s own track
  corner (a prior 20.dp mismatch is called out at 9320–9323).
- **`CarSettingsCard` "Sections shown"** filters the `"ai"` toggle out unless
  `state.aiEnabled` (9649), so the AI-summary visibility row only appears on
  AI-capable, AI-enabled devices.
- Custom-palette and per-car-palette UIs only surface when
  `appearance.customPalettes.isNotEmpty()` (9533) — the underlying store/VM
  support existed with no UI entry point before this was added.

---

## 9. Cross-references

- Command execution mechanism: `docs/codebase/app/qs-tiles.md` and
  `data/TileCommandRunner.kt` (documented behaviour summarised in §3.3).
- VM AI/status surface: `docs/codebase/app/AppViewModel-part2-status-ai.md`
  (`askAi` at `AppViewModel.kt:1471`, `aiBusy`/`aiSearchReply` state at 135–136).
- VM settings mutators: `docs/codebase/app/AppViewModel-part3-commands-settings.md`.
- Shared segmented/`SegmentOption`: `docs/codebase/uicommon/components.md`.
- Earlier `Screens.kt` parts: Part 1 (root/login/onboarding), Part 2
  (garage/carousel), Part 3 (pebbles). `SeatPositions` (1448) and `triangleWave`
  (2014) are defined in earlier parts.
