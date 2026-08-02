plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bloo.bluelink.shared"
    compileSdk = 37

    defaultConfig {
        // Must be <= the lowest consumer minSdk. The phone app targets 26 and the
        // watch app 30, so 26 keeps this library usable by both.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Exposed with api(...) so both :app and :wear pick up the model/networking
    // types transitively — these classes appear in their public signatures.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Pure-JVM unit tests (CI: testDebugUnitTest). kotlin-test-junit maps
    // kotlin.test's @Test/assert* onto JUnit 4 AND pulls JUnit transitively, so
    // Android's testDebugUnitTest (a JUnit-4-based task) actually discovers and
    // runs the tests — the bare `kotlin-test` artifact provides only the assert
    // API with no runner, so no tests would be found. Version = project Kotlin 2.2.20.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.20")
}
