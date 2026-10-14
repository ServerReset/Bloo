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
        versionCode = runNumber.coerceAtLeast(1)
        versionName = if (runNumber > 0) "build $runNumber" else "dev"
        // Same CI run number as the phone, so the watch can tell whether the phone is
        // advertising a newer watch build than the one it is running.
        buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // The SAME checked-in keystore the phone app uses, so every CI build of the watch is signed
        // with one stable key -- an unsigned release APK cannot be installed at all (the phone hands
        // it to the watch's installer and PackageManager rejects it with NO_CERTIFICATES), and a
        // key that changed per build would make in-place updates impossible.
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
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
