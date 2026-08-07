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
        versionCode = 1
        versionName = "0.1"
        vectorDrawables { useSupportLibrary = true }
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
            isMinifyEnabled = false
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
    val composeUi = "1.12.0-alpha03"

    // Shared networking / auth / model layer (also consumed by the :wear app).
    implementation(project(":shared"))
    // Shared foundation-only Compose components (custom slider, WiggleText, etc.).
    implementation(project(":uicommon"))
    // Wear OS Data Layer — pushes car snapshots to the watch and receives the
    // watch's remote commands.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-graphics:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")

    // Material 3 Expressive — the Expressive components (ButtonGroup,
    // SplitButtonLayout, FloatingToolbar, LoadingIndicator) live in 1.5.0-alpha.
    implementation("androidx.compose.material3:material3:1.5.0-alpha21")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Chrome Custom Tabs for opening Hyundai/Genesis links in-app
    implementation("androidx.browser:browser:1.8.0")
    // Background service/door alerts
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Home-screen widgets (Jetpack Glance)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Real car photos (URL or the system photo picker)
    implementation("io.coil-kt:coil-compose:2.7.0")



    // On-device Gemini Nano (ML Kit GenAI) — optional AI summaries; gated at
    // runtime by feature availability so unsupported devices simply hide it.
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

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
}
