# Build, Manifests, CI & ProGuard — Deep Dive

Unit: `build: gradle + manifests + CI + proguard + key resources`

This document is the exhaustive reference for how the Bloo project is assembled:
its Gradle module graph, plugin/version choices, SDK levels, signing, the exact
set of Android components (activities / services / receivers / providers) and
permissions declared in every manifest, the widget / QS-tile / Wear-tile /
complication registrations, the ProGuard keep rules, and the GitHub Actions
build+release pipeline — including precisely how `BuildConfig.BUILD_RUN_NUMBER`
and `BUILD_BRANCH` are injected.

Files covered:
- `build.gradle.kts` (root)
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `shared/build.gradle.kts`
- `wear/build.gradle.kts`
- `uicommon/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `wear/src/main/AndroidManifest.xml`
- `shared/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`
- `wear/proguard-rules.pro`
- `.github/workflows/android.yml`
- `README.md`

---

## 1. Purpose

This unit is the **build and packaging skeleton** of Bloo. It defines four Gradle
modules, wires their dependencies, sets the SDK/Java/Kotlin toolchain, declares
every runtime Android component the OS can bind, and drives the CI pipeline that
produces and publishes the two installable APKs (`Bloo.apk`, `Bloo-Wear.apk`).

Because Bloo is **not on the Play Store** and self-updates from GitHub Releases,
this unit carries unusually load-bearing details that the rest of the app depends
on at runtime:
- `BuildConfig.BUILD_RUN_NUMBER` / `BUILD_BRANCH` — injected here from CI env vars,
  read by the in-app `UpdateChecker`/`UpdateApi` to decide "is there a newer build".
- A **checked-in debug keystore** used for *all* build types so every CI build is
  signed with the same key (installs over prior installs; also required for the
  phone↔watch Data Layer pairing).
- The GitHub Actions workflow that builds, tests, generates a per-commit changelog,
  and publishes a rolling `build-<run_number>` pre-release whose plain APK assets
  the in-app updater downloads.

---

## 2. Module structure & Gradle graph

### 2.1 `settings.gradle.kts`
- `rootProject.name = "Bloo"` (`settings.gradle.kts:22`).
- Includes four modules (`:app`, `:shared`, `:uicommon`, `:wear`) — lines 23-26.
- **pluginManagement** repositories (lines 1-13): `google()` (content-filtered to
  `com.android.*`, `com.google.*`, `androidx.*` group regexes so unrelated groups
  skip Google's repo), then `mavenCentral()` and `gradlePluginPortal()`.
- **dependencyResolutionManagement** (lines 14-20): `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`
  — modules may **not** declare their own repositories; all resolution goes through
  `google()` + `mavenCentral()` declared centrally here.

### 2.2 Root `build.gradle.kts`
Declares plugin versions with `apply false` (applied per-module):
- `com.android.application` version **9.1.0** (AGP 9.1) — `build.gradle.kts:4`.
- `org.jetbrains.kotlin.plugin.compose` version **2.2.20** — line 5.
- `org.jetbrains.kotlin.plugin.serialization` version **2.2.20** — line 6.

**Key comment (lines 1-3):** AGP 9.0+ provides *built-in* Kotlin support, so the
`kotlin.android` plugin is deliberately **not** applied anywhere. Only the Compose
and serialization compiler plugins are added on top. This is a subtle,
version-specific fact: none of the module `build.gradle.kts` files apply
`org.jetbrains.kotlin.android`.

### 2.3 Module dependency graph
```
:shared    (com.android.library)      -> no project deps; api() exposes serialization + coroutines
:uicommon  (com.android.library)      -> implementation(:shared)
:app       (com.android.application)  -> implementation(:shared), implementation(:uicommon)
:wear      (com.android.application)  -> implementation(:shared), implementation(:uicommon)
```
- `:shared` is the leaf. It uses `api(...)` for `kotlinx-serialization-json` and
  `kotlinx-coroutines-android` (`shared/build.gradle.kts:31-32`) so those types
  appear transitively in `:app` and `:wear` public signatures.
- `:uicommon` depends on `:shared` (for `BlooColors` semantic ARGB Int constants)
  and Compose **foundation only** — deliberately **no Material** dependency, so its
  composables stay neutral between phone `compose.material3` and
  `wear.compose.material3` (see `uicommon/build.gradle.kts:38-41`, 49-51).

---

## 3. Per-module build config (Public surface of the build)

### 3.1 `:app` — `app/build.gradle.kts`
Plugins (lines 1-5): `com.android.application`, `kotlin.plugin.compose`,
`kotlin.plugin.serialization`.

`android { }` block:
- `namespace = "com.bloo.bluelink"` (line 8).
- `compileSdk = 37` (line 9).
- `defaultConfig` (lines 11-30):
  - `applicationId = "com.bloo.bluelink"` (line 12).
  - `minSdk = 26` (line 13).
  - `targetSdk = 36` (line 14).
  - `versionCode = 1`, `versionName = "0.1"` (lines 15-16).
  - `vectorDrawables { useSupportLibrary = true }` (line 17).
  - **`buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")`**
    (line 23). See §8 for full semantics.
  - **`buildConfigField("String", "BUILD_BRANCH", "\"${System.getenv("GITHUB_REF_NAME") ?: ""}\"")`**
    (line 29).
