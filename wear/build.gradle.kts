plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bloo.wear"
    compileSdk = 37

    defaultConfig {
        // Must match the phone app so the Wearable Data Layer pairs the two.
        applicationId = "com.bloo.bluelink"
        // Wear OS 3+ (the first Compose-capable Wear release).
        minSdk = 30
        // Android 16 (API 36) — matches the phone app's targetSdk so both
        // opt into the same platform behavior. compileSdk stays 37.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        vectorDrawables { useSupportLibrary = true }
        // See app/build.gradle.kts - same CI run produces both APKs, so this is
        // the same value baked into the phone build.
        buildConfigField("int", "BUILD_RUN_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "BUILD_BRANCH", "\"${System.getenv("GITHUB_REF_NAME") ?: ""}\"")
    }

    signingConfigs {
        // Reuse the phone app's checked-in debug key. The Data Layer only links
        // a phone and watch app when they share both applicationId AND signature.
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "Bloo-Wear.apk"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    implementation(project(":shared"))
    // Shared foundation-only Compose components (custom slider, WiggleText, etc.).
    implementation(project(":uicommon"))
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Google's Wear OS Compose stack (Material 3 Expressive for watches).
    val wear = "1.5.1"
    implementation("androidx.wear.compose:compose-material3:$wear")
    implementation("androidx.wear.compose:compose-foundation:$wear")
    implementation("androidx.wear.compose:compose-navigation:$wear")
    implementation("androidx.wear:wear-input:1.1.0")
    implementation("androidx.wear:wear-remote-interactions:1.1.0")

    // Regular Compose primitives (Box/Column/Canvas/pager) + icons, aligned via
    // the Compose BOM so the versions match what Wear Compose is built against.
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    debugImplementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended")
    // Small static map thumbnail on the Location tile.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Wear OS Tile (the swipeable tile outside the app) via ProtoLayout.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("com.google.guava:guava:33.0.0-android")

    // Watch-face complications (charge % slot, ranged value slot).
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
}
