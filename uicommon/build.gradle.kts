plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bloo.uicommon"
    compileSdk = 37

    defaultConfig {
        // <= the lowest consumer minSdk (phone 26, watch 30).
        minSdk = 26
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
    // Foundation-only, pinned via the same Compose BOM the watch uses so the
    // versions resolve <= each consumer (the phone pins a newer foundation, the
    // watch this BOM) and Gradle upgrades, never downgrades. Deliberately NO
    // Material dependency: shared composables take colours/specs as parameters so
    // they're neutral to compose.material3 (phone) vs wear.compose.material3.
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.runtime:runtime")
    // Icons needed for weatherIcon(); the material-icons artifact is pure ImageVector
    // data — no Material theme dependency — so it's safe to add here.
    implementation("androidx.compose.material:material-icons-extended")
}
