plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bloo.bluelink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bloo.bluelink"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The GitHub Actions run number that produced this APK (0 for a local/dev
        // build, which UpdateChecker treats as "nothing to compare against" and
        // skips). Bloo isn't on the Play Store and doesn't reliably cut tagged
        // Releases, so this - not versionCode - is what "is there a newer build"
        // actually compares.
        buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        // The branch this build came from, so the update checker compares against
        // builds of the SAME branch. run_number increments globally across all
        // branches, so checking a fixed branch both missed newer builds of the
        // installed branch and could offer a higher-numbered build of a different
        // branch that lacks the code the user is running.
        buildConfigField("String", "BUILD_BRANCH", "\"${System.getenv("GITHUB_REF_NAME") ?: ""}\"")
    }

    signingConfigs {
        // A checked-in debug keystore so every CI build is signed with the SAME
        // key — otherwise each build's signature differs and Android refuses to
        // install one over another (you'd have to uninstall first).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Shrinking on, renaming off (-dontobfuscate lives in proguard-rules.pro,
            // with the reasoning). This file's proguardFiles have been declared since
            // the project was created while minify stayed false, so every rule in them
            // -- including the serializer keeps -- was dead configuration that looked
            // like protection. isShrinkResources is deliberately NOT enabled: it can
            // drop resources that are only ever referenced by name.
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // Two deliberate, non-actionable warnings, silenced by id (not a blanket baseline) so
        // any NEW finding still fails/prints:
        //  - NewerVersionAvailable/GradleDependency: haze is pinned to 1.7.0 on purpose (2.0's
        //    factory/input/style renderer rewrite needs a visual re-tune of the status-bar and
        //    glass chrome this environment cannot verify), and a "newer Gradle/AGP" notice is a
        //    toolchain decision, not a code bug.
        //  - OldTargetApi: targetSdk 36 is the newest the toolchain here ships; bumped as
        //    tooling allows, not on a lint nudge.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "OldTargetApi")
        // Current targetSdk is intentional; see above.
        checkOnly += listOf()
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "Bloo.apk"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // The four opt-ins every UI file used to restate in its own
        // @file:OptIn(...) header -- roughly ninety copies of the same block.
        // Opting in at the module level moves the declaration to one place; a
        // file that needs something rarer (Haze, Coil) still states that itself.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

// Classes the Compose compiler must treat as STABLE even though it does not
// compile them itself. See compose-stability.conf, which is deliberately just
// the pattern list -- the explanation lives here so that file has nothing in it
// for the compiler to parse but class patterns.
//
// Everything in com.bloo.bluelink.data lives in :shared, a plain
// android.library with no Compose compiler plugin (it is shared with the watch
// and has no UI in it). The compiler cannot infer stability for a class it did
// not compile, so it assumes the worst: Vehicle, VehicleStatus, GeoLocation,
// Weather, EvTrip, ClimatePreset, SeatConfig, Powertrain and the rest were all
// UNSTABLE to this module.
//
// Skippability is all-or-nothing per call site, so one unstable parameter makes
// a composable non-skippable whatever the others are. VehicleDetailContent
// takes a Vehicle and hands it to the whole pebble column, so the car pages
// could never skip -- and every other stability fix aimed at them (@Immutable
// on UiState, @Stable on AppViewModel) was blocked behind this one and could
// not show any effect on its own.
//
// The claim is true of these types: data classes of vals parsed from JSON or
// read from disk, no mutable collection fields, and no var fields anywhere in
// the package (the only vars in it are locals inside functions). Keep it that
// way -- a var added there makes this a lie, and the symptom is a STALE ui,
// not a slow one.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf"),
    )
}