- `signingConfigs.getByName("debug")` (lines 36-41): `storeFile = file("debug.keystore")`,
  `storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"`.
  Comment (lines 33-35): checked-in debug keystore so every CI build shares one key.
- `buildTypes` (lines 44-56):
  - `debug` → uses the debug signing config (line 46).
  - `release` → **`isMinifyEnabled = false`** (line 49), signed with the **debug**
    signing config (line 50), `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`
    (lines 51-54). Because minify is off, ProGuard rules are effectively inert for
    normal release builds (see §9).
- `compileOptions`: Java 17 source & target (lines 58-61).
- `buildFeatures { compose = true; buildConfig = true }` (lines 62-65).
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"` (lines 66-70).

`androidComponents.onVariants` (lines 73-81): renames every variant's APK output
to **`Bloo.apk`** by casting to `com.android.build.api.variant.impl.VariantOutputImpl`
and setting `output.outputFileName`. This is why CI can reference a fixed
`Bloo.apk` name for both debug and release.

`kotlin.compilerOptions.jvmTarget = JVM_17` (lines 83-87).

**Dependencies (lines 89-143)** — the `:app` APK surface:
- Local `val composeUi = "1.12.0-alpha03"` (line 90) pins core Compose UI/foundation.
- `project(":shared")`, `project(":uicommon")` (lines 93, 95).
- `com.google.android.gms:play-services-wearable:18.2.0` (line 98) — Wear Data Layer.
- `androidx.core:core-ktx` — declared twice (**1.15.0** at line 100, **1.13.1** at
  line 119); Gradle resolves to the higher, 1.15.0. This is a redundant/mismatched
  declaration (gotcha §10).
- Lifecycle runtime + viewmodel-compose `2.8.7` (lines 101-102), `activity-compose:1.9.3`
  (line 103), `fragment-ktx:1.8.5` (line 104).
- Compose UI stack pinned to `$composeUi` (lines 106-110); `ui-tooling` is
  `debugImplementation` only (line 109).
- `compose.material3:material3:1.5.0-alpha21` (line 114) — chosen for M3 **Expressive**
  components (`ButtonGroup`, `SplitButtonLayout`, `FloatingToolbar`, `LoadingIndicator`).
- `material-icons-extended:1.7.8` (line 115).
- `datastore-preferences:1.1.1` (117), `biometric:1.1.0` (118),
  `exifinterface:1.3.7` (120), `browser:1.8.0` Custom Tabs (122),
  `work-runtime-ktx:2.9.1` (124), `security-crypto:1.1.0-alpha06` (125).
- Glance widgets: `glance-appwidget:1.1.1`, `glance-material3:1.1.1` (128-129).
- `coil-compose:2.7.0` (132) — real car photos.
- `com.google.mlkit:genai-summarization:1.0.0-beta1` (137) — on-device Gemini Nano
  AI summaries, runtime-gated by feature availability.
- OkHttp `4.12.0` + logging-interceptor (139-140), `kotlinx-serialization-json:1.7.3`
  (141), `kotlinx-coroutines-android:1.8.1` (142).

