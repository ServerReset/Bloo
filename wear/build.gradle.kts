plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bloo.bluelink.wear"
    compileSdk = 37

    defaultConfig {
        // The SAME applicationId as the phone app, and deliberately so: the Wearable Data Layer only
        // exposes a node to an app that shares its package name and signing certificate, so with the
        // phone's `com.bloo.bluelink` and this module's old `com.bloo.bluelink.wear` the two could
        // never see each other -- the watch sat on "Waiting for your phone…" forever and the phone
        // showed no paired watch, whatever else was wired correctly.
        //
        // The code package stays com.bloo.bluelink.wear (see `namespace` above); only the app's
        // identity is shared. Installing this replaces any earlier watch build, which is expected --
        // it is a different app as far as the watch is concerned.
        applicationId = "com.bloo.bluelink"
        // Wear OS 3.0 (the first with a real Compose runtime) is API 30; the
        // phone app is 26. :shared/:uicommon both floor at 26 so they stay
        // usable by both, which is why this is not lowered to match them.
        minSdk = 30
        targetSdk = 36
        // Same as the phone: the version IS the release (the CI run number), no marketing version.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        // UNIQUE ACROSS FORM FACTORS, and deliberately higher than the phone's. Play rejects a second
        // artifact for the same app with a version code it already has, and this ordering is also what
        // routes the two: on a watch both bundles are compatible, so Play serves the higher code (this
        // build), while on a phone only this one is incompatible -- it requires the watch feature -- and
        // the phone bundle wins. One listing, one install each, no targeting rules to maintain.
        versionCode = 10_000_000 + runNumber.coerceAtLeast(1)
        versionName = if (runNumber > 0) "build $runNumber" else "dev"
        // Same CI run number as the phone, so the watch can tell whether the phone is
        // advertising a newer watch build than the one it is running.
        buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    /**
     * The same split the phone app makes, for the same reason.
     *
     * `sideload` is the watch app the phone pushes over Wireless debugging, and it carries
     * REQUEST_INSTALL_PACKAGES because installing that APK is the whole point of it.
     * `play` is the watch app Google Play installs, where Play owns the update and the permission is
     * one Play treats as restricted -- so this flavor does not have it at all.
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            // The same flag the phone app's flavors carry, for the same reason: the phone pushes this
            // one, and it updates itself from the GitHub release.
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")
        }
        create("play") {
            dimension = "distribution"
            // Play owns the update for this one. The watch's own GitHub update checker has to stand
            // down for it -- see WearUpdateChecker, which was offering a GitHub download on a
            // Play-installed watch.
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "true")
        }
    }

    signingConfigs {
        // Debug builds only. The phone's checked-in debug keystore -- the standard Android
        // development key, published in every copy of the Android SDK -- has no business signing
        // anything a user installs, and a release artifact that carries it can be impersonated by
        // anyone. See the release block below.
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The real key, from the environment so it is never in the repo. The watch shares the app's
        // identity (see applicationId above), so it uses the same key.
        System.getenv("BLOO_UPLOAD_STORE_FILE")?.let { path ->
            create("upload") {
                storeFile = file(path)
                storePassword = System.getenv("BLOO_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("BLOO_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("BLOO_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Shrinking ON, renaming OFF -- the same split the phone takes, and for the same reason
            // written out at length in app/proguard-rules.pro: CI never RUNS a watch build, and
            // renaming is the half of R8 whose failures land at runtime on a device this pipeline
            // does not have. Shrinking's failures are overwhelmingly build-time, so it is the half
            // that can be taken without a watch in the loop.
            //
            // It was off entirely, with no rules file at all, which is why the watch APK came out at
            // 57 MB: Compose, Wear Compose, the Tiles runtime and material-icons-extended all shipped
            // whole, and nothing was ever removed. The libraries that need keeps bring their own --
            // play-services-wearable and protolayout each ship a proguard.txt, which AGP applies
            // automatically -- and the rest is in wear/proguard-rules.pro.
            isMinifyEnabled = true
            // No fallback, deliberately: the dev key used to sign every watch release, which meant a
            // released APK carried a certificate anyone can reproduce. Without the real key this
            // comes out UNSIGNED, which the watch's installer rejects outright (NO_CERTIFICATES) --
            // a visible failure instead of a quietly impersonable artifact. The workflow refuses to
            // publish either one (see the "Require the real signing key" step in android.yml).
            //
            // Consequence worth knowing: this CHANGED the watch's signing key, so a watch app
            // installed from a build before this one cannot be updated in place -- it has to be
            // uninstalled on the watch once, and the phone re-pushes it.
            signingConfig = signingConfigs.findByName("upload")
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
    lint {
        // Same deliberate toolchain-noise ids silenced in :app (see app/build.gradle.kts for the
        // reasoning). Wear-compose is pinned to the Compose-BOM-compatible 1.5.0 line; a newer
        // minor is a deliberate toolchain decision, not a code finding.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

androidComponents {
    onVariants { variant ->
        // The phone's in-app updater and its watch-push both match the release asset by name
        // (UpdateApi.WATCH_ASSET_NAME), so the sideload APK must keep the name the rolling release has
        // always attached. The Play flavor is uploaded as a bundle, so its APK name only has to differ.
        val name = if (variant.flavorName == "play") "Bloo-watch-play.apk" else "Bloo-watch.apk"
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = name
            }
        }
    }
}

dependencies {
    // Both shared modules are the whole point: :shared carries the on-disk
    // snapshot the phone mirrors (SnapshotStore) plus the command layer
    // (CarCommand/CarCommandRunner), and :uicommon carries the foundation-only
    // button/chrome composables this app reuses rather than re-implementing.
    implementation(project(":shared"))
    implementation(project(":uicommon"))

    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Wear Compose (Material 3) for the watch shell, plus the Pager/Foundation
    // scrolling every watch UI leans on for its swipe-between-cars gesture.
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    // The Tile: a glance at the car from the watch's carousel (see BlooTileService).
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    // Ongoing activities: a charging car shows on the watch face and in Recents, not only in the shade.
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    // Raised to the version the phone app uses: a dependency above (the Wearable and Wear libraries) pulls in an old
    // androidx.fragment (1.1.0), which Google Play flags as an outdated SDK. Gradle takes the highest version asked for.
    implementation("androidx.fragment:fragment:1.9.1")
    // The phone's own backdrop-blur library (same version), so the watch's glass is the same glass.
    implementation("dev.chrisbanes.haze:haze:2.0.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
    // .await() for a Play Services Task in a coroutine.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.20")
}

// The watch APK ships as "Bloo-watch.apk" both in CI and on the GitHub Release, so the
// phone's update surface can advertise a direct download URL for it (UpdateApi).
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "Bloo-watch.apk"
            }
        }
    }
}
