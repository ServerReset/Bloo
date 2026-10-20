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
        // Module-level opt-ins, matching what :app does -- a file needing
        // something rarer states that itself. No material3 opt-ins: this
        // module deliberately has no Material dependency.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    // BlooColors (semantic ARGB Int constants) and other pure-Kotlin data --
    // :app already depends on :shared directly, so this adds nothing new to
    // its dependency graph, just lets uicommon composables reference the same
    // canonical constants instead of re-declaring (and silently drifting
    // from) their own copies.
    implementation(project(":shared"))
    // Foundation-only, pinned via a Compose BOM no newer than :app's own so
    // versions resolve consistently and Gradle upgrades, never downgrades.
    // Deliberately NO Material dependency: shared composables take
    // colours/specs as parameters instead, a boundary originally kept so
    // this module stayed neutral to compose.material3 vs. a since-removed
    // Wear OS companion app's own wear.compose.material3.
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.runtime:runtime")
    // Icons needed for weatherIcon(); the material-icons artifact is pure ImageVector
    // data - no Material theme dependency - so it's safe to add here.
    implementation("androidx.compose.material:material-icons-extended")
    // Material3, for exactly one thing: the Text that RevealText draws with. The boundary this module
    // kept -- foundation only, colours and specs passed in as parameters -- existed to stay neutral
    // between compose.material3 and the Wear OS companion's own wear.compose.material3. That
    // neutrality is spent: the watch app depends on the phone Material3 itself, so a shared component
    // can use it without either side having to change what it is.
    // The SAME version :app pins. This module resolved material3 from the Compose BOM, which is
    // older, so anything moved here that uses a newer Material3 API failed to resolve at all --
    // LoadingIndicator did. Aligned before moving anything else across.
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.20")
}