### 3.2 `:shared` — `shared/build.gradle.kts`
- Plugins: `com.android.library`, `kotlin.plugin.serialization` (lines 1-4). **No
  compose plugin** (it's the pure domain/networking layer).
- `namespace = "com.bloo.bluelink.shared"`, `compileSdk = 37` (lines 7-9).
- `minSdk = 26` (line 12) — comment: must be `<=` the lowest consumer minSdk
  (phone 26, watch 30), so 26 keeps it usable by both.
- Java 17 (16-19), `jvmTarget = JVM_17` (22-26).
- Dependencies:
  - **`api`** `kotlinx-serialization-json:1.7.3` and `kotlinx-coroutines-android:1.8.1`
    (lines 31-32) — exposed transitively because these types appear in public
    signatures consumed by `:app`/`:wear`.
  - `implementation` `core-ktx:1.15.0`, `datastore-preferences:1.1.1`,
    `security-crypto:1.1.0-alpha06`, `okhttp:4.12.0`, `logging-interceptor:4.12.0`
    (lines 34-38).
- No `applicationId`, no signing, no buildConfig — a library.

### 3.3 `:wear` — `wear/build.gradle.kts`
- Plugins (1-5): application + compose + serialization (same as `:app`).
- `namespace = "com.bloo.wear"` (line 8), `compileSdk = 37` (line 9).
- `defaultConfig` (11-24):
  - **`applicationId = "com.bloo.bluelink"`** (line 13) — deliberately the **same**
    as the phone so the Wearable Data Layer pairs the two apps (comment line 12).
  - `minSdk = 30` (line 16) — Wear OS 3+, first Compose-capable release.
  - `targetSdk = 34` (line 17) — note this is **lower** than the phone's 36.
  - `versionCode = 1`, `versionName = "0.1"` (18-19).
  - Same two `buildConfigField` injections (`BUILD_RUN_NUMBER`, `BUILD_BRANCH`) at
    lines 22-23 — same CI run bakes identical values into both APKs.
- `signingConfigs.debug` (26-35): reuses the phone's key via
  `storeFile = file("../app/debug.keystore")`. Comment (27-28): the Data Layer only
  links phone+watch apps when they share **both** `applicationId` AND signature.
- `buildTypes`: identical shape to `:app` — `release` has `isMinifyEnabled = false`,
  debug signing, proguard files (37-49).
- `androidComponents.onVariants` (66-74): renames outputs to **`Bloo-Wear.apk`**.
- Dependencies (82-123):
  - `project(":shared")`, `project(":uicommon")` (83, 85),
    `play-services-wearable:18.2.0` (86).
  - `core-ktx:1.15.0` (88), `datastore-preferences:1.1.1` (89),
    `activity-compose:1.9.3` (90), lifecycle `2.8.7` (91-92).
  - Wear Compose stack pinned via `val wear = "1.5.1"` (95):
    `wear.compose:compose-material3`, `compose-foundation`, `compose-navigation`
    (96-98); `wear:wear-input:1.1.0` (99), `wear:wear-remote-interactions:1.1.0` (100).
  - **Compose BOM** `platform("androidx.compose:compose-bom:2025.04.01")` (104),
    applied as both `implementation` and `debugImplementation` (105-106); pulls
    `foundation`, `ui`, `ui-tooling-preview`, `material-icons-extended` unversioned
    (107-111). Comment (102-103): aligns plain-Compose primitives with what Wear
    Compose is built against.
  - `coil-compose:2.7.0` (113) — Location tile map thumbnail.
  - **Wear Tiles / ProtoLayout:** `wear.tiles:tiles:1.4.1` (116),
    `wear.protolayout:protolayout:1.2.1` (117), `protolayout-material:1.2.1` (118),
    `com.google.guava:guava:33.0.0-android` (119).
  - **Complications:** `wear.watchface:watchface-complications-data-source-ktx:1.2.1`
    (122).

### 3.4 `:uicommon` — `uicommon/build.gradle.kts`
- Plugins (1-4): `com.android.library` + `kotlin.plugin.compose`. **No serialization.**
- `namespace = "com.bloo.uicommon"`, `compileSdk = 37` (7-9), `minSdk = 26` (12).
- `buildFeatures { compose = true }` (19-21). Java 17 / `jvmTarget = JVM_17`.
- Dependencies (30-52):
  - `project(":shared")` (36) — for `BlooColors` constants (adds nothing new to
    consumers' graphs since both already depend on `:shared`).
  - **Compose BOM** `2025.04.01` (42) with `foundation`, `ui`, `animation`,
    `animation-core`, `runtime`, `material-icons-extended` (44-51). Icons are pure
    `ImageVector` data (no Material theme) so safe to include for `weatherIcon()`.
  - **Deliberately NO Material dependency** (comment 38-41): shared composables take
    colors/specs as parameters, keeping them platform-neutral.

### 3.5 Version alignment strategy (cross-module)
- BOM `2025.04.01` is used by **both** `:uicommon` and `:wear` so their foundation
  versions match. `:app` instead pins `composeUi = 1.12.0-alpha03` (a **newer**
  foundation). Gradle resolves per-APK to the highest; the comment in
  `uicommon/build.gradle.kts:39-41` notes the BOM is chosen to be `<=` each consumer
  so Gradle *upgrades*, never downgrades.

---

## 4. Manifests — every declared component & permission

### 4.1 `:shared` — `shared/src/main/AndroidManifest.xml`
Minimal library manifest. Two permissions only (lines 7-8):
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

No components. Comment (4-6): all remote car commands go over the network so
INTERNET is the one permission the library needs.

### 4.2 `:app` — `app/src/main/AndroidManifest.xml`

**Permissions (lines 4-14):**
| Permission | Purpose |
|---|---|
| `INTERNET` | network |
| `ACCESS_NETWORK_STATE` | network |
| `POST_NOTIFICATIONS` | alerts (API 33+) |
| `VIBRATE` | haptics |
| `ACCESS_COARSE_LOCATION` | *optional* — set weather location to device position; app works without it |
| `REQUEST_INSTALL_PACKAGES` | self-update: download APK and launch system installer (required on API 26+) |

**`<queries>` (lines 19-38)** — Android 11+ package visibility. Declared packages so
Bloo can detect/launch official apps and digital-key/wallet apps:
- `com.stationdm.bluelink`, `com.stationdm.genesis`, `com.myuvo.link`
- `com.hyundaiusa.hyundai.digitalcarkey`, `com.genesisusa.genesis.digitalcarkey`
- `com.google.android.apps.walletnfcrel`, `com.google.android.apps.wallet`,
  `com.samsung.android.spay`
- Intent queries: `VIEW` + `https` scheme; `DIAL`; `CustomTabsService`.

**`<application>` (line 40):** `allowBackup="false"` (no cloud backup of encrypted
creds), `icon`/`roundIcon = @mipmap/ic_launcher`, `label = @string/app_name`,
`supportsRtl="true"`, `theme = @style/Theme.Bloo`.

**Declared components:**

1. **`.MainActivity`** (48-59) — exported, `launchMode="singleTask"`,
   `windowSoftInputMode="adjustResize"`, `theme=Theme.Bloo`. `MAIN`/`LAUNCHER`
   intent filter — the app's entry point.

2. **`androidx.core.content.FileProvider`** (65-73) — `authorities="${applicationId}.fileprovider"`,
   `exported="false"`, `grantUriPermissions="true"`, meta-data `FILE_PROVIDER_PATHS`
   → `@xml/file_paths`. Grants share-sheet targets temporary read of exported
   settings-backup files (comment 61-64).

3. **`.wear.WearPhoneService`** (78-85) — **exported** service. Intent filter on
   `com.google.android.gms.wearable.MESSAGE_RECEIVED` with data
   `wear://*/bloo` (scheme `wear`, host `*`, pathPrefix `/bloo`). Bound on-demand by
   Play Services when a `/bloo` message arrives — works with the phone app closed
   (comment 75-77). This is the phone side of the WearSync command/sync_request wire.

4. **`.tiles.BlooTile1` … `.tiles.BlooTile12`** (87-206) — **twelve** Quick Settings
   tile services, each exported, `icon=@drawable/ic_shortcut_car`, `label="Bloo tile N"`,
   `permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, intent filter
   `android.service.quicksettings.action.QS_TILE`. A fixed pool of 12 so a
   multi-car user can pin up to 12 QS tiles, each assignable per car.

5. **`.widget.BlooWidgetReceiver`** (209-219) — exported receiver, `label=@string/app_name`,
   intent filter `APPWIDGET_UPDATE`, meta-data `android.appwidget.provider` →
   `@xml/bloo_widget_info`. The Glance home-screen widget provider.

6. **`.widget.WidgetConfigActivity`** (222-229) — exported, `theme=Theme.Bloo`,
   intent filter `APPWIDGET_CONFIGURE`. Widget setup (first drop + launcher
   long-press → settings).

7. **`.widget.WidgetAuthActivity`** (232-236) — **not exported**,
   `excludeFromRecents="true"`, `theme=Theme.Bloo.Transparent`. Transparent
   biometric/PIN gate for widget button taps.

8. **`.data.AlertActionReceiver`** (240-242) — **not exported** receiver. Runs an
   alert notification action button (e.g. Lock / Turn off), including taps bridged
   from the watch.

9. **`.tiles.TileActionActivity`** (245-250) — **not exported**,
   `excludeFromRecents="true"`, `noHistory="true"`, `theme=Theme.Bloo.Transparent`.
   Transparent "open & send, then close" surface for Quick Settings tile taps.

### 4.3 `:wear` — `wear/src/main/AndroidManifest.xml`

`xmlns:tools` declared (line 3). **`<uses-feature android:name="android.hardware.type.watch" />`**
(line 5) marks it a watch app.

**Permissions (7-15):** `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `VIBRATE`,
`POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` (self-update, same rationale as phone).

**`<application>` (17):** `allowBackup="false"`, `icon`/`roundIcon=@drawable/ic_bloo`,
`theme=@style/Theme.BlooWear`.

**Application-level meta-data / libraries:**
- `com.google.android.wearable.standalone = true` (26-28) — watch operates
  independently when the phone isn't nearby.
- `<uses-library name="com.google.android.wearable" required="false" />` (30-32).
- `FileProvider` (36-44) — `${applicationId}.fileprovider`, for the system
  installer to read the downloaded update APK.

**Components:**

1. **`.MainActivity`** (46-55) — exported, `taskAffinity=""`, `theme=Theme.BlooWear`,
   `MAIN`/`LAUNCHER`.

2. **`.tile.BlooTile1` … `.tile.BlooTile4`** (61-112) — **four** Wear OS Tile
   providers (ProtoLayout). Each exported, icon `@drawable/ic_bloo`,
   `permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER"`, intent
   filter `androidx.wear.tiles.action.BIND_TILE_PROVIDER`, meta-data
   `androidx.wear.tiles.PREVIEW` → `@drawable/ic_bloo`. Labels: Tile1 uses
   `@string/app_name`; Tile2-4 are literals `"Bloo — car 2/3/4"`. A pool of 4, one
   per car (mirrors the phone's BlooTile1..12 QS pool). NOTE the README says "6
   poolable Wear OS Tiles" (README line 33) — the manifest declares **4** (gotcha §10).

3. **`.WearListenerService`** (116-124) — **exported**. Intent filter on **both**
   `com.google.android.gms.wearable.DATA_CHANGED` and `MESSAGE_RECEIVED`, data
   `wear://*/bloo`. Receives car snapshots + sessions (DataItems) and command
   results/messages from the phone even when the watch UI is closed. Watch side of
   WearSync.

4. **`.complication.ChargeComplication`** (127-145) — exported,
   `permission=...BIND_COMPLICATION_PROVIDER`, intent filter
   `ACTION_COMPLICATION_UPDATE_REQUEST`. Meta-data: `SUPPORTED_TYPES = SHORT_TEXT,RANGED_VALUE`,
   `UPDATE_PERIOD_SECONDS = 300`, `PROVIDER_CONFIG_ACTION = com.bloo.wear.COMPLICATION_CONFIG`.
   Charge % complication.

5. **`.complication.LockComplication`** (148-166) — same shape;
   `SUPPORTED_TYPES = SHORT_TEXT,MONOCHROMATIC_IMAGE`, `UPDATE_PERIOD_SECONDS = 300`,
   icon `@drawable/ic_shortcut_lock`. Lock state; tap to lock/unlock.

6. **`.complication.ClimateComplication`** (169-187) — same shape;
   `SUPPORTED_TYPES = SHORT_TEXT,MONOCHROMATIC_IMAGE`, icon `@drawable/ic_shortcut_climate`.
   Climate state; tap to start/stop.

7. **`.complication.ComplicationTapReceiver`** (190-192) — **not exported**. Runs a
   complication tap (lock / climate toggle).

8. **`.complication.ComplicationConfigActivity`** (195-205) — exported,
   `theme=Theme.BlooWear`. Intent filter: action `com.bloo.wear.COMPLICATION_CONFIG`,
   categories `...complications.category.PROVIDER_CONFIG` + `DEFAULT`. Per-instance
   car picker launched by the watch-face complication picker.

---

## 5. ProGuard rules

### 5.1 `app/proguard-rules.pro`
Keeps kotlinx.serialization generated serializers so JSON (de)serialization of the
wire models survives shrinking:
- `-keepattributes *Annotation*, InnerClasses` (line 2).
- `-dontnote kotlinx.serialization.**` (line 3).
- `-keepclassmembers class **$$serializer { *; }` (line 4).
- `-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }`
  (lines 5-7).
- `-keep,includedescriptorclasses class com.bloo.bluelink.data.**$$serializer { *; }`
  (line 8).
- `-keepclassmembers class com.bloo.bluelink.data.** { *** Companion; }` (lines 9-11).

### 5.2 `wear/proguard-rules.pro`
Same intent, slightly reordered/narrower (targets `com.bloo.bluelink.data.**`):
- `-keepattributes *Annotation*, InnerClasses` (2), `-dontnote kotlinx.serialization.**` (3).
- `-keepclassmembers class com.bloo.bluelink.data.** { *** Companion; }` (4-6).
- `-keepclasseswithmembers class com.bloo.bluelink.data.** { kotlinx.serialization.KSerializer serializer(...); }`
  (7-9).

**Important:** both modules set `isMinifyEnabled = false` for release
(`app/build.gradle.kts:49`, `wear/build.gradle.kts:42`), so these rules are **not
applied** in normal release builds today — they exist as correct-if-you-turn-on-minify
insurance. The serialized wire models live under package `com.bloo.bluelink.data.**`
(the keep target confirms this package layout for both phone and wear, even though
wear's namespace is `com.bloo.wear` — the wire models come from `:shared`, namespace
`com.bloo.bluelink.shared`, but the data classes are packaged `com.bloo.bluelink.data`).

---

## 6. CI/CD — `.github/workflows/android.yml`

Workflow name: **"Build Android APK"**.

**Triggers (lines 3-8):**
- `push` on **all branches** (`"**"`) and tags matching `v*`.
- `pull_request` (any).
- `workflow_dispatch` (manual).

**Permissions (10-11):** `contents: write` (needed to create Releases/tags).

### 6.1 Job `build` (13-108) — runs on every trigger
`runs-on: ubuntu-latest`. Steps:
1. **Checkout** `actions/checkout@v4` with **`fetch-depth: 0`** (full history + tags)
   — required so the changelog step can walk from the previous `build-N` tag to HEAD
   (comment 20-22).
2. **Set up JDK 21** (temurin) — note CI builds with **JDK 21** while the modules
   compile to **JVM 17 bytecode** (`sourceCompatibility`/`jvmTarget = 17`).
3. **Set up Android SDK** `android-actions/setup-android@v3`.
4. **Set up Gradle** `gradle/actions/setup-gradle@v4`.
5. `chmod +x ./gradlew`.
6. **Build debug APK**: `./gradlew assembleDebug --stacktrace`.
7. **Run unit tests**: `./gradlew testDebugUnitTest --stacktrace`.
8. **Upload phone APK** artifact `bloo-phone-apk` ← `app/build/outputs/apk/debug/*.apk`,
   `if-no-files-found: error`.
9. **Upload Wear APK** artifact `bloo-wear-apk` ← `wear/build/outputs/apk/debug/*.apk`,
   `if-no-files-found: error`.
10. **Generate changelog** (`if: github.event_name == 'push'`, lines 69-88): writes
    `release_notes.md`:
    - `git fetch --tags --quiet`, then `prev_tag=$(git tag -l 'build-*' | sed 's/^build-//' | sort -n | tail -1)`
      — the highest previous build number.
    - Emits a fixed **"How to install"** section, then **"What's changed"**: if a
      previous tag exists, `git log --pretty=format:'- %s' "build-$prev_tag"..HEAD`;
      otherwise the last 20 commit subjects.
    - Rationale (comment 61-68): GitHub's own `generate_release_notes` only lists
      merged PRs; this repo pushes straight to branches with no PRs, so it produced
      empty notes that `UpdateApi.extractChangelog` discarded. Hand-writing real
      per-commit messages is what the in-app updater reads as patch notes.
11. **Publish build as GitHub Release** (`if: push`, lines 98-108) via
    `softprops/action-gh-release@v2`:
    - `tag_name: build-${{ github.run_number }}`, `name: "Build #<run_number>"`,
      **`prerelease: true`**, `body_path: release_notes.md`.
    - `files:` `app/build/outputs/apk/debug/Bloo.apk` + `wear/build/outputs/apk/debug/Bloo-Wear.apk`.
    - Rationale (79-97): Actions artifacts are zipped and require a signed-in GitHub
      session; Release assets are plain public files, so the in-app updater can hit
      the raw `.apk` directly. One rolling pre-release per push, tagged by run number.

### 6.2 Job `release` (116-154) — version-tag pushes only
- `if: startsWith(github.ref, 'refs/tags/v')`, `needs: build`, `permissions: contents: write`.
- Steps: Checkout (shallow, default depth), JDK 21, Android SDK, Gradle,
  `chmod +x ./gradlew`.
- **Build release APKs**: `./gradlew assembleRelease --stacktrace` — release build
  type uses the checked-in **debug** signing config so it's a normally-installable
  signed APK that installs over a debug install without uninstall (comment 140-144).
- **Publish GitHub Release** `softprops/action-gh-release@v2` with
  `app/build/outputs/apk/release/Bloo.apk` + `wear/build/outputs/apk/release/Bloo-Wear.apk`
  and `generate_release_notes: true`.
- Comment (110-115): this `vN` release is for people who want a stable build; the
  in-app `UpdateApi` **ignores** these `v*` releases — it only reads `build-*`
  tagged releases (which carry a comparable `run_number`).

---

## 7. Update-checker data flow (why the build metadata matters)

`BuildConfig.BUILD_RUN_NUMBER` and `BUILD_BRANCH` are the linchpin of Bloo's
self-update because it is not on the Play Store:
- CI sets env `GITHUB_RUN_NUMBER` (globally incrementing per workflow run) and
  `GITHUB_REF_NAME` (the branch/tag name). Gradle bakes them into `BuildConfig`.
- The rolling release is tagged `build-<GITHUB_RUN_NUMBER>` — the same number baked
  into the APK. `UpdateChecker`/`UpdateApi` compares the installed
  `BUILD_RUN_NUMBER` against the newest `build-*` release **of the same branch**
  (`BUILD_BRANCH`), downloads `Bloo.apk`/`Bloo-Wear.apk`, and hands it to the system
  installer (needing `REQUEST_INSTALL_PACKAGES`).

---

## 8. `BuildConfig.BUILD_RUN_NUMBER` / `BUILD_BRANCH` — exact injection

Declared identically in `:app` and `:wear` `defaultConfig`:

```kotlin
buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
buildConfigField("String", "BUILD_BRANCH", "\"${System.getenv("GITHUB_REF_NAME") ?: ""}\"")
```
(`app/build.gradle.kts:23,29`; `wear/build.gradle.kts:22,23`)

- The **third argument to `buildConfigField` is literal Java source code**, not a
  runtime value. For the int, `System.getenv("GITHUB_RUN_NUMBER")` returns the CI
  run number as a decimal string that is valid int-literal source; `?: "0"` supplies
  the literal `0` for local/dev builds (no env var).
- For the String, the env value must be **embedded inside escaped quotes**
  (`"\"${...}\""`) so the generated `BuildConfig` field is a valid Kotlin/Java
  String literal. An empty branch yields `""`.
- `buildConfig = true` in `buildFeatures` (both modules) enables `BuildConfig`
  generation at all.
- **Reasoning captured in comments** (`app/build.gradle.kts:18-28`): `run_number`,
  not `versionCode`, is the real "is there a newer build" comparator because Bloo
  doesn't reliably cut tagged Releases and isn't on the Play Store; `0` means
  "nothing to compare against, skip". `BUILD_BRANCH` scopes the comparison to the
  same branch because `run_number` increments **globally** across all branches —
  without it the checker both missed newer builds of the installed branch and could
  offer a higher-numbered build of a *different* branch lacking the running code.

---

## 9. State & concurrency

This unit is declarative build config and static XML — **no runtime state or
concurrency of its own**. The runtime-relevant facts it *establishes*:
- Enables `BuildConfig` (compile-time constants) and Compose compiler.
- The exported `WearPhoneService` / `WearListenerService` are bound **on demand** by
  Play Services off the main app process lifecycle — they work with the UI closed;
  that is a manifest guarantee, but the concurrency lives in those service classes.
- The single debug signing key is what lets updates install in place and what pairs
  the Data Layer — a build-time invariant with runtime consequences.

---

## 10. Collaborators & data flow

- **Consumes CI env:** `GITHUB_RUN_NUMBER`, `GITHUB_REF_NAME` → `BuildConfig`.
- **Produces:** `app/build/outputs/apk/{debug,release}/Bloo.apk`,
  `wear/build/outputs/apk/{debug,release}/Bloo-Wear.apk` (fixed names via
  `onVariants`), consumed by the CI upload/release steps and ultimately by the
  in-app `UpdateApi`/`UpdateChecker`.
- **Registers runtime entry points** other code implements:
  - `:app` — `MainActivity`, `WearPhoneService` (WearSync `/bloo` message side),
    `BlooTile1..12` (QS tiles), `BlooWidgetReceiver` + `WidgetConfigActivity` +
    `WidgetAuthActivity` (Glance widget), `AlertActionReceiver`,
    `TileActionActivity`, `FileProvider`.
  - `:wear` — `MainActivity`, `BlooTile1..4` (ProtoLayout tiles),
    `WearListenerService` (WearSync DataItem + message side), `ChargeComplication`,
    `LockComplication`, `ClimateComplication`, `ComplicationTapReceiver`,
    `ComplicationConfigActivity`, `FileProvider`.
- **Resource references** the build assumes exist: `@mipmap/ic_launcher`,
  `@drawable/ic_bloo`, `@drawable/ic_shortcut_car`, `@drawable/ic_shortcut_lock`,
  `@drawable/ic_shortcut_climate`, `@style/Theme.Bloo`, `@style/Theme.Bloo.Transparent`,
  `@style/Theme.BlooWear`, `@string/app_name`, `@xml/file_paths`, `@xml/bloo_widget_info`.

---

## 11. Invariants & assumptions

1. **Phone and watch share `applicationId = "com.bloo.bluelink"` AND the same debug
   signing key.** The Wearable Data Layer only pairs the two when both match
   (`wear/build.gradle.kts:12-13,27-28`). Breaking either silently unpairs the watch.
2. **`debug.keystore` is checked in and reused for all build types** — including
   release. Every CI build is signed identically so updates install over prior
   installs without uninstall (`app/build.gradle.kts:33-35,50`).
3. **`shared`/`uicommon` minSdk (26) ≤ every consumer's minSdk** (phone 26, watch
   30). Enforced by comments, not tooling.
4. **`BUILD_RUN_NUMBER` mirrors the `build-<run_number>` release tag**, and the tag
   number is derived from the same `GITHUB_RUN_NUMBER` env — the updater's
   comparison relies on this equality.
5. **`0` run number = local/dev build**, treated as "no update comparison".
6. **APK output file names are fixed** (`Bloo.apk` / `Bloo-Wear.apk`) via
   `onVariants`; CI release steps hard-code these paths.
7. **All serialized wire models live under `com.bloo.bluelink.data.**`** — the
   ProGuard keep rules assume this package name.
8. **No `kotlin.android` plugin anywhere** — relies on AGP 9.x built-in Kotlin
   support. Downgrading AGP below 9.0 would break the build.
9. **`FAIL_ON_PROJECT_REPOS`** — no module may declare its own repositories.
10. **Exported services with permissions** (`BIND_QUICK_SETTINGS_TILE`,
    `BIND_TILE_PROVIDER`, `BIND_COMPLICATION_PROVIDER`) rely on the OS enforcing that
    only the privileged system binder can bind them despite `exported="true"`.

---

## 12. Gotchas & sharp edges

1. **`core-ktx` declared twice in `:app`** with different versions — `1.15.0`
   (`app/build.gradle.kts:100`) and `1.13.1` (line 119). Gradle resolves to 1.15.0,
   so the 1.13.1 line is dead/misleading and should be removed.
2. **`isMinifyEnabled = false` in both release build types** means the
   `proguard-rules.pro` keep rules are currently **inert**. If someone flips minify
   on without those rules being complete, serialization will break at runtime — the
   rules exist precisely to prevent that, but they're untested by CI because release
   builds don't shrink.
3. **README says "6 poolable Wear OS Tiles" but the manifest declares only 4**
   (`BlooTile1..4`, `wear/src/main/AndroidManifest.xml:61-112` vs README:33). The
   manifest is authoritative; the README is stale.
4. **Phone QS-tile pool is 12, Wear tile pool is 4** — different pool sizes; don't
   assume symmetry between the two "tile" concepts (QS tiles vs ProtoLayout Wear
   tiles are entirely different mechanisms that merely share the "BlooTileN" name).
5. **Wear `targetSdk = 34` while phone `targetSdk = 36`.** Behavioral opt-ins differ
   between the two apps; don't assume the watch gets the same OS-level behavior
   changes as the phone.
6. **`buildConfigField`'s value is source code, not data.** The escaped-quote
   pattern for `BUILD_BRANCH` is mandatory; forgetting the inner quotes produces an
   uncompilable `BuildConfig` (an unquoted identifier).
7. **CI uses JDK 21 to compile JVM-17 bytecode.** The toolchain JDK and the target
   bytecode level differ deliberately; don't "align" them by lowering the setup-java
   version.
8. **`v*` releases are invisible to the in-app updater.** Only `build-*` releases are
   read by `UpdateApi`. Cutting a `v1.0` tag will not notify installed apps
   (`android.yml:110-115`).
9. **Changelog walks `build-*` tags via numeric sort of the suffix**
   (`git tag -l 'build-*' | sed 's/^build-//' | sort -n | tail -1`). If a `build-`
   tag with a non-numeric suffix ever existed, `sort -n` would misbehave.
10. **`Theme.Bloo.Transparent`** is used by `WidgetAuthActivity` and
    `TileActionActivity` to render invisible "do work then finish" surfaces
    (`noHistory`/`excludeFromRecents`), a common footgun if that theme is renamed.
11. **`allowBackup="false"`** on both apps is intentional (encrypted creds on
    device, not in cloud backup) — flipping it on would risk leaking credential blobs
    into Auto Backup.
12. **Wear reuses the phone keystore by relative path `../app/debug.keystore`**
    (`wear/build.gradle.kts:30`); moving/renaming the phone module or its keystore
    breaks the watch build.