dependencies {
    val composeUi = "1.12.1"

    // Shared networking / auth / model layer.
    implementation(project(":shared"))
    // Shared foundation-only Compose components (custom slider, WiggleText, etc.).
    implementation(project(":uicommon"))
    // AutoLock's optional confirmation signals: geofencing (left the car's parking spot)
    // and Activity Recognition (driving -> walking), plus the fused location read used to
    // register/confirm a geofence. See app/.../autolock/.
    implementation("com.google.android.gms:play-services-location:21.4.0")
    // Phone side of the watch sync (Wearable Data Layer). See app/.../wear/.
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Installs the baseline profile packaged at assets/dexopt/baseline.prof so ART can
    // partially AOT-compile ahead of first use, instead of interpreting everything until
    // background dexopt eventually happens.
    //
    // We ship no profile of our own (generating one needs a device or emulator running
    // Macrobenchmark, and CI has neither) -- but AGP already MERGES the baseline profiles
    // that Compose and the other AndroidX AARs ship into the APK, and without this library
    // that merged asset is inert on the devices that need it most. Per Google's own
    // compilation-behaviour table, on API 24-27 "Baseline Profiles are installed by
    // androidx.profileinstaller on the first run WHEN THE APP MODULE DEFINES THIS
    // DEPENDENCY"; from API 28 it is Play that applies them at install time. minSdk here is
    // 26, so two supported API levels had no delivery mechanism at all.
    //
    // It matters above 27 too, for a reason specific to how this app is distributed: the
    // same docs warn that "non-Google-Play-Store app distribution channels might not support
    // using Baseline Profiles at installation", and Bloo is sideloaded from a GitHub
    // Release. So the install-time path Play would provide never runs for anyone, on any API
    // level, and this is what puts the profile in place for the next dexopt pass.
    //
    // Depends on the release build being minified, which it now is -- the docs are explicit
    // that "your release build should always have isMinifyEnabled = true", since R8 rewrites
    // the profile's rules to match the shrunk code.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // collectAsStateWithLifecycle() -- same 2.8.7 line as the two lifecycle artifacts above,
    // which ship together and are meant to be pinned in lockstep. Lets every StateFlow
    // collector in the UI pause while the app is backgrounded (below STARTED) instead of
    // collecting -- and preparing a recomposition for -- state nobody can see.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Only FragmentActivity is used (MainActivity / BiometricAuth); no fragment-ktx
    // extensions are called, so the plain `fragment` artifact is the honest dependency.
    implementation("androidx.fragment:fragment:1.9.1")

    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-graphics:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")

    // Material 3 Expressive — the Expressive components (ButtonGroup,
    // SplitButtonLayout, FloatingToolbar, LoadingIndicator) live in 1.5.0-alpha.
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    // Chrome Custom Tabs for opening Hyundai/Genesis links in-app
    implementation("androidx.browser:browser:1.10.0")
    // Background service/door alerts
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.security:security-crypto:1.1.0")

    // Real car photos (URL or the system photo picker)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Real backdrop blur for StatusBarScrim (Widgets.kt) -- Modifier.blur() only
    // affects what the modifier's OWN node draws, which for a scrim is just its own
    // flat gradient; blurring a smooth gradient with no detail in it is a visual
    // no-op regardless of API level, which is why the status bar scrim never actually
    // looked blurred. Haze captures the content behind an overlay into its own layer
    // and blurs THAT, the actual "frosted glass" effect this needed. Pinned to the
    // last 1.x release, before the 2.0 pluggable-effects rewrite changed hazeEffect's
    // own configuration shape.
    implementation("dev.chrisbanes.haze:haze:2.0.0")
    // The built-in Blur effect (HazeBlurStyle/hazeBlur), split out of the core artifact in 2.0.
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
    // Glass: refraction, chromatic dispersion and a specular rim on top of the blur (AGSL on API 33+,
    // Haze's own simplified renderer below). Used for floating elements.
    implementation("dev.chrisbanes.haze:haze-glass:2.0.0")



    // On-device Gemini Nano (ML Kit GenAI) — optional AI summaries; gated at
    // runtime by feature availability so unsupported devices simply hide it.
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")

    // Play-free watch install: the phone pairs with the watch's Wireless debugging and sideloads
    // the watch APK (see WatchAdbInstaller). Conscrypt supplies TLS 1.3 below Android 10.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Shizuku (optional): silent APK install for the self-update flow via local ADB.
    // `api`/`provider` ship; `:hidden-api-stub` is compileOnly (framework PackageInstaller
    // AIDL stubs, never in the APK); hiddenapibypass lifts the runtime non-SDK block on
    // the reflected hidden constructors. All gated at runtime by Shizuku.pingBinder().
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    compileOnly(project(":hidden-api-stub"))

    // Pure-JVM unit tests (CI: testDebugUnitTest), same setup and rationale as
    // :shared -- kotlin-test-junit maps kotlin.test's @Test/assert* onto JUnit 4
    // and pulls JUnit transitively, so the Android unit-test task actually
    // discovers them. Version = project Kotlin 2.2.20.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.20")

    // Instrumented UI tests (run on an emulator in CI -- see .github/workflows/android.yml):
    // real layouts, real gestures, real windows, the things a JVM unit test cannot see.
    // AGP pins the androidTest classpath to the app's runtime versions; androidx.test.ext:junit 1.2.1
    // wants concurrent-futures 1.2.0, so the app itself carries it (the Wear tile already does).
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeUi")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeUi")
}
