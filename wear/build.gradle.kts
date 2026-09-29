plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bloo.bluelink.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bloo.bluelink.wear"
        // Wear OS 3.0 (the first with a real Compose runtime) is API 30; the
        // phone app is 26. :shared/:uicommon both floor at 26 so they stay
        // usable by both, which is why this is not lowered to match them.
        minSdk = 30
        targetSdk = 36
        // Same as the phone: a plain incrementing number, since Bloo ships from
        // GitHub releases rather than the Play Store.
        versionCode = 2
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
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
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Wear Compose (Material 3) for the watch shell, plus the Pager/Foundation
    // scrolling every watch UI leans on for its swipe-between-cars gesture.
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.20")
}
